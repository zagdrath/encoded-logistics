/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// A signal device's settings screen (client: SignalScreen): no slots; the screen shows the device's synced rows and
// sends a click on one, text for one, or its action (SignalConfigPayload), each needing the Firewall's build permission
// on a network and checked by the device (SignalBlockEntity.apply).
public class SignalMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Player player;

    public static void open(ServerPlayer player, BlockPos pos, Component title) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SignalMenu(id, inventory, pos), title), buf -> buf.writeBlockPos(pos));
    }

    // Client constructor.
    public SignalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    public SignalMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuTypes.SIGNAL_DEVICE.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
    }

    public BlockPos pos() {
        return pos;
    }

    public @Nullable SignalBlockEntity device() {
        return player.level().getBlockEntity(pos) instanceof SignalBlockEntity device ? device : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return device() != null && player.isWithinBlockInteractionRange(pos, 6.0);
    }

    // From the screen: a row's click (step) or text, or "action".
    public void apply(ServerPlayer player, String key, int step, @Nullable String text) {
        SignalBlockEntity device = device();
        if (device == null || !(player.level() instanceof ServerLevel level) || !NetworkAccess.check(level, pos, player, RackPermission.BUILD)) {
            return;
        }
        try {
            device.apply(player, key, step, text);
        } catch (ElclException e) {
            player.sendOverlayMessage(Component.literal(e.elclMessage().id() + "  " + e.elclMessage().text()));
        }
    }
}
