/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.List;

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
import net.zagdrath.encodedlogistics.multiblock.ControllerBuffer;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// One Network Controller block: which structure it belongs to, and its share of the structure's FE buffer. Each block
// buffers its own energyPerBlock and takes up to maxReceivePerBlock FE per tick; the structure's buffer is the sum, so
// energy stays put when structures merge or split. The energy capability on every face is a view of the whole
// structure (StructureEnergy): FE in fills this block, then spills over into the others. ControllerStructures drains it
// and counts what came in.
public class NetworkControllerBlockEntity extends BlockEntity implements ControllerBuffer {
    private long structureId;
    private final Buffer energy = new Buffer();
    private final StructureEnergy structureEnergy = new StructureEnergy();

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

    // Insert-only on every face, for the whole structure.
    public EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return structureEnergy;
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

    // Takes up to amount FE out of this block's buffer for the network; returns what it took.
    @Override
    public int drain(int amount) {
        int taken = Math.min(amount, energy.getAmountAsInt());
        if (taken > 0) {
            energy.set(energy.getAmountAsInt() - taken);
        }
        return taken;
    }

    // Network side (a Power Inlet's FE): fills this block's buffer past the per-tick limit on its faces. It counts toward
    // what the structure received, and so toward this tick's allowance on its faces too.
    @Override
    public int fill(int amount, TransactionContext transaction) {
        return energy.fill(amount, transaction);
    }

    // FE received since the last call; starts a new tick's receive allowance.
    @Override
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

    // This block first, then the rest of its structure (loaded blocks only). Just this block when the level doesn't know
    // its structure (client side, or before the structure is validated).
    private List<NetworkControllerBlockEntity> structureBlocks() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return List.of(this);
        }
        ControllerStructures.Structure structure = ControllerStructures.get(serverLevel).get(structureId);
        if (structure == null) {
            return List.of(this);
        }
        List<NetworkControllerBlockEntity> blocks = new ArrayList<>(structure.members().size());
        blocks.add(this);
        for (BlockPos pos : structure.members()) {
            if (!pos.equals(worldPosition) && serverLevel.isLoaded(pos)
                    && serverLevel.getBlockEntity(pos) instanceof NetworkControllerBlockEntity other && other.structureId == structureId) {
                blocks.add(other);
            }
        }
        return blocks;
    }

    private final class StructureEnergy implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            long amount = 0;
            for (NetworkControllerBlockEntity block : structureBlocks()) {
                amount += block.energy.getAmountAsLong();
            }
            return amount;
        }

        @Override
        public long getCapacityAsLong() {
            long capacity = 0;
            for (NetworkControllerBlockEntity block : structureBlocks()) {
                block.energy.refreshLimits();
                capacity += block.energy.getCapacityAsLong();
            }
            return capacity;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            int left = amount;
            for (NetworkControllerBlockEntity block : structureBlocks()) {
                if (left <= 0) {
                    break;
                }
                left -= block.energy.insert(left, transaction);
            }
            return amount - left;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
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
        protected void onEnergyChanged(int previousAmount) {
            if (energy > previousAmount) {
                receivedThisTick += energy - previousAmount;
            }
            setChanged();
        }
    }
}
