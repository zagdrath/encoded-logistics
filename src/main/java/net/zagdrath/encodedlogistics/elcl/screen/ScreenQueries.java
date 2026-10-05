/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.store.StoredMessageService;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.exec.MachineCommands;
import net.zagdrath.encodedlogistics.elcl.exec.OsCommands;
import net.zagdrath.encodedlogistics.elcl.job.JobHost;
import net.zagdrath.encodedlogistics.elcl.job.JobHosts;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.machine.MachineInfo;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;

// The Terminal OS screens' data (TerminalService.SCREEN requests), from the services: each row a line of cells (one
// per field, the client lays them out), a failure an "ID: text" message. Requests:
//  libraries / library L / members L / source L M [from] / lock L M / unlock L M / printmember L M
//  savebegin L M / savepart <lines> / savecommit  (a member's source in pieces: "seq\tdate\ttext" lines)
//  jobs / job J / joblog J / callstack J / schedules / triggers
//  messages / readmessages / removemessage N / removemessages
//  spooled [job] / splf N / deletesplf N / printsplf N [printer]
//  sysvals / sysval S
//  signon USER [CURLIB]   (the session signs on: ELC0402 for a name not the player's; the profile's class, current
//                          library and library list come back)
//  values <items|devices|libraries|members|programs|jobs|sysvals> [filter]   (the prompter's F4 lists)
public final class ScreenQueries {
    // Lines of source a response carries (a longer member comes in pages).
    public static final int SOURCE_PAGE = 1_000;
    // A save in progress per player: library, member, the lines so far.
    private record Saving(String library, String member, List<SourceLine> lines) {}

    private static final Map<UUID, Saving> SAVING = new HashMap<>();

    private ScreenQueries() {}

    private static TerminalLine row(Object... cells) {
        TerminalLine.Builder builder = TerminalLine.builder();
        for (Object cell : cells) {
            builder.left(String.valueOf(cell), 0);
        }
        return builder.build();
    }

    private static TerminalOutput failed(ElclMessage message) {
        return TerminalOutput.message(Component.literal(message.toString()));
    }

    public static TerminalOutput handle(TerminalContext context, String text) {
        List<String> words = TerminalCommands.words(text);
        if (words.isEmpty()) {
            return new TerminalOutput();
        }
        if (context.network() == null) {
            return failed(ElclMessage.of("ELC1302", "*NETWORK"));
        }
        ElclSystem system = new ElclSystem(context.server(), context.network());
        String user = context.user();
        String first = words.getFirst().toLowerCase(Locale.ROOT);
        try {
            return switch (first) {
                case "libraries" -> libraries(system);
                case "library" -> library(system, arg(words, 1));
                case "members" -> members(system, arg(words, 1));
                case "source" -> source(system, user, arg(words, 1), arg(words, 2), words.size() > 3 ? Integer.parseInt(words.get(3)) : 0);
                case "lock" -> {
                    ElclServices.libraries().lock(system, user, arg(words, 1), arg(words, 2));
                    yield new TerminalOutput();
                }
                case "unlock" -> {
                    ElclServices.libraries().unlock(system, user, arg(words, 1), arg(words, 2));
                    yield new TerminalOutput();
                }
                case "printmember" -> printMember(system, user, arg(words, 1), arg(words, 2));
                case "savebegin" -> {
                    SAVING.put(context.player().getUUID(), new Saving(arg(words, 1), arg(words, 2), new ArrayList<>()));
                    yield new TerminalOutput();
                }
                case "savepart" -> savePart(context, text.substring(text.indexOf(' ') + 1));
                case "savecommit" -> saveCommit(context, system, user);
                case "jobs" -> jobs(system);
                case "job" -> job(system, own(system, user, arg(words, 1)));
                case "joblog" -> jobLog(system, user, arg(words, 1));
                case "callstack" -> callStack(system, own(system, user, arg(words, 1)));
                case "schedules" -> schedules(system);
                case "triggers" -> triggers(system);
                case "messages" -> messages(system, queue(system, user, arg(words, 1)));
                case "readmessages" -> {
                    ElclServices.messages().markRead(system, queue(system, user, arg(words, 1)));
                    yield new TerminalOutput();
                }
                case "removemessage" -> {
                    ElclServices.messages().remove(system, queue(system, user, arg(words, 2)), Long.parseLong(arg(words, 1)));
                    yield new TerminalOutput();
                }
                case "removemessages" -> {
                    ElclServices.messages().removeAll(system, queue(system, user, arg(words, 1)));
                    yield new TerminalOutput();
                }
                case "spooled" -> spooled(system, user, words.size() > 1 && !words.get(1).equals("*ALL") ? words.get(1) : null);
                case "splf" -> splf(system, Integer.parseInt(arg(words, 1)));
                case "deletesplf" -> {
                    ElclServices.spool().delete(system, user, Integer.parseInt(arg(words, 1)));
                    yield new TerminalOutput();
                }
                case "printsplf" -> TerminalOutput.message(Component.literal(ElclServices.spool()
                        .print(system, user, Integer.parseInt(arg(words, 1)), words.size() > 2 ? words.get(2) : "*DFT").toString()));
                case "sysvals" -> sysvals(system);
                case "machines" -> machines(context);
                case "signon" -> signOn(context, system, words.size() > 1 ? words.get(1) : "", arg(words, 2));
                case "signoff" -> {
                    // SIGNOFF: the session needs signing on again.
                    if (context.player().containerMenu instanceof TerminalDeskMenu menu) {
                        menu.signOff();
                    }
                    yield new TerminalOutput();
                }
                case "values" -> values(context, system, arg(words, 1), words.size() > 2 ? words.get(2) : "");
                default -> new TerminalOutput();
            };
        } catch (ElclException e) {
            return failed(e.elclMessage());
        } catch (NumberFormatException e) {
            return failed(ElclMessage.of("ELC0001", text));
        }
    }

