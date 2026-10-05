/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.elcl.cmd.Wait;
import net.zagdrath.encodedlogistics.elcl.device.DisplayDevice;
import net.zagdrath.encodedlogistics.elcl.device.Displays;
import net.zagdrath.encodedlogistics.elcl.device.Printers;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.JobData;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.part.CablePart;
import net.zagdrath.encodedlogistics.part.InventoryTapPart;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.part.PlanePart;
import net.zagdrath.encodedlogistics.part.PortPart;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.terminal.TerminalActions;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;

// The mod commands (COMMANDS.md 3-8) on the network the job runs on: inventory (counts, lists, moving items to a
// device or the desk, importing from one, tape tiers, storage status), crafting (start, status, end), devices
// (status, lists, enable / disable, filters, lanes), power, displays and printed reports. Their Auth is checked before
// they run (CommandRunner, the VM). Async: MOVITM waits for a recall from tape, STRCRAFT WAIT(*YES) for its job - in a
// program; on a command line they start it and say so.
public final class ModCommands {
    private ModCommands() {}

    static void bind() {
        inventory();
        crafting();
        devices();
        power();
        output();
    }

    // --- Where a command works ---

    private static ElclContext context(Invocation call) throws ElclException {
        ElclContext context = OsCommands.context(call);
        if (context.network() == null) {
            throw new ElclException("ELC1302", "*NETWORK");
        }
        return context;
    }

    private static NetworkStorage storage(ElclContext context) throws ElclException {
        NetworkStorage storage = ControllerStructures.sharedStorageOf(context.server(), context.network(), false);
        if (storage == null) {
            throw new ElclException("ELC1302", "*NETWORK");
        }
        return storage;
    }

    private static ElclDevices.Device device(ElclContext context, String name) throws ElclException {
        String wanted = name.trim().toUpperCase(Locale.ROOT);
        ElclDevices.Device device = ElclDevices.find(context.server(), context.network(), wanted);
        if (device == null) {
            throw new ElclException("ELC1301", wanted);
        }
        return device;
    }

    // *ONLINE, *OFFLINE, *FAULT or *DISABLED.
    public static String status(ElclDevices.Device device) {
        if (device.part() != null && !device.part().enabled()) {
            return "*DISABLED";
        }
        if (device.rack() != null && device.rack().status() == RackDeviceInfo.Status.FAULT) {
            return "*FAULT";
        }
        return device.online() ? "*ONLINE" : "*OFFLINE";
    }

    // The inventory a part faces (ELC1303 for a device that isn't a part, ELC1302 offline, ELC1304 when it faces none).
    private static ResourceHandler<ItemResource> faced(ElclDevices.Device device) throws ElclException {
        CablePart part = device.part();
        if (part == null) {
            throw new ElclException("ELC1303", device.name(), device.type());
        }
        if (!part.isOnline()) {
            throw new ElclException("ELC1302", device.name());
        }
        ResourceHandler<ItemResource> handler = part.host().getLevel() instanceof ServerLevel level && level.isLoaded(part.facing())
                ? level.getCapability(Capabilities.Item.BLOCK, part.facing(), part.side().getOpposite()) : null;
        if (handler == null) {
            throw new ElclException("ELC1304", device.name());
        }
        return handler;
    }

    // --- 3. Inventory ---

