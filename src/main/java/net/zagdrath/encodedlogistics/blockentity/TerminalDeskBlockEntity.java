/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.block.TerminalDeskBlock;
import net.zagdrath.encodedlogistics.client.DeskSounds;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModSounds;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// A Terminal Desk's master: its drawer (9 slots - where *DRAWER withdrawals go; opened from the pedestal half, a plain
// 3x3 menu; hoppers can take from it), its screen (off while offline; on coming online it boots for BOOT_TICKS with a
// degauss, then stays on; the CRT hums while it's lit) and its pending deliveries - crafts sent to the drawer or a
// player's inventory, taken from the network once the job is done, and withdrawals waiting for a tape recall, taken as
// the items come back.
public class TerminalDeskBlockEntity extends BaseContainerBlockEntity implements NetworkDevice {
    public static final int SLOTS = 9, BOOT_TICKS = 60;
    private static final int DELIVERY_INTERVAL = 20;

    // Items to take from the network once they're there - a craft's output once its job is done (job), or a withdrawal
    // once its recall from tape is in - to a player's inventory (player set, while they're online), else the drawer.
    public record Delivery(Optional<UUID> job, ItemKey item, long amount, Optional<UUID> player) {}

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private boolean online;
    private int boot;
    private final List<Delivery> deliveries = new ArrayList<>();
    private int deliveryTimer;

