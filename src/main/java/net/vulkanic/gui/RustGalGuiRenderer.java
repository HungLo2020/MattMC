package net.vulkanic.gui;

import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;
import net.vulkanic.bridge.RustGalFrameScheduler;
import net.vulkanic.bridge.VulkanicGalBridge;

import net.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.TextGlyphQuad;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.ColoredRectangleRenderState;
import net.minecraft.client.gui.render.state.GlyphRenderState;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.client.gui.render.state.TiledBlitRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.render.state.pip.GuiProfilerChartRenderState;
import net.minecraft.client.gui.render.state.pip.GuiBannerResultRenderState;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Unit;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.pipeline.BlendFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.BossEvent;
import net.vulkanic.VulkanicAPI;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

public final class RustGalGuiRenderer {
	/** Small normalized overlap used by vanilla's loading-logo seam quads. */
	private static final float GUI_UV_OVERLAP_LIMIT = 1.0F / 16.0F;
	/** Must match Rust's FFI_MAX_GUI_ASSET_BYTES bound. */
	private static final int MAX_GUI_ASSET_BYTES = 64 * 1024 * 1024;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String CROSSHAIR_PRODUCER = "minecraft.gui.crosshair";
	private static final String HOTBAR_BASE_PRODUCER = "minecraft.gui.hotbar.base";
	private static final String HOTBAR_SELECTION_PRODUCER = "minecraft.gui.hotbar.selection";
	private static final String EXPERIENCE_BACKGROUND_PRODUCER = "minecraft.gui.experience.background";
	private static final String EXPERIENCE_PROGRESS_PRODUCER = "minecraft.gui.experience.progress";
	private static final String ATTACK_CROSSHAIR_BACKGROUND_PRODUCER = "minecraft.gui.attack.crosshair.background";
	private static final String ATTACK_CROSSHAIR_PROGRESS_PRODUCER = "minecraft.gui.attack.crosshair.progress";
	private static final String ATTACK_HOTBAR_BACKGROUND_PRODUCER = "minecraft.gui.attack.hotbar.background";
	private static final String ATTACK_HOTBAR_PROGRESS_PRODUCER = "minecraft.gui.attack.hotbar.progress";
	private static final String BOSS_BAR_BACKGROUND_PRODUCER = "minecraft.gui.boss.background";
	private static final String BOSS_BAR_PROGRESS_PRODUCER = "minecraft.gui.boss.progress";
	private static final String ARMOR_ICON_PRODUCER = "minecraft.gui.armor";
	private static final String PLAYER_HEART_PRODUCER = "minecraft.gui.player-heart";
	private static final String ABSORPTION_HEART_PRODUCER = "minecraft.gui.absorption-heart";
	private static final String HUNGER_ICON_PRODUCER = "minecraft.gui.hunger";
	private static final String AIR_BUBBLE_PRODUCER = "minecraft.gui.air";
	private static final String MOUNT_HEART_PRODUCER = "minecraft.gui.mount-heart";
	private static final boolean ASSET_UPDATES_DISABLED =
		Boolean.getBoolean("mattmc.dev.rustGalGui.assetUpdates.disabled");
	private static final boolean TEXT_ROUTE_ENABLED =
		Boolean.parseBoolean(System.getProperty("mattmc.rustGal.guiText.enabled", "true"));
	private static final boolean TEXT_ROUTE_DIAGNOSTICS_ENABLED =
		Boolean.getBoolean("mattmc.dev.rustGalGui.textDiagnostics");
	private static final int MAX_GUI_DIAGNOSTIC_ENTRIES = 256;
	/** Hard bound for per-frame admission misses reported by arbitrary GUI producers. */
	private static final int MAX_GUI_UNSUPPORTED_ELEMENTS = 4_096;
	private static final int MAX_RUST_GUI_TEXT_QUADS = 65_536;
	private static final int MAX_TEXT_ATLAS_IDENTITIES = 4_096;
	private static final int MAX_TEXT_ATLAS_GENERATIONS = 4_096;

	private static void appendBoundedTextQuad(List<TextGlyphQuad> quads, TextGlyphQuad quad) {
		if (quads.size() >= MAX_RUST_GUI_TEXT_QUADS) {
			throw new TextQuadLimitExceeded();
		}
		quads.add(quad);
	}

	private static final class TextQuadLimitExceeded extends RuntimeException {
		private TextQuadLimitExceeded() {
		}
	}
	private static final Map<Long, String> TEXT_ATLAS_IDENTITIES = new HashMap<>();
	private static final Map<String, TextAtlasGeneration> TEXT_ATLAS_GENERATIONS = new HashMap<>();
	private static final Map<String, Boolean> TEXT_ROUTE_DIAGNOSTICS = new HashMap<>();
	private static final Map<String, Integer> WHOLE_FRAME_UNSUPPORTED_ELEMENTS = new HashMap<>();
	private static int wholeFrameUnsupportedElementCount;
	private static final String TEXT_PRODUCER = "minecraft.gui.text";
	private static final int WHOLE_FRAME_DYNAMIC_LAYER_BASE = 10_000;
	/** Stable Rust-owned raw-image identity for untextured GUI rectangles. */
	private static final long SOLID_WHITE_ASSET_ID = 0x5247_4354_5748_4954L;
	private static final byte[] SOLID_WHITE_RGBA = new byte[] {(byte)255, (byte)255, (byte)255, (byte)255};
	private static final String RECTANGLE_PRODUCER = "minecraft.gui.rectangle";
	/** Bounds the number of affine requests a single tiled GUI producer may emit. */
	private static final int MAX_GUI_TILED_SEGMENTS = 16_384;
	private static final String PROFILER_CHART_PRODUCER = "minecraft.gui.profiler-chart";
	private static final String LOADING_GRID_PRODUCER = "minecraft.gui.loading-grid";
	private static final long LOADING_GRID_ASSET_ID = 0x52475F4C4F414447L;
	/** Hard cap for the optional packed loading-grid image (16 MiB RGBA). */
	private static final int MAX_LOADING_GRID_TEXTURE_EDGE = 2_048;
	private static int loadingGridAssetHash;
	private static int loadingGridAssetWidth;
	private static int loadingGridAssetHeight;
	private static boolean loadingGridAssetResident;
	private static int loadingGridFramesSinceUpload;

	private RustGalGuiRenderer() {
	}

	/**
	 * Drops the local loading-grid residency hint when the Rust GUI frontend is
	 * rebuilt.  The next loading-screen frame must stage the image again; keeping
	 * this hint across a frontend/resource-generation reset would submit a mesh
	 * that references an already-retired Rust asset.
	 */
	public static void invalidateLoadingGridAsset() {
		loadingGridAssetResident = false;
		loadingGridAssetHash = 0;
		loadingGridAssetWidth = 0;
		loadingGridAssetHeight = 0;
		loadingGridFramesSinceUpload = 0;
	}

	/**
	 * Enqueues the vanilla loading status grid as one explicit semantic mesh.
	 * Each cell remains an independent quad (preserving exact geometry and
	 * colors), while avoiding one scheduler/FFI item per cell.
	 */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueLoadingGrid(
		int[] colors, int gridSize, int originX, int originY, int cellSize,
		int guiWidth, int guiHeight
	) {
		return tryEnqueueLoadingGrid(colors, gridSize, originX, originY, cellSize, cellSize, guiWidth, guiHeight);
	}

	/** Aggregates a large plain-rectangle semantic layer without admitting Java GUI work. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueRectangleGroup(
		List<net.minecraft.client.gui.render.state.ColoredRectangleRenderState> rectangles,
		int guiWidth, int guiHeight, int sourceLayerOrder
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME || rectangles == null
			|| rectangles.size() < 1024 || guiWidth <= 2 || guiHeight <= 2 || sourceLayerOrder < 0) return null;
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = new ArrayList<>(rectangles.size() * 4);
		List<Integer> indices = new ArrayList<>(rectangles.size() * 6);
		int left = guiWidth, top = guiHeight, right = 0, bottom = 0;
		for (var rectangle : rectangles) {
			if (rectangle.pipeline() != RenderPipelines.GUI) { recordUnsupportedElementDetail("rectangle-group:pipeline"); return null; }
			if (rectangle.textureSetup() != net.minecraft.client.gui.render.TextureSetup.noTexture()) { recordUnsupportedElementDetail("rectangle-group:texture"); return null; }
			if (rectangle.scissorArea() != null) { recordUnsupportedElementDetail("rectangle-group:scissor"); return null; }
			if (!identityGuiPose(rectangle.pose())) { recordUnsupportedElementDetail("rectangle-group:pose"); return null; }
			if (rectangle.x1() <= rectangle.x0() || rectangle.y1() <= rectangle.y0()) { recordUnsupportedElementDetail("rectangle-group:geometry"); return null; }
			int x0 = rectangle.x0(), y0 = rectangle.y0(), x1 = rectangle.x1(), y1 = rectangle.y1();
			int base = vertices.size();
			vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x0, y0, 0}, new float[] {0, 0}, new float[] {0, 0}, rectangle.col1(), 0x007F0000));
			vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x1, y0, 0}, new float[] {1, 0}, new float[] {1, 0}, rectangle.col1(), 0x007F0000));
			vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x1, y1, 0}, new float[] {1, 1}, new float[] {1, 1}, rectangle.col2(), 0x007F0000));
			vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x0, y1, 0}, new float[] {0, 1}, new float[] {0, 1}, rectangle.col2(), 0x007F0000));
			indices.add(base); indices.add(base + 1); indices.add(base + 2); indices.add(base + 2); indices.add(base + 3); indices.add(base);
			left = Math.min(left, x0); top = Math.min(top, y0); right = Math.max(right, x1); bottom = Math.max(bottom, y1);
		}
		left = Math.max(0, left); top = Math.max(0, top); right = Math.min(guiWidth, right); bottom = Math.min(guiHeight, bottom);
		if (left >= right || top >= bottom) return null;
		VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
			dynamicLayerOrder(sourceLayerOrder), 0, 1, 1, SOLID_WHITE_ASSET_ID, 0L, 0.0F,
			new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}, new float[] {1, 0, 0, 1, 0, 0},
			left, top, right, bottom, guiWidth, guiHeight, right - left + 2, bottom - top + 2, 1, vertices, indices);
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(List.of(batch), dynamicLayerId(sourceLayerOrder), dynamicLayerOrder(sourceLayerOrder), System.nanoTime());
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA));
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_RECTANGLES, "minecraft.gui.rectangle-group", -1, -1.0F, GuiFillDirection.NONE, left, top, right - left, bottom - top, guiWidth, guiHeight));
	}

	private static boolean identityGuiPose(Matrix3x2f pose) {
		return pose != null && pose.m00() == 1.0F && pose.m11() == 1.0F && pose.m01() == 0.0F
			&& pose.m10() == 0.0F && pose.m20() == 0.0F && pose.m21() == 0.0F;
	}

	/** Variant retaining vanilla's independent horizontal/vertical cell stride. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueLoadingGrid(
		int[] colors, int gridSize, int originX, int originY, int cellSize, int stride,
		int guiWidth, int guiHeight
	) {
		if (!currentExecutionRoute().usesRustGui()
			&& !Boolean.getBoolean("mattmc.dev.rustGalVulkanWholeFrame")
			|| colors == null || gridSize <= 0 || gridSize > 512 || colors.length != gridSize * gridSize
			|| cellSize <= 0 || stride < cellSize || guiWidth <= 2 || guiHeight <= 2) return null;
		long extent = (long) (gridSize - 1) * stride + cellSize;
		if (extent <= 0 || extent > Integer.MAX_VALUE) return null;
		int right = Math.min(guiWidth, Math.max(originX, (int) Math.min(Integer.MAX_VALUE, originX + extent)));
		int bottom = Math.min(guiHeight, Math.max(originY, (int) Math.min(Integer.MAX_VALUE, originY + extent)));
		int left = Math.max(0, originX);
		int top = Math.max(0, originY);
		if (left >= right || top >= bottom) return null;
		if (currentExecutionRoute() == GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT) {
			// This path runs in Minecraft's legacy GL 3.3 context.  Lower every
			// semantic chunk-status cell into the explicit affine primitive already
			// used by the Rust-owned rectangle and progress-bar routes.  In
			// particular, do not route the grid through the mesh frontend (which
			// requires storage-buffer bindings) or an opaque copied-image shortcut:
			// each source status and its exact color remain explicit backend-neutral
			// data.  A loading view is bounded well below the frame-wide affine limit.
		int cellCount = Math.multiplyExact(gridSize, gridSize);
		if (cellCount > 65_536) return null;
		List<VulkanicGalBridge.GuiAffineQuadRecord> requests = new ArrayList<>(cellCount);
		for (int column = 0; column < gridSize; column++) for (int row = 0; row < gridSize; row++) {
			float x = originX + (float)column * stride;
			float y = originY + (float)row * stride;
			requests.add(new VulkanicGalBridge.GuiAffineQuadRecord(
				GuiRenderStratum.GUI_RECTANGLES.order(), SOLID_WHITE_ASSET_ID,
				x, y, x + cellSize, y, x, y + cellSize,
				0.0F, 0.0F, 0.0F, 1.0F, 1.0F, ARGB.opaque(colors[row * gridSize + column]),
				guiWidth, guiHeight));
		}
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiAffineQuadRequests(
			requests, GuiRenderStratum.GUI_RECTANGLES.id(), GuiRenderStratum.GUI_RECTANGLES.order(), System.nanoTime());
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA));
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_RECTANGLES,
			LOADING_GRID_PRODUCER, -1, -1.0F, GuiFillDirection.NONE,
			left, top, right - left, bottom - top, guiWidth, guiHeight));
	}
		if (Boolean.parseBoolean(System.getProperty("mattmc.dev.rustGalGui.loadingGridTexture", "true"))) {
			// The packed-image variant is an optimization, never an unbounded
			// allocation request. Reject pathological producer strides before the
			// byte[] multiplication below. The borrowed OpenGL affine path above
			// remains available for the same semantic grid.
			if (extent > MAX_LOADING_GRID_TEXTURE_EDGE) return null;
			int imageWidth = Math.max(1, (int) extent), imageHeight = imageWidth;
			byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(imageWidth, imageHeight), 4)];
			for (int row = 0; row < gridSize; row++) for (int col = 0; col < gridSize; col++) {
				int color = ARGB.opaque(colors[row * gridSize + col]);
				int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
				int x0 = col * stride, y0 = row * stride;
				for (int y = y0; y < y0 + cellSize; y++) for (int x = x0; x < x0 + cellSize; x++) {
					int offset = (y * imageWidth + x) * 4;
					pixels[offset] = (byte) red; pixels[offset + 1] = (byte) green; pixels[offset + 2] = (byte) blue; pixels[offset + 3] = (byte) 255;
				}
			}
			VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
				GuiRenderStratum.GUI_RECTANGLES.order(), 0, 1, 1, LOADING_GRID_ASSET_ID, 0L, 0.0F,
				new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}, new float[] {1, 0, 0, 1, 0, 0},
				left, top, right, bottom, guiWidth, guiHeight, right - left + 2, bottom - top + 2, 1,
				List.of(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {originX, originY, 0}, new float[] {0, 0}, new float[] {0, 0}, -1, 0x007F0000),
					new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {originX + extent, originY, 0}, new float[] {1, 0}, new float[] {1, 0}, -1, 0x007F0000),
					new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {originX + extent, originY + extent, 0}, new float[] {1, 1}, new float[] {1, 1}, -1, 0x007F0000),
					new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {originX, originY + extent, 0}, new float[] {0, 1}, new float[] {0, 1}, -1, 0x007F0000)), List.of(0, 1, 2, 2, 3, 0));
			RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(List.of(batch), GuiRenderStratum.GUI_RECTANGLES, System.nanoTime());
			int assetHash = 31 * (31 * imageWidth + imageHeight) + Arrays.hashCode(pixels);
			loadingGridFramesSinceUpload++;
			// A changed status color is visible gameplay state, not a cache hint.
			// Publish the new packed image on the first frame that observes its hash;
			// the resident/hash check still prevents repeated uploads while the grid
			// is unchanged.
			if (!loadingGridAssetResident || loadingGridAssetHash != assetHash || loadingGridAssetWidth != imageWidth || loadingGridAssetHeight != imageHeight) {
				RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(LOADING_GRID_ASSET_ID, 2, imageWidth, imageHeight, pixels));
				loadingGridAssetHash = assetHash;
				loadingGridAssetWidth = imageWidth;
				loadingGridAssetHeight = imageHeight;
				loadingGridAssetResident = true;
				loadingGridFramesSinceUpload = 0;
			}
			return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_RECTANGLES, LOADING_GRID_PRODUCER, -1, -1.0F, GuiFillDirection.NONE, left, top, right - left, bottom - top, guiWidth, guiHeight));
		}
		int vertexCount = Math.multiplyExact(Math.multiplyExact(gridSize, gridSize), 4);
		int indexCount = Math.multiplyExact(Math.multiplyExact(gridSize, gridSize), 6);
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = new ArrayList<>(vertexCount);
		List<Integer> indices = new ArrayList<>(indexCount);
		for (int row = 0; row < gridSize; row++) {
			for (int col = 0; col < gridSize; col++) {
				int x0 = originX + col * stride;
				int y0 = originY + row * stride;
				int x1 = x0 + cellSize;
				int y1 = y0 + cellSize;
				int color = ARGB.opaque(colors[row * gridSize + col]);
				int base = vertices.size();
				vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x0, y0, 0}, new float[] {0, 0}, new float[] {0, 0}, color, 0x007F0000));
				vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x1, y0, 0}, new float[] {1, 0}, new float[] {1, 0}, color, 0x007F0000));
				vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x1, y1, 0}, new float[] {1, 1}, new float[] {1, 1}, color, 0x007F0000));
				vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x0, y1, 0}, new float[] {0, 1}, new float[] {0, 1}, color, 0x007F0000));
				indices.add(base); indices.add(base + 1); indices.add(base + 2);
				indices.add(base + 2); indices.add(base + 3); indices.add(base);
			}
		}
		VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
			GuiRenderStratum.GUI_RECTANGLES.order(), 0, 1, 1, SOLID_WHITE_ASSET_ID, 0L, 0.0F,
			new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
			new float[] {1, 0, 0, 1, 0, 0}, left, top, right, bottom, guiWidth, guiHeight,
			right - left + 2, bottom - top + 2, 1, vertices, indices);
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
			List.of(batch), GuiRenderStratum.GUI_RECTANGLES, System.nanoTime());
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA));
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_RECTANGLES,
			LOADING_GRID_PRODUCER, -1, -1.0F, GuiFillDirection.NONE,
			left, top, right - left, bottom - top, guiWidth, guiHeight));
	}

	public enum GuiExecutionRoute {
		DISABLED(false, false),
		JAVA_COMPATIBILITY(true, false),
		RUST_OPENGL_BORROWED_CONTEXT(false, true),
		RUST_VULKAN_WHOLE_FRAME(false, true);

		private final boolean javaCompatibility;
		private final boolean rustGui;

		GuiExecutionRoute(boolean javaCompatibility, boolean rustGui) {
			this.javaCompatibility = javaCompatibility;
			this.rustGui = rustGui;
		}

		public boolean usesJavaCompatibility() {
			// GUI compatibility is a private OpenGL lowering. A stale enum value
			// must never authorize Java GUI rendering after Vulkan selection or
			// during the Rust whole-frame handoff.
			return this.javaCompatibility
				&& !VulkanicAPI.isVulkanBackendSelected()
				&& !RustGalVulkanWholeFrameMode.enabled();
		}

		public boolean usesRustGui() {
			return this.rustGui;
		}
	}

	public static boolean isMigratedGuiEnabled() {
		String legacyCrosshairFlag = System.getProperty("mattmc.rustGal.guiCrosshair.enabled", "true");
		return Boolean.parseBoolean(System.getProperty("mattmc.rustGal.gui.enabled", legacyCrosshairFlag));
	}

	/**
	 * Attempts one explicit semantic text extraction. Returning {@code null}
	 * declines admission; the whole-frame Vulkan caller records that miss and
	 * never re-enters the Java text renderer.
	 */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueText(GuiTextRenderState textState, int guiWidth, int guiHeight) {
		return tryEnqueueText(textState, guiWidth, guiHeight, null);
	}

