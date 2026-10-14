/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.block.OpticalMultiplexerBlock;
import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;
import net.zagdrath.encodedlogistics.menu.PanelHost;
import net.zagdrath.encodedlogistics.midrange.MidrangeHud;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.optical.FiberTrace;
import net.zagdrath.encodedlogistics.optical.Multiplexer;
import net.zagdrath.encodedlogistics.optical.OpticalState;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// The Optical Multiplexer block's state (OpticalMultiplexerBlock): its Multiplexer, its ELCL name (MUX01), whether it's
// online. Its line is the Fiber Cable at its front.
public class OpticalMultiplexerBlockEntity extends BlockEntity implements NetworkDevice, NamedDevice, MidrangeHud, PanelHost, Multiplexer.Host {
    private final Multiplexer multiplexer = new Multiplexer(this);
    private boolean online;
    private String deviceName = "";

    public OpticalMultiplexerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.OPTICAL_MULTIPLEXER.get(), pos, state);
    }

    public Multiplexer multiplexer() {
        return multiplexer;
    }

    private Direction facing() {
        return getBlockState().getBlock() instanceof OpticalMultiplexerBlock ? getBlockState().getValue(OpticalMultiplexerBlock.FACING) : Direction.NORTH;
    }

    public void serverTick(ServerLevel level) {
        multiplexer.tick(level);
        OpticalState want = !online ? OpticalState.OFF : multiplexer.fault() ? OpticalState.FAULT : OpticalState.ON;
        BlockState state = getBlockState();
        if (state.getBlock() instanceof OpticalMultiplexerBlock && state.getValue(OpticalMultiplexerBlock.STATE) != want) {
            level.setBlock(worldPosition, state.setValue(OpticalMultiplexerBlock.STATE, want), Block.UPDATE_CLIENTS);
        }
    }

    // --- Multiplexer.Host ---

    @Override
    public @Nullable ServerLevel serverLevel() {
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    @Override
    public @Nullable GlobalPos where() {
        return level != null ? GlobalPos.of(level.dimension(), worldPosition) : null;
    }

    @Override
    public boolean online() {
        return online;
    }

    @Override
    public FiberTrace.@Nullable Result trace(ServerLevel level, FiberTrace.Wanted wanted) {
        return FiberTrace.fromPort(level, worldPosition, facing(), wanted);
    }

    @Override
    public void multiplexerChanged() {
        setChanged();
    }

    // --- Network ---

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public String deviceType() {
        return "MUX";
    }

    @Override
    public String deviceName() {
        return deviceName;
    }

    @Override
    public void setDeviceName(String name) {
        if (!deviceName.equals(name)) {
            deviceName = name;
            setChanged();
        }
    }

    @Override
    public RackDeviceInfo hudInfo() {
        RackDeviceInfo.Status status = multiplexer.status();
        return new RackDeviceInfo(getBlockState().getBlock().getName(), status, status.text(), multiplexer.lines());
    }

    // --- Panel ---

    @Override
    public String panelKind() {
        return "optical_mux";
    }

    @Override
    public Component panelTitle() {
        return Component.translatable("gui.encodedlogistics.mux.title", deviceName);
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        multiplexer.writePanel(output, viewer.level().getServer());
    }

    @Override
    public void handlePanelAction(ServerPlayer player, int action, int value, String text) {
        multiplexer.handle(player, level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null, action, value);
    }

    // --- Loading and removal ---

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel serverLevel) {
            multiplexer.removed(serverLevel);
        }
    }

    // Broken: its partner unpairs, and its channels go.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            multiplexer.unpair(serverLevel.getServer());
            multiplexer.removed(serverLevel);
        }
    }

    // --- Saving ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        multiplexer.save(output);
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        multiplexer.load(input);
        deviceName = input.getStringOr("device_name", "");
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        String carried = components.get(ModDataComponents.DEVICE_NAME.get());
        if (carried != null) {
            deviceName = carried;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!deviceName.isEmpty()) {
            components.set(ModDataComponents.DEVICE_NAME.get(), deviceName);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("device_name");
    }
}
