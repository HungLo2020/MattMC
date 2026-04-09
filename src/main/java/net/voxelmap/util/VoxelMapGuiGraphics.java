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
    public static void blitFloatGradient(GuiGraphics graphics, RenderPipeline pipeline, GpuTextureView textureView, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color, int color2) {
        graphics.guiRenderState.submitGuiElement(new FloatBlitRenderState(pipeline, TextureSetup.singleTexture(textureView),
                new Matrix3x2f(graphics.pose()), x, y, x + w, y + h,
                minu, maxu, minv, maxv, color, color2, graphics.scissorStack.peek()));
    }

    public static void blitFloatQuad(GuiGraphics graphics, RenderPipeline pipeline, GpuTextureView textureView, float x0, float y0, float x1, float y1, float minu, float maxu, float minv, float maxv, int color) {
        graphics.guiRenderState.submitGuiElement(new FloatBlitRenderState(
                pipeline,
                TextureSetup.singleTexture(textureView),
                new Matrix3x2f(graphics.pose()),
                x0,
                y0,
                x1,
                y1,
                minu,
                maxu,
                minv,
                maxv,
                color,
                color,
                graphics.scissorStack.peek()));
    }

    public static void blitFloat(GuiGraphics graphics, RenderPipeline pipeline, GpuTextureView textureView, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color) {
        blitFloatGradient(graphics, pipeline, textureView, x, y, w, h, minu, maxu, minv, maxv, color, color);
    }

    public static void blitFloat(GuiGraphics graphics, RenderPipeline pipeline, GpuTextureView textureView, GpuTextureView textureView2, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color) {
        graphics.guiRenderState.submitGuiElement(new FloatBlitRenderState(
                pipeline,
                TextureSetup.doubleTexture(textureView, textureView2),
                new Matrix3x2f(graphics.pose()),
                x,
                y,
                x + w,
                y + h,
                minu,
                maxu,
                minv,
                maxv,
                color,
                color,
                graphics.scissorStack.peek()));
    }

    public static void blitFloatGradient(GuiGraphics graphics, RenderPipeline pipeline, ResourceLocation texture, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color, int color2) {
        blitFloatGradient(graphics, pipeline, Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView(), x, y, w, h, minu, maxu, minv, maxv, color, color2);
    }

    public static void blitFloat(GuiGraphics graphics, RenderPipeline pipeline, ResourceLocation texture, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color) {
        blitFloatGradient(graphics, pipeline, Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView(), x, y, w, h, minu, maxu, minv, maxv, color, color);
    }

    public static void blitCircular(
            GuiGraphics graphics,
            RenderPipeline pipeline,
            GpuTextureView textureView,
            float centerX,
            float centerY,
            float radius,
            float angleRadians,
            float mapScale,
            float sourceOffsetX,
            float sourceOffsetY,
            int color) {
        graphics.guiRenderState.submitGuiElement(
                new CircularMaskBlitRenderState(
                        pipeline,
                        TextureSetup.singleTexture(textureView),
                        new Matrix3x2f(graphics.pose()),
                        centerX,
                        centerY,
                        radius,
                        angleRadians,
                        mapScale,
                        sourceOffsetX,
                        sourceOffsetY,
                        color,
                        graphics.scissorStack.peek()));
    }

                public static void blitSquareMap(
                    GuiGraphics graphics,
                    RenderPipeline pipeline,
                    GpuTextureView textureView,
                    float centerX,
                    float centerY,
                    float halfSize,
                    float angleRadians,
                    float mapScale,
                    float sourceOffsetX,
                    float sourceOffsetY,
                    int color) {
                graphics.guiRenderState.submitGuiElement(
                    new SquareMapBlitRenderState(
                        pipeline,
                        TextureSetup.singleTexture(textureView),
                        new Matrix3x2f(graphics.pose()),
                        centerX,
                        centerY,
                        halfSize,
                        angleRadians,
                        mapScale,
                        sourceOffsetX,
                        sourceOffsetY,
                        color,
                        graphics.scissorStack.peek()));
                }

    public static void fillGradient(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color00, int color10, int color01, int color11) {
        graphics.guiRenderState.submitGuiElement(new FourColoredRectangleRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()), x0, y0, x1, y1, color00, color10, color01, color11, graphics.scissorStack.peek()));
    }
}
