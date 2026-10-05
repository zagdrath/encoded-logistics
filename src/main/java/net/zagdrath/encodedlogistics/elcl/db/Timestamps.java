/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// A T field's value: a moment on the game clock as "ddddd hh:mm:ss" - the game day (from 1) and the time of day - so it
// sorts and compares as text. Typed or imported, "Day 12 06:30", "12 06:30:15" and "00012 06:30:15" all do; blank or
// *NOW means the moment it's written.
public final class Timestamps {
    public static final String NOW = "*NOW";
    private static final Pattern FORM = Pattern.compile("(?:DAY\\s+)?(\\d{1,5})\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");

    private Timestamps() {}

    // The overworld clock's ticks (day 1 starts at tick 0, 06:00) as a timestamp.
    public static String of(long ticks) {
        long day = Math.max(0, ticks) / 24_000 + 1, tick = Math.max(0, ticks) % 24_000;
        long seconds = (tick * 86_400 / 24_000 + 6 * 3_600) % 86_400;
        return String.format(Locale.ROOT, "%05d %02d:%02d:%02d", Math.min(day, 99_999), seconds / 3_600, seconds / 60 % 60, seconds % 60);
    }

    // Typed text as a timestamp; "" for blank or *NOW (the time it's written). ELC2209 for anything else.
    public static String normalize(String text) throws ElclException {
        String value = text.strip().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.equals(NOW)) {
            return "";
        }
        Matcher matcher = FORM.matcher(value);
        if (!matcher.matches()) {
            throw new ElclException("ELC2209", text.strip(), "*TIME");
        }
        int day = Integer.parseInt(matcher.group(1)), hour = Integer.parseInt(matcher.group(2)), minute = Integer.parseInt(matcher.group(3));
        int second = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;
        if (day < 1 || hour > 23 || minute > 59 || second > 59) {
            throw new ElclException("ELC2209", text.strip(), "*TIME");
        }
        return String.format(Locale.ROOT, "%05d %02d:%02d:%02d", day, hour, minute, second);
    }
}
