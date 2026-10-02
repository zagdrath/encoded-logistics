/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// One Network Controller block: which structure it belongs to, and its share of the structure's FE buffer. Each block
// buffers its own energyPerBlock and accepts up to maxReceivePerBlock FE per tick on any face; the structure's buffer is
// the sum, so energy stays put when structures merge or split. ControllerStructures drains it and counts what came in.
public class NetworkControllerBlockEntity extends BlockEntity {
    private long structureId;
    private final Buffer energy = new Buffer();

    public NetworkControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.NETWORK_CONTROLLER.get(), pos, state);
    }

    public long getStructureId() {
        return structureId;
    }

    public void setStructureId(long structureId) {
        if (this.structureId != structureId) {
            this.structureId = structureId;
            setChanged();
        }
    }

    // Insert-only on every face.
    public EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return energy;
    }

    public int getEnergy() {
        return energy.getAmountAsInt();
    }

    public int getCapacity() {
        energy.refreshLimits();
        return energy.getCapacityAsInt();
    }

    // Takes up to amount FE out of this block's buffer for the network; returns what it took.
    public int drain(int amount) {
        int taken = Math.min(amount, energy.getAmountAsInt());
        if (taken > 0) {
            energy.set(energy.getAmountAsInt() - taken);
        }
        return taken;
    }

    // FE received since the last call; starts a new tick's receive allowance.
    public int takeReceived() {
        int received = energy.receivedThisTick;
        energy.receivedThisTick = 0;
        energy.refreshLimits();
        return received;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // A block whose structure the level doesn't know (or that isn't listed in it) gets revalidated.
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures structures = ControllerStructures.get(serverLevel);
            ControllerStructures.Structure structure = structures.get(structureId);
            if (structure == null || !structure.members().contains(worldPosition)) {
                structures.queue(worldPosition);
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).queueRemoved(pos, structureId);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        structureId = input.getLongOr("structure", 0L);
        energy.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("structure", structureId);
        energy.serialize(output);
    }

    private final class Buffer extends SimpleEnergyHandler {
        int receivedThisTick;

        Buffer() {
            super(Config.CONTROLLER_ENERGY_PER_BLOCK.getDefault(), Config.CONTROLLER_MAX_RECEIVE.getDefault(), 0);
        }

        // Limits come from the config, which may not be loaded when the block entity is created.
        void refreshLimits() {
            if (Config.SPEC.isLoaded()) {
                capacity = Config.CONTROLLER_ENERGY_PER_BLOCK.getAsInt();
                maxInsert = Config.CONTROLLER_MAX_RECEIVE.getAsInt();
            }
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return super.insert(Math.min(amount, Math.max(0, maxInsert - receivedThisTick)), transaction);
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            if (energy > previousAmount) {
                receivedThisTick += energy - previousAmount;
            }
            setChanged();
        }
    }
}
