/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.wireless;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.util.StringRepresentable;

// A Wireless Bridge, Wireless Port or Small Wireless Bridge (on a machine: MachineBridge, not a block entity): linked
// (Link Card) to a Wireless Controller, it's on that controller's network over the air while the controller admits it
// (Wireless.problem). Its end of the remote link points at the controller's rack.
public interface WirelessClient {
    enum Kind implements StringRepresentable {
        BRIDGE, INGRESS, EGRESS, MACHINE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind byName(String name) {
            for (Kind kind : values()) {
                if (kind.getSerializedName().equals(name)) {
                    return kind;
                }
            }
            return BRIDGE;
        }
    }

    Kind wirelessKind();

    @Nullable WirelessLink link();

    // Linked to a controller (null: unlinked); the network finds the change.
    void setLink(@Nullable WirelessLink link);

    GlobalPos self();
}