    private static void inventory() {
        CommandRegistry.bind("RTVITMCNT", call -> {
            NetworkStorage storage = storage(context(call));
            boolean zero = call.text("NOTFND").equals("*ZERO");
            Item item;
            try {
                item = ElclItems.resolve(call.text("ITEM"));
            } catch (ElclException e) {
                if (zero && e.elclMessage().id().equals("ELC1201")) {
                    call.returns("RTNCOUNT", 0L);
                    return;
                }
                throw e;
            }
            if (!zero && ElclItems.count(storage, item, "*ALL") == 0) {
                throw new ElclException("ELC1201", call.text("ITEM").toUpperCase(Locale.ROOT));
            }
            call.returns("RTNCOUNT", ElclItems.count(storage, item, call.text("TIER")));
        });
        CommandRegistry.bind("RTVITMLST", call -> {
            NetworkStorage storage = storage(context(call));
            String filter = call.text("FILTER");
            List<Map.Entry<Item, Long>> items = new ArrayList<>();
            for (Map.Entry<Item, Long> entry : ElclItems.totals(storage, call.text("TIER")).entrySet()) {
                if (entry.getValue() > 0 && ElclItems.matches(ItemKey.of(new ItemStack(entry.getKey())), filter)) {
                    items.add(entry);
                }
            }
            items.sort(call.text("SORT").equals("*QTY") ? Map.Entry.<Item, Long>comparingByValue().reversed()
                    : Comparator.comparing(entry -> ElclItems.id(entry.getKey())));
            long max = call.text("MAX").equals("*NOMAX") ? Long.MAX_VALUE : call.integer("MAX");
            List<String> ids = new ArrayList<>();
            for (Map.Entry<Item, Long> entry : items) {
                if (ids.size() >= max) {
                    break;
                }
                ids.add(ElclItems.id(entry.getKey()));
            }
            call.returns("RTNLST", ids);
        });
        CommandRegistry.bind("MOVITM", ModCommands::moveItems);
        CommandRegistry.bind("IMPITM", ModCommands::importItems);
        CommandRegistry.bind("CHGITMTIER", ModCommands::itemTier);
        CommandRegistry.bind("RTVSTGSTS", call -> {
            ElclContext context = context(call);
            NetworkStorage storage = storage(context);
            String tier = call.text("TIER");
            long used = 0, total = 0;
            if (!tier.equals("*COLD")) {
                long[] hot = storage.hotBytes();
                used += hot[0] + StoredLibraryService.storageBytes(new ElclSystem(context.server(), context.network()));
                total += hot[1];
            }
            if (!tier.equals("*HOT")) {
                for (RackDevice device : ControllerStructures.rackDevicesServing(context.server(), context.network())) {
                    if (device instanceof TapeLibraryDevice library && library.isOnline()) {
                        long[] cold = library.coldBytes();
                        used += cold[0];
                        total += cold[1];
                    }
                }
            }
            call.returns("RTNUSED", used);
            call.returns("RTNTOTAL", total);
            call.returns("RTNPCT", total <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(used * 100).divide(BigDecimal.valueOf(total), 5, RoundingMode.HALF_UP));
        });
    }

    // MOVITM: from the network to a device's faced inventory (or *DESK, the drawer of the Terminal Desk the job runs
    // at). Cold items are recalled first; a program waits for them.
    private static void moveItems(Invocation call) throws ElclException {
        ElclContext context = context(call);
        NetworkStorage storage = storage(context);
        Item item = ElclItems.resolve(call.text("ITEM"));
        long hot = ElclItems.count(storage, item, "*HOT"), all = ElclItems.count(storage, item, "*ALL");
        if (all <= 0) {
            throw new ElclException("ELC1201", ElclItems.id(item));
        }
        long want = call.text("QTY").equals("*ALL") ? all : call.integer("QTY");
        boolean partial = !call.text("PARTIAL").equals("*NO");
        if (!partial && all < want) {
            throw new ElclException("ELC1202", all, want);
        }
        String to = call.text("TODEV").toUpperCase(Locale.ROOT);
        TerminalDeskBlockEntity desk = null;
        ResourceHandler<ItemResource> target = null;
        if (to.equals("*DESK")) {
            TerminalContext terminal = call.context(TerminalContext.class);
            desk = terminal != null ? terminal.desk() : null;
            if (desk == null) {
                throw new ElclException("ELC1301", "*DESK");
            }
        } else {
            target = faced(device(context, to));
        }
        // Short of hot items with more on tape: recall them (a program waits for them, once).
        if (hot < want && all > hot && call.resumed() == null) {
            for (ItemKey key : ElclItems.keys(storage, item)) {
                long cold = storage.cold().count(key);
                if (cold > 0) {
                    storage.cold().recall(key, Math.min(cold, want - hot));
                }
            }
            call.send(ElclMessage.of("ELC1203", ElclItems.id(item)));
            if (call.canWait()) {
                call.await(Wait.of("RECALL", "item", ElclItems.id(item), "need", Long.toString(want)));
                return;
            }
        }
        long moved = 0;
        for (ItemKey key : ElclItems.keys(storage, item)) {
            long left = want - moved;
            if (left <= 0) {
                break;
            }
            moved += desk != null ? toDesk(storage, desk, key, left) : toHandler(storage, target, key, left);
        }
        call.returns("RTNMOVED", moved);
        if (!partial && moved < want) {
            throw new ElclException("ELC1202", moved, want);
        }
    }

