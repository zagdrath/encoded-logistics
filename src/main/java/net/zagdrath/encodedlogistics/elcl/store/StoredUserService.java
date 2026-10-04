/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.UserService;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;

// User profiles in the system's saved data. The network's owner (its Firewall's owner) is *SECOFR-class.
public final class StoredUserService implements UserService {
    @Override
    public boolean securityOfficer(ElclSystem system, String user) {
        FirewallDevice firewall = ControllerStructures.firewall(system.server(), system.network());
        return firewall != null && firewall.ownerName().toUpperCase(Locale.ROOT).equals(user.toUpperCase(Locale.ROOT));
    }
}
