package net.vulkanic.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.sodium.client.model.quad.BakedQuadView;
import net.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * Copies ordinary vanilla GUI item-model semantics before any native packing.
 * It intentionally retains no renderer, model, atlas, or GPU objects. The
 * collected family is private until a Rust-owned GUI mesh pass consumes it.
 */
public final class GuiItemMeshSemanticCollector {
	private GuiItemMeshSemanticCollector() {
	}

	/**
	 * Copies the bounded standard-3D GUI item setup. This is the semantic
	 * equivalent of {@code Standard3dItemRenderer}'s offscreen pose, not a
	 * reference to its renderer, target, projection buffer, or lighting state.
	 */
	public static CollectionResult collectStandard3d(GuiItemRenderState item, int guiScale) {
		if (item.itemStackRenderState().displayContext() != net.minecraft.world.item.ItemDisplayContext.GUI) {
			return CollectionResult.rejected("display-context");
		}
		if (!item.itemStackRenderState().usesBlockLight()) {
			return CollectionResult.rejected("flat-lighting");
		}
		if (item.itemStackRenderState().isAnimated()) {
			return CollectionResult.rejected("animated-item");
		}
		if (guiScale <= 0) {
			return CollectionResult.rejected("gui-scale");
		}
		Standard3dTarget target = standard3dTarget(item, guiScale);
		List<GuiItemMeshLayer> layers = new ArrayList<>();
		String[] rejection = new String[1];
		item.itemStackRenderState().forEachSemanticLayer(layer -> {
			if (rejection[0] != null) {
				return;
			}
			rejection[0] = appendLayer(layer, target.modelTransform(), layers);
		});
		if (rejection[0] != null || layers.isEmpty()) {
			return CollectionResult.rejected(rejection[0] == null ? "empty-mesh" : rejection[0]);
		}
		ScreenRectangle bounds = item.bounds();
		int left = bounds == null ? item.x() : bounds.left();
		int top = bounds == null ? item.y() : bounds.top();
		int right = bounds == null ? item.x() + 16 : bounds.right();
		int bottom = bounds == null ? item.y() + 16 : bounds.bottom();
		return CollectionResult.accepted(new GuiItemMesh(
			item.name(), item.x(), item.y(), left, top, right, bottom,
			new float[] {item.pose().m00(), item.pose().m01(), item.pose().m10(), item.pose().m11(), item.pose().m20(), item.pose().m21()},
			target.width(), target.height(), target.guardPixels(), target.modelTransform(),
			layers
		));
	}

	private static Standard3dTarget standard3dTarget(GuiItemRenderState item, int guiScale) {
		AABB bounds = item.itemStackRenderState().getModelBoundingBox();
		int logicalWidth = Math.max(16, Mth.ceil((float)bounds.getXsize() * 16.0F));
		int logicalHeight = Math.max(16, Mth.ceil((float)bounds.getYsize() * 16.0F));
		boolean expanded = logicalWidth > 16 || logicalHeight > 16;
		int guardPixels = 1;
		int width = Math.addExact(Math.multiplyExact(logicalWidth, guiScale), guardPixels * 2);
		int height = Math.addExact(Math.multiplyExact(logicalHeight, guiScale), guardPixels * 2);
		PoseStack pose = new PoseStack();
		// Matches PictureInPictureRenderer + Standard3dItemRenderer exactly,
		// while retaining only a copied matrix at this Java/Rust boundary.
		pose.translate(width / 2.0F, height / 2.0F, 0.0F);
		float scale = guiScale * 16.0F;
		pose.scale(scale, scale, -scale);
		pose.scale(1.0F, -1.0F, -1.0F);
		if (expanded) {
			pose.translate((float)(-(bounds.minX + bounds.maxX) / 2.0), (float)(-(bounds.minY + bounds.maxY) / 2.0), 0.0F);
		}
		float[] modelTransform = new float[16];
		pose.last().pose().get(modelTransform);
		return new Standard3dTarget(width, height, guardPixels, modelTransform);
	}

