/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

// The five storage tiers, by capacity (K = 1,024 bytes), with their colour ramps (deep, shade, base, light) as used on
// dies, drives, the Drive Bay's sleds and in GUIs. REGISTERED: the tiers with items (all of them since Phase 3).
public enum StorageTier implements DriveCapacity {
    K8("8k", "8K", 8L * 1024, 0x7A3212, 0xB8521F, 0xF07A3C, 0xFFB48A),
    K32("32k", "32K", 32L * 1024, 0x7A5C08, 0xB88E14, 0xF0C030, 0xFFE08A),
    K128("128k", "128K", 128L * 1024, 0x00663F, 0x00A06B, 0x00D992, 0xB5FFE3),
    K512("512k", "512K", 512L * 1024, 0x164A80, 0x2474C2, 0x3FA3F5, 0xA8D8FF),
    M2("2m", "2M", 2048L * 1024, 0x4C2685, 0x7A44C8, 0xA66BF5, 0xDDC4FF);

    // Tiers with items (8K and 32K from Phase 1, 128K and 512K from Phase 2, 2M from Phase 3).
    public static final StorageTier[] REGISTERED = { K8, K32, K128, K512, M2 };

    private final String id, label;
    private final long bytes;
    private final int deep, shade, base, light;

    StorageTier(String id, String label, long bytes, int deep, int shade, int base, int light) {
        this.id = id;
        this.label = label;
        this.bytes = bytes;
        this.deep = deep;
        this.shade = shade;
        this.base = base;
        this.light = light;
    }

    // The item id suffix: storage_drive_8k, storage_die_32k...
    public String id() {
        return id;
    }

    // As players read it: 8K, 32K, 128K, 512K, 2M.
    public String label() {
        return label;
    }

    @Override
    public long bytes() {
        return bytes;
    }

    public int deep() {
        return deep;
    }

    public int shade() {
        return shade;
    }

    public int base() {
        return base;
    }

    public int light() {
        return light;
    }
}
