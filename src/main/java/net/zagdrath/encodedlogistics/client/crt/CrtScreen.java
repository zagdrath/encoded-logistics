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

// The Terminal Desk's green screen (HANDOFF 3): an 80 x 24 text terminal filling the window, drawn character by
// character from the terminal font sheet (6 x 10 cells) with tall pixels - each virtual pixel 2k wide and 3k tall, so
// the 520 x 260 virtual glass (the 480 x 240 text and its margin) is 4:3 - in real screen pixels, crisp. Passes: the
// phosphor's background, the glow (pre-blurred glyphs at 45%), the text, a scanline under every virtual row, the
// vignette, the bezel.
//
// Every screen has the same frame: id, title and system name, the date and time, its body, a prompt and the command
// line ("===> "), the message line and its function keys. Keys: typing goes to the focused field; Tab / Shift+Tab and
// Up / Down move between fields; Enter submits (the command line first, when there's anything on it); PageUp / PageDown
// roll lists; F3 exits, F4 prompts, F5 refreshes, F9 opens Command Entry, F11 sorts, F12 (or Esc) goes back, Shift+F12
// (F24) shows more keys and the phosphor. Clicking a function key presses it.
public class CrtScreen extends Screen implements MenuAccess<TerminalDeskMenu> {
    private static final Identifier FONT = EncodedLogistics.id("textures/font/terminal.png"), GLOW = EncodedLogistics.id("textures/font/terminal_glow.png"),
            BEZEL = EncodedLogistics.id("textures/gui/crt/bezel.png"), VIGNETTE = EncodedLogistics.id("textures/gui/crt/vignette.png");
    private static final int VW = 520, VH = 260, MARGIN_X = 20, MARGIN_Y = 10, CW = 6, CH = 10, BEZEL_PX = 16, HISTORY = 500;

    // A phosphor's colours (screens/crt/phosphor.json).
    record Palette(int normal, int bright, int dim, int bg, int glow) {
        static final Palette GREEN = new Palette(0xFF28D25A, 0xFFDAFFE4, 0xFF167A34, 0xFF020904, 0xFF33F06A);
    }

    // A line of Command Entry's history.
    record HistoryLine(String text, byte attr) {}

