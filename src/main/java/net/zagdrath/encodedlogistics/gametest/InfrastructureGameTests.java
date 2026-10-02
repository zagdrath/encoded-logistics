/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.CapacitorBankBlock;
import net.zagdrath.encodedlogistics.block.PowerInletBlock;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableConnection;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.CapacitorBankBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.item.CableFacadeItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;

// Power and cable infrastructure: Fiber Cable, anchors and facades, the Power Inlet, Capacitor Bank and Segment
// Isolator.
final class InfrastructureGameTests {
    private InfrastructureGameTests() {}

    // --- Helpers ---

    private static void cable(GameTestHelper helper, BlockPos pos, CableTier tier, CableColor color) {
        NetworkCableBlock block = ModBlocks.cable(tier, color).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    private static void inlet(GameTestHelper helper, BlockPos pos, Direction front) {
        helper.setBlock(pos, ModBlocks.POWER_INLET.get().defaultBlockState().setValue(PowerInletBlock.FACING, front));
    }

    private static void assertSide(GameTestHelper helper, BlockPos pos, Direction side, CableConnection expected) {
        CableConnection actual = NetworkCableBlock.connection(helper.getBlockState(pos), side);
        helper.assertTrue(actual == expected, pos + " " + side + " is " + actual + ", expected " + expected);
    }

    private static @Nullable EnergyHandler energy(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), side);
    }

    private static int insert(EnergyHandler handler, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static NetworkSnapshot snapshot(GameTestHelper helper, BlockPos controller) {
        long id = helper.getBlockEntity(controller, NetworkControllerBlockEntity.class).getStructureId();
        return ControllerStructures.get(helper.getLevel()).snapshot(id);
    }

    // Uses an item on a cable, aimed at one of its sides.
    private static void useOnSide(GameTestHelper helper, Player player, BlockPos pos, Direction side, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(pos);
        Vec3 at = Vec3.atCenterOf(absolute).add(side.getStepX() * 0.3, side.getStepY() * 0.3, side.getStepZ() * 0.3);
        BlockHitResult hit = new BlockHitResult(at, side, absolute, false);
        if (stack.isEmpty()) {
            helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
        } else {
            helper.getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        }
    }

    // --- Fiber Cable ---

    // Fiber Cable carries 32 lanes from a controller face, joins a Network Cable of a compatible colour and keeps away
    // from other dyes.
    static void fiberCable(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0), fiber = new BlockPos(1, 1, 0), normal = new BlockPos(2, 1, 0), blue = new BlockPos(1, 1, 1);
        helper.setBlock(controller, ModBlocks.NETWORK_CONTROLLER.get());
        cable(helper, fiber, CableTier.FIBER, CableColor.RED);
        cable(helper, normal, CableTier.NORMAL, CableColor.NEUTRAL);
        cable(helper, blue, CableTier.FIBER, CableColor.BLUE);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    assertSide(helper, fiber, Direction.WEST, CableConnection.BLOCK);
                    assertSide(helper, fiber, Direction.EAST, CableConnection.CABLE);
                    assertSide(helper, fiber, Direction.SOUTH, CableConnection.NONE);
                    helper.assertTrue(CableTier.FIBER.lanes() == 32, "Fiber carries " + CableTier.FIBER.lanes());
                    NetworkSnapshot snapshot = snapshot(helper, controller);
                    helper.assertTrue(snapshot.laneCapacity() == 32, "Capacity is " + snapshot.laneCapacity());
                    helper.assertTrue(snapshot.devices().stream().anyMatch(entry -> entry.item().getPath().equals("red_fiber_cable")),
                            "Fiber Cable not on the network");
                    AABB shape = helper.getBlockState(fiber).getShape(helper.getLevel(), helper.absolutePos(fiber)).bounds();
                    helper.assertTrue(shape.minY == 4 / 16.0 && shape.maxY == 12 / 16.0, "Fiber shape is " + shape);
                })
                .thenSucceed();
    }

    // --- Anchors ---

    // An anchor on a side stops both cables connecting there and puts the cable on its junction cube, and stays on
    // when the cable is dyed; sneak-using it with an empty hand takes it off again (dropping it) and the cables join
    // back up.
    static void anchorBlocksSide(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 0), b = new BlockPos(1, 1, 1), c = new BlockPos(1, 1, 2);
        cable(helper, a, CableTier.NORMAL, CableColor.NEUTRAL);
        cable(helper, b, CableTier.NORMAL, CableColor.NEUTRAL);
        cable(helper, c, CableTier.DENSE, CableColor.NEUTRAL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> assertSide(helper, b, Direction.SOUTH, CableConnection.CABLE))
                .thenExecute(() -> useOnSide(helper, player, b, Direction.SOUTH, new ItemStack(ModItems.CABLE_ANCHOR.get(), 2)))
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(player.getMainHandItem().getCount() == 1, "Anchor not used up");
                    helper.assertTrue(helper.getBlockEntity(b, CableBlockEntity.class).getAttachments().anchored(Direction.SOUTH), "No anchor");
                    assertSide(helper, b, Direction.SOUTH, CableConnection.NONE);
                    assertSide(helper, c, Direction.NORTH, CableConnection.NONE);
                    assertSide(helper, b, Direction.NORTH, CableConnection.CABLE);
                    // The anchor's plate and bolts stick out of the 6 px cube toward the south.
                    AABB shape = helper.getBlockState(b).getShape(helper.getLevel(), helper.absolutePos(b)).bounds();
                    helper.assertTrue(shape.maxZ == 14.5 / 16.0 && shape.minX == 4 / 16.0, "Anchored shape is " + shape);
                })
                // Dyeing the cable keeps its anchor.
                .thenExecute(() -> useOnSide(helper, player, b, Direction.NORTH, new ItemStack(Items.DYE.pick(DyeColor.BLUE))))
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(b).getBlock() == ModBlocks.cable(CableTier.NORMAL, CableColor.BLUE).get(), "Not dyed");
                    helper.assertTrue(helper.getBlockEntity(b, CableBlockEntity.class).getAttachments().anchored(Direction.SOUTH),
                            "Dyeing lost the anchor");
                    assertSide(helper, b, Direction.SOUTH, CableConnection.NONE);
                })
                .thenExecute(() -> {
                    player.setShiftKeyDown(true);
                    useOnSide(helper, player, b, Direction.SOUTH, ItemStack.EMPTY);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(b, CableBlockEntity.class).getAttachments().isEmpty(), "Anchor still there");
                    assertSide(helper, b, Direction.SOUTH, CableConnection.CABLE);
                    assertSide(helper, c, Direction.NORTH, CableConnection.CABLE);
                    helper.assertItemEntityPresent(ModItems.CABLE_ANCHOR.get(), b, 2);
                })
                .thenSucceed();
    }

    // --- Facades ---

    // Six facades make the cable a full block to walk on; the attachments survive saving and loading and drop with the
    // cable when it's broken.
    static void facades(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        cable(helper, pos, CableTier.NORMAL, CableColor.NEUTRAL);
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(CableFacadeItem.canCopy(stone), "Stone can't be a facade");
                    helper.assertFalse(CableFacadeItem.canCopy(Blocks.GLASS.defaultBlockState()), "Glass can be a facade");
                    helper.assertFalse(CableFacadeItem.canCopy(Blocks.CHEST.defaultBlockState()), "A chest can be a facade");
                    CableBlockEntity cable = helper.getBlockEntity(pos, CableBlockEntity.class);
                    CableAttachments all = CableAttachments.EMPTY;
                    for (Direction side : Direction.values()) {
                        all = all.with(side, CableAttachments.Attachment.facade(stone));
                    }
                    cable.setAttachments(all);
                })
                .thenExecute(() -> {
                    VoxelShape collision = helper.getBlockState(pos).getCollisionShape(helper.getLevel(), helper.absolutePos(pos));
                    helper.assertTrue(Block.isShapeFullBlock(collision), "Six facades aren't a full block: " + collision.bounds());

                    // Save and load the block entity again.
                    CableBlockEntity cable = helper.getBlockEntity(pos, CableBlockEntity.class);
                    CompoundTag saved = cable.saveWithFullMetadata(helper.getLevel().registryAccess());
                    BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), helper.getBlockState(pos), saved,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(loaded instanceof CableBlockEntity copy && copy.getAttachments().equals(cable.getAttachments()),
                            "Attachments didn't survive saving");
                })
                .thenExecute(() -> helper.destroyBlock(pos))
                .thenIdle(1)
                .thenExecute(() -> helper.assertItemEntityPresent(ModItems.CABLE_FACADE.get(), pos, 2))
                .thenSucceed();
    }

    // --- Power Inlet and Capacitor Bank ---

    // FE goes in through the inlet's front only and fills the controller first, then the bank; the inlet lights up and
    // the bank's gauge and comparator follow.
    static void inletFillsControllerThenBank(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0), cablePos = new BlockPos(1, 1, 0), inlet = new BlockPos(2, 1, 0), bank = new BlockPos(1, 1, 1);
        helper.setBlock(controller, ModBlocks.NETWORK_CONTROLLER.get());
        helper.setBlock(bank, ModBlocks.CAPACITOR_BANK.get());
        inlet(helper, inlet, Direction.EAST);
        cable(helper, cablePos, CableTier.NORMAL, CableColor.NEUTRAL);
        int[] taken = new int[1];
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    assertSide(helper, cablePos, Direction.EAST, CableConnection.BLOCK);
                    assertSide(helper, cablePos, Direction.SOUTH, CableConnection.BLOCK);
                    helper.assertTrue(energy(helper, inlet, Direction.WEST) == null, "Energy on the inlet's back");
                    EnergyHandler port = energy(helper, inlet, Direction.EAST);
                    helper.assertTrue(port != null, "No energy on the inlet's front");
                    taken[0] = insert(port, 30_000);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(taken[0] == 16_384, "Inlet took " + taken[0] + " in one tick");
                    helper.assertTrue(helper.getBlockState(inlet).getValue(PowerInletBlock.POWERED), "Inlet not powered");
                    NetworkControllerBlockEntity block = helper.getBlockEntity(controller, NetworkControllerBlockEntity.class);
                    helper.assertTrue(block.getEnergy() > 16_000, "Controller has " + block.getEnergy());
                    helper.assertTrue(helper.getBlockEntity(bank, CapacitorBankBlockEntity.class).getStored() == 0, "Bank filled before the controller");
                })
                .thenExecute(() -> taken[0] = insert(energy(helper, inlet, Direction.EAST), 16_384))
                .thenIdle(25)
                .thenExecute(() -> {
                    NetworkControllerBlockEntity block = helper.getBlockEntity(controller, NetworkControllerBlockEntity.class);
                    CapacitorBankBlockEntity capacitor = helper.getBlockEntity(bank, CapacitorBankBlockEntity.class);
                    // The controller took what it had room for and the bank the rest; the drain comes out of the bank.
                    helper.assertTrue(block.getEnergy() == 25_000, "Controller has " + block.getEnergy());
                    helper.assertTrue(capacitor.getStored() > 7_000 && capacitor.getStored() < 7_768, "Bank has " + capacitor.getStored());
                    helper.assertTrue(helper.getBlockState(bank).getValue(CapacitorBankBlock.FILL) == 1, "Bank shows " + helper.getBlockState(bank));
                    helper.assertTrue(capacitor.comparatorSignal() == 1, "Comparator reads " + capacitor.comparatorSignal());
                    NetworkSnapshot snapshot = snapshot(helper, controller);
                    helper.assertTrue(snapshot.capacity() == 25_000 + 2_000_000, "Network capacity is " + snapshot.capacity());
                })
                .thenSucceed();
    }

    // --- Segment Isolator ---

    // An isolator splits the network: the inlet past it isn't on the controller's network (it takes nothing) until the
    // isolator is swapped for a cable. Its light is on while both ends are attached.
    static void isolatorSplitsNetwork(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0), isolator = new BlockPos(2, 1, 0), inlet = new BlockPos(4, 1, 0);
        helper.setBlock(controller, ModBlocks.NETWORK_CONTROLLER.get());
        helper.setBlock(isolator, ModBlocks.SEGMENT_ISOLATOR.get().defaultBlockState().setValue(SegmentIsolatorBlock.AXIS, Direction.Axis.X));
        inlet(helper, inlet, Direction.EAST);
        cable(helper, new BlockPos(1, 1, 0), CableTier.NORMAL, CableColor.NEUTRAL);
        cable(helper, new BlockPos(3, 1, 0), CableTier.NORMAL, CableColor.NEUTRAL);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(isolator).getValue(SegmentIsolatorBlock.ACTIVE), "Isolator not active");
                    helper.assertTrue(insert(energy(helper, inlet, Direction.EAST), 1_000) == 0, "Energy crossed the isolator");
                    NetworkSnapshot snapshot = snapshot(helper, controller);
                    helper.assertTrue(snapshot.devices().stream().anyMatch(entry -> entry.item().getPath().equals("segment_isolator")),
                            "Isolator not on the controller's network");
                    helper.assertFalse(snapshot.devices().stream().anyMatch(entry -> entry.item().getPath().equals("power_inlet")),
                            "Inlet on the controller's network");
                })
                .thenExecute(() -> cable(helper, isolator, CableTier.NORMAL, CableColor.NEUTRAL))
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(insert(energy(helper, inlet, Direction.EAST), 1_000) == 1_000, "Inlet still cut off"))
                .thenSucceed();
    }
}
