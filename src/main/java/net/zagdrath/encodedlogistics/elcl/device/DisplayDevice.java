/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

// Something SNDDSPTXT writes to (a Status Display, NOC Video Wall or a Rack Console's screen, none of which the mod has
// yet): lines of text it shows. Found through Displays; docs/elcl/INTERFACES.md says what an implementation must do.
public interface DisplayDevice {
    // Its device name (NOCDSP01).
    String name();

    boolean online();

    // How many lines it shows.
    int lines();

    // Writes a line (1-based; 0: the line after the last one written), clearing everything first if asked.
    void write(int line, String text, boolean clear);

    // The same with the line's size (DisplayContent.SMALL - HUGE), colour (ARGB, 0 the default) and alignment;
    // DisplayContent.KEEP keeps what the line had. A device that has no styles just writes the text.
    default void write(int line, String text, boolean clear, int size, int color, int align) {
        write(line, text, clear);
    }

    // The highest line number LINE() takes.
    default int maxLine() {
        return lines();
    }
}