	private static String appendLayer(ItemStackRenderState.SemanticLayer layer, float[] standard3dTransform, List<GuiItemMeshLayer> output) {
		if (layer.hasSpecialRenderer()) return "special-renderer";
		if (layer.foilType() != ItemStackRenderState.FoilType.NONE) return "foil";
		if (layer.renderType() == null || layer.quads().isEmpty()) return "empty-or-missing-render-type";
		MaterialMode mode = materialMode(layer.renderType());
		if (mode == null) return "render-type";

		List<GuiItemMeshQuad> quads = new ArrayList<>(layer.quads().size());
		for (BakedQuad quad : layer.quads()) {
			GuiItemMeshQuad copied = copyQuad(quad, layer.tintLayers());
			if (copied == null) return "unsupported-quad";
			quads.add(copied);
		}
		Matrix4f combined = new Matrix4f().set(standard3dTransform).mul(new Matrix4f().set(layer.modelTransform()));
		float[] modelTransform = new float[16];
		combined.get(modelTransform);
		output.add(new GuiItemMeshLayer(mode, layer.usesBlockLight(), modelTransform, quads));
		return null;
	}

	private static MaterialMode materialMode(RenderType renderType) {
		String name = renderType.toString();
		if (name.contains("cutout")) return MaterialMode.CUTOUT;
		return name.contains("item") || name.contains("solid") ? MaterialMode.OPAQUE : null;
	}

