/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.model;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.mojang.math.Quadrant;

import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// The model parts a cable with attachments is built from (CableModel), baked as standalone models in every rotation the
// blockstates use: each tier's and colour's cubes and arms, the two anchors, and the blank facade panel. Once baking is
// done, every cable blockstate's model is wrapped in a CableModel.
public final class CableParts {
    private static final Map<String, StandaloneModelKey<BlockStateModelPart>> KEYS = new HashMap<>();

    // A cable's own parts, already rotated: [axis ordinal] for the straight cube, [Direction ordinal] for the arms.
    record Body(BlockStateModelPart[] cubeStraight, BlockStateModelPart cubeJunction, BlockStateModelPart[] armStraight,
            BlockStateModelPart[] armJunction, BlockStateModelPart[] armBlock) {}

    private CableParts() {}

    // --- Registering ---

    public static void register(ModelEvent.RegisterStandalone event) {
        for (CableTier tier : CableTier.values()) {
            for (CableColor color : CableColor.values()) {
                for (String part : parts(tier)) {
                    for (Direction toward : Direction.values()) {
                        register(event, model(tier, color, part), toward);
                    }
                }
            }
        }
        for (Direction toward : Direction.values()) {
            register(event, "cable_anchor", toward);
            register(event, "cable_anchor_dense", toward);
        }
        register(event, "facade_solid", Direction.NORTH);
    }

    private static void register(ModelEvent.RegisterStandalone event, String model, Direction toward) {
        String name = name(model, toward);
        StandaloneModelKey<BlockStateModelPart> key = new StandaloneModelKey<>(() -> EncodedLogistics.MODID + ":" + name);
        KEYS.put(name, key);
        event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(EncodedLogistics.id("block/" + model), rotation(toward)));
    }

    // Every part model of a tier: Fiber Cable has one cube and one cable arm for both straight runs and junctions.
    private static String[] parts(CableTier tier) {
        return tier == CableTier.FIBER ? new String[] { "cube", "arm_cable", "arm_block" }
                : new String[] { "cube_straight", "cube_junction", "arm_straight", "arm_junction", "arm_block" };
    }

    private static String model(CableTier tier, CableColor color, String part) {
        String folder = switch (tier) {
            case NORMAL -> "normal";
            case DENSE -> "dense";
            case FIBER -> "fiber";
        };
        String colour = color == CableColor.NEUTRAL ? "neutral" : color.dye().getSerializedName();
        return "cable/" + folder + "/" + colour + "/" + part;
    }

    private static String name(String model, Direction toward) {
        return model + "#" + toward.getSerializedName();
    }

    // A part modelled pointing north, turned toward a side as the blockstates do: east y90, south y180, west y270,
    // up x270, down x90.
    private static BlockModelRotation rotation(Direction toward) {
        Quadrant x = Quadrant.R0, y = Quadrant.R0;
        switch (toward) {
            case NORTH -> {}
            case EAST -> y = Quadrant.R90;
            case SOUTH -> y = Quadrant.R180;
            case WEST -> y = Quadrant.R270;
            case UP -> x = Quadrant.R270;
            case DOWN -> x = Quadrant.R90;
        }
        return BlockModelRotation.get(Quadrant.fromXYZAngles(x, y, Quadrant.R0));
    }

    // --- Wrapping the baked cables ---

    public static void wrap(ModelEvent.ModifyBakingResult event) {
        StandaloneModelLoader.BakedModels baked = event.getBakingResult().standaloneModels();
        Map<Direction, BlockStateModelPart> anchors = new EnumMap<>(Direction.class);
        Map<Direction, BlockStateModelPart> denseAnchors = new EnumMap<>(Direction.class);
        for (Direction toward : Direction.values()) {
            anchors.put(toward, get(baked, "cable_anchor", toward));
            denseAnchors.put(toward, get(baked, "cable_anchor_dense", toward));
        }
        FacadeQuads facades = new FacadeQuads(get(baked, "facade_solid", Direction.NORTH));
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        for (CableTier tier : CableTier.values()) {
            for (CableColor color : CableColor.values()) {
                Body body = body(baked, tier, color);
                NetworkCableBlock block = ModBlocks.cable(tier, color).get();
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BlockStateModel original = models.get(state);
                    if (original != null) {
                        models.put(state, new CableModel(original, tier, body, tier.dense() ? denseAnchors : anchors, facades));
                    }
                }
            }
        }
    }

    private static Body body(StandaloneModelLoader.BakedModels baked, CableTier tier, CableColor color) {
        String cubeStraight = tier == CableTier.FIBER ? "cube" : "cube_straight";
        String cubeJunction = tier == CableTier.FIBER ? "cube" : "cube_junction";
        String armStraight = tier == CableTier.FIBER ? "arm_cable" : "arm_straight";
        String armJunction = tier == CableTier.FIBER ? "arm_cable" : "arm_junction";
        BlockStateModelPart[] straight = new BlockStateModelPart[3];
        // The straight cube runs along Z as modelled; the blockstates turn it y90 for X and x90 for Y.
        straight[Direction.Axis.Z.ordinal()] = get(baked, model(tier, color, cubeStraight), Direction.NORTH);
        straight[Direction.Axis.X.ordinal()] = get(baked, model(tier, color, cubeStraight), Direction.EAST);
        straight[Direction.Axis.Y.ordinal()] = get(baked, model(tier, color, cubeStraight), Direction.DOWN);
        BlockStateModelPart[] armsStraight = new BlockStateModelPart[6], armsJunction = new BlockStateModelPart[6], armsBlock = new BlockStateModelPart[6];
        for (Direction toward : Direction.values()) {
            armsStraight[toward.ordinal()] = get(baked, model(tier, color, armStraight), toward);
            armsJunction[toward.ordinal()] = get(baked, model(tier, color, armJunction), toward);
            armsBlock[toward.ordinal()] = get(baked, model(tier, color, "arm_block"), toward);
        }
        return new Body(straight, get(baked, model(tier, color, cubeJunction), Direction.NORTH), armsStraight, armsJunction, armsBlock);
    }

    private static BlockStateModelPart get(StandaloneModelLoader.BakedModels baked, String model, Direction toward) {
        BlockStateModelPart part = baked.get(KEYS.get(name(model, toward)));
        if (part == null) {
            throw new IllegalStateException("Cable part " + name(model, toward) + " was not baked");
        }
        return part;
    }
}
