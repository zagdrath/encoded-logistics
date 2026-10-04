/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;

// The UPS (2U): a battery for its network. Each tick the network compares what its Power Inlets (and anything else
// feeding its controllers and banks) brought in with what it drains (ControllerStructures; full buffers count as a
// supply that covers it). When supply falls short, the UPSes on the network cover the difference from their batteries
// in the same tick, before the network's own buffers are touched; otherwise they recharge from the top half of the
// buffers. Online mode always conditions the supply (covers any shortfall,
// recharges continuously); Standby only steps in once supply has failed altogether, and idles on less. Several UPSes on
// a network add up: they discharge together and recharge in parallel. Overload (a shortfall over their rated output)
// is a fault.
public class UpsDevice extends RackDevice {
    public enum Mode {
        ONLINE, STANDBY;

        public Component label() {
            return Component.translatable("gui.encodedlogistics.ups.mode." + name().toLowerCase(Locale.ROOT));
        }
    }

    // A switchover: when, and which way.
    public record Event(long time, boolean toBattery) {}

    public static final int ACTION_TOGGLE_MODE = 0;
    public static final int LOAD_LEDS = 10;
    private static final int LOG_SIZE = 3, FAULT_HOLD = 20;

    private long stored;
    private Mode mode = Mode.ONLINE;
    private boolean onBattery;
    // FE per tick the network asks of this UPS's share, and what it gave from its battery, last tick.
    private double load;
    private int supplied;
    private int faultTicks;
    // The game time a network last ran this UPS's energy (cover).
    private long lastCovered = Long.MIN_VALUE;
    private final Deque<Event> log = new ArrayDeque<>();
    // Client: what the front shows, as synced.
    private int shownPercent, shownLeds;
    private boolean shownFault;

    public UpsDevice(RackDeviceType type) {
        super(type);
    }

    public static long capacity() {
        return Config.UPS_CAPACITY.getAsInt();
    }

    public static int maxOutput() {
        return Config.UPS_MAX_OUTPUT.getAsInt();
    }

    public long stored() {
        return stored;
    }

    public Mode mode() {
        return mode;
    }

    public boolean onBattery() {
        return onBattery;
    }

    public double load() {
        return load;
    }

    public List<Event> log() {
        return List.copyOf(log);
    }

    public int percent() {
        return (int) Math.min(100, stored * 100 / Math.max(1, capacity()));
    }

    // Load LEDs lit (0..10): the load against the rated output.
    public int loadLeds() {
        return (int) Math.min(LOAD_LEDS, Math.ceil(load * LOAD_LEDS / Math.max(1, maxOutput())));
    }

    // Client: the LCD and load bar as synced.
    public int shownPercent() {
        return shownPercent;
    }

    public int shownLeds() {
        return shownLeds;
    }

    // Seconds the battery lasts at the present load (-1: no load).
    public long runtimeSeconds() {
        double rate = onBattery ? Math.max(supplied, load) : load;
        return rate <= 0 ? -1 : (long) (stored / rate / 20);
    }

    @Override
    public double drain() {
        return mode == Mode.ONLINE ? Config.UPS_DRAIN_ONLINE.getAsDouble() : Config.UPS_DRAIN_STANDBY.getAsDouble();
    }

    // --- The network's energy (called by ControllerStructures every tick for the UPSes that have their lanes) ---

    // demand: FE the network drains this tick; supply: FE that came in. Returns what the batteries give.
    public static int cover(List<UpsDevice> upses, int demand, int supply, long time) {
        int deficit = Math.max(0, demand - supply);
        List<UpsDevice> engaged = new ArrayList<>();
        for (UpsDevice ups : upses) {
            ups.lastCovered = time;
            ups.load = (double) demand / upses.size();
            if (deficit > 0 && (ups.mode == Mode.ONLINE || supply <= 0) && ups.stored > 0) {
                engaged.add(ups);
            }
            ups.supplied = 0;
        }
        int given = 0;
        if (!engaged.isEmpty()) {
            long rated = (long) maxOutput() * engaged.size();
            if (deficit > rated) {
                engaged.forEach(ups -> ups.faultTicks = FAULT_HOLD);
            }
            // Evenly, what one can't give passing to the rest.
            int left = (int) Math.min(deficit, rated);
            for (int i = 0; i < engaged.size() && left > 0; i++) {
                UpsDevice ups = engaged.get(i);
                int share = (int) Math.min(Math.min(left / (engaged.size() - i) + (left % (engaged.size() - i) > 0 ? 1 : 0), maxOutput()), ups.stored);
                ups.stored -= share;
                ups.supplied = share;
                left -= share;
                given += share;
            }
        }
        for (UpsDevice ups : upses) {
            ups.setOnBattery(ups.supplied > 0, time);
            if (ups.faultTicks > 0) {
                ups.faultTicks--;
            }
            ups.tickDisplay();
        }
        return given;
    }

    // Off every network (cable cut, lane lost): nothing to cover or carry.
    @Override
    public void tick(ServerLevel level) {
        if (level.getGameTime() - lastCovered > 2 && (onBattery || load != 0 || supplied != 0)) {
            onBattery = false;
            load = 0;
            supplied = 0;
            changed(false);
        }
    }

