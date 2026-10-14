/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.elcl.exec.RoutingCommands;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.chassis.Chassis;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;

// The Chassis Switch (10U, the optical handoff 5): an L3 Switch built from cards. Two supervisors (A and B, the top row):
// the active one runs the switch, and if it fails or is taken out the standby takes over in the same tick, traffic and
// all (*SUPFAILOVER). Eight line cards: an Access Card gives accessCardLanes rack-local lanes (an L2 Switch's pool), an
// Uplink Card four Optical Transceiver cages (the pool's uplinks: aggregated, any one keeps it up), a Routing Card the
// L3 Switch's routes and QoS (each card adds routingCardRate items a second), a Trunk Card lets its ports be VLAN
// trunks. Four power supplies (the bottom row): it runs on any one, degraded below two. Every card is hot-swappable;
// taking one out affects only what it gave. It drains chassisDrain FE/t and chassisCardDrain a card.
public class ChassisSwitchDevice extends L3SwitchDevice implements Chassis {
    private static final int SUP_A = 0, SUP_B = 1;

    private int[] shownParts = new int[0];
    // The active supervisor's bay (-1: none), and the last one to fail (its bay and when).
    private int active = -1;
    private int failedBay = -1;
    private long failedAt = -1;

    public ChassisSwitchDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public ChassisLayout layout() {
        return ChassisLayout.SWITCH;
    }

    @Override
    public ChassisSwitchDevice device() {
        return this;
    }

    @Override
    public int[] shownParts() {
        return shownParts;
    }

    @Override
    public void setShownParts(int[] parts) {
        shownParts = parts;
    }

    private boolean has(int slot, ChassisPart part) {
        return ChassisPart.of(items().get(slot)) == part;
    }

    private ChassisLayout.Bay bay(int slot) {
        return layout().bay(slot);
    }

    // --- What it has ---

    public int supervisors() {
        return (has(SUP_A, ChassisPart.SUPERVISOR_CARD) ? 1 : 0) + (has(SUP_B, ChassisPart.SUPERVISOR_CARD) ? 1 : 0);
    }

    public int activeSupervisor() {
        return active;
    }

    public int powerSupplies() {
        return count(ChassisPart.CHASSIS_PSU);
    }

    // Running: a supervisor and a power supply (on its rack's power).
    public boolean running() {
        return active >= 0 && powerSupplies() > 0;
    }

    public boolean degraded() {
        return running() && (powerSupplies() < 2 || supervisors() < 2);
    }

    // An Uplink Card's cages with a transceiver in them, and all its cages.
    private int cagesFilled(int lineSlot) {
        int filled = 0;
        int first = ChassisLayout.SWITCH_CAGES_FROM + (lineSlot - 2) * ChassisLayout.CAGES_PER_CARD;
        for (int k = 0; k < ChassisLayout.CAGES_PER_CARD; k++) {
            if (!items().get(first + k).isEmpty()) {
                filled++;
            }
        }
        return filled;
    }

    public int activeUplinks() {
        int up = 0;
        for (int slot = 2; slot < 10; slot++) {
            if (has(slot, ChassisPart.UPLINK_CARD)) {
                up += cagesFilled(slot);
            }
        }
        return up;
    }

    public int totalUplinks() {
        return count(ChassisPart.UPLINK_CARD) * ChassisLayout.CAGES_PER_CARD;
    }

    // --- As a switch ---

    // The Access Cards' lanes, while it runs with an uplink.
    @Override
    public int capacity() {
        return running() && activeUplinks() > 0 ? count(ChassisPart.ACCESS_CARD) * Config.ACCESS_CARD_LANES.getAsInt() : 0;
    }

    @Override
    public double drain() {
        return Chassis.drain(this);
    }

    @Override
    protected boolean routing() {
        return running() && count(ChassisPart.ROUTING_CARD) > 0;
    }

    @Override
    protected int rate() {
        return count(ChassisPart.ROUTING_CARD) * Config.ROUTING_CARD_RATE.getAsInt();
    }

    // --- Supervisors ---

    @Override
    public void tick(ServerLevel level) {
        failover(level);
        super.tick(level);
    }

