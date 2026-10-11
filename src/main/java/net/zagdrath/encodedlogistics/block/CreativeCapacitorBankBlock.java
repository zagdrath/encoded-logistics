/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

// The Creative Capacitor Bank: a Capacitor Bank that never runs out and takes anything (CapacitorBankBlockEntity's
// creative side). It's always full, gives its network and any face all the FE asked of it, and takes (voids) all
// it's offered, with no transfer limit. Creative menu only: it has no recipe.
public class CreativeCapacitorBankBlock extends CapacitorBankBlock {
    public CreativeCapacitorBankBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILL, 4));
    }
}
