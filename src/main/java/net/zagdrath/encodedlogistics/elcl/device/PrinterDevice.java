/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

import java.util.List;

// A printer on a system (the Line Printer, from the Midrange line): takes a spooled file or report and says whether
// it has paper. Found through Printers; docs/elcl/INTERFACES.md says what an implementation must do.
public interface PrinterDevice {
    // Its device name: PRT01 unless renamed.
    String name();

    boolean online();

    // Whether it can print now (false: ELC1306, out of paper).
    boolean hasPaper();

    // Prints the lines under a title (the spooled file's or report's name). Only called while online with paper.
    void print(String title, List<String> lines);
}
