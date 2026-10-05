/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.arcforge;

import org.jspecify.annotations.Nullable;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.machine.MachineAccess;
import net.zagdrath.encodedlogistics.machine.MachineBridges;
import net.zagdrath.encodedlogistics.storage.PressurizedSource;
import net.zagdrath.encodedlogistics.storage.PressurizedSources;

// Turns the Arcforge integration on at startup when it can be: Arcforge loaded, with its API (Arcforge 2.5.0 and
// later), of the major version this was built against and at least the minor version each part needs. Machines (the
// Small Wireless Bridge's) need API 1.0; gases (ArcforgeGases: PRESSURIZED storage) need 1.1. Otherwise that part stays
// off - bridges do nothing, the network stores no Arcforge gases, nothing touches Arcforge's classes - and the log says
// why. This class touches none of Arcforge's classes itself: the API's version is read by reflection (its constants
// would otherwise be the compile-time copies), and ArcforgeMachines and ArcforgeGases, which use the API, are loaded
// only once the check passes.
public final class ArcforgeCompat {
    public static final String MOD_ID = "arcforge";
    // The API version this integration is built against (net.zagdrath.arcforge:arcforge-api, gradle.properties): its
    // machine control needs 1.0, its gases 1.1.
    static final int BUILT_AGAINST_MAJOR = 1, BUILT_AGAINST_MINOR = 0, GASES_MINOR = 1;
    private static final String API_CLASS = "net.zagdrath.arcforge.api.ArcforgeApi";
    private static final String MACHINES_CLASS = "net.zagdrath.encodedlogistics.compat.arcforge.ArcforgeMachines";
    private static final String GASES_CLASS = "net.zagdrath.encodedlogistics.compat.arcforge.ArcforgeGases";
    // For testing the version check without another Arcforge: -Dencodedlogistics.arcforgeApiMajor=2 makes the loaded API
    // report that major version.
    private static final String MAJOR_OVERRIDE = "encodedlogistics.arcforgeApiMajor";

    private ArcforgeCompat() {}

    public static void init() {
        ModContainer arcforge = ModList.get().getModContainerById(MOD_ID).orElse(null);
        if (arcforge == null) {
            return;
        }
        String modVersion = arcforge.getModInfo().getVersion().toString();
        int major, minor;
        String version;
        try {
            Class<?> api = Class.forName(API_CLASS);
            major = api.getField("API_VERSION_MAJOR").getInt(null);
            minor = api.getField("API_VERSION_MINOR").getInt(null);
            version = String.valueOf(api.getField("API_VERSION").get(null));
        } catch (ReflectiveOperationException | LinkageError e) {
            EncodedLogistics.LOGGER.warn("Arcforge integration disabled: Arcforge {} has no machine control API (it came in Arcforge 2.5.0). "
                    + "Small Wireless Bridges will do nothing until Arcforge is updated.", modVersion);
            return;
        }
        major = Integer.getInteger(MAJOR_OVERRIDE, major);
        String problem = incompatibility(major, minor);
        if (problem != null) {
            EncodedLogistics.LOGGER.warn("Arcforge integration disabled: Arcforge {} has machine control API {}, {}. Small Wireless Bridges will do "
                    + "nothing until Encoded Logistics and Arcforge versions that match are installed.", modVersion, version, problem);
            return;
        }
        try {
            MachineBridges.setAccess((MachineAccess) Class.forName(MACHINES_CLASS).getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError e) {
            EncodedLogistics.LOGGER.warn("Arcforge integration disabled: it couldn't start with Arcforge {} (API {}).", modVersion, version, e);
            return;
        }
        EncodedLogistics.LOGGER.info("Arcforge integration enabled (Arcforge {}, machine control API {}).", modVersion, version);
        if (!gasesSupported(major, minor)) {
            EncodedLogistics.LOGGER.warn("Arcforge gas storage disabled: Arcforge {} has API {}, and storing gases needs API {}.{} or later.",
                    modVersion, version, BUILT_AGAINST_MAJOR, GASES_MINOR);
            return;
        }
        try {
            PressurizedSources.register((PressurizedSource) Class.forName(GASES_CLASS).getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError e) {
            EncodedLogistics.LOGGER.warn("Arcforge gas storage disabled: it couldn't start with Arcforge {} (API {}).", modVersion, version, e);
            return;
        }
        EncodedLogistics.LOGGER.info("Arcforge gas storage enabled (gas API {}).", version);
    }

    // Whether an API version (already compatible) has the gas API.
    static boolean gasesSupported(int major, int minor) {
        return major == BUILT_AGAINST_MAJOR && minor >= GASES_MINOR;
    }

    // Why an API version can't be used, or null when it can: another major version changed or removed what this uses,
    // and an older minor version lacks what was added since. Newer minor versions only add, so they work.
    static @Nullable String incompatibility(int major, int minor) {
        if (major != BUILT_AGAINST_MAJOR) {
            return "but this version of Encoded Logistics needs API " + BUILT_AGAINST_MAJOR + ".x";
        }
        if (minor < BUILT_AGAINST_MINOR) {
            return "but this version of Encoded Logistics needs API " + BUILT_AGAINST_MAJOR + "." + BUILT_AGAINST_MINOR + " or later";
        }
        return null;
    }
}
