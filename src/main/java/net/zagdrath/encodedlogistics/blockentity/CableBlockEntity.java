/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.RemoteLink;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.part.CablePart;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// Every cable's block entity (Network, Dense and Fiber, all colours), and a part host's: the anchors, facades and parts
// mounted on its sides. The connections stay in the blockstate; this adds the attachments (saved with the chunk, synced to
// clients, handed to the model as ModelData), each part's own state and behaviour (CablePart), whether its parts are
// online (on a powered network with their lanes) and which of them are lit. It only ticks (through ControllerStructures)
// while it holds a part that ticks, so a cable without parts costs next to nothing.
public class CableBlockEntity extends BlockEntity implements NetworkDevice {
    public static final ModelProperty<CableAttachments> ATTACHMENTS = new ModelProperty<>();
    // Which sides' parts show their lit model, by Direction ordinal bit.
    public static final ModelProperty<Integer> LIT = new ModelProperty<>();
    // Which model each side's part shows (CablePart.look()), four bits per side by Direction ordinal.
    public static final ModelProperty<Integer> LOOKS = new ModelProperty<>();

    private CableAttachments attachments = CableAttachments.EMPTY;
    private final Map<Direction, CablePart> parts = new EnumMap<>(Direction.class);
    private boolean online;
    private int lit, looks;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CABLE.get(), pos, state);
    }

    public CableAttachments getAttachments() {
        return attachments;
    }

    public @Nullable CablePart part(Direction side) {
        return parts.get(side);
    }

    public Iterable<CablePart> parts() {
        return parts.values();
    }

    public void setAttachment(Direction side, CableAttachments.Attachment attachment) {
        setAttachments(attachments.with(side, attachment));
    }

    public void setAttachments(CableAttachments attachments) {
        if (this.attachments.equals(attachments)) {
            return;
        }
        this.attachments = attachments;
        // Parts coming off (or swapped for another) hear about it first.
        if (level instanceof ServerLevel serverLevel) {
            for (Direction side : Direction.values()) {
                CablePart part = parts.get(side);
                if (part != null && attachments.part(side) != part.type()) {
                    part.removed(serverLevel);
                }
            }
        }
        syncParts();
        setChanged();
        updateTicking();
        updateLit(false);
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // Makes the part instances match the attachments: new parts for new sides, gone for removed ones.
    private void syncParts() {
        for (Direction side : Direction.values()) {
            PartType type = attachments.part(side);
            CablePart part = parts.get(side);
            if (type == null) {
                parts.remove(side);
            } else if (part == null || part.type() != type) {
                parts.put(side, type.create(this, side));
            }
        }
    }

    // What one side drops when its attachment comes off: the attachment's item and its part's contents.
    public List<ItemStack> drops(Direction side) {
        List<ItemStack> drops = new ArrayList<>();
        CableAttachments.Attachment attachment = attachments.get(side);
        if (attachment.isFacade()) {
            // A facade drops blank, with the block it was dressed in beside it.
            drops.add(new ItemStack(ModItems.CABLE_FACADE.get()));
            if (attachment.target() != null) {
                drops.add(new ItemStack(attachment.target().getBlock()));
            }
        } else if (attachment.kind() != CableAttachments.Kind.NONE) {
            drops.add(attachment.toItem());
        }
        CablePart part = parts.get(side);
        if (part != null) {
            drops.addAll(part.contents());
        }
        return drops;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            for (CablePart part : List.copyOf(parts.values())) {
                part.removed(serverLevel);
            }
            for (Direction side : Direction.values()) {
                for (ItemStack drop : drops(side)) {
                    Block.popResource(level, pos, drop);
                }
            }
        }
    }

    // --- Online, lit, ticking, redstone ---

    // Whether its parts are on a powered network with their lanes.
    public boolean isOnline() {
        return online;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            updateLit(true);
        }
    }

    // A part's settings, contents or look changed.
    public void partChanged() {
        setChanged();
        updateLit(false);
    }

    // Recomputes which parts are lit and which models they show; clients get the change (and, when asked, the online
    // state with it).
    private void updateLit(boolean always) {
        int now = 0, nowLooks = 0;
        for (CablePart part : parts.values()) {
            if (part.lit()) {
                now |= 1 << part.side().ordinal();
            }
            nowLooks |= (part.look() & 0xF) << part.side().ordinal() * 4;
        }
        if ((now != lit || nowLooks != looks || always) && level != null && !level.isClientSide()) {
            lit = now;
            looks = nowLooks;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        lit = now;
        looks = nowLooks;
    }

    // The remote links its parts carry (lanes Point-to-Point Links), for its network node.
    public List<RemoteLink> remoteLinks() {
        if (level == null || parts.isEmpty()) {
            return List.of();
        }
        List<RemoteLink> links = new ArrayList<>();
        for (CablePart part : parts.values()) {
            links.addAll(part.remoteLinks(level.dimension()));
        }
        return links;
    }

    // Server, every tick while it holds ticking parts (ControllerStructures calls it).
    public void tickParts(ServerLevel level) {
        for (CablePart part : List.copyOf(parts.values())) {
            if (part.type().ticks()) {
                part.tick(level);
            }
        }
    }

    private void updateTicking() {
        if (level instanceof ServerLevel serverLevel) {
            boolean ticks = parts.values().stream().anyMatch(part -> part.type().ticks());
            ControllerStructures.get(serverLevel).setTicking(this, ticks && !isRemoved());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateTicking();
        // An end of a remote link coming back can join a network up again.
        if (level instanceof ServerLevel serverLevel && !remoteLinks().isEmpty()) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).setTicking(this, false);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).setTicking(this, false);
            if (!remoteLinks().isEmpty()) {
                ControllerStructures.get(serverLevel).markTopologyChanged();
            }
        }
    }

    // The redstone its parts send toward a side: weak (any part emitting powers every side, like a lever) or strong (only
    // into the block a part's face is against).
    public int weakSignal() {
        int signal = 0;
        for (CablePart part : parts.values()) {
            signal = Math.max(signal, part.signal());
        }
        return signal;
    }

    public int strongSignal(Direction side) {
        CablePart part = parts.get(side);
        return part != null ? part.signal() : 0;
    }

    public boolean emitsRedstone(Direction side) {
        return parts.get(side) != null && parts.get(side).emitsRedstone();
    }

    // A part's redstone changed: update the neighbours, and the block its face is against.
    public void signalChanged(Direction side) {
        if (level != null) {
            Block block = getBlockState().getBlock();
            level.updateNeighborsAt(worldPosition, block);
            level.updateNeighborsAt(worldPosition.relative(side), block);
        }
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        attachments = input.read("attachments", CableAttachments.CODEC).orElse(CableAttachments.EMPTY);
        syncParts();
        ValueInput saved = input.childOrEmpty("parts");
        for (Map.Entry<Direction, CablePart> entry : parts.entrySet()) {
            entry.getValue().load(saved.childOrEmpty(entry.getKey().getSerializedName()));
        }
        // Only sent to clients (getUpdateTag); the server works these out as it runs.
        online = input.getBooleanOr("online", false);
        lit = input.getIntOr("lit", 0);
        looks = input.getIntOr("looks", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!attachments.isEmpty()) {
            output.store("attachments", CableAttachments.CODEC, attachments);
        }
        if (!parts.isEmpty()) {
            ValueOutput saved = output.child("parts");
            parts.forEach((side, part) -> part.save(saved.child(side.getSerializedName())));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        if (online) {
            tag.putBoolean("online", true);
        }
        if (lit != 0) {
            tag.putInt("lit", lit);
        }
        if (looks != 0) {
            tag.putInt("looks", looks);
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Client side: the attachments or lights changed; rebuild the model data and re-mesh the block.
    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        CableAttachments before = attachments;
        int litBefore = lit, looksBefore = looks;
        super.onDataPacket(connection, input);
        if (!attachments.equals(before) || lit != litBefore || looks != looksBefore) {
            remesh();
        }
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        remesh();
    }

    private void remesh() {
        requestModelDataUpdate();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    public ModelData getModelData() {
        return attachments.isEmpty() ? ModelData.EMPTY
                : ModelData.builder().with(ATTACHMENTS, attachments).with(LIT, lit).with(LOOKS, looks).build();
    }
}
