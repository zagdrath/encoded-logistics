/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.menu.BlockPanelMenu;
import net.zagdrath.encodedlogistics.menu.PanelHost;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// The Trunk Port (the routing handoff 4): VLAN trunking on a cable. It carries segments (networks beyond Segment
// Isolators, each linked with a Link Card used on it and given a segment ID) into the network it's on: every Server Rack
// on that network can put its pooled devices on them as on its own linked segments, without a Link Card of its own.
// ACCESS: it carries one segment (its native one); TRUNK: the allowed segments (all, or those picked), the native one
// for untagged traffic. Its panel shows the lanes the devices on its segments use against trunkPortLanes, as a guide
// (each device's lanes are its rack's to grant). Segments on a trunk
// still can't see each other: only an L3 Switch or a Router routes between them.
public class TrunkPortPart extends CablePart implements PanelHost {
    public static final int MAX_SEGMENTS = 8, MAX_ID = 4094, ID_STEP = 10;
    public static final int ACTION_MODE = 0, ACTION_ALLOW = 1, ACTION_NATIVE = 2, ACTION_UNLINK = 3;
    private static final Map<MinecraftServer, Map<NetworkRef, Carried>> CACHE = new WeakHashMap<>();

    public enum Mode {
        ACCESS, TRUNK
    }

    // A segment it carries: its ID and a block on its network.
    public record Segment(int id, GlobalPos node) {}

    private record Carried(long key, List<Segment> segments) {}

    public enum LinkResult {
        LINKED, UNLINKED, FULL
    }

    private final List<Segment> segments = new ArrayList<>();
    private Mode mode = Mode.TRUNK;
    // TRUNK: whether it carries all its segments, else the IDs it carries (none: its native one alone). The native
    // segment's ID (ACCESS: the one it carries).
    private boolean allowAll = true;
    private final Set<Integer> allowed = new LinkedHashSet<>();
    private int nativeId;

    public TrunkPortPart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public List<Segment> segments() {
        return segments;
    }

    public Mode mode() {
        return mode;
    }

    public Set<Integer> allowed() {
        return allowed;
    }

    public int nativeId() {
        return nativeId;
    }

    // What it carries now.
    public List<Segment> carried() {
        List<Segment> carried = new ArrayList<>();
        for (Segment segment : segments) {
            boolean in = mode == Mode.ACCESS ? segment.id() == nativeId : allowAll || allowed.contains(segment.id()) || segment.id() == nativeId;
            if (in) {
                carried.add(segment);
            }
        }
        return carried;
    }

    // The segments the Trunk Ports on a network carry (online ones; the first port to carry an ID has it), cached
    // until anything networks are made of changes.
    public static List<Segment> carriedOn(MinecraftServer server, @Nullable NetworkRef network) {
        if (network == null) {
            return List.of();
        }
        long key = ControllerStructures.cacheKey(server);
        Map<NetworkRef, Carried> cache;
        synchronized (CACHE) {
            cache = CACHE.computeIfAbsent(server, k -> new HashMap<>());
        }
        Carried known = cache.get(network);
        if (known != null && known.key() == key) {
            return known.segments();
        }
        List<Segment> found = new ArrayList<>();
        Set<Integer> ids = new java.util.HashSet<>();
        for (CableBlockEntity host : ControllerStructures.partHosts(server, network)) {
            for (CablePart part : host.parts()) {
                if (part instanceof TrunkPortPart trunk && trunk.isOnline()) {
                    for (Segment segment : trunk.carried()) {
                        if (ids.add(segment.id())) {
                            found.add(segment);
                        }
                    }
                }
            }
        }
        List<Segment> result = List.copyOf(found);
        cache.put(network, new Carried(key, result));
        return result;
    }

    // A Link Card's network: linked (with the next free ID), or unlinked when it carries it already.
    public LinkResult toggle(GlobalPos node) {
        for (Segment segment : segments) {
            if (segment.node().equals(node)) {
                segments.remove(segment);
                allowed.remove(segment.id());
                if (nativeId == segment.id()) {
                    nativeId = 0;
                }
                configChanged();
                return LinkResult.UNLINKED;
            }
        }
        if (segments.size() >= MAX_SEGMENTS) {
            return LinkResult.FULL;
        }
        int id = ID_STEP;
        for (Segment segment : segments) {
            id = Math.max(id, (segment.id() / ID_STEP + 1) * ID_STEP);
        }
        segments.add(new Segment(Math.min(MAX_ID, id), node));
        configChanged();
        return LinkResult.LINKED;
    }

    private void configChanged() {
        changed();
        ControllerStructures.contentChanged();
    }

