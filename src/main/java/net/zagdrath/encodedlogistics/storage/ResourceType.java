/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// What a StorageKey counts, and in what unit: items (one each), fluids (mB, shown in buckets), pressurized gases and
// chemicals (their own mod's unit; Arcforge's is mB) and energy (FE). Energy has no keys: Energy Storage Drives add to
// the network's energy pool instead (EnergyDrives).
//
// UNITS_PER_BYTE is the drives' byte model by type: a byte of a Storage Drive holds 8 items, a bucket of fluid or gas,
// or 1,000 FE, so the same Storage Die tiers (8K-2M) size every kind of drive.
public enum ResourceType implements StringRepresentable {
    ITEM("item", "ITEM", 8),
    FLUID("fluid", "FLUID", 1_000),
    PRESSURIZED("pressurized", "PRES", 1_000),
    ENERGY("energy", "ENERGY", 1_000);

    public static final Codec<ResourceType> CODEC = StringRepresentable.fromEnum(ResourceType::values);
    public static final StreamCodec<ByteBuf, ResourceType> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], ResourceType::ordinal);
    // The types network storage holds by key (all but energy).
    public static final ResourceType[] KEYED = { ITEM, FLUID, PRESSURIZED };

    private final String id, code;
    private final long unitsPerByte;

    ResourceType(String id, String code, long unitsPerByte) {
        this.id = id;
        this.code = code;
        this.unitsPerByte = unitsPerByte;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    // As Terminal OS shows it (Work with Inventory's Type column, INVITEMS): ITEM, FLUID, PRES, ENERGY.
    public String code() {
        return code;
    }

    // As ELCL's TYPE() takes it: *ITEM, *FLUID, *PRES, *ENERGY.
    public String special() {
        return "*" + code;
    }

    public long unitsPerByte() {
        return unitsPerByte;
    }

    // Amounts in mB (fluids and Arcforge gases), shown as buckets.
    public boolean inMillibuckets() {
        return this == FLUID || this == PRESSURIZED;
    }

    // An amount as players read it: 1,234 (items), 41,000 mB (fluids, gases), 1,234 FE.
    public String format(long amount) {
        return switch (this) {
            case ITEM -> String.format(Locale.ROOT, "%,d", amount);
            case FLUID, PRESSURIZED -> String.format(Locale.ROOT, "%,d mB", amount);
            case ENERGY -> String.format(Locale.ROOT, "%,d FE", amount);
        };
    }

    // An amount in short: 950, 1.2K, 34M (items); 250mB, 1.5B, 12KB (fluids, gases); 8MFE (energy). For the grid's corner.
    public String abbreviate(long amount) {
        return switch (this) {
            case ITEM -> shortNumber(amount);
            case FLUID, PRESSURIZED -> amount < 1_000 ? amount + "mB" : shortNumber(amount / 1_000) + "B";
            case ENERGY -> shortNumber(amount) + "FE";
        };
    }

    public static String buckets(long millibuckets) {
        if (millibuckets < 1_000) {
            return String.format(Locale.ROOT, "%,d mB", millibuckets);
        }
        if (millibuckets % 1_000 == 0) {
            return String.format(Locale.ROOT, "%,d B", millibuckets / 1_000);
        }
        return String.format(Locale.ROOT, "%,.3f B", millibuckets / 1_000.0).replaceAll("0+ B$", " B");
    }

    public static String shortNumber(long count) {
        if (count < 1_000) {
            return Long.toString(count);
        }
        String[] suffixes = { "K", "M", "B", "T" };
        double value = count;
        int index = -1;
        while (value >= 1_000 && index < suffixes.length - 1) {
            value /= 1_000;
            index++;
        }
        return (value < 10 ? String.format(Locale.ROOT, "%.1f", value).replace(".0", "") : Long.toString((long) value)) + suffixes[index];
    }

    // From ELCL's *ITEM / *FLUID / *PRES / *ENERGY (or the plain code); null for anything else, including *ALL.
    public static @Nullable ResourceType bySpecial(String text) {
        String code = text.startsWith("*") ? text.substring(1) : text;
        for (ResourceType type : values()) {
            if (type.code.equalsIgnoreCase(code) || type.id.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
