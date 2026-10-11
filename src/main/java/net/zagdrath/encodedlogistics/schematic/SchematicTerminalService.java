/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.schematic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.net.LocatePayload;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.RackScheduler;

// The Schematic Terminal's server side: what it shows (snapshot) and what its clicks do (SchematicTerminalActionPayload).
// Viewing needs the Firewall's VIEW; moving schematics and renaming holders BUILD; cancelling, holding or releasing a
// job CRAFT, or being the one who asked for it.
public final class SchematicTerminalService {
    // Actions: take a slot's schematic onto the cursor; put the carried one in a slot; put an inventory stack (slot index
    // in the menu) into a holder's free slots; rename a holder; locate a holder, or the holder of a job's step; cancel,
    // hold or release a job.
    public static final int TAKE = 0, PUT = 1, QUICK_PUT = 2, RENAME = 3, LOCATE = 4, LOCATE_STEP = 5, CANCEL = 6, HOLD = 7, RELEASE = 8;
    // Ended jobs shown with the rest (the Jobs tab's "all").
    private static final int RECENT = 20;

    private SchematicTerminalService() {}

    // --- What it shows ---

    public static SchematicData.Snapshot snapshot(ServerPlayer player, @Nullable NetworkRef home, @Nullable NetworkRef network) {
        MinecraftServer server = player.level().getServer();
        if (network == null || !NetworkAccess.allowed(server, home, player, RackPermission.VIEW)) {
            return SchematicData.Snapshot.EMPTY;
        }
        boolean edit = NetworkAccess.allowed(server, home, player, RackPermission.BUILD);
        boolean craft = NetworkAccess.allowed(server, home, player, RackPermission.CRAFT);
        List<SchematicHolders.Holder> holders = SchematicHolders.of(server, network);
        List<SchematicData.Holder> holderViews = SchematicHolders.views(server, network, holders);
        List<SchematicData.Job> jobs = new ArrayList<>();
        List<SchematicData.Scheduler> schedulers = new ArrayList<>();
        List<CraftingProvider> online = ControllerStructures.providersOf(server, network);
        long now = server.overworld().getGameTime();
        List<JobHost> hosts = CraftRequests.schedulers(server, network);
        List<ElclDevices.Device> devices = ElclDevices.list(server, network);
        for (int i = 0; i < hosts.size(); i++) {
            JobHost host = hosts.get(i);
            String name = schedulerName(hosts, host, devices);
            schedulers.add(new SchematicData.Scheduler(name, host.threadsUsed(), host.threads(), host.memoryUsed(), host.memory()));
            for (CraftingJob job : host.jobs()) {
                jobs.add(job(server, network, player, host, name, job, holders, online, now));
            }
        }
        for (CraftLog.Entry entry : CraftLog.entries(server, network)) {
            if (jobs.stream().filter(SchematicData.Job::ended).count() >= RECENT) {
                break;
            }
            int status = switch (entry.status()) {
                case DONE -> SchematicData.DONE;
                case FAILED -> SchematicData.FAILED;
                case CANCELLED -> SchematicData.CANCELLED;
            };
            boolean mine = entry.user().equalsIgnoreCase(player.getGameProfile().name());
            jobs.add(new SchematicData.Job(entry.id(), entry.number(), net.zagdrath.encodedlogistics.storage.StorageKey.of(CraftLog.stack(entry)),
                    entry.requested(), status, (int) Math.min(Integer.MAX_VALUE, entry.produced()), (int) Math.min(Integer.MAX_VALUE, entry.requested()),
                    entry.scheduler(), entry.duration(), -1, entry.requestedBy(), mine, entry.reason(), List.of(), entry.jobId()));
        }
        return new SchematicData.Snapshot(true, edit, craft, holderViews, jobs, schedulers);
    }

    // A scheduler's short name: SCHED01 (Scheduler Cores, in order), RACKSCHED (the network's rack Scheduler), a Midrange
    // System's device name.
    public static String schedulerName(List<JobHost> hosts, JobHost host, List<ElclDevices.Device> devices) {
        if (host instanceof RackScheduler) {
            return "RACKSCHED";
        }
        if (host instanceof MidrangeSystemBlockEntity midrange && midrange.getLevel() != null) {
            String name = ElclDevices.nameAt(devices, NodePos.of(midrange.getLevel().dimension(), midrange.getBlockPos()), null);
            return name.isEmpty() ? MidrangeSystemBlockEntity.TYPE : name;
        }
        int number = 0;
        for (JobHost other : hosts) {
            if (other instanceof SchedulerCoreBlockEntity) {
                number++;
            }
            if (other == host) {
                break;
            }
        }
        return String.format(Locale.ROOT, "SCHED%02d", Math.max(1, number));
    }

