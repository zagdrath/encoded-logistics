/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

// A Control Interface's six channels (RedstoneChannels): per face, the level it gives out (kept while it's offline and
// given out again when it's back) and the level arriving there, its own output never seen. Its name (CTLIF01, ...) is
// given the first time it's on a network (ElclDevices): the lowest number free there. A change of input fires *RSCHANGE.
public class ControlInterfaceBlockEntity extends BlockEntity implements NetworkDevice, RedstoneDevice {
    public static final String TYPE = "CTLIF";
    private static final Direction[] FACES = RedstoneChannels.FACES;

    private final RedstoneChannels channels = new RedstoneChannels();
    private String name = "";
    private boolean online, inputsDirty = true, ledsDirty = true;

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

    @Override
    public boolean isOnline() {
        return online;
    }

    public @Nullable NetworkRef network() {
        return online && level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
    }

    // What the face gives out to the block next to it: its level while online, else nothing.
    public int emitted(Direction face) {
        return channels.emitted(face, online);
    }

    public int output(Direction face) {
        return channels.output(face);
    }

    @Override
    public int input(Direction face) {
        if (inputsDirty) {
            readInputs();
        }
        return channels.input(face);
    }

    @Override
    public int maxInput() {
        if (inputsDirty) {
            readInputs();
        }
        return channels.maxInput();
    }

    // CHGRSOUT: a face's output (null: every face).
    public void setOutput(@Nullable Direction face, int levelValue) {
        if (channels.setOutput(face, levelValue).length > 0) {
            setChanged();
            ledsDirty = true;
            notifyNeighbours();
        }
    }

    @Override
    public void setOutput(@Nullable Direction face, int levelValue, int line) {
        setOutput(face, levelValue);
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
        int[] before = channels.read(level, worldPosition, online);
        if (before != null) {
            ledsDirty = true;
            NetworkRef network = network();
            if (network != null && level instanceof ServerLevel serverLevel) {
                for (Direction face : FACES) {
                    if (before[face.ordinal()] != channels.input(face)) {
                        ElclEvents.redstoneChanged(serverLevel.getServer(), network, name, face, channels.input(face));
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
            int shown = Math.max(channels.input(face), channels.output(face));
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
        channels.load(input);
        inputsDirty = true;
        ledsDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!name.isEmpty()) {
            output.putString("name", name);
        }
        channels.save(output);
    }
}
