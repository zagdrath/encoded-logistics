/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.menu.DiskDriveMenu;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModSounds;
import net.zagdrath.encodedlogistics.storage.DriveHolder;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// The Disk Drive (HANDOFF 5): one Storage Drive (any tier) served as hot storage exactly as a Drive Bay slot is (same
// capacity, priority, fullness rules), while it has its lane. A drive used on it goes in and spins up (diskSpinUpTicks)
// before the network can read it; 4=Unload on its screen or a sneak-use with an empty hand spins it down
// (diskSpinDownTicks), then it comes out - to the player who asked, else out of its front. Device type DISK (DISK01).
public class DiskDriveBlockEntity extends PeripheralBlockEntity implements DriveHolder, NetworkDevice {
    public static final String TYPE = "DISK";
    // What its screen says it's doing.
    public enum State {
        OFFLINE, NO_PACK, SPIN_UP, SPINNING, SPIN_DOWN
    }

    private boolean online;
    private int spinUp, spinDown;
    private @Nullable UUID ejectTo;

    public DiskDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DISK_DRIVE.get(), pos, state, 1);
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

    // --- Online: its own lane, no Midrange System needed ---

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            showSpinning();
        }
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public boolean listedOnline() {
        return online;
    }

    public State state() {
        if (!online) {
            return State.OFFLINE;
        }
        if (getItem(0).isEmpty()) {
            return State.NO_PACK;
        }
        return spinDown > 0 ? State.SPIN_DOWN : spinUp > 0 ? State.SPIN_UP : State.SPINNING;
    }

    // --- Its pack ---

    public ItemStack pack() {
        return getItem(0);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof StorageDriveItem && getItem(0).isEmpty();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    // A Storage Drive used on it: in, and it spins up.
    @Override
    public boolean insert(ItemStack stack) {
        if (!(stack.getItem() instanceof StorageDriveItem) || !getItem(0).isEmpty()) {
            return false;
        }
        setItem(0, stack.split(1));
        if (level instanceof ServerLevel serverLevel) {
            refresh(serverLevel);
        }
        spinUp = Math.max(1, Config.DISK_SPIN_UP_TICKS.getAsInt());
        spinDown = 0;
        setChanged();
        showSpinning();
        return true;
    }

    // 4=Unload or a sneak-use: it spins down, then the pack comes out to that player.
    @Override
    public boolean ejectLater(Player player) {
        if (getItem(0).isEmpty()) {
            return false;
        }
        if (spinDown <= 0) {
            spinDown = Math.max(1, Config.DISK_SPIN_DOWN_TICKS.getAsInt());
            spinUp = 0;
            ejectTo = player.getUUID();
            setChanged();
            if (level != null) {
                level.playSound(null, worldPosition, ModSounds.DRIVE_SEEK.value(), SoundSource.BLOCKS, 0.6F, 0.7F);
            }
        }
        return true;
    }

    @Override
    protected void tick(ServerLevel level) {
        if (spinDown > 0) {
            if (--spinDown == 0) {
                out(level);
            }
            return;
        }
        if (spinUp > 0 && online && !getItem(0).isEmpty() && --spinUp == 0) {
            showSpinning();
        }
    }

    // The pack comes out: to the player who asked, when they're near, else out of its front.
    private void out(ServerLevel level) {
        ItemStack pack = removeItemNoUpdate(0);
        setChanged();
        showSpinning();
        ControllerStructures.get(level).markTopologyChanged();
        if (pack.isEmpty()) {
            return;
        }
        ServerPlayer player = ejectTo != null ? level.getServer().getPlayerList().getPlayer(ejectTo) : null;
        ejectTo = null;
        if (player != null && player.level() == level && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) < 64) {
            player.getInventory().placeItemBackInInventory(pack, Prediction.SERVER_ONLY);
        } else {
            Block.popResourceFromFace(level, worldPosition, getBlockState().getValue(FootprintBlock.FACING), pack);
        }
        level.playSound(null, worldPosition, ModSounds.DISKETTE_LATCH.value(), SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    // The lamp: on while the pack spins up or turns.
    private void showSpinning() {
        State state = state();
        showActive(state == State.SPIN_UP || state == State.SPINNING);
    }

    // Its id and its fill on the item.
    private void refresh(ServerLevel level) {
        ItemStack stack = getItem(0);
        if (!(stack.getItem() instanceof StorageDriveItem drive)) {
            return;
        }
        UUID id = StorageDriveItem.id(stack);
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(ModDataComponents.DRIVE_ID.get(), id);
        }
        DriveStats stats = DriveStorage.get(level.getServer()).stats(id, drive.getTier());
        if (!stats.equals(stack.get(ModDataComponents.DRIVE_STATS.get()))) {
            stack.set(ModDataComponents.DRIVE_STATS.get(), stats);
        }
    }

    // What's on its pack, most first (5=Display contents).
    public List<Map.Entry<ItemKey, Long>> contents() {
        UUID id = StorageDriveItem.id(getItem(0));
        if (id == null || !(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        List<Map.Entry<ItemKey, Long>> list = new ArrayList<>(DriveStorage.get(serverLevel.getServer()).contents(id).entrySet());
        list.sort(Map.Entry.<ItemKey, Long>comparingByValue(Comparator.reverseOrder()));
        return list;
    }

    // --- DriveHolder: readable once spun up, until it spins down ---

    @Override
    public int driveSlots() {
        return 1;
    }

    @Override
    public @Nullable ItemStack drive(int slot) {
        ItemStack stack = getItem(0);
        return slot == 0 && state() == State.SPINNING && StorageDriveItem.id(stack) != null ? stack : null;
    }

    @Override
    public void driveChanged(int slot) {
        if (level instanceof ServerLevel serverLevel) {
            refresh(serverLevel);
            setChanged();
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DiskDriveMenu(containerId, inventory, this, this, PeripheralMenu.Opening.SERVER);
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        spinUp = input.getIntOr("spin_up", 0);
        spinDown = input.getIntOr("spin_down", 0);
        ejectTo = input.read("eject_to", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("spin_up", spinUp);
        output.putInt("spin_down", spinDown);
        if (ejectTo != null) {
            output.store("eject_to", UUIDUtil.CODEC, ejectTo);
        }
    }
}
