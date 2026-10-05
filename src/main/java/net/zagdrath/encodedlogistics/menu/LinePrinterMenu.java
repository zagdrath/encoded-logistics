/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.midrange.LinePrinterBlockEntity;
import net.zagdrath.encodedlogistics.midrange.PeripheralBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Line Printer's screen: the paper and printed-output slots, then the player's inventory. Fields: the report
// (1-4) and, for 4, the spooled file (*LAST or a name); the screen shows the report's first lines and its pages
// (MachinePayloads.Info: lines, numbers [pages]), refreshed every second while it's open. Button: Print (F6).
public class LinePrinterMenu extends PeripheralMenu {
    public static final int BUTTON_PRINT = 0;
    public static final int FIELD_REPORT = 0, FIELD_FILE = 1;
    public static final int PREVIEW_LINES = 3;
    public static final int PAPER_X = 18, OUTPUT_X = 22 * 6, SLOTS_Y = 130;

    private final @Nullable LinePrinterBlockEntity printer;
    private int report = LinePrinterBlockEntity.INVENTORY;
    private String file = "*LAST";
    private List<String> shown = List.of();
    private int shownPages = -1;

    // Client constructor.
    public LinePrinterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(2), null, Opening.read(buf));
    }

    public LinePrinterMenu(int containerId, Inventory inventory, Container slots, @Nullable PeripheralBlockEntity peripheral, Opening opening) {
        super(ModMenuTypes.LINE_PRINTER.get(), containerId, inventory, slots, peripheral, opening);
        this.printer = peripheral instanceof LinePrinterBlockEntity p ? p : null;
        addSlot(new MachineSlot(slots, LinePrinterBlockEntity.PAPER, PAPER_X, SLOTS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.PAPER);
            }
        });
        addSlot(new MachineSlot(slots, LinePrinterBlockEntity.OUTPUT, OUTPUT_X, SLOTS_Y));
        addPlayerSlots(inventory);
    }

    @Override
    public void setText(int key, String text) {
        if (key == FIELD_REPORT) {
            String value = text.trim();
            report = value.length() == 1 && value.charAt(0) >= '1' && value.charAt(0) <= '4' ? value.charAt(0) - '0' : report;
        } else if (key == FIELD_FILE) {
            file = text.trim().isEmpty() ? "*LAST" : text.trim().toUpperCase(Locale.ROOT);
        }
        shownPages = -1;
        refresh();
    }

    // The report's first lines and its pages, sent when they change.
    @Override
    protected void refresh() {
        if (printer == null) {
            return;
        }
        List<String> preview = new ArrayList<>();
        int pages;
        try {
            LinePrinterBlockEntity.Report built = printer.report(report, file);
            for (String line : built.lines()) {
                if (preview.size() < PREVIEW_LINES && !line.isBlank()) {
                    preview.add(line);
                }
            }
            pages = LinePrinterBlockEntity.pages(built.lines()).size();
        } catch (ElclException e) {
            ElclMessage message = e.elclMessage();
            preview.add(message.id() + "  " + message.text());
            pages = 0;
        }
        if (pages != shownPages || !preview.equals(shown)) {
            shown = preview;
            shownPages = pages;
            send(Component.empty(), preview, List.of(pages));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (printer == null || id != BUTTON_PRINT) {
            return false;
        }
        send(printer.printReport(report, file));
        return true;
    }
}
