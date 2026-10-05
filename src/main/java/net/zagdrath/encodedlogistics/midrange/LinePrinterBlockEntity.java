/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.device.PrinterDevice;
import net.zagdrath.encodedlogistics.elcl.exec.Reports;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.menu.LinePrinterMenu;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModSounds;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Line Printer (HANDOFF 3, 5): prints reports and spooled files as Written Books, one paper a page, from its paper
// out of its front. Paper goes in when used on it; a sneak-use with an empty hand takes it back. Its own screen prints
// the network inventory, the newest job's log, the device list or a spooled file; ELCL prints on it too (PRTRPT, Work
// with Output's 6=Print: DEV(PRT01), or *DFT). It works while a Midrange System on its network is online.
public class LinePrinterBlockEntity extends PeripheralBlockEntity implements PrinterDevice {
    public static final String TYPE = "PRT";
    public static final int PAPER = 0;
    public static final int INVENTORY = 1, JOB_LOG = 2, DEVICES = 3, SPOOLED = 4;
    // A book page: 14 lines of about 19 characters.
    private static final int PAGE_ROWS = 14, ROW_CHARS = 19, MAX_PAGES = 100, ACTIVE_TICKS = 60;

    public LinePrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.LINE_PRINTER.get(), pos, state, 1);
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == PAPER && stack.is(Items.PAPER);
    }

    // Paper used on it: as much as fits.
    @Override
    public boolean insert(ItemStack stack) {
        if (!stack.is(Items.PAPER)) {
            return false;
        }
        ItemStack paper = getItem(PAPER);
        int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - paper.getCount());
        if (moved <= 0) {
            return false;
        }
        if (paper.isEmpty()) {
            setItem(PAPER, stack.split(moved));
        } else {
            paper.grow(moved);
            stack.shrink(moved);
        }
        setChanged();
        return true;
    }

    // A sneak-use with an empty hand: the paper.
    @Override
    public List<ItemStack> eject() {
        ItemStack out = removeItemNoUpdate(PAPER);
        setChanged();
        return out.isEmpty() ? List.of() : List.of(out);
    }

    public int paper() {
        return getItem(PAPER).getCount();
    }

    // Its status on its screen: *OFFLINE, *PRINTING, *NOPAPER, *READY.
    public String status() {
        return !isOnline() ? "*OFFLINE" : active() ? "*PRINTING" : !hasPaper() ? "*NOPAPER" : "*READY";
    }

    // What it printed comes out of its front.
    protected void output(ItemStack printed) {
        if (level != null) {
            Block.popResourceFromFace(level, worldPosition, getBlockState().getValue(FootprintBlock.FACING), printed);
        }
    }

    // --- Reports ---

    public record Report(String title, List<String> lines) {}

    // One of its screen's reports (1-4; the file for 4: *LAST or a name).
    public Report report(int which, String file) throws ElclException {
        NetworkRef network = network();
        if (network == null || !(level instanceof ServerLevel serverLevel)) {
            throw new ElclException("ELC1302", name());
        }
        ElclSystem system = new ElclSystem(serverLevel.getServer(), network);
        return switch (which) {
            case INVENTORY -> {
                NetworkStorage storage = ControllerStructures.sharedStorageOf(system.server(), network, false);
                if (storage == null) {
                    throw new ElclException("ELC1302", "*NETWORK");
                }
                yield new Report("INV", Reports.inventory(system, storage));
            }
            case JOB_LOG -> {
                // The newest job.
                JobService.Job job = ElclServices.jobs().jobs(system).stream().max(Comparator.comparing(JobService.Job::number)).orElse(null);
                if (job == null) {
                    List<String> lines = Reports.heading(system, "*JOBLOG");
                    lines.add("(no jobs)");
                    yield new Report("JOBLOG", lines);
                }
                yield new Report(job.name(), Reports.jobLog(system, job));
            }
            case DEVICES -> new Report("DEV", Reports.devices(system));
            default -> {
                String name = file.isBlank() ? "*LAST" : file.trim().toUpperCase(Locale.ROOT);
                SpoolService.SpooledFile found = Reports.spooled(system, null, null, name);
                if (found == null) {
                    throw new ElclException("ELC0103", name, "SPLF");
                }
                yield new Report(found.name(), Reports.spooled(system, found));
            }
        };
    }

    // The lines as book pages: each line on rows of about ROW_CHARS (runs of spaces shortened), PAGE_ROWS rows a page.
    public static List<String> pages(List<String> lines) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int rows = 0;
        for (String raw : lines) {
            String line = raw.stripTrailing().replaceAll(" {3,}", "  ");
            int need = Math.max(1, (line.length() + ROW_CHARS - 1) / ROW_CHARS);
            if (rows > 0 && rows + need > PAGE_ROWS) {
                pages.add(page.toString());
                page.setLength(0);
                rows = 0;
            }
            if (rows > 0) {
                page.append('\n');
            }
            page.append(line);
            rows += need;
        }
        if (rows > 0 || pages.isEmpty()) {
            pages.add(page.toString());
        }
        return pages;
    }

    // Prints one of its screen's reports; the message line says how it went.
    public Component printReport(int which, String file) {
        if (!isOnline()) {
            return Component.translatable("crt.encodedlogistics.machine.no_host");
        }
        Report report;
        try {
            report = report(which, file);
        } catch (ElclException e) {
            ElclMessage message = e.elclMessage();
            return Component.literal(message.id() + "  " + message.text());
        }
        if (!hasPaper()) {
            return Component.translatable("crt.encodedlogistics.printer.no_paper");
        }
        int pages = Math.min(pages(report.lines()).size(), MAX_PAGES), paper = Math.min(pages, getItem(PAPER).getCount());
        print(report.title(), report.lines());
        return Component.translatable("crt.encodedlogistics.msg.spooled", report.title(), pages, paper);
    }

    // --- PrinterDevice ---

    @Override
    public String name() {
        return deviceName().isEmpty() ? TYPE + "01" : deviceName();
    }

    @Override
    public boolean online() {
        return isOnline();
    }

    @Override
    public boolean hasPaper() {
        return !getItem(PAPER).isEmpty();
    }

    // A Written Book of as many pages as there's paper for (one paper a page; the rest isn't printed).
    @Override
    public void print(String title, List<String> lines) {
        ItemStack paper = getItem(PAPER);
        List<String> pages = pages(lines);
        int count = Math.min(Math.min(pages.size(), MAX_PAGES), paper.getCount());
        if (count <= 0) {
            return;
        }
        List<Filterable<Component>> printed = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            printed.add(Filterable.passThrough(Component.literal(pages.get(i))));
        }
        String bookTitle = title.length() > 32 ? title.substring(0, 32) : title;
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(bookTitle), name(), 0, printed, true));
        paper.shrink(count);
        output(book);
        setChanged();
        activate(ACTIVE_TICKS, ModSounds.LINE_PRINTER_CHATTER);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new LinePrinterMenu(containerId, inventory, this, this, PeripheralMenu.Opening.SERVER);
    }
}
