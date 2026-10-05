/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.List;
import java.util.UUID;

import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// What a network's cold tier (TapeTier) is made of: its online Tape Libraries and Tape Drives - their tapes (or reel),
// the drives to work them, and how long a recall from one of them takes.
public interface TapeSource {
    // The ids its tapes keep their contents under in DriveStorage.
    List<UUID> tapeIds();

    int driveCount();

    // Ticks to load and read amount of an item from the tape of its holding it (-1: none holds it).
    int recallTicks(DriveStorage data, ItemKey key, long amount);
}
