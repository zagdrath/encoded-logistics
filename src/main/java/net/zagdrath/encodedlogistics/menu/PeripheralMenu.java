/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.elcl.exec.NamedDevice;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.net.MachinePayloads;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;

// A Midrange machine's green screen (HANDOFF 3: the Keypunch's, Card Reader's, Line Printer's, the control panels, the
// Disk and Tape Drives'): text only - no slots, items go in and out on the block itself. What the screen shows comes
// from the server as lines and numbers (MachinePayloads.Info), every second while it's open and after each action;
// whether the machine's online goes in a data slot. The screen sends its fields back (MachinePayloads.Text): a value
// (a field's key), an option typed beside a row of a list (OPTION + the row) and the command line (COMMAND), run as
// on a Terminal Desk's command line.
public abstract class PeripheralMenu extends AbstractContainerMenu {
    public static final int OPTION = 500, COMMAND = 999;
    private static final int REFRESH = 20;

    // The header: the system's and device's names and the phosphor (Midranges.writeOpening).
    public record Opening(String system, String device, String phosphor) {
        public static final Opening SERVER = new Opening("", "", "*GREEN");

        public static Opening read(RegistryFriendlyByteBuf buf) {
            return new Opening(buf.readUtf(), buf.readUtf(), buf.readUtf());
        }
    }

    protected final Container machine;
    protected final Player player;
    protected final @Nullable NamedDevice peripheral;
    private final Opening opening;
    private final DataSlot online = DataSlot.standalone();
    private int timer;
    // On the client, from the server.
    private Component message = Component.empty();
    private List<String> lines = List.of();
    private List<Integer> numbers = List.of();
    private int received;

    protected PeripheralMenu(@Nullable MenuType<?> type, int containerId, Inventory inventory, Container machine, @Nullable NamedDevice peripheral,
            Opening opening) {
        super(type, containerId);
        this.machine = machine;
        this.player = inventory.player;
        this.peripheral = peripheral;
        this.opening = opening;
        machine.startOpen(player);
        addDataSlot(online);
        if (peripheral != null) {
            online.set(peripheral.isOnline() ? 1 : 0);
        }
    }

    public Opening opening() {
        return opening;
    }

    public boolean online() {
        return online.get() != 0;
    }

    // --- Server ---

    @Override
    public void broadcastChanges() {
        if (peripheral != null && ++timer >= refreshTicks()) {
            timer = 0;
            online.set(peripheral.isOnline() ? 1 : 0);
            refresh();
        }
        super.broadcastChanges();
    }

    // What the screen shows: every second while it's open (or refreshTicks()), after each action, and once when it opens.
    protected void refresh() {}

    // How often it's sent while the screen's open.
    protected int refreshTicks() {
        return REFRESH;
    }

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        if (peripheral != null) {
            refresh();
        }
    }

    // A field typed on the screen: OPTION + a row's option, COMMAND the command line, else the menu's own fields.
    public final void setText(int key, String text) {
        if (peripheral == null) {
            return;
        }
        if (key == COMMAND) {
            if (!text.isBlank()) {
                send(command(text.trim()));
            }
        } else if (key >= OPTION && key < COMMAND) {
            String option = text.trim();
            if (!option.isEmpty()) {
                Component said = option(key - OPTION, option);
                send(said != null ? said : Component.translatable("crt.encodedlogistics.machine.bad_option", option));
            }
        } else {
            field(key, text);
        }
        refresh();
    }

    // A field's value (a grid cell, the report).
    protected void field(int key, String text) {}

    // An option typed beside row n of its list: what the message line says, or null when it isn't one of its options.
    protected @Nullable Component option(int row, String option) {
        return null;
    }

    // The command line: an ELCL or desk command, as at a Terminal Desk on the machine's network; its first line back
    // goes on the message line.
    protected Component command(String line) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(machine instanceof BlockEntity entity) || !(entity.getLevel() instanceof ServerLevel level)) {
            return Component.empty();
        }
        NetworkRef network = ControllerStructures.networkOf(level, entity.getBlockPos());
        if (network == null) {
            return Component.translatable("crt.encodedlogistics.machine.no_network");
        }
        TerminalOutput output = TerminalCommands.execute(new TerminalContext(level.getServer(), network, null, serverPlayer), line);
        if (output.message() != null) {
            return output.message();
        }
        return output.lines().isEmpty() ? Component.translatable("crt.encodedlogistics.machine.command_done", line.split("\\s", 2)[0].toUpperCase(Locale.ROOT))
                : Component.literal(output.lines().getFirst().text());
    }

    // Tells the screen: the message line and its lines and numbers.
    protected void send(Component message, List<String> lines, List<Integer> numbers) {
        // (A game test's mock player has no channel for it.)
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection.hasChannel(MachinePayloads.Info.TYPE)) {
            PacketDistributor.sendToPlayer(serverPlayer, new MachinePayloads.Info(containerId, message, lines, numbers));
        }
    }

    // The message line only.
    protected void send(Component message) {
        send(message, List.of(), List.of());
    }

    // Gives an item to the player (option 4: it comes out of the machine): into the inventory, or dropped at their feet.
    protected void give(ItemStack stack) {
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    // --- Client ---

    public void receive(MachinePayloads.Info info) {
        if (!info.message().getString().isEmpty() || info.lines().isEmpty() && info.numbers().isEmpty()) {
            message = info.message();
        }
        if (!info.lines().isEmpty() || !info.numbers().isEmpty()) {
            lines = info.lines();
            numbers = info.numbers();
        }
        received++;
    }

    public Component message() {
        return message;
    }

    public void clearMessage() {
        message = Component.empty();
    }

    public List<String> lines() {
        return lines;
    }

    public int number(int i) {
        return i < numbers.size() ? numbers.get(i) : 0;
    }

    // How many answers have come (the screen notices new ones).
    public int received() {
        return received;
    }

    // --- No slots ---

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        machine.stopOpen(player);
    }
}
