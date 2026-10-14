/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.chassis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// A modular chassis (the optical handoff 4-7): a rack device with bays (ChassisLayout) that hold parts (ChassisPart) in
// its item slots - the Optical Transport Shelf, the Chassis Switch and the Blade Enclosure. Parts are hot-swappable:
// taking one out affects only what it gave. A part goes in used on its bay through the rack's open front, or clicked
// onto its bay in the chassis's panel (which also takes parts out). Each bay has a state (its WRKHW status, its light,
// its popup), the rack draws each part's faceplate in its bay (lit while it works), and looking at a part through the
// open door shows that part's popup (RackUnitPayloads, a unit and a bay).
//
// Putting parts in and taking them out needs rack access (RackPermission.RACK); its settings, BUILD; looking, VIEW.
public interface Chassis {
    // Panel actions every chassis takes (its own start at 0).
    int ACTION_INSTALL = 900, ACTION_REMOVE = 901, ACTION_PART_SETTING = 902;

    // A bay's state: its status word in WRKHW (*ONLINE, *ACTIVE, *STANDBY, *FAILED, *EMPTY, *OFFLINE, *PROTECT), its
    // popup status, a detail line (what it gives, why it's down) and whether its faceplate is lit.
    record PartState(String code, RackDeviceInfo.Status status, Component detail, boolean lit) {
        public static PartState empty(Component detail) {
            return new PartState("*EMPTY", RackDeviceInfo.Status.OFFLINE, detail, false);
        }
    }

    ChassisLayout layout();

    // The device itself.
    RackDevice device();

    PartState partState(ChassisLayout.Bay bay);

    // The part's popup lines (after its status).
    default List<RackDeviceInfo.InfoLine> partLines(ChassisLayout.Bay bay) {
        return List.of();
    }

    // The client's copy of what's in each bay (writeClient): the part's ordinal + 1 (0 empty), and 256 when lit.
    int[] shownParts();

    void setShownParts(int[] parts);

    default @Nullable ChassisPart part(ChassisLayout.Bay bay) {
        return ChassisPart.of(device().items().get(bay.slot()));
    }

    default ItemStack stack(ChassisLayout.Bay bay) {
        return device().items().get(bay.slot());
    }

    default int count(ChassisPart part) {
        int count = 0;
        for (ChassisLayout.Bay bay : layout().bays()) {
            if (part(bay) == part) {
                count++;
            }
        }
        return count;
    }

