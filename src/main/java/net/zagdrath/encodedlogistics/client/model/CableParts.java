/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.math.Quadrant;

import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
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
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// The model parts a cable with attachments is built from (CableModel), baked as standalone models in every rotation the
// blockstates use: each tier's and colour's cubes and arms, the two anchors, every part (unlit and lit) and the blank
// facade panel. Once baking is done, every cable blockstate's model is wrapped in a CableModel, the part
// host's in a CableModel without a cable, and the Drive Bay's in a DriveBayModel (its sleds are baked here too).
public final class CableParts {
    private static final Map<String, StandaloneModelKey<BlockStateModelPart>> KEYS = new HashMap<>();
    private static final StandaloneModelKey<DriveBayModel.Sleds> SLEDS = new StandaloneModelKey<>(() -> "encodedlogistics:drive_bay sleds");
    // How far a host's terminal moves from the far side to the block it's mounted on: the 16 px block less its 2.5 px housing.
    private static final float HOST_SHIFT = 13.5F / 16;

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
        for (PartType part : PartType.values()) {
            for (Direction toward : Direction.values()) {
                register(event, part.model(false), toward);
                register(event, part.model(true), toward);
            }
        }
        event.register(SLEDS, new DriveBayModel.Unbaked());
    }

    // model: a path under models/, without "block/" for block models.
    private static void register(ModelEvent.RegisterStandalone event, String model, Direction toward) {
        String name = name(model, toward);
        StandaloneModelKey<BlockStateModelPart> key = new StandaloneModelKey<>(() -> EncodedLogistics.MODID + ":" + name);
        KEYS.put(name, key);
        String path = model.startsWith("part/") ? model : "block/" + model;
        event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(EncodedLogistics.id(path), rotation(toward)));
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
        // [part][lit ? 1 : 0][Direction ordinal]: on a cable, and on a part host (a part that faces out moved back against
        // the block it's mounted on; one that faces the block as on a cable).
        BlockStateModelPart[][][] parts = new BlockStateModelPart[PartType.values().length][2][6];
        BlockStateModelPart[][][] hostParts = new BlockStateModelPart[PartType.values().length][2][6];
        for (PartType part : PartType.values()) {
            for (Direction toward : Direction.values()) {
                for (int lit = 0; lit < 2; lit++) {
                    String model = part.model(lit == 1);
                    parts[part.ordinal()][lit][toward.ordinal()] = get(baked, model, toward);
                    hostParts[part.ordinal()][lit][toward.ordinal()] = part.facesOut() ? mounted(get(baked, model, toward.getOpposite()), toward)
                            : get(baked, model, toward);
                }
            }
        }
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        for (BlockState state : ModBlocks.PART_HOST.get().getStateDefinition().getPossibleStates()) {
            BlockStateModel original = models.get(state);
            if (original != null) {
                models.put(state, new CableModel(original, CableTier.NORMAL, null, anchors, hostParts, facades));
            }
        }
        DriveBayModel.Sleds sleds = baked.get(SLEDS);
        if (sleds != null) {
            for (BlockState state : ModBlocks.DRIVE_BAY.get().getStateDefinition().getPossibleStates()) {
                BlockStateModel original = models.get(state);
                if (original != null) {
                    models.put(state, new DriveBayModel(original, sleds));
                }
            }
        }
        for (CableTier tier : CableTier.values()) {
            for (CableColor color : CableColor.values()) {
                Body body = body(baked, tier, color);
                NetworkCableBlock block = ModBlocks.cable(tier, color).get();
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    BlockStateModel original = models.get(state);
                    if (original != null) {
                        models.put(state, new CableModel(original, tier, body, tier.dense() ? denseAnchors : anchors, parts, facades));
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

    // A part host's part that faces out: the part on the far side, moved back against the block it's mounted on
    // (toward), still facing out; its stub, which would poke into that block, is left out.
    private static BlockStateModelPart mounted(BlockStateModelPart part, Direction toward) {
        float dx = toward.getStepX() * HOST_SHIFT, dy = toward.getStepY() * HOST_SHIFT, dz = toward.getStepZ() * HOST_SHIFT;
        QuadCollection.Builder quads = new QuadCollection.Builder();
        List<BakedQuad> all = new ArrayList<>(part.getQuads(null));
        for (Direction face : Direction.values()) {
            all.addAll(part.getQuads(face));
        }
        for (BakedQuad quad : all) {
            Vector3f[] moved = new Vector3f[4];
            boolean inside = true;
            for (int i = 0; i < 4; i++) {
                moved[i] = new Vector3f(quad.position(i)).add(dx, dy, dz);
                inside &= moved[i].x > -1.0E-3F && moved[i].x < 1.001F && moved[i].y > -1.0E-3F && moved[i].y < 1.001F
                        && moved[i].z > -1.0E-3F && moved[i].z < 1.001F;
            }
            if (inside) {
                quads.addUnculledFace(new BakedQuad(moved[0], moved[1], moved[2], moved[3], quad.packedUV0(), quad.packedUV1(), quad.packedUV2(),
                        quad.packedUV3(), quad.direction(), quad.materialInfo(), quad.bakedNormals(), quad.bakedColors()));
            }
        }
        return new SimpleModelWrapper(quads.build(), part.useAmbientOcclusion(), part.particleMaterial());
    }

    private static BlockStateModelPart get(StandaloneModelLoader.BakedModels baked, String model, Direction toward) {
        BlockStateModelPart part = baked.get(KEYS.get(name(model, toward)));
        if (part == null) {
            throw new IllegalStateException("Cable part " + name(model, toward) + " was not baked");
        }
        return part;
    }
}
