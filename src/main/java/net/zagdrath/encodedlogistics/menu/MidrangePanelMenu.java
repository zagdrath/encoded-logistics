/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.midrange.DisketteData;
import net.zagdrath.encodedlogistics.midrange.DisketteStack;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// MRCTL / IMCTL (HANDOFF 3; layouts mrctl, imctl): a Midrange System's or Integrated Midrange System's control panel.
// Data: threads, max job, batch jobs, expanded, integrated, held. Every second the server sends, tab-separated:
//   S code wordKey                                             its status ("A6", RUNNING)
//   D position label recipes status                            a drive position (label empty: none; *DFT / *READY / *EMPTY)
//   J job itemKey amount status percent awaitingItemKey        a job (*ACTIVE / *QUEUED / *HELD)
//   V label, R itemKey count                                   5=Display recipes: the diskette's recipes, while shown
// Options: a drive's (row 0-3) 4=Eject (tier 2: Remove from magazine), 5=Display recipes, 8=Make default library; a
// job's (row JOB_ROW + n) 3=Hold, 4=End, 6=Release. Buttons: IPL (F7), Hold the queue (F10), Release it (F11), back from
// the recipes (F12).
public class MidrangePanelMenu extends PeripheralMenu {
    public static final int BUTTON_IPL = 0, BUTTON_HOLD = 1, BUTTON_RELEASE = 2, BUTTON_BACK = 3;
    public static final int JOB_ROW = 10, JOBS_SHOWN = 6;

    private final @Nullable MidrangeSystemBlockEntity system;
    private final DataSlot threads = DataSlot.standalone(), memory = DataSlot.standalone(), batch = DataSlot.standalone(),
            expanded = DataSlot.standalone(), integrated = DataSlot.standalone(), held = DataSlot.standalone();
    // The jobs as last listed (an option's row is one of them), and the drive whose recipes are shown (-1: none).
    private final List<UUID> listed = new ArrayList<>();
    private int showing = -1;