	/**
	 * Whole-frame variant that keeps the source GuiRenderState node and prepare
	 * phase in scheduler order instead of collapsing text into a fixed HUD slot.
	 */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueText(
		GuiTextRenderState textState, int guiWidth, int guiHeight, @Nullable Integer dynamicLayerOrder
	) {
		if (!TEXT_ROUTE_ENABLED || !currentExecutionRoute().usesRustGui()
			|| textState == null || guiWidth <= 0 || guiHeight <= 0
			|| !finiteAffinePose(textState.pose)) {
			return null;
		}
		List<TextGlyphQuad> quads = new ArrayList<>();
		Font.SemanticTextExtraction extraction;
		try {
			extraction = textState.ensurePrepared().collectSemanticQuads(
				quad -> appendBoundedTextQuad(quads, quad)
			);
		} catch (TextQuadLimitExceeded limit) {
			recordTextRouteDiagnostic("text-quad-cap=" + MAX_RUST_GUI_TEXT_QUADS);
			return null;
		}
		if (extraction.unsupportedRenderableCount() != 0) {
			recordTextRouteDiagnostic("unsupported-renderables=" + extraction.unsupportedRenderableCount()
				+ " renderables=" + extraction.renderableCount() + " quads=" + extraction.quadCount());
			return null;
		}
		// A single text node commonly contains many glyphs from the same atlas.
		// Keep one staging request per semantic atlas while preserving the glyph
		// order in `requests`; stageTextAtlas itself remains generation-aware.
		List<TextAtlasRequest> atlasRequests = new ArrayList<>();
		List<RustGalGuiRawImageAssets.Asset> rawAssets = new ArrayList<>();
		Set<Long> stagedAtlasIds = new HashSet<>();
		List<VulkanicGalBridge.GuiAffineQuadRecord> requests = new ArrayList<>(quads.size());
		try {
			for (TextGlyphQuad quad : quads) {
				if (!finiteTextQuad(quad) || !isParallelogram(quad)) {
					recordTextRouteDiagnostic("non-parallelogram");
					return null;
				}
				FontTexture.SemanticAtlasSnapshot atlas = FontTexture.semanticAtlasSnapshot(quad.atlasIdentity());
				long assetId;
				if (atlas != null) {
					assetId = semanticTextAtlasId(atlas.identity(), atlas.colored());
					if (stagedAtlasIds.add(assetId)) {
						atlasRequests.add(new TextAtlasRequest(assetId, atlas));
					}
				} else {
					ResourceLocation identity = ResourceLocation.parse(quad.atlasIdentity());
					RustGalGuiRawImageAssets.Asset raw = RustGalGuiRawImageAssets.resolve(identity);
					if (raw == null) {
						recordTextRouteDiagnostic("missing-semantic-atlas=" + quad.atlasIdentity());
						return null;
					}
					rawAssets.add(raw);
					assetId = raw.assetId();
				}
				requests.add(transformTextQuad(quad, textState.pose, assetId, guiWidth, guiHeight, textState.scissor));
			}
		} catch (RuntimeException error) {
			LOGGER.debug("Rust GUI text semantic extraction declined", error);
			recordTextRouteDiagnostic("extraction-error=" + error.getClass().getSimpleName());
			return null;
		}

		List<RustGalGuiElementRenderState> elements = new ArrayList<>(requests.isEmpty() ? 0 : 1);
		if (!requests.isEmpty()) {
			int requestLayerOrder = dynamicLayerOrder == null ? GuiRenderStratum.GUI_TEXT.order() : dynamicLayerOrder(dynamicLayerOrder);
			List<VulkanicGalBridge.GuiAffineQuadRecord> orderedRequests = new ArrayList<>(requests.size());
			for (VulkanicGalBridge.GuiAffineQuadRecord request : requests) {
				orderedRequests.add(request.withStratum(requestLayerOrder));
			}
			long startedNanos = System.nanoTime();
			var token = RustGalFrameCoordinator.enqueueGuiAffineQuadRequests(
				orderedRequests,
				dynamicLayerOrder == null ? GuiRenderStratum.GUI_TEXT.id() : dynamicLayerId(dynamicLayerOrder),
				requestLayerOrder,
				startedNanos);
			// Commit copied font assets only after the complete text request has
			// entered the scheduler. If admission rejects, no asset generation or
			// raw image remains retained without corresponding semantic work.
			for (TextAtlasRequest atlasRequest : atlasRequests) {
				stageTextAtlas(atlasRequest.assetId(), atlasRequest.atlas());
			}
			for (RustGalGuiRawImageAssets.Asset raw : rawAssets) {
				RustGalGuiRawImageAssets.stage(raw);
			}
			double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
			for (VulkanicGalBridge.GuiAffineQuadRecord request : orderedRequests) {
				minX = Math.min(minX, Math.min(request.x0(), Math.min(request.x1(), request.x3())));
				minY = Math.min(minY, Math.min(request.y0(), Math.min(request.y1(), request.y3())));
				maxX = Math.max(maxX, Math.max(request.x0(), Math.max(request.x1(), request.x3())));
				maxY = Math.max(maxY, Math.max(request.y0(), Math.max(request.y1(), request.y3())));
			}
			elements.add(new RustGalGuiElementRenderState(
				token, GuiRenderStratum.GUI_TEXT, TEXT_PRODUCER, -1, -1.0F, GuiFillDirection.NONE,
				(int)Math.floor(minX), (int)Math.floor(minY),
				Math.max(1, (int)Math.ceil(maxX - minX)), Math.max(1, (int)Math.ceil(maxY - minY)),
				guiWidth, guiHeight
			));
		}
		if (!requests.isEmpty()) {
			VulkanicGalBridge.GuiAffineQuadRecord first = requests.getFirst();
			recordTextRouteDiagnostic(
				"accepted quads=" + requests.size() + " asset=" + first.assetId()
					+ " origin=" + first.x0() + "," + first.y0()
					+ " u_axis=" + first.x1() + "," + first.y1()
					+ " v_axis=" + first.x3() + "," + first.y3()
					+ " uv=" + first.u0() + "," + first.v0() + ".." + first.u1() + "," + first.v1()
			);
		}
		return List.copyOf(elements);
	}

	/**
	 * Admits a directly submitted baked glyph element without reconstructing a
	 * Java font buffer. This covers hooks that submit {@link GlyphRenderState}
	 * directly instead of building a {@link GuiTextRenderState}; renderables
	 * that cannot expose copied semantic quads remain unavailable.
	 */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueGlyph(
		GlyphRenderState glyph, int guiWidth, int guiHeight, int dynamicLayerOrder
	) {
		if (!TEXT_ROUTE_ENABLED || currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
			|| glyph == null || guiWidth <= 0 || guiHeight <= 0 || dynamicLayerOrder < 0
			|| !finiteAffinePose(glyph.pose())) {
			return null;
		}
		List<TextGlyphQuad> quads = new ArrayList<>();
		final int extracted;
		try {
			extracted = glyph.renderable().collectSemanticQuads(
				quad -> appendBoundedTextQuad(quads, quad)
			);
		} catch (TextQuadLimitExceeded limit) {
			recordTextRouteDiagnostic("direct-glyph-quad-cap=" + MAX_RUST_GUI_TEXT_QUADS);
			return null;
		}
		if (extracted <= 0 || quads.isEmpty()) {
			recordTextRouteDiagnostic("direct-glyph-renderable-unavailable");
			return null;
		}
		List<TextAtlasRequest> atlasRequests = new ArrayList<>();
		List<RustGalGuiRawImageAssets.Asset> directGlyphRawAssets = new ArrayList<>();
		Set<Long> stagedAtlasIds = new HashSet<>();
		List<VulkanicGalBridge.GuiAffineQuadRecord> requests = new ArrayList<>(quads.size());
		try {
			for (TextGlyphQuad quad : quads) {
				if (!finiteTextQuad(quad) || !isParallelogram(quad)) {
					recordTextRouteDiagnostic("direct-glyph-non-parallelogram");
					return null;
				}
				FontTexture.SemanticAtlasSnapshot atlas = FontTexture.semanticAtlasSnapshot(quad.atlasIdentity());
				long assetId;
				if (atlas != null) {
					assetId = semanticTextAtlasId(atlas.identity(), atlas.colored());
					if (stagedAtlasIds.add(assetId)) atlasRequests.add(new TextAtlasRequest(assetId, atlas));
				} else {
					ResourceLocation identity = ResourceLocation.parse(quad.atlasIdentity());
					RustGalGuiRawImageAssets.Asset raw = RustGalGuiRawImageAssets.resolve(identity);
					if (raw == null) {
						recordTextRouteDiagnostic("direct-glyph-missing-semantic-atlas=" + quad.atlasIdentity());
						return null;
					}
					directGlyphRawAssets.add(raw);
					assetId = raw.assetId();
				}
				requests.add(transformTextQuad(quad, glyph.pose(), assetId, guiWidth, guiHeight, glyph.scissorArea()));
			}
		} catch (RuntimeException error) {
			LOGGER.debug("Rust GUI direct glyph semantic extraction declined", error);
			recordTextRouteDiagnostic("direct-glyph-extraction-error=" + error.getClass().getSimpleName());
			return null;
		}
		int requestLayerOrder = dynamicLayerOrder(dynamicLayerOrder);
		List<VulkanicGalBridge.GuiAffineQuadRecord> orderedRequests = new ArrayList<>(requests.size());
		for (VulkanicGalBridge.GuiAffineQuadRecord request : requests) orderedRequests.add(request.withStratum(requestLayerOrder));
		long startedNanos = System.nanoTime();
		var token = RustGalFrameCoordinator.enqueueGuiAffineQuadRequests(
			orderedRequests, dynamicLayerId(dynamicLayerOrder), requestLayerOrder, startedNanos);
		for (TextAtlasRequest atlasRequest : atlasRequests) stageTextAtlas(atlasRequest.assetId(), atlasRequest.atlas());
		for (RustGalGuiRawImageAssets.Asset raw : directGlyphRawAssets) RustGalGuiRawImageAssets.stage(raw);
		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		for (VulkanicGalBridge.GuiAffineQuadRecord request : orderedRequests) {
			minX = Math.min(minX, Math.min(request.x0(), Math.min(request.x1(), request.x3())));
			minY = Math.min(minY, Math.min(request.y0(), Math.min(request.y1(), request.y3())));
			maxX = Math.max(maxX, Math.max(request.x0(), Math.max(request.x1(), request.x3())));
			maxY = Math.max(maxY, Math.max(request.y0(), Math.max(request.y1(), request.y3())));
		}
		return List.of(new RustGalGuiElementRenderState(
			token, GuiRenderStratum.GUI_TEXT, TEXT_PRODUCER, -1, -1.0F, GuiFillDirection.NONE,
			(int)Math.floor(minX), (int)Math.floor(minY),
			Math.max(1, (int)Math.ceil(maxX - minX)), Math.max(1, (int)Math.ceil(maxY - minY)),
			guiWidth, guiHeight
		));
	}

	/**
	 * Converts one GUI rectangle into an explicit Rust-owned primitive. Uniform
	 * colors use the compact affine path; vertical gradients use an owned mesh
	 * so interpolation happens in Rust rather than in a Java renderer.
	 */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueUniformRectangle(
		ColoredRectangleRenderState rectangle, int guiWidth, int guiHeight
	) {
		return tryEnqueueUniformRectangle(rectangle, guiWidth, guiHeight, null);
	}

