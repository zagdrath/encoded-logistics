/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import net.zagdrath.encodedlogistics.Config;

// How much a medium in DriveStorage holds, by the drives' byte model: K bytes, 8 items a byte, each type reserving
// K / 128 bytes, at most driveTypeLimit types. A Storage Drive's tier (StorageTier) or a tape's generation
// (TapeGeneration).
public interface DriveCapacity {
    long bytes();

    // Bytes each stored type reserves.
    default long bytesPerType() {
        return bytes() / 128;
    }

    // The most types it holds.
    default int typeLimit() {
        return Config.DRIVE_TYPE_LIMIT.getAsInt();
    }
}
