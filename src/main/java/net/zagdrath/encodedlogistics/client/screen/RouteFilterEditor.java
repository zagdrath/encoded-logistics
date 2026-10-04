/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.rack.ItemRouting;

// A route's filter editor (the Router's and the L3 Switch's), drawn over the panel's route list while open: the route's
// endpoints, its nine entries in a 3x3 grid (click with an item to set one, empty-handed or right-click to clear it; JEI
// drags land too) and the port filter's option buttons beside them (allow / deny list, match by tag, match components
// exactly). Under the grid, the route's mode (Move / Share) and, for a Share, its options: Read or Read/Write, whether
// crafting may use what it shares, one way or both ways. The panel's "Done" closes it. Area coordinates are
// panel-relative.
final class RouteFilterEditor {
    private static final int SLOT = 18, GRID_Y = 14, OPTIONS_GAP = 8, OPTIONS_STEP = 20, LIST = 0xFF2A2A2A;
    private static final int SHARE_SIZE = 14, SHARE_STEP = 16;
    // Where the item numbers start for the share toggles (at() gives entries 0..8, filter options SIZE..).
    private static final int SHARE_HIT = 100;

    private final RackScreen.Panel panel;
    private final int x, y, width, height, setAction, optionAction, shareAction;
    private int route = -1;

    RouteFilterEditor(RackScreen.Panel panel, int x, int y, int width, int height, int setAction, int optionAction, int shareAction) {
        this.panel = panel;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.setAction = setAction;
        this.optionAction = optionAction;
        this.shareAction = shareAction;
    }

    private int shareY() {
        return gridY() + 3 * SLOT + 2;
    }

    // The share toggles shown: the mode, and a Share's three options.
    private static int shareButtons(ItemRouting.Route route) {
        return route.shares() ? ItemRouting.SHARE_OPTIONS : 1;
    }

    boolean isOpen() {
        return route >= 0;
    }

    int route() {
        return route;
    }

    void open(int route) {
        this.route = route;
    }

    void close() {
        route = -1;
    }

    // The route being edited, or null (closing the editor when it's gone).
    ItemRouting.@Nullable Route current(List<ItemRouting.Route> routes) {
        if (route >= routes.size()) {
            route = -1;
        }
        currentRoute = route >= 0 ? routes.get(route) : null;
        return currentRoute;
    }

    // The route as last drawn (which share toggles show).
    private ItemRouting.@Nullable Route currentRoute;

    private int gridX() {
        return x + 2;
    }

    private int gridY() {
        return y + GRID_Y;
    }

    private int optionsX() {
        return gridX() + 3 * SLOT + OPTIONS_GAP;
    }

    // The entry or option under a panel-relative point: 0..8 an entry, SIZE + option an option button, else -1.
    private int at(double px, double py) {
        for (int i = 0; i < PartFilter.SIZE; i++) {
            int sx = gridX() + i % 3 * SLOT, sy = gridY() + i / 3 * SLOT;
            if (px >= sx && px < sx + SLOT && py >= sy && py < sy + SLOT) {
                return i;
            }
        }
        for (int option = 0; option < ItemRouting.OPTIONS; option++) {
            int by = gridY() + option * OPTIONS_STEP;
            if (px >= optionsX() && px < optionsX() + PartScreens.BUTTON_SIZE && py >= by && py < by + PartScreens.BUTTON_SIZE) {
                return PartFilter.SIZE + option;
            }
        }
        for (int option = 0; option < ItemRouting.SHARE_OPTIONS; option++) {
            int bx = gridX() + option * SHARE_STEP;
            if (px >= bx && px < bx + SHARE_SIZE && py >= shareY() && py < shareY() + SHARE_SIZE) {
                return SHARE_HIT + option;
            }
        }
        return -1;
    }

    // A share toggle's icon (an item), and whether it's on (off ones are dimmed).
    private static ItemStack shareIcon(ItemRouting.Route route, int option) {
        return new ItemStack(switch (option) {
            case ItemRouting.SHARE_MODE -> route.shares() ? Items.SPYGLASS : Items.HOPPER;
            case ItemRouting.SHARE_ACCESS -> route.readWrite() ? Items.WRITABLE_BOOK : Items.BOOK;
            case ItemRouting.SHARE_CRAFTING -> Items.CRAFTING_TABLE;
            default -> Items.COMPASS;
        });
    }

    private static boolean shareOn(ItemRouting.Route route, int option) {
        return switch (option) {
            case ItemRouting.SHARE_CRAFTING -> route.crafting();
            case ItemRouting.SHARE_BIDIRECTIONAL -> route.bidirectional();
            default -> true;
        };
    }

