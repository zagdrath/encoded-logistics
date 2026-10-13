/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
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
import net.zagdrath.encodedlogistics.block.DirectoryServerBlock;
import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;
import net.zagdrath.encodedlogistics.menu.PanelHost;
import net.zagdrath.encodedlogistics.midrange.MidrangeHud;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.routing.Directory;
import net.zagdrath.encodedlogistics.network.routing.DirectoryPanels;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// The Directory Server block's state: its Directory (what it publishes, the lookups), its ELCL name (DIR01), whether
// it's online.
public class DirectoryServerBlockEntity extends BlockEntity implements NetworkDevice, NamedDevice, MidrangeHud, PanelHost, Directory.Host {
    private final Directory directory = new Directory();
    private boolean online;
    private String deviceName = "";

    public DirectoryServerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DIRECTORY_SERVER.get(), pos, state);
    }

    @Override
    public Directory directory() {
        return directory;
    }

    @Override
    public @Nullable NetworkRef directoryNetwork() {
        return level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
    }

    @Override
    public void directoryChanged() {
        setChanged();
    }

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
        BlockState state = getBlockState();
        if (level != null && state.getBlock() instanceof DirectoryServerBlock && state.getValue(DirectoryServerBlock.ONLINE) != online) {
            level.setBlock(worldPosition, state.setValue(DirectoryServerBlock.ONLINE, online), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public String deviceType() {
        return "DIR";
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
        return new RackDeviceInfo(getBlockState().getBlock().getName(), online ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE,
                Component.translatable(online ? "hud.encodedlogistics.status.online" : "hud.encodedlogistics.status.offline"),
                level instanceof ServerLevel serverLevel ? DirectoryPanels.lines(this, serverLevel.getServer(), false) : java.util.List.of());
    }

    // --- Panel ---

    @Override
    public String panelKind() {
        return "directory";
    }

    @Override
    public Component panelTitle() {
        return Component.translatable("gui.encodedlogistics.directory.title", deviceName);
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        DirectoryPanels.write(this, output, viewer);
    }

    @Override
    public void handlePanelAction(ServerPlayer player, int action, int value, String text) {
        DirectoryPanels.handle(this, player, action, value);
    }

    // --- Saving ---

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        directory.save(output);
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        directory.load(input);
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
