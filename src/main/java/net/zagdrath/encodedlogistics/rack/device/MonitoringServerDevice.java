/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;

// The Monitoring Server (1U): samples the network it serves once a second - item flow (items moved in or out of
// storage per minute), energy use (FE/t), lane usage and crafting throughput (jobs finished per minute) - and keeps them
// for its graph: the last 10 minutes a sample a second (the 1m and 10m views), the last hour a sample every 10 seconds,
// and the last day a sample every 2 minutes, each the average over its interval. Kept with the device in its rack.
public class MonitoringServerDevice extends RackDevice {
    public static final int ITEMS = 0, ENERGY = 1, LANES = 2, JOBS = 3, STATS = 4;
    public static final int MINUTE = 0, TEN_MINUTES = 1, HOUR = 2, DAY = 3, RANGES = 4;
    public static final int ACTION_STAT = 0, ACTION_RANGE = 1;
    // Samples kept at each resolution, and how many seconds each covers.
    private static final int FINE = 600, MEDIUM = 360, COARSE = 720, MEDIUM_STEP = 10, COARSE_STEP = 120;

    // [stat] ring buffers, newest at index head.
    private final float[][] fine = new float[STATS][FINE], medium = new float[STATS][MEDIUM], coarse = new float[STATS][COARSE];
    private int fineHead, mediumHead, coarseHead, samples;
    private final float[] mediumSum = new float[STATS], coarseSum = new float[STATS];
    private long lastItems = -1, lastJobs = -1;
    private final float[] now = new float[STATS];
    private int stat, range;

    public MonitoringServerDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public double drain() {
        return Config.MONITORING_SERVER_DRAIN.getAsDouble();
    }

    public float now(int stat) {
        return now[stat];
    }

    // --- Sampling ---

