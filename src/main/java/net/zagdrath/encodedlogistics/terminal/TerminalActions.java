/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.RackScheduler;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.ResourceContainers;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// What the Terminal Desk does for its screens and its command line alike: withdraw (to the desk's drawer or the
// player's inventory; what's on tape is recalled and follows when it's back), craft (optionally sent to the drawer or
// inventory once done), cancel a job, sum up a plan, and list the network's jobs.
public final class TerminalActions {
    public enum Destination {
        DRAWER, INV, NETWORK;

        public static @Nullable Destination parse(String text) {
            return switch (text.trim().toUpperCase(Locale.ROOT)) {
                case "*DRAWER", "DRAWER" -> DRAWER;
                case "*INV", "INV" -> INV;
                case "*NETWORK", "NETWORK" -> NETWORK;
                default -> null;
            };
        }

        public Component label() {
            return Component.translatable("crt.encodedlogistics.dest." + name().toLowerCase(Locale.ROOT));
        }
    }

    private TerminalActions() {}

    public static Component notAuthorised(RackPermission permission) {
        return Component.translatable("crt.encodedlogistics.msg.not_authorised", permission.label());
    }

    public static Component offline() {
        return Component.translatable("crt.encodedlogistics.msg.offline");
    }

    // --- Withdrawing ---

