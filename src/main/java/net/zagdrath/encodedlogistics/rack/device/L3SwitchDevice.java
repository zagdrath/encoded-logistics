/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The L3 Switch (1U): an L2 Switch's lane pool (32 lanes), plus routing between the segments its rack knows (the rack's
// own network and the networks beyond Segment Isolators it's linked to): each route moves items from one segment's
// storage to another's, optionally only one item. Once a second it moves up to l3SwitchRate items, shared between its
// active routes (each with a port's filter: ItemRouting); QoS rules mark items high, normal or low priority, and each route moves its high-priority items
// first, then normal, then low. A Share route moves nothing: the source segment's items are part of the destination's
// storage instead, while the switch is online (ItemRouting.ShareSource; QoS has nothing to order there).
public class L3SwitchDevice extends SwitchDevice implements ItemRouting.ShareSource {
    public static final int MAX_ROUTES = 8, MAX_QOS = 8;
    public static final int ACTION_ADD_ROUTE = 0, ACTION_REMOVE_ROUTE = 1, ACTION_CYCLE_SOURCE = 2, ACTION_CYCLE_DEST = 3, ACTION_SET_FILTER = 4,
            ACTION_FILTER_OPTION = 7, ACTION_ADD_QOS = 10, ACTION_REMOVE_QOS = 11, ACTION_CYCLE_QOS_LEVEL = 12, ACTION_SET_QOS_FILTER = 13,
            ACTION_SHARE_OPTION = 14;
    private static final int WINDOW = 5;

    // Items matching filter (any item when empty) have this priority level.
    public record QosRule(ItemStack filter, int level) {
        boolean matches(StorageKey key) {
            return filter.isEmpty() || key.stack().is(filter.getItem());
        }
    }

    private final List<ItemRouting.Route> routes = new ArrayList<>();
    private final List<QosRule> qos = new ArrayList<>();
    private int cursor, activeRoutes, activeShares, movedIndex;
    private final int[] moved = new int[WINDOW];

    public L3SwitchDevice(RackDeviceType type) {
        super(type);
    }

    @Override
    public int capacity() {
        return Config.L3_SWITCH_LANES.getAsInt();
    }

    @Override
    public double drain() {
        return Config.L3_SWITCH_DRAIN.getAsDouble();
    }

    public List<ItemRouting.Route> routes() {
        return routes;
    }

    public List<QosRule> qos() {
        return qos;
    }

    private int endpoints() {
        return rack() != null ? rack().segmentCount() : 1;
    }

    // An item's priority level: the highest of the rules it matches, or normal.
    public int level(StorageKey key) {
        int level = ItemRouting.NORMAL;
        boolean matched = false;
        for (QosRule rule : qos) {
            if (rule.matches(key)) {
                level = matched ? Math.min(level, rule.level()) : rule.level();
                matched = true;
            }
        }
        return level;
    }

    public double throughput() {
        int total = 0;
        for (int amount : moved) {
            total += amount;
        }
        return (double) total / WINDOW;
    }

    // Adds a route between two of the rack's segments (by index) for the items filter passes; false when it's full or a
    // segment doesn't exist.
    public boolean addRoute(int source, int dest, PartFilter filter) {
        if (routes.size() >= MAX_ROUTES || source < 0 || source >= endpoints() || dest < 0 || dest >= endpoints()) {
            return false;
        }
        routes.add(new ItemRouting.Route(source, dest, filter));
        changed(false);
        return true;
    }

    // --- Moving items ---

