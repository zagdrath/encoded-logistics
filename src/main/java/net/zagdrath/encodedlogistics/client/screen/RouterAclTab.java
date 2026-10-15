/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RouterAcl;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// The Router panel's ACLs tab (previews/gui_router_acls.png): the ACL shown (click for the next; right-click adds one,
// shift-right-click deletes it), what it's applied to (click the name for the next route or directly reached network,
// the state to apply it or take it off), its rules (#, match, action, hits; click to pick one) with the default last,
// and Add / Edit / Del / Up / Dn. Add and Edit open a line to type the rule in: "COAL deny out 64/m 2000/d" (match,
// then any of permit|deny, in|out|both, N/m, N/d).
final class RouterAclTab {
    private static final int ACL_Y = 36, ON_Y = 49, HEAD_Y = 62, RULES_Y = 75, ROW = 11, ROWS = 5, DEFAULT_Y = RULES_Y + ROWS * ROW;
    private static final int BUTTONS_Y = 144, BUTTON_W = 29, BUTTON_H = 13, BUTTON_STEP = 31, ON_X = 30;
    // The rules' columns: # at 12 (two digits), match at MATCH_X, action right at ACT_RIGHT, hits right at 152; each value
    // cut short of the next column (RouterPanel.GAP), the rule whole in its tooltip.
    private static final int MATCH_X = 26, ACT_RIGHT = 124;
    private static final String[] BUTTONS = {"add", "edit", "del", "up", "dn"};

    private final RouterPanel panel;
    private int aclIndex, target, scroll, selected = -1;
    private @Nullable EditBox editor;
    private boolean adding;

    RouterAclTab(RouterPanel panel) {
        this.panel = panel;
    }

    private @Nullable RouterAcl acl(RouterDevice router) {
        List<RouterAcl> acls = new ArrayList<>(router.acls().values());
        if (acls.isEmpty()) {
            return null;
        }
        aclIndex = Math.floorMod(aclIndex, acls.size());
        return acls.get(aclIndex);
    }

    // What the ACL can be applied to: the routes, then the networks the Router reaches directly.
    private record Target(String name, boolean applied) {}

