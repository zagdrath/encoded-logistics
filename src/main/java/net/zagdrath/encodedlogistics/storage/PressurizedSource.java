/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.access.ItemAccess;

// A mod whose gases or chemicals the network can store as PRESSURIZED resources, each under that mod's own id and unit
// (there is no conversion between mods). Registered with PressurizedSources by its integration once the mod is known to
// be present with an API this was built against (compat/arcforge/ArcforgeGases); with none registered, nothing is
// pressurized and the type has nothing to store.
//
// A source whose gases are NeoForge fluids (Arcforge's) answers fluid(): blocks then move them through NeoForge's fluid
// capability, which carries them as fluids, and the network keeps them as PRESSURIZED rather than FLUID keys.
public interface PressurizedSource {
    // The source's id, also the namespace players filter by: "arcforge".
    String id();

    // Whether a fluid is one of this source's gases (so storage keeps it as PRESSURIZED, under this source).
    boolean isGas(Fluid fluid);

    // The fluid a gas is, for a source whose gases are fluids.
    Optional<Fluid> fluid(Identifier gas);

    Component name(Identifier gas);

    // 0xAARRGGBB, for drawing the gas where it has no texture of its own.
    int color(Identifier gas);

    // Every gas the source has, for the filter picker; sorted by id.
    List<Identifier> all();

    // A gas container item's tank (a Gas Cartridge), or null when the item has none.
    @Nullable Container container(ItemAccess access);

    // One gas tank in an item: what it holds, and filling and emptying it (simulated or not), in the source's unit.
    interface Container {
        @Nullable Identifier gas();

        long amount();

        long insert(Identifier gas, long amount, boolean simulate);

        long extract(Identifier gas, long amount, boolean simulate);
    }
}
