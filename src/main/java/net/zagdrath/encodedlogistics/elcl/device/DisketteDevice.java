/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

import java.util.List;

// Something that takes 8" Diskettes (the Midrange System's drive, the Card Reader): SAVLIB / RSTLIB DEV() names it.
// Found through Diskettes; docs/elcl/INTERFACES.md says what an implementation must do.
public interface DisketteDevice {
    // Its device name (MIDRANGE01, CARDRDR01).
    String name();

    boolean online();

    // The diskettes in it now; SAVLIB writes to the first, RSTLIB reads the first that has the library.
    List<Diskette> mounted();
}
