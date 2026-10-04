/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.List;

// A recipe library a Scheduler can read: the Midrange System's job library, recipes kept on an 8" Diskette. Its recipes
// count as the network's (CraftRequests.schematics: what can be crafted, and planning); each step runs on a provider
// that accepts it (CraftingProvider.accepts). Found through RecipeLibraries; docs/elcl/INTERFACES.md says what an
// implementation must do.
public interface RecipeLibrarySource {
    // Its device name (MIDRANGE01).
    String name();

    // Loaded, powered and with its diskette in: its recipes count.
    boolean online();

    List<Schematic> recipes();
}
