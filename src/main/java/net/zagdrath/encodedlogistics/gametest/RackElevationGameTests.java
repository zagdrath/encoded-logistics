/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.net.RackElevationPayloads;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackElevations;
import net.zagdrath.encodedlogistics.rack.RackTemplate;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;
import net.zagdrath.encodedlogistics.rack.device.BladeEnclosureDevice;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.recipe.RackElevationRecipes;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Rack Elevations (RackElevations): recording a rack with its contents and settings and applying it back from the
// player's inventory; applying never removes anything (skips, reports what's missing); creative needs no items; shulker
// boxes are a source; the same device already there gets its planned contents; the editor's rules; the copy and clear
// recipes.
final class RackElevationGameTests {
    private RackElevationGameTests() {}

    private static final BlockPos BOTTOM_FRONT = new BlockPos(3, 1, 0);

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(mode);
        player.getInventory().clearContent();
        return player;
    }

    private static ItemStack held(ServerPlayer player, RackTemplate template) {
        ItemStack stack = new ItemStack(ModItems.RACK_ELEVATION.get());
        if (template != null) {
            RackTemplate.write(stack, template);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player.getMainHandItem();
    }

    // A Firewall, a UPS and a NAS with a drive and a priority, recorded with contents and settings; taken out; applied
    // back from the inventory: the same devices at the same units, the drive in its bay, the priority set.
    static void recordAndApply(GameTestHelper helper) {
        BlockPos master = RackGameTests.rack(helper, BOTTOM_FRONT, Direction.NORTH);
        RackGameTests.install(helper, master, RackDeviceType.FIREWALL, 1, RackDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 3, RackDevice.class);
        NasDevice nas = RackGameTests.install(helper, master, RackDeviceType.NAS, 5, NasDevice.class);
        nas.items().set(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        nas.itemsChanged();
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        nas.handleAction(player, StorageDevice.ACTION_SET_PRIORITY, 7, "");
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);

        ItemStack elevation = held(player, RackTemplate.EMPTY.withContents(true).withSettings(true));
        RackElevations.record(player, rack, elevation);
        RackTemplate recorded = RackTemplate.of(elevation);
        helper.assertTrue(recorded != null && recorded.devices().size() == 3, "Recorded " + recorded);
        RackTemplate.Planned plannedNas = recorded.at(5);
        helper.assertTrue(plannedNas != null && plannedNas.contents().size() == 1 && plannedNas.settings().isPresent(), "NAS plan " + plannedNas);
        helper.assertTrue(plannedNas.contents().getFirst().item().get(net.zagdrath.encodedlogistics.registry.ModDataComponents.DRIVE_ID.get()) == null,
                "The recorded drive kept its id");
        helper.assertTrue(elevation.getMaxStackSize() == 1, "A written elevation stacks to " + elevation.getMaxStackSize());

        for (int u : new int[] { 1, 3, 5 }) {
            RackDevice removed = rack.remove(u);
            removed.clearContents();
        }
        player.getInventory().add(new ItemStack(ModItems.FIREWALL.get()));
        player.getInventory().add(new ItemStack(ModItems.UPS.get()));
        player.getInventory().add(new ItemStack(ModItems.NAS.get()));
        player.getInventory().add(new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        RackElevations.Result result = RackElevations.apply(player, rack, elevation);
        helper.assertTrue(result != null && result.installed() == 3 && result.skipped() == 0 && result.missing() == 0, "Applied " + result);
        helper.assertTrue(rack.deviceAt(1).type() == RackDeviceType.FIREWALL && rack.deviceAt(3).type() == RackDeviceType.UPS
                && rack.deviceAt(5).type() == RackDeviceType.NAS, "Devices " + rack.devices());
        StorageDevice applied = (StorageDevice) rack.deviceAt(5);
        helper.assertTrue(applied.items().get(0).getItem() instanceof StorageDriveItem, "No drive in the NAS");
        helper.assertTrue(applied.priority() == 7, "Priority " + applied.priority());
        helper.assertTrue(player.getInventory().countItem(ModItems.NAS.get()) == 0 && player.getInventory().countItem(ModItems.FIREWALL.get()) == 0,
                "Items weren't used up");
        helper.assertTrue(rack.isFrontOpen(), "The front door didn't open to install");
        helper.succeed();
    }

    // A full Blade Enclosure (sixteen Storage Blades with every drive bay filled, every rear module, a schematic in every
    // slot): recorded with contents, the plan saves and syncs; taken out; applied back into another rack, everything's
    // where it was.
    static void bladeEnclosureContents(GameTestHelper helper) {
        BlockPos master = RackGameTests.rack(helper, BOTTOM_FRONT, Direction.NORTH);
        BladeEnclosureDevice blades = RackGameTests.install(helper, master, RackDeviceType.BLADE_ENCLOSURE, 1, BladeEnclosureDevice.class);
        for (ChassisLayout.Bay bay : ChassisLayout.BLADE.bays()) {
            ChassisPart part = switch (bay.kind()) {
                case INTERCONNECT -> ChassisPart.INTERCONNECT_MODULE;
                case MANAGEMENT -> ChassisPart.MANAGEMENT_MODULE;
                case BLADE_PSU -> ChassisPart.BLADE_PSU;
                default -> ChassisPart.STORAGE_BLADE;
            };
            blades.items().set(bay.slot(), new ItemStack(ModItems.chassisPart(part).get()));
        }
        for (int blade = 0; blade < ChassisLayout.BLADES; blade++) {
            for (int k = 0; k < ChassisLayout.DRIVES_PER_BLADE; k++) {
                blades.items().set(ChassisLayout.bladeDriveSlot(blade, k), new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
            }
        }
        for (int i = 0; i < ChassisLayout.SCHEMATICS; i++) {
            blades.items().set(ChassisLayout.bladeSchematicSlot(i), new ItemStack(ModItems.ENCODED_SCHEMATIC_CRAFTING.get()));
        }
        blades.itemsChanged();
        int filled = (int) blades.items().stream().filter(stack -> !stack.isEmpty()).count();
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        ServerPlayer player = player(helper, GameType.CREATIVE);

        ItemStack elevation = held(player, RackTemplate.EMPTY.withContents(true));
        RackElevations.record(player, rack, elevation);
        RackTemplate recorded = RackTemplate.of(elevation);
        helper.assertTrue(recorded != null && recorded.at(1) != null && recorded.at(1).contents().size() == filled, "Recorded " + (recorded != null
                && recorded.at(1) != null ? recorded.at(1).contents().size() : -1) + " of " + filled);
        var registries = helper.getLevel().registryAccess();
        var saved = RackTemplate.CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), recorded);
        helper.assertTrue(saved.isSuccess(), "The plan doesn't save: " + saved.error().map(Object::toString).orElse(""));
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
        try {
            RackTemplate.STREAM_CODEC.encode(buf, recorded);
            helper.assertTrue(RackTemplate.STREAM_CODEC.decode(buf).equals(recorded), "The plan doesn't sync back the same");
        } catch (RuntimeException e) {
            helper.fail("The plan doesn't sync: " + e);
        } finally {
            buf.release();
        }

        rack.remove(1).clearContents();
        RackElevations.Result result = RackElevations.apply(player, rack, elevation);
        helper.assertTrue(result != null && result.installed() == 1 && result.missing() == 0, "Applied " + result);
        RackDevice applied = rack.deviceAt(1);
        helper.assertTrue(applied instanceof BladeEnclosureDevice, "No Blade Enclosure");
        int got = (int) applied.items().stream().filter(stack -> !stack.isEmpty()).count();
        helper.assertTrue(got == filled, "Applied " + got + " of " + filled + " items");
        helper.succeed();
    }

    // A Router planned at U1 where a UPS is, and a Firewall the player hasn't got: the Router's skipped, the Firewall's
    // missing, the UPS stays. In the plan: SKIP and MISSING.
    static void skipsAndReportsMissing(GameTestHelper helper) {
        BlockPos master = RackGameTests.rack(helper, BOTTOM_FRONT, Direction.NORTH);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 1, RackDevice.class);
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        RackTemplate plan = RackTemplate.EMPTY.place(RackDeviceType.ROUTER, 1).place(RackDeviceType.FIREWALL, 5);
        ItemStack elevation = held(player, plan);

        RackElevations.Plan preview = RackElevations.plan(player, plan, rack);
        helper.assertTrue(preview.count(RackElevations.State.SKIP) == 1 && preview.count(RackElevations.State.MISSING) == 1, "Plan " + preview.steps());
        helper.assertTrue(preview.missing() == 1, "Materials " + preview.materials());

        RackElevations.Result result = RackElevations.apply(player, rack, elevation);
        helper.assertTrue(result != null && result.installed() == 0 && result.skipped() == 1 && result.missing() == 1, "Applied " + result);
        helper.assertTrue(rack.deviceAt(1) != null && rack.deviceAt(1).type() == RackDeviceType.UPS, "The UPS was replaced");
        helper.assertTrue(rack.deviceAt(5) == null, "Something was installed at U5");
        helper.succeed();
    }

    // In creative (rackElevationCreativeFree on), nothing's needed: every device goes in from an empty inventory.
    static void creativeNeedsNoItems(GameTestHelper helper) {
        BlockPos master = RackGameTests.rack(helper, BOTTOM_FRONT, Direction.NORTH);
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        ServerPlayer player = player(helper, GameType.CREATIVE);
        RackTemplate plan = RackTemplate.EMPTY.place(RackDeviceType.FIREWALL, 1).place(RackDeviceType.SAN, 10);
        ItemStack elevation = held(player, plan);
        helper.assertTrue(RackElevations.plan(player, plan, rack).missing() == 0, "Creative materials are short");
        RackElevations.Result result = RackElevations.apply(player, rack, elevation);
        helper.assertTrue(result != null && result.installed() == 2 && result.missing() == 0, "Applied " + result);
        helper.assertTrue(rack.deviceAt(10) != null && rack.deviceAt(10).type() == RackDeviceType.SAN, "No SAN");
        helper.succeed();
    }

    // A device in a shulker box in the inventory is used; a NAS already at its unit is left and gets its planned drive.
    static void shulkerBoxAndSameDevice(GameTestHelper helper) {
        BlockPos master = RackGameTests.rack(helper, BOTTOM_FRONT, Direction.NORTH);
        RackGameTests.install(helper, master, RackDeviceType.NAS, 5, NasDevice.class);
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        RackDevice before = rack.deviceAt(5);
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        RackTemplate plan = RackTemplate.EMPTY.withContents(true).place(RackDeviceType.ROUTER, 1).place(RackDeviceType.NAS, 5);
        RackTemplate.Planned nas = plan.at(5);
        plan = plan.withDevices(List.of(plan.at(1), new RackTemplate.Planned(5, nas.type(), nas.size(),
                List.of(new RackTemplate.SlotItem(0, net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(
                        new ItemStack(ModItems.storageDrive(StorageTier.K8).get())))), java.util.Optional.empty())));
        ItemStack elevation = held(player, plan);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(ModItems.ROUTER.get()))));
        player.getInventory().add(box);
        player.getInventory().add(new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        RackElevations.Result result = RackElevations.apply(player, rack, elevation);
        helper.assertTrue(result != null && result.installed() == 1 && result.missing() == 0, "Applied " + result);
        helper.assertTrue(rack.deviceAt(1) != null && rack.deviceAt(1).type() == RackDeviceType.ROUTER, "The Router from the box wasn't installed");
        ItemContainerContents left = null;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.SHULKER_BOX)) {
                left = player.getInventory().getItem(slot).get(DataComponents.CONTAINER);
            }
        }
        helper.assertTrue(left != null && left.nonEmptyItemCopyStream().findAny().isEmpty(), "The Router is still in the box");
        helper.assertTrue(rack.deviceAt(5) == before, "The NAS was replaced");
        helper.assertTrue(before.items().get(0).getItem() instanceof StorageDriveItem, "The NAS didn't get its drive");
        helper.succeed();
    }

    // The editor's rules (the same on both sides): a device that doesn't fit, overlaps or is unknown is refused; moving
    // one is a remove and a place; the name is kept short.
    static void editRules(GameTestHelper helper) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        RackTemplate plan = RackElevationPayloads.Edit.apply(RackTemplate.EMPTY, new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.PLACE, 1,
                RackDeviceType.SAN.id().toString(), ""));
        helper.assertTrue(plan != null && plan.at(4) != null, "A SAN at U1 wasn't placed");
        helper.assertTrue(RackElevationPayloads.Edit.apply(plan, new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.PLACE, 3,
                RackDeviceType.FIREWALL.id().toString(), "")) == null, "An overlapping Firewall was placed");
        helper.assertTrue(RackElevationPayloads.Edit.apply(plan, new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.PLACE, 40,
                RackDeviceType.SAN.id().toString(), "")) == null, "A SAN past U42 was placed");
        helper.assertTrue(RackElevationPayloads.Edit.apply(plan, new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.PLACE, 10,
                "encodedlogistics:nothing", "")) == null, "An unknown device was placed");
        RackTemplate removed = RackElevationPayloads.Edit.apply(plan, new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.REMOVE, 2, "", ""));
        helper.assertTrue(removed != null && removed.devices().isEmpty(), "The SAN wasn't removed");
        RackTemplate named = RackElevationPayloads.Edit.apply(plan, new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.NAME, 0, "",
                "  WEBRACK  "));
        helper.assertTrue(named != null && named.name().equals("WEBRACK"), "Name " + (named != null ? named.name() : null));
        // A plan with overlaps (a hand-edited item) keeps only what fits.
        RackTemplate bad = RackTemplate.EMPTY.withDevices(List.of(new RackTemplate.Planned(1, RackDeviceType.SAN),
                new RackTemplate.Planned(2, RackDeviceType.FIREWALL), new RackTemplate.Planned(42, RackDeviceType.UPS)));
        helper.assertTrue(bad.valid().devices().size() == 1, "Valid " + bad.valid().devices());
        helper.succeed();
    }

    // Copy: written + blank = two written; clear: written alone = blank; neither takes anything else.
    static void recipes(GameTestHelper helper) {
        ItemStack written = new ItemStack(ModItems.RACK_ELEVATION.get());
        RackTemplate.write(written, RackTemplate.EMPTY.place(RackDeviceType.ROUTER, 1).withName("A"));
        ItemStack blank = new ItemStack(ModItems.RACK_ELEVATION.get());
        CraftingInput copyGrid = CraftingInput.of(2, 1, List.of(written, blank));
        helper.assertTrue(RackElevationRecipes.Copy.INSTANCE.matches(copyGrid, helper.getLevel()), "Copy didn't match");
        ItemStack copy = RackElevationRecipes.Copy.INSTANCE.assemble(copyGrid);
        helper.assertTrue(RackTemplate.of(copy) != null && RackTemplate.of(copy).equals(RackTemplate.of(written)), "Copy " + RackTemplate.of(copy));
        helper.assertTrue(!RackElevationRecipes.Copy.INSTANCE.getRemainingItems(copyGrid).get(0).isEmpty(), "The original was used up");
        helper.assertTrue(!RackElevationRecipes.Copy.INSTANCE.matches(CraftingInput.of(2, 1, List.of(written, written.copy())), helper.getLevel()),
                "Two written ones matched");
        CraftingInput clearGrid = CraftingInput.of(1, 1, List.of(written));
        helper.assertTrue(RackElevationRecipes.Clear.INSTANCE.matches(clearGrid, helper.getLevel()), "Clear didn't match");
        ItemStack cleared = RackElevationRecipes.Clear.INSTANCE.assemble(clearGrid);
        helper.assertTrue(RackTemplate.of(cleared) == null && cleared.getMaxStackSize() == 16, "Cleared " + cleared.getComponentsPatch());
        helper.assertTrue(ItemStack.isSameItemSameComponents(cleared, blank), "A cleared one doesn't stack with a new one");
        helper.assertTrue(!RackElevationRecipes.Clear.INSTANCE.matches(CraftingInput.of(1, 1, List.of(blank)), helper.getLevel()), "Clear took a blank");
        helper.succeed();
    }
}
