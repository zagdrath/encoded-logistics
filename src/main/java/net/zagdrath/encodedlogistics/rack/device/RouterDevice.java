/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Router (1U): moves items between network segments that Segment Isolators keep apart. It knows its rack's own
// network (segment 0) and every segment linked to it with a Link Card (sneak-use the card on a Segment Isolator's end to
// record the segment on that side, then use it on the Router's unit in an open rack). Each route moves items from one
// segment's storage to another's, optionally only one item (a ghost filter). Once a second it moves up to its rate
// (routerBaseRate items/s, routerRatePerTransceiver more for each Optical Transceiver in its three cages), shared
// between its active routes in turn.
public class RouterDevice extends RackDevice {
    public static final int MAX_SEGMENTS = 6, MAX_ROUTES = 6, CAGES = 3;
    public static final int ACTION_ADD_ROUTE = 0, ACTION_REMOVE_ROUTE = 1, ACTION_CYCLE_SOURCE = 2, ACTION_CYCLE_DEST = 3, ACTION_SET_FILTER = 4,
            ACTION_RENAME = 5, ACTION_UNLINK = 6;
    private static final int WINDOW = 5;

    // A segment linked to the Router, found through a node on it (the block next to a Segment Isolator's end).
    public record Segment(GlobalPos node, String name) {}

    public record Route(int source, int dest, ItemStack filter) {
        boolean matches(ItemKey key) {
            return filter.isEmpty() || key.stack().is(filter.getItem());
        }
    }

    private final List<Segment> segments = new ArrayList<>();
    private String localName = "";
    private final List<Route> routes = new ArrayList<>();
    private final NonNullList<ItemStack> transceivers = NonNullList.withSize(CAGES, ItemStack.EMPTY);
    private int cursor;
    // Items moved in each of the last WINDOW seconds.
    private final int[] moved = new int[WINDOW];
    private int movedIndex;
    private int activeRoutes;
    // Client: transceivers fitted, as synced.
    private int shownTransceivers;

    public RouterDevice(RackDeviceType type) {
        super(type);
    }

    public int transceiverCount() {
        int count = 0;
        for (ItemStack stack : transceivers) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // Which cages hold a transceiver (client: as synced).
    public boolean hasTransceiver(int cage) {
        return rack() != null && rack().getLevel() != null && rack().getLevel().isClientSide() ? (shownTransceivers >> cage & 1) != 0
                : !transceivers.get(cage).isEmpty();
    }

    public NonNullList<ItemStack> transceivers() {
        return transceivers;
    }

    public void transceiversChanged() {
        changed(true);
    }

    // Items per second.
    public int rate() {
        return Config.ROUTER_BASE_RATE.getAsInt() + Config.ROUTER_RATE_PER_TRANSCEIVER.getAsInt() * transceiverCount();
    }

    public double throughput() {
        int total = 0;
        for (int amount : moved) {
            total += amount;
        }
        return (double) total / WINDOW;
    }

    public List<Segment> segments() {
        return segments;
    }

    public List<Route> routes() {
        return routes;
    }

    public int segmentCount() {
        return 1 + segments.size();
    }

    public Component segmentName(int index) {
        String name = index == 0 ? localName : index - 1 < segments.size() ? segments.get(index - 1).name() : "";
        if (!name.isEmpty()) {
            return Component.literal(name);
        }
        return index == 0 ? Component.translatable("gui.encodedlogistics.router.local") : Component.translatable("gui.encodedlogistics.router.segment", index);
    }

    @Override
    public double drain() {
        return Config.ROUTER_DRAIN.getAsDouble() + Config.ROUTER_DRAIN_PER_TRANSCEIVER.getAsDouble() * transceiverCount();
    }

    // --- Segments ---

    public enum LinkResult {
        LINKED, ALREADY, FULL
    }

    public LinkResult link(GlobalPos node) {
        for (Segment segment : segments) {
            if (segment.node().equals(node)) {
                return LinkResult.ALREADY;
            }
        }
        if (segmentCount() >= MAX_SEGMENTS) {
            return LinkResult.FULL;
        }
        segments.add(new Segment(node, ""));
        changed(false);
        return LinkResult.LINKED;
    }

    // Adds a route from one segment to another (by index), for any item or just the filter's; false when it's full or a
    // segment doesn't exist.
    public boolean addRoute(int source, int dest, ItemStack filter) {
        if (routes.size() >= MAX_ROUTES || source < 0 || source >= segmentCount() || dest < 0 || dest >= segmentCount()) {
            return false;
        }
        routes.add(new Route(source, dest, filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1)));
        changed(false);
        return true;
    }

