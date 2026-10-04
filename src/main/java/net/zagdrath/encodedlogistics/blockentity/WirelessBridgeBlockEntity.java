/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.WirelessBridgeBlock;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.wireless.Wireless;
import net.zagdrath.encodedlogistics.wireless.WirelessClient;
import net.zagdrath.encodedlogistics.wireless.WirelessDevice;
import net.zagdrath.encodedlogistics.wireless.WirelessLink;
import net.zagdrath.encodedlogistics.wireless.WirelessState;

// A Wireless Bridge: its link to a Wireless Controller, the name scripts know it by (WBRIDGE01), and what its block shows -
// checked every CHECK_INTERVAL ticks (Wireless.check: a controller that no longer lists it unlinks it).
public class WirelessBridgeBlockEntity extends BlockEntity implements WirelessClient, WirelessDevice {
    private static final int CHECK_INTERVAL = 10;

    private @Nullable WirelessLink link;
    private String deviceName = "";
    private int timer;

    public WirelessBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WIRELESS_BRIDGE.get(), pos, state);
    }

    @Override
    public Kind wirelessKind() {
        return Kind.BRIDGE;
    }

    @Override
    public @Nullable WirelessLink link() {
        return link;
    }

    @Override
    public void setLink(@Nullable WirelessLink link) {
        if (java.util.Objects.equals(this.link, link)) {
            return;
        }
        this.link = link;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
            updateState(serverLevel);
        }
    }

    @Override
    public GlobalPos self() {
        return GlobalPos.of(level.dimension(), worldPosition);
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
            Wireless.check(level.getServer(), this);
            updateState(level);
        }
    }

    // Lanes crossing to the controller's rack, as last solved.
    public int lanesUsed(MinecraftServer server) {
        return link != null ? ControllerStructures.remoteUsage(server, NodePos.of(self()), link.rackNode()) : 0;
    }

    private void updateState(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof WirelessBridgeBlock)) {
            return;
        }
        Wireless.Problem problem = Wireless.problem(level.getServer(), this);
        WirelessState shown = problem.state();
        if (problem == Wireless.Problem.NONE) {
            shown = !ControllerStructures.isOnline(level.getServer(), link.network()) ? WirelessState.OFF
                    : lanesUsed(level.getServer()) > 0 ? WirelessState.ACTIVE : WirelessState.ONLINE;
        }
        if (state.getValue(WirelessBridgeBlock.STATE) != shown) {
            level.setBlock(worldPosition, state.setValue(WirelessBridgeBlock.STATE, shown), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public String controllerName(MinecraftServer server) {
        WirelessControllerDevice controller = Wireless.controller(server, link);
        return controller != null ? Wireless.name(controller) : "";
    }

    @Override
    public RackDeviceInfo describe(MinecraftServer server) {
        Wireless.Problem problem = Wireless.problem(server, this);
        int lanes = lanesUsed(server), capacity = Config.WIRELESS_BRIDGE_LANES.getAsInt();
        Component text = problem != Wireless.Problem.NONE ? problem.text()
                : Component.translatable(lanes > 0 ? "hud.encodedlogistics.wireless.linked_active" : "hud.encodedlogistics.wireless.linked_idle");
        String controller = controllerName(server);
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.controller"),
                link == null ? Component.translatable("hud.encodedlogistics.wireless.use_card")
                        : controller.isEmpty() ? Component.translatable("hud.encodedlogistics.wireless.none") : Component.literal(controller)));
        int behind = problem == Wireless.Problem.NONE ? ControllerStructures.devicesBehind(server, NodePos.of(self())) : 0;
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.devices"),
                behind > 0 ? Component.translatable("hud.encodedlogistics.wireless.behind", behind) : Component.literal("0")));
        if (link != null) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.lanes"), Component.literal(lanes + " / " + capacity),
                    new RackDeviceInfo.Bar((float) lanes / capacity, lanes >= capacity ? RackDeviceInfo.BarStyle.WARN : RackDeviceInfo.BarStyle.NORMAL)));
        }
        return new RackDeviceInfo(getBlockState().getBlock().getName(), problem.status(), text, lines);
    }

    // --- Loading and removal ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && link != null) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel serverLevel && link != null) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            Wireless.removed(serverLevel.getServer(), this);
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        link = input.read("link", WirelessLink.CODEC).orElse(null);
        deviceName = input.getStringOr("device_name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (link != null) {
            output.store("link", WirelessLink.CODEC, link);
        }
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
    }
}
