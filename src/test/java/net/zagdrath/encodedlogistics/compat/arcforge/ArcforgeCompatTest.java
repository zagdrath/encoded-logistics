/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.arcforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.machine.MachineBridge;

// The Arcforge integration's version checks (machines need API 1.0, gases 1.1) and the device-name prefixes bridged
// machines get from their types.
class ArcforgeCompatTest {
    @Test
    void sameMajorVersionWorks() {
        assertNull(ArcforgeCompat.incompatibility(1, 0));
        // Newer minor versions only add.
        assertNull(ArcforgeCompat.incompatibility(1, 4));
    }

    @Test
    void gasesNeedOneOne() {
        assertFalse(ArcforgeCompat.gasesSupported(1, 0));
        assertTrue(ArcforgeCompat.gasesSupported(1, 1));
        assertTrue(ArcforgeCompat.gasesSupported(1, 3));
        assertFalse(ArcforgeCompat.gasesSupported(2, 1));
    }

    @Test
    void otherMajorVersionsDisable() {
        assertNotNull(ArcforgeCompat.incompatibility(2, 0));
        assertNotNull(ArcforgeCompat.incompatibility(0, 9));
    }

    @Test
    void typeCodes() {
        assertEquals("ARCCRU", MachineBridge.typeCode("arcforge:arc_crusher"));
        assertEquals("ELECTR", MachineBridge.typeCode("arcforge:electrolyzer"));
        assertEquals("SIFTER", MachineBridge.typeCode("arcforge:sifter"));
        assertEquals("STETUR", MachineBridge.typeCode("arcforge:steam_turbine_array"));
        assertEquals("XPDRA", MachineBridge.typeCode("arcforge:xp_drain"));
        // Names start with a letter.
        assertEquals("M3DPRI", MachineBridge.typeCode("othermod:3d_printer"));
        assertEquals("MCH", MachineBridge.typeCode("othermod:_"));
    }
}
