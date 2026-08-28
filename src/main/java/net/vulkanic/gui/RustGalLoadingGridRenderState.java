package net.vulkanic.gui;

import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.render.TextureSetup;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.Nullable;

/** Semantic loading-grid data carried to the Rust collector after GUI traversal begins. */
public record RustGalLoadingGridRenderState(int[] colors, int gridSize, int originX, int originY, int cellSize, int stride, int guiWidth, int guiHeight) implements GuiElementRenderState {
    public RustGalLoadingGridRenderState {
        colors = colors.clone();
    }
    @Override public void buildVertices(VertexConsumer vertexConsumer) {}
    @Override public RenderPipeline pipeline() { return RenderPipelines.CROSSHAIR; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override @Nullable public ScreenRectangle scissorArea() { return null; }
    @Override @Nullable public ScreenRectangle bounds() { return new ScreenRectangle(originX, originY, gridSize * stride, gridSize * stride); }
    @Override public String shaderInputParityGeometryContext() { return "rust-gal:loading-grid"; }
    @Override public int[] colors() { return colors.clone(); }
}
