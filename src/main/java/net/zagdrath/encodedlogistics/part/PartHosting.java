/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.network.NetworkPart;

// What cables and part hosts share about their parts: the network sees each part as a device of its own (its lanes and
// drain), using one opens its menu, and taking one off drops it with its contents.
public final class PartHosting {
    private PartHosting() {}

    public static List<NetworkPart> networkParts(CableAttachments attachments) {
        List<NetworkPart> parts = new ArrayList<>();
        for (Direction side : Direction.values()) {
            PartType type = attachments.part(side);
            if (type != null) {
                parts.add(new NetworkPart(type.item(), type.drain()));
            }
        }
        return List.copyOf(parts);
    }

    public static int lanes(CableAttachments attachments) {
        int lanes = 0;
        for (Direction side : Direction.values()) {
            PartType type = attachments.part(side);
            if (type != null) {
                lanes += type.lanes();
            }
        }
        return lanes;
    }

    public static double drain(CableAttachments attachments) {
        double drain = 0;
        for (Direction side : Direction.values()) {
            PartType type = attachments.part(side);
            if (type != null) {
                drain += type.drain();
            }
        }
        return drain;
    }

    // Opens the menu of the part on that side; false when there's no part with a menu there.
    public static boolean open(Level level, BlockPos pos, Direction side, ServerPlayer player) {
        return level.getBlockEntity(pos) instanceof CableBlockEntity host && host.part(side) != null && host.part(side).openMenu(player);
    }

    // Drops what comes off a side (the attachment and its part's contents) out of that face.
    public static void dropFrom(Level level, BlockPos pos, Direction side, CableBlockEntity host) {
        for (ItemStack drop : host.drops(side)) {
            Block.popResourceFromFace(level, pos, side, drop);
        }
    }

    public static int weakSignal(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CableBlockEntity host ? host.weakSignal() : 0;
    }

    // direction: from the block asking toward this one, so the part facing it is on the opposite side.
    public static int strongSignal(BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CableBlockEntity host ? host.strongSignal(direction.getOpposite()) : 0;
    }

    public static boolean hasSensor(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CableBlockEntity host)) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (host.emitsRedstone(side)) {
                return true;
            }
        }
        return false;
    }
}
