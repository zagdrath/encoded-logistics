/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.item.TapeReelItem;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.menu.TapeDriveMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.TapeRecalls;
import net.zagdrath.encodedlogistics.rack.TapeSource;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModSounds;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// The Tape Drive (HANDOFF 5): one Tape Reel as cold storage in the same tier as the Tape Libraries (TapeSource) - one
// drive, one reel, no picker. A reel used on it is threaded (tapeLoadTicks) before it works. Then, as a library does:
// a waiting recall of an item on its reel is read back into hot storage; else, every tapeArchiveInterval while hot
// storage is fuller than tapeDefaultHotPercent, the oldest item untouched for tapeDefaultAgeHours is written to it. A
// read or write takes tapeDriveBaseTicks + tapeDriveTicksPer4k a 4,096 items, the reels turning (ACTIVE). 7=Rewind
// rewinds it (tapeRewindTicks) to the load point; 4=Unload or a sneak-use with an empty hand rewinds, then the reel
// comes out - to the player who asked, else out of its front. Device type TAPE (TAPE01).
public class TapeDriveBlockEntity extends PeripheralBlockEntity implements NetworkDevice, TapeSource, MidrangeHud {
    public static final String TYPE = "TAPE";
    private static final int ASSIGN_INTERVAL = 10;

    // What its screen says it's doing.
    public enum State {
        OFFLINE, NO_REEL, LOADING, READY, READING, WRITING, REWINDING
    }

    // The reel at work: reading (a recall) or writing (archiving) one item type.
    private record Op(boolean write, StorageKey key, long amount, long touched, int work) {}

    private boolean online;
    private int load, rewind, done, assignTimer, archiveTimer;
    private boolean unloading, atLoadPoint = true;
    private @Nullable Op op;
    private @Nullable UUID ejectTo;
    // When its reel was last read or written (the overworld's clock; -1 never).
    private long lastAccess = -1;
    // Game tests: archive anything, now, however full hot storage is.
    private boolean archiveForTest;

