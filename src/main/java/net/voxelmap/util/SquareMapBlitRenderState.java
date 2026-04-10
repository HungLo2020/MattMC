package net.voxelmap.util;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record SquareMapBlitRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        float centerX,
        float centerY,
        float halfSize,
        float angleRadians,
        float mapScale,
        float sourceOffsetX,
        float sourceOffsetY,
        int color,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
    public SquareMapBlitRenderState(
            RenderPipeline renderPipeline,
            TextureSetup textureSetup,
            Matrix3x2f matrix3x2f,
            float centerX,
            float centerY,
            float halfSize,
            float angleRadians,
            float mapScale,
            float sourceOffsetX,
            float sourceOffsetY,
            int color,
            @Nullable ScreenRectangle screenRectangle) {
        this(
                renderPipeline,
                textureSetup,
                matrix3x2f,
                centerX,
                centerY,
                halfSize,
                angleRadians,
                mapScale,
                sourceOffsetX,
                sourceOffsetY,
                color,
                screenRectangle,
                getBounds(centerX, centerY, halfSize, matrix3x2f, screenRectangle));
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        float cos = Mth.cos(this.angleRadians());
        float sin = Mth.sin(this.angleRadians());
        float inverseScale = 1.0F / this.mapScale();
        float screenToSourceScale = 256.0F / this.halfSize();
        int halfPixels = Mth.floor(this.halfSize());
        float left = this.centerX() - this.halfSize();
        float right = this.centerX() + this.halfSize();

        for (int row = -halfPixels; row < halfPixels; row++) {
            float top = this.centerY() + row;
            float bottom = top + 1.0F;

            this.addVertex(vertexConsumer, left, top, cos, sin, inverseScale, screenToSourceScale);
            this.addVertex(vertexConsumer, left, bottom, cos, sin, inverseScale, screenToSourceScale);
            this.addVertex(vertexConsumer, right, bottom, cos, sin, inverseScale, screenToSourceScale);
            this.addVertex(vertexConsumer, right, top, cos, sin, inverseScale, screenToSourceScale);
        }
    }

    private void addVertex(VertexConsumer vertexConsumer, float x, float y, float cos, float sin, float inverseScale, float screenToSourceScale) {
        float dx = (x - this.centerX()) * screenToSourceScale * inverseScale;
        float dy = (y - this.centerY()) * screenToSourceScale * inverseScale;
        float sourceX = cos * dx + sin * dy + this.sourceOffsetX();
        float sourceY = -sin * dx + cos * dy + this.sourceOffsetY();
        float u = (sourceX + 256.0F) / 512.0F;
        float v = (sourceY + 256.0F) / 512.0F;
        vertexConsumer.addVertexWith2DPose(this.pose(), x, y).setUv(u, v).setColor(this.color());
    }

    @Nullable
    private static ScreenRectangle getBounds(float centerX, float centerY, float halfSize, Matrix3x2f matrix3x2f, @Nullable ScreenRectangle screenRectangle) {
        int left = Mth.floor(centerX - halfSize);
        int top = Mth.floor(centerY - halfSize);
        int size = Mth.ceil(halfSize * 2.0F);
        ScreenRectangle screenRectangle2 = new ScreenRectangle(left, top, size, size).transformMaxBounds(matrix3x2f);
        return screenRectangle != null ? screenRectangle.intersection(screenRectangle2) : screenRectangle2;
    }
}