/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// The Firewall (1U): who may use its network. Each player listed has five permissions (RackPermission), each ON, OFF or
// INHERIT (the default policy: no access, view only, or full access). Its owner (whoever installed it) always has
// every permission. Players who may build on the network may change its rules. A network uses one Firewall; any other
// shows a fault and is ignored (see ControllerStructures.firewall).
public class FirewallDevice extends RackDevice {
    public enum Policy {
        DENY, VIEW, FULL;

        public static Policy byId(int id) {
            return values()[Math.clamp(id, 0, 2)];
        }

        public boolean allows(RackPermission permission) {
            return this == FULL || this == VIEW && permission == RackPermission.VIEW;
        }

        public Component label() {
            return Component.translatable("gui.encodedlogistics.firewall.policy." + name().toLowerCase(Locale.ROOT));
        }
    }

    public static final byte INHERIT = 0, ON = 1, OFF = 2;
    public static final int MAX_PLAYERS = 64;

    // Panel actions: value and text as noted.
    public static final int ACTION_CYCLE_POLICY = 0, ACTION_TOGGLE = 1, ACTION_ADD = 2, ACTION_REMOVE = 3;

    public record Entry(UUID id, String name, byte[] permissions) {
        public byte permission(RackPermission permission) {
            return permissions[permission.ordinal()];
        }

        public boolean custom() {
            for (byte value : permissions) {
                if (value != INHERIT) {
                    return true;
                }
            }
            return false;
        }
    }

    private @Nullable UUID owner;
    private String ownerName = "";
    private Policy policy = Policy.FULL;
    private final Map<UUID, Entry> players = new LinkedHashMap<>();
    // Another Firewall on the same network is the one in use.
    private boolean conflict;

    public FirewallDevice(RackDeviceType type) {
        super(type);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public Policy policy() {
        return policy;
    }

    public List<Entry> entries() {
        return List.copyOf(players.values());
    }

    public boolean allows(UUID player, RackPermission permission) {
        if (player.equals(owner)) {
            return true;
        }
        Entry entry = players.get(player);
        byte value = entry != null ? entry.permission(permission) : INHERIT;
        return value == INHERIT ? policy.allows(permission) : value == ON;
    }

    public boolean canEdit(ServerPlayer player) {
        return owner == null || allows(player.getUUID(), RackPermission.BUILD)
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public double drain() {
        return Config.FIREWALL_DRAIN.getAsDouble();
    }

    @Override
    public void onInstalled(@Nullable ServerPlayer by) {
        if (owner == null && by != null) {
            owner = by.getUUID();
            ownerName = by.getGameProfile().name();
            changed(false);
        }
    }

    @Override
    public void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0 || rack() == null) {
            return;
        }
        var network = ControllerStructures.networkOf(level, rack().getBlockPos());
        boolean conflicting = network != null && ControllerStructures.firewall(level.getServer(), network) != this
                && ControllerStructures.firewalls(level.getServer(), network) > 1;
        if (conflicting != conflict) {
            conflict = conflicting;
            changed(false);
        }
    }

    @Override
    public RackDeviceInfo.Status status() {
        return conflict ? RackDeviceInfo.Status.FAULT : super.status();
    }

    @Override
    public Component statusText() {
        return conflict ? Component.translatable("hud.encodedlogistics.firewall.conflict") : super.statusText();
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        long custom = players.values().stream().filter(Entry::custom).count();
        return List.of(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.firewall.policy"), policy.label()),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.firewall.custom"), Component.literal(Long.toString(custom))));
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (!canEdit(player)) {
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.firewall.no_edit"));
            return;
        }
        switch (action) {
            case ACTION_CYCLE_POLICY -> policy = Policy.byId((policy.ordinal() + (value < 0 ? 2 : 1)) % 3);
            case ACTION_TOGGLE -> {
                Entry entry = entry(text);
                if (entry == null) {
                    return;
                }
                int index = RackPermission.byId(value).ordinal();
                // INHERIT -> ON -> OFF -> INHERIT
                entry.permissions[index] = (byte) ((entry.permissions[index] + 1) % 3);
            }
            case ACTION_ADD -> {
                String name = text.trim();
                ServerPlayer target = player.level().getServer().getPlayerList().getPlayerByName(name);
                if (target == null) {
                    player.sendOverlayMessage(Component.translatable("message.encodedlogistics.firewall.not_found", name));
                    return;
                }
                if (players.size() >= MAX_PLAYERS && !players.containsKey(target.getUUID())) {
                    return;
                }
                players.putIfAbsent(target.getUUID(), new Entry(target.getUUID(), target.getGameProfile().name(), new byte[RackPermission.values().length]));
            }
            case ACTION_REMOVE -> {
                Entry entry = entry(text);
                if (entry != null) {
                    players.remove(entry.id());
                }
            }
            default -> {
                return;
            }
        }
        changed(false);
    }

    private @Nullable Entry entry(String id) {
        try {
            return players.get(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        saveSettings(output);
        output.putBoolean("can_edit", canEdit(viewer));
    }

    // --- Saving ---

    @Override
    public void saveSettings(ValueOutput output) {
        if (owner != null) {
            output.store("owner", UUIDUtil.CODEC, owner);
            output.putString("owner_name", ownerName);
        }
        output.putInt("policy", policy.ordinal());
        ValueOutput.ValueOutputList list = output.childrenList("players");
        for (Entry entry : players.values()) {
            ValueOutput child = list.addChild();
            child.store("id", UUIDUtil.CODEC, entry.id());
            child.putString("name", entry.name());
            int[] values = new int[entry.permissions().length];
            for (int i = 0; i < values.length; i++) {
                values[i] = entry.permissions()[i];
            }
            child.putIntArray("permissions", values);
        }
    }

    @Override
    public void loadSettings(ValueInput input) {
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        policy = Policy.byId(input.getIntOr("policy", Policy.FULL.ordinal()));
        players.clear();
        for (ValueInput child : input.childrenListOrEmpty("players")) {
            Optional<UUID> id = child.read("id", UUIDUtil.CODEC);
            if (id.isEmpty()) {
                continue;
            }
            byte[] permissions = new byte[RackPermission.values().length];
            int[] values = child.getIntArray("permissions").orElse(new int[0]);
            for (int i = 0; i < permissions.length && i < values.length; i++) {
                permissions[i] = (byte) Math.clamp(values[i], 0, 2);
            }
            players.put(id.get(), new Entry(id.get(), child.getStringOr("name", "?"), permissions));
        }
    }

    public String ownerName() {
        return ownerName;
    }
}
