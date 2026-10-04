/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.UserService;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;

// User profiles in the system's saved data (OS.md 6). *SECOFR: the network's owner - its Firewall's owner - and server
// operators (when online). A profile's user class is worked out each time, so a new Firewall owner takes effect at once.
public final class StoredUserService implements UserService {
    public static final List<String> DEFAULT_LIBRARY_LIST = List.of(SystemData.GENERAL, SystemData.SYSTEM_LIBRARY);

    private static String upper(String text) {
        return text.trim().toUpperCase(Locale.ROOT);
    }

    private static @Nullable FirewallDevice firewall(ElclSystem system) {
        return ControllerStructures.firewall(system.server(), system.network());
    }

    @Override
    public synchronized Profile profile(ElclSystem system, String user, @Nullable UUID player) {
        SystemData data = ElclStore.of(system);
        String name = upper(user);
        SystemData.Profile profile = data.profiles.get(name);
        if (profile == null) {
            profile = new SystemData.Profile(name, player, new ArrayList<>(DEFAULT_LIBRARY_LIST), SystemData.GENERAL, system.nowShort());
            data.profiles.put(name, profile);
            data.changed();
        } else if (player != null && !player.equals(profile.player)) {
            profile.player = player;
            data.changed();
        }
        return view(system, profile);
    }

    private Profile view(ElclSystem system, SystemData.Profile profile) {
        return new Profile(profile.user, profile.player, securityOfficer(system, profile.user) ? SECOFR : USER, List.copyOf(profile.libraryList),
                profile.currentLibrary, profile.created, profile.lastSignOn);
    }

    @Override
    public synchronized Profile signOn(ElclSystem system, String user, UUID player, String typed, String currentLibrary) throws ElclException {
        if (typed.isBlank() || !upper(typed).equals(upper(user))) {
            throw new ElclException("ELC0402", typed.isBlank() ? "*NONE" : upper(typed));
        }
        profile(system, user, player);
        SystemData data = ElclStore.of(system);
        SystemData.Profile profile = data.profiles.get(upper(user));
        String library = upper(currentLibrary);
        if (!library.isEmpty() && !library.equals("*USRPRF")) {
            ElclServices.libraries().library(system, library);
            profile.currentLibrary = library;
            // The current library is first on the list.
            profile.libraryList.remove(library);
            profile.libraryList.addFirst(library);
        }
        profile.lastSignOn = system.nowShort();
        data.changed();
        return view(system, profile);
    }

    @Override
    public boolean securityOfficer(ElclSystem system, String user) {
        FirewallDevice firewall = firewall(system);
        if (firewall != null && upper(firewall.ownerName()).equals(upper(user))) {
            return true;
        }
        SystemData.Profile profile = ElclStore.of(system).profiles.get(upper(user));
        ServerPlayer player = profile != null && profile.player != null ? system.server().getPlayerList().getPlayer(profile.player) : null;
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public boolean signOnRequired(ElclSystem system) {
        return firewall(system) != null && checksAuthority(system);
    }

    @Override
    public boolean checksAuthority(ElclSystem system) {
        return !ElclServices.sysvals().get(system, "SECLVL").trim().equals("10");
    }

    @Override
    public boolean mayManage(ElclSystem system, String user, String owner) {
        return upper(user).equals(upper(owner)) || firewall(system) == null || !checksAuthority(system) || securityOfficer(system, user);
    }
}
