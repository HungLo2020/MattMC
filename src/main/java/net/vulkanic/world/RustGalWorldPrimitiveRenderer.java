package net.vulkanic.world;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.BlockBreakingRenderState;
import net.minecraft.core.BlockPos;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class RustGalWorldPrimitiveRenderer {
	public static final int STRATUM_WORLD_BLOCK_BREAKING_CRACK = 90;
	public static final int STRATUM_WORLD_BLOCK_OUTLINE = 100;
	public static final int STYLE_NORMAL = 1;
	public static final int STYLE_HIGH_CONTRAST = 2;
	public static final int DEPTH_POLICY_DISABLED = 0;
	public static final int DEPTH_POLICY_TEST_WRITE = 1;
	public static final int CRACK_BLEND_MULTIPLY = 1;
	public static final int CULL_NONE = 0;
	private static final float CRACK_FACE_OFFSET = 0.002F;
	private static final String DIAGNOSTIC_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldOutline.scenario", "").trim();
	private static final String DIAGNOSTIC_STYLE = System.getProperty("mattmc.dev.rustGalWorldOutline.style", "").trim();
	private static final String DIAGNOSTIC_DEPTH_POLICY = System.getProperty("mattmc.dev.rustGalWorldOutline.depthPolicy", "").trim();
	private static final boolean DIAGNOSTIC_DEPTH_PROBE = Boolean.getBoolean("mattmc.dev.rustGalWorldOutline.depthProbe");
	private static final String DIAGNOSTIC_CRACK_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldCrack.scenario", "").trim();
	private static final String DIAGNOSTIC_CRACK_STAGE = System.getProperty("mattmc.dev.rustGalWorldCrack.stage", "0").trim();
	private static final Object LOCK = new Object();
	private static final List<VulkanicGalBridge.WorldLineSegmentRecord> PENDING_SEGMENTS = new ArrayList<>();
	private static final List<VulkanicGalBridge.WorldCrackQuadRecord> PENDING_CRACK_QUADS = new ArrayList<>();
	private static final float[] PENDING_VIEW = new float[16];
	private static final float[] PENDING_PROJECTION = new float[16];
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

	public static void beginFrame(Matrix4f viewMatrix, Matrix4f projectionMatrix, int viewportWidth, int viewportHeight) {
		synchronized (LOCK) {
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
			viewMatrix.get(PENDING_VIEW);
			projectionMatrix.get(PENDING_PROJECTION);
			if (!isFinite(PENDING_VIEW) || !isFinite(PENDING_PROJECTION)) {
				new Matrix4f().get(PENDING_VIEW);
				new Matrix4f().get(PENDING_PROJECTION);
				PENDING_CRACK_QUADS.clear();
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

	private static boolean isFinite(float[] values) {
		for (float value : values) {
			if (!Float.isFinite(value)) {
				return false;
			}
		}
		return true;
	}

	public static PrimitiveFrame consumeFrame() {
		synchronized (LOCK) {
			PrimitiveFrame frame = new PrimitiveFrame(
				pendingViewportWidth,
				pendingViewportHeight,
				PENDING_VIEW.clone(),
				PENDING_PROJECTION.clone(),
				List.copyOf(PENDING_SEGMENTS),
				List.copyOf(PENDING_CRACK_QUADS)
			);
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
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
		List<VulkanicGalBridge.WorldCrackQuadRecord> crackQuads
	) {
	}
}