    // The network a segment is, if it's on one right now.
    public @Nullable NetworkRef network(MinecraftServer server, int index) {
        if (rack() == null || !(rack().getLevel() instanceof ServerLevel level)) {
            return null;
        }
        if (index == 0) {
            return ControllerStructures.networkOf(level, rack().getBlockPos());
        }
        if (index - 1 >= segments.size()) {
            return null;
        }
        GlobalPos node = segments.get(index - 1).node();
        ServerLevel there = server.getLevel(node.dimension());
        return there != null && there.isLoaded(node.pos()) ? ControllerStructures.networkOf(there, node.pos()) : null;
    }

    // --- Moving items ---

    @Override
    public void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        int movedNow = isOnline() ? route(level.getServer()) : 0;
        moved[movedIndex] = movedNow;
        movedIndex = (movedIndex + 1) % WINDOW;
        saveOnly();
    }

    // One second's work: up to rate() items, shared between the active routes in turn. Returns how many moved.
    private int route(MinecraftServer server) {
        List<Integer> active = new ArrayList<>();
        List<NetworkStorage[]> storages = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            NetworkRef source = network(server, route.source()), dest = network(server, route.dest());
            if (source == null || dest == null || source.equals(dest)) {
                continue;
            }
            NetworkStorage from = ControllerStructures.storageOf(server, source), to = ControllerStructures.storageOf(server, dest);
            if (from != null && to != null) {
                active.add(i);
                storages.add(new NetworkStorage[] { from, to });
            }
        }
        if (activeRoutes != active.size()) {
            activeRoutes = active.size();
            changed(false);
        }
        if (active.isEmpty()) {
            return 0;
        }
        int budget = rate(), total = 0;
        int count = active.size();
        cursor = Math.floorMod(cursor, count);
        for (int n = 0; n < count && budget > 0; n++) {
            int at = (cursor + n) % count;
            // An even share, the remainder to the routes whose turn comes first; what a route can't use goes on to
            // the next.
            int share = Math.max(1, budget / (count - n));
            int done = move(routes.get(active.get(at)), storages.get(at)[0], storages.get(at)[1], share);
            budget -= done;
            total += done;
        }
        cursor = (cursor + 1) % count;
        return total;
    }

    private static int move(Route route, NetworkStorage from, NetworkStorage to, int limit) {
        int left = limit;
        for (Map.Entry<ItemKey, Long> entry : from.list().entrySet()) {
            if (left <= 0) {
                break;
            }
            ItemKey key = entry.getKey();
            if (!route.matches(key)) {
                continue;
            }
            long fits = to.insert(key, Math.min(left, entry.getValue()), true);
            if (fits <= 0) {
                continue;
            }
            long taken = from.extract(key, fits, false);
            long put = to.insert(key, taken, false);
            if (put < taken) {
                from.insert(key, taken - put, false);
            }
            left -= (int) put;
        }
        return limit - left;
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return List.of(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.routes"),
                        Component.literal(activeRoutes + " / " + routes.size())),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.throughput"),
                        Component.translatable("gui.encodedlogistics.router.throughput", String.format(Locale.ROOT, "%.1f", throughput())),
                        new RackDeviceInfo.Bar((float) Math.min(1, throughput() / Math.max(1, rate())), RackDeviceInfo.BarStyle.NORMAL)));
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        switch (action) {
            case ACTION_ADD_ROUTE -> {
                if (!addRoute(0, segmentCount() > 1 ? 1 : 0, ItemStack.EMPTY)) {
                    return;
                }
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
                Route route = routes.get(value);
                int n = segmentCount();
                routes.set(value, action == ACTION_CYCLE_SOURCE ? new Route((route.source() + 1) % n, route.dest(), route.filter())
                        : new Route(route.source(), (route.dest() + 1) % n, route.filter()));
            }
            case ACTION_SET_FILTER -> {
                if (value < 0 || value >= routes.size()) {
                    return;
                }
                Route route = routes.get(value);
                ItemStack carried = player.containerMenu.getCarried();
                routes.set(value, new Route(route.source(), route.dest(), carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1)));
            }
            case ACTION_RENAME -> {
                String name = text.length() > 24 ? text.substring(0, 24) : text;
                if (value == 0) {
                    localName = name.trim();
                } else if (value - 1 >= 0 && value - 1 < segments.size()) {
                    segments.set(value - 1, new Segment(segments.get(value - 1).node(), name.trim()));
                } else {
                    return;
                }
            }
            case ACTION_UNLINK -> {
                if (value < 1 || value - 1 >= segments.size()) {
                    return;
                }
                segments.remove(value - 1);
                // Routes through it go; later segments move down one.
                List<Route> kept = new ArrayList<>();
                for (Route route : routes) {
                    if (route.source() != value && route.dest() != value) {
                        kept.add(new Route(route.source() > value ? route.source() - 1 : route.source(),
                                route.dest() > value ? route.dest() - 1 : route.dest(), route.filter()));
                    }
                }
                routes.clear();
                routes.addAll(kept);
            }
            default -> {
                return;
            }
        }
        changed(false);
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        output.putInt("rate", rate());
        output.putDouble("throughput", throughput());
        output.putInt("active", activeRoutes);
        // Which segments are reachable right now.
        int reachable = 0;
        for (int i = 0; i < segmentCount(); i++) {
            if (network(viewer.level().getServer(), i) != null) {
                reachable |= 1 << i;
            }
        }
        output.putInt("reachable", reachable);
    }

    // --- Contents ---

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> stacks = new ArrayList<>();
        transceivers.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> stacks.add(stack.copy()));
        return stacks;
    }

    @Override
    public void clearContents() {
        transceivers.replaceAll(stack -> ItemStack.EMPTY);
    }

    public static boolean isTransceiver(ItemStack stack) {
        return stack.is(ModItems.OPTICAL_TRANSCEIVER.get());
    }

    // --- Saving ---

    @Override
    public void saveSettings(ValueOutput output) {
        if (!localName.isEmpty()) {
            output.putString("local_name", localName);
        }
        ValueOutput.ValueOutputList segmentList = output.childrenList("segments");
        for (Segment segment : segments) {
            ValueOutput child = segmentList.addChild();
            child.store("node", GlobalPos.CODEC, segment.node());
            child.putString("name", segment.name());
        }
        ValueOutput.ValueOutputList routeList = output.childrenList("routes");
        for (Route route : routes) {
            ValueOutput child = routeList.addChild();
            child.putInt("source", route.source());
            child.putInt("dest", route.dest());
            if (!route.filter().isEmpty()) {
                child.store("filter", ItemStack.CODEC, route.filter());
            }
        }
    }

    @Override
    public void loadSettings(ValueInput input) {
        localName = input.getStringOr("local_name", "");
        segments.clear();
        for (ValueInput child : input.childrenListOrEmpty("segments")) {
            child.read("node", GlobalPos.CODEC).ifPresent(node -> segments.add(new Segment(node, child.getStringOr("name", ""))));
        }
        routes.clear();
        int n = segmentCount();
        for (ValueInput child : input.childrenListOrEmpty("routes")) {
            int source = child.getIntOr("source", 0), dest = child.getIntOr("dest", 0);
            if (source >= 0 && source < n && dest >= 0 && dest < n && routes.size() < MAX_ROUTES) {
                routes.add(new Route(source, dest, child.read("filter", ItemStack.CODEC).orElse(ItemStack.EMPTY)));
            }
        }
    }

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
        ValueOutput.ValueOutputList cages = output.childrenList("transceivers");
        for (int i = 0; i < CAGES; i++) {
            if (!transceivers.get(i).isEmpty()) {
                ValueOutput child = cages.addChild();
                child.putInt("slot", i);
                child.store("item", ItemStack.CODEC, transceivers.get(i));
            }
        }
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
        clearContents();
        for (ValueInput child : input.childrenListOrEmpty("transceivers")) {
            int slot = child.getIntOr("slot", -1);
            if (slot >= 0 && slot < CAGES) {
                transceivers.set(slot, child.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
            }
        }
    }

    @Override
    public void writeClient(ValueOutput output) {
        int cages = 0;
        for (int i = 0; i < CAGES; i++) {
            if (!transceivers.get(i).isEmpty()) {
                cages |= 1 << i;
            }
        }
        output.putInt("cages", cages);
    }

    @Override
    public void readClient(ValueInput input) {
        shownTransceivers = input.getIntOr("cages", 0);
    }
}
