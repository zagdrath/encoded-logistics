/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

// One craft (or one processing run) a Scheduler handed to a Fabricator or Gateway: whose it is (the Core and job), which
// of the job's steps, the schematic, and the inputs it was given. What it makes goes back to that job.
public record CraftTask(BlockPos core, UUID job, int step, Schematic schematic, List<ItemStack> inputs) {
    public static final Codec<CraftTask> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("core").forGetter(CraftTask::core),
            UUIDUtil.CODEC.fieldOf("job").forGetter(CraftTask::job),
            Codec.INT.fieldOf("step").forGetter(CraftTask::step),
            Schematic.CODEC.fieldOf("schematic").forGetter(CraftTask::schematic),
            ItemStack.CODEC.listOf().fieldOf("inputs").forGetter(CraftTask::inputs))
            .apply(i, CraftTask::new));

    public CraftTask {
        inputs = inputs.stream().map(ItemStack::copy).toList();
    }
}
