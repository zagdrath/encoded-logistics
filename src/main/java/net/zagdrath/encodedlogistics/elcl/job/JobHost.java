/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

// Something that runs batch jobs (OS.md 5): a Compute Server (4 at once; its jobs end with ELC0310 when it's unloaded
// or loses power), and - with the Midrange line - the Midrange System (1, +1 with an Expansion Cabinet), the
// Integrated Midrange System (4) and the Mainframe (32 per CPC Drawer; its jobs are journaled and resume). Found
// through JobHosts; docs/elcl/INTERFACES.md says what an implementation must do.
public interface JobHost {
    // Its device name (CMPSRV01): how jobs, SBMJOB HOST() and the screens know it. Stable while it stands.
    String name();

    // Batch jobs it runs at once.
    int capacity();

    // Whether its jobs carry on after it's unloaded, the server restarts or it loses power (else they end, ELC0310).
    boolean resumes();

    // Loaded and powered: it can run jobs now.
    boolean online();
}
