/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;

// STUB: waiting on elcl.job (spooled files in system data) and the Line Printer (not in the mod yet: printing finds
// no printer, ELC1301). In memory per system.
final class StubSpoolService implements SpoolService {
    private static final class Files {
        final List<SpooledFile> files = new ArrayList<>();
        int nextId = 1;
    }

    private final ElclServices.Store<Files> store = new ElclServices.Store<>(system -> new Files());

    private static int pages(int lines) {
        return Math.max(1, (lines + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized List<SpooledFile> files(ElclSystem system, @Nullable String user, @Nullable String job) {
        List<SpooledFile> list = new ArrayList<>();
        for (SpooledFile file : store.of(system).files) {
            if ((user == null || file.user().equalsIgnoreCase(user)) && (job == null || file.jobName().equalsIgnoreCase(job) || file.job().equalsIgnoreCase(job)
                    || file.jobNumber().equals(job))) {
                list.addFirst(file);
            }
        }
        return list;
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized SpooledFile file(ElclSystem system, int id) throws ElclException {
        for (SpooledFile file : store.of(system).files) {
            if (file.id() == id) {
                return file;
            }
        }
        throw new ElclException("ELC0103", id, "SPLF");
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized int create(ElclSystem system, String name, String jobNumber, String jobName, String user, List<String> lines) {
        Files files = store.of(system);
        int id = files.nextId++;
        files.files.add(new SpooledFile(id, name, jobNumber, jobName, user, pages(lines.size()), "*RDY", system.nowShort(), List.copyOf(lines)));
        return id;
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void append(ElclSystem system, String name, String jobNumber, String jobName, String user, String line) {
        Files files = store.of(system);
        for (int i = files.files.size() - 1; i >= 0; i--) {
            SpooledFile file = files.files.get(i);
            if (file.name().equalsIgnoreCase(name) && file.jobNumber().equals(jobNumber)) {
                List<String> lines = new ArrayList<>(file.lines());
                lines.add(line);
                files.files.set(i, new SpooledFile(file.id(), file.name(), file.jobNumber(), file.jobName(), file.user(), pages(lines.size()), file.status(),
                        file.created(), List.copyOf(lines)));
                return;
            }
        }
        create(system, name, jobNumber, jobName, user, List.of(line));
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void delete(ElclSystem system, String user, int id) throws ElclException {
        if (!store.of(system).files.removeIf(file -> file.id() == id)) {
            throw new ElclException("ELC0103", id, "SPLF");
        }
    }

    // STUB: waiting on the Line Printer
    @Override
    public ElclMessage print(ElclSystem system, String user, int id, String printer) throws ElclException {
        file(system, id);
        throw new ElclException("ELC1301", printer.equals("*DFT") ? "PRT01" : printer);
    }
}
