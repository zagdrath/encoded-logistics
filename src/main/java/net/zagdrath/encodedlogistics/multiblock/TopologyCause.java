/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

// Why a network was worked out again (ControllerStructures.topologyChanged): shown by /encodedlogistics debug solves and
// in the slow-solve warning.
public enum TopologyCause {
    // A network node placed, broken or reconnected.
    BLOCK("block placed, broken or reconnected"),
    // A part mounted or taken off, a drive in or out, a node's lanes changed.
    PARTS("parts or lanes changed"),
    // A remote link (Network Bridge, Point-to-Point Link, Wireless Bridge or Port, Small Wireless Bridge) set, broken,
    // or one of its ends loaded or unloaded with the other loaded.
    LINK("remote link changed"),
    // A Server Rack's devices or segments changed.
    RACK("rack devices changed"),
    // An optical channel (multiplexer line, transport shelf) came or went, or an amplifier's power did.
    OPTICAL("optical channel changed"),
    // A chunk at a network's edge, where its last walk stopped at an unloaded block, loaded.
    CHUNK("chunk at its edge loaded"),
    // A controller structure formed, changed or went.
    STRUCTURE("controller structure changed"),
    // A network that hadn't been worked out yet (new, or loaded).
    NEW("first solve"),
    // Everything at once.
    EVERYTHING("every network");

    private final String label;

    TopologyCause(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
