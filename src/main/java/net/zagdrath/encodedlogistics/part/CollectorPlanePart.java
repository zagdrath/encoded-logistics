/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.menu.CollectorPlaneMenu;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.network.NetworkNodeHost;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Collector Plane: breaks the block in front of it as an iron pickaxe would - hardness x collectorTicksPerHardness
// ticks, only what an iron pickaxe harvests, never unbreakable blocks, network blocks or blocks holding items - and puts
// the drops into the network, along with any dropped items in that space (every COLLECT_INTERVAL ticks). Its one module
// slot (#encodedlogistics:plane_modules) takes a Filter Module (its filter applies, with the filter options) or a Fuzzy
// Match Module (its filter applies, entries matching loosely); without one it takes everything. A block is broken only
// when all its drops pass and fit in the network.
public class CollectorPlanePart extends PlanePart {
    public static final int COLLECT_INTERVAL = 10;
    private static final ItemStack TOOL = new ItemStack(Items.IRON_PICKAXE);

    private final NonNullList<ItemStack> module = NonNullList.withSize(1, ItemStack.EMPTY);
    private @Nullable BlockState breaking;
    private int progress, timer;

    public CollectorPlanePart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public NonNullList<ItemStack> module() {
        return module;
    }

    public boolean hasFilterModule() {
        return module.getFirst().is(ModItems.FILTER_MODULE.get());
    }

    public boolean hasFuzzyModule() {
        return module.getFirst().is(ModItems.FUZZY_MATCH_MODULE.get());
    }

    // Whether it takes an item.
    public boolean passes(ItemStack stack) {
        return module.getFirst().isEmpty() || filter.test(stack, hasFilterModule(), true, hasFuzzyModule());
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        CollectorPlaneMenu.open(player, this);
        return true;
    }

    @Override
    public List<ItemStack> contents() {
        return module.getFirst().isEmpty() ? List.of() : List.of(module.getFirst().copy());
    }

    // --- Working ---

    @Override
    protected void work(ServerLevel level) {
        NetworkStorage storage = storage(level);
        if (storage == null) {
            stopBreaking(level);
            return;
        }
        if (++timer >= COLLECT_INTERVAL) {
            timer = 0;
            collectItems(level, storage);
        }
        breakBlock(level, storage);
    }

    // Dropped items in the space in front go into the network.
    private void collectItems(ServerLevel level, NetworkStorage storage) {
        boolean collected = false;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(facing()), ItemEntity::isAlive)) {
            ItemStack stack = entity.getItem();
            if (!passes(stack)) {
                continue;
            }
            int stored = (int) storage.insert(ItemKey.of(stack), stack.getCount(), false);
            if (stored > 0) {
                collected = true;
                ItemStack left = stack.copyWithCount(stack.getCount() - stored);
                if (left.isEmpty()) {
                    entity.discard();
                } else {
                    entity.setItem(left);
                }
            }
        }
        if (collected) {
            flash();
        }
    }

    private void breakBlock(ServerLevel level, NetworkStorage storage) {
        BlockPos target = facing();
        BlockState state = level.getBlockState(target);
        if (!canBreak(level, target, state)) {
            stopBreaking(level);
            return;
        }
        if (state != breaking) {
            stopBreaking(level);
            breaking = state;
        }
        int needed = Math.max(1, Mth.ceil(state.getDestroySpeed(level, target) * Config.COLLECTOR_TICKS_PER_HARDNESS.getAsInt()));
        if (progress < needed) {
            progress++;
            level.destroyBlockProgress(breakerId(), target, Math.min(9, progress * 10 / needed));
            if (progress < needed) {
                return;
            }
        }
        FakePlayer player = fakePlayer(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, TOOL.copy());
        List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), player, TOOL);
        // Everything it drops has to pass, and fit; otherwise it waits.
        Map<ItemKey, Long> totals = new HashMap<>();
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                if (!passes(drop)) {
                    return;
                }
                totals.merge(ItemKey.of(drop), (long) drop.getCount(), Long::sum);
            }
        }
        for (Map.Entry<ItemKey, Long> total : totals.entrySet()) {
            if (storage.insert(total.getKey(), total.getValue(), true) < total.getValue()) {
                return;
            }
        }
        if (CommonHooks.fireBlockBreak(level, GameType.SURVIVAL, player, target, state).isCanceled()) {
            stopBreaking(level);
            return;
        }
        level.destroyBlock(target, false, player);
        stopBreaking(level);
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            int stored = (int) storage.insert(ItemKey.of(drop), drop.getCount(), false);
            if (stored < drop.getCount()) {
                Block.popResource(level, target, drop.copyWithCount(drop.getCount() - stored));
            }
        }
        flash();
    }

    private static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        if (state.requiresCorrectToolForDrops() && !TOOL.isCorrectToolForDrops(state)) {
            return false;
        }
        if (state.getBlock() instanceof NetworkNodeBlock) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof NetworkNodeHost || blockEntity instanceof NetworkControllerBlockEntity) {
            return false;
        }
        if (blockEntity instanceof Container container && !container.isEmpty()) {
            return false;
        }
        if (blockEntity != null) {
            ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, pos, null);
            if (items != null) {
                for (int slot = 0; slot < items.size(); slot++) {
                    if (!items.getResource(slot).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void stopBreaking(ServerLevel level) {
        if (progress > 0) {
            level.destroyBlockProgress(breakerId(), facing(), -1);
        }
        progress = 0;
        breaking = null;
    }

    // An id for its crack overlay that no entity has.
    private int breakerId() {
        return -1_000 - Math.floorMod(host.getBlockPos().hashCode() * 6 + side.ordinal(), 1_000_000);
    }

    @Override
    public void removed(ServerLevel level) {
        stopBreaking(level);
    }

    // --- Saving ---

    @Override
    public void load(ValueInput input) {
        super.load(input);
        module.set(0, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty("module"), module);
    }

    @Override
    public void save(ValueOutput output) {
        super.save(output);
        ContainerHelper.saveAllItems(output.child("module"), module);
    }
}
