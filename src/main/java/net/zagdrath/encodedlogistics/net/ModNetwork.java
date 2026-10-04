/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String VERSION = "9";

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
        registrar.playToServer(GhostSlotPayload.TYPE, GhostSlotPayload.STREAM_CODEC, GhostSlotPayload::handle);
        registrar.playToServer(MenuValuePayload.TYPE, MenuValuePayload.STREAM_CODEC, MenuValuePayload::handle);
        registrar.playToServer(EncoderRecipePayload.TYPE, EncoderRecipePayload.STREAM_CODEC, EncoderRecipePayload::handle);
        registrar.playToServer(CraftRequestPayload.TYPE, CraftRequestPayload.STREAM_CODEC, CraftRequestPayload::handle);
        registrar.playToClient(CraftPlanPayload.TYPE, CraftPlanPayload.STREAM_CODEC, CraftPlanPayload::handle);
        registrar.playToClient(SchedulerStatusPayload.TYPE, SchedulerStatusPayload.STREAM_CODEC, SchedulerStatusPayload::handle);
        registrar.playBidirectional(JobStatusPayload.TYPE, JobStatusPayload.STREAM_CODEC, JobStatusPayload::handleServer,
                JobStatusPayload::handleClient);
        registrar.playToServer(JobCancelPayload.TYPE, JobCancelPayload.STREAM_CODEC, JobCancelPayload::handle);
        registrar.playToClient(RelayTerminalsPayload.TYPE, RelayTerminalsPayload.STREAM_CODEC, RelayTerminalsPayload::handle);
        registrar.playToClient(RackPanelPayload.TYPE, RackPanelPayload.STREAM_CODEC, RackPanelPayload::handle);
        registrar.playToServer(RackActionPayload.TYPE, RackActionPayload.STREAM_CODEC, RackActionPayload::handle);
        registrar.playToServer(RackUnitPayloads.Query.TYPE, RackUnitPayloads.Query.STREAM_CODEC, RackUnitPayloads.Query::handle);
        registrar.playToClient(RackUnitPayloads.Info.TYPE, RackUnitPayloads.Info.STREAM_CODEC, RackUnitPayloads.Info::handle);
    }
}
