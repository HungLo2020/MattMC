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
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.blaze3d.vertex.PoseStack;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

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
	private static final String PROFILER_CHART_PRODUCER = "minecraft.gui.profiler-chart";

	private RustGalGuiRenderer() {
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
			// must never authorize Java GUI rendering after Vulkan selection.
			return this.javaCompatibility && !VulkanicAPI.isVulkanBackendSelected();
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
	 * preserves the normal Java text path for unsupported state.
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
		if (!TEXT_ROUTE_ENABLED || !currentExecutionRoute().usesRustGui()) {
			return null;
		}
		List<TextGlyphQuad> quads = new ArrayList<>();
		Font.SemanticTextExtraction extraction = textState.ensurePrepared().collectSemanticQuads(quads::add);
		if (extraction.unsupportedRenderableCount() != 0) {
			recordTextRouteDiagnostic("unsupported-renderables=" + extraction.unsupportedRenderableCount()
				+ " renderables=" + extraction.renderableCount() + " quads=" + extraction.quadCount());
			return null;
		}
		// A single text node commonly contains many glyphs from the same atlas.
		// Keep one staging request per semantic atlas while preserving the glyph
		// order in `requests`; stageTextAtlas itself remains generation-aware.
		List<TextAtlasRequest> atlasRequests = new ArrayList<>();
		Set<Long> stagedAtlasIds = new HashSet<>();
		List<VulkanicGalBridge.GuiAffineQuadRecord> requests = new ArrayList<>(quads.size());
		try {
			for (TextGlyphQuad quad : quads) {
				if (!isParallelogram(quad)) {
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
					RustGalGuiRawImageAssets.stage(raw);
					assetId = raw.assetId();
				}
				requests.add(transformTextQuad(quad, textState.pose, assetId, guiWidth, guiHeight, textState.scissor));
			}
		} catch (RuntimeException error) {
			LOGGER.debug("Rust GUI text semantic extraction declined", error);
			recordTextRouteDiagnostic("extraction-error=" + error.getClass().getSimpleName());
			return null;
		}

		for (TextAtlasRequest atlasRequest : atlasRequests) {
			stageTextAtlas(atlasRequest.assetId(), atlasRequest.atlas());
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
		RustGalGuiRawImageAssets.stage(sky);
		RustGalGuiRawImageAssets.stage(portal);
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
			batches.add(endPortalBatch(GuiRenderStratum.GUI_FILE_BACKED_BLIT.order() + layer, layer, portal.assetId(), colors[layer - 1],
				uv[0], uv[1], uv[2], uv[3], guiWidth, guiHeight, uv));
		}
		long startedNanos = System.nanoTime();
		RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiMeshItemRequest(
			batches, "gui.semantic.layer." + sourceLayerOrder, dynamicLayerOrder(sourceLayerOrder), startedNanos);
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
			|| pose == null || guiWidth <= 0 || guiHeight <= 0 || sourceLayerOrder < 0
			|| !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
			|| !Float.isFinite(u0) || !Float.isFinite(u1) || !Float.isFinite(v0) || !Float.isFinite(v1)
			|| width <= 0.0F || height <= 0.0F) return null;
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(source);
		if (asset == null) return null;
		RustGalGuiRawImageAssets.stage(asset);
		float x0 = pose.m00() * x + pose.m10() * y + pose.m20();
		float y0 = pose.m01() * x + pose.m11() * y + pose.m21();
		float x1 = pose.m00() * (x + width) + pose.m10() * y + pose.m20();
		float y1 = pose.m01() * (x + width) + pose.m11() * y + pose.m21();
		float x3 = pose.m00() * x + pose.m10() * (y + height) + pose.m20();
		float y3 = pose.m01() * x + pose.m11() * (y + height) + pose.m21();
		float x2 = x1 + x3 - x0, y2 = y1 + y3 - y0;
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
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME) {
			return null;
		}
		if (rectangle.col1() != rectangle.col2()) {
			return tryEnqueueVerticalGradientRectangle(rectangle, guiWidth, guiHeight, dynamicLayerOrder);
		}
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA
		));
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
			requestLayerOrder = 795;
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
		if (guiWidth <= 0 || guiHeight <= 0) return null;
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
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA
		));
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
		RustGalFrameCoordinator.stageGuiRawImage(new VulkanicGalBridge.GuiRawImageAssetRecord(
			SOLID_WHITE_ASSET_ID, 2, 1, 1, SOLID_WHITE_RGBA));
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
		return tryEnqueueModelPip(model, texture, x0, y0, x1, y1, scale, pose, clip, sourceLayerOrder, setup, tint, 1);
	}

	/** Model PIP capture with an explicit Rust GUI material mode (for glint). */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueModelPip(
		Model<?> model, ResourceLocation texture, int x0, int y0, int x1, int y1, float scale,
		Matrix3x2f pose, @Nullable ScreenRectangle clip, @Nullable Integer sourceLayerOrder,
		GuiModelPipSemanticCollector.ModelPose setup, int tint, int materialMode
	) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME) return null;
		int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
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
		ScreenRectangle bounds = result.bounds();
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_ITEM,
			"minecraft.gui.model-pip", -1, -1.0F, GuiFillDirection.NONE,
			bounds.left(), bounds.top(), bounds.width(), bounds.height(), guiWidth, guiHeight));
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
		ResourceLocation baseTexture = net.minecraft.client.resources.model.ModelBakery.BANNER_BASE.texture();
		GuiModelPipSemanticCollector.ModelPose setup = pose -> pose.translate(0.0F, 0.25F, 0.0F);
		GuiModelPipSemanticCollector.Result base = GuiModelPipSemanticCollector.collect(
			banner.flag(), baseTexture, banner.x0(), banner.y0(), banner.x1(), banner.y1(), banner.scale(), guiScale,
			guiWidth, guiHeight, new float[] {1,0,0,1,0,0}, banner.scissorArea(), setup,
			banner.baseColor().getTextureDiffuseColor());
		if (base == null) return null;
		batches.add(base.batch());
		int patternCount = Math.min(16, banner.resultBannerPatterns().layers().size());
		for (int index = 0; index < patternCount; index++) {
			net.minecraft.world.level.block.entity.BannerPatternLayers.Layer layer = banner.resultBannerPatterns().layers().get(index);
			GuiModelPipSemanticCollector.Result pattern = GuiModelPipSemanticCollector.collect(
				banner.flag(), net.minecraft.client.renderer.Sheets.getBannerMaterial(layer.pattern()).texture(),
				banner.x0(), banner.y0(), banner.x1(), banner.y1(), banner.scale(), guiScale, guiWidth, guiHeight,
				new float[] {1,0,0,1,0,0}, banner.scissorArea(), setup, layer.color().getTextureDiffuseColor());
			if (pattern == null) return null;
			batches.add(pattern.batch());
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
		return List.of(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_ITEM, "minecraft.gui.banner", -1, -1.0F,
			GuiFillDirection.NONE, banner.x0(), banner.y0(), banner.x1() - banner.x0(), banner.y1() - banner.y0(), guiWidth, guiHeight));
	}

	/** Admits living-entity GUI previews through copied model/material semantics. */
	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueEntityPip(GuiEntityRenderState entityPip, @Nullable Integer sourceLayerOrder) {
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME || entityPip == null) return null;
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		var renderer = dispatcher.getRenderer(entityPip.renderState());
		if (!(renderer instanceof LivingEntityRenderer<?, ?, ?>)) return null;
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
		return tryEnqueueModelPip(living.getModel(), texture, entityPip.x0(), entityPip.y0(), entityPip.x1(), entityPip.y1(),
			entityPip.scale(), new Matrix3x2f(), entityPip.scissorArea(), sourceLayerOrder, setup);
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
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
			|| (blit.pipeline() != RenderPipelines.GUI_TEXTURED
				&& blit.pipeline() != RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND
				&& blit.pipeline() != RenderPipelines.BLOCK_SCREEN_EFFECT
				&& blit.pipeline() != RenderPipelines.FIRE_SCREEN_EFFECT
				&& blit.pipeline() != RenderPipelines.VIGNETTE
				&& blit.pipeline() != RenderPipelines.CROSSHAIR
				&& blit.pipeline() != RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA
				&& blit.pipeline() != RenderPipelines.GUI_NAUSEA_OVERLAY
				&& blit.pipeline() != RenderPipelines.MOJANG_LOGO)
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
		RustGalGuiRawImageAssets.stage(asset);
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
		}
		boolean invertBlend = blit.pipeline() == RenderPipelines.CROSSHAIR;
		boolean premultipliedBlend = blit.pipeline() == RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA;
		boolean additiveBlend = blit.pipeline() == RenderPipelines.GUI_NAUSEA_OVERLAY;
		if (!admissibleAffineQuad(requestLayerOrder, asset.assetId(), x0, y0, x1, y1, x3, y3,
			u0, v0, u1, v1, guiWidth, guiHeight, blit.scissorArea())) {
			// Do not coerce UVs, clips, or non-finite coordinates into a different
			// image. This producer is unavailable until it can provide the explicit
			// affine contract that the Rust backend consumes.
			recordTextRouteDiagnostic("copied-blit-outside-affine-contract");
			return null;
		}
		VulkanicGalBridge.GuiAffineQuadRecord request = new VulkanicGalBridge.GuiAffineQuadRecord(
			invertBlend ? GuiRenderStratum.GUI_CROSSHAIR.order()
				: (premultipliedBlend ? 790 : (additiveBlend ? 795 : requestLayerOrder)),
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
		int left = (int)Math.floor(Math.min(Math.min(x0, x1), Math.min(x2, x3)));
		int top = (int)Math.floor(Math.min(Math.min(y0, y1), Math.min(y2, y3)));
		int width = Math.max(1, (int)Math.ceil(Math.max(Math.max(x0, x1), Math.max(x2, x3)) - left));
		int height = Math.max(1, (int)Math.ceil(Math.max(Math.max(y0, y1), Math.max(y2, y3)) - top));
		return List.of(new RustGalGuiElementRenderState(
			token, GuiRenderStratum.GUI_FILE_BACKED_BLIT, "minecraft.gui.file-backed-blit", -1, -1.0F, GuiFillDirection.NONE,
			left, top, width, height, guiWidth, guiHeight
		));
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
			&& blit.pipeline() != RenderPipelines.MOJANG_LOGO) return "pipeline:" + blit.pipeline();
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
		RustGalGuiRawImageAssets.stage(asset);
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
		RustGalGuiRawImageAssets.registerDynamicTexture(identity, texture);
		if (!RustGalGuiRawImageAssets.stageDynamicTexture(texture)) return null;
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolve(identity);
		if (asset == null) return null;
		return enqueueAffineAsset(asset, x0, y0, x1, y1, x3, y3, u0, v0, u1, v1,
			color, guiWidth, guiHeight, dynamicLayerOrder, null, "dynamic-quad");
	}

	@Nullable
	private static List<RustGalGuiElementRenderState> enqueueAffineAsset(
		RustGalGuiRawImageAssets.Asset asset, float x0, float y0, float x1, float y1, float x3, float y3,
		float u0, float v0, float u1, float v1, int color, int guiWidth, int guiHeight, int dynamicLayerOrder,
		@Nullable ScreenRectangle clip, String producerSuffix
	) {
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

	@Nullable
	public static List<RustGalGuiElementRenderState> tryEnqueueTiledCopiedBlit(
		TiledBlitRenderState blit, int guiWidth, int guiHeight, int dynamicLayerOrder
	) {
		int width = blit.x1() - blit.x0();
		int height = blit.y1() - blit.y0();
		if (currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
			|| (blit.pipeline() != RenderPipelines.GUI_TEXTURED
				&& blit.pipeline() != RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND
				&& blit.pipeline() != RenderPipelines.VIGNETTE)
			|| blit.semanticTexture() == null
			|| !semanticSingleTexture(blit.textureSetup(), blit.semanticTexture())
			|| blit.tileWidth() <= 0 || blit.tileHeight() <= 0 || width <= 0 || height <= 0
			|| !Float.isFinite(blit.u0()) || !Float.isFinite(blit.v0())
			|| !Float.isFinite(blit.u1()) || !Float.isFinite(blit.v1())
			|| blit.u1() < blit.u0() || blit.v1() < blit.v0()
			|| blit.u1() - blit.u0() <= 0.0F || blit.u1() - blit.u0() > 4096.0F
			|| blit.v1() - blit.v0() <= 0.0F || blit.v1() - blit.v0() > 4096.0F
			|| (long)((width + blit.tileWidth() - 1) / blit.tileWidth()) * ((height + blit.tileHeight() - 1) / blit.tileHeight()) > 4096L) {
			return null;
		}
		RustGalGuiRawImageAssets.Asset asset = RustGalGuiRawImageAssets.resolveAtlas(blit.semanticTexture());
		if (asset == null) asset = RustGalGuiRawImageAssets.resolve(blit.semanticTexture());
		if (asset == null) return null;
		RustGalGuiRawImageAssets.stage(asset);
		int requestLayerOrder = dynamicLayerOrder(dynamicLayerOrder);
		if (blit.pipeline() == RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND) {
			requestLayerOrder = GuiRenderStratum.GUI_OPAQUE_BLIT.order();
		} else if (blit.pipeline() == RenderPipelines.VIGNETTE) {
			requestLayerOrder = GuiRenderStratum.GUI_VIGNETTE_BLIT.order();
		}
		long startedNanos = System.nanoTime();
		List<RustGalGuiElementRenderState> elements = new ArrayList<>();
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
						RustGalFrameScheduler.Token token = RustGalFrameCoordinator.enqueueGuiAffineQuadRequest(
							request, dynamicLayerId(dynamicLayerOrder), requestLayerOrder, startedNanos
						);
						elements.add(new RustGalGuiElementRenderState(token, GuiRenderStratum.GUI_FILE_BACKED_BLIT,
							"minecraft.gui.tiled-blit", -1, -1.0F, GuiFillDirection.NONE,
							(int)Math.floor(segmentLeft), (int)Math.floor(segmentTop),
							Math.max(1, (int)Math.ceil(segmentRight - segmentLeft)),
							Math.max(1, (int)Math.ceil(segmentBottom - segmentTop)), guiWidth, guiHeight));
					}
				}
			}
		}
		return List.copyOf(elements);
	}

	/**
	 * Splits one positive UV interval into unit-wrapped pieces. Repeated GUI
	 * textures can span more than one turn, so this deliberately emits more than
	 * two pieces when necessary. Returned values are geometry fractions followed by normalized UV bounds:
	 * {@code geometryStart, geometryEnd, uvStart, uvEnd}.
	 */
	private static List<float[]> wrappedUnitIntervalSegments(float start, float span) {
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
		if (!TEXT_ROUTE_DIAGNOSTICS_ENABLED || TEXT_ROUTE_DIAGNOSTICS.putIfAbsent(detail, Boolean.TRUE) != null) {
			return;
		}
		RustGalFrameCoordinator.auditMessage("gui.text.route " + detail);
	}

	/** Records a bounded semantic-coverage miss without reopening Java GUI draws. */
	public static void recordUnsupportedElement(String elementKind) {
		if (elementKind == null || elementKind.isBlank()) {
			elementKind = "unknown";
		}
		synchronized (RustGalGuiRenderer.class) {
			if (isWholeFrameVulkanActive()) {
				wholeFrameUnsupportedElementCount++;
				WHOLE_FRAME_UNSUPPORTED_ELEMENTS.merge(elementKind, 1, Integer::sum);
			}
		}
		recordTextRouteDiagnostic("unsupported-element=" + elementKind);
	}

	/** Adds bounded detail to the current unsupported-family diagnostic without changing its count. */
	public static void recordUnsupportedElementDetail(String detail) {
		if (detail == null || detail.isBlank()) return;
		synchronized (RustGalGuiRenderer.class) {
			if (isWholeFrameVulkanActive()) {
				WHOLE_FRAME_UNSUPPORTED_ELEMENTS.merge(detail, 1, Integer::sum);
			}
		}
	}

	/** Starts the explicit GUI admission window for one whole-frame Vulkan frame. */
	public static synchronized void beginWholeFrameVulkanFrame() {
		wholeFrameUnsupportedElementCount = 0;
		WHOLE_FRAME_UNSUPPORTED_ELEMENTS.clear();
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
		String previous = TEXT_ATLAS_IDENTITIES.putIfAbsent(hash, key);
		if (previous != null && !previous.equals(key)) {
			throw new IllegalStateException("semantic text atlas identity collision");
		}
		return hash;
	}

	private static synchronized void stageTextAtlas(long assetId, FontTexture.SemanticAtlasSnapshot atlas) {
		String key = atlas.identity() + (atlas.colored() ? "#rgba" : "#alpha");
		TextAtlasGeneration generation = new TextAtlasGeneration(atlas.generation(), atlas.revision());
		if (generation.equals(TEXT_ATLAS_GENERATIONS.get(key))) {
			return;
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
			isWholeFrameVulkanActive(),
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
		if (vulkanBackendSelected) {
			return wholeFrameVulkanEnabled
				? GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME
				: GuiExecutionRoute.DISABLED;
		}
		return GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT;
	}

	public static boolean shouldDrawJavaCompatibilityGui() {
		return currentExecutionRoute().usesJavaCompatibility();
	}

	private static boolean legacyControlEnabled(String property) {
		return Boolean.getBoolean(property) && !isWholeFrameVulkanActive();
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
		if (minecraft == null || guiGraphics == null || !isWholeFrameVulkanActive()) return;
		enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.POST_EFFECT_INVERT,
			"post-effect.invert", -1, -1.0F, GuiFillDirection.NONE,
			0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight());
    }

    /** Queues creeper vision as a semantic effect marker for Rust admission. */
    public static void enqueuePostEffectCreeper(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics) {
        if (minecraft == null || guiGraphics == null || !isWholeFrameVulkanActive()) return;
        enqueueGuiSprite(minecraft, guiGraphics, GuiSprite.POST_EFFECT_CREEPER,
            "post-effect.creeper", -1, -1.0F, GuiFillDirection.NONE,
            0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight());
    }

    /** Queues spider vision as a semantic effect marker for Rust admission. */
    public static void enqueuePostEffectSpider(Minecraft minecraft, net.minecraft.client.gui.GuiGraphics guiGraphics) {
        if (minecraft == null || guiGraphics == null || !isWholeFrameVulkanActive()) return;
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
