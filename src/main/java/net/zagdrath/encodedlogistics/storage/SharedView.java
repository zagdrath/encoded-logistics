/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

// Another segment's (or network's) storage as a Share route shows it to its destination (ItemRouting): only the items
// the route's filter passes, read-only unless the route is Read/Write. NetworkStorage keeps these after its own storage,
// for putting in and taking out alike. Not a drive here (archiving and drive stats are the source's own business).
public final class SharedView implements StorageView {
    private final StorageView inner;
    private final Predicate<StorageKey> filter;
    private final boolean readWrite;
    private final Component from;

    public SharedView(StorageView inner, Predicate<StorageKey> filter, boolean readWrite, Component from) {
        this.inner = inner;
        this.filter = filter;
        this.readWrite = readWrite;
        this.from = from;
    }

    @Override
    public int priority() {
        return inner.priority();
    }

    @Override
    public boolean isTap() {
        return inner.isTap();
    }

    @Override
    public boolean isShared() {
        return true;
    }

    @Override
    public @Nullable Component sharedFrom() {
        return from;
    }

    @Override
    public void listInto(Map<StorageKey, Long> all) {
        Map<StorageKey, Long> mine = new HashMap<>();
        inner.listInto(mine);
        mine.forEach((key, count) -> {
            if (filter.test(key)) {
                all.merge(key, count, Long::sum);
            }
        });
    }

    @Override
    public long count(StorageKey key) {
        return filter.test(key) ? inner.count(key) : 0;
    }

    @Override
    public long insert(StorageKey key, long amount, boolean simulate) {
        return readWrite && filter.test(key) ? inner.insert(key, amount, simulate) : 0;
    }

    @Override
    public long extract(StorageKey key, long amount, boolean simulate) {
        return filter.test(key) ? inner.extract(key, amount, simulate) : 0;
    }
}