    private static long toDesk(NetworkStorage storage, TerminalDeskBlockEntity desk, ItemKey key, long amount) {
        long moved = 0;
        while (moved < amount) {
            long chunk = Math.min(Math.min(amount - moved, key.maxStackSize()), Math.min(desk.drawerRoom(key), storage.count(key)));
            if (chunk <= 0) {
                break;
            }
            long taken = storage.extract(key, chunk, false);
            if (taken <= 0) {
                break;
            }
            ItemStack left = desk.addToDrawer(key.toStack((int) taken));
            if (!left.isEmpty()) {
                storage.insert(key, left.getCount(), false);
            }
            moved += taken - left.getCount();
            if (!left.isEmpty()) {
                break;
            }
        }
        return moved;
    }

    private static long toHandler(NetworkStorage storage, ResourceHandler<ItemResource> target, ItemKey key, long amount) {
        long moved = 0;
        ItemResource resource = ItemResource.of(key.stack());
        while (moved < amount) {
            int want = (int) Math.min(Integer.MAX_VALUE, Math.min(amount - moved, storage.count(key)));
            if (want <= 0) {
                break;
            }
            int room;
            try (Transaction transaction = Transaction.openRoot()) {
                room = target.insert(resource, want, transaction);
            }
            int taken = room > 0 ? (int) storage.extract(key, room, false) : 0;
            if (taken <= 0) {
                break;
            }
            int inserted;
            try (Transaction transaction = Transaction.openRoot()) {
                inserted = target.insert(resource, taken, transaction);
                transaction.commit();
            }
            if (inserted < taken) {
                storage.insert(key, taken - inserted, false);
            }
            moved += inserted;
            if (inserted < taken) {
                break;
            }
        }
        return moved;
    }

    // IMPITM: from a device's faced inventory into the network; ELC1204 when nothing would fit.
    private static void importItems(Invocation call) throws ElclException {
        ElclContext context = context(call);
        NetworkStorage storage = storage(context);
        ResourceHandler<ItemResource> source = faced(device(context, call.text("FROMDEV")));
        Item only = call.text("ITEM").equals("*ALL") ? null : ElclItems.resolve(call.text("ITEM"));
        long want = call.text("QTY").equals("*ALL") ? Long.MAX_VALUE : call.integer("QTY");
        long moved = 0;
        boolean blocked = false;
        for (int slot = 0; slot < source.size() && moved < want; slot++) {
            ItemResource resource = source.getResource(slot);
            if (resource.isEmpty() || only != null && !resource.toStack(1).is(only)) {
                continue;
            }
            ItemKey key = ItemKey.of(resource.toStack(1));
            int amount = (int) Math.min(want - moved, source.getAmountAsInt(slot));
            int fits = (int) storage.insert(key, amount, true);
            if (fits <= 0) {
                blocked = true;
                continue;
            }
            int extracted;
            try (Transaction transaction = Transaction.openRoot()) {
                extracted = source.extract(slot, resource, fits, transaction);
                transaction.commit();
            }
            int stored = (int) storage.insert(key, extracted, false);
            if (stored < extracted) {
                try (Transaction transaction = Transaction.openRoot()) {
                    source.insert(resource, extracted - stored, transaction);
                    transaction.commit();
                }
            }
            moved += stored;
        }
        call.returns("RTNMOVED", moved);
        if (moved == 0 && blocked) {
            throw new ElclException("ELC1204");
        }
    }

