/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;

// An L2 Switch (24 or 48 port, 1U): rack-local lanes. The other devices in its rack take their lanes from the switches
// (in unit order, until the pool's full) instead of from the network, which only carries the switches' uplinks (one lane
// each); devices beyond the pool use their own network lanes as before. A pooled device can be put on another segment
// the rack knows (a network beyond a Segment Isolator, linked with a Link Card), and then serves that network.
public class SwitchDevice extends RackDevice implements RackDevice.LanePool {
    public static final int ACTION_CYCLE_SEGMENT = 100;

    public SwitchDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public LanePool lanePool() {
        return this;
    }

    @Override
    public int capacity() {
        return type() == RackDeviceType.L2_SWITCH_24 ? Config.L2_SWITCH_24_LANES.getAsInt() : Config.L2_SWITCH_48_LANES.getAsInt();
    }

    @Override
    public int uplinkCost() {
        return 1;
    }

    @Override
    public int laneCost() {
        return uplinkCost();
    }

    @Override
    public double drain() {
        return Config.SWITCH_DRAIN.getAsDouble();
    }

    protected Component uplink() {
        return Component.translatable(isOnline() ? "gui.encodedlogistics.switch.uplink.linked" : "gui.encodedlogistics.switch.uplink.none");
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        RackBlockEntity rack = rack();
        RackBlockEntity.Lanes lanes = rack != null ? rack.lanes() : RackBlockEntity.Lanes.NONE;
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.switch.lanes"),
                        Component.literal(lanes.poolUsed() + " / " + lanes.poolCapacity()),
                        new RackDeviceInfo.Bar(lanes.poolCapacity() == 0 ? 0 : (float) lanes.poolUsed() / lanes.poolCapacity(), RackDeviceInfo.BarStyle.NORMAL)),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.switch.devices"), Component.literal(Integer.toString(lanes.pooled()))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.switch.uplink"), uplink()));
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (action == ACTION_CYCLE_SEGMENT && rack() != null) {
            rack().cycleSegment(value, text.equals("back") ? -1 : 1);
        }
    }

    // The rack's devices with their lanes and segments, the pool and the segments known.
    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        RackBlockEntity rack = rack();
        if (rack == null) {
            return;
        }
        RackBlockEntity.Lanes lanes = rack.lanes();
        output.putInt("pool_used", lanes.poolUsed());
        output.putInt("pool_capacity", lanes.poolCapacity());
        output.putBoolean("uplink", isOnline());
        ValueOutput.ValueOutputList list = output.childrenList("devices");
        for (RackDevice device : rack.devices()) {
            if (device.lanePool() != null) {
                continue;
            }
            ValueOutput child = list.addChild();
            child.putInt("u", device.u());
            child.putString("type", device.type().id().toString());
            child.putBoolean("pooled", lanes.isPooled(device));
            child.putInt("lanes", device.laneCost());
            child.putInt("segment", device.segment());
        }
        ValueOutput.ValueOutputList segments = output.childrenList("segments");
        for (int i = 0; i < rack.segmentCount(); i++) {
            segments.addChild().putString("name", rack.segmentName(i).getString());
        }
    }
}
