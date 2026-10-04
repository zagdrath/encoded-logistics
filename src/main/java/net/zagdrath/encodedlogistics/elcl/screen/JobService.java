/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.List;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;

// Script jobs (OS.md 5): interactive and batch, their logs, schedule entries and trigger events - Work with Active
// Jobs, Work with Job, Display Job Log, Work with Job Schedule Entries, Work with Trigger Events. A job is named by
// its number, its name (if unique) or nnnnnn/USER/NAME.
public interface JobService {
    // type: INT or BCH; status: *ACTIVE, *WAIT, *JOBQ, *HELD, *MSGW; budget: % of its per-tick budget used.
    record Job(String number, String name, String user, String type, String host, String status, int budget, int priority, boolean log) {
        public String qualified() {
            return number + "/" + user + "/" + name;
        }
    }

    // A job log entry: a command run (command true, its text) or a message (its ID, severity and text).
    record LogEntry(boolean command, String id, int severity, String text, String from, String time) {}

    // status: *SCD or *HLD; frequency: *ONCE, *DAILY, *INTERVAL.
    record ScheduleEntry(String job, String status, String frequency, String next, String command, String user, String time, int interval) {}

    // status: *ACTIVE or *HELD.
    record Trigger(String name, String event, String item, String device, String value, String program, String status, String user) {}

    List<Job> jobs(ElclSystem system);

    Job job(ElclSystem system, String id) throws ElclException;

    // The interactive job of a terminal session (opened with it, ended with it).
    Job interactive(ElclSystem system, String user, String session, String host);

    void endInteractive(ElclSystem system, String session);

    ElclMessage hold(ElclSystem system, String user, String id) throws ElclException;

    ElclMessage release(ElclSystem system, String user, String id) throws ElclException;

    // option: *CNTRLD or *IMMED.
    ElclMessage end(ElclSystem system, String user, String id, String option) throws ElclException;

    void change(ElclSystem system, String user, String id, int priority, String log) throws ElclException;

    ElclMessage submit(ElclSystem system, String user, String command, String name, String host, boolean log) throws ElclException;

    List<LogEntry> log(ElclSystem system, String id) throws ElclException;

    void logCommand(ElclSystem system, String id, String command);

    void logMessage(ElclSystem system, String id, ElclMessage message);

    List<String> callStack(ElclSystem system, String id) throws ElclException;

    List<ScheduleEntry> scheduleEntries(ElclSystem system);

    ElclMessage addScheduleEntry(ElclSystem system, String user, String job, String command, String frequency, String time, int interval)
            throws ElclException;

    ElclMessage removeScheduleEntry(ElclSystem system, String user, String job) throws ElclException;

    void holdScheduleEntry(ElclSystem system, String user, String job, boolean hold) throws ElclException;

    List<Trigger> triggers(ElclSystem system);

    ElclMessage addTrigger(ElclSystem system, String user, Trigger trigger) throws ElclException;

    ElclMessage removeTrigger(ElclSystem system, String user, String name) throws ElclException;

    void holdTrigger(ElclSystem system, String user, String name, boolean hold) throws ElclException;
}
