/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;

// A Collector or Deployer Plane: a flat plate over a whole cable face, working on the space in front of it. Planes of
// one kind side by side on the same face join into one big plane (CableModel). Lit (the flash over its face) for
// ACTIVE_TICKS after each thing it does. A device: partLanes lanes, partDrain FE/t.
public abstract class PlanePart extends CablePart {
    public static final int ACTIVE_TICKS = 12;

    protected final PartFilter filter = new PartFilter();
    private int activeTicks;

    protected PlanePart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public PartFilter filter() {
        return filter;
    }

    public void settingsChanged() {
        changed();
    }

    @Override
    public boolean lit() {
        return activeTicks > 0;
    }

    // It did something: flash.
    protected void flash() {
        boolean was = activeTicks > 0;
        activeTicks = ACTIVE_TICKS;
        if (!was) {
            changed();
        }
    }

    @Override
    public void tick(ServerLevel level) {
        if (activeTicks > 0 && --activeTicks == 0) {
            changed();
        }
        if (isOnline()) {
            work(level);
        }
    }

    // Server, every tick while online.
    protected abstract void work(ServerLevel level);

    // A player standing on the plane looking out of it, for placing and breaking as a player would.
    protected FakePlayer fakePlayer(ServerLevel level) {
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        Vec3 center = Vec3.atCenterOf(host.getBlockPos());
        player.setPos(center.x, center.y - player.getEyeHeight(), center.z);
        player.setYRot(side.getAxis().isHorizontal() ? side.toYRot() : 0);
        player.setXRot(side == Direction.UP ? -90 : side == Direction.DOWN ? 90 : 0);
        player.yHeadRot = player.getYRot();
        return player;
    }

    @Override
    public void load(ValueInput input) {
        filter.load(input);
    }

    @Override
    public void save(ValueOutput output) {
        filter.save(output);
    }
}
