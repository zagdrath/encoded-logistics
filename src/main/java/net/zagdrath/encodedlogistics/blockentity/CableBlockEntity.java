/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// Every cable's block entity (Network, Dense and Fiber, all colours), and a part host's: the anchors, facades and
// terminals mounted on its sides. The connections stay in the blockstate; this only adds the attachments, saved with
// the chunk, synced to clients and handed to the model as ModelData, and whether its terminals are online (their
// screens light up). It never ticks, so a cable without attachments costs next to nothing.
public class CableBlockEntity extends BlockEntity implements NetworkDevice {
    public static final ModelProperty<CableAttachments> ATTACHMENTS = new ModelProperty<>();
    public static final ModelProperty<Boolean> ONLINE = new ModelProperty<>();

    private CableAttachments attachments = CableAttachments.EMPTY;
    private boolean online;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CABLE.get(), pos, state);
    }

    public CableAttachments getAttachments() {
        return attachments;
    }

    public void setAttachment(Direction side, CableAttachments.Attachment attachment) {
        setAttachments(attachments.with(side, attachment));
    }

    public void setAttachments(CableAttachments attachments) {
        if (this.attachments.equals(attachments)) {
            return;
        }
        this.attachments = attachments;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // Whether its terminals are on a powered network with their lanes.
    public boolean isOnline() {
        return online;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) {
            for (ItemStack drop : attachments.drops()) {
                Block.popResource(level, pos, drop);
            }
        }
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        attachments = input.read("attachments", CableAttachments.CODEC).orElse(CableAttachments.EMPTY);
        // Only sent to clients (getUpdateTag); the server works it out again every tick.
        online = input.getBooleanOr("online", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!attachments.isEmpty()) {
            output.store("attachments", CableAttachments.CODEC, attachments);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        if (online) {
            tag.putBoolean("online", true);
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Client side: the attachments arrived; rebuild the model data and re-mesh the block.
    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        CableAttachments before = attachments;
        boolean wasOnline = online;
        super.onDataPacket(connection, input);
        if (!attachments.equals(before) || online != wasOnline) {
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
        return attachments.isEmpty() ? ModelData.EMPTY : ModelData.builder().with(ATTACHMENTS, attachments).with(ONLINE, online).build();
    }
}