    // DEPOSIT: stacks from the player's inventory into the network - the slots given, or *all (everything outside the
    // hotbar). What doesn't fit stays with them.
    public static TerminalOutput deposit(TerminalContext context, List<String> slots) {
        if (!context.allowed(RackPermission.INSERT)) {
            return TerminalOutput.message(notAuthorised(RackPermission.INSERT));
        }
        NetworkStorage storage = context.storage();
        if (storage == null) {
            return TerminalOutput.message(offline());
        }
        Inventory inventory = context.player().getInventory();
        List<Integer> chosen = new ArrayList<>();
        if (slots.size() == 1 && slots.getFirst().equalsIgnoreCase("*all")) {
            for (int slot = Inventory.getSelectionSize(); slot < INVENTORY_SLOTS; slot++) {
                chosen.add(slot);
            }
        } else {
            for (String slot : slots) {
                try {
                    int at = Integer.parseInt(slot);
                    if (at >= 0 && at < INVENTORY_SLOTS) {
                        chosen.add(at);
                    }
                } catch (NumberFormatException e) {
                    return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.invalid_value", slot));
                }
            }
        }
        long put = 0, kept = 0;
        for (int slot : chosen) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            long in = storage.insert(StorageKey.of(stack), stack.getCount(), false);
            put += in;
            kept += stack.getCount() - in;
            stack.shrink((int) in);
        }
        inventory.setChanged();
        if (put == 0) {
            return TerminalOutput.message(Component.translatable(kept > 0 ? "crt.encodedlogistics.msg.deposit_full" : "crt.encodedlogistics.msg.deposit_none"));
        }
        return TerminalOutput.message(kept > 0 ? Component.translatable("crt.encodedlogistics.msg.deposited_part", TerminalItems.count(put), TerminalItems.count(kept))
                : Component.translatable("crt.encodedlogistics.msg.deposited", TerminalItems.count(put)));
    }

    // The player's inventory a deposit takes from: the hotbar and the main inventory (not armour or the offhand).
    private static final int INVENTORY_SLOTS = 36;

    public static TerminalOutput withdraw(TerminalContext context, StorageKey key, long amount, Destination destination) {
        if (!context.allowed(RackPermission.EXTRACT)) {
            return TerminalOutput.message(notAuthorised(RackPermission.EXTRACT));
        }
        NetworkStorage storage = context.storage();
        TerminalDeskBlockEntity desk = context.desk();
        if (storage == null) {
            return TerminalOutput.message(offline());
        }
        if (destination == Destination.DRAWER && desk == null) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_drawer"));
        }
        if (destination == Destination.NETWORK) {
            destination = Destination.DRAWER;
        }
        if (!key.isItem()) {
            return withdrawResource(context, storage, desk, key, amount, destination);
        }
        long hot = storage.count(key), cold = storage.cold().count(key);
        if (hot + cold <= 0) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.none", key.stack().getHoverName()));
        }
        long room = destination == Destination.DRAWER ? desk.drawerRoom(key) : Long.MAX_VALUE;
        long taken = storage.extract(key, Math.min(Math.min(amount, room), hot), false);
        long given = give(context, key, taken, destination, storage);
        long remaining = amount - given;
        if (remaining > 0 && cold > 0 && desk != null && (destination == Destination.INV || room > given)) {
            long recall = Math.min(remaining, cold);
            storage.cold().recall(key, recall);
            desk.addDelivery(new TerminalDeskBlockEntity.Delivery(Optional.empty(), key, recall,
                    destination == Destination.INV ? Optional.of(context.player().getUUID()) : Optional.empty()));
            int eta = Math.max(0, storage.cold().eta(key));
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.recall", key.stack().getHoverName(), TerminalItems.count(recall),
                    destination.label(), TerminalItems.seconds(eta, true)));
        }
        if (given < Math.min(amount, hot) && destination == Destination.DRAWER) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.drawer_full", TerminalItems.count(Math.min(amount, hot) - given)));
        }
        return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.withdrawn", TerminalItems.count(given), key.stack().getHoverName(),
                destination.label()));
    }

    // A fluid or gas withdrawn: into the empty (or part-filled) containers in the drawer or the player's inventory, as far
    // as they take it.
    private static TerminalOutput withdrawResource(TerminalContext context, NetworkStorage storage, @Nullable TerminalDeskBlockEntity desk, StorageKey key,
            long amount, Destination destination) {
        if (storage.count(key) <= 0) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.none", key.displayName()));
        }
        List<ItemAccess> containers = new ArrayList<>();
        if (destination == Destination.DRAWER && desk != null) {
            ResourceHandler<ItemResource> drawer = VanillaContainerWrapper.of(desk);
            for (int slot = 0; slot < drawer.size(); slot++) {
                containers.add(ItemAccess.forHandlerIndex(drawer, slot));
            }
        } else {
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                containers.add(ItemAccess.forPlayerSlot(context.player(), slot));
            }
        }
        long given = ResourceContainers.fillFrom(storage, key, amount, containers);
        if (given <= 0) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_container", key.displayName(), destination.label()));
        }
        return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.withdrawn", key.format(given), key.displayName(),
                destination.label()));
    }

    // Puts taken items where they go; what doesn't fit goes back to the network. Returns how many went.
    private static long give(TerminalContext context, StorageKey key, long count, Destination destination, NetworkStorage storage) {
        long given = 0, left = count;
        while (left > 0) {
            ItemStack stack = key.toStack((int) Math.min(left, key.maxStackSize()));
            left -= stack.getCount();
            int size = stack.getCount();
            ItemStack rest;
            if (destination == Destination.INV) {
                context.player().getInventory().add(stack);
                rest = stack;
            } else {
                rest = context.desk().addToDrawer(stack);
            }
            given += size - rest.getCount();
            if (!rest.isEmpty()) {
                storage.insert(StorageKey.of(rest), rest.getCount(), false);
            }
        }
        return given;
    }

    // --- Crafting ---

    // The plan in a line: steps, missing items, recall time.
    public static Component planSummary(TerminalContext context, StorageKey key, long amount) {
        CraftPlanner.Plan plan = CraftRequests.plan(context.server(), context.network(), key, amount);
        if (plan == null) {
            return offline();
        }
        return Component.translatable("crt.encodedlogistics.craft.plan_summary", plan.crafts().size(), plan.missing(),
                TerminalItems.seconds(plan.recallTicks(), true));
    }

    // A scheduler's name as the screens show it: "Core x, y, z" or "Rack x, y, z".
    public static String schedulerName(JobHost host) {
        BlockPos pos = host.hostPos();
        return (host instanceof RackScheduler ? "Rack " : "Core ") + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    // *AUTO (-1), a number (1 the first), or the start of a scheduler's name; -2 when nothing matches.
    public static int scheduler(List<JobHost> schedulers, String spec) {
        String wanted = spec.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty() || wanted.equals("*auto")) {
            return -1;
        }
        long number = TerminalItems.amount(wanted);
        if (number >= 1 && number <= schedulers.size()) {
            return (int) number - 1;
        }
        for (int i = 0; i < schedulers.size(); i++) {
            if (schedulerName(schedulers.get(i)).toLowerCase(Locale.ROOT).startsWith(wanted)) {
                return i;
            }
        }
        return -2;
    }

    public static TerminalOutput craft(TerminalContext context, StorageKey key, long amount, String schedulerSpec, Destination destination) {
        if (!context.allowed(RackPermission.CRAFT)) {
            return TerminalOutput.message(notAuthorised(RackPermission.CRAFT));
        }
        // A delivery waits on the desk; an Integrated system's console has none, so the job's output stays in the network.
        if (destination != Destination.NETWORK && context.desk() == null) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_desk_delivery"));
        }
        CraftPlanner.Plan plan = CraftRequests.plan(context.server(), context.network(), key, amount);
        if (plan == null) {
            return TerminalOutput.message(offline());
        }
        if (plan.crafts().isEmpty()) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_schematic", key.stack().getHoverName()));
        }
        if (!plan.complete()) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.missing", key.stack().getHoverName(), plan.missing()));
        }
        List<JobHost> schedulers = CraftRequests.schedulers(context.server(), context.network());
        int index = scheduler(schedulers, schedulerSpec);
        JobHost host = index >= -1 ? CraftRequests.choose(schedulers, plan.memory(), index) : null;
        if (host == null) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_scheduler"));
        }
        CraftingJob job = CraftRequests.start(context.server(), context.network(), plan, host,
                new CraftRequests.Requester(Optional.of(context.player().getUUID()), context.user(), ""));
        if (job == null) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.not_started"));
        }
        if (destination != Destination.NETWORK && context.desk() != null) {
            context.desk().addDelivery(new TerminalDeskBlockEntity.Delivery(Optional.of(job.id), key, amount,
                    destination == Destination.INV ? Optional.of(context.player().getUUID()) : Optional.empty()));
        }
        return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.job_submitted", context.jobNumber(job.id)));
    }

    public static TerminalOutput cancel(TerminalContext context, int number) {
        if (!context.allowed(RackPermission.CRAFT)) {
            return TerminalOutput.message(notAuthorised(RackPermission.CRAFT));
        }
        UUID id = context.job(number);
        String shown = String.format("%04d", number);
        for (JobHost host : CraftRequests.schedulers(context.server(), context.network())) {
            if (id != null && host.job(id) != null && host.cancel(id)) {
                return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.cancelled", shown));
            }
        }
        return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_job", shown));
    }

    // --- Jobs ---

    // A job as Work with Jobs lists it.
    // scheduler: its full name ("Rack 12, 64, -30"); schedulerShort: its number (as CRAFT's scheduler takes it) and kind.
    public record JobRow(String number, UUID id, StorageKey item, long amount, String status, int percent, String scheduler, String schedulerShort,
            CraftingJob job) {}

    public static List<JobRow> jobs(TerminalContext context) {
        List<JobRow> rows = new ArrayList<>();
        List<JobHost> schedulers = CraftRequests.schedulers(context.server(), context.network());
        for (int i = 0; i < schedulers.size(); i++) {
            JobHost host = schedulers.get(i);
            String brief = (i + 1) + (host instanceof RackScheduler ? " Rack" : " Core");
            for (CraftingJob job : host.jobs()) {
                String status = !job.awaiting.isEmpty() ? "Recall" : job.running ? "Active" : "Waiting";
                int percent = job.total() <= 0 ? 0 : job.done() * 100 / job.total();
                rows.add(new JobRow(context.jobNumber(job.id), job.id, job.target, job.amount, status, percent, schedulerName(host), brief, job));
            }
        }
        rows.sort((a, b) -> a.number().compareTo(b.number()));
        return rows;
    }
}
