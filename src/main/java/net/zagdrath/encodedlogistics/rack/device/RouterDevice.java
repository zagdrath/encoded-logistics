/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

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
import net.zagdrath.encodedlogistics.blockentity.ExchangeSwitchBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.item.CrossConnectItem;
import net.zagdrath.encodedlogistics.network.routing.ExchangeRegistry;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.routing.RoutingDomain;
import net.zagdrath.encodedlogistics.network.routing.RoutingGraph;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.RouterAcl;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Router (1U): the WAN edge. It moves items between separate networks, however far apart or whichever dimension
// they're in: the network it serves (endpoint 0) and every network linked to it with a Link Card (sneak-use the card on
// any block of that network, then use it on the Router's unit in an open rack). Each route moves items from one
// network's storage to another's, the items its filter passes (ItemRouting). Once a second it moves up to its rate
// (routerBaseRate items/s, routerRatePerTransceiver more for each Optical Transceiver in its three cages), shared
// between its active routes in turn. (Routing between the segments of one installation is the L3 Switch's.) A Share route
// moves nothing: the source network's items are part of the destination's storage instead (ItemRouting.ShareSource).
//
// Routing mode: STATIC (as above; every Router saved before dynamic routing loads so) or DYNAMIC. A DYNAMIC Router takes
// part in dynamic routing (RoutingDomain): it advertises the networks it links to and learns, from its neighbours, every
// network beyond them, with the cheapest path to each (its route table). Its routes can then go to any of those (a
// remote destination: ItemRouting.REMOTE, by the network's id); their items travel hop by hop along the best path, each
// Router on it carrying them (and spending its own throughput), never landing in a network on the way. A path crosses a
// network only where its Routers allow transit (NONE, LISTED networks, ALL) for the source; while a route has no path,
// it pauses (nothing moves, its Share shows nothing) until there's one again. Its own links (Link Cards) always win over
// paths it learns (*STATIC over *DYNAMIC).
public class RouterDevice extends RackDevice implements ItemRouting.ShareSource {
    public static final int MAX_NETWORKS = 6, MAX_ROUTES = 6, CAGES = 3, MAX_TRANSIT = 16;
    // ACTION_SET_FILTER: value is route * PartFilter.SIZE + entry; ACTION_FILTER_OPTION: route * ItemRouting.OPTIONS + option.
    // ACTION_TRANSIT_NETWORK: value is a route table row; ACTION_NEIGHBOUR_COST: a neighbour row, text "+", "-" or "clear".
    // The ACL actions: text is the ACL's name, then (ADD, EDIT) a rule as RouterAcl.parse reads it; value a rule's
    // sequence number (ADD: the rule to add after, -1 the end), or (BIND) the target (toggleAcl).
    public static final int ACTION_ADD_ROUTE = 0, ACTION_REMOVE_ROUTE = 1, ACTION_CYCLE_SOURCE = 2, ACTION_CYCLE_DEST = 3, ACTION_SET_FILTER = 4,
            ACTION_RENAME = 5, ACTION_UNLINK = 6, ACTION_FILTER_OPTION = 7, ACTION_SHARE_OPTION = 14, ACTION_MODE = 20, ACTION_TRANSIT = 21,
            ACTION_TRANSIT_NETWORK = 22, ACTION_NEIGHBOUR_COST = 23, ACTION_ACL_NEW = 24, ACTION_ACL_DELETE = 25, ACTION_ACL_BIND = 26,
            ACTION_ACL_ADD = 27, ACTION_ACL_EDIT = 28, ACTION_ACL_DEL = 29, ACTION_ACL_UP = 30, ACTION_ACL_DOWN = 31;
    // A neighbour's cost override steps by this in the panel.
    public static final int COST_STEP = 10;
    private static final int WINDOW = 5;

    public enum Mode {
        STATIC, DYNAMIC
    }

    // Where a route table entry came from: a network a neighbour is on (one hop), its own link (a Link Card), or one
    // learned through others.
    public enum Source {
        CONNECTED, STATIC, DYNAMIC;

        public String special() {
            return "*" + name();
        }
    }

    // A route table entry: the destination network, its next hop (direct, a network or IX:name), the path's cost and
    // hops, and since when it's been this (game time).
    // firstHop: the network the path's first hop goes to.
    public record TableEntry(NetworkRef dest, String name, String next, int cost, int hops, Source source, long since, @Nullable NetworkRef firstHop) {}

    public enum PeerTransit {
        DEFAULT, YES, NO
    }

    // Its own side's policy for an exchange peer (by the peer's network): whether it exchanges routes with it (if not,
    // the peering only carries traffic between the two networks), and whether it lets the peer's traffic cross its
    // network (DEFAULT: as its transit setting says).
    public record PeerPolicy(boolean routes, PeerTransit transit) {
        public static final PeerPolicy DEFAULT = new PeerPolicy(true, PeerTransit.DEFAULT);

        public PeerPolicy withRoutes(boolean routes) {
            return new PeerPolicy(routes, transit);
        }

        public PeerPolicy nextTransit() {
            return new PeerPolicy(routes, PeerTransit.values()[(transit.ordinal() + 1) % PeerTransit.values().length]);
        }
    }

    // A neighbour as the panels and ELCL show it: its network, how it's reached, its state and cost (override: -1 none).
    public record NeighbourEntry(NetworkRef home, String name, String via, RoutingDomain.State state, int cost, int override, long since) {}

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

