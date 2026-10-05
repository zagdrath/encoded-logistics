/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import net.zagdrath.encodedlogistics.midrange.DisketteMagazineItem;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// A Midrange System's or Integrated Midrange System's control panel (HANDOFF 2, 4): its diskette slots (A, and B with
// an Expansion Cabinet) or its magazine slot and the magazine's four diskettes (shown, not taken), then the player's
// inventory. Data: threads, max job, batch jobs, expanded, integrated, held. Every second the server sends its status
// and jobs as lines (MachinePayloads.Info), tab-separated:
//   S code statusKey
//   C job itemKey amount stepsDone steps percent awaitingItemKey   (a running job)
//   Q job itemKey amount                                             (a queued one)
// Buttons: IPL (F7), Hold the queue (F10), Release it (F11).
public class MidrangePanelMenu extends PeripheralMenu {
    public static final int BUTTON_IPL = 0, BUTTON_HOLD = 1, BUTTON_RELEASE = 2;
    public static final int SLOT_A_X = 18, SLOT_B_X = 48, SLOTS_Y = 60, MAGAZINE_X = 60, MAGAZINE_PITCH = 48;
    // The magazine's diskettes shown, after the two real slots.
    public static final int SHOWN = 2;
    private static final int QUEUE_SHOWN = 6;

    private final @Nullable MidrangeSystemBlockEntity system;
    private final SimpleContainer shown = new SimpleContainer(DisketteMagazineItem.CAPACITY);
    private final DataSlot threads = DataSlot.standalone(), memory = DataSlot.standalone(), batch = DataSlot.standalone(),
            expanded = DataSlot.standalone(), integrated = DataSlot.standalone(), held = DataSlot.standalone();

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
        addSlot(new MachineSlot(slots, MidrangeSystemBlockEntity.SLOT_A, SLOT_A_X, SLOTS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return integrated() ? stack.is(ModItems.DISKETTE_MAGAZINE.get()) : stack.is(ModItems.DISKETTE_8IN.get());
            }
        });
        addSlot(new MachineSlot(slots, MidrangeSystemBlockEntity.SLOT_B, SLOT_B_X, SLOTS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return expanded() && stack.is(ModItems.DISKETTE_8IN.get());
            }

            @Override
            public boolean isActive() {
                return expanded();
            }
        });
        for (int i = 0; i < DisketteMagazineItem.CAPACITY; i++) {
            addSlot(new GhostSlot(shown, i, MAGAZINE_X + i * MAGAZINE_PITCH, SLOTS_Y) {
                @Override
                public boolean isActive() {
                    return integrated();
                }
            });
        }
        addPlayerSlots(inventory);
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
        ItemStack magazine = system.getItem(MidrangeSystemBlockEntity.SLOT_A);
        List<ItemStack> inside = system.integrated() && magazine.is(ModItems.DISKETTE_MAGAZINE.get()) ? DisketteMagazineItem.diskettes(magazine) : List.of();
        for (int i = 0; i < shown.getContainerSize(); i++) {
            ItemStack want = i < inside.size() ? inside.get(i) : ItemStack.EMPTY;
            if (!ItemStack.matches(shown.getItem(i), want)) {
                shown.setItem(i, want.copy());
            }
        }
    }

    @Override
    public void broadcastChanges() {
        update();
        super.broadcastChanges();
    }

    // Its status and jobs, every second.
    @Override
    protected void refresh() {
        if (system == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.join("\t", "S", system.statusCode(), system.statusKey()));
        int queued = 0;
        for (CraftingJob job : system.jobs()) {
            String id = shortId(job), item = itemKey(job.target), amount = Long.toString(job.amount);
            if (job.running) {
                int done = 0, total = 0, stepsDone = 0;
                for (CraftingJob.Step step : job.steps) {
                    done += step.done;
                    total += step.total;
                    stepsDone += step.finished() ? 1 : 0;
                }
                float own = system.stepProgress(job.id);
                int percent = total == 0 ? 100 : Math.min(100, Math.round((done + own) * 100 / total));
                String awaiting = job.awaiting.isEmpty() ? "" : itemKey(job.awaiting.keySet().iterator().next());
                lines.add(String.join("\t", "C", id, item, amount, Integer.toString(Math.min(job.steps.size(), stepsDone + 1)),
                        Integer.toString(job.steps.size()), Integer.toString(percent), awaiting));
            } else if (queued++ < QUEUE_SHOWN) {
                lines.add(String.join("\t", "Q", id, item, amount));
            }
        }
        send(Component.empty(), lines, List.of(lines.size()));
    }

    // A job's number as the panel shows it: four hex digits of its id.
    private static String shortId(CraftingJob job) {
        return job.id.toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    // An item's name, as its lang key (the client translates it).
    private static String itemKey(ItemKey key) {
        return key.stack().getItem().getDescriptionId();
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
            default -> {
                return false;
            }
        }
        refresh();
        return true;
    }
}
