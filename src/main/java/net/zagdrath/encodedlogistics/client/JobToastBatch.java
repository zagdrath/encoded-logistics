/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.encodedlogistics.crafting.JobEvents;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// What one job toast shows: one job's end, or several of the same kind (completed, failed or cancelled) that ended
// within WINDOW of each other ("3 jobs complete"). Plain logic, so it can be tested without a client.
public final class JobToastBatch {
    // How close together jobs have to end to share a toast, in milliseconds.
    public static final long WINDOW = 3_000;

    public record Event(ItemKey item, long amount, JobEvents.Outcome outcome, boolean processing, String reason) {}

    private final JobEvents.Outcome outcome;
    private final List<Event> events = new ArrayList<>();
    private long lastAdded;

    public JobToastBatch(Event first, long now) {
        this.outcome = first.outcome();
        events.add(first);
        lastAdded = now;
    }

    public JobEvents.Outcome outcome() {
        return outcome;
    }

    // Whether another job's end joins this toast: the same kind, within WINDOW of the last one.
    public boolean accepts(Event event, long now) {
        return event.outcome() == outcome && now - lastAdded <= WINDOW;
    }

    public void add(Event event, long now) {
        events.add(event);
        lastAdded = now;
    }

    public long lastAdded() {
        return lastAdded;
    }

    public int count() {
        return events.size();
    }

    public Event first() {
        return events.getFirst();
    }

    // The jobs' kinds all processing (a combined toast says "Processing" only then).
    public boolean processing() {
        return events.stream().allMatch(Event::processing);
    }

    // Whether a job's end is shown at all, by the player's settings.
    public static boolean wanted(boolean enabled, boolean allJobs, boolean completed, boolean failed, boolean cancelled, int minimumSeconds, boolean mine,
            JobEvents.Outcome outcome, long durationTicks) {
        if (!enabled || !mine && !allJobs) {
            return false;
        }
        boolean kind = switch (outcome) {
            case COMPLETED -> completed;
            case FAILED -> failed;
            case CANCELLED -> cancelled;
        };
        // A failure is worth knowing however quick it was.
        return kind && (outcome == JobEvents.Outcome.FAILED || durationTicks >= minimumSeconds * 20L);
    }
}
