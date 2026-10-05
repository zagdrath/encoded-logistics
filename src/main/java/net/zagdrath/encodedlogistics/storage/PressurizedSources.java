/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

// The registered PressurizedSources (Arcforge, when present). Integrations register during mod setup; the rest of the
// mod asks here and never touches another mod's classes.
public final class PressurizedSources {
    private static final Map<String, PressurizedSource> SOURCES = new ConcurrentHashMap<>();

    private PressurizedSources() {}

    public static void register(PressurizedSource source) {
        SOURCES.put(source.id(), source);
    }

    public static @Nullable PressurizedSource get(String id) {
        return SOURCES.get(id);
    }

    public static List<PressurizedSource> all() {
        return List.copyOf(SOURCES.values());
    }

    public static boolean any() {
        return !SOURCES.isEmpty();
    }

    // The source a fluid is a gas of, if any.
    public static Optional<PressurizedSource> forFluid(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return Optional.empty();
        }
        for (PressurizedSource source : SOURCES.values()) {
            if (source.isGas(fluid)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
