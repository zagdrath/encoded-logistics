/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.net.TerminalItemsPayload;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.terminal.TerminalItems;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// WITHDRAW: Item (filled in), Quantity (1-999999), Destination (*DRAWER, the default, or *INV; only *INV at an
// Integrated system's console, which has no drawer), what's on hand and how long a recall from tape would take. Enter withdraws (what's on tape is recalled first) and goes back. A fluid or gas
// (Item "FLUID minecraft:water"; Quantity in mB, a bucket to start with) goes into containers there.
final class WithdrawPanel extends CrtPanel {
    static final int LABEL = 34;
    private final StorageKey key;
    private final CrtField item, quantity, destination;
    private final boolean drawer;
    private boolean sent;

    WithdrawPanel(CrtTerminal screen, StorageKey key) {
        super(screen);
        this.key = key;
        drawer = screen.getMenu().desk() != null;
        long count = screen.getMenu().items().getOrDefault(key, 0L);
        item = new CrtField(5, LABEL, 40, key.isItem() ? BuiltInRegistries.ITEM.getKey(key.stack().getItem()).toString() : ElclItems.text(key));
        quantity = new CrtField(6, LABEL, 10, Long.toString(Math.max(1, Math.min(count, key.isItem() ? key.maxStackSize() : 1_000))));
        destination = new CrtField(7, LABEL, 10, drawer ? "*DRAWER" : "*INV");
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
        grid.put(7, LABEL + 12, drawer ? "*DRAWER, *INV" : "*INV");
        long count = screen.getMenu().items().getOrDefault(key, 0L);
        TerminalItemsPayload.Entry cold = screen.getMenu().cold(key);
        String onHand = tr("crt.encodedlogistics.withdraw.on_hand", key.isItem() ? String.format(Locale.ROOT, "%,d", count) : key.format(count));
        if (cold != null && cold.cold() > 0) {
            onHand += "   " + tr("crt.encodedlogistics.withdraw.on_tape", String.format(Locale.ROOT, "%,d", cold.cold()),
                    cold.eta() < 0 ? tr("tooltip.encodedlogistics.tape.no_drive") : "~" + TerminalItems.seconds(cold.eta(), true));
        }
        grid.put(9, 2, onHand, CrtGrid.DIM);
    }

    @Override
    Component prompt(CrtField field) {
        if (field == destination) {
            String inv = "*INV  " + tr("crt.encodedlogistics.dest.inv");
            return Component.literal(drawer ? "*DRAWER  " + tr("crt.encodedlogistics.dest.drawer") + "   " + inv : inv);
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
        if (!(drawer && to.equals("*DRAWER")) && !to.equals("*INV")) {
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