    // CHGITMTIER: through the Tape Libraries' lists - *HOT keeps it hot (and recalls what's on tape), *PIN pins it, *COLD
    // sends it to tape now, *AUTO leaves it to the archiving rules. ELC1206 with no Tape Library; ELC1305 when the list
    // it goes on is full.
    private static void itemTier(Invocation call) throws ElclException {
        ElclContext context = context(call);
        NetworkStorage storage = storage(context);
        Item item = ElclItems.resolve(call.text("ITEM"));
        List<TapeLibraryDevice> libraries = new ArrayList<>();
        for (RackDevice device : ControllerStructures.rackDevicesServing(context.server(), context.network())) {
            if (device instanceof TapeLibraryDevice library) {
                libraries.add(library);
            }
        }
        if (libraries.isEmpty()) {
            throw new ElclException("ELC1206");
        }
        // Off every list first.
        for (TapeLibraryDevice library : libraries) {
            boolean changed = false;
            PartFilter keep = library.keepHot();
            for (int i = 0; i < PartFilter.SIZE; i++) {
                if (keep.entries().get(i).is(item)) {
                    keep.set(i, ItemStack.EMPTY);
                    changed = true;
                }
            }
            for (int i = 0; i < library.pinned().size(); i++) {
                if (library.pinned().get(i).is(item)) {
                    library.pinned().set(i, ItemStack.EMPTY);
                    changed = true;
                }
            }
            if (changed) {
                library.policyChanged();
            }
        }
        String tier = call.text("TIER");
        switch (tier) {
            case "*HOT" -> {
                TapeLibraryDevice library = libraries.stream().filter(l -> l.keepHot().nonEmpty().size() < PartFilter.SIZE).findFirst()
                        .orElseThrow(() -> new ElclException("ELC1305", ElclDevices.code(libraries.getFirst())));
                PartFilter keep = library.keepHot();
                for (int i = 0; i < PartFilter.SIZE; i++) {
                    if (keep.entries().get(i).isEmpty()) {
                        keep.set(i, new ItemStack(item));
                        break;
                    }
                }
                library.policyChanged();
                for (ItemKey key : ElclItems.keys(storage, item)) {
                    long cold = storage.cold().count(key);
                    if (cold > 0) {
                        storage.cold().recall(key, cold);
                    }
                }
            }
            case "*PIN" -> {
                TapeLibraryDevice library = null;
                int slot = -1;
                for (TapeLibraryDevice candidate : libraries) {
                    for (int i = 0; i < candidate.pinned().size() && slot < 0; i++) {
                        if (candidate.pinned().get(i).isEmpty()) {
                            library = candidate;
                            slot = i;
                        }
                    }
                }
                if (library == null) {
                    throw new ElclException("ELC1305", ElclDevices.code(libraries.getFirst()));
                }
                library.pinned().set(slot, new ItemStack(item));
                library.policyChanged();
            }
            case "*COLD" -> {
                TapeLibraryDevice library = libraries.stream().filter(RackDevice::isOnline).findFirst().orElse(libraries.getFirst());
                for (ItemKey key : ElclItems.keys(storage, item)) {
                    if (storage.count(key) > 0) {
                        library.archiveNow(key);
                    }
                }
            }
            default -> {}
        }
    }

    // --- 4. Crafting ---

    private static void crafting() {
        CommandRegistry.bind("STRCRAFT", ModCommands::startCraft);
        // A job still on a Scheduler: *ACTIVE or *QUEUED; one that ended and is still in the history (CraftLog): *DONE,
        // *FAILED or *CANCELLED, its percent what it made of what was asked; ELC1404 once it's aged out.
        CommandRegistry.bind("RTVCRFSTS", call -> {
            ElclContext context = context(call);
            int number = craftNumber(call.text("CRFJOB"));
            UUID id = ControllerStructures.jobByNumber(context.server(), context.network(), number);
            if (id != null) {
                for (JobHost host : CraftRequests.schedulers(context.server(), context.network())) {
                    CraftingJob job = host.job(id);
                    if (job != null) {
                        call.returns("RTNSTS", job.running ? "*ACTIVE" : "*QUEUED");
                        call.returns("RTNPCT", percent(job.done(), job.total()));
                        return;
                    }
                }
            }
            CraftLog.Entry ended = CraftLog.byNumber(context.server(), context.network(), number);
            if (ended == null) {
                throw new ElclException("ELC1404", call.text("CRFJOB").toUpperCase(Locale.ROOT));
            }
            call.returns("RTNSTS", ended.status().special());
            call.returns("RTNPCT", ended.status() == CraftLog.Status.DONE ? BigDecimal.valueOf(100)
                    : ended.requested() <= 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(Math.min(ended.produced(), ended.requested()) * 100).divide(BigDecimal.valueOf(ended.requested()), 5,
                            RoundingMode.HALF_UP));
        });
        // RTVCRFLOG: the history's job IDs (C0042), newest first - ITEM() and STATUS() narrow it, MAX() caps it.
        CommandRegistry.bind("RTVCRFLOG", call -> {
            ElclContext context = context(call);
            String item = call.text("ITEM");
            String wanted = item.equals("*ALL") ? null : ElclItems.id(ElclItems.resolve(item));
            String status = call.text("STATUS");
            String max = call.text("MAX");
            int limit = max.equals("*NOMAX") ? Integer.MAX_VALUE : (int) call.integer("MAX");
            List<String> ids = new ArrayList<>();
            for (CraftLog.Entry entry : CraftLog.entries(context.server(), context.network())) {
                if (ids.size() >= limit) {
                    break;
                }
                if ((wanted == null || entry.item().equals(wanted)) && (status.equals("*ALL") || entry.status().special().equals(status))) {
                    ids.add(entry.jobId());
                }
            }
            call.returns("RTNLST", ids);
        });
        CommandRegistry.bind("ENDCRAFT", call -> {
            ElclContext context = context(call);
            UUID id = craftJob(context, call.text("CRFJOB"));
            for (JobHost host : CraftRequests.schedulers(context.server(), context.network())) {
                if (host.cancel(id)) {
                    return;
                }
            }
            throw new ElclException("ELC1404", call.text("CRFJOB").toUpperCase(Locale.ROOT));
        });
    }

