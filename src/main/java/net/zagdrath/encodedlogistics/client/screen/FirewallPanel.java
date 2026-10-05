/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;

// The Firewall's panel (screens/rack/firewall.json): the default policy (click to cycle, right-click back), the players
// with their five permissions (click a toggle: inherit, on, off; right-click a row to remove the player), and a name
// field to add an online player (Tab completes, Enter or Add). The owner heads the list with everything on. Only players
// allowed to build on the network may change it; for anyone else it's shown dimmed.
public class FirewallPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/firewall.png");
    private static final Identifier HEAD = EncodedLogistics.id("rack/firewall/head_placeholder");
    private static final Identifier[] TOGGLES = { EncodedLogistics.id("rack/firewall/toggle_inherit"), EncodedLogistics.id("rack/firewall/toggle_on"),
            EncodedLogistics.id("rack/firewall/toggle_off") };
    private static final int POLICY_X = 8, POLICY_Y = 18, POLICY_W = 160, POLICY_H = 14;
    // The list is the inset at (8, 36, 160, 108): a header row, then seven rows of 13 from y 51; the five toggles end at its
    // inner right edge (x 165), each header icon over its toggle.
    private static final int LIST_X = 10, LIST_Y = 51, ROWS = 7, ROW_H = 13, PERM_X = 101, PERM_STEP = 11, HEADER_Y = 41, TOGGLE = 9;
    private static final int FIELD_X = 10, FIELD_Y = 151, FIELD_W = 106, ADD_X = 122, ADD_Y = 148, ADD_W = 46, ADD_H = 14;

    // A row: the owner (fixed) or a listed player.
    private record Row(UUID id, String name, byte @Nullable [] permissions) {}

    private @Nullable EditBox name;
    private int scroll;

    public FirewallPanel(RackScreen screen) {
        super(screen);
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    @Override
    protected void init() {
        name = new EditBox(font(), screen.left() + FIELD_X + 3, screen.top() + FIELD_Y + 1, FIELD_W - 6, 9,
                Component.translatable("gui.encodedlogistics.firewall.player_name"));
        name.setBordered(false);
        name.setMaxLength(16);
        name.setTextColor(RackScreen.TEXT);
        name.setHint(Component.translatable("gui.encodedlogistics.firewall.player_name").withColor(RackScreen.TEXT_DISABLED));
        screen.addPanelWidget(name);
    }

    @Override
    protected void removed() {
        if (name != null) {
            screen.removePanelWidget(name);
        }
    }

    private @Nullable FirewallDevice firewall() {
        return device(FirewallDevice.class);
    }

    private boolean canEdit() {
        ValueInput data = data();
        return data != null && data.getBooleanOr("can_edit", false);
    }

    private List<Row> rows(@Nullable FirewallDevice firewall) {
        List<Row> rows = new ArrayList<>();
        if (firewall == null) {
            return rows;
        }
        if (firewall.owner() != null) {
            rows.add(new Row(firewall.owner(), firewall.ownerName(), null));
        }
        for (FirewallDevice.Entry entry : firewall.entries()) {
            rows.add(new Row(entry.id(), entry.name(), entry.permissions()));
        }
        return rows;
    }

    private int maxScroll(List<Row> rows) {
        return Math.max(0, rows.size() - ROWS);
    }

    // --- Drawing ---

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        FirewallDevice firewall = firewall();
        boolean editable = canEdit();
        Component policy = Component.translatable("gui.encodedlogistics.firewall.default",
                firewall != null ? firewall.policy().label() : Component.literal("…"));
        PartScreens.wideButton(graphics, font(), x + POLICY_X, y + POLICY_Y, POLICY_W, POLICY_H, policy, editable, mouseX, mouseY);
        PartScreens.wideButton(graphics, font(), x + ADD_X, y + ADD_Y, ADD_W, ADD_H, Component.translatable("gui.encodedlogistics.firewall.add"),
                editable, mouseX, mouseY);
        for (RackPermission permission : RackPermission.values()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EncodedLogistics.id("rack/firewall/perm_" + permission.key()),
                    x + PERM_X + PERM_STEP * permission.ordinal() + 1, y + HEADER_Y, 8, 7);
        }
        List<Row> rows = rows(firewall);
        scroll = Mth.clamp(scroll, 0, maxScroll(rows));
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int rowY = y + LIST_Y + i * ROW_H;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEAD, x + LIST_X + 2, rowY + 2, 8, 8);
            for (RackPermission permission : RackPermission.values()) {
                byte value = row.permissions() == null ? FirewallDevice.ON : row.permissions()[permission.ordinal()];
                int toggleX = x + PERM_X + PERM_STEP * permission.ordinal();
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TOGGLES[Math.clamp(value, 0, 2)], toggleX, rowY + 2, TOGGLE, TOGGLE,
                        row.permissions() == null || !editable ? 0xFF9A9A9A : 0xFFFFFFFF);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font(), Component.translatable("gui.encodedlogistics.firewall.player"), LIST_X + 2, HEADER_Y, RackScreen.TEXT_MUTED, false);
        List<Row> rows = rows(firewall());
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            String text = font().plainSubstrByWidth(row.name(), PERM_X - LIST_X - 16);
            graphics.text(font(), text, LIST_X + 12, LIST_Y + i * ROW_H + 3, row.permissions() == null ? RackScreen.ACCENT : RackScreen.TEXT, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (RackPermission permission : RackPermission.values()) {
            if (screen.over(mouseX, mouseY, PERM_X + PERM_STEP * permission.ordinal(), HEADER_Y, TOGGLE, 7)) {
                graphics.setTooltipForNextFrame(permission.label(), mouseX, mouseY);
                return;
            }
        }
        List<Row> rows = rows(firewall());
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            for (RackPermission permission : RackPermission.values()) {
                if (screen.over(mouseX, mouseY, PERM_X + PERM_STEP * permission.ordinal(), LIST_Y + i * ROW_H + 2, TOGGLE, TOGGLE)) {
                    graphics.setTooltipForNextFrame(toggleText(row, permission), mouseX, mouseY);
                    return;
                }
            }
            if (row.permissions() == null && screen.over(mouseX, mouseY, LIST_X, LIST_Y + i * ROW_H, PERM_X - LIST_X - 4, ROW_H)) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.firewall.owner"), mouseX, mouseY);
                return;
            }
        }
    }

    private static Component toggleText(Row row, RackPermission permission) {
        if (row.permissions() == null) {
            return Component.translatable("gui.encodedlogistics.firewall.owner");
        }
        byte value = row.permissions()[permission.ordinal()];
        String state = value == FirewallDevice.ON ? "on" : value == FirewallDevice.OFF ? "off" : "inherit";
        // Rack access left alone follows build.
        if (value == FirewallDevice.INHERIT && permission == RackPermission.RACK) {
            state = "inherit_build";
        }
        return Component.translatable("gui.encodedlogistics.firewall.toggle." + state, permission.label());
    }

    // --- Input ---

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (!canEdit() || !left && !right) {
            return false;
        }
        if (in(x, y, POLICY_X, POLICY_Y, POLICY_W, POLICY_H)) {
            send(FirewallDevice.ACTION_CYCLE_POLICY, right ? -1 : 1, "");
            return true;
        }
        if (left && in(x, y, ADD_X, ADD_Y, ADD_W, ADD_H)) {
            add();
            return true;
        }
        List<Row> rows = rows(firewall());
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int rowY = LIST_Y + i * ROW_H;
            if (!in(x, y, LIST_X, rowY, 166 - LIST_X, ROW_H) || row.permissions() == null) {
                continue;
            }
            if (right) {
                send(FirewallDevice.ACTION_REMOVE, 0, row.id().toString());
                return true;
            }
            for (RackPermission permission : RackPermission.values()) {
                if (in(x, y, PERM_X + PERM_STEP * permission.ordinal(), rowY + 2, TOGGLE, TOGGLE)) {
                    send(FirewallDevice.ACTION_TOGGLE, permission.ordinal(), row.id().toString());
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean mouseScrolled(double x, double y, double amount) {
        if (in(x, y, LIST_X, LIST_Y, 166 - LIST_X, ROWS * ROW_H)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, maxScroll(rows(firewall())));
            return true;
        }
        return false;
    }

    // While the name field has focus: Enter adds, Tab completes, other keys type (so "e" doesn't close the screen).
    @Override
    protected boolean keyPressed(KeyEvent event) {
        if (name == null || !name.isFocused()) {
            return false;
        }
        if (event.isConfirmation()) {
            add();
            return true;
        }
        if (event.key() == InputConstants.KEY_TAB) {
            complete();
            return true;
        }
        if (event.isEscape()) {
            return false;
        }
        return name.keyPressed(event) || name.canConsumeInput();
    }

    private void add() {
        if (name != null && !name.getValue().isBlank()) {
            send(FirewallDevice.ACTION_ADD, 0, name.getValue().trim());
            name.setValue("");
        }
    }

    // The first online player whose name starts with what's typed.
    private void complete() {
        if (name == null || screen.getMinecraft().getConnection() == null) {
            return;
        }
        String typed = name.getValue().toLowerCase(Locale.ROOT);
        for (PlayerInfo info : screen.getMinecraft().getConnection().getOnlinePlayers()) {
            String candidate = info.getProfile().name();
            if (candidate.toLowerCase(Locale.ROOT).startsWith(typed) && !candidate.equalsIgnoreCase(name.getValue())) {
                name.setValue(candidate);
                return;
            }
        }
    }

    private static boolean in(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