    private static SchematicData.Job job(MinecraftServer server, NetworkRef network, Player player, JobHost host, String scheduler, CraftingJob job,
            List<SchematicHolders.Holder> holders, List<CraftingProvider> online, long now) {
        boolean held = host.isHeld(job.id);
        int status = held ? SchematicData.HELD : !job.running ? SchematicData.QUEUED : job.stalled ? SchematicData.STALLED
                : !job.awaiting.isEmpty() ? SchematicData.WAITING : SchematicData.ACTIVE;
        long elapsed = job.started >= 0 ? Math.max(0, now - job.started) : 0;
        int done = job.done(), total = job.total();
        long eta = job.running && done > 0 && total > done ? elapsed * (total - done) / done : -1;
        String requester = !job.user.isEmpty() ? job.user
                : job.requester.map(id -> server.getPlayerList().getPlayer(id)).map(found -> found.getGameProfile().name()).orElse("");
        boolean mine = job.requester.map(id -> id.equals(player.getUUID())).orElse(false);
        List<SchematicData.Step> steps = steps(job, holders, online);
        return new SchematicData.Job(job.id, ControllerStructures.jobNumber(server, network, job.id), job.target, job.amount, status, done, total, scheduler,
                elapsed, eta, requester, mine, job.stalled && job.stallReason != null ? job.stallReason : "", steps, "");
    }

    // A job's steps as the Jobs tab, WRKCRFJOB's 8=Display steps and RTVCRFSTP show them: each step's item and how many
    // in all, runs done of total, its state (done, running, stalled - the step a stalled job is stuck on -, nobody online
    // holds its schematic, or waiting), a stalled step's reason, and whether a holder of it can be located.
    public static List<SchematicData.Step> steps(MinecraftServer server, @Nullable NetworkRef network, CraftingJob job) {
        return steps(job, SchematicHolders.of(server, network), ControllerStructures.providersOf(server, network));
    }

    private static List<SchematicData.Step> steps(CraftingJob job, List<SchematicHolders.Holder> holders, List<CraftingProvider> online) {
        List<SchematicData.Step> steps = new ArrayList<>();
        for (int index = 0; index < job.steps.size(); index++) {
            CraftingJob.Step step = job.steps.get(index);
            ItemStack output = step.schematic.output().copy();
            long amount = (long) Math.max(1, output.getCount()) * step.total;
            output.setCount(1);
            boolean able = online.stream().anyMatch(provider -> provider.accepts(step.schematic));
            int state = step.done >= step.total ? SchematicData.STEP_DONE
                    : job.stalled && index == job.stallStep ? SchematicData.STEP_STALLED
                    : step.running > 0 ? SchematicData.STEP_ACTIVE
                    : !able ? SchematicData.STEP_MISSING : SchematicData.STEP_WAITING;
            String reason = state == SchematicData.STEP_STALLED && job.stallReason != null ? job.stallReason : "";
            boolean locatable = holders.stream().anyMatch(holder -> holder.holds(step.schematic));
            steps.add(new SchematicData.Step(output, amount, Math.min(step.done, step.total), step.total, state, reason, locatable));
        }
        return steps;
    }

    // A running or queued job by id, or null.
    public static @Nullable CraftingJob findJob(MinecraftServer server, @Nullable NetworkRef network, UUID id) {
        for (JobHost host : CraftRequests.schedulers(server, network)) {
            CraftingJob job = host.job(id);
            if (job != null) {
                return job;
            }
        }
        return null;
    }

    // ELCL's names for step states and stall reasons (RTVCRFSTP).
    public static String stateSpecial(int state) {
        return switch (state) {
            case SchematicData.STEP_DONE -> "*DONE";
            case SchematicData.STEP_ACTIVE -> "*ACTIVE";
            case SchematicData.STEP_STALLED -> "*STALLED";
            case SchematicData.STEP_MISSING -> "*MISSING";
            default -> "*WAITING";
        };
    }

    public static String reasonSpecial(@Nullable String reason) {
        if (reason == null) {
            return "*UNKNOWN";
        }
        return switch (reason) {
            case "tape" -> "*TAPE";
            case "missing_machine" -> "*NOMACHINE";
            case "unpowered" -> "*NOPOWER";
            case "output_full" -> "*OUTPUTFULL";
            case "gateway_blocked" -> "*BLOCKED";
            case "ingredients" -> "*INGREDIENTS";
            default -> "*UNKNOWN";
        };
    }

    // --- What its clicks do ---

    public static void act(ServerPlayer player, AbstractContainerMenu menu, @Nullable NetworkRef home, @Nullable NetworkRef network, int action,
            String holderKey, int slot, Optional<UUID> jobId, String text) {
        MinecraftServer server = player.level().getServer();
        if (network == null || !NetworkAccess.allowed(server, home, player, RackPermission.VIEW)) {
            return;
        }
        switch (action) {
            case TAKE, PUT, QUICK_PUT, RENAME -> {
                if (!NetworkAccess.allowed(server, home, player, RackPermission.BUILD)) {
                    player.sendOverlayMessage(Component.translatable("gui.encodedlogistics.schematic.denied").withStyle(ChatFormatting.RED));
                    return;
                }
                SchematicHolders.Holder holder = SchematicHolders.find(SchematicHolders.of(server, network), holderKey);
                if (holder == null || holder.readOnly() && action != RENAME) {
                    return;
                }
                switch (action) {
                    case TAKE -> take(menu, holder, slot);
                    case PUT -> put(menu, holder, slot);
                    case QUICK_PUT -> quickPut(menu, holder, slot);
                    default -> rename(player, server, network, holder, text);
                }
            }
            case LOCATE -> {
                SchematicHolders.Holder holder = SchematicHolders.find(SchematicHolders.of(server, network), holderKey);
                if (holder != null) {
                    locate(player, server, holder);
                }
            }
            case LOCATE_STEP -> jobId.ifPresent(id -> locateStep(player, server, network, id, slot));
            case CANCEL, HOLD, RELEASE -> jobId.ifPresent(id -> jobAction(player, server, home, network, id, action));
            default -> {}
        }
    }

