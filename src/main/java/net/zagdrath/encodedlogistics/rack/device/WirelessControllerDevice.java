/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.AccessPointBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.item.HandheldLinkState;
import net.zagdrath.encodedlogistics.item.HandheldTerminalItem;
import net.zagdrath.encodedlogistics.menu.HandheldTerminalMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.network.RemoteLink;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.wireless.Wireless;
import net.zagdrath.encodedlogistics.wireless.WirelessClient;
import net.zagdrath.encodedlogistics.wireless.WirelessLink;

// The Wireless Controller (1U): the brain of its network's wireless, with infinite range in its dimension (and others
// with wirelessCrossDimension). A Handheld Terminal used on its unit (front door open) or anywhere on its rack is linked
// to it - and to its network - and from then on works anywhere, through this controller or, with
// wirelessAnyController, any online one on the network. A Link Card used on its unit takes it (LinkAddress.wireless),
// and then links Wireless Bridges, Wireless Ports and Small Wireless Bridges (on machines) to it: its clients. Clients work only while its network has
// Access Points online, each giving wirelessApClients slots; past the slots the most recently linked wait (over
// capacity) until slots free up. Each admitted client is a remote link from its rack (a bridge's wirelessBridgeLanes
// lanes, a port's or Small Wireless Bridge's one), so the network's lanes reach it. With clients and no Access Point online it faults. It keeps
// who and what it has linked (its id and lists go with it when it's taken out); unlinking a client here cuts it off.
public class WirelessControllerDevice extends RackDevice {
    public static final int ACTION_UNLINK = 0, ACTION_UNLINK_CLIENT = 1;
    private static final int CHECK_INTERVAL = 20;

    public record Linked(UUID player, String name, String dimension) {}

    // A linked Wireless Bridge, Port or Small Wireless Bridge, in the order they were linked.
    public record Client(GlobalPos pos, WirelessClient.Kind kind) {}

    private UUID id = UUID.randomUUID();
    private final List<Linked> linked = new ArrayList<>();
    private final List<Client> clients = new ArrayList<>();
    // As last worked out: the online Access Points and the clients they have room for (not saved).
    private int accessPoints;
    private Set<GlobalPos> admitted = Set.of();
    private List<RemoteLink> links = List.of();
    private int timer;

    public WirelessControllerDevice(RackDeviceType type) {
        super(type);
    }

    public UUID id() {
        return id;
    }

    public List<Linked> linked() {
        return linked;
    }

    public List<Client> clients() {
        return clients;
    }

    @Override
    public double drain() {
        return Config.WIRELESS_CONTROLLER_DRAIN.getAsDouble();
    }

    // --- Handheld Terminals ---

    // Whether a player's terminal is linked here.
    public boolean serves(Player player) {
        return linked.stream().anyMatch(entry -> entry.player().equals(player.getUUID()));
    }

    // Links a Handheld Terminal (and its holder) to this controller and its network.
    public boolean link(ServerPlayer player, ItemStack stack) {
        NetworkRef network = network();
        if (network == null || network.id() <= 0) {
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.handheld.no_network"));
            return false;
        }
        stack.set(ModDataComponents.HANDHELD_NETWORK.get(), network);
        stack.set(ModDataComponents.WIRELESS_LINK.get(), id);
        stack.set(ModDataComponents.HANDHELD_LINK_STATE.get(), isOnline() ? HandheldLinkState.LINKED : HandheldLinkState.OUT_OF_RANGE);
        linked.removeIf(entry -> entry.player().equals(player.getUUID()));
        linked.add(new Linked(player.getUUID(), player.getName().getString(), player.level().dimension().identifier().toString()));
        player.sendOverlayMessage(Component.translatable("message.encodedlogistics.wireless.linked", network.id()));
        changed(false);
        return true;
    }

    @Override
    public boolean useItem(ServerPlayer player, ItemStack stack, boolean atUnit) {
        return stack.getItem() instanceof HandheldTerminalItem && link(player, stack);
    }

