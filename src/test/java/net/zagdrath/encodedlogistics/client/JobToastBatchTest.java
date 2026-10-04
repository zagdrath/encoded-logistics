/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.crafting.JobEvents.Outcome;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobToastBatchTest {
    private static JobToastBatch.Event event(Outcome outcome) {
        return new JobToastBatch.Event(null, 32, outcome, false, "");
    }

    // enabled, all jobs, completed, failed, cancelled, minimum seconds, mine, outcome, ticks
    @Test
    void defaultsShowMyLongerJobs() {
        assertTrue(JobToastBatch.wanted(true, false, true, true, false, 5, true, Outcome.COMPLETED, 200));
        assertFalse(JobToastBatch.wanted(true, false, true, true, false, 5, true, Outcome.COMPLETED, 99), "Shorter than the minimum");
        assertFalse(JobToastBatch.wanted(true, false, true, true, false, 5, false, Outcome.COMPLETED, 200), "Not mine");
        assertFalse(JobToastBatch.wanted(true, false, true, true, false, 5, true, Outcome.CANCELLED, 200), "Cancelled is off by default");
        assertTrue(JobToastBatch.wanted(true, false, true, true, false, 5, true, Outcome.FAILED, 1), "Failures show however quick");
    }

    @Test
    void settingsTurnThemOff() {
        assertFalse(JobToastBatch.wanted(false, true, true, true, true, 0, true, Outcome.COMPLETED, 200), "Toasts off");
        assertFalse(JobToastBatch.wanted(true, false, true, false, false, 0, true, Outcome.FAILED, 200), "Failed off");
        assertTrue(JobToastBatch.wanted(true, true, true, true, true, 0, false, Outcome.CANCELLED, 0), "All jobs, cancelled on");
    }

    @Test
    void endsCloseTogetherShareOneToast() {
        JobToastBatch batch = new JobToastBatch(event(Outcome.COMPLETED), 1_000);
        assertTrue(batch.accepts(event(Outcome.COMPLETED), 3_500));
        batch.add(event(Outcome.COMPLETED), 3_500);
        assertTrue(batch.accepts(event(Outcome.COMPLETED), 6_000), "The window runs from the last one");
        batch.add(event(Outcome.COMPLETED), 6_000);
        assertEquals(3, batch.count());
        assertFalse(batch.accepts(event(Outcome.FAILED), 6_100), "A failure joined a completion");
        assertFalse(batch.accepts(event(Outcome.COMPLETED), 9_500), "Joined after the window");
    }
}
