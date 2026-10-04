/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.item.HandheldLinkState;
import net.zagdrath.encodedlogistics.item.HandheldTerminalItem;
import net.zagdrath.encodedlogistics.menu.HandheldTerminalMenu;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// The Wireless Controller (1U): a Relay Antenna with no range. A Handheld Terminal used on its unit (front door open) or
// anywhere on its rack is linked to it - and to its network - and from then on works anywhere: in any dimension
// (wirelessCrossDimension), through this controller or, with wirelessAnyController, any online one on the network.
// Unlinking a terminal here (its owner's entry) cuts it off. It keeps who it has linked, with the dimension they were
// last seen in; its id and list go with it when it's taken out.
public class WirelessControllerDevice extends RackDevice {
    public static final int ACTION_UNLINK = 0;

    public record Linked(UUID player, String name, String dimension) {}

    private UUID id = UUID.randomUUID();
    private final List<Linked> linked = new ArrayList<>();

    public WirelessControllerDevice(RackDeviceType type) {
        super(type);
    }

    public UUID id() {
        return id;
    }

    public List<Linked> linked() {
        return linked;
    }

    @Override
    public double drain() {
        return Config.WIRELESS_CONTROLLER_DRAIN.getAsDouble();
    }

    // Whether a player's terminal is linked here.
    public boolean serves(Player player) {
        return linked.stream().anyMatch(entry -> entry.player().equals(player.getUUID()));
    }

    // Links a Handheld Terminal (and its holder) to this controller and its network.
    public boolean link(ServerPlayer player, ItemStack stack) {
        NetworkRef network = rack() != null ? rack().network(this) : null;
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

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.linked"), Component.literal(Integer.toString(linked.size()))),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.wireless.connected"),
                        Component.literal(Integer.toString(connected(viewer.level().getServer())))));
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (action == ACTION_UNLINK && value >= 0 && value < linked.size()) {
            linked.remove(value);
            changed(false);
        }
    }

    // Its list, each entry with where its player is now (or was last seen) and whether they're online and connected.
    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        MinecraftServer server = viewer.level().getServer();
        ValueOutput.ValueOutputList list = output.childrenList("entries");
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
            ValueOutput child = list.addChild();
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
    }

    @Override
    public void loadSettings(ValueInput input) {
        id = input.read("id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        linked.clear();
        for (ValueInput child : input.childrenListOrEmpty("linked")) {
            child.read("player", UUIDUtil.CODEC).ifPresent(player -> linked.add(new Linked(player, child.getStringOr("name", "?"),
                    child.getStringOr("dimension", "minecraft:overworld"))));
        }
    }
}
