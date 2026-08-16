package net.vulkanic.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.vulkanic.bridge.VulkanicGalBridge;
import net.sodium.client.model.quad.BakedQuadView;
import org.joml.Matrix3x2f;

/**
 * Java-side semantic extraction for the bounded flat GUI-item subset. It
 * copies sprite pixels and screen-space quads immediately; no item renderer,
 * atlas object, or backend state crosses into Rust.
 */
public final class RustGalGuiItemRenderer {
	private static final String PRODUCER = "minecraft.gui.item.flat";
	private static final int RAW_RGBA8 = 2;
	private static final boolean STANDARD_3D_ROUTE_DISABLED = Boolean.getBoolean("mattmc.rustGal.gui.standard3d.disabled");
	private static final boolean DEBUG_STANDARD_3D_ITEM_ENABLED = Boolean.getBoolean("mattmc.rustGal.gui.standard3d.debugItem");
	private static final Map<Long, String> ASSET_IDENTITIES = new HashMap<>();
	private static final Map<ResourceLocation, ImageAsset> IMAGE_CACHE = new HashMap<>();
	private static final Map<String, Boolean> DIAGNOSTICS = new HashMap<>();

	private RustGalGuiItemRenderer() {
	}

	public static boolean standard3dRouteEnabled() {
		// This is a normal producer route for Rust's exclusive Vulkan frame. An
		// unsupported item remains Java-owned before selection; selected items
		// never enter Java's PIP renderer in the same frame.
		return !STANDARD_3D_ROUTE_DISABLED
			&& RustGalGuiRenderer.currentExecutionRoute() == RustGalGuiRenderer.GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME;
	}

	/**
	 * Adds one ordinary vanilla GUI item state before whole-frame semantic
	 * collection. This is capture-only infrastructure: it exercises the same
	 * ItemModelResolver and GUI item records as production items, but never
	 * invokes Java's PIP renderer or any Java draw path.
	 */
	public static void enqueueDebugStandard3dItem(GuiRenderState guiRenderState) {
		if (!DEBUG_STANDARD_3D_ITEM_ENABLED || !standard3dRouteEnabled()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.getModelManager().hasLoadedModels() || minecraft.level == null || minecraft.player == null) {
			return;
		}
		TrackingItemStackRenderState itemState = new TrackingItemStackRenderState();
		minecraft.getItemModelResolver().updateForTopItem(
			itemState, new ItemStack(Blocks.GRASS_BLOCK), ItemDisplayContext.GUI, minecraft.level, minecraft.player, 0
		);
		guiRenderState.submitItem(new GuiItemRenderState(
			"rust_gal_debug_standard_3d_grass_block", new Matrix3x2f(), itemState, 16, 16, null
		));
	}

