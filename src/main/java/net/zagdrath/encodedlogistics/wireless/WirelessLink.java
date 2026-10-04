/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.wireless;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NodePos;

// What a Wireless Bridge or Port is linked to: its Wireless Controller (by id), that controller's network, and where its
// rack's master was last seen (the far end of the client's remote link; kept up to date while the controller's loaded).
public record WirelessLink(UUID controller, NetworkRef network, GlobalPos rack) {
    public static final Codec<WirelessLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("controller").forGetter(WirelessLink::controller),
            NetworkRef.CODEC.fieldOf("network").forGetter(WirelessLink::network),
            GlobalPos.CODEC.fieldOf("rack").forGetter(WirelessLink::rack))
            .apply(i, WirelessLink::new));

    public NodePos rackNode() {
        return NodePos.of(rack);
    }
}
