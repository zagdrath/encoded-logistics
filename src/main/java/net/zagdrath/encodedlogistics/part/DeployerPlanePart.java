/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.DeployerPlaneMenu;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Deployer Plane: every deployerInterval ticks, takes the first item its 3x3 ghost filter lists that the network
// has (entries in order; an empty filter does nothing) and either places it as a block in the space in front, as a
// player facing out of the plane would (place mode: block items only, and only into a replaceable space), or drops one
// out of its face (drop mode).
public class DeployerPlanePart extends PlanePart {
    private boolean drop;
    private int timer;

    public DeployerPlanePart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public boolean dropMode() {
        return drop;
    }

    public void toggleMode() {
        drop = !drop;
        changed();
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        DeployerPlaneMenu.open(player, this);
        return true;
    }

    @Override
    protected void work(ServerLevel level) {
        if (++timer < Config.DEPLOYER_INTERVAL.getAsInt()) {
            return;
        }
        timer = 0;
        NetworkStorage storage = storage(level);
        if (storage == null || filter.isEmpty()) {
            return;
        }
        if (drop ? dropOne(level, storage) : placeOne(level, storage)) {
            flash();
        }
    }

    // The first stored item matching a filter entry (in entry order) that passes test.
    private @Nullable ItemKey next(NetworkStorage storage, Predicate<ItemKey> test) {
        Map<ItemKey, Long> stored = storage.list();
        for (int index = 0; index < PartFilter.SIZE; index++) {
            if (filter.entries().get(index).isEmpty()) {
                continue;
            }
            for (Map.Entry<ItemKey, Long> entry : stored.entrySet()) {
                if (entry.getValue() > 0 && filter.matches(index, entry.getKey().stack(), false, false) && test.test(entry.getKey())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean placeOne(ServerLevel level, NetworkStorage storage) {
        BlockPos target = facing();
        if (!level.getBlockState(target).canBeReplaced()) {
            return false;
        }
        ItemKey key = next(storage, candidate -> candidate.stack().getItem() instanceof BlockItem);
        if (key == null || !(key.stack().getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        FakePlayer player = fakePlayer(level);
        ItemStack stack = key.toStack(1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), side.getOpposite(), target, false);
        BlockPlaceContext context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit);
        boolean placed = blockItem.place(context).consumesAction() && stack.isEmpty();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (placed) {
            storage.extract(key, 1, false);
        }
        return placed;
    }

    private boolean dropOne(ServerLevel level, NetworkStorage storage) {
        ItemKey key = next(storage, candidate -> true);
        if (key == null || storage.extract(key, 1, false) <= 0) {
            return false;
        }
        Vec3 out = Vec3.atCenterOf(host.getBlockPos()).add(side.getStepX() * 0.75, side.getStepY() * 0.75 - 0.125, side.getStepZ() * 0.75);
        ItemEntity entity = new ItemEntity(level, out.x, out.y, out.z, key.toStack(1));
        entity.setDeltaMovement(side.getStepX() * 0.2, side.getStepY() * 0.2 + (side.getAxis().isHorizontal() ? 0.1 : 0), side.getStepZ() * 0.2);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
        return true;
    }

    // --- Saving ---

    @Override
    public void load(ValueInput input) {
        super.load(input);
        drop = input.getBooleanOr("drop", false);
    }

    @Override
    public void save(ValueOutput output) {
        super.save(output);
        output.putBoolean("drop", drop);
    }
}