    // A slot's schematic onto the empty cursor.
    private static void take(AbstractContainerMenu menu, SchematicHolders.Holder holder, int slot) {
        if (slot < 0 || slot >= holder.slots() || !menu.getCarried().isEmpty()) {
            return;
        }
        ItemStack stack = holder.get(slot);
        if (stack.isEmpty()) {
            return;
        }
        holder.set(slot, ItemStack.EMPTY);
        menu.setCarried(stack.copy());
    }

    // The carried schematic into a slot: one of them into an empty slot, or swapped with what's there when one is carried.
    private static void put(AbstractContainerMenu menu, SchematicHolders.Holder holder, int slot) {
        ItemStack carried = menu.getCarried();
        if (slot < 0 || slot >= holder.slots() || carried.isEmpty() || !holder.accepts(slot, carried)) {
            return;
        }
        ItemStack there = holder.get(slot);
        if (there.isEmpty()) {
            holder.set(slot, carried.split(1));
            menu.setCarried(carried);
        } else if (carried.getCount() == 1) {
            holder.set(slot, carried.copy());
            menu.setCarried(there.copy());
        }
    }

    // An inventory stack into the holder's free slots, one a slot.
    private static void quickPut(AbstractContainerMenu menu, SchematicHolders.Holder holder, int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= menu.slots.size()) {
            return;
        }
        ItemStack stack = menu.slots.get(inventorySlot).getItem();
        for (int slot = 0; slot < holder.slots() && !stack.isEmpty(); slot++) {
            if (holder.get(slot).isEmpty() && holder.accepts(slot, stack)) {
                holder.set(slot, stack.split(1));
            }
        }
        menu.slots.get(inventorySlot).setChanged();
    }

    private static void rename(ServerPlayer player, MinecraftServer server, NetworkRef network, SchematicHolders.Holder holder, String text) {
        if (holder.deviceName().isEmpty()) {
            return;
        }
        try {
            ElclDevices.rename(server, network, holder.deviceName(), text);
        } catch (ElclException e) {
            player.sendOverlayMessage(Component.literal(e.getMessage() != null ? e.getMessage() : e.toString()).withStyle(ChatFormatting.RED));
        }
    }

    // The outline (CrtLocate, through walls for ten seconds) and the coordinates in chat.
    public static void locate(ServerPlayer player, MinecraftServer server, SchematicHolders.Holder holder) {
        AABB box = holder.box(server);
        String dimension = holder.pos().dimension().identifier().toString();
        PacketDistributor.sendToPlayer(player, new LocatePayload(String.format(Locale.ROOT, "%.4f %.4f %.4f %.4f %.4f %.4f %s", box.minX, box.minY,
                box.minZ, box.maxX, box.maxY, box.maxZ, dimension)));
        BlockPos at = holder.pos().pos();
        player.sendSystemMessage(Component.translatable("gui.encodedlogistics.schematic.located", holder.name(), at.getX() + ", " + at.getY() + ", " + at.getZ(),
                dimension));
    }

    // The holder of a job's step: the first holder with its schematic.
    private static void locateStep(ServerPlayer player, MinecraftServer server, NetworkRef network, UUID id, int step) {
        for (JobHost host : CraftRequests.schedulers(server, network)) {
            CraftingJob job = host.job(id);
            if (job == null || step < 0 || step >= job.steps.size()) {
                continue;
            }
            for (SchematicHolders.Holder holder : SchematicHolders.of(server, network)) {
                if (holder.holds(job.steps.get(step).schematic)) {
                    locate(player, server, holder);
                    return;
                }
            }
        }
    }

    // Cancel, hold or release: CRAFT, or the job's own requester.
    private static void jobAction(ServerPlayer player, MinecraftServer server, @Nullable NetworkRef home, NetworkRef network, UUID id, int action) {
        for (JobHost host : CraftRequests.schedulers(server, network)) {
            CraftingJob job = host.job(id);
            if (job == null) {
                continue;
            }
            boolean mine = job.requester.map(requester -> requester.equals(player.getUUID())).orElse(false);
            if (!mine && !NetworkAccess.allowed(server, home, player, RackPermission.CRAFT)) {
                player.sendOverlayMessage(Component.translatable("gui.encodedlogistics.jobs.denied").withStyle(ChatFormatting.RED));
                return;
            }
            switch (action) {
                case CANCEL -> host.cancel(id);
                case HOLD -> host.holdJob(id, true);
                default -> host.holdJob(id, false);
            }
            return;
        }
    }
}