    // --- Dynamic routing ---
    private Mode mode = Mode.STATIC;
    private RoutingDomain.Transit transit = RoutingDomain.Transit.NONE;
    private final Set<NetworkRef> transitList = new LinkedHashSet<>();
    // Per-neighbour cost overrides, by the neighbour's network.
    private final Map<NetworkRef, Integer> costOverrides = new LinkedHashMap<>();
    // Server: the database as last worked out (the domain's version then), and the paths found over it by source.
    private RoutingDomain.@Nullable View view;
    private int viewVersion = -1;
    private long changeSeen = -1, reconverged = -1;
    private final Map<NetworkRef, Map<NetworkRef, RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId>>> paths = new HashMap<>();
    private List<TableEntry> table = List.of();
    private List<NeighbourEntry> neighbourTable = List.of();
    private final Map<NetworkRef, TableEntry> lastBest = new HashMap<>();
    private boolean tableKnown;
    // The network whose best path changed last, and when (DSPROUTE's note).
    private String lastChanged = "";
    private long lastChangedAt = -1;
    // The domain it takes part in, as which Router (to leave it when taken out).
    private @Nullable RoutingDomain joined;
    private RoutingDomain.@Nullable RouterId joinedAs;
    // ACLs by name, and the ones applied to networks it reaches directly.
    private final Map<String, RouterAcl> acls = new LinkedHashMap<>();
    private final Map<NetworkRef, String> networkAcls = new LinkedHashMap<>();
    private final Map<NetworkRef, PeerPolicy> peerPolicies = new LinkedHashMap<>();
    private final Map<Object, Long> lastDenied = new HashMap<>();
    // Routes (bits) that have no path right now, and how many remote Shares are active (a change resets storage caches).
    private int paused, remoteShares = -1;

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
        String name = index == 0 ? localName : index > 0 && index - 1 < networks.size() ? networks.get(index - 1).name() : "";
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
        return index > 0 && index - 1 < networks.size() ? networkAt(server, networks.get(index - 1).node()) : null;
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
        MinecraftServer server = level.getServer();
        long now = server.overworld().getGameTime();
        int movedNow = 0;
        if (isOnline()) {
            syncTargets(server);
            updateRouting(server, now);
            movedNow = route(server, now);
        } else {
            leave();
        }
        moved[movedIndex] = movedNow;
        movedIndex = (movedIndex + 1) % WINDOW;
        saveOnly();
    }

    @Override
    public void onRemoved() {
        leave();
    }

    // Routes to its endpoints keep their network's id (routes saved before network ids get one here).
    private void syncTargets(MinecraftServer server) {
        for (int i = 0; i < routes.size(); i++) {
            ItemRouting.Route route = routes.get(i);
            if (!route.remote()) {
                NetworkRef network = network(server, route.dest());
                if (network != null && !network.equals(route.target())) {
                    routes.set(i, route.withTarget(network));
                }
            }
        }
    }

    // One second's work: up to its free throughput, its routes to its own endpoints first (shared between them in
    // turn), then those to remote networks, each along its path as far as every Router on it can still carry. Returns
    // how many moved.
    private int route(MinecraftServer server, long now) {
        List<ItemRouting.Leg> legs = new ArrayList<>();
        int shares = 0, remoteActive = 0, pausedNow = 0;
        List<Integer> remote = new ArrayList<>();
        for (int i = 0; i < routes.size(); i++) {
            ItemRouting.Route route = routes.get(i);
            if (route.remote()) {
                if (path(server, route) == null) {
                    pausedNow |= 1 << i;
                } else if (route.shares()) {
                    shares++;
                    remoteActive++;
                } else {
                    remote.add(i);
                }
                continue;
            }
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
                legs.add(new ItemRouting.Leg(route, from, to, gate(server, localChecks(route, network(server, 0), source, dest), now)));
            }
        }
        int active = legs.size() + remote.size();
        if (activeRoutes != active || activeShares != shares || paused != pausedNow) {
            activeRoutes = active;
            activeShares = shares;
            paused = pausedNow;
            changed(false);
        }
        if (remoteShares != remoteActive) {
            // What remote Shares put into other networks' storage changed.
            remoteShares = remoteActive;
            ControllerStructures.contentChanged();
        }
        RoutingDomain domain = joined;
        RoutingDomain.RouterId self = joinedAs;
        boolean dynamic = domain != null && self != null && domain.participates(self);
        int budget = dynamic ? domain.remaining(self, now) : rate();
        int total = ItemRouting.move(legs, budget, cursor, key -> ItemRouting.HIGH);
        if (dynamic) {
            domain.charge(self, total, now);
        }
        cursor = active == 0 ? 0 : (cursor + 1) % active;
        for (int n = 0; dynamic && n < remote.size(); n++) {
            ItemRouting.Route route = routes.get(remote.get(Math.floorMod(cursor + n, remote.size())));
            total += forward(server, domain, self, route, now);
        }
        return total;
    }

    // Moves a route's items to its remote network along the best path: as many as its narrowest Router can still carry
    // this second (this one included), charged to every one of them.
    private int forward(MinecraftServer server, RoutingDomain domain, RoutingDomain.RouterId self, ItemRouting.Route route, long now) {
        RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId> path = path(server, route);
        NetworkRef source = network(server, route.source());
        if (path == null || source == null) {
            return 0;
        }
        Set<RoutingDomain.RouterId> carriers = new LinkedHashSet<>();
        carriers.add(self);
        for (RoutingGraph.Hop<NetworkRef, RoutingDomain.RouterId> hop : path.hops()) {
            carriers.addAll(hop.carriers());
        }
        int cap = path.throughput();
        for (RoutingDomain.RouterId carrier : carriers) {
            cap = Math.min(cap, domain.remaining(carrier, now));
        }
        NetworkStorage from = ControllerStructures.storageOf(server, source), to = ControllerStructures.storageOf(server, route.target());
        if (cap <= 0 || from == null || to == null) {
            return 0;
        }
        ItemRouting.Gate gate = gate(server, pathChecks(server, route, source, path), now);
        int done = ItemRouting.move(List.of(new ItemRouting.Leg(route, from, to, gate)), cap, 0, key -> ItemRouting.HIGH);
        for (RoutingDomain.RouterId carrier : carriers) {
            domain.charge(carrier, done, now);
        }
        // Exchange hops (two carriers): their traffic counts at the exchange.
        for (RoutingGraph.Hop<NetworkRef, RoutingDomain.RouterId> hop : path.hops()) {
            if (done > 0 && hop.carriers().size() == 2) {
                ExchangeRegistry.traffic(server, hop.carriers().get(0), hop.carriers().get(1), done, now);
            }
        }
        return done;
    }

    // --- Dynamic routing ---

    public Mode mode() {
        return mode;
    }

    public RoutingDomain.Transit transit() {
        return transit;
    }

    public Set<NetworkRef> transitList() {
        return transitList;
    }

    public Map<NetworkRef, Integer> costOverrides() {
        return costOverrides;
    }

    // Its route table and neighbours, as of its last second (server).
    public List<TableEntry> table() {
        return table;
    }

    public List<NeighbourEntry> neighbourTable() {
        return neighbourTable;
    }

    // Game time its paths were last worked out again after a change (-1: never).
    public long reconverged() {
        return reconverged;
    }

    public String lastChanged() {
        return lastChanged;
    }

    public long lastChangedAt() {
        return lastChangedAt;
    }

    // Whether route index has no path right now.
    public boolean paused(int index) {
        return (paused >> index & 1) != 0;
    }

    public void setMode(Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            if (mode == Mode.STATIC) {
                leave();
            }
            changed(false);
        }
    }

    public void setTransit(RoutingDomain.Transit transit) {
        if (this.transit != transit) {
            this.transit = transit;
            changed(false);
        }
    }

    public RoutingDomain.@Nullable RouterId routerId() {
        return rack() != null && rack().getLevel() != null ? new RoutingDomain.RouterId(rack().getLevel().dimension(), rack().getBlockPos(), u()) : null;
    }

    // A hop's cost through this Router: its throughput (Optical Transceivers) against the base cost.
    public int linkCost() {
        return RoutingGraph.cost(Config.ROUTING_BASE_COST.getAsInt(), Integer.MAX_VALUE, rate());
    }

    public static String networkName(MinecraftServer server, NetworkRef network) {
        return new ElclSystem(server, network).name();
    }

    // Its hello (DYNAMIC), then its paths worked out again when the database changed (once it's been 20 ticks).
    private void updateRouting(MinecraftServer server, long now) {
        NetworkRef home = network(server, 0);
        RoutingDomain.RouterId id = routerId();
        if (mode != Mode.DYNAMIC || home == null || id == null) {
            leave();
            rebuildTable(server, now, home);
            return;
        }
        RoutingDomain domain = RoutingDomain.get(server);
        joined = domain;
        joinedAs = id;
        for (RoutingDomain.Change change : domain.hello(lsa(server, id, home), now)) {
            ElclEvents.neighbour(server, home, deviceName(), change.neighbour().name(), change.up());
        }
        if (domain.version() != viewVersion) {
            if (changeSeen < 0) {
                changeSeen = now;
            }
            if (view == null || now - changeSeen >= RoutingDomain.HELLO_TICKS) {
                view = RoutingDomain.view(domain.database(id));
                viewVersion = domain.version();
                changeSeen = -1;
                reconverged = now;
                paths.clear();
            }
        }
        rebuildTable(server, now, home);
    }

    // What it advertises: its network, the networks its links reach (their cost its own), its transit policy.
    private RoutingDomain.Lsa lsa(MinecraftServer server, RoutingDomain.RouterId id, NetworkRef home) {
        List<RoutingDomain.Link> links = new ArrayList<>();
        Set<NetworkRef> seen = new HashSet<>();
        for (int i = 1; i < endpointCount(); i++) {
            NetworkRef network = network(server, i);
            if (network != null && !network.equals(home) && seen.add(network)) {
                links.add(new RoutingDomain.Link(network, linkCost(), rate(), networkName(server, network), null, false));
            }
        }
        // Its exchange peerings (up sessions): through the port, with the peer as the hop's other carrier.
        Set<NetworkRef> granted = new LinkedHashSet<>(), refused = new LinkedHashSet<>();
        for (ExchangeSwitchBlockEntity exchange : ExchangeRegistry.of(server, id)) {
            for (ExchangeSwitchBlockEntity.Member peer : exchange.peersOf(id)) {
                NetworkRef peerHome = peer.home();
                if (peerHome == null || peerHome.equals(home)) {
                    continue;
                }
                PeerPolicy policy = peerPolicy(peerHome);
                links.add(new RoutingDomain.Link(peerHome, exchangeCost(), Math.min(rate(), Config.EXCHANGE_PORT_LANES.getAsInt()),
                        "IX:" + exchange.exchangeName(), peer.router(), !policy.routes()));
                switch (policy.transit()) {
                    case YES -> granted.add(peerHome);
                    case NO -> refused.add(peerHome);
                    default -> {
                    }
                }
            }
        }
        // Peers let through on their own: a Router with no transit lets them (alone) through, as a Listed one would.
        RoutingDomain.Transit effective = transit;
        Set<NetworkRef> listed = new LinkedHashSet<>(transitList);
        if (transit == RoutingDomain.Transit.NONE && !granted.isEmpty()) {
            effective = RoutingDomain.Transit.LISTED;
            listed = granted;
        } else {
            listed.addAll(granted);
        }
        return new RoutingDomain.Lsa(id, home, networkName(server, home), deviceName(), List.copyOf(links), effective, Set.copyOf(listed),
                Set.copyOf(refused), Map.copyOf(costOverrides), rate(), 0);
    }

    // Leaves dynamic routing (static mode, offline, taken out): its neighbours see it go, its paths are dropped.
    private void leave() {
        if (joined != null && joinedAs != null) {
            joined.remove(joinedAs);
        }
        joined = null;
        joinedAs = null;
        view = null;
        viewVersion = -1;
        changeSeen = -1;
        paths.clear();
        neighbourTable = List.of();
    }

    // The best paths from a network over its database (none in static mode).
    private Map<NetworkRef, RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId>> pathsFrom(NetworkRef source) {
        RoutingDomain.View current = view;
        if (current == null) {
            return Map.of();
        }
        return paths.computeIfAbsent(source, key -> current.graph().paths(key, current::crossable, Config.ROUTING_MAX_HOPS.getAsInt()));
    }

    // A remote route's path from its source, or null when it has none (static mode, unreachable, no transit).
    public RoutingGraph.@Nullable Path<NetworkRef, RoutingDomain.RouterId> path(MinecraftServer server, ItemRouting.Route route) {
        NetworkRef source = network(server, route.source());
        if (!route.remote() || route.target() == null || source == null || mode != Mode.DYNAMIC) {
            return null;
        }
        return pathsFrom(source).get(route.target());
    }

    // The best path from its network to another (RTVRTE), or null.
    public @Nullable TableEntry routeTo(NetworkRef network) {
        for (TableEntry entry : table) {
            if (entry.dest().equals(network)) {
                return entry;
            }
        }
        return null;
    }

    // Its route table: its links (*STATIC, always first), then what it learned (*CONNECTED one hop away, *DYNAMIC
    // beyond). A changed best path fires *RTECHG (dynamic mode).
    private void rebuildTable(MinecraftServer server, long now, @Nullable NetworkRef home) {
        Map<NetworkRef, TableEntry> entries = new LinkedHashMap<>();
        for (int i = 1; i < endpointCount(); i++) {
            NetworkRef network = network(server, i);
            if (network != null && !network.equals(home) && !entries.containsKey(network)) {
                entries.put(network, entry(network, networkName(server, network), "direct", linkCost(), 1, Source.STATIC, now, network));
            }
        }
        RoutingDomain.View current = view;
        if (home != null && current != null) {
            for (RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId> path : pathsFrom(home).values()) {
                if (entries.containsKey(path.dest())) {
                    continue;
                }
                RoutingGraph.Hop<NetworkRef, RoutingDomain.RouterId> first = path.first();
                String next = !first.label().isEmpty() ? first.label() : path.hopCount() == 1 ? "direct" : current.name(first.to());
                String name = current.name(path.dest());
                entries.put(path.dest(), entry(path.dest(), name.isEmpty() ? networkName(server, path.dest()) : name, next, path.cost(), path.hopCount(),
                        path.hopCount() == 1 ? Source.CONNECTED : Source.DYNAMIC, now, first.to()));
            }
        }
        if (mode == Mode.DYNAMIC && tableKnown && home != null) {
            Set<NetworkRef> all = new HashSet<>(entries.keySet());
            all.addAll(lastBest.keySet());
            for (NetworkRef network : all) {
                TableEntry was = lastBest.get(network), is = entries.get(network);
                if (was == null || is == null || !was.next().equals(is.next()) || was.cost() != is.cost()) {
                    lastChanged = (is != null ? is : was).name();
                    lastChangedAt = now;
                    ElclEvents.routeChanged(server, home, deviceName(), lastChanged);
                }
            }
        }
        lastBest.clear();
        lastBest.putAll(entries);
        tableKnown = true;
        table = List.copyOf(entries.values());
        neighbourTable = neighbours(home);
    }

    // An entry, keeping how old it is while its next hop and cost stay the same.
    private TableEntry entry(NetworkRef dest, String name, String next, int cost, int hops, Source source, long now, @Nullable NetworkRef firstHop) {
        TableEntry was = lastBest.get(dest);
        long since = was != null && was.next().equals(next) && was.cost() == cost && was.source() == source ? was.since() : now;
        return new TableEntry(dest, name, next, cost, hops, source, since, firstHop);
    }

    // How many networks its route table reaches through a peer (the peer's own included): the routes exchanged.
    public int routesVia(NetworkRef peer) {
        int count = 0;
        for (TableEntry entry : table) {
            if (peer.equals(entry.firstHop()) && entry.source() != Source.STATIC) {
                count++;
            }
        }
        return count;
    }

    // --- Exchange peers ---

    public PeerPolicy peerPolicy(NetworkRef peer) {
        return peerPolicies.getOrDefault(peer, PeerPolicy.DEFAULT);
    }

    public void setPeerPolicy(NetworkRef peer, PeerPolicy policy) {
        if (policy.equals(PeerPolicy.DEFAULT)) {
            peerPolicies.remove(peer);
        } else {
            peerPolicies.put(peer, policy);
        }
        changed(false);
    }

    // A hop through an exchange port: its lanes against this Router's throughput.
    private int exchangeCost() {
        return RoutingGraph.cost(Config.ROUTING_BASE_COST.getAsInt(), Config.EXCHANGE_PORT_LANES.getAsInt(), rate());
    }

    // The Cross-Connect: used on its unit, it records this Router (then used on an Exchange Switch, it joins it there).
    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, boolean atUnit) {
        if (!atUnit || !stack.is(ModItems.CROSS_CONNECT.get()) || rack() == null) {
            return false;
        }
        if (!NetworkAccess.check(player.level().getServer(), rack().network(this), player, RackPermission.BUILD)) {
            return true;
        }
        RoutingDomain.RouterId id = routerId();
        if (id != null) {
            CrossConnectItem.record(stack, id, deviceName());
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.cross_connect.recorded", deviceName()));
        }
        return true;
    }

    private List<NeighbourEntry> neighbours(@Nullable NetworkRef home) {
        if (joined == null || joinedAs == null) {
            return List.of();
        }
        List<NeighbourEntry> list = new ArrayList<>();
        for (RoutingDomain.Neighbour neighbour : joined.neighbours(joinedAs)) {
            int override = costOverrides.getOrDefault(neighbour.home(), -1);
            int cost = override >= 0 ? override : neighbourCost(home, neighbour.home());
            list.add(new NeighbourEntry(neighbour.home(), neighbour.name(), neighbour.via(), neighbour.state(), cost, override, neighbour.since()));
        }
        return list;
    }

    // The cheapest hop from its network to a neighbour's, as its database has it.
    private int neighbourCost(@Nullable NetworkRef home, NetworkRef neighbour) {
        int best = Integer.MAX_VALUE;
        if (home != null && view != null) {
            for (RoutingGraph.Hop<NetworkRef, RoutingDomain.RouterId> hop : view.graph().hopsFrom(home)) {
                if (hop.to().equals(neighbour)) {
                    best = Math.min(best, hop.cost());
                }
            }
        }
        return best == Integer.MAX_VALUE ? linkCost() : best;
    }

    public int upNeighbours() {
        int up = 0;
        for (NeighbourEntry neighbour : neighbourTable) {
            if (neighbour.state() == RoutingDomain.State.FULL) {
                up++;
            }
        }
        return up;
    }

    // A neighbour by its network's name (RTVNBRSTS), or null.
    public @Nullable NeighbourEntry neighbour(String name) {
        for (NeighbourEntry entry : neighbourTable) {
            if (entry.name().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    // A route's destination network's name: its endpoint's, or a remote network's as the route table knows it.
    public Component destName(ItemRouting.Route route) {
        if (!route.remote()) {
            return endpointName(route.dest());
        }
        for (TableEntry entry : table) {
            if (entry.dest().equals(route.target())) {
                return Component.literal(entry.name());
            }
        }
        return Component.translatable("gui.encodedlogistics.router.remote");
    }

    // Where a route's destination goes next: its endpoints, then (dynamic) every other network in its route table.
    private ItemRouting.Route nextDest(ItemRouting.Route route, MinecraftServer server) {
        int n = endpointCount();
        List<NetworkRef> remotes = new ArrayList<>();
        if (mode == Mode.DYNAMIC) {
            Set<NetworkRef> endpoints = new HashSet<>();
            for (int i = 0; i < n; i++) {
                NetworkRef network = network(server, i);
                if (network != null) {
                    endpoints.add(network);
                }
            }
            for (TableEntry entry : table) {
                if (!endpoints.contains(entry.dest())) {
                    remotes.add(entry.dest());
                }
            }
        }
        int at = route.remote() ? n + Math.max(0, remotes.indexOf(route.target())) : route.dest();
        int next = (at + 1) % (n + remotes.size());
        return next < n ? route.withDest(next) : route.toRemote(remotes.get(next - n));
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return List.of(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.mode"),
                        Component.translatable("gui.encodedlogistics.router.mode." + mode.name().toLowerCase(Locale.ROOT))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.networks"),
                        Component.literal(Integer.toString(networks.size()))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.neighbours"),
                        Component.translatable("gui.encodedlogistics.router.neighbours_up", upNeighbours(), neighbourTable.size())),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.table"), Component.literal(Integer.toString(table.size()))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.routes"), ItemRouting.split(activeRoutes, activeShares)),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.router.throughput"),
                        Component.translatable("gui.encodedlogistics.router.throughput", String.format(Locale.ROOT, "%.1f", throughput())),
                        new RackDeviceInfo.Bar((float) Math.min(1, throughput() / Math.max(1, rate())), RackDeviceInfo.BarStyle.NORMAL)));
    }

    // --- Sharing ---

    @Override
    public List<ItemRouting.ShareLink> shareLinks(MinecraftServer server) {
        if (rack() == null || !isOnline()) {
            return List.of();
        }
        NetworkRef home = network(server, 0);
        List<ItemRouting.ShareLink> links = new ArrayList<>();
        for (ItemRouting.ShareLink link : ItemRouting.shareLinks(routes, index -> network(server, index), this::endpointName)) {
            links.add(new ItemRouting.ShareLink(link.from(), link.into(), link.route(), link.fromName(),
                    shown(link.route(), localChecks(link.route(), home, link.from(), link.into()), server)));
        }
        // Shares with a remote network, while there's a path (each way of a bidirectional one needs its own).
        for (ItemRouting.Route route : routes) {
            if (!route.remote() || !route.shares() || route.idle() || path(server, route) == null) {
                continue;
            }
            NetworkRef source = Objects.requireNonNull(network(server, route.source())), target = Objects.requireNonNull(route.target());
            RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId> there = Objects.requireNonNull(path(server, route));
            links.add(new ItemRouting.ShareLink(source, target, route, endpointName(route.source()), shown(route, pathChecks(server, route, source, there), server)));
            RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId> back = pathsFrom(target).get(source);
            if (route.bidirectional() && back != null) {
                links.add(new ItemRouting.ShareLink(target, source, route, destName(route), shown(route, pathChecks(server, route, target, back), server)));
            }
        }
        return links;
    }

    // --- ACLs ---

    public Map<String, RouterAcl> acls() {
        return acls;
    }

    public @Nullable RouterAcl acl(String name) {
        return acls.get(name.toUpperCase(Locale.ROOT));
    }

    // The ACL applied to a network it reaches directly (a link, a neighbour, an exchange peer), or "".
    public Map<NetworkRef, String> networkAcls() {
        return networkAcls;
    }

    // A new, empty ACL; false when it has as many as it can hold, or one by that name.
    public boolean createAcl(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (acls.size() >= RouterAcl.MAX_ACLS || acls.containsKey(upper) || !RouterAcl.validName(upper)) {
            return false;
        }
        acls.put(upper, new RouterAcl(upper));
        changed(false);
        return true;
    }

    // Drops an ACL and every use of it.
    public boolean deleteAcl(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (acls.remove(upper) == null) {
            return false;
        }
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).acl().equals(upper)) {
                routes.set(i, routes.get(i).withAcl(""));
            }
        }
        networkAcls.values().removeIf(upper::equals);
        changed(false);
        return true;
    }

    // An ACL's rules changed (ELCL, the panel).
    public void aclChanged() {
        changed(false);
    }

    // What an ACL can be applied to, in the panel's order: its routes, then the networks it reaches directly (one hop).
    public List<NetworkRef> directNetworks() {
        List<NetworkRef> direct = new ArrayList<>();
        for (TableEntry entry : table) {
            if (entry.hops() == 1) {
                direct.add(entry.dest());
            }
        }
        return direct;
    }

    // Applies an ACL to target (a route, then a direct network: directNetworks) or takes it off; false when there's no
    // such target or ACL.
    public boolean toggleAcl(int target, String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!acls.containsKey(upper) || target < 0) {
            return false;
        }
        if (target < routes.size()) {
            ItemRouting.Route route = routes.get(target);
            routes.set(target, route.withAcl(route.acl().equals(upper) ? "" : upper));
        } else {
            List<NetworkRef> direct = directNetworks();
            if (target - routes.size() >= direct.size()) {
                return false;
            }
            NetworkRef network = direct.get(target - routes.size());
            if (upper.equals(networkAcls.get(network))) {
                networkAcls.remove(network);
            } else {
                networkAcls.put(network, upper);
            }
        }
        changed(false);
        return true;
    }

    private static boolean permitByDefault() {
        return Config.ACL_DEFAULT_PERMIT.getAsBoolean();
    }

    // An ACL a Router applies to some traffic, one way.
    private record Check(RouterDevice router, RouterAcl acl, RouterAcl.Dir way) {}

    private void check(List<Check> checks, String name, RouterAcl.Dir way) {
        RouterAcl acl = name.isEmpty() ? null : acls.get(name);
        if (acl != null) {
            checks.add(new Check(this, acl, way));
        }
    }

    // Which way traffic goes for this Router: out of its own network, into it, or neither (both apply).
    private static RouterAcl.Dir way(@Nullable NetworkRef home, @Nullable NetworkRef from, @Nullable NetworkRef to) {
        return home != null && home.equals(from) ? RouterAcl.Dir.OUT : home != null && home.equals(to) ? RouterAcl.Dir.IN : RouterAcl.Dir.BOTH;
    }

    // The ACLs on a route from one of its own networks to another: the route's, and the networks' at either end.
    private List<Check> localChecks(ItemRouting.Route route, @Nullable NetworkRef home, NetworkRef from, NetworkRef to) {
        List<Check> checks = new ArrayList<>();
        check(checks, route.acl(), way(home, from, to));
        if (!from.equals(home)) {
            check(checks, networkAcls.getOrDefault(from, ""), RouterAcl.Dir.IN);
        }
        if (!to.equals(home)) {
            check(checks, networkAcls.getOrDefault(to, ""), RouterAcl.Dir.OUT);
        }
        return checks;
    }

    // The ACLs on a remote route: the route's, then every Router on its path's on the networks either side of its hop.
    private List<Check> pathChecks(MinecraftServer server, ItemRouting.Route route, NetworkRef from, RoutingGraph.Path<NetworkRef, RoutingDomain.RouterId> path) {
        NetworkRef home = network(server, 0);
        List<Check> checks = new ArrayList<>();
        check(checks, route.acl(), way(home, from, route.target()));
        for (RoutingGraph.Hop<NetworkRef, RoutingDomain.RouterId> hop : path.hops()) {
            for (RoutingDomain.RouterId carrier : hop.carriers()) {
                RouterDevice router = at(server, carrier);
                NetworkRef theirs = router != null ? router.network(server, 0) : null;
                if (theirs == null) {
                    continue;
                }
                if (theirs.equals(hop.from())) {
                    router.check(checks, router.networkAcls.getOrDefault(hop.to(), ""), RouterAcl.Dir.OUT);
                } else if (theirs.equals(hop.to())) {
                    router.check(checks, router.networkAcls.getOrDefault(hop.from(), ""), RouterAcl.Dir.IN);
                }
            }
        }
        return checks;
    }

    // The Router a domain id names, if it's loaded.
    public static @Nullable RouterDevice at(MinecraftServer server, RoutingDomain.RouterId id) {
        ServerLevel level = server.getLevel(id.dimension());
        if (level == null || !level.isLoaded(id.rack())) {
            return null;
        }
        return level.getBlockEntity(id.rack()) instanceof RackBlockEntity rack && rack.deviceAt(id.u()) instanceof RouterDevice router ? router : null;
    }

    // A move's gate over the checks: as much as every ACL lets through; denials fire *ACLDENY (once a second a rule).
    private ItemRouting.@Nullable Gate gate(MinecraftServer server, List<Check> checks, long now) {
        if (checks.isEmpty()) {
            return null;
        }
        boolean byDefault = permitByDefault();
        return new ItemRouting.Gate() {
            @Override
            public long allow(StorageKey key, long amount) {
                long allowed = amount;
                for (Check check : checks) {
                    RouterAcl.Verdict verdict = check.acl().verdict(key, check.way(), byDefault);
                    allowed = Math.min(allowed, check.acl().allow(verdict, amount, now));
                    if (!verdict.permit()) {
                        check.acl().count(verdict, amount, now);
                        check.router().denied(server, check.acl(), verdict, key, now);
                    }
                    if (allowed <= 0) {
                        return 0;
                    }
                }
                return allowed;
            }

            @Override
            public void moved(StorageKey key, long amount) {
                for (Check check : checks) {
                    check.acl().count(check.acl().verdict(key, check.way(), byDefault), amount, now);
                }
            }
        };
    }

    // What a Share shows through the checks: what its filter passes and every ACL permits.
    private static Predicate<StorageKey> shown(ItemRouting.Route route, List<Check> checks, MinecraftServer server) {
        if (checks.isEmpty()) {
            return route::matches;
        }
        boolean byDefault = permitByDefault();
        return key -> {
            if (!route.matches(key)) {
                return false;
            }
            long now = server.overworld().getGameTime();
            for (Check check : checks) {
                if (!check.acl().permits(key, check.way(), byDefault, now)) {
                    return false;
                }
            }
            return true;
        };
    }

    // *ACLDENY, at most once a second for each rule.
    private void denied(MinecraftServer server, RouterAcl acl, RouterAcl.Verdict verdict, StorageKey key, long now) {
        Object rule = verdict.rule() != null ? verdict.rule() : acl;
        Long last = lastDenied.get(rule);
        if (last != null && now - last < 20) {
            return;
        }
        lastDenied.put(rule, now);
        NetworkRef home = rack() != null ? rack().network(this) : null;
        if (home != null) {
            ElclEvents.aclDenied(server, home, deviceName(), acl.name(), verdict.rule() != null ? verdict.rule().seq() : 0, key);
        }
    }

    // Panel ACL actions (BUILD checked by the caller): text is "ACL" or "ACL rule-spec".
    private boolean aclAction(int action, int value, String text) {
        int space = text.indexOf(' ');
        String name = (space < 0 ? text : text.substring(0, space)).trim().toUpperCase(Locale.ROOT), spec = space < 0 ? "" : text.substring(space + 1);
        if (action == ACTION_ACL_NEW) {
            for (int n = 1; n <= RouterAcl.MAX_ACLS * 2; n++) {
                String candidate = String.format(Locale.ROOT, "ACL%02d", n);
                if (!acls.containsKey(candidate)) {
                    return createAcl(candidate);
                }
            }
            return false;
        }
        if (action == ACTION_ACL_DELETE) {
            return deleteAcl(name);
        }
        if (action == ACTION_ACL_BIND) {
            return toggleAcl(value, name);
        }
        RouterAcl acl = acl(name);
        if (acl == null) {
            return false;
        }
        switch (action) {
            case ACTION_ACL_ADD, ACTION_ACL_EDIT -> {
                RouterAcl.Spec parsed = RouterAcl.parse(spec.isBlank() ? "*ANY" : spec);
                if (parsed == null) {
                    return false;
                }
                if (action == ACTION_ACL_ADD) {
                    // After rule value (its seq), or at the end.
                    RouterAcl.Rule after = acl.rule(value);
                    int seq = -1;
                    if (after != null) {
                        int index = acl.rules().indexOf(after);
                        int next = index + 1 < acl.rules().size() ? acl.rules().get(index + 1).seq() : after.seq() + 2 * RouterAcl.SEQ_STEP;
                        seq = next - after.seq() > 1 ? after.seq() + (next - after.seq()) / 2 : -1;
                    }
                    return acl.add(seq, parsed.match(), parsed.dir(), parsed.permit(), parsed.rate(), parsed.quota());
                }
                RouterAcl.Rule rule = acl.rule(value);
                if (rule == null) {
                    return false;
                }
                rule.set(parsed.match(), parsed.dir(), parsed.permit(), parsed.rate(), parsed.quota());
                return true;
            }
            case ACTION_ACL_DEL -> {
                return acl.remove(value);
            }
            case ACTION_ACL_UP, ACTION_ACL_DOWN -> {
                return acl.move(value, action == ACTION_ACL_UP);
            }
            default -> {
                return false;
            }
        }
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
                routes.set(value, action == ACTION_CYCLE_SOURCE ? route.withSource((route.source() + 1) % n) : nextDest(route, player.level().getServer()));
            }
            case ACTION_MODE, ACTION_TRANSIT, ACTION_TRANSIT_NETWORK, ACTION_NEIGHBOUR_COST -> {
                if (!routingAction(player, action, value, text)) {
                    return;
                }
            }
            case ACTION_ACL_NEW, ACTION_ACL_DELETE, ACTION_ACL_BIND, ACTION_ACL_ADD, ACTION_ACL_EDIT, ACTION_ACL_DEL, ACTION_ACL_UP, ACTION_ACL_DOWN -> {
                if (rack() == null || !NetworkAccess.check(player.level().getServer(), rack().network(this), player, RackPermission.BUILD)
                        || !aclAction(action, value, text)) {
                    return;
                }
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

    // Routing settings need BUILD on its network.
    private boolean routingAction(ServerPlayer player, int action, int value, String text) {
        MinecraftServer server = player.level().getServer();
        if (rack() == null || !NetworkAccess.check(server, rack().network(this), player, RackPermission.BUILD)) {
            return false;
        }
        switch (action) {
            case ACTION_MODE -> setMode(mode == Mode.STATIC ? Mode.DYNAMIC : Mode.STATIC);
            case ACTION_TRANSIT -> setTransit(RoutingDomain.Transit.values()[(transit.ordinal() + 1) % RoutingDomain.Transit.values().length]);
            case ACTION_TRANSIT_NETWORK -> {
                if (value < 0 || value >= table.size()) {
                    return false;
                }
                NetworkRef network = table.get(value).dest();
                if (!transitList.remove(network)) {
                    if (transitList.size() >= MAX_TRANSIT) {
                        return false;
                    }
                    transitList.add(network);
                }
            }
            default -> {
                if (value < 0 || value >= neighbourTable.size()) {
                    return false;
                }
                NeighbourEntry neighbour = neighbourTable.get(value);
                int current = neighbour.override() >= 0 ? neighbour.override() : neighbour.cost();
                switch (text) {
                    case "+" -> costOverrides.put(neighbour.home(), Math.min(1_000_000, current + COST_STEP));
                    case "-" -> costOverrides.put(neighbour.home(), Math.max(1, current - COST_STEP));
                    default -> costOverrides.remove(neighbour.home());
                }
            }
        }
        return true;
    }

    // A neighbour's cost override (CHGNBR): a cost, or -1 to clear it. False when it isn't a neighbour.
    public boolean overrideCost(NetworkRef neighbour, int cost) {
        boolean known = neighbourTable.stream().anyMatch(entry -> entry.home().equals(neighbour));
        if (!known) {
            return false;
        }
        if (cost < 0) {
            costOverrides.remove(neighbour);
        } else {
            costOverrides.put(neighbour, Math.max(1, cost));
        }
        changed(false);
        return true;
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
        output.putInt("paused", paused);
        output.putInt("reconverge", Config.ROUTING_RECONVERGE_SECONDS.getAsInt());
        output.putInt("max_hops", Config.ROUTING_MAX_HOPS.getAsInt());
        output.putBoolean("acl_default", permitByDefault());
        long now = viewer.level().getServer().overworld().getGameTime();
        ValueOutput.ValueOutputList rows = output.childrenList("table");
        for (TableEntry entry : table) {
            ValueOutput row = rows.addChild();
            row.putString("name", entry.name());
            row.putString("next", entry.next());
            row.putInt("cost", entry.cost());
            row.putInt("hops", entry.hops());
            row.putString("source", entry.source().name());
            row.putBoolean("transit", transitList.contains(entry.dest()));
            row.putLong("age", (now - entry.since()) / 20);
            row.store("network", NetworkRef.CODEC, entry.dest());
        }
        ValueOutput.ValueOutputList nbrs = output.childrenList("neighbour_rows");
        for (NeighbourEntry entry : neighbourTable) {
            ValueOutput row = nbrs.addChild();
            row.putString("name", entry.name());
            row.putString("via", entry.via());
            row.putString("state", entry.state().name());
            row.putInt("cost", entry.cost());
            row.putInt("override", entry.override());
        }
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
        if (mode != Mode.STATIC) {
            output.putString("routing", mode.name().toLowerCase(Locale.ROOT));
        }
        if (transit != RoutingDomain.Transit.NONE) {
            output.putString("transit", transit.name().toLowerCase(Locale.ROOT));
        }
        if (!transitList.isEmpty()) {
            output.store("transit_list", NetworkRef.CODEC.listOf(), List.copyOf(transitList));
        }
        ValueOutput.ValueOutputList aclList = output.childrenList("acls");
        for (RouterAcl acl : acls.values()) {
            acl.save(aclList.addChild());
        }
        ValueOutput.ValueOutputList bound = output.childrenList("network_acls");
        for (Map.Entry<NetworkRef, String> entry : networkAcls.entrySet()) {
            ValueOutput child = bound.addChild();
            child.store("network", NetworkRef.CODEC, entry.getKey());
            child.putString("acl", entry.getValue());
        }
        ValueOutput.ValueOutputList policies = output.childrenList("peer_policies");
        for (Map.Entry<NetworkRef, PeerPolicy> entry : peerPolicies.entrySet()) {
            ValueOutput child = policies.addChild();
            child.store("network", NetworkRef.CODEC, entry.getKey());
            child.putBoolean("routes", entry.getValue().routes());
            child.putString("transit", entry.getValue().transit().name());
        }
        ValueOutput.ValueOutputList overrides = output.childrenList("cost_overrides");
        for (Map.Entry<NetworkRef, Integer> entry : costOverrides.entrySet()) {
            ValueOutput child = overrides.addChild();
            child.store("network", NetworkRef.CODEC, entry.getKey());
            child.putInt("cost", entry.getValue());
        }
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
        // Routers saved before dynamic routing load as STATIC, their routes as they were.
        mode = input.getStringOr("routing", "static").equals("dynamic") ? Mode.DYNAMIC : Mode.STATIC;
        transit = switch (input.getStringOr("transit", "none")) {
            case "listed" -> RoutingDomain.Transit.LISTED;
            case "all" -> RoutingDomain.Transit.ALL;
            default -> RoutingDomain.Transit.NONE;
        };
        acls.clear();
        for (ValueInput child : input.childrenListOrEmpty("acls")) {
            RouterAcl acl = RouterAcl.load(child);
            if (acls.size() < RouterAcl.MAX_ACLS) {
                acls.put(acl.name(), acl);
            }
        }
        peerPolicies.clear();
        for (ValueInput child : input.childrenListOrEmpty("peer_policies")) {
            PeerTransit peerTransit;
            try {
                peerTransit = PeerTransit.valueOf(child.getStringOr("transit", "DEFAULT"));
            } catch (IllegalArgumentException e) {
                peerTransit = PeerTransit.DEFAULT;
            }
            PeerPolicy policy = new PeerPolicy(child.getBooleanOr("routes", true), peerTransit);
            child.read("network", NetworkRef.CODEC).ifPresent(network -> peerPolicies.put(network, policy));
        }
        networkAcls.clear();
        for (ValueInput child : input.childrenListOrEmpty("network_acls")) {
            child.read("network", NetworkRef.CODEC).ifPresent(network -> networkAcls.put(network, child.getStringOr("acl", "")));
        }
        transitList.clear();
        input.read("transit_list", NetworkRef.CODEC.listOf()).ifPresent(transitList::addAll);
        costOverrides.clear();
        for (ValueInput child : input.childrenListOrEmpty("cost_overrides")) {
            child.read("network", NetworkRef.CODEC).ifPresent(network -> costOverrides.put(network, Math.max(1, child.getIntOr("cost", 1))));
        }
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
