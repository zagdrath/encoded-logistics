/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Router (1U): the WAN edge. It moves items between separate networks, however far apart or whichever dimension
// they're in: the network it serves (endpoint 0) and every network linked to it with a Link Card (sneak-use the card on
// any block of that network, then use it on the Router's unit in an open rack). Each route moves items from one
// network's storage to another's, the items its filter passes (ItemRouting). Once a second it moves up to its rate
// (routerBaseRate items/s, routerRatePerTransceiver more for each Optical Transceiver in its three cages), shared
// between its active routes in turn. (Routing between the segments of one installation is the L3 Switch's.) A Share route
// moves nothing: the source network's items are part of the destination's storage instead (ItemRouting.ShareSource).
public class RouterDevice extends RackDevice implements ItemRouting.ShareSource {
    public static final int MAX_NETWORKS = 6, MAX_ROUTES = 6, CAGES = 3;
    // ACTION_SET_FILTER: value is route * PartFilter.SIZE + entry; ACTION_FILTER_OPTION: route * ItemRouting.OPTIONS + option.
    public static final int ACTION_ADD_ROUTE = 0, ACTION_REMOVE_ROUTE = 1, ACTION_CYCLE_SOURCE = 2, ACTION_CYCLE_DEST = 3, ACTION_SET_FILTER = 4,
            ACTION_RENAME = 5, ACTION_UNLINK = 6, ACTION_FILTER_OPTION = 7, ACTION_SHARE_OPTION = 14;
    private static final int WINDOW = 5;

    // A network linked to the Router, found through a block on it (its controller, or anything else on it).
    public record Linked(GlobalPos node, String name) {}

    private final List<Linked> networks = new ArrayList<>();
    private String localName = "";
    private final List<ItemRouting.Route> routes = new ArrayList<>();
    private int cursor;
    // Items moved in each of the last WINDOW seconds.
    private final int[] moved = new int[WINDOW];
    private int movedIndex;
    private int activeRoutes, activeShares;
    // Client: transceivers fitted, as synced.
    private int shownTransceivers;

    public RouterDevice(RackDeviceType type) {
        super(type);
    }

