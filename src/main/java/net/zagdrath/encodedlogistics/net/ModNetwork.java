/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String VERSION = "12";

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
        registrar.playToServer(CrtRequestPayload.TYPE, CrtRequestPayload.STREAM_CODEC, CrtRequestPayload::handle);
        registrar.playToClient(CrtResponsePayload.TYPE, CrtResponsePayload.STREAM_CODEC, CrtResponsePayload::handle);
        registrar.playToServer(GhostSlotPayload.TYPE, GhostSlotPayload.STREAM_CODEC, GhostSlotPayload::handle);
        registrar.playToServer(MenuValuePayload.TYPE, MenuValuePayload.STREAM_CODEC, MenuValuePayload::handle);
        registrar.playToServer(EncoderRecipePayload.TYPE, EncoderRecipePayload.STREAM_CODEC, EncoderRecipePayload::handle);
        registrar.playToServer(CraftRequestPayload.TYPE, CraftRequestPayload.STREAM_CODEC, CraftRequestPayload::handle);
        registrar.playToClient(CraftPlanPayload.TYPE, CraftPlanPayload.STREAM_CODEC, CraftPlanPayload::handle);
        registrar.playToClient(SchedulerStatusPayload.TYPE, SchedulerStatusPayload.STREAM_CODEC, SchedulerStatusPayload::handle);
        registrar.playBidirectional(JobStatusPayload.TYPE, JobStatusPayload.STREAM_CODEC, JobStatusPayload::handleServer,
                JobStatusPayload::handleClient);
        registrar.playToServer(JobCancelPayload.TYPE, JobCancelPayload.STREAM_CODEC, JobCancelPayload::handle);
        registrar.playToClient(JobToastPayload.TYPE, JobToastPayload.STREAM_CODEC, JobToastPayload::handle);
        registrar.playToClient(RelayTerminalsPayload.TYPE, RelayTerminalsPayload.STREAM_CODEC, RelayTerminalsPayload::handle);
        registrar.playToClient(RackPanelPayload.TYPE, RackPanelPayload.STREAM_CODEC, RackPanelPayload::handle);
        registrar.playToServer(RackActionPayload.TYPE, RackActionPayload.STREAM_CODEC, RackActionPayload::handle);
        registrar.playToServer(RackUnitPayloads.Query.TYPE, RackUnitPayloads.Query.STREAM_CODEC, RackUnitPayloads.Query::handle);
        registrar.playToClient(RackUnitPayloads.Info.TYPE, RackUnitPayloads.Info.STREAM_CODEC, RackUnitPayloads.Info::handle);
        registrar.playToServer(WirelessInfoPayloads.Query.TYPE, WirelessInfoPayloads.Query.STREAM_CODEC, WirelessInfoPayloads.Query::handle);
        registrar.playToClient(WirelessInfoPayloads.Info.TYPE, WirelessInfoPayloads.Info.STREAM_CODEC, WirelessInfoPayloads.Info::handle);
        registrar.playToServer(MachinePayloads.Text.TYPE, MachinePayloads.Text.STREAM_CODEC, MachinePayloads.Text::handle);
        registrar.playToClient(MachinePayloads.Info.TYPE, MachinePayloads.Info.STREAM_CODEC, MachinePayloads.Info::handle);
        registrar.playToClient(DisplayFramePayload.TYPE, DisplayFramePayload.STREAM_CODEC, DisplayFramePayload::handle);
        registrar.playToServer(DisplayConfigPayload.TYPE, DisplayConfigPayload.STREAM_CODEC, DisplayConfigPayload::handle);
        registrar.playToClient(MachineBridgesPayload.TYPE, MachineBridgesPayload.STREAM_CODEC, MachineBridgesPayload::handle);
        registrar.playToServer(SignalConfigPayload.TYPE, SignalConfigPayload.STREAM_CODEC, SignalConfigPayload::handle);
        registrar.playToServer(AudioPayloads.Request.TYPE, AudioPayloads.Request.STREAM_CODEC, AudioPayloads.Request::handle);
        registrar.playToClient(AudioPayloads.Chunk.TYPE, AudioPayloads.Chunk.STREAM_CODEC, AudioPayloads.Chunk::handle);
        registrar.playToServer(AudioPayloads.Report.TYPE, AudioPayloads.Report.STREAM_CODEC, AudioPayloads.Report::handle);
    }
}
