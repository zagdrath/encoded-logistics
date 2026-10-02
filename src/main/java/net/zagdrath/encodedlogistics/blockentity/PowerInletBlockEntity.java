/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.PowerInletBlock;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Power Inlet's port: FE pushed in goes straight into its network's energy (ControllerStructures.fill), up to
// inletMaxInput FE per tick. Not on a controller's network, it accepts nothing. With inletBuffer above 0 it also holds
// that much when the network is full and passes it on as soon as there's room.
public class PowerInletBlockEntity extends BlockEntity {
    private static final int WINDOW = 20;

    private final Port port = new Port();
    // This tick's FE taken in, and the buffer; journaled so a cancelled transaction takes them back.
    private int receivedThisTick;
    private int buffer;
    private final int[] window = new int[WINDOW];
    private int windowIndex;
    private int quietTicks = WINDOW;

    public PowerInletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.POWER_INLET.get(), pos, state);
    }

    // The energy capability, on the front face only.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return side != null && side == getBlockState().getValue(PowerInletBlock.FACING) ? port : null;
    }

    // FE per tick over the last second.
    public double averageRate() {
        long total = 0;
        for (int amount : window) {
            total += amount;
        }
        return (double) total / WINDOW;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerInletBlockEntity inlet) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Whatever was held back goes in first.
        if (inlet.buffer > 0) {
            ControllerStructures structures = ControllerStructures.get(serverLevel);
            long network = structures.energyNetworkOf(pos);
            if (network > 0) {
                try (Transaction transaction = Transaction.openRoot()) {
                    int moved = structures.fill(serverLevel, network, inlet.buffer, transaction);
                    transaction.commit();
                    inlet.buffer -= moved;
                }
                inlet.setChanged();
            }
        }
        int received = inlet.receivedThisTick;
        inlet.receivedThisTick = 0;
        inlet.window[inlet.windowIndex] = received;
        inlet.windowIndex = (inlet.windowIndex + 1) % WINDOW;
        inlet.quietTicks = received > 0 ? 0 : inlet.quietTicks + 1;
        boolean powered = inlet.quietTicks < WINDOW;
        if (state.getValue(PowerInletBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(PowerInletBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer = Math.max(0, input.getIntOr("buffer", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (buffer > 0) {
            output.putInt("buffer", buffer);
        }
    }

    private final class Port implements EnergyHandler {
        private final Journal journal = new Journal();

        @Override
        public long getAmountAsLong() {
            return buffer;
        }

        @Override
        public long getCapacityAsLong() {
            return Config.INLET_BUFFER.getAsInt();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (amount <= 0 || !(level instanceof ServerLevel serverLevel)) {
                return 0;
            }
            ControllerStructures structures = ControllerStructures.get(serverLevel);
            long network = structures.energyNetworkOf(worldPosition);
            if (network <= 0) {
                return 0;
            }
            int allowed = Math.min(amount, Math.max(0, Config.INLET_MAX_INPUT.getAsInt() - receivedThisTick));
            if (allowed <= 0) {
                return 0;
            }
            int filled = structures.fill(serverLevel, network, allowed, transaction);
            int held = Math.min(allowed - filled, Math.max(0, Config.INLET_BUFFER.getAsInt() - buffer));
            int taken = filled + held;
            if (taken > 0) {
                journal.updateSnapshots(transaction);
                receivedThisTick += taken;
                buffer += held;
            }
            return taken;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }

    private final class Journal extends SnapshotJournal<int[]> {
        @Override
        protected int[] createSnapshot() {
            return new int[] { receivedThisTick, buffer };
        }

        @Override
        protected void revertToSnapshot(int[] snapshot) {
            receivedThisTick = snapshot[0];
            buffer = snapshot[1];
        }

        @Override
        protected void onRootCommit(int[] originalState) {
            if (buffer != originalState[1]) {
                setChanged();
            }
        }
    }
}
