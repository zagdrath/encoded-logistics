/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.menu.RackMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.network.NetworkPart;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceItem;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// The Server Rack's master block entity: the devices by the unit they sit at (bottom U), the doors, and the rack's place
// on its network. It's one network device using a lane per device (all or none: they share the rack's connection),
// draining what its devices drain; its devices go online and offline with it.
//
// Clients get the doors, whether each device is on, off or faulted, and what each device's front shows (writeClient).
// Door changes are sent at once; device changes at most every SYNC_INTERVAL ticks. The doors animate on the client
// (DOOR_TICKS, smoothstep).
public class RackBlockEntity extends BlockEntity implements NetworkDevice {
    public static final int DOOR_TICKS = 10;
    private static final int SYNC_INTERVAL = 10;

    private final TreeMap<Integer, RackDevice> devices = new TreeMap<>();
    private boolean frontOpen, rearOpen;
    private boolean online;
    private boolean syncPending;
    private int syncCooldown;
    // Client: door animation, 0 (closed) to DOOR_TICKS (open), this tick and last.
    private int frontTicks, rearTicks, lastFrontTicks, lastRearTicks;
    private boolean synced;

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SERVER_RACK.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(ServerRackBlock.FACING);
    }

    // --- Devices ---

    public Collection<RackDevice> devices() {
        return Collections.unmodifiableCollection(devices.values());
    }

    // The device taking up unit u, if any.
    public @Nullable RackDevice deviceAt(int u) {
        Map.Entry<Integer, RackDevice> entry = devices.floorEntry(u);
        return entry != null && entry.getValue().occupies(u) ? entry.getValue() : null;
    }

    // Whether size units from u up are all inside the rack and free.
    public boolean fits(int u, int size) {
        if (u < 1 || u + size - 1 > RackGeometry.UNITS) {
            return false;
        }
        for (int unit = u; unit < u + size; unit++) {
            if (deviceAt(unit) != null) {
                return false;
            }
        }
        return true;
    }

    // The lowest unit a device of that size fits at, or 0.
    public int lowestFit(int size) {
        for (int u = 1; u + size - 1 <= RackGeometry.UNITS; u++) {
            if (fits(u, size)) {
                return u;
            }
        }
        return 0;
    }

    public int freeUnits() {
        int used = 0;
        for (RackDevice device : devices.values()) {
            used += device.size();
        }
        return RackGeometry.UNITS - used;
    }

    public void install(RackDevice device, int u, @Nullable ServerPlayer by) {
        device.attach(this, u);
        devices.put(u, device);
        device.setOnline(online);
        device.onInstalled(by);
        deviceChanged(true);
    }

    public @Nullable RackDevice remove(int u) {
        RackDevice device = deviceAt(u);
        if (device == null) {
            return null;
        }
        devices.remove(device.u());
        device.onRemoved();
        device.detach();
        deviceChanged(true);
        return device;
    }

    // A player mounts the device in their hand at u (the open front, or the screen).
    public boolean installFromHand(ServerPlayer player, ItemStack stack, int u) {
        RackDeviceType type = RackDeviceType.of(stack);
        if (type == null || !(level instanceof ServerLevel serverLevel) || !NetworkAccess.check(serverLevel, worldPosition, player, RackPermission.BUILD)) {
            return false;
        }
        if (!fits(u, type.size())) {
            player.sendOverlayMessage(Component.translatable("gui.encodedlogistics.rack.no_room", type.size()));
            return false;
        }
        RackDevice device = RackDeviceItem.create(stack, serverLevel.registryAccess());
        if (device == null) {
            return false;
        }
        install(device, u, player);
        stack.consume(1, player);
        return true;
    }

    // A player takes the device at u out: it (with its settings) and whatever it held go to their inventory.
    public boolean takeOut(ServerPlayer player, int u) {
        if (!(level instanceof ServerLevel serverLevel) || deviceAt(u) == null
                || !NetworkAccess.check(serverLevel, worldPosition, player, RackPermission.BUILD)) {
            return false;
        }
        RackDevice device = remove(u);
        if (device == null) {
            return false;
        }
        player.getInventory().placeItemBackInInventory(RackDeviceItem.toStack(device, serverLevel.registryAccess()), Prediction.SERVER_ONLY);
        device.contents().forEach(stack -> player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY));
        device.clearContents();
        return true;
    }

    // A device's settings or state changed.
    public void deviceChanged(boolean topology) {
        if (level instanceof ServerLevel serverLevel) {
            setChanged();
            if (topology) {
                ControllerStructures.get(serverLevel).markTopologyChanged();
            }
            syncPending = true;
        }
    }

    // --- Doors ---

    public boolean isFrontOpen() {
        return frontOpen;
    }

    public boolean isRearOpen() {
        return rearOpen;
    }

    public void setFrontOpen(boolean open) {
        if (frontOpen != open) {
            frontOpen = open;
            doorMoved(open);
        }
    }

    public void setRearOpen(boolean open) {
        if (rearOpen != open) {
            rearOpen = open;
            doorMoved(open);
        }
    }

    private void doorMoved(boolean open) {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition, (open ? ModSounds.RACK_DOOR_OPEN : ModSounds.RACK_DOOR_CLOSE).value(), SoundSource.BLOCKS, 0.8F,
                    0.95F + level.getRandom().nextFloat() * 0.1F);
            syncNow();
        }
    }

    // Client: how open a door is, 0 to 1, eased.
    public float frontOpenness(float partialTick) {
        return RackGeometry.ease((lastFrontTicks + (frontTicks - lastFrontTicks) * partialTick) / DOOR_TICKS);
    }

    public float rearOpenness(float partialTick) {
        return RackGeometry.ease((lastRearTicks + (rearTicks - lastRearTicks) * partialTick) / DOOR_TICKS);
    }

    // --- Network ---

    public DeviceNode networkNode(Set<Direction> sides) {
        int lanes = 0;
        double drain = 0;
        List<NetworkPart> parts = new ArrayList<>();
        for (RackDevice device : devices.values()) {
            lanes += device.laneCost();
            drain += device.drain();
            parts.add(new NetworkPart(device.type().item(), device.drain()));
        }
        return new DeviceNode(worldPosition.immutable(), sides, lanes, drain, parts, true);
    }

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            devices.values().forEach(device -> device.setOnline(online));
        }
    }

    public boolean isOnline() {
        return online;
    }

    // --- Ticking ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, RackBlockEntity rack) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (RackDevice device : List.copyOf(rack.devices.values())) {
            device.tick(serverLevel);
        }
        if (rack.syncCooldown > 0) {
            rack.syncCooldown--;
        } else if (rack.syncPending) {
            rack.syncNow();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RackBlockEntity rack) {
        rack.lastFrontTicks = rack.frontTicks;
        rack.lastRearTicks = rack.rearTicks;
        rack.frontTicks = Math.clamp(rack.frontTicks + (rack.frontOpen ? 1 : -1), 0, DOOR_TICKS);
        rack.rearTicks = Math.clamp(rack.rearTicks + (rack.rearOpen ? 1 : -1), 0, DOOR_TICKS);
    }

    private void syncNow() {
        syncPending = false;
        syncCooldown = SYNC_INTERVAL;
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- The screen ---

    public void openMenu(ServerPlayer player) {
        if (!NetworkAccess.check(player.level(), worldPosition, player, RackPermission.VIEW)) {
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RackMenu(id, inventory, this),
                Component.translatable("block.encodedlogistics.server_rack")), buf -> buf.writeBlockPos(worldPosition));
    }

    // The whole rack, for rendering.
    public AABB bounds() {
        return RackGeometry.bounds(worldPosition, facing());
    }

    // --- Breaking ---

    // The devices, with their settings, and what they hold.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            for (RackDevice device : devices.values()) {
                Block.popResource(serverLevel, pos, RackDeviceItem.toStack(device, serverLevel.registryAccess()));
                device.contents().forEach(stack -> Block.popResource(serverLevel, pos, stack));
                device.clearContents();
                device.onRemoved();
            }
            devices.clear();
        }
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        frontOpen = input.getBooleanOr("front_open", false);
        rearOpen = input.getBooleanOr("rear_open", false);
        devices.clear();
        for (ValueInput child : input.childrenListOrEmpty("devices")) {
            RackDevice device = read(child);
            if (device != null) {
                device.load(child.childOrEmpty("data"));
                devices.put(device.u(), device);
            }
        }
    }

    // A device's type and place from a saved or synced entry, or null if it's unknown or doesn't fit.
    private @Nullable RackDevice read(ValueInput child) {
        Identifier id = Identifier.tryParse(child.getStringOr("type", ""));
        RackDeviceType type = id != null ? RackDeviceType.byId(id) : null;
        int u = child.getIntOr("u", 0);
        if (type == null || !fits(u, type.size())) {
            return null;
        }
        RackDevice device = type.create();
        device.attach(this, u);
        return device;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("front_open", frontOpen);
        output.putBoolean("rear_open", rearOpen);
        ValueOutput.ValueOutputList list = output.childrenList("devices");
        for (RackDevice device : devices.values()) {
            ValueOutput child = list.addChild();
            child.putString("type", device.type().id().toString());
            child.putInt("u", device.u());
            device.save(child.child("data"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.putBoolean("front_open", frontOpen);
        output.putBoolean("rear_open", rearOpen);
        output.putBoolean("online", online);
        ValueOutput.ValueOutputList list = output.childrenList("devices");
        for (RackDevice device : devices.values()) {
            ValueOutput child = list.addChild();
            child.putString("type", device.type().id().toString());
            child.putInt("u", device.u());
            child.putInt("status", device.status().ordinal());
            child.putBoolean("online", device.isOnline());
            device.writeClient(child.child("client"));
        }
        return output.buildResult();
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readUpdate(input);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        readUpdate(input);
    }

    private void readUpdate(ValueInput input) {
        frontOpen = input.getBooleanOr("front_open", false);
        rearOpen = input.getBooleanOr("rear_open", false);
        // The first time (the chunk loading), the doors are where they are rather than swinging there.
        if (!synced) {
            synced = true;
            frontTicks = lastFrontTicks = frontOpen ? DOOR_TICKS : 0;
            rearTicks = lastRearTicks = rearOpen ? DOOR_TICKS : 0;
        }
        online = input.getBooleanOr("online", false);
        devices.clear();
        for (ValueInput child : input.childrenListOrEmpty("devices")) {
            RackDevice device = read(child);
            if (device != null) {
                device.setOnline(child.getBooleanOr("online", false));
                device.setShownStatus(RackDeviceInfo.Status.byId(child.getIntOr("status", 1)));
                device.readClient(child.childOrEmpty("client"));
                devices.put(device.u(), device);
            }
        }
    }
}
