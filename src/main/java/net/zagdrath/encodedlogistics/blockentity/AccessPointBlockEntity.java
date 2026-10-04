/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.AccessPointBlock;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.wireless.Wireless;
import net.zagdrath.encodedlogistics.wireless.WirelessDevice;
import net.zagdrath.encodedlogistics.wireless.WirelessState;

// An Access Point: whether it's online (its lane on a running network), the name scripts know it by (AP01), and what its
// block shows - checked every CHECK_INTERVAL ticks. The clients it carries are its share of its controller's admitted
// ones, the online Access Points filling up in turn.
public class AccessPointBlockEntity extends BlockEntity implements NetworkDevice, WirelessDevice {
    private static final int CHECK_INTERVAL = 20;

    private boolean online;
    private String deviceName = "";
    private int timer;

    public AccessPointBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ACCESS_POINT.get(), pos, state);
    }

    public boolean isOnline() {
        return online;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            if (level instanceof ServerLevel serverLevel) {
                updateState(serverLevel);
            }
        }
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

    public void serverTick(ServerLevel level) {
        if (++timer >= CHECK_INTERVAL) {
            timer = 0;
            updateState(level);
        }
    }

    private NetworkRef network(ServerLevel level) {
        return ControllerStructures.networkOf(level, worldPosition);
    }

    private void updateState(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AccessPointBlock)) {
            return;
        }
        WirelessState shown = !online ? WirelessState.OFF : Wireless.hasController(level.getServer(), network(level)) ? WirelessState.ONLINE
                : WirelessState.LINKING;
        if (state.getValue(AccessPointBlock.STATE) != shown) {
            level.setBlock(worldPosition, state.setValue(AccessPointBlock.STATE, shown), Block.UPDATE_CLIENTS);
        }
    }

    // How many of its controller's clients it carries: the online Access Points take them in turn, wirelessApClients each.
    public int clients(MinecraftServer server) {
        if (!online || !(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        NetworkRef network = network(serverLevel);
        WirelessControllerDevice controller = Wireless.firstController(server, network);
        if (controller == null) {
            return 0;
        }
        int perAp = Config.WIRELESS_AP_CLIENTS.getAsInt(), left = controller.admittedCount();
        for (AccessPointBlockEntity ap : ControllerStructures.onNetwork(server, network, AccessPointBlockEntity.class, true)) {
            int carried = Math.min(perAp, Math.max(0, left));
            if (ap == this) {
                return carried;
            }
            left -= carried;
        }
        return 0;
    }

    @Override
    public String controllerName(MinecraftServer server) {
        WirelessControllerDevice controller = level instanceof ServerLevel serverLevel ? Wireless.firstController(server, network(serverLevel)) : null;
        return controller != null ? Wireless.name(controller) : "";
    }

    // What it's cabled to: the first network block beside it (not on its puck's face).
    private Component uplink() {
        if (level == null) {
            return Component.literal("-");
        }
        Direction puck = getBlockState().getValue(AccessPointBlock.FACING);
        for (Direction side : Direction.values()) {
            BlockState next = level.getBlockState(worldPosition.relative(side));
            if (side != puck && next.getBlock() instanceof NetworkNodeBlock) {
                return next.getBlock().getName();
            }
        }
        return Component.translatable("hud.encodedlogistics.wireless.none");
    }

    @Override
    public RackDeviceInfo describe(MinecraftServer server) {
        int perAp = Config.WIRELESS_AP_CLIENTS.getAsInt(), clients = clients(server);
        String controller = controllerName(server);
        RackDeviceInfo.Status status;
        Component text;
        if (!online) {
            status = RackDeviceInfo.Status.OFFLINE;
            text = Component.translatable("hud.encodedlogistics.wireless.no_uplink");
        } else if (controller.isEmpty()) {
            status = RackDeviceInfo.Status.WARNING;
            text = Wireless.Problem.NO_CONTROLLER.text();
        } else {
            status = RackDeviceInfo.Status.ONLINE;
            text = status.text();
        }
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.clients"), Component.literal(clients + " / " + perAp),
                new RackDeviceInfo.Bar((float) clients / perAp, clients >= perAp ? RackDeviceInfo.BarStyle.WARN : RackDeviceInfo.BarStyle.NORMAL)));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.controller"),
                controller.isEmpty() ? Component.translatable("hud.encodedlogistics.wireless.none") : Component.literal(controller)));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.uplink"), uplink()));
        return new RackDeviceInfo(getBlockState().getBlock().getName(), status, text, lines);
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        deviceName = input.getStringOr("device_name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
    }
}
