package net.vulkanic.gui;

import java.util.ArrayList;
import java.util.List;
import net.sodium.api.util.NormI8;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.vulkanic.bridge.VulkanicGalBridge;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;

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
		return collectInternal(model, texture, x0, y0, x1, y1, scale, guiScale, guiWidth, guiHeight,
			guiPose, clip, setup, tint, materialMode, 0.0F, 0.0F);
	}

	/** Copies an animated model layer with its explicit texture-matrix offset. */
	static Result collectAnimated(Model<?> model, ResourceLocation texture, int x0, int y0, int x1, int y1,
		float scale, int guiScale, int guiWidth, int guiHeight, float[] guiPose,
		ScreenRectangle clip, ModelPose setup, int tint, int materialMode,
		float uvOffsetU, float uvOffsetV, int textureWidth, int textureHeight) {
		if (textureWidth <= 0 || textureHeight <= 0
			|| !Float.isFinite(uvOffsetU) || !Float.isFinite(uvOffsetV)) return null;
		return collectInternal(model, texture, x0, y0, x1, y1, scale, guiScale, guiWidth, guiHeight,
			guiPose, clip, setup, tint, materialMode, uvOffsetU, uvOffsetV);
	}

	private static Result collectInternal(Model<?> model, ResourceLocation texture, int x0, int y0, int x1, int y1,
		float scale, int guiScale, int guiWidth, int guiHeight, float[] guiPose,
		ScreenRectangle clip, ModelPose setup, int tint, int materialMode,
		float uvOffsetU, float uvOffsetV) {
		if (model == null || texture == null || x1 <= x0 || y1 <= y0 || guiScale <= 0
			|| guiWidth <= 0 || guiHeight <= 0
			|| materialMode < 1 || materialMode > 4
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
        CaptureConsumer capture = new CaptureConsumer(tint, uvOffsetU, uvOffsetV);
        model.renderToBuffer(pose, capture, 15728880, OverlayTexture.NO_OVERLAY);
        if (capture.overflowed || capture.vertices.size() < 3 || capture.vertices.size() % 4 != 0) return null;
        // Admit the copied asset only after every bounded geometry check has
        // succeeded. A rejected preview must not leave a staged resource
        // without a corresponding semantic draw token.
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
        return new Result(batch, new ScreenRectangle(x0, y0, x1 - x0, y1 - y0), List.of(asset));
    }

    /** Copies a bounded renderer-layer item submission into explicit GUI mesh batches. */
    static List<Result> collectBakedQuads(List<BakedQuad> quads, int[] tintLayers,
        Matrix4f layerPose, int x0, int y0, int x1, int y1, float scale, int guiScale,
        int guiWidth, int guiHeight, float[] guiPose, ScreenRectangle clip, int materialMode,
        ItemStackRenderState.FoilType foilType) {
        if (quads == null || quads.isEmpty() || quads.size() > 256 || tintLayers == null
            || layerPose == null || materialMode < 1 || materialMode > 4 || x1 <= x0 || y1 <= y0
            || guiScale <= 0 || guiWidth <= 0 || guiHeight <= 0 || !Float.isFinite(scale) || scale <= 0.0F
            || guiPose == null || guiPose.length != 6 || foilType == null) return List.of();
        for (float value : guiPose) if (!Float.isFinite(value)) return List.of();
        if (clip != null && (clip.left() < 0 || clip.top() < 0 || clip.width() < 0 || clip.height() < 0
            || clip.left() > guiWidth || clip.top() > guiHeight
            || clip.width() > guiWidth - clip.left() || clip.height() > guiHeight - clip.top())) return List.of();
        long scaledWidth = (long)(x1 - x0) * guiScale + 2L;
        long scaledHeight = (long)(y1 - y0) * guiScale + 2L;
        if (scaledWidth > Integer.MAX_VALUE || scaledHeight > Integer.MAX_VALUE) return List.of();
        int width = Math.max(2, (int)scaledWidth), height = Math.max(2, (int)scaledHeight);
        PoseStack pose = new PoseStack();
        pose.translate(width / 2.0F, height / 2.0F, 0.0F);
        float modelScale = guiScale * scale;
        pose.scale(modelScale, modelScale, -modelScale);
        pose.last().pose().mul(layerPose);
        RustGalGuiRawImageAssets.Asset glint = null;
        if (foilType == ItemStackRenderState.FoilType.STANDARD
            || foilType == ItemStackRenderState.FoilType.SPECIAL) {
            glint = RustGalGuiRawImageAssets.resolve(ItemRenderer.ENCHANTED_GLINT_ITEM);
            if (glint == null) return List.of();
        }
        List<Result> results = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            if (!(quad instanceof net.sodium.client.model.quad.BakedQuadView view) || view.getSprite() == null) return List.of();
			RustGalGuiRawImageAssets.Asset baseAsset = view.getSprite().contents().isAnimated()
				? RustGalGuiRawImageAssets.resolveAnimatedSprite(view.getSprite())
				: RustGalGuiRawImageAssets.resolve(view.getSprite().contents().name());
            if (baseAsset == null) return List.of();
            long assetId = baseAsset.assetId();
            List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = new ArrayList<>(4);
            for (int index = 0; index < 4; index++) {
                Vector3f position = pose.last().pose().transformPosition(view.getX(index), view.getY(index), view.getZ(index), new Vector3f());
                float u = view.getTexU(index), v = view.getTexV(index);
                if (!Float.isFinite(position.x()) || !Float.isFinite(position.y()) || !Float.isFinite(position.z())
                    || !Float.isFinite(u) || !Float.isFinite(v)) return List.of();
                int tint = !quad.isTinted() || quad.tintIndex() < 0 || quad.tintIndex() >= tintLayers.length
                    ? 0xffffffff : tintLayers[quad.tintIndex()];
                if (tint == -1) tint = 0xffffffff;
                vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(
                    new float[] {position.x(), position.y(), position.z()}, new float[] {u, v}, new float[] {u, v},
                    tint, view.getVertexNormal(index)));
            }
            int clipMode = clip == null ? 0 : 1;
            VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
                GuiRenderStratum.GUI_ITEM.order(), 0, materialMode, 2, assetId, 0L, 0.0F,
                new float[] {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}, guiPose,
                x0, y0, x1, y1, guiWidth, guiHeight, width, height, 1,
                clipMode, clip == null ? 0 : clip.left(), clip == null ? 0 : clip.top(),
                clip == null ? 0 : clip.width(), clip == null ? 0 : clip.height(),
                vertices, List.of(0, 1, 2, 2, 3, 0));
            results.add(new Result(batch, new ScreenRectangle(x0, y0, x1 - x0, y1 - y0), List.of(baseAsset)));
            if (glint != null) {
                float ticks = (float)(Util.getMillis() * Minecraft.getInstance().options.glintSpeed().get() * 8.0);
                float g = (ticks % 110000.0F) / 110000.0F;
                float h = (ticks % 30000.0F) / 30000.0F;
                float angle = (float)(Math.PI / 18.0), glintScale = 8.0F;
                float cos = (float)Math.cos(angle) * glintScale, sin = (float)Math.sin(angle) * glintScale;
                List<VulkanicGalBridge.GuiMeshVertexRecord> glintVertices = new ArrayList<>(4);
                int strength = Mth.clamp((int)Math.round(Minecraft.getInstance().options.glintStrength().get() * 255.0F), 0, 255);
                int glintColor = ARGB.color(strength, 255, 255, 255);
                for (int index = 0; index < 4; index++) {
                    float u = view.getTexU(index), v = view.getTexV(index);
                    float gu, gv;
                    if (foilType == ItemStackRenderState.FoilType.SPECIAL) {
                        Matrix4f inversePose = new Matrix4f(pose.last().pose()).invert();
                        Matrix3f inverseNormal = new Matrix3f(pose.last().pose()).invert();
                        Vector3f projected = inversePose.transformPosition(view.getX(index), view.getY(index), view.getZ(index), new Vector3f());
                        Vector3f normal = inverseNormal.transform(new Vector3f(
                            (byte)(view.getVertexNormal(index) & 0xff) / 127.0F,
                            (byte)((view.getVertexNormal(index) >>> 8) & 0xff) / 127.0F,
                            (byte)((view.getVertexNormal(index) >>> 16) & 0xff) / 127.0F), new Vector3f());
                        Direction direction = Direction.getApproximateNearest(normal.x(), normal.y(), normal.z());
                        projected.rotateY((float)Math.PI).rotateX((float)(-Math.PI / 2.0)).rotate(direction.getRotation());
                        gu = -projected.x() * ItemRenderer.SPECIAL_FOIL_TEXTURE_SCALE;
                        gv = -projected.y() * ItemRenderer.SPECIAL_FOIL_TEXTURE_SCALE;
                    } else {
                        gu = cos * u - sin * v - g;
                        gv = sin * u + cos * v + h;
                    }
                    Vector3f position = pose.last().pose().transformPosition(view.getX(index), view.getY(index), view.getZ(index), new Vector3f());
                    glintVertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(
                        new float[] {position.x(), position.y(), position.z()}, new float[] {gu, gv}, new float[] {gu, gv},
                        glintColor, view.getVertexNormal(index)));
                }
                VulkanicGalBridge.GuiMeshBatchRecord glintBatch = new VulkanicGalBridge.GuiMeshBatchRecord(
                    GuiRenderStratum.GUI_ITEM.order(), 1, 4, 2, glint.assetId(), 0L, 0.0F,
                    new float[] {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}, guiPose,
                    x0, y0, x1, y1, guiWidth, guiHeight, width, height, 1,
                    clipMode, clip == null ? 0 : clip.left(), clip == null ? 0 : clip.top(),
                    clip == null ? 0 : clip.width(), clip == null ? 0 : clip.height(),
                    glintVertices, List.of(0, 1, 2, 2, 3, 0));
                results.add(new Result(glintBatch, new ScreenRectangle(x0, y0, x1 - x0, y1 - y0), List.of(glint)));
            }
        }
        if (glint != null) {
            // Publish the shared glint asset only after every bounded quad and
            // foil transform has validated successfully.
        }
        return List.copyOf(results);
    }

    public interface ModelPose { void apply(PoseStack pose); }
    record Result(VulkanicGalBridge.GuiMeshBatchRecord batch, ScreenRectangle bounds,
                  List<RustGalGuiRawImageAssets.Asset> assets) {
        Result {
            assets = List.copyOf(assets);
        }
    }

    private static final class CaptureConsumer implements VertexConsumer {
        final List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = new ArrayList<>();
        boolean overflowed;
        float x, y, z, u, v, nx, ny, nz;
        int color;
        final int tint;
        final float uvOffsetU, uvOffsetV;
        CaptureConsumer(int tint, float uvOffsetU, float uvOffsetV) {
            this.tint = tint;
            this.uvOffsetU = uvOffsetU;
            this.uvOffsetV = uvOffsetV;
            this.color = tint;
        }
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
            this.u=u + uvOffsetU; this.v=v + uvOffsetV;
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
}