    public TerminalDeskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.TERMINAL_DESK.get(), pos, state);
    }

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
    }

    public boolean isOnline() {
        return online;
    }

    public TerminalDeskBlock.Screen screen() {
        BlockState state = getBlockState();
        return state.hasProperty(TerminalDeskBlock.SCREEN) ? state.getValue(TerminalDeskBlock.SCREEN) : TerminalDeskBlock.Screen.OFF;
    }

    private void setScreen(TerminalDeskBlock.Screen screen) {
        if (level != null && screen() != screen) {
            level.setBlock(worldPosition, getBlockState().setValue(TerminalDeskBlock.SCREEN, screen), Block.UPDATE_CLIENTS);
        }
    }

    // The network it's on while it's online, else null.
    // The name scripts know it by (ELDESK01): given once on its first network, kept until renamed.
    private String deviceName = "";

    public String deviceName() {
        return deviceName;
    }

    public void setDeviceName(String name) {
        if (!deviceName.equals(name)) {
            deviceName = name;
            setChanged();
        }
    }

    public @Nullable NetworkRef network() {
        return online && level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
    }

    public void openTerminal(ServerPlayer player) {
        TerminalDeskMenu.open(player, worldPosition);
    }

    // --- Ticking ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, TerminalDeskBlockEntity desk) {
        // Screen: off -> boot (60 ticks, degauss) -> on, back off whenever the desk is offline.
        if (!desk.online) {
            desk.boot = 0;
            desk.setScreen(TerminalDeskBlock.Screen.OFF);
        } else if (desk.screen() == TerminalDeskBlock.Screen.OFF) {
            desk.boot = BOOT_TICKS;
            desk.setScreen(TerminalDeskBlock.Screen.BOOT);
            level.playSound(null, pos, ModSounds.DESK_DEGAUSS.value(), SoundSource.BLOCKS, 0.6F, 1.0F);
        } else if (desk.screen() == TerminalDeskBlock.Screen.BOOT && --desk.boot <= 0) {
            desk.setScreen(TerminalDeskBlock.Screen.ON);
        }
        if (!desk.deliveries.isEmpty() && ++desk.deliveryTimer >= DELIVERY_INTERVAL && level instanceof ServerLevel serverLevel) {
            desk.deliveryTimer = 0;
            desk.deliver(serverLevel.getServer());
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, TerminalDeskBlockEntity desk) {
        DeskSounds.tick(desk);
    }

    // --- The drawer ---

    // Puts as much of a stack in the drawer as fits; returns what didn't.
    public ItemStack addToDrawer(ItemStack stack) {
        ItemStack left = stack.copy();
        for (int slot = 0; slot < SLOTS && !left.isEmpty(); slot++) {
            ItemStack there = items.get(slot);
            if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, left)) {
                int move = Math.min(left.getCount(), there.getMaxStackSize() - there.getCount());
                there.grow(move);
                left.shrink(move);
            }
        }
        for (int slot = 0; slot < SLOTS && !left.isEmpty(); slot++) {
            if (items.get(slot).isEmpty()) {
                int move = Math.min(left.getCount(), left.getMaxStackSize());
                items.set(slot, left.split(move));
            }
        }
        if (left.getCount() != stack.getCount()) {
            setChanged();
        }
        return left;
    }

    // How many of an item the drawer has room for.
    public long drawerRoom(ItemKey key) {
        long room = 0;
        for (ItemStack there : items) {
            if (there.isEmpty()) {
                room += key.maxStackSize();
            } else if (ItemStack.isSameItemSameComponents(there, key.stack())) {
                room += there.getMaxStackSize() - there.getCount();
            }
        }
        return room;
    }

    // --- Deliveries ---

    public void addDelivery(Delivery delivery) {
        deliveries.add(delivery);
        setChanged();
    }

    public List<Delivery> deliveries() {
        return deliveries;
    }

    // Finished jobs' output comes out of the network to where it was asked for.
    private void deliver(MinecraftServer server) {
        NetworkRef network = network();
        NetworkStorage storage = network != null ? ControllerStructures.storageOf(server, network) : null;
        if (storage == null) {
            return;
        }
        List<JobHost> hosts = ControllerStructures.schedulersOf(server, network);
        ListIterator<Delivery> iterator = deliveries.listIterator();
        while (iterator.hasNext()) {
            Delivery delivery = iterator.next();
            if (delivery.job().isPresent() && hosts.stream().anyMatch(host -> host.job(delivery.job().get()) != null)) {
                continue;
            }
            ServerPlayer player = delivery.player().map(id -> server.getPlayerList().getPlayer(id)).orElse(null);
            long room = player != null ? delivery.amount() : drawerRoom(delivery.item());
            // Only what's hot (taking more would recall it again).
            long hot = storage.count(delivery.item());
            long taken = storage.extract(delivery.item(), Math.min(hot, Math.min(room, delivery.amount())), false);
            long left = taken;
            while (left > 0) {
                ItemStack stack = delivery.item().toStack((int) Math.min(left, delivery.item().maxStackSize()));
                left -= stack.getCount();
                ItemStack rest = player != null ? give(player, stack) : addToDrawer(stack);
                if (!rest.isEmpty()) {
                    storage.insert(ItemKey.of(rest), rest.getCount(), false);
                    taken -= rest.getCount();
                }
            }
            long still = delivery.amount() - taken;
            // Done, or nothing more to come (none hot, none on tape, no job making it).
            if (still <= 0 || storage.count(delivery.item()) <= 0 && storage.cold().count(delivery.item()) <= 0) {
                iterator.remove();
            } else if (taken > 0) {
                iterator.set(new Delivery(Optional.empty(), delivery.item(), still, delivery.player()));
            }
            setChanged();
        }
    }

    private static ItemStack give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        return stack;
    }

    // --- Container (the drawer) ---

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.encodedlogistics.terminal_desk.drawer");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DispenserMenu(containerId, inventory, this);
    }

    // --- Components (its device name goes with the item) ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
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

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        deviceName = input.getStringOr("device_name", "");
        deliveries.clear();
        for (ValueInput child : input.childrenListOrEmpty("deliveries")) {
            Optional<ItemKey> item = child.read("item", ItemKey.CODEC);
            if (item.isPresent()) {
                deliveries.add(new Delivery(child.read("job", UUIDUtil.CODEC), item.get(), child.getLongOr("amount", 0), child.read("player", UUIDUtil.CODEC)));
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
        ValueOutput.ValueOutputList list = output.childrenList("deliveries");
        for (Delivery delivery : deliveries) {
            ValueOutput child = list.addChild();
            delivery.job().ifPresent(job -> child.store("job", UUIDUtil.CODEC, job));
            child.store("item", ItemKey.CODEC, delivery.item());
            child.putLong("amount", delivery.amount());
            delivery.player().ifPresent(player -> child.store("player", UUIDUtil.CODEC, player));
        }
    }
}