    private final TerminalDeskMenu menu;
    private final Deque<CrtPanel> panels = new ArrayDeque<>();
    private final CrtGrid grid = new CrtGrid();
    final CrtField command = new CrtField(21, 5, 72, "");
    private @Nullable CrtField focused;
    private @Nullable Component message;
    final List<HistoryLine> history = new ArrayList<>();
    final List<String> commands = new ArrayList<>();
    String network = "", user = "";
    boolean firewall, signedOn;
    private Palette palette = Palette.GREEN;
    private int ticks;
    // This frame's layout, in real pixels: the glass's corner and the virtual pixel's size.
    private int glassX, glassY, sx = 2, sy = 3;

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
        palette = loadPalette(TerminalSettings.phosphor());
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
        panels.push(panel);
        focusFirst();
        panel.shown();
    }

    // Back one screen (F12); off the main menu, exit.
    void back() {
        if (panels.size() <= 1) {
            onClose();
            return;
        }
        panels.pop();
        focusFirst();
        current().shown();
    }

    void replace(CrtPanel panel) {
        panels.pop();
        push(panel);
    }

    private List<CrtField> focusable() {
        List<CrtField> list = new ArrayList<>(current().fields);
        list.add(command);
        return list;
    }

    void focusFirst() {
        focused = focusable().getFirst();
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
        ClientPacketDistributor.sendToServer(new CrtRequestPayload(menu.containerId, kind, text.length() > 256 ? text.substring(0, 256) : text));
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
        if (response.kind() == TerminalService.QUERY && response.topic().equals("info")) {
            List<TerminalLine> lines = response.lines();
            network = lines.size() > 0 ? lines.get(0).text() : "";
            firewall = lines.size() > 1 && lines.get(1).text().equals("1");
            user = lines.size() > 2 ? lines.get(2).text() : "";
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
        int width = minecraft.getWindow().getWidth(), height = minecraft.getWindow().getHeight();
        layout(width, height);
        int glassW = VW * sx, glassH = VH * sy;
        graphics.pose().pushMatrix();
        graphics.pose().scale(1 / scale, 1 / scale);
        graphics.fill(0, 0, width, height, 0xFF000000);
        graphics.fill(glassX, glassY, glassX + glassW, glassY + glassH, palette.bg());
        int textX = glassX + MARGIN_X * sx, textY = glassY + MARGIN_Y * sy;
        int glowColor = (0x73 << 24) | (palette.glow() & 0xFFFFFF);
        // Glow, then text.
        for (int row = 0; row < CrtGrid.ROWS; row++) {
            for (int col = 0; col < CrtGrid.COLS; col++) {
                char c = grid.chars[row][col];
                if (c != ' ' && !grid.reverse[row][col]) {
                    int i = c - 32;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, GLOW, textX + col * CW * sx - 2 * sx, textY + row * CH * sy - 2 * sy, (i % 16) * 10,
                            (i / 16) * 14, 10 * sx, 14 * sy, 10, 14, 160, 84, glowColor);
                }
            }
        }
        for (int row = 0; row < CrtGrid.ROWS; row++) {
            for (int col = 0; col < CrtGrid.COLS; col++) {
                int x = textX + col * CW * sx, y = textY + row * CH * sy;
                int color = color(grid.attrs[row][col]);
                char c = grid.chars[row][col];
                if (grid.reverse[row][col]) {
                    graphics.fill(x, y, x + CW * sx, y + CH * sy, color);
                    color = palette.bg();
                }
                if (c != ' ') {
                    int i = c - 32;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, FONT, x, y, (i % 16) * CW, (i / 16) * CH, CW * sx, CH * sy, CW, CH, 96, 60, color);
                }
                if (grid.underline[row][col]) {
                    graphics.fill(x, y + (CH - 1) * sy, x + CW * sx, y + CH * sy, color);
                }
            }
        }
        // The cursor: a block in the focused field, blinking.
        if (focused != null && ticks / 10 % 2 == 0) {
            int col = Math.min(focused.col + focused.cursor, focused.col + focused.length - 1);
            int x = textX + col * CW * sx, y = textY + focused.row * CH * sy;
            graphics.fill(x, y + sy, x + 5 * sx, y + 8 * sy, palette.bright());
        }
        // Scanlines: the bottom of every virtual row, a third of it.
        int line = Math.max(1, sy / 3);
        for (int v = 0; v < VH; v++) {
            int y = glassY + v * sy + sy - line;
            graphics.fill(glassX, y, glassX + glassW, y + line, 0x34000000);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, glassX, glassY, 0, 0, glassW, glassH, 256, 192, 256, 192);
        bezel(graphics, glassX - BEZEL_PX, glassY - BEZEL_PX, glassW + 2 * BEZEL_PX, glassH + 2 * BEZEL_PX);
        graphics.pose().popMatrix();
    }

    // The scale (HANDOFF 3): k = the largest whole number the glass fits at (2k x 3k a virtual pixel) inside the
    // bezel; if even k = 1 doesn't, 1 x 2.
    private void layout(int width, int height) {
        int k = Math.max(1, Math.min((width - 2 * BEZEL_PX) / (VW * 2), (height - 2 * BEZEL_PX) / (VH * 3)));
        if (VW * 2 > width - 2 * BEZEL_PX || VH * 3 > height - 2 * BEZEL_PX) {
            sx = 1;
            sy = 2;
        } else {
            sx = 2 * k;
            sy = 3 * k;
        }
        glassX = (width - VW * sx) / 2;
        glassY = (height - VH * sy) / 2;
    }

    private int color(byte attr) {
        return attr == CrtGrid.BRIGHT ? palette.bright() : attr == CrtGrid.DIM ? palette.dim() : palette.normal();
    }

    // The 9-slice bezel (64 x 64, 16-pixel borders) round the glass.
    private static void bezel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        int b = BEZEL_PX;
        slice(graphics, x, y, b, b, 0, 0);
        slice(graphics, x + w - b, y, b, b, 48, 0);
        slice(graphics, x, y + h - b, b, b, 0, 48);
        slice(graphics, x + w - b, y + h - b, b, b, 48, 48);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL, x + b, y, 16, 0, w - 2 * b, b, 32, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL, x + b, y + h - b, 16, 48, w - 2 * b, b, 32, 16, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL, x, y + b, 0, 16, b, h - 2 * b, 16, 32, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL, x + w - b, y + b, 48, 16, b, h - 2 * b, 16, 32, 64, 64);
    }

    private static void slice(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int u, int v) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEZEL, x, y, u, v, w, h, w, h, 64, 64);
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
            grid.put(22, 1, message.getString(), CrtGrid.BRIGHT);
        }
        grid.put(23, 1, panel.keys(), CrtGrid.BRIGHT);
        for (CrtField field : panel.fields) {
            field.draw(grid);
        }
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

    void setPhosphor(String name) {
        TerminalSettings.phosphor(name);
        palette = loadPalette(name);
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
            back();
            return true;
        }
        // F1-F12 (shifted: F13-F24), and F13-F24 themselves.
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
            functionKey(key - InputConstants.KEY_F1 + 1 + (shift ? 12 : 0));
            return true;
        }
        if (key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24) {
            functionKey(key - InputConstants.KEY_F13 + 13);
            return true;
        }
        if (event.isConfirmation()) {
            submit();
            return true;
        }
        List<CrtField> fields = focusable();
        int at = focused != null ? fields.indexOf(focused) : -1;
        if (key == InputConstants.KEY_TAB || event.isDown() || event.isUp()) {
            boolean back = key == InputConstants.KEY_TAB ? shift : event.isUp();
            focused = fields.get(Math.floorMod(at + (back ? -1 : 1), fields.size()));
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN) {
            current().page(key == InputConstants.KEY_PAGEUP ? -1 : 1);
            return true;
        }
        if (focused == null) {
            return true;
        }
        if (event.isPaste()) {
            for (char c : minecraft.keyboardHandler.getClipboard().toCharArray()) {
                if (c >= 32 && c < 127) {
                    focused.type(c);
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
            focused.cursor = focused.value.length();
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int c = event.codepoint();
        if (focused != null && c >= 32 && c < 127) {
            focused.type((char) c);
        }
        return true;
    }

    // F3 exit, F4 prompt, F5 refresh, F9 Command Entry, F11 sort, F12 back, F13 clear, F24 more keys.
    void functionKey(int f) {
        switch (f) {
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

    // Enter: the command line first (a menu's option, or a command), else the screen.
    private void submit() {
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
        double px = mouseX * scale - glassX - MARGIN_X * sx, py = mouseY * scale - glassY - MARGIN_Y * sy;
        int col = (int) Math.floor(px / (CW * sx)), row = (int) Math.floor(py / (CH * sy));
        return col >= 0 && col < CrtGrid.COLS && row >= 0 && row < CrtGrid.ROWS ? new int[] { row, col } : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int[] cell = cell(event.x(), event.y());
        if (cell == null) {
            return true;
        }
        int row = cell[0], col = cell[1];
        if (row == 23) {
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
        current().click(row, col, doubleClick);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        current().page(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }
}
