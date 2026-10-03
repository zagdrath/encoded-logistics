/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import org.jspecify.annotations.Nullable;

// A recipe viewer's search bar the terminals can sync with (their JEI search mode, as in AE2): JEI's, set by the JEI
// plugin once its runtime is up and cleared when it goes away. Kept free of JEI's classes so the terminals load without
// it.
public final class ExternalSearch {
    public interface Field {
        String text();

        void setText(String text);
    }

    private static @Nullable Field field;

    private ExternalSearch() {}

    public static @Nullable Field field() {
        return field;
    }

    public static void set(@Nullable Field field) {
        ExternalSearch.field = field;
    }
}
