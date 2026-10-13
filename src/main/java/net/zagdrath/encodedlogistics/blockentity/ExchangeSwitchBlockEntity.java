/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.ExchangeSwitchBlock;
import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;
import net.zagdrath.encodedlogistics.menu.PanelHost;
import net.zagdrath.encodedlogistics.midrange.MidrangeHud;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.routing.ExchangeRegistry;
import net.zagdrath.encodedlogistics.network.routing.RoutingDomain;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// The Exchange Switch's state (the routing handoff 2): an Internet Exchange where Routers on any networks meet.
//
// Its placer owns it. A Router joins with a Cross-Connect (used on the Router, then here): at once while membership is
// OPEN, as a request the owner approves (APPROVAL); each member takes a port (exchangePorts at most). The owner can
// remove members, but never touches their Routers (each side's own Firewall governs its Router).
//
// Peering: with the route server on, members who opt in (the default) peer with every other such member; any two
// members can also peer BILATERALLY, once each accepts the other. A session is up while the exchange is online and both
// Routers are up (online, in Dynamic mode). Each member's own side sets, per peer, whether it exchanges routes and
// allows transit (RouterDevice.PeerPolicy), and can apply an ACL to the peer (the Router's ACLs tab).
//
// While it has members, it keeps its chunk loaded (exchangeChunkLoading).
public class ExchangeSwitchBlockEntity extends BlockEntity implements NetworkDevice, NamedDevice, MidrangeHud, PanelHost {
    public static final int MAX_NAME = 8;
    public static final int ACTION_APPROVE = 0, ACTION_REMOVE = 1, ACTION_MEMBERSHIP = 2, ACTION_ROUTE_SERVER = 3, ACTION_PORTS = 4, ACTION_RENAME = 5,
            ACTION_OPT_IN = 6, ACTION_ACCEPT = 7, ACTION_PEER_ROUTES = 8, ACTION_PEER_TRANSIT = 9;

    public enum Membership {
        OPEN, APPROVAL
    }

    public enum JoinResult {
        JOINED, REQUESTED, ALREADY, FULL
    }

    // A member (or, with no port, a request): its Router, the network it's on (and its name), who joined it, its port,
    // whether it peers through the route server, and the members it accepts bilateral peering with.
    public static final class Member {
        private final RoutingDomain.RouterId router;
        private @Nullable NetworkRef home;
        private String network;
        private final UUID owner;
        private final String ownerName;
        private int port;
        private boolean routeServer = true;
        private final Set<RoutingDomain.RouterId> accepts = new LinkedHashSet<>();
        // Whether its Router is up (online, Dynamic), as last looked at.
        private boolean up;

        Member(RoutingDomain.RouterId router, @Nullable NetworkRef home, String network, UUID owner, String ownerName, int port) {
            this.router = router;
            this.home = home;
            this.network = network;
            this.owner = owner;
            this.ownerName = ownerName;
            this.port = port;
        }

        public RoutingDomain.RouterId router() {
            return router;
        }

        public @Nullable NetworkRef home() {
            return home;
        }

        public String network() {
            return network;
        }

        public String ownerName() {
            return ownerName;
        }

        public int port() {
            return port;
        }

        public boolean request() {
            return port < 0;
        }

        public boolean routeServer() {
            return routeServer;
        }

        public boolean up() {
            return up;
        }

        public boolean accepts(Member other) {
            return accepts.contains(other.router);
        }
    }

    // A peering session between two members, through the route server or bilateral; up while both are.
    public record Session(Member a, Member b, boolean viaRouteServer, boolean up) {
        public Member other(Member one) {
            return one == a ? b : a;
        }
    }

    private String name = "";
    private @Nullable UUID owner;
    private String ownerName = "";
    private Membership membership = Membership.APPROVAL;
    private boolean routeServer = true;
    private int ports = -1;
    private final List<Member> members = new ArrayList<>();
    private boolean online, forced, registered;
    private String deviceName = "";
    // Items crossed between two members this minute and last (by the pair).
    private final Map<Set<RoutingDomain.RouterId>, long[]> traffic = new HashMap<>();