    // A job named "*" (or not named): the user's own interactive job.
    private static String own(ElclSystem system, String user, String id) {
        return id.equals("*") || id.isEmpty() ? OsCommands.interactiveJob(system, user).number() : id;
    }

    // DSPMSG USR(): the user's own queue (none given, *CURRENT), or another's - QSYSOPR (*SYSOPR) among them - for a
    // *SECOFR (or with full authority); ELC0401 otherwise.
    private static String queue(ElclSystem system, String user, String queue) throws ElclException {
        if (queue.isEmpty() || queue.equals("*CURRENT") || queue.equalsIgnoreCase(user)) {
            return user;
        }
        String name = queue.equals("*SYSOPR") ? StoredMessageService.SYSOPR : queue;
        if (!ElclServices.users().mayManage(system, user, name)) {
            throw new ElclException("ELC0401", user, "*USE");
        }
        return name;
    }

    private static String arg(List<String> words, int index) {
        return index < words.size() ? words.get(index).toUpperCase(Locale.ROOT) : "";
    }

    // --- Libraries and members ---

    // name, type, text, owner, authority, members, size, created
    private static TerminalOutput libraries(ElclSystem system) {
        TerminalOutput out = new TerminalOutput();
        for (LibraryService.Library library : ElclServices.libraries().libraries(system)) {
            out.line(libraryRow(library));
        }
        return out;
    }

    private static TerminalLine libraryRow(LibraryService.Library library) {
        return row(library.name(), library.type(), library.text(), library.owner(), library.authority(), library.members(), library.size(), library.created());
    }

    private static TerminalOutput library(ElclSystem system, String name) throws ElclException {
        return new TerminalOutput().line(libraryRow(ElclServices.libraries().library(system, name)));
    }

    // name, type, changed (* or blank), text, lines, updated, program (1 / 0)
    private static TerminalOutput members(ElclSystem system, String library) throws ElclException {
        TerminalOutput out = new TerminalOutput();
        for (LibraryService.Member member : ElclServices.libraries().members(system, library)) {
            out.line(row(member.name(), member.type(), member.changed() ? "*" : "", member.text(), member.lines(), member.updated(),
                    member.program() ? "1" : "0"));
        }
        return out;
    }

