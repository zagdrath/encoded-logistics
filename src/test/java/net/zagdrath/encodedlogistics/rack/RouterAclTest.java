/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterAclTest {
    private static List<String> order(RouterAcl acl) {
        return acl.rules().stream().map(rule -> rule.seq() + ":" + rule.match()).toList();
    }

    @Test
    void insertAfterKeepsItsPlace() {
        RouterAcl acl = new RouterAcl("TEST");
        assertTrue(acl.add(-1, "*FLUID", RouterAcl.Dir.BOTH, false, 0, 0));
        assertTrue(acl.add(-1, "*ANY", RouterAcl.Dir.BOTH, true, 0, 0));
        // Again and again after the first: each lands right after it, the rules numbered again once there's no gap.
        for (int i = 0; i < 5; i++) {
            assertTrue(acl.insertAfter(acl.rules().getFirst().seq(), "*ENERGY", RouterAcl.Dir.BOTH, false, 0, 0));
        }
        assertEquals(7, acl.rules().size());
        assertEquals("*FLUID", acl.rules().getFirst().match());
        assertEquals("*ANY", acl.rules().getLast().match(), "Inserted after the last rule: " + order(acl));
        for (int i = 1; i < acl.rules().size(); i++) {
            assertTrue(acl.rules().get(i).seq() > acl.rules().get(i - 1).seq(), "Out of order: " + order(acl));
        }
        assertFalse(acl.insertAfter(999, "*ANY", RouterAcl.Dir.BOTH, true, 0, 0));
    }

    @Test
    void moveSwapsNeighbours() {
        RouterAcl acl = new RouterAcl("TEST");
        acl.add(-1, "*FLUID", RouterAcl.Dir.BOTH, false, 0, 0);
        acl.add(-1, "*ANY", RouterAcl.Dir.BOTH, true, 0, 0);
        assertTrue(acl.move(20, true));
        assertEquals(List.of("10:*ANY", "20:*FLUID"), order(acl));
        assertFalse(acl.move(10, true));
    }

    @Test
    void controlRequestsTakeTheFirstControlRule() {
        RouterAcl acl = new RouterAcl("TEST");
        acl.add(-1, "*CONTROL", RouterAcl.Dir.OUT, false, 0, 0);
        assertFalse(acl.permitsControl(true, 0));
        acl.remove(10);
        acl.add(-1, "*CONTROL", RouterAcl.Dir.BOTH, true, 1, 0);
        assertTrue(acl.permitsControl(false, 0));
        // Its rate (one a minute) is used up.
        assertFalse(acl.permitsControl(true, 20));
        assertTrue(acl.permitsControl(true, RouterAcl.MINUTE));
        // No rule: the default.
        RouterAcl empty = new RouterAcl("NONE");
        assertTrue(empty.permitsControl(true, 0));
        assertFalse(empty.permitsControl(false, 0));
    }
}
