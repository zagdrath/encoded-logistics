/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.chassis;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackSlot;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.registry.ModItems;

// Where a modular chassis's bays are (models/block/rack_card/slot_positions.json): each bay's item slot in the device,
// the kind of part it takes, its label (A, L3, P1, C1, F5, R2), which face it's on and its place on that face in the
// device sheet's texels (x from the left of the face as seen, y down from its top; the front's 104 x 8U, the back's
// the same). The Blade Enclosure's rear modules (not in the drop's slot positions) sit on its back: the two
// Interconnect Modules and two Management Modules side by side at the left, the six power supplies in two rows below.
//
// Its item slots (RackDeviceType#slots) are the bays, which the panel shows as the chassis's front (they're never drawn
// as slots: scrollRow HIDDEN), then any banks of real slots the panel pages through (a Chassis Switch's uplink cages, a
// Blade Enclosure's drive bays and schematics), each page its own scrollRow.
public final class ChassisLayout {
    // A bay: its item slot, what it takes, its label, its face and place.
    public record Bay(int slot, ChassisPart.Kind kind, String label, boolean rear, int x, int y, int w, int h) {
        public boolean contains(double tx, double ty) {
            return tx >= x && tx < x + w && ty >= y && ty < y + h;
        }

        public boolean accepts(ItemStack stack) {
            ChassisPart part = ChassisPart.of(stack);
            return part != null && part.kind() == kind;
        }
    }

    public static final int HIDDEN = 1000, CAGE_PAGE = 100, BAY_PAGE = 200, SCHEMATIC_PAGE = 300;
    // The Chassis Switch's cages: four for each line slot, after its 14 bays.
    public static final int SWITCH_CAGES_FROM = 14, CAGES_PER_CARD = 4;
    // The Blade Enclosure's slots, in the order they were added (so older enclosures load as they were): each front
    // slot's first two drive bays, its bays, its first nine schematics, each front slot's other four drive bays, the
    // other eighteen schematics. The panel pages through them: drive bays two a blade a page, schematics nine a page.
    public static final int BLADE_BAYS_FROM = 32, BLADE_SCHEMATICS_FROM = 58, BLADE_MORE_DRIVES_FROM = 67, BLADE_MORE_SCHEMATICS_FROM = 131;
    public static final int BLADES = 16, DRIVES_PER_BLADE = 6, DRIVES_PER_PAGE = 2, SCHEMATICS = 27, SCHEMATICS_PER_PAGE = 9;

    public static final ChassisLayout SWITCH = switchLayout(), SHELF = shelfLayout(), BLADE = bladeLayout();

    private final List<Bay> bays;

    private ChassisLayout(List<Bay> bays) {
        this.bays = List.copyOf(bays);
    }

    public List<Bay> bays() {
        return bays;
    }

    // The bay at an item slot, or null.
    public @Nullable Bay bay(int slot) {
        for (Bay bay : bays) {
            if (bay.slot() == slot) {
                return bay;
            }
        }
        return null;
    }

    public @Nullable Bay byLabel(String label) {
        for (Bay bay : bays) {
            if (bay.label().equalsIgnoreCase(label)) {
                return bay;
            }
        }
        return null;
    }

    // The bay at a point on a face (texels), or null.
    public @Nullable Bay at(boolean rear, double tx, double ty) {
        for (Bay bay : bays) {
            if (bay.rear() == rear && bay.contains(tx, ty)) {
                return bay;
            }
        }
        return null;
    }

    public boolean hasRear() {
        return bays.stream().anyMatch(Bay::rear);
    }

    // --- The three chassis ---

    // 2 supervisors (half width, top row), 8 line cards (a 1U row each), 4 power supplies (quarter width, bottom row).
    private static ChassisLayout switchLayout() {
        List<Bay> bays = new ArrayList<>();
        bays.add(new Bay(0, ChassisPart.Kind.SUPERVISOR, "A", false, 12, 1, 40, 7));
        bays.add(new Bay(1, ChassisPart.Kind.SUPERVISOR, "B", false, 52, 1, 40, 7));
        for (int i = 0; i < 8; i++) {
            bays.add(new Bay(2 + i, ChassisPart.Kind.LINE, "L" + (i + 1), false, 12, 9 + 8 * i, 80, 7));
        }
        for (int i = 0; i < 4; i++) {
            bays.add(new Bay(10 + i, ChassisPart.Kind.CHASSIS_PSU, "P" + (i + 1), false, 12 + 20 * i, 73, 20, 6));
        }
        return new ChassisLayout(bays);
    }

