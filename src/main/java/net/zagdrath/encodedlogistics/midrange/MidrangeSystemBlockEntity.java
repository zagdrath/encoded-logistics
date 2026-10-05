/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.FabricatorBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.client.MidrangeSounds;
import net.zagdrath.encodedlogistics.crafting.CraftTask;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.crafting.JobEvents;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.crafting.JobRunner;
import net.zagdrath.encodedlogistics.crafting.RecipeLibrarySource;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.elcl.device.Diskette;
import net.zagdrath.encodedlogistics.elcl.device.DisketteDevice;
import net.zagdrath.encodedlogistics.menu.MidrangePanelMenu;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerBuffer;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.ListedDevice;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.network.SourceNode;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// The master block of a Midrange System (tier 1) or Integrated Midrange System (tier 2) (HANDOFF 2, 4):
// - its library diskettes: one 8" Diskette (two with an Expansion Cabinet), or a Diskette Magazine of four; their
//   recipes are the network's (RecipeLibrarySource) and it's ELCL's diskette drive (SAVLIB / RSTLIB);
// - a crafting job host (a choice on the Craft Plan screen, "Midrange MIDRANGE01") with threads 1 / 2 / 4 and max job
//   64 / 128 / 512, and a crafting provider for its diskettes' recipes, crafting each step itself (40 / 25 ticks,
//   midrangeCraftEnergy FE a craft); steps other schematics need go to the network's Fabricators and Gateways;
// - an ELCL batch job host for 1 / 2 / 4 jobs (Midranges registers it);
// - its network's controller (HANDOFF 4): a structure of its own in ControllerStructures, a lane source (SourceNode:
//   midrangeLaneFaces / integratedMidrangeLaneFaces faces' worth of lanes) and the network's energy buffer
//   (midrangeEnergyBlocks / integratedMidrangeEnergyBlocks controller blocks' worth). Any other controller on its
//   network is a conflict (status E8);
// - its states: off while its network isn't running; an IPL (midrangeIplTicks) each time it comes online or on F7;
//   then run, busy while crafting, attn while a job waits for items. Hold (F10) stops its job queue until Release (F11).
public class MidrangeSystemBlockEntity extends BaseContainerBlockEntity implements NetworkDevice, MidrangeDevice, JobHost, CraftingProvider,
        RecipeLibrarySource, DisketteDevice, ControllerBuffer, ListedDevice {
    public static final String TYPE = "MIDRANGE";
    // Tier 1: diskette slots A and B (B with an Expansion Cabinet). Tier 2: the magazine in slot A.
    public static final int SLOT_A = 0, SLOT_B = 1;

    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private final JobRunner runner = new JobRunner();
    // The steps it's crafting itself, each with its progress.
    private final List<CraftTask> tasks = new ArrayList<>();
    private final List<Integer> progress = new ArrayList<>();
    private boolean online, wasOnline, held;
    private int ipl;
    // The default library's drive position (8=Make default library): its diskette is mounted first (SAVLIB / RSTLIB).
    private int defaultDrive;
    private double energyCredit;
    private String deviceName = "";
    // Terminal OS sessions open at an Integrated system's console (not saved).
    private int sessions;
    // As its network's controller: its structure (kept, as a rack keeps its controllers'), its buffer, how its network
    // stands.
    private long structure;
    private final Buffer energy = new Buffer();
    private NetworkStatus networkStatus = NetworkStatus.NO_POWER;

    public MidrangeSystemBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.MIDRANGE_SYSTEM.get(), pos, state);
    }

    // --- Its tier ---

    public boolean integrated() {
        return getBlockState().getBlock() instanceof IntegratedMidrangeBlock;
    }

    public boolean expanded() {
        BlockState state = getBlockState();
        return !integrated() && state.hasProperty(MidrangeStates.EXPANSION) && state.getValue(MidrangeStates.EXPANSION);
    }

    @Override
    public int threads() {
        return integrated() ? 4 : expanded() ? 2 : 1;
    }

    @Override
    public long memory() {
        return integrated() ? 512 : expanded() ? 128 : 64;
    }

    public int batchJobs() {
        return integrated() ? 4 : expanded() ? 2 : 1;
    }

    private int stepTicks() {
        return Math.max(1, integrated() ? Config.INTEGRATED_MIDRANGE_STEP_TICKS.getAsInt() : Config.MIDRANGE_STEP_TICKS.getAsInt());
    }

    // --- Its network's controller ---

    // Its node: a lane source of its own structure (none yet: an ordinary pass-through block until it has one).
    public static NetworkNode node(Level level, BlockPos pos, double drain) {
        long id = level.getBlockEntity(pos) instanceof MidrangeSystemBlockEntity system ? system.structure : 0;
        boolean integrated = level.getBlockState(pos).getBlock() instanceof IntegratedMidrangeBlock;
        int lanes = (integrated ? Config.INTEGRATED_MIDRANGE_LANE_FACES.getAsInt() : Config.MIDRANGE_LANE_FACES.getAsInt())
                * Config.LANES_PER_CONTROLLER_FACE.getAsInt();
        return new SourceNode(pos.immutable(), EnumSet.allOf(Direction.class), drain, id > 0 ? id : NetworkNode.NO_CONTROLLER, lanes);
    }

    public long controllerStructure() {
        return structure;
    }

    // Its structure in ControllerStructures: made when it has none (or lost it), dropped when it goes.
    private void keepStructure(ServerLevel level) {
        ControllerStructures structures = ControllerStructures.get(level);
        ControllerStructures.Structure own = structure > 0 ? structures.get(structure) : null;
        if (own == null || !own.midrange() || !own.members().getFirst().equals(worldPosition)) {
            structure = structures.addMidrange(worldPosition);
            setChanged();
        }
    }

    // How its network stands (ControllerStructures, each tick it runs).
    public void setNetworkStatus(NetworkStatus status) {
        networkStatus = status;
    }

    public NetworkStatus networkStatus() {
        return networkStatus;
    }

    @Override
    public int getEnergy() {
        return energy.getAmountAsInt();
    }

    @Override
    public int getCapacity() {
        energy.refreshLimits();
        return energy.getCapacityAsInt();
    }

    @Override
    public int drain(int amount) {
        int taken = Math.min(amount, energy.getAmountAsInt());
        if (taken > 0) {
            energy.set(energy.getAmountAsInt() - taken);
        }
        return taken;
    }

    @Override
    public int fill(int amount, TransactionContext transaction) {
        return energy.fill(amount, transaction);
    }

    @Override
    public int takeReceived() {
        int received = energy.receivedThisTick;
        energy.receivedThisTick = 0;
        energy.refreshLimits();
        return received;
    }

    // Sets its buffer (game tests).
    public void charge(int amount) {
        energy.refreshLimits();
        energy.set(Math.min(amount, energy.getCapacityAsInt()));
    }

    // Controller blocks' worth of buffer and receive rate.
    private int energyBlocks() {
        return integrated() ? Config.INTEGRATED_MIDRANGE_ENERGY_BLOCKS.getAsInt() : Config.MIDRANGE_ENERGY_BLOCKS.getAsInt();
    }

    // Its buffer, sized from the config (which may not be loaded when the block entity is made).
    private final class Buffer extends SimpleEnergyHandler {
        int receivedThisTick;

        Buffer() {
            super(Config.CONTROLLER_ENERGY_PER_BLOCK.getDefault() * 4, Config.CONTROLLER_MAX_RECEIVE.getDefault() * 4, 0);
        }

        void refreshLimits() {
            if (Config.SPEC.isLoaded() && level != null) {
                capacity = (int) Math.min(Integer.MAX_VALUE, (long) Config.CONTROLLER_ENERGY_PER_BLOCK.getAsInt() * energyBlocks());
                maxInsert = (int) Math.min(Integer.MAX_VALUE, (long) Config.CONTROLLER_MAX_RECEIVE.getAsInt() * energyBlocks());
            }
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            refreshLimits();
            return super.insert(Math.min(amount, Math.max(0, maxInsert - receivedThisTick)), transaction);
        }

        int fill(int amount, TransactionContext transaction) {
            refreshLimits();
            int limit = maxInsert;
            maxInsert = Integer.MAX_VALUE;
            try {
                return super.insert(amount, transaction);
            } finally {
                maxInsert = limit;
            }
        }

        @Override
        public void deserialize(ValueInput input) {
            refreshLimits();
            super.deserialize(input);
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            if (energy > previousAmount) {
                receivedThisTick += energy - previousAmount;
            }
            setChanged();
        }
    }

    // --- State ---

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public boolean listedOnline() {
        return online;
    }

    // Up and past its IPL: it runs jobs.
    public boolean running() {
        return online && wasOnline && ipl <= 0;
    }

    public boolean held() {
        return held;
    }

    public int iplLeft() {
        return ipl;
    }

    // An IPL: its crafting is held until it's done (the steps it's crafting finish after); its batch jobs end.
    public void startIpl() {
        ipl = Math.max(1, Config.MIDRANGE_IPL_TICKS.getAsInt());
        if (level != null) {
            level.playSound(null, worldPosition, ModSounds.DRIVE_SEEK.value(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        setChanged();
    }

    public void setHeld(boolean held) {
        if (this.held != held) {
            this.held = held;
            setChanged();
        }
    }

    // The status display's code (HANDOFF 2): Cn during IPL; A6 running (A6b and the threads in use while crafting);
    // E1 no diskette, E2 a job waits for items, E8 another controller on its network, E9 network offline (no power).
    public String statusCode() {
        if (!online) {
            return networkStatus == NetworkStatus.CONFLICT ? "E8" : "E9";
        }
        if (ipl > 0) {
            String[] codes = { "C1", "C3", "C6", "C9", "CA", "CC" };
            int total = Math.max(1, Config.MIDRANGE_IPL_TICKS.getAsInt());
            return codes[Math.min(codes.length - 1, (total - ipl) * codes.length / total)];
        }
        if (waiting()) {
            return "E2";
        }
        if (diskettes().isEmpty()) {
            return "E1";
        }
        return busy() ? "A6b" + threadsUsed() : "A6";
    }

    // What the status code means, as a lang key.
    public String statusKey() {
        if (!online) {
            return networkStatus == NetworkStatus.CONFLICT ? "crt.encodedlogistics.mrctl.status.conflict" : "crt.encodedlogistics.mrctl.status.offline";
        }
        if (ipl > 0) {
            return "crt.encodedlogistics.mrctl.status.ipl";
        }
        if (waiting()) {
            return "crt.encodedlogistics.mrctl.status.waiting";
        }
        if (diskettes().isEmpty()) {
            return "crt.encodedlogistics.mrctl.status.no_diskette";
        }
        if (held) {
            return "crt.encodedlogistics.mrctl.status.held";
        }
        return busy() ? "crt.encodedlogistics.mrctl.status.busy" : "crt.encodedlogistics.mrctl.status.idle";
    }

    private boolean busy() {
        return !tasks.isEmpty() || runner.anyRunning();
    }

    // A running job waits for items (from tape, or never there).
    private boolean waiting() {
        return runner.jobs().stream().anyMatch(job -> job.running && !job.awaiting.isEmpty());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MidrangeSystemBlockEntity system) {
        if (level instanceof ServerLevel serverLevel) {
            system.tick(serverLevel);
        }
    }

    // Its fans (client).
    public static void clientTick(Level level, BlockPos pos, BlockState state, MidrangeSystemBlockEntity system) {
        MidrangeSounds.tick(system);
    }

    private void tick(ServerLevel level) {
        keepStructure(level);
        if (!online) {
            wasOnline = false;
            ipl = 0;
            show(level, MidrangeStates.Run.OFF, MidrangeStates.Console.OFF, false);
            return;
        }
        if (!wasOnline) {
            wasOnline = true;
            startIpl();
        }
        ejectUnusable(level);
        tickTasks(level);
        if (ipl > 0) {
            ipl--;
            show(level, MidrangeStates.Run.IPL, MidrangeStates.Console.BOOT, true);
            return;
        }
        if (!held) {
            MinecraftServer server = level.getServer();
            NetworkRef network = network();
            if (runner.tick(level, worldPosition, threads(), () -> providers(server, network), () -> ControllerStructures.sharedStorageOf(server, network, true),
                    job -> {
                        ControllerStructures.jobFinished(server, network);
                        JobEvents.ended(server, network, this, job, JobEvents.Outcome.COMPLETED, "");
                    })) {
                setChanged();
            }
        }
        MidrangeStates.Run run = waiting() ? MidrangeStates.Run.ATTN : busy() ? MidrangeStates.Run.BUSY : MidrangeStates.Run.RUN;
        show(level, run, sessions > 0 ? MidrangeStates.Console.ON : MidrangeStates.Console.OFF, true);
    }

    // Itself first, then the rest of its network's providers.
    private List<CraftingProvider> providers(MinecraftServer server, @Nullable NetworkRef network) {
        List<CraftingProvider> providers = new ArrayList<>();
        providers.add(this);
        for (CraftingProvider provider : ControllerStructures.providersOf(server, network)) {
            if (provider != this) {
                providers.add(provider);
            }
        }
        return providers;
    }

    public @Nullable NetworkRef network() {
        return level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
    }

    // The block's state and console, and its Expansion Cabinet's lamp.
    private void show(ServerLevel level, MidrangeStates.Run run, MidrangeStates.Console console, boolean lit) {
        BlockState state = getBlockState();
        BlockState shown = state.setValue(MidrangeStates.STATE, run);
        if (shown.hasProperty(MidrangeStates.CONSOLE)) {
            shown = shown.setValue(MidrangeStates.CONSOLE, console);
        }
        if (shown != state) {
            level.setBlock(worldPosition, shown, Block.UPDATE_ALL);
        }
        BlockPos cabinet = cabinet();
        if (cabinet != null) {
            BlockState there = level.getBlockState(cabinet);
            if (there.hasProperty(MidrangeStates.LIT) && there.getValue(MidrangeStates.LIT) != lit) {
                level.setBlock(cabinet, there.setValue(MidrangeStates.LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    // Where its Expansion Cabinet is, or null without one.
    private @Nullable BlockPos cabinet() {
        return expanded() && level != null ? ExpansionCabinetBlock.cabinetOf(level, worldPosition, getBlockState().getValue(FootprintBlock.FACING)) : null;
    }

    // A diskette in slot B with the cabinet gone comes out.
    private void ejectUnusable(ServerLevel level) {
        if (!integrated() && !expanded() && !items.get(SLOT_B).isEmpty()) {
            Block.popResource(level, worldPosition.relative(getBlockState().getValue(FootprintBlock.FACING)), items.get(SLOT_B));
            items.set(SLOT_B, ItemStack.EMPTY);
            setChanged();
        }
    }

    // --- Its diskettes ---

    // The diskettes it reads: slot A (and B with the cabinet), or the magazine's.
    public List<ItemStack> diskettes() {
        List<ItemStack> diskettes = new ArrayList<>();
        if (integrated()) {
            ItemStack magazine = items.get(SLOT_A);
            if (magazine.is(ModItems.DISKETTE_MAGAZINE.get())) {
                diskettes.addAll(DisketteMagazineItem.diskettes(magazine));
            }
        } else {
            for (int slot = SLOT_A; slot <= (expanded() ? SLOT_B : SLOT_A); slot++) {
                if (!items.get(slot).isEmpty()) {
                    diskettes.add(items.get(slot));
                }
            }
        }
        return diskettes;
    }

    @Override
    public List<Schematic> recipes() {
        List<Schematic> recipes = new ArrayList<>();
        for (ItemStack diskette : diskettes()) {
            recipes.addAll(DisketteStack.data(diskette).recipes());
        }
        return recipes;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (integrated()) {
            return slot == SLOT_A && stack.is(ModItems.DISKETTE_MAGAZINE.get());
        }
        return (slot == SLOT_A || slot == SLOT_B && expanded()) && stack.is(ModItems.DISKETTE_8IN.get());
    }

    // A diskette (or magazine) in or out: the latch.
    @Override
    public void setItem(int slot, ItemStack stack) {
        boolean changed = items.get(slot).isEmpty() != stack.isEmpty();
        super.setItem(slot, stack);
        if (changed && level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition, ModSounds.DISKETTE_LATCH.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = super.removeItem(slot, amount);
        if (!removed.isEmpty() && level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition, ModSounds.DISKETTE_LATCH.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return removed;
    }

    // Whether a diskette (a magazine for tier 2) in hand would go in a free slot.
    public boolean canTake(ItemStack stack) {
        for (int slot = SLOT_A; slot <= SLOT_B; slot++) {
            if (items.get(slot).isEmpty() && canPlaceItem(slot, stack)) {
                return true;
            }
        }
        return false;
    }

    // Puts a diskette (a magazine for tier 2) from a hand into a free slot; false when it doesn't take it.
    public boolean insert(ItemStack stack) {
        for (int slot = SLOT_A; slot <= SLOT_B; slot++) {
            if (items.get(slot).isEmpty() && canPlaceItem(slot, stack)) {
                setItem(slot, stack.split(1));
                setChanged();
                return true;
            }
        }
        return false;
    }

    // The drive positions its control panel lists, empty ones too: slot A (and B with the cabinet), or the magazine's
    // four.
    public List<ItemStack> positions() {
        List<ItemStack> positions = new ArrayList<>();
        if (integrated()) {
            ItemStack magazine = items.get(SLOT_A);
            List<ItemStack> inside = magazine.is(ModItems.DISKETTE_MAGAZINE.get()) ? DisketteMagazineItem.diskettes(magazine) : List.of();
            for (int i = 0; i < DisketteMagazineItem.CAPACITY; i++) {
                positions.add(i < inside.size() ? inside.get(i) : ItemStack.EMPTY);
            }
        } else {
            positions.add(items.get(SLOT_A));
            if (expanded()) {
                positions.add(items.get(SLOT_B));
            }
        }
        return positions;
    }

    // The default position: the one chosen while it holds a diskette, else the first that does (-1: none).
    public int defaultDrive() {
        List<ItemStack> positions = positions();
        if (defaultDrive < positions.size() && !positions.get(defaultDrive).isEmpty()) {
            return defaultDrive;
        }
        for (int i = 0; i < positions.size(); i++) {
            if (!positions.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public boolean setDefaultDrive(int position) {
        List<ItemStack> positions = positions();
        if (position < 0 || position >= positions.size() || positions.get(position).isEmpty()) {
            return false;
        }
        defaultDrive = position;
        setChanged();
        return true;
    }

    // 4=Eject / 4=Remove from magazine: the diskette at a position comes out (empty when there's none).
    public ItemStack takeDiskette(int position) {
        List<ItemStack> positions = positions();
        if (position < 0 || position >= positions.size() || positions.get(position).isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (integrated()) {
            List<ItemStack> inside = new ArrayList<>(DisketteMagazineItem.diskettes(items.get(SLOT_A)));
            ItemStack out = inside.remove(position);
            DisketteMagazineItem.setDiskettes(items.get(SLOT_A), inside);
            setChanged();
            if (level != null) {
                level.playSound(null, worldPosition, ModSounds.DISKETTE_LATCH.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return out;
        }
        return removeItemNoUpdate(position == 0 ? SLOT_A : SLOT_B).copy();
    }

    // A sneak-use with an empty hand: the last diskette in (B, then A), or the magazine.
    public ItemStack ejectLast() {
        for (int slot = SLOT_B; slot >= SLOT_A; slot--) {
            if (!items.get(slot).isEmpty()) {
                ItemStack out = items.get(slot).copy();
                setItem(slot, ItemStack.EMPTY);
                setChanged();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    // 3=Hold / 6=Release a job: a held job doesn't start, a running one starts no more steps.
    public boolean holdJob(UUID id, boolean hold) {
        if (runner.setHeld(id, hold)) {
            setChanged();
            return true;
        }
        return false;
    }

    public boolean jobHeld(UUID id) {
        return runner.isHeld(id);
    }

    // --- RecipeLibrarySource and DisketteDevice ---

    @Override
    public String name() {
        return deviceName.isEmpty() ? TYPE + "01" : deviceName;
    }

    @Override
    public boolean online() {
        return running();
    }

    @Override
    public List<Diskette> mounted() {
        List<Diskette> mounted = new ArrayList<>();
        if (integrated()) {
            int count = diskettes().size();
            for (int i = 0; i < count; i++) {
                int index = i;
                mounted.add(new DisketteStack(() -> DisketteMagazineItem.diskettes(items.get(SLOT_A)).get(index).copy(), written -> {
                    List<ItemStack> inside = new ArrayList<>(DisketteMagazineItem.diskettes(items.get(SLOT_A)));
                    inside.set(index, written);
                    DisketteMagazineItem.setDiskettes(items.get(SLOT_A), inside);
                    setChanged();
                }));
            }
        } else {
            for (int slot = SLOT_A; slot <= (expanded() ? SLOT_B : SLOT_A); slot++) {
                int at = slot;
                if (!items.get(at).isEmpty()) {
                    mounted.add(new DisketteStack(() -> items.get(at), written -> setChanged()));
                }
            }
        }
        // The default library first.
        int first = defaultDrive();
        if (first > 0) {
            List<ItemStack> positions = positions();
            int index = (int) positions.subList(0, first).stream().filter(stack -> !stack.isEmpty()).count();
            if (index > 0 && index < mounted.size()) {
                mounted.addFirst(mounted.remove(index));
            }
        }
        return mounted;
    }

    // --- Crafting its diskettes' recipes (CraftingProvider) ---

    @Override
    public List<Schematic> schematics() {
        return recipes();
    }

    // A crafting step from its diskettes, while it's running and has a thread free; midrangeCraftEnergy FE a craft.
    @Override
    public boolean offer(ServerLevel level, CraftTask offered) {
        if (!running() || tasks.size() >= threads() || offered.schematic().kind() != Schematic.Kind.CRAFTING || !accepts(offered.schematic())) {
            return false;
        }
        int cost = Config.MIDRANGE_CRAFT_ENERGY.getAsInt();
        if (energyCredit < cost) {
            energyCredit += ControllerStructures.get(level).drawEnergy(level, worldPosition, (int) Math.ceil(cost - energyCredit));
            if (energyCredit < cost) {
                return false;
            }
        }
        energyCredit -= cost;
        tasks.add(offered);
        progress.add(0);
        setChanged();
        return true;
    }

    private void tickTasks(ServerLevel level) {
        int ticks = stepTicks();
        for (int i = tasks.size() - 1; i >= 0; i--) {
            int now = progress.get(i) + 1;
            if (now < ticks) {
                progress.set(i, now);
                continue;
            }
            CraftTask done = tasks.remove(i);
            progress.remove(i);
            SchedulerCoreBlockEntity.deliver(level, done, FabricatorBlockEntity.craft(level, done), true, worldPosition);
            setChanged();
        }
    }

    // How far its own steps are, 0-1 (the panel's bar), for a job.
    public float stepProgress(UUID job) {
        int ticks = stepTicks();
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).job().equals(job)) {
                return Math.min(1, (float) progress.get(i) / ticks);
            }
        }
        return 0;
    }

    // --- Crafting jobs (JobHost) ---

    @Override
    public BlockPos hostPos() {
        return worldPosition;
    }

    @Override
    public List<CraftingJob> jobs() {
        return runner.jobs();
    }

    @Override
    public @Nullable CraftingJob job(UUID id) {
        return runner.job(id);
    }

    @Override
    public int threadsUsed() {
        return runner.threadsUsed();
    }

    @Override
    public long memoryUsed() {
        return runner.memoryUsed();
    }

    // Takes jobs while it's running and its queue isn't held.
    public boolean takesJobs() {
        return running() && !held;
    }

    @Override
    public void addJob(CraftingJob job) {
        runner.add(job);
        setChanged();
    }

    @Override
    public boolean cancel(UUID id) {
        CraftingJob job = level instanceof ServerLevel serverLevel
                ? runner.cancel(serverLevel, worldPosition, id, ControllerStructures.storageOf(serverLevel.getServer(), network())) : null;
        if (job == null) {
            return false;
        }
        setChanged();
        JobEvents.ended(level.getServer(), network(), this, job, JobEvents.Outcome.CANCELLED, "");
        return true;
    }

    @Override
    public void jobChanged() {
        setChanged();
    }

    // Broken: its own steps' inputs go back to their jobs, then what its jobs hold drops, and they've failed.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Its network has no controller now.
        if (structure > 0) {
            ControllerStructures.get(serverLevel).removeRack(serverLevel, structure);
        }
        for (CraftTask task : List.copyOf(tasks)) {
            SchedulerCoreBlockEntity.refund(serverLevel, task, task.inputs(), worldPosition);
        }
        tasks.clear();
        progress.clear();
        NetworkRef network = network();
        for (CraftingJob job : runner.dropAll(serverLevel, worldPosition)) {
            JobEvents.ended(serverLevel.getServer(), network, this, job, JobEvents.Outcome.FAILED, JobEvents.SCHEDULER_REMOVED);
        }
    }

    // As ELCL's batch job host: 1 / 2 / 4 jobs, while it's running; its jobs end when it's down (an IPL too).
    public net.zagdrath.encodedlogistics.elcl.job.JobHost batchHost() {
        return new net.zagdrath.encodedlogistics.elcl.job.JobHost() {
            @Override
            public String name() {
                return MidrangeSystemBlockEntity.this.name();
            }

            @Override
            public int capacity() {
                return batchJobs();
            }

            @Override
            public boolean resumes() {
                return false;
            }

            @Override
            public boolean online() {
                return running() && !isRemoved();
            }
        };
    }

    public void writeOpening(RegistryFriendlyByteBuf buf) {
        Midranges.writeOpening(this, this, buf);
    }

    // --- An Integrated system's console ---

    public void consoleOpened() {
        sessions++;
    }

    public void consoleClosed() {
        sessions = Math.max(0, sessions - 1);
    }

    // --- Name ---

    @Override
    public String deviceType() {
        return TYPE;
    }

    @Override
    public String deviceName() {
        return deviceName;
    }

    @Override
    public void setDeviceName(String name) {
        if (!deviceName.equals(name)) {
            deviceName = name;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // The client knows its name (the Craft Plan screen's "Midrange MIDRANGE01").
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("device_name", deviceName);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Container and menu ---

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return 2;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new MidrangePanelMenu(containerId, inventory, this, this, PeripheralMenu.Opening.SERVER);
    }

    // --- Saving (its name goes with the item) ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        deviceName = input.getStringOr("device_name", "");
        // The client's update has its name only.
        if (!input.getBooleanOr("saved", false)) {
            return;
        }
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        held = input.getBooleanOr("held", false);
        structure = input.getLongOr("structure", 0);
        input.child("energy_buffer").ifPresent(energy::deserialize);
        defaultDrive = input.getIntOr("default_drive", 0);
        ipl = input.getIntOr("ipl", 0);
        energyCredit = input.getDoubleOr("energy", 0);
        runner.load(input.read("jobs", CraftingJob.CODEC.listOf()).orElse(List.of()));
        runner.heldJobs().clear();
        runner.heldJobs().addAll(input.read("held_jobs", UUIDUtil.CODEC.listOf()).orElse(List.of()));
        tasks.clear();
        progress.clear();
        tasks.addAll(input.read("tasks", CraftTask.CODEC.listOf()).orElse(List.of()));
        List<Integer> saved = input.read("progress", Codec.INT.listOf()).orElse(List.of());
        for (int i = 0; i < tasks.size(); i++) {
            progress.add(i < saved.size() ? saved.get(i) : 0);
        }
        // Back online after a load: an IPL first.
        wasOnline = false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
        output.putBoolean("saved", true);
        output.putLong("structure", structure);
        energy.serialize(output.child("energy_buffer"));
        output.putBoolean("held", held);
        output.putInt("default_drive", defaultDrive);
        if (!runner.heldJobs().isEmpty()) {
            output.store("held_jobs", UUIDUtil.CODEC.listOf(), List.copyOf(runner.heldJobs()));
        }
        output.putInt("ipl", ipl);
        output.putDouble("energy", energyCredit);
        if (!runner.jobs().isEmpty()) {
            output.store("jobs", CraftingJob.CODEC.listOf(), runner.jobs());
        }
        if (!tasks.isEmpty()) {
            output.store("tasks", CraftTask.CODEC.listOf(), tasks);
            output.store("progress", Codec.INT.listOf(), progress);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        String carried = components.get(ModDataComponents.DEVICE_NAME.get());
        if (carried != null) {
            deviceName = carried;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!deviceName.isEmpty()) {
            components.set(ModDataComponents.DEVICE_NAME.get(), deviceName);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("device_name");
    }
}