    private static Component shareTip(ItemRouting.Route route, int option) {
        return Component.translatable(switch (option) {
            case ItemRouting.SHARE_MODE -> route.shares() ? "gui.encodedlogistics.route.mode.share" : "gui.encodedlogistics.route.mode.move";
            case ItemRouting.SHARE_ACCESS -> route.readWrite() ? "gui.encodedlogistics.route.access.read_write" : "gui.encodedlogistics.route.access.read";
            case ItemRouting.SHARE_CRAFTING -> route.crafting() ? "gui.encodedlogistics.route.crafting.on" : "gui.encodedlogistics.route.crafting.off";
            default -> route.bidirectional() ? "gui.encodedlogistics.route.bidirectional.on" : "gui.encodedlogistics.route.bidirectional.off";
        });
    }

    // A route's mode for its row's tooltip: "Move", or "Share (Read/Write, crafting, both ways)".
    static Component modeLine(ItemRouting.Route route) {
        if (!route.shares()) {
            return Component.translatable("gui.encodedlogistics.route.mode.move");
        }
        return Component.translatable("gui.encodedlogistics.route.share_summary",
                Component.translatable(route.readWrite() ? "gui.encodedlogistics.route.access.read_write_short" : "gui.encodedlogistics.route.access.read_short"),
                Component.translatable(route.crafting() ? "gui.encodedlogistics.route.crafting.short_on" : "gui.encodedlogistics.route.crafting.short_off"),
                Component.translatable(route.bidirectional() ? "gui.encodedlogistics.route.bidirectional.short_on"
                        : "gui.encodedlogistics.route.bidirectional.short_off"));
    }

    // The colour a route row's arrow is tinted: white for Move, blue for Share.
    static int arrowColor(ItemRouting.Route route) {
        return route.shares() ? 0xFF8FB8F0 : 0xFFFFFFFF;
    }

    // --- Drawing ---

