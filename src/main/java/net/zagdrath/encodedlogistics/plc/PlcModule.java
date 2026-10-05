/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.plc;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.StringRepresentable;

// What's in one of a PLC's four module slots (docs/plc HANDOFF 2): nothing, or a sensor module. The block state's
// slot1..slot4 show it (models block/plc/slotN_<module>); RTVSNSVAL TYPE() names it (*PRESENCE ...).
public enum PlcModule implements StringRepresentable {
    EMPTY("empty", "*NONE"),
    PRESENCE_SENSOR("presence_sensor", "*PRESENCE"),
    INVENTORY_SENSOR("inventory_sensor", "*INVENTORY"),
    FLUID_SENSOR("fluid_sensor", "*FLUID"),
    LIGHT_SENSOR("light_sensor", "*LIGHT"),
    TIMER_MODULE("timer_module", "*TIMER");

    private final String name, special;

    PlcModule(String name, String special) {
        this.name = name;
        this.special = special;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    // RTVSNSVAL TYPE()'s value for it.
    public String special() {
        return special;
    }

    // Its name in messages (ELC1501): "Inventory Sensor"; an empty slot's (TYPE(*ANY)) "sensor module".
    public String label() {
        return switch (this) {
            case EMPTY -> "sensor module";
            case PRESENCE_SENSOR -> "Presence Sensor";
            case INVENTORY_SENSOR -> "Inventory Sensor";
            case FLUID_SENSOR -> "Fluid Sensor";
            case LIGHT_SENSOR -> "Light Sensor";
            case TIMER_MODULE -> "Timer Module";
        };
    }

    // Its item's lang key ("" for an empty slot).
    public String descriptionId() {
        return this == EMPTY ? "" : "item.encodedlogistics." + name;
    }

    // Whether it has settings to change (PLCMOD 2=Change setting): a radius and what it counts, or a face.
    public boolean hasSetting() {
        return this == PRESENCE_SENSOR || this == INVENTORY_SENSOR || this == FLUID_SENSOR;
    }

    public static @Nullable PlcModule bySpecial(String special) {
        for (PlcModule module : values()) {
            if (module.special.equalsIgnoreCase(special)) {
                return module;
            }
        }
        return null;
    }

    public static PlcModule byName(String name) {
        for (PlcModule module : values()) {
            if (module.name.equals(name.toLowerCase(Locale.ROOT))) {
                return module;
            }
        }
        return EMPTY;
    }
}