    // The players with a Handheld Terminal open through this controller right now.
    public int connected(MinecraftServer server) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (connected(player)) {
                count++;
            }
        }
        return count;
    }

    private boolean connected(ServerPlayer player) {
        return player.containerMenu instanceof HandheldTerminalMenu menu && id.equals(menu.wireless());
    }

    // --- Wireless Bridges and Ports ---

    public @Nullable NetworkRef network() {
        return rack() != null ? rack().network(this) : null;
    }

    // Where its rack's master is (a client's remote link points there).
    public @Nullable GlobalPos rackPos() {
        return rack() != null && rack().getLevel() != null ? GlobalPos.of(rack().getLevel().dimension(), rack().getBlockPos()) : null;
    }

    // Links a Wireless Bridge or Port here (from wherever it was linked before); false off a network.
    public boolean link(WirelessClient client) {
        NetworkRef network = network();
        GlobalPos rack = rackPos();
        if (network == null || network.id() <= 0 || rack == null) {
            return false;
        }
        GlobalPos pos = client.self();
        clients.removeIf(entry -> entry.pos().equals(pos));
        clients.add(new Client(pos, client.wirelessKind()));
        client.setLink(new WirelessLink(id, network, rack));
        refresh(true);
        return true;
    }

    // Unlinks the nth client (the panel's X): it's cut off, and told so if it's loaded.
    public void unlink(int index) {
        if (index < 0 || index >= clients.size()) {
            return;
        }
        Client client = clients.remove(index);
        if (rack() != null && rack().getLevel() instanceof ServerLevel level
                && Wireless.clientAt(level.getServer(), NodePos.of(client.pos())) instanceof WirelessClient there
                && there.link() != null && id.equals(there.link().controller())) {
            there.setLink(null);
        }
        refresh(true);
    }

    // A client broken, or linked elsewhere.
    public void forget(GlobalPos pos) {
        if (clients.removeIf(entry -> entry.pos().equals(pos))) {
            refresh(true);
        }
    }

    public boolean lists(GlobalPos pos) {
        return clients.stream().anyMatch(entry -> entry.pos().equals(pos));
    }

    public boolean admits(GlobalPos pos) {
        return admitted.contains(pos);
    }

    public int accessPointsOnline() {
        return accessPoints;
    }

    public int slots() {
        return accessPoints * Config.WIRELESS_AP_CLIENTS.getAsInt();
    }

    public int admittedCount() {
        return admitted.size();
    }

    @Override
    public void tick(ServerLevel level) {
        if (++timer >= CHECK_INTERVAL) {
            timer = 0;
            refresh(false);
        }
    }

    @Override
    protected void onlineChanged() {
        refresh(false);
    }

    // Checks its clients still point here (dropping those that don't), counts the online Access Points and admits the
    // clients they have room for, oldest link first; a change in what it links reaches the network.
    private void refresh(boolean listChanged) {
        if (rack() == null || !(rack().getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        boolean changed = listChanged;
        for (Client client : List.copyOf(clients)) {
            ServerLevel there = server.getLevel(client.pos().dimension());
            if (there == null || !there.isLoaded(client.pos().pos())) {
                continue;
            }
            if (!(Wireless.clientAt(there, client.pos().pos()) instanceof WirelessClient found) || found.link() == null
                    || !id.equals(found.link().controller())) {
                clients.remove(client);
                changed = true;
            }
        }
        NetworkRef network = network();
        int aps = isOnline() ? ControllerStructures.onNetwork(server, network, AccessPointBlockEntity.class, true).size() : 0;
        int room = aps * Config.WIRELESS_AP_CLIENTS.getAsInt();
        Set<GlobalPos> serving = new LinkedHashSet<>();
        for (Client client : clients) {
            if (serving.size() >= room) {
                break;
            }
            if (Config.WIRELESS_CROSS_DIMENSION.getAsBoolean() || client.pos().dimension().equals(level.dimension())) {
                serving.add(client.pos());
            }
        }
        List<RemoteLink> now = new ArrayList<>();
        for (Client client : clients) {
            if (serving.contains(client.pos())) {
                now.add(new RemoteLink(NodePos.of(client.pos()), client.kind() == WirelessClient.Kind.BRIDGE ? Config.WIRELESS_BRIDGE_LANES.getAsInt() : 1, true));
            }
        }
        boolean shown = aps != accessPoints || !serving.equals(admitted);
        accessPoints = aps;
        admitted = serving;
        if (!now.equals(links)) {
            links = List.copyOf(now);
            changed(true);
        } else if (changed || shown) {
            changed(false);
        }
    }

    @Override
    public List<RemoteLink> remoteLinks() {
        return links;
    }

    // --- Status and the popup ---

    // With clients and no Access Point online: "No access points".
    private boolean noAccessPoints() {
        return isOnline() && !clients.isEmpty() && accessPoints <= 0;
    }

    @Override
    public RackDeviceInfo.Status status() {
        return noAccessPoints() ? RackDeviceInfo.Status.FAULT : super.status();
    }

    @Override
    public Component statusText() {
        return noAccessPoints() ? Wireless.Problem.NO_ACCESS_POINTS.text() : super.statusText();
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        int slots = slots();
        Component linkedCount = Component.literal(Integer.toString(clients.size() + linked.size()));
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.aps"), Component.literal(Integer.toString(accessPoints))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.clients"),
                        Component.literal(admitted.size() + " / " + slots),
                        new RackDeviceInfo.Bar(slots <= 0 ? 0 : (float) admitted.size() / slots,
                                clients.size() > slots ? RackDeviceInfo.BarStyle.WARN : RackDeviceInfo.BarStyle.NORMAL)),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.linked"),
                        accessPoints <= 0 && !clients.isEmpty() ? Component.translatable("hud.encodedlogistics.wireless.linked_offline", linkedCount) : linkedCount));
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (action == ACTION_UNLINK && value >= 0 && value < linked.size()) {
            linked.remove(value);
            changed(false);
        } else if (action == ACTION_UNLINK_CLIENT) {
            unlink(value);
        }
    }

    // The APs tab (each online Access Point with the clients it carries, in order: full ones first; offline ones with no
    // uplink), the Linked tab (its clients, then its Handheld Terminals) and the totals under them.
    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        MinecraftServer server = viewer.level().getServer();
        NetworkRef network = network();
        int perAp = Config.WIRELESS_AP_CLIENTS.getAsInt();
        int left = admitted.size();
        ValueOutput.ValueOutputList aps = output.childrenList("aps");
        List<ElclDevices.Device> named = ElclDevices.list(server, network);
        for (AccessPointBlockEntity ap : ControllerStructures.onNetwork(server, network, AccessPointBlockEntity.class, false)) {
            ValueOutput child = aps.addChild();
            boolean online = ap.isOnline();
            child.putString("name", ap.deviceName().isEmpty() ? ap.getBlockState().getBlock().getName().getString() : ap.deviceName());
            child.putBoolean("online", online);
            int carried = online ? Math.min(perAp, left) : 0;
            left -= carried;
            child.putInt("clients", carried);
            child.putInt("capacity", perAp);
        }
        ValueOutput.ValueOutputList list = output.childrenList("clients");
        Set<String> seen = new HashSet<>();
        for (Client client : clients) {
            ValueOutput child = list.addChild();
            String name = ElclDevices.nameAt(named, NodePos.of(client.pos()), null);
            if (name.isEmpty() && Wireless.deviceAt(server, NodePos.of(client.pos())) instanceof net.zagdrath.encodedlogistics.wireless.WirelessDevice device) {
                name = device.deviceName();
            }
            seen.add(name);
            child.putString("name", name.isEmpty() ? client.kind().getSerializedName() : name);
            child.putString("kind", client.kind().getSerializedName());
            child.putBoolean("connected", admitted.contains(client.pos()));
        }
        output.putInt("ap_slots", slots());
        output.putInt("admitted", admitted.size());
        ValueOutput.ValueOutputList entries = output.childrenList("entries");
        for (int i = 0; i < linked.size(); i++) {
            Linked entry = linked.get(i);
            ServerPlayer player = server.getPlayerList().getPlayer(entry.player());
            if (player != null) {
                String dimension = player.level().dimension().identifier().toString();
                if (!dimension.equals(entry.dimension()) || !player.getName().getString().equals(entry.name())) {
                    entry = new Linked(entry.player(), player.getName().getString(), dimension);
                    linked.set(i, entry);
                    saveOnly();
                }
            }
            ValueOutput child = entries.addChild();
            child.store("player", UUIDUtil.CODEC, entry.player());
            child.putString("name", entry.name());
            child.putString("dimension", entry.dimension());
            child.putBoolean("online", player != null);
            child.putBoolean("connected", player != null && connected(player));
        }
        output.putInt("connected", connected(server));
    }

    // --- Saving ---

    @Override
    public void saveSettings(ValueOutput output) {
        output.store("id", UUIDUtil.CODEC, id);
        ValueOutput.ValueOutputList list = output.childrenList("linked");
        for (Linked entry : linked) {
            ValueOutput child = list.addChild();
            child.store("player", UUIDUtil.CODEC, entry.player());
            child.putString("name", entry.name());
            child.putString("dimension", entry.dimension());
        }
        ValueOutput.ValueOutputList clientList = output.childrenList("clients");
        for (Client client : clients) {
            ValueOutput child = clientList.addChild();
            child.store("pos", GlobalPos.CODEC, client.pos());
            child.putString("kind", client.kind().getSerializedName());
        }
    }

    @Override
    public void loadSettings(ValueInput input) {
        id = input.read("id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        linked.clear();
        for (ValueInput child : input.childrenListOrEmpty("linked")) {
            child.read("player", UUIDUtil.CODEC).ifPresent(player -> linked.add(new Linked(player, child.getStringOr("name", "?"),
                    child.getStringOr("dimension", "minecraft:overworld"))));
        }
        clients.clear();
        for (ValueInput child : input.childrenListOrEmpty("clients")) {
            child.read("pos", GlobalPos.CODEC).ifPresent(pos -> clients.add(new Client(pos, WirelessClient.Kind.byName(child.getStringOr("kind", "bridge")))));
        }
    }
}
