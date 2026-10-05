/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.plc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;

// What a PLC's sensor modules read (docs/plc HANDOFF 2), once each time a program asks (RTVSNSVAL) and for the Modules
// screen: a value, a status (*OK, *NOMODULE, *NOTARGET) and an extra value (RTNAUX).
//   presence_sensor   players or mobs within its radius (1-16; *PLAYERS, *MOBS or *ALL): the count; extra the nearest's name
//   inventory_sensor  the container on its face: how full, percent (as a comparator sees it, by slot); extra the item count
//   fluid_sensor      the tank on its face: how full, percent; extra the amount in mB
//   light_sensor      the light at the PLC: 0-15; extra *DAY or *NIGHT
//   timer_module      the game clock: game ticks; extra the day and time ("Day 2 14:32")
public final class PlcSensors {
    public static final List<String> COUNTS = List.of("*PLAYERS", "*MOBS", "*ALL");

    // A slot's settings (PLCMOD 2=Change setting): the presence sensor's radius and what it counts, the inventory and
    // fluid sensors' face (null: behind the PLC, the block it hangs on).
    public record Setting(int radius, String count, java.util.Optional<Direction> face) {
        public static final Setting DEFAULT = new Setting(8, "*PLAYERS", java.util.Optional.empty());
        public static final Codec<Setting> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("radius", 8).forGetter(Setting::radius),
                Codec.STRING.optionalFieldOf("count", "*PLAYERS").forGetter(Setting::count),
                Direction.CODEC.optionalFieldOf("face").forGetter(Setting::face)).apply(i, Setting::new));

        public Direction faceOf(Direction facing) {
            return face.orElse(facing.getOpposite());
        }

        // As the Modules screen shows it: "r 8 *PLAYERS", "face *WEST", or "".
        public String shown(PlcModule module, Direction facing) {
            return switch (module) {
                case PRESENCE_SENSOR -> "r " + radius + " " + count;
                case INVENTORY_SENSOR, FLUID_SENSOR -> "face " + side(faceOf(facing));
                default -> "";
            };
        }
    }

    public record Reading(BigDecimal value, String status, String aux) {
        static Reading ok(long value, String aux) {
            return new Reading(BigDecimal.valueOf(value), "*OK", aux);
        }

        static Reading noTarget(String what) {
            return new Reading(BigDecimal.ZERO, "*NOTARGET", what);
        }

        public boolean ok() {
            return status.equals("*OK");
        }
    }

    public static final Reading NO_MODULE = new Reading(BigDecimal.ZERO, "*NOMODULE", "");

    private PlcSensors() {}

    public static String side(Direction face) {
        return "*" + face.getSerializedName().toUpperCase(Locale.ROOT);
    }

    public static Reading read(ServerLevel level, BlockPos pos, Direction facing, PlcModule module, Setting setting) {
        return switch (module) {
            case EMPTY -> NO_MODULE;
            case PRESENCE_SENSOR -> presence(level, pos, setting);
            case INVENTORY_SENSOR -> inventory(level, pos, setting.faceOf(facing));
            case FLUID_SENSOR -> fluid(level, pos, setting.faceOf(facing));
            case LIGHT_SENSOR -> light(level, pos);
            case TIMER_MODULE -> Reading.ok(level.getGameTime(), ElclSystem.clock(level.getOverworldClockTime(), false).replace("  ", " "));
        };
    }

    private static Reading presence(ServerLevel level, BlockPos pos, Setting setting) {
        int radius = Math.clamp(setting.radius(), 1, 16);
        AABB box = new AABB(pos).inflate(radius);
        Vec3 center = Vec3.atCenterOf(pos);
        boolean players = !setting.count().equals("*MOBS"), mobs = !setting.count().equals("*PLAYERS");
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator()
                && (players && e instanceof Player || mobs && e instanceof Mob) && e.position().distanceTo(center) <= radius);
        LivingEntity nearest = null;
        for (LivingEntity entity : found) {
            if (nearest == null || entity.distanceToSqr(center) < nearest.distanceToSqr(center)) {
                nearest = entity;
            }
        }
        return Reading.ok(found.size(), nearest == null ? "" : nearest.getName().getString());
    }

    private static Reading inventory(ServerLevel level, BlockPos pos, Direction face) {
        BlockPos target = pos.relative(face);
        ResourceHandler<ItemResource> handler = level.isLoaded(target) ? level.getCapability(Capabilities.Item.BLOCK, target, face.getOpposite()) : null;
        if (handler == null || handler.size() == 0) {
            return Reading.noTarget(side(face));
        }
        double fill = 0;
        long count = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            long amount = handler.getAmountAsLong(slot);
            if (resource.isEmpty() || amount <= 0) {
                continue;
            }
            count += amount;
            long capacity = handler.getCapacityAsLong(slot, resource);
            fill += capacity > 0 ? Math.min(1.0, (double) amount / capacity) : 0;
        }
        return Reading.ok(Math.round(fill * 100 / handler.size()), Long.toString(count));
    }

    private static Reading fluid(ServerLevel level, BlockPos pos, Direction face) {
        BlockPos target = pos.relative(face);
        ResourceHandler<FluidResource> handler = level.isLoaded(target) ? level.getCapability(Capabilities.Fluid.BLOCK, target, face.getOpposite()) : null;
        if (handler == null || handler.size() == 0) {
            return Reading.noTarget(side(face));
        }
        long amount = 0, capacity = 0;
        for (int tank = 0; tank < handler.size(); tank++) {
            FluidResource resource = handler.getResource(tank);
            amount += handler.getAmountAsLong(tank);
            capacity += handler.getCapacityAsLong(tank, resource);
        }
        long percent = capacity > 0 ? BigDecimal.valueOf(amount * 100).divide(BigDecimal.valueOf(capacity), 0, RoundingMode.HALF_UP).longValue() : 0;
        return Reading.ok(percent, Long.toString(amount));
    }

    private static Reading light(ServerLevel level, BlockPos pos) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24_000L);
        // Day from sunrise to dusk (as villagers and beds see it).
        boolean day = time < 12_542 || time > 23_460;
        return Reading.ok(level.getMaxLocalRawBrightness(pos), day ? "*DAY" : "*NIGHT");
    }
}