	/**
	 * Extracts a flat, untransformed GUI item into the existing coarse affine
	 * GUI request family. 3D-lit, transformed, foil, special, animated, and
	 * otherwise non-planar items are explicitly left absent until a Rust-owned
	 * GUI mesh pass exists.
	 */
	public static List<RustGalGuiElementRenderState> tryEnqueueFlatItem(
		GuiItemRenderState item,
		int guiWidth,
		int guiHeight
	) {
		if (RustGalGuiRenderer.currentExecutionRoute() != RustGalGuiRenderer.GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME) {
			return List.of();
		}
		if (item.itemStackRenderState().displayContext() != ItemDisplayContext.GUI) {
			recordDiagnostic("display-context");
			return List.of();
		}
		if (item.itemStackRenderState().isAnimated()) {
			recordDiagnostic("animated-item");
			return List.of();
		}

		List<FlatQuad> quads = new ArrayList<>();
		String[] rejected = new String[1];
		item.itemStackRenderState().forEachSemanticLayer(layer -> {
			if (rejected[0] != null) {
				return;
			}
			rejected[0] = appendSupportedLayer(item, layer, quads);
		});
		if (rejected[0] != null || quads.isEmpty()) {
			recordDiagnostic(rejected[0] == null ? "empty-flat-geometry" : rejected[0]);
			return List.of();
		}

		List<RustGalGuiElementRenderState> elements = new ArrayList<>(quads.size());
		long startedNanos = System.nanoTime();
		for (FlatQuad quad : quads) {
			stageImage(quad.asset());
			VulkanicGalBridge.GuiAffineQuadRecord request = new VulkanicGalBridge.GuiAffineQuadRecord(
				GuiRenderStratum.GUI_ITEM.order(),
				quad.asset().assetId(),
				quad.x0(), quad.y0(), quad.x1(), quad.y1(), quad.x3(), quad.y3(),
				0.0F,
				quad.u0(), quad.v0(), quad.u1(), quad.v1(),
				quad.colorArgb(), guiWidth, guiHeight
			);
			if (quad.clipWidth() != 0 || quad.clipHeight() != 0) {
				request = request.withClip(quad.clipLeft(), quad.clipTop(), quad.clipWidth(), quad.clipHeight());
			}
			var token = RustGalFrameCoordinator.enqueueGuiAffineQuadRequest(request, GuiRenderStratum.GUI_ITEM, startedNanos);
			int left = Math.max(0, (int)Math.floor(Math.min(request.x0(), Math.min(request.x1(), request.x3()))));
			int top = Math.max(0, (int)Math.floor(Math.min(request.y0(), Math.min(request.y1(), request.y3()))));
			int right = Math.min(guiWidth, (int)Math.ceil(Math.max(request.x0(), Math.max(request.x1(), request.x3()))));
			int bottom = Math.min(guiHeight, (int)Math.ceil(Math.max(request.y0(), Math.max(request.y1(), request.y3()))));
			elements.add(new RustGalGuiElementRenderState(
				token, GuiRenderStratum.GUI_ITEM, PRODUCER, -1, -1.0F, GuiFillDirection.NONE,
				left, top, Math.max(1, right - left), Math.max(1, bottom - top), guiWidth, guiHeight
			));
		}
		recordDiagnostic("accepted-quads=" + quads.size());
		return List.copyOf(elements);
	}

