/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.db.SystemFiles;
import net.zagdrath.encodedlogistics.elcl.db.Timestamps;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.store.StoredFileService;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.ResourceType;

// The rows of ELSYS's system files (SystemFiles), from the network as it is: INVITEMS its items (hot and cold, as
// RTVITMCNT counts them), DEVICES its named devices (as Work with Devices and PRTRPT *DEV show them), CRFHIST its
// crafting job history (RTVCRFLOG's), JOBS its script jobs (Work with Active Jobs').
final class LiveFiles implements StoredFileService.LiveSource {
    @Override
    public List<Object[]> rows(ElclSystem system, String file) {
        RecordFormat format = SystemFiles.formats().get(file);
        if (format == null) {
            return List.of();
        }
        List<Object[]> rows = new ArrayList<>();
        switch (file) {
            case SystemFiles.INVITEMS -> items(system, format, rows);
            case SystemFiles.DEVICES -> devices(system, format, rows);
            case SystemFiles.CRFHIST -> crafts(system, format, rows);
            case SystemFiles.JOBS -> jobs(system, format, rows);
            default -> {}
        }
        return rows;
    }

    // A row in the format: each value as its field holds it (text cut to its length); null when one doesn't fit.
    private static void add(List<Object[]> rows, RecordFormat format, Object... values) {
        Object[] row = new Object[values.length];
        try {
            for (int i = 0; i < values.length; i++) {
                FieldDef field = format.fields().get(i);
                row[i] = field.convert(values[i]);
            }
        } catch (ElclException e) {
            return;
        }
        rows.add(row);
    }

    private static void items(ElclSystem system, RecordFormat format, List<Object[]> rows) {
        NetworkStorage storage = ControllerStructures.sharedStorageOf(system.server(), system.network(), false);
        if (storage == null) {
            return;
        }
        Map<Item, Long> hot = ElclItems.totals(storage, "*HOT"), cold = ElclItems.totals(storage, "*COLD");
        Map<Item, long[]> counts = new LinkedHashMap<>();
        hot.forEach((item, count) -> counts.computeIfAbsent(item, k -> new long[2])[0] += count);
        cold.forEach((item, count) -> counts.computeIfAbsent(item, k -> new long[2])[1] += count);
        counts.forEach((item, count) -> {
            if (count[0] + count[1] > 0) {
                add(rows, format, ElclItems.id(item), new ItemStack(item).getHoverName().getString(), count[0], count[1],
                        BuiltInRegistries.ITEM.getKey(item).getNamespace(), ResourceType.ITEM.code());
            }
        });
        // Fluids and gases by their full IDs, in mB (or their mod's unit); never on tape.
        for (ResourceType type : List.of(ResourceType.FLUID, ResourceType.PRESSURIZED)) {
            storage.list(type).forEach((key, count) -> add(rows, format, ElclItems.scriptId(key), key.displayName().getString(), count, 0L,
                    key.source() != null ? key.source() : key.namespace(), type.code()));
        }
    }

    private static void devices(ElclSystem system, RecordFormat format, List<Object[]> rows) {
        Map<Object, Integer> lanes = new HashMap<>();
        for (ControllerStructures.DeviceRow row : ControllerStructures.deviceRows(system.server(), system.network())) {
            lanes.put(row.rackDevice() != null ? row.rackDevice() : row.pos(), row.lanes());
        }
        for (ElclDevices.Device device : ElclDevices.list(system.server(), system.network())) {
            Integer used = lanes.get(device.rack() != null ? device.rack() : device.pos());
            var pos = device.pos().pos();
            String where = pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + device.pos().dimension().identifier().getPath();
            add(rows, format, device.name(), device.type(), where, used == null ? 0L : (long) used, ModCommands.status(device));
        }
    }

    private static void crafts(ElclSystem system, RecordFormat format, List<Object[]> rows) {
        for (CraftLog.Entry entry : CraftLog.entries(system.server(), system.network())) {
            add(rows, format, (long) entry.number(), entry.jobId(), entry.item(), entry.requested(), entry.produced(), entry.status().special(),
                    entry.requestedBy(), entry.started() < 0 ? "" : Timestamps.of(entry.started()), entry.ended() < 0 ? "" : Timestamps.of(entry.ended()));
        }
    }

    private static void jobs(ElclSystem system, RecordFormat format, List<Object[]> rows) {
        for (JobService.Job job : ElclServices.jobs().jobs(system)) {
            add(rows, format, job.number(), job.name(), job.user(), job.type(), job.host(), job.status(), (long) job.priority(), (long) job.budget());
        }
    }
}
