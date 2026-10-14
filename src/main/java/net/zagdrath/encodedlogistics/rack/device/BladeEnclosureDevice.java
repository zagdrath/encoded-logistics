/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftQueue;
import net.zagdrath.encodedlogistics.crafting.CraftTask;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.chassis.Chassis;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;

// The Blade Enclosure (10U, the optical handoff 6). Sixteen half-height blade slots in front: Compute Blades and Memory
// Blades give its network's rack Scheduler threads and job memory as their servers do, bladeBonusPercent more per rack
// unit (RackScheduler); Fabrication Blades craft from the enclosure's nine schematic slots as a Fabrication Server does
// (more blades, quicker crafts: one per Throughput Module's worth, up to two); a Storage Blade opens two of its drive
// bays (network storage, StorageDevice). Behind: two Interconnect Modules (the lanes its blades use - the blades take
// none of the rack's; the second is redundant), six power supplies and one or two Management Modules (the second a
// standby). It runs with an interconnect, a management module and a power supply; degraded below two interconnects or
// two power supplies.
public class BladeEnclosureDevice extends StorageDevice implements Chassis, CraftingProvider {
    private static final int BLADES = 16, BLADE_PSUS = 6;
    // A blade's share of the 10U chassis.
    private static final double BLADE_UNITS = 10.0 / BLADES;

    private int[] shownParts = new int[0];
    private final CraftQueue crafts = new CraftQueue();
    private double energyCredit;
    // Modules as last counted (*CARDFAULT when one goes).
    private int lastModules = -1;

    public BladeEnclosureDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public ChassisLayout layout() {
        return ChassisLayout.BLADE;
    }

    @Override
    public BladeEnclosureDevice device() {
        return this;
    }

    @Override
    public int[] shownParts() {
        return shownParts;
    }

    @Override
    public void setShownParts(int[] parts) {
        shownParts = parts;
    }

    // --- What it has ---

    public int blades() {
        return count(ChassisPart.COMPUTE_BLADE) + count(ChassisPart.MEMORY_BLADE) + count(ChassisPart.FABRICATION_BLADE) + count(ChassisPart.STORAGE_BLADE);
    }

    public boolean running() {
        return count(ChassisPart.INTERCONNECT_MODULE) > 0 && count(ChassisPart.MANAGEMENT_MODULE) > 0 && count(ChassisPart.BLADE_PSU) > 0;
    }

    public boolean degraded() {
        return running() && (count(ChassisPart.INTERCONNECT_MODULE) < 2 || count(ChassisPart.BLADE_PSU) < 2);
    }

    private boolean working() {
        return isOnline() && running();
    }

    // Lanes its blades have: its interconnect's (the second is a standby).
    public int lanes() {
        return count(ChassisPart.INTERCONNECT_MODULE) > 0 ? Config.INTERCONNECT_LANES.getAsInt() : 0;
    }

    private static double bonus() {
        return 1 + Config.BLADE_BONUS_PERCENT.getAsInt() / 100.0;
    }

    // A Compute Server gives its threads over 2U, a Memory Server its memory over 1U; a blade its share of that.
    public static double threadsPerBlade() {
        return Config.COMPUTE_SERVER_THREADS.getAsInt() / 2.0 * BLADE_UNITS * bonus();
    }

    public static long memoryPerBlade() {
        return Math.round(Config.MEMORY_SERVER_MEMORY.getAsInt() * BLADE_UNITS * bonus());
    }

    @Override
    public int schedulerThreads() {
        int blades = count(ChassisPart.COMPUTE_BLADE);
        return running() && blades > 0 ? (int) Math.ceil(blades * threadsPerBlade() - 1.0E-9) : 0;
    }

    @Override
    public long schedulerMemory() {
        return running() ? count(ChassisPart.MEMORY_BLADE) * memoryPerBlade() : 0;
    }

    // --- Storage: two bays behind each Storage Blade ---

    @Override
    public int drives() {
        return ChassisLayout.BLADE_BAYS_FROM;
    }

    @Override
    protected boolean bayUsable(int slot) {
        return running() && ChassisPart.of(items().get(ChassisLayout.BLADE_BAYS_FROM + slot / ChassisLayout.DRIVES_PER_BLADE)) == ChassisPart.STORAGE_BLADE;
    }

    public int storageBays() {
        return count(ChassisPart.STORAGE_BLADE) * ChassisLayout.DRIVES_PER_BLADE;
    }

    @Override
    public boolean ready() {
        return working();
    }

    @Override
    protected double baseDrain() {
        return Chassis.drain(this);
    }

    // --- Crafting: the Fabrication Blades ---

