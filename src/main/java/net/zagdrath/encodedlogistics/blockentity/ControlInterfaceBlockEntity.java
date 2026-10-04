/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.block.ControlInterfaceBlock;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// A Control Interface's six channels: per face, the level it gives out (out, 0-15, kept while it's offline and given
// out again when it's back) and the level arriving there (in). Reading a face never sees this block's own output:
// while it measures, it gives out nothing, and redstone dust it powers itself (dust keeps its level) only counts when
// something else drives it higher. Its name (CTLIF01, ...) is given the first time it's on a network (ElclDevices): the
// lowest number free there. A change of input fires *RSCHANGE.
public class ControlInterfaceBlockEntity extends BlockEntity implements NetworkDevice {
    public static final String TYPE = "CTLIF";
    private static final Direction[] FACES = Direction.values();

    private final int[] out = new int[6], in = new int[6];
    private String name = "";
    private boolean online, measuring, inputsDirty = true, ledsDirty = true;

    public ControlInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CONTROL_INTERFACE.get(), pos, state);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        setChanged();
    }

    public boolean isOnline() {
        return online;
    }

    public @Nullable NetworkRef network() {
        return online && level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
    }

    // What the face gives out to the block next to it: its level while online, else nothing.
    public int emitted(Direction face) {
        return online && !measuring ? out[face.ordinal()] : 0;
    }

    public int output(Direction face) {
        return out[face.ordinal()];
    }

    public int input(Direction face) {
        if (inputsDirty) {
            readInputs();
        }
        return in[face.ordinal()];
    }

    // The highest level arriving on any face (RTVRSIN SIDE(*MAX)).
    public int maxInput() {
        int max = 0;
        for (Direction face : FACES) {
            max = Math.max(max, input(face));
        }
        return max;
    }

    // CHGRSOUT: a face's output (null: every face).
    public void setOutput(@Nullable Direction face, int levelValue) {
        int value = Math.clamp(levelValue, 0, 15);
        boolean changed = false;
        for (Direction side : FACES) {
            if ((face == null || face == side) && out[side.ordinal()] != value) {
                out[side.ordinal()] = value;
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            ledsDirty = true;
            notifyNeighbours();
        }
    }

    public void inputsChanged() {
        inputsDirty = true;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online == online) {
            return;
        }
        this.online = online;
        ledsDirty = true;
        inputsDirty = true;
        notifyNeighbours();
    }

    private void notifyNeighbours() {
        if (level != null && !level.isClientSide()) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControlInterfaceBlockEntity ci) {
        if (ci.inputsDirty) {
            ci.readInputs();
        }
        if (ci.ledsDirty) {
            ci.ledsDirty = false;
            ci.updateBlockState();
        }
    }

    // What arrives on each face, its own output left out; a change fires *RSCHANGE (side, new level).
    private void readInputs() {
        inputsDirty = false;
        if (level == null || level.isClientSide()) {
            return;
        }
        int[] before = in.clone();
        measuring = true;
        try {
            for (Direction face : FACES) {
                BlockPos next = worldPosition.relative(face);
                int value = level.getSignal(next, face);
                // Dust this face powers holds that level itself: only more than that comes from elsewhere.
                if (online && value > 0 && value <= out[face.ordinal()] && level.getBlockState(next).is(Blocks.REDSTONE_WIRE)) {
                    value = 0;
                }
                in[face.ordinal()] = value;
            }
        } finally {
            measuring = false;
        }
        if (!Arrays.equals(before, in)) {
            ledsDirty = true;
            NetworkRef network = network();
            if (network != null && level instanceof ServerLevel serverLevel) {
                for (Direction face : FACES) {
                    if (before[face.ordinal()] != in[face.ordinal()]) {
                        ElclEvents.redstoneChanged(serverLevel.getServer(), network, name, face, in[face.ordinal()]);
                    }
                }
            }
        }
    }

    // ONLINE and each face's LED (the higher of in and out), at most once a tick.
    private void updateBlockState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ControlInterfaceBlock)) {
            return;
        }
        BlockState next = state.setValue(ControlInterfaceBlock.ONLINE, online);
        for (Direction face : FACES) {
            int shown = Math.max(in[face.ordinal()], out[face.ordinal()]);
            next = next.setValue(ControlInterfaceBlock.LEDS.get(face), ControlInterfaceBlock.Led.of(shown));
        }
        if (next != state) {
            level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
        }
    }

    // --- Components (its device name goes with the item) ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        String carried = components.get(ModDataComponents.DEVICE_NAME.get());
        if (carried != null) {
            name = carried;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) {
            components.set(ModDataComponents.DEVICE_NAME.get(), name);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("name");
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        name = input.getStringOr("name", "");
        for (Direction face : FACES) {
            out[face.ordinal()] = Math.clamp(input.getIntOr("out_" + face.getSerializedName(), 0), 0, 15);
        }
        inputsDirty = true;
        ledsDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!name.isEmpty()) {
            output.putString("name", name);
        }
        for (Direction face : FACES) {
            if (out[face.ordinal()] != 0) {
                output.putInt("out_" + face.getSerializedName(), out[face.ordinal()]);
            }
        }
    }
}