	private static GuiItemMeshQuad copyQuad(BakedQuad bakedQuad, int[] tintLayers) {
		if (!(bakedQuad instanceof BakedQuadView quad)) return null;
		TextureAtlasSprite sprite = quad.getSprite();
		ResourceLocation spriteIdentity = sprite == null ? null : sprite.contents().name();
		if (spriteIdentity == null || sprite.contents().isAnimated()) return null;
		long assetId = RustGalGuiItemRenderer.stageSemanticImage(spriteIdentity);
		if (assetId == 0L) return null;
		float[] positions = new float[12];
		float[] atlasUvs = new float[8];
		float[] localUvs = new float[8];
		int[] colors = new int[4];
		int[] normals = new int[4];
		int tint = itemTint(bakedQuad, tintLayers);
		for (int index = 0; index < 4; index++) {
			float x = quad.getX(index);
			float y = quad.getY(index);
			float z = quad.getZ(index);
			float u = quad.getTexU(index);
			float v = quad.getTexV(index);
			if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(u) || !Float.isFinite(v)) {
				return null;
			}
			int position = index * 3;
			positions[position] = x;
			positions[position + 1] = y;
			positions[position + 2] = z;
			int uv = index * 2;
			atlasUvs[uv] = u;
			atlasUvs[uv + 1] = v;
			localUvs[uv] = localU(sprite, u);
			localUvs[uv + 1] = localV(sprite, v);
			colors[index] = shadedColor(quad.getColor(index), tint);
			normals[index] = quad.getAccurateNormal(index);
		}
		return new GuiItemMeshQuad(
			assetId, spriteIdentity.toString(), positions, atlasUvs, localUvs, colors, normals,
			quad.getLightFace().get3DDataValue(), quad.hasShade()
		);
	}

	private static float localU(TextureAtlasSprite sprite, float atlasU) {
		float width = sprite.getU1() - sprite.getU0();
		return width == 0.0F ? 0.0F : Mth.clamp((atlasU - sprite.getU0()) / width, 0.0F, 1.0F);
	}

	private static float localV(TextureAtlasSprite sprite, float atlasV) {
		float height = sprite.getV1() - sprite.getV0();
		return height == 0.0F ? 0.0F : Mth.clamp((atlasV - sprite.getV0()) / height, 0.0F, 1.0F);
	}

	private static int itemTint(BakedQuad quad, int[] tintLayers) {
		if (!quad.isTinted() || tintLayers.length == 0 || quad.tintIndex() < 0 || quad.tintIndex() >= tintLayers.length) return 0xffffffff;
		int tint = tintLayers[quad.tintIndex()];
		return tint == -1 ? 0xffffffff : tint;
	}

	private static int shadedColor(int bakedColor, int tint) {
		int alpha = Math.round(((bakedColor >>> 24 & 0xff) / 255.0F) * (ARGB.alpha(tint) / 255.0F) * 255.0F);
		int red = Math.round(((bakedColor & 0xff) / 255.0F) * (ARGB.red(tint) / 255.0F) * 255.0F);
		int green = Math.round(((bakedColor >>> 8 & 0xff) / 255.0F) * (ARGB.green(tint) / 255.0F) * 255.0F);
		int blue = Math.round(((bakedColor >>> 16 & 0xff) / 255.0F) * (ARGB.blue(tint) / 255.0F) * 255.0F);
		return ARGB.color(Mth.clamp(alpha, 0, 255), Mth.clamp(red, 0, 255), Mth.clamp(green, 0, 255), Mth.clamp(blue, 0, 255));
	}

	public enum MaterialMode {
		OPAQUE,
		CUTOUT
	}

	public record CollectionResult(GuiItemMesh mesh, String rejection) {
		private static CollectionResult accepted(GuiItemMesh mesh) {
			return new CollectionResult(mesh, null);
		}

		private static CollectionResult rejected(String rejection) {
			return new CollectionResult(null, rejection);
		}

		public boolean accepted() {
			return this.mesh != null;
		}
	}

	public record GuiItemMesh(
		String itemIdentity, int itemX, int itemY, int left, int top, int right, int bottom,
		float[] guiPose, int renderWidth, int renderHeight, int guardPixels, float[] offscreenModelTransform,
		List<GuiItemMeshLayer> layers
	) {
		public GuiItemMesh {
			guiPose = checkedCopy(guiPose, 6, "GUI item pose");
			if (renderWidth <= 0 || renderHeight <= 0 || guardPixels < 0 || guardPixels * 2 >= renderWidth || guardPixels * 2 >= renderHeight) {
				throw new IllegalArgumentException("GUI item mesh has an invalid standard-3D target extent");
			}
			offscreenModelTransform = checkedCopy(offscreenModelTransform, 16, "GUI item offscreen model transform");
			layers = List.copyOf(layers);
		}

		@Override
		public float[] guiPose() {
			return this.guiPose.clone();
		}

		@Override
		public float[] offscreenModelTransform() {
			return this.offscreenModelTransform.clone();
		}
	}

	private record Standard3dTarget(int width, int height, int guardPixels, float[] modelTransform) {
		private Standard3dTarget {
			modelTransform = checkedCopy(modelTransform, 16, "standard-3D item model transform");
		}

		@Override
		public float[] modelTransform() {
			return this.modelTransform.clone();
		}
	}

	public record GuiItemMeshLayer(MaterialMode materialMode, boolean blockLight, float[] modelTransform, List<GuiItemMeshQuad> quads) {
		public GuiItemMeshLayer {
			modelTransform = checkedCopy(modelTransform, 16, "GUI item model transform");
			quads = List.copyOf(quads);
		}

		@Override
		public float[] modelTransform() {
			return this.modelTransform.clone();
		}
	}

	public record GuiItemMeshQuad(
		long assetId, String materialIdentity, float[] positions, float[] atlasUvs, float[] localUvs, int[] colorsArgb, int[] packedNormals,
		int lightFace, boolean shade
	) {
		public GuiItemMeshQuad {
			if (assetId == 0L) {
				throw new IllegalArgumentException("GUI item mesh quad requires a non-zero semantic image asset id");
			}
			positions = checkedCopy(positions, 12, "GUI item positions");
			atlasUvs = checkedCopy(atlasUvs, 8, "GUI item atlas UVs");
			localUvs = checkedCopy(localUvs, 8, "GUI item local UVs");
			colorsArgb = colorsArgb.clone();
			packedNormals = packedNormals.clone();
			if (colorsArgb.length != 4 || packedNormals.length != 4) {
				throw new IllegalArgumentException("GUI item mesh quad requires four colors and normals");
			}
		}

		@Override
		public float[] positions() {
			return this.positions.clone();
		}

		@Override
		public float[] atlasUvs() {
			return this.atlasUvs.clone();
		}

		@Override
		public float[] localUvs() {
			return this.localUvs.clone();
		}

		@Override
		public int[] colorsArgb() {
			return this.colorsArgb.clone();
		}

		@Override
		public int[] packedNormals() {
			return this.packedNormals.clone();
		}
	}

	private static float[] checkedCopy(float[] values, int expectedLength, String name) {
		if (values.length != expectedLength) {
			throw new IllegalArgumentException(name + " must contain " + expectedLength + " floats");
		}
		for (float value : values) {
			if (!Float.isFinite(value)) {
				throw new IllegalArgumentException(name + " must be finite");
			}
		}
		return values.clone();
	}
}
