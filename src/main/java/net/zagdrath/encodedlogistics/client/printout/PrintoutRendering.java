/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.printout;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.PrintoutItem;
import net.zagdrath.encodedlogistics.midrange.Printout;

// A Printout in the world (HANDOFF 9): held, its page in view as a map is (both hands when the other is empty, one hand
// otherwise; previews/printout_in_hand); in an item frame, its first page on the frame; its icon one sheet or a fan-fold
// stack (the encodedlogistics:printout_pages property, items/printout.json). The page is a texture (PrintoutPages).
@EventBusSubscriber(modid = EncodedLogistics.MODID, value = Dist.CLIENT)
public final class PrintoutRendering {
    private static final ContextKey<Printout> FRAMED = new ContextKey<>(EncodedLogistics.id("framed_printout"));
    // A page in map units (a map is 0-128 with a 7 unit border): 3 wide to 4 tall.
    private static final float HAND_LEFT = 4, HAND_RIGHT = 124, HAND_TOP = -16, HAND_BOTTOM = 144;
    private static final float FRAME_LEFT = 16, FRAME_RIGHT = 112, FRAME_TOP = 0, FRAME_BOTTOM = 128;

    private PrintoutRendering() {}

    // The icon: its page count.
    public record Pages() implements RangeSelectItemModelProperty {
        public static final MapCodec<Pages> MAP_CODEC = MapCodec.unit(new Pages());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            Printout printout = PrintoutItem.printout(stack);
            return printout == null ? 1 : printout.pages().size();
        }

        @Override
        public MapCodec<Pages> type() {
            return MAP_CODEC;
        }
    }

    @SubscribeEvent
    static void registerProperty(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(EncodedLogistics.id("printout_pages"), Pages.MAP_CODEC);
    }

    // An item frame's render state carries the printout in it.
    @SubscribeEvent
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void registerFrameModifier(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) ItemFrameRenderer.class, (entity, state) -> {
            if (entity instanceof ItemFrame frame && state instanceof ItemFrameRenderState frameState) {
                frameState.setRenderData(FRAMED, PrintoutItem.printout(frame.getItem()));
            }
        });
    }

    // --- In an item frame: the first page, as a map is drawn ---

    @SubscribeEvent
    static void onItemInFrame(RenderItemInFrameEvent event) {
        ItemFrameRenderState state = event.getItemFrameRenderState();
        Printout printout = state.getRenderData(FRAMED);
        if (printout == null) {
            return;
        }
        event.setCanceled(true);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(state.rotation % 4 * 2 * 45.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(0.0078125F, 0.0078125F, 0.0078125F);
        pose.translate(-64.0F, -64.0F, -1.0F);
        page(event.getSubmitNodeCollector(), pose, PrintoutPages.texture(printout, 0), state.isGlowFrame ? 15728850 : state.lightCoords,
                FRAME_LEFT, FRAME_TOP, FRAME_RIGHT, FRAME_BOTTOM);
        pose.popPose();
    }

    // --- Held ---

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        Printout printout = PrintoutItem.printout(event.getItemStack());
        LocalPlayer player = Minecraft.getInstance().player;
        if (printout == null || player == null) {
            return;
        }
        event.setCanceled(true);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float attack = event.getSwingProgress(), height = event.getEquipProgress();
        if (event.getHand() == InteractionHand.MAIN_HAND && player.getOffhandItem().isEmpty()) {
            twoHanded(pose, event.getInterpolatedPitch(), height, attack);
        } else {
            HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            oneHanded(pose, arm, height, attack);
        }
        // As vanilla's renderMap: the page facing the player, in map units.
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(0.38F, 0.38F, 0.38F);
        pose.translate(-0.5F, -0.5F, 0.0F);
        pose.scale(0.0078125F, 0.0078125F, 0.0078125F);
        page(event.getSubmitNodeCollector(), pose, PrintoutPages.texture(printout, 0), event.getPackedLight(), HAND_LEFT, HAND_TOP, HAND_RIGHT, HAND_BOTTOM);
        pose.popPose();
    }

    // Vanilla's two-handed map, without the arms.
    private static void twoHanded(PoseStack pose, float pitch, float height, float attack) {
        float sqrtAttack = Mth.sqrt(attack);
        pose.translate(0.0F, 0.2F * Mth.sin(attack * (float) Math.PI) / 2.0F, -0.4F * Mth.sin(sqrtAttack * (float) Math.PI));
        float tilt = mapTilt(pitch);
        pose.translate(0.0F, 0.04F + height * -1.2F + tilt * -0.5F, -0.72F);
        pose.mulPose(Axis.XP.rotationDegrees(tilt * -85.0F));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(sqrtAttack * (float) Math.PI) * 20.0F));
        pose.scale(2.0F, 2.0F, 2.0F);
    }

    // Vanilla's one-handed map, without the arm.
    private static void oneHanded(PoseStack pose, HumanoidArm arm, float height, float attack) {
        float invert = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.translate(invert * 0.125F, -0.125F, 0.0F);
        pose.translate(invert * 0.51F, -0.08F + height * -1.2F, -0.75F);
        float sqrtAttack = Mth.sqrt(attack), xSwing = Mth.sin(sqrtAttack * (float) Math.PI);
        pose.translate(invert * -0.5F * xSwing, 0.4F * Mth.sin(sqrtAttack * (float) (Math.PI * 2)) - 0.3F * xSwing, -0.3F * Mth.sin(attack * (float) Math.PI));
        pose.mulPose(Axis.XP.rotationDegrees(xSwing * -45.0F));
        pose.mulPose(Axis.YP.rotationDegrees(invert * xSwing * -30.0F));
    }

    private static float mapTilt(float pitch) {
        float tilt = Mth.clamp(1.0F - pitch / 45.0F + 0.1F, 0.0F, 1.0F);
        return -Mth.cos(tilt * (float) Math.PI) * 0.5F + 0.5F;
    }

    private static void page(SubmitNodeCollector collector, PoseStack pose, Identifier texture, int light, float left, float top, float right, float bottom) {
        collector.submitCustomGeometry(pose, RenderTypes.text(texture), (at, buffer) -> {
            buffer.addVertex(at, left, bottom, 0.0F).setColor(-1).setUv(0.0F, 1.0F).setLight(light);
            buffer.addVertex(at, right, bottom, 0.0F).setColor(-1).setUv(1.0F, 1.0F).setLight(light);
            buffer.addVertex(at, right, top, 0.0F).setColor(-1).setUv(1.0F, 0.0F).setLight(light);
            buffer.addVertex(at, left, top, 0.0F).setColor(-1).setUv(0.0F, 0.0F).setLight(light);
        });
    }
}