    // 10 vertical slots: two controllers, then eight line cards.
    private static ChassisLayout shelfLayout() {
        List<Bay> bays = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean controller = i < 2;
            bays.add(new Bay(i, controller ? ChassisPart.Kind.SHELF_CONTROLLER : ChassisPart.Kind.SHELF_LINE, controller ? "C" + (i + 1) : "L" + (i - 1),
                    false, 12 + 8 * i, 2, 7, 36));
        }
        return new ChassisLayout(bays);
    }

    // 16 half-height blade slots (two rows of eight) in front; behind, two interconnects, two management modules, six
    // power supplies.
    private static ChassisLayout bladeLayout() {
        List<Bay> bays = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            bays.add(new Bay(BLADE_BAYS_FROM + i, ChassisPart.Kind.BLADE, "F" + (i + 1), false, 12 + 10 * (i % 8), i < 8 ? 2 : 41, 9, 37));
        }
        bays.add(new Bay(BLADE_BAYS_FROM + 16, ChassisPart.Kind.INTERCONNECT, "R1", true, 12, 2, 9, 37));
        bays.add(new Bay(BLADE_BAYS_FROM + 17, ChassisPart.Kind.INTERCONNECT, "R2", true, 22, 2, 9, 37));
        bays.add(new Bay(BLADE_BAYS_FROM + 18, ChassisPart.Kind.MANAGEMENT, "M1", true, 34, 2, 9, 37));
        bays.add(new Bay(BLADE_BAYS_FROM + 19, ChassisPart.Kind.MANAGEMENT, "M2", true, 44, 2, 9, 37));
        for (int i = 0; i < 6; i++) {
            bays.add(new Bay(BLADE_BAYS_FROM + 20 + i, ChassisPart.Kind.BLADE_PSU, "P" + (i + 1), true, 12 + 20 * (i % 3), i < 3 ? 64 : 72, 20, 6));
        }
        return new ChassisLayout(bays);
    }

    // --- Item slots ---

    // Its bays as slots that are never drawn (the panel's front view puts parts in and takes them out).
    private List<RackSlot> baySlots() {
        List<RackSlot> slots = new ArrayList<>();
        for (Bay bay : bays) {
            slots.add(new RackSlot(0, 0, bay::accepts, 1, HIDDEN));
        }
        return slots;
    }

    // The Chassis Switch: its bays, then each line slot's four uplink cages (2 x 2, four groups a row, two rows).
    public static List<RackSlot> switchSlots() {
        List<RackSlot> slots = new ArrayList<>(SWITCH.baySlots());
        for (int line = 0; line < 8; line++) {
            for (int k = 0; k < CAGES_PER_CARD; k++) {
                slots.add(new RackSlot(cageX(line) + k % 2 * 18, cageY(line) + k / 2 * 18, RouterDevice::isTransceiver, 1, CAGE_PAGE));
            }
        }
        return slots;
    }

    // Where a line slot's group of cages starts on the Cages page.
    public static int cageX(int line) {
        return 13 + line % 4 * 39;
    }

    public static int cageY(int line) {
        return 44 + line / 4 * 50;
    }

    public static List<RackSlot> shelfSlots() {
        return SHELF.baySlots();
    }

    // The Blade Enclosure: its slots in the order above. On each drive page every front slot shows two of its bays
    // (eight slots' bays a row); on each schematic page nine schematics, 3 x 3.
    public static List<RackSlot> bladeSlots() {
        RackSlot[] slots = new RackSlot[BLADE_MORE_SCHEMATICS_FROM + SCHEMATICS - SCHEMATICS_PER_PAGE];
        Predicate<ItemStack> drive = RackDeviceType::isDrive;
        for (int blade = 0; blade < BLADES; blade++) {
            for (int k = 0; k < DRIVES_PER_BLADE; k++) {
                slots[bladeDriveSlot(blade, k)] = new RackSlot(driveX(blade), driveY(blade, k % DRIVES_PER_PAGE), drive, 1, BAY_PAGE + k / DRIVES_PER_PAGE);
            }
        }
        List<RackSlot> bays = BLADE.baySlots();
        for (int i = 0; i < bays.size(); i++) {
            slots[BLADE_BAYS_FROM + i] = bays.get(i);
        }
        Predicate<ItemStack> schematic = stack -> stack.is(ModItems.ENCODED_SCHEMATIC_CRAFTING.get());
        for (int i = 0; i < SCHEMATICS; i++) {
            int k = i % SCHEMATICS_PER_PAGE;
            slots[bladeSchematicSlot(i)] = new RackSlot(62 + k % 3 * 18, 46 + k / 3 * 18, schematic, 1, SCHEMATIC_PAGE + i / SCHEMATICS_PER_PAGE);
        }
        return List.of(slots);
    }

    // A front slot's k-th drive bay (0-5), as an item slot.
    public static int bladeDriveSlot(int blade, int k) {
        return k < DRIVES_PER_PAGE ? blade * DRIVES_PER_PAGE + k : BLADE_MORE_DRIVES_FROM + blade * (DRIVES_PER_BLADE - DRIVES_PER_PAGE) + k - DRIVES_PER_PAGE;
    }

    // The front slot (0-15) whose drive bay an item slot is, or -1.
    public static int bladeOfDriveSlot(int slot) {
        if (slot >= 0 && slot < BLADE_BAYS_FROM) {
            return slot / DRIVES_PER_PAGE;
        }
        int more = slot - BLADE_MORE_DRIVES_FROM;
        return more >= 0 && more < BLADES * (DRIVES_PER_BLADE - DRIVES_PER_PAGE) ? more / (DRIVES_PER_BLADE - DRIVES_PER_PAGE) : -1;
    }

    // The i-th schematic (0-26), as an item slot.
    public static int bladeSchematicSlot(int i) {
        return i < SCHEMATICS_PER_PAGE ? BLADE_SCHEMATICS_FROM + i : BLADE_MORE_SCHEMATICS_FROM + i - SCHEMATICS_PER_PAGE;
    }

    public static int driveX(int blade) {
        return 16 + blade % 8 * 18;
    }

    // A front slot's bay on a drive page (k 0 or 1).
    public static int driveY(int blade, int k) {
        return 44 + (blade / 8 * 2 + k) * 18 + blade / 8 * 6;
    }
}
