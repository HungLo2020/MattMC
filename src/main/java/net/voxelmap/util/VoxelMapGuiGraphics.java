package net.voxelmap.util;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2f;

public class VoxelMapGuiGraphics {

    public static void blitFloat(GuiGraphics graphics, RenderPipeline pipeline, ResourceLocation texture, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color) {
        graphics.submitRustSemanticBlit(texture, Math.round(x), Math.round(y), Math.round(w), Math.round(h), minu, minv, maxu, maxv, color);
        return;
    }

    public static void blitCircular(
            GuiGraphics graphics, ResourceLocation texture, float centerX, float centerY, float radius,
            float angleRadians, float mapScale, float sourceOffsetX, float sourceOffsetY, int color) {
        if (net.vulkanic.gui.RustGalGuiRenderer.tryEnqueueVoxelMapMask(texture,
                graphics.guiWidth(), graphics.guiHeight(), centerX, centerY, radius, angleRadians,
                mapScale, sourceOffsetX, sourceOffsetY, color, true,
                graphics.guiRenderState.currentSemanticLayerOrder(net.minecraft.client.gui.render.state.GuiRenderState.SemanticPhase.ELEMENTS)) == null) {
            throw new IllegalStateException("Rust VoxelMap circular map semantic mesh was unavailable");
        }
                    return;
    }

                public static void blitSquareMap(
                    GuiGraphics graphics, ResourceLocation texture, float centerX, float centerY, float halfSize,
                    float angleRadians, float mapScale, float sourceOffsetX, float sourceOffsetY, int color) {
                    if (net.vulkanic.gui.RustGalGuiRenderer.tryEnqueueVoxelMapMask(texture,
                            graphics.guiWidth(), graphics.guiHeight(), centerX, centerY, halfSize, angleRadians,
                            mapScale, sourceOffsetX, sourceOffsetY, color, false,
                            graphics.guiRenderState.currentSemanticLayerOrder(net.minecraft.client.gui.render.state.GuiRenderState.SemanticPhase.ELEMENTS)) == null) {
                        throw new IllegalStateException("Rust VoxelMap square map semantic mesh was unavailable");
                    }
                    return;
                }

    public static void fillGradient(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color00, int color10, int color01, int color11) {
        graphics.guiRenderState.submitGuiElement(new FourColoredRectangleRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()), x0, y0, x1, y1, color00, color10, color01, color11, graphics.scissorStack.peek()));
    }
}
