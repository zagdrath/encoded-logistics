/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.optical.FiberTrace;
import net.zagdrath.encodedlogistics.optical.Multiplexer;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;

// The Optical Multiplexer's rack form (1U, the optical handoff 2): the block's work (optical.Multiplexer) in a rack. Its
// line is a Fiber Cable leaving the rack at any of its connection points that leads to its partner (the shortest such);
// it takes its power and a lane from the network its rack serves. Fault: paired but the line is down. Its panel is
// the block's (OpticalMuxPanel).
public class OpticalMultiplexerDevice extends RackDevice implements Multiplexer.Host {
    private final Multiplexer multiplexer = new Multiplexer(this);

    public OpticalMultiplexerDevice(RackDeviceType type) {
        super(type);
    }

    public Multiplexer multiplexer() {
        return multiplexer;
    }

    @Override
    public double drain() {
        return Config.MULTIPLEXER_DRAIN.getAsDouble();
    }

    @Override
    public void tick(ServerLevel level) {
        multiplexer.tick(level);
    }

    @Override
    public void onRemoved() {
        if (rack() != null && rack().getLevel() instanceof ServerLevel level) {
            multiplexer.removed(level);
        }
    }

    @Override
    public RackDeviceInfo.Status status() {
        return multiplexer.status();
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return multiplexer.lines();
    }

    // --- Multiplexer.Host ---

    @Override
    public @Nullable ServerLevel serverLevel() {
        return rack() != null && rack().getLevel() instanceof ServerLevel level ? level : null;
    }

    @Override
    public @Nullable GlobalPos where() {
        RackBlockEntity rack = rack();
        return rack != null && rack.getLevel() != null ? GlobalPos.of(rack.getLevel().dimension(), rack.getBlockPos()) : null;
    }

    @Override
    public boolean online() {
        return isOnline();
    }

    // The shortest line from any of the rack's connection points.
    @Override
    public FiberTrace.@Nullable Result trace(ServerLevel level, FiberTrace.Wanted wanted) {
        RackBlockEntity rack = rack();
        if (rack == null) {
            return null;
        }
        FiberTrace.Result best = null;
        for (RackGeometry.Point point : RackGeometry.Point.values()) {
            FiberTrace.Result result = FiberTrace.fromRack(level, rack, point, wanted);
            if (result != null && (best == null || result.length() < best.length())) {
                best = result;
            }
        }
        return best;
    }

    @Override
    public void multiplexerChanged() {
        changed(false);
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        multiplexer.handle(player, rack() != null ? rack().network(this) : null, action, value);
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        multiplexer.writePanel(output, viewer.level().getServer());
    }

    // --- Saving: its pairing and channels go with its item ---

    @Override
    public void saveSettings(ValueOutput output) {
        multiplexer.save(output);
    }

    @Override
    public void loadSettings(ValueInput input) {
        multiplexer.load(input);
    }
}