    // How much the UPSes take from what's available to recharge.
    public static int wantedCharge(List<UpsDevice> upses, int surplus) {
        long want = 0;
        for (UpsDevice ups : upses) {
            if (!ups.onBattery) {
                want += Math.min(Config.UPS_MAX_INPUT.getAsInt(), capacity() - ups.stored);
            }
        }
        return (int) Math.max(0, Math.min(surplus, want));
    }

    // Shares FE taken for recharging between the UPSes.
    public static void charge(List<UpsDevice> upses, int amount) {
        List<UpsDevice> charging = upses.stream().filter(ups -> !ups.onBattery && ups.stored < capacity()).toList();
        int left = amount;
        for (int i = 0; i < charging.size() && left > 0; i++) {
            UpsDevice ups = charging.get(i);
            int share = (int) Math.min(Math.min(left / (charging.size() - i) + (left % (charging.size() - i) > 0 ? 1 : 0),
                    Config.UPS_MAX_INPUT.getAsInt()), capacity() - ups.stored);
            ups.stored += share;
            left -= share;
        }
        charging.forEach(UpsDevice::tickDisplay);
    }

    private void setOnBattery(boolean battery, long time) {
        if (battery != onBattery) {
            onBattery = battery;
            log.addFirst(new Event(time, battery));
            while (log.size() > LOG_SIZE) {
                log.removeLast();
            }
            changed(false);
        }
    }

    // Saved now and then; synced when what the front shows changes.
    private void tickDisplay() {
        if (percent() != shownPercent || loadLeds() != shownLeds || faultTicks > 0 != shownFault) {
            shownPercent = percent();
            shownLeds = loadLeds();
            shownFault = faultTicks > 0;
            changed(false);
        } else {
            saveOnly();
        }
    }

    // --- Status and popup ---

    @Override
    public RackDeviceInfo.Status status() {
        return faultTicks > 0 ? RackDeviceInfo.Status.FAULT : super.status();
    }

    @Override
    public Component statusText() {
        if (faultTicks > 0) {
            return Component.translatable("hud.encodedlogistics.ups.overload");
        }
        if (!isOnline()) {
            return super.statusText();
        }
        return Component.translatable(onBattery ? "hud.encodedlogistics.ups.on_battery" : "hud.encodedlogistics.ups.on_mains");
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        int percent = percent();
        RackDeviceInfo.BarStyle style = percent > 50 ? RackDeviceInfo.BarStyle.NORMAL : percent >= 20 ? RackDeviceInfo.BarStyle.WARN
                : RackDeviceInfo.BarStyle.LOW;
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.ups.battery"), Component.literal(percent + "%"),
                        new RackDeviceInfo.Bar(percent / 100.0F, style)),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.ups.load"), Component.literal(rate(load))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.ups.runtime"), runtime(runtimeSeconds())),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.ups.mode"),
                        Component.translatable("gui.encodedlogistics.ups.mode_short." + mode.name().toLowerCase(Locale.ROOT))));
    }

    public static String rate(double value) {
        long whole = Math.round(value);
        return (Math.abs(value - whole) < 0.05 ? String.format(Locale.ROOT, "%,d", whole) : String.format(Locale.ROOT, "%,.1f", value)) + " FE/t";
    }

    // "1h 02m", "5m 07s", "42s", or a dash with no load.
    public static Component runtime(long seconds) {
        if (seconds < 0) {
            return Component.literal("—");
        }
        if (seconds >= 3600) {
            return Component.literal(String.format(Locale.ROOT, "%dh %02dm", seconds / 3600, seconds / 60 % 60));
        }
        if (seconds >= 60) {
            return Component.literal(String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60));
        }
        return Component.literal(seconds + "s");
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (action == ACTION_TOGGLE_MODE) {
            mode = mode == Mode.ONLINE ? Mode.STANDBY : Mode.ONLINE;
            // Its drain changed.
            changed(true);
        }
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        output.putDouble("load", load);
        output.putInt("supplied", supplied);
        output.putLong("runtime", runtimeSeconds());
        output.putLong("now", viewer.level().getGameTime());
        output.putLong("capacity", capacity());
        output.putInt("max_output", maxOutput());
    }

    // --- Saving ---

    @Override
    public void saveSettings(ValueOutput output) {
        output.putInt("mode", mode.ordinal());
        output.putLong("stored", stored);
    }

    @Override
    public void loadSettings(ValueInput input) {
        mode = input.getIntOr("mode", 0) == 1 ? Mode.STANDBY : Mode.ONLINE;
        stored = Math.clamp(input.getLongOr("stored", 0), 0, capacity());
    }

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
        output.putBoolean("on_battery", onBattery);
        ValueOutput.ValueOutputList events = output.childrenList("log");
        for (Event event : log) {
            ValueOutput child = events.addChild();
            child.putLong("time", event.time());
            child.putBoolean("to_battery", event.toBattery());
        }
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
        onBattery = input.getBooleanOr("on_battery", false);
        log.clear();
        for (ValueInput child : input.childrenListOrEmpty("log")) {
            if (log.size() < LOG_SIZE) {
                log.addLast(new Event(child.getLongOr("time", 0), child.getBooleanOr("to_battery", false)));
            }
        }
        shownPercent = percent();
    }

    @Override
    public void writeClient(ValueOutput output) {
        output.putInt("percent", percent());
        output.putInt("leds", loadLeds());
    }

    @Override
    public void readClient(ValueInput input) {
        shownPercent = input.getIntOr("percent", 0);
        shownLeds = input.getIntOr("leds", 0);
    }
}
