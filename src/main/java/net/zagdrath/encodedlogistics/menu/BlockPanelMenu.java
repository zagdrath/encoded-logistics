/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.net.BlockPanelPayloads;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// A block's panel screen (a PanelHost: the Exchange Switch, the Directory Server): no slots; the host's state goes to
// the client every SYNC_INTERVAL ticks (and at once after an action), the player's actions come back. The kind of panel
// (and the host's position) come with the menu.
public class BlockPanelMenu extends AbstractContainerMenu {
    private static final int SYNC_INTERVAL = 10;
    private final BlockPos pos;
    private final String kind;
    private final @Nullable PanelHost host;
    private final @Nullable Player player;
    private int ticksUntilSync;

    public BlockPanelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenuTypes.BLOCK_PANEL.get(), containerId);
        this.pos = BlockPos.STREAM_CODEC.decode(extraData);
        this.kind = ByteBufCodecs.STRING_UTF8.decode(extraData);
        this.host = null;
        this.player = null;
    }

    public BlockPanelMenu(int containerId, Inventory inventory, PanelHost host, BlockPos pos) {
        super(ModMenuTypes.BLOCK_PANEL.get(), containerId);
        this.pos = pos;
        this.kind = host.panelKind();
        this.host = host;
        this.player = inventory.player;
    }

    // Opens a host's panel for a player.
    public static void open(ServerPlayer player, PanelHost host, BlockPos pos) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return host.panelTitle();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return new BlockPanelMenu(containerId, inventory, host, pos);
            }
        }, buf -> {
            BlockPos.STREAM_CODEC.encode(buf, pos);
            ByteBufCodecs.STRING_UTF8.encode(buf, host.panelKind());
        });
    }

    public BlockPos pos() {
        return pos;
    }

    public String kind() {
        return kind;
    }

    public void handleAction(ServerPlayer player, int action, int value, String text) {
        if (host != null) {
            host.handlePanelAction(player, action, value, text);
            ticksUntilSync = 0;
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (host == null || !(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level) || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL;
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        host.writePanel(output, serverPlayer);
        PacketDistributor.sendToPlayer(serverPlayer, new BlockPanelPayloads.Data(containerId, output.buildResult()));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (host instanceof BlockEntity entity) {
            return !entity.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
        }
        return true;
    }
}
