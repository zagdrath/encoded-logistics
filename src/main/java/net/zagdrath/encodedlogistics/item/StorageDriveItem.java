/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.EnergyDrives;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// A Storage Drive of one tier and resource type: items (Storage Drive), fluids (Fluid Storage Drive), gases and
// chemicals (Pressurized Storage Drive) or FE (Energy Storage Drive). Every drive holder takes every type.
//
// An item, fluid or pressurized drive's contents are kept in DriveStorage under its drive_id; the item carries only that
// id and a cache of its fill (drive_stats). Works once it's in a Drive Bay on a network. An Energy Storage Drive keeps
// its FE on the item itself (drive_energy, EnergyDrives), so it holds its charge out of a holder.
public class StorageDriveItem extends Item {
    private final StorageTier tier;
    private final ResourceType type;

    public StorageDriveItem(Item.Properties properties, StorageTier tier) {
        this(properties, tier, ResourceType.ITEM);
    }

    public StorageDriveItem(Item.Properties properties, StorageTier tier, ResourceType type) {
        super(properties);
        this.tier = tier;
        this.type = type;
    }

    public StorageTier getTier() {
        return tier;
    }

    public ResourceType getType() {
        return type;
    }

    public static @Nullable UUID id(ItemStack stack) {
        return stack.get(ModDataComponents.DRIVE_ID.get());
    }

    public static ResourceType type(ItemStack stack) {
        return stack.getItem() instanceof StorageDriveItem drive ? drive.type : ResourceType.ITEM;
    }

    // A drive in a holder: its id (given the first time), and its fill cached on it (not for an Energy Storage Drive,
    // whose charge is on the item already). Returns its id, or null for anything but a drive.
    public static @Nullable UUID refresh(MinecraftServer server, ItemStack stack) {
        if (!(stack.getItem() instanceof StorageDriveItem drive)) {
            return null;
        }
        UUID id = id(stack);
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(ModDataComponents.DRIVE_ID.get(), id);
        }
        if (drive.type != ResourceType.ENERGY) {
            DriveStats stats = DriveStorage.get(server).stats(id, drive.tier, drive.type);
            if (!stats.equals(stack.get(ModDataComponents.DRIVE_STATS.get()))) {
                stack.set(ModDataComponents.DRIVE_STATS.get(), stats);
            }
        }
        return id;
    }

    // What a holder's model draws for a drive (Drive Bay sleds, NAS / SAN bays): type * 64 + tier * 8 + light, the light
    // being its fill (0 green - 3 red), or for an Energy Storage Drive its charge (EnergyDrives.chargeLight) or
    // LIGHT_CHARGING; LIGHT_OFF while the holder is offline. -1 for no drive.
    public static final int LIGHT_OFF = 4, LIGHT_CHARGING = 5;

    public static int sledCode(ItemStack stack, boolean online) {
        if (!(stack.getItem() instanceof StorageDriveItem drive)) {
            return -1;
        }
        int light;
        if (!online) {
            light = LIGHT_OFF;
        } else if (drive.type == ResourceType.ENERGY) {
            light = EnergyDrives.charging(id(stack)) ? LIGHT_CHARGING : EnergyDrives.chargeLight(stack);
        } else {
            light = stats(stack).light();
        }
        return drive.type.ordinal() * 64 + drive.tier.ordinal() * 8 + light;
    }

    public static ResourceType sledType(int code) {
        return ResourceType.values()[Math.min(code / 64, ResourceType.values().length - 1)];
    }

    public static int sledTier(int code) {
        return Math.min(code / 8 % 8, StorageTier.values().length - 1);
    }

    public static int sledLight(int code) {
        return code % 8;
    }

    public static DriveStats stats(ItemStack stack) {
        if (stack.getItem() instanceof StorageDriveItem drive && drive.type == ResourceType.ENERGY) {
            return EnergyDrives.stats(stack);
        }
        DriveStats stats = stack.get(ModDataComponents.DRIVE_STATS.get());
        return stats != null ? stats : stack.getItem() instanceof StorageDriveItem drive ? DriveStats.empty(drive.tier) : DriveStats.empty(StorageTier.K8);
    }
}
