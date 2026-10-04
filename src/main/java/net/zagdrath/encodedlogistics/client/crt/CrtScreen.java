/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.DeskSounds;
import net.zagdrath.encodedlogistics.client.screen.TerminalSettings;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.net.CrtRequestPayload;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The Terminal Desk's green screen (HANDOFF 3): an 80 x 24 text terminal on a CRT monitor drawn over the game (which
// shows round it), character by character from the terminal font sheet (6 x 10 cells) with tall pixels (1.3 times as
// tall as wide: the 520 x 260 virtual glass - the 480 x 240 text and its margin - a little wider than 4:3), in real
// screen pixels. Passes: the monitor's case, the phosphor's background, the glow (pre-blurred glyphs at 45%), the text,
// a scanline under every virtual row, the vignette, the bezel. While it's open it takes text input (typing reaches it).
//
// Every screen has the same frame: id, title and system name, the date and time, its body, a prompt and the command
// line ("===> "), the message line and its function keys. Keys: typing goes to the focused field; Tab / Shift+Tab and
// Up / Down move between fields; Enter submits (the command line first, when there's anything on it); PageUp / PageDown
// roll lists; F3 exits, F4 prompts, F5 refreshes, F9 opens Command Entry, F11 sorts, F12 (or Esc) goes back, Shift+F12
// (F24) shows more keys and the phosphor. Clicking a function key presses it.
public class CrtScreen extends Screen implements MenuAccess<TerminalDeskMenu> {
    private static final Identifier FONT = EncodedLogistics.id("textures/font/terminal.png"), GLOW = EncodedLogistics.id("textures/font/terminal_glow.png"),
            BEZEL_TEXTURE = EncodedLogistics.id("textures/gui/crt/bezel.png"), VIGNETTE = EncodedLogistics.id("textures/gui/crt/vignette.png");
    private static final int VW = 520, VH = 260, MARGIN_X = 20, MARGIN_Y = 10, CW = 6, CH = 10, HISTORY = 500;
    // The font sheets: 16 x 7 cells of 6 x 10 (the glow sheet's of 10 x 14).
    private static final int FONT_W = 96, FONT_H = 70, GLOW_W = 160, GLOW_H = 98;

    // A phosphor's colours (screens/crt/phosphor.json).
    record Palette(int normal, int bright, int dim, int bg, int glow) {
        static final Palette GREEN = new Palette(0xFF28D25A, 0xFFDAFFE4, 0xFF167A34, 0xFF020904, 0xFF33F06A);
    }

    // A line of Command Entry's history.
    record HistoryLine(String text, byte attr) {}

    private final TerminalDeskMenu menu;
    private final Deque<CrtPanel> panels = new ArrayDeque<>();
    // Pop-up windows over the current screen, the top one first.
    private final Deque<CrtWindow> windows = new ArrayDeque<>();
    private final CrtGrid grid = new CrtGrid();
    final CrtField command = new CrtField(21, 5, 72, "");
    private @Nullable CrtField focused;
    private @Nullable Component message;
    final List<HistoryLine> history = new ArrayList<>();
    final List<String> commands = new ArrayList<>();
    String network = "", user = "";
    boolean firewall, signedOn;
    // Insert mode (the Insert key) for every field; the user's unread messages ("MW"); the system's PHOSPHOR.
    boolean insert;
    int unread;
    String systemPhosphor = "*GREEN";
    private Palette palette = Palette.GREEN;
    private int ticks;
    // This frame's layout, in real pixels: the glass's corner and a virtual pixel's size.
    private int glassX, glassY;
    private float vx = 2, vy = 2.6F;