    public int transceiverCount() {
        int count = 0;
        for (ItemStack stack : items()) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // Which cages hold a transceiver (client: as synced).
    public boolean hasTransceiver(int cage) {
        return rack() != null && rack().getLevel() != null && rack().getLevel().isClientSide() ? (shownTransceivers >> cage & 1) != 0
                : !items().get(cage).isEmpty();
    }

    // Transceivers in or out: its drain and rate change.
    @Override
    public void itemsChanged() {
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

    public List<Linked> networks() {
        return networks;
    }

    public List<ItemRouting.Route> routes() {
        return routes;
    }

    // Endpoints: its own network, then the linked ones.
    public int endpointCount() {
        return 1 + networks.size();
    }

    public Component endpointName(int index) {
        String name = index == 0 ? localName : index - 1 < networks.size() ? networks.get(index - 1).name() : "";
        if (!name.isEmpty()) {
            return Component.literal(name);
        }
        return index == 0 ? Component.translatable("gui.encodedlogistics.router.local") : Component.translatable("gui.encodedlogistics.router.network", index);
    }

    @Override
    public double drain() {
        return Config.ROUTER_DRAIN.getAsDouble() + Config.ROUTER_DRAIN_PER_TRANSCEIVER.getAsDouble() * transceiverCount();
    }

    // --- Networks ---

    public enum LinkResult {
        LINKED, ALREADY, FULL
    }

    public LinkResult link(GlobalPos node) {
        for (Linked linked : networks) {
            if (linked.node().equals(node)) {
                return LinkResult.ALREADY;
            }
        }
        if (endpointCount() >= MAX_NETWORKS) {
            return LinkResult.FULL;
        }
        networks.add(new Linked(node, ""));
        changed(false);
        return LinkResult.LINKED;
    }

    // Adds a route from one endpoint to another (by index) for the items filter passes; false when it's full or an
    // endpoint doesn't exist.
    public boolean addRoute(int source, int dest, PartFilter filter) {
        if (routes.size() >= MAX_ROUTES || source < 0 || source >= endpointCount() || dest < 0 || dest >= endpointCount()) {
            return false;
        }
        routes.add(new ItemRouting.Route(source, dest, filter));
        changed(false);
        return true;
    }

    // The network an endpoint is, if it's on one right now.
    public @Nullable NetworkRef network(MinecraftServer server, int index) {
        if (rack() == null) {
            return null;
        }
        if (index == 0) {
            return rack().network(this);
        }
        return index - 1 < networks.size() ? networkAt(server, networks.get(index - 1).node()) : null;
    }

    // The network a block is on: a controller's own, or any other node's.
    public static @Nullable NetworkRef networkAt(MinecraftServer server, GlobalPos node) {
        ServerLevel level = server.getLevel(node.dimension());
        if (level == null || !level.isLoaded(node.pos())) {
            return null;
        }
        if (level.getBlockEntity(node.pos()) instanceof NetworkControllerBlockEntity controller && controller.getStructureId() > 0) {
            return new NetworkRef(node.dimension(), controller.getStructureId());
        }
        return ControllerStructures.networkOf(level, node.pos());
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
        List<ItemRouting.Leg> legs = new ArrayList<>();
        int shares = 0;
        for (ItemRouting.Route route : routes) {
            NetworkRef source = network(server, route.source()), dest = network(server, route.dest());
            if (source == null || dest == null || source.equals(dest)) {
                continue;
            }
            if (route.shares()) {
                shares++;
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
        int total = ItemRouting.move(legs, rate(), cursor, key -> ItemRouting.HIGH);
        cursor = legs.isEmpty() ? 0 : (cursor + 1) % legs.size();
        return total;
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return List.of(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.networks"),
                        Component.literal(Integer.toString(networks.size()))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.routes"), ItemRouting.split(activeRoutes, activeShares)),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.throughput"),
                        Component.translatable("gui.encodedlogistics.router.throughput", String.format(Locale.ROOT, "%.1f", throughput())),
                        new RackDeviceInfo.Bar((float) Math.min(1, throughput() / Math.max(1, rate())), RackDeviceInfo.BarStyle.NORMAL)));
    }

    // --- Sharing ---

    @Override
    public List<ItemRouting.ShareLink> shareLinks(MinecraftServer server) {
        return rack() != null && isOnline() ? ItemRouting.shareLinks(routes, index -> network(server, index), this::endpointName) : List.of();
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        switch (action) {
            case ACTION_ADD_ROUTE -> {
                // An empty allow list: it moves nothing until it's set up.
                if (!addRoute(0, endpointCount() > 1 ? 1 : 0, new PartFilter())) {
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
                ItemRouting.Route route = routes.get(value);
                int n = endpointCount();
                routes.set(value, action == ACTION_CYCLE_SOURCE ? route.withSource((route.source() + 1) % n) : route.withDest((route.dest() + 1) % n));
            }
            case ACTION_SET_FILTER -> {
                if (!ItemRouting.setEntry(routes, value, filter(player, text))) {
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
            case ACTION_RENAME -> {
                String name = (text.length() > 24 ? text.substring(0, 24) : text).trim();
                if (value == 0) {
                    localName = name;
                } else if (value - 1 >= 0 && value - 1 < networks.size()) {
                    networks.set(value - 1, new Linked(networks.get(value - 1).node(), name));
                } else {
                    return;
                }
            }
            case ACTION_UNLINK -> {
                if (value < 1 || value - 1 >= networks.size()) {
                    return;
                }
                networks.remove(value - 1);
                List<ItemRouting.Route> kept = ItemRouting.withoutEndpoint(routes, value);
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
        // Which endpoints are reachable right now.
        int reachable = 0;
        for (int i = 0; i < endpointCount(); i++) {
            if (network(viewer.level().getServer(), i) != null) {
                reachable |= 1 << i;
            }
        }
        output.putInt("reachable", reachable);
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
        ValueOutput.ValueOutputList list = output.childrenList("networks");
        for (Linked linked : networks) {
            ValueOutput child = list.addChild();
            child.store("node", GlobalPos.CODEC, linked.node());
            child.putString("name", linked.name());
        }
        ItemRouting.save(output, "routes", routes);
    }

    @Override
    public void loadSettings(ValueInput input) {
        localName = input.getStringOr("local_name", "");
        networks.clear();
        // Routers saved before the WAN change linked Segment Isolator segments; each was a network too.
        String key = input.childrenList("networks").isPresent() ? "networks" : "segments";
        for (ValueInput child : input.childrenListOrEmpty(key)) {
            child.read("node", GlobalPos.CODEC).ifPresent(node -> networks.add(new Linked(node, child.getStringOr("name", ""))));
        }
        routes.clear();
        routes.addAll(ItemRouting.load(input, "routes", endpointCount(), MAX_ROUTES));
    }

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
        saveItems(output);
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
        loadItems(input);
        // Routers saved before item slots kept their transceivers here.
        for (ValueInput child : input.childrenListOrEmpty("transceivers")) {
            int slot = child.getIntOr("slot", -1);
            if (slot >= 0 && slot < CAGES) {
                items().set(slot, child.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
            }
        }
    }

    @Override
    public void writeClient(ValueOutput output) {
        int cages = 0;
        for (int i = 0; i < CAGES; i++) {
            if (!items().get(i).isEmpty()) {
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
