/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelReader;

// Client-only helpers called from common code behind an isClientSide() check.
public final class ClientRenderHooks {
    private ClientRenderHooks() {}

    // A controller's look depends on blocks up to two away (whether its neighbours are column pieces), while vanilla
    // only re-meshes one block round a change: re-mesh the sections two blocks round it.
    public static void controllerChanged(LevelReader level, BlockPos pos) {
        if (level instanceof ClientLevel clientLevel) {
            clientLevel.setSectionRangeDirty(SectionPos.blockToSectionCoord(pos.getX() - 2), SectionPos.blockToSectionCoord(pos.getY() - 2),
                    SectionPos.blockToSectionCoord(pos.getZ() - 2), SectionPos.blockToSectionCoord(pos.getX() + 2),
                    SectionPos.blockToSectionCoord(pos.getY() + 2), SectionPos.blockToSectionCoord(pos.getZ() + 2));
        }
    }
}
