/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String VERSION = "5";

    private ModNetwork() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(NetworkSnapshotPayload.TYPE, NetworkSnapshotPayload.STREAM_CODEC, NetworkSnapshotPayload::handle);
        registrar.playToClient(CapacitorBankPayload.TYPE, CapacitorBankPayload.STREAM_CODEC, CapacitorBankPayload::handle);
        registrar.playToClient(TerminalItemsPayload.TYPE, TerminalItemsPayload.STREAM_CODEC, TerminalItemsPayload::handle);
        registrar.playToServer(TerminalClickPayload.TYPE, TerminalClickPayload.STREAM_CODEC, TerminalClickPayload::handle);
        registrar.playToServer(TerminalRecipePayload.TYPE, TerminalRecipePayload.STREAM_CODEC, TerminalRecipePayload::handle);
        registrar.playToServer(MenuValuePayload.TYPE, MenuValuePayload.STREAM_CODEC, MenuValuePayload::handle);
    }
}
