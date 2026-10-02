/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.function.BiFunction;

import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModItems;

// The parts that mount on a cable side (or, through a part host, on a block face). Each has its models
// (models/part/<id>.json and <id>_<lit>.json for its lit state: a terminal online, a port moving items, a tap attached, a
// sensor emitting), its collision boxes (the model's, modelled on the NORTH face with z = 0 on the face it mounts on), and
// its behaviour (CablePart). Terminals and the sensor face away from a block they're mounted on through a part host;
// ports and taps face it. Every part is a device: terminals use terminalLanes and terminalDrain, the rest partLanes and
// partDrain.
public enum PartType implements StringRepresentable {
    ACCESS_TERMINAL("access_terminal", "online", true, false, Boxes.TERMINAL, TerminalPart::new),
    FABRICATION_TERMINAL("fabrication_terminal", "online", true, false, Boxes.TERMINAL, FabricationTerminalPart::new),
    SCHEMATIC_ENCODER("schematic_encoder", "online", true, false, Boxes.TERMINAL, SchematicEncoderPart::new),
    INGRESS_PORT("ingress_port", "active", false, true, Boxes.PORT, PortPart::new),
    EGRESS_PORT("egress_port", "active", false, true, Boxes.PORT, PortPart::new),
    INVENTORY_TAP("inventory_tap", "active", false, true, Boxes.TAP, InventoryTapPart::new),
    THRESHOLD_SENSOR("threshold_sensor", "on", true, true, Boxes.SENSOR, ThresholdSensorPart::new);

    public static final Codec<PartType> CODEC = StringRepresentable.fromEnum(PartType::values);

    // Pixel boxes {x1, y1, z1, x2, y2, z2} on the NORTH face, from the part models (without their glow elements).
    private static final class Boxes {
        static final double[][] TERMINAL = { { 1, 1, 0, 15, 15, 2.5 }, { 6, 6, 2.5, 10, 10, 5 } };
        static final double[][] PORT = { { 2, 2, 0, 14, 14, 1 }, { 3, 3, 1, 13, 13, 4 }, { 4, 4, 4, 12, 12, 4.5 }, { 6, 6, 4.5, 10, 10, 5 } };
        static final double[][] TAP = { { 1, 1, 0, 15, 15, 1 }, { 1, 1, 1, 15, 2, 2.5 }, { 1, 14, 1, 15, 15, 2.5 }, { 4, 4, 1, 12, 12, 3 },
                { 6, 6, 3, 10, 10, 5 } };
        static final double[][] SENSOR = { { 3, 3, 0.5, 13, 13, 2 }, { 6, 6, 2, 10, 10, 5 }, { 6, 6, 0, 10, 10, 0.5 } };
    }

    private final String id, lit;
    private final boolean facesOut, ticks;
    private final double[][] boxes;
    private final BiFunction<CableBlockEntity, Direction, CablePart> factory;

    PartType(String id, String lit, boolean facesOut, boolean ticks, double[][] boxes, Factory factory) {
        this.id = id;
        this.lit = lit;
        this.facesOut = facesOut;
        this.ticks = ticks;
        this.boxes = boxes;
        this.factory = (host, side) -> factory.create(this, host, side);
    }

    @FunctionalInterface
    interface Factory {
        CablePart create(PartType type, CableBlockEntity host, Direction side);
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    // The part model, unlit and lit: part/<id> and part/<id>_<lit>.
    public String model(boolean lit) {
        return lit ? "part/" + id + "_" + this.lit : "part/" + id;
    }

    // On a part host: true when the part faces away from the block it's mounted on (terminals, the sensor), false when
    // it faces the block (ports, the tap).
    public boolean facesOut() {
        return facesOut;
    }

    public boolean ticks() {
        return ticks;
    }

    public boolean isTerminal() {
        return this == ACCESS_TERMINAL || this == FABRICATION_TERMINAL || this == SCHEMATIC_ENCODER;
    }

    public double[][] boxes() {
        return boxes;
    }

    public Item item() {
        return ModItems.part(this).get();
    }

    public int lanes() {
        return isTerminal() ? Config.TERMINAL_LANES.getAsInt() : Config.PART_LANES.getAsInt();
    }

    public double drain() {
        return isTerminal() ? Config.TERMINAL_DRAIN.getAsDouble() : Config.PART_DRAIN.getAsDouble();
    }

    public CablePart create(CableBlockEntity host, Direction side) {
        return factory.apply(host, side);
    }

    public static PartType byItem(Item item) {
        for (PartType type : values()) {
            if (type.item() == item) {
                return type;
            }
        }
        return null;
    }
}