    @Override
    public List<Schematic> schematics() {
        List<Schematic> schematics = new ArrayList<>();
        if (count(ChassisPart.FABRICATION_BLADE) == 0) {
            return schematics;
        }
        for (int slot = ChassisLayout.BLADE_SCHEMATICS_FROM; slot < ChassisLayout.BLADE_SCHEMATICS_FROM + ChassisLayout.SCHEMATICS; slot++) {
            Schematic schematic = SchematicItem.schematic(items().get(slot));
            if (schematic != null && schematic.kind() == Schematic.Kind.CRAFTING) {
                schematics.add(schematic);
            }
        }
        return schematics;
    }

    // More Fabrication Blades craft quicker: the second as a Throughput Module, the third as two.
    private int craftTicks() {
        List<? extends Integer> ticks = Config.FABRICATOR_CRAFT_TICKS.get();
        int speed = Math.max(0, count(ChassisPart.FABRICATION_BLADE) - 1);
        return ticks.isEmpty() ? 20 : Math.max(1, ticks.get(Math.min(speed, ticks.size() - 1)));
    }

    @Override
    public boolean offer(ServerLevel level, CraftTask offered) {
        if (!working() || rack() == null || count(ChassisPart.FABRICATION_BLADE) == 0 || offered.schematic().kind() != Schematic.Kind.CRAFTING
                || !accepts(offered.schematic())) {
            return false;
        }
        int cost = Config.FABRICATOR_ENERGY_PER_CRAFT.getAsInt();
        if (energyCredit < cost) {
            energyCredit += ControllerStructures.drawEnergy(level.getServer(), rack().network(this), (int) Math.ceil(cost - energyCredit));
            if (energyCredit < cost) {
                return false;
            }
        }
        energyCredit -= cost;
        crafts.add(offered, craftTicks());
        changed(false);
        return true;
    }

    @Override
    public void tick(ServerLevel level) {
        if (crafts.isEmpty() || !working() || rack() == null) {
            return;
        }
        List<CraftTask> done = crafts.tick();
        for (CraftTask task : done) {
            SchedulerCoreBlockEntity.deliver(level, task, craft(level, task), true, rack().getBlockPos());
        }
        if (!done.isEmpty()) {
            changed(false);
        } else {
            saveOnly();
        }
    }

