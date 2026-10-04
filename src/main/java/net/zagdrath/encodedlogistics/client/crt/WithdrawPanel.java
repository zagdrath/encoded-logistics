/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.net.TerminalItemsPayload;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.terminal.TerminalItems;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// WITHDRAW: Item (filled in), Quantity (1-999999), Destination (*DRAWER, the default, or *INV), what's on hand and how
// long a recall from tape would take. Enter withdraws (what's on tape is recalled first) and goes back.
final class WithdrawPanel extends CrtPanel {
    static final int LABEL = 34;
    private final ItemKey key;
    private final CrtField item, quantity, destination;
    private boolean sent;

    WithdrawPanel(CrtTerminal screen, ItemKey key) {
        super(screen);
        this.key = key;
        long count = screen.getMenu().items().getOrDefault(key, 0L);
        item = new CrtField(5, LABEL, 40, BuiltInRegistries.ITEM.getKey(key.stack().getItem()).toString());
        quantity = new CrtField(6, LABEL, 10, Long.toString(Math.max(1, Math.min(count, key.maxStackSize()))));
        destination = new CrtField(7, LABEL, 10, "*DRAWER");
        fields.add(item);
        fields.add(quantity);
        fields.add(destination);
    }

    @Override
    String id() {
        return "WITHDRAW";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.withdraw.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.form");
    }

    @Override
    void shown() {
        screen.focus(quantity);
    }

    @Override
    void draw(CrtGrid grid) {
        grid.put(3, 0, tr("crt.encodedlogistics.type_choices"));
        grid.put(5, 0, CrtGrid.pad(tr("crt.encodedlogistics.field.item"), LABEL));
        grid.put(6, 0, CrtGrid.pad(tr("crt.encodedlogistics.field.quantity"), LABEL));
        grid.put(6, LABEL + 12, "1-999999");
        grid.put(7, 0, CrtGrid.pad(tr("crt.encodedlogistics.field.destination"), LABEL));
        grid.put(7, LABEL + 12, "*DRAWER, *INV");
        long count = screen.getMenu().items().getOrDefault(key, 0L);
        TerminalItemsPayload.Entry cold = screen.getMenu().cold(key);
        String onHand = tr("crt.encodedlogistics.withdraw.on_hand", String.format(Locale.ROOT, "%,d", count));
        if (cold != null && cold.cold() > 0) {
            onHand += "   " + tr("crt.encodedlogistics.withdraw.on_tape", String.format(Locale.ROOT, "%,d", cold.cold()),
                    cold.eta() < 0 ? tr("tooltip.encodedlogistics.tape.no_drive") : "~" + TerminalItems.seconds(cold.eta(), true));
        }
        grid.put(9, 2, onHand, CrtGrid.DIM);
    }

    @Override
    Component prompt(CrtField field) {
        if (field == destination) {
            return Component.literal("*DRAWER  " + tr("crt.encodedlogistics.dest.drawer") + "   *INV  " + tr("crt.encodedlogistics.dest.inv"));
        }
        return field == quantity ? Component.literal("1-999999 (k, M: thousands, millions)") : null;
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
        if (!to.equals("*DRAWER") && !to.equals("*INV")) {
            screen.message(tr("crt.encodedlogistics.msg.invalid_value", destination.trimmed()));
            screen.focus(destination);
            return true;
        }
        sent = true;
        screen.send(TerminalService.QUERY, "withdraw \"" + item.trimmed() + "\" " + amount + " " + to.toLowerCase(Locale.ROOT));
        return true;
    }

    // Done: back to the list, the message on its line.
    @Override
    void receive(CrtResponsePayload response) {
        if (sent && response.kind() == TerminalService.QUERY && response.topic().equals("withdraw")) {
            screen.back();
        }
    }
}
