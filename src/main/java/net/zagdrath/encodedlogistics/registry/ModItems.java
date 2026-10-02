/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.NetworkControllerItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EncodedLogistics.MODID);

    public static final DeferredItem<NetworkControllerItem> NETWORK_CONTROLLER = ITEMS.registerItem("network_controller",
            p -> new NetworkControllerItem(ModBlocks.NETWORK_CONTROLLER.get(), p), p -> p.useBlockDescriptionPrefix());

    private ModItems() {}
}
