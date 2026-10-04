/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

// A link from a node to one that isn't next to it, maybe in another dimension: a Network Bridge to its partner, a
// lanes Point-to-Point Link to its other end. NetworkDiscovery joins the two only when each lists the other; the link
// carries the smaller of their capacities. wireless: a Wireless Controller's link to a client (or back), which
// wirelessCrossDimension lets into another dimension (bridgeCrossDimension the rest).
public record RemoteLink(NodePos target, int capacity, boolean wireless) {
    public RemoteLink(NodePos target, int capacity) {
        this(target, capacity, false);
    }
}
