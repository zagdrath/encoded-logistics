/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;


import net.zagdrath.encodedlogistics.elcl.job.StoredJobService;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.elcl.store.StoredMessageService;
import net.zagdrath.encodedlogistics.elcl.store.StoredSpoolService;
import net.zagdrath.encodedlogistics.elcl.store.StoredSysvalService;
import net.zagdrath.encodedlogistics.elcl.store.StoredUserService;

// The services behind the Terminal OS screens, each keeping what it has in the system's saved data (elcl.store): libraries,
// messages, spooled files, system values, users, and jobs (elcl.job). Replaceable (set*) for tests.
public final class ElclServices {
    private static LibraryService libraries = new StoredLibraryService();
    private static JobService jobs = new StoredJobService();
    private static MessageService messages = new StoredMessageService();
    private static SpoolService spool = new StoredSpoolService();
    private static SysvalService sysvals = new StoredSysvalService();
    private static UserService users = new StoredUserService();

    private ElclServices() {}

    public static LibraryService libraries() {
        return libraries;
    }

    public static JobService jobs() {
        return jobs;
    }

    public static MessageService messages() {
        return messages;
    }

    public static SpoolService spool() {
        return spool;
    }

    public static SysvalService sysvals() {
        return sysvals;
    }

    public static UserService users() {
        return users;
    }

    public static void setUsers(UserService service) {
        users = service;
    }

    public static void setLibraries(LibraryService service) {
        libraries = service;
    }

    public static void setJobs(JobService service) {
        jobs = service;
    }

    public static void setMessages(MessageService service) {
        messages = service;
    }

    public static void setSpool(SpoolService service) {
        spool = service;
    }

    public static void setSysvals(SysvalService service) {
        sysvals = service;
    }
}