    // First line: total lines, from, read-only (1 / 0); then seq, date, text for up to SOURCE_PAGE lines from `from`.
    private static TerminalOutput source(ElclSystem system, String user, String library, String member, int from) throws ElclException {
        List<SourceLine> lines = ElclServices.libraries().source(system, library, member);
        LibraryService.Library lib = ElclServices.libraries().library(system, library);
        boolean readOnly = !StoredLibraryService.canChange(system, lib, user);
        TerminalOutput out = new TerminalOutput();
        out.line(row(lines.size(), from, readOnly ? "1" : "0"));
        for (int i = Math.max(0, from); i < Math.min(lines.size(), from + SOURCE_PAGE); i++) {
            SourceLine line = lines.get(i);
            out.line(row(line.seq(), line.date(), line.text()));
        }
        return out;
    }

    // WRKMBR 6=Print: the source, numbered, to a spooled file (the member's name) in the user's job.
    private static TerminalOutput printMember(ElclSystem system, String user, String library, String member) throws ElclException {
        List<String> lines = new ArrayList<>();
        lines.add("Source member " + library + "/" + member + "   " + system.nowShort() + "   " + system.name());
        lines.add("");
        for (SourceLine line : ElclServices.libraries().source(system, library, member)) {
            lines.add(line.seqText() + " " + line.text());
        }
        JobService.Job job = OsCommands.interactiveJob(system, user);
        ElclServices.spool().create(system, member, job.number(), job.name(), user, lines);
        return TerminalOutput.message(Component.translatable("crt.encodedlogistics.wrkmbr.printed", library + "/" + member));
    }

    private static TerminalOutput savePart(TerminalContext context, String encoded) {
        Saving saving = SAVING.get(context.player().getUUID());
        if (saving == null) {
            return failed(ElclMessage.of("ELC0001", "savepart"));
        }
        for (String line : encoded.split("\n", -1)) {
            String[] parts = line.split("\t", 3);
            if (parts.length == 3) {
                try {
                    saving.lines().add(new SourceLine(Integer.parseInt(parts[0]), parts[2], Integer.parseInt(parts[1])));
                } catch (NumberFormatException ignored) {}
            }
        }
        return new TerminalOutput();
    }

    private static TerminalOutput saveCommit(TerminalContext context, ElclSystem system, String user) throws ElclException {
        Saving saving = SAVING.remove(context.player().getUUID());
        if (saving == null) {
            return failed(ElclMessage.of("ELC0001", "savecommit"));
        }
        ElclServices.libraries().save(system, user, saving.library(), saving.member(), saving.lines());
        return TerminalOutput.message(Component.literal(ElclMessage.of("ELC0213", saving.member(), saving.library()).toString()));
    }

    // --- Jobs ---

    // First line: budget used %, active jobs, hosts busy, hosts; then number, name, user, type, host, status, budget %,
    // priority, log (1 / 0) per job.
    private static TerminalOutput jobs(ElclSystem system) {
        List<JobService.Job> jobs = ElclServices.jobs().jobs(system);
        TerminalOutput out = new TerminalOutput();
        int budget = jobs.stream().mapToInt(JobService.Job::budget).sum();
        long active = jobs.stream().filter(job -> job.status().equals("*ACTIVE")).count();
        // Hosts running batch jobs, of all the system's job hosts.
        List<JobHost> hosts = JobHosts.all(system);
        long busy = hosts.stream().filter(host -> jobs.stream().anyMatch(job -> job.type().equals("BCH") && job.host().equals(host.name())
                && !job.status().equals("*JOBQ") && !job.status().equals("*HELD"))).count();
        out.line(row(Math.min(100, budget), active, busy, hosts.size()));
        for (JobService.Job job : jobs) {
            out.line(jobRow(job));
        }
        return out;
    }

    private static TerminalLine jobRow(JobService.Job job) {
        return row(job.number(), job.name(), job.user(), job.type(), job.host(), job.status(), job.budget(), job.priority(), job.log() ? "1" : "0");
    }

    private static TerminalOutput job(ElclSystem system, String id) throws ElclException {
        return new TerminalOutput().line(jobRow(ElclServices.jobs().job(system, id)));
    }

    // command (1 / 0), id, severity, text, from, time; "*" is the user's own interactive job.
    private static TerminalOutput jobLog(ElclSystem system, String user, String id) throws ElclException {
        String job = id.equals("*") || id.isEmpty() ? OsCommands.interactiveJob(system, user).number() : id;
        TerminalOutput out = new TerminalOutput();
        out.line(jobRow(ElclServices.jobs().job(system, job)));
        for (JobService.LogEntry entry : ElclServices.jobs().log(system, job)) {
            out.line(row(entry.command() ? "1" : "0", entry.id(), entry.severity(), entry.text(), entry.from(), entry.time()));
        }
        return out;
    }