    public CrtScreen(TerminalDeskMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    public TerminalDeskMenu getMenu() {
        return menu;
    }

    @Override
    protected void init() {
        // Typed characters only arrive while something has text input focus.
        minecraft.onTextInputFocusChange(this, true);
        borrowFunctionKeys();
        palette = loadPalette(phosphor());
        if (panels.isEmpty()) {
            push(new MainMenuPanel(this));
            send(TerminalService.QUERY, "info");
        }
    }

    // --- Panels ---

    CrtPanel current() {
        return panels.peek();
    }

    void push(CrtPanel panel) {
        windows.clear();
        panels.push(panel);
        focusFirst();
        panel.shown();
    }

    // Back one screen (F12, Esc); off the main menu, or from Sign On (it guards the rest), exit.
    void back() {
        if (panels.size() <= 1 || current() instanceof SignOnPanel) {
            onClose();
            return;
        }
        windows.clear();
        panels.pop();
        focusFirst();
        current().shown();
    }

    // Off this screen to the one under it, never exiting (Sign On, done).
    void leave() {
        if (panels.size() > 1) {
            panels.pop();
            focusFirst();
            current().shown();
        }
    }

    void replace(CrtPanel panel) {
        panels.pop();
        push(panel);
    }

    // What takes focus: the top window's fields while one's open, else the screen's (protected ones never) and the
    // command line.
    private List<CrtField> focusable() {
        List<CrtField> list = new ArrayList<>();
        CrtWindow window = windows.peek();
        for (CrtField field : window != null ? window.fields : current().fields) {
            if (!field.isProtected) {
                list.add(field);
            }
        }
        if (window == null) {
            list.add(command);
        }
        return list;
    }

    void focusFirst() {
        List<CrtField> list = focusable();
        focused = list.isEmpty() ? null : list.getFirst();
    }

    // --- Windows ---

    @Nullable CrtWindow window() {
        return windows.peek();
    }

    void openWindow(CrtWindow window) {
        windows.push(window);
        focusFirst();
    }

    void closeWindow() {
        windows.poll();
        focusFirst();
    }

    // F1: help for the field under the cursor, else the screen, in a window over it (screen 15).
    void help() {
        CrtPanel panel = current();
        String text = panel.help(panel.helpField(focused));
        openWindow(new CrtWindow(this, 5, 12, 13, 56, CrtPanel.tr("crt.encodedlogistics.help.title", panel.title())).text(text)
                .keys(CrtPanel.tr("crt.encodedlogistics.help.keys")));
    }

    // A confirmation (list deletes, ENDJOB): Enter does it, F12 goes back. f11, if any, is offered as another choice.
    void confirm(String text, Runnable confirmed, @Nullable Runnable f11) {
        openWindow(new CrtWindow(this, 7, 10, 10, 60, CrtPanel.tr("crt.encodedlogistics.confirm.title")) {
            @Override
            boolean enter() {
                screen.closeWindow();
                confirmed.run();
                return true;
            }

            @Override
            boolean functionKey(int f) {
                if (f == 11 && f11 != null) {
                    screen.closeWindow();
                    f11.run();
                    return true;
                }
                return false;
            }
        }.text(text).keys(CrtPanel.tr(f11 != null ? "crt.encodedlogistics.confirm.keys_f11" : "crt.encodedlogistics.confirm.keys")));
    }

    void focus(CrtField field) {
        focused = field;
    }

    @Nullable CrtField focused() {
        return focused;
    }

    void message(@Nullable Component message) {
        this.message = message;
    }

    void message(String text) {
        message = Component.literal(text);
    }

    // --- Server ---

    void send(int kind, String text) {
        String cut = text.length() > CrtRequestPayload.MAX_TEXT ? text.substring(0, CrtRequestPayload.MAX_TEXT) : text;
        ClientPacketDistributor.sendToServer(new CrtRequestPayload(menu.containerId, kind, cut));
    }

    // A command line: run it, and keep it and what comes back in Command Entry's history.
    void runCommand(String line) {
        commands.add(line);
        addHistory("> " + line, CrtGrid.NORMAL);
        if (line.trim().equalsIgnoreCase("clear")) {
            history.clear();
            message(Component.translatable("crt.encodedlogistics.msg.cleared"));
            return;
        }
        send(TerminalService.COMMAND, line);
    }

    void addHistory(String text, byte attr) {
        history.add(new HistoryLine(text, attr));
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
    }

    public void receive(CrtResponsePayload response) {
        if (response.unread() >= 0) {
            unread = response.unread();
        }
        if (response.kind() == TerminalService.QUERY && response.topic().equals("info")) {
            List<TerminalLine> lines = response.lines();
            network = lines.size() > 0 ? lines.get(0).text() : "";
            firewall = lines.size() > 1 && lines.get(1).text().equals("1");
            user = lines.size() > 2 ? lines.get(2).text() : "";
            if (lines.size() > 3 && !lines.get(3).text().equals(systemPhosphor)) {
                systemPhosphor = lines.get(3).text();
                palette = loadPalette(phosphor());
            }
            if (firewall && !signedOn) {
                push(new SignOnPanel(this));
            }
            return;
        }
        response.message().ifPresent(this::message);
        if (response.kind() == TerminalService.COMMAND) {
            for (TerminalLine line : response.lines()) {
                addHistory(line.text(), (byte) line.attr());
            }
            response.message().ifPresent(text -> addHistory("    " + text.getString(), CrtGrid.BRIGHT));
            if (!response.lines().isEmpty() && !(current() instanceof CommandEntryPanel)) {
                push(new CommandEntryPanel(this));
            }
        }
        current().receive(response);
    }

    public int containerId() {
        return menu.containerId;
    }

    // --- Ticking ---

    @Override
    public void tick() {
        ticks++;
        current().tick();
        if (!menu.stillValid(minecraft.player)) {
            onClose();
        }
    }

    int ticks() {
        return ticks;
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        compose();
        float scale = (float) minecraft.getWindow().getGuiScale();
        layout(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        graphics.pose().pushMatrix();
        // Real pixels.
        graphics.pose().scale(1 / scale, 1 / scale);
        housing(graphics);
        int glassW = Math.round(VW * vx), glassH = Math.round(VH * vy);
        graphics.fill(glassX, glassY, glassX + glassW, glassY + glassH, palette.bg());
        // Virtual pixels: the text and its glow.
        graphics.pose().pushMatrix();
        graphics.pose().translate(glassX, glassY);
        graphics.pose().scale(vx, vy);
        int glowColor = (0x73 << 24) | (palette.glow() & 0xFFFFFF);
        for (int row = 0; row < CrtGrid.ROWS; row++) {
            for (int col = 0; col < CrtGrid.COLS; col++) {
                char c = grid.chars[row][col];
                if (c != ' ' && !grid.reverse[row][col]) {
                    int i = CrtGrid.glyph(c);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, GLOW, MARGIN_X + col * CW - 2, MARGIN_Y + row * CH - 2, (i % 16) * 10, (i / 16) * 14, 10, 14,
                            10, 14, GLOW_W, GLOW_H, glowColor);
                }
            }
        }
        for (int row = 0; row < CrtGrid.ROWS; row++) {
            for (int col = 0; col < CrtGrid.COLS; col++) {
                int x = MARGIN_X + col * CW, y = MARGIN_Y + row * CH;
                int color = color(grid.attrs[row][col]);
                char c = grid.chars[row][col];
                if (grid.reverse[row][col]) {
                    graphics.fill(x, y, x + CW, y + CH, color);
                    color = palette.bg();
                }
                if (c != ' ') {
                    int i = CrtGrid.glyph(c);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, FONT, x, y, (i % 16) * CW, (i / 16) * CH, CW, CH, CW, CH, FONT_W, FONT_H, color);
                }
                if (grid.underline[row][col]) {
                    graphics.fill(x, y + CH - 1, x + CW, y + CH, color);
                }
            }
        }
        // The cursor: a block in the focused field, blinking.
        if (focused != null && ticks / 10 % 2 == 0) {
            int col = Math.min(focused.col + focused.cursor, focused.col + focused.length - 1);
            int x = MARGIN_X + col * CW, y = MARGIN_Y + focused.row * CH;
            graphics.fill(x, y + 1, x + 5, y + 8, palette.bright());
        }
        graphics.pose().popMatrix();
        // Scanlines: the bottom third of every virtual row.
        int line = Math.max(1, Math.round(vy / 3));
        for (int v = 1; v <= VH; v++) {
            int y = glassY + Math.round(v * vy) - line;
            graphics.fill(glassX, y, glassX + glassW, y + line, 0x30000000);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, glassX, glassY, 0, 0, glassW, glassH, 256, 192, 256, 192);
        int bezel = bezelPx();
        bezel(graphics, glassX - bezel, glassY - bezel, glassW + 2 * bezel, glassH + 2 * bezel, bezel);
        graphics.pose().popMatrix();
    }

