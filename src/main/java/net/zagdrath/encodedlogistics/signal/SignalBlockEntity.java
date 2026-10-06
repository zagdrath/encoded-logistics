/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;
import net.zagdrath.encodedlogistics.midrange.MidrangeHud;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// What every signal device keeps (SignalBlock): its device name (given by ElclDevices the first time it's on a network,
// and kept on its item), whether its network has it online, and the redstone at its block (read when a neighbour
// changes; a change calls redstone(before, now)). Its settings are synced to clients (the update tag is everything
// saved), where the settings screen (SignalScreen) and the sounds (SignalSounds) read them. The settings screen's rows
// and their changes (setting) are the subclass's; the bottom field edits text rows and the device's name.
public abstract class SignalBlockEntity extends BlockEntity implements NetworkDevice, NamedDevice, MidrangeHud {
    // What sets a siren or speaker off: the redstone at its block, its network's commands, or either.
    public enum Trigger implements StringRepresentable {
        REDSTONE, NETWORK, BOTH;

        public boolean redstone() {
            return this != NETWORK;
        }

        public boolean network() {
            return this != REDSTONE;
        }

        public Component label() {
            return Component.translatable("gui.encodedlogistics.signal.trigger." + getSerializedName());
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // A settings screen row: its key (sent back by a click), label and shown value; editable rows take a click (left:
    // next, right: back, shift: by one); text rows open the bottom field with their text.
    public record Row(String key, Component label, Component value, boolean editable, boolean text) {
        public static Row shown(String key, Component label, Component value) {
            return new Row(key, label, value, false, false);
        }

        public static Row cycled(String key, Component label, Component value) {
            return new Row(key, label, value, true, false);
        }

        public static Row edited(String key, Component label, Component value) {
            return new Row(key, label, value, true, true);
        }
    }

    public static final String ROW_DEVICE = "device";

    private String name = "";
    private boolean online, powered, redstoneDirty = true;

    protected SignalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Network and name ---

    @Override
    public String deviceName() {
        return name;
    }

    @Override
    public void setDeviceName(String name) {
        this.name = name;
        sync();
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            sync();
        }
    }

    // Its network, while it's online there.
    public @Nullable NetworkRef network() {
        return online && level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
    }

    // Its network, online or not (the Firewall still asks it).
    public boolean networked() {
        return level instanceof ServerLevel serverLevel && ControllerStructures.networkOf(serverLevel, worldPosition) != null;
    }

    // --- Redstone ---

    public boolean powered() {
        return powered;
    }

    void redstoneChanged() {
        redstoneDirty = true;
    }

    // The redstone at its block went on or off (server).
    protected abstract void redstone(boolean now);

    // --- Ticking ---

    final void tick() {
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            clientTick();
            return;
        }
        if (redstoneDirty) {
            redstoneDirty = false;
            boolean now = level.hasNeighborSignal(worldPosition);
            if (now != powered) {
                powered = now;
                setChanged();
                redstone(now);
            }
        }
        serverTick();
    }

    protected void serverTick() {}

    protected void clientTick() {}

    // --- State shown on the block ---

    // Sets the block's state if it differs (its model and light follow).
    protected void showState(BlockState next) {
        if (level != null && !level.isClientSide() && next != getBlockState()) {
            level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
        }
    }

    // --- Settings screen ---

    // Its status (the screen's top field and the popup's status line).
    public abstract Component statusText();

    public abstract RackDeviceInfo.Status status();

    // The settings rows, from the synced state (both sides).
    public abstract List<Row> rows();

    // A row's text for the bottom field (a text row's, or the device name).
    public String text(String key) {
        return key.equals(ROW_DEVICE) ? name : "";
    }

    // A click on a row (step: +1 / -1, or +-10 with shift, as the row takes it) or text entered for it; true if
    // something changed. Server side, the player checked already.
    protected abstract boolean setting(ServerPlayer player, String key, int step, @Nullable String text) throws ElclException;

    // The screen's action (the speaker's Play / Stop): its label, or null for none.
    public @Nullable Component action() {
        return null;
    }

    protected void act(ServerPlayer player) {}

    // From the screen (SignalMenu.apply): a row's click or text, or the action.
    public void apply(ServerPlayer player, String key, int step, @Nullable String text) throws ElclException {
        if (key.equals("action")) {
            act(player);
            return;
        }
        if (key.equals(ROW_DEVICE)) {
            rename(text);
            return;
        }
        if (setting(player, key, step, text)) {
            sync();
        }
    }

    // A new device name (as RNMDEV): only on a network.
    private void rename(@Nullable String wanted) throws ElclException {
        NetworkRef network = level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
        if (wanted == null || network == null || name.isEmpty() || wanted.trim().equalsIgnoreCase(name)) {
            return;
        }
        ElclDevices.list(level.getServer(), network);
        ElclDevices.rename(level.getServer(), network, name, wanted.trim());
    }

    // The device row every screen ends with: its name on a network, else "not networked".
    protected Row deviceRow() {
        return name.isEmpty() ? Row.shown(ROW_DEVICE, Component.translatable("gui.encodedlogistics.signal.device"),
                Component.translatable("gui.encodedlogistics.signal.standalone"))
                : Row.edited(ROW_DEVICE, Component.translatable("gui.encodedlogistics.signal.device"), Component.literal(name));
    }

    // --- Popup (MidrangeHud: WirelessHud asks for it) ---

    @Override
    public RackDeviceInfo hudInfo() {
        return new RackDeviceInfo(getBlockState().getBlock().getName(), status(), statusText(), hudLines());
    }

    protected abstract List<RackDeviceInfo.InfoLine> hudLines();

    // --- Syncing ---

    // Saved and sent to the clients near it.
    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Components (its device name goes with the item) ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        String carried = components.get(ModDataComponents.DEVICE_NAME.get());
        if (carried != null) {
            name = carried;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) {
            components.set(ModDataComponents.DEVICE_NAME.get(), name);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("name");
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        name = input.getStringOr("name", "");
        powered = input.getBooleanOr("powered", false);
        // The server's own comes from its network.
        if (level != null && level.isClientSide()) {
            online = input.getBooleanOr("online", false);
        }
        redstoneDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!name.isEmpty()) {
            output.putString("name", name);
        }
        output.putBoolean("powered", powered);
        // For the client's popup and screen; worked out again on the server.
        output.putBoolean("online", online);
    }

    // An enum saved by its name, or its default.
    protected static <E extends Enum<E>> E load(ValueInput input, String key, E fallback) {
        String saved = input.getStringOr(key, fallback.name());
        for (E value : fallback.getDeclaringClass().getEnumConstants()) {
            if (value.name().equals(saved)) {
                return value;
            }
        }
        return fallback;
    }

    // The next (or previous) value of an enum, wrapping.
    protected static <E extends Enum<E>> E cycle(E value, int step) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[Math.floorMod(value.ordinal() + Integer.signum(step), values.length)];
    }
}
