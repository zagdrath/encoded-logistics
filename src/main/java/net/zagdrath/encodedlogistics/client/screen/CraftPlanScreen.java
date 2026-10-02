/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.net.CraftPlanPayload;
import net.zagdrath.encodedlogistics.net.CraftRequestPayload;

// The crafting plan (screens/craft_plan.json): the ingredient tree (click the arrow to fold a branch) with, for each
// item, how many are in storage (Have), will be made (Make) and are missing (Miss); how many are missing, top right;
// the Scheduler to use (click to cycle: Auto or one of the network's); Start (only with nothing missing and a Scheduler
// with room for the job) and Cancel, back to the terminal.
public class CraftPlanScreen extends Screen {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/craft_plan.png");
    private static final Identifier EXPAND = EncodedLogistics.id("common/tree_expand"), COLLAPSE = EncodedLogistics.id("common/tree_collapse"),
            HIGHLIGHT = EncodedLogistics.id("common/row_highlight"), THUMB = EncodedLogistics.id("controller/scroll_thumb"),
            THUMB_DISABLED = EncodedLogistics.id("controller/scroll_thumb_disabled");
    private static final int WIDTH = 220, HEIGHT = 196, TREE_X = 10, TREE_Y = 44, ROWS = 6, ROW = 18, INDENT = 8, MAX_INDENT = 6;
    private static final int HAVE_X = 112, MAKE_X = 142, MISS_X = 172, HEADER_Y = 31, TREE_WIDTH = 192;
    private static final int SCROLL_X = 205, SCROLL_Y = 29, SCROLL_H = 130, THUMB_W = 6, THUMB_H = 15;
    private static final int BUTTON_Y = 168, BUTTON_H = 18, SCHEDULER_X = 8, SCHEDULER_W = 104, START_X = 118, CANCEL_X = 166, SMALL_W = 46;

    private final Screen terminal;
    private final AccessTerminalMenu menu;
    private CraftPlanPayload plan;
    private final Set<Integer> collapsed = new HashSet<>();
    private int scroll, scheduler = -1, left, top;
    private boolean waiting;

    public CraftPlanScreen(Screen terminal, AccessTerminalMenu menu, CraftPlanPayload plan) {
        super(Component.translatable("gui.encodedlogistics.craft.plan"));
        this.terminal = terminal;
        this.menu = menu;
        this.plan = plan;
    }

    public int containerId() {
        return menu.containerId;
    }

    public Screen terminal() {
        return terminal;
    }