    @Override
    public boolean lit() {
        return isOnline() && !carried().isEmpty();
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        BlockPanelMenu.open(player, this, host.getBlockPos());
        return true;
    }

    // Lanes the devices on its segments use, of the racks on its network.
    public int lanesUsed(MinecraftServer server) {
        NetworkRef network = host.getLevel() instanceof ServerLevel level ? ControllerStructures.networkOf(level, host.getBlockPos()) : null;
        Set<Integer> ids = new java.util.HashSet<>();
        for (Segment segment : carried()) {
            ids.add(segment.id());
        }
        int used = 0;
        for (RackBlockEntity rack : ControllerStructures.onNetwork(server, network, RackBlockEntity.class, false)) {
            for (RackDevice device : rack.devices()) {
                if (device.lanePool() == null && (ids.contains(device.segment()) || device.trunk() && rack.servedIds(device).stream().anyMatch(ids::contains))) {
                    used += device.laneCost();
                }
            }
        }
        return used;
    }

    // --- Panel ---

    @Override
    public String panelKind() {
        return "trunk";
    }

    @Override
    public Component panelTitle() {
        return Component.translatable("gui.encodedlogistics.trunk.title", deviceName().isEmpty() ? "" : deviceName());
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        MinecraftServer server = viewer.level().getServer();
        output.putString("mode", mode.name());
        output.putInt("native", nativeId);
        output.putInt("lanes_used", lanesUsed(server));
        output.putInt("lanes", Config.TRUNK_PORT_LANES.getAsInt());
        output.putBoolean("online", isOnline());
        ValueOutput.ValueOutputList list = output.childrenList("segments");
        for (Segment segment : segments) {
            ValueOutput row = list.addChild();
            row.putInt("id", segment.id());
            NetworkRef network = RouterDevice.networkAt(server, segment.node());
            row.putString("network", network != null ? RouterDevice.networkName(server, network) : "");
            row.putBoolean("allowed", allowAll || allowed.contains(segment.id()));
        }
        output.putBoolean("all", allowAll);
    }

    @Override
    public void handlePanelAction(ServerPlayer player, int action, int value, String text) {
        if (!(host.getLevel() instanceof ServerLevel level) || !NetworkAccess.check(level, host.getBlockPos(), player, RackPermission.BUILD)) {
            return;
        }
        switch (action) {
            case ACTION_MODE -> mode = mode == Mode.ACCESS ? Mode.TRUNK : Mode.ACCESS;
            case ACTION_ALLOW -> {
                // value: a segment's row (from all: every other one); -1 back to all.
                if (value < 0) {
                    allowed.clear();
                    allowAll = true;
                } else if (value < segments.size()) {
                    int id = segments.get(value).id();
                    if (allowAll) {
                        allowAll = false;
                        segments.forEach(segment -> allowed.add(segment.id()));
                    }
                    if (!allowed.remove(id)) {
                        allowed.add(id);
                    }
                }
            }
            case ACTION_NATIVE -> nativeId = value >= 0 && value < segments.size() && segments.get(value).id() != nativeId ? segments.get(value).id() : 0;
            case ACTION_UNLINK -> {
                if (value < 0 || value >= segments.size()) {
                    return;
                }
                toggle(segments.get(value).node());
                return;
            }
            default -> {
                return;
            }
        }
        configChanged();
    }

    // --- Saving ---

    @Override
    public void load(ValueInput input) {
        segments.clear();
        for (ValueInput child : input.childrenListOrEmpty("segments")) {
            child.read("node", GlobalPos.CODEC).ifPresent(node -> segments.add(new Segment(child.getIntOr("id", ID_STEP), node)));
        }
        mode = input.getStringOr("mode", "TRUNK").equals("ACCESS") ? Mode.ACCESS : Mode.TRUNK;
        allowAll = input.getBooleanOr("allow_all", true);
        allowed.clear();
        for (int id : input.getIntArray("allowed").orElse(new int[0])) {
            allowed.add(id);
        }
        nativeId = input.getIntOr("native", 0);
    }

    @Override
    public void save(ValueOutput output) {
        ValueOutput.ValueOutputList list = output.childrenList("segments");
        for (Segment segment : segments) {
            ValueOutput child = list.addChild();
            child.putInt("id", segment.id());
            child.store("node", GlobalPos.CODEC, segment.node());
        }
        output.putString("mode", mode.name());
        if (!allowAll) {
            output.putBoolean("allow_all", false);
            output.putIntArray("allowed", allowed.stream().mapToInt(Integer::intValue).toArray());
        }
        output.putInt("native", nativeId);
    }
}
