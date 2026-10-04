/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.device;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;

// The places devices of one kind come from (a block, a rack device, a test's fake): each lists the ones on a system.
public final class DeviceSources<T> {
    @FunctionalInterface
    public interface Source<T> {
        List<T> on(ElclSystem system);
    }

    private final List<Source<T>> sources = new CopyOnWriteArrayList<>();

    public void register(Source<T> source) {
        sources.add(source);
    }

    public void unregister(Source<T> source) {
        sources.remove(source);
    }

    public List<T> all(ElclSystem system) {
        List<T> all = new ArrayList<>();
        for (Source<T> source : sources) {
            all.addAll(source.on(system));
        }
        return all;
    }
}
