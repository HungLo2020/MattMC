package net.vulkanic.world;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.WorldBorderRenderState;
import net.minecraft.client.renderer.state.BlockBreakingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.bridge.RustGalVulkanWholeFrameMode;
import net.vulkanic.bridge.VulkanicGalBridge;
import net.logging.LogUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RustGalWorldPrimitiveRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int STRATUM_WORLD_BORDER = 80;
	public static final int STRATUM_WORLD_BLOCK_BREAKING_CRACK = 90;
	public static final int STRATUM_WORLD_BLOCK_OUTLINE = 100;
	public static final int STYLE_NORMAL = 1;
	public static final int STYLE_HIGH_CONTRAST = 2;
	public static final int DEPTH_POLICY_DISABLED = 0;
	public static final int DEPTH_POLICY_TEST_WRITE = 1;
	public static final int BORDER_TEXTURE_FORCEFIELD = 1;
	public static final int BORDER_BLEND_OVERLAY = 1;
	public static final int CRACK_BLEND_MULTIPLY = 1;
	public static final int CULL_NONE = 0;
	private static final float CRACK_FACE_OFFSET = 0.002F;
	private static final String DIAGNOSTIC_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldOutline.scenario", "").trim();
	private static final String DIAGNOSTIC_STYLE = System.getProperty("mattmc.dev.rustGalWorldOutline.style", "").trim();
	private static final String DIAGNOSTIC_DEPTH_POLICY = System.getProperty("mattmc.dev.rustGalWorldOutline.depthPolicy", "").trim();
	private static final boolean DIAGNOSTIC_DEPTH_PROBE = Boolean.getBoolean("mattmc.dev.rustGalWorldOutline.depthProbe");
	private static final String DIAGNOSTIC_CRACK_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldCrack.scenario", "").trim();
	private static final String DIAGNOSTIC_CRACK_STAGE = System.getProperty("mattmc.dev.rustGalWorldCrack.stage", "0").trim();
	private static final String DIAGNOSTIC_BORDER_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldBorder.scenario", "").trim();
	private static final String DIAGNOSTIC_BORDER_SCROLL = System.getProperty("mattmc.dev.rustGalWorldBorder.scrollPhase", "").trim();
	private static final ResourceLocation FORCEFIELD_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
	private static final Object LOCK = new Object();
	private static final List<VulkanicGalBridge.WorldLineSegmentRecord> PENDING_SEGMENTS = new ArrayList<>();
	private static final List<VulkanicGalBridge.WorldCrackQuadRecord> PENDING_CRACK_QUADS = new ArrayList<>();
	private static final List<VulkanicGalBridge.WorldBorderQuadRecord> PENDING_BORDER_QUADS = new ArrayList<>();
	private static final float[] PENDING_VIEW = new float[16];
	private static final float[] PENDING_PROJECTION = new float[16];
	private static VulkanicGalBridge.WorldBorderAssetRecord pendingWorldBorderAsset =
		new VulkanicGalBridge.WorldBorderAssetRecord(BORDER_TEXTURE_FORCEFIELD, new byte[0]);
	private static long worldBorderAssetGeneration = 1L;
	private static long uploadedWorldBorderAssetGeneration;
	private static long attemptedWorldBorderAssetGeneration;
	private static long lastWorldBorderAssetPayloadCount;
	private static long lastWorldBorderAssetPayloadBytes;
	private static long worldBorderAssetUpdateFailures;
	private static String lastWorldBorderAssetSourcePack = "vanilla";
	private static String lastWorldBorderAssetSha256 = "fallback";
	private static boolean lastWorldBorderAssetFallback = true;
	private static int pendingViewportWidth;
	private static int pendingViewportHeight;

	private RustGalWorldPrimitiveRenderer() {
	}

	public enum BlockOutlineRoute {
		JAVA_COMPATIBILITY,
		RUST_VULKAN_WHOLE_FRAME
	}

	public static BlockOutlineRoute selectBlockOutlineRouteForTests(boolean vulkanBackendSelected, boolean wholeFrameVulkanEnabled) {
		return vulkanBackendSelected && wholeFrameVulkanEnabled
			? BlockOutlineRoute.RUST_VULKAN_WHOLE_FRAME
			: BlockOutlineRoute.JAVA_COMPATIBILITY;
	}

	public static BlockOutlineRoute currentBlockOutlineRoute() {
		return RustGalVulkanWholeFrameMode.enabledForBackend(VulkanicAPI.isVulkanBackendSelected())
			? BlockOutlineRoute.RUST_VULKAN_WHOLE_FRAME
			: BlockOutlineRoute.JAVA_COMPATIBILITY;
	}

	public static boolean shouldUseRustWholeFrameOutline() {
		return currentBlockOutlineRoute() == BlockOutlineRoute.RUST_VULKAN_WHOLE_FRAME;
	}

	public static void reloadWorldAssets(ResourceManager resourceManager) {
		WorldBorderAssetResolution resolution = resolveWorldBorderAsset(resourceManager);
		synchronized (LOCK) {
			if (resolution.preserveLastValid()) {
				worldBorderAssetUpdateFailures++;
				auditMessage(
					"Rust VulkanicGAL world-border asset update skipped"
						+ " reason=java-read-failure"
						+ " generation=" + worldBorderAssetGeneration
						+ " uploaded_generation=" + uploadedWorldBorderAssetGeneration
						+ " failures=" + worldBorderAssetUpdateFailures
						+ " preserve_last_valid=true"
				);
				return;
			}
			worldBorderAssetGeneration++;
			pendingWorldBorderAsset = new VulkanicGalBridge.WorldBorderAssetRecord(BORDER_TEXTURE_FORCEFIELD, resolution.payload());
			attemptedWorldBorderAssetGeneration = Math.min(attemptedWorldBorderAssetGeneration, uploadedWorldBorderAssetGeneration);
			lastWorldBorderAssetPayloadCount = resolution.payload().length == 0 ? 0L : 1L;
			lastWorldBorderAssetPayloadBytes = resolution.payload().length;
			lastWorldBorderAssetSourcePack = resolution.sourcePack();
			lastWorldBorderAssetSha256 = resolution.sha256();
			lastWorldBorderAssetFallback = resolution.fallback();
			auditMessage(
				"Rust VulkanicGAL world-border asset resolved"
					+ " generation=" + worldBorderAssetGeneration
					+ " texture_id=" + BORDER_TEXTURE_FORCEFIELD
					+ " path=" + FORCEFIELD_LOCATION
					+ " source_pack=" + metricValue(lastWorldBorderAssetSourcePack)
					+ " payloads=" + lastWorldBorderAssetPayloadCount
					+ " payload_bytes=" + lastWorldBorderAssetPayloadBytes
					+ " fallback=" + lastWorldBorderAssetFallback
					+ " sha256=" + lastWorldBorderAssetSha256
			);
		}
	}

	public static VulkanicGalBridge.Status flushPendingWorldBorderAssets(VulkanicGalBridge bridge) {
		synchronized (LOCK) {
			if (bridge == null || uploadedWorldBorderAssetGeneration >= worldBorderAssetGeneration || attemptedWorldBorderAssetGeneration >= worldBorderAssetGeneration) {
				return null;
			}
			attemptedWorldBorderAssetGeneration = worldBorderAssetGeneration;
			try {
				VulkanicGalBridge.Status status = bridge.updateWorldBorderAsset(worldBorderAssetGeneration, pendingWorldBorderAsset);
				uploadedWorldBorderAssetGeneration = worldBorderAssetGeneration;
				auditMessage(
					"Rust VulkanicGAL world-border asset update accepted"
						+ " generation=" + worldBorderAssetGeneration
						+ " texture_id=" + pendingWorldBorderAsset.textureId()
						+ " payloads=" + lastWorldBorderAssetPayloadCount
						+ " payload_bytes=" + lastWorldBorderAssetPayloadBytes
						+ " source_pack=" + metricValue(lastWorldBorderAssetSourcePack)
						+ " fallback=" + lastWorldBorderAssetFallback
						+ " uploaded_generation=" + uploadedWorldBorderAssetGeneration
				);
				return status;
			} catch (RuntimeException error) {
				worldBorderAssetUpdateFailures++;
				LOGGER.error(
					"Rust VulkanicGAL world-border asset update failed for generation {}; preserving last valid texture",
					worldBorderAssetGeneration,
					error
				);
				auditMessage(
					"Rust VulkanicGAL world-border asset update failed"
						+ " generation=" + worldBorderAssetGeneration
						+ " texture_id=" + pendingWorldBorderAsset.textureId()
						+ " uploaded_generation=" + uploadedWorldBorderAssetGeneration
						+ " failures=" + worldBorderAssetUpdateFailures
						+ " preserve_last_valid=true"
				);
				return null;
			}
		}
	}

	public static WorldBorderAssetMetrics worldBorderAssetMetrics() {
		synchronized (LOCK) {
			return new WorldBorderAssetMetrics(
				worldBorderAssetGeneration,
				uploadedWorldBorderAssetGeneration,
				lastWorldBorderAssetPayloadCount,
				lastWorldBorderAssetPayloadBytes,
				worldBorderAssetUpdateFailures,
				lastWorldBorderAssetSourcePack,
				lastWorldBorderAssetSha256,
				lastWorldBorderAssetFallback
			);
		}
	}

	private static WorldBorderAssetResolution resolveWorldBorderAsset(ResourceManager resourceManager) {
		if (resourceManager == null) {
			return WorldBorderAssetResolution.fallback("missing-resource-manager");
		}
		Optional<Resource> resource = resourceManager.getResource(FORCEFIELD_LOCATION);
		if (resource.isEmpty()) {
			return WorldBorderAssetResolution.fallback("missing");
		}
		String sourcePack = resource.get().sourcePackId();
		if ("vanilla".equals(sourcePack)) {
			return WorldBorderAssetResolution.fallback("vanilla");
		}
		try (InputStream input = resource.get().open()) {
			byte[] bytes = input.readAllBytes();
			return new WorldBorderAssetResolution(bytes, sourcePack, sha256Hex(bytes), false, false);
		} catch (IOException error) {
			LOGGER.warn(
				"Failed to read Rust VulkanicGAL world-border texture {}; preserving last valid texture",
				FORCEFIELD_LOCATION,
				error
			);
			return WorldBorderAssetResolution.preserve("read-error");
		}
	}

	public static void beginFrame(Matrix4f viewMatrix, Matrix4f projectionMatrix, int viewportWidth, int viewportHeight) {
		synchronized (LOCK) {
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
			PENDING_BORDER_QUADS.clear();
			viewMatrix.get(PENDING_VIEW);
			projectionMatrix.get(PENDING_PROJECTION);
			if (!isFinite(PENDING_VIEW) || !isFinite(PENDING_PROJECTION)) {
				new Matrix4f().get(PENDING_VIEW);
				new Matrix4f().get(PENDING_PROJECTION);
				PENDING_CRACK_QUADS.clear();
				PENDING_BORDER_QUADS.clear();
				pendingViewportWidth = 0;
				pendingViewportHeight = 0;
				return;
			}
			pendingViewportWidth = viewportWidth;
			pendingViewportHeight = viewportHeight;
		}
	}

	public static void clearFrame() {
		synchronized (LOCK) {
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
			PENDING_BORDER_QUADS.clear();
			new Matrix4f().get(PENDING_VIEW);
			new Matrix4f().get(PENDING_PROJECTION);
			pendingViewportWidth = 0;
			pendingViewportHeight = 0;
		}
	}

	public static void enqueueBlockBreakingCracks(List<BlockBreakingRenderState> states, Camera camera) {
		if (!shouldUseRustWholeFrameOutline()) {
			return;
		}
		if (enqueueDiagnosticBlockBreakingCrack(camera)) {
			return;
		}
		if (states.isEmpty()) {
			return;
		}
		Vec3 cameraPos = camera.getPosition();
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				return;
			}
			for (BlockBreakingRenderState state : states) {
				if (state.progress < 0 || state.progress >= 10) {
					continue;
				}
				VoxelShape shape = state.blockState.getShape(state.level, state.blockPos, CollisionContext.of(camera.getEntity()));
				if (shape.isEmpty()) {
					shape = Shapes.block();
				}
				appendCrackShape(shape, state.blockPos, cameraPos, state.progress, viewportWidth, viewportHeight);
			}
		}
	}

	private static boolean enqueueDiagnosticBlockBreakingCrack(Camera camera) {
		if (DIAGNOSTIC_CRACK_SCENARIO.isBlank()) {
			return false;
		}
		if ("hidden".equalsIgnoreCase(DIAGNOSTIC_CRACK_SCENARIO) || "no-target".equalsIgnoreCase(DIAGNOSTIC_CRACK_SCENARIO)) {
			return true;
		}
		VoxelShape shape = diagnosticShape(DIAGNOSTIC_CRACK_SCENARIO, "Rust GAL world-crack diagnostic scenario");
		if (shape.isEmpty()) {
			return true;
		}
		int stage = diagnosticCrackStage();
		Vec3 cameraPos = camera.getPosition();
		BlockPos blockPos = diagnosticBlockPos(camera);
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				return true;
			}
			appendCrackShape(shape, blockPos, cameraPos, stage, viewportWidth, viewportHeight);
		}
		return true;
	}

	public static boolean enqueueWorldBorder(WorldBorderRenderState state, Vec3 cameraPosition, double renderDistance, double depthFar) {
		if (!shouldUseRustWholeFrameOutline()) {
			return false;
		}
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				return true;
			}
			if (enqueueDiagnosticWorldBorder(cameraPosition, renderDistance, depthFar, viewportWidth, viewportHeight)) {
				return true;
			}
			if (state.alpha <= 0.0) {
				return true;
			}
			appendVisibleWorldBorderSides(
				state.minX,
				state.maxX,
				state.minZ,
				state.maxZ,
				state.alpha,
				state.tint,
				cameraPosition,
				renderDistance,
				depthFar,
				viewportWidth,
				viewportHeight,
				false
			);
		}
		return true;
	}

	private static boolean enqueueDiagnosticWorldBorder(Vec3 cameraPosition, double renderDistance, double depthFar, int viewportWidth, int viewportHeight) {
		if (DIAGNOSTIC_BORDER_SCENARIO.isBlank()) {
			return false;
		}
		DiagnosticBorderBounds bounds = diagnosticBorderBounds(cameraPosition, renderDistance);
		if (bounds.alpha <= 0.0) {
			return true;
		}
		appendVisibleWorldBorderSides(
			bounds.minX,
			bounds.maxX,
			bounds.minZ,
			bounds.maxZ,
			bounds.alpha,
			bounds.tint,
			cameraPosition,
			renderDistance,
			depthFar,
			viewportWidth,
			viewportHeight,
			bounds.forceAllSides
		);
		return true;
	}

	public static boolean applyDiagnosticWorldBorderState(WorldBorderRenderState state, Vec3 cameraPosition, double renderDistance) {
		if (DIAGNOSTIC_BORDER_SCENARIO.isBlank()) {
			return false;
		}
		DiagnosticBorderBounds bounds = diagnosticBorderBounds(cameraPosition, renderDistance);
		state.minX = bounds.minX;
		state.maxX = bounds.maxX;
		state.minZ = bounds.minZ;
		state.maxZ = bounds.maxZ;
		state.alpha = bounds.alpha;
		state.tint = bounds.tint;
		return true;
	}

	private static DiagnosticBorderBounds diagnosticBorderBounds(Vec3 cameraPosition, double renderDistance) {
		String scenario = DIAGNOSTIC_BORDER_SCENARIO.toLowerCase(java.util.Locale.ROOT);
		double near = "corner".equals(scenario) ? 2.0 : 4.0;
		double far = Math.max(renderDistance * 4.0, 512.0);
		if ("hidden".equals(scenario) || "far".equals(scenario) || "no-target".equals(scenario)) {
			return new DiagnosticBorderBounds(
				cameraPosition.x - far,
				cameraPosition.x + far,
				cameraPosition.z - far,
				cameraPosition.z + far,
				0.0,
				0x55ff55,
				false
			);
		}
		if ("corner".equals(scenario)) {
			return new DiagnosticBorderBounds(
				cameraPosition.x - near,
				cameraPosition.x + far,
				cameraPosition.z - near,
				cameraPosition.z + far,
				0.85,
				0x55ff55,
				false
			);
		}
		if ("all-sides".equals(scenario)) {
			return new DiagnosticBorderBounds(
				cameraPosition.x - near,
				cameraPosition.x + near,
				cameraPosition.z - near,
				cameraPosition.z + near,
				0.85,
				0x55ff55,
				true
			);
		}
		return new DiagnosticBorderBounds(
			cameraPosition.x - far,
			cameraPosition.x + far,
			cameraPosition.z - near,
			cameraPosition.z + far,
			0.85,
			0x55ff55,
			false
		);
	}

	private static void appendVisibleWorldBorderSides(
		double minX,
		double maxX,
		double minZ,
		double maxZ,
		double alpha,
		int tint,
		Vec3 cameraPosition,
		double renderDistance,
		double depthFar,
		int viewportWidth,
		int viewportHeight,
		boolean forceAllSides
	) {
		double cameraX = cameraPosition.x;
		double cameraZ = cameraPosition.z;
		List<WorldBorderRenderState.DistancePerDirection> sides = new ArrayList<>();
		sides.add(new WorldBorderRenderState.DistancePerDirection(Direction.NORTH, cameraZ - minZ));
		sides.add(new WorldBorderRenderState.DistancePerDirection(Direction.SOUTH, maxZ - cameraZ));
		sides.add(new WorldBorderRenderState.DistancePerDirection(Direction.WEST, cameraX - minX));
		sides.add(new WorldBorderRenderState.DistancePerDirection(Direction.EAST, maxX - cameraX));
		sides.sort(java.util.Comparator.comparingDouble(WorldBorderRenderState.DistancePerDirection::distance));
		for (WorldBorderRenderState.DistancePerDirection side : sides) {
			if (forceAllSides || side.distance() < renderDistance) {
				appendWorldBorderSide(
					side.direction(),
					minX,
					maxX,
					minZ,
					maxZ,
					alpha,
					tint,
					cameraPosition,
					renderDistance,
					depthFar,
					(float)side.distance(),
					viewportWidth,
					viewportHeight
				);
			}
		}
	}

	private static void appendWorldBorderSide(
		Direction direction,
		double minX,
		double maxX,
		double minZ,
		double maxZ,
		double alpha,
		int tint,
		Vec3 cameraPosition,
		double renderDistance,
		double depthFar,
		float distanceToBorder,
		int viewportWidth,
		int viewportHeight
	) {
		double clippedMinZ = Math.max(Mth.floor(cameraPosition.z - renderDistance), minZ);
		double clippedMaxZ = Math.min(Mth.ceil(cameraPosition.z + renderDistance), maxZ);
		float zUvStart = (Mth.floor(clippedMinZ) & 1) * 0.5F;
		float zUvWidth = (float)(clippedMaxZ - clippedMinZ) / 2.0F;
		double clippedMinX = Math.max(Mth.floor(cameraPosition.x - renderDistance), minX);
		double clippedMaxX = Math.min(Mth.ceil(cameraPosition.x + renderDistance), maxX);
		float xUvStart = (Mth.floor(clippedMinX) & 1) * 0.5F;
		float xUvWidth = (float)(clippedMaxX - clippedMinX) / 2.0F;
		float y0 = (float)-depthFar;
		float y1 = (float)depthFar;
		float topV = (float)(-Mth.frac(cameraPosition.y * 0.5));
		float bottomV = topV + (float)depthFar;
		float scroll = diagnosticWorldBorderScroll((float)(Util.getMillis() % 3000L) / 3000.0F);
		int color = (Mth.clamp((int)Math.round(alpha * 255.0), 0, 255) << 24) | (tint & 0x00ffffff);
		float borderSize = (float)Math.max(maxX - minX, maxZ - minZ);
		switch (direction) {
			case SOUTH -> appendWorldBorderQuad(
				color,
				borderSize,
				distanceToBorder,
				scroll,
				xUvStart,
				bottomV,
				xUvWidth,
				topV - bottomV,
				(float)(clippedMinX - cameraPosition.x), y0, (float)(maxZ - cameraPosition.z),
				(float)(clippedMaxX - cameraPosition.x), y0, (float)(maxZ - cameraPosition.z),
				(float)(clippedMaxX - cameraPosition.x), y1, (float)(maxZ - cameraPosition.z),
				(float)(clippedMinX - cameraPosition.x), y1, (float)(maxZ - cameraPosition.z),
				viewportWidth,
				viewportHeight
			);
			case WEST -> appendWorldBorderQuad(
				color,
				borderSize,
				distanceToBorder,
				scroll,
				zUvStart,
				bottomV,
				zUvWidth,
				topV - bottomV,
				(float)(minX - cameraPosition.x), y0, (float)(clippedMinZ - cameraPosition.z),
				(float)(minX - cameraPosition.x), y0, (float)(clippedMaxZ - cameraPosition.z),
				(float)(minX - cameraPosition.x), y1, (float)(clippedMaxZ - cameraPosition.z),
				(float)(minX - cameraPosition.x), y1, (float)(clippedMinZ - cameraPosition.z),
				viewportWidth,
				viewportHeight
			);
			case NORTH -> appendWorldBorderQuad(
				color,
				borderSize,
				distanceToBorder,
				scroll,
				xUvStart,
				bottomV,
				xUvWidth,
				topV - bottomV,
				(float)(clippedMaxX - cameraPosition.x), y0, (float)(minZ - cameraPosition.z),
				(float)(clippedMinX - cameraPosition.x), y0, (float)(minZ - cameraPosition.z),
				(float)(clippedMinX - cameraPosition.x), y1, (float)(minZ - cameraPosition.z),
				(float)(clippedMaxX - cameraPosition.x), y1, (float)(minZ - cameraPosition.z),
				viewportWidth,
				viewportHeight
			);
			case EAST -> appendWorldBorderQuad(
				color,
				borderSize,
				distanceToBorder,
				scroll,
				zUvStart,
				bottomV,
				zUvWidth,
				topV - bottomV,
				(float)(maxX - cameraPosition.x), y0, (float)(clippedMaxZ - cameraPosition.z),
				(float)(maxX - cameraPosition.x), y0, (float)(clippedMinZ - cameraPosition.z),
				(float)(maxX - cameraPosition.x), y1, (float)(clippedMinZ - cameraPosition.z),
				(float)(maxX - cameraPosition.x), y1, (float)(clippedMaxZ - cameraPosition.z),
				viewportWidth,
				viewportHeight
			);
			default -> {
			}
		}
	}

	public static float diagnosticWorldBorderScroll(float fallback) {
		if (!DIAGNOSTIC_BORDER_SCROLL.isBlank()) {
			try {
				return Float.parseFloat(DIAGNOSTIC_BORDER_SCROLL);
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException("Rust GAL world-border diagnostic scroll phase must be a float: " + DIAGNOSTIC_BORDER_SCROLL, exception);
			}
		}
		return fallback;
	}

	private record DiagnosticBorderBounds(double minX, double maxX, double minZ, double maxZ, double alpha, int tint, boolean forceAllSides) {
	}

	public static void enqueueBlockOutline(Minecraft minecraft, GameRenderer gameRenderer, Camera camera) {
		if (!shouldUseRustWholeFrameOutline()) {
			return;
		}
		if (enqueueDiagnosticBlockOutline(camera)) {
			return;
		}
		if (minecraft.level == null || minecraft.player == null || !gameRenderer.shouldRenderBlockOutline()) {
			return;
		}
		if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult) || blockHitResult.getType() == HitResult.Type.MISS) {
			return;
		}
		BlockPos blockPos = blockHitResult.getBlockPos();
		BlockState blockState = minecraft.level.getBlockState(blockPos);
		if (blockState.isAir() || !minecraft.level.getWorldBorder().isWithinBounds(blockPos)) {
			return;
		}
		if (!mayRenderForPlayer(minecraft, blockPos, blockState)) {
			return;
		}
		CollisionContext collisionContext = CollisionContext.of(camera.getEntity());
		VoxelShape shape = blockState.getShape(minecraft.level, blockPos, collisionContext);
		if (shape.isEmpty()) {
			return;
		}
		boolean highContrast = minecraft.options.highContrastBlockOutline().get();
		Vec3 cameraPos = camera.getPosition();
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (highContrast) {
				appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_HIGH_CONTRAST, DEPTH_POLICY_TEST_WRITE, -16777216);
				appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_HIGH_CONTRAST, DEPTH_POLICY_TEST_WRITE, -11010079);
			} else {
				appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_NORMAL, DEPTH_POLICY_TEST_WRITE, 0x66000000);
			}
		}
	}

	private static boolean enqueueDiagnosticBlockOutline(Camera camera) {
		if (DIAGNOSTIC_SCENARIO.isBlank()) {
			return false;
		}
		if ("no-target".equalsIgnoreCase(DIAGNOSTIC_SCENARIO)) {
			return true;
		}
		VoxelShape shape = diagnosticShape();
		if (shape.isEmpty()) {
			return true;
		}
		int style = diagnosticStyle();
		int depthPolicy = diagnosticDepthPolicy();
		Vec3 cameraPos = camera.getPosition();
		BlockPos blockPos = diagnosticBlockPos(camera);
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				return true;
			}
			if (style == STYLE_HIGH_CONTRAST) {
				appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_HIGH_CONTRAST, depthPolicy, -16777216);
				appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_HIGH_CONTRAST, depthPolicy, -11010079);
			} else {
				appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_NORMAL, depthPolicy, 0x66000000);
			}
			if (DIAGNOSTIC_DEPTH_PROBE) {
				appendDepthProbe(camera, viewportWidth, viewportHeight, depthPolicy);
			}
		}
		return true;
	}

	private static VoxelShape diagnosticShape() {
		return diagnosticShape(DIAGNOSTIC_SCENARIO, "Rust GAL world-outline diagnostic scenario");
	}

	private static VoxelShape diagnosticShape(String scenario, String label) {
		return switch (scenario.toLowerCase(java.util.Locale.ROOT)) {
			case "full-cube", "cube" -> Shapes.block();
			case "partial-shape", "partial" -> Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
			case "disconnected-shape", "disconnected" -> Shapes.or(
				Shapes.box(0.0, 0.0, 0.0, 0.375, 0.375, 0.375),
				Shapes.box(0.625, 0.625, 0.625, 1.0, 1.0, 1.0)
			);
			default -> throw new IllegalArgumentException("unknown " + label + ": " + scenario);
		};
	}

	private static int diagnosticCrackStage() {
		try {
			int stage = Integer.parseInt(DIAGNOSTIC_CRACK_STAGE);
			if (stage < 0 || stage >= 10) {
				throw new IllegalArgumentException("Rust GAL world-crack diagnostic stage must be in 0..9: " + DIAGNOSTIC_CRACK_STAGE);
			}
			return stage;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Rust GAL world-crack diagnostic stage must be an integer: " + DIAGNOSTIC_CRACK_STAGE, exception);
		}
	}

	private static int diagnosticStyle() {
		return "high-contrast".equalsIgnoreCase(DIAGNOSTIC_STYLE) ? STYLE_HIGH_CONTRAST : STYLE_NORMAL;
	}

	private static int diagnosticDepthPolicy() {
		if ("disabled".equalsIgnoreCase(DIAGNOSTIC_DEPTH_POLICY)) {
			return DEPTH_POLICY_DISABLED;
		}
		return DEPTH_POLICY_TEST_WRITE;
	}

	private static BlockPos diagnosticBlockPos(Camera camera) {
		Vector3f look = camera.getLookVector();
		Vec3 cameraPos = camera.getPosition();
		double distance = 4.0;
		return BlockPos.containing(
			cameraPos.x() + look.x() * distance - 0.5,
			cameraPos.y() + look.y() * distance - 0.5,
			cameraPos.z() + look.z() * distance - 0.5
		);
	}

	private static void appendDepthProbe(Camera camera, int viewportWidth, int viewportHeight, int depthPolicy) {
		Vector3f look = camera.getLookVector();
		Vector3f right = new Vector3f(look).cross(new Vector3f(0.0F, 1.0F, 0.0F));
		if (right.lengthSquared() < 1.0E-4F) {
			right.set(1.0F, 0.0F, 0.0F);
		} else {
			right.normalize();
		}
		Vector3f up = new Vector3f(right).cross(look).normalize();
		Vec3 cameraPos = camera.getPosition();
		appendCameraRelativeSegment(cameraPos, look, right, up, 2.0F, -0.35F, -0.20F, 0.35F, -0.20F, viewportWidth, viewportHeight, STYLE_NORMAL, depthPolicy, 0xff00ff00);
		appendCameraRelativeSegment(cameraPos, look, right, up, 3.0F, -0.525F, -0.30F, 0.525F, -0.30F, viewportWidth, viewportHeight, STYLE_NORMAL, depthPolicy, 0xffff00ff);
	}

	private static void appendCameraRelativeSegment(
		Vec3 cameraPos,
		Vector3f look,
		Vector3f right,
		Vector3f up,
		float depth,
		float startRight,
		float startUp,
		float endRight,
		float endUp,
		int viewportWidth,
		int viewportHeight,
		int style,
		int depthPolicy,
		int color
	) {
		Vector3f start = new Vector3f(look).mul(depth).fma(startRight, right).fma(startUp, up);
		Vector3f end = new Vector3f(look).mul(depth).fma(endRight, right).fma(endUp, up);
		PENDING_SEGMENTS.add(new VulkanicGalBridge.WorldLineSegmentRecord(
			STRATUM_WORLD_BLOCK_OUTLINE,
			style,
			depthPolicy,
			color,
			1.0F,
			start.x(),
			start.y(),
			start.z(),
			end.x(),
			end.y(),
			end.z(),
			viewportWidth,
			viewportHeight
		));
	}

	private static void appendShapeEdges(
		VoxelShape shape,
		BlockPos blockPos,
		Vec3 cameraPos,
		int viewportWidth,
		int viewportHeight,
		int style,
		int depthPolicy,
		int color
	) {
			shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> PENDING_SEGMENTS.add(
				new VulkanicGalBridge.WorldLineSegmentRecord(
					STRATUM_WORLD_BLOCK_OUTLINE,
					style,
					depthPolicy,
					color,
					1.0F,
					(float)(blockPos.getX() + x0 - cameraPos.x()),
					(float)(blockPos.getY() + y0 - cameraPos.y()),
					(float)(blockPos.getZ() + z0 - cameraPos.z()),
				(float)(blockPos.getX() + x1 - cameraPos.x()),
				(float)(blockPos.getY() + y1 - cameraPos.y()),
				(float)(blockPos.getZ() + z1 - cameraPos.z()),
				viewportWidth,
				viewportHeight
			)
		));
	}

	private static void appendCrackBoxFaces(
		BlockPos blockPos,
		Vec3 cameraPos,
		int stage,
		int viewportWidth,
		int viewportHeight,
		double minX,
		double minY,
		double minZ,
		double maxX,
		double maxY,
		double maxZ
	) {
		float x0 = (float)(blockPos.getX() + minX - cameraPos.x());
		float y0 = (float)(blockPos.getY() + minY - cameraPos.y());
		float z0 = (float)(blockPos.getZ() + minZ - cameraPos.z());
		float x1 = (float)(blockPos.getX() + maxX - cameraPos.x());
		float y1 = (float)(blockPos.getY() + maxY - cameraPos.y());
		float z1 = (float)(blockPos.getZ() + maxZ - cameraPos.z());
		appendCrackQuad(stage, viewportWidth, viewportHeight, x0, y1, z0 - CRACK_FACE_OFFSET, x1, y1, z0 - CRACK_FACE_OFFSET, x1, y0, z0 - CRACK_FACE_OFFSET, x0, y0, z0 - CRACK_FACE_OFFSET);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x1, y1, z1 + CRACK_FACE_OFFSET, x0, y1, z1 + CRACK_FACE_OFFSET, x0, y0, z1 + CRACK_FACE_OFFSET, x1, y0, z1 + CRACK_FACE_OFFSET);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x1 + CRACK_FACE_OFFSET, y1, z0, x1 + CRACK_FACE_OFFSET, y1, z1, x1 + CRACK_FACE_OFFSET, y0, z1, x1 + CRACK_FACE_OFFSET, y0, z0);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x0 - CRACK_FACE_OFFSET, y1, z1, x0 - CRACK_FACE_OFFSET, y1, z0, x0 - CRACK_FACE_OFFSET, y0, z0, x0 - CRACK_FACE_OFFSET, y0, z1);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x0, y1 + CRACK_FACE_OFFSET, z1, x1, y1 + CRACK_FACE_OFFSET, z1, x1, y1 + CRACK_FACE_OFFSET, z0, x0, y1 + CRACK_FACE_OFFSET, z0);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x0, y0 - CRACK_FACE_OFFSET, z0, x1, y0 - CRACK_FACE_OFFSET, z0, x1, y0 - CRACK_FACE_OFFSET, z1, x0, y0 - CRACK_FACE_OFFSET, z1);
	}

	private static void appendCrackShape(
		VoxelShape shape,
		BlockPos blockPos,
		Vec3 cameraPos,
		int stage,
		int viewportWidth,
		int viewportHeight
	) {
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> appendCrackBoxFaces(
			blockPos,
			cameraPos,
			stage,
			viewportWidth,
			viewportHeight,
			minX,
			minY,
			minZ,
			maxX,
			maxY,
			maxZ
		));
	}

	private static void appendCrackQuad(
		int stage,
		int viewportWidth,
		int viewportHeight,
		float p0x,
		float p0y,
		float p0z,
		float p1x,
		float p1y,
		float p1z,
		float p2x,
		float p2y,
		float p2z,
		float p3x,
		float p3y,
		float p3z
	) {
		PENDING_CRACK_QUADS.add(new VulkanicGalBridge.WorldCrackQuadRecord(
			STRATUM_WORLD_BLOCK_BREAKING_CRACK,
			stage,
			DEPTH_POLICY_TEST_WRITE,
			CRACK_BLEND_MULTIPLY,
			CULL_NONE,
			0xFFFFFFFF,
			new float[] {
				p0x, p0y, p0z,
				p1x, p1y, p1z,
				p2x, p2y, p2z,
				p3x, p3y, p3z
			},
			viewportWidth,
				viewportHeight
			));
	}

	private static void appendWorldBorderQuad(
		int color,
		float borderSize,
		float distanceToBorder,
		float scroll,
		float uvU,
		float uvV,
		float uvWidth,
		float uvHeight,
		float p0x,
		float p0y,
		float p0z,
		float p1x,
		float p1y,
		float p1z,
		float p2x,
		float p2y,
		float p2z,
		float p3x,
		float p3y,
		float p3z,
		int viewportWidth,
		int viewportHeight
	) {
		PENDING_BORDER_QUADS.add(new VulkanicGalBridge.WorldBorderQuadRecord(
			STRATUM_WORLD_BORDER,
			BORDER_TEXTURE_FORCEFIELD,
			DEPTH_POLICY_TEST_WRITE,
			BORDER_BLEND_OVERLAY,
			CULL_NONE,
			color,
			borderSize,
			Math.max(distanceToBorder, 0.0F),
			scroll,
			scroll,
			uvU,
			uvV,
			uvWidth,
			uvHeight,
			new float[] {
				p0x, p0y, p0z,
				p1x, p1y, p1z,
				p2x, p2y, p2z,
				p3x, p3y, p3z
			},
			viewportWidth,
			viewportHeight
		));
	}

	private static boolean isFinite(float[] values) {
		for (float value : values) {
			if (!Float.isFinite(value)) {
				return false;
			}
		}
		return true;
	}

	private static String sha256Hex(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 digest is unavailable", error);
		}
	}

	private static String metricValue(String value) {
		return value == null || value.isBlank() ? "unset" : value.replaceAll("\\s+", "_");
	}

	private static void auditMessage(String message) {
		if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			System.out.println("[MattMC graphics audit] " + message);
		}
	}

	public static PrimitiveFrame consumeFrame() {
		synchronized (LOCK) {
			PrimitiveFrame frame = new PrimitiveFrame(
				pendingViewportWidth,
				pendingViewportHeight,
					PENDING_VIEW.clone(),
					PENDING_PROJECTION.clone(),
					List.copyOf(PENDING_SEGMENTS),
					List.copyOf(PENDING_CRACK_QUADS),
					List.copyOf(PENDING_BORDER_QUADS)
				);
				PENDING_SEGMENTS.clear();
				PENDING_CRACK_QUADS.clear();
				PENDING_BORDER_QUADS.clear();
				return frame;
			}
		}

	private static boolean mayRenderForPlayer(Minecraft minecraft, BlockPos blockPos, BlockState blockState) {
		if (minecraft.player.getAbilities().mayBuild) {
			return true;
		}
		ItemStack itemStack = minecraft.player.getMainHandItem();
		if (minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			return blockState.getMenuProvider(minecraft.level, blockPos) != null;
		}
		BlockInWorld blockInWorld = new BlockInWorld(minecraft.level, blockPos, false);
		return !itemStack.isEmpty()
			&& (itemStack.canBreakBlockInAdventureMode(blockInWorld) || itemStack.canPlaceOnBlockInAdventureMode(blockInWorld));
	}

	public record PrimitiveFrame(
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
			float[] projectionMatrix,
			List<VulkanicGalBridge.WorldLineSegmentRecord> segments,
			List<VulkanicGalBridge.WorldCrackQuadRecord> crackQuads,
			List<VulkanicGalBridge.WorldBorderQuadRecord> borderQuads
		) {
		}

	public record WorldBorderAssetMetrics(
		long generation,
		long uploadedGeneration,
		long payloadCount,
		long payloadBytes,
		long failures,
		String sourcePack,
		String sha256,
		boolean fallback
	) {
	}

	private record WorldBorderAssetResolution(byte[] payload, String sourcePack, String sha256, boolean fallback, boolean preserveLastValid) {
		private static WorldBorderAssetResolution fallback(String sourcePack) {
			return new WorldBorderAssetResolution(new byte[0], sourcePack, "fallback", true, false);
		}

		private static WorldBorderAssetResolution preserve(String sourcePack) {
			return new WorldBorderAssetResolution(new byte[0], sourcePack, "preserve-last-valid", true, true);
		}
	}
	}
