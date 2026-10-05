/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.machine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.display.SmallWirelessBridgeBlock;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.net.MachineBridgesPayload;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.network.RemoteLink;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.wireless.Wireless;

// A level's Small Wireless Bridges (MachineBridge), by the block each is on: saved with the level, shown to its players
// (MachineBridgesPayload: where each is and its LED, for the model) and ticked with it. Every CHECK_INTERVAL ticks each
// loaded one is checked: the block it's on gone (the machine broken) drops it; a link its controller no longer lists is
// cut (Wireless.check); what the machine is and the LED are brought up to date. A linked one is a network node of its
// own (node: one lane, its drain, a remote link to its controller's rack, no cables), so the network finds it like a
// Wireless Port. Sneak-use with an empty hand on its face takes it off (build permission while it's linked).
//
// What a machine is comes through the machine mod's integration (access): with none (Arcforge missing or incompatible)
// bridges are kept as they are but do nothing - no node, no checks - so taking Arcforge out and back loses nothing.
public final class MachineBridges extends SavedData {
    private static final int CHECK_INTERVAL = 10;
    private static @Nullable MachineAccess access;

    public static final SavedDataType<MachineBridges> TYPE = new SavedDataType<>(EncodedLogistics.id("machine_bridges"),
            level -> new MachineBridges(dimension(level), List.of()), level -> codec(dimension(level)));

    private final ResourceKey<Level> dimension;
    private final Map<BlockPos, MachineBridge> bridges = new LinkedHashMap<>();
    // Not saved: the level (set as it's looked up), whether players' copies are out of date, and the check timer.
    private @Nullable ServerLevel level;
    private boolean shownChanged = true;
    private int timer;

    private MachineBridges(ResourceKey<Level> dimension, List<MachineBridge> saved) {
        this.dimension = dimension;
        for (MachineBridge bridge : saved) {
            bridge.owner = this;
            bridges.put(bridge.pos(), bridge);
        }
    }

    private static ResourceKey<Level> dimension(@Nullable ServerLevel level) {
        return level != null ? level.dimension() : Level.OVERWORLD;
    }

    private static Codec<MachineBridges> codec(ResourceKey<Level> dimension) {
        return RecordCodecBuilder.create(i -> i.group(
                MachineBridge.codec(dimension).listOf().fieldOf("bridges").forGetter(data -> List.copyOf(data.bridges.values())))
                .apply(i, saved -> new MachineBridges(dimension, saved)));
    }

    public static MachineBridges get(ServerLevel level) {
        MachineBridges data = level.getDataStorage().computeIfAbsent(TYPE);
        data.level = level;
        return data;
    }

    // --- The machine mod's integration ---

    public static @Nullable MachineAccess access() {
        return access;
    }

    // Set once at startup by the integration (compat.arcforge.ArcforgeCompat), when it's loaded and compatible.
    public static void setAccess(@Nullable MachineAccess machines) {
        access = machines;
    }

    public static boolean enabled() {
        return access != null;
    }

    // --- Lookups ---

    // The bridge on the block at pos, or null.
    public static @Nullable MachineBridge at(ServerLevel level, BlockPos pos) {
        return get(level).bridges.get(pos);
    }

    public static @Nullable MachineBridge at(MinecraftServer server, NodePos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        return level != null ? at(level, pos.pos()) : null;
    }

    public Collection<MachineBridge> all() {
        return bridges.values();
    }