    private List<Target> targets(RouterDevice router, RouterAcl acl, List<RouterPanel.TableRow> table) {
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < router.routes().size(); i++) {
            ItemRouting.Route route = router.routes().get(i);
            targets.add(new Target(Component.translatable("gui.encodedlogistics.router.acl.route", i + 1).getString(), route.acl().equals(acl.name())));
        }
        for (RouterPanel.TableRow row : table) {
            if (row.hops() == 1 && row.network() != null) {
                NetworkRef network = row.network();
                targets.add(new Target(row.name(), acl.name().equals(router.networkAcls().get(network))));
            }
        }
        return targets;
    }

    void close() {
        if (editor != null) {
            panel.screen.removePanelWidget(editor);
            editor = null;
        }
    }

    // --- Drawing ---

    void extractBackground(GuiGraphicsExtractor graphics, RouterDevice router, int mouseX, int mouseY) {
        int x = panel.screen.left(), y = panel.screen.top();
        RouterAcl acl = acl(router);
        PanelBody.scrollbar(graphics, x, y, scroll, acl == null ? 0 : acl.rules().size(), ROWS);
        if (acl != null && selected >= scroll && selected < scroll + ROWS && selected < acl.rules().size()) {
            int rowY = y + RULES_Y + (selected - scroll) * ROW - 1;
            graphics.fill(x + RouterPanel.INSET_X - 1, rowY, x + RouterPanel.INSET_RIGHT + 1, rowY + ROW, 0x40FFFFFF);
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            boolean enabled = acl != null && (i == 0 || selected >= 0 && selected < acl.rules().size());
            PartScreens.wideButton(graphics, panel.font(), x + RouterPanel.INSET_X + i * BUTTON_STEP, y + BUTTONS_Y, BUTTON_W, BUTTON_H,
                    Component.translatable("gui.encodedlogistics.router.acl." + BUTTONS[i]), enabled, mouseX, mouseY);
        }
    }

    void extractLabels(GuiGraphicsExtractor graphics, RouterDevice router, List<RouterPanel.TableRow> table, boolean byDefault) {
        graphics.text(panel.font(), Component.translatable("gui.encodedlogistics.router.acl"), RouterPanel.INSET_X, ACL_Y, RackScreen.TEXT_MUTED, false);
        RouterAcl acl = acl(router);
        if (acl == null) {
            right(graphics, Component.translatable("gui.encodedlogistics.router.acl.none").getString(), ACL_Y, RackScreen.TEXT_DISABLED);
            return;
        }
        right(graphics, PanelBody.fit(panel.font(), acl.name(), valueWidth("gui.encodedlogistics.router.acl")), ACL_Y, RackScreen.ACCENT);
        graphics.text(panel.font(), Component.translatable("gui.encodedlogistics.router.acl.on"), RouterPanel.INSET_X, ON_Y, RackScreen.TEXT_MUTED, false);
        List<Target> targets = targets(router, acl, table);
        if (targets.isEmpty()) {
            graphics.text(panel.font(), Component.translatable("gui.encodedlogistics.router.acl.nothing"), ON_X, ON_Y, RackScreen.TEXT_DISABLED, false);
        } else {
            Target shown = targets.get(Math.floorMod(target, targets.size()));
            text(graphics, shown.name(), ON_X, ON_Y, stateLeft() - RouterPanel.GAP - ON_X, RackScreen.TEXT);
            right(graphics, Component.translatable(shown.applied() ? "gui.encodedlogistics.router.acl.applied" : "gui.encodedlogistics.router.acl.not_applied")
                    .getString(), ON_Y, shown.applied() ? RackScreen.ACCENT : RackScreen.TEXT_DISABLED);
        }
        PanelBody.divider(graphics, HEAD_Y);
        graphics.text(panel.font(), "#", RouterPanel.INSET_X, HEAD_Y, RackScreen.TEXT_MUTED, false);
        text(graphics, Component.translatable("gui.encodedlogistics.router.acl.match").getString(), MATCH_X, HEAD_Y, matchWidth(), RackScreen.TEXT_MUTED);
        rightAt(graphics, Component.translatable("gui.encodedlogistics.router.acl.act").getString(), ACT_RIGHT, HEAD_Y, RackScreen.TEXT_MUTED);
        right(graphics, Component.translatable("gui.encodedlogistics.router.acl.hits").getString(), HEAD_Y, RackScreen.TEXT_MUTED);
        List<RouterAcl.Rule> rules = acl.rules();
        for (int i = 0; i < ROWS && scroll + i < rules.size(); i++) {
            if (editor != null && !adding && scroll + i == selected) {
                continue;
            }
            RouterAcl.Rule rule = rules.get(scroll + i);
            int rowY = RULES_Y + i * ROW;
            graphics.text(panel.font(), Integer.toString(scroll + i + 1), RouterPanel.INSET_X, rowY, RackScreen.TEXT, false);
            String match = matchName(rule.match()) + (rule.rate() > 0 ? " " + rule.rate() + "/m" : "") + (rule.quota() > 0 ? " " + rule.quota() + "/d" : "");
            text(graphics, match, MATCH_X, rowY, matchWidth(), RackScreen.TEXT);
            action(graphics, rule.permit(), rowY);
            right(graphics, compact(rule.hits()), rowY, RackScreen.TEXT);
        }
        if (editor != null && adding) {
            // The line being typed in is where the default shows.
            return;
        }
        graphics.text(panel.font(), "*", RouterPanel.INSET_X, DEFAULT_Y, RackScreen.TEXT_MUTED, false);
        text(graphics, Component.translatable("gui.encodedlogistics.router.acl.default").getString(), MATCH_X, DEFAULT_Y, matchWidth(), RackScreen.TEXT_MUTED);
        action(graphics, byDefault, DEFAULT_Y);
        right(graphics, compact(acl.defaultHits()), DEFAULT_Y, RackScreen.TEXT_MUTED);
    }

    private void action(GuiGraphicsExtractor graphics, boolean permit, int y) {
        rightAt(graphics, Component.translatable(permit ? "gui.encodedlogistics.router.acl.permit" : "gui.encodedlogistics.router.acl.deny").getString(),
                ACT_RIGHT, y, permit ? RackScreen.ACCENT : RackScreen.ERROR);
    }

    // The match column: up to the action column's widest word.
    private int matchWidth() {
        int act = Math.max(width("gui.encodedlogistics.router.acl.act"), Math.max(width("gui.encodedlogistics.router.acl.permit"),
                width("gui.encodedlogistics.router.acl.deny")));
        return ACT_RIGHT - act - RouterPanel.GAP - MATCH_X;
    }

    // Where "applied" / "off" starts (right-aligned at 152): the target's name stops short of it, and clicks split there.
    private int stateLeft() {
        return RouterPanel.INSET_RIGHT - Math.max(width("gui.encodedlogistics.router.acl.applied"), width("gui.encodedlogistics.router.acl.not_applied"));
    }

    // The room for a value right-aligned beside the label at key.
    private int valueWidth(String key) {
        return RouterPanel.INSET_RIGHT - RouterPanel.INSET_X - width(key) - RouterPanel.GAP * 2;
    }

    private int width(String key) {
        return panel.font().width(Component.translatable(key));
    }

    // An item's name (Coal), or the match as written (#c:fuels, *ENERGY).
    static String matchName(String match) {
        return match.startsWith("*") || match.startsWith("#") ? match : ElclItems.displayName(match);
    }

    // 310, 1.2k, 12k, 4.1M.
    static String compact(long count) {
        if (count < 1_000) {
            return Long.toString(count);
        }
        if (count < 1_000_000) {
            return count < 10_000 ? String.format(Locale.ROOT, "%.1fk", count / 1_000.0) : count / 1_000 + "k";
        }
        return count < 10_000_000 ? String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0) : count / 1_000_000 + "M";
    }

    // text in width, cut with "..." (the tooltip over it has it whole).
    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        graphics.text(panel.font(), PanelBody.fit(panel.font(), text, width), x, y, color, false);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int y, int color) {
        rightAt(graphics, text, RouterPanel.INSET_RIGHT, y, color);
    }

    private void rightAt(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(panel.font(), text, right - panel.font().width(text), y, color, false);
    }

    void extractTooltip(GuiGraphicsExtractor graphics, RouterDevice router, double x, double y, int mouseX, int mouseY) {
        RouterAcl acl = acl(router);
        if (x < RouterPanel.INSET_X || x >= RouterPanel.INSET_RIGHT) {
            return;
        }
        // The ACL's and the target's names whole above the hints (they're cut to fit beside their labels).
        if (y >= ACL_Y - 2 && y < ACL_Y + 9) {
            List<Component> lines = new ArrayList<>();
            if (acl != null) {
                lines.add(Component.literal(acl.name()));
            }
            lines.add(Component.translatable("gui.encodedlogistics.router.acl.hint.picker").withColor(acl != null ? RackScreen.TEXT_DISABLED : RackScreen.TEXT));
            Tooltips.show(graphics, panel.font(), lines, mouseX, mouseY);
        } else if (acl != null && y >= ON_Y - 2 && y < ON_Y + 9) {
            List<Target> targets = targets(router, acl, panel.table());
            List<Component> lines = new ArrayList<>();
            if (!targets.isEmpty()) {
                lines.add(Component.literal(targets.get(Math.floorMod(target, targets.size())).name()));
            }
            lines.add(Component.translatable("gui.encodedlogistics.router.acl.hint.on").withColor(lines.isEmpty() ? RackScreen.TEXT : RackScreen.TEXT_DISABLED));
            Tooltips.show(graphics, panel.font(), lines, mouseX, mouseY);
        } else if (acl != null && y >= RULES_Y && y < RULES_Y + ROWS * ROW) {
            int row = scroll + (int) ((y - RULES_Y) / ROW);
            if (row < acl.rules().size()) {
                RouterAcl.Rule rule = acl.rules().get(row);
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(rule.seq() + "  " + RouterAcl.spec(rule)));
                lines.add(Component.translatable("gui.encodedlogistics.router.acl.hits_line", String.format(Locale.ROOT, "%,d", rule.hits()))
                        .withColor(RackScreen.TEXT_MUTED));
                lines.add(Component.translatable("gui.encodedlogistics.router.acl.hint.rule").withColor(RackScreen.TEXT_DISABLED));
                Tooltips.show(graphics, panel.font(), lines, mouseX, mouseY);
            }
        } else if (editor != null && y >= RULES_Y - 2) {
            Tooltips.show(graphics, panel.font(), Component.translatable("gui.encodedlogistics.router.acl.hint.edit"), mouseX, mouseY);
        }
    }

    // --- Input ---

    boolean mouseScrolled(RouterDevice router, double amount) {
        RouterAcl acl = acl(router);
        int size = acl != null ? acl.rules().size() : 0;
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, size - ROWS));
        return true;
    }

    boolean mouseClicked(RouterDevice router, List<RouterPanel.TableRow> table, double x, double y, int button, boolean shift) {
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (editor != null && !(y >= editor.getY() - panel.screen.top() && y < editor.getY() - panel.screen.top() + 10)) {
            submit(router);
        }
        RouterAcl acl = acl(router);
        if (y >= ACL_Y - 2 && y < ACL_Y + 9 && x >= RouterPanel.INSET_X && x < RouterPanel.INSET_RIGHT) {
            if (right && shift && acl != null) {
                panel.send(RouterDevice.ACTION_ACL_DELETE, 0, acl.name());
            } else if (right) {
                panel.send(RouterDevice.ACTION_ACL_NEW, 0, "");
            } else if (left) {
                aclIndex++;
                selected = -1;
                scroll = 0;
            }
            return true;
        }
        if (acl == null) {
            return false;
        }
        if (y >= ON_Y - 2 && y < ON_Y + 9 && x >= RouterPanel.INSET_X && x < RouterPanel.INSET_RIGHT) {
            List<Target> targets = targets(router, acl, table);
            if (targets.isEmpty()) {
                return true;
            }
            if (x >= stateLeft() - RouterPanel.GAP / 2) {
                panel.send(RouterDevice.ACTION_ACL_BIND, Math.floorMod(target, targets.size()), acl.name());
            } else {
                target = Math.floorMod(target + (right ? -1 : 1), targets.size());
            }
            return true;
        }
        if (y >= RULES_Y && y < RULES_Y + ROWS * ROW && x >= RouterPanel.INSET_X && x < RouterPanel.INSET_RIGHT && left) {
            int row = scroll + (int) ((y - RULES_Y) / ROW);
            selected = row < acl.rules().size() ? row : -1;
            return true;
        }
        if (left && y >= BUTTONS_Y && y < BUTTONS_Y + BUTTON_H) {
            int index = (int) ((x - RouterPanel.INSET_X) / BUTTON_STEP);
            if (x < RouterPanel.INSET_X || index >= BUTTONS.length || x - RouterPanel.INSET_X - index * BUTTON_STEP >= BUTTON_W) {
                return false;
            }
            RouterAcl.Rule rule = selected >= 0 && selected < acl.rules().size() ? acl.rules().get(selected) : null;
            switch (index) {
                case 0 -> open(false, "*ANY permit", rule);
                case 1 -> {
                    if (rule != null) {
                        open(true, RouterAcl.spec(rule), rule);
                    }
                }
                case 2 -> {
                    if (rule != null) {
                        panel.send(RouterDevice.ACTION_ACL_DEL, rule.seq(), acl.name());
                        selected = -1;
                    }
                }
                default -> {
                    if (rule != null) {
                        panel.send(index == 3 ? RouterDevice.ACTION_ACL_UP : RouterDevice.ACTION_ACL_DOWN, rule.seq(), acl.name());
                        selected = Math.clamp(selected + (index == 3 ? -1 : 1), 0, acl.rules().size() - 1);
                    }
                }
            }
            return true;
        }
        return false;
    }

    // A line to type a rule in: over the picked rule (edit), or under the rules (add).
    private void open(boolean edit, String text, RouterAcl.@Nullable Rule rule) {
        close();
        adding = !edit;
        int row = edit && selected >= scroll && selected < scroll + ROWS ? selected - scroll : ROWS;
        editor = new EditBox(panel.font(), panel.screen.left() + MATCH_X, panel.screen.top() + RULES_Y + row * ROW, RouterPanel.INSET_RIGHT - MATCH_X, 9,
                Component.translatable("gui.encodedlogistics.router.acl.edit"));
        editor.setBordered(false);
        editor.setMaxLength(48);
        editor.setTextColor(RackScreen.TEXT);
        editor.setValue(text);
        panel.screen.addPanelWidget(editor);
        editor.setFocused(true);
        panel.screen.setFocused(editor);
        editSeq = rule != null ? rule.seq() : -1;
    }

    private int editSeq = -1;

    private void submit(RouterDevice router) {
        RouterAcl acl = acl(router);
        if (editor != null && acl != null) {
            String text = acl.name() + " " + editor.getValue().trim();
            panel.send(adding ? RouterDevice.ACTION_ACL_ADD : RouterDevice.ACTION_ACL_EDIT, editSeq, text);
        }
        close();
    }

    boolean keyPressed(RouterDevice router, KeyEvent event) {
        if (editor == null) {
            return false;
        }
        if (event.isConfirmation()) {
            submit(router);
            return true;
        }
        if (event.isEscape()) {
            close();
            return true;
        }
        return editor.keyPressed(event) || editor.canConsumeInput();
    }
}
