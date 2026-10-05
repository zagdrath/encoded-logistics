/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.RecipeLibraries;
import net.zagdrath.encodedlogistics.crafting.RecipeLibrarySource;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.device.DeviceSources;
import net.zagdrath.encodedlogistics.elcl.device.Diskette;
import net.zagdrath.encodedlogistics.elcl.device.DisketteDevice;
import net.zagdrath.encodedlogistics.elcl.device.Diskettes;
import net.zagdrath.encodedlogistics.elcl.device.LibraryImage;
import net.zagdrath.encodedlogistics.elcl.device.Printers;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The Midrange line's interfaces (Part 7) against fakes: SAVLIB / RSTLIB through a DisketteDevice (ELC1301 with no
// device, ELC1302 offline, ELC1310 with no diskette, ELC1311 too big, ELC0201 not on the diskette, ELC0205 into
// ELSYS), the library image round-tripping through NBT; Work with Output's 6=Print through a PrinterDevice (ELC1301,
// ELC1306, ELC1307); and a RecipeLibrarySource's recipes counting as the network's for crafting.
final class InterfaceGameTests {
    private InterfaceGameTests() {}

    static final class FakeDiskette implements Diskette {
        final List<LibraryImage> saved = new ArrayList<>();
        long capacity = 65_536;

        @Override
        public String label() {
            return "VOL001";
        }

        @Override
        public long capacity() {
            return capacity;
        }

        @Override
        public List<LibraryImage> libraries() {
            return Collections.unmodifiableList(saved);
        }

        @Override
        public void write(LibraryImage image) {
            saved.removeIf(old -> old.library().equalsIgnoreCase(image.library()));
            saved.add(image);
        }
    }

    static final class FakeDrive implements DisketteDevice {
        final List<Diskette> mounted = new ArrayList<>();
        boolean online = true;

        @Override
        public String name() {
            return "MIDRANGE01";
        }

        @Override
        public boolean online() {
            return online;
        }

        @Override
        public List<Diskette> mounted() {
            return mounted;
        }
    }

    private static void expect(GameTestHelper helper, TerminalContext context, String line, String wanted) {
        BatchJobGameTests.expect(helper, context, line, wanted);
    }

    static void saveRestore(GameTestHelper helper) {
        FakeDrive drive = new FakeDrive();
        FakeDiskette diskette = new FakeDiskette();
        ElclSystem[] system = new ElclSystem[1];
        DeviceSources.Source<DisketteDevice> source = s -> s.equals(system[0]) ? List.of(drive) : List.of();
        ElclStoreGameTests.withDesk(helper, (c, sys) -> {
            system[0] = sys;
            expect(helper, c, "CRTLIB KEEP", "ELC0210");
            expect(helper, c, "CPYMBR FROM(ELSYS/RESTOCK) TO(KEEP/RESTOCK)", "ELC0215");
            expect(helper, c, "CRTELPGM PGM(KEEP/RESTOCK)", "ELC0218");
            // No diskette device at all.
            expect(helper, c, "SAVLIB LIB(KEEP) DEV(MIDRANGE01)", "ELC1301");
            Diskettes.register(source);
            try {
                expect(helper, c, "SAVLIB LIB(KEEP) DEV(MIDRANGE01)", "ELC1310");
                drive.mounted.add(diskette);
                drive.online = false;
                expect(helper, c, "SAVLIB LIB(KEEP) DEV(MIDRANGE01)", "ELC1302");
                drive.online = true;
                expect(helper, c, "SAVLIB LIB(KEEP) DEV(MIDRANGE01)", "ELC0220");
                LibraryImage image = diskette.library("KEEP");
                helper.assertTrue(image != null && image.members().size() == 1 && image.programs().size() == 1, "Image " + image);
                helper.assertTrue(LibraryImage.load(image.save()).equals(image), "Image doesn't survive NBT");
                // Gone and back.
                expect(helper, c, "DLTLIB LIB(KEEP)", "ELC0211");
                expect(helper, c, "RSTLIB LIB(KEEP) DEV(MIDRANGE01)", "ELC0221");
                try {
                    helper.assertTrue(ElclServices.libraries().source(sys, "KEEP", "RESTOCK").equals(image.members().getFirst().lines()),
                            "Member not restored as saved");
                    helper.assertTrue(ElclServices.libraries().program(sys, "KEEP", "RESTOCK") != null, "Program not restored");
                    helper.assertTrue(ElclServices.libraries().library(sys, "KEEP").owner().equals(c.user()), "Restored library's owner");
                } catch (ElclException e) {
                    helper.fail("Restored: " + e.getMessage());
                }
                expect(helper, c, "RSTLIB LIB(NOTHERE) DEV(MIDRANGE01)", "ELC0201");
                expect(helper, c, "SAVLIB LIB(ELSYS) DEV(MIDRANGE01)", "ELC0220");
                expect(helper, c, "RSTLIB LIB(ELSYS) DEV(MIDRANGE01)", "ELC0205");
                // Too big for what's left.
                diskette.capacity = 100;
                expect(helper, c, "SAVLIB LIB(KEEP) DEV(MIDRANGE01)", "ELC1311");
            } finally {
                Diskettes.unregister(source);
            }
        });
    }

