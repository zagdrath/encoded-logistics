/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;

// A Midrange-line block with a device name (HANDOFF 7): MIDRANGE01, KEYPUNCH01, CARDRDR01, PRT01. ElclDevices gives and
// keeps it; it goes with the block's item (ModDataComponents.DEVICE_NAME).
public interface MidrangeDevice extends NamedDevice {}
