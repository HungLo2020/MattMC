package net.vulkanic.gui;

import java.util.ArrayList;
import java.util.List;
import net.sodium.api.util.NormI8;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.bridge.VulkanicGalBridge;

/** Copies model-based GUI PIP geometry into the explicit Rust GUI mesh ABI. */
final class GuiModelPipSemanticCollector {
    /**
     * Hard bound for one copied model preview. Keep this equal to Rust's
     * GUI_MESH_MAX_VERTICES so a preview rejected here can never consume an
     * unbounded Java list only to be rejected at the FFI boundary.
     */
    private static final int MAX_CAPTURED_VERTICES = 65_536;
    private GuiModelPipSemanticCollector() {}

    static Result collect(Model<?> model, ResourceLocation texture, int x0, int y0, int x1, int y1,
        float scale, int guiScale, int guiWidth, int guiHeight, float[] guiPose,
        ScreenRectangle clip, ModelPose setup) {
        return collect(model, texture, x0, y0, x1, y1, scale, guiScale, guiWidth, guiHeight, guiPose, clip, setup, 0xffffffff);
    }

    static Result collect(Model<?> model, ResourceLocation texture, int x0, int y0, int x1, int y1,
        float scale, int guiScale, int guiWidth, int guiHeight, float[] guiPose,
        ScreenRectangle clip, ModelPose setup, int tint) {
        return collect(model, texture, x0, y0, x1, y1, scale, guiScale, guiWidth, guiHeight, guiPose, clip, setup, tint, 1);
    }

    static Result collect(Model<?> model, ResourceLocation texture, int x0, int y0, int x1, int y1,
        float scale, int guiScale, int guiWidth, int guiHeight, float[] guiPose,
        ScreenRectangle clip, ModelPose setup, int tint, int materialMode) {
        if (model == null || texture == null || x1 <= x0 || y1 <= y0 || guiScale <= 0
            || !Float.isFinite(scale) || scale <= 0.0F || setup == null
            || guiPose == null || guiPose.length != 6) {
            return null;
        }
        for (float value : guiPose) {
            if (!Float.isFinite(value)) return null;
        }
        if (clip != null && (clip.left() < 0 || clip.top() < 0 || clip.width() < 0 || clip.height() < 0
            || clip.left() > guiWidth || clip.top() > guiHeight
            || clip.width() > guiWidth - clip.left() || clip.height() > guiHeight - clip.top())) {
            return null;
        }
        RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(texture);
        if (asset == null) return null;
        RustGalGuiRawImageAssets.stage(asset);
        long scaledWidth = (long)(x1 - x0) * guiScale + 2L;
        long scaledHeight = (long)(y1 - y0) * guiScale + 2L;
        if (scaledWidth > Integer.MAX_VALUE || scaledHeight > Integer.MAX_VALUE) return null;
        int width = Math.max(2, (int)scaledWidth);
        int height = Math.max(2, (int)scaledHeight);
        PoseStack pose = new PoseStack();
        pose.translate(width / 2.0F, height / 2.0F, 0.0F);
        float modelScale = guiScale * scale;
        pose.scale(modelScale, modelScale, -modelScale);
        setup.apply(pose);
        CaptureConsumer capture = new CaptureConsumer(tint);
        model.renderToBuffer(pose, capture, 15728880,  OverlayTextureNoOverlay.VALUE);
        if (capture.overflowed || capture.vertices.size() < 3 || capture.vertices.size() % 4 != 0) return null;
        List<Integer> indices = new ArrayList<>(capture.vertices.size() / 4 * 6);
        for (int i = 0; i < capture.vertices.size(); i += 4) {
            indices.add(i); indices.add(i + 1); indices.add(i + 2);
            indices.add(i + 2); indices.add(i + 3); indices.add(i);
        }
        float[] identity = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
        int clipMode = clip == null ? 0 : 1;
        int clipLeft = clip == null ? 0 : clip.left();
        int clipTop = clip == null ? 0 : clip.top();
        int clipWidth = clip == null ? 0 : clip.width();
        int clipHeight = clip == null ? 0 : clip.height();
        VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
            GuiRenderStratum.GUI_ITEM.order(), 0, materialMode, 2, asset.assetId(), 0L, 0.0F,
            identity, guiPose, x0, y0, x1, y1, guiWidth, guiHeight, width, height, 1,
            clipMode, clipLeft, clipTop, clipWidth, clipHeight,
            capture.vertices, indices);
        return new Result(batch, new ScreenRectangle(x0, y0, x1 - x0, y1 - y0));
    }

    public interface ModelPose { void apply(PoseStack pose); }
    record Result(VulkanicGalBridge.GuiMeshBatchRecord batch, ScreenRectangle bounds) {}

    private static final class CaptureConsumer implements VertexConsumer {
        final List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = new ArrayList<>();
        boolean overflowed;
        float x, y, z, u, v, nx, ny, nz;
        int color;
        final int tint;
        CaptureConsumer(int tint) { this.tint = tint; this.color = tint; }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            if (vertices.size() >= MAX_CAPTURED_VERTICES) {
                overflowed = true;
                return this;
            }
            this.x=x; this.y=y; this.z=z; return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            int supplied = ((a&255)<<24)|((r&255)<<16)|((g&255)<<8)|(b&255);
            color = net.minecraft.util.ARGB.multiply(supplied, tint);
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) {
            this.u=u; this.v=v;
            return this;
        }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            if (overflowed) return this;
            nx=x; ny=y; nz=z;
            vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[]{this.x,this.y,this.z}, new float[]{u,v}, new float[]{u,v}, color, NormI8.pack(nx, ny, nz)));
            return this;
        }
    }

    private static final class OverlayTextureNoOverlay { static final int VALUE = 0; }
}
