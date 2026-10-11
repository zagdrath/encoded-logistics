/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

// The render type for the mod's translucent outlines (rack units, facade previews, located devices, machine bridges),
// picked as vanilla picks its block outline's. With improved transparency (Graphics: Fabulous) translucent features
// are drawn through order-independent transparency, and lines_translucent has no OIT pipelines: drawing with it then
// crashes ("Render type lines_translucent does not have OIT pipelines set up"). lines_translucent_no_depth_write has
// them, so that's used there.
public final class OutlineRenderTypes {
    private OutlineRenderTypes() {}

    public static RenderType translucentLines() {
        return Minecraft.getInstance().gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
    }
}
