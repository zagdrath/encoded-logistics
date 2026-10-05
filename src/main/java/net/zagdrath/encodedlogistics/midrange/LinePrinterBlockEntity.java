/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModSounds;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Line Printer (HANDOFF 3, 5, 9): prints reports and spooled files as Printouts, one paper a page, from its paper
// out of its front. Paper goes in when used on it; a sneak-use with an empty hand takes it back. Its own screen prints
// the network inventory, the newest job's log, the device list or a spooled file; ELCL prints on it too (PRTRPT, Work
// with Output's 6=Print: DEV(PRT01), or *DFT). It works while a Midrange System on its network is online.
public class LinePrinterBlockEntity extends PeripheralBlockEntity implements PrinterDevice {
    public static final String TYPE = "PRT";
    public static final int PAPER = 0;
    public static final int INVENTORY = 1, JOB_LOG = 2, DEVICES = 3, SPOOLED = 4;
    private static final int ACTIVE_TICKS = 60;
    // A report's pages still to print, waiting for paper.
    private @Nullable Printout waiting;

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
        return !isOnline() ? "*OFFLINE" : active() ? "*PRINTING" : !hasPaper() ? "*NOPAPER" : waiting != null ? "*WAITING" : "*READY";
    }

    // What it printed comes out of its front.
    protected void output(ItemStack printed) {
        if (level != null) {
            Block.popResourceFromFace(level, worldPosition, getBlockState().getValue(FootprintBlock.FACING), printed);
        }
    }

    // --- Reports ---

    // A report: its title on the pages, which it is (Reports: inventory, joblog, devices, splf:<name>), its lines.
    public record Report(String title, String kind, List<String> lines) {}

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
                yield new Report(Reports.title(Reports.INVENTORY, ""), Reports.INVENTORY, Reports.inventory(system, storage));
            }
            case JOB_LOG -> {
                // The newest job.
                JobService.Job job = ElclServices.jobs().jobs(system).stream().max(Comparator.comparing(JobService.Job::number)).orElse(null);
                if (job == null) {
                    yield new Report(Reports.title(Reports.JOB_LOG, "*NONE"), Reports.JOB_LOG, List.of("(no jobs)"));
                }
                yield new Report(Reports.title(Reports.JOB_LOG, job.name()), Reports.JOB_LOG, Reports.jobLog(system, job));
            }
            case DEVICES -> new Report(Reports.title(Reports.DEVICES, ""), Reports.DEVICES, Reports.devices(system));
            default -> {
                String name = file.isBlank() ? "*LAST" : file.trim().toUpperCase(Locale.ROOT);
                SpoolService.SpooledFile found = Reports.spooled(system, null, null, name);
                if (found == null) {
                    throw new ElclException("ELC0103", name, "SPLF");
                }
                yield new Report(found.name(), Reports.SPOOLED + found.name(), Reports.spooled(system, found));
            }
        };
    }

    // How many pages a report's lines take.
    public static int pages(List<String> lines) {
        return Math.min(Printout.MAX_PAGES, Printout.paginate(lines).size());
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
        int pages = pages(report.lines()), paper = Math.min(pages, getItem(PAPER).getCount());
        print(report.title(), report.kind(), report.lines());
        return paper < pages ? Component.literal(ElclMessage.of("ELC1306", name()).toString())
                : Component.translatable("crt.encodedlogistics.msg.spooled", report.title(), pages, paper);
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

    @Override
    public void print(String title, List<String> lines) {
        print(title, Reports.SPOOLED + title, lines);
    }

    // A Printout of the report (HANDOFF 9): one paper a page. Out of paper part-way, the pages printed so far come out and
    // the rest waits (ELC1306) until paper goes in.
    @Override
    public void print(String title, String report, List<String> lines) {
        List<Printout.Page> pages = Printout.paginate(lines);
        if (pages.size() > Printout.MAX_PAGES) {
            pages = pages.subList(0, Printout.MAX_PAGES);
        }
        String system = "", printed = "";
        if (level instanceof ServerLevel serverLevel && network() != null) {
            ElclSystem elcl = new ElclSystem(serverLevel.getServer(), network());
            system = elcl.name();
            printed = elcl.now().toUpperCase(Locale.ROOT);
        }
        printPages(new Printout(title, report, system, printed, name(), List.copyOf(pages)));
    }

    // As many of its pages as there's paper for; the rest waits.
    private void printPages(Printout printout) {
        ItemStack paper = getItem(PAPER);
        int count = Math.min(printout.pages().size(), paper.getCount());
        if (count <= 0) {
            waiting = printout;
            setChanged();
            return;
        }
        ItemStack item = new ItemStack(ModItems.PRINTOUT.get());
        item.set(ModDataComponents.PRINTOUT.get(), new Printout(printout.title(), printout.report(), printout.system(), printout.printed(), printout.printer(),
                List.copyOf(printout.pages().subList(0, count))));
        paper.shrink(count);
        waiting = count < printout.pages().size() ? new Printout(printout.title(), printout.report(), printout.system(), printout.printed(),
                printout.printer(), List.copyOf(printout.pages().subList(count, printout.pages().size()))) : null;
        output(item);
        setChanged();
        activate(ACTIVE_TICKS, ModSounds.LINE_PRINTER_CHATTER);
    }

    // The pages still to print, waiting for paper (null: none).
    public @Nullable Printout waiting() {
        return waiting;
    }

    // The rest of a report goes on as soon as there's paper.
    @Override
    protected void tick(ServerLevel level) {
        if (waiting != null && isOnline() && hasPaper() && !active()) {
            printPages(waiting);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        waiting = input.read("waiting", Printout.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (waiting != null) {
            output.store("waiting", Printout.CODEC, waiting);
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new LinePrinterMenu(containerId, inventory, this, this, PeripheralMenu.Opening.SERVER);
    }
}