    public ExchangeSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.EXCHANGE_SWITCH.get(), pos, state);
    }

    // --- Who and what ---

    // Its name (IX:name in route tables): set by renaming it (an anvil, its Setup tab), else its device name.
    public String exchangeName() {
        return !name.isEmpty() ? name : !deviceName.isEmpty() ? deviceName : "IX";
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
        setChanged();
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean isOwner(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    // A batch job's submitter (no player at hand): only the owner.
    public boolean isOwner(@Nullable UUID player) {
        return owner == null || owner.equals(player);
    }

    public Membership membership() {
        return membership;
    }

    public boolean routeServer() {
        return routeServer;
    }

    public void setMembership(Membership membership) {
        this.membership = membership;
        changedAll();
    }

    public int ports() {
        int max = Config.EXCHANGE_PORTS.getAsInt();
        return ports < 1 ? max : Math.min(ports, max);
    }

    public List<Member> members() {
        return members;
    }

    // Approved members (with a port).
    public List<Member> approved() {
        return members.stream().filter(member -> !member.request()).toList();
    }

    public @Nullable Member member(RoutingDomain.RouterId router) {
        for (Member member : members) {
            if (member.router.equals(router) && !member.request()) {
                return member;
            }
        }
        return null;
    }

    public @Nullable Member memberOn(String network) {
        for (Member member : members) {
            if (member.network.equalsIgnoreCase(network)) {
                return member;
            }
        }
        return null;
    }

    public boolean online() {
        return online;
    }

    // --- Joining ---

    // A Router joins (OPEN) or asks to (APPROVAL); the Router's network's BUILD permission is the caller's to check.
    public JoinResult join(RouterDevice router, MinecraftServer server, Player player) {
        RoutingDomain.RouterId id = router.routerId();
        if (id == null) {
            return JoinResult.FULL;
        }
        for (Member member : members) {
            if (member.router.equals(id)) {
                return JoinResult.ALREADY;
            }
        }
        if (approved().size() >= ports() && membership == Membership.OPEN || members.size() >= ports() * 2) {
            return JoinResult.FULL;
        }
        NetworkRef home = router.network(server, 0);
        String network = home != null ? RouterDevice.networkName(server, home) : router.deviceName();
        Member member = new Member(id, home, network, player.getUUID(), player.getGameProfile().name(), -1);
        members.add(member);
        if (membership == Membership.OPEN) {
            member.port = freePort();
        }
        changedAll();
        return member.request() ? JoinResult.REQUESTED : JoinResult.JOINED;
    }

    private int freePort() {
        Set<Integer> taken = new java.util.HashSet<>();
        for (Member member : members) {
            taken.add(member.port);
        }
        for (int port = 1; port <= ports(); port++) {
            if (!taken.contains(port)) {
                return port;
            }
        }
        return -1;
    }

    public boolean approve(int index) {
        if (index < 0 || index >= members.size() || !members.get(index).request()) {
            return false;
        }
        int port = freePort();
        if (port < 0) {
            return false;
        }
        members.get(index).port = port;
        changedAll();
        return true;
    }

    public boolean remove(int index) {
        if (index < 0 || index >= members.size()) {
            return false;
        }
        Member gone = members.remove(index);
        for (Member member : members) {
            member.accepts.remove(gone.router);
        }
        changedAll();
        return true;
    }

    // --- Sessions ---

    public List<Session> sessions() {
        List<Session> sessions = new ArrayList<>();
        List<Member> approved = approved();
        for (int i = 0; i < approved.size(); i++) {
            for (int j = i + 1; j < approved.size(); j++) {
                Member a = approved.get(i), b = approved.get(j);
                boolean viaServer = routeServer && a.routeServer && b.routeServer;
                if (viaServer || a.accepts(b) && b.accepts(a)) {
                    sessions.add(new Session(a, b, viaServer, online && a.up && b.up));
                }
            }
        }
        return sessions;
    }

    // The members a Router has an up session with.
    public List<Member> peersOf(RoutingDomain.RouterId router) {
        List<Member> peers = new ArrayList<>();
        Member self = member(router);
        if (self == null) {
            return peers;
        }
        for (Session session : sessions()) {
            if (session.up() && (session.a() == self || session.b() == self)) {
                peers.add(session.other(self));
            }
        }
        return peers;
    }

    public long sessionsUp() {
        return sessions().stream().filter(Session::up).count();
    }

    public void traffic(RoutingDomain.RouterId a, RoutingDomain.RouterId b, long amount, long now) {
        if (member(a) == null || member(b) == null) {
            return;
        }
        long[] window = traffic.computeIfAbsent(Set.of(a, b), key -> new long[] {now / 1_200, 0, 0});
        roll(window, now);
        window[1] += amount;
    }

    // Items a minute between two members (the last whole minute).
    public long trafficPerMinute(Member a, Member b, long now) {
        long[] window = traffic.get(Set.of(a.router, b.router));
        if (window == null) {
            return 0;
        }
        roll(window, now);
        return window[2];
    }

    private static void roll(long[] window, long now) {
        long minute = now / 1_200;
        if (minute != window[0]) {
            window[2] = minute == window[0] + 1 ? window[1] : 0;
            window[1] = 0;
            window[0] = minute;
        }
    }

    // --- Ticking ---

    public void serverTick(ServerLevel level) {
        if (!registered) {
            ExchangeRegistry.add(level.getServer(), GlobalPos.of(level.dimension(), worldPosition), this);
            registered = true;
        }
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        boolean changed = false;
        for (Member member : members) {
            RouterDevice router = RouterDevice.at(level.getServer(), member.router);
            boolean up = router != null && router.isOnline() && router.mode() == RouterDevice.Mode.DYNAMIC;
            NetworkRef home = router != null ? router.network(level.getServer(), 0) : null;
            if (home != null && !home.equals(member.home)) {
                member.home = home;
                changed = true;
            }
            if (home != null) {
                String network = RouterDevice.networkName(level.getServer(), home);
                if (!network.equals(member.network)) {
                    member.network = network;
                    changed = true;
                }
            }
            member.up = up;
        }
        keepLoaded(level, Config.EXCHANGE_CHUNK_LOADING.getAsBoolean() && !approved().isEmpty());
        if (changed) {
            setChanged();
        }
    }

    // Keeps its chunk loaded (a forced chunk) while it has members.
    private void keepLoaded(ServerLevel level, boolean keep) {
        if (keep != forced) {
            level.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, keep);
            forced = keep;
            setChanged();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel && registered) {
            ExchangeRegistry.remove(serverLevel.getServer(), GlobalPos.of(serverLevel.dimension(), worldPosition), this);
            registered = false;
        }
    }

    // Broken: its chunk goes back to normal.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            keepLoaded(serverLevel, false);
        }
    }

    private void changedAll() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Network ---

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
        BlockState state = getBlockState();
        if (level != null && state.getBlock() instanceof ExchangeSwitchBlock && state.getValue(ExchangeSwitchBlock.ONLINE) != online) {
            level.setBlock(worldPosition, state.setValue(ExchangeSwitchBlock.ONLINE, online), Block.UPDATE_CLIENTS);
            BlockState upper = level.getBlockState(worldPosition.above());
            if (upper.getBlock() instanceof ExchangeSwitchBlock) {
                level.setBlock(worldPosition.above(), upper.setValue(ExchangeSwitchBlock.ONLINE, online), Block.UPDATE_CLIENTS);
            }
        }
    }

    // --- Its ELCL name and popup ---

    @Override
    public String deviceType() {
        return "XSW";
    }

    @Override
    public String deviceName() {
        return deviceName;
    }

    @Override
    public void setDeviceName(String name) {
        if (!deviceName.equals(name)) {
            deviceName = name;
            setChanged();
        }
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public RackDeviceInfo hudInfo() {
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.exchange.name"), Component.literal(exchangeName())));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.exchange.members"),
                Component.literal(approved().size() + " / " + ports())));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.exchange.sessions"),
                Component.translatable("hud.encodedlogistics.exchange.sessions_up", sessionsUp())));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.exchange.owner"), Component.literal(ownerName)));
        return new RackDeviceInfo(getBlockState().getBlock().getName(), online ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE,
                Component.translatable(online ? "hud.encodedlogistics.status.online" : "hud.encodedlogistics.status.offline"), lines);
    }

    // --- Panel ---

    @Override
    public String panelKind() {
        return "exchange";
    }

    @Override
    public Component panelTitle() {
        return Component.translatable("gui.encodedlogistics.exchange.title", exchangeName());
    }

    // The member a player speaks for: the first whose network they may build on.
    private int mine(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            if (!member.request() && member.home != null && NetworkAccess.allowed(server, member.home, player, RackPermission.BUILD)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        MinecraftServer server = viewer.level().getServer();
        long now = server.overworld().getGameTime();
        output.putString("name", exchangeName());
        output.putString("owner", ownerName);
        output.putBoolean("is_owner", isOwner(viewer));
        output.putString("membership", membership.name());
        output.putBoolean("route_server", routeServer);
        output.putInt("ports", ports());
        output.putInt("max_ports", Config.EXCHANGE_PORTS.getAsInt());
        output.putInt("used", approved().size());
        output.putBoolean("online", online);
        output.putBoolean("chunk", forced);
        int mine = mine(viewer);
        output.putInt("mine", mine);
        ValueOutput.ValueOutputList list = output.childrenList("members");
        for (Member member : members) {
            ValueOutput row = list.addChild();
            row.putString("network", member.network);
            row.putString("owner", member.ownerName);
            row.putInt("port", member.port);
            row.putBoolean("up", member.up);
            row.putBoolean("rs", member.routeServer);
        }
        List<Session> sessions = sessions();
        ValueOutput.ValueOutputList peers = output.childrenList("peers");
        if (mine >= 0) {
            // From the viewer's member: every other member, the session with it, and its own side's policy.
            Member self = members.get(mine);
            RouterDevice router = RouterDevice.at(server, self.router);
            for (int i = 0; i < members.size(); i++) {
                Member other = members.get(i);
                if (other == self || other.request()) {
                    continue;
                }
                Session session = sessions.stream().filter(s -> s.a() == self && s.b() == other || s.a() == other && s.b() == self).findFirst().orElse(null);
                ValueOutput row = peers.addChild();
                row.putInt("index", i);
                row.putString("network", other.network);
                row.putString("state", session == null ? "NONE" : session.up() ? "UP" : "DOWN");
                row.putString("mode", session == null ? "" : session.viaRouteServer() ? "RS" : "BILAT");
                row.putBoolean("we_accept", self.accepts(other));
                row.putBoolean("they_accept", other.accepts(self));
                row.putInt("routes", router != null && other.home != null ? router.routesVia(other.home) : 0);
                row.putLong("traffic", trafficPerMinute(self, other, now));
                RouterDevice.PeerPolicy policy = router != null && other.home != null ? router.peerPolicy(other.home) : RouterDevice.PeerPolicy.DEFAULT;
                row.putBoolean("exchange_routes", policy.routes());
                row.putString("transit", policy.transit().name());
            }
        } else {
            for (Session session : sessions) {
                ValueOutput row = peers.addChild();
                row.putInt("index", -1);
                row.putString("network", session.a().network + "-" + session.b().network);
                row.putString("state", session.up() ? "UP" : "DOWN");
                row.putString("mode", session.viaRouteServer() ? "RS" : "BILAT");
                row.putLong("traffic", trafficPerMinute(session.a(), session.b(), now));
            }
        }
    }

    @Override
    public void handlePanelAction(ServerPlayer player, int action, int value, String text) {
        MinecraftServer server = player.level().getServer();
        switch (action) {
            case ACTION_APPROVE, ACTION_MEMBERSHIP, ACTION_ROUTE_SERVER, ACTION_PORTS, ACTION_RENAME -> {
                if (!isOwner(player)) {
                    player.sendOverlayMessage(Component.translatable("message.encodedlogistics.exchange.not_owner", ownerName));
                    return;
                }
                switch (action) {
                    case ACTION_APPROVE -> approve(value);
                    case ACTION_MEMBERSHIP -> membership = membership == Membership.OPEN ? Membership.APPROVAL : Membership.OPEN;
                    case ACTION_ROUTE_SERVER -> routeServer = !routeServer;
                    case ACTION_PORTS -> {
                        // Never below its members (nor above the config's ports, even if they now are).
                        int max = Config.EXCHANGE_PORTS.getAsInt();
                        ports = Math.clamp(ports() + (value < 0 ? -1 : 1), Math.min(max, Math.max(1, approved().size())), max);
                    }
                    default -> {
                        String upper = text.trim().toUpperCase(Locale.ROOT);
                        if (upper.isEmpty() || upper.matches("[A-Z][A-Z0-9]{0," + (MAX_NAME - 1) + "}")) {
                            name = upper;
                        }
                    }
                }
            }
            case ACTION_REMOVE -> {
                // The owner, or the member's own side.
                if (value < 0 || value >= members.size()) {
                    return;
                }
                Member member = members.get(value);
                if (!isOwner(player) && (member.home == null || !NetworkAccess.check(server, member.home, player, RackPermission.BUILD))) {
                    return;
                }
                remove(value);
            }
            case ACTION_OPT_IN -> {
                int mine = mine(player);
                if (mine < 0) {
                    return;
                }
                members.get(mine).routeServer = !members.get(mine).routeServer;
            }
            case ACTION_ACCEPT, ACTION_PEER_ROUTES, ACTION_PEER_TRANSIT -> {
                int mine = mine(player);
                if (mine < 0 || value < 0 || value >= members.size() || value == mine) {
                    return;
                }
                Member self = members.get(mine), other = members.get(value);
                if (action == ACTION_ACCEPT) {
                    if (!self.accepts.remove(other.router)) {
                        self.accepts.add(other.router);
                    }
                } else {
                    RouterDevice router = RouterDevice.at(server, self.router);
                    if (router == null || other.home == null) {
                        return;
                    }
                    RouterDevice.PeerPolicy policy = router.peerPolicy(other.home);
                    router.setPeerPolicy(other.home, action == ACTION_PEER_ROUTES ? policy.withRoutes(!policy.routes()) : policy.nextTransit());
                }
            }
            default -> {
                return;
            }
        }
        changedAll();
    }

    // --- Saving ---

    private static void saveRouter(ValueOutput output, RoutingDomain.RouterId router) {
        output.store("dimension", Level.RESOURCE_KEY_CODEC, router.dimension());
        output.store("rack", BlockPos.CODEC, router.rack());
        output.putInt("u", router.u());
    }

    private static RoutingDomain.@Nullable RouterId loadRouter(ValueInput input) {
        var dimension = input.read("dimension", Level.RESOURCE_KEY_CODEC).orElse(null);
        BlockPos rack = input.read("rack", BlockPos.CODEC).orElse(null);
        return dimension != null && rack != null ? new RoutingDomain.RouterId(dimension, rack, input.getIntOr("u", 1)) : null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!name.isEmpty()) {
            output.putString("exchange_name", name);
        }
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
        }
        output.putString("owner_name", ownerName);
        output.putString("membership", membership.name());
        output.putBoolean("route_server", routeServer);
        output.putInt("ports", ports);
        output.putBoolean("forced", forced);
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
        ValueOutput.ValueOutputList list = output.childrenList("members");
        for (Member member : members) {
            ValueOutput child = list.addChild();
            saveRouter(child, member.router);
            if (member.home != null) {
                child.store("home", NetworkRef.CODEC, member.home);
            }
            child.putString("network", member.network);
            child.store("owner", UUIDUtil.CODEC, member.owner);
            child.putString("owner_name", member.ownerName);
            child.putInt("port", member.port);
            child.putBoolean("route_server", member.routeServer);
            ValueOutput.ValueOutputList accepts = child.childrenList("accepts");
            for (RoutingDomain.RouterId accepted : member.accepts) {
                saveRouter(accepts.addChild(), accepted);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        name = input.getStringOr("exchange_name", "");
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        membership = input.getStringOr("membership", "APPROVAL").equals("OPEN") ? Membership.OPEN : Membership.APPROVAL;
        routeServer = input.getBooleanOr("route_server", true);
        ports = input.getIntOr("ports", -1);
        forced = input.getBooleanOr("forced", false);
        deviceName = input.getStringOr("device_name", "");
        members.clear();
        for (ValueInput child : input.childrenListOrEmpty("members")) {
            RoutingDomain.RouterId router = loadRouter(child);
            if (router == null) {
                continue;
            }
            Member member = new Member(router, child.read("home", NetworkRef.CODEC).orElse(null), child.getStringOr("network", ""),
                    child.read("owner", UUIDUtil.CODEC).orElse(new UUID(0, 0)), child.getStringOr("owner_name", ""), child.getIntOr("port", -1));
            member.routeServer = child.getBooleanOr("route_server", true);
            for (ValueInput accepted : child.childrenListOrEmpty("accepts")) {
                RoutingDomain.RouterId id = loadRouter(accepted);
                if (id != null) {
                    member.accepts.add(id);
                }
            }
            members.add(member);
        }
    }

    // Placed from a renamed item: that's its name (and its device name comes with the item, as other devices').
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Component custom = components.get(DataComponents.CUSTOM_NAME);
        if (custom != null) {
            String upper = custom.getString().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (!upper.isEmpty() && Character.isLetter(upper.charAt(0))) {
                name = upper.substring(0, Math.min(MAX_NAME, upper.length()));
            }
        }
        String carried = components.get(ModDataComponents.DEVICE_NAME.get());
        if (carried != null) {
            deviceName = carried;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!deviceName.isEmpty()) {
            components.set(ModDataComponents.DEVICE_NAME.get(), deviceName);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("device_name");
    }
}
