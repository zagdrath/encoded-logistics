/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

// A connection between two nodes, carrying at most capacity lanes: between neighbours, the smaller tier of its two ends;
// a remote link (a Bridge pair, a lanes Point-to-Point Link), what its ends allow. from is always the lower position
// (NetworkGraph.ORDER), so a link has one identity whichever end it is looked up from.
public record NetworkLink(NodePos from, NodePos to, int capacity) {
    public NetworkLink {
        if (NetworkGraph.ORDER.compare(from, to) > 0) {
            NodePos swap = from;
            from = to;
            to = swap;
        }
    }

    public NodePos other(NodePos end) {
        return end.equals(from) ? to : from;
    }
}