    // Client constructor.
    public MidrangePanelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(2), null, Opening.read(buf));
    }

    public MidrangePanelMenu(int containerId, Inventory inventory, Container slots, @Nullable MidrangeSystemBlockEntity system, Opening opening) {
        super(ModMenuTypes.MIDRANGE_PANEL.get(), containerId, inventory, slots, system, opening);
        this.system = system;
        for (DataSlot slot : List.of(threads, memory, batch, expanded, integrated, held)) {
            addDataSlot(slot);
        }
        update();
    }

    public int threads() {
        return threads.get();
    }

    public int memory() {
        return memory.get();
    }

    public int batchJobs() {
        return batch.get();
    }

    public boolean expanded() {
        return expanded.get() != 0;
    }

    public boolean integrated() {
        return integrated.get() != 0;
    }

    public boolean held() {
        return held.get() != 0;
    }

    // --- Server ---

    private void update() {
        if (system == null) {
            return;
        }
        threads.set(system.threads());
        memory.set((int) system.memory());
        batch.set(system.batchJobs());
        expanded.set(system.expanded() ? 1 : 0);
        integrated.set(system.integrated() ? 1 : 0);
        held.set(system.held() ? 1 : 0);
    }

    @Override
    public void broadcastChanges() {
        update();
        super.broadcastChanges();
    }

    // Its status word's lang key for the status code.
    private static String word(MidrangeSystemBlockEntity system) {
        String code = system.statusCode();
        String word = code.startsWith("E9") ? "offline" : code.startsWith("E8") ? "conflict" : code.startsWith("C") ? "ipl" : code.startsWith("E2") ? "attention"
                : code.startsWith("E1") ? "no_library" : system.held() ? "held" : code.startsWith("A6b") ? "busy" : "running";
        return "crt.encodedlogistics.mrctl.word." + word;
    }

    @Override
    protected void refresh() {
        if (system == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.join("\t", "S", system.statusCode(), word(system)));
        List<ItemStack> positions = system.positions();
        int first = system.defaultDrive();
        for (int i = 0; i < positions.size(); i++) {
            ItemStack diskette = positions.get(i);
            if (diskette.isEmpty()) {
                lines.add(String.join("\t", "D", Integer.toString(i + 1), "", "", "*EMPTY"));
            } else {
                DisketteData data = DisketteStack.data(diskette);
                lines.add(String.join("\t", "D", Integer.toString(i + 1), label(data), Integer.toString(data.recipes().size()), i == first ? "*DFT" : "*READY"));
            }
        }
        listed.clear();
        for (CraftingJob job : system.jobs()) {
            if (listed.size() >= JOBS_SHOWN) {
                break;
            }
            listed.add(job.id);
            String status = system.jobHeld(job.id) || !job.running && system.held() ? "*HELD" : job.running ? "*ACTIVE" : "*QUEUED";
            String percent = "", awaiting = "";
            if (job.running) {
                int done = 0, total = 0;
                for (CraftingJob.Step step : job.steps) {
                    done += step.done;
                    total += step.total;
                }
                float own = system.stepProgress(job.id);
                percent = Integer.toString(total == 0 ? 100 : Math.min(100, Math.round((done + own) * 100 / total)));
                awaiting = job.awaiting.isEmpty() ? "" : itemKey(job.awaiting.keySet().iterator().next());
            }
            lines.add(String.join("\t", "J", shortId(job), itemKey(job.target), Long.toString(job.amount), status, percent, awaiting));
        }
        if (showing >= 0 && showing < positions.size() && !positions.get(showing).isEmpty()) {
            DisketteData data = DisketteStack.data(positions.get(showing));
            lines.add(String.join("\t", "V", label(data)));
            for (Schematic recipe : data.recipes()) {
                ItemStack output = recipe.output();
                lines.add(String.join("\t", "R", output.getItem().getDescriptionId(), Integer.toString(output.getCount())));
            }
        } else {
            showing = -1;
        }
        send(Component.empty(), lines, List.of(lines.size()));
    }

    private static String label(DisketteData data) {
        return data.label().isEmpty() ? DisketteStack.DEFAULT_LABEL : data.label();
    }

    // A job's number as the panel shows it: four hex digits of its id.
    private static String shortId(CraftingJob job) {
        return job.id.toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    // An item's name, as its lang key (the client translates it).
    private static String itemKey(StorageKey key) {
        return key.stack().getItem().getDescriptionId();
    }

    @Override
    protected @Nullable Component option(int row, String option) {
        if (system == null) {
            return null;
        }
        if (row >= JOB_ROW) {
            int index = row - JOB_ROW;
            if (index >= listed.size() || system.job(listed.get(index)) == null) {
                return Component.translatable("crt.encodedlogistics.mrctl.job_gone");
            }
            UUID id = listed.get(index);
            String shown = id.toString().substring(0, 4).toUpperCase(Locale.ROOT);
            return switch (option) {
                case "3" -> {
                    system.holdJob(id, true);
                    yield Component.translatable("crt.encodedlogistics.mrctl.job_held", shown);
                }
                case "4" -> system.cancel(id) ? Component.translatable("crt.encodedlogistics.mrctl.job_ended", shown)
                        : Component.translatable("crt.encodedlogistics.mrctl.job_gone");
                case "6" -> {
                    system.holdJob(id, false);
                    yield Component.translatable("crt.encodedlogistics.mrctl.job_released", shown);
                }
                default -> null;
            };
        }
        List<ItemStack> positions = system.positions();
        if (row >= positions.size()) {
            return null;
        }
        boolean empty = positions.get(row).isEmpty();
        return switch (option) {
            case "4" -> {
                if (empty) {
                    yield Component.translatable("crt.encodedlogistics.mrctl.drive_empty", row + 1);
                }
                ItemStack out = system.takeDiskette(row);
                give(out);
                yield Component.translatable(system.integrated() ? "crt.encodedlogistics.mrctl.removed" : "crt.encodedlogistics.mrctl.ejected",
                        label(DisketteStack.data(out)), row + 1);
            }
            case "5" -> {
                if (empty) {
                    yield Component.translatable("crt.encodedlogistics.mrctl.drive_empty", row + 1);
                }
                showing = row;
                yield Component.empty();
            }
            case "8" -> system.setDefaultDrive(row)
                    ? Component.translatable("crt.encodedlogistics.mrctl.default", label(DisketteStack.data(positions.get(row))))
                    : Component.translatable("crt.encodedlogistics.mrctl.drive_empty", row + 1);
            default -> null;
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (system == null) {
            return false;
        }
        switch (id) {
            case BUTTON_IPL -> {
                if (!system.isOnline()) {
                    send(Component.translatable("crt.encodedlogistics.mrctl.offline"));
                    return true;
                }
                CraftingJob active = system.jobs().stream().filter(job -> job.running).findFirst().orElse(null);
                system.startIpl();
                send(active != null ? Component.translatable("crt.encodedlogistics.msg.ipl", shortId(active))
                        : Component.translatable("crt.encodedlogistics.mrctl.ipl"));
            }
            case BUTTON_HOLD -> {
                system.setHeld(true);
                send(Component.translatable("crt.encodedlogistics.mrctl.held"));
            }
            case BUTTON_RELEASE -> {
                system.setHeld(false);
                send(Component.translatable("crt.encodedlogistics.mrctl.released"));
            }
            case BUTTON_BACK -> showing = -1;
            default -> {
                return false;
            }
        }
        refresh();
        return true;
    }
}
