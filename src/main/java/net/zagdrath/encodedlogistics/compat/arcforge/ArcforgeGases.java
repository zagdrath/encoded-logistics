/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.arcforge;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.zagdrath.arcforge.api.gas.Gas;
import net.zagdrath.arcforge.api.gas.GasCapabilities;
import net.zagdrath.arcforge.api.gas.GasHandler;
import net.zagdrath.arcforge.api.gas.GasRegistry;
import net.zagdrath.arcforge.api.gas.GasTank;
import net.zagdrath.encodedlogistics.storage.PressurizedSource;

// Arcforge's gases as PRESSURIZED resources, through its gas API (1.1 and later; ArcforgeCompat registers this only
// then). An Arcforge gas is a NeoForge fluid (GasRegistry.isGas: lighter than air, or #arcforge:gases), kept in mB under
// its fluid id, so blocks move it through NeoForge's fluid capability like any fluid; this adds which fluids are gases,
// their names and colours, and Gas Cartridges (GasCapabilities.ITEM).
final class ArcforgeGases implements PressurizedSource {
    static final String ID = "arcforge";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isGas(Fluid fluid) {
        return GasRegistry.isGas(fluid);
    }

    @Override
    public Optional<Fluid> fluid(Identifier gas) {
        return GasRegistry.get(gas).map(Gas::fluid);
    }

    @Override
    public Component name(Identifier gas) {
        return GasRegistry.get(gas).map(Gas::displayName).orElseGet(() -> Component.literal(gas.toString()));
    }

    @Override
    public int color(Identifier gas) {
        return GasRegistry.get(gas).map(Gas::color).orElse(Gas.DEFAULT_COLOR);
    }

    @Override
    public List<Identifier> all() {
        return GasRegistry.all().stream().map(Gas::id).toList();
    }

    @Override
    public @Nullable Container container(ItemAccess access) {
        GasHandler handler = access.getCapability(GasCapabilities.ITEM);
        return handler == null ? null : new Cartridge(handler);
    }

    // A gas item's first tank (a Gas Cartridge holds one gas at a time).
    private record Cartridge(GasHandler handler) implements Container {
        @Override
        public @Nullable Identifier gas() {
            GasTank tank = handler.tank(0);
            return tank.isEmpty() ? null : tank.gas().map(Gas::id).orElse(null);
        }

        @Override
        public long amount() {
            return handler.tank(0).amount();
        }

        @Override
        public long insert(Identifier gas, long amount, boolean simulate) {
            return GasRegistry.get(gas).map(found -> handler.insert(found, (int) Math.min(amount, Integer.MAX_VALUE), simulate)).orElse(0);
        }

        @Override
        public long extract(Identifier gas, long amount, boolean simulate) {
            return GasRegistry.get(gas).map(found -> handler.extract(found, (int) Math.min(amount, Integer.MAX_VALUE), simulate)).orElse(0);
        }
    }
}
