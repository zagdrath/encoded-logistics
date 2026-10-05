/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.ThresholdSensorMenu;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Threshold Sensor: emits redstone while the network's count of its item is above, below or equal to its
// threshold - a strong signal (15) into the block its face is against and a weak one round it, like a lever. Checked
// every CHECK ticks; off while the network is offline or no item is set. Its lamp is lit while it emits.
public class ThresholdSensorPart extends CablePart {
    public static final int ABOVE = 0, BELOW = 1, EQUAL = 2;
    private static final int CHECK = 4;

    private ItemStack item = ItemStack.EMPTY;
    private int threshold, mode = ABOVE;
    private boolean emitting;
    private int timer;

    public ThresholdSensorPart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public ItemStack item() {
        return item;
    }

    public void setItem(ItemStack stack) {
        item = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        changed();
    }

    public int threshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = Math.max(0, threshold);
        changed();
    }

    public int mode() {
        return mode;
    }

    public void cycleMode() {
        mode = (mode + 1) % 3;
        changed();
    }

    public boolean emitting() {
        return emitting;
    }

    @Override
    public boolean lit() {
        return emitting;
    }

    @Override
    public int signal() {
        return emitting ? 15 : 0;
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        ThresholdSensorMenu.open(player, this);
        return true;
    }

    @Override
    public void tick(ServerLevel level) {
        if (++timer < CHECK) {
            return;
        }
        timer = 0;
        boolean now = false;
        NetworkStorage storage = isOnline() && !item.isEmpty() ? storage(level) : null;
        if (storage != null) {
            long count = storage.count(StorageKey.entry(item));
            now = switch (mode) {
                case BELOW -> count < threshold;
                case EQUAL -> count == threshold;
                default -> count > threshold;
            };
        }
        if (now != emitting) {
            emitting = now;
            changed();
            host.signalChanged(side);
        }
    }

    @Override
    public void load(ValueInput input) {
        item = input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        threshold = Math.max(0, input.getIntOr("threshold", 0));
        mode = Mth.clamp(input.getIntOr("mode", ABOVE), ABOVE, EQUAL);
        emitting = input.getBooleanOr("emitting", false);
    }

    @Override
    public void save(ValueOutput output) {
        if (!item.isEmpty()) {
            output.store("item", ItemStack.CODEC, item);
        }
        output.putInt("threshold", threshold);
        output.putInt("mode", mode);
        output.putBoolean("emitting", emitting);
    }
}
