/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.block.WirelessPortBlock;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.part.CablePart;
import net.zagdrath.encodedlogistics.part.PortPart;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.wireless.Wireless;
import net.zagdrath.encodedlogistics.wireless.WirelessClient;
import net.zagdrath.encodedlogistics.wireless.WirelessDevice;
import net.zagdrath.encodedlogistics.wireless.WirelessLink;
import net.zagdrath.encodedlogistics.wireless.WirelessState;

// A Wireless Ingress or Egress Port: a part host (CableBlockEntity) with the port's part on the side toward its
// inventory, so it moves items, filters, takes modules and opens its screen exactly as a cabled port does; plus its link
// to a Wireless Controller, checked every CHECK_INTERVAL ticks with what its block shows. Clients get the controller's
// name and what's wrong (Wireless.Problem) with its update, for the port screen's link light. It drops its modules;
// the block drops itself.
public class WirelessPortBlockEntity extends CableBlockEntity implements WirelessClient, WirelessDevice {
    private static final int CHECK_INTERVAL = 10;

    private @Nullable WirelessLink link;
    private int timer;
    // As last sent to clients (and, on the client, as received).
    private String shownController = "";
    private Wireless.Problem shownProblem = Wireless.Problem.NOT_LINKED;

    public WirelessPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WIRELESS_PORT.get(), pos, state);
    }

    private Direction facing() {
        return getBlockState().getValue(WirelessPortBlock.FACING);
    }

    private boolean ingress() {
        return getBlockState().getBlock() instanceof WirelessPortBlock block && block.ingress();
    }

    // The port part, on the side toward the inventory.
    public @Nullable CablePart port() {
        return part(facing());
    }

    @Override
    public Kind wirelessKind() {
        return ingress() ? Kind.INGRESS : Kind.EGRESS;
    }

    @Override
    public @Nullable WirelessLink link() {
        return link;
    }

    @Override
    public void setLink(@Nullable WirelessLink link) {
        if (Objects.equals(this.link, link)) {
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
        return port() != null ? port().deviceName() : "";
    }

    @Override
    public void setDeviceName(String name) {
        if (port() != null) {
            port().setDeviceName(name);
        }
    }

    public String shownController() {
        return shownController;
    }

    public Wireless.Problem shownProblem() {
        return shownProblem;
    }

    // Its part, on the side toward the inventory (placed, or turned).
    private void ensurePart() {
        if (!(getBlockState().getBlock() instanceof WirelessPortBlock block)) {
            return;
        }
        Direction facing = facing();
        if (getAttachments().part(facing) != block.partType()) {
            setAttachments(CableAttachments.EMPTY.with(facing, CableAttachments.Attachment.part(block.partType())));
        }
    }

    public void serverTick(ServerLevel level) {
        if (++timer >= CHECK_INTERVAL) {
            timer = 0;
            ensurePart();
            Wireless.check(level.getServer(), this);
            updateState(level);
        }
    }

    private void updateState(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof WirelessPortBlock)) {
            return;
        }
        Wireless.Problem problem = Wireless.problem(level.getServer(), this);
        WirelessState shown = problem == Wireless.Problem.NONE ? isOnline() ? WirelessState.ONLINE : WirelessState.OFF : problem.state();
        if (state.getValue(WirelessPortBlock.STATE) != shown) {
            level.setBlock(worldPosition, state.setValue(WirelessPortBlock.STATE, shown), Block.UPDATE_CLIENTS);
        }
        String controller = controllerName(level.getServer());
        if (problem != shownProblem || !controller.equals(shownController)) {
            shownProblem = problem;
            shownController = controller;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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
        String controller = controllerName(server);
        RackDeviceInfo.Status status = problem == Wireless.Problem.NONE && !isOnline() ? RackDeviceInfo.Status.OFFLINE : problem.status();
        Component text = problem != Wireless.Problem.NONE ? problem.text() : status.text();
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.controller"),
                link == null ? Component.translatable("hud.encodedlogistics.wireless.use_card")
                        : controller.isEmpty() ? Component.translatable("hud.encodedlogistics.wireless.none") : Component.literal(controller)));
        BlockState faced = level != null ? level.getBlockState(worldPosition.relative(facing())) : null;
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.inventory"),
                faced == null || faced.isAir() ? Component.translatable("hud.encodedlogistics.wireless.none") : faced.getBlock().getName()));
        if (port() instanceof PortPart part && level != null) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.moved"),
                    Component.translatable("hud.encodedlogistics.wireless.per_minute", part.resourceType().format(part.movedPerMinute(level.getGameTime())))));
        }
        return new RackDeviceInfo(getBlockState().getBlock().getName(), status, text, lines);
    }

    // --- Loading, drops and removal ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ensurePart();
            if (link != null) {
                ControllerStructures.get(serverLevel).markTopologyChanged();
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel serverLevel && link != null) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    // Only the part's contents (its modules): the block drops itself.
    @Override
    public List<ItemStack> drops(Direction side) {
        CablePart part = part(side);
        return part != null ? part.contents() : List.of();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            Wireless.removed(serverLevel.getServer(), this);
        }
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        link = input.read("link", WirelessLink.CODEC).orElse(null);
        // Only sent to clients.
        shownController = input.getStringOr("shown_controller", "");
        shownProblem = Wireless.Problem.values()[Math.clamp(input.getIntOr("shown_problem", Wireless.Problem.NOT_LINKED.ordinal()), 0,
                Wireless.Problem.values().length - 1)];
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (link != null) {
            output.store("link", WirelessLink.CODEC, link);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("shown_controller", shownController);
        tag.putInt("shown_problem", shownProblem.ordinal());
        return tag;
    }
}