    // The monitor fills about four fifths of the window, its pixels 1.3 times as tall as wide (the glass a little
    // wider than 4:3), centred a little above the middle; the game shows round it.
    private static final float PIXEL_ASPECT = 1.3F;
    // The housing round the glass, in glass widths: the bezel, the case's sides, top and chin.
    private static final float BEZEL = 0.025F, SIDE = 0.055F, TOP = 0.045F, CHIN = 0.10F;

    private void layout(int width, int height) {
        float caseW = VW * (1 + 2 * BEZEL + 2 * SIDE), caseH = VH * PIXEL_ASPECT + VW * (2 * BEZEL + TOP + CHIN);
        vx = Math.max(0.5F, Math.min(0.80F * width / caseW, 0.84F * height / caseH));
        vy = vx * PIXEL_ASPECT;
        int glassW = Math.round(VW * vx), glassH = Math.round(VH * vy);
        int outerH = Math.round(caseH * vx);
        glassX = (width - glassW) / 2;
        int caseTop = (height - outerH) / 2;
        glassY = caseTop + Math.round(VW * vx * (TOP + BEZEL));
    }

    private int bezelPx() {
        return Math.max(4, Math.round(VW * vx * BEZEL));
    }

    private int color(byte attr) {
        return attr == CrtGrid.BRIGHT ? palette.bright() : attr == CrtGrid.DIM ? palette.dim() : palette.normal();
    }

