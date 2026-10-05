/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.machine;

// One machine stat as a Monitoring Server keeps it (one sample a second), at its graph's resolutions: the last 10
// minutes a sample a second, the last hour a sample every 10 seconds and the last day a sample every 2 minutes, each the
// average over its interval. Ranges are MonitoringServerDevice's (MINUTE, TEN_MINUTES, HOUR, DAY).
public final class MachineSeries {
    private static final int FINE = 600, MEDIUM = 360, COARSE = 720, MEDIUM_STEP = 10, COARSE_STEP = 120;
    private final float[] fine = new float[FINE], medium = new float[MEDIUM], coarse = new float[COARSE];
    private int fineHead, mediumHead, coarseHead, samples;
    private float mediumSum, coarseSum, now;

    public void add(float value) {
        samples++;
        now = value;
        fineHead = (fineHead + 1) % FINE;
        fine[fineHead] = value;
        mediumSum += value;
        coarseSum += value;
        if (samples % MEDIUM_STEP == 0) {
            mediumHead = (mediumHead + 1) % MEDIUM;
            medium[mediumHead] = mediumSum / MEDIUM_STEP;
            mediumSum = 0;
        }
        if (samples % COARSE_STEP == 0) {
            coarseHead = (coarseHead + 1) % COARSE;
            coarse[coarseHead] = coarseSum / COARSE_STEP;
            coarseSum = 0;
        }
    }

    public float now() {
        return now;
    }

    // Over a range (0: a minute, 1: ten minutes, 2: an hour, 3: a day), oldest first.
    public float[] series(int range) {
        return switch (range) {
            case 0 -> last(fine, fineHead, 60);
            case 1 -> last(fine, fineHead, FINE);
            case 2 -> last(medium, mediumHead, MEDIUM);
            default -> last(coarse, coarseHead, COARSE);
        };
    }

    private static float[] last(float[] ring, int head, int count) {
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = ring[Math.floorMod(head - (count - 1 - i), ring.length)];
        }
        return out;
    }
}