    void extractBackground(GuiGraphicsExtractor graphics, ItemRouting.Route route, int left, int top, int mouseX, int mouseY) {
        // Over the list's rows.
        graphics.fill(left + x + 1, top + y + 1, left + x + width - 1, top + y + height - 1, LIST);
        int hovered = at(mouseX - left, mouseY - top);
        PartFilter filter = route.filter();
        for (int i = 0; i < PartFilter.SIZE; i++) {
            int sx = left + gridX() + i % 3 * SLOT, sy = top + gridY() + i / 3 * SLOT;
            RackScreen.filterBox(graphics, sx, sy, SLOT, hovered == i);
            ItemStack entry = filter.entries().get(i);
            if (!entry.isEmpty()) {
                graphics.item(entry, sx + 1, sy + 1);
            }
        }
        for (int option = 0; option < ItemRouting.OPTIONS; option++) {
            int bx = left + optionsX(), by = top + gridY() + option * OPTIONS_STEP;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered == PartFilter.SIZE + option ? PartScreens.BUTTON_HOVER : PartScreens.BUTTON, bx, by,
                    PartScreens.BUTTON_SIZE, PartScreens.BUTTON_SIZE);
            graphics.item(new ItemStack(switch (option) {
                case ItemRouting.OPTION_DENY -> filter.deny() ? Items.BARRIER : Items.PAPER;
                case ItemRouting.OPTION_TAGS -> Items.NAME_TAG;
                default -> Items.ENCHANTED_BOOK;
            }), bx + 1, by + 1);
            if (option != ItemRouting.OPTION_DENY && !(option == ItemRouting.OPTION_TAGS ? filter.tags() : filter.components())) {
                // Off: dimmed, as on a port.
                graphics.fill(bx + 1, by + 1, bx + 17, by + 17, 0x99303030);
            }
        }
        for (int option = 0; option < shareButtons(route); option++) {
            int bx = left + gridX() + option * SHARE_STEP, by = top + shareY();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered == SHARE_HIT + option ? PartScreens.BUTTON_HOVER : PartScreens.BUTTON, bx, by,
                    SHARE_SIZE, SHARE_SIZE);
            graphics.pose().pushMatrix();
            graphics.pose().translate(bx + 1, by + 1);
            graphics.pose().scale(0.75F, 0.75F);
            graphics.item(shareIcon(route, option), 0, 0);
            graphics.pose().popMatrix();
            if (!shareOn(route, option)) {
                graphics.fill(bx + 1, by + 1, bx + SHARE_SIZE - 1, by + SHARE_SIZE - 1, 0x99303030);
            }
        }
    }

    // In the labels' pose: the route's endpoints as a heading.
    void extractLabels(GuiGraphicsExtractor graphics, Component heading) {
        graphics.text(panel.font(), panel.font().substrByWidth(heading, width - 4).getString(), x + 2, y + 3, RackScreen.TEXT, false);
    }

    void extractTooltip(GuiGraphicsExtractor graphics, ItemRouting.Route route, int left, int top, int mouseX, int mouseY) {
        int hovered = at(mouseX - left, mouseY - top);
        PartFilter filter = route.filter();
        if (hovered >= SHARE_HIT) {
            if (hovered - SHARE_HIT < shareButtons(route)) {
                graphics.setComponentTooltipForNextFrame(panel.font(), List.of(shareTip(route, hovered - SHARE_HIT),
                        Component.translatable("gui.encodedlogistics.route.share_hint").withColor(RackScreen.TEXT_DISABLED)), mouseX, mouseY);
            }
        } else if (hovered >= PartFilter.SIZE) {
            graphics.setTooltipForNextFrame(Component.translatable(switch (hovered - PartFilter.SIZE) {
                case ItemRouting.OPTION_DENY -> filter.deny() ? "gui.encodedlogistics.port.deny" : "gui.encodedlogistics.port.allow";
                case ItemRouting.OPTION_TAGS -> filter.tags() ? "gui.encodedlogistics.port.tags.on" : "gui.encodedlogistics.port.tags.off";
                default -> filter.components() ? "gui.encodedlogistics.port.components.on" : "gui.encodedlogistics.port.components.off";
            }), mouseX, mouseY);
        } else if (hovered >= 0) {
            ItemStack entry = filter.entries().get(hovered);
            List<Component> lines = new ArrayList<>();
            if (!entry.isEmpty()) {
                lines.add(entry.getHoverName());
            }
            lines.add(Component.translatable("gui.encodedlogistics.route.entry_hint").withColor(RackScreen.TEXT_DISABLED));
            graphics.setComponentTooltipForNextFrame(panel.font(), lines, mouseX, mouseY);
        }
    }

    // A route's filter in a line: "Allow list: Iron Ingot, Coal and 2 more", or what an empty one does.
    static Component summary(PartFilter filter) {
        List<ItemStack> entries = filter.nonEmpty();
        if (entries.isEmpty()) {
            return Component.translatable(filter.deny() ? "gui.encodedlogistics.route.deny_empty" : "gui.encodedlogistics.route.allow_empty");
        }
        Component items = entries.size() <= 2 ? join(entries) : Component.translatable("gui.encodedlogistics.route.more", join(entries.subList(0, 2)),
                entries.size() - 2);
        return Component.translatable(filter.deny() ? "gui.encodedlogistics.route.deny" : "gui.encodedlogistics.route.allow", items);
    }

    private static Component join(List<ItemStack> entries) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(entries.get(i).getHoverName());
        }
        return text;
    }

    // JEI drop targets on the routes' row boxes (screen x, the first row's y): each adds to its route's first free entry.
    static List<RackScreen.GhostTarget> rowTargets(RackScreen.Panel panel, List<ItemRouting.Route> routes, int rows, int x, int y, int rowHeight,
            int setAction) {
        List<RackScreen.GhostTarget> targets = new ArrayList<>();
        for (int i = 0; i < Math.min(rows, routes.size()); i++) {
            int free = firstFree(routes.get(i).filter());
            if (free >= 0) {
                int entry = i * PartFilter.SIZE + free;
                targets.add(new RackScreen.GhostTarget(x, y + i * rowHeight, 14, 14, stack -> panel.sendItem(setAction, entry, stack)));
            }
        }
        return targets;
    }

    private static int firstFree(PartFilter filter) {
        for (int i = 0; i < PartFilter.SIZE; i++) {
            if (filter.entries().get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    // --- Input ---

    // A click at a panel-relative point while open; true when it's the editor's.
    boolean mouseClicked(double px, double py, int button) {
        if (px < x || px >= x + width || py < y || py >= y + height) {
            return false;
        }
        int hit = at(px, py);
        if (hit >= SHARE_HIT) {
            ItemRouting.Route current = currentRoute;
            if (button == InputConstants.MOUSE_BUTTON_LEFT && current != null && hit - SHARE_HIT < shareButtons(current)) {
                panel.send(shareAction, route * ItemRouting.SHARE_OPTIONS + hit - SHARE_HIT, "");
            }
        } else if (hit >= PartFilter.SIZE) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                panel.send(optionAction, route * ItemRouting.OPTIONS + hit - PartFilter.SIZE, "");
            }
        } else if (hit >= 0) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                // Right-click clears it whatever is carried (an empty stack names air).
                panel.sendItem(setAction, route * PartFilter.SIZE + hit, ItemStack.EMPTY);
            } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                panel.send(setAction, route * PartFilter.SIZE + hit, "");
            }
        }
        return true;
    }

    // JEI drop targets while open: the entries.
    List<RackScreen.GhostTarget> ghostTargets(int left, int top) {
        List<RackScreen.GhostTarget> targets = new ArrayList<>();
        for (int i = 0; i < PartFilter.SIZE; i++) {
            int entry = route * PartFilter.SIZE + i;
            targets.add(new RackScreen.GhostTarget(left + gridX() + i % 3 * SLOT, top + gridY() + i / 3 * SLOT, SLOT, SLOT,
                    stack -> panel.sendItem(setAction, entry, stack)));
        }
        return targets;
    }
}
