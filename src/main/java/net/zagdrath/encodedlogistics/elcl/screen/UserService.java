/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

// User profiles (OS.md 6): who is *SECOFR-class on a system (the network's owner: its Firewall's owner).
public interface UserService {
    boolean securityOfficer(ElclSystem system, String user);
}
