/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.NetworkBridgeBlock;
import net.zagdrath.encodedlogistics.menu.NetworkBridgeMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// A Network Bridge's partner (where the other Bridge of its pair is, in any dimension) and what its block shows. Every
// CHECK_INTERVAL ticks it checks the partner still points back (a partner that was broken, or paired elsewhere, unlinks
// it once its chunk is loaded) and sets STATUS: linked_active while lanes cross the link on a running network. With
// bridgeChunkLoading on, a linked Bridge keeps its own chunk loaded.
public class NetworkBridgeBlockEntity extends BlockEntity implements MenuProvider {
    private static final int CHECK_INTERVAL = 10;

    // Chunk tickets for linked Bridges; dropped when the world loads if bridgeChunkLoading has been turned off.
    public static final TicketController CHUNKS = new TicketController(EncodedLogistics.id("network_bridge"), (level, tickets) -> {
        if (!Config.BRIDGE_CHUNK_LOADING.getAsBoolean()) {
            for (BlockPos owner : List.copyOf(tickets.getBlockTickets().keySet())) {
                tickets.removeAllTickets(owner);
            }
        }
    });

    // What the screen says about the link.
    public enum LinkStatus {
        UNLINKED, LINKED, PARTNER_OFFLINE
    }

    private @Nullable GlobalPos partner;
    private boolean chunkForced;
    private int timer;

    public NetworkBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.NETWORK_BRIDGE.get(), pos, state);
    }

    public @Nullable GlobalPos partner() {
        return partner;
    }

    public GlobalPos self() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    // Pairs with (or, with null, unpairs from) another Bridge: this end only.
    public void setPartner(@Nullable GlobalPos partner) {
        if (Objects.equals(this.partner, partner)) {
            return;
        }
        this.partner = partner;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
            updateChunkTicket(serverLevel);
            updateStatus(serverLevel);
        }
    }

    // The Bridge at a position in any dimension, loading its chunk if need be (pairing writes both ends).
    public static @Nullable NetworkBridgeBlockEntity at(MinecraftServer server, GlobalPos pos, boolean load) {
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null || !load && !level.isLoaded(pos.pos())) {
            return null;
        }
        if (load) {
            level.getChunkAt(pos.pos());
        }
        return level.getBlockEntity(pos.pos()) instanceof NetworkBridgeBlockEntity bridge ? bridge : null;
    }

    // Pairs two Bridges with each other, unpairing any old partners. False when they can't be (the same Bridge, or
    // another dimension while bridgeCrossDimension is off).
    public static boolean pair(NetworkBridgeBlockEntity a, NetworkBridgeBlockEntity b) {
        if (a == b || !(a.level instanceof ServerLevel levelA) || !(b.level instanceof ServerLevel)
                || !a.level.dimension().equals(b.level.dimension()) && !Config.BRIDGE_CROSS_DIMENSION.getAsBoolean()) {
            return false;
        }
        MinecraftServer server = levelA.getServer();
        for (NetworkBridgeBlockEntity end : new NetworkBridgeBlockEntity[] { a, b }) {
            GlobalPos old = end.partner;
            if (old != null && !old.equals(a.self()) && !old.equals(b.self())) {
                NetworkBridgeBlockEntity oldPartner = at(server, old, false);
                if (oldPartner != null && end.self().equals(oldPartner.partner)) {
                    oldPartner.setPartner(null);
                }
            }
        }
        a.setPartner(b.self());
        b.setPartner(a.self());
        return true;
    }

    // Whether the partner is loaded and points back.
    public boolean partnerOnline(MinecraftServer server) {
        if (partner == null) {
            return false;
        }
        NetworkBridgeBlockEntity other = at(server, partner, false);
        return other != null && self().equals(other.partner);
    }

    public LinkStatus linkStatus(MinecraftServer server) {
        return partner == null ? LinkStatus.UNLINKED : partnerOnline(server) ? LinkStatus.LINKED : LinkStatus.PARTNER_OFFLINE;
    }

    // Lanes crossing the link, as last solved.
    public int lanesUsed(MinecraftServer server) {
        return partner != null ? ControllerStructures.remoteUsage(server, NodePos.of(self()), NodePos.of(partner)) : 0;
    }

    public void serverTick(ServerLevel level) {
        if (++timer < CHECK_INTERVAL) {
            return;
        }
        timer = 0;
        if (partner != null) {
            ServerLevel there = level.getServer().getLevel(partner.dimension());
            if (there == null || there.isLoaded(partner.pos())
                    && !(there.getBlockEntity(partner.pos()) instanceof NetworkBridgeBlockEntity other && self().equals(other.partner))) {
                setPartner(null);
            }
        }
        updateChunkTicket(level);
        updateStatus(level);
    }

    private void updateStatus(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof NetworkBridgeBlock)) {
            return;
        }
        NetworkBridgeBlock.Status status;
        if (partner == null) {
            status = NetworkBridgeBlock.Status.UNLINKED;
        } else {
            boolean active = lanesUsed(level.getServer()) > 0
                    && ControllerStructures.isOnline(level.getServer(), ControllerStructures.networkOf(level, worldPosition));
            status = active ? NetworkBridgeBlock.Status.LINKED_ACTIVE : NetworkBridgeBlock.Status.LINKED_IDLE;
        }
        if (state.getValue(NetworkBridgeBlock.STATUS) != status) {
            level.setBlock(worldPosition, state.setValue(NetworkBridgeBlock.STATUS, status), Block.UPDATE_CLIENTS);
        }
    }

    private void updateChunkTicket(ServerLevel level) {
        boolean wanted = partner != null && !isRemoved() && Config.BRIDGE_CHUNK_LOADING.getAsBoolean();
        if (wanted != chunkForced) {
            ChunkPos chunk = ChunkPos.containing(worldPosition);
            CHUNKS.forceChunk(level, worldPosition, chunk.x(), chunk.z(), wanted, true);
            chunkForced = wanted;
        }
    }

    // --- Loading and removal ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    // Broken: the partner (if loaded; otherwise once it is) unlinks, and the chunk is let go.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            if (partner != null) {
                NetworkBridgeBlockEntity other = at(serverLevel.getServer(), partner, false);
                if (other != null && self().equals(other.partner)) {
                    other.setPartner(null);
                }
            }
            if (chunkForced) {
                ChunkPos chunk = ChunkPos.containing(worldPosition);
                CHUNKS.forceChunk(serverLevel, worldPosition, chunk.x(), chunk.z(), false, true);
                chunkForced = false;
            }
        }
    }

    // --- Menu ---

    public void openMenu(ServerPlayer player) {
        player.openMenu(this, buf -> ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).encode(buf, Optional.ofNullable(partner)));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.encodedlogistics.network_bridge");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new NetworkBridgeMenu(containerId, inventory, this);
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        partner = input.read("partner", GlobalPos.CODEC).orElse(null);
        chunkForced = input.getBooleanOr("chunk_forced", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (partner != null) {
            output.store("partner", GlobalPos.CODEC, partner);
        }
        if (chunkForced) {
            output.putBoolean("chunk_forced", true);
        }
    }
}
