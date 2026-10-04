/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;

// Spooled files in the system's saved data (Work with Output): kept until deleted, at most spooledFileCap per system
// (the oldest go first). Printing goes to a printer device (elcl.device.Printers): ELC1301 with none, ELC1306 when it's
// out of paper.
public final class StoredSpoolService implements SpoolService {
    static int pages(int lines) {
        return Math.max(1, (lines + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
    }

    @Override
    public synchronized List<SpooledFile> files(ElclSystem system, @Nullable String user, @Nullable String job) {
        List<SpooledFile> list = new ArrayList<>();
        for (SpooledFile file : ElclStore.of(system).spooled) {
            if ((user == null || file.user().equalsIgnoreCase(user)) && (job == null || file.jobName().equalsIgnoreCase(job) || file.job().equalsIgnoreCase(job)
                    || file.jobNumber().equals(job))) {
                list.addFirst(file);
            }
        }
        return list;
    }

    @Override
    public synchronized SpooledFile file(ElclSystem system, int id) throws ElclException {
        for (SpooledFile file : ElclStore.of(system).spooled) {
            if (file.id() == id) {
                return file;
            }
        }
        throw new ElclException("ELC0103", id, "SPLF");
    }

    private static void trim(SystemData data) {
        int cap = ElclConfig.spooledFileCap();
        while (data.spooled.size() > cap) {
            data.spooled.removeFirst();
        }
    }

    @Override
    public synchronized int create(ElclSystem system, String name, String jobNumber, String jobName, String user, List<String> lines) {
        SystemData data = ElclStore.of(system);
        int id = data.nextSpooled++;
        data.spooled.add(new SpooledFile(id, name, jobNumber, jobName, user, pages(lines.size()), "*RDY", system.nowShort(), List.copyOf(lines)));
        trim(data);
        data.changed();
        return id;
    }

    @Override
    public synchronized void append(ElclSystem system, String name, String jobNumber, String jobName, String user, String line) {
        SystemData data = ElclStore.of(system);
        for (int i = data.spooled.size() - 1; i >= 0; i--) {
            SpooledFile file = data.spooled.get(i);
            if (file.name().equalsIgnoreCase(name) && file.jobNumber().equals(jobNumber)) {
                List<String> lines = new ArrayList<>(file.lines());
                lines.add(line);
                data.spooled.set(i, new SpooledFile(file.id(), file.name(), file.jobNumber(), file.jobName(), file.user(), pages(lines.size()), file.status(),
                        file.created(), List.copyOf(lines)));
                data.changed();
                return;
            }
        }
        create(system, name, jobNumber, jobName, user, List.of(line));
    }

    @Override
    public synchronized void delete(ElclSystem system, String user, int id) throws ElclException {
        SystemData data = ElclStore.of(system);
        if (!data.spooled.removeIf(file -> file.id() == id)) {
            throw new ElclException("ELC0103", id, "SPLF");
        }
        data.changed();
    }

    @Override
    public ElclMessage print(ElclSystem system, String user, int id, String printer) throws ElclException {
        SpooledFile file = file(system, id);
        return net.zagdrath.encodedlogistics.elcl.device.Printers.print(system, printer, file.name(), file.lines());
    }
}
