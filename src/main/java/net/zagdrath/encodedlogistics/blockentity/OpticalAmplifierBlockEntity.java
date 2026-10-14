/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.block.OpticalAmplifierBlock;
import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;
import net.zagdrath.encodedlogistics.midrange.MidrangeHud;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.NetworkGraph;
import net.zagdrath.encodedlogistics.optical.FiberTrace;
import net.zagdrath.encodedlogistics.optical.OpticalState;
import net.zagdrath.encodedlogistics.optical.SpanLoss;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// The Optical Amplifier block's state (OpticalAmplifierBlock): online on its network, or powered by a line through it
// (an online Optical Multiplexer or Transport Shelf whose line it's on says so each time it looks, linePowered), its
// ELCL name (AMP01), and the spans on either side of it as last seen (for its popup). Every CHECK_INTERVAL ticks it
// works out its look; going between amplifying and not changes the spans, so the networks are worked out again.
public class OpticalAmplifierBlockEntity extends BlockEntity implements NetworkDevice, NamedDevice, MidrangeHud {
    private static final int CHECK_INTERVAL = 10;
    // A line's power lasts this long after the line was last looked at.
    private static final int LINE_POWER_TICKS = 60;

    private boolean online;
    private long linePoweredUntil;
    private int lineBefore = -1, lineAfter = -1;
    private String deviceName = "";
    private int timer;

    public OpticalAmplifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.OPTICAL_AMPLIFIER.get(), pos, state);
    }

    // A line through it is powered (by an online multiplexer or shelf), with the spans before and after it on that line.
    public static void linePowered(ServerLevel level, BlockPos pos, int before, int after) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof OpticalAmplifierBlockEntity amplifier) {
            amplifier.linePoweredUntil = level.getGameTime() + LINE_POWER_TICKS;
            amplifier.lineBefore = before;
            amplifier.lineAfter = after;
        }
    }

    public boolean powered() {
        return online || level != null && level.getGameTime() < linePoweredUntil;
    }

    private Direction facing() {
        return getBlockState().getBlock() instanceof OpticalAmplifierBlock ? getBlockState().getValue(OpticalAmplifierBlock.FACING) : Direction.NORTH;
    }

    // Light coming in: a Fiber Cable at its back, joined to it.
    public boolean hasInput() {
        return level != null && FiberTrace.fiberToward(level, worldPosition.relative(facing().getOpposite()), facing());
    }

    public OpticalState state() {
        return getBlockState().getBlock() instanceof OpticalAmplifierBlock ? getBlockState().getValue(OpticalAmplifierBlock.STATE) : OpticalState.OFF;
    }

    public void serverTick(ServerLevel level) {
        if (++timer < CHECK_INTERVAL) {
            return;
        }
        timer = 0;
        OpticalState want = !powered() ? OpticalState.OFF : hasInput() ? OpticalState.ON : OpticalState.FAULT;
        OpticalState now = state();
        if (want != now && getBlockState().getBlock() instanceof OpticalAmplifierBlock) {
            level.setBlock(worldPosition, getBlockState().setValue(OpticalAmplifierBlock.STATE, want), Block.UPDATE_CLIENTS);
            if (want == OpticalState.ON || now == OpticalState.ON) {
                ControllerStructures.get(level).markTopologyChanged();
            }
        }
    }

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
        return "AMP";
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

    // The span on one side of it: what its network's span loss found for the Fiber Cable there, else what the line
    // through it said; null when neither knows.
    private SpanLoss.@org.jspecify.annotations.Nullable Span span(Direction side, int line) {
        if (level == null) {
            return null;
        }
        SpanLoss.Span found = SpanLoss.at(NetworkGraph.at(level.dimension(), worldPosition.relative(side)));
        if (found != null) {
            return found;
        }
        if (line < 0 || level.getGameTime() >= linePoweredUntil) {
            return null;
        }
        int lanes = SpanLoss.lanes(line, SpanLoss.fiberLanes());
        return new SpanLoss.Span(line, lanes, SpanLoss.fiberLanes() - lanes);
    }

    @Override
    public RackDeviceInfo hudInfo() {
        OpticalState state = state();
        RackDeviceInfo.Status status = switch (state) {
            case ON -> RackDeviceInfo.Status.ONLINE;
            case FAULT -> RackDeviceInfo.Status.FAULT;
            case OFF -> RackDeviceInfo.Status.OFFLINE;
        };
        Component text = Component.translatable("hud.encodedlogistics.amplifier.state." + state.getSerializedName());
        SpanLoss.Span before = span(facing().getOpposite(), lineBefore), after = span(facing(), lineAfter);
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.amplifier.input"),
                Component.literal(before != null && state != OpticalState.FAULT ? Integer.toString(before.lanes()) : "—")));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.amplifier.output"),
                Component.literal(state == OpticalState.ON ? Integer.toString(SpanLoss.fiberLanes()) : before != null ? Integer.toString(before.lanes()) : "—")));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.amplifier.span_before"),
                Component.literal(before != null ? before.length() + " m" : "—")));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.amplifier.span_after"),
                Component.literal(after != null ? after.length() + " m" : "—")));
        return new RackDeviceInfo(getBlockState().getBlock().getName(), status, text, lines);
    }

    // --- Saving ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
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
