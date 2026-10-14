/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.optical.FiberTrace;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;

// The Optical Amplifier's rack form (1U, the optical handoff 3): patched in, it regenerates the optical lines that pass
// through its rack - a multiplexer's or a transport shelf's line may come in at one of the rack's connection points and
// leave at another, its span count starting again there (FiberTrace). Amplifying while online with Fiber Cable at two
// or more of the rack's connection points (its input and output); with fewer, input loss of signal (fault). Unpowered,
// the rack passes no line through.
public class OpticalAmplifierDevice extends RackDevice {
    private static final int CHECK_INTERVAL = 20;
    private int fibres, timer;

    public OpticalAmplifierDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public double drain() {
        return Config.AMPLIFIER_DRAIN.getAsDouble();
    }

    @Override
    public void tick(ServerLevel level) {
        if (++timer < CHECK_INTERVAL) {
            return;
        }
        timer = 0;
        RackBlockEntity rack = rack();
        int count = 0;
        if (rack != null) {
            for (RackGeometry.Point point : RackGeometry.Point.values()) {
                if (FiberTrace.fiberAt(level, rack, point)) {
                    count++;
                }
            }
        }
        if (count != fibres) {
            fibres = count;
            changed(false);
        }
    }

    @Override
    public RackDeviceInfo.Status status() {
        if (!isOnline()) {
            return RackDeviceInfo.Status.OFFLINE;
        }
        return fibres >= 2 ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.FAULT;
    }

    @Override
    public Component statusText() {
        RackDeviceInfo.Status status = status();
        return status == RackDeviceInfo.Status.OFFLINE ? status.text()
                : Component.translatable(status == RackDeviceInfo.Status.ONLINE ? "hud.encodedlogistics.amplifier.state.on" : "hud.encodedlogistics.amplifier.state.fault");
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return List.of(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.amplifier.patched"),
                Component.translatable("hud.encodedlogistics.amplifier.patched_value", fibres)));
    }

    @Override
    public void writeClient(ValueOutput output) {
        output.putInt("fibres", fibres);
    }

    @Override
    public void readClient(ValueInput input) {
        fibres = input.getIntOr("fibres", 0);
    }
}
