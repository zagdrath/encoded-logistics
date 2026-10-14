/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.chassis;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// What goes into a modular chassis's bays (the optical handoff 4-6): the Chassis Switch's supervisor, line cards and
// power supplies, the Optical Transport Shelf's controller and line cards, and the Blade Enclosure's blades, power
// supplies and rear modules. Each is an item (ChassisPartItem) that fits one kind of bay, drawn in its bay from
// models/block/rack_card/<id>.json (and <id>_on.json lit).
public enum ChassisPart {
    SUPERVISOR_CARD(Kind.SUPERVISOR),
    ACCESS_CARD(Kind.LINE),
    UPLINK_CARD(Kind.LINE),
    ROUTING_CARD(Kind.LINE),
    TRUNK_CARD(Kind.LINE),
    CHASSIS_PSU(Kind.CHASSIS_PSU),
    CONTROLLER_CARD(Kind.SHELF_CONTROLLER),
    TRANSPONDER_CARD(Kind.SHELF_LINE),
    MUX_CARD(Kind.SHELF_LINE),
    AMPLIFIER_CARD(Kind.SHELF_LINE),
    PROTECTION_CARD(Kind.SHELF_LINE),
    COMPUTE_BLADE(Kind.BLADE),
    MEMORY_BLADE(Kind.BLADE),
    FABRICATION_BLADE(Kind.BLADE),
    STORAGE_BLADE(Kind.BLADE),
    INTERCONNECT_MODULE(Kind.INTERCONNECT),
    BLADE_PSU(Kind.BLADE_PSU),
    MANAGEMENT_MODULE(Kind.MANAGEMENT);

    // The kinds of bay.
    public enum Kind {
        SUPERVISOR, LINE, CHASSIS_PSU, SHELF_CONTROLLER, SHELF_LINE, BLADE, INTERCONNECT, BLADE_PSU, MANAGEMENT
    }

    private static final ChassisPart[] VALUES = values();
    private final Kind kind;

    ChassisPart(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    // Its item's id: supervisor_card, compute_blade.
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable ChassisPart of(ItemStack stack) {
        return stack.getItem() instanceof ChassisPartItem item ? item.part() : null;
    }

    public static @Nullable ChassisPart byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }

    // Its faceplate's model in a bay: dark, or lit.
    public Identifier model(boolean lit) {
        return EncodedLogistics.id("block/rack_card/" + id() + (lit ? "_on" : ""));
    }

    // Its faceplate's texture (the bay's w x h texels from (0, 0) of a 128 x 64 sheet), for the rack screen.
    public Identifier texture() {
        return EncodedLogistics.id("textures/block/rack_card/" + id() + ".png");
    }
}