    private static BigDecimal percent(int done, int total) {
        return total <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(done * 100L).divide(BigDecimal.valueOf(total), 5, RoundingMode.HALF_UP);
    }

    // C0042 (or 42) to its job; ELC1404 when no job has that number.
    private static UUID craftJob(ElclContext context, String text) throws ElclException {
        UUID id = ControllerStructures.jobByNumber(context.server(), context.network(), craftNumber(text));
        if (id == null) {
            throw new ElclException("ELC1404", text.trim().toUpperCase(Locale.ROOT));
        }
        return id;
    }

    // C0042 (or 42) as its number; ELC1404 when it isn't one.
    private static int craftNumber(String text) throws ElclException {
        String number = text.trim().toUpperCase(Locale.ROOT);
        try {
            return Integer.parseInt(number.startsWith("C") ? number.substring(1) : number);
        } catch (NumberFormatException e) {
            throw new ElclException("ELC1404", number);
        }
    }

    // The ELCL job a command runs in, as a crafting job records who asked ("000123/USER/NAME", and what submitted it):
    // empty in an interactive job (the player asked).
    private static String origin(ElclContext context) {
        String number = context.job();
        if (number == null || context.network() == null) {
            return "";
        }
        JobData.Batch batch = ElclStore.of(new ElclSystem(context.server(), context.network())).jobs.batch.get(number);
        if (batch == null) {
            return "";
        }
        return batch.source.isEmpty() ? batch.qualified() : batch.qualified() + " " + batch.source;
    }

    // STRCRAFT: plans the craft (MISSING(*PARTIAL): as many as can be made), picks a Scheduler and starts the job,
    // returning its ID (C0042). WAIT(*YES) in a program waits until it has ended.
    private static void startCraft(Invocation call) throws ElclException {
        Wait resumed = call.resumed();
        if (resumed != null && resumed.kind().equals("CRAFT")) {
            call.returns("RTNCRFJOB", resumed.get("id"));
            return;
        }
        ElclContext context = context(call);
        Item item = ElclItems.resolve(call.text("ITEM"));
        ItemKey key = null;
        for (ItemKey craftable : CraftRequests.craftables(context.server(), context.network())) {
            if (craftable.stack().is(item)) {
                key = craftable;
                break;
            }
        }
        if (key == null) {
            throw new ElclException("ELC1402", ElclItems.id(item));
        }
        List<JobHost> schedulers = CraftRequests.schedulers(context.server(), context.network());
        if (schedulers.isEmpty()) {
            throw new ElclException("ELC1401");
        }
        long amount = call.integer("QTY");
        CraftPlanner.Plan plan = CraftRequests.plan(context.server(), context.network(), key, amount);
        if (plan == null) {
            throw new ElclException("ELC1302", "*NETWORK");
        }
        if (plan.crafts().isEmpty()) {
            throw new ElclException("ELC1402", ElclItems.id(item));
        }
        if (!plan.complete()) {
            if (!call.text("MISSING").equals("*PARTIAL")) {
                throw new ElclException("ELC1403", ElclItems.id(item));
            }
            plan = mostMakeable(context, key, amount);
            if (plan == null) {
                throw new ElclException("ELC1403", ElclItems.id(item));
            }
        }
        String spec = call.text("SCHEDULER");
        int index = TerminalActions.scheduler(schedulers, spec.equals("*ANY") ? "*auto" : spec);
        JobHost host = index == -2 ? null : CraftRequests.choose(schedulers, plan.memory(), index);
        if (host == null) {
            throw new ElclException("ELC1401");
        }
        TerminalContext terminal = call.context(TerminalContext.class);
        Optional<UUID> player = terminal != null ? Optional.of(terminal.player().getUUID()) : Optional.empty();
        CraftingJob job = CraftRequests.start(context.server(), context.network(), plan, host,
                new CraftRequests.Requester(player, context.user(), context.user(), origin(context)));
        if (job == null) {
            throw new ElclException("ELC1403", ElclItems.id(item));
        }
        String id = String.format(Locale.ROOT, "C%04d", ControllerStructures.jobNumber(context.server(), context.network(), job.id));
        call.returns("RTNCRFJOB", id);
        if (call.text("WAIT").equals("*YES") && call.canWait()) {
            call.await(Wait.of("CRAFT", "job", job.id.toString(), "id", id));
        }
    }