    private static TerminalOutput callStack(ElclSystem system, String id) throws ElclException {
        TerminalOutput out = new TerminalOutput();
        ElclServices.jobs().callStack(system, id).forEach(entry -> out.line(row(entry)));
        return out;
    }

    // job, status, frequency, next, command, user, time, interval
    private static TerminalOutput schedules(ElclSystem system) {
        TerminalOutput out = new TerminalOutput();
        for (JobService.ScheduleEntry entry : ElclServices.jobs().scheduleEntries(system)) {
            out.line(row(entry.job(), entry.status(), entry.frequency(), entry.next(), entry.command(), entry.user(), entry.time(), entry.interval()));
        }
        return out;
    }

    // name, event, item, device, value, program, status, user
    private static TerminalOutput triggers(ElclSystem system) {
        TerminalOutput out = new TerminalOutput();
        for (JobService.Trigger trigger : ElclServices.jobs().triggers(system)) {
            out.line(row(trigger.name(), trigger.event(), trigger.item(), trigger.device(), trigger.value(), trigger.program(), trigger.status(),
                    trigger.user()));
        }
        return out;
    }

    // --- Sign-on ---

    // The session signs on (the desk's menu remembers it): user class, current library, library list (blank-joined).
    private static TerminalOutput signOn(TerminalContext context, ElclSystem system, String typed, String library) throws ElclException {
        UserService.Profile profile = ElclServices.users().signOn(system, context.user(), context.player().getUUID(), typed, library);
        if (context.player().containerMenu instanceof TerminalDeskMenu menu) {
            menu.signOn();
        }
        return new TerminalOutput().line(row(profile.userClass(), profile.currentLibrary(), String.join(" ", profile.libraryList())));
    }

    // --- Messages, spooled files, system values ---

    // id, message ID, severity, from, sent, text, unread (1 / 0); newest first.
    private static TerminalOutput messages(ElclSystem system, String user) {
        TerminalOutput out = new TerminalOutput();
        for (MessageService.Message message : ElclServices.messages().messages(system, user)) {
            out.line(row(message.id(), message.msgId(), message.severity(), message.from(), message.sent(), message.text(), message.unread() ? "1" : "0"));
        }
        return out;
    }

    // id, name, job, user, pages, status, created
    private static TerminalOutput spooled(ElclSystem system, String user, String job) {
        TerminalOutput out = new TerminalOutput();
        for (SpoolService.SpooledFile file : ElclServices.spool().files(system, user, job)) {
            out.line(row(file.id(), file.name(), file.job(), file.user(), file.pages(), file.status(), file.created()));
        }
        return out;
    }

    // First line: id, name, job, pages; then the file's lines.
    private static TerminalOutput splf(ElclSystem system, int id) throws ElclException {
        SpoolService.SpooledFile file = ElclServices.spool().file(system, id);
        TerminalOutput out = new TerminalOutput();
        out.line(row(file.id(), file.name(), file.job(), file.pages()));
        file.lines().forEach(line -> out.line(row(line)));
        return out;
    }

    // name, value, default, description, allowed (", " joined)
    private static TerminalOutput sysvals(ElclSystem system) {
        TerminalOutput out = new TerminalOutput();
        for (SysvalService.Sysval sysval : ElclServices.sysvals().values(system)) {
            out.line(row(sysval.name(), sysval.value(), sysval.defaultValue(), sysval.description(), String.join(", ", sysval.allowed())));
        }
        return out;
    }

