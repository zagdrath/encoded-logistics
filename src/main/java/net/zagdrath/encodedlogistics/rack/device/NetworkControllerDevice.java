/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerBuffer;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;

// A rack Network Controller (2U or 4U): the network core in rack form. Its rack is a lane source for the network (it
// hands out lanes() lanes; LaneSolver) and its buffer is the network's working energy, as a controller structure's
// blocks are (ControllerStructures runs both). A 2U matches a frame of rackController2uEnergyBlocks controller blocks for
// energy, receive rate and drain, and rackController2uLaneFaces connected faces for lanes; a 4U is twice that.
//
// Two of the same size on one network are a redundant pair: one active, one standby (which keeps its own buffer topped up
// from the surplus and draws rackControllerStandbyFactor of its drain). Which is active is saved; when it loses power,
// faults or goes, the standby takes over within FAILOVER_TICKS (the network's FAILOVER status). "Switch over" on its
// panel does the same on purpose. ControllerStructures decides all that and tells each controller how it stands (view);
// this keeps the buffer, the saved role and what its front, popup and panel show.
public class NetworkControllerDevice extends RackDevice implements ControllerBuffer {
    public static final int ACTION_SWITCH_OVER = 0;
    public static final int FAILOVER_TICKS = 20;

    // How it stands on its network: off (no network running), standalone (no partner) or one of a pair, in a switchover,
    // or in conflict with another controller, or faulted.
    public enum Shown {
        OFF, ACTIVE, ACTIVE_PAIR, STANDBY, TAKING_OVER, HANDING_OVER, CONFLICT, FAULT;

        private static final Shown[] VALUES = values();

        public static Shown byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : OFF;
        }

