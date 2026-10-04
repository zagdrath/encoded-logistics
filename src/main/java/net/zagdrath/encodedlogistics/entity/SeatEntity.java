/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.blockentity.SwivelChairBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModEntityTypes;

// What a player sits on in a Swivel Chair: an invisible entity at the chair's base, its rider's hips on the cushion
// (SEAT_HEIGHT above the block's bottom). The chair turns with its rider. It goes once nobody's on it or the chair's gone;
// a player getting up stands a block in front of the seat.
public class SeatEntity extends Entity {
    public static final double SEAT_HEIGHT = 9 / 16.0;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static SeatEntity at(Level level, BlockPos chair) {
        SeatEntity seat = new SeatEntity(ModEntityTypes.SEAT.get(), level);
        seat.setPos(chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5);
        return seat;
    }

    public BlockPos chair() {
        return BlockPos.containing(getX(), getY() + 0.1, getZ());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        if (!(level().getBlockEntity(chair()) instanceof SwivelChairBlockEntity chair)) {
            ejectPassengers();
            discard();
            return;
        }
        Entity rider = getFirstPassenger();
        if (rider == null) {
            discard();
            return;
        }
        chair.setYaw(rider.getYRot());
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0, SEAT_HEIGHT, 0);
    }

    // A block in front of the seat (where its sitter faces), or the seat's top if that's blocked.
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 ahead = Vec3.directionFromRotation(0, passenger.getYRot());
        BlockPos front = BlockPos.containing(getX() + ahead.x, getY() + 0.1, getZ() + ahead.z);
        if (level().getBlockState(front).getCollisionShape(level(), front).isEmpty()
                && level().getBlockState(front.above()).getCollisionShape(level(), front.above()).isEmpty()) {
            return new Vec3(front.getX() + 0.5, front.getY(), front.getZ() + 0.5);
        }
        return new Vec3(getX(), getY() + 1.4, getZ());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