    // The plan for the most of an item that can be made (under amount), or null when none can.
    private static CraftPlanner.@Nullable Plan mostMakeable(ElclContext context, ItemKey key, long amount) {
        long low = 0, high = amount - 1;
        CraftPlanner.Plan best = null;
        while (low < high) {
            long mid = (low + high + 1) / 2;
            CraftPlanner.Plan plan = CraftRequests.plan(context.server(), context.network(), key, mid);
            if (plan != null && plan.complete()) {
                low = mid;
                best = plan;
            } else {
                high = mid - 1;
            }
        }
        if (best == null && low >= 1) {
            best = CraftRequests.plan(context.server(), context.network(), key, low);
        }
        return best != null && best.complete() ? best : null;
    }

    // --- 5. Devices ---

    private static void devices() {
        CommandRegistry.bind("RTVDEVSTS", call -> {
            ElclDevices.Device device = device(context(call), call.text("DEV"));
            call.returns("RTNSTS", status(device));
            call.returns("RTNTYPE", device.type());
        });
        CommandRegistry.bind("RTVDEVLST", call -> {
            ElclContext context = context(call);
            String type = call.text("TYPE").toUpperCase(Locale.ROOT), status = call.text("STATUS");
            List<String> names = new ArrayList<>();
            for (ElclDevices.Device device : ElclDevices.list(context.server(), context.network())) {
                if ((type.equals("*ALL") || device.type().equals(type) || type.equals("ELDESK") && device.type().equals("DESK"))
                        && (status.equals("*ALL") || status(device).equals(status))) {
                    names.add(device.name());
                }
            }
            call.returns("RTNLST", names);
        });
        CommandRegistry.bind("CHGDEVSTS", call -> {
            ElclDevices.Device device = device(context(call), call.text("DEV"));
            if (device.part() == null) {
                throw new ElclException("ELC1303", device.name(), device.type());
            }
            device.part().setEnabled(call.text("STATUS").equals("*ENABLE"));
        });
        CommandRegistry.bind("CHGDEVFTR", call -> {
            ElclDevices.Device device = device(context(call), call.text("DEV"));
            CablePart part = device.part();
            PartFilter filter = part instanceof PortPart port ? port.filter() : part instanceof InventoryTapPart tap ? tap.filter()
                    : part instanceof PlanePart plane ? plane.filter() : null;
            if (filter == null) {
                throw new ElclException("ELC1303", device.name(), device.type());
            }
            String action = call.text("ACTION");
            if (action.equals("*CLR")) {
                for (int i = 0; i < PartFilter.SIZE; i++) {
                    filter.set(i, ItemStack.EMPTY);
                }
            } else {
                if (!call.given("ITEM")) {
                    throw new ElclException("ELC0102", "ITEM");
                }
                Item item = ElclItems.resolve(call.text("ITEM"));
                if (action.equals("*RMV")) {
                    for (int i = 0; i < PartFilter.SIZE; i++) {
                        if (filter.entries().get(i).is(item)) {
                            filter.set(i, ItemStack.EMPTY);
                        }
                    }
                } else if (filter.entries().stream().noneMatch(stack -> stack.is(item))) {
                    int free = filter.entries().indexOf(ItemStack.EMPTY);
                    for (int i = 0; i < PartFilter.SIZE && free < 0; i++) {
                        if (filter.entries().get(i).isEmpty()) {
                            free = i;
                        }
                    }
                    if (free < 0) {
                        throw new ElclException("ELC1305", device.name());
                    }
                    filter.set(free, new ItemStack(item));
                }
            }
            switch (part) {
                case PortPart port -> port.settingsChanged();
                case InventoryTapPart tap -> tap.settingsChanged();
                case PlanePart plane -> plane.settingsChanged();
                default -> {}
            }
        });
        CommandRegistry.bind("RTVLANES", call -> {
            ElclContext context = context(call);
            NetworkSnapshot snapshot = ControllerStructures.snapshotOf(context.server(), context.network());
            call.returns("RTNUSED", (long) snapshot.lanesUsed());
            call.returns("RTNTOTAL", (long) snapshot.laneCapacity());
        });
    }