	/**
	 * Converts one ordinary block-lit GUI item into a single ordered semantic
	 * mesh item. Its material layers remain nested under one scheduler token so
	 * Rust owns the offscreen raster and final composition ordering.
	 */
	public static List<RustGalGuiElementRenderState> tryEnqueueStandard3dItem(
		GuiItemRenderState item,
		int guiWidth,
		int guiHeight
	) {
		if (RustGalGuiRenderer.currentExecutionRoute() != RustGalGuiRenderer.GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME) {
			return List.of();
		}
		GuiItemMeshSemanticCollector.CollectionResult collected = GuiItemMeshSemanticCollector.collectStandard3d(
			item, Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()));
		if (!collected.accepted()) {
			recordDiagnostic("mesh-" + collected.rejection());
			return List.of();
		}
		GuiItemMeshSemanticCollector.GuiItemMesh mesh = collected.mesh();
		if (mesh.right() > guiWidth || mesh.bottom() > guiHeight || mesh.left() < 0 || mesh.top() < 0) {
			recordDiagnostic("mesh-bounds");
			return List.of();
		}
		List<VulkanicGalBridge.GuiMeshBatchRecord> batches = new ArrayList<>();
		int batchLayerIndex = 0;
		for (int layerIndex = 0; layerIndex < mesh.layers().size(); layerIndex++) {
			GuiItemMeshSemanticCollector.GuiItemMeshLayer layer = mesh.layers().get(layerIndex);
			for (GuiItemMeshSemanticCollector.GuiItemMeshQuad quad : layer.quads()) {
				List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = new ArrayList<>(4);
				for (int vertex = 0; vertex < 4; vertex++) {
					int position = vertex * 3;
					int uv = vertex * 2;
					vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(
						new float[] {quad.positions()[position], quad.positions()[position + 1], quad.positions()[position + 2]},
						new float[] {quad.atlasUvs()[uv], quad.atlasUvs()[uv + 1]},
						new float[] {quad.localUvs()[uv], quad.localUvs()[uv + 1]},
						quad.colorsArgb()[vertex], quad.packedNormals()[vertex]
					));
				}
				batches.add(new VulkanicGalBridge.GuiMeshBatchRecord(
					GuiRenderStratum.GUI_ITEM.order(), batchLayerIndex++,
					layer.materialMode() == GuiItemMeshSemanticCollector.MaterialMode.CUTOUT ? 2 : 1,
					layer.blockLight() ? 2 : 1, quad.assetId(), 0L,
					layer.materialMode() == GuiItemMeshSemanticCollector.MaterialMode.CUTOUT ? 0.1F : 0.0F,
					layer.modelTransform(), mesh.guiPose(), mesh.left(), mesh.top(), mesh.right(), mesh.bottom(),
					guiWidth, guiHeight, mesh.renderWidth(), mesh.renderHeight(), mesh.guardPixels(),
					vertices, List.of(0, 1, 2, 2, 3, 0)
				));
			}
		}
		if (batches.isEmpty()) return List.of();
		long startedNanos = System.nanoTime();
		var token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(batches, GuiRenderStratum.GUI_ITEM, startedNanos);
		recordDiagnostic("mesh-accepted-layers=" + batches.size());
		return List.of(new RustGalGuiElementRenderState(
			token, GuiRenderStratum.GUI_ITEM, "minecraft.gui.item.standard3d", -1, -1.0F, GuiFillDirection.NONE,
			mesh.left(), mesh.top(), mesh.right() - mesh.left(), mesh.bottom() - mesh.top(), guiWidth, guiHeight
		));
	}

	public static void invalidateAssets() {
		synchronized (IMAGE_CACHE) {
			IMAGE_CACHE.clear();
		}
	}

	private static String appendSupportedLayer(
		GuiItemRenderState item,
		ItemStackRenderState.SemanticLayer layer,
		List<FlatQuad> output
	) {
		if (layer.hasSpecialRenderer()) return "special-renderer";
		if (layer.usesBlockLight()) return "block-light";
		if (!layer.identityTransform()) return "non-identity-transform";
		if (layer.foilType() != ItemStackRenderState.FoilType.NONE) return "foil";
		if (layer.renderType() == null || layer.quads().isEmpty()) return "empty-or-missing-render-type";
		if (!supportedGuiRenderType(layer.renderType())) return "render-type";

		BakedQuad selected = null;
		for (BakedQuad candidate : layer.quads()) {
			if (candidate.direction() == net.minecraft.core.Direction.SOUTH) {
				if (selected != null) return "multiple-front-quads";
				selected = candidate;
			}
		}
		if (selected == null) return "missing-front-quad";
		FlatQuad quad = copyFlatQuad(item, selected, layer.tintLayers());
		if (quad == null) return "non-planar-or-nonuniform-quad";
		output.add(quad);
		return null;
	}

	private static boolean supportedGuiRenderType(RenderType renderType) {
		String name = renderType.toString();
		return name.contains("item") || name.contains("cutout") || name.contains("solid");
	}

	private static FlatQuad copyFlatQuad(GuiItemRenderState item, BakedQuad bakedQuad, int[] tintLayers) {
		if (!(bakedQuad instanceof BakedQuadView quad)) return null;
		TextureAtlasSprite sprite = quad.getSprite();
		if (sprite == null || sprite.contents().name() == null || sprite.contents().isAnimated()) return null;
		ImageAsset asset = imageAsset(sprite.contents().name());
		if (asset == null) return null;
		int tint = itemTint(bakedQuad, tintLayers);
		int color = shadedColor(quad.getColor(0), tint);
		for (int index = 1; index < 4; index++) {
			if (shadedColor(quad.getColor(index), tint) != color) return null;
		}

		int origin = findUvVertex(quad, sprite, true, true);
		int axisU = findUvVertex(quad, sprite, false, true);
		int axisV = findUvVertex(quad, sprite, true, false);
		if (origin < 0 || axisU < 0 || axisV < 0) return null;
		float u0 = localU(sprite, quad.getTexU(origin));
		float v0 = localV(sprite, quad.getTexV(origin));
		float u1 = localU(sprite, quad.getTexU(axisU));
		float v1 = localV(sprite, quad.getTexV(axisV));
		if (u1 <= u0 || v1 <= v0) return null;

		float[] p0 = guiPoint(item, quad.getX(origin), quad.getY(origin));
		float[] p1 = guiPoint(item, quad.getX(axisU), quad.getY(axisU));
		float[] p3 = guiPoint(item, quad.getX(axisV), quad.getY(axisV));
		if (!finite(p0) || !finite(p1) || !finite(p3) || Math.abs(area(p0, p1, p3)) < 0.01F) return null;
		ScreenRectangle scissor = item.scissorArea();
		return new FlatQuad(
			asset, p0[0], p0[1], p1[0], p1[1], p3[0], p3[1], u0, v0, u1, v1, color,
			scissor == null ? 0 : scissor.left(),
			scissor == null ? 0 : scissor.top(),
			scissor == null ? 0 : scissor.width(),
			scissor == null ? 0 : scissor.height()
		);
	}

	private static int findUvVertex(BakedQuadView quad, TextureAtlasSprite sprite, boolean minU, boolean minV) {
		int best = -1;
		float bestDistance = Float.POSITIVE_INFINITY;
		float targetU = minU ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
		float targetV = minV ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
		for (int index = 0; index < 4; index++) {
			float u = localU(sprite, quad.getTexU(index));
			float v = localV(sprite, quad.getTexV(index));
			targetU = minU ? Math.min(targetU, u) : Math.max(targetU, u);
			targetV = minV ? Math.min(targetV, v) : Math.max(targetV, v);
		}
		for (int index = 0; index < 4; index++) {
			float du = localU(sprite, quad.getTexU(index)) - targetU;
			float dv = localV(sprite, quad.getTexV(index)) - targetV;
			float distance = du * du + dv * dv;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = index;
			}
		}
		return bestDistance <= 0.0001F ? best : -1;
	}

	private static float[] guiPoint(GuiItemRenderState item, float modelX, float modelY) {
		float x = item.x() + modelX * 16.0F;
		float y = item.y() + (1.0F - modelY) * 16.0F;
		Matrix3x2f pose = item.pose();
		return new float[] {pose.m00() * x + pose.m10() * y + pose.m20(), pose.m01() * x + pose.m11() * y + pose.m21()};
	}

	private static ImageAsset imageAsset(ResourceLocation sprite) {
		synchronized (IMAGE_CACHE) {
			ImageAsset cached = IMAGE_CACHE.get(sprite);
			if (cached != null) return cached;
		}
		ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isEmpty()) return null;
		try (InputStream input = resource.get().open()) {
			byte[] png = input.readAllBytes();
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
			if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return null;
			byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(image.getWidth(), image.getHeight()), 4)];
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int argb = image.getRGB(x, y);
					int offset = (y * image.getWidth() + x) * 4;
					pixels[offset] = (byte)ARGB.red(argb);
					pixels[offset + 1] = (byte)ARGB.green(argb);
					pixels[offset + 2] = (byte)ARGB.blue(argb);
					pixels[offset + 3] = (byte)ARGB.alpha(argb);
				}
			}
			long assetId = semanticAssetId(texture.toString());
			ImageAsset result = new ImageAsset(assetId, texture.toString(), image.getWidth(), image.getHeight(), pixels);
			synchronized (IMAGE_CACHE) {
				IMAGE_CACHE.put(sprite, result);
			}
			return result;
		} catch (IOException | ArithmeticException error) {
			recordDiagnostic("image-read-failure=" + texture);
			return null;
		}
	}

	private static void stageImage(ImageAsset asset) {
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			asset.assetId(), RAW_RGBA8, asset.width(), asset.height(), asset.pixels()
		));
	}

	/**
	 * Resolves and stages a copied semantic image for a future Rust-owned mesh
	 * layer. The returned key is stable across a resource generation; neither a
	 * sprite nor an atlas object escapes this Java boundary.
	 */
	static long stageSemanticImage(ResourceLocation sprite) {
		ImageAsset asset = imageAsset(sprite);
		if (asset == null) {
			return 0L;
		}
		stageImage(asset);
		return asset.assetId();
	}

	private static long semanticAssetId(String identity) {
		long hash = 0xcbf29ce484222325L;
		for (int index = 0; index < identity.length(); index++) {
			hash ^= identity.charAt(index);
			hash *= 0x100000001b3L;
		}
		if (hash == 0L) hash = 1L;
		String previous = ASSET_IDENTITIES.putIfAbsent(hash, identity);
		if (previous != null && !previous.equals(identity)) {
			throw new IllegalStateException("semantic GUI item image identity collision");
		}
		return hash;
	}

	private static int itemTint(BakedQuad quad, int[] tintLayers) {
		if (!quad.isTinted() || tintLayers.length == 0 || quad.tintIndex() < 0 || quad.tintIndex() >= tintLayers.length) return 0xffffffff;
		int tint = tintLayers[quad.tintIndex()];
		return tint == -1 ? 0xffffffff : tint;
	}

	private static int shadedColor(int bakedColor, int tint) {
		int alpha = Math.round(((bakedColor >>> 24 & 0xff) / 255.0F) * ((ARGB.alpha(tint)) / 255.0F) * 255.0F);
		int red = Math.round(((bakedColor & 0xff) / 255.0F) * ((ARGB.red(tint)) / 255.0F) * 255.0F);
		int green = Math.round(((bakedColor >>> 8 & 0xff) / 255.0F) * ((ARGB.green(tint)) / 255.0F) * 255.0F);
		int blue = Math.round(((bakedColor >>> 16 & 0xff) / 255.0F) * ((ARGB.blue(tint)) / 255.0F) * 255.0F);
		return ARGB.color(Mth.clamp(alpha, 0, 255), Mth.clamp(red, 0, 255), Mth.clamp(green, 0, 255), Mth.clamp(blue, 0, 255));
	}

	private static float localU(TextureAtlasSprite sprite, float atlasU) {
		float width = sprite.getU1() - sprite.getU0();
		return width == 0.0F ? 0.0F : Mth.clamp((atlasU - sprite.getU0()) / width, 0.0F, 1.0F);
	}

	private static float localV(TextureAtlasSprite sprite, float atlasV) {
		float height = sprite.getV1() - sprite.getV0();
		return height == 0.0F ? 0.0F : Mth.clamp((atlasV - sprite.getV0()) / height, 0.0F, 1.0F);
	}

	private static boolean finite(float[] point) {
		return Float.isFinite(point[0]) && Float.isFinite(point[1]);
	}

	private static float area(float[] p0, float[] p1, float[] p3) {
		return (p1[0] - p0[0]) * (p3[1] - p0[1]) - (p1[1] - p0[1]) * (p3[0] - p0[0]);
	}

	private static synchronized void recordDiagnostic(String detail) {
		if (DIAGNOSTICS.putIfAbsent(detail, Boolean.TRUE) == null) {
			RustGalFrameCoordinator.auditMessage("gui.item.route " + detail);
		}
	}

	private record ImageAsset(long assetId, String identity, int width, int height, byte[] pixels) {
		private ImageAsset {
			pixels = pixels.clone();
		}

		@Override
		public byte[] pixels() {
			return this.pixels.clone();
		}
	}

	private record FlatQuad(
		ImageAsset asset,
		float x0, float y0, float x1, float y1, float x3, float y3,
		float u0, float v0, float u1, float v1,
		int colorArgb,
		int clipLeft, int clipTop, int clipWidth, int clipHeight
	) {
	}
}
