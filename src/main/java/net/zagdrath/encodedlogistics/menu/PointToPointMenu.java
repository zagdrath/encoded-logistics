/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.part.LinkType;
import net.zagdrath.encodedlogistics.part.PointToPointPart;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// A Point-to-Point Link's screen (screens/point_to_point_link.json): what it carries and which way (buttons 0-3 pick the
// type, 4 flips in / out; both only while unpaired), button 5 unpairs, and the link's status with its first partner.
// No slots. data: 0 type, 1 output, 2 partners, 3 status (STATUS_*), 4-9 the first partner's x, y, z in 16-bit halves.
public class PointToPointMenu extends AbstractContainerMenu {
    public static final int BUTTON_TYPE = 0, BUTTON_DIRECTION = 4, BUTTON_UNPAIR = 5;
    public static final int STATUS_UNLINKED = 0, STATUS_LINKED = 1, STATUS_OFFLINE = 2;
    private static final int DATA = 10;

    private final @Nullable PointToPointPart part;
    private final ContainerData data;

    public static void open(ServerPlayer player, PointToPointPart part) {
        PartMenus.open(player, part, Component.translatable(part.type().item().getDescriptionId()),
                (id, inventory, p) -> new PointToPointMenu(id, inventory, (PointToPointPart) p));
    }

    // Client constructor (the menu data names the part; the client works from the synced data).
    public PointToPointMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, (PointToPointPart) null);
    }

    private PointToPointMenu(int containerId, Inventory inventory, @Nullable PointToPointPart part) {
        super(ModMenuTypes.POINT_TO_POINT_LINK.get(), containerId);
        this.part = part;
        if (part != null) {
            data = new ContainerData() {
                @Override
                public int get(int index) {
                    BlockPos partner = part.paired() ? part.partners().getFirst().pos() : BlockPos.ZERO;
                    return switch (index) {
                        case 0 -> part.linkType().ordinal();
                        case 1 -> part.output() ? 1 : 0;
                        case 2 -> part.partners().size();
                        case 3 -> status(part);
                        case 4 -> PartMenus.low(partner.getX());
                        case 5 -> PartMenus.high(partner.getX());
                        case 6 -> PartMenus.low(partner.getY());
                        case 7 -> PartMenus.high(partner.getY());
                        case 8 -> PartMenus.low(partner.getZ());
                        default -> PartMenus.high(partner.getZ());
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return DATA;
                }
            };
        } else {
            data = new SimpleContainerData(DATA);
        }
        addDataSlots(data);
    }

    // Linked while a partner is loaded and points back; paired but none is, the partner's offline.
    private static int status(PointToPointPart part) {
        if (!part.paired()) {
            return STATUS_UNLINKED;
        }
        Level level = part.host().getLevel();
        for (PointToPointPart.Endpoint partner : part.partners()) {
            if (level != null && level.isLoaded(partner.pos())) {
                PointToPointPart other = PointToPointPart.at(level, partner.pos(), partner.side());
                if (other != null && other.partners().contains(part.self())) {
                    return STATUS_LINKED;
                }
            }
        }
        return STATUS_OFFLINE;
    }

    public LinkType linkType() {
        return LinkType.byId(data.get(0));
    }

    public boolean output() {
        return data.get(1) != 0;
    }

    public int partners() {
        return data.get(2);
    }

    public int status() {
        return data.get(3);
    }

    public BlockPos partner() {
        return new BlockPos(PartMenus.join(data.get(4), data.get(5)), PartMenus.join(data.get(6), data.get(7)),
                PartMenus.join(data.get(8), data.get(9)));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (part == null) {
            return false;
        }
        if (id >= BUTTON_TYPE && id < BUTTON_TYPE + LinkType.values().length) {
            part.setLinkType(LinkType.byId(id - BUTTON_TYPE));
            return true;
        }
        if (id == BUTTON_DIRECTION) {
            part.toggleDirection();
            return true;
        }
        if (id == BUTTON_UNPAIR) {
            part.unpairAll();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return part == null || part.stillValid(player);
    }
}