    static void printing(GameTestHelper helper) {
        ElclVmGameTests.FakePrinter printer = new ElclVmGameTests.FakePrinter();
        ElclSystem[] system = new ElclSystem[1];
        DeviceSources.Source<net.zagdrath.encodedlogistics.elcl.device.PrinterDevice> source = s -> s.equals(system[0]) ? List.of(printer) : List.of();
        ElclStoreGameTests.withDesk(helper, (c, sys) -> {
            system[0] = sys;
            ElclServices.jobs().interactive(sys, c.user(), "print", "ELDESK01");
            expect(helper, c, "PRTTXT TEXT('To print') SPLF(PAPER)", "");
            int id = ElclServices.spool().files(sys, c.user(), null).getFirst().id();
            String none = TerminalService.handle(c, TerminalService.SCREEN, "printsplf " + id).message().getString();
            helper.assertTrue(none.startsWith("ELC1301"), "With no printer: " + none);
            Printers.register(source);
            try {
                String done = TerminalService.handle(c, TerminalService.SCREEN, "printsplf " + id).message().getString();
                helper.assertTrue(done.startsWith("ELC1307") && printer.printed.contains("To print") && printer.titles.contains("PAPER"),
                        "Printed: " + done + " " + printer.printed);
                printer.paper = false;
                String out = TerminalService.handle(c, TerminalService.SCREEN, "printsplf " + id).message().getString();
                helper.assertTrue(out.startsWith("ELC1306"), "Out of paper: " + out);
            } finally {
                Printers.unregister(source);
            }
        });
    }

    static void recipeLibrary(GameTestHelper helper) {
        Schematic block = Schematic.of(Schematic.Kind.CRAFTING, Collections.nCopies(9, new ItemStack(Items.IRON_INGOT)),
                List.of(new ItemStack(Items.IRON_BLOCK)));
        ElclSystem[] system = new ElclSystem[1];
        RecipeLibrarySource library = new RecipeLibrarySource() {
            @Override
            public String name() {
                return "MIDRANGE01";
            }

            @Override
            public boolean online() {
                return true;
            }

            @Override
            public List<Schematic> recipes() {
                return List.of(block);
            }
        };
        DeviceSources.Source<RecipeLibrarySource> source = s -> s.equals(system[0]) ? List.of(library) : List.of();
        ElclStoreGameTests.withDesk(helper, (c, sys) -> {
            system[0] = sys;
            StorageKey ironBlock = StorageKey.of(new ItemStack(Items.IRON_BLOCK));
            helper.assertFalse(CraftRequests.craftables(sys.server(), sys.network()).contains(ironBlock), "Craftable before the library");
            RackGameTests.storage(helper, ElclGameTests.BAY).insert(StorageKey.of(new ItemStack(Items.IRON_INGOT)), 9, false);
            RecipeLibraries.register(source);
            try {
                helper.assertTrue(CraftRequests.craftables(sys.server(), sys.network()).contains(ironBlock), "The library's recipe isn't craftable");
                CraftPlanner.Plan plan = CraftRequests.plan(sys.server(), sys.network(), ironBlock, 1);
                helper.assertTrue(plan != null && plan.complete(), "No plan from the library's recipe");
                // Planned, but there's no Scheduler to run it.
                expect(helper, c, "STRCRAFT IRON_BLOCK 1", "ELC1401");
            } finally {
                RecipeLibraries.unregister(source);
            }
        });
    }
}
