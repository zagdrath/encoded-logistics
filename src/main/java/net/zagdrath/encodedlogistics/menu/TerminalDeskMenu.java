/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The Terminal Desk's green screen (client: CrtScreen): the Access Terminal's item sync (the network's items, hot and
// cold) for Work with Inventory, and requests - commands, screen queries, completions - answered by TerminalService.
// Open while the desk is there and the player near it. An Integrated Midrange System's console opens the same session
// (its pos the system's master), while the system is running.
public class TerminalDeskMenu extends AccessTerminalMenu implements CrtHost {
    // The session signed on (needed at SECLVL 30 on a network with a Firewall).
    private boolean signedOn;

    public TerminalDeskMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public TerminalDeskMenu(int containerId, Inventory inventory, BlockPos desk) {
        super(ModMenuTypes.TERMINAL_DESK.get(), containerId, inventory, desk, Direction.NORTH, DEFAULT_ROWS, 0);
        if (console() instanceof MidrangeSystemBlockEntity system && !inventory.player.level().isClientSide()) {
            system.consoleOpened();
        }
    }

    // An Integrated Midrange System's console, when that's where the session is (not a desk).
    private @Nullable MidrangeSystemBlockEntity console() {
        return player.level().getBlockEntity(pos) instanceof MidrangeSystemBlockEntity system && system.integrated() ? system : null;
    }

    public static void open(ServerPlayer player, BlockPos desk) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TerminalDeskMenu(id, inventory, desk),
                Component.translatable("block.encodedlogistics.terminal_desk")), buf -> buf.writeBlockPos(desk));
    }

    public @Nullable TerminalDeskBlockEntity desk() {
        return player.level().getBlockEntity(pos) instanceof TerminalDeskBlockEntity desk ? desk : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return (desk() != null || console() != null && console().consoleReady()) && player.isWithinBlockInteractionRange(pos, 4.0);
    }

    // A request from the screen: a command line, a screen's query or a completion. The session's interactive job
    // starts with its first request; every answer says how many of the player's messages wait unread.
    @Override
    public void handle(ServerPlayer player, int kind, String text) {
        TerminalContext context = new TerminalContext(player.level().getServer(), network(), desk(), player);
        ElclSystem system = context.network() != null ? new ElclSystem(context.server(), context.network()) : null;
        TerminalOutput output;
        if (refused(system, signedOn, kind, text)) {
            // Not signed on yet where the system wants it: nothing but the sign-on.
            output = TerminalOutput.message(Component.literal(ElclMessage.of("ELC0402", context.user()).toString()));
        } else {
            if (system != null) {
                ElclServices.users().profile(system, context.user(), player.getUUID());
                ElclServices.jobs().interactive(system, context.user(), session(player), deskName(context));
            }
            output = TerminalService.handle(context, kind, text);
        }
        int unread = system != null ? ElclServices.messages().unread(system, context.user()) : -1;
        PacketDistributor.sendToPlayer(player, new CrtResponsePayload(containerId, kind, TerminalService.topic(kind, text), output.lines(),
                Optional.ofNullable(output.message()), unread));
    }

    // The session has signed on (ScreenQueries' signon).
    public void signOff() {
        signedOn = false;
    }

    public void signOn() {
        signedOn = true;
    }

    // Whether a request is turned away for want of a sign-on: the system needs one, the session hasn't, and it isn't the
    // system's info or the sign-on itself.
    public static boolean refused(@Nullable ElclSystem system, boolean signedOn, int kind, String text) {
        return system != null && !signedOn && ElclServices.users().signOnRequired(system) && !signOnRequest(kind, text);
    }

    private static boolean signOnRequest(int kind, String text) {
        String first = text.trim().split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        return kind == TerminalService.QUERY && first.equals("info") || kind == TerminalService.SCREEN && first.equals("signon");
    }

    private String session(Player player) {
        return player.getUUID() + "/" + containerId;
    }

    // The desk's name on its network (ELDESK01), the interactive job's host.
    private String deskName(TerminalContext context) {
        for (ElclDevices.Device device : ElclDevices.list(context.server(), context.network())) {
            if (device.pos().pos().equals(pos)) {
                return device.name();
            }
        }
        return "ELDESK01";
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (console() != null && !player.level().isClientSide()) {
            console().consoleClosed();
        }
        if (player instanceof ServerPlayer serverPlayer && network() != null) {
            ElclSystem system = new ElclSystem(serverPlayer.level().getServer(), network());
            ElclServices.jobs().endInteractive(system, session(player));
            ElclServices.libraries().unlockAll(system, player.getName().getString());
        }
    }
}
