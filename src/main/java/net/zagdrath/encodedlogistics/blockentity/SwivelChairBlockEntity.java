/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// A Swivel Chair's turn (yaw, as an entity's: 0 faces south, its seat's front the way its sitter looks) and its fabric's
// dye (the item's dyed_color, kept when it's broken; none: the tweed as drawn).
public class SwivelChairBlockEntity extends BlockEntity {
    private float yaw;
    private @Nullable DyedItemColor color;
    private long lastSync;

    public SwivelChairBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SWIVEL_CHAIR.get(), pos, state);
    }

    public float yaw() {
        return yaw;
    }

    // Turns the chair (its sitter turning it, or its placer); clients hear of it at most every 4 ticks, the sitter
    // themselves turning it smoothly on theirs.
    public void setYaw(float yaw) {
        float wrapped = Mth.wrapDegrees(yaw);
        if (Math.abs(Mth.wrapDegrees(wrapped - this.yaw)) < 0.5F) {
            return;
        }
        this.yaw = wrapped;
        setChanged();
        if (level != null && !level.isClientSide() && level.getGameTime() - lastSync >= 4) {
            lastSync = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // The fabric's colour (ARGB), or -1 for none.
    public int color() {
        return color != null ? 0xFF000000 | color.rgb() : -1;
    }

    // --- Components (the dye goes with the item) ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        color = components.get(DataComponents.DYED_COLOR);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (color != null) {
            components.set(DataComponents.DYED_COLOR, color);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("color");
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        yaw = input.getFloatOr("yaw", 0);
        color = input.read("color", DyedItemColor.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("yaw", yaw);
        if (color != null) {
            output.store("color", DyedItemColor.CODEC, color);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