    // The recipe's result and remainders, or the inputs back when the grid no longer matches a recipe.
    private static List<ItemStack> craft(ServerLevel level, CraftTask task) {
        CraftingInput input = task.schematic().craftingInput();
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isEmpty()) {
            return task.inputs();
        }
        List<ItemStack> made = new ArrayList<>();
        made.add(recipe.get().value().assemble(input));
        for (ItemStack remainder : recipe.get().value().getRemainingItems(input)) {
            if (!remainder.isEmpty()) {
                made.add(remainder);
            }
        }
        return made;
    }

    // Taken out mid-craft: the inputs go back to the jobs.
    @Override
    public void onRemoved() {
        if (rack() != null && rack().getLevel() instanceof ServerLevel level) {
            for (CraftTask task : crafts.clear()) {
                SchedulerCoreBlockEntity.refund(level, task, task.inputs(), rack().getBlockPos());
            }
        }
    }

    // --- State ---

    @Override
    public void itemsChanged() {
        RackBlockEntity rack = rack();
        int modules = count(ChassisPart.INTERCONNECT_MODULE) + count(ChassisPart.MANAGEMENT_MODULE) + count(ChassisPart.BLADE_PSU);
        if (lastModules >= 0 && modules < lastModules && rack != null && rack.getLevel() instanceof ServerLevel level) {
            ElclEvents.hardware(level.getServer(), rack.network(this), "*CARDFAULT", deviceName(), "R",
                    String.format(Locale.ROOT, "%d interconnect, %d management, %d/%d power supplies", count(ChassisPart.INTERCONNECT_MODULE),
                            count(ChassisPart.MANAGEMENT_MODULE), count(ChassisPart.BLADE_PSU), BLADE_PSUS));
        }
        lastModules = modules;
        // Blades in or out: the Scheduler's pool and the network's storage change with them.
        ControllerStructures.contentChanged();
        super.itemsChanged();
    }

    @Override
    public RackDeviceInfo.Status status() {
        if (!isOnline()) {
            return RackDeviceInfo.Status.OFFLINE;
        }
        if (!running()) {
            return RackDeviceInfo.Status.FAULT;
        }
        return degraded() ? RackDeviceInfo.Status.WARNING : RackDeviceInfo.Status.ONLINE;
    }

    @Override
    public Component statusText() {
        return status() == RackDeviceInfo.Status.WARNING ? Component.translatable("hud.encodedlogistics.chassis.degraded") : super.statusText();
    }

    @Override
    public @Nullable String modelVariant() {
        return status() == RackDeviceInfo.Status.WARNING ? RackDeviceType.DEGRADED : null;
    }

    // --- Its bays ---

    // "0.8", "2".
    private static String number(double value) {
        return value == Math.floor(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public PartState partState(ChassisLayout.Bay bay) {
        ChassisPart part = part(bay);
        boolean on = working();
        RackDeviceInfo.Status status = on ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE;
        if (bay.kind() == ChassisPart.Kind.BLADE_PSU) {
            Component detail = Component.translatable("hud.encodedlogistics.chassis.psus", count(ChassisPart.BLADE_PSU), BLADE_PSUS);
            return part == null ? PartState.empty(detail) : new PartState(isOnline() ? "*ONLINE" : "*OFFLINE",
                    isOnline() ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE, detail, isOnline());
        }
        if (part == null) {
            return PartState.empty(Component.empty());
        }
        String code = on ? "*ONLINE" : "*OFFLINE";
        return switch (part) {
            case COMPUTE_BLADE -> new PartState(code, status, Component.translatable("hud.encodedlogistics.chassis.threads", number(threadsPerBlade())), on);
            case MEMORY_BLADE -> new PartState(code, status, Component.translatable("hud.encodedlogistics.chassis.buffer",
                    MemoryServerDevice.memory(memoryPerBlade())), on);
            case FABRICATION_BLADE -> new PartState(code, status, Component.translatable("hud.encodedlogistics.chassis.schematics", schematics().size()), on);
            case STORAGE_BLADE -> {
                int drives = 0;
                for (int k = 0; k < ChassisLayout.DRIVES_PER_BLADE; k++) {
                    if (items().get((bay.slot() - ChassisLayout.BLADE_BAYS_FROM) * ChassisLayout.DRIVES_PER_BLADE + k).getItem() instanceof StorageDriveItem) {
                        drives++;
                    }
                }
                yield new PartState(code, status, Component.translatable("hud.encodedlogistics.chassis.drives", drives, ChassisLayout.DRIVES_PER_BLADE), on);
            }
            case INTERCONNECT_MODULE, MANAGEMENT_MODULE -> {
                // The first one there is active, the other a standby.
                boolean first = true;
                for (ChassisLayout.Bay other : layout().bays()) {
                    if (other.kind() == bay.kind() && other.slot() < bay.slot() && part(other) == part) {
                        first = false;
                    }
                }
                boolean live = isOnline();
                Component detail = part == ChassisPart.INTERCONNECT_MODULE ? Component.translatable("hud.encodedlogistics.chassis.lanes", lanes())
                        : Component.empty();
                yield new PartState(live ? first ? "*ACTIVE" : "*STANDBY" : "*OFFLINE", live ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE,
                        detail, live);
            }
            default -> new PartState("*FAILED", RackDeviceInfo.Status.FAULT, Component.empty(), false);
        };
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.chassis.blades"), Component.literal(blades() + " / " + BLADES)));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.server.threads"),
                Component.literal(Integer.toString(schedulerThreads()))));
        if (count(ChassisPart.MEMORY_BLADE) > 0) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.server.buffer"),
                    Component.literal(MemoryServerDevice.memory(schedulerMemory()))));
        }
        if (storageBays() > 0) {
            lines.addAll(super.lines(viewer));
        }
        return lines;
    }

    // --- Using it ---

    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, boolean atUnit) {
        return atUnit && Chassis.useOnBay(this, player, stack);
    }

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (!Chassis.handle(this, player, action, value)) {
            super.handleAction(player, action, value, text);
        }
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        super.writePanel(output, viewer);
        Chassis.writePanel(this, output);
        output.putInt("blades", blades());
        output.putInt("threads", schedulerThreads());
        output.putLong("buffer", schedulerMemory());
        output.putInt("storage_bays", storageBays());
        output.putInt("lanes", lanes());
        output.putInt("schematics", schematics().size());
        output.putInt("crafts", crafts.size());
        output.putBoolean("running", running());
        output.putInt("interconnects", count(ChassisPart.INTERCONNECT_MODULE));
        output.putInt("managements", count(ChassisPart.MANAGEMENT_MODULE));
        output.putInt("psus", count(ChassisPart.BLADE_PSU));
    }

    @Override
    public void writeClient(ValueOutput output) {
        super.writeClient(output);
        Chassis.writeClient(this, output);
    }

    @Override
    public void readClient(ValueInput input) {
        super.readClient(input);
        Chassis.readClient(this, input);
    }

    // --- Saving ---

    @Override
    public void save(ValueOutput output) {
        super.save(output);
        crafts.save(output);
        output.putDouble("energy", energyCredit);
    }

    @Override
    public void load(ValueInput input) {
        super.load(input);
        crafts.load(input);
        energyCredit = input.getDoubleOr("energy", 0);
        lastModules = count(ChassisPart.INTERCONNECT_MODULE) + count(ChassisPart.MANAGEMENT_MODULE) + count(ChassisPart.BLADE_PSU);
    }
}