    // The active supervisor gone: the standby takes over (fired once). None active yet: the first there.
    private void failover(@Nullable ServerLevel level) {
        if (active >= 0 && has(active, ChassisPart.SUPERVISOR_CARD)) {
            return;
        }
        int standby = has(SUP_A, ChassisPart.SUPERVISOR_CARD) ? SUP_A : has(SUP_B, ChassisPart.SUPERVISOR_CARD) ? SUP_B : -1;
        if (standby == active) {
            return;
        }
        if (active >= 0 && level != null) {
            failedBay = active;
            failedAt = level.getGameTime();
            String from = bay(active).label(), to = standby >= 0 ? bay(standby).label() : "-";
            ElclEvents.hardware(level.getServer(), rack() != null ? rack().network(this) : null, standby >= 0 ? "*SUPFAILOVER" : "*CARDFAULT",
                    deviceName(), from, standby >= 0 ? "failover to " + to : "no supervisor");
        }
        active = standby;
        changed(true);
    }

    @Override
    public void itemsChanged() {
        RackBlockEntity rack = rack();
        ServerLevel level = rack != null && rack.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
        failover(level);
        // The failed supervisor's bay filled again.
        if (failedBay >= 0 && has(failedBay, ChassisPart.SUPERVISOR_CARD)) {
            failedBay = -1;
        }
        // A power supply taken out.
        if (level != null && powerSupplies() < 4 && powerSupplies() < lastPowerSupplies) {
            ElclEvents.hardware(level.getServer(), rack.network(this), "*CARDFAULT", deviceName(), "PSU",
                    powerSupplies() + "/4 power supplies");
        }
        lastPowerSupplies = powerSupplies();
        // Its lanes, uplinks and drain may have changed.
        changed(true);
    }

    private int lastPowerSupplies = -1;

    @Override
    public RackDeviceInfo.Status status() {
        if (!isOnline()) {
            return RackDeviceInfo.Status.OFFLINE;
        }
        if (!running()) {
            return RackDeviceInfo.Status.FAULT;
        }
        return degraded() ? RackDeviceInfo.Status.WARNING : RackDeviceInfo.Status.ONLINE;
    }

    @Override
    public Component statusText() {
        return status() == RackDeviceInfo.Status.WARNING ? Component.translatable("hud.encodedlogistics.chassis.degraded") : super.statusText();
    }

    @Override
    public @Nullable String modelVariant() {
        return status() == RackDeviceInfo.Status.WARNING ? RackDeviceType.DEGRADED : null;
    }

    // --- Its bays ---

    @Override
    public PartState partState(ChassisLayout.Bay bay) {
        ChassisPart part = part(bay);
        int psus = powerSupplies();
        if (bay.kind() == ChassisPart.Kind.SUPERVISOR) {
            if (part == null) {
                return bay.slot() == failedBay && failedAt >= 0
                        ? new PartState("*FAILED", RackDeviceInfo.Status.FAULT, Component.translatable("hud.encodedlogistics.chassis.failed_over",
                                active >= 0 ? bay(active).label() : "-", clock(failedAt)), false)
                        : PartState.empty(Component.empty());
            }
            boolean on = isOnline() && psus > 0;
            if (bay.slot() == active) {
                return new PartState("*ACTIVE", on ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE,
                        Component.translatable(supervisors() > 1 ? "hud.encodedlogistics.chassis.standby_ready" : "hud.encodedlogistics.chassis.standby_lost"), on);
            }
            return new PartState("*STANDBY", on ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE, Component.empty(), on);
        }
        if (bay.kind() == ChassisPart.Kind.CHASSIS_PSU) {
            Component detail = Component.translatable("hud.encodedlogistics.chassis.psus", psus, 4);
            if (part == null) {
                return PartState.empty(detail);
            }
            return new PartState("*ONLINE", isOnline() ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE, detail, isOnline());
        }
        if (part == null) {
            return PartState.empty(Component.empty());
        }
        boolean on = isOnline() && running();
        RackDeviceInfo.Status status = on ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE;
        return switch (part) {
            case ACCESS_CARD -> new PartState(on ? "*ONLINE" : "*OFFLINE", status,
                    Component.translatable("hud.encodedlogistics.chassis.lanes", Config.ACCESS_CARD_LANES.getAsInt()), on);
            case UPLINK_CARD -> {
                int filled = cagesFilled(bay.slot());
                yield filled == 0 ? new PartState("*FAILED", on ? RackDeviceInfo.Status.FAULT : status,
                        Component.translatable("hud.encodedlogistics.chassis.no_transceivers"), false)
                        : new PartState(on ? "*ONLINE" : "*OFFLINE", status, Component.translatable("hud.encodedlogistics.chassis.cages", filled,
                                ChassisLayout.CAGES_PER_CARD), on);
            }
            case ROUTING_CARD -> new PartState(on ? "*ONLINE" : "*OFFLINE", status,
                    Component.translatable("hud.encodedlogistics.chassis.rate", Config.ROUTING_CARD_RATE.getAsInt()), on);
            case TRUNK_CARD -> new PartState(on ? "*ONLINE" : "*OFFLINE", status, Component.translatable("hud.encodedlogistics.chassis.trunks"), on);
            default -> new PartState("*FAILED", RackDeviceInfo.Status.FAULT, Component.empty(), false);
        };
    }

