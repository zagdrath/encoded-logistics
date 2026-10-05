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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.midrange.LinePrinterBlockEntity;
import net.zagdrath.encodedlogistics.midrange.PeripheralBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// PRINTER (HANDOFF 3; layout printer): the report (field 0: 1-4) and, for 4, the spooled file (field 1: a name or
// *LAST). Lines, tab-separated: P pages paper status (the report's pages, the paper loaded, *READY / *NOPAPER /
// *PRINTING / *OFFLINE), F name (the network's spooled files, newest first: F4's list), X message (why the report
// can't be made). Button: Print (F6).
public class LinePrinterMenu extends PeripheralMenu {
    public static final int BUTTON_PRINT = 0;
    public static final int FIELD_REPORT = 0, FIELD_FILE = 1;
    private static final int FILES_LISTED = 30;

    private final @Nullable LinePrinterBlockEntity printer;
    private int report = LinePrinterBlockEntity.INVENTORY;
    private String file = "*LAST";

    // Client constructor.
    public LinePrinterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(1), null, Opening.read(buf));
    }

    public LinePrinterMenu(int containerId, Inventory inventory, Container slots, @Nullable PeripheralBlockEntity peripheral, Opening opening) {
        super(ModMenuTypes.LINE_PRINTER.get(), containerId, inventory, slots, peripheral, opening);
        this.printer = peripheral instanceof LinePrinterBlockEntity p ? p : null;
    }

    @Override
    protected void field(int key, String text) {
        if (key == FIELD_REPORT) {
            String value = text.trim();
            if (value.length() == 1 && value.charAt(0) >= '1' && value.charAt(0) <= '4') {
                report = value.charAt(0) - '0';
            } else {
                send(Component.translatable("crt.encodedlogistics.printer.bad_report", value));
            }
        } else if (key == FIELD_FILE) {
            file = text.trim().isEmpty() ? "*LAST" : text.trim().toUpperCase(Locale.ROOT);
        }
    }

    @Override
    protected void refresh() {
        if (printer == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        int pages = 0;
        try {
            pages = LinePrinterBlockEntity.pages(printer.report(report, file).lines());
        } catch (ElclException e) {
            ElclMessage message = e.elclMessage();
            lines.add("X\t" + message.id() + "  " + message.text());
        }
        lines.add(String.join("\t", "P", Integer.toString(pages), Integer.toString(printer.paper()), printer.status()));
        if (printer.network() != null && printer.getLevel() instanceof ServerLevel level) {
            List<SpoolService.SpooledFile> files = ElclServices.spool().files(new ElclSystem(level.getServer(), printer.network()), null, null);
            for (int i = 0; i < Math.min(FILES_LISTED, files.size()); i++) {
                lines.add("F\t" + files.get(i).name());
            }
        }
        send(Component.empty(), lines, List.of(lines.size()));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (printer == null || id != BUTTON_PRINT) {
            return false;
        }
        send(printer.printReport(report, file));
        refresh();
        return true;
    }
}
