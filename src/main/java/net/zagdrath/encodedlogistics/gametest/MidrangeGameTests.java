/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.zagdrath.encodedlogistics.midrange.ExpansionCabinetBlock;
import net.zagdrath.encodedlogistics.midrange.FootprintBlock;
import net.zagdrath.encodedlogistics.midrange.MidrangeStates;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// The Midrange line's blocks: footprints placed and broken whole, their shapes, and the Expansion Cabinet attaching to a
// Midrange System on either side (one per system, the same facing) and coming loose again.
final class MidrangeGameTests {
    private MidrangeGameTests() {}

    // Places a footprint block's master as a player would (its dummies with it), facing north.
    private static void place(GameTestHelper helper, Block block, BlockPos pos) {
        BlockState state = block.defaultBlockState().setValue(FootprintBlock.FACING, Direction.NORTH);
        helper.setBlock(pos, state);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), null, ItemStack.EMPTY);
    }

    private static boolean shaped(GameTestHelper helper, BlockPos pos) {
        return !helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos), CollisionContext.empty()).isEmpty();
    }

    // A Midrange System facing north: its dummy to the east; a Line Printer's three; shapes on every block; breaking a
    // dummy breaks the whole footprint.
    static void footprints(GameTestHelper helper) {
        BlockPos system = new BlockPos(1, 1, 1), printer = new BlockPos(1, 1, 4);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        place(helper, ModBlocks.LINE_PRINTER.get(), printer);
        helper.assertBlockProperty(system.east(), FootprintBlock.PART, FootprintBlock.Part.DUMMY);
        for (BlockPos pos : new BlockPos[] { printer.east(), printer.above(), printer.east().above() }) {
            helper.assertBlockProperty(pos, FootprintBlock.PART, FootprintBlock.Part.DUMMY);
        }
        helper.assertTrue(shaped(helper, system) && shaped(helper, system.east()) && shaped(helper, printer.above()), "A footprint block has no shape");
        FootprintBlock block = (FootprintBlock) ModBlocks.LINE_PRINTER.get();
        helper.assertTrue(helper.absolutePos(printer).equals(block.master(helper.getLevel(), helper.absolutePos(printer.east().above()),
                helper.getBlockState(printer.east().above()))), "A dummy can't find its master");
        helper.getLevel().destroyBlock(helper.absolutePos(printer.east().above()), false);
        helper.assertBlockNotPresent(ModBlocks.LINE_PRINTER.get(), printer);
        helper.assertBlockNotPresent(ModBlocks.LINE_PRINTER.get(), printer.east());
        helper.getLevel().destroyBlock(helper.absolutePos(system.east()), false);
        helper.assertBlockNotPresent(ModBlocks.MIDRANGE_SYSTEM.get(), system);
        helper.succeed();
    }

    // A cabinet left of the master: attached pos, the system's expansion neg; broken, the system's back to none. One right
    // of the dummy: neg / pos; a second on the other side then stays unattached, as does one facing another way.
    static void expansionCabinet(GameTestHelper helper) {
        BlockPos system = new BlockPos(2, 1, 2), left = system.west(), right = system.east(2), other = new BlockPos(2, 1, 4);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        BlockState cabinet = ModBlocks.EXPANSION_CABINET.get().defaultBlockState().setValue(ExpansionCabinetBlock.FACING, Direction.NORTH);
        helper.setBlock(left, cabinet);
        helper.assertBlockProperty(left, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.POS);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, MidrangeStates.Side.NEG);
        helper.setBlock(left, net.minecraft.world.level.block.Blocks.AIR);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, MidrangeStates.Side.NONE);

        helper.setBlock(right, cabinet);
        helper.assertBlockProperty(right, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.NEG);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, MidrangeStates.Side.POS);
        helper.setBlock(left, cabinet);
        helper.assertBlockProperty(left, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.NONE);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, MidrangeStates.Side.POS);

        // Facing another way beside another system: nothing.
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), other);
        helper.setBlock(other.west(), cabinet.setValue(ExpansionCabinetBlock.FACING, Direction.SOUTH));
        helper.assertBlockProperty(other.west(), ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.NONE);
        helper.assertBlockProperty(other, MidrangeStates.EXPANSION, MidrangeStates.Side.NONE);
        helper.succeed();
    }
}
