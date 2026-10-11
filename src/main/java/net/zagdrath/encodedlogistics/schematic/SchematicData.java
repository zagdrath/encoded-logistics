/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.schematic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// What the Schematic Terminal shows, as the server sends it (SchematicTerminalPayload): the network's schematic holders
// with their slots and badges, its crafting jobs with their steps, and its schedulers.
public final class SchematicData {
    // Holder kinds, as the type filter cycles them.
    public static final int FABRICATOR = 0, GATEWAY = 1, SERVER = 2, MIDRANGE = 3, MACHINE = 4, KINDS = 5;
    // Badges.
    public static final int PARALLEL = 1, CONFLICT = 2, GATEWAY_PROBLEM = 4;
    // Job statuses.
    public static final int QUEUED = 0, ACTIVE = 1, WAITING = 2, STALLED = 3, HELD = 4, DONE = 5, FAILED = 6, CANCELLED = 7;
    // Step states (the step tree's icons).
    public static final int STEP_DONE = 0, STEP_ACTIVE = 1, STEP_WAITING = 2, STEP_STALLED = 3, STEP_MISSING = 4;

    private SchematicData() {}

    // A holder: its key (which block, and which rack unit), name ("FAB01", "GATEWAY01 > Etcher"), where it is ("212 64
    // -88" or "RACK01 U14"), icon, kind; read-only (a Midrange System's diskettes: edited with the Card Reader); whether
    // it can be renamed (it has a device name); its slots; its badges, how many other holders make its outputs, and for a
    // conflict the output and the holder whose recipe the planner uses.
    public record Holder(String key, String name, String location, ItemStack icon, int kind, boolean readOnly, boolean renamable,
            List<ItemStack> slots, int badges, int parallel, String conflictItem, String conflictPreferred) {
        public int used() {
            return (int) slots.stream().filter(stack -> !stack.isEmpty()).count();
        }

        static void write(RegistryFriendlyByteBuf buf, Holder holder) {
            buf.writeUtf(holder.key);
            buf.writeUtf(holder.name);
            buf.writeUtf(holder.location);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, holder.icon);
            buf.writeVarInt(holder.kind);
            buf.writeBoolean(holder.readOnly);
            buf.writeBoolean(holder.renamable);
            buf.writeVarInt(holder.slots.size());
            holder.slots.forEach(stack -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack));
            buf.writeVarInt(holder.badges);
            buf.writeVarInt(holder.parallel);
            buf.writeUtf(holder.conflictItem);
            buf.writeUtf(holder.conflictPreferred);
        }

        static Holder read(RegistryFriendlyByteBuf buf) {
            String key = buf.readUtf(), name = buf.readUtf(), location = buf.readUtf();
            ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int kind = buf.readVarInt();
            boolean readOnly = buf.readBoolean(), renamable = buf.readBoolean();
            int count = Math.min(buf.readVarInt(), 4_096);
            List<ItemStack> slots = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                slots.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            return new Holder(key, name, location, icon, kind, readOnly, renamable, slots, buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }
    }

    // A job's step: what it makes (and how many in all), runs done of total, its state, and why it's stuck (a reason key,
    // "" for none); locatable: a holder for it can be located.
    public record Step(ItemStack item, long amount, int done, int total, int state, String reason, boolean locatable) {
        static void write(RegistryFriendlyByteBuf buf, Step step) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, step.item);
            buf.writeVarLong(step.amount);
            buf.writeVarInt(step.done);
            buf.writeVarInt(step.total);
            buf.writeVarInt(step.state);
            buf.writeUtf(step.reason);
            buf.writeBoolean(step.locatable);
        }

        static Step read(RegistryFriendlyByteBuf buf) {
            return new Step(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(), buf.readBoolean());
        }
    }

    // A job: its id and number, what it makes and how many, status, runs done of total, its scheduler (short name),
    // ticks since it started, ticks to go (-1: not known), who asked, whether that's the viewer, why it's stalled (a reason
    // key, ""), its steps (none for an ended job), and an ended job's history record ("C0042").
    public record Job(UUID id, int number, StorageKey target, long amount, int status, int done, int total, String scheduler, long elapsed,
            long eta, String requester, boolean mine, String reason, List<Step> steps, String record) {
        public boolean ended() {
            return status >= DONE;
        }

        static void write(RegistryFriendlyByteBuf buf, Job job) {
            UUIDUtil.STREAM_CODEC.encode(buf, job.id);
            buf.writeVarInt(job.number);
            StorageKey.STREAM_CODEC.encode(buf, job.target);
            buf.writeVarLong(job.amount);
            buf.writeVarInt(job.status);
            buf.writeVarInt(job.done);
            buf.writeVarInt(job.total);
            buf.writeUtf(job.scheduler);
            buf.writeVarLong(job.elapsed);
            buf.writeLong(job.eta);
            buf.writeUtf(job.requester);
            buf.writeBoolean(job.mine);
            buf.writeUtf(job.reason);
            buf.writeVarInt(job.steps.size());
            job.steps.forEach(step -> Step.write(buf, step));
            buf.writeUtf(job.record);
        }

        static Job read(RegistryFriendlyByteBuf buf) {
            UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
            int number = buf.readVarInt();
            StorageKey target = StorageKey.STREAM_CODEC.decode(buf);
            long amount = buf.readVarLong();
            int status = buf.readVarInt(), done = buf.readVarInt(), total = buf.readVarInt();
            String scheduler = buf.readUtf();
            long elapsed = buf.readVarLong(), eta = buf.readLong();
            String requester = buf.readUtf();
            boolean mine = buf.readBoolean();
            String reason = buf.readUtf();
            int count = Math.min(buf.readVarInt(), 1_024);
            List<Step> steps = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                steps.add(Step.read(buf));
            }
            return new Job(id, number, target, amount, status, done, total, scheduler, elapsed, eta, requester, mine, reason, steps, buf.readUtf());
        }
    }

    // A scheduler: its short name and its threads and job memory in use.
    public record Scheduler(String name, int threadsUsed, int threads, long memoryUsed, long memory) {
        static void write(RegistryFriendlyByteBuf buf, Scheduler scheduler) {
            buf.writeUtf(scheduler.name);
            buf.writeVarInt(scheduler.threadsUsed);
            buf.writeVarInt(scheduler.threads);
            buf.writeVarLong(scheduler.memoryUsed);
            buf.writeVarLong(scheduler.memory);
        }

        static Scheduler read(RegistryFriendlyByteBuf buf) {
            return new Scheduler(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong());
        }
    }

    // Everything at once. online: the terminal reaches its network and the player may view it; edit: may move schematics
    // and rename (BUILD); craft: may cancel or hold any job (CRAFT; their own they always may).
    public record Snapshot(boolean online, boolean edit, boolean craft, List<Holder> holders, List<Job> jobs, List<Scheduler> schedulers) {
        public static final Snapshot EMPTY = new Snapshot(false, false, false, List.of(), List.of(), List.of());

        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(Snapshot::write, Snapshot::read);

        static void write(RegistryFriendlyByteBuf buf, Snapshot snapshot) {
            buf.writeBoolean(snapshot.online);
            buf.writeBoolean(snapshot.edit);
            buf.writeBoolean(snapshot.craft);
            buf.writeVarInt(snapshot.holders.size());
            snapshot.holders.forEach(holder -> Holder.write(buf, holder));
            buf.writeVarInt(snapshot.jobs.size());
            snapshot.jobs.forEach(job -> Job.write(buf, job));
            buf.writeVarInt(snapshot.schedulers.size());
            snapshot.schedulers.forEach(scheduler -> Scheduler.write(buf, scheduler));
        }

        static Snapshot read(RegistryFriendlyByteBuf buf) {
            boolean online = buf.readBoolean(), edit = buf.readBoolean(), craft = buf.readBoolean();
            int holders = Math.min(buf.readVarInt(), 4_096);
            List<Holder> holderList = new ArrayList<>(holders);
            for (int i = 0; i < holders; i++) {
                holderList.add(Holder.read(buf));
            }
            int jobs = Math.min(buf.readVarInt(), 4_096);
            List<Job> jobList = new ArrayList<>(jobs);
            for (int i = 0; i < jobs; i++) {
                jobList.add(Job.read(buf));
            }
            int schedulers = Math.min(buf.readVarInt(), 1_024);
            List<Scheduler> schedulerList = new ArrayList<>(schedulers);
            for (int i = 0; i < schedulers; i++) {
                schedulerList.add(Scheduler.read(buf));
            }
            return new Snapshot(online, edit, craft, holderList, jobList, schedulerList);
        }
    }
}
