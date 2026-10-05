/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.net.TerminalItemsPayload;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// WRKINV: the network's items, fluids and gases (what the desk's terminal sync brings: hot and cold) - Opt, Item, Type
// (ITEM, FLUID, PRES), Quantity (fluids and gases in B / mB), Location (Hot, Cold on tape, Hot+Cold) - eleven a page,
// those whose names start with "Position to" (blank or *ALL: all) and of the Type asked for (*ALL, *ITEM, *FLUID,
// *PRES). 1=Withdraw takes a fluid or gas into containers in the drawer or your inventory.
// Options: 1=Withdraw (WITHDRAW prompt), 5=Display details, 7=Craft (CRAFT prompt). Several are done one after another.
// F11 sorts by name, quantity or mod; F6 deposits from your inventory (DepositPanel).
final class InventoryPanel extends ListPanel<StorageKey> {
    private enum Sort {
        NAME, QUANTITY, MOD
    }

    private final CrtField position = new CrtField(3, 27, 30, "");
    private final CrtField type = new CrtField(4, 27, 6, "*ALL");
    private Sort sort = Sort.NAME;
    private int version = -1;
    private String filtered = "";
    // Options still to do once the one shown is done.
    private final Deque<Runnable> queued = new ArrayDeque<>();

    InventoryPanel(CrtTerminal screen) {
        super(screen);
        fields.add(position);
        fields.add(type);
    }

    @Override
    String id() {
        return "WRKINV";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.inv.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrkinv");
    }

    @Override
    int firstRow() {
        return 9;
    }

    @Override
    int pageSize() {
        return 11;
    }

    @Override
    Object key(StorageKey row) {
        return row;
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    void shown() {
        if (!queued.isEmpty()) {
            queued.poll().run();
        }
    }

    @Override
    void refresh() {
        version = -1;
    }

    @Override
    void tick() {
        String filter = position.trimmed() + "|" + type.trimmed().toUpperCase(Locale.ROOT);
        if (screen.getMenu().version() != version || !filter.equals(filtered)) {
            version = screen.getMenu().version();
            filtered = filter;
            setRows(list());
        }
    }

    private List<StorageKey> list() {
        String position = this.position.trimmed();
        String start = position.equalsIgnoreCase("*all") ? "" : position.toLowerCase(Locale.ROOT);
        ResourceType only = ResourceType.bySpecial(type.trimmed());
        Map<StorageKey, Long> items = screen.getMenu().items();
        List<StorageKey> keys = new ArrayList<>();
        for (StorageKey key : items.keySet()) {
            if ((only == null || key.is(only)) && (start.isEmpty() || key.displayName().getString().toLowerCase(Locale.ROOT).startsWith(start))) {
                keys.add(key);
            }
        }
        Comparator<StorageKey> byName = Comparator.comparing(key -> key.displayName().getString(), String.CASE_INSENSITIVE_ORDER);
        keys.sort(switch (sort) {
            case NAME -> byName;
            case QUANTITY -> Comparator.<StorageKey>comparingLong(key -> items.getOrDefault(key, 0L)).reversed().thenComparing(byName);
            case MOD -> Comparator.<StorageKey, String>comparing(StorageKey::namespace).thenComparing(byName);
        });
        return keys;
    }

    // F6: put items from your inventory in.
    @Override
    boolean functionKey(int f) {
        if (f != 6) {
            return false;
        }
        screen.push(new DepositPanel(screen));
        return true;
    }

    @Override
    void sort() {
        sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
        screen.message(tr("crt.encodedlogistics.inv.sorted", tr("crt.encodedlogistics.inv.sort." + sort.name().toLowerCase(Locale.ROOT))));
        version = -1;
    }

    @Override
    void drawHead(CrtGrid grid) {
        grid.put(3, 0, tr("crt.encodedlogistics.inv.position"));
        grid.put(3, 59, tr("crt.encodedlogistics.inv.start"));
        grid.put(4, 0, tr("crt.encodedlogistics.inv.type"));
        grid.put(4, 59, "*ALL *ITEM *FLUID *PRES");
        grid.put(5, 0, tr("crt.encodedlogistics.type_options"));
        grid.put(6, 0, tr("crt.encodedlogistics.inv.opts"));
        grid.put(8, 0, tr("crt.encodedlogistics.inv.cols"), CrtGrid.BRIGHT);
        if (!screen.getMenu().isOnline()) {
            grid.put(10, 5, tr("crt.encodedlogistics.msg.offline"), CrtGrid.DIM);
        }
    }

    @Override
    void drawRow(CrtGrid grid, int screenRow, StorageKey key) {
        long count = screen.getMenu().items().getOrDefault(key, 0L);
        TerminalItemsPayload.Entry cold = screen.getMenu().cold(key);
        long onTape = cold != null ? cold.cold() : 0;
        grid.put(screenRow, 5, CrtGrid.pad(key.displayName().getString(), 33));
        grid.put(screenRow, 39, key.type().code());
        grid.put(screenRow, 45, CrtGrid.padLeft(key.isItem() ? String.format(Locale.ROOT, "%,d", count) : key.format(count), 11));
        String location = tr(onTape <= 0 ? "crt.encodedlogistics.loc.hot" : onTape >= count ? "crt.encodedlogistics.loc.cold" : "crt.encodedlogistics.loc.split");
        grid.put(screenRow, 59, location, onTape > 0 ? CrtGrid.BRIGHT : CrtGrid.NORMAL);
    }

    @Override
    boolean process(List<Option<StorageKey>> chosen) {
        queued.clear();
        for (Option<StorageKey> option : chosen) {
            StorageKey key = option.row();
            switch (option.option()) {
                case "1" -> queued.add(() -> screen.push(new WithdrawPanel(screen, key)));
                case "5" -> queued.add(() -> screen.push(new TextPanel(screen, "DSPITM", tr("crt.encodedlogistics.detail.title"),
                        "detail \"" + (key.isItem() ? BuiltInRegistries.ITEM.getKey(key.stack().getItem()).toString() : ElclItems.text(key)) + "\"")));
                case "7" -> {
                    if (!screen.getMenu().craftables().contains(key)) {
                        screen.message(tr("crt.encodedlogistics.msg.invalid_option", option.option()));
                        return true;
                    }
                    queued.add(() -> screen.push(new CraftPanel(screen, key)));
                }
                default -> {
                    screen.message(tr("crt.encodedlogistics.msg.invalid_option", option.option()));
                    queued.clear();
                    return true;
                }
            }
        }
        if (!queued.isEmpty()) {
            queued.poll().run();
        }
        return true;
    }
}
