/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.routing.Directory;
import net.zagdrath.encodedlogistics.network.routing.DirectoryPanels;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;

// The Directory Server's rack form (1U, the routing handoff 5): the block's work in a rack - it publishes devices of the
// network it serves, so other networks can reach them as SYSNAME/DEVICE (Directories), and logs the lookups made from
// it. Its panel is the block's (DirectoryPanel).
public class DirectoryServerDevice extends RackDevice implements Directory.Host {
    private final Directory directory = new Directory();

    public DirectoryServerDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public Directory directory() {
        return directory;
    }

    @Override
    public @Nullable NetworkRef directoryNetwork() {
        return rack() != null ? rack().network(this) : null;
    }

    @Override
    public void directoryChanged() {
        changed(false);
    }

    @Override
    public double drain() {
        return Config.DIRECTORY_DRAIN.getAsDouble();
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return DirectoryPanels.lines(this, viewer.level().getServer(), true);
    }

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        DirectoryPanels.handle(this, player, action, value);
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        DirectoryPanels.write(this, output, viewer);
    }

    @Override
    public void saveSettings(ValueOutput output) {
        directory.save(output);
    }

    @Override
    public void loadSettings(ValueInput input) {
        directory.load(input);
    }

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
    }
}