    // The CRT's case: a soft shadow, the beige shell with its lit top edge and dark rim, a recess round the bezel, and
    // the chin with its badge strip, two knobs and the power light.
    private void housing(GuiGraphicsExtractor graphics) {
        int glassW = Math.round(VW * vx), glassH = Math.round(VH * vy);
        float g = VW * vx;
        int bezel = bezelPx(), side = Math.round(g * SIDE), top = Math.round(g * TOP), chin = Math.round(g * CHIN);
        int x0 = glassX - bezel - side, y0 = glassY - bezel - top, x1 = glassX + glassW + bezel + side, y1 = glassY + glassH + bezel + chin;
        int shadow = Math.max(4, Math.round(g * 0.012F));
        graphics.fill(x0 + shadow, y0 + shadow * 2, x1 + shadow, y1 + shadow * 2, 0x55000000);
        int rim = Math.max(2, Math.round(g * 0.003F));
        graphics.fill(x0, y0, x1, y1, 0xFF7F786B);
        graphics.fill(x0 + rim, y0 + rim, x1 - rim, y1 - rim, 0xFFCFC8B6);
        graphics.fill(x0 + rim, y0 + rim, x1 - rim, y0 + rim * 2, 0xFFE6E0D0);
        graphics.fill(x0 + rim, y1 - rim * 3, x1 - rim, y1 - rim, 0xFFB3AC9B);
        // The recess round the bezel.
        int recess = Math.max(2, Math.round(g * 0.006F));
        graphics.fill(glassX - bezel - recess, glassY - bezel - recess, glassX + glassW + bezel + recess, glassY + glassH + bezel + recess, 0xFFA59E8E);
        // The chin: a badge strip left, two knobs and the power light right.
        int chinTop = glassY + glassH + bezel + recess, chinMid = (chinTop + y1 - rim * 3) / 2;
        int unit = Math.max(2, Math.round(g * 0.008F));
        graphics.fill(glassX, chinMid - unit / 2, glassX + Math.round(g * 0.14F), chinMid + unit / 2 + 1, 0xFFB7B09F);
        int knob = unit * 3, right = glassX + glassW;
        for (int i = 0; i < 2; i++) {
            int kx = right - knob * (5 + i * 2);
            graphics.fill(kx, chinMid - knob / 2, kx + knob, chinMid + knob / 2, 0xFF9C9584);
            graphics.fill(kx + 1, chinMid - knob / 2 + 1, kx + knob - 1, chinMid + knob / 2 - 1, 0xFFC3BCAA);
        }
        int led = unit * 2;
        graphics.fill(right - led * 2 - 2, chinMid - led / 2 - 2, right - led + 2, chinMid + led / 2 + 2, 0x4033F06A);
        graphics.fill(right - led * 2, chinMid - led / 2, right - led, chinMid + led / 2, 0xFF33F06A);
    }