    @Override
    public void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        moved[movedIndex] = isOnline() ? route(level.getServer()) : 0;
        movedIndex = (movedIndex + 1) % WINDOW;
        saveOnly();
    }

    private int route(MinecraftServer server) {
        List<ItemRouting.Leg> legs = new ArrayList<>();
        int shares = 0;
        for (ItemRouting.Route route : routes) {
            NetworkRef source = rack().segmentNetwork(route.source()), dest = rack().segmentNetwork(route.dest());
            if (route.shares()) {
                if (source != null && dest != null && !source.equals(dest)) {
                    shares++;
                }
                continue;
            }
            if (source == null || dest == null || source.equals(dest)) {
                continue;
            }
            NetworkStorage from = ControllerStructures.storageOf(server, source), to = ControllerStructures.storageOf(server, dest);
            if (from != null && to != null) {
                legs.add(new ItemRouting.Leg(route, from, to));
            }
        }
        if (activeRoutes != legs.size() || activeShares != shares) {
            activeRoutes = legs.size();
            activeShares = shares;
            changed(false);
        }
        int total = ItemRouting.move(legs, Config.L3_SWITCH_RATE.getAsInt(), cursor, this::level);
        cursor = legs.isEmpty() ? 0 : (cursor + 1) % legs.size();
        return total;
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>(super.lines(viewer));
        // Lanes, then routes and QoS, then the uplink last.
        RackDeviceInfo.InfoLine uplink = lines.removeLast();
        lines.removeLast();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.l3.routes"), ItemRouting.split(activeRoutes, activeShares)));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.l3.qos"), Component.literal(Integer.toString(qos.size()))));
        lines.add(uplink);
        return lines;
    }

    // --- Sharing ---

    @Override
    public List<ItemRouting.ShareLink> shareLinks(MinecraftServer server) {
        RackBlockEntity rack = rack();
        if (rack == null || !isOnline()) {
            return List.of();
        }
        return ItemRouting.shareLinks(routes, rack::segmentNetwork, rack::segmentName);
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (action == ACTION_CYCLE_SEGMENT) {
            super.handleAction(player, action, value, text);
            return;
        }
        int n = endpoints();
        ItemStack filter = filter(player, text);
        switch (action) {
            case ACTION_ADD_ROUTE -> {
                if (routes.size() >= MAX_ROUTES) {
                    return;
                }
                // An empty allow list: it moves nothing until it's set up.
                routes.add(new ItemRouting.Route(0, n > 1 ? 1 : 0));
            }
            case ACTION_REMOVE_ROUTE -> {
                if (value < 0 || value >= routes.size()) {
                    return;
                }
                routes.remove(value);
            }
            case ACTION_CYCLE_SOURCE, ACTION_CYCLE_DEST -> {
                if (value < 0 || value >= routes.size()) {
                    return;
                }
                ItemRouting.Route route = routes.get(value);
                routes.set(value, action == ACTION_CYCLE_SOURCE ? route.withSource((route.source() + 1) % n) : route.withDest((route.dest() + 1) % n));
            }
            case ACTION_SET_FILTER -> {
                if (!ItemRouting.setEntry(routes, value, filter)) {
                    return;
                }
            }
            case ACTION_FILTER_OPTION -> {
                if (!ItemRouting.toggleOption(routes, value)) {
                    return;
                }
            }
            case ACTION_SHARE_OPTION -> {
                if (!ItemRouting.toggleShare(routes, value)) {
                    return;
                }
            }
            case ACTION_ADD_QOS -> {
                if (qos.size() >= MAX_QOS) {
                    return;
                }
                qos.add(new QosRule(filter, ItemRouting.HIGH));
            }
            case ACTION_REMOVE_QOS -> {
                if (value < 0 || value >= qos.size()) {
                    return;
                }
                qos.remove(value);
            }
            case ACTION_CYCLE_QOS_LEVEL -> {
                if (value < 0 || value >= qos.size()) {
                    return;
                }
                QosRule rule = qos.get(value);
                qos.set(value, new QosRule(rule.filter(), (rule.level() + 1) % ItemRouting.LEVELS));
            }
            case ACTION_SET_QOS_FILTER -> {
                if (value < 0 || value >= qos.size()) {
                    return;
                }
                qos.set(value, new QosRule(filter, qos.get(value).level()));
            }
            default -> {
                return;
            }
        }
        changed(false);
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        super.writePanel(output, viewer);
        output.putInt("rate", Config.L3_SWITCH_RATE.getAsInt());
        output.putDouble("throughput", throughput());
        output.putString("throughput_text", String.format(Locale.ROOT, "%.1f", throughput()));
    }

    // --- Saving ---

    @Override
    public void saveSettings(ValueOutput output) {
        ItemRouting.save(output, "routes", routes);
        ValueOutput.ValueOutputList list = output.childrenList("qos");
        for (QosRule rule : qos) {
            ValueOutput child = list.addChild();
            child.putInt("level", rule.level());
            if (!rule.filter().isEmpty()) {
                child.store("filter", ItemStack.CODEC, rule.filter());
            }
        }
    }

    @Override
    public void loadSettings(ValueInput input) {
        routes.clear();
        // Segment indices are the rack's: up to 1 + the most it can know.
        routes.addAll(ItemRouting.load(input, "routes", 1 + RackBlockEntity.MAX_SEGMENTS, MAX_ROUTES));
        qos.clear();
        for (ValueInput child : input.childrenListOrEmpty("qos")) {
            if (qos.size() < MAX_QOS) {
                qos.add(new QosRule(child.read("filter", ItemStack.CODEC).orElse(ItemStack.EMPTY),
                        Math.clamp(child.getIntOr("level", ItemRouting.HIGH), 0, ItemRouting.LEVELS - 1)));
            }
        }
    }
}
