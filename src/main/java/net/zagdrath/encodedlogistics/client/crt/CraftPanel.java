/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.terminal.TerminalItems;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// CRAFT: Item, Quantity, Scheduler (*AUTO, a number or a name), Destination (*NETWORK, *DRAWER, *INV), and the plan in a
// line (steps, missing, recall time), asked for again as the quantity changes. Enter submits the job and goes back.
final class CraftPanel extends CrtPanel {
    private static final int LABEL = WithdrawPanel.LABEL;
    private final CrtField item, quantity, scheduler, destination;
    private String plan = "", planned = "";
    private int planTimer;
    private boolean sent;

    CraftPanel(CrtTerminal screen, ItemKey key) {
        super(screen);
        item = new CrtField(5, LABEL, 40, BuiltInRegistries.ITEM.getKey(key.stack().getItem()).toString());
        quantity = new CrtField(6, LABEL, 10, "1");
        scheduler = new CrtField(7, LABEL, 10, "*AUTO");
        destination = new CrtField(8, LABEL, 10, "*NETWORK");
        fields.add(item);
        fields.add(quantity);
        fields.add(scheduler);
        fields.add(destination);
    }

    @Override
    String id() {
        return "CRAFT";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.craft.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.form");
    }

    @Override
    void shown() {
        screen.focus(quantity);
        planned = "";
    }

    @Override
    void refresh() {
        planned = "";
    }

    // The plan, asked for again half a second after the item or quantity changes.
    @Override
    void tick() {
        String now = item.trimmed() + " " + quantity.trimmed();
        if (!now.equals(planned) && ++planTimer >= 10) {
            planTimer = 0;
            planned = now;
            screen.send(TerminalService.QUERY, "plan \"" + item.trimmed() + "\" " + Math.max(1, TerminalItems.amount(quantity.trimmed())));
        }
    }

    @Override
    void draw(CrtGrid grid) {
        grid.put(3, 0, tr("crt.encodedlogistics.type_choices"));
        grid.put(5, 0, CrtGrid.pad(tr("crt.encodedlogistics.field.item"), LABEL));
        grid.put(6, 0, CrtGrid.pad(tr("crt.encodedlogistics.field.quantity"), LABEL));
        grid.put(6, LABEL + 12, "1-999999");
        grid.put(7, 0, CrtGrid.pad(tr("crt.encodedlogistics.field.scheduler"), LABEL));
        grid.put(7, LABEL + 12, "*AUTO, name");
        grid.put(8, 0, CrtGrid.pad(tr("crt.encodedlogistics.field.destination"), LABEL));
        grid.put(8, LABEL + 12, "*NETWORK, *DRAWER, *INV");
        if (!plan.isEmpty()) {
            grid.put(10, 2, CrtGrid.pad(tr("crt.encodedlogistics.craft.plan"), 16) + ":   " + plan, CrtGrid.DIM);
        }
    }

    @Override
    Component prompt(CrtField field) {
        if (field == destination) {
            return Component.literal("*NETWORK  *DRAWER  *INV");
        }
        if (field == scheduler) {
            return Component.literal("*AUTO, a scheduler's number (1 the first) or the start of its name");
        }
        return null;
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() == TerminalService.QUERY && response.topic().equals("plan") && !response.lines().isEmpty()) {
            plan = response.lines().getFirst().text();
        }
        if (sent && response.kind() == TerminalService.COMMAND) {
            screen.back();
        }
    }

    @Override
    boolean enter() {
        long amount = TerminalItems.amount(quantity.trimmed());
        String to = destination.trimmed().toUpperCase(Locale.ROOT);
        if (amount < 1 || amount > 999_999) {
            screen.message(tr("crt.encodedlogistics.msg.invalid_value", quantity.trimmed()));
            screen.focus(quantity);
            return true;
        }
        if (!to.equals("*NETWORK") && !to.equals("*DRAWER") && !to.equals("*INV")) {
            screen.message(tr("crt.encodedlogistics.msg.invalid_value", destination.trimmed()));
            screen.focus(destination);
            return true;
        }
        sent = true;
        String using = scheduler.trimmed().isEmpty() ? "*AUTO" : scheduler.trimmed();
        screen.runCommand("craft \"" + item.trimmed() + "\" " + amount + " \"" + using + "\" " + to.toLowerCase(Locale.ROOT));
        return true;
    }
}