    private String clock(long gameTime) {
        return rack() != null && rack().getLevel() instanceof ServerLevel level ? RoutingCommands.clock(level.getServer(), gameTime).substring(0, 5) : "";
    }

    // "B active, A failed", "A active, B standby", "none".
    public Component supervisorText() {
        if (active < 0) {
            return Component.translatable("hud.encodedlogistics.chassis.no_supervisor");
        }
        String self = bay(active).label(), other = active == SUP_A ? "B" : "A";
        int otherSlot = active == SUP_A ? SUP_B : SUP_A;
        String key = has(otherSlot, ChassisPart.SUPERVISOR_CARD) ? "standby" : otherSlot == failedBay ? "failed" : "empty";
        return Component.translatable("hud.encodedlogistics.chassis.supervisor." + key, self, other);
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        RackBlockEntity rack = rack();
        RackBlockEntity.Lanes lanes = rack != null ? rack.lanes() : RackBlockEntity.Lanes.NONE;
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.chassis.supervisor"), supervisorText()));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.switch.lanes"),
                Component.literal(lanes.poolUsed() + " / " + lanes.poolCapacity())));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.chassis.uplinks"),
                Component.literal(activeUplinks() + " / " + totalUplinks())));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.chassis.psu"), Component.literal(powerSupplies() + " / 4")));
        if (count(ChassisPart.ROUTING_CARD) > 0) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.l3.routes"), Component.literal(Integer.toString(routes().size()))));
        }
        return lines;
    }

    // --- Using it ---

    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, boolean atUnit) {
        return atUnit && Chassis.useOnBay(this, player, stack);
    }

    // Parts in and out; a trunk port only with a Trunk Card.
    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (Chassis.handle(this, player, action, value)) {
            return;
        }
        RackBlockEntity rack = rack();
        if (action == ACTION_PORT_MODE && rack != null && count(ChassisPart.TRUNK_CARD) == 0) {
            var device = rack.deviceAt(value);
            if (device != null && !device.trunk()) {
                player.sendOverlayMessage(Component.translatable("message.encodedlogistics.chassis.needs_trunk_card"));
                return;
            }
        }
        super.handleAction(player, action, value, text);
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        super.writePanel(output, viewer);
        Chassis.writePanel(this, output);
        output.putString("supervisor", supervisorText().getString());
        output.putInt("sup_active", active);
        output.putInt("psus", powerSupplies());
        output.putInt("uplinks", activeUplinks());
        output.putInt("uplinks_total", totalUplinks());
        output.putInt("lanes", capacity());
        output.putBoolean("running", running());
        output.putBoolean("trunk_card", count(ChassisPart.TRUNK_CARD) > 0);
        output.putBoolean("routing_card", count(ChassisPart.ROUTING_CARD) > 0);
    }

    @Override
    public void writeClient(ValueOutput output) {
        Chassis.writeClient(this, output);
    }

    @Override
    public void readClient(ValueInput input) {
        Chassis.readClient(this, input);
    }

    // --- Saving: its routes and QoS go with its item; its cards come out as items ---

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
        saveItems(output);
        output.putInt("active", active);
        if (failedBay >= 0) {
            output.putInt("failed_bay", failedBay);
            output.putLong("failed_at", failedAt);
        }
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
        loadItems(input);
        active = input.getIntOr("active", -1);
        failedBay = input.getIntOr("failed_bay", -1);
        failedAt = input.getLongOr("failed_at", -1);
        lastPowerSupplies = powerSupplies();
    }
}