    // The 9-slice bezel (64 x 64, 16-texel borders) round the glass, its borders b pixels.
    private static void bezel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int b) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x, y, 0, 0, b, b, 16, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + w - b, y, 48, 0, b, b, 16, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x, y + h - b, 0, 48, b, b, 16, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + w - b, y + h - b, 48, 48, b, b, 16, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + b, y, 16, 0, w - 2 * b, b, 32, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + b, y + h - b, 16, 48, w - 2 * b, b, 32, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x, y + b, 0, 16, b, h - 2 * b, 16, 32, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL_TEXTURE, x + w - b, y + b, 48, 16, b, h - 2 * b, 16, 32, 64, 64);
    }

    // The frame and the current screen, into the grid.
    private void compose() {
        CrtPanel panel = current();
        grid.clear();
        grid.put(0, 1, panel.id(), CrtGrid.NORMAL);
        grid.center(0, panel.title(), CrtGrid.BRIGHT);
        grid.right(0, CrtPanel.tr("crt.encodedlogistics.system", network.isEmpty() ? "*OFFLINE" : network), CrtGrid.NORMAL);
        grid.right(1, clock(), CrtGrid.NORMAL);
        panel.draw(grid);
        String prompt = panel.prompt();
        if (!prompt.isEmpty()) {
            grid.put(20, 1, prompt, CrtGrid.NORMAL);
            grid.put(21, 1, "===>", CrtGrid.NORMAL);
            command.draw(grid);
        }
        if (message != null) {
            String text = message.getString();
            // "MW" takes the line's end while messages wait.
            grid.put(22, 1, unread > 0 && text.length() > 73 ? text.substring(0, 73) : text, CrtGrid.BRIGHT);
        }
        if (unread > 0) {
            grid.put(22, 76, "MW", CrtGrid.BRIGHT);
        }
        grid.put(23, 1, panel.keys(), CrtGrid.BRIGHT);
        for (CrtField field : panel.fields) {
            field.draw(grid);
        }
        windows.descendingIterator().forEachRemaining(window -> window.draw(grid));
    }

    // The grid as last composed (tests compare it with the layouts).
    CrtGrid compose(boolean fresh) {
        if (fresh) {
            compose();
        }
        return grid;
    }

    // The game's day and time: "Day 12  14:32:07".
    private String clock() {
        if (minecraft == null || minecraft.level == null) {
            return "";
        }
        long time = minecraft.level.getOverworldClockTime();
        long day = time / 24_000 + 1, tick = time % 24_000;
        long seconds = (tick * 86_400 / 24_000 + 6 * 3_600) % 86_400;
        return String.format(Locale.ROOT, "Day %d  %02d:%02d:%02d", day, seconds / 3_600, seconds / 60 % 60, seconds % 60);
    }

    // --- Phosphor ---

    // The player's choice (green, amber, white), or *SYSVAL: the system's PHOSPHOR value for every terminal on it.
    void setPhosphor(String name) {
        TerminalSettings.phosphor(name);
        palette = loadPalette(phosphor());
    }

    // The phosphor in use: the player's own choice when they made one, else the system value's.
    String phosphor() {
        String chosen = TerminalSettings.phosphor();
        if (!chosen.equalsIgnoreCase(TerminalSettings.SYSVAL)) {
            return chosen;
        }
        return systemPhosphor.startsWith("*") ? systemPhosphor.substring(1).toLowerCase(Locale.ROOT) : systemPhosphor.toLowerCase(Locale.ROOT);
    }

    private Palette loadPalette(String name) {
        try (Reader reader = minecraft.getResourceManager().openAsReader(EncodedLogistics.id("screens/crt/phosphor.json"))) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject colours = json.has(name) ? json.getAsJsonObject(name) : json.getAsJsonObject(json.get("default").getAsString());
            return new Palette(hex(colours, "normal"), hex(colours, "bright"), hex(colours, "dim"), hex(colours, "bg"), hex(colours, "glow"));
        } catch (Exception e) {
            return Palette.GREEN;
        }
    }

    private static int hex(JsonObject json, String key) {
        return 0xFF000000 | Integer.parseInt(json.get(key).getAsString().substring(1), 16);
    }

    // --- Input ---

    @Override
    public boolean keyPressed(KeyEvent event) {
        DeskSounds.keyClack();
        int key = event.key();
        boolean shift = event.hasShiftDown();
        if (event.isEscape()) {
            if (window() != null) {
                cancelWindow();
            } else {
                back();
            }
            return true;
        }
        // Function keys act on release (keyReleased), so the release can't reach the game after an exit: F3's
        // debug overlay toggles on release.
        if (functionKey(event) > 0) {
            return true;
        }
        // Field Exit: Ctrl+Enter or the keypad's Enter clears the rest of the field and moves on.
        if (focused != null && (key == InputConstants.KEY_NUMPADENTER || event.isConfirmation() && event.hasControlDown())) {
            focused.fieldExit();
            nextField(false);
            return true;
        }
        if (event.isConfirmation()) {
            submit();
            return true;
        }
        if (key == InputConstants.KEY_TAB || event.isDown() || event.isUp()) {
            nextField(key == InputConstants.KEY_TAB ? shift : event.isUp());
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN) {
            page(key == InputConstants.KEY_PAGEUP ? -1 : 1);
            return true;
        }
        if (key == InputConstants.KEY_INSERT) {
            insert = !insert;
            return true;
        }
        if (focused == null) {
            return true;
        }
        if (event.isPaste()) {
            for (char c : minecraft.keyboardHandler.getClipboard().toCharArray()) {
                if (c >= 32 && c < 127) {
                    focused.type(c, true);
                }
            }
        } else if (key == InputConstants.KEY_BACKSPACE) {
            focused.backspace();
        } else if (key == InputConstants.KEY_DELETE) {
            focused.delete();
        } else if (event.isLeft()) {
            focused.left();
        } else if (event.isRight()) {
            focused.right();
        } else if (key == InputConstants.KEY_HOME) {
            focused.cursor = 0;
        } else if (key == InputConstants.KEY_END) {
            focused.cursor = Math.min(focused.value.length(), focused.length - 1);
        }
        return true;
    }

    // Tab / Shift+Tab, Up / Down, Field Exit and Field Advance: the next (or previous) field, the cursor at its end.
    private void nextField(boolean back) {
        List<CrtField> fields = focusable();
        if (fields.isEmpty()) {
            return;
        }
        int at = focused != null ? fields.indexOf(focused) : -1;
        focused = fields.get(Math.floorMod(at + (back ? -1 : 1), fields.size()));
        focused.cursor = Math.min(focused.value.length(), focused.length - 1);
    }

    private void page(int direction) {
        if (window() != null) {
            window().page(direction);
        } else {
            current().page(direction);
        }
    }

    // Esc / F3 / F12 on a window: it goes, the screen under it stays.
    private void cancelWindow() {
        CrtWindow window = windows.poll();
        if (window != null) {
            window.cancelled();
        }
        focusFirst();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int c = event.codepoint();
        // Field Advance: typing into a field's last position moves on to the next.
        if (focused != null && c >= 32 && c < 127 && focused.type((char) c, insert)) {
            nextField(false);
        }
        return true;
    }

    // While a window is open: F3 / F12 close it, others are its own. Else the screen's own keys first (F6 Create,
    // F10, F19 / F20...), then F1 help, F3 exit, F4 prompt, F5 refresh, F9 Command Entry, F11 sort, F12 back, F13
    // clear, F24 more keys.
    void functionKey(int f) {
        CrtWindow window = window();
        if (window != null) {
            if (f == 3 || f == 12) {
                cancelWindow();
            } else {
                window.functionKey(f);
            }
            return;
        }
        if (current().functionKey(f)) {
            return;
        }
        switch (f) {
            case 1 -> help();
            case 3 -> onClose();
            case 4 -> {
                Component prompt = focused != null ? current().prompt(focused) : null;
                if (prompt != null) {
                    message(prompt);
                }
            }
            case 5 -> {
                message((Component) null);
                current().refresh();
            }
            case 9 -> {
                if (current() instanceof CommandEntryPanel entry) {
                    entry.retrieve();
                } else {
                    push(new CommandEntryPanel(this));
                }
            }
            case 11 -> current().sort();
            case 12 -> back();
            case 13 -> {
                if (current() instanceof CommandEntryPanel) {
                    history.clear();
                }
            }
            case 24 -> {
                if (!(current() instanceof MoreKeysPanel)) {
                    push(new MoreKeysPanel(this));
                }
            }
            default -> {}
        }
    }

    // Enter: the top window's, else the command line first (a menu's option, or a command), else the screen.
    private void submit() {
        if (window() != null) {
            window().enter();
            return;
        }
        String text = command.trimmed();
        if (!text.isEmpty() && !current().prompt().isEmpty()) {
            command.set("");
            if (!current().option(text)) {
                runCommand(text);
            }
            return;
        }
        if (!current().enter()) {
            message((Component) null);
        }
    }

    // The cell under a point on the GUI, or null outside the text.
    private int @Nullable [] cell(double mouseX, double mouseY) {
        double scale = minecraft.getWindow().getGuiScale();
        double px = (mouseX * scale - glassX) / vx - MARGIN_X, py = (mouseY * scale - glassY) / vy - MARGIN_Y;
        int col = (int) Math.floor(px / CW), row = (int) Math.floor(py / CH);
        return col >= 0 && col < CrtGrid.COLS && row >= 0 && row < CrtGrid.ROWS ? new int[] { row, col } : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int[] cell = cell(event.x(), event.y());
        if (cell == null) {
            return true;
        }
        int row = cell[0], col = cell[1];
        if (row == 23 && window() == null) {
            // A function key's label: "F3=Exit" pressed.
            String keys = " " + current().keys();
            int start = keys.lastIndexOf('F', col + 1);
            if (start >= 0) {
                int end = keys.indexOf('=', start);
                if (end > start && (keys.indexOf(' ', start) < 0 || keys.indexOf(' ', start) > end)) {
                    try {
                        functionKey(Integer.parseInt(keys.substring(start + 1, end)));
                    } catch (NumberFormatException ignored) {}
                }
            }
            return true;
        }
        for (CrtField field : focusable()) {
            if (field.contains(row, col)) {
                focused = field;
                field.cursor = Math.min(field.value.length(), col - field.col);
            }
        }
        if (window() != null) {
            window().click(row, col, doubleClick);
        } else {
            current().click(row, col, doubleClick);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        page(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Fullscreen and screenshot (F11, F2) are taken before any screen sees the key: while the terminal is open, the
    // ones bound to function keys are unbound, so the terminal gets them. Given back when it closes.
    private final List<KeyMapping> borrowed = new ArrayList<>();
    private final List<InputConstants.Key> borrowedKeys = new ArrayList<>();

    private void borrowFunctionKeys() {
        if (!borrowed.isEmpty()) {
            return;
        }
        for (KeyMapping mapping : List.of(minecraft.options.keyFullscreen, minecraft.options.keyScreenshot)) {
            InputConstants.Key key = mapping.getKey();
            int code = key.getValue();
            if (key.getType() == InputConstants.Type.KEYBOARD
                    && (code >= InputConstants.KEY_F1 && code <= InputConstants.KEY_F12 || code >= InputConstants.KEY_F13 && code <= InputConstants.KEY_F24)) {
                borrowed.add(mapping);
                borrowedKeys.add(key);
                mapping.setKey(InputConstants.UNKNOWN);
            }
        }
    }

    private void returnFunctionKeys() {
        for (int i = 0; i < borrowed.size(); i++) {
            borrowed.get(i).setKey(borrowedKeys.get(i));
        }
        borrowed.clear();
        borrowedKeys.clear();
    }

    // Releases too: F3's debug overlay toggles on release. A function key's release presses it.
    @Override
    public boolean keyReleased(KeyEvent event) {
        int f = functionKey(event);
        if (f > 0) {
            functionKey(f);
        }
        return true;
    }

    // F1-F12 (shifted: F13-F24), and F13-F24 themselves; 0 for any other key.
    private static int functionKey(KeyEvent event) {
        int key = event.key();
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
            return key - InputConstants.KEY_F1 + 1 + (event.hasShiftDown() ? 12 : 0);
        }
        return key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24 ? key - InputConstants.KEY_F13 + 13 : 0;
    }

    @Override
    public void removed() {
        returnFunctionKeys();
        minecraft.onTextInputFocusChange(this, false);
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }
}
