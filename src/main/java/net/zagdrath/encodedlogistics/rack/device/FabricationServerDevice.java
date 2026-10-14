/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;
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
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftQueue;
import net.zagdrath.encodedlogistics.crafting.CraftTask;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.registry.ModItems;

// The Fabrication Server (2U): a Fabricator in a rack. Nine Crafting Schematic slots and two Throughput Module slots;
// any Scheduler on the network it serves offers it crafts (the network's rack Scheduler first), and it works as a
// Fabricator does: as many crafts at once as the jobs' threads allow (CraftQueue), fabricatorEnergyPerCraft FE a craft,
// fabricatorCraftTicks[modules] ticks (pausing while offline), the result and any remainders back to the job.
public class FabricationServerDevice extends RackDevice implements CraftingProvider {
    public static final int SCHEMATIC_SLOTS = 9;

    private final CraftQueue crafts = new CraftQueue();
    private double energyCredit;
    // Its lights: blinking while it crafts and ServerDevice.LINGER ticks after, solid while online and idle.
    private long busyUntil;
    private boolean busy;

    public FabricationServerDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public double drain() {
        return Config.FABRICATION_SERVER_DRAIN.getAsDouble();
    }

    public int throughputModules() {
        int count = 0;
        for (int slot = SCHEMATIC_SLOTS; slot < items().size(); slot++) {
            if (items().get(slot).is(ModItems.THROUGHPUT_MODULE.get())) {
                count++;
            }
        }
        return count;
    }

    private int craftTicks() {
        List<? extends Integer> ticks = Config.FABRICATOR_CRAFT_TICKS.get();
        return ticks.isEmpty() ? 20 : Math.max(1, ticks.get(Math.min(throughputModules(), ticks.size() - 1)));
    }

    // --- Crafting ---

    @Override
    public List<Schematic> schematics() {
        List<Schematic> schematics = new ArrayList<>();
        for (int slot = 0; slot < SCHEMATIC_SLOTS; slot++) {
            Schematic schematic = SchematicItem.schematic(items().get(slot));
            if (schematic != null && schematic.kind() == Schematic.Kind.CRAFTING) {
                schematics.add(schematic);
            }
        }
        return schematics;
    }

    @Override
    public int load() {
        return crafts.size();
    }

    @Override
    public boolean offer(ServerLevel level, CraftTask offered) {
        if (!isOnline() || rack() == null || offered.schematic().kind() != Schematic.Kind.CRAFTING
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
    public @Nullable String modelVariant() {
        return isOnline() && !busy ? ServerDevice.IDLE : null;
    }

    @Override
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (!crafts.isEmpty() && isOnline()) {
            busyUntil = now + ServerDevice.LINGER;
        }
        boolean shown = isOnline() && now < busyUntil;
        if (shown != busy) {
            busy = shown;
            changed(false);
        }
        if (crafts.isEmpty() || !isOnline() || rack() == null) {
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

    // How many crafts it's working on at once.
    public int craftsRunning() {
        return crafts.size();
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        CraftTask task = crafts.lead();
        int progress = crafts.leadProgress(), duration = crafts.leadDuration();
        Component job = task != null ? task.schematic().outputTotals().keySet().stream().findFirst()
                .map(key -> key.stack().getHoverName()).orElse(Component.literal("?")) : Component.translatable("hud.encodedlogistics.fab.idle");
        if (crafts.size() > 1) {
            // Several crafts side by side: how many.
            job = job.copy().append(" \u00d7" + crafts.size());
        }
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.fab.schematics"),
                        Component.literal(schematics().size() + " / " + SCHEMATIC_SLOTS)),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.fab.job"), job),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.fab.progress"),
                        Component.literal(task != null ? progress * 100 / Math.max(1, duration) + "%" : "—"),
                        task != null ? new RackDeviceInfo.Bar((float) progress / Math.max(1, duration), RackDeviceInfo.BarStyle.NORMAL) : null));
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        CraftTask task = crafts.lead();
        output.putInt("progress_now", crafts.leadProgress());
        output.putInt("duration_now", crafts.leadDuration());
        if (task != null) {
            task.schematic().outputTotals().forEach((key, count) -> {
                output.putString("job", key.stack().getHoverName().getString());
                output.putLong("job_count", count);
            });
        }
    }

    // --- Saving ---

    @Override
    public void save(ValueOutput output) {
        saveItems(output);
        crafts.save(output);
        output.putDouble("energy", energyCredit);
    }

    @Override
    public void load(ValueInput input) {
        loadItems(input);
        crafts.load(input);
        energyCredit = input.getDoubleOr("energy", 0);
    }
}