	/** Copies VoxelMap's four-corner, no-texture gradient into the Rust GUI mesh ABI. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueFourColoredRectangle(
		net.voxelmap.util.FourColoredRectangleRenderState rectangle, int guiWidth, int guiHeight,
		int sourceLayerOrder
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
			|| rectangle == null || guiWidth <= 0 || guiHeight <= 0 || sourceLayerOrder < 0
			|| rectangle.textureSetup() != net.minecraft.client.gui.render.TextureSetup.noTexture()
			|| rectangle.pipeline() != RenderPipelines.GUI || !finiteAffinePose(rectangle.pose())) return null;
		Matrix3x2f pose = rectangle.pose();
		float x0 = pose.m00() * rectangle.x0() + pose.m10() * rectangle.y0() + pose.m20();
		float y0 = pose.m01() * rectangle.x0() + pose.m11() * rectangle.y0() + pose.m21();
		float x1 = pose.m00() * rectangle.x1() + pose.m10() * rectangle.y0() + pose.m20();
		float y1 = pose.m01() * rectangle.x1() + pose.m11() * rectangle.y0() + pose.m21();
		float x3 = pose.m00() * rectangle.x0() + pose.m10() * rectangle.y1() + pose.m20();
		float y3 = pose.m01() * rectangle.x0() + pose.m11() * rectangle.y1() + pose.m21();
		float x2 = x1 + x3 - x0, y2 = y1 + y3 - y0;
		if (!Float.isFinite(x0) || !Float.isFinite(y0) || !Float.isFinite(x1) || !Float.isFinite(y1)
			|| !Float.isFinite(x2) || !Float.isFinite(y2) || !Float.isFinite(x3) || !Float.isFinite(y3)) return null;
		int left = (int)Math.floor(Math.min(Math.min(x0, x1), Math.min(x2, x3)));
		int top = (int)Math.floor(Math.min(Math.min(y0, y1), Math.min(y2, y3)));
		int right = (int)Math.ceil(Math.max(Math.max(x0, x1), Math.max(x2, x3)));
		int bottom = (int)Math.ceil(Math.max(Math.max(y0, y1), Math.max(y2, y3)));
		// GUI producers are allowed to submit partially off-screen geometry (for
		// example a map overlay entering from an edge).  Keep the semantic vertices
		// intact so Rust performs the same viewport clipping, but clamp the copied
		// batch metadata to the actual frame.  Reject only a rectangle with no
		// visible intersection; treating that as unsupported would make a selected
		// Vulkan frame lose otherwise valid GUI work.
		if (left >= right || top >= bottom || right <= 0 || bottom <= 0
			|| left >= guiWidth || top >= guiHeight) return null;
		int clippedLeft = Math.max(0, left);
		int clippedTop = Math.max(0, top);
		int clippedRight = Math.min(guiWidth, right);
		int clippedBottom = Math.min(guiHeight, bottom);
		if (clippedLeft >= clippedRight || clippedTop >= clippedBottom) return null;
		ScreenRectangle scissor = rectangle.scissorArea();
		int scissorLeft = 0, scissorTop = 0, scissorRight = guiWidth, scissorBottom = guiHeight;
		if (scissor != null) {
			if (scissor.width() < 0 || scissor.height() < 0) return null;
			scissorLeft = Math.max(0, scissor.left());
			scissorTop = Math.max(0, scissor.top());
			scissorRight = (int)Math.min((long)guiWidth, (long)scissor.left() + scissor.width());
			scissorBottom = (int)Math.min((long)guiHeight, (long)scissor.top() + scissor.height());
			if (scissorLeft >= scissorRight || scissorTop >= scissorBottom) return null;
		}
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = List.of(
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x0, y0, 0}, new float[] {0, 0}, new float[] {0, 0}, rectangle.color00(), 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x1, y1, 0}, new float[] {1, 0}, new float[] {1, 0}, rectangle.color10(), 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x2, y2, 0}, new float[] {1, 1}, new float[] {1, 1}, rectangle.color11(), 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x3, y3, 0}, new float[] {0, 1}, new float[] {0, 1}, rectangle.color01(), 0x007F0000));
		VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
			dynamicLayerOrder(sourceLayerOrder), 0, 1, 1, SOLID_WHITE_ASSET_ID, 0L, 0.0F,
			new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
			new float[] {1, 0, 0, 1, 0, 0}, clippedLeft, clippedTop, clippedRight, clippedBottom, guiWidth, guiHeight,
			clippedRight - clippedLeft + 2, clippedBottom - clippedTop + 2, 1, scissor == null ? 0 : 1,
			scissor == null ? 0 : scissorLeft, scissor == null ? 0 : scissorTop,
			scissor == null ? 0 : scissorRight - scissorLeft, scissor == null ? 0 : scissorBottom - scissorTop,
			vertices, List.of(0, 1, 2, 2, 3, 0));
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
			List.of(batch), dynamicLayerId(sourceLayerOrder), dynamicLayerOrder(sourceLayerOrder), System.nanoTime());
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA));
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_RECTANGLES,
			"voxelmap.gui.four-colored-rectangle", -1, -1.0F, GuiFillDirection.NONE,
			clippedLeft, clippedTop, clippedRight - clippedLeft, clippedBottom - clippedTop, guiWidth, guiHeight));
	}

	/**
	 * Copies VoxelMap's rotated square/circular map into an owned GUI mesh. The
	 * circular route uses the same bounded scanline geometry as the legacy
	 * mask, while the square route retains its rotated quad and source transform.
	 */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueVoxelMapMask(
		ResourceLocation texture, int guiWidth, int guiHeight, float centerX, float centerY,
		float radius, float angleRadians, float mapScale, float sourceOffsetX, float sourceOffsetY,
		int color, boolean circular, int sourceLayerOrder
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME || texture == null
			|| guiWidth <= 0 || guiHeight <= 0 || sourceLayerOrder < 0
			|| !Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(radius)
			|| !Float.isFinite(angleRadians) || !Float.isFinite(mapScale)
			|| !Float.isFinite(sourceOffsetX) || !Float.isFinite(sourceOffsetY)
			|| radius <= 0.0F || radius > 256.0F || mapScale <= 0.0F) return null;
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(texture);
		if (asset == null) return null;
		int pixelRadius = Math.max(1, Mth.ceil(radius));
		if (circular && pixelRadius > 256) return null;
		// VoxelMap's selected dynamic image is 32 * 2^zoom pixels wide, not
		// always the legacy 512px FBO.  Derive the source-space center and scale
		// from the copied asset so lower zoom levels sample the generated map
		// instead of clamping to its dark edge texel.
		float sourceWidth = asset.width();
		float sourceHeight = asset.height();
		if (sourceWidth <= 0.0F || sourceHeight <= 0.0F || sourceWidth != sourceHeight) return null;
		// Preserve the legacy transform's bounded-input contract even though the
		// final-coordinate semantic mesh does not multiply UVs by mapScale.
		if (!Float.isFinite(radius * mapScale)) return null;
		float cos = Mth.cos(angleRadians), sin = Mth.sin(angleRadians);
		// The semantic mesh is already expressed in final GUI/frame coordinates;
		// the legacy pose scale is not applied a second time by Rust.  Applying
		// mapScale here would expand the UV span by scaleProj and clamp the map to
		// one edge texel (a uniform square in the presented frame).
		float sourceScale = sourceWidth * 0.5F / radius;
		if (!Float.isFinite(sourceScale)) return null;
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = new ArrayList<>();
		List<Integer> indices = new ArrayList<>();
		if (!circular) {
			float left = centerX - radius, top = centerY - radius;
			float right = centerX + radius, bottom = centerY + radius;
			// GUI mesh rasterization reflects Y into clip space and the identity
			// semantic transform therefore uses clockwise front faces. Emit the
			// source quad clockwise in top-left GUI coordinates so it remains
			// front-facing after that required reflection.
			float[][] corners = {{left, top}, {right, top}, {right, bottom}, {left, bottom}};
			for (float[] corner : corners) {
				float dx = (corner[0] - centerX) * sourceScale, dy = (corner[1] - centerY) * sourceScale;
				float u = (cos * dx - sin * dy + sourceOffsetX + sourceWidth * 0.5F) / sourceWidth;
				float v = (sourceHeight * 0.5F - (-sin * dx - cos * dy + sourceOffsetY)) / sourceHeight;
				vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {corner[0], corner[1], 0},
					new float[] {u, v}, new float[] {u, v}, color, 0x007F0000));
			}
			indices.addAll(List.of(0, 1, 2, 2, 3, 0));
		} else {
			float radiusSquared = radius * radius;
			for (int row = -pixelRadius; row < pixelRadius; row++) {
				float bandCenter = row + 0.5F;
				float halfWidth = (float)Math.floor(Math.sqrt(Math.max(0.0F, radiusSquared - bandCenter * bandCenter)));
				float left = centerX - halfWidth, right = centerX + halfWidth + 1.0F;
				float top = centerY + row, bottom = top + 1.0F;
				int base = vertices.size();
				float[][] corners = {{left, top}, {right, top}, {right, bottom}, {left, bottom}};
				for (float[] corner : corners) {
					float dx = (corner[0] - centerX) * sourceScale, dy = (corner[1] - centerY) * sourceScale;
					float u = (cos * dx - sin * dy + sourceOffsetX + sourceWidth * 0.5F) / sourceWidth;
					float v = (sourceHeight * 0.5F - (-sin * dx - cos * dy + sourceOffsetY)) / sourceHeight;
					vertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {corner[0], corner[1], 0},
						new float[] {u, v}, new float[] {u, v}, color, 0x007F0000));
				}
				indices.addAll(List.of(base, base + 1, base + 2, base + 2, base + 3, base));
			}
		}
		int left = (int)Math.floor(centerX - radius), top = (int)Math.floor(centerY - radius);
		int size = Math.max(1, (int)Math.ceil(radius * 2.0F));
		if (left < 0 || top < 0 || left + size > guiWidth || top + size > guiHeight) return null;
		// Mesh batches rasterize into a small Rust-owned offscreen target before
		// compositing at (left, top).  Their vertex positions are therefore
		// target-local, unlike affine GUI quads which rasterize directly against
		// the full frame.  Keep the semantic map geometry in frame coordinates
		// while translating the copied vertices exactly once at this boundary.
		List<VulkanicGalBridge.GuiMeshVertexRecord> localVertices = new ArrayList<>(vertices.size());
		for (VulkanicGalBridge.GuiMeshVertexRecord vertex : vertices) {
			float[] position = vertex.position();
			// Raster coordinates are local to the compact offscreen target. Keep
			// one pixel of guard band on every side for the later composite UVs.
			position[0] -= left - 1.0F;
			position[1] -= top - 1.0F;
			localVertices.add(new VulkanicGalBridge.GuiMeshVertexRecord(position, vertex.atlasUv(), vertex.localUv(), vertex.colorArgb(), vertex.normalPacked()));
		}
		VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
			// The map image and frame both contain meaningful transparent pixels;
			// use the explicit translucent material so Rust preserves their alpha
			// instead of filling the entire offscreen target opaquely.
			// The map is already color- and light-baked on the CPU.  Use the
			// explicit translucent mesh mode (ABI value 3) so transparent pixels
			// composite over the world; lighting mode 1 is flat.
			dynamicLayerOrder(sourceLayerOrder), 0, 3, 1, asset.assetId(), 0L, 0.0F,
			new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
			new float[] {1, 0, 0, 1, 0, 0}, left, top, left + size, top + size, guiWidth, guiHeight,
			size + 2, size + 2, 1, 0, 0, 0, 0, 0, localVertices, indices);
		// Publish the immutable CPU snapshot before freezing the request so the
		// coordinator cannot consume a mesh against a previous dynamic-image
		// generation.
		RustGalGuiRawImageAssets.stage(asset);
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
			List.of(batch), dynamicLayerId(sourceLayerOrder), dynamicLayerOrder(sourceLayerOrder), System.nanoTime());
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_FILE_BACKED_BLIT,
			"voxelmap.gui." + (circular ? "circular-map" : "square-map"), -1, -1.0F, GuiFillDirection.NONE,
			left, top, size, size, guiWidth, guiHeight));
	}

	/**
	 * Copies the vanilla End Portal screen's sky plus sixteen animated portal
	 * layers into one explicit Rust GUI mesh request. Per-vertex UVs are retained
	 * here because the portal layers rotate and cannot be represented by an
	 * affine four-corner blit.
	 */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueEndPortal(
		int guiWidth, int guiHeight, float gameTime, int sourceLayerOrder
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
			|| guiWidth <= 0 || guiHeight <= 0 || !Float.isFinite(gameTime) || sourceLayerOrder < 0) return null;
		ResourceLocation skyLocation = net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer.END_SKY_LOCATION;
		ResourceLocation portalLocation = net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer.END_PORTAL_LOCATION;
		RustGalGuiRawImageAssets.Asset sky = RustGalGuiRawImageAssets.resolve(skyLocation);
		RustGalGuiRawImageAssets.Asset portal = RustGalGuiRawImageAssets.resolve(portalLocation);
		if (sky == null || portal == null) return null;
		int[] colors = {
			0x160B636F, 0x1E185A59, 0x241A6766, 0x2F1D7374,
			0x382C7C68, 0x3A2B587D, 0x44366BA8, 0x4A438A59,
			0x514F6A96, 0x4E526FBE, 0x5A6B7078, 0x4C398FEE,
			0x5F918CDA, 0x55C0A0A4, 0x66A6D0A3, 0x6EA0D2FF
		};
		List<VulkanicGalBridge.GuiMeshBatchRecord> batches = new ArrayList<>(17);
		batches.add(endPortalBatch(GuiRenderStratum.GUI_OPAQUE_BLIT.order(), 0, sky.assetId(), 0xFFFFFFFF,
			0.0F, 0.0F, 1.0F, 1.0F, guiWidth, guiHeight));
		for (int layer = 1; layer <= 16; layer++) {
			float scale = (4.5F - layer / 4.0F) * 2.0F;
			float radians = (layer * layer * 4321.0F + layer * 9.0F) * 2.0F * Mth.DEG_TO_RAD;
			float cos = Mth.cos(radians), sin = Mth.sin(radians);
			float tx = 17.0F / layer;
			float ty = (2.0F + layer / 1.5F) * (gameTime * 1.5F);
			float[] uv = new float[8];
			for (int vertex = 0; vertex < 4; vertex++) {
				float u = (vertex == 1 || vertex == 2 ? 1.0F : 0.0F) - 0.5F;
				float v = (vertex >= 2 ? 1.0F : 0.0F) - 0.5F;
				uv[vertex * 2] = scale * (cos * u - sin * v) + tx + 0.25F;
				uv[vertex * 2 + 1] = scale * (sin * u + cos * v) + ty + 0.25F;
			}
			for (float value : uv) if (!Float.isFinite(value)) return null;
			batches.add(endPortalBatch(GuiRenderStratum.GUI_FILE_BACKED_BLIT.order() + layer, layer, portal.assetId(), colors[layer - 1],
				uv[0], uv[1], uv[2], uv[3], guiWidth, guiHeight, uv));
		}
		long startedNanos = System.nanoTime();
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
			batches, "gui.semantic.layer." + sourceLayerOrder, dynamicLayerOrder(sourceLayerOrder), startedNanos);
		// Admit image resources only after the complete bounded mesh request has
		// been accepted. A rejected scheduler request must not leave staged
		// End-Portal assets live without a corresponding semantic frame item.
		RustGalGuiRawImageAssets.stage(sky);
		RustGalGuiRawImageAssets.stage(portal);
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_FILE_BACKED_BLIT,
			"minecraft.gui.end-portal", -1, -1.0F, GuiFillDirection.NONE,
			0, 0, guiWidth, guiHeight, guiWidth, guiHeight));
	}

	/** Copies a textured vertical color gradient into the explicit GUI mesh ABI. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueGradientBlit(
		ResourceLocation source, Matrix3x2f pose, float x, float y, float width, float height,
		float u0, float u1, float v0, float v1, int topColor, int bottomColor,
		int guiWidth, int guiHeight, int sourceLayerOrder
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME || source == null
			|| pose == null || !finiteAffinePose(pose) || guiWidth <= 0 || guiHeight <= 0 || sourceLayerOrder < 0
			|| !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
			|| !Float.isFinite(u0) || !Float.isFinite(u1) || !Float.isFinite(v0) || !Float.isFinite(v1)
			|| u0 < -GUI_UV_OVERLAP_LIMIT || u0 > 1.0F + GUI_UV_OVERLAP_LIMIT
			|| u1 < -GUI_UV_OVERLAP_LIMIT || u1 > 1.0F + GUI_UV_OVERLAP_LIMIT
			|| v0 < -GUI_UV_OVERLAP_LIMIT || v0 > 1.0F + GUI_UV_OVERLAP_LIMIT
			|| v1 < -GUI_UV_OVERLAP_LIMIT || v1 > 1.0F + GUI_UV_OVERLAP_LIMIT
			|| width <= 0.0F || height <= 0.0F) return null;
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(source);
		if (asset == null) return null;
		float x0 = pose.m00() * x + pose.m10() * y + pose.m20();
		float y0 = pose.m01() * x + pose.m11() * y + pose.m21();
		float x1 = pose.m00() * (x + width) + pose.m10() * y + pose.m20();
		float y1 = pose.m01() * (x + width) + pose.m11() * y + pose.m21();
		float x3 = pose.m00() * x + pose.m10() * (y + height) + pose.m20();
		float y3 = pose.m01() * x + pose.m11() * (y + height) + pose.m21();
		float x2 = x1 + x3 - x0, y2 = y1 + y3 - y0;
		if (!Float.isFinite(x0) || !Float.isFinite(y0) || !Float.isFinite(x1) || !Float.isFinite(y1)
			|| !Float.isFinite(x2) || !Float.isFinite(y2) || !Float.isFinite(x3) || !Float.isFinite(y3)) return null;
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = List.of(
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x0, y0, 0}, new float[] {u0, v0}, new float[] {u0, v0}, topColor, 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x1, y1, 0}, new float[] {u1, v0}, new float[] {u1, v0}, topColor, 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x2, y2, 0}, new float[] {u1, v1}, new float[] {u1, v1}, bottomColor, 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x3, y3, 0}, new float[] {u0, v1}, new float[] {u0, v1}, bottomColor, 0x007F0000)
		);
		int left = (int)Math.floor(Math.min(Math.min(x0, x1), Math.min(x2, x3)));
		int top = (int)Math.floor(Math.min(Math.min(y0, y1), Math.min(y2, y3)));
		int right = (int)Math.ceil(Math.max(Math.max(x0, x1), Math.max(x2, x3)));
		int bottom = (int)Math.ceil(Math.max(Math.max(y0, y1), Math.max(y2, y3)));
		if (left < 0 || top < 0 || right > guiWidth || bottom > guiHeight || left >= right || top >= bottom) return null;
		VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
			dynamicLayerOrder(sourceLayerOrder), 0, 1, 1, asset.assetId(), 0L, 0.0F,
			new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
			new float[] {1, 0, 0, 1, 0, 0}, left, top, right, bottom, guiWidth, guiHeight,
			right - left + 2, bottom - top + 2, 1, vertices, List.of(0, 1, 2, 2, 3, 0));
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
			List.of(batch), dynamicLayerId(sourceLayerOrder), dynamicLayerOrder(sourceLayerOrder), System.nanoTime());
		RustGalGuiRawImageAssets.stage(asset);
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_FILE_BACKED_BLIT,
			"voxelmap.gui.gradient", -1, -1.0F, GuiFillDirection.NONE, left, top, right - left, bottom - top, guiWidth, guiHeight));
	}

	private static VulkanicGalBridge.GuiMeshBatchRecord endPortalBatch(
		int stratum, int layerIndex, long assetId, int color, float u0, float v0, float u1, float v1,
		int guiWidth, int guiHeight
	) {
		return endPortalBatch(stratum, layerIndex, assetId, color, u0, v0, u1, v1, guiWidth, guiHeight,
			new float[] {u0, v0, u1, v0, u1, v1, u0, v1});
	}

	private static VulkanicGalBridge.GuiMeshBatchRecord endPortalBatch(
		int stratum, int layerIndex, long assetId, int color, float u0, float v0, float u1, float v1,
		int guiWidth, int guiHeight, float[] uv
	) {
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = List.of(
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {1, 1, 0}, new float[] {uv[0], uv[1]}, new float[] {uv[0], uv[1]}, color, 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {guiWidth + 1, 1, 0}, new float[] {uv[2], uv[3]}, new float[] {uv[2], uv[3]}, color, 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {guiWidth + 1, guiHeight + 1, 0}, new float[] {uv[4], uv[5]}, new float[] {uv[4], uv[5]}, color, 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {1, guiHeight + 1, 0}, new float[] {uv[6], uv[7]}, new float[] {uv[6], uv[7]}, color, 0x007F0000)
		);
		return new VulkanicGalBridge.GuiMeshBatchRecord(stratum, layerIndex, 1, 1, assetId, layerIndex,
			0.0F, new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
			new float[] {1, 0, 0, 1, 0, 0}, 0, 0, guiWidth, guiHeight, guiWidth, guiHeight,
			guiWidth + 2, guiHeight + 2, 1, vertices, List.of(0, 1, 2, 2, 3, 0));
	}

	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueUniformRectangle(
		ColoredRectangleRenderState rectangle, int guiWidth, int guiHeight, @Nullable Integer dynamicLayerOrder
	) {
		if (!currentExecutionRoute().usesRustGui()
			|| rectangle == null || guiWidth <= 0 || guiHeight <= 0
			|| !finiteAffinePose(rectangle.pose())) {
			return null;
		}
		if (rectangle.col1() != rectangle.col2()) {
			return tryEnqueueVerticalGradientRectangle(rectangle, guiWidth, guiHeight, dynamicLayerOrder);
		}
		Matrix3x2f pose = rectangle.pose();
		float x0 = pose.m00() * rectangle.x0() + pose.m10() * rectangle.y0() + pose.m20();
		float y0 = pose.m01() * rectangle.x0() + pose.m11() * rectangle.y0() + pose.m21();
		float x1 = pose.m00() * rectangle.x1() + pose.m10() * rectangle.y0() + pose.m20();
		float y1 = pose.m01() * rectangle.x1() + pose.m11() * rectangle.y0() + pose.m21();
		float x3 = pose.m00() * rectangle.x0() + pose.m10() * rectangle.y1() + pose.m20();
		float y3 = pose.m01() * rectangle.x0() + pose.m11() * rectangle.y1() + pose.m21();
		float x2 = x1 + x3 - x0;
		float y2 = y1 + y3 - y0;
		int requestLayerOrder = dynamicLayerOrder == null ? GuiRenderStratum.GUI_RECTANGLES.order() : dynamicLayerOrder(dynamicLayerOrder);
		if (rectangle.pipeline() == RenderPipelines.GUI_INVERT) {
			requestLayerOrder = GuiRenderStratum.GUI_INVERT_RECTANGLE.order();
		} else if (rectangle.pipeline() == RenderPipelines.GUI_TEXT_HIGHLIGHT) {
			requestLayerOrder = GuiRenderStratum.GUI_ADDITIVE_BLIT.order();
		}
		VulkanicGalBridge.GuiAffineQuadRecord request = new VulkanicGalBridge.GuiAffineQuadRecord(
			requestLayerOrder, SOLID_WHITE_ASSET_ID, x0, y0, x1, y1, x3, y3,
			0.0F, 0.0F, 0.0F, 1.0F, 1.0F, rectangle.col1(), guiWidth, guiHeight
		);
		if (rectangle.scissorArea() != null) {
			ScreenRectangle scissor = rectangle.scissorArea();
			request = request.withClip(scissor.left(), scissor.top(), scissor.width(), scissor.height());
		}
		long startedNanos = System.nanoTime();
		RustGalFrameScheduler.Token token = dynamicLayerOrder == null
			? RustGalFrameCoordinator.enqueueGuiAffineQuadRequest(request, GuiRenderStratum.GUI_RECTANGLES, startedNanos)
			: RustGalFrameCoordinator.enqueueGuiAffineQuadRequest(
				request, dynamicLayerId(dynamicLayerOrder), requestLayerOrder, startedNanos
			);
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA
		));
		int left = (int)Math.floor(Math.min(Math.min(x0, x1), Math.min(x2, x3)));
		int top = (int)Math.floor(Math.min(Math.min(y0, y1), Math.min(y2, y3)));
		int width = Math.max(1, (int)Math.ceil(Math.max(Math.max(x0, x1), Math.max(x2, x3)) - left));
		int height = Math.max(1, (int)Math.ceil(Math.max(Math.max(y0, y1), Math.max(y2, y3)) - top));
		return List.of(new RustGalGuiElementRenderState(
			token, GuiRenderStratum.GUI_RECTANGLES, RECTANGLE_PRODUCER, -1, -1.0F, GuiFillDirection.NONE,
			left, top, width, height, guiWidth, guiHeight
		));
	}

	@Nullable
	private static List<RustGalGuiElementRenderState> tryEnqueueVerticalGradientRectangle(
		ColoredRectangleRenderState rectangle, int guiWidth, int guiHeight, @Nullable Integer dynamicLayerOrder
	) {
		if (rectangle == null || guiWidth <= 0 || guiHeight <= 0
			|| !finiteAffinePose(rectangle.pose())) return null;
		ScreenRectangle scissor = rectangle.scissorArea();
		if (scissor != null && (scissor.left() < 0 || scissor.top() < 0
			|| scissor.width() < 0 || scissor.height() < 0
			|| scissor.left() > guiWidth || scissor.top() > guiHeight
			|| scissor.width() > guiWidth - scissor.left()
			|| scissor.height() > guiHeight - scissor.top())) {
			return null;
		}
		int left = Math.min(rectangle.x0(), rectangle.x1());
		int top = Math.min(rectangle.y0(), rectangle.y1());
		int right = Math.max(rectangle.x0(), rectangle.x1());
		int bottom = Math.max(rectangle.y0(), rectangle.y1());
		if (left == right || top == bottom) return null;
		int guard = 1;
		int renderWidth;
		int renderHeight;
		try {
			renderWidth = Math.addExact(Math.subtractExact(right, left), guard * 2);
			renderHeight = Math.addExact(Math.subtractExact(bottom, top), guard * 2);
		} catch (ArithmeticException error) {
			return null;
		}
		int requestLayerOrder = dynamicLayerOrder == null ? GuiRenderStratum.GUI_RECTANGLES.order() : dynamicLayerOrder(dynamicLayerOrder);
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = List.of(
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {guard, guard, 0.0F}, new float[] {0, 0}, new float[] {0, 0}, rectangle.col1(), 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {renderWidth - guard, guard, 0.0F}, new float[] {1, 0}, new float[] {1, 0}, rectangle.col1(), 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {renderWidth - guard, renderHeight - guard, 0.0F}, new float[] {1, 1}, new float[] {1, 1}, rectangle.col2(), 0x007F0000),
			new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {guard, renderHeight - guard, 0.0F}, new float[] {0, 1}, new float[] {0, 1}, rectangle.col2(), 0x007F0000)
		);
		Matrix3x2f pose = rectangle.pose();
		VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
			requestLayerOrder, 0, 1, 1, SOLID_WHITE_ASSET_ID, 0L, 0.0F,
			new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
			new float[] {pose.m00(), pose.m01(), pose.m10(), pose.m11(), pose.m20(), pose.m21()},
			left, top, right, bottom, guiWidth, guiHeight, renderWidth, renderHeight, guard,
			scissor == null ? 0 : 1,
			scissor == null ? 0 : scissor.left(),
			scissor == null ? 0 : scissor.top(),
			scissor == null ? 0 : scissor.width(),
			scissor == null ? 0 : scissor.height(), vertices, List.of(0, 1, 2, 2, 3, 0)
		);
		long startedNanos = System.nanoTime();
		RustGalFrameScheduler.Token token = dynamicLayerOrder == null
			? RustGalFrameCoordinator.enqueueGuiMeshItemRequest(List.of(batch), GuiRenderStratum.GUI_RECTANGLES, startedNanos)
			: RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
				List.of(batch), dynamicLayerId(dynamicLayerOrder), requestLayerOrder, startedNanos
			);
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA
		));
		return List.of(new RustGalGuiElementRenderState(
			token, GuiRenderStratum.GUI_RECTANGLES, RECTANGLE_PRODUCER + ".gradient", -1, -1.0F, GuiFillDirection.NONE,
			left, top, right - left, bottom - top, guiWidth, guiHeight
		));
	}

	/** Copies the vanilla profiler pie chart into a bounded explicit GUI mesh. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueProfilerChart(
		GuiProfilerChartRenderState chart, int guiWidth, int guiHeight, int sourceLayerOrder
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
			|| chart == null || guiWidth <= 0 || guiHeight <= 0
			|| chart.x1() <= chart.x0() || chart.y1() <= chart.y0()
			|| chart.chartData().size() > 128) return null;
		int left = chart.x0(), top = chart.y0(), right = chart.x1(), bottom = chart.y1();
		if (left < 0 || top < 0 || right > guiWidth || bottom > guiHeight) return null;
		ScreenRectangle scissor = chart.scissorArea();
		if (scissor != null && (scissor.left() < 0 || scissor.top() < 0
			|| scissor.width() < 0 || scissor.height() < 0
			|| scissor.left() > guiWidth || scissor.top() > guiHeight
			|| scissor.width() > guiWidth - scissor.left()
			|| scissor.height() > guiHeight - scissor.top())) {
			return null;
		}
		float centerX = (left + right) * 0.5F;
		float centerY = (top + bottom) * 0.5F - 5.0F;
		float radius = Math.min(105.0F, Math.min((right - left) * 0.5F, (bottom - top)));
		if (!(radius > 0.0F) || !Float.isFinite(radius)) return null;
		List<VulkanicGalBridge.GuiMeshVertexRecord> vertices = new ArrayList<>();
		List<Integer> indices = new ArrayList<>();
		double cumulative = 0.0;
		for (ResultField field : chart.chartData()) {
			if (field == null || !Double.isFinite(field.percentage) || field.percentage <= 0.0) return null;
			int slices = Mth.floor(field.percentage / 4.0) + 1;
			if (slices > 128) return null;
			int color = ARGB.opaque(field.getColor());
			int darkColor = ARGB.multiply(color, -8355712);
			for (int slice = slices; slice > 0; slice--) {
				float a0 = (float)((cumulative + field.percentage * slice / slices) * (Math.PI * 2.0) / 100.0);
				float a1 = (float)((cumulative + field.percentage * (slice - 1) / slices) * (Math.PI * 2.0) / 100.0);
				float x0 = centerX + Mth.sin(a0) * radius, y0 = centerY + Mth.cos(a0) * radius * 0.5F;
				float x1 = centerX + Mth.sin(a1) * radius, y1 = centerY + Mth.cos(a1) * radius * 0.5F;
				addProfilerTriangle(vertices, indices, centerX, centerY, x0, y0, x1, y1, color, left, top);
				if ((y0 + y1) * 0.5F - centerY < 0.0F) addProfilerBar(vertices, indices, x0, y0, x1, y1, darkColor, left, top);
			}
			cumulative += field.percentage;
		}
		if (vertices.isEmpty() || indices.isEmpty()) return null;
		int requestLayerOrder = dynamicLayerOrder(sourceLayerOrder);
		VulkanicGalBridge.GuiMeshBatchRecord batch = new VulkanicGalBridge.GuiMeshBatchRecord(
			requestLayerOrder, 0, 1, 1, SOLID_WHITE_ASSET_ID, 0L, 0.0F,
			new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
			new float[] {1, 0, 0, 1, left, top}, left, top, right, bottom, guiWidth, guiHeight,
			Math.max(2, right - left + 2), Math.max(2, bottom - top + 2), 1,
			scissor == null ? 0 : 1,
			scissor == null ? 0 : scissor.left(),
			scissor == null ? 0 : scissor.top(),
			scissor == null ? 0 : scissor.width(),
			scissor == null ? 0 : scissor.height(), vertices, indices);
		long startedNanos = System.nanoTime();
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
			List.of(batch), dynamicLayerId(sourceLayerOrder), requestLayerOrder, startedNanos);
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA));
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_RECTANGLES,
			PROFILER_CHART_PRODUCER, -1, -1.0F, GuiFillDirection.NONE,
			left, top, right - left, bottom - top, guiWidth, guiHeight));
	}

	/** Admits a model-backed GUI picture-in-picture as copied Rust mesh semantics. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueModelPip(
		Model<?> model, ResourceLocation texture, int x0, int y0, int x1, int y1, float scale,
		Matrix3x2f pose, @Nullable ScreenRectangle clip, @Nullable Integer sourceLayerOrder,
		GuiModelPipSemanticCollector.ModelPose setup
	) {
		return tryEnqueueModelPip(model, texture, x0, y0, x1, y1, scale, pose, clip, sourceLayerOrder, setup, 0xffffffff);
	}

	/** Copies a vanilla ModelPart tree through the same bounded Rust mesh ABI. */
	public static List<RustGalGuiElementRenderState> tryEnqueueModelPartPip(
		ModelPart part, ResourceLocation texture, int x0, int y0, int x1, int y1, float scale,
		Matrix3x2f pose, @Nullable ScreenRectangle clip, @Nullable Integer sourceLayerOrder,
		GuiModelPipSemanticCollector.ModelPose setup
	) {
		if (part == null) return List.of();
		return tryEnqueueModelPip(
			new Model.Simple(part, RenderType::entitySolid), texture, x0, y0, x1, y1, scale,
			pose, clip, sourceLayerOrder, setup
		);
	}

	/** Admits one model layer with an explicit semantic tint. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueModelPip(
		Model<?> model, ResourceLocation texture, int x0, int y0, int x1, int y1, float scale,
		Matrix3x2f pose, @Nullable ScreenRectangle clip, @Nullable Integer sourceLayerOrder,
		GuiModelPipSemanticCollector.ModelPose setup, int tint
	) {
		return tryEnqueueModelPip(model, texture, x0, y0, x1, y1, scale, pose, clip, sourceLayerOrder, setup,
			tint, guiModelMaterialMode(model, texture));
	}

	/** Model PIP capture with an explicit Rust GUI material mode (for glint). */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueModelPip(
		Model<?> model, ResourceLocation texture, int x0, int y0, int x1, int y1, float scale,
		Matrix3x2f pose, @Nullable ScreenRectangle clip, @Nullable Integer sourceLayerOrder,
		GuiModelPipSemanticCollector.ModelPose setup, int tint, int materialMode
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME) return null;
		if (materialMode < 1 || materialMode > 4) return null;
		int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		if (model == null || texture == null || pose == null || setup == null
			|| x1 <= x0 || y1 <= y0 || !Float.isFinite(scale) || scale <= 0.0F) {
			return null;
		}
		GuiModelPipSemanticCollector.Result result;
		try {
			result = GuiModelPipSemanticCollector.collect(model, texture, x0, y0, x1, y1, scale,
				Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()), guiWidth, guiHeight,
				new float[] {pose.m00(), pose.m01(), pose.m10(), pose.m11(), pose.m20(), pose.m21()}, clip, setup, tint, materialMode);
		} catch (RuntimeException error) {
			recordTextRouteDiagnostic("model-pip-rejected=" + model.getClass().getName());
			return null;
		}
		if (result == null) return null;
		int layerOrder = sourceLayerOrder == null ? GuiRenderStratum.GUI_ITEM.order() : dynamicLayerOrder(sourceLayerOrder);
		VulkanicGalBridge.GuiMeshBatchRecord batch = result.batch();
		batch = new VulkanicGalBridge.GuiMeshBatchRecord(layerOrder, batch.layerIndex(), batch.materialMode(), batch.lightingMode(),
			batch.assetId(), batch.sequence(), batch.alphaCutoff(), batch.modelTransform(), batch.guiPose(),
			batch.left(), batch.top(), batch.right(), batch.bottom(), batch.guiWidth(), batch.guiHeight(),
			batch.renderWidth(), batch.renderHeight(), batch.guardPixels(), batch.clipMode(), batch.clipLeft(), batch.clipTop(), batch.clipWidth(), batch.clipHeight(), batch.vertices(), batch.indices());
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
			List.of(batch), sourceLayerOrder == null ? GuiRenderStratum.GUI_ITEM.id() : dynamicLayerId(sourceLayerOrder),
			layerOrder, System.nanoTime());
		for (RustGalGuiRawImageAssets.Asset asset : result.assets()) {
			RustGalGuiRawImageAssets.stage(asset);
		}
		ScreenRectangle bounds = result.bounds();
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_ITEM,
			"minecraft.gui.model-pip", -1, -1.0F, GuiFillDirection.NONE,
			bounds.left(), bounds.top(), bounds.width(), bounds.height(), guiWidth, guiHeight));
	}

	private static int guiModelMaterialMode(Model<?> model, ResourceLocation texture) {
		if (model == null || texture == null) return -1;
		try {
			RenderType renderType = model.renderType(texture);
			if (renderType == null) return -1;
			String name = renderType.toString().toLowerCase(java.util.Locale.ROOT);
			if (name.contains("cutout")) return 2;
			var blend = renderType.pipeline().getBlendFunction();
			if (blend.isEmpty()) return 1;
			return BlendFunction.TRANSLUCENT.equals(blend.get()) ? 3 : -1;
		} catch (RuntimeException ignored) {
			return -1;
		}
	}

	/** Copies banner base and pattern layers into one explicit Rust GUI mesh submission. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueBannerPip(
		GuiBannerResultRenderState banner, @Nullable Integer sourceLayerOrder
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME || banner == null) return null;
		int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		List<VulkanicGalBridge.GuiMeshBatchRecord> batches = new ArrayList<>();
		List<RustGalGuiRawImageAssets.Asset> assets = new ArrayList<>();
		ResourceLocation baseTexture = net.minecraft.client.resources.model.ModelBakery.BANNER_BASE.texture();
		int patternCount = banner.resultBannerPatterns().layers().size();
		if (patternCount > 16) {
			recordTextRouteDiagnostic("banner-pip-rejected=pattern-cap-" + patternCount);
			return null;
		}
		// Resolve every layer's copied texture before capturing or staging any
		// model. This keeps predictable missing-resource failures atomic across
		// the complete banner preview.
		if (RustGalGuiRawImageAssets.resolve(baseTexture) == null) return null;
		try {
			for (net.minecraft.world.level.block.entity.BannerPatternLayers.Layer layer : banner.resultBannerPatterns().layers()) {
				if (RustGalGuiRawImageAssets.resolve(net.minecraft.client.renderer.Sheets.getBannerMaterial(layer.pattern()).texture()) == null) {
					return null;
				}
			}
		} catch (RuntimeException error) {
			recordTextRouteDiagnostic("banner-pip-rejected=texture-preflight");
			return null;
		}
		GuiModelPipSemanticCollector.ModelPose setup = pose -> pose.translate(0.0F, 0.25F, 0.0F);
		GuiModelPipSemanticCollector.Result base;
		try {
			base = GuiModelPipSemanticCollector.collect(
				banner.flag(), baseTexture, banner.x0(), banner.y0(), banner.x1(), banner.y1(), banner.scale(), guiScale,
				guiWidth, guiHeight, new float[] {1,0,0,1,0,0}, banner.scissorArea(), setup,
				banner.baseColor().getTextureDiffuseColor());
		} catch (RuntimeException error) {
			recordTextRouteDiagnostic("banner-pip-rejected=base");
			return null;
		}
		if (base == null) return null;
		batches.add(base.batch());
		assets.addAll(base.assets());
		for (int index = 0; index < patternCount; index++) {
			net.minecraft.world.level.block.entity.BannerPatternLayers.Layer layer = banner.resultBannerPatterns().layers().get(index);
			GuiModelPipSemanticCollector.Result pattern;
			try {
				pattern = GuiModelPipSemanticCollector.collect(
					banner.flag(), net.minecraft.client.renderer.Sheets.getBannerMaterial(layer.pattern()).texture(),
					banner.x0(), banner.y0(), banner.x1(), banner.y1(), banner.scale(), guiScale, guiWidth, guiHeight,
					new float[] {1,0,0,1,0,0}, banner.scissorArea(), setup, layer.color().getTextureDiffuseColor());
			} catch (RuntimeException error) {
				recordTextRouteDiagnostic("banner-pip-rejected=pattern-" + index);
				return null;
			}
			if (pattern == null) return null;
			batches.add(pattern.batch());
			assets.addAll(pattern.assets());
		}
		int order = sourceLayerOrder == null ? GuiRenderStratum.GUI_ITEM.order() : dynamicLayerOrder(sourceLayerOrder);
		List<VulkanicGalBridge.GuiMeshBatchRecord> ordered = new ArrayList<>(batches.size());
		for (int index = 0; index < batches.size(); index++) {
			VulkanicGalBridge.GuiMeshBatchRecord batch = batches.get(index);
			ordered.add(new VulkanicGalBridge.GuiMeshBatchRecord(order, index, 1, 1, batch.assetId(), 0L, 0.0F,
				batch.modelTransform(), batch.guiPose(), batch.left(), batch.top(), batch.right(), batch.bottom(),
				batch.guiWidth(), batch.guiHeight(), batch.renderWidth(), batch.renderHeight(), batch.guardPixels(), batch.clipMode(), batch.clipLeft(), batch.clipTop(), batch.clipWidth(), batch.clipHeight(), batch.vertices(), batch.indices()));
		}
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(ordered,
			sourceLayerOrder == null ? GuiRenderStratum.GUI_ITEM.id() : dynamicLayerId(sourceLayerOrder), order, System.nanoTime());
		for (RustGalGuiRawImageAssets.Asset asset : assets) RustGalGuiRawImageAssets.stage(asset);
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_ITEM, "minecraft.gui.banner", -1, -1.0F,
			GuiFillDirection.NONE, banner.x0(), banner.y0(), banner.x1() - banner.x0(), banner.y1() - banner.y0(), guiWidth, guiHeight));
	}

	/** Admits living-entity GUI previews through copied model/material semantics. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueEntityPip(GuiEntityRenderState entityPip, @Nullable Integer sourceLayerOrder) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME || entityPip == null) return null;
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		var renderer = dispatcher.getRenderer(entityPip.renderState());
		if (!(renderer instanceof LivingEntityRenderer<?, ?, ?>)
			|| !(entityPip.renderState() instanceof LivingEntityRenderState)) return null;
		@SuppressWarnings("rawtypes") LivingEntityRenderer living = (LivingEntityRenderer) renderer;
		ResourceLocation texture;
		try {
			texture = living.getTextureLocation((net.minecraft.client.renderer.entity.state.LivingEntityRenderState) entityPip.renderState());
		} catch (RuntimeException error) {
			return null;
		}
		if (texture == null) return null;
		GuiModelPipSemanticCollector.ModelPose setup = pose -> {
			var translation = entityPip.translation();
			pose.translate(translation.x, translation.y, translation.z);
			pose.mulPose(entityPip.rotation());
			if (entityPip.overrideCameraAngle() != null) pose.mulPose(entityPip.overrideCameraAngle());
			living.applySemanticModelPose((net.minecraft.client.renderer.entity.state.LivingEntityRenderState) entityPip.renderState(), pose);
		};
		// Vanilla entity PIPs also submit renderer layers (armor, capes, eyes,
		// markings, and other semantic model layers). Capture only direct
		// resource-identity model submissions; item/model-part/custom callbacks
		// remain unavailable as one coherent preview rather than producing a
		// partial Rust image or leaking into the world collector.
		@SuppressWarnings("unchecked") LivingEntityRenderState livingState =
			(LivingEntityRenderState) entityPip.renderState();
		EntityPipLayerCapture layerCapture = new EntityPipLayerCapture();
		PoseStack layerPose = new PoseStack();
		try {
			for (Object rawLayer : living.layers) {
				@SuppressWarnings("rawtypes") RenderLayer layer = (RenderLayer) rawLayer;
				layer.submit(layerPose, layerCapture, 15728880, livingState, 0.0F, 0.0F);
			}
		} catch (RuntimeException error) {
			return null;
		}
		if (layerCapture.unsupported || layerCapture.models.size() > 32 || layerCapture.items.size() > 32) return null;
		layerCapture.models.sort(java.util.Comparator.comparingInt(EntityPipLayerModel::layerOrder));
		layerCapture.items.sort(java.util.Comparator.comparingInt(EntityPipLayerItem::layerOrder));
		// Validate/copy every layer before publishing any scheduler token. This
		// keeps a rejected preview atomic: no base mesh can remain queued after a
		// layer declines its explicit contract.
		List<EntityPipLayerResult> layerResults = new ArrayList<>(layerCapture.models.size() + layerCapture.items.size());
		for (EntityPipLayerModel layerModel : layerCapture.models) {
			Matrix4f relativePose = new Matrix4f(layerModel.pose());
			GuiModelPipSemanticCollector.Result result;
			try {
				result = layerModel.animated()
					? GuiModelPipSemanticCollector.collectAnimated(layerModel.model(), layerModel.texture(),
						entityPip.x0(), entityPip.y0(), entityPip.x1(), entityPip.y1(), entityPip.scale(),
						Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()),
						Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight(),
						new float[] {1, 0, 0, 1, 0, 0}, entityPip.scissorArea(), pose -> {
							setup.apply(pose);
							pose.last().pose().mul(relativePose);
						}, 0xffffffff, layerModel.materialMode(), layerModel.uvOffsetU(), layerModel.uvOffsetV(),
						layerModel.textureWidth(), layerModel.textureHeight())
					: GuiModelPipSemanticCollector.collect(layerModel.model(), layerModel.texture(),
					entityPip.x0(), entityPip.y0(), entityPip.x1(), entityPip.y1(), entityPip.scale(),
					Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()),
					Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight(),
					new float[] {1, 0, 0, 1, 0, 0}, entityPip.scissorArea(), pose -> {
						setup.apply(pose);
						pose.last().pose().mul(relativePose);
					}, 0xffffffff, layerModel.materialMode());
			} catch (RuntimeException error) {
				return null;
			}
			if (result == null) return null;
			layerResults.add(new EntityPipLayerResult(result, layerModel.layerOrder()));
		}
		for (EntityPipLayerItem layerItem : layerCapture.items) {
			List<GuiModelPipSemanticCollector.Result> itemResults;
			try {
				itemResults = GuiModelPipSemanticCollector.collectBakedQuads(layerItem.quads(), layerItem.tintLayers(),
					layerItem.pose(), entityPip.x0(), entityPip.y0(), entityPip.x1(), entityPip.y1(), entityPip.scale(),
					Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()),
					Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight(),
					new float[] {1, 0, 0, 1, 0, 0}, entityPip.scissorArea(), layerItem.materialMode(), layerItem.foilType());
			} catch (RuntimeException error) {
				return null;
			}
			if (itemResults.isEmpty()) return null;
			for (GuiModelPipSemanticCollector.Result itemResult : itemResults) {
				layerResults.add(new EntityPipLayerResult(itemResult, layerItem.layerOrder()));
			}
		}
		layerResults.sort(java.util.Comparator.comparingInt(EntityPipLayerResult::layerOrder));
		GuiModelPipSemanticCollector.Result baseResult;
		try {
			baseResult = GuiModelPipSemanticCollector.collect(living.getModel(), texture,
				entityPip.x0(), entityPip.y0(), entityPip.x1(), entityPip.y1(), entityPip.scale(),
				Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()),
				Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight(),
				new float[] {1, 0, 0, 1, 0, 0}, entityPip.scissorArea(), setup, 0xffffffff, 1);
		} catch (RuntimeException error) {
			return null;
		}
		if (baseResult == null) return null;
		// All captures are now validated. Publish them in vanilla base-then-layer
		// order through the existing explicit mesh enqueue path.
		List<GuiModelPipSemanticCollector.Result> allResults = new ArrayList<>(layerResults.size() + 1);
		allResults.add(baseResult);
		for (EntityPipLayerResult layerResult : layerResults) allResults.add(layerResult.result());
		List<RustGalGuiElementRenderState> elements = new ArrayList<>(allResults.size());
		for (GuiModelPipSemanticCollector.Result result : allResults) {
			int layerOrder = sourceLayerOrder == null ? GuiRenderStratum.GUI_ITEM.order() : dynamicLayerOrder(sourceLayerOrder);
			VulkanicGalBridge.GuiMeshBatchRecord batch = result.batch();
			batch = new VulkanicGalBridge.GuiMeshBatchRecord(layerOrder, batch.layerIndex(), batch.materialMode(), batch.lightingMode(),
				batch.assetId(), batch.sequence(), batch.alphaCutoff(), batch.modelTransform(), batch.guiPose(),
				batch.left(), batch.top(), batch.right(), batch.bottom(), batch.guiWidth(), batch.guiHeight(),
				batch.renderWidth(), batch.renderHeight(), batch.guardPixels(), batch.clipMode(), batch.clipLeft(), batch.clipTop(),
				batch.clipWidth(), batch.clipHeight(), batch.vertices(), batch.indices());
			RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
				List.of(batch), sourceLayerOrder == null ? GuiRenderStratum.GUI_ITEM.id() : dynamicLayerId(sourceLayerOrder),
				layerOrder, System.nanoTime());
			for (RustGalGuiRawImageAssets.Asset asset : result.assets()) RustGalGuiRawImageAssets.stage(asset);
			ScreenRectangle bounds = result.bounds();
			elements.add(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_ITEM, "minecraft.gui.model-pip", -1, -1.0F,
				GuiFillDirection.NONE, bounds.left(), bounds.top(), bounds.width(), bounds.height(),
				Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight()));
		}
		return List.copyOf(elements);
	}

	private record EntityPipLayerModel(Model<?> model, ResourceLocation texture, Matrix4f pose, int layerOrder, int materialMode,
		boolean animated, float uvOffsetU, float uvOffsetV, int textureWidth, int textureHeight) {
		EntityPipLayerModel(Model<?> model, ResourceLocation texture, Matrix4f pose, int layerOrder, int materialMode) {
			this(model, texture, pose, layerOrder, materialMode, false, 0.0F, 0.0F, 1, 1);
		}
	}
	private record EntityPipLayerItem(List<net.minecraft.client.renderer.block.model.BakedQuad> quads,
		int[] tintLayers, Matrix4f pose, int layerOrder, int materialMode, ItemStackRenderState.FoilType foilType) {
	}
	private static final int MAX_ENTITY_PIP_ITEM_QUADS = 1_024;
	private record EntityPipLayerResult(GuiModelPipSemanticCollector.Result result, int layerOrder) {
	}

	/** Strict collector used only while extracting one GUI entity PIP. */
	private static final class EntityPipLayerCapture extends SubmitNodeCollection implements SubmitNodeCollector {
		private final List<EntityPipLayerModel> models = new ArrayList<>();
		private final List<EntityPipLayerItem> items = new ArrayList<>();
		private int itemQuadCount;
		private boolean unsupported;

		private EntityPipLayerCapture() {
			super(null);
		}

		@Override
		public net.minecraft.client.renderer.OrderedSubmitNodeCollector order(int ignored) {
			return this;
		}

		@Override
		public <S> void submitModelSemanticTexture(Model<? super S> model, S object, PoseStack poseStack,
			RenderType renderType, int light, int overlay, int order, ResourceLocation textureIdentity,
			int outlineColor, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
			int materialMode = materialMode(renderType);
			if (model == null || textureIdentity == null || materialMode < 0
				|| crumblingOverlay != null || models.size() >= 32) {
				unsupported = true;
				return;
			}
			models.add(new EntityPipLayerModel(model, textureIdentity, new Matrix4f(poseStack.last().pose()), order, materialMode));
		}

		@Override
		public <S> void submitModel(Model<? super S> model, S object, PoseStack poseStack, RenderType renderType,
			int light, int overlay, int order, @Nullable TextureAtlasSprite sprite, int outlineColor,
			@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
			// Armor trims, wool overlays, and similar layers submit an atlas sprite
			// rather than a standalone resource identity. The copied atlas is an
			// explicit Rust image asset, so these layers can share the same bounded
			// GUI mesh path. Glint and crumbling variants retain distinct contracts.
			int materialMode = materialMode(renderType);
			if (model == null || sprite == null || sprite.atlasLocation() == null || materialMode < 0
				|| crumblingOverlay != null || (renderType != null && renderType.toString().contains("glint"))
				|| models.size() >= 32) {
				unsupported = true;
				return;
			}
			models.add(new EntityPipLayerModel(model, sprite.atlasLocation(), new Matrix4f(poseStack.last().pose()), order, materialMode));
		}

		@Override
		public <S> void submitAnimatedModelSemanticTexture(Model<? super S> model, S object, PoseStack poseStack,
			RenderType renderType, int light, int overlay, int order, ResourceLocation textureIdentity,
			int outlineColor, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
			float uvOffsetU, float uvOffsetV, int textureWidth, int textureHeight) {
			if (model == null || textureIdentity == null || materialMode(renderType) < 0
				|| crumblingOverlay != null || models.size() >= 32
				|| textureWidth <= 0 || textureHeight <= 0
				|| !Float.isFinite(uvOffsetU) || !Float.isFinite(uvOffsetV)) {
				unsupported = true;
				return;
			}
			models.add(new EntityPipLayerModel(model, textureIdentity, new Matrix4f(poseStack.last().pose()), order,
				materialMode(renderType), true, uvOffsetU, uvOffsetV, textureWidth, textureHeight));
		}

		@Override
		public void submitModelPart(net.minecraft.client.model.geom.ModelPart modelPart, PoseStack poseStack,
			RenderType renderType, int light, int overlay, @Nullable TextureAtlasSprite sprite, boolean affectsCrumbling,
			boolean bl, int order, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, int outlineColor) {
			int materialMode = materialMode(renderType);
			if (modelPart == null || sprite == null || sprite.atlasLocation() == null || materialMode < 0
				|| affectsCrumbling || bl || crumblingOverlay != null || models.size() >= 32) {
				unsupported = true;
				return;
			}
			models.add(new EntityPipLayerModel(
				new Model.Simple(modelPart, RenderType::entitySolid), sprite.atlasLocation(),
				new Matrix4f(poseStack.last().pose()), order, materialMode));
		}

		/* Keep GUI capture isolated from SubmitNodeCollection's world storage. */
		@Override
		public void submitBlock(PoseStack poseStack, net.minecraft.world.level.block.state.BlockState blockState,
			int light, int overlay, int outlineColor) {
			unsupported = true;
		}

		@Override
		public void submitBlockDisplay(PoseStack poseStack, net.minecraft.world.level.block.state.BlockState blockState,
			int light, int overlay, int outlineColor) {
			unsupported = true;
		}

		@Override
		public void submitBlockModel(PoseStack poseStack, RenderType renderType,
			net.minecraft.client.renderer.block.model.BlockStateModel blockStateModel, float red, float green,
			float blue, int light, int overlay, int outlineColor) {
			unsupported = true;
		}

		@Override
		public void submitMovingBlock(PoseStack poseStack,
			net.minecraft.client.renderer.block.MovingBlockRenderState movingBlockRenderState) {
			unsupported = true;
		}

		@Override
		public void submitHitbox(PoseStack poseStack, EntityRenderState entityRenderState,
			net.minecraft.client.renderer.entity.state.HitboxesRenderState hitboxesRenderState) {
			unsupported = true;
		}

		@Override
		public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
			unsupported = true;
		}

		@Override
		public void submitNameTag(PoseStack poseStack, @Nullable net.minecraft.world.phys.Vec3 offset,
			int packedLight, net.minecraft.network.chat.Component text, boolean seeThrough, int width,
			double distance, net.minecraft.client.renderer.state.CameraRenderState cameraRenderState) {
			unsupported = true;
		}

		@Override
		public void submitText(PoseStack poseStack, float x, float y,
			net.minecraft.util.FormattedCharSequence text, boolean shadow,
			Font.DisplayMode mode, int color, int backgroundColor, int packedLight, int packedOverlay) {
			unsupported = true;
		}

		@Override
		public void submitFlame(PoseStack poseStack, EntityRenderState entityRenderState,
			org.joml.Quaternionf rotation) {
			unsupported = true;
		}

		@Override
		public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
			unsupported = true;
		}

		@Override
		public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
			unsupported = true;
		}

		@Override
		public void submitItem(PoseStack poseStack, net.minecraft.world.item.ItemDisplayContext displayContext,
			int light, int overlay, int order, int[] tintedColors, List<net.minecraft.client.renderer.block.model.BakedQuad> quads,
			RenderType renderType, ItemStackRenderState.FoilType foilType) {
			int materialMode = materialMode(renderType);
			if (displayContext == null || tintedColors == null || quads == null || quads.isEmpty()
				|| renderType == null || materialMode < 0 || foilType == null
				|| items.size() >= 32 || quads.size() > 256
				|| itemQuadCount > MAX_ENTITY_PIP_ITEM_QUADS - quads.size()) {
				unsupported = true;
				return;
			}
			itemQuadCount += quads.size();
			items.add(new EntityPipLayerItem(List.copyOf(quads), tintedColors.clone(),
				new Matrix4f(poseStack.last().pose()), order, materialMode, foilType));
		}

		@Override
		public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
			SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
			unsupported = true;
		}

		private static int materialMode(RenderType renderType) {
			if (renderType == null) return -1;
			var blend = renderType.pipeline().getBlendFunction();
			String name = renderType.toString().toLowerCase(java.util.Locale.ROOT);
			if (name.contains("cutout")) return 2;
			if (blend.isEmpty()) return 1;
			return BlendFunction.TRANSLUCENT.equals(blend.get()) ? 3 : -1;
		}
	}

	private static void addProfilerTriangle(List<VulkanicGalBridge.GuiMeshVertexRecord> vertices, List<Integer> indices,
		float cx, float cy, float x0, float y0, float x1, float y1, int color, int left, int top) {
		int base = vertices.size();
		vertices.add(profilerVertex(cx, cy, color, left, top));
		vertices.add(profilerVertex(x0, y0, color, left, top));
		vertices.add(profilerVertex(x1, y1, color, left, top));
		indices.add(base); indices.add(base + 1); indices.add(base + 2);
	}

	private static void addProfilerBar(List<VulkanicGalBridge.GuiMeshVertexRecord> vertices, List<Integer> indices,
		float x0, float y0, float x1, float y1, int color, int left, int top) {
		int base = vertices.size();
		vertices.add(profilerVertex(x0, y0, color, left, top));
		vertices.add(profilerVertex(x0, y0 + 10.0F, color, left, top));
		vertices.add(profilerVertex(x1, y1 + 10.0F, color, left, top));
		vertices.add(profilerVertex(x1, y1, color, left, top));
		indices.add(base); indices.add(base + 1); indices.add(base + 2);
		indices.add(base); indices.add(base + 2); indices.add(base + 3);
	}

	private static VulkanicGalBridge.GuiMeshVertexRecord profilerVertex(float x, float y, int color, int left, int top) {
		return new VulkanicGalBridge.GuiMeshVertexRecord(new float[] {x - left, y - top, 0.0F},
			new float[] {0, 0}, new float[] {0, 0}, color, 0x007F0000);
	}

	static int dynamicLayerOrder(int sourceLayerOrder) {
		if (sourceLayerOrder < 0) {
			throw new IllegalArgumentException("negative semantic GUI source layer");
		}
		return Math.addExact(WHOLE_FRAME_DYNAMIC_LAYER_BASE, sourceLayerOrder);
	}

	static String dynamicLayerId(int sourceLayerOrder) {
		return "gui.semantic.layer." + sourceLayerOrder;
	}

	/**
	 * Converts an ordinary, one-texture GUI blit backed by either a copied PNG or
	 * a copied first-frame stitched atlas into an owned affine image primitive.
	 * Multi-texture and nonstandard blend-pipeline blits stay unavailable until
	 * their distinct contracts exist. Tiled blits use the separate explicit
	 * wrapped-UV path below; mirrored affine quads are normalized here without
	 * borrowing Java texture state.
	 * The first-person block/fire screen effects are ordinary single-sampler
	 * alpha blits as well; admitting them here keeps those overlays on the same
	 * copied-image route instead of treating them as unsupported GUI elements.
	 */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueCopiedBlit(
		BlitRenderState blit, int guiWidth, int guiHeight, int dynamicLayerOrder
	) {
		if (blit == null || blit.pose() == null || !finiteAffinePose(blit.pose())
			|| currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
			|| (blit.pipeline() != RenderPipelines.GUI_TEXTURED
				&& blit.pipeline() != RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND
				&& blit.pipeline() != RenderPipelines.BLOCK_SCREEN_EFFECT
				&& blit.pipeline() != RenderPipelines.FIRE_SCREEN_EFFECT
				&& blit.pipeline() != RenderPipelines.VIGNETTE
				&& blit.pipeline() != RenderPipelines.CROSSHAIR
				&& blit.pipeline() != RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA
				&& blit.pipeline() != RenderPipelines.GUI_NAUSEA_OVERLAY
				&& blit.pipeline() != RenderPipelines.MOJANG_LOGO
				&& blit.pipeline() != net.voxelmap.util.VoxelMapPipelines.GUI_TEXTURED_LESS_OR_EQUAL_DEPTH_PIPELINE)
			|| blit.semanticTexture() == null
			|| !semanticSingleTexture(blit.textureSetup(), blit.semanticTexture())) {
			return null;
		}
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolveAtlas(blit.semanticTexture());
		if (asset == null) {
			asset = RustGalGuiRawImageAssets.resolve(blit.semanticTexture());
		}
		if (asset == null) {
			return null;
		}
		Matrix3x2f pose = blit.pose();
		float x0 = pose.m00() * blit.x0() + pose.m10() * blit.y0() + pose.m20();
		float y0 = pose.m01() * blit.x0() + pose.m11() * blit.y0() + pose.m21();
		float x1 = pose.m00() * blit.x1() + pose.m10() * blit.y0() + pose.m20();
		float y1 = pose.m01() * blit.x1() + pose.m11() * blit.y0() + pose.m21();
		float x3 = pose.m00() * blit.x0() + pose.m10() * blit.y1() + pose.m20();
		float y3 = pose.m01() * blit.x0() + pose.m11() * blit.y1() + pose.m21();
		float u0 = blit.u0();
		float u1 = blit.u1();
		float v0 = blit.v0();
		float v1 = blit.v1();
		if (u1 < u0) {
			float x2 = x1 + x3 - x0;
			float y2 = y1 + y3 - y0;
			float previousX0 = x0;
			float previousY0 = y0;
			x0 = x1;
			y0 = y1;
			x1 = previousX0;
			y1 = previousY0;
			x3 = x2;
			y3 = y2;
			float previousU0 = u0;
			u0 = u1;
			u1 = previousU0;
		}
		if (v1 < v0) {
			float x2 = x1 + x3 - x0;
			float y2 = y1 + y3 - y0;
			float previousX0 = x0;
			float previousY0 = y0;
			x0 = x3;
			y0 = y3;
			x1 = x2;
			y1 = y2;
			x3 = previousX0;
			y3 = previousY0;
			float previousV0 = v0;
			v0 = v1;
			v1 = previousV0;
		}
		float x2 = x1 + x3 - x0;
		float y2 = y1 + y3 - y0;
		int requestLayerOrder = dynamicLayerOrder(dynamicLayerOrder);
		if (blit.pipeline() == RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND) {
			requestLayerOrder = GuiRenderStratum.GUI_OPAQUE_BLIT.order();
		} else if (blit.pipeline() == RenderPipelines.VIGNETTE) {
			requestLayerOrder = GuiRenderStratum.GUI_VIGNETTE_BLIT.order();
		} else if (blit.pipeline() == net.voxelmap.util.VoxelMapPipelines.GUI_TEXTURED_LESS_OR_EQUAL_DEPTH_PIPELINE) {
			requestLayerOrder = GuiRenderStratum.GUI_LEQUAL_DEPTH_BLIT.order();
		}
		boolean invertBlend = blit.pipeline() == RenderPipelines.CROSSHAIR;
		boolean premultipliedBlend = blit.pipeline() == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA;
		boolean additiveBlend = blit.pipeline() == RenderPipelines.GUI_NAUSEA_OVERLAY;
		int semanticStratum = invertBlend ? GuiRenderStratum.GUI_CROSSHAIR.order()
			: (premultipliedBlend ? GuiRenderStratum.GUI_PREMULTIPLIED_BLIT.order()
				: (additiveBlend ? GuiRenderStratum.GUI_ADDITIVE_BLIT.order() : requestLayerOrder));
		if (u0 < -GUI_UV_OVERLAP_LIMIT || u1 > 1.0F + GUI_UV_OVERLAP_LIMIT
			|| v0 < -GUI_UV_OVERLAP_LIMIT || v1 > 1.0F + GUI_UV_OVERLAP_LIMIT) {
			// Vanilla's regular BlitRenderState is also used for repeated separator
			// and list-background images. Preserve those repeated UV semantics by
			// splitting them into bounded unit-UV affine quads; do not inherit a
			// Java sampler wrap mode or reject valid title/menu geometry.
			return enqueueWrappedAffineAsset(asset, semanticStratum, requestLayerOrder, x0, y0, x1, y1, x3, y3,
				u0, v0, u1, v1, blit.color(), guiWidth, guiHeight, dynamicLayerOrder, blit.scissorArea());
		}
		if (!admissibleAffineQuad(requestLayerOrder, asset.assetId(), x0, y0, x1, y1, x3, y3,
			u0, v0, u1, v1, guiWidth, guiHeight, blit.scissorArea())) {
			// Do not coerce UVs, clips, or non-finite coordinates into a different
			// image. This producer is unavailable until it can provide the explicit
			// affine contract that the Rust backend consumes.
			recordTextRouteDiagnostic("copied-blit-outside-affine-contract");
			return null;
		}
		VulkanicGalBridge.GuiAffineQuadRecord request = new VulkanicGalBridge.GuiAffineQuadRecord(
			semanticStratum,
			asset.assetId(), x0, y0, x1, y1, x3, y3,
			0.0F, u0, v0, u1, v1, blit.color(), guiWidth, guiHeight
		);
		if (blit.scissorArea() != null) {
			ScreenRectangle scissor = blit.scissorArea();
			request = request.withClip(scissor.left(), scissor.top(), scissor.width(), scissor.height());
		}
		long startedNanos = System.nanoTime();
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiAffineQuadRequest(
			request, dynamicLayerId(dynamicLayerOrder), requestLayerOrder, startedNanos
		);
		RustGalGuiRawImageAssets.stage(asset);
		int left = (int)Math.floor(Math.min(Math.min(x0, x1), Math.min(x2, x3)));
		int top = (int)Math.floor(Math.min(Math.min(y0, y1), Math.min(y2, y3)));
		int width = Math.max(1, (int)Math.ceil(Math.max(Math.max(x0, x1), Math.max(x2, x3)) - left));
		int height = Math.max(1, (int)Math.ceil(Math.max(Math.max(y0, y1), Math.max(y2, y3)) - top));
		return List.of(new RustGalGuiElementRenderState(
			token, GuiRenderStratum.GUI_FILE_BACKED_BLIT, "minecraft.gui.file-backed-blit", -1, -1.0F, GuiFillDirection.NONE,
			left, top, width, height, guiWidth, guiHeight
		));
	}

	/**
	 * Converts one repeated affine image into independently bounded unit-UV
	 * quads. This is semantic geometry expansion: Rust still owns the image,
	 * pipeline, synchronization, and draw execution.
	 */
	@Nullable
	private static List<RustGalGuiElementRenderState> enqueueWrappedAffineAsset(
		RustGalGuiRawImageAssets.Asset asset, int stratum, int semanticLayerOrder,
		float x0, float y0, float x1, float y1, float x3, float y3,
		float u0, float v0, float u1, float v1, int color, int guiWidth, int guiHeight, int dynamicLayerOrder,
		@Nullable ScreenRectangle clip
	) {
		float uSpan = u1 - u0;
		float vSpan = v1 - v0;
		if (asset == null || stratum < 0 || semanticLayerOrder < 0 || dynamicLayerOrder < 0
			|| !Float.isFinite(uSpan) || !Float.isFinite(vSpan) || uSpan <= 0.0F || vSpan <= 0.0F
			|| uSpan > 4096.0F || vSpan > 4096.0F) return null;
		List<float[]> uSegments = wrappedUnitIntervalSegments(u0, uSpan);
		List<float[]> vSegments = wrappedUnitIntervalSegments(v0, vSpan);
		if (uSegments.isEmpty() || vSegments.isEmpty()
			|| (long)uSegments.size() * vSegments.size() > MAX_GUI_TILED_SEGMENTS) return null;
		float axisXx = x1 - x0;
		float axisXy = y1 - y0;
		float axisYx = x3 - x0;
		float axisYy = y3 - y0;
		// Fully preflight all expanded records before queueing or staging anything.
		for (float[] u : uSegments) for (float[] v : vSegments) {
			float[] corners = wrappedAffineCorners(x0, y0, axisXx, axisXy, axisYx, axisYy, u, v);
			if (!admissibleAffineQuad(stratum, asset.assetId(), corners[0], corners[1], corners[2], corners[3], corners[4], corners[5],
				u[2], v[2], u[3], v[3], guiWidth, guiHeight, clip)) return null;
		}
		List<VulkanicGalBridge.GuiAffineQuadRecord> requests = new ArrayList<>(uSegments.size() * vSegments.size());
		List<float[]> cornersByRequest = new ArrayList<>(uSegments.size() * vSegments.size());
		for (float[] u : uSegments) for (float[] v : vSegments) {
			float[] corners = wrappedAffineCorners(x0, y0, axisXx, axisXy, axisYx, axisYy, u, v);
			VulkanicGalBridge.GuiAffineQuadRecord request = new VulkanicGalBridge.GuiAffineQuadRecord(
				stratum, asset.assetId(), corners[0], corners[1], corners[2], corners[3], corners[4], corners[5],
				0.0F, u[2], v[2], u[3], v[3], color, guiWidth, guiHeight
			);
			if (clip != null) request = request.withClip(clip.left(), clip.top(), clip.width(), clip.height());
			requests.add(request);
			cornersByRequest.add(corners);
		}
		// One complete repeated image is one semantic operation. Submit it atomically
		// after every expanded quad passed preflight, so it cannot leave a partial
		// prefix in the scheduler if a later quad would be rejected.
		long startedNanos = System.nanoTime();
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiAffineQuadRequests(
			requests, dynamicLayerId(dynamicLayerOrder), semanticLayerOrder, startedNanos
		);
		RustGalGuiRawImageAssets.stage(asset);
		List<RustGalGuiElementRenderState> elements = new ArrayList<>(cornersByRequest.size());
		for (float[] corners : cornersByRequest) {
			float x2 = corners[2] + corners[4] - corners[0];
			float y2 = corners[3] + corners[5] - corners[1];
			int left = (int)Math.floor(Math.min(Math.min(corners[0], corners[2]), Math.min(x2, corners[4])));
			int top = (int)Math.floor(Math.min(Math.min(corners[1], corners[3]), Math.min(y2, corners[5])));
			int width = Math.max(1, (int)Math.ceil(Math.max(Math.max(corners[0], corners[2]), Math.max(x2, corners[4])) - left));
			int height = Math.max(1, (int)Math.ceil(Math.max(Math.max(corners[1], corners[3]), Math.max(y2, corners[5])) - top));
			elements.add(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_FILE_BACKED_BLIT,
				"minecraft.gui.wrapped-blit", -1, -1.0F, GuiFillDirection.NONE, left, top, width, height, guiWidth, guiHeight));
		}
		return List.copyOf(elements);
	}

	private static float[] wrappedAffineCorners(
		float x0, float y0, float axisXx, float axisXy, float axisYx, float axisYy, float[] u, float[] v
	) {
		float left = u[0], right = u[1], top = v[0], bottom = v[1];
		return new float[] {
			x0 + axisXx * left + axisYx * top, y0 + axisXy * left + axisYy * top,
			x0 + axisXx * right + axisYx * top, y0 + axisXy * right + axisYy * top,
			x0 + axisXx * left + axisYx * bottom, y0 + axisXy * left + axisYy * bottom
		};
	}

	/** Bounded diagnostics for a copied blit that was deliberately not admitted. */
	public static String copiedBlitFailureDetail(BlitRenderState blit) {
		if (blit == null) return "null-state";
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME) return "route";
		if (blit.pipeline() != RenderPipelines.GUI_TEXTURED
			&& blit.pipeline() != RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND
			&& blit.pipeline() != RenderPipelines.BLOCK_SCREEN_EFFECT
			&& blit.pipeline() != RenderPipelines.FIRE_SCREEN_EFFECT
			&& blit.pipeline() != RenderPipelines.VIGNETTE
			&& blit.pipeline() != RenderPipelines.CROSSHAIR
			&& blit.pipeline() != RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA
			&& blit.pipeline() != RenderPipelines.GUI_NAUSEA_OVERLAY
			&& blit.pipeline() != RenderPipelines.MOJANG_LOGO
			&& blit.pipeline() != net.voxelmap.util.VoxelMapPipelines.GUI_TEXTURED_LESS_OR_EQUAL_DEPTH_PIPELINE) return "pipeline:" + blit.pipeline();
		if (blit.semanticTexture() == null) return "missing-semantic-texture";
		if (!semanticSingleTexture(blit.textureSetup(), blit.semanticTexture())) return "texture-setup";
		return RustGalGuiRawImageAssets.resolve(blit.semanticTexture()) == null ? "raw-image-missing" : "geometry-validation";
	}

	/**
	 * Admits a producer-owned CPU image as one explicit affine GUI primitive.
	 * The producer supplies geometry and UVs; no Java texture or GPU view crosses
	 * the VulkanicGAL boundary.
	 */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueResourceQuad(
		ResourceLocation source, float x0, float y0, float x1, float y1, float x3, float y3,
		float u0, float v0, float u1, float v1, int color, int guiWidth, int guiHeight, int dynamicLayerOrder
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME || source == null) return null;
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(source);
		if (asset == null) return null;
		return enqueueAffineAsset(asset, x0, y0, x1, y1, x3, y3, u0, v0, u1, v1,
			color, guiWidth, guiHeight, dynamicLayerOrder, null, "resource-quad");
	}

	/** Registers and stages a bounded CPU DynamicTexture before admitting its quad. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueDynamicTextureQuad(
		ResourceLocation identity, DynamicTexture texture, float x0, float y0, float x1, float y1, float x3, float y3,
		float u0, float v0, float u1, float v1, int color, int guiWidth, int guiHeight, int dynamicLayerOrder
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME || identity == null || texture == null) return null;
		RustGalGuiRawImageAssets.registerDynamicTextureUnstaged(identity, texture);
		if (RustGalGuiRawImageAssets.prepareDynamicTexture(texture) == null) return null;
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(identity);
		if (asset == null) return null;
		List<RustGalGuiElementRenderState> result = enqueueAffineAsset(asset, x0, y0, x1, y1, x3, y3,
			u0, v0, u1, v1, color, guiWidth, guiHeight, dynamicLayerOrder, null, "dynamic-quad");
		return result;
	}

	@Nullable
	private static List<RustGalGuiElementRenderState> enqueueAffineAsset(
		RustGalGuiRawImageAssets.Asset asset, float x0, float y0, float x1, float y1, float x3, float y3,
		float u0, float v0, float u1, float v1, int color, int guiWidth, int guiHeight, int dynamicLayerOrder,
		@Nullable ScreenRectangle clip, String producerSuffix
	) {
		if (asset == null || dynamicLayerOrder < 0) return null;
		int requestLayerOrder = dynamicLayerOrder(dynamicLayerOrder);
		if (!admissibleAffineQuad(requestLayerOrder, asset.assetId(), x0, y0, x1, y1, x3, y3,
			u0, v0, u1, v1, guiWidth, guiHeight, clip)) return null;
		long startedNanos = System.nanoTime();
		VulkanicGalBridge.GuiAffineQuadRecord request = new VulkanicGalBridge.GuiAffineQuadRecord(
			requestLayerOrder, asset.assetId(), x0, y0, x1, y1, x3, y3, 0.0F,
			u0, v0, u1, v1, color, guiWidth, guiHeight
		);
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiAffineQuadRequest(
			request, "gui.semantic.layer." + dynamicLayerOrder, requestLayerOrder, startedNanos
		);
		RustGalGuiRawImageAssets.stage(asset);
		int x2 = Math.round(x1 + x3 - x0);
		int y2 = Math.round(y1 + y3 - y0);
		int left = (int)Math.floor(Math.min(Math.min(x0, x1), Math.min(x2, x3)));
		int top = (int)Math.floor(Math.min(Math.min(y0, y1), Math.min(y2, y3)));
		int width = Math.max(1, (int)Math.ceil(Math.max(Math.max(x0, x1), Math.max(x2, x3)) - left));
		int height = Math.max(1, (int)Math.ceil(Math.max(Math.max(y0, y1), Math.max(y2, y3)) - top));
		return List.of(new RustGalGuiElementRenderState(
			token, GuiRenderStratum.GUI_FILE_BACKED_BLIT, "voxelmap.gui." + producerSuffix, -1, -1.0F,
			GuiFillDirection.NONE, left, top, width, height, guiWidth, guiHeight
		));
	}

	private static boolean admissibleAffineQuad(
		int stratum, long assetId, float x0, float y0, float x1, float y1, float x3, float y3,
		float u0, float v0, float u1, float v1, int guiWidth, int guiHeight, @Nullable ScreenRectangle clip
	) {
		if (stratum < 0 || assetId == 0L || guiWidth <= 0 || guiHeight <= 0
			|| !Float.isFinite(x0) || !Float.isFinite(y0) || !Float.isFinite(x1) || !Float.isFinite(y1)
			|| !Float.isFinite(x3) || !Float.isFinite(y3)
			|| !Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)
			|| u0 < -GUI_UV_OVERLAP_LIMIT || u0 > 1.0F + GUI_UV_OVERLAP_LIMIT
			|| v0 < -GUI_UV_OVERLAP_LIMIT || v0 > 1.0F + GUI_UV_OVERLAP_LIMIT
			|| u1 < -GUI_UV_OVERLAP_LIMIT || u1 > 1.0F + GUI_UV_OVERLAP_LIMIT
			|| v1 < -GUI_UV_OVERLAP_LIMIT || v1 > 1.0F + GUI_UV_OVERLAP_LIMIT) {
			return false;
		}
		return clip == null || (clip.left() >= 0 && clip.top() >= 0 && clip.width() >= 0 && clip.height() >= 0
			&& (long)clip.left() + clip.width() <= guiWidth && (long)clip.top() + clip.height() <= guiHeight);
	}

	private static boolean finiteAffinePose(Matrix3x2f pose) {
		return pose != null
			&& Float.isFinite(pose.m00()) && Float.isFinite(pose.m01())
			&& Float.isFinite(pose.m10()) && Float.isFinite(pose.m11())
			&& Float.isFinite(pose.m20()) && Float.isFinite(pose.m21());
	}

	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueTiledCopiedBlit(
		TiledBlitRenderState blit, int guiWidth, int guiHeight, int dynamicLayerOrder
	) {
		if (blit == null) return null;
		long wideWidth = (long)blit.x1() - blit.x0();
		long wideHeight = (long)blit.y1() - blit.y0();
		int width = wideWidth > 0 && wideWidth <= Integer.MAX_VALUE ? (int)wideWidth : -1;
		int height = wideHeight > 0 && wideHeight <= Integer.MAX_VALUE ? (int)wideHeight : -1;
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
			|| !finiteAffinePose(blit.pose())
			|| (blit.pipeline() != RenderPipelines.GUI_TEXTURED
				&& blit.pipeline() != RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND
				&& blit.pipeline() != RenderPipelines.VIGNETTE
				&& blit.pipeline() != RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA
				&& blit.pipeline() != RenderPipelines.GUI_NAUSEA_OVERLAY)
			|| blit.semanticTexture() == null
			|| !semanticSingleTexture(blit.textureSetup(), blit.semanticTexture())
			|| blit.tileWidth() <= 0 || blit.tileHeight() <= 0 || width <= 0 || height <= 0
			|| !Float.isFinite(blit.u0()) || !Float.isFinite(blit.v0())
			|| !Float.isFinite(blit.u1()) || !Float.isFinite(blit.v1())
			|| blit.u1() < blit.u0() || blit.v1() < blit.v0()
			|| blit.u1() - blit.u0() <= 0.0F || blit.u1() - blit.u0() > 4096.0F
			|| blit.v1() - blit.v0() <= 0.0F || blit.v1() - blit.v0() > 4096.0F
			|| ((wideWidth + blit.tileWidth() - 1L) / blit.tileWidth())
				* ((wideHeight + blit.tileHeight() - 1L) / blit.tileHeight()) > 4096L) {
			return null;
		}
		// UV wrapping can split one geometric tile into multiple affine requests.
		// Preflight the exact bounded request count before staging the asset or
		// enqueueing a prefix, so a pathological repeat interval cannot create an
		// unbounded Java request list or a partial Rust GUI submission.
		long estimatedSegments = 0L;
		for (int offsetX = 0; offsetX < width; offsetX += blit.tileWidth()) {
			int tileWidth = Math.min(blit.tileWidth(), width - offsetX);
			float uSpan = (blit.u1() - blit.u0()) * tileWidth / blit.tileWidth();
			float uStart = blit.u0() + (blit.u1() - blit.u0()) * offsetX / blit.tileWidth();
			long uSegments = boundedWrappedSegmentCount(uStart, uSpan);
			for (int offsetY = 0; offsetY < height; offsetY += blit.tileHeight()) {
				int tileHeight = Math.min(blit.tileHeight(), height - offsetY);
				float vSpan = (blit.v1() - blit.v0()) * tileHeight / blit.tileHeight();
				float vStart = blit.v0() + (blit.v1() - blit.v0()) * offsetY / blit.tileHeight();
				long vSegments = boundedWrappedSegmentCount(vStart, vSpan);
				estimatedSegments = Math.addExact(estimatedSegments, Math.multiplyExact(uSegments, vSegments));
				if (estimatedSegments > MAX_GUI_TILED_SEGMENTS) return null;
			}
		}
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolveAtlas(blit.semanticTexture());
		if (asset == null) asset = RustGalGuiRawImageAssets.resolve(blit.semanticTexture());
		if (asset == null) return null;
		int requestLayerOrder = dynamicLayerOrder(dynamicLayerOrder);
		if (blit.pipeline() == RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND) {
			requestLayerOrder = GuiRenderStratum.GUI_OPAQUE_BLIT.order();
		} else if (blit.pipeline() == RenderPipelines.VIGNETTE) {
			requestLayerOrder = GuiRenderStratum.GUI_VIGNETTE_BLIT.order();
		} else if (blit.pipeline() == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA) {
			requestLayerOrder = GuiRenderStratum.GUI_PREMULTIPLIED_BLIT.order();
		} else if (blit.pipeline() == RenderPipelines.GUI_NAUSEA_OVERLAY) {
			requestLayerOrder = GuiRenderStratum.GUI_ADDITIVE_BLIT.order();
		}
		List<VulkanicGalBridge.GuiAffineQuadRecord> requests = new ArrayList<>((int)estimatedSegments);
		List<int[]> elementBounds = new ArrayList<>((int)estimatedSegments);
		Matrix3x2f pose = blit.pose();
		for (int offsetX = 0; offsetX < width; offsetX += blit.tileWidth()) {
			int tileWidth = Math.min(blit.tileWidth(), width - offsetX);
			float uSpan = (blit.u1() - blit.u0()) * tileWidth / blit.tileWidth();
			float uStart = blit.u0() + (blit.u1() - blit.u0()) * offsetX / blit.tileWidth();
			for (int offsetY = 0; offsetY < height; offsetY += blit.tileHeight()) {
				int tileHeight = Math.min(blit.tileHeight(), height - offsetY);
				float vSpan = (blit.v1() - blit.v0()) * tileHeight / blit.tileHeight();
				float vStart = blit.v0() + (blit.v1() - blit.v0()) * offsetY / blit.tileHeight();
				float left = blit.x0() + offsetX;
				float top = blit.y0() + offsetY;
				float right = left + tileWidth;
				float bottom = top + tileHeight;
				for (float[] xs : wrappedUnitIntervalSegments(uStart, uSpan)) {
					float segmentLeft = left + tileWidth * xs[0];
					float segmentRight = left + tileWidth * xs[1];
					for (float[] ys : wrappedUnitIntervalSegments(vStart, vSpan)) {
						float segmentTop = top + tileHeight * ys[0];
						float segmentBottom = top + tileHeight * ys[1];
						float x0 = pose.m00() * segmentLeft + pose.m10() * segmentTop + pose.m20();
						float y0 = pose.m01() * segmentLeft + pose.m11() * segmentTop + pose.m21();
						float x1 = pose.m00() * segmentRight + pose.m10() * segmentTop + pose.m20();
						float y1 = pose.m01() * segmentRight + pose.m11() * segmentTop + pose.m21();
						float x3 = pose.m00() * segmentLeft + pose.m10() * segmentBottom + pose.m20();
						float y3 = pose.m01() * segmentLeft + pose.m11() * segmentBottom + pose.m21();
						VulkanicGalBridge.GuiAffineQuadRecord request = new VulkanicGalBridge.GuiAffineQuadRecord(
							requestLayerOrder, asset.assetId(), x0, y0, x1, y1, x3, y3,
							0.0F, xs[2], ys[2], xs[3], ys[3], blit.color(), guiWidth, guiHeight
						);
						if (blit.scissorArea() != null) {
							ScreenRectangle scissor = blit.scissorArea();
							request = request.withClip(scissor.left(), scissor.top(), scissor.width(), scissor.height());
						}
						requests.add(request);
						elementBounds.add(new int[] {
							(int)Math.floor(segmentLeft), (int)Math.floor(segmentTop),
							Math.max(1, (int)Math.ceil(segmentRight - segmentLeft)),
							Math.max(1, (int)Math.ceil(segmentBottom - segmentTop))
						});
					}
				}
			}
		}
		// The prior count-and-geometry preflight covers the complete expanded
		// request. Submit it as one semantic operation so a tiled image cannot
		// leave an accepted prefix behind and its asset stages only after admission.
		long startedNanos = System.nanoTime();
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiAffineQuadRequests(
			requests, dynamicLayerId(dynamicLayerOrder), requestLayerOrder, startedNanos
		);
		RustGalGuiRawImageAssets.stage(asset);
		List<RustGalGuiElementRenderState> elements = new ArrayList<>(elementBounds.size());
		for (int[] bounds : elementBounds) {
			elements.add(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_FILE_BACKED_BLIT,
				"minecraft.gui.tiled-blit", -1, -1.0F, GuiFillDirection.NONE,
				bounds[0], bounds[1], bounds[2], bounds[3], guiWidth, guiHeight));
		}
		return List.copyOf(elements);
	}

	private static long boundedWrappedSegmentCount(float start, float span) {
		if (!Float.isFinite(start) || !Float.isFinite(span) || span <= 0.0F) return Long.MAX_VALUE;
		// wrappedUnitIntervalSegments emits at most ceil(span)+1 pieces. Keep the
		// same conservative bound here without allocating its lists during preflight.
		return Math.min(4_097L, (long)Math.ceil(span) + 1L);
	}

	/**
	 * Splits one positive UV interval into unit-wrapped pieces. Repeated GUI
	 * textures can span more than one turn, so this deliberately emits more than
	 * two pieces when necessary. Returned values are geometry fractions followed by normalized UV bounds:
	 * {@code geometryStart, geometryEnd, uvStart, uvEnd}.
	 */
	static List<float[]> wrappedUnitIntervalSegments(float start, float span) {
		if (!Float.isFinite(start) || !Float.isFinite(span) || span <= 0.0F || span > 4096.0F) {
			return List.of();
		}
		float normalized = start - (float)Math.floor(start);
		List<float[]> segments = new ArrayList<>(Math.min(4096, (int)Math.ceil(span) + 1));
		float consumed = 0.0F;
		while (consumed < span - 0.000001F && segments.size() < 4096) {
			float available = 1.0F - normalized;
			float piece = Math.min(span - consumed, available);
			segments.add(new float[] {consumed / span, (consumed + piece) / span, normalized, normalized + piece});
			consumed += piece;
			normalized = 0.0F;
		}
		return consumed >= span - 0.000001F ? segments : List.of();
	}

	private static boolean semanticSingleTexture(net.minecraft.client.gui.render.TextureSetup setup, ResourceLocation semanticTexture) {
		return setup.texure1() == null && setup.texure2() == null
			&& (setup.texure0() != null || semanticTexture != null);
	}

	private static synchronized void recordTextRouteDiagnostic(String detail) {
		if (!TEXT_ROUTE_DIAGNOSTICS_ENABLED || TEXT_ROUTE_DIAGNOSTICS.containsKey(detail)) {
			return;
		}
		if (TEXT_ROUTE_DIAGNOSTICS.size() >= MAX_GUI_DIAGNOSTIC_ENTRIES) return;
		TEXT_ROUTE_DIAGNOSTICS.put(detail, Boolean.TRUE);
		RustGalFrameCoordinator.auditMessage("gui.text.route " + detail);
	}

	/** Records a bounded semantic-coverage miss without reopening Java GUI draws. */
	public static void recordUnsupportedElement(String elementKind) {
		if (elementKind == null || elementKind.isBlank()) {
			elementKind = "unknown";
		}
		synchronized (RustGalGuiRenderer.class) {
			if (isWholeFrameVulkanEnabled()) {
				if (wholeFrameUnsupportedElementCount < MAX_GUI_UNSUPPORTED_ELEMENTS) {
					wholeFrameUnsupportedElementCount++;
				}
				if (WHOLE_FRAME_UNSUPPORTED_ELEMENTS.containsKey(elementKind)
					|| WHOLE_FRAME_UNSUPPORTED_ELEMENTS.size() < MAX_GUI_DIAGNOSTIC_ENTRIES) {
					WHOLE_FRAME_UNSUPPORTED_ELEMENTS.merge(elementKind, 1, Integer::sum);
				}
			}
		}
		recordTextRouteDiagnostic("unsupported-element=" + elementKind);
	}

	/** Adds bounded detail to the current unsupported-family diagnostic without changing its count. */
	public static void recordUnsupportedElementDetail(String detail) {
		if (detail == null || detail.isBlank()) return;
		synchronized (RustGalGuiRenderer.class) {
			if (isWholeFrameVulkanEnabled()) {
				if (WHOLE_FRAME_UNSUPPORTED_ELEMENTS.containsKey(detail)
					|| WHOLE_FRAME_UNSUPPORTED_ELEMENTS.size() < MAX_GUI_DIAGNOSTIC_ENTRIES) {
					WHOLE_FRAME_UNSUPPORTED_ELEMENTS.merge(detail, 1, Integer::sum);
				}
			}
		}
	}

	/** Starts the explicit GUI admission window for one whole-frame Vulkan frame. */
	public static synchronized void beginWholeFrameVulkanFrame() {
		wholeFrameUnsupportedElementCount = 0;
		WHOLE_FRAME_UNSUPPORTED_ELEMENTS.clear();
	}

	/** Retires generation-keyed text metadata when resource-pack assets reload. */
	public static synchronized void invalidateTextAtlasMetadata() {
		TEXT_ATLAS_IDENTITIES.clear();
		TEXT_ATLAS_GENERATIONS.clear();
	}

	/** Returns the number of GUI elements that could not be represented semantically. */
	public static synchronized int wholeFrameUnsupportedElementCount() {
		return wholeFrameUnsupportedElementCount;
	}

	/** Returns bounded diagnostics for unsupported GUI families in the active frame. */
	public static synchronized String wholeFrameUnsupportedElementSummary() {
		if (WHOLE_FRAME_UNSUPPORTED_ELEMENTS.isEmpty()) return "none";
		return WHOLE_FRAME_UNSUPPORTED_ELEMENTS.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.reduce((left, right) -> left + "," + right)
			.orElse("none");
	}

	static VulkanicGalBridge.GuiAffineQuadRecord transformTextQuad(
		TextGlyphQuad quad, Matrix3x2f pose, long assetId, int guiWidth, int guiHeight, @Nullable ScreenRectangle scissor
	) {
		float x0 = pose.m00() * quad.x0() + pose.m10() * quad.y0() + pose.m20();
		float y0 = pose.m01() * quad.x0() + pose.m11() * quad.y0() + pose.m21();
		// TextGlyphQuad follows Minecraft's vertex order: TL, BL, BR, TR. The
		// semantic affine ABI is origin, U-axis endpoint, V-axis endpoint.
		float x1 = pose.m00() * quad.x3() + pose.m10() * quad.y3() + pose.m20();
		float y1 = pose.m01() * quad.x3() + pose.m11() * quad.y3() + pose.m21();
		float x3 = pose.m00() * quad.x1() + pose.m10() * quad.y1() + pose.m20();
		float y3 = pose.m01() * quad.x1() + pose.m11() * quad.y1() + pose.m21();
		VulkanicGalBridge.GuiAffineQuadRecord request = new VulkanicGalBridge.GuiAffineQuadRecord(
			GuiRenderStratum.GUI_TEXT.order(), assetId, x0, y0, x1, y1, x3, y3, quad.z(),
			quad.u0(), quad.v0(), quad.u1(), quad.v1(), quad.colorArgb(), guiWidth, guiHeight
		);
		return scissor == null ? request : request.withClip(scissor.left(), scissor.top(), scissor.width(), scissor.height());
	}

	private static boolean isParallelogram(TextGlyphQuad quad) {
		return Math.abs((quad.x1() + quad.x3()) - (quad.x0() + quad.x2())) <= 0.001F
			&& Math.abs((quad.y1() + quad.y3()) - (quad.y0() + quad.y2())) <= 0.001F;
	}

	private static boolean finiteTextQuad(TextGlyphQuad quad) {
		return quad != null
			&& Float.isFinite(quad.x0()) && Float.isFinite(quad.y0())
			&& Float.isFinite(quad.x1()) && Float.isFinite(quad.y1())
			&& Float.isFinite(quad.x2()) && Float.isFinite(quad.y2())
			&& Float.isFinite(quad.x3()) && Float.isFinite(quad.y3())
			&& Float.isFinite(quad.z())
			&& Float.isFinite(quad.u0()) && Float.isFinite(quad.v0())
			&& Float.isFinite(quad.u1()) && Float.isFinite(quad.v1());
	}

	private static synchronized long semanticTextAtlasId(String identity, boolean colored) {
		long hash = 0xcbf29ce484222325L;
		String key = identity + (colored ? "#rgba" : "#alpha");
		for (int i = 0; i < key.length(); i++) {
			hash ^= key.charAt(i);
			hash *= 0x100000001b3L;
		}
		if (hash == 0L) {
			hash = 1L;
		}
		String previous = TEXT_ATLAS_IDENTITIES.get(hash);
		if (previous != null && !previous.equals(key)) {
			throw new IllegalStateException("semantic text atlas identity collision");
		}
		if (previous == null && TEXT_ATLAS_IDENTITIES.size() >= MAX_TEXT_ATLAS_IDENTITIES) {
			throw new IllegalStateException("semantic text atlas identity bound exceeded " + MAX_TEXT_ATLAS_IDENTITIES);
		}
		TEXT_ATLAS_IDENTITIES.putIfAbsent(hash, key);
		return hash;
	}

	private static synchronized void stageTextAtlas(long assetId, FontTexture.SemanticAtlasSnapshot atlas) {
		String key = atlas.identity() + (atlas.colored() ? "#rgba" : "#alpha");
		TextAtlasGeneration generation = new TextAtlasGeneration(atlas.generation(), atlas.revision());
		if (generation.equals(TEXT_ATLAS_GENERATIONS.get(key))) {
			return;
		}
		if (!TEXT_ATLAS_GENERATIONS.containsKey(key) && TEXT_ATLAS_GENERATIONS.size() >= MAX_TEXT_ATLAS_GENERATIONS) {
			throw new IllegalStateException("semantic text atlas generation bound exceeded " + MAX_TEXT_ATLAS_GENERATIONS);
		}
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			assetId, atlas.colored() ? 2 : 1, atlas.width(), atlas.height(), atlas.pixels()
		));
		TEXT_ATLAS_GENERATIONS.put(key, generation);
	}

	private record TextAtlasGeneration(long atlasGeneration, long revision) {
	}

	private record TextAtlasRequest(long assetId, FontTexture.SemanticAtlasSnapshot atlas) {
	}

	public static boolean isWholeFrameVulkanEnabled() {
		return RustGalVulkanWholeFrameMode.enabled();
	}

	public static boolean isWholeFrameVulkanActive() {
		return RustGalVulkanWholeFrameMode.enabledForBackend(VulkanicAPI.isVulkanBackendSelected());
	}

	public static boolean isMigratedGuiDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.guiCrosshair.disabled") || Boolean.getBoolean("mattmc.dev.rustGalGui.disabled");
	}

	public static boolean isMigratedGuiLegacyControl() {
		return legacyControlEnabled("mattmc.dev.guiCrosshair.legacyControl")
			|| legacyControlEnabled("mattmc.dev.rustGalGui.legacyControl");
	}

	public static boolean isArmorDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.armor.disabled");
	}

	public static boolean isArmorLegacyControl() {
		return legacyControlEnabled("mattmc.dev.rustGalGui.armor.legacyControl");
	}

	public static boolean isPlayerHealthDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.playerHealth.disabled");
	}

	public static boolean isPlayerHealthLegacyControl() {
		return legacyControlEnabled("mattmc.dev.rustGalGui.playerHealth.legacyControl");
	}

	public static boolean isAbsorptionHealthDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.absorption.disabled");
	}

	public static boolean isAbsorptionHealthLegacyControl() {
		return legacyControlEnabled("mattmc.dev.rustGalGui.absorption.legacyControl");
	}

	public static boolean isHungerDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.hunger.disabled");
	}

	public static boolean isHungerLegacyControl() {
		return legacyControlEnabled("mattmc.dev.rustGalGui.hunger.legacyControl");
	}

	public static boolean isAirDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.air.disabled");
	}

	public static boolean isAirLegacyControl() {
		return legacyControlEnabled("mattmc.dev.rustGalGui.air.legacyControl");
	}

	public static boolean isMountHealthDisabledForDiagnostics() {
		return Boolean.getBoolean("mattmc.dev.rustGalGui.mountHealth.disabled");
	}

	public static boolean isMountHealthLegacyControl() {
		return legacyControlEnabled("mattmc.dev.rustGalGui.mountHealth.legacyControl");
	}

	public static GuiExecutionRoute currentExecutionRoute() {
		return selectExecutionRoute(
			VulkanicAPI.isVulkanBackendSelected(),
			isWholeFrameVulkanEnabled(),
			isMigratedGuiDisabledForDiagnostics(),
			isMigratedGuiLegacyControl()
		);
	}

	public static GuiExecutionRoute selectExecutionRouteForTests(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean diagnosticsDisabled,
		boolean diagnosticLegacyControl
	) {
		return selectExecutionRoute(vulkanBackendSelected, wholeFrameVulkanEnabled, diagnosticsDisabled, diagnosticLegacyControl);
	}

	private static GuiExecutionRoute selectExecutionRoute(
		boolean vulkanBackendSelected,
		boolean wholeFrameVulkanEnabled,
		boolean diagnosticsDisabled,
		boolean diagnosticLegacyControl
	) {
		if (diagnosticsDisabled) {
			return GuiExecutionRoute.DISABLED;
		}
		if (diagnosticLegacyControl) {
			return vulkanBackendSelected ? GuiExecutionRoute.DISABLED : GuiExecutionRoute.JAVA_COMPATIBILITY;
		}
		if (wholeFrameVulkanEnabled) {
			return GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME;
		}
		if (vulkanBackendSelected) {
			return GuiExecutionRoute.DISABLED;
		}
		return GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT;
	}

	public static boolean shouldDrawJavaCompatibilityGui() {
		return currentExecutionRoute().usesJavaCompatibility();
	}

	private static boolean legacyControlEnabled(String property) {
		return Boolean.getBoolean(property) && !isWholeFrameVulkanEnabled();
	}

	public static void enqueueCrosshair(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int width, int height) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.CROSSHAIR, CROSSHAIR_PRODUCER, -1, x, y, width, height);
	}

	public static void enqueueHotbarBase(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int width, int height) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.HOTBAR_BASE, HOTBAR_BASE_PRODUCER, -1, x, y, width, height);
	}

	public static void enqueueHotbarSelection(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int selectedSlot,
		int x,
		int y,
		int width,
		int height
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (selectedSlot < 0 || selectedSlot > 8) {
			throw new IllegalArgumentException("selected hotbar slot must be in 0..8: " + selectedSlot);
		}
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.HOTBAR_SELECTION, HOTBAR_SELECTION_PRODUCER, selectedSlot, x, y, width, height);
	}

	public static void enqueueExperienceBar(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		int width,
		int height,
		float progressFraction,
		int filledWidth
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (!Float.isFinite(progressFraction)) {
			throw new IllegalArgumentException("experience progress fraction must be finite: " + progressFraction);
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("experience bar dimensions must be positive: " + width + "x" + height);
		}
		if (filledWidth < 0 || filledWidth > width + 1) {
			throw new IllegalArgumentException("experience bar filled width is outside the vanilla range: " + filledWidth);
		}
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			GuiSprite.EXPERIENCE_BAR_BACKGROUND,
			EXPERIENCE_BACKGROUND_PRODUCER,
			-1,
			progressFraction,
			GuiFillDirection.NONE,
			x,
			y,
			width,
			height
		);
		if (filledWidth > 0) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.EXPERIENCE_BAR_PROGRESS,
				EXPERIENCE_PROGRESS_PRODUCER,
				-1,
				progressFraction,
				GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
				x,
				y,
				filledWidth,
				height
			);
		}
	}

	public static void enqueueCrosshairAttackIndicator(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		float cooldownProgress,
		int filledWidth,
		boolean fullIndicator
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (!Float.isFinite(cooldownProgress)) {
			throw new IllegalArgumentException("crosshair attack indicator progress must be finite: " + cooldownProgress);
		}
		if (fullIndicator) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.CROSSHAIR_ATTACK_FULL,
				ATTACK_CROSSHAIR_PROGRESS_PRODUCER,
				-1,
				Math.max(0.0F, Math.min(1.0F, cooldownProgress)),
				GuiFillDirection.NONE,
				x,
				y,
				16,
				16
			);
			return;
		}
		if (filledWidth < 0 || filledWidth > 16) {
			throw new IllegalArgumentException("crosshair attack indicator filled width must be in 0..16: " + filledWidth);
		}
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			GuiSprite.CROSSHAIR_ATTACK_BACKGROUND,
			ATTACK_CROSSHAIR_BACKGROUND_PRODUCER,
			-1,
			cooldownProgress,
			GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
			x,
			y,
			16,
			4
		);
		if (filledWidth > 0) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.CROSSHAIR_ATTACK_PROGRESS,
				ATTACK_CROSSHAIR_PROGRESS_PRODUCER,
				-1,
				cooldownProgress,
				GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
				x,
				y,
				filledWidth,
				4
			);
		}
	}

	public static void enqueueHotbarAttackIndicator(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		float cooldownProgress,
		int filledHeight
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (!Float.isFinite(cooldownProgress)) {
			throw new IllegalArgumentException("hotbar attack indicator progress must be finite: " + cooldownProgress);
		}
		if (filledHeight < 0 || filledHeight > 18) {
			throw new IllegalArgumentException("hotbar attack indicator filled height must be in 0..18: " + filledHeight);
		}
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			GuiSprite.HOTBAR_ATTACK_BACKGROUND,
			ATTACK_HOTBAR_BACKGROUND_PRODUCER,
			-1,
			cooldownProgress,
			GuiFillDirection.VERTICAL_BOTTOM_TO_TOP,
			x,
			y,
			18,
			18
		);
		if (filledHeight > 0) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				GuiSprite.HOTBAR_ATTACK_PROGRESS,
				ATTACK_HOTBAR_PROGRESS_PRODUCER,
				-1,
				cooldownProgress,
				GuiFillDirection.VERTICAL_BOTTOM_TO_TOP,
				x,
				y + 18 - filledHeight,
				18,
				filledHeight
			);
		}
	}

	public static void enqueueArmorIcons(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics, int armorValue, int x, int y) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (armorValue < 0 || armorValue > 20) {
			throw new IllegalArgumentException("armor value must be in 0..20: " + armorValue);
		}
		if (armorValue == 0) {
			return;
		}
		for (int icon = 0; icon < 10; icon++) {
			ArmorIconState state = armorIconState(armorValue, icon);
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				armorIconSprite(state),
				ARMOR_ICON_PRODUCER + "." + state.id() + ".slot" + icon,
				icon,
				armorValue / 20.0F,
				GuiFillDirection.NONE,
				x + icon * 8,
				y,
				9,
				9
			);
		}
	}

	public static ArmorIconState armorIconStateForTests(int armorValue, int iconIndex) {
		return armorIconState(armorValue, iconIndex);
	}

	public static void enqueuePlayerHearts(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<PlayerHeartRequest> hearts
	) {
		if (!isMigratedGuiEnabled() || hearts.isEmpty()) {
			return;
		}
		for (PlayerHeartRequest heart : hearts) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				playerHeartSprite(heart),
				PLAYER_HEART_PRODUCER + "." + heart.variant().id() + "." + heart.state().id() + ".order" + heart.order(),
				heart.order(),
				heart.state().progressValue(),
				GuiFillDirection.NONE,
				heart.x(),
				heart.y(),
				9,
				9
			);
		}
	}

	public static void enqueueAbsorptionHearts(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<AbsorptionHeartRequest> hearts
	) {
		if (!isMigratedGuiEnabled() || hearts.isEmpty()) {
			return;
		}
		for (AbsorptionHeartRequest heart : hearts) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				absorptionHeartSprite(heart),
				ABSORPTION_HEART_PRODUCER + "." + heart.variant().id() + "." + heart.state().id() + ".order" + heart.order(),
				heart.order(),
				heart.state().progressValue(),
				GuiFillDirection.NONE,
				heart.x(),
				heart.y(),
				9,
				9
			);
		}
	}

	public static void enqueueHungerIcons(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<HungerIconRequest> icons
	) {
		if (!isMigratedGuiEnabled() || icons.isEmpty()) {
			return;
		}
		for (HungerIconRequest icon : icons) {
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				hungerIconSprite(icon),
				HUNGER_ICON_PRODUCER + "." + icon.variant().id() + "." + icon.state().id() + ".order" + icon.order(),
				icon.order(),
				icon.state().progressValue(),
				GuiFillDirection.NONE,
				icon.x(),
				icon.y(),
				9,
				9
			);
		}
	}

	public static void enqueueAirBubbles(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<AirBubbleRequest> bubbles
	) {
		if (!isMigratedGuiEnabled() || bubbles.isEmpty()) {
			return;
		}
		for (AirBubbleRequest bubble : bubbles) {
			if (!bubble.visible()) {
				continue;
			}
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				airBubbleSprite(bubble),
				AIR_BUBBLE_PRODUCER + "." + bubble.state().id() + (bubble.popping() ? ".popping" : "") + ".order" + bubble.order(),
				bubble.order(),
				bubble.state().progressValue(),
				GuiFillDirection.NONE,
				bubble.x(),
				bubble.y(),
				9,
				9
			);
		}
	}

	public static void enqueueMountHearts(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		List<MountHeartRequest> hearts
	) {
		if (!isMigratedGuiEnabled() || hearts.isEmpty()) {
			return;
		}
		for (MountHeartRequest heart : hearts) {
			if (!heart.visible()) {
				continue;
			}
			enqueueGuiSprite(
				minecraft,
				guiGraphics,
				mountHeartSprite(heart),
				MOUNT_HEART_PRODUCER + "." + heart.variant().id() + "." + heart.state().id() + ".row" + heart.row() + ".order" + heart.order(),
				heart.order(),
				heart.state().progressValue(),
				GuiFillDirection.NONE,
				heart.x(),
				heart.y(),
				9,
				9
			);
		}
	}

	private static ArmorIconState armorIconState(int armorValue, int iconIndex) {
		if (armorValue < 0 || armorValue > 20) {
			throw new IllegalArgumentException("armor value must be in 0..20: " + armorValue);
		}
		if (iconIndex < 0 || iconIndex >= 10) {
			throw new IllegalArgumentException("armor icon index must be in 0..9: " + iconIndex);
		}
		int threshold = iconIndex * 2 + 1;
		if (threshold < armorValue) {
			return ArmorIconState.FULL;
		}
		if (threshold == armorValue) {
			return ArmorIconState.HALF;
		}
		return ArmorIconState.EMPTY;
	}

	private static GuiSprite armorIconSprite(ArmorIconState state) {
		return switch (state) {
			case EMPTY -> GuiSprite.ARMOR_EMPTY;
			case HALF -> GuiSprite.ARMOR_HALF;
			case FULL -> GuiSprite.ARMOR_FULL;
		};
	}

	private static GuiSprite playerHeartSprite(PlayerHeartRequest request) {
		return switch (request.variant()) {
			case CONTAINER -> containerHeartSprite(request.state(), request.hardcore(), request.flashing());
			case NORMAL -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_NORMAL_FULL,
				GuiSprite.HEART_NORMAL_FULL_FLASHING,
				GuiSprite.HEART_NORMAL_HALF,
				GuiSprite.HEART_NORMAL_HALF_FLASHING,
				GuiSprite.HEART_NORMAL_HARDCORE_FULL,
				GuiSprite.HEART_NORMAL_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_NORMAL_HARDCORE_HALF,
				GuiSprite.HEART_NORMAL_HARDCORE_HALF_FLASHING);
			case POISONED -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_POISONED_FULL,
				GuiSprite.HEART_POISONED_FULL_FLASHING,
				GuiSprite.HEART_POISONED_HALF,
				GuiSprite.HEART_POISONED_HALF_FLASHING,
				GuiSprite.HEART_POISONED_HARDCORE_FULL,
				GuiSprite.HEART_POISONED_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_POISONED_HARDCORE_HALF,
				GuiSprite.HEART_POISONED_HARDCORE_HALF_FLASHING);
			case WITHERED -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_WITHERED_FULL,
				GuiSprite.HEART_WITHERED_FULL_FLASHING,
				GuiSprite.HEART_WITHERED_HALF,
				GuiSprite.HEART_WITHERED_HALF_FLASHING,
				GuiSprite.HEART_WITHERED_HARDCORE_FULL,
				GuiSprite.HEART_WITHERED_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_WITHERED_HARDCORE_HALF,
				GuiSprite.HEART_WITHERED_HARDCORE_HALF_FLASHING);
			case FROZEN -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_FROZEN_FULL,
				GuiSprite.HEART_FROZEN_FULL_FLASHING,
				GuiSprite.HEART_FROZEN_HALF,
				GuiSprite.HEART_FROZEN_HALF_FLASHING,
				GuiSprite.HEART_FROZEN_HARDCORE_FULL,
				GuiSprite.HEART_FROZEN_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_FROZEN_HARDCORE_HALF,
				GuiSprite.HEART_FROZEN_HARDCORE_HALF_FLASHING);
		};
	}

	private static GuiSprite absorptionHeartSprite(AbsorptionHeartRequest request) {
		return switch (request.variant()) {
			case CONTAINER -> containerHeartSprite(request.state(), request.hardcore(), request.flashing());
			case ABSORBING -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_ABSORBING_FULL,
				GuiSprite.HEART_ABSORBING_FULL_FLASHING,
				GuiSprite.HEART_ABSORBING_HALF,
				GuiSprite.HEART_ABSORBING_HALF_FLASHING,
				GuiSprite.HEART_ABSORBING_HARDCORE_FULL,
				GuiSprite.HEART_ABSORBING_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_ABSORBING_HARDCORE_HALF,
				GuiSprite.HEART_ABSORBING_HARDCORE_HALF_FLASHING);
			case WITHERED -> filledHeartSprite(request.state(), request.hardcore(), request.flashing(),
				GuiSprite.HEART_WITHERED_FULL,
				GuiSprite.HEART_WITHERED_FULL_FLASHING,
				GuiSprite.HEART_WITHERED_HALF,
				GuiSprite.HEART_WITHERED_HALF_FLASHING,
				GuiSprite.HEART_WITHERED_HARDCORE_FULL,
				GuiSprite.HEART_WITHERED_HARDCORE_FULL_FLASHING,
				GuiSprite.HEART_WITHERED_HARDCORE_HALF,
				GuiSprite.HEART_WITHERED_HARDCORE_HALF_FLASHING);
		};
	}

	private static GuiSprite hungerIconSprite(HungerIconRequest request) {
		return switch (request.variant()) {
			case NORMAL -> switch (request.state()) {
				case EMPTY -> GuiSprite.HUNGER_EMPTY;
				case HALF -> GuiSprite.HUNGER_HALF;
				case FULL -> GuiSprite.HUNGER_FULL;
			};
			case HUNGER_EFFECT -> switch (request.state()) {
				case EMPTY -> GuiSprite.HUNGER_EFFECT_EMPTY;
				case HALF -> GuiSprite.HUNGER_EFFECT_HALF;
				case FULL -> GuiSprite.HUNGER_EFFECT_FULL;
			};
		};
	}

	private static GuiSprite airBubbleSprite(AirBubbleRequest request) {
		return switch (request.state()) {
			case FULL -> GuiSprite.AIR_FULL;
			case PARTIAL -> request.popping() ? GuiSprite.AIR_POPPING : GuiSprite.AIR_FULL;
			case EMPTY -> GuiSprite.AIR_EMPTY;
		};
	}

	private static GuiSprite mountHeartSprite(MountHeartRequest request) {
		return switch (request.state()) {
			case EMPTY -> GuiSprite.HEART_VEHICLE_CONTAINER;
			case HALF -> GuiSprite.HEART_VEHICLE_HALF;
			case FULL -> GuiSprite.HEART_VEHICLE_FULL;
		};
	}

	private static GuiSprite containerHeartSprite(GuiHeartState state, boolean hardcore, boolean flashing) {
		if (state != GuiHeartState.CONTAINER) {
			throw new IllegalArgumentException("container heart variant cannot render " + state);
		}
		if (hardcore) {
			return flashing ? GuiSprite.HEART_CONTAINER_HARDCORE_FLASHING : GuiSprite.HEART_CONTAINER_HARDCORE;
		}
		return flashing ? GuiSprite.HEART_CONTAINER_FLASHING : GuiSprite.HEART_CONTAINER;
	}

	private static GuiSprite filledHeartSprite(
		GuiHeartState state,
		boolean hardcore,
		boolean flashing,
		GuiSprite full,
		GuiSprite fullFlashing,
		GuiSprite half,
		GuiSprite halfFlashing,
		GuiSprite hardcoreFull,
		GuiSprite hardcoreFullFlashing,
		GuiSprite hardcoreHalf,
		GuiSprite hardcoreHalfFlashing
	) {
		return switch (state) {
			case CONTAINER -> throw new IllegalArgumentException("filled heart variant cannot render a container");
			case FULL -> hardcore ? (flashing ? hardcoreFullFlashing : hardcoreFull) : (flashing ? fullFlashing : full);
			case HALF -> hardcore ? (flashing ? hardcoreHalfFlashing : hardcoreHalf) : (flashing ? halfFlashing : half);
		};
	}

	private static void enqueueGuiSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerId,
		int selectedSlot,
		int x,
		int y,
		int width,
		int height
	) {
		enqueueGuiSprite(minecraft, guiGraphics, sprite, producerId, selectedSlot, -1.0F, GuiFillDirection.NONE, x, y, width, height);
	}

	private static void enqueueGuiSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerId,
		int selectedSlot,
		float progressFraction,
		GuiFillDirection fillDirection,
		int x,
		int y,
		int width,
		int height
	) {
		long started = System.nanoTime();
		GraphicsFrameBenchmark.beginPhase("rust-gal." + sprite.phaseName + ".java-producer");
		GuiExecutionRoute route = currentExecutionRoute();
		if (!route.usesRustGui()) {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
			throw new IllegalStateException("Rust VulkanicGAL GUI enqueue requested while route is " + route);
		}
        boolean fullscreenPostEffect = sprite == GuiSprite.POST_EFFECT_INVERT
            || sprite == GuiSprite.POST_EFFECT_CREEPER
            || sprite == GuiSprite.POST_EFFECT_SPIDER;
        if (width <= 0 || height <= 0 || (!fullscreenPostEffect && (width > sprite.width || height > sprite.height))
            || (fullscreenPostEffect && (width > 16384 || height > 16384))) {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
			throw new IllegalArgumentException("GUI sprite destination extent is outside " + sprite.name() + ": " + width + "x" + height);
		}
		try {
			VulkanicGalBridge.GuiSpriteRecord request = new VulkanicGalBridge.GuiSpriteRecord(
				sprite.stratum.order(),
				sprite.semanticId(),
				selectedSlot,
				progressFraction,
				fillDirection.ordinal(),
				0xFFFFFFFF,
				x,
				y,
				width,
				height,
				guiGraphics.guiWidth(),
				guiGraphics.guiHeight()
			);
			var token = RustGalFrameCoordinator.enqueueGuiRequest(request, sprite.stratum, started);
			guiGraphics.guiRenderState.submitGuiElement(
				new RustGalGuiElementRenderState(
					token,
					sprite.stratum,
					producerId,
					selectedSlot,
					progressFraction,
					fillDirection,
					x,
					y,
					width,
					height,
					guiGraphics.guiWidth(),
					guiGraphics.guiHeight()
				)
			);
		} finally {
			GraphicsFrameBenchmark.endPhase("rust-gal." + sprite.phaseName + ".java-producer");
		}
	}

	/** Enqueues the explicit white/invert fullscreen blend used by enderman vision. */
    public static void enqueuePostEffectInvert(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics) {
		if (minecraft == null || guiGraphics == null || !isWholeFrameVulkanEnabled()) return;
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.POST_EFFECT_INVERT,
			"post-effect.invert", -1, -1.0F, GuiFillDirection.NONE,
			0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight());
    }

    /** Queues creeper vision as a semantic effect marker for Rust admission. */
    public static void enqueuePostEffectCreeper(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics) {
        if (minecraft == null || guiGraphics == null || !isWholeFrameVulkanEnabled()) return;
        enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.POST_EFFECT_CREEPER,
            "post-effect.creeper", -1, -1.0F, GuiFillDirection.NONE,
            0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight());
    }

    /** Queues spider vision as a semantic effect marker for Rust admission. */
    public static void enqueuePostEffectSpider(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics) {
        if (minecraft == null || guiGraphics == null || !isWholeFrameVulkanEnabled()) return;
        enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.POST_EFFECT_SPIDER,
            "post-effect.spider", -1, -1.0F, GuiFillDirection.NONE,
            0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight());
    }

	public static void enqueueBossBar(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		int x,
		int y,
		int width,
		int height,
		float progressFraction,
		int filledWidth,
		BossEvent.BossBarColor color,
		BossEvent.BossBarOverlay overlay
	) {
		if (!isMigratedGuiEnabled()) {
			return;
		}
		if (!Float.isFinite(progressFraction)) {
			throw new IllegalArgumentException("boss bar progress fraction must be finite: " + progressFraction);
		}
		if (color == null || overlay == null) {
			throw new IllegalArgumentException("boss bar color and overlay must be present");
		}
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("boss bar dimensions must be positive: " + width + "x" + height);
		}
		if (filledWidth < 0 || filledWidth > width) {
			throw new IllegalArgumentException("boss bar filled width must be in 0.." + width + ": " + filledWidth);
		}
		enqueueBossBarSprite(minecraft, guiGraphics, bossBarColorBackground(color), BOSS_BAR_BACKGROUND_PRODUCER, color, overlay,
			progressFraction, x, y, width, height);
		if (overlay != BossEvent.BossBarOverlay.PROGRESS) {
			enqueueBossBarSprite(minecraft, guiGraphics, bossBarOverlayBackground(overlay), BOSS_BAR_BACKGROUND_PRODUCER, color, overlay,
				progressFraction, x, y, width, height);
		}
		if (filledWidth > 0) {
			enqueueBossBarSprite(minecraft, guiGraphics, bossBarColorProgress(color), BOSS_BAR_PROGRESS_PRODUCER, color, overlay,
				progressFraction, x, y, filledWidth, height);
			if (overlay != BossEvent.BossBarOverlay.PROGRESS) {
				enqueueBossBarSprite(minecraft, guiGraphics, bossBarOverlayProgress(overlay), BOSS_BAR_PROGRESS_PRODUCER, color, overlay,
					progressFraction, x, y, filledWidth, height);
			}
		}
	}

	private static void enqueueBossBarSprite(
		Minecraft minecraft,
		net.minecraft.client.gui.GuiGraphics guiGraphics,
		GuiSprite sprite,
		String producerPrefix,
		BossEvent.BossBarColor color,
		BossEvent.BossBarOverlay overlay,
		float progressFraction,
		int x,
		int y,
		int width,
		int height
	) {
		enqueueGuiSprite(
			minecraft,
			guiGraphics,
			sprite,
			producerPrefix + "." + color.getSerializedName() + "." + overlay.getSerializedName() + "." + sprite.semanticSuffix,
			-1,
			progressFraction,
			GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT,
			x,
			y,
			width,
			height
		);
	}

	static boolean assetUpdatesDisabled() {
		return ASSET_UPDATES_DISABLED;
	}

	static List<VulkanicGalBridge.GuiAssetRecord> collectResolvedAssets(ResourceManager resourceManager) {
		if (resourceManager == null) {
			return List.of();
		}
		List<VulkanicGalBridge.GuiAssetRecord> assets = new ArrayList<>();
		for (GuiSprite sprite : GuiSprite.values()) {
            if (sprite == GuiSprite.POST_EFFECT_INVERT
                || sprite == GuiSprite.POST_EFFECT_CREEPER
                || sprite == GuiSprite.POST_EFFECT_SPIDER) {
				// This one-pixel source is generated and owned by the Rust GUI
				// frontend; it is deliberately not a resource-pack lookup.
				continue;
			}
			ResourceLocation location = sprite.resourceLocation();
			Optional<Resource> resource = resourceManager.getResource(location);
			if (resource.isEmpty()) {
				RustGalFrameCoordinator.auditMessage(
					"Rust VulkanicGAL GUI asset missing"
						+ " sprite=" + sprite.name()
						+ " sprite_id=" + sprite.semanticId()
						+ " path=" + location
				);
				if (RustGalVulkanWholeFrameMode.enabled()) {
					throw new IllegalStateException("Rust Vulkan whole-frame GUI asset is unavailable: " + location);
				}
				continue;
			}
			try (InputStream input = resource.get().open()) {
				byte[] bytes = input.readNBytes(MAX_GUI_ASSET_BYTES + 1);
				if (bytes.length > MAX_GUI_ASSET_BYTES) {
					throw new IOException("GUI asset exceeds the " + MAX_GUI_ASSET_BYTES + " byte bound");
				}
				assets.add(new VulkanicGalBridge.GuiAssetRecord(sprite.semanticId(), bytes));
					RustGalFrameCoordinator.auditMessage(
					"Rust VulkanicGAL GUI asset resolved"
						+ " sprite=" + sprite.name()
						+ " sprite_id=" + sprite.semanticId()
						+ " path=" + location
						+ " source_pack=" + resource.get().sourcePackId()
						+ " bytes=" + bytes.length
						+ " sha256=" + sha256Hex(bytes)
				);
			} catch (IOException error) {
				if (RustGalVulkanWholeFrameMode.enabled()) {
					throw new IllegalStateException("Failed to read Rust VulkanicGAL GUI asset: " + location, error);
				}
				LOGGER.warn("Failed to read Rust VulkanicGAL GUI sprite override {}", location, error);
			}
		}
		return assets;
	}

	private static String sha256Hex(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 digest is unavailable", error);
		}
	}

	public static GuiSprite debugArmorSpriteForTests(ArmorIconState state) {
		return armorIconSprite(state);
	}

	private static GuiSprite bossBarColorBackground(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> GuiSprite.BOSS_BAR_PINK_BACKGROUND;
			case BLUE -> GuiSprite.BOSS_BAR_BLUE_BACKGROUND;
			case RED -> GuiSprite.BOSS_BAR_RED_BACKGROUND;
			case GREEN -> GuiSprite.BOSS_BAR_GREEN_BACKGROUND;
			case YELLOW -> GuiSprite.BOSS_BAR_YELLOW_BACKGROUND;
			case PURPLE -> GuiSprite.BOSS_BAR_PURPLE_BACKGROUND;
			case WHITE -> GuiSprite.BOSS_BAR_WHITE_BACKGROUND;
		};
	}

	private static GuiSprite bossBarColorProgress(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> GuiSprite.BOSS_BAR_PINK_PROGRESS;
			case BLUE -> GuiSprite.BOSS_BAR_BLUE_PROGRESS;
			case RED -> GuiSprite.BOSS_BAR_RED_PROGRESS;
			case GREEN -> GuiSprite.BOSS_BAR_GREEN_PROGRESS;
			case YELLOW -> GuiSprite.BOSS_BAR_YELLOW_PROGRESS;
			case PURPLE -> GuiSprite.BOSS_BAR_PURPLE_PROGRESS;
			case WHITE -> GuiSprite.BOSS_BAR_WHITE_PROGRESS;
		};
	}

	private static GuiSprite bossBarOverlayBackground(BossEvent.BossBarOverlay overlay) {
		return switch (overlay) {
			case PROGRESS -> throw new IllegalArgumentException("progress boss overlay has no notch background sprite");
			case NOTCHED_6 -> GuiSprite.BOSS_BAR_NOTCHED_6_BACKGROUND;
			case NOTCHED_10 -> GuiSprite.BOSS_BAR_NOTCHED_10_BACKGROUND;
			case NOTCHED_12 -> GuiSprite.BOSS_BAR_NOTCHED_12_BACKGROUND;
			case NOTCHED_20 -> GuiSprite.BOSS_BAR_NOTCHED_20_BACKGROUND;
		};
	}

	private static GuiSprite bossBarOverlayProgress(BossEvent.BossBarOverlay overlay) {
		return switch (overlay) {
			case PROGRESS -> throw new IllegalArgumentException("progress boss overlay has no notch progress sprite");
			case NOTCHED_6 -> GuiSprite.BOSS_BAR_NOTCHED_6_PROGRESS;
			case NOTCHED_10 -> GuiSprite.BOSS_BAR_NOTCHED_10_PROGRESS;
			case NOTCHED_12 -> GuiSprite.BOSS_BAR_NOTCHED_12_PROGRESS;
			case NOTCHED_20 -> GuiSprite.BOSS_BAR_NOTCHED_20_PROGRESS;
		};
	}

	enum TextureGroup {
		GUI_ALPHA("gui-textured-alpha-atlas", "gui-alpha", false),
		GUI_INVERT("gui-textured-invert-atlas", "gui-invert", true);

		final String cacheKind;
		final String semanticId;
		final boolean invertBlend;

		TextureGroup(String cacheKind, String semanticId, boolean invertBlend) {
			this.cacheKind = cacheKind;
			this.semanticId = semanticId;
			this.invertBlend = invertBlend;
		}
	}

	enum GuiSprite {
		CROSSHAIR(
			GuiRenderStratum.GUI_CROSSHAIR,
			"crosshair",
			"gui-textured-invert-crosshair",
			"/assets/minecraft/textures/gui/sprites/hud/crosshair.png",
			15,
			15,
			true
		),
		HOTBAR_BASE(
			GuiRenderStratum.GUI_HOTBAR_BASE,
			"hotbar-base",
			"gui-textured-alpha-hotbar-base",
			"/assets/minecraft/textures/gui/sprites/hud/hotbar.png",
			182,
			22,
			false
		),
			HOTBAR_SELECTION(
				GuiRenderStratum.GUI_HOTBAR_SELECTION,
				"hotbar-selection",
				"gui-textured-alpha-hotbar-selection",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png",
				24,
				23,
				false
			),
			ARMOR_EMPTY(
				GuiRenderStratum.GUI_ARMOR,
				"armor-empty",
				"gui-textured-alpha-armor-empty",
				"/assets/minecraft/textures/gui/sprites/hud/armor_empty.png",
				9,
				9,
				false
			),
			ARMOR_HALF(
				GuiRenderStratum.GUI_ARMOR,
				"armor-half",
				"gui-textured-alpha-armor-half",
				"/assets/minecraft/textures/gui/sprites/hud/armor_half.png",
				9,
				9,
				false
			),
				ARMOR_FULL(
					GuiRenderStratum.GUI_ARMOR,
					"armor-full",
					"gui-textured-alpha-armor-full",
					"/assets/minecraft/textures/gui/sprites/hud/armor_full.png",
					9,
					9,
					false
				),
				HEART_CONTAINER(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container.png",
					9,
					9,
					false
				),
				HEART_CONTAINER_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container_blinking.png",
					9,
					9,
					false
				),
				HEART_CONTAINER_HARDCORE(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container-hardcore",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore.png",
					9,
					9,
					false
				),
				HEART_CONTAINER_HARDCORE_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-container",
					"gui-textured-alpha-heart-container-hardcore-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/container_hardcore_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/full.png",
					9,
					9,
					false
				),
				HEART_NORMAL_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/full_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/half.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/half_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half.png",
					9,
					9,
					false
				),
				HEART_NORMAL_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-normal",
					"gui-textured-alpha-heart-normal-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full.png",
					9,
					9,
					false
				),
				HEART_POISONED_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_full_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half.png",
					9,
					9,
					false
				),
				HEART_POISONED_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_half_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_POISONED_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-poisoned",
					"gui-textured-alpha-heart-poisoned-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/poisoned_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_full.png",
					9,
					9,
					false
				),
				HEART_WITHERED_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_full_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_half.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_half_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_WITHERED_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-withered",
					"gui-textured-alpha-heart-withered-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/withered_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full.png",
					9,
					9,
					false
				),
				HEART_FROZEN_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_full_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_half_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_FROZEN_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"player-heart-frozen",
					"gui-textured-alpha-heart-frozen-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/frozen_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full_blinking.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half_blinking.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HARDCORE_FULL(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-hardcore-full",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HARDCORE_FULL_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-hardcore-full-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_full_blinking.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HARDCORE_HALF(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-hardcore-half",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half.png",
					9,
					9,
					false
				),
				HEART_ABSORBING_HARDCORE_HALF_FLASHING(
					GuiRenderStratum.GUI_PLAYER_HEALTH,
					"absorption-heart",
					"gui-textured-alpha-heart-absorbing-hardcore-half-flashing",
					"/assets/minecraft/textures/gui/sprites/hud/heart/absorbing_hardcore_half_blinking.png",
					9,
					9,
					false
				),
				EXPERIENCE_BAR_BACKGROUND(
			GuiRenderStratum.GUI_EXPERIENCE_BAR_BACKGROUND,
			"experience-background",
			"gui-textured-alpha-experience-background",
			"/assets/minecraft/textures/gui/sprites/hud/experience_bar_background.png",
			182,
			5,
			false
		),
			EXPERIENCE_BAR_PROGRESS(
				GuiRenderStratum.GUI_EXPERIENCE_BAR_PROGRESS,
				"experience-progress",
			"gui-textured-alpha-experience-progress",
			"/assets/minecraft/textures/gui/sprites/hud/experience_bar_progress.png",
				182,
				5,
				false
			),
			CROSSHAIR_ATTACK_FULL(
				GuiRenderStratum.GUI_ATTACK_CROSSHAIR_PROGRESS,
				"attack-crosshair-full",
				"gui-textured-alpha-attack-crosshair-full",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png",
				16,
				16,
				false
			),
			CROSSHAIR_ATTACK_BACKGROUND(
				GuiRenderStratum.GUI_ATTACK_CROSSHAIR_BACKGROUND,
				"attack-crosshair-background",
				"gui-textured-alpha-attack-crosshair-background",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png",
				16,
				4,
				false
			),
			CROSSHAIR_ATTACK_PROGRESS(
				GuiRenderStratum.GUI_ATTACK_CROSSHAIR_PROGRESS,
				"attack-crosshair-progress",
				"gui-textured-alpha-attack-crosshair-progress",
				"/assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png",
				16,
				4,
				false
			),
			HOTBAR_ATTACK_BACKGROUND(
				GuiRenderStratum.GUI_ATTACK_HOTBAR_BACKGROUND,
				"attack-hotbar-background",
				"gui-textured-alpha-attack-hotbar-background",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png",
				18,
				18,
				false
			),
			HOTBAR_ATTACK_PROGRESS(
				GuiRenderStratum.GUI_ATTACK_HOTBAR_PROGRESS,
				"attack-hotbar-progress",
				"gui-textured-alpha-attack-hotbar-progress",
				"/assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png",
				18,
				18,
				false
			),
			BOSS_BAR_PINK_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-pink-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_BLUE_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-blue-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_RED_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-red-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/red_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_GREEN_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-green-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/green_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_YELLOW_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-yellow-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/yellow_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_PURPLE_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-purple-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_WHITE_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-background",
				"gui-textured-alpha-boss-bar-white-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/white_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_PINK_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-pink-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/pink_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_BLUE_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-blue-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/blue_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_RED_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-red-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/red_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_GREEN_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-green-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/green_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_YELLOW_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-yellow-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/yellow_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_PURPLE_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-purple-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_WHITE_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-progress",
				"gui-textured-alpha-boss-bar-white-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_6_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-6-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_10_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-10-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_12_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-12-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_20_BACKGROUND(
				GuiRenderStratum.GUI_BOSS_BAR_BACKGROUND,
				"boss-bar-overlay-background",
				"gui-textured-alpha-boss-bar-notched-20-background",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_background.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_6_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-6-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_6_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_10_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-10-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_10_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_12_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-12-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_12_progress.png",
				182,
				5,
				false
			),
			BOSS_BAR_NOTCHED_20_PROGRESS(
				GuiRenderStratum.GUI_BOSS_BAR_PROGRESS,
				"boss-bar-overlay-progress",
				"gui-textured-alpha-boss-bar-notched-20-progress",
				"/assets/minecraft/textures/gui/sprites/boss_bar/notched_20_progress.png",
				182,
				5,
				false
			),
			HUNGER_EMPTY(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-empty",
				"/assets/minecraft/textures/gui/sprites/hud/food_empty.png",
				9,
				9,
				false
			),
			HUNGER_HALF(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-half",
				"/assets/minecraft/textures/gui/sprites/hud/food_half.png",
				9,
				9,
				false
			),
			HUNGER_FULL(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-full",
				"/assets/minecraft/textures/gui/sprites/hud/food_full.png",
				9,
				9,
				false
			),
			HUNGER_EFFECT_EMPTY(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-effect-empty",
				"/assets/minecraft/textures/gui/sprites/hud/food_empty_hunger.png",
				9,
				9,
				false
			),
			HUNGER_EFFECT_HALF(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-effect-half",
				"/assets/minecraft/textures/gui/sprites/hud/food_half_hunger.png",
				9,
				9,
				false
			),
			HUNGER_EFFECT_FULL(
				GuiRenderStratum.GUI_HUNGER,
				"hunger",
				"gui-textured-alpha-hunger-effect-full",
				"/assets/minecraft/textures/gui/sprites/hud/food_full_hunger.png",
				9,
				9,
				false
			),
			AIR_FULL(
				GuiRenderStratum.GUI_AIR,
				"air",
				"gui-textured-alpha-air-full",
				"/assets/minecraft/textures/gui/sprites/hud/air.png",
				9,
				9,
				false
			),
			AIR_POPPING(
				GuiRenderStratum.GUI_AIR,
				"air",
				"gui-textured-alpha-air-popping",
				"/assets/minecraft/textures/gui/sprites/hud/air_bursting.png",
				9,
				9,
				false
			),
			AIR_EMPTY(
				GuiRenderStratum.GUI_AIR,
				"air",
				"gui-textured-alpha-air-empty",
				"/assets/minecraft/textures/gui/sprites/hud/air_empty.png",
				9,
				9,
				false
			),
			HEART_VEHICLE_CONTAINER(
				GuiRenderStratum.GUI_MOUNT_HEALTH,
				"mount-health",
				"gui-textured-alpha-heart-vehicle-container",
				"/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_container.png",
				9,
				9,
				false
			),
			HEART_VEHICLE_FULL(
				GuiRenderStratum.GUI_MOUNT_HEALTH,
				"mount-health",
				"gui-textured-alpha-heart-vehicle-full",
				"/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_full.png",
				9,
				9,
				false
			),
			HEART_VEHICLE_HALF(
				GuiRenderStratum.GUI_MOUNT_HEALTH,
				"mount-health",
				"gui-textured-alpha-heart-vehicle-half",
				"/assets/minecraft/textures/gui/sprites/hud/heart/vehicle_half.png",
				9,
				9,
				false
			),
            POST_EFFECT_INVERT(
				GuiRenderStratum.GUI_POST_EFFECT,
				"post-effect",
				"gui-textured-invert-post-effect",
				"/assets/minecraft/textures/misc/rust_generated_white.png",
                1, 1, true
            ),
            POST_EFFECT_CREEPER(
                GuiRenderStratum.GUI_POST_EFFECT,
                "post-effect-creeper",
                "gui-textured-creeper-post-effect",
                "/assets/minecraft/textures/misc/rust_generated_white.png",
                1, 1, false
            ),
            POST_EFFECT_SPIDER(
                GuiRenderStratum.GUI_POST_EFFECT,
                "post-effect-spider",
                "gui-textured-spider-post-effect",
                "/assets/minecraft/textures/misc/rust_generated_white.png",
                1, 1, false
            );

		final GuiRenderStratum stratum;
		final String phaseName;
		final String cacheKind;
		final String semanticSuffix;
		final String textureResource;
		final int width;
		final int height;
		final TextureGroup textureGroup;

		GuiSprite(GuiRenderStratum stratum, String phaseName, String cacheKind, String textureResource, int width, int height, boolean invertBlend) {
			this.stratum = stratum;
			this.phaseName = phaseName;
			this.cacheKind = cacheKind;
			this.semanticSuffix = semanticSuffix(cacheKind);
			this.textureResource = textureResource;
			this.width = width;
			this.height = height;
			this.textureGroup = invertBlend ? TextureGroup.GUI_INVERT : TextureGroup.GUI_ALPHA;
		}

		int textureBytes() {
			return this.width * this.height * 4;
		}

		int semanticId() {
			return ordinal() + 1;
		}

		ResourceLocation resourceLocation() {
			String prefix = "/assets/minecraft/";
			if (!this.textureResource.startsWith(prefix)) {
				throw new IllegalStateException("unexpected GUI sprite resource path: " + this.textureResource);
			}
			return ResourceLocation.withDefaultNamespace(this.textureResource.substring(prefix.length()));
		}

		private static String semanticSuffix(String cacheKind) {
			if (cacheKind.startsWith("gui-textured-alpha-")) {
				return cacheKind.substring("gui-textured-alpha-".length()).replace('_', '-');
			}
			if (cacheKind.startsWith("gui-textured-invert-")) {
				return cacheKind.substring("gui-textured-invert-".length()).replace('_', '-');
			}
			return cacheKind.replace('_', '-');
		}
	}

}
