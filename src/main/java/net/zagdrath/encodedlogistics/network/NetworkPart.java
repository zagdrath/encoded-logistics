/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import net.minecraft.world.item.Item;

// A device mounted on another node (an Access Terminal on a cable side): it shows in the network's device list as its
// own entry, with its own drain. Its lanes count toward the node it's on.
public record NetworkPart(Item item, double passiveDrain) {}