    // Parts in all its bays.
    default int installed() {
        int count = 0;
        for (ChassisLayout.Bay bay : layout().bays()) {
            if (!stack(bay).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // --- Helpers for the chassis ---

    // Its panel's part actions (install from what the player carries, take out). True when the action was one of them.
    static boolean handle(Chassis chassis, ServerPlayer player, int action, int value) {
        RackDevice device = chassis.device();
        RackBlockEntity rack = device.rack();
        ChassisLayout.Bay bay = chassis.layout().bay(value);
        if (action != ACTION_INSTALL && action != ACTION_REMOVE) {
            return false;
        }
        if (rack == null || bay == null) {
            return true;
        }
        if (!NetworkAccess.allowed(player.level(), rack.getBlockPos(), player, RackPermission.RACK)) {
            return true;
        }
        if (action == ACTION_INSTALL) {
            ItemStack carried = player.containerMenu.getCarried();
            if (chassis.stack(bay).isEmpty() && bay.accepts(carried)) {
                device.items().set(bay.slot(), carried.split(1));
                player.containerMenu.setCarried(carried);
                device.itemsChanged();
            }
        } else {
            ItemStack taken = chassis.stack(bay);
            if (!taken.isEmpty()) {
                device.items().set(bay.slot(), ItemStack.EMPTY);
                device.itemsChanged();
                if (player.containerMenu.getCarried().isEmpty()) {
                    player.containerMenu.setCarried(taken);
                } else {
                    player.getInventory().placeItemBackInInventory(taken, Prediction.SERVER_ONLY);
                }
            }
        }
        return true;
    }

    // A part used on its bay through the rack's open front: put in (true when it took it).
    static boolean useOnBay(Chassis chassis, ServerPlayer player, ItemStack stack) {
        ChassisLayout.Bay bay = pick(chassis, player.getEyePosition(), player.getViewVector(1.0F), false);
        if (bay == null || !chassis.stack(bay).isEmpty() || !bay.accepts(stack)) {
            return false;
        }
        chassis.device().items().set(bay.slot(), stack.split(1));
        chassis.device().itemsChanged();
        return true;
    }

    // The bay a line of sight meets on the chassis's front (or back), or null. Its parts' faces are in front of the
    // chassis (z 1.5 in front, 29.5 behind), placed as RackRenderer draws them.
    static ChassisLayout.@Nullable Bay pick(Chassis chassis, Vec3 eye, Vec3 look, boolean rear) {
        RackDevice device = chassis.device();
        RackBlockEntity rack = device.rack();
        if (rack == null) {
            return null;
        }
        Vec3 from = RackGeometry.toLocal(eye, rack.getBlockPos(), rack.facing());
        Vec3 to = RackGeometry.toLocal(eye.add(look.normalize().scale(8)), rack.getBlockPos(), rack.facing());
        double plane = rear ? 29.5 : 1.5;
        if (Math.abs(to.z - from.z) < 1.0E-9) {
            return null;
        }
        double t = (plane - from.z) / (to.z - from.z);
        if (t < 0 || t > 1) {
            return null;
        }
        double x = from.x + (to.x - from.x) * t, y = from.y + (to.y - from.y) * t;
        double tx = rear ? (x - 1.55) * 8 : (14.45 - x) * 8;
        double ty = (device.size() - (y - RackGeometry.unitBottom(device.u()))) * 8;
        return chassis.layout().at(rear, tx, ty);
    }

    // What clients see of its bays.
    static void writeClient(Chassis chassis, ValueOutput output) {
        List<ChassisLayout.Bay> bays = chassis.layout().bays();
        int[] parts = new int[bays.size()];
        for (int i = 0; i < bays.size(); i++) {
            ChassisPart part = chassis.part(bays.get(i));
            parts[i] = part == null ? 0 : part.ordinal() + 1 | (chassis.partState(bays.get(i)).lit() ? 256 : 0);
        }
        output.putIntArray("parts", parts);
    }

    static void readClient(Chassis chassis, ValueInput input) {
        chassis.setShownParts(input.getIntArray("parts").orElse(new int[0]));
    }

    // The client's part in a bay (by its place in the layout), and whether it's lit.
    static @Nullable ChassisPart shownPart(Chassis chassis, int index) {
        int[] parts = chassis.shownParts();
        return index >= 0 && index < parts.length ? ChassisPart.byOrdinal((parts[index] & 255) - 1) : null;
    }

    static boolean shownLit(Chassis chassis, int index) {
        int[] parts = chassis.shownParts();
        return index >= 0 && index < parts.length && (parts[index] & 256) != 0;
    }

    // Every bay's part and state, for its panel.
    static void writePanel(Chassis chassis, ValueOutput output) {
        ValueOutput.ValueOutputList list = output.childrenList("bays");
        for (ChassisLayout.Bay bay : chassis.layout().bays()) {
            ValueOutput child = list.addChild();
            ChassisPart part = chassis.part(bay);
            PartState state = chassis.partState(bay);
            child.putInt("part", part == null ? -1 : part.ordinal());
            child.putString("code", state.code());
            child.putInt("status", state.status().ordinal());
            child.putString("detail", state.detail().getString());
            child.putBoolean("lit", state.lit());
        }
    }

    // A part's popup.
    static RackDeviceInfo describe(Chassis chassis, ChassisLayout.Bay bay) {
        ItemStack stack = chassis.stack(bay);
        PartState state = chassis.partState(bay);
        Component name = stack.isEmpty() ? Component.translatable("hud.encodedlogistics.chassis.empty_bay") : stack.getHoverName();
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        if (!state.detail().getString().isEmpty()) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.chassis.detail"), state.detail()));
        }
        if (!stack.isEmpty()) {
            lines.addAll(chassis.partLines(bay));
        }
        return new RackDeviceInfo(name, state.status(), Component.translatable("hud.encodedlogistics.chassis.code." + state.code().substring(1)
                .toLowerCase(Locale.ROOT)), lines);
    }

    // Its drain: the chassis's own and its parts'.
    static double drain(Chassis chassis) {
        return Config.CHASSIS_DRAIN.getAsDouble() + chassis.installed() * Config.CHASSIS_CARD_DRAIN.getAsDouble();
    }
}
