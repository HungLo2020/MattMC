package net.vulkanic.bridge;

import net.vulkanic.gui.AbsorptionHeartRequest;
import net.vulkanic.gui.AbsorptionHeartVariant;
import net.vulkanic.gui.AirBubbleRequest;
import net.vulkanic.gui.AirBubbleState;
import net.vulkanic.gui.ArmorIconState;
import net.vulkanic.gui.GuiHeartState;
import net.vulkanic.gui.HungerIconRequest;
import net.vulkanic.gui.HungerIconState;
import net.vulkanic.gui.HungerIconVariant;
import net.vulkanic.gui.MountHeartRequest;
import net.vulkanic.gui.MountHeartState;
import net.vulkanic.gui.MountHeartVariant;
import net.vulkanic.gui.PlayerHeartRequest;
import net.vulkanic.gui.PlayerHeartVariant;
import net.vulkanic.gui.RustGalFrameCoordinator;
import net.vulkanic.gui.RustGalGuiRenderer;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.WorldRenderRoutePolicy;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanicGalBridgeAbiTest {
	private static String readRustFfiModules() throws Exception {
		Path root = Path.of("src/main/rust/render/vulkanic/ffi");
		StringBuilder source = new StringBuilder();
		try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths
				.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".rs"))
				.sorted()
				.toList()) {
				source.append(Files.readString(path)).append('\n');
			}
		}
		return source.toString();
	}

	@Test
	void javaLayoutsAreQueriedFromRustAbi() {
		for (VulkanicGalBridge.Struct struct : VulkanicGalBridge.Struct.values()) {
			assertTrue(struct.byteSize() > 0, "byte size should be reported for " + struct);
			assertTrue(struct.alignment() > 0, "alignment should be reported for " + struct);
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment segment = struct.allocate(arena);
				assertEquals(struct.byteSize(), segment.byteSize(), "allocation should use Rust byte size for " + struct);
			}
		}
	}

	@Test
	void malformedBackendKindIsRejectedDeterministically() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment request = VulkanicGalBridge.Struct.CONTEXT_CREATE.allocate(arena);
			VulkanicGalBridge.Abi.writeHeader(request, VulkanicGalBridge.Struct.CONTEXT_CREATE);
			VulkanicGalBridge.Struct.CONTEXT_CREATE.setInt(request, 1, 9999);
			VulkanicGalBridge.Struct.CONTEXT_CREATE.setInt(request, 2, 0);
			VulkanicGalBridge.Abi.writeBytes(arena, request, VulkanicGalBridge.Struct.CONTEXT_CREATE, 3, "bad-backend");
			MemorySegment result = VulkanicGalBridge.Struct.CONTEXT_RESULT.allocate(arena);

			int status = VulkanicGalBridge.Native.contextCreate(request, result);

			assertEquals(-5, status);
			assertEquals(-5, VulkanicGalBridge.Struct.CONTEXT_RESULT.getInt(result, 1));
		}
	}

	@Test
	void rustGalGuiRoutePolicySeparatesOpenGlJavaVulkanAndWholeFrameVulkan() {
		assertEquals(
			RustGalGuiRenderer.GuiExecutionRoute.RUST_OPENGL_BORROWED_CONTEXT,
			RustGalGuiRenderer.selectExecutionRouteForTests(false, false, false, false)
		);
		assertEquals(
			RustGalGuiRenderer.GuiExecutionRoute.JAVA_COMPATIBILITY,
			RustGalGuiRenderer.selectExecutionRouteForTests(true, false, false, false)
		);
		assertEquals(
			RustGalGuiRenderer.GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME,
			RustGalGuiRenderer.selectExecutionRouteForTests(true, true, false, false)
		);
		assertEquals(
			RustGalGuiRenderer.GuiExecutionRoute.JAVA_COMPATIBILITY,
			RustGalGuiRenderer.selectExecutionRouteForTests(false, false, false, true)
		);
		assertEquals(
			RustGalGuiRenderer.GuiExecutionRoute.DISABLED,
			RustGalGuiRenderer.selectExecutionRouteForTests(true, true, true, false)
		);
	}

	@Test
	void blockOutlineRouteKeepsJavaPathsOutsideRustVulkanShell() throws Exception {
		assertEquals(
			WorldRenderRoutePolicy.Route.RUST_OPENGL_BORROWED_CONTEXT,
			WorldRenderRoutePolicy.selectRouteForTests(false, false, false, false)
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.RUST_OPENGL_BORROWED_CONTEXT,
			WorldRenderRoutePolicy.selectRouteForTests(false, true, false, false)
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY,
			WorldRenderRoutePolicy.selectRouteForTests(true, false, false, false)
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME,
			WorldRenderRoutePolicy.selectRouteForTests(true, true, false, false)
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectRouteForTests(false, false, true, false)
		);
		assertTrue(
			Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"))
				.contains("shouldUseRustOpenGlCrack()")
		);
		assertTrue(
			Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"))
				.contains("currentBlockDisplayRoute()")
		);
		assertTrue(
			Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"))
				.contains("currentFallingBlockRoute()")
		);
		assertTrue(
			Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"))
				.contains("selected.usesRustOpenGl() && net.irisshaders.iris.Iris.isPackInUseQuick()"),
			"Iris-active OpenGL falling blocks must be routed to Java compatibility before Rust selection"
		);
		assertTrue(
			Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"))
				.contains("currentPistonMovingBlockRoute()")
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY,
			WorldRenderRoutePolicy.selectWholeFrameRouteForTests(false, false, false, false),
			"static terrain must stay compatibility-owned unless Rust Vulkan whole-frame is active"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME,
			WorldRenderRoutePolicy.selectWholeFrameRouteForTests(true, true, false, false)
		);
		String worldRoutePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		assertTrue(worldRoutePolicy.contains("currentStaticTerrainRoute()"));
		assertTrue(worldRoutePolicy.contains("mattmc.dev.rustGalStaticTerrain.disabled"));
		assertTrue(worldRoutePolicy.contains("staticTerrainBuildRequiresRustWholeFrameMetadata()"));
		assertTrue(
			Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask.java"))
				.contains("WorldRenderRoutePolicy.staticTerrainBuildRequiresRustWholeFrameMetadata()"),
			"chunk builds must prepare Rust terrain metadata before backend-selected state is stable"
		);
	}

	@Test
	void staticTerrainBuildMetadataRouteDoesNotDependOnBackendSelectionTiming() {
		String previousWholeFrame = System.getProperty("mattmc.dev.rustGalVulkanWholeFrame");
		String previousDisabled = System.getProperty("mattmc.dev.rustGalStaticTerrain.disabled");
		String previousLegacy = System.getProperty("mattmc.dev.rustGalStaticTerrain.legacyControl");
		try {
			System.setProperty("mattmc.dev.rustGalVulkanWholeFrame", "true");
			System.clearProperty("mattmc.dev.rustGalStaticTerrain.disabled");
			System.clearProperty("mattmc.dev.rustGalStaticTerrain.legacyControl");
			assertTrue(WorldRenderRoutePolicy.staticTerrainBuildRequiresRustWholeFrameMetadata());

			System.setProperty("mattmc.dev.rustGalStaticTerrain.disabled", "true");
			assertFalse(WorldRenderRoutePolicy.staticTerrainBuildRequiresRustWholeFrameMetadata());
			System.clearProperty("mattmc.dev.rustGalStaticTerrain.disabled");

			System.setProperty("mattmc.dev.rustGalStaticTerrain.legacyControl", "true");
			assertFalse(WorldRenderRoutePolicy.staticTerrainBuildRequiresRustWholeFrameMetadata());
		} finally {
			restoreProperty("mattmc.dev.rustGalVulkanWholeFrame", previousWholeFrame);
			restoreProperty("mattmc.dev.rustGalStaticTerrain.disabled", previousDisabled);
			restoreProperty("mattmc.dev.rustGalStaticTerrain.legacyControl", previousLegacy);
		}
	}

	@Test
	void shaderPackSourceTransportIsWholeFrameOnlyAndDoesNotBorrowIrisRuntimeState() throws Exception {
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		String collector = Files.readString(Path.of("src/main/java/net/vulkanic/shaderpack/RustShaderPackSourceCollector.java"));
		assertTrue(coordinator.contains("RustGalGuiRenderer.isWholeFrameVulkanActive()"));
		assertTrue(coordinator.contains("bridge.updateShaderPackSources("));
		assertTrue(coordinator.contains("bridge.updateShaderPackAssets("));
		assertTrue(coordinator.contains("uploadedShaderPackSourceGeneration < generation"));
		assertTrue(coordinator.contains("source_execution_selected=false"));
		assertFalse(collector.contains("IrisRenderingPipeline"));
		assertFalse(collector.contains("WorldRenderingPipeline"));
		assertFalse(collector.contains("GlImage"));
		assertFalse(collector.contains("MemorySegment"));
		assertTrue(collector.contains("collectWithAssets"));
		assertTrue(collector.contains("ShaderPackAssetFileRecord"));
	}

	private static void restoreProperty(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}

	@Test
	void indexedBakedMeshesUseExplicitCcWFrontFaceContract() throws Exception {
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String rustMeshFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/world_primitive_frontend.rs"));
		String rustOpenGlLowering = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/opengl/lowering.rs"));
		String rustVulkanResources = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/vulkan/resources.rs"));

		assertTrue(worldRenderer.contains("return dot >= 0.0F ? WORLD_WINDING_CCW : WORLD_WINDING_CW;"),
			"baked quads whose emitted indices face the baked Direction must be tagged as CCW for GAL culling");
		assertTrue(rustMeshFrontend.contains("(WORLD_CULL_BACK, WORLD_WINDING_CCW) => Ok(CullMode::Back)"));
		assertTrue(rustMeshFrontend.contains("(WORLD_CULL_BACK, WORLD_WINDING_CW) => Ok(CullMode::Front)"));
		assertTrue(rustOpenGlLowering.contains("self.gl.front_face(glow::CCW);"),
			"OpenGL backend must seed the GAL CCW front-face convention instead of inheriting Java/Iris state");
		assertTrue(rustVulkanResources.contains(".front_face(vk::FrontFace::COUNTER_CLOCKWISE)"));
	}

	@Test
	void pistonMovingMeshParticipatesInOpenGlFrameSeedingAndFlush() throws Exception {
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String blockFeatureRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/BlockFeatureRenderer.java"));
		String frameBenchmark = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/GraphicsFrameBenchmark.java"));
		String deterministicCapture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
		String pistonEntity = Files.readString(Path.of("src/main/java/net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java"));
		int meshHelperStart = worldRenderer.indexOf("private static boolean shouldUseRustOpenGlMeshInstances()");
		int meshHelperEnd = worldRenderer.indexOf("public static boolean shouldRouteTerrainParticle", meshHelperStart);
		String meshHelper = worldRenderer.substring(meshHelperStart, meshHelperEnd);
		assertTrue(meshHelper.contains("currentBlockDisplayRoute().usesRustOpenGl()"));
		assertTrue(meshHelper.contains("currentFallingBlockRoute().usesRustOpenGl()"));
		assertTrue(meshHelper.contains("currentPistonMovingBlockRoute().usesRustOpenGl()"));

		int frameHelperStart = worldRenderer.indexOf("public static boolean shouldUseRustOpenGlWorldPrimitives()");
		int frameHelperEnd = worldRenderer.indexOf("public static boolean crackDisabledForDiagnostics()", frameHelperStart);
		String frameHelper = worldRenderer.substring(frameHelperStart, frameHelperEnd);
		assertTrue(frameHelper.contains("currentPistonMovingBlockRoute().usesRustOpenGl()"));
		assertTrue(worldRenderer.contains("movingBlockLightColor(movingBlockRenderState, blockState, movingBlockRenderState.blockPos)"));
		assertTrue(worldRenderer.contains("return dot >= 0.0F ? WORLD_WINDING_CCW : WORLD_WINDING_CW;"));
		assertTrue(worldRenderer.contains("public static PrimitiveFrame withViewport(PrimitiveFrame frame, int viewportWidth, int viewportHeight)"));
		assertFalse(worldRenderer.contains("currentFallingBlockRoute().usesRustOpenGl() && net.irisshaders.iris.Iris.isPackInUseQuick()"));
		assertFalse(blockFeatureRenderer.contains("Iris.isPackInUseQuick()"));
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		assertTrue(coordinator.contains("RustGalWorldPrimitiveRenderer.withViewport("));
		assertTrue(frameBenchmark.contains("Direction direction = pistonDirection();"));
		assertTrue(frameBenchmark.contains("PistonHeadBlock.FACING, pistonDirection()"));
		assertTrue(frameBenchmark.contains("getEyePosition().add(forward.normalize().scale(4.0))"));
		assertTrue(pistonEntity.contains("mattmc.dev.rustGalWorldMesh.pistonProgress"));
		assertTrue(deterministicCapture.contains("Direction direction = pistonDirection();"));
		assertTrue(deterministicCapture.contains("PistonHeadBlock.FACING, pistonDirection()"));
		assertTrue(deterministicCapture.contains("getEyePosition().add(forward.normalize().scale(4.0))"));
	}

	@Test
	void wholeFrameWorldPrimitiveRouteIsVulkanShellOnlyAndCoarse() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String frameCoordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		String guiRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String worldRoutePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		String sodiumWorldRenderer = Files.readString(Path.of("src/main/java/net/sodium/client/render/SodiumWorldRenderer.java"));
		String renderSectionManager = Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/RenderSectionManager.java"));
		String terrainRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java"));
		String deterministicCapture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
		String graphicsHarness = Files.readString(Path.of("DevUtils/Common/graphics_harness.py"));
		String rustFfi = readRustFfiModules();
		String rustWorldFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/world_primitive_frontend.rs"));
		String rustTerrainFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/terrain/mod.rs"));

		assertTrue(bridge.contains("mattmc_vulkanic_gal_whole_frame_submit"));
		assertTrue(bridge.contains("mattmc_vulkanic_gal_world_border_update_asset"));
		assertTrue(bridge.contains("record WorldBorderAssetRecord"));
		assertTrue(bridge.contains("updateWorldBorderAsset"));
			assertTrue(bridge.contains("record WorldLineSegmentRecord"));
			assertTrue(bridge.contains("record WorldCrackQuadRecord"));
			assertTrue(bridge.contains("record WorldBorderQuadRecord"));
			assertTrue(bridge.contains("record WorldBackgroundRecord"));
			assertTrue(bridge.contains("List<WorldLineSegmentRecord> worldSegments"));
			assertTrue(bridge.contains("List<WorldCrackQuadRecord> worldCrackQuads"));
			assertTrue(bridge.contains("List<WorldBorderQuadRecord> worldBorderQuads"));
			assertTrue(bridge.contains("submitWorldPrimitives("));
			assertTrue(bridge.contains("worldCrackQuads,"));
			assertTrue(frameCoordinator.contains("executeWholeFrameVulkan"));
			assertTrue(frameCoordinator.contains("executeWorldPrimitiveFrame"));
			assertTrue(frameCoordinator.contains("bridge.submitWholeFrame"));
			assertTrue(frameCoordinator.contains("primitiveFrame.crackQuads()"));
			assertTrue(guiRenderer.contains("isWholeFrameVulkanActive()"));
			int worldPrimitiveExecution = frameCoordinator.indexOf("public static boolean executeWorldPrimitiveFrame");
			int worldCrackAssetFlush = frameCoordinator.indexOf("flushPendingWorldAssetsLocked();", worldPrimitiveExecution);
			int worldPrimitiveSubmit = frameCoordinator.indexOf("bridge.submitWorldPrimitives", worldPrimitiveExecution);
			assertTrue(worldCrackAssetFlush > worldPrimitiveExecution && worldCrackAssetFlush < worldPrimitiveSubmit,
				"standalone world primitive execution must flush crack assets before submitting real crack work");
		assertTrue(gameRenderer.contains("renderRustVulkanWholeFrameShell"));
			assertTrue(gameRenderer.contains("RustGalWorldPrimitiveRenderer.enqueueBlockOutline"));
			assertTrue(gameRenderer.contains("RustGalWorldPrimitiveRenderer.enqueueWorldBackground"));
			assertTrue(gameRenderer.contains("levelRenderer.enqueueRustGalBlockBreakingCracks"));
		assertTrue(gameRenderer.contains("levelRenderer.enqueueRustGalWorldBorder"));
		assertTrue(levelRenderer.contains("RustGalWorldPrimitiveRenderer.enqueueWorldBorder"));
		assertTrue(levelRenderer.contains("reason=no-valid-destroy-progress"),
			"normal OpenGL must draw nothing when no active valid destroy-progress state exists");
		assertTrue(levelRenderer.contains("Rust OpenGL block-breaking crack overlay was selected with valid semantic requests but submitted no work"),
			"Rust submission/validation failure must be explicit instead of falling back to Java in the same frame");
		assertFalse(levelRenderer.contains("java-opengl-retained-after-empty-rust"),
			"normal OpenGL must not use same-frame Java fallback after selecting the Rust crack route");
			String borderRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/WorldBorderRenderer.java"));
			String terrainParticle = Files.readString(Path.of("src/main/java/net/minecraft/client/particle/TerrainParticle.java"));
			assertTrue(borderRenderer.contains("RustGalWorldPrimitiveRenderer.enqueueWorldBorder"));
			assertTrue(worldRenderer.contains("enqueueBlockBreakingCracks"));
			assertTrue(worldRenderer.contains("renderOpenGlBlockBreakingCracks"));
			assertTrue(worldRenderer.contains("state.blockState.isAir()"),
				"real crack extraction must not synthesize full-cube crack quads for stale air states");
			assertTrue(worldRenderer.contains("enqueueWorldBorder"));
			assertTrue(worldRenderer.contains("enqueueWorldBackground"));
			assertTrue(worldRenderer.contains("reloadWorldAssets"));
			assertTrue(worldRenderer.contains("enqueueBlockDisplay"));
			assertTrue(worldRenderer.contains("currentBlockDisplayRoute()"));
			assertTrue(worldRenderer.contains("enqueueFallingBlock"));
			assertTrue(worldRenderer.contains("currentFallingBlockRoute()"));
			assertTrue(worldRenderer.contains("enqueuePistonMovingBlock"));
			assertTrue(worldRenderer.contains("currentPistonMovingBlockRoute()"));
			assertTrue(worldRenderer.contains("MovingBlockSubmitSource.FALLING_BLOCK"));
			assertTrue(worldRenderer.contains("MovingBlockSubmitSource.PISTON"));
			assertTrue(worldRenderer.contains("SubmitNodeStorage.BlockSubmitSource.BLOCK_DISPLAY"));
			assertTrue(worldRenderer.contains("RenderShape.MODEL"));
			assertTrue(worldRenderer.contains("specialBlockModelRenderer().get().hasRenderer(blockState.getBlock())"));
			assertTrue(worldRenderer.contains("WorldMeshAssetRecord"));
			assertTrue(worldRenderer.contains("WorldMeshInstanceRecord"));
			assertTrue(worldRenderer.contains("flushPendingWorldMeshAssets"));
		assertTrue(worldRenderer.contains("flushPendingWorldBorderAssets"));
		assertTrue(worldRenderer.contains("textures/misc/forcefield.png"));
		assertTrue(worldRenderer.contains("WorldCrackQuadRecord"));
		assertTrue(worldRenderer.contains("WorldBorderQuadRecord"));
		assertTrue(worldRenderer.contains("STRATUM_WORLD_BLOCK_BREAKING_CRACK"));
		assertTrue(worldRenderer.contains("STRATUM_WORLD_BORDER"));
		assertTrue(worldRenderer.contains("CRACK_BLEND_MULTIPLY"));
		assertTrue(worldRenderer.contains("BORDER_BLEND_OVERLAY"));
		assertTrue(worldRoutePolicy.contains("DISABLED"));
		assertTrue(worldRoutePolicy.contains("JAVA_COMPATIBILITY"));
		assertTrue(worldRoutePolicy.contains("RUST_OPENGL_BORROWED_CONTEXT"));
		assertTrue(worldRoutePolicy.contains("RUST_VULKAN_WHOLE_FRAME"));
		assertTrue(worldRenderer.contains("WorldRenderRoutePolicy.currentBackgroundRoute()"));
		assertTrue(worldRenderer.contains("WorldRenderRoutePolicy.currentWorldBorderRoute()"));
		assertTrue(worldRoutePolicy.contains("currentStaticTerrainRoute()"));
		assertTrue(gameRenderer.contains("enqueueRustGalStaticTerrainForWholeFrame"));
		assertTrue(levelRenderer.contains("this.cullTerrain(camera, frustum, this.minecraft.player.isSpectator())"));
		assertTrue(sodiumWorldRenderer.contains("enqueueRustGalStaticTerrain"));
		assertTrue(renderSectionManager.contains("RustGalTerrainRenderer.acceptChunkBuildOutput(chunkBuildOutput)"));
		assertTrue(terrainRenderer.contains("WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()"));
		assertTrue(terrainRenderer.contains("DefaultTerrainRenderPasses.SOLID"));
		assertTrue(terrainRenderer.contains("DefaultTerrainRenderPasses.CUTOUT"));
			assertTrue(terrainRenderer.contains("DefaultTerrainRenderPasses.TRANSLUCENT"));
			assertTrue(terrainRenderer.contains("ChunkSectionLayer.TRANSLUCENT"));
				assertTrue(terrainRenderer.contains("asset.unsupportedPrimitiveCount() > 0"));
			assertTrue(terrainRenderer.contains("unsupported-fluid-omitted"),
				"water terrain v1 must omit unsupported fluids without discarding supported mixed translucent sections");
			assertTrue(terrainRenderer.contains("MATERIAL_ID_WATER_TRANSLUCENT"),
				"built-in water must carry an explicit semantic material identity");
		assertTrue(terrainRenderer.contains("acceptChunkSortOutput"));
		assertTrue(renderSectionManager.contains("RustGalTerrainRenderer.acceptChunkSortOutput(sortOutput)"));
		assertTrue(terrainRenderer.contains("registeredAtlasGeneration"));
		assertTrue(terrainRenderer.contains("atlasTextureUpdatePayload()"));
		assertTrue(terrainRenderer.contains("vertex.colorArgb()"));
		assertTrue(terrainRenderer.contains("vertex.light()"));
		assertTrue(terrainRenderer.contains("WorldMeshSectionRecord section"));
		assertTrue(terrainRenderer.contains("removeSection(int x, int y, int z, String reason)"));
		assertTrue(terrainRenderer.contains("TerrainDiagnostics"));
		assertTrue(terrainRenderer.contains("DeterministicCameraCapture.recordSubmittedWorkIdentity(\"static-terrain\""));
		assertTrue(deterministicCapture.contains("rustGalStaticTerrainDiagnostics"));
		assertTrue(graphicsHarness.contains("--world-static-terrain-scenario"));
		assertTrue(graphicsHarness.contains("static_terrain_workload_complete"));
		assertTrue(renderSectionManager.contains("RustGalTerrainRenderer.removeSection(x, y, z, \"section-removed\")"));
		assertTrue(worldRenderer.contains("DIRTY_WORLD_MESH_TEXTURES"));
		assertTrue(worldRenderer.contains("DIRTY_WORLD_MESH_SORTED_INDICES"));
		assertTrue(worldRenderer.contains("registerStaticTerrainSortedIndex"));
		assertTrue(bridge.contains("record WorldMeshSortedIndexRecord"));
		assertTrue(worldRenderer.contains("dirtyWorldMeshTextureAssetsLocked()"));
		assertTrue(worldRenderer.contains("dirtyWorldMeshSortedIndicesLocked()"));
		assertTrue(worldRenderer.contains("removeStaticTerrainMeshAsset"));
			assertTrue(rustWorldFrontend.contains("self.mesh_texture_assets.insert(texture_id, texture);")
					&& rustWorldFrontend.contains("destroy_mesh_texture_resources_for_ids(gal, &incoming_texture_ids);"),
				"incremental mesh updates must preserve previously uploaded semantic texture assets");
		assertTrue(rustTerrainFrontend.contains("Static chunk-terrain frontend boundary"));
		assertFalse(worldRenderer.contains("CRACK_DISABLED_CONTROL ||"));
		assertTrue(worldRenderer.contains("shape.forAllEdges"));
		assertFalse(worldRenderer.contains("CommandOp"));
		assertFalse(worldRenderer.contains("Vk"));
		assertFalse(worldRenderer.matches("(?s).*\\bGL_[A-Z0-9_]+.*"));
			assertTrue(rustFfi.contains("FfiWorldLineSegmentRequest"));
			assertTrue(rustFfi.contains("FfiWorldCrackQuadRequest"));
			assertTrue(rustFfi.contains("FfiWorldBorderQuadRequest"));
			assertTrue(rustFfi.contains("FfiWorldBackgroundRequest"));
			assertTrue(rustFfi.contains("FfiWorldBorderAssetUpdateRequest"));
			assertTrue(rustFfi.contains("FfiWorldMeshAssetRecord"));
			assertTrue(rustFfi.contains("FfiWorldMeshInstanceRecord"));
			assertTrue(rustFfi.contains("mattmc_vulkanic_gal_world_mesh_update_assets"));
			assertFalse(rustFfi.contains("BlockDisplay"));
			assertFalse(rustFfi.contains("BlockSubmit"));
		assertTrue(rustFfi.contains("decode_world_border_asset_update"));
		assertTrue(rustFfi.contains("decode_whole_frame_submit"));
		assertTrue(rustFfi.contains("context.world_primitive_frontend"));
			assertTrue(rustWorldFrontend.contains("WORLD_LINE_VERTEX_SHADER_VULKAN"));
			assertTrue(rustWorldFrontend.contains("PrimitiveTopology::Triangles"));
		assertTrue(rustWorldFrontend.contains("WorldCrackQuadRequest"));
		assertTrue(rustWorldFrontend.contains("WorldBorderQuadRequest"));
		assertTrue(rustWorldFrontend.contains("BlendMode::Multiply"));
		assertTrue(rustWorldFrontend.contains("BlendMode::Overlay"));
		assertTrue(rustWorldFrontend.contains("forcefield.png"));
		assertTrue(rustWorldFrontend.contains("apply_world_border_asset_update"));
		assertTrue(rustWorldFrontend.contains("WorldPrimitiveFrontend"));
		assertTrue(rustWorldFrontend.contains("submit_whole_frame"));
			assertTrue(rustWorldFrontend.contains("unsupported_feature"));
			assertTrue(terrainParticle.contains("private boolean alphaTested;"),
				"TerrainParticle must track alpha-test semantics separately from Iris opaque particle-layer routing");
			assertTrue(terrainParticle.contains("|| type == net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT_MIPPED"),
				"cutout and cutout-mipped block particles must be recognized before Rust material submission");
			assertTrue(terrainParticle.contains("!this.alphaTested"),
				"leaf/cutout TerrainParticles must submit Rust cutout material mode so transparent texels discard instead of rendering black");
		}

	@Test
	void rustBridgeIsOnlyRoutedFromSubsystemBenchmarkControls() throws Exception {
		String subsystem = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/GraphicsSubsystemBenchmark.java"));
		String bridge = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/RustGraphicsSubsystemBenchmark.java"));

		assertTrue(subsystem.contains("rust-vulkan") && subsystem.contains("rust-opengl"));
		assertTrue(bridge.contains("Rust VulkanicGAL bridge"));
		assertTrue(bridge.contains("awaitTracyCaptureGrace()"));
		assertTrue(bridge.contains("Boolean.getBoolean(\"mattmc.dev.tracyCapture\")"));
		assertTrue(bridge.contains("mattmc.dev.graphicsSubsystemBenchmark.tracyGraceMillis"));
	}

	@Test
	void bridgePollsCompletionBeforeReadbackAndReportsFinalFfiMetrics() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String benchmark = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/RustGraphicsSubsystemBenchmark.java"));

		assertTrue(bridge.contains("mattmc_vulkanic_gal_completion_query"));
		assertTrue(benchmark.indexOf("pollCompletion(bridge, submission)") < benchmark.indexOf("bridge.readback(submission"));
		assertTrue(benchmark.contains("VulkanicGalBridge.Status retireStatus = bridge.retire(submission)"));
		assertTrue(benchmark.contains("\\\"completionPolls\\\""));
	}

	@Test
	void subsystemBridgeBarriersUseSemanticUsageStatesOnly() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String benchmark = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/RustGraphicsSubsystemBenchmark.java"));
		String rustFfi = readRustFfiModules();

		assertTrue(benchmark.contains(".barrier(handles.sampledTexture, VulkanicGalBridge.USAGE_TRANSFER_DST, VulkanicGalBridge.USAGE_SHADER_READ, true)"));
		assertTrue(benchmark.contains("layout(std430, binding = 3) readonly buffer Storage3"));
		assertTrue(benchmark.contains("layout(set = 0, binding = 3, std430) readonly buffer Storage3"));
		assertTrue(bridge.contains("Struct.BARRIER.setInt(barrier, 4, before);"));
		assertTrue(bridge.contains("Struct.BARRIER.setInt(barrier, 5, after);"));
		assertTrue(bridge.contains("Struct.BARRIER.setInt(barrier, 6, 0);"));
		assertTrue(bridge.contains("Struct.BARRIER.setInt(barrier, 7, 0);"));
		assertFalse(bridge.contains("Struct.BARRIER.setInt(barrier, 6, STAGE"));
		assertFalse(bridge.contains("Struct.BARRIER.setInt(barrier, 7, ACCESS"));
		assertTrue(rustFfi.contains("resource barrier stage/access bits are deprecated"));
	}

	@Test
	void rustOpenGlContextFallbackIsExplicitAndDoesNotUseProductionCallsites() throws Exception {
		String context = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/opengl/context.rs"));
		String subsystem = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/GraphicsSubsystemBenchmark.java"));

		assertTrue(context.contains("EGL:") && context.contains("GLX:"));
		assertTrue(context.contains("glXCreatePbuffer"));
		assertTrue(subsystem.contains("RustGraphicsSubsystemBenchmark.run"));
		assertTrue(subsystem.contains("minecraft.stop()"));
	}

	@Test
	void guiSpriteBatchingPreservesIncompatibleStratumAndStateBoundaries() throws Exception {
		String rustGuiFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/gui_frontend.rs"));
		assertEquals(ArmorIconState.EMPTY, RustGalGuiRenderer.armorIconStateForTests(0, 0));
		assertEquals(ArmorIconState.HALF, RustGalGuiRenderer.armorIconStateForTests(1, 0));
		assertEquals(ArmorIconState.FULL, RustGalGuiRenderer.armorIconStateForTests(2, 0));
		assertEquals(ArmorIconState.EMPTY, RustGalGuiRenderer.armorIconStateForTests(18, 9));
		assertEquals(ArmorIconState.HALF, RustGalGuiRenderer.armorIconStateForTests(19, 9));
		assertEquals(ArmorIconState.FULL, RustGalGuiRenderer.armorIconStateForTests(20, 9));
		assertTrue(rustGuiFrontend.contains("texelFetch(Sampler0"));
		assertTrue(rustGuiFrontend.contains("fn packed_uniform_bytes"));
		assertTrue(rustGuiFrontend.contains("SpriteBatch"));
		for (int armor = 0; armor <= 20; armor++) {
			for (int icon = 0; icon < 10; icon++) {
				int threshold = icon * 2 + 1;
				ArmorIconState expected = threshold < armor
					? ArmorIconState.FULL
					: threshold == armor ? ArmorIconState.HALF : ArmorIconState.EMPTY;
				assertEquals(expected, RustGalGuiRenderer.armorIconStateForTests(armor, icon), "armor=" + armor + " icon=" + icon);
			}
		}
		assertThrows(IllegalArgumentException.class, () -> new PlayerHeartRequest(
			PlayerHeartVariant.NORMAL,
			GuiHeartState.CONTAINER,
			false,
			false,
			0,
			0,
			0
		));
		assertThrows(IllegalArgumentException.class, () -> new PlayerHeartRequest(
			PlayerHeartVariant.CONTAINER,
			GuiHeartState.FULL,
			false,
			false,
			0,
			0,
			0
		));
		assertThrows(IllegalArgumentException.class, () -> new PlayerHeartRequest(
			PlayerHeartVariant.NORMAL,
			GuiHeartState.FULL,
			false,
			false,
			-1,
			0,
			0
		));
		for (PlayerHeartVariant variant : List.of(
			PlayerHeartVariant.NORMAL,
			PlayerHeartVariant.POISONED,
			PlayerHeartVariant.WITHERED,
			PlayerHeartVariant.FROZEN
		)) {
			for (GuiHeartState state : List.of(
				GuiHeartState.HALF,
				GuiHeartState.FULL
			)) {
				for (boolean hardcore : List.of(false, true)) {
					for (boolean flashing : List.of(false, true)) {
						new PlayerHeartRequest(variant, state, hardcore, flashing, 1, 2, 3);
					}
				}
			}
		}
		new PlayerHeartRequest(
			PlayerHeartVariant.CONTAINER,
			GuiHeartState.CONTAINER,
			true,
			true,
			0,
			2,
			3
		);
		assertThrows(IllegalArgumentException.class, () -> new AbsorptionHeartRequest(
			AbsorptionHeartVariant.ABSORBING,
			GuiHeartState.CONTAINER,
			false,
			false,
			0,
			0,
			0
		));
		assertThrows(IllegalArgumentException.class, () -> new AbsorptionHeartRequest(
			AbsorptionHeartVariant.CONTAINER,
			GuiHeartState.FULL,
			false,
			false,
			0,
			0,
			0
		));
		for (AbsorptionHeartVariant variant : List.of(AbsorptionHeartVariant.ABSORBING, AbsorptionHeartVariant.WITHERED)) {
			for (GuiHeartState state : List.of(GuiHeartState.HALF, GuiHeartState.FULL)) {
				new AbsorptionHeartRequest(variant, state, true, false, 1, 2, 3);
			}
		}
		new AbsorptionHeartRequest(AbsorptionHeartVariant.CONTAINER, GuiHeartState.CONTAINER, true, true, 0, 2, 3);
		assertThrows(IllegalArgumentException.class, () -> new HungerIconRequest(
			HungerIconVariant.NORMAL,
			HungerIconState.FULL,
			false,
			2,
			0,
			0,
			0
		));
		assertThrows(IllegalArgumentException.class, () -> new HungerIconRequest(
			HungerIconVariant.NORMAL,
			HungerIconState.FULL,
			false,
			0,
			-1,
			0,
			0
		));
		for (HungerIconVariant variant : HungerIconVariant.values()) {
			for (HungerIconState state : HungerIconState.values()) {
				new HungerIconRequest(variant, state, true, -1, 1, 2, 3);
				new HungerIconRequest(variant, state, false, 0, 1, 2, 3);
				new HungerIconRequest(variant, state, true, 1, 1, 2, 3);
			}
		}
		assertThrows(IllegalArgumentException.class, () -> new AirBubbleRequest(
			AirBubbleState.FULL,
			true,
			true,
			0,
			0,
			0
		));
		assertThrows(IllegalArgumentException.class, () -> new AirBubbleRequest(
			AirBubbleState.EMPTY,
			false,
			true,
			-1,
			0,
			0
		));
		new AirBubbleRequest(AirBubbleState.FULL, false, true, 0, 1, 2);
		new AirBubbleRequest(AirBubbleState.PARTIAL, true, true, 1, 1, 2);
		new AirBubbleRequest(AirBubbleState.EMPTY, false, true, 2, 1, 2);
		assertThrows(IllegalArgumentException.class, () -> new MountHeartRequest(
			MountHeartVariant.VEHICLE,
			MountHeartState.FULL,
			true,
			-1,
			0,
			0,
			0
		));
		assertThrows(IllegalArgumentException.class, () -> new MountHeartRequest(
			MountHeartVariant.VEHICLE,
			MountHeartState.FULL,
			true,
			0,
			-1,
			0,
			0
		));
		for (MountHeartState state : MountHeartState.values()) {
			new MountHeartRequest(MountHeartVariant.VEHICLE, state, true, 1, 2, 3, 4);
		}
		new AirBubbleRequest(AirBubbleState.EMPTY, false, false, 3, 1, 2);
		assertTrue(rustGuiFrontend.contains("GUI_MAX_PACKED_SPRITES"));
		assertTrue(rustGuiFrontend.contains("CommandOp::CopyBufferToTexture"));
	}

	@Test
	void frameAbiV6PreservesFrameContractAndAddsSemanticShaderEnvironmentPayload() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String world = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String queue = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		String rustGuiFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/gui_frontend.rs"));
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		String gui = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		String guiRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));
		String experienceBar = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/contextualbar/ExperienceBarRenderer.java"));
		String bossOverlay = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/components/BossHealthOverlay.java"));

		assertEquals(11, VulkanicGalBridge.ABI_VERSION);
		assertTrue(bridge.contains("int midBlockPacked"));
		assertTrue(bridge.contains("record WorldVoxelVolumeFrameRecord"));
		assertTrue(bridge.contains("record WorldShaderEnvironmentFrameRecord"));
		assertTrue(bridge.contains("WORLD_VOXEL_VOLUME_FRAME(72)"));
		assertTrue(bridge.contains("WORLD_SHADER_ENVIRONMENT_FRAME(73)"));
		assertTrue(bridge.contains("voxelVolumeFrame.worldGeneration()"));
		assertTrue(bridge.contains("voxelVolumeFrame.cameraX()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.timeOfDay()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.eyeSubmersion()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.farPlane()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.skyColorRed()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.darknessLightFactor()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.nightVision()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.fogColorRed()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.biomePrecipitation()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.biomeResourceLocation()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.mainHandItemModelResourceLocation()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.offHandItemModelResourceLocation()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.mainHandItemLightEmission()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.offHandItemLightEmission()"));
		assertFalse(bridge.contains("IrisShaderEnvironmentFrame"));
		assertTrue(world.contains("level.getSkyColor(camera.getPosition(), shaderPackFramePartialTick)"));
		assertTrue(world.contains("shaderPackFogColor(level, camera)"));
		assertTrue(world.contains("fogRenderer.computeFogColor("));
		assertTrue(world.contains("shaderPackBiomePrecipitation(level, camera)"));
		assertTrue(world.contains("shaderPackBiomeResourceLocation(level, camera)"));
		assertTrue(world.contains("shaderPackHeldItemModelResourceLocation("));
		assertTrue(world.contains("shaderPackHeldItemLightEmission("));
		assertTrue(world.contains("IrisItemLightProvider"));
		assertTrue(world.contains("DataComponents.ITEM_MODEL"));
		assertTrue(world.contains("BuiltInRegistries.ITEM.getKey"));
		assertTrue(world.contains("Biome.Precipitation precipitation"));
		assertFalse(world.contains("CapturedRenderingState.INSTANCE.getFogColor"));
		assertTrue(world.contains("camera.getFluidInCamera()"));
		assertTrue(world.contains("shaderPackDarknessLightFactor()"));
		assertTrue(world.contains("shaderPackNightVision()"));
		assertFalse(world.contains("CapturedRenderingState"));
		assertTrue(bridge.contains("Struct.WORLD_MESH_VERTEX.setInt(vertexItem, 13, vertex.midBlockPacked())"));
		assertTrue(bridge.contains("mattmc_vulkanic_gal_context_create_borrowed_opengl"));
		assertTrue(bridge.contains("mattmc_vulkanic_gal_frame_acquire"));
		assertTrue(bridge.contains("mattmc_vulkanic_gal_frame_present"));
		assertTrue(bridge.contains("mattmc_vulkanic_gal_gui_submit_frame"));
		assertTrue(bridge.contains("record GuiSpriteRecord"));
		assertEquals(1, VulkanicGalBridge.HANDLE_BUFFER);
		assertEquals(2, VulkanicGalBridge.HANDLE_TEXTURE);
		assertEquals(3, VulkanicGalBridge.HANDLE_TEXTURE_VIEW);
		assertEquals(4, VulkanicGalBridge.HANDLE_SAMPLER);
		assertEquals(5, VulkanicGalBridge.HANDLE_SHADER_MODULE);
		assertEquals(6, VulkanicGalBridge.HANDLE_RESOURCE_LAYOUT);
		assertEquals(7, VulkanicGalBridge.HANDLE_RESOURCE_SET);
		assertEquals(8, VulkanicGalBridge.HANDLE_PIPELINE_LAYOUT);
		assertEquals(9, VulkanicGalBridge.HANDLE_GRAPHICS_PIPELINE);
		assertEquals(13, VulkanicGalBridge.HANDLE_FRAME_TARGET);
		assertTrue(queue.contains("GUI_CROSSHAIR"));
		assertTrue(queue.contains("GUI_HOTBAR_BASE"));
		assertTrue(queue.contains("GUI_HOTBAR_SELECTION"));
		assertTrue(queue.contains("GUI_ARMOR"));
		assertTrue(queue.contains("GUI_EXPERIENCE_BAR_BACKGROUND"));
		assertTrue(queue.contains("GUI_EXPERIENCE_BAR_PROGRESS"));
		assertTrue(queue.contains("GUI_BOSS_BAR_BACKGROUND"));
		assertTrue(queue.contains("GUI_BOSS_BAR_PROGRESS"));
		assertTrue(queue.contains("GUI_PLAYER_HEALTH"));
		assertTrue(queue.contains("HOTBAR_BASE"));
		assertTrue(queue.contains("HOTBAR_SELECTION"));
		assertTrue(queue.contains("EXPERIENCE_BAR_BACKGROUND"));
		assertTrue(queue.contains("EXPERIENCE_BAR_PROGRESS"));
		assertTrue(queue.contains("enqueuePlayerHearts"));
		assertTrue(queue.contains("enqueueHotbarBase"));
		assertTrue(queue.contains("enqueueHotbarSelection"));
		assertTrue(queue.contains("enqueueArmorIcons"));
		assertTrue(queue.contains("enqueueExperienceBar"));
		assertTrue(queue.contains("enqueueBossBar"));
		assertFalse(bridge.contains("guiAlphaPipeline"));
		assertFalse(bridge.contains("guiInvertPipeline"));
		assertTrue(rustGuiFrontend.contains("TextureGroup::Alpha"));
		assertFalse(queue.contains("DeferredBatchScheduler"));
		String frameCoordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		assertFalse(queue.contains("RustGalFrameScheduler<VulkanicGalBridge.GuiSpriteRecord>"));
		assertTrue(frameCoordinator.contains("RustGalFrameScheduler<VulkanicGalBridge.GuiSpriteRecord>"));
		assertFalse(queue.contains("record CachedResources"));
		assertFalse(queue.contains("record CacheKey"));
		assertFalse(Files.exists(Path.of("src/main/java/net/vulkanic/gui/GuiBatchBuilder.java")));
		assertFalse(Files.exists(Path.of("src/main/java/net/vulkanic/gui/GuiResourceCache.java")));
		assertFalse(Files.exists(Path.of("src/main/java/net/vulkanic/gui/GuiSpriteAtlas.java")));
		assertFalse(Files.exists(Path.of("src/main/java/net/vulkanic/gui/GuiPipelineLibrary.java")));
		assertTrue(frameCoordinator.contains("cacheHits"));
		assertTrue(frameCoordinator.contains("completionTimeouts"));
		assertFalse(frameCoordinator.contains("RETIRE_INTERVAL_FRAMES"));
		assertTrue(frameCoordinator.contains("if (!force)"));
		assertTrue(frameCoordinator.contains("rust_gal_frames_executed"));
		assertFalse(queue.contains("destroyHandles(created)"));
		assertTrue(rustGuiFrontend.contains("pub struct GuiFrontend"));
		assertTrue(rustGuiFrontend.contains("fn create_resources"));
		assertTrue(rustGuiFrontend.contains("fn build_atlas"));
		assertTrue(rustGuiFrontend.contains("fn packed_uniform_bytes"));
		assertTrue(rustGuiFrontend.contains("const VERTEX_SHADER"));
		assertTrue(rustGuiFrontend.contains("const FRAGMENT_SHADER"));
		assertTrue(frameCoordinator.contains("VulkanicGalBridge.isBorrowedOpenGlContextCurrent"));
		assertTrue(bridge.contains("GLFW.glfwGetCurrentContext()"));
		assertFalse(queue.contains("beginFramePass(frameResources.pass(), frameResources.target())"));
		assertFalse(queue.contains("frameResourcesFor("));
		assertFalse(queue.contains("GuiBatchBuilder.packCompatibleSpriteBatches"));
		assertTrue(frameCoordinator.contains("bridge.submitGuiFrame"));
		assertTrue(rustGuiFrontend.contains("CommandOp::DrawIndexed"));
		assertTrue(rustGuiFrontend.contains("TextureGroup::Alpha"));
		assertTrue(frameCoordinator.contains("rust_gal_sprite_batches_executed"));
		assertTrue(queue.contains("GuiExecutionRoute.JAVA_COMPATIBILITY"));
		assertTrue(queue.contains("Rust VulkanicGAL GUI enqueue requested while route is"));
		assertTrue(gameRenderer.contains("RustGalFrameCoordinator.resize"));
		assertTrue(gameRenderer.contains("RustGalFrameCoordinator.shutdown"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueueCrosshair"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueueHotbarBase"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueueArmorIcons"));
		assertTrue(experienceBar.contains("RustGalGuiRenderer.enqueueExperienceBar"));
		assertTrue(bossOverlay.contains("RustGalGuiRenderer.enqueueBossBar"));
		assertTrue(bossOverlay.contains("drawString(this.minecraft.font"));
		assertTrue(guiRenderer.contains("RustGalGuiElementRenderState"));
		assertTrue(guiRenderer.contains("RustGalFrameCoordinator.executeGuiFrame"));
		assertTrue(guiRenderer.contains("try (RenderPass ignored = VulkanicAPI.createRenderPass("));
		assertTrue(
			guiRenderer.indexOf("try (RenderPass ignored = VulkanicAPI.createRenderPass(") < guiRenderer.indexOf("RustGalFrameCoordinator.executeGuiFrame"),
			"Rust OpenGL must execute while the Java GUI render target is bound so frame_acquire captures the visible framebuffer"
		);
		assertTrue(
			guiRenderer.indexOf("RustGalFrameCoordinator.executeGuiFrame") < guiRenderer.indexOf("rustGalFrameExecuted.setTrue()"),
			"the combined Rust GUI frame should be marked executed only after the scoped render-pass submission"
		);
	}

	@Test
	void productionCrosshairSliceHasLifecycleInvalidationAndNoJavaFallback() throws Exception {
		String minecraft = Files.readString(Path.of("src/main/java/net/minecraft/client/Minecraft.java"));
		String gui = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		String queue = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		String frameCoordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		String stratum = Files.readString(Path.of("src/main/java/net/vulkanic/gui/GuiRenderStratum.java"));
		String experienceBar = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/contextualbar/ExperienceBarRenderer.java"));
		String bossOverlay = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/components/BossHealthOverlay.java"));
		String context = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/opengl/context.rs"));
		String openGlResources = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/opengl/resources.rs"));
		String rustGuiFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/gui_frontend.rs"));

		assertTrue(minecraft.contains("ResourceManagerReloadListener") && minecraft.contains("RustGalFrameCoordinator.reload(resourceManager)"));
		assertTrue(minecraft.contains("RustGalFrameCoordinator.cancelPending(\"world-disconnect\")"));
		assertTrue(minecraft.contains("RustGalFrameCoordinator.cancelPending(\"world-unload\")"));
		assertTrue(frameCoordinator.contains("SCHEDULER.cancelAll(\"resource-reload\")"));
		assertTrue(frameCoordinator.contains("SCHEDULER.cancelAll(\"resize\")"));
		assertTrue(frameCoordinator.contains("SCHEDULER.cancelAll(\"shutdown\")"));
		assertTrue(queue.contains("mattmc.rustGal.gui.enabled"));
		assertTrue(queue.contains("mattmc.rustGal.guiCrosshair.enabled"));
		assertTrue(frameCoordinator.contains("rust_gal_ffi_resource_batch_calls"));
		assertTrue(queue.contains("collectResolvedAssets(ResourceManager resourceManager)"));
		assertTrue(frameCoordinator.contains("bridge.updateGuiAssets(assetGeneration, pendingAssets)"));
		assertTrue(frameCoordinator.contains("rust_gal_ffi_completion_query_calls"));
		assertTrue(frameCoordinator.contains("rust_gal_queue_depth"));
		assertTrue(frameCoordinator.contains("rust_gal_batches_executed"));
		assertTrue(queue.contains("mattmc.dev.guiCrosshair.disabled"));
		assertTrue(queue.contains("mattmc.dev.guiCrosshair.legacyControl"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.disabled"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.legacyControl"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.armor.disabled"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.armor.legacyControl"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.playerHealth.disabled"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.playerHealth.legacyControl"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.absorption.disabled"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.absorption.legacyControl"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.hunger.disabled"));
		assertTrue(queue.contains("mattmc.dev.rustGalGui.hunger.legacyControl"));
		assertTrue(queue.contains("HOTBAR_SELECTION_PRODUCER"));
		assertTrue(queue.contains("EXPERIENCE_BACKGROUND_PRODUCER"));
		assertTrue(queue.contains("EXPERIENCE_PROGRESS_PRODUCER"));
		assertTrue(queue.contains("ATTACK_CROSSHAIR_BACKGROUND_PRODUCER"));
		assertTrue(queue.contains("ATTACK_CROSSHAIR_PROGRESS_PRODUCER"));
		assertTrue(queue.contains("ATTACK_HOTBAR_BACKGROUND_PRODUCER"));
		assertTrue(queue.contains("ATTACK_HOTBAR_PROGRESS_PRODUCER"));
		assertTrue(queue.contains("BOSS_BAR_BACKGROUND_PRODUCER"));
		assertTrue(queue.contains("BOSS_BAR_PROGRESS_PRODUCER"));
		assertTrue(queue.contains("ARMOR_ICON_PRODUCER"));
		assertTrue(queue.contains("PLAYER_HEART_PRODUCER"));
		assertTrue(queue.contains("HUNGER_ICON_PRODUCER"));
		assertTrue(queue.contains("ABSORPTION_HEART_PRODUCER"));
		assertTrue(queue.contains("selected hotbar slot must be in 0..8"));
		assertTrue(queue.contains("armor value must be in 0..20"));
		assertTrue(queue.contains("PlayerHeartRequest"));
		assertTrue(queue.contains("AbsorptionHeartRequest"));
		assertFalse(queue.contains("getHealth()"));
		assertFalse(queue.contains("getMaxHealth()"));
		assertFalse(queue.contains("getAbsorptionAmount()"));
		assertTrue(queue.contains("experience progress fraction must be finite"));
		assertTrue(queue.contains("experience bar filled width is outside the vanilla range"));
		assertTrue(queue.contains("crosshair attack indicator filled width must be in 0..16"));
		assertTrue(queue.contains("hotbar attack indicator filled height must be in 0..18"));
		assertTrue(queue.contains("boss bar progress fraction must be finite"));
		assertTrue(queue.contains("boss bar filled width must be in 0.."));
			assertTrue(stratum.indexOf("GUI_HOTBAR_BASE(\"gui.hotbar.base\", 300)") < stratum.indexOf("GUI_HOTBAR_SELECTION(\"gui.hotbar.selection\", 310)"));
			assertTrue(stratum.indexOf("GUI_HOTBAR_SELECTION(\"gui.hotbar.selection\", 310)") < stratum.indexOf("GUI_ARMOR(\"gui.armor\", 350)"));
			assertTrue(stratum.indexOf("GUI_ARMOR(\"gui.armor\", 350)") < stratum.indexOf("GUI_PLAYER_HEALTH(\"gui.player-health\", 360)"));
			assertTrue(stratum.indexOf("GUI_PLAYER_HEALTH(\"gui.player-health\", 360)") < stratum.indexOf("GUI_EXPERIENCE_BAR_BACKGROUND(\"gui.experience.background\", 400)"));
			assertTrue(stratum.indexOf("GUI_EXPERIENCE_BAR_BACKGROUND(\"gui.experience.background\", 400)") < stratum.indexOf("GUI_EXPERIENCE_BAR_PROGRESS(\"gui.experience.progress\", 410)"));
		assertTrue(stratum.indexOf("GUI_EXPERIENCE_BAR_PROGRESS(\"gui.experience.progress\", 410)") < stratum.indexOf("GUI_ATTACK_CROSSHAIR_BACKGROUND(\"gui.attack.crosshair.background\", 500)"));
		assertTrue(stratum.indexOf("GUI_ATTACK_CROSSHAIR_BACKGROUND(\"gui.attack.crosshair.background\", 500)") < stratum.indexOf("GUI_ATTACK_CROSSHAIR_PROGRESS(\"gui.attack.crosshair.progress\", 510)"));
		assertTrue(stratum.indexOf("GUI_ATTACK_CROSSHAIR_PROGRESS(\"gui.attack.crosshair.progress\", 510)") < stratum.indexOf("GUI_ATTACK_HOTBAR_BACKGROUND(\"gui.attack.hotbar.background\", 520)"));
		assertTrue(stratum.indexOf("GUI_ATTACK_HOTBAR_BACKGROUND(\"gui.attack.hotbar.background\", 520)") < stratum.indexOf("GUI_ATTACK_HOTBAR_PROGRESS(\"gui.attack.hotbar.progress\", 530)"));
		assertTrue(stratum.indexOf("GUI_ATTACK_HOTBAR_PROGRESS(\"gui.attack.hotbar.progress\", 530)") < stratum.indexOf("GUI_BOSS_BAR_BACKGROUND(\"gui.boss.background\", 600)"));
		assertTrue(stratum.indexOf("GUI_BOSS_BAR_BACKGROUND(\"gui.boss.background\", 600)") < stratum.indexOf("GUI_BOSS_BAR_PROGRESS(\"gui.boss.progress\", 610)"));
			assertTrue(queue.contains("GuiSprite.HOTBAR_SELECTION"));
			assertTrue(queue.contains("GuiSprite.ARMOR_FULL"));
			assertTrue(queue.contains("GuiSprite.ARMOR_HALF"));
			assertTrue(queue.contains("GuiSprite.ARMOR_EMPTY"));
			assertTrue(queue.contains("GuiSprite.HEART_NORMAL_FULL"));
			assertTrue(queue.contains("GuiSprite.HEART_NORMAL_HALF"));
			assertTrue(queue.contains("GuiSprite.HEART_POISONED_FULL"));
			assertTrue(queue.contains("GuiSprite.HEART_WITHERED_FULL"));
			assertTrue(queue.contains("GuiSprite.HEART_FROZEN_FULL"));
			assertTrue(queue.contains("GuiSprite.HEART_ABSORBING_FULL"));
			assertTrue(queue.contains("GuiSprite.EXPERIENCE_BAR_BACKGROUND"));
		assertTrue(queue.contains("GuiSprite.EXPERIENCE_BAR_PROGRESS"));
		assertTrue(queue.contains("GuiSprite.CROSSHAIR_ATTACK_BACKGROUND"));
		assertTrue(queue.contains("GuiSprite.CROSSHAIR_ATTACK_PROGRESS"));
		assertTrue(queue.contains("GuiSprite.HOTBAR_ATTACK_BACKGROUND"));
		assertTrue(queue.contains("GuiSprite.HOTBAR_ATTACK_PROGRESS"));
		assertTrue(queue.contains("BOSS_BAR_PINK_BACKGROUND"));
		assertTrue(queue.contains("BOSS_BAR_WHITE_PROGRESS"));
		assertTrue(queue.contains("BOSS_BAR_NOTCHED_20_BACKGROUND"));
		assertTrue(queue.contains("BOSS_BAR_NOTCHED_20_PROGRESS"));
		assertTrue(queue.contains("GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT"));
		assertTrue(queue.contains("GuiFillDirection.VERTICAL_BOTTOM_TO_TOP"));
		assertTrue(queue.contains("GuiFillDirection.HORIZONTAL_LEFT_TO_RIGHT"));
		assertTrue(rustGuiFrontend.contains("uv_region"));
		assertTrue(queue.contains("selectedSlot"));
		assertTrue(queue.contains("progressFraction"));
		assertTrue(gui.indexOf("RustGalGuiRenderer.shouldDrawJavaCompatibilityGui()") < gui.indexOf("blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_SPRITE"));
		assertTrue(gui.indexOf("blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_SPRITE") < gui.indexOf("RustGalGuiRenderer.enqueueCrosshair"));
		assertTrue(gui.indexOf("RustGalGuiRenderer.shouldDrawJavaCompatibilityGui()", gui.indexOf("renderItemHotbar")) < gui.indexOf("blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE"));
			int hotbarMethod = gui.indexOf("renderItemHotbar");
			assertTrue(gui.indexOf("RustGalGuiRenderer.enqueueHotbarBase", hotbarMethod) < gui.indexOf("HOTBAR_SELECTION_SPRITE", hotbarMethod));
		assertTrue(gui.indexOf("HOTBAR_SELECTION_SPRITE", hotbarMethod) < gui.indexOf("RustGalGuiRenderer.enqueueHotbarSelection", hotbarMethod));
		assertTrue(gui.indexOf("RustGalGuiRenderer.enqueueHotbarSelection", hotbarMethod) < gui.indexOf("HOTBAR_OFFHAND_LEFT_SPRITE", hotbarMethod));
			assertTrue(gui.contains("selectedHotbarHighlightX"));
			assertTrue(gui.contains("selectedHotbarHighlightY"));
			int armorMethod = gui.indexOf("renderArmor");
			assertTrue(gui.indexOf("RustGalGuiRenderer.shouldDrawJavaCompatibilityGui()", armorMethod)
				< gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_FULL_SPRITE", armorMethod));
			assertTrue(gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_EMPTY_SPRITE", armorMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueueArmorIcons", armorMethod));
			int healthMethod = gui.indexOf("private void renderHearts");
			assertTrue(gui.indexOf("rustAbsorptionHearts.add(new AbsorptionHeartRequest", healthMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueueAbsorptionHearts", healthMethod));
			assertTrue(gui.indexOf("rustPlayerHearts.add(new PlayerHeartRequest", healthMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueuePlayerHearts", healthMethod));
			assertTrue(gui.indexOf("RustGalGuiRenderer.enqueueAbsorptionHearts", healthMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueuePlayerHearts", healthMethod));
			assertTrue(gui.indexOf("rustHeartVariant(heartType)", healthMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueuePlayerHearts", healthMethod));
			assertTrue(gui.indexOf("rustAbsorptionHeartVariant(heartType)", healthMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueueAbsorptionHearts", healthMethod));
			int foodMethod = gui.indexOf("private void renderFood");
			assertTrue(gui.indexOf("RustGalGuiRenderer.isHungerLegacyControl()", foodMethod)
				< gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, resourceLocation", foodMethod));
			assertTrue(gui.indexOf("rustHungerIcons.add(new HungerIconRequest", foodMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueueHungerIcons", foodMethod));
			assertTrue(gui.indexOf("diagnosticFoodLevel(foodData.getFoodLevel())", foodMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueueHungerIcons", foodMethod));
			int airMethod = gui.indexOf("private void renderAirBubbles");
			assertTrue(gui.indexOf("RustGalGuiRenderer.isAirLegacyControl()", airMethod)
				< gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, AIR_SPRITE", airMethod));
			assertTrue(gui.indexOf("rustAirBubbles.add(new AirBubbleRequest", airMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueueAirBubbles", airMethod));
			assertTrue(gui.indexOf("diagnosticAirSupply(player.getAirSupply(), l)", airMethod)
				< gui.indexOf("RustGalGuiRenderer.enqueueAirBubbles", airMethod));
			int experienceMethod = experienceBar.indexOf("renderBackground");
		assertTrue(experienceBar.indexOf("RustGalGuiRenderer.shouldDrawJavaCompatibilityGui()", experienceMethod)
			< experienceBar.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND_SPRITE", experienceMethod));
		assertTrue(experienceBar.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND_SPRITE", experienceMethod)
			< experienceBar.indexOf("RustGalGuiRenderer.enqueueExperienceBar", experienceMethod));
		assertTrue(experienceBar.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_PROGRESS_SPRITE", experienceMethod)
			< experienceBar.indexOf("RustGalGuiRenderer.enqueueExperienceBar", experienceMethod));
		int crosshairAttack = gui.indexOf("CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE");
		assertTrue(gui.indexOf("RustGalGuiRenderer.shouldDrawJavaCompatibilityGui()", gui.indexOf("renderCrosshair"))
			< gui.indexOf("guiGraphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE", crosshairAttack));
		int crosshairProgressBlit = gui.indexOf("guiGraphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE", crosshairAttack);
		assertTrue(crosshairProgressBlit < gui.indexOf("RustGalGuiRenderer.enqueueCrosshairAttackIndicator", crosshairProgressBlit));
		int hotbarAttack = gui.indexOf("HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE");
		assertTrue(gui.indexOf("RustGalGuiRenderer.shouldDrawJavaCompatibilityGui()", gui.indexOf("AttackIndicatorStatus.HOTBAR"))
			< gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE", hotbarAttack));
		assertTrue(gui.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE", hotbarAttack)
			< gui.indexOf("RustGalGuiRenderer.enqueueHotbarAttackIndicator", hotbarAttack));
		assertTrue(bossOverlay.contains("DeterministicCameraCapture.applyBossBarOverridesForDiagnostics"));
		assertTrue(bossOverlay.contains("RustGalGuiRenderer.enqueueBossBar"));
		assertTrue(bossOverlay.indexOf("RustGalGuiRenderer.shouldDrawJavaCompatibilityGui()") < bossOverlay.indexOf("guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, resourceLocations"));
		assertTrue(bossOverlay.indexOf("RustGalGuiRenderer.shouldDrawJavaCompatibilityGui()") < bossOverlay.indexOf("RustGalGuiRenderer.enqueueBossBar"));
		assertTrue(bossOverlay.indexOf("RustGalGuiRenderer.enqueueBossBar")
			< bossOverlay.indexOf("private void drawBar(\n\t\tGuiGraphics guiGraphics"));
		assertTrue(bossOverlay.contains("drawString(this.minecraft.font"));
		assertTrue(context.contains("MAX_COMBINED_TEXTURE_IMAGE_UNITS"));
		assertTrue(openGlResources.contains("current_frame_target_framebuffer"),
			"persistent borrowed frame-target handles must refresh the native OpenGL framebuffer after screen transitions");
		assertTrue(openGlResources.contains("borrowed_frame_targets_follow_latest_acquired_framebuffer"));
	}

	@Test
	void devOnlyRustVulkanWholeFrameShellOwnsPresentationWithoutJavaVulkanExecution() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String mode = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/RustGalVulkanWholeFrameMode.java"));
		String queue = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		String minecraft = Files.readString(Path.of("src/main/java/net/minecraft/client/Minecraft.java"));
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		String featureRenderDispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		String renderSystem = Files.readString(Path.of("src/main/java/net/blaze3d/systems/RenderSystem.java"));
		String window = Files.readString(Path.of("src/main/java/net/blaze3d/platform/Window.java"));
		String vulkanicApi = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		String rustBackends = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/mod.rs"));
		String rustVulkan = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/vulkan/mod.rs"));
		String rustGuiFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/gui_frontend.rs"));
		String rustFfi = readRustFfiModules();

		assertTrue(mode.contains("mattmc.dev.rustGalVulkanWholeFrame"));
		assertTrue(bridge.contains("mattmc_vulkanic_gal_context_create_windowed_vulkan"));
		assertTrue(bridge.contains("WINDOWED_VULKAN_CONTEXT_CREATE(49)"));
		assertTrue(queue.contains("createWindowedVulkan"));
		assertTrue(queue.contains("executeWholeFrameVulkan"));
		assertFalse(queue.contains("GLFWNativeX11.glfwGetX11Window"));
		assertFalse(queue.contains("GLFWNativeWayland.glfwGetWaylandWindow"));
		assertTrue(bridge.contains("GLFWNativeX11.glfwGetX11Window"));
		assertTrue(bridge.contains("GLFWNativeWayland.glfwGetWaylandWindow"));
		assertTrue(minecraft.contains("renderRustVulkanWholeFrameShell"));
		assertTrue(minecraft.contains("game.rendering.rust-vulkan-whole-frame"));
		assertTrue(gameRenderer.contains("rustVulkanWholeFrameGuiExtraction"));
		assertTrue(gameRenderer.contains("enqueueRustGalIndexedMeshFeaturesForWholeFrame"));
		assertTrue(levelRenderer.contains("enqueueRustGalIndexedMeshFeaturesForWholeFrame"));
		assertTrue(levelRenderer.contains("this.entityRenderDispatcher"));
		assertTrue(levelRenderer.contains(".submit("));
		assertTrue(queue.contains("primitiveFrame = RustGalWorldPrimitiveRenderer.consumeFrame();\n\t\t\t\tflushPendingWorldAssetsLocked();"));
		int shellBlockDisplaysStart = levelRenderer.indexOf("enqueueRustGalIndexedMeshFeaturesForWholeFrame");
		int shellBlockDisplaysEnd = levelRenderer.indexOf("public void extractVisibleBlockEntities", shellBlockDisplaysStart);
		String shellBlockDisplays = levelRenderer.substring(shellBlockDisplaysStart, shellBlockDisplaysEnd);
		assertFalse(shellBlockDisplays.contains("isSectionCompiled"));
		assertTrue(shellBlockDisplays.contains("Display.BlockDisplay"));
		assertTrue(shellBlockDisplays.contains("FallingBlockEntity"));
		assertTrue(shellBlockDisplays.contains("submitPistonMovingBlocksForWholeFrame"));
		assertTrue(shellBlockDisplays.contains("extractPistonMovingBlocksForWholeFrame"));
		assertTrue(shellBlockDisplays.contains("currentPistonMovingBlockRoute()"));
		assertTrue(levelRenderer.contains("PistonHeadRenderState"));
		assertTrue(levelRenderer.contains("this.extractVisibleBlockEntities(camera, partialTick, levelRenderState);"));
		assertTrue(levelRenderer.contains("PistonMovingBlockEntity pistonMovingBlockEntity"));
		assertTrue(levelRenderer.contains("this.blockEntityRenderDispatcher.tryExtractRenderState"));
		assertTrue(levelRenderer.contains("this.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)"));
		assertTrue(levelRenderer.contains("RustGalWorldPrimitiveRenderer.recordMovingBlockShellScan"));
		assertTrue(featureRenderDispatcher.contains("renderBlockFeaturesOnly"));
		assertTrue(featureRenderDispatcher.contains("blockFeatureRenderer.render"));
		assertTrue(gameRenderer.contains("RustGalFrameCoordinator.executeWholeFrameVulkan"));
		int shellStart = gameRenderer.indexOf("renderRustVulkanWholeFrameShell");
		int shellEnd = gameRenderer.indexOf("private void tryTakeScreenshotIfNeeded", shellStart);
		assertFalse(gameRenderer.substring(shellStart, shellEnd).contains("renderLevel(deltaTracker)"));
		assertTrue(renderSystem.contains("RustGalVulkanWholeFrameMode.enabledForBackend"));
		assertTrue(renderSystem.indexOf("RustGalVulkanWholeFrameMode.enabledForBackend") < renderSystem.indexOf("VulkanicAPI.beginFrame()"));
		assertTrue(window.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(vulkanicApi.contains("Java Vulkan beginFrame is disabled while Rust owns whole-frame Vulkan presentation"));
		assertTrue(vulkanicApi.contains("Java Vulkan presentTextureToScreen is disabled while Rust owns whole-frame Vulkan presentation"));
		assertTrue(vulkanicApi.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(vulkanicApi.indexOf("RustGalVulkanWholeFrameMode.enabled()") < vulkanicApi.indexOf("if (configuredValue == null)"));
		assertTrue(rustBackends.contains("create_native_windowed_vulkan_backend"));
		assertTrue(rustVulkan.contains("struct NativeWindowSurface"));
		assertTrue(rustVulkan.contains("WINDOW_PLATFORM_X11"));
		assertTrue(rustVulkan.contains("WINDOW_PLATFORM_WAYLAND"));
		assertTrue(rustGuiFrontend.contains("if batches.is_empty()"));
		assertTrue(rustGuiFrontend.contains("frame_target_color_format(frame_target)"));
		assertFalse(rustFfi.contains("ash::"));
	}

	@Test
	void rustGalGuiIntegrationHasCleanBridgeSchedulerAndGuiDomainBoundary() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String scheduler = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/RustGalFrameScheduler.java"));
		String guiRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		String frameCoordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		String guiElement = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiElementRenderState.java"));
		String rustGuiFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/gui_frontend.rs"));
		String gui = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		String bossOverlay = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/components/BossHealthOverlay.java"));
		String experienceBar = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/contextualbar/ExperienceBarRenderer.java"));

		assertFalse(Files.exists(Path.of("src/main/java/net/vulkanic/gui/GuiSpriteAtlas.java")));
		assertFalse(Files.exists(Path.of("src/main/java/net/vulkanic/gui/GuiResourceCache.java")));
		assertFalse(Files.exists(Path.of("src/main/java/net/vulkanic/gui/GuiBatchBuilder.java")));
		assertFalse(Files.exists(Path.of("src/main/java/net/vulkanic/gui/GuiPipelineLibrary.java")));
		assertFalse(bridge.contains("guiAlphaPipeline"));
		assertFalse(bridge.contains("guiInvertPipeline"));
		assertFalse(bridge.contains("CROSSHAIR_PRODUCER"));
		assertFalse(bridge.contains("HOTBAR_SELECTION_PRODUCER"));
		assertFalse(bridge.contains("ARMOR_ICON_PRODUCER"));
		assertFalse(scheduler.contains("GuiSprite"));
		assertFalse(scheduler.contains("VulkanicGalBridge"));
		assertFalse(scheduler.contains("shader"));
		assertFalse(scheduler.contains("atlas"));
		assertFalse(scheduler.contains("pipeline"));
		assertFalse(guiRenderer.contains("RustGalFrameScheduler<VulkanicGalBridge.GuiSpriteRecord>"));
		assertTrue(frameCoordinator.contains("RustGalFrameScheduler<VulkanicGalBridge.GuiSpriteRecord>"));
		assertFalse(guiRenderer.contains("record CachedResources"));
		assertFalse(guiRenderer.contains("record TextureAtlas"));
		assertFalse(guiRenderer.contains("record FrameSpriteBatch"));
		assertFalse(guiRenderer.contains("class FrameSpriteBatchBuilder"));
		assertFalse(guiRenderer.contains("GuiBatchBuilder.packCompatibleSpriteBatches"));
		assertFalse(guiRenderer.contains("GuiResourceCache resources"));
		assertFalse(guiRenderer.contains("bridge.submitGuiFrame"));
		assertTrue(frameCoordinator.contains("bridge.submitGuiFrame"));
		assertFalse(guiRenderer.contains("createBorrowedOpenGl"));
		assertFalse(guiRenderer.contains("createWindowedVulkan"));
		assertFalse(guiRenderer.contains("acquireFrame"));
		assertFalse(guiRenderer.contains("presentFrame"));
		assertFalse(guiRenderer.contains("submitWholeFrame"));
		assertFalse(guiRenderer.contains("submitWorldPrimitives"));
		assertFalse(guiRenderer.contains("executeWorldPrimitiveFrame"));
		assertFalse(guiRenderer.contains("executeWholeFrameVulkan"));
		assertFalse(guiRenderer.contains("currentAuditMetricsLine"));
		assertFalse(guiRenderer.contains("MetricsSnapshot"));
		assertFalse(guiRenderer.contains("RustGalWorldPrimitiveRenderer"));
		assertTrue(frameCoordinator.contains("createBorrowedOpenGl"));
		assertTrue(frameCoordinator.contains("createWindowedVulkan"));
		assertTrue(frameCoordinator.contains("acquireFrame"));
		assertTrue(frameCoordinator.contains("presentFrame"));
		assertTrue(frameCoordinator.contains("submitWholeFrame"));
		assertTrue(frameCoordinator.contains("submitWorldPrimitives"));
		assertTrue(frameCoordinator.contains("executeWorldPrimitiveFrame"));
		assertTrue(frameCoordinator.contains("executeWholeFrameVulkan"));
		assertTrue(frameCoordinator.contains("currentAuditMetricsLine"));
		assertTrue(frameCoordinator.contains("MetricsSnapshot"));
		assertTrue(frameCoordinator.contains("RustGalWorldPrimitiveRenderer"));
		assertTrue(rustGuiFrontend.contains("struct TextureAtlas"));
		assertTrue(rustGuiFrontend.contains("struct GuiResources"));
		assertTrue(rustGuiFrontend.contains("fn create_resources"));
		assertTrue(rustGuiFrontend.contains("fn build_atlas"));
		assertFalse(guiRenderer.contains("layout(std140) uniform GuiSpriteBatch"));
		assertTrue(rustGuiFrontend.contains("layout(std140) uniform GuiSpriteBatch"));
		assertTrue(guiElement.contains("RustGalFrameScheduler.Token token"));
		assertFalse(gui.contains("VulkanicGalBridge"));
		assertFalse(gui.contains("MemorySegment"));
		assertFalse(gui.contains("HANDLE_"));
		assertFalse(bossOverlay.contains("VulkanicGalBridge"));
		assertFalse(experienceBar.contains("VulkanicGalBridge"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueueCrosshair"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueueArmorIcons"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueuePlayerHearts"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueueAbsorptionHearts"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueueHungerIcons"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueueAirBubbles"));
		assertTrue(gui.contains("RustGalGuiRenderer.enqueueMountHearts"));
		assertTrue(gui.contains("List<PlayerHeartRequest> rustPlayerHearts"));
		assertTrue(gui.contains("List<AbsorptionHeartRequest> rustAbsorptionHearts"));
		assertTrue(gui.contains("List<HungerIconRequest> rustHungerIcons"));
		assertTrue(gui.contains("List<AirBubbleRequest> rustAirBubbles"));
		assertTrue(gui.contains("List<MountHeartRequest> rustMountHearts"));
		assertTrue(gui.contains("rustHeartVariant(heartType)"));
		assertTrue(gui.contains("rustAbsorptionHeartVariant(heartType)"));
		assertTrue(gui.contains("diagnosticPlayerHealth(player.getHealth())"));
		assertTrue(gui.contains("diagnosticPlayerMaxHealth((float)player.getAttributeValue(Attributes.MAX_HEALTH))"));
		assertTrue(gui.contains("diagnosticPlayerAbsorption(player.getAbsorptionAmount())"));
		assertTrue(gui.contains("diagnosticFoodLevel(foodData.getFoodLevel())"));
		assertFalse(guiRenderer.contains("public record PlayerHeartRequest"));
		assertFalse(guiRenderer.contains("public enum PlayerHeartVariant"));
		assertFalse(guiRenderer.contains("public enum PlayerHeartState"));
		assertFalse(guiRenderer.contains("public enum ArmorIconState"));
		assertTrue(Files.exists(Path.of("src/main/java/net/vulkanic/gui/PlayerHeartRequest.java")));
		assertTrue(Files.exists(Path.of("src/main/java/net/vulkanic/gui/AbsorptionHeartRequest.java")));
		assertTrue(Files.exists(Path.of("src/main/java/net/vulkanic/gui/MountHeartRequest.java")));
		assertTrue(Files.exists(Path.of("src/main/java/net/vulkanic/gui/GuiHeartState.java")));
		assertTrue(Files.exists(Path.of("src/main/java/net/vulkanic/gui/ArmorIconState.java")));
		assertTrue(bossOverlay.contains("RustGalGuiRenderer.enqueueBossBar"));
		assertTrue(experienceBar.contains("RustGalGuiRenderer.enqueueExperienceBar"));
		assertFalse(guiRenderer.contains("GL_TEXTURE"));
		assertFalse(guiRenderer.contains("VK_"));
	}
}