    // Every bridge in every level.
    public static List<MachineBridge> all(MinecraftServer server) {
        List<MachineBridge> all = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            all.addAll(get(level).bridges.values());
        }
        return all;
    }

    // The bridge on the machine whose own position (a multiblock's controller) is machine, or null.
    public @Nullable MachineBridge forMachine(BlockPos machine) {
        for (MachineBridge bridge : bridges.values()) {
            if (bridge.machine().equals(machine)) {
                return bridge;
            }
        }
        return null;
    }

    // --- Attaching and taking off ---

    // A new, unlinked bridge on the face of the machine block at pos.
    public MachineBridge attach(ServerLevel level, BlockPos pos, Direction face, MachineInfo info) {
        MachineBridge bridge = new MachineBridge(dimension, pos, face, blockId(level, pos), info.position(), info.type(), info.name(), null, "", false);
        bridge.owner = this;
        bridge.setLed(led(level.getServer(), bridge, info));
        bridges.put(bridge.pos(), bridge);
        setDirty();
        shownChanged = true;
        sync(level);
        return bridge;
    }

    // Takes a bridge off: its controller forgets it and the network loses it; dropped in front of its face if drop.
    public void remove(ServerLevel level, MachineBridge bridge, boolean drop) {
        if (bridges.remove(bridge.pos()) == null) {
            return;
        }
        if (access != null) {
            access.unwatch(level, bridge.pos());
        }
        Wireless.removed(level.getServer(), bridge);
        bridge.owner = null;
        if (drop) {
            Block.popResourceFromFace(level, bridge.pos(), bridge.face(), ModItems.SMALL_WIRELESS_BRIDGE.toStack());
        }
        ControllerStructures.get(level).markTopologyChanged();
        setDirty();
        shownChanged = true;
        sync(level);
    }

    // Its link changed: saved, re-solved, and its LED shown again.
    void relinked() {
        setDirty();
        shownChanged = true;
        if (level != null) {
            ControllerStructures.get(level).markTopologyChanged();
        }
    }

    private static Identifier blockId(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }

    // --- On the network ---

    // A linked bridge as a network node: a one-lane device with no cable connections and a wireless link to its
    // controller's rack (which links back while the controller admits it). Null with no bridge, no link or no
    // integration.
    public static @Nullable NetworkNode node(ServerLevel level, BlockPos pos) {
        if (access == null) {
            return null;
        }
        MachineBridge bridge = at(level, pos);
        if (bridge == null || bridge.link() == null) {
            return null;
        }
        return new DeviceNode(pos.immutable(), EnumSet.noneOf(Direction.class), 1, Config.SMALL_WIRELESS_BRIDGE_DRAIN.getAsDouble(), List.of(), false,
                List.of(new RemoteLink(bridge.link().rackNode(), 1, true)));
    }

    // The network has the bridge at pos online (it has its lane) or not: its LED follows at once.
    public static void setOnline(MinecraftServer server, NodePos pos, boolean online) {
        ServerLevel level = server.getLevel(pos.dimension());
        MachineBridge bridge = level != null ? at(level, pos.pos()) : null;
        if (bridge != null && bridge.setOnline(online) && access != null) {
            bridge.setLed(led(server, bridge, access.info(level, pos.pos())));
            get(level).shownChanged = true;
        }
    }

    // --- Ticking ---

    public void tick(ServerLevel level) {
        this.level = level;
        if (access == null) {
            return;
        }
        boolean check = ++timer >= CHECK_INTERVAL;
        if (check) {
            timer = 0;
            MinecraftServer server = level.getServer();
            for (MachineBridge bridge : List.copyOf(bridges.values())) {
                if (!level.isLoaded(bridge.pos())) {
                    continue;
                }
                // The block it was put on is gone: the machine was broken (or replaced).
                if (!blockId(level, bridge.pos()).equals(bridge.block())) {
                    remove(level, bridge, true);
                    continue;
                }
                Wireless.check(server, bridge);
                MachineInfo info = access.info(level, bridge.pos());
                if (info != null && bridge.seen(info)) {
                    setDirty();
                }
                if (bridge.setLed(led(server, bridge, info))) {
                    shownChanged = true;
                }
            }
        }
        if (shownChanged) {
            sync(level);
        }
    }

    // The LED: amber for a fault (no Access Points or no slot for it, or the machine missing, unformed or faulted); light
    // blue while it's online on its network; otherwise yellow, blinking (not linked, or not connected).
    static SmallWirelessBridgeBlock.State led(MinecraftServer server, MachineBridge bridge, @Nullable MachineInfo info) {
        Wireless.Problem problem = Wireless.problem(server, bridge);
        if (problem == Wireless.Problem.NO_ACCESS_POINTS || problem == Wireless.Problem.OVER_CAPACITY) {
            return SmallWirelessBridgeBlock.State.FAULT;
        }
        if (problem != Wireless.Problem.NONE) {
            return SmallWirelessBridgeBlock.State.UNLINKED;
        }
        if (info == null || info.status() == MachineInfo.State.FAULT || info.status() == MachineInfo.State.NOT_FORMED
                || info.status() == MachineInfo.State.UNKNOWN) {
            return SmallWirelessBridgeBlock.State.FAULT;
        }
        return bridge.isOnline() ? SmallWirelessBridgeBlock.State.LINKED : SmallWirelessBridgeBlock.State.UNLINKED;
    }

    // --- Players' copies (the models) ---

    private MachineBridgesPayload payload() {
        List<MachineBridgesPayload.Entry> entries = new ArrayList<>(bridges.size());
        for (MachineBridge bridge : bridges.values()) {
            entries.add(new MachineBridgesPayload.Entry(bridge.pos(), bridge.face(), bridge.led()));
        }
        return new MachineBridgesPayload(enabled(), entries);
    }

    // To the level's players that have the channel (not fake or mock players).
    private void sync(ServerLevel level) {
        shownChanged = false;
        MachineBridgesPayload payload = null;
        for (ServerPlayer player : level.players()) {
            if (player.connection.hasChannel(MachineBridgesPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, payload != null ? payload : (payload = payload()));
            }
        }
    }

    private static void syncTo(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null && serverPlayer.connection.hasChannel(MachineBridgesPayload.TYPE)) {
            PacketDistributor.sendToPlayer(serverPlayer, get(serverPlayer.level()).payload());
        }
    }

    // --- Events ---

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MachineBridges::takeOff);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> syncTo(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> syncTo(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> syncTo(event.getEntity()));
    }

    // Sneak-use with an empty hand on a bridge's face: it comes off into the hand (build permission on its network while
    // it's linked). On the client, the same use is taken (the machine's screen doesn't open) where a bridge shows.
    private static void takeOff(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!(event.getLevel() instanceof ServerLevel level)) {
            if (MachineBridgesPayload.faceAt(pos) == event.getFace() && event.getFace() != null) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        MachineBridge bridge = at(level, pos);
        if (bridge == null || bridge.face() != event.getFace()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (bridge.link() != null && !NetworkAccess.guard(level.getServer(), bridge.link().network(), player, RackPermission.BUILD)) {
            return;
        }
        get(level).remove(level, bridge, false);
        player.getInventory().placeItemBackInInventory(ModItems.SMALL_WIRELESS_BRIDGE.toStack(), Prediction.SERVER_ONLY);
        player.sendOverlayMessage(Component.translatable("message.encodedlogistics.small_bridge.removed"));
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    // Whether a player may use the machine at all: the owner its mod tracks, anyone when it tracks none, and operators.
    public static boolean mayUse(Player player, MachineInfo info) {
        return info.owner().isEmpty() || Objects.equals(info.owner().get(), player.getUUID()) || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    // Whether a player may link the bridge to a network (a Link Card): only one who may use its machine, so nobody puts
    // another player's machine on their own network. Tells the player when not.
    public static boolean mayLink(Player player, MachineBridge bridge) {
        MachineInfo info = player.level().getServer() != null ? bridge.info(player.level().getServer()) : null;
        if (info == null || mayUse(player, info)) {
            return true;
        }
        player.sendOverlayMessage(Component.translatable("message.encodedlogistics.small_bridge.not_yours", info.name()));
        return false;
    }
}
