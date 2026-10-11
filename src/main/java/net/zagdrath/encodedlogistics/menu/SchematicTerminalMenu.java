/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.net.SchematicTerminalPayload;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.schematic.SchematicData;
import net.zagdrath.encodedlogistics.schematic.SchematicTerminalService;

// The Schematic Terminal's menu (screens/schematic_terminal.json): the player's inventory below rows the screen fills
// with the network's schematic holders (the Schematics tab) or its crafting jobs (the Jobs tab), and a 12 px summary
// strip between them. Nothing above the inventory is a slot: the server sends a snapshot (SchematicData) every
// SYNC_INTERVAL ticks when it changed, and the client asks for clicks (SchematicTerminalActionPayload). It's a cable
// face's Schematic Terminal (pos, side) or a Rack Schematic Console (the rack's master, u).
public class SchematicTerminalMenu extends AbstractContainerMenu {
    public static final int SUMMARY = 12;
    private static final int SYNC_INTERVAL = 10;
    private static final int PART = 0, CONSOLE = 1;

    private final int mode;
    private final BlockPos pos;
    private final Direction side;
    private final int u;
    private final int rows;
    private final Player player;
    // Server: what the client was last sent (encoded), and when next.
    private byte @Nullable [] sent;
    private int ticksUntilSync;
    // Client: the last snapshot, and a count that goes up with each.
    private SchematicData.Snapshot snapshot = SchematicData.Snapshot.EMPTY;
    private int version;

    public SchematicTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readVarInt(), extraData.readBlockPos(), extraData.readEnum(Direction.class), extraData.readVarInt(),
                AccessTerminalMenu.clientRows.applyAsInt(SUMMARY));
    }

    private SchematicTerminalMenu(int containerId, Inventory inventory, int mode, BlockPos pos, Direction side, int u, int rows) {
        super(ModMenuTypes.SCHEMATIC_TERMINAL.get(), containerId);
        this.mode = mode;
        this.pos = pos;
        this.side = side;
        this.u = u;
        this.rows = rows;
        this.player = inventory.player;
        addStandardInventorySlots(inventory, 9, AccessTerminalMenu.TOP + rows * AccessTerminalMenu.ROW + SUMMARY + 17);
    }

    // The Schematic Terminal on that side of the block at pos.
    public static void open(ServerPlayer player, BlockPos pos, Direction side) {
        open(player, PART, pos, side, 0, Component.translatable("gui.encodedlogistics.schematic_terminal"));
    }

    // A Rack Schematic Console's.
    public static void openConsole(ServerPlayer player, BlockPos rack, int u) {
        open(player, CONSOLE, rack, Direction.NORTH, u, Component.translatable("item.encodedlogistics.rack_schematic_console"));
    }

    private static void open(ServerPlayer player, int mode, BlockPos pos, Direction side, int u, Component title) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SchematicTerminalMenu(id, inventory, mode, pos, side, u,
                AccessTerminalMenu.DEFAULT_ROWS), title), buf -> {
                    buf.writeVarInt(mode);
                    buf.writeBlockPos(pos);
                    buf.writeEnum(side);
                    buf.writeVarInt(u);
                });
    }

    public int rows() {
        return rows;
    }

    // --- Server ---

    // The network it belongs to (its Firewall decides what the player may do).
    public @Nullable NetworkRef homeNetwork() {
        if (mode == CONSOLE) {
            return RackConsoleMenu.homeNetwork(player, pos, u);
        }
        return player.level() instanceof ServerLevel level ? ControllerStructures.networkOf(level, pos) : null;
    }

    // The network it reaches now: its home network while the terminal (or console, open) is online.
    public @Nullable NetworkRef network() {
        if (mode == CONSOLE) {
            return RackConsoleMenu.network(player, pos, u);
        }
        return player.level() instanceof ServerLevel level && ControllerStructures.get(level).isDeviceOnline(level, pos) ? homeNetwork() : null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (mode == CONSOLE) {
            return RackConsoleMenu.stillValid(player, pos, u);
        }
        if (!(player.level().getBlockEntity(pos) instanceof CableBlockEntity host)) {
            return false;
        }
        return host.getAttachments().part(side) == PartType.SCHEMATIC_TERMINAL && player.isWithinBlockInteractionRange(pos, 4.0);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer) || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL;
        SchematicData.Snapshot now = SchematicTerminalService.snapshot(serverPlayer, homeNetwork(), network());
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), serverPlayer.registryAccess());
        SchematicData.Snapshot.STREAM_CODEC.encode(buf, now);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        if (sent == null || !Arrays.equals(sent, bytes)) {
            sent = bytes;
            PacketDistributor.sendToPlayer(serverPlayer, new SchematicTerminalPayload(containerId, now));
        }
    }

    public void handleAction(ServerPlayer player, int action, String holder, int slot, Optional<UUID> job, String text) {
        SchematicTerminalService.act(player, this, homeNetwork(), network(), action, holder, slot, job, text);
        // The next sync goes out at once, so the click shows straight away.
        ticksUntilSync = 0;
        broadcastChanges();
    }

    // Shift-clicks in the inventory are the screen's (into the selected holder: QUICK_PUT); nothing moves on its own.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    // --- Client ---

    public void apply(SchematicData.Snapshot snapshot) {
        this.snapshot = snapshot;
        version++;
    }

    public SchematicData.Snapshot snapshot() {
        return snapshot;
    }

    public int version() {
        return version;
    }
}