    @Override
    public void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0 || rack() == null) {
            return;
        }
        ControllerStructures.NetworkStats stats = isOnline() ? ControllerStructures.stats(level.getServer(), rack().network(this)) : null;
        float[] sample = new float[STATS];
        if (stats != null) {
            sample[ITEMS] = lastItems < 0 ? 0 : (stats.itemsMoved() - lastItems) * 60f;
            sample[ENERGY] = (float) stats.usage();
            sample[LANES] = stats.lanesUsed();
            sample[JOBS] = lastJobs < 0 ? 0 : (stats.jobsDone() - lastJobs) * 60f;
            lastItems = stats.itemsMoved();
            lastJobs = stats.jobsDone();
        } else {
            lastItems = lastJobs = -1;
        }
        record(sample);
        saveOnly();
    }

    private void record(float[] sample) {
        samples++;
        fineHead = (fineHead + 1) % FINE;
        for (int s = 0; s < STATS; s++) {
            now[s] = sample[s];
            fine[s][fineHead] = sample[s];
            mediumSum[s] += sample[s];
            coarseSum[s] += sample[s];
        }
        if (samples % MEDIUM_STEP == 0) {
            mediumHead = (mediumHead + 1) % MEDIUM;
            for (int s = 0; s < STATS; s++) {
                medium[s][mediumHead] = mediumSum[s] / MEDIUM_STEP;
                mediumSum[s] = 0;
            }
        }
        if (samples % COARSE_STEP == 0) {
            coarseHead = (coarseHead + 1) % COARSE;
            for (int s = 0; s < STATS; s++) {
                coarse[s][coarseHead] = coarseSum[s] / COARSE_STEP;
                coarseSum[s] = 0;
            }
        }
    }

    // A stat over a range, oldest first: 60 or 600 one-second samples, 360 of 10 seconds, 720 of 2 minutes.
    public float[] series(int stat, int range) {
        return switch (range) {
            case MINUTE -> last(fine[stat], fineHead, 60);
            case TEN_MINUTES -> last(fine[stat], fineHead, FINE);
            case HOUR -> last(medium[stat], mediumHead, MEDIUM);
            default -> last(coarse[stat], coarseHead, COARSE);
        };
    }

    private static float[] last(float[] ring, int head, int count) {
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = ring[Math.floorMod(head - (count - 1 - i), ring.length)];
        }
        return out;
    }

    // Seconds each sample of a range covers.
    public static int step(int range) {
        return range == HOUR ? MEDIUM_STEP : range == DAY ? COARSE_STEP : 1;
    }

    // "846 items/min", "12.5 FE/t", "9 lanes", "3 jobs/min".
    public static String format(int stat, float value) {
        String number = value >= 1000 ? String.format(Locale.ROOT, "%.1fK", value / 1000)
                : Math.abs(value - Math.round(value)) < 0.05 ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
        return number + switch (stat) {
            case ITEMS -> " items/min";
            case ENERGY -> " FE/t";
            case LANES -> " lanes";
            default -> " jobs/min";
        };
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.monitor.items"), Component.literal(format(ITEMS, now[ITEMS]))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.monitor.energy"), Component.literal(format(ENERGY, now[ENERGY]))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.monitor.lanes"), Component.literal(format(LANES, now[LANES]))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.monitor.jobs"), Component.literal(format(JOBS, now[JOBS]))));
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (action == ACTION_STAT) {
            stat = Math.clamp(value, 0, STATS - 1);
        } else if (action == ACTION_RANGE) {
            range = Math.clamp(value, 0, RANGES - 1);
        } else {
            return;
        }
        changed(false);
    }

    public int stat() {
        return stat;
    }

    public int range() {
        return range;
    }

    // The chosen stat and range's samples (as float bits), and every stat's latest value.
    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        saveSettings(output);
        float[] series = series(stat, range);
        int[] bits = new int[series.length];
        for (int i = 0; i < series.length; i++) {
            bits[i] = Float.floatToIntBits(series[i]);
        }
        output.putIntArray("series", bits);
        int[] latest = new int[STATS];
        for (int s = 0; s < STATS; s++) {
            latest[s] = Float.floatToIntBits(now[s]);
        }
        output.putIntArray("now", latest);
        output.putInt("filled", Math.min(samples, FINE));
        output.putInt("samples", samples);
    }

    public static float[] floats(int[] bits) {
        float[] out = new float[bits.length];
        for (int i = 0; i < bits.length; i++) {
            out[i] = Float.intBitsToFloat(bits[i]);
        }
        return out;
    }

    // --- Saving ---

    @Override
    public void saveSettings(ValueOutput output) {
        output.putInt("stat", stat);
        output.putInt("range", range);
    }

    @Override
    public void loadSettings(ValueInput input) {
        stat = Math.clamp(input.getIntOr("stat", 0), 0, STATS - 1);
        range = Math.clamp(input.getIntOr("range", 0), 0, RANGES - 1);
    }

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
        output.putInt("samples", samples);
        output.putInt("fine_head", fineHead);
        output.putInt("medium_head", mediumHead);
        output.putInt("coarse_head", coarseHead);
        for (int s = 0; s < STATS; s++) {
            output.putIntArray("fine" + s, bits(fine[s]));
            output.putIntArray("medium" + s, bits(medium[s]));
            output.putIntArray("coarse" + s, bits(coarse[s]));
        }
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
        samples = input.getIntOr("samples", 0);
        fineHead = Math.floorMod(input.getIntOr("fine_head", 0), FINE);
        mediumHead = Math.floorMod(input.getIntOr("medium_head", 0), MEDIUM);
        coarseHead = Math.floorMod(input.getIntOr("coarse_head", 0), COARSE);
        for (int s = 0; s < STATS; s++) {
            restore(input, "fine" + s, fine[s]);
            restore(input, "medium" + s, medium[s]);
            restore(input, "coarse" + s, coarse[s]);
        }
    }

    private static int[] bits(float[] values) {
        int[] out = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = Float.floatToIntBits(values[i]);
        }
        return out;
    }

    private static void restore(ValueInput input, String key, float[] into) {
        int[] saved = input.getIntArray(key).orElse(new int[0]);
        for (int i = 0; i < into.length && i < saved.length; i++) {
            into[i] = Float.intBitsToFloat(saved[i]);
        }
    }
}
