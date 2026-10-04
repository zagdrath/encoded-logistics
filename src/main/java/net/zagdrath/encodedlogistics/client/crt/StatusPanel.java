/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

// DSPNETSTS: storage hot and cold, energy, lanes, crafting, devices, with gauges; refreshed every two seconds. Enter
// goes back.
final class StatusPanel extends TextPanel {
    private int timer;

    StatusPanel(CrtScreen screen) {
        super(screen, "DSPNETSTS", tr("crt.encodedlogistics.status.title"), "status");
    }

    @Override
    void tick() {
        if (++timer >= 40) {
            timer = 0;
            shown();
        }
    }
}
