/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.StorageDevice;

// The SAN (4U): 24 Storage Drives as network storage (StorageDevice), reached through Optical Transceivers in its four
// rear uplink cages. Without one it's a fault ("Offline - no uplink") and its drives aren't reachable; each one beyond
// the first raises its storage priority by one.
public class SanDevice extends StorageDevice {
    public static final int DRIVES = 24, CAGES = 4;
    private int shownCages;

    public SanDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public int drives() {
        return DRIVES;
    }

    @Override
    protected double baseDrain() {
        return Config.SAN_DRAIN.getAsDouble();
    }

    public int transceivers() {
        int count = 0;
        for (int slot = DRIVES; slot < DRIVES + CAGES; slot++) {
            if (!items().get(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean ready() {
        return isOnline() && transceivers() > 0;
    }

    @Override
    protected int priorityBonus() {
        return Math.max(0, transceivers() - 1);
    }

    @Override
    public RackDeviceInfo.Status status() {
        return isOnline() && transceivers() == 0 ? RackDeviceInfo.Status.FAULT : super.status();
    }

    @Override
    public Component statusText() {
        return status() == RackDeviceInfo.Status.FAULT ? Component.translatable("gui.encodedlogistics.san.no_uplink") : super.statusText();
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>(super.lines(viewer));
        int count = transceivers();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.san.uplink"), count > 0
                ? Component.translatable("hud.encodedlogistics.san.transceivers", count) : Component.translatable("hud.encodedlogistics.san.none")));
        return lines;
    }

    // Client: which rear cages hold a transceiver.
    public boolean hasTransceiver(int cage) {
        return (shownCages >> cage & 1) != 0;
    }

    @Override
    public void writeClient(ValueOutput output) {
        super.writeClient(output);
        int cages = 0;
        for (int i = 0; i < CAGES; i++) {
            if (!items().get(DRIVES + i).isEmpty()) {
                cages |= 1 << i;
            }
        }
        output.putInt("cages", cages);
    }

    @Override
    public void readClient(ValueInput input) {
        super.readClient(input);
        shownCages = input.getIntOr("cages", 0);
    }
}