    public void update(CraftPlanPayload plan) {
        if (plan.lines().size() != this.plan.lines().size()) {
            collapsed.clear();
        }
        this.plan = plan;
        waiting = false;
        scroll = Math.min(scroll, maxScroll());
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    // --- The tree ---

    private List<CraftPlanner.Line> lines() {
        return plan.lines();
    }

    private boolean hasChildren(int index) {
        return index + 1 < lines().size() && lines().get(index + 1).depth() > lines().get(index).depth();
    }

    // The lines shown: everything but what's under a folded line.
    private List<Integer> visible() {
        List<Integer> shown = new ArrayList<>();
        int hideDeeperThan = Integer.MAX_VALUE;
        for (int i = 0; i < lines().size(); i++) {
            int depth = lines().get(i).depth();
            if (depth > hideDeeperThan) {
                continue;
            }
            hideDeeperThan = Integer.MAX_VALUE;
            shown.add(i);
            if (collapsed.contains(i)) {
                hideDeeperThan = depth;
            }
        }
        return shown;
    }

    private int maxScroll() {
        return Math.max(0, visible().size() - ROWS);
    }

    private int rowAt(double mouseX, double mouseY) {
        double y = mouseY - top - TREE_Y;
        if (mouseX < left + TREE_X || mouseX >= left + TREE_X + TREE_WIDTH || y < 0 || y >= ROWS * ROW) {
            return -1;
        }
        return (int) y / ROW;
    }

    private static int indent(int depth) {
        return Math.min(depth, MAX_INDENT) * INDENT;
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F, WIDTH, HEIGHT, 256, 256);
        graphics.text(font, title, left + 8, top + 5, PartScreens.TEXT, false);
        Component status = status();
        graphics.text(font, status, left + 212 - font.width(status), top + 18, plan.canStart() ? PartScreens.ACCENT : PartScreens.ERROR, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.craft.stored"), left + HAVE_X, top + HEADER_Y, PartScreens.TEXT_MUTED, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.craft.to_craft"), left + MAKE_X, top + HEADER_Y, PartScreens.TEXT_MUTED, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.craft.missing"), left + MISS_X, top + HEADER_Y, PartScreens.TEXT_MUTED, false);

        List<Integer> shown = visible();
        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < ROWS && scroll + row < shown.size(); row++) {
            int index = shown.get(scroll + row);
            CraftPlanner.Line line = lines().get(index);
            int y = top + TREE_Y + row * ROW, x = left + TREE_X + indent(line.depth());
            if (row == hovered) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT, 200, 18, 0, 0, left + TREE_X, y, TREE_WIDTH, ROW);
            }
            if (hasChildren(index)) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, collapsed.contains(index) ? EXPAND : COLLAPSE, x, y + 5, 7, 7);
            }
            graphics.item(line.key().stack(), x + 8, y + 1);
            int nameX = x + 26, room = left + HAVE_X - 4 - nameX;
            if (room > 8) {
                graphics.text(font, font.plainSubstrByWidth(line.key().stack().getHoverName().getString(), room), nameX, y + 5, PartScreens.TEXT, false);
            }
            number(graphics, line.have(), left + HAVE_X, y + 5, PartScreens.TEXT);
            number(graphics, line.make(), left + MAKE_X, y + 5, PartScreens.ACCENT);
            number(graphics, line.missing(), left + MISS_X, y + 5, PartScreens.ERROR);
        }
        int max = maxScroll();
        int thumbY = top + SCROLL_Y + (max == 0 ? 0 : Math.round((float) scroll * (SCROLL_H - THUMB_H) / max));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, max == 0 ? THUMB_DISABLED : THUMB, left + SCROLL_X, thumbY, THUMB_W, THUMB_H);

        PartScreens.wideButton(graphics, font, left + SCHEDULER_X, top + BUTTON_Y, SCHEDULER_W, BUTTON_H, schedulerText(), !plan.schedulers().isEmpty(),
                mouseX, mouseY);
        PartScreens.wideButton(graphics, font, left + START_X, top + BUTTON_Y, SMALL_W, BUTTON_H, Component.translatable("gui.encodedlogistics.craft.start"),
                plan.canStart() && !waiting, mouseX, mouseY);
        PartScreens.wideButton(graphics, font, left + CANCEL_X, top + BUTTON_Y, SMALL_W, BUTTON_H, Component.translatable("gui.encodedlogistics.craft.cancel"),
                true, mouseX, mouseY);
    }

    private void number(GuiGraphicsExtractor graphics, long value, int x, int y, int color) {
        if (value > 0) {
            graphics.text(font, AbstractTerminalScreen.abbreviate(value), x, y, color, false);
        }
    }

    private Component status() {
        if (plan.lines().isEmpty()) {
            return Component.translatable("gui.encodedlogistics.terminal.offline");
        }
        if (plan.missing() > 0) {
            return Component.translatable("gui.encodedlogistics.craft.missing_count", plan.missing());
        }
        if (plan.schedulers().isEmpty()) {
            return Component.translatable("gui.encodedlogistics.craft.no_scheduler");
        }
        if (!plan.room()) {
            return Component.translatable("gui.encodedlogistics.craft.no_room");
        }
        return Component.translatable("gui.encodedlogistics.craft.ready");
    }

    private Component schedulerText() {
        if (scheduler < 0 || scheduler >= plan.schedulers().size()) {
            return Component.translatable("gui.encodedlogistics.craft.scheduler.auto");
        }
        BlockPos pos = plan.schedulers().get(scheduler);
        return Component.translatable("gui.encodedlogistics.craft.scheduler", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int row = rowAt(mouseX, mouseY);
        List<Integer> shown = visible();
        if (row >= 0 && scroll + row < shown.size()) {
            graphics.setTooltipForNextFrame(font, lines().get(shown.get(scroll + row)).key().stack(), mouseX, mouseY);
            return;
        }
        String[] columns = { "stored", "to_craft", "missing" };
        int[] xs = { HAVE_X, MAKE_X, MISS_X };
        for (int i = 0; i < 3; i++) {
            if (PartScreens.over(mouseX, mouseY, left + xs[i], top + HEADER_Y - 1, 28, 10)) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.craft." + columns[i] + ".tooltip"), mouseX, mouseY);
            }
        }
        if (PartScreens.over(mouseX, mouseY, left + START_X, top + BUTTON_Y, SMALL_W, BUTTON_H)) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.craft.memory", String.format(Locale.ROOT, "%,d", plan.memory())), mouseX, mouseY);
        }
    }

    // --- Input ---

    private void request(boolean start) {
        waiting = true;
        ClientPacketDistributor.sendToServer(new CraftRequestPayload(menu.containerId, plan.key(), plan.amount(), scheduler, start));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            int row = rowAt(mx, my);
            List<Integer> shown = visible();
            if (row >= 0 && scroll + row < shown.size()) {
                int index = shown.get(scroll + row);
                int x = left + TREE_X + indent(lines().get(index).depth());
                if (hasChildren(index) && mx >= x && mx < x + 8) {
                    if (!collapsed.remove(index)) {
                        collapsed.add(index);
                    }
                    scroll = Math.min(scroll, maxScroll());
                }
                return true;
            }
            if (PartScreens.over(mx, my, left + SCHEDULER_X, top + BUTTON_Y, SCHEDULER_W, BUTTON_H) && !plan.schedulers().isEmpty()) {
                scheduler = scheduler + 1 >= plan.schedulers().size() ? -1 : scheduler + 1;
                request(false);
                return true;
            }
            if (PartScreens.over(mx, my, left + START_X, top + BUTTON_Y, SMALL_W, BUTTON_H) && plan.canStart() && !waiting) {
                request(true);
                return true;
            }
            if (PartScreens.over(mx, my, left + CANCEL_X, top + BUTTON_Y, SMALL_W, BUTTON_H)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(terminal);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