    // Work with Machines' rows: name, the machine's name, status, progress, energy, rate, then what 2=Change shows and
    // 7=Enable/Disable reads - redstone mode, auto-eject, power from network, Gateway (*NONE for none or a setting it
    // hasn't), the redstone modes it takes, and whether it's switched on.
    private static TerminalOutput machines(TerminalContext context) {
        TerminalOutput out = new TerminalOutput();
        for (MachineCommands.Machine machine : MachineCommands.list(context.server(), context.network())) {
            MachineInfo info = machine.info();
            MachineBridge bridge = machine.bridge();
            String gateway = bridge.gatewayName(context.server());
            if (info == null) {
                out.line(row(machine.name(), bridge.shown().getString(), machine.status(), "", "", "", "*NONE", "*NONE", bridge.powerFromNetwork() ? "*YES" : "*NO",
                        gateway.isEmpty() ? "*NONE" : gateway, "", "*YES"));
                continue;
            }
            MachineInfo.Settings settings = info.settings();
            String energy = info.energy().map(e -> compact(e.stored()) + "/" + compact(e.capacity()) + " FE").orElse("");
            StringBuilder modes = new StringBuilder();
            settings.redstoneModes().forEach(mode -> modes.append(modes.isEmpty() ? "" : " ").append('*').append(mode.toUpperCase(Locale.ROOT)));
            out.line(row(machine.name(), info.name().getString(), machine.status(), info.percent() < 0 ? "" : info.percent() + "%", energy,
                    String.format(Locale.ROOT, "%.1f/min", info.statistics().operationsPerMinute()),
                    settings.redstoneMode().map(mode -> "*" + mode.toUpperCase(Locale.ROOT)).orElse("*NONE"),
                    settings.autoEjectSupported() ? settings.autoEject() ? "*YES" : "*NO" : "*NONE", bridge.powerFromNetwork() ? "*YES" : "*NO",
                    gateway.isEmpty() ? "*NONE" : gateway, modes.toString(), settings.enabled() ? "*YES" : "*NO"));
        }
        return out;
    }

    // 12345 -> "12.3k", as the energy column fits it.
    static String compact(long value) {
        return value < 10_000 ? Long.toString(value) : value < 10_000_000 ? String.format(Locale.ROOT, "%.1fk", value / 1000.0)
                : String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }

    // --- The prompter's value lists: value, description ---

    private static TerminalOutput values(TerminalContext context, ElclSystem system, String kind, String filter) throws ElclException {
        TerminalOutput out = new TerminalOutput();
        String match = filter.toLowerCase(Locale.ROOT);
        switch (kind) {
            case "ITEMS" -> {
                NetworkStorage storage = context.storage();
                if (storage != null) {
                    List<ItemKey> keys = new ArrayList<>(storage.listAll().keySet());
                    keys.removeIf(key -> !match.isEmpty() && !key.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(match)
                            && !BuiltInRegistries.ITEM.getKey(key.stack().getItem()).toString().contains(match));
                    keys.sort((a, b) -> a.stack().getHoverName().getString().compareToIgnoreCase(b.stack().getHoverName().getString()));
                    for (ItemKey key : keys.subList(0, Math.min(200, keys.size()))) {
                        var id = BuiltInRegistries.ITEM.getKey(key.stack().getItem());
                        String value = id.getNamespace().equals("minecraft") ? id.getPath().toUpperCase(Locale.ROOT) : id.toString();
                        out.line(row(value, key.stack().getHoverName().getString()));
                    }
                }
            }
            case "DEVICES" -> {
                for (ElclDevices.Device device : ElclDevices.list(context.server(), context.network())) {
                    if (match.isEmpty() || device.type().equalsIgnoreCase(filter)) {
                        out.line(row(device.name(), device.type()));
                    }
                }
            }
            case "LIBRARIES" -> ElclServices.libraries().libraries(system).forEach(library -> out.line(row(library.name(), library.text())));
            case "MEMBERS" -> {
                for (String library : filter.isEmpty() ? OsCommands.libraryList(system, context.user()) : List.of(filter.toUpperCase(Locale.ROOT))) {
                    for (LibraryService.Member member : ElclServices.libraries().members(system, library)) {
                        out.line(row(library + "/" + member.name(), member.text()));
                    }
                }
            }
            case "PROGRAMS" -> {
                for (String library : filter.isEmpty() ? OsCommands.libraryList(system, context.user()) : List.of(filter.toUpperCase(Locale.ROOT))) {
                    for (String program : ElclServices.libraries().programs(system, library)) {
                        out.line(row(library + "/" + program, ""));
                    }
                }
            }
            case "JOBS" -> ElclServices.jobs().jobs(system).forEach(job -> out.line(row(job.qualified(), job.status())));
            case "SYSVALS" -> ElclServices.sysvals().values(system).forEach(sysval -> out.line(row(sysval.name(), sysval.description())));
            default -> {}
        }
        return out;
    }
}
