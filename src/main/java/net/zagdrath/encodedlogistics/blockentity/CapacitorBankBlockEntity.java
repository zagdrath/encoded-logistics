/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.EnergyCell;
import net.zagdrath.encodedlogistics.block.CapacitorBankBlock;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// A Capacitor Bank's buffer: capacitorCapacity FE, at most capacitorMaxTransfer FE in and out per tick (network and
// direct alike). On a controller's network it's part of the energy pool (ControllerStructures fills controllers, then
// banks, and drains banks first). With capacitorDirectIO, every face also takes and gives FE directly. Keeps 20-tick
// averages of what went in and out for its screen, and shows its fill (0-4) in the blockstate, at most once a second.
public class CapacitorBankBlockEntity extends BlockEntity implements EnergyCell {
    private static final int WINDOW = 20;
    private static final int FILL_INTERVAL = 20;

    private final Buffer energy = new Buffer();
    private final int[] inputs = new int[WINDOW], outputs = new int[WINDOW];
    private int windowIndex;
    private int lastInput;
    private int ticksUntilFill;
    private int comparator = -1;

    public CapacitorBankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CAPACITOR_BANK.get(), pos, state);
    }

    // The capability: every face when direct IO is on; otherwise only the side-less view (for Jade and the like).
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return side == null || Config.CAPACITOR_DIRECT_IO.getAsBoolean() ? energy : null;
    }

    @Override
    public long getStored() {
        return energy.getAmountAsLong();
    }

    @Override
    public long getCapacity() {
        energy.refreshLimits();
        return energy.getCapacityAsLong();
    }

    // Network side: FE in, as far as this tick's allowance goes.
    @Override
    public int fill(int amount, TransactionContext transaction) {
        return energy.insert(amount, transaction);
    }

    // Network side: FE out to cover the network's drain, as far as this tick's allowance goes; returns what it gave.
    @Override
    public int drain(int amount) {
        energy.refreshLimits();
        int taken = Math.min(amount, Math.min(energy.getAmountAsInt(), Math.max(0, energy.maxExtract() - energy.extractedThisTick)));
        if (taken > 0) {
            energy.set(energy.getAmountAsInt() - taken);
        }
        return taken;
    }

    // FE that came in during the last tick, for the network's Received figure.
    public int lastInput() {
        return lastInput;
    }

    public double averageInput() {
        return average(inputs);
    }

    public double averageOutput() {
        return average(outputs);
    }

    private static double average(int[] window) {
        long total = 0;
        for (int amount : window) {
            total += amount;
        }
        return (double) total / WINDOW;
    }

    // 0 when empty, otherwise ceil(4 * stored / capacity): 4 above 75%.
    public int fillLevel() {
        long capacity = getCapacity(), stored = getStored();
        return stored <= 0 || capacity <= 0 ? 0 : (int) Math.min(4, (stored * 4 + capacity - 1) / capacity);
    }

    public int comparatorSignal() {
        long capacity = getCapacity(), stored = getStored();
        return stored <= 0 || capacity <= 0 ? 0 : 1 + (int) (stored * 14 / capacity);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CapacitorBankBlockEntity bank) {
        bank.energy.refreshLimits();
        bank.lastInput = bank.energy.insertedThisTick;
        bank.inputs[bank.windowIndex] = bank.energy.insertedThisTick;
        bank.outputs[bank.windowIndex] = bank.energy.extractedThisTick;
        bank.windowIndex = (bank.windowIndex + 1) % WINDOW;
        bank.energy.insertedThisTick = 0;
        bank.energy.extractedThisTick = 0;

        if (--bank.ticksUntilFill <= 0) {
            bank.ticksUntilFill = FILL_INTERVAL;
            int fill = bank.fillLevel();
            if (state.getValue(CapacitorBankBlock.FILL) != fill) {
                level.setBlock(pos, state.setValue(CapacitorBankBlock.FILL, fill), Block.UPDATE_CLIENTS);
            }
        }
        int signal = bank.comparatorSignal();
        if (signal != bank.comparator) {
            bank.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
    }

    private final class Buffer extends SimpleEnergyHandler {
        int insertedThisTick, extractedThisTick;

        Buffer() {
            super(Config.CAPACITOR_CAPACITY.getDefault(), Config.CAPACITOR_MAX_TRANSFER.getDefault(), Config.CAPACITOR_MAX_TRANSFER.getDefault());
        }

        // Limits come from the config, which may not be loaded when the block entity is created.
        void refreshLimits() {
            if (Config.SPEC.isLoaded()) {
                capacity = Config.CAPACITOR_CAPACITY.getAsInt();
                maxInsert = Config.CAPACITOR_MAX_TRANSFER.getAsInt();
                maxExtract = maxInsert;
            }
        }

        int maxExtract() {
            return maxExtract;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            refreshLimits();
            return super.insert(Math.min(amount, Math.max(0, maxInsert - insertedThisTick)), transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            refreshLimits();
            return super.extract(Math.min(amount, Math.max(0, maxExtract - extractedThisTick)), transaction);
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            if (energy > previousAmount) {
                insertedThisTick += energy - previousAmount;
            } else {
                extractedThisTick += previousAmount - energy;
            }
            setChanged();
        }
    }
}
