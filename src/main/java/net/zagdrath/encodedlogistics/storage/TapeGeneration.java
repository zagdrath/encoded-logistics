/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

// The five LTO tape generations (M = 1,024 K): their capacity by the drives' byte model, the item id and the label
// players read. Each generation above LTO-6 reads and writes 15% faster than the one before (LTO-10 takes ~0.52x).
public enum TapeGeneration implements DriveCapacity {
    LTO_6("lto_tape_6", "LTO-6", 8L << 20),
    LTO_7("lto_tape_7", "LTO-7", 32L << 20),
    LTO_8("lto_tape_8", "LTO-8", 128L << 20),
    LTO_9("lto_tape_9", "LTO-9", 512L << 20),
    LTO_10("lto_tape_10", "LTO-10", 2L << 30);

    private final String id, label;
    private final long bytes;

    TapeGeneration(String id, String label, long bytes) {
        this.id = id;
        this.label = label;
        this.bytes = bytes;
    }

    // The item id: lto_tape_6 ... lto_tape_10.
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    @Override
    public long bytes() {
        return bytes;
    }

    // Read and write times are multiplied by this.
    public double speed() {
        return Math.pow(0.85, ordinal());
    }
}