    public TapeDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.TAPE_DRIVE.get(), pos, state, 1);
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

    // --- Online: its own lane ---

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

    // Its popup: what it's doing, its reel and how full that is, when it was last read or written.
    @Override
    public RackDeviceInfo hudInfo() {
        State state = state();
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        if (!reel().isEmpty()) {
            long used = used(), capacity = Config.TAPE_REEL_ITEMS.getAsInt();
            float fraction = capacity <= 0 ? 0 : (float) used / capacity;
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.tape.reel"), Component.literal(TapeReelItem.volume(reel()))));
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.tape.used"),
                    Component.literal(String.format(Locale.ROOT, "%,d / %,d", used, capacity)),
                    new RackDeviceInfo.Bar(fraction, fraction > 0.95F ? RackDeviceInfo.BarStyle.LOW : fraction >= 0.75F ? RackDeviceInfo.BarStyle.WARN
                            : RackDeviceInfo.BarStyle.NORMAL)));
        }
        RackDeviceInfo.Status status = state == State.OFFLINE ? RackDeviceInfo.Status.OFFLINE
                : state == State.NO_REEL ? RackDeviceInfo.Status.WARNING : RackDeviceInfo.Status.ONLINE;
        return new RackDeviceInfo(getBlockState().getBlock().getName(), status,
                Component.translatable("hud.encodedlogistics.tape.state." + state.name().toLowerCase(Locale.ROOT)), lines);
    }

    public State state() {
        if (!online) {
            return State.OFFLINE;
        }
        if (reel().isEmpty()) {
            return State.NO_REEL;
        }
        if (rewind > 0) {
            return State.REWINDING;
        }
        if (load > 0) {
            return State.LOADING;
        }
        return op == null ? State.READY : op.write() ? State.WRITING : State.READING;
    }

    public boolean atLoadPoint() {
        return atLoadPoint;
    }

    public long lastAccess() {
        return lastAccess;
    }

    public void archiveForTest() {
        archiveForTest = true;
        archiveTimer = Config.TAPE_ARCHIVE_INTERVAL.getAsInt();
    }

    // --- Its reel ---

    public ItemStack reel() {
        return getItem(0);
    }

    private @Nullable UUID reelId() {
        return TapeReelItem.id(reel());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof TapeReelItem && reel().isEmpty();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    // A reel used on it: mounted, and threaded before it works.
    @Override
    public boolean insert(ItemStack stack) {
        if (!(stack.getItem() instanceof TapeReelItem) || !reel().isEmpty()) {
            return false;
        }
        ItemStack mounted = stack.split(1);
        if (TapeReelItem.id(mounted) == null) {
            mounted.set(ModDataComponents.DRIVE_ID.get(), UUID.randomUUID());
        }
        setItem(0, mounted);
        load = Math.max(1, Config.TAPE_LOAD_TICKS.getAsInt());
        rewind = 0;
        unloading = false;
        atLoadPoint = true;
        refresh();
        setChanged();
        return true;
    }

    // 4=Unload or a sneak-use: it rewinds, then the reel comes out to that player.
    @Override
    public boolean ejectLater(Player player) {
        if (reel().isEmpty()) {
            return false;
        }
        ejectTo = player.getUUID();
        startRewind(true);
        return true;
    }

    // 7=Rewind (and before unloading): whatever it was doing stops.
    public void startRewind(boolean thenUnload) {
        if (reel().isEmpty()) {
            return;
        }
        abort(level instanceof ServerLevel serverLevel ? recalls(serverLevel.getServer()) : null);
        unloading |= thenUnload;
        if (rewind <= 0) {
            rewind = Math.max(1, Config.TAPE_REWIND_TICKS.getAsInt());
            if (level != null) {
                level.playSound(null, worldPosition, ModSounds.RACK_TAPE_LOAD.value(), SoundSource.BLOCKS, 0.5F, 0.8F);
            }
        }
        setChanged();
    }

    private void out(ServerLevel level) {
        ItemStack reel = removeItemNoUpdate(0);
        unloading = false;
        load = 0;
        setChanged();
        if (reel.isEmpty()) {
            return;
        }
        ServerPlayer player = ejectTo != null ? level.getServer().getPlayerList().getPlayer(ejectTo) : null;
        ejectTo = null;
        if (player != null && player.level() == level && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) < 64) {
            player.getInventory().placeItemBackInInventory(reel, Prediction.SERVER_ONLY);
        } else {
            Block.popResourceFromFace(level, worldPosition, getBlockState().getValue(FootprintBlock.FACING), reel);
        }
        level.playSound(null, worldPosition, ModSounds.DISKETTE_LATCH.value(), SoundSource.BLOCKS, 0.8F, 0.7F);
    }

    // Its fill on the reel.
    private void refresh() {
        UUID id = reelId();
        if (id != null && level instanceof ServerLevel serverLevel) {
            DriveStats stats = DriveStorage.get(serverLevel.getServer()).stats(id, TapeReelItem.CAPACITY);
            if (!stats.equals(reel().get(ModDataComponents.DRIVE_STATS.get()))) {
                reel().set(ModDataComponents.DRIVE_STATS.get(), stats);
            }
        }
    }

    // How many items are on its reel, and what (5=Display contents: most first).
    public long used() {
        UUID id = reelId();
        if (id == null || !(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        return DriveStorage.get(serverLevel.getServer()).contents(id).values().stream().mapToLong(Long::longValue).sum();
    }

    public List<Map.Entry<StorageKey, Long>> contents() {
        UUID id = reelId();
        if (id == null || !(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        List<Map.Entry<StorageKey, Long>> list = new ArrayList<>(DriveStorage.get(serverLevel.getServer()).contents(id).entrySet());
        list.sort(Map.Entry.<StorageKey, Long>comparingByValue(Comparator.reverseOrder()));
        return list;
    }

    // --- Working ---

    private @Nullable TapeRecalls recalls(MinecraftServer server) {
        NetworkRef network = network();
        return network != null ? ControllerStructures.recalls(server, network) : null;
    }

    private @Nullable NetworkStorage storage(MinecraftServer server) {
        return ControllerStructures.storageOf(server, network());
    }

    // Ticks to read or write amount items.
    public static int workTicks(long amount) {
        return Math.max(1, (int) Math.round(Config.TAPE_DRIVE_BASE_TICKS.getAsInt() + Config.TAPE_DRIVE_TICKS_PER_4K.getAsInt() * (amount / 4096.0)));
    }

    @Override
    protected void tick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        TapeRecalls recalls = recalls(server);
        if (!online || recalls == null) {
            if (op != null) {
                abort(recalls);
            }
            return;
        }
        if (rewind > 0) {
            if (--rewind == 0) {
                atLoadPoint = true;
                if (unloading) {
                    out(level);
                }
                setChanged();
            }
            return;
        }
        if (reel().isEmpty() || reelId() == null) {
            return;
        }
        if (load > 0) {
            load--;
            return;
        }
        if (op != null) {
            work(server, recalls);
            return;
        }
        if (++assignTimer >= ASSIGN_INTERVAL) {
            assignTimer = 0;
            op = nextRecall(server, recalls);
        }
        if (op == null && ++archiveTimer >= Config.TAPE_ARCHIVE_INTERVAL.getAsInt()) {
            archiveTimer = 0;
            op = nextArchive(server, recalls);
        }
        if (op != null) {
            done = 0;
            atLoadPoint = false;
            showActive(true);
            setChanged();
        }
    }

    private void work(MinecraftServer server, TapeRecalls recalls) {
        Op working = op;
        done++;
        if (!working.write()) {
            recalls.progress(working.key(), done, working.work());
        }
        if (done < working.work()) {
            return;
        }
        if (working.write()) {
            finishWrite(server, working);
            recalls.stopArchiving(working.key());
        } else {
            recalls.finish(working.key(), !finishRead(server, working));
        }
        op = null;
        lastAccess = level != null ? level.getOverworldClockTime() : -1;
        refresh();
        showActive(false);
        setChanged();
    }

    // What it was doing stops: a recall goes back in the queue, an archive is let go.
    private void abort(@Nullable TapeRecalls recalls) {
        if (op != null && recalls != null) {
            if (op.write()) {
                recalls.stopArchiving(op.key());
            } else {
                recalls.finish(op.key(), false);
                recalls.request(op.key(), op.amount());
            }
        }
        if (op != null) {
            op = null;
            showActive(false);
        }
    }

    private @Nullable Op nextRecall(MinecraftServer server, TapeRecalls recalls) {
        DriveStorage data = DriveStorage.get(server);
        UUID id = reelId();
        for (StorageKey key : recalls.waiting()) {
            long count = data.count(id, key);
            if (count <= 0 || recalls.busy(key)) {
                continue;
            }
            long amount = Math.min(recalls.waitingAmount(key), count);
            int work = workTicks(amount);
            recalls.claim(key, amount, work);
            return new Op(false, key, amount, -1, work);
        }
        return null;
    }

    // The oldest item due for tape with room on the reel, while hot storage is full enough.
    private @Nullable Op nextArchive(MinecraftServer server, TapeRecalls recalls) {
        NetworkStorage storage = storage(server);
        if (storage == null || !archiveForTest && storage.hotFill() * 100 < Config.TAPE_DEFAULT_HOT_PERCENT.getAsInt()) {
            return null;
        }
        DriveStorage data = DriveStorage.get(server);
        UUID id = reelId();
        long now = data.clock(), age = archiveForTest ? 0 : Config.TAPE_DEFAULT_AGE_HOURS.getAsInt() * 72_000L;
        StorageKey oldest = null;
        long oldestAccess = Long.MAX_VALUE;
        for (StorageKey key : storage.driveContents().keySet()) {
            long last = storage.lastAccess(key);
            if (last >= 0 && now - last >= age && last < oldestAccess && !recalls.busy(key) && data.room(id, TapeReelItem.CAPACITY, key) > 0) {
                oldest = key;
                oldestAccess = last;
            }
        }
        if (oldest == null || !recalls.startArchiving(oldest)) {
            return null;
        }
        long amount = Math.min(storage.extractFromDrives(oldest, Long.MAX_VALUE, true), data.room(id, TapeReelItem.CAPACITY, oldest));
        return new Op(true, oldest, amount, oldestAccess, workTicks(amount));
    }

    // Archiving: the item moves from the drives to the reel, unless it was touched while it was being written.
    private void finishWrite(MinecraftServer server, Op op) {
        NetworkStorage storage = storage(server);
        UUID id = reelId();
        if (storage == null || id == null || storage.lastAccess(op.key()) > op.touched()) {
            return;
        }
        DriveStorage data = DriveStorage.get(server);
        long amount = Math.min(Math.min(op.amount(), data.room(id, TapeReelItem.CAPACITY, op.key())), storage.extractFromDrives(op.key(), op.amount(), true));
        long taken = storage.extractFromDrives(op.key(), amount, false);
        long written = data.insert(id, TapeReelItem.CAPACITY, op.key(), taken, false);
        if (written < taken) {
            storage.insert(op.key(), taken - written, false);
        }
    }

    // A recall: the items move from the reel into hot storage, as many as fit; false when not all of them did.
    private boolean finishRead(MinecraftServer server, Op op) {
        NetworkStorage storage = storage(server);
        UUID id = reelId();
        if (storage == null || id == null) {
            return false;
        }
        DriveStorage data = DriveStorage.get(server);
        long wanted = Math.min(op.amount(), data.count(id, op.key()));
        long fits = storage.insert(op.key(), wanted, true);
        long read = data.extract(id, op.key(), fits, false);
        long put = storage.insert(op.key(), read, false);
        if (put < read) {
            data.insert(id, TapeReelItem.CAPACITY, op.key(), read - put, false);
        }
        return put >= wanted;
    }

    // ACTIVE on the master and the top block (its lamps).
    @Override
    protected void showActive(boolean on) {
        super.showActive(on);
        if (level == null) {
            return;
        }
        BlockPos top = worldPosition.above(2);
        BlockState state = level.getBlockState(top);
        if (state.getBlock() instanceof TapeDriveBlock && state.hasProperty(MidrangeStates.ACTIVE) && state.getValue(MidrangeStates.ACTIVE) != on) {
            level.setBlock(top, state.setValue(MidrangeStates.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    // --- TapeSource: its reel, once threaded ---

    @Override
    public List<UUID> tapeIds() {
        UUID id = reelId();
        return id != null && load <= 0 && rewind <= 0 ? List.of(id) : List.of();
    }

    @Override
    public int driveCount() {
        return tapeIds().isEmpty() ? 0 : 1;
    }

    @Override
    public int recallTicks(DriveStorage data, StorageKey key, long amount) {
        UUID id = reelId();
        return id != null && data.count(id, key) > 0 ? load + rewind + workTicks(amount) : -1;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new TapeDriveMenu(containerId, inventory, this, this, PeripheralMenu.Opening.SERVER);
    }

    // --- Saving: what it was doing stops with a reload (a recall goes back in the queue by itself) ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        load = input.getIntOr("load", 0);
        rewind = input.getIntOr("rewind", 0);
        unloading = input.getBooleanOr("unloading", false);
        atLoadPoint = input.getBooleanOr("load_point", true);
        lastAccess = input.getLongOr("last_access", -1);
        ejectTo = input.read("eject_to", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("load", load);
        output.putInt("rewind", rewind);
        output.putBoolean("unloading", unloading);
        output.putBoolean("load_point", atLoadPoint);
        output.putLong("last_access", lastAccess);
        if (ejectTo != null) {
            output.store("eject_to", UUIDUtil.CODEC, ejectTo);
        }
    }
}
