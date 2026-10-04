/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// User profiles and security (OS.md 6-7): a profile per player, made the first time they use a terminal on the system
// (or sign on), with a library list (ELGPL ELSYS) and current library. The network's owner (its Firewall's owner) and
// server operators are *SECOFR-class. Sign-on is needed at SECLVL 30 on a network with a Firewall; at SECLVL 10 there's
// no sign-on and no Firewall authority in the Terminal OS. ELC0401 when a check fails, ELC0402 when a sign-on does.
public interface UserService {
    String SECOFR = "*SECOFR", USER = "*USER";

    record Profile(String user, @Nullable UUID player, String userClass, List<String> libraryList, String currentLibrary, String created,
            String lastSignOn) {}

    // The user's profile, made now if they have none.
    Profile profile(ElclSystem system, String user, @Nullable UUID player);

    // Sign-on: the name typed must be the player's own (ELC0402); the current library (*USRPRF: the profile's) must exist
    // (ELC0201). Updates the profile.
    Profile signOn(ElclSystem system, String user, UUID player, String typed, String currentLibrary) throws ElclException;

    boolean securityOfficer(ElclSystem system, String user);

    // Whether terminals on the system need a sign-on.
    boolean signOnRequired(ElclSystem system);

    // Whether the Firewall's permissions apply in the Terminal OS (SECLVL 30); at 10 everyone has full authority.
    boolean checksAuthority(ElclSystem system);

    // Whether a user may hold, end or change what another created (jobs, schedule entries, triggers): theirs, or
    // *SECOFR, or full authority (no Firewall, or SECLVL 10).
    boolean mayManage(ElclSystem system, String user, String owner);
}
