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
        rejectRustTextureView("blitFloatGradient");
        graphics.guiRenderState.submitGuiElement(new FloatBlitRenderState(pipeline, TextureSetup.singleTexture(textureView),
                new Matrix3x2f(graphics.pose()), x, y, x + w, y + h,
                minu, maxu, minv, maxv, color, color2, graphics.scissorStack.peek()));
    }

    public static void blitFloatQuad(GuiGraphics graphics, RenderPipeline pipeline, GpuTextureView textureView, float x0, float y0, float x1, float y1, float minu, float maxu, float minv, float maxv, int color) {
        rejectRustTextureView("blitFloatQuad");
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
        rejectRustTextureView("blitFloat-double-texture");
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
        // Contract marker: isWholeFrameVulkanEnabled()) is the shell half of
        // this combined ownership gate; finalized Vulkan is equivalent.
        if (net.vulkanic.gui.RustGalGuiRenderer.isWholeFrameVulkanEnabled()
                || net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            if (color != color2) {
                graphics.submitRustSemanticGradientBlit(texture, x, y, w, h, minu, maxu, minv, maxv, color, color2);
                return;
            }
            graphics.submitRustSemanticBlit(texture, Math.round(x), Math.round(y), Math.round(w), Math.round(h), minu, minv, maxu, maxv, color);
            return;
        }
        if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            throw new IllegalStateException("VoxelMap semantic GUI blit is unavailable before Rust Vulkan presentation admission");
        }
        blitFloatGradient(graphics, pipeline, Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView(), x, y, w, h, minu, maxu, minv, maxv, color, color2);
    }

    public static void blitFloat(GuiGraphics graphics, RenderPipeline pipeline, ResourceLocation texture, float x, float y, float w, float h, float minu, float maxu, float minv, float maxv, int color) {
        if (net.vulkanic.gui.RustGalGuiRenderer.isWholeFrameVulkanEnabled()
                || net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            graphics.submitRustSemanticBlit(texture, Math.round(x), Math.round(y), Math.round(w), Math.round(h), minu, minv, maxu, maxv, color);
            return;
        }
        if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            throw new IllegalStateException("VoxelMap semantic GUI blit is unavailable before Rust Vulkan presentation admission");
        }
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
        rejectRustTextureView("blitCircular");
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

    public static void blitCircular(
            GuiGraphics graphics, ResourceLocation texture, float centerX, float centerY, float radius,
            float angleRadians, float mapScale, float sourceOffsetX, float sourceOffsetY, int color) {
        if (net.vulkanic.gui.RustGalGuiRenderer.isWholeFrameVulkanEnabled()
                || net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            if (net.vulkanic.gui.RustGalGuiRenderer.tryEnqueueVoxelMapMask(texture,
                    graphics.guiWidth(), graphics.guiHeight(), centerX, centerY, radius, angleRadians,
                    mapScale, sourceOffsetX, sourceOffsetY, color, true,
                    graphics.guiRenderState.currentSemanticLayerOrder(net.minecraft.client.gui.render.state.GuiRenderState.SemanticPhase.ELEMENTS)) == null) {
                throw new IllegalStateException("Rust VoxelMap circular map semantic mesh was unavailable");
            }
                        return;
                    }
                    if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
                        throw new IllegalStateException("VoxelMap semantic GUI blit is unavailable before Rust Vulkan presentation admission");
                    }
                    blitCircular(graphics, RenderPipelines.GUI_TEXTURED,
                Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView(),
                centerX, centerY, radius, angleRadians, mapScale, sourceOffsetX, sourceOffsetY, color);
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
                    rejectRustTextureView("blitSquareMap");
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

                public static void blitSquareMap(
                    GuiGraphics graphics, ResourceLocation texture, float centerX, float centerY, float halfSize,
                    float angleRadians, float mapScale, float sourceOffsetX, float sourceOffsetY, int color) {
                    if (net.vulkanic.gui.RustGalGuiRenderer.isWholeFrameVulkanEnabled()
                            || net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
                        if (net.vulkanic.gui.RustGalGuiRenderer.tryEnqueueVoxelMapMask(texture,
                                graphics.guiWidth(), graphics.guiHeight(), centerX, centerY, halfSize, angleRadians,
                                mapScale, sourceOffsetX, sourceOffsetY, color, false,
                                graphics.guiRenderState.currentSemanticLayerOrder(net.minecraft.client.gui.render.state.GuiRenderState.SemanticPhase.ELEMENTS)) == null) {
                            throw new IllegalStateException("Rust VoxelMap square map semantic mesh was unavailable");
                        }
                        return;
                    }
                    blitSquareMap(graphics, RenderPipelines.GUI_TEXTURED,
                            Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView(),
                            centerX, centerY, halfSize, angleRadians, mapScale, sourceOffsetX, sourceOffsetY, color);
                }

    private static void rejectRustTextureView(String producer) {
        if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
                || net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
            throw new IllegalStateException(
                "Rust whole-frame VoxelMap GUI producer " + producer
                    + " requires a semantic ResourceLocation; Java texture views are unavailable"
            );
        }
    }

    public static void fillGradient(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color00, int color10, int color01, int color11) {
        graphics.guiRenderState.submitGuiElement(new FourColoredRectangleRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(graphics.pose()), x0, y0, x1, y1, color00, color10, color01, color11, graphics.scissorStack.peek()));
    }
}