    // --- 6. Power ---

    private static void power() {
        CommandRegistry.bind("RTVPWRSTS", call -> {
            ElclContext context = context(call);
            NetworkSnapshot snapshot = ControllerStructures.snapshotOf(context.server(), context.network());
            List<UpsDevice> upses = new ArrayList<>();
            for (RackDevice device : ControllerStructures.rackDevicesServing(context.server(), context.network())) {
                if (device instanceof UpsDevice ups && ups.isOnline()) {
                    upses.add(ups);
                }
            }
            boolean battery = upses.stream().anyMatch(UpsDevice::onBattery);
            boolean powered = snapshot.status() == NetworkStatus.ONLINE || snapshot.status() == NetworkStatus.FAILOVER;
            call.returns("RTNSRC", battery ? "*UPS" : powered ? "*NETWORK" : "*NONE");
            call.returns("RTNCHG", upses.isEmpty() ? BigDecimal.ZERO
                    : BigDecimal.valueOf(upses.stream().mapToInt(UpsDevice::percent).sum()).divide(BigDecimal.valueOf(upses.size()), 5, RoundingMode.HALF_UP));
            call.returns("RTNLOAD", Math.round(snapshot.usage()));
            call.returns("RTNSTORED", snapshot.stored());
        });
    }

    // --- 8. Displays and printed reports ---

    private static void output() {
        CommandRegistry.bind("SNDDSPTXT", call -> {
            ElclContext context = context(call);
            ElclSystem system = new ElclSystem(context.server(), context.network());
            String name = call.text("DEV").toUpperCase(Locale.ROOT);
            DisplayDevice display = Displays.find(system, name);
            if (display == null) {
                ElclDevices.Device device = ElclDevices.find(context.server(), context.network(), name);
                throw device != null ? new ElclException("ELC1303", name, device.type()) : new ElclException("ELC1301", name);
            }
            if (!display.online()) {
                throw new ElclException("ELC1302", name);
            }
            int line = call.text("LINE").equals("*NEXT") ? 0 : (int) call.integer("LINE");
            if (line > display.lines()) {
                throw new ElclException("ELC0004", line);
            }
            display.write(line, call.text("TEXT"), call.text("CLEAR").equals("*YES"));
        });
        CommandRegistry.bind("PRTRPT", call -> {
            ElclContext context = context(call);
            ElclSystem system = new ElclSystem(context.server(), context.network());
            String report = call.text("RPT");
            List<String> lines;
            String title = report.substring(1);
            switch (report) {
                case "*INV" -> lines = Reports.inventory(system, storage(context));
                case "*DEV" -> lines = Reports.devices(system);
                case "*JOBLOG" -> {
                    JobService.Job job = OsCommands.currentJob(call, system);
                    title = job.name();
                    lines = Reports.jobLog(system, job);
                }
                default -> {
                    JobService.Job job = OsCommands.currentJob(call, system);
                    String name = call.text("SPLF").toUpperCase(Locale.ROOT);
                    SpoolService.SpooledFile found = Reports.spooled(system, null, job.number(), name);
                    if (found == null) {
                        throw new ElclException("ELC0103", name, "SPLF");
                    }
                    title = found.name();
                    lines = Reports.spooled(system, found);
                }
            }
            call.send(Printers.print(system, call.text("DEV"), title, lines));
        });
    }
}