        public boolean failover() {
            return this == TAKING_OVER || this == HANDING_OVER;
        }
    }

    private final Buffer energy = new Buffer();
    // Saved: the active one of a pair, when it last switched over (the overworld's clock, -1 never), and a fault.
    private boolean active;
    private long lastFailover = -1;
    private boolean fault;
    private boolean switchRequested;
    // Live (server; the shown state also on clients, as synced).
    private Shown shown = Shown.OFF;
    private Component partner = Component.empty();
    private int lanesUsed, lanesTotal, devices;
    private long networkStored, networkCapacity;
    private double usage;

    public NetworkControllerDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    protected Priority defaultPriority() {
        return Priority.HIGH;
    }

    // --- Size ---

    private int scale() {
        return size() >= 4 ? 2 : 1;
    }

    // Controller blocks' worth of energy, receive rate and drain.
    public int blocks() {
        return Config.RACK_CONTROLLER_2U_ENERGY_BLOCKS.getAsInt() * scale();
    }

    public int lanes() {
        return Config.RACK_CONTROLLER_2U_LANE_FACES.getAsInt() * Config.LANES_PER_CONTROLLER_FACE.getAsInt() * scale();
    }

    // Its full drain while active, and its idle draw as the standby.
    public double activeDrain() {
        return Config.CONTROLLER_DRAIN.getAsDouble() * blocks();
    }

    public double idleDrain() {
        return activeDrain() * Config.RACK_CONTROLLER_STANDBY_FACTOR.getAsDouble();
    }

    @Override
    public double drain() {
        return active || shown == Shown.OFF ? activeDrain() : idleDrain();
    }

    @Override
    public int laneCost() {
        return 0;
    }

    // --- The pair (ControllerStructures) ---

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            // Its drain changed.
            changed(true);
        }
    }

    // Not faulted: it can run the network.
    public boolean usable() {
        return !fault;
    }

    public boolean faulted() {
        return fault;
    }

    public void setFault(boolean fault) {
        if (this.fault != fault) {
            this.fault = fault;
            changed(true);
        }
    }

    public long lastFailover() {
        return lastFailover;
    }

    public void setLastFailover(long time) {
        lastFailover = time;
        saveOnly();
    }

    // A Switch over asked for from its panel since the last call.
    public boolean takeSwitchRequest() {
        boolean requested = switchRequested;
        switchRequested = false;
        return requested;
    }

    public Shown shown() {
        return shown;
    }

    // How it stands, and what its popup and panel show about its network (its partner, or the controller it's in conflict
    // with).
    public void view(Shown shown, Component partner, int lanesUsed, int lanesTotal, int devices, long stored, long capacity, double usage) {
        this.partner = partner;
        this.lanesUsed = lanesUsed;
        this.lanesTotal = lanesTotal;
        this.devices = devices;
        this.networkStored = stored;
        this.networkCapacity = capacity;
        this.usage = usage;
        if (this.shown != shown) {
            this.shown = shown;
            changed(false);
        }
    }

    // Off every controlled network (its rack's network gone): nothing to show.
    public void clearView() {
        view(Shown.OFF, Component.empty(), 0, 0, 0, 0, 0, 0);
    }

    // --- Energy ---

    @Override
    public int getEnergy() {
        return energy.getAmountAsInt();
    }

    @Override
    public int getCapacity() {
        energy.refreshLimits();
        return energy.getCapacityAsInt();
    }

    @Override
    public int drain(int amount) {
        int taken = Math.min(amount, energy.getAmountAsInt());
        if (taken > 0) {
            energy.set(energy.getAmountAsInt() - taken);
        }
        return taken;
    }

    @Override
    public int fill(int amount, TransactionContext transaction) {
        return energy.fill(amount, transaction);
    }

    @Override
    public int takeReceived() {
        int received = energy.receivedThisTick;
        energy.receivedThisTick = 0;
        energy.refreshLimits();
        return received;
    }

    // --- Status and popup ---

    @Override
    public RackDeviceInfo.Status status() {
        return switch (shown) {
            case CONFLICT, FAULT -> RackDeviceInfo.Status.FAULT;
            case TAKING_OVER, HANDING_OVER -> isOnline() ? RackDeviceInfo.Status.WARNING : super.status();
            default -> fault ? RackDeviceInfo.Status.FAULT : super.status();
        };
    }

    @Override
    public Component statusText() {
        return switch (shown) {
            case CONFLICT -> Component.translatable("hud.encodedlogistics.status.conflict");
            case TAKING_OVER, HANDING_OVER -> isOnline() ? Component.translatable("hud.encodedlogistics.status.failover") : super.statusText();
            case ACTIVE_PAIR -> isOnline() ? Component.translatable("hud.encodedlogistics.controller.role.active") : super.statusText();
            case STANDBY -> isOnline() ? Component.translatable("hud.encodedlogistics.controller.role.standby") : super.statusText();
            default -> super.statusText();
        };
    }

    @Override
    public @Nullable String modelVariant() {
        return switch (shown) {
            case CONFLICT -> "_conflict";
            case TAKING_OVER, HANDING_OVER -> isOnline() ? "_failover" : null;
            case STANDBY -> isOnline() ? "_standby" : null;
            default -> null;
        };
    }

    public Component role() {
        return Component.translatable("hud.encodedlogistics.controller.role." + switch (shown) {
            case ACTIVE_PAIR -> "active_pair";
            case STANDBY, HANDING_OVER -> "standby";
            case TAKING_OVER -> "taking_over";
            case CONFLICT -> "conflict";
            default -> "standalone";
        });
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.controller.role"), role()));
        if (shown == Shown.CONFLICT) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.controller.with"), partner));
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.controller.lanes"), Component.literal("0 / " + lanes())));
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.controller.devices"),
                    Component.translatable("gui.encodedlogistics.rack.status.offline")));
            return lines;
        }
        if (shown.failover()) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.controller.lanes"),
                    Component.translatable("hud.encodedlogistics.status.paused")));
        } else {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.controller.lanes"),
                    Component.literal(lanesUsed + " / " + lanesTotal),
                    new RackDeviceInfo.Bar(lanesTotal == 0 ? 0 : (float) lanesUsed / lanesTotal, RackDeviceInfo.BarStyle.NORMAL)));
        }
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.controller.energy"), energyText()));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.controller.devices"), Component.literal(Integer.toString(devices))));
        return lines;
    }

    // The network's energy, or the standby's idle draw.
    private Component energyText() {
        if (shown == Shown.STANDBY) {
            return Component.translatable("hud.encodedlogistics.controller.idle", decimal(idleDrain()));
        }
        return Component.literal(compact(networkStored) + " / " + compact(networkCapacity) + " FE");
    }

    // 950, 25k, 1.36M: three significant digits, trailing zeros dropped.
    public static String compact(long value) {
        if (value < 1000) {
            return Long.toString(value);
        }
        String[] units = { "k", "M", "G", "T" };
        double scaled = value;
        int unit = -1;
        while (scaled >= 1000 && unit < units.length - 1) {
            scaled /= 1000;
            unit++;
        }
        int decimals = scaled >= 100 ? 0 : scaled >= 10 ? 1 : 2;
        return trim(String.format(Locale.ROOT, "%." + decimals + "f", scaled)) + units[unit];
    }

    public static String decimal(double value) {
        return trim(String.format(Locale.ROOT, "%.1f", value));
    }

    private static String trim(String number) {
        return number.contains(".") ? number.replaceAll("0+$", "").replaceAll("\\.$", "") : number;
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (action == ACTION_SWITCH_OVER) {
            switchRequested = true;
        }
    }

    public boolean canSwitchOver() {
        return (shown == Shown.ACTIVE_PAIR || shown == Shown.STANDBY) && !partner.getString().isEmpty() && isOnline();
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        output.putInt("shown", shown.ordinal());
        output.putString("status", statusText().getString());
        output.putInt("status_kind", status().ordinal());
        output.putString("role", role().getString());
        output.putString("partner", partner.getString());
        output.putLong("network_stored", networkStored);
        output.putLong("network_capacity", networkCapacity);
        output.putDouble("usage", usage);
        output.putDouble("idle", idleDrain());
        output.putInt("devices", devices);
        output.putInt("lanes_used", lanesUsed);
        output.putInt("lanes_total", shown == Shown.CONFLICT ? lanes() : lanesTotal);
        output.putInt("capacity", getCapacity());
        output.putBoolean("can_switch", canSwitchOver());
        output.putString("last_failover_text", lastFailover < 0 ? "" : ElclSystem.clock(lastFailover, false));
        if (rack() != null) {
            rack().writeUplinks(output.childrenList("uplinks"));
        }
    }

    // --- Saving ---

    @Override
    public void saveSettings(ValueOutput output) {
        energy.serialize(output);
    }

    @Override
    public void loadSettings(ValueInput input) {
        energy.deserialize(input);
    }

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
        output.putBoolean("active", active);
        output.putLong("last_failover", lastFailover);
        output.putBoolean("fault", fault);
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
        active = input.getBooleanOr("active", false);
        lastFailover = input.getLongOr("last_failover", -1);
        fault = input.getBooleanOr("fault", false);
    }

    @Override
    public void writeClient(ValueOutput output) {
        output.putInt("shown", shown.ordinal());
    }

    @Override
    public void readClient(ValueInput input) {
        shown = Shown.byId(input.getIntOr("shown", 0));
    }

    private final class Buffer extends SimpleEnergyHandler {
        int receivedThisTick;

        Buffer() {
            super(Config.CONTROLLER_ENERGY_PER_BLOCK.getDefault() * 20, Config.CONTROLLER_MAX_RECEIVE.getDefault() * 20, 0);
        }

        // Limits come from the config (and the size), which may not be loaded when the device is created.
        void refreshLimits() {
            if (Config.SPEC.isLoaded()) {
                capacity = (int) Math.min(Integer.MAX_VALUE, (long) Config.CONTROLLER_ENERGY_PER_BLOCK.getAsInt() * blocks());
                maxInsert = (int) Math.min(Integer.MAX_VALUE, (long) Config.CONTROLLER_MAX_RECEIVE.getAsInt() * blocks());
            }
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            refreshLimits();
            return super.insert(Math.min(amount, Math.max(0, maxInsert - receivedThisTick)), transaction);
        }

        int fill(int amount, TransactionContext transaction) {
            refreshLimits();
            int limit = maxInsert;
            maxInsert = Integer.MAX_VALUE;
            try {
                return super.insert(amount, transaction);
            } finally {
                maxInsert = limit;
            }
        }

        @Override
        public void deserialize(ValueInput input) {
            refreshLimits();
            super.deserialize(input);
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            if (energy > previousAmount) {
                receivedThisTick += energy - previousAmount;
            }
            saveOnly();
        }
    }
}
