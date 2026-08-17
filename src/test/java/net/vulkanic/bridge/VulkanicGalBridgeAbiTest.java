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
				.contains("if (selected.usesRustOpenGl() && irisPackActive)"),
			"Iris-active OpenGL falling blocks must be routed to Java compatibility before Rust selection"
		);
		assertTrue(
			Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"))
				.contains("currentPistonMovingBlockRoute()")
		);
		String weatherRoutePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		assertTrue(weatherRoutePolicy.contains("currentWeatherRoute()"));
		assertFalse(
			weatherRoutePolicy.contains("mattmc.dev.rustGalWeather.v1"),
			"weather must follow whole-frame ownership without a separate opt-in"
		);
		assertTrue(
			weatherRoutePolicy.contains("return selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());"),
			"weather must use the shared shader-aware policy once the bounded Rust weather feature is selected"
		);
		String weatherRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		assertTrue(weatherRenderer.contains("enqueueWorldWeather(WeatherRenderState state, Vec3 cameraPos, boolean depthWrite)"));
		assertTrue(weatherRenderer.contains("refreshBorrowedOpenGlFrameSeed"));
		assertTrue(weatherRenderer.contains("route=rust-vulkan-whole-frame"));
		assertTrue(weatherRenderer.contains("Rust VulkanicGAL weather submission failed after Rust route selection"));
		assertFalse(weatherRenderer.contains("return RustGalFrameCoordinator.executeWorldPrimitiveFrame(minecraft, frame, \"minecraft.world.weather\")"));
		String cloudRoutePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		assertTrue(cloudRoutePolicy.contains("currentCloudRoute()"));
		assertFalse(cloudRoutePolicy.contains("mattmc.dev.rustGalClouds.v1"));
		assertTrue(weatherRoutePolicy.contains("currentDistantHorizonsOpaqueRoute()"));
		assertFalse(
			weatherRoutePolicy.contains("mattmc.dev.rustGalDistantHorizons.opaqueV1"),
			"DH must rely on its real render-list admission, not an opt-in flag before preflight"
		);
		assertTrue(
			Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java"))
				.contains("buffers.buildRenderList(renderParams)"),
			"DH route selection must inspect the real render list before Rust ownership"
		);
		assertTrue(
			Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java"))
				.contains("hasCompleteVisibleExactAtlasCoverage()"),
			"DH must reject visible material streams that lack exact Rust-owned atlas coverage"
		);
		String cloudRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/CloudRenderer.java"));
		assertTrue(cloudRenderer.contains("enqueueRustGalClouds"));
		assertTrue(cloudRenderer.contains("this.texture.cells()"));
		assertFalse(cloudRenderer.contains("enqueueWorldCloudFaces(\n\t\t\tthis.texture,"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		assertTrue(levelRenderer.contains("currentCloudRoute().usesRustWholeFrameVulkan()"));
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
	void shaderAffectedWeatherRouteKeepsIrisAndNormalJavaVulkanCompatibilityOwned() {
		assertEquals(
			WorldRenderRoutePolicy.Route.RUST_VULKAN_WHOLE_FRAME,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, true, true, false, false),
			"a selected Rust Vulkan whole-frame route owns weather even with a shader pack configured"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, false, false, false, false),
			"normal Java Vulkan must remain on the compatibility route"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(false, false, true, false, false),
			"Iris OpenGL must remain Java-compatible until Rust owns the complete shader frame"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.RUST_OPENGL_BORROWED_CONTEXT,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(false, false, false, false, false),
			"non-Iris OpenGL may select Rust's explicit borrowed-context route"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, true, false, true, false)
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, true, false, false, true)
		);
	}

	@Test
	void arrowRouteIsWholeFrameOnlyAndStaysExplicitOutsideItsBoundedEligibility() throws Exception {
		assertEquals(
			WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY,
			WorldRenderRoutePolicy.currentArrowRoute(false),
			"unsupported arrow semantics must remain Java-owned before route selection"
		);
		String routePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		String arrowRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/ArrowRenderer.java"));
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		assertTrue(routePolicy.contains("currentArrowRoute(boolean eligible)"));
		assertTrue(routePolicy.contains("return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());"));
		assertTrue(arrowRenderer.contains("enqueueArrowModel("));
		assertTrue(arrowRenderer.contains("isSemanticCoverageOnly()"));
		assertTrue(arrowRenderer.contains("Rust whole-frame Arrow encountered unsupported semantic state before route selection"));
		String deterministicCapture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
		assertTrue(deterministicCapture.contains("decision.javaDrawn() && !decision.rustSelected() && !decision.rustQueued()"));
		assertTrue(deterministicCapture.contains("decision.rustSelected() && decision.rustQueued() && !decision.javaDrawn()"));
		assertTrue(deterministicCapture.contains("server.execute(() -> applySourceEntityIsolationOnServer(server, dimension))"));
		assertTrue(deterministicCapture.contains("private static void applySourceEntityIsolationOnServer"));
		int captureAfterRender = deterministicCapture.indexOf("public static void afterRender(Minecraft minecraft)");
		int captureIsolationAdvance = deterministicCapture.indexOf("prepareSourceEntityIsolationBeforeFrame(minecraft);", captureAfterRender);
		int captureSettledGate = deterministicCapture.indexOf("settledReadyGateSatisfied(minecraft)", captureAfterRender);
		assertTrue(captureIsolationAdvance >= 0 && captureIsolationAdvance < captureSettledGate,
			"whole-frame captures must advance copied-world source isolation before checking source-plan readiness");
		int captureModelSetup = deterministicCapture.indexOf("setupMovingMeshScenarioAfterSettledReady(minecraft)", captureAfterRender);
		int captureSourceReceiptGate = deterministicCapture.indexOf("selectedSourceCaptureRequested() && !requiredRustSourceExecutionObserved()", captureAfterRender);
		assertTrue(captureModelSetup >= 0 && captureModelSetup < captureSourceReceiptGate,
			"selected-source receipt validation must run after copied-world producer setup so a required model can produce it");
		assertTrue(deterministicCapture.contains("else if (!MODEL_MESH_SCENARIO.isEmpty() && !\"hidden\".equals(MODEL_MESH_SCENARIO))"),
			"static ModelPart scenarios must retain the same bounded capture sequence as animated model fixtures");
		assertTrue(deterministicCapture.contains("selectedSourceCaptureRequested() && isModelMeshEntityScenario()"),
			"selected-source entity captures must retain the exact producer-visible pose instead of accepting unrelated later entity work");
		assertTrue(worldRenderer.contains("extractArrowModelMesh"));
		assertTrue(worldRenderer.contains("STRATUM_WORLD_ENTITY_MESH"));
		assertTrue(worldRenderer.contains("applyPackedLight(0xffffffff, packedLight)"));
		assertTrue(worldRenderer.contains("normalPacked, 0, 0"));
		assertTrue(worldRenderer.contains("state.nameTag == null"));
		assertTrue(worldRenderer.contains("state.shadowPieces.isEmpty()"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		assertTrue(levelRenderer.contains("isVanillaArrowStateEligible(arrowState)"));
		assertFalse(worldRenderer.contains("TextureAtlasSprite sprite, ResourceLocation textureLocation"));
		String modelSubmitter = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		String livingEntityRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java"));
		assertTrue(routePolicy.contains("currentModelMeshRoute(boolean eligible)"));
		assertFalse(
			routePolicy.contains("mattmc.dev.rustGalWorldModelMesh.v1"),
			"eligible indexed entity models must follow Rust whole-frame ownership without an opt-in"
		);
		assertTrue(worldRenderer.contains("model instanceof ChestModel"));
		assertTrue(worldRenderer.contains("modelMeshIneligibilityReason("));
		assertTrue(levelRenderer.contains("eligibility=\" + (ineligibility"));
		assertTrue(worldRenderer.contains("model instanceof ShulkerBoxRenderer.ShulkerBoxModel"));
		assertTrue(worldRenderer.contains("model instanceof LlamaSpitModel"));
		assertTrue(worldRenderer.contains("model instanceof EvokerFangsModel"));
		assertTrue(worldRenderer.contains("model instanceof SkullModel"));
		assertTrue(worldRenderer.contains("isStandaloneModelMeshEligible("));
		assertTrue(worldRenderer.contains("enqueueStandaloneModelMesh("));
		assertTrue(worldRenderer.contains("readModelTexturePayload(textureIdentity)"));
		assertTrue(worldRenderer.contains("path.startsWith(\"textures/\") && path.endsWith(\".png\")"));
		assertTrue(worldRenderer.contains("neither the RenderType nor its backing GPU"));
		assertTrue(worldRenderer.contains("modelMeshRenderSemantics(renderType)"));
		assertTrue(worldRenderer.contains("BlendFunction.TRANSLUCENT.equals(blend.get())"));
		assertTrue(worldRenderer.contains("MATERIAL_MODE_TRANSLUCENT"));
		assertTrue(worldRenderer.contains("renderType.pipeline().isCull() ? CULL_BACK : CULL_NONE"));
		assertTrue(worldRenderer.contains("extractModelPartMesh("));
		assertTrue(worldRenderer.contains("recordModelMeshRouteDecision("));
		assertTrue(worldRenderer.contains("hasCurrentFrameRustModelMeshDecision(Model<?> model, TextureAtlasSprite sprite)"));
		assertTrue(worldRenderer.contains("hasCurrentFrameRustModelMeshDecision(Model<?> model, ResourceLocation textureIdentity)"));
		assertTrue(worldRenderer.contains("PENDING_MODEL_MESH_SEMANTICS.add(new ModelMeshSemanticIdentity("));
		assertTrue(worldRenderer.contains("PENDING_MODEL_MESH_SEMANTICS.contains(new ModelMeshSemanticIdentity("));
		assertTrue(worldRenderer.contains("isVanillaCowModelMeshEligible("));
		assertTrue(worldRenderer.contains("isVanillaZombieModelMeshEligible("));
		assertFalse(worldRenderer.contains("decision.frameIndex() == frameIndex"),
			"coverage replay must use the active semantic request, not a delayed capture-frame counter");
		assertTrue(levelRenderer.contains("isSelectedWholeFrameModelSemantic("),
			"coverage replay may suppress a model only after the real same-frame Rust semantic route selected it");
		assertTrue(levelRenderer.contains("model instanceof net.minecraft.client.model.ChestModel && state instanceof Float"));
		assertTrue(levelRenderer.contains("state instanceof net.minecraft.client.renderer.entity.state.CowRenderState cowRenderState"));
		assertTrue(levelRenderer.contains("state instanceof net.minecraft.client.renderer.entity.state.PigRenderState pigRenderState"));
		assertTrue(levelRenderer.contains("state instanceof net.minecraft.client.renderer.entity.state.RabbitRenderState rabbitRenderState"));
		assertTrue(levelRenderer.contains("entityRenderState instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState"));
		assertTrue(levelRenderer.contains("state instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState"));
		assertTrue(livingEntityRenderer.contains("Rust whole-frame living-model route selected without a copied indexed mesh request"));
		assertTrue(livingEntityRenderer.contains("isVanillaCowModelMeshEligible("));
		assertTrue(livingEntityRenderer.contains("isVanillaPigModelMeshEligible("));
		assertTrue(livingEntityRenderer.contains("isVanillaRabbitModelMeshEligible("));
		assertTrue(livingEntityRenderer.contains("isVanillaZombieModelMeshEligible("));
		assertTrue(livingEntityRenderer.contains("ZombieRenderState zombieRenderState"));
		assertTrue(worldRenderer.contains("&& !state.displayFireAnimation"));
		assertTrue(deterministicCapture.contains("prepareModelMeshScenarioDifficulty"));
		assertTrue(deterministicCapture.contains("zombie-requires-non-peaceful-difficulty"));
		assertTrue(deterministicCapture.contains("difficultyEffective"));
		assertTrue(deterministicCapture.contains("case \"zombie\" -> \"minecraft:textures/entity/zombie/zombie.png\""));
		assertTrue(worldRenderer.contains("textures/entity/pig/temperate_pig.png"));
		assertTrue(worldRenderer.contains("textures/entity/pig/warm_pig.png"));
		assertFalse(worldRenderer.contains("textures/entity/pig/pig.png"),
			"the direct-texture Pig contract must use the current vanilla variant identities, not the removed pre-variant texture");
		assertTrue(livingEntityRenderer.contains("enqueueStandaloneModelMesh("));
		assertTrue(livingEntityRenderer.contains("EntityRenderDispatcher.isSemanticSubmission()"));
		int wholeFrameEntitySubmitStart = levelRenderer.indexOf("private void submitSelectedWholeFrameMeshEntities");
		int wholeFrameEntitySubmitEnd = levelRenderer.indexOf("private void submitWholeFrameWorldText", wholeFrameEntitySubmitStart);
		String wholeFrameEntitySubmit = levelRenderer.substring(wholeFrameEntitySubmitStart, wholeFrameEntitySubmitEnd);
		assertTrue(wholeFrameEntitySubmit.contains("entityRenderState instanceof net.minecraft.client.renderer.entity.state.CowRenderState"));
		assertTrue(wholeFrameEntitySubmit.contains("entityRenderState instanceof net.minecraft.client.renderer.entity.state.PigRenderState"),
			"an eligible Pig must reach the same real whole-frame entity submit traversal as Cow before its renderer makes the final route decision");
		assertTrue(modelSubmitter.contains("enqueueModelMesh("));
		assertTrue(modelSubmitter.contains("Rust whole-frame model route selected without a copied indexed mesh request"));
		assertTrue(routePolicy.contains("currentModelPartMeshRoute(boolean eligible)"));
		assertFalse(routePolicy.contains("mattmc.dev.rustGalWorldModelPart.v1"));
		assertTrue(worldRenderer.contains("isModelPartMeshEligible("));
		assertTrue(worldRenderer.contains("enqueueModelPartMesh("));
		assertTrue(worldRenderer.contains("modelPartEntityIdentity(textureIdentity)"));
		assertTrue(worldRenderer.contains("\":model_part/\""));
		assertTrue(worldRenderer.contains("if (outlineColor > 0) return \"outline\";"));
		assertTrue(worldRenderer.contains("resolvedModelInstanceColor(tintedColor)"));
		assertTrue(worldRenderer.contains("tintedColor == 0 ? 0xffffffff : tintedColor"));
		assertTrue(worldRenderer.contains("PENDING_MESH_PRODUCERS.add(PendingMeshProducer.MODEL_PART)"));
		assertTrue(worldRenderer.contains("The coordinator calls this only after the owning Rust whole-frame"));
		assertFalse(worldRenderer.contains("instancesByProducer.isEmpty() || !DeterministicCameraCapture.isActiveForDiagnostics()"));
		assertTrue(worldRenderer.contains("if (containsWorldMeshAsset(meshes, mesh.meshKey()))"));
		assertTrue(modelSubmitter.contains("currentModelPartMeshRoute(rustEligible)"));
		assertTrue(modelSubmitter.contains("Rust whole-frame ModelPart route selected without a copied indexed mesh request"));
		assertTrue(levelRenderer.contains("currentModelMeshRoute(true).usesRustWholeFrameVulkan()"));
		assertTrue(levelRenderer.contains("currentModelPartMeshRoute(true).usesRustWholeFrameVulkan()"));
		assertTrue(levelRenderer.contains("submitSelectedWholeFrameModelBlockEntities"));
		assertTrue(levelRenderer.contains("instanceof net.minecraft.client.renderer.blockentity.state.ChestRenderState"));
		assertTrue(levelRenderer.contains("instanceof BedRenderState"));
		assertTrue(levelRenderer.contains("instanceof net.minecraft.client.renderer.blockentity.state.DecoratedPotRenderState"));
		assertTrue(levelRenderer.contains("instanceof BellRenderState"));
		assertTrue(levelRenderer.contains("instanceof net.minecraft.client.renderer.entity.state.LlamaSpitRenderState"));
		assertTrue(levelRenderer.contains("Animated and translucent models remain semantic coverage"));
		assertTrue(levelRenderer.contains("isSelectedWholeFrameModelSemantic(model, state, sprite)"));
		assertTrue(levelRenderer.contains("model instanceof net.minecraft.client.model.LlamaSpitModel"));
		assertTrue(levelRenderer.contains("sprite.contents().name().getPath().startsWith(\"entity/bed/\")"));
		assertTrue(levelRenderer.contains("hasCurrentFrameRustModelMeshDecision(model, sprite)"),
			"selected-source coverage must only exempt a model after the real same-frame Rust submit queued it");
		String llamaSpitRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/LlamaSpitRenderer.java"));
		assertTrue(llamaSpitRenderer.contains("enqueueStandaloneModelMesh("));
		assertTrue(llamaSpitRenderer.contains("Rust whole-frame LlamaSpit route selected"));
		assertFalse(llamaSpitRenderer.contains("TextureAtlasSprite"));
		String evokerFangsRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/EvokerFangsRenderer.java"));
		assertTrue(evokerFangsRenderer.contains("enqueueStandaloneModelMesh("));
		assertTrue(evokerFangsRenderer.contains("Rust whole-frame EvokerFangs route selected"));
		assertFalse(evokerFangsRenderer.contains("TextureAtlasSprite"));
		String witherSkullRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/WitherSkullRenderer.java"));
		assertTrue(witherSkullRenderer.contains("enqueueStandaloneModelMesh("));
		assertTrue(witherSkullRenderer.contains("Rust whole-frame WitherSkull route selected"));
		assertFalse(witherSkullRenderer.contains("TextureAtlasSprite"));
		assertTrue(deterministicCapture.contains("\"evoker-fangs\".equals(MODEL_MESH_SCENARIO)"));
		assertTrue(deterministicCapture.contains("\"wither-skull\".equals(MODEL_MESH_SCENARIO)"));
		assertTrue(deterministicCapture.contains("\"cow\".equals(MODEL_MESH_SCENARIO)"));
		assertTrue(deterministicCapture.contains("\"pig\".equals(MODEL_MESH_SCENARIO)"));
		assertTrue(deterministicCapture.contains("&& (MODEL_MESH_SCENARIO.isEmpty() || \"hidden\".equals(MODEL_MESH_SCENARIO))"));
	}

	@Test
	void experienceOrbRouteUsesOnlyTheSharedSemanticMaterialPath() throws Exception {
		String routePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		String orbRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/ExperienceOrbRenderer.java"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		String capture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));

		assertTrue(routePolicy.contains("currentExperienceOrbRoute()"));
		assertTrue(routePolicy.contains("mattmc.dev.rustGalWorldExperienceOrb.disabled"));
		assertTrue(routePolicy.contains("selectShaderAffectedRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive())"));
		assertTrue(orbRenderer.contains("enqueueExperienceOrb("));
		assertTrue(orbRenderer.contains("!submitNodeCollector.isSemanticCoverageOnly() && route.usesRustWholeFrameVulkan()"));
		assertTrue(orbRenderer.contains("Rust whole-frame experience-orb route selected without a semantic material request"));
		assertTrue(orbRenderer.contains("!route.usesRustWholeFrameVulkan()"));
		assertTrue(levelRenderer.contains("boolean experienceOrbs = net.vulkanic.world.WorldRenderRoutePolicy.currentExperienceOrbRoute().usesRustWholeFrameVulkan();"));
		assertTrue(levelRenderer.contains("experienceOrbs && entityRenderState instanceof ExperienceOrbRenderState"));
		assertTrue(worldRenderer.contains("MATERIAL_TEXTURE_EXPERIENCE_ORB"));
		assertTrue(worldRenderer.contains("MATERIAL_MODE_TRANSLUCENT"));
		assertTrue(worldRenderer.contains("recordWholeFrameExperienceOrbExecution"));
		assertTrue(coordinator.contains("recordWholeFrameExperienceOrbExecution("));
		assertTrue(capture.contains("setupExperienceOrbScenario"));
		assertTrue(capture.contains("rustGalWorldExperienceOrbExecution"));
		assertTrue(capture.contains("hasCurrentExperienceOrbRoute"));
	}

	@Test
	void beaconBeamRouteUsesTheSharedTranslucentMaterialPath() throws Exception {
		String routePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		String beaconRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/blockentity/BeaconRenderer.java"));
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		String capture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));

		assertTrue(routePolicy.contains("currentBeaconBeamRoute()"));
		assertTrue(routePolicy.contains("mattmc.dev.rustGalWorldBeaconBeam.disabled"));
		assertTrue(routePolicy.contains("selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive())"));
		assertTrue(beaconRenderer.contains("enqueueBeaconBeam("));
		assertTrue(beaconRenderer.contains("!submitNodeCollector.isSemanticCoverageOnly()"));
		assertTrue(beaconRenderer.contains("BEAM_LOCATION.equals(resourceLocation)"));
		assertTrue(worldRenderer.contains("MATERIAL_TEXTURE_BEACON_BEAM"));
		assertTrue(worldRenderer.contains("MATERIAL_MODE_TRANSLUCENT"));
		assertTrue(worldRenderer.contains("MATERIAL_SOURCE_UV_LOCAL_TEXTURE"));
		assertTrue(worldRenderer.contains("recordWholeFrameBeaconBeamExecution"));
		assertTrue(coordinator.contains("recordWholeFrameBeaconBeamExecution("));
		assertTrue(capture.contains("setupBeaconBeamScenario"));
		assertTrue(capture.contains("hasCurrentBeaconBeamRoute"));
		assertFalse(beaconRenderer.contains("TextureAtlasSprite"));
	}

	@Test
	void entityFireRouteIsCollectedBeforeWholeFrameFeatureQueuesAreCleared() throws Exception {
		String routePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		String features = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		String capture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));

		assertTrue(routePolicy.contains("currentEntityFlameRoute()"));
		assertTrue(routePolicy.contains("mattmc.dev.rustGalWorldEntityFlame.disabled"));
		assertTrue(routePolicy.contains("selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive())"));
		assertTrue(features.contains("collectEntityFlameSemantics(submitNodeCollection.getFlameSubmits(), this.atlasManager)"));
		assertTrue(features.contains("public void renderBlockFeaturesOnly()"));
		assertTrue(features.contains("this.submitNodeStorage.clear();"));
		assertTrue(levelRenderer.contains("!net.vulkanic.world.WorldRenderRoutePolicy.currentEntityFlameRoute().usesRustWholeFrameVulkan()"));
		assertTrue(worldRenderer.contains("collectEntityFlameSemantics("));
		assertTrue(worldRenderer.contains("MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS"));
		assertTrue(worldRenderer.contains("MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS"));
		assertTrue(worldRenderer.contains("MATERIAL_MODE_CUTOUT"));
		assertTrue(worldRenderer.contains("Rust whole-frame entity-fire route selected before the copied terrain atlas was registered"));
		assertTrue(worldRenderer.contains("\"entity-flame\", \"rust-vulkan-whole-frame:cutout-quads=\""));
		assertTrue(worldRenderer.contains("pendingEntityFlameQuadCount"));
		assertTrue(worldRenderer.contains("recordWholeFrameEntityFlameExecution"));
		assertTrue(coordinator.contains("primitiveFrame.entityFlameQuadCount()"));
		assertTrue(capture.contains("mattmc.dev.rustGalWorldEntityFlame.scenario"));
		assertTrue(capture.contains("igniteEntityFlameCarrier"));
		assertTrue(capture.contains("hasCurrentEntityFlameRoute"));
		assertTrue(capture.contains("entityFlameSemantic"));
		assertTrue(capture.contains("entityFlameExecuted"));
	}

	@Test
	void entityShadowRouteUsesCopiedMaterialQuadsWithoutJavaDrawOrBackendState() throws Exception {
		String routePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		String features = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		String entityDispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/EntityRenderDispatcher.java"));
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));

		assertTrue(routePolicy.contains("currentEntityShadowRoute()"));
		assertTrue(routePolicy.contains("mattmc.dev.rustGalWorldEntityShadow.disabled"));
		assertTrue(routePolicy.contains("selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive())"));
		assertTrue(features.contains("collectEntityShadowSemantics(submitNodeCollection.getShadowSubmits())"));
		assertTrue(features.contains("public void renderBlockFeaturesOnly()"));
		assertTrue(entityDispatcher.contains("rustWholeFrameShadowRoute"));
		assertTrue(entityDispatcher.contains("!rustWholeFrameShadowRoute"));
		assertTrue(entityDispatcher.contains("submitNodeCollector.submitShadow"));
		assertTrue(worldRenderer.contains("collectEntityShadowSemantics("));
		assertTrue(worldRenderer.contains("MATERIAL_TEXTURE_ENTITY_SHADOW"));
		assertTrue(worldRenderer.contains("textures/misc/shadow.png"));
		assertTrue(worldRenderer.contains("MATERIAL_MODE_TRANSLUCENT"));
		assertTrue(worldRenderer.contains("DEPTH_POLICY_TEST_NO_WRITE"));
		assertTrue(worldRenderer.contains("recordWholeFrameEntityShadowExecution"));
		assertTrue(coordinator.contains("recordWholeFrameEntityShadowExecution("));
		assertFalse(worldRenderer.contains("TextureAtlasSprite entityShadow"));
		assertFalse(worldRenderer.contains("RenderType.entityShadow"));
	}

	@Test
	void itemEntityRouteUsesOnlyTheSharedIndexedMeshPath() throws Exception {
		String routePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		String itemRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/ItemEntityRenderer.java"));
		String submitCollection = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String deterministicCapture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));

		assertTrue(routePolicy.contains("currentItemEntityMeshRoute(boolean eligible)"));
		assertTrue(routePolicy.contains("mattmc.dev.rustGalWorldItemEntity.disabled"));
		assertTrue(routePolicy.contains("selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive())"));
		assertTrue(itemRenderer.contains("beginItemEntitySubmission()"));
		assertTrue(itemRenderer.contains("endItemEntitySubmission()"));
		assertTrue(submitCollection.contains("isItemEntitySubmissionActive()"));
		assertTrue(submitCollection.contains("itemEntityMeshIneligibility("));
		assertTrue(submitCollection.contains("currentItemEntityMeshRoute(rustEligible)"));
		assertTrue(submitCollection.contains("enqueueItemEntityMesh("));
		assertTrue(levelRenderer.contains("currentItemEntityMeshRoute(true).usesRustWholeFrameVulkan()"));
		assertTrue(levelRenderer.contains("itemEntities && entityRenderState instanceof ItemEntityRenderState"));
		assertTrue(worldRenderer.contains("displayContext != ItemDisplayContext.GROUND"));
		assertTrue(worldRenderer.contains("foilType != ItemStackRenderState.FoilType.NONE"));
		assertTrue(worldRenderer.contains("ItemStackRenderState deliberately keeps an empty tint array"));
		assertTrue(worldRenderer.contains("itemQuadTintColor(bakedQuad, tintLayers)"));
		assertTrue(worldRenderer.contains("extractItemQuadMesh("));
		assertTrue(worldRenderer.contains("MATERIAL_ID_TRANSLUCENT_TEXTURED"));
		assertTrue(worldRenderer.contains("DEPTH_POLICY_TEST_NO_WRITE"));
		assertTrue(worldRenderer.contains("PENDING_MESH_PRODUCERS.add(PendingMeshProducer.ITEM_ENTITY)"));
		assertTrue(worldRenderer.contains("\"item-entity\".equals(producer)"));
		assertFalse(submitCollection.contains("getItemRenderer().render"));
		assertTrue(deterministicCapture.contains("setupItemEntityScenario"));
		assertTrue(deterministicCapture.contains("new ItemEntity(serverLevel"));
		assertTrue(deterministicCapture.contains("hasCurrentItemEntityRoute"));
		assertTrue(deterministicCapture.contains("if (!movingMeshProducerReady())"));
		assertFalse(deterministicCapture.contains("!wholeFrameAttachmentCaptureReady && !movingMeshProducerReady()"));
		assertTrue(deterministicCapture.contains("movingMeshProducerReady(deterministicRenderedFrameIndex)"));
		assertTrue(deterministicCapture.contains("required-moving-producer-not-in-submission"));
		String worldFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/world_primitive_frontend.rs"));
		assertTrue(worldFrontend.contains("WORLD_MATERIAL_MODE_TRANSLUCENT"));
		assertTrue(worldFrontend.contains("depth_policy != WORLD_DEPTH_POLICY_TEST_NO_WRITE"));
	}

	@Test
	void indexedMeshShaderStateIdentityUsesTheCopiedRuntimeTableKey() throws Exception {
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));

		assertTrue(worldRenderer.contains("int rawStateId = Block.getId(blockState);"));
		assertFalse(worldRenderer.contains("return blockState.toString().hashCode();"));
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
		assertTrue(collector.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& collector.contains("wholeFrameShaderConfigEnabled()")
			&& collector.indexOf("wholeFrameShaderConfigEnabled()") < collector.indexOf("Iris.getIrisConfig().areShadersEnabled()"),
			"whole-frame shader-source discovery must read only the copied preference before any Iris runtime state");
		assertTrue(collector.contains("ShaderPackAssetFileRecord"));
		assertTrue(collector.contains("getBooleanValueOrDefault(name)"));
		assertTrue(collector.contains("getStringValueOrDefault(name)"));
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
		assertTrue(rustOpenGlLowering.contains(".front_face(if front_face_ccw { glow::CCW } else { glow::CW })"),
			"OpenGL backend must apply the explicit GAL front-face convention instead of inheriting Java/Iris state");
		assertTrue(rustVulkanResources.contains(".front_face(front_face(desc.front_face))"));
		assertTrue(rustVulkanResources.contains("FrontFace::CounterClockwise"));
		assertTrue(rustVulkanResources.contains("vk::FrontFace::COUNTER_CLOCKWISE"));
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
		assertTrue(meshHelper.contains("currentPrimedTntRoute().usesRustOpenGl()"));

		int frameHelperStart = worldRenderer.indexOf("public static boolean shouldUseRustOpenGlWorldPrimitives()");
		int frameHelperEnd = worldRenderer.indexOf("public static boolean crackDisabledForDiagnostics()", frameHelperStart);
		String frameHelper = worldRenderer.substring(frameHelperStart, frameHelperEnd);
		assertTrue(frameHelper.contains("currentPistonMovingBlockRoute().usesRustOpenGl()"));
		assertTrue(frameHelper.contains("currentPrimedTntRoute().usesRustOpenGl()"));
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
		String wholeFrameTerrainSource = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		assertTrue(wholeFrameTerrainSource.contains("getEffectiveRenderDistance()"));
		assertTrue(wholeFrameTerrainSource.contains("admitSection"));
		assertTrue(wholeFrameTerrainSource.contains("drainVisibilityFrontier"));
		assertTrue(wholeFrameTerrainSource.contains("OcclusionCuller.getVisibilityConnections"));
		assertTrue(wholeFrameTerrainSource.contains("new ChunkBuilder(level, ChunkMeshFormats.COMPACT, false)"));
		assertTrue(wholeFrameTerrainSource.contains("workerBuilder.scheduleTask"));
		assertTrue(wholeFrameTerrainSource.contains("propagationPending"));
		assertTrue(wholeFrameTerrainSource.contains("Frustum frustum"));
		assertTrue(wholeFrameTerrainSource.contains("drainCompletedBuilds(frustum)"));
		assertFalse(wholeFrameTerrainSource.contains("RenderDevice"),
			"the direct CPU terrain source must not construct Sodium's OpenGL render-device scope");
		assertFalse(wholeFrameTerrainSource.contains("task.execute(this."),
			"whole-frame terrain meshing must not synchronously block the render thread");
		assertFalse(wholeFrameTerrainSource.contains("BUILDS_PER_FRAME"));
		assertTrue(wholeFrameTerrainSource.contains("isWholeFrameSurfaceQueueDrained"));
		assertTrue(wholeFrameTerrainSource.contains("isWholeFrameTerrainQueueDrained"));
		assertTrue(wholeFrameTerrainSource.contains("wholeFrameTerrainQueueSummary"));
		assertTrue(wholeFrameTerrainSource.contains("this.level.hasChunk(section.getX(), section.getZ())"),
			"the direct CPU source must only mesh client-resident chunks and never request server generation");
		assertFalse(wholeFrameTerrainSource.contains("HORIZONTAL_RADIUS"));
		assertFalse(wholeFrameTerrainSource.contains("VERTICAL_RADIUS"));
		String fluidRenderer = Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/compile/pipeline/DefaultFluidRenderer.java"));
		String blockRenderer = Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/compile/pipeline/BlockRenderer.java"));
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
		assertTrue(gameRenderer.contains("private float fovModifier = 1.0F;"),
			"the whole-frame shell must start from the neutral gameplay FOV before its first client tick");
		assertTrue(gameRenderer.contains("RustGalWorldPrimitiveRenderer.enqueueBlockOutline"));
			assertTrue(gameRenderer.contains("RustGalWorldPrimitiveRenderer.enqueueWorldBackground"));
			assertTrue(gameRenderer.contains("FogParameters wholeFrameFog"),
				"whole-frame background must receive the CPU fog semantic rather than raw sky color");
			assertFalse(worldRenderer.contains("level.getSkyColor(camera.getPosition(), partialTick)"),
				"Rust whole-frame background must not substitute raw sky color for the extracted fog semantic");
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
		assertTrue(worldRenderer.contains("enqueuePrimedTntBlock"));
		assertTrue(worldRenderer.contains("isPrimedTntMeshEligible"));
		assertTrue(worldRenderer.contains("BlockSubmitSource.PRIMED_TNT"));
		assertTrue(worldRenderer.contains("Primed TNT extraction failed after Rust route selection"));
		assertTrue(worldRenderer.contains("recordWorldMeshSubmittedWorkIdentity"));
		assertTrue(worldRenderer.contains("DeterministicCameraCapture.recordSubmittedWorkIdentity(family, identity)"),
			"deterministic gameplay captures must receive the same semantic mesh submission identities as benchmark rows");
			assertTrue(worldRenderer.contains("MovingBlockSubmitSource.FALLING_BLOCK"));
			assertTrue(worldRenderer.contains("MovingBlockSubmitSource.PISTON"));
			assertTrue(worldRenderer.contains("SubmitNodeStorage.BlockSubmitSource.BLOCK_DISPLAY"));
			assertTrue(worldRenderer.contains("blockSubmit.tintPos()"),
				"BlockDisplay extraction must retain the real copied display position for biome-tint semantics");
			assertTrue(worldRenderer.contains("Minecraft.getInstance().level"),
				"BlockDisplay extraction must resolve eligible model tint from the semantic client world, not an empty placeholder");
			String displayRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/DisplayRenderer.java"));
			assertTrue(displayRenderer.contains("BlockPos.containing(blockDisplayEntityRenderState.x"),
				"BlockDisplay submit must carry its render-state position before Rust route selection");
			assertTrue(worldRenderer.contains("RenderShape.MODEL"));
			assertTrue(worldRenderer.contains("specialBlockModelRenderer().get().hasRenderer(blockState.getBlock())"));
		assertTrue(worldRenderer.contains("WorldMeshAssetRecord"));
		assertTrue(worldRenderer.contains("WorldMeshInstanceRecord"));
		assertTrue(worldRenderer.contains("WorldFeatureCoverageRecord"));
		assertTrue(worldRenderer.contains("enqueueWorldFeatureCoverage"));
		assertTrue(levelRenderer.contains("WorldFeatureCoverageCollector"));
		assertTrue(levelRenderer.contains("submitSemantic("));
		int coverageStart = levelRenderer.indexOf("private void collectUnsupportedWholeFrameFeatures");
		int coverageEnd = levelRenderer.indexOf("private static final class WorldFeatureCoverageCollector", coverageStart);
		String coverageMethod = levelRenderer.substring(coverageStart, coverageEnd);
		assertFalse(coverageMethod.contains("renderAllFeatures()"),
			"whole-frame feature coverage must collect semantic counts without issuing Java feature draws");
		String entityDispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/EntityRenderDispatcher.java"));
		String blockEntityDispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher.java"));
		assertTrue(entityDispatcher.contains("void submitSemantic("));
		assertTrue(blockEntityDispatcher.contains("void submitSemantic("));
		assertTrue(entityDispatcher.contains("captureIrisRenderState"));
		assertTrue(blockEntityDispatcher.contains("captureIrisRenderState"));
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
		assertTrue(gameRenderer.contains("enqueueRustGalWeatherForWholeFrame"));
		assertTrue(gameRenderer.contains("enqueueRustGalSkyForWholeFrame"));
		assertTrue(levelRenderer.contains("weatherEffectRenderer.extractRenderState"));
		assertTrue(levelRenderer.contains("weatherRenderState.reset()"));
		assertTrue(levelRenderer.contains("RustGalWorldPrimitiveRenderer.enqueueWorldWeather"));
		assertTrue(levelRenderer.contains("skyRenderer.extractRenderState"));
		assertTrue(levelRenderer.contains("RustGalWorldPrimitiveRenderer.enqueueWorldSky"));
		assertTrue(levelRenderer.contains("this.cullTerrain(camera, frustum, this.minecraft.player.isSpectator())"));
		assertTrue(levelRenderer.contains("this.rustGalWholeFrameTerrainSource.enqueue("));
		assertFalse(levelRenderer.contains("this.renderer.enqueueRustGalStaticTerrain(camera)"),
			"whole-frame Vulkan terrain must not source visibility from Sodium's GL renderer");
		assertTrue(sodiumWorldRenderer.contains("enqueueRustGalStaticTerrain"));
		assertTrue(renderSectionManager.contains("RustGalTerrainRenderer.acceptChunkBuildOutput(chunkBuildOutput)"));
		assertTrue(wholeFrameTerrainSource.contains("new ChunkBuilder(level, ChunkMeshFormats.COMPACT, false)"));
		assertTrue(wholeFrameTerrainSource.contains("RustGalTerrainRenderer.acceptWholeFrameChunkBuildOutput(output)"));
		assertTrue(wholeFrameTerrainSource.contains("output.destroy()"),
			"the direct CPU source must release native intermediate mesh buffers after VulkanicGAL copies them");
		assertFalse(wholeFrameTerrainSource.contains("MAX_RESIDENT_RENDERABLE_SECTIONS"),
			"Rust-owned Vulkan terrain must not conceal incomplete coverage behind a resident-section cap");
		assertTrue(wholeFrameTerrainSource.contains("cpu-source-window-evicted"));
		assertFalse(wholeFrameTerrainSource.contains("RenderDevice"));
		assertFalse(wholeFrameTerrainSource.contains("RenderRegion"));
		assertFalse(wholeFrameTerrainSource.contains("WorldRenderingSettings"));
		assertTrue(blockRenderer.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"),
			"the compact whole-frame source must not read Iris material-map state while producing semantic terrain");
		assertTrue(terrainRenderer.contains("WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()"));
		assertTrue(terrainRenderer.contains("enqueueWholeFrameTerrainSections"));
		assertTrue(terrainRenderer.contains("TerrainMeshLayout.compact()"));
		assertTrue(terrainRenderer.contains("recordSubmittedWorkIdentity(\"sodium-terrain\", identity)"),
			"the direct CPU terrain producer must participate in the shared settled-work family");
		assertTrue(terrainRenderer.contains("DefaultTerrainRenderPasses.SOLID"));
		assertTrue(terrainRenderer.contains("DefaultTerrainRenderPasses.CUTOUT"));
			assertTrue(terrainRenderer.contains("DefaultTerrainRenderPasses.TRANSLUCENT"));
			assertTrue(terrainRenderer.contains("ChunkSectionLayer.TRANSLUCENT"));
			assertTrue(terrainRenderer.contains("PRIMITIVE_KIND_GENERIC_FLUID"),
				"non-water fluid terrain must retain an explicit generic-fluid semantic lane");
			assertTrue(fluidRenderer.contains(": NativeSectionMeshBuilder.PRIMITIVE_KIND_GENERIC_FLUID")
				&& fluidRenderer.contains("this.rustGalPrimitiveKind != NativeSectionMeshBuilder.PRIMITIVE_KIND_UNSUPPORTED_FLUID"),
				"the fluid callsite must retain non-water atlas sprites instead of classifying them as omitted work");
			assertTrue(terrainRenderer.contains("without a semantic material route"),
				"unknown fluid metadata must fail closed rather than being omitted from a Rust frame");
			assertTrue(terrainRenderer.contains("MATERIAL_ID_WATER_TRANSLUCENT"),
				"built-in water must carry an explicit semantic material identity");
		assertTrue(terrainRenderer.contains("acceptChunkSortOutput"));
		assertTrue(renderSectionManager.contains("RustGalTerrainRenderer.acceptChunkSortOutput(sortOutput)"));
		assertTrue(terrainRenderer.contains("registeredAtlasGeneration"));
		assertTrue(terrainRenderer.contains("atlasTextureUpdatePayload()"));
			assertTrue(terrainRenderer.contains("vertex.colorArgb()"));
			assertTrue(terrainRenderer.contains("vertex.light()"));
			assertTrue(terrainRenderer.contains("fnv64Int(hash, vertex.midBlockPacked())"),
				"the mesh generation must cover every semantic field retained by the Rust occupancy source");
			assertTrue(terrainRenderer.contains("WorldMeshSectionRecord section"));
		assertTrue(terrainRenderer.contains("removeSection(int x, int y, int z, String reason)"));
		assertTrue(terrainRenderer.contains("TerrainDiagnostics"));
		assertTrue(terrainRenderer.contains("DeterministicCameraCapture.recordSubmittedWorkIdentity(\"static-terrain\""));
		assertTrue(terrainRenderer.contains("recordVisibleSubmissionIdentity"),
			"static-terrain readiness must identify the section/layer/generation that actually reached Rust");
		assertTrue(terrainRenderer.contains("staticTerrainExecutionSnapshot"),
			"static-terrain captures must distinguish completed static-terrain execution from unrelated world work");
		assertTrue(deterministicCapture.contains("visible-submission-identities"),
			"off-camera streaming must not replace visible semantic submission stability as the capture gate");
		assertTrue(deterministicCapture.contains("rustGalStaticTerrainDiagnostics"));
		assertTrue(deterministicCapture.contains("waiting-for-post-setup-static-terrain-execution"),
			"a static-terrain screenshot must wait for an execution after fixture setup, not merely asset registration");
		assertTrue(deterministicCapture.contains("rustGalStaticTerrainExecution"),
			"static-terrain screenshot acknowledgements must carry route-specific execution correlation");
		assertTrue(deterministicCapture.contains("appendStaticTerrainExecutionCorrelation(json, 2)"),
			"Rust final-output captures must retain static-terrain correlation instead of only the normal screenshot path");
		int finalOutputCaptureStart = deterministicCapture.indexOf("private static boolean captureWholeFrameFinalOutput()");
		int finalOutputCaptureEnd = deterministicCapture.indexOf("public static void beforeTick", finalOutputCaptureStart);
		String finalOutputCapture = deterministicCapture.substring(finalOutputCaptureStart, finalOutputCaptureEnd);
		assertTrue(finalOutputCapture.contains("appendStaticTerrainAtlasReceipt(json, 2)"),
			"Rust final-output captures must retain the atlas receipt for their exact terrain frame");
		assertTrue(finalOutputCapture.contains("appendStaticTerrainTextureProbeReceipt(json, 2)"),
			"Rust final-output captures must retain the projected palette probes required to validate that image");
		assertTrue(deterministicCapture.contains("requiredRustSourceExecutionDir"),
			"selected-source captures must wait for Rust execution evidence, not only normal-graph preparation");
		assertTrue(deterministicCapture.contains("selected-source-execution-frame-*.json"),
			"the capture gate must require the bounded Rust-written source execution record");
		assertTrue(deterministicCapture.contains("sourceExecutionReceiptHasVisibleWorldWork"),
			"selected-source capture readiness must recognize real world work independent of its producer family");
		assertTrue(deterministicCapture.contains("readJsonLongField(json, \"lod_instances\", 0L) > 0L"),
			"Distant Horizons-only selected-source frames must not be misclassified as zero work");
		assertTrue(graphicsHarness.contains("--world-static-terrain-scenario"));
		assertTrue(graphicsHarness.contains("static_terrain_workload_complete"));
		assertTrue(renderSectionManager.contains("RustGalTerrainRenderer.removeSection(x, y, z, \"section-removed\")"));
		assertTrue(worldRenderer.contains("DIRTY_WORLD_MESH_TEXTURES"));
		assertTrue(worldRenderer.contains("DIRTY_WORLD_MESH_SORTED_INDICES"));
		assertTrue(worldRenderer.contains("registerStaticTerrainSortedIndex"));
		assertTrue(bridge.contains("record WorldMeshSortedIndexRecord"));
		assertTrue(worldRenderer.contains("dirtyWorldMeshTextureAssetsLocked("));
		assertTrue(worldRenderer.contains("dirtyWorldMeshSortedIndicesLocked("));
		assertTrue(worldRenderer.contains("removeStaticTerrainMeshAsset"));
			assertTrue(rustWorldFrontend.contains("self.mesh_texture_assets.insert(texture_id, texture);")
					&& rustWorldFrontend.contains("destroy_mesh_texture_resources_for_ids(gal, &replaced_texture_ids);"),
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
	void frameAbiV15PreservesFrameContractAndAddsSemanticFogInputs() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String world = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String queue = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		String rustGuiFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/gui_frontend.rs"));
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		String gui = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		String guiRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));
		String experienceBar = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/contextualbar/ExperienceBarRenderer.java"));
		String bossOverlay = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/components/BossHealthOverlay.java"));

		assertEquals(23, VulkanicGalBridge.ABI_VERSION);
		assertTrue(bridge.contains("GUI_AFFINE_QUAD_REQUEST(92)"));
		assertTrue(bridge.contains("WORLD_TEXT_QUAD_REQUEST(93)"));
		assertTrue(bridge.contains("WORLD_TEXT_IMAGE_ASSET_PAYLOAD(94)"));
		assertTrue(bridge.contains("record WorldTextImageAssetRecord"));
		assertTrue(bridge.contains("record WorldTextQuadRecord"));
		assertTrue(bridge.contains("record GuiAffineQuadRecord"));
		assertTrue(bridge.contains("int midBlockPacked"));
		assertTrue(bridge.contains("record WorldVoxelVolumeFrameRecord"));
		assertTrue(bridge.contains("record WorldShaderEnvironmentFrameRecord"));
		assertTrue(bridge.contains("record WorldLodRenderFrameRecord"));
		assertTrue(bridge.contains("float[] modelViewMatrix"));
		assertTrue(bridge.contains("float[] projectionMatrix"));
		assertTrue(bridge.contains("float[] projectionInverseMatrix"));
		assertTrue(bridge.contains("WORLD_VOXEL_VOLUME_FRAME(72)"));
		assertTrue(bridge.contains("WORLD_SHADER_ENVIRONMENT_FRAME(73)"));
		assertTrue(bridge.contains("WORLD_FIRST_PERSON_FRAME(98)"));
		assertTrue(bridge.contains("record WorldFirstPersonFrameRecord"));
		assertTrue(bridge.contains("float[] modelViewMatrix"));
		assertTrue(bridge.contains("firstPersonMeshInstances"));
		assertTrue(bridge.contains("voxelVolumeFrame.worldGeneration()"));
		assertTrue(bridge.contains("voxelVolumeFrame.cameraX()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.timeOfDay()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.eyeSubmersion()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.farPlane()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.skyColorRed()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.darknessLightFactor()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.blindness()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.darknessFactor()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.eyeBrightnessBlock()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.eyeBrightnessSky()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.fogParameterColorRed()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.fogEnvironmentalStart()"));
		assertTrue(bridge.contains("shaderEnvironmentFrame.fogRenderDistanceEnd()"));
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
		assertTrue(world.contains("shaderPackFogParameters()"));
		assertTrue(world.contains("fogRenderer.sodium$getFogParameters()"));
		assertFalse(world.contains("FogStorage"));
		assertFalse(world.contains("FogParameters.NONE"));
		assertTrue(world.contains("shaderPackBlindness()"));
		assertTrue(world.contains("shaderPackDarknessFactor()"));
		assertTrue(world.contains("shaderPackEyeBrightness()"));
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
		assertTrue(frameCoordinator.contains("RustGalFrameScheduler<QueuedGuiRequest>"));
		assertTrue(frameCoordinator.contains("enqueueGuiAffineQuadRequest"));
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
	void semanticGuiTextTransportStaysCopiedAndBackendNeutral() throws Exception {
		String guiRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));
		String rustGui = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));

		assertTrue(guiRenderer.contains("RustGalGuiRenderer.tryEnqueueText"));
		assertTrue(rustGui.contains("FontTexture.semanticAtlasSnapshot"));
		assertTrue(rustGui.contains("GuiAffineQuadRecord"));
		assertTrue(rustGui.contains("List<TextAtlasRequest>"));
		assertTrue(rustGui.contains("request.withClip"));
		assertFalse(rustGui.contains("textState.scissor != null"));
		assertFalse(rustGui.contains("semantic-text-extraction-failed"));
		assertFalse(rustGui.contains("getTextureView("));
		assertFalse(rustGui.contains("RenderSystem."));
		assertTrue(coordinator.contains("enqueueGuiAffineQuadRequest"));
		assertTrue(coordinator.contains("SCHEDULER.takeAllItems"));
		assertTrue(coordinator.contains("withSequence(sequence)"));
		assertTrue(coordinator.contains("updateGuiRawImages"));
		assertTrue(bridge.contains("GUI_AFFINE_QUAD_REQUEST(92)"));
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
		String guiRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));
		String hud = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		String guiState = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/state/GuiRenderState.java"));
		String blitState = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/state/BlitRenderState.java"));
		String guiGraphics = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/GuiGraphics.java"));
		String guiSemanticRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		String guiStratum = Files.readString(Path.of("src/main/java/net/vulkanic/gui/GuiRenderStratum.java"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		String featureRenderDispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		String worldPrimitiveRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		String renderSystem = Files.readString(Path.of("src/main/java/net/blaze3d/systems/RenderSystem.java"));
		String window = Files.readString(Path.of("src/main/java/net/blaze3d/platform/Window.java"));
		String vulkanicApi = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		String vulkanBackend = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
		String rustBackends = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/mod.rs"));
		String rustVulkan = Files.readString(Path.of("src/main/rust/render/vulkanic/backends/vulkan/mod.rs"));
		String rustGuiFrontend = Files.readString(Path.of("src/main/rust/render/vulkanic/gui_frontend.rs"));
		String rustFfi = readRustFfiModules();
		String cubeMap = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/CubeMap.java"));
		String panoramaRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/PanoramaRenderer.java"));
		String semanticPanorama = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalPanoramaRenderer.java"));
		String wholeFrameTextureAtlas = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/TextureAtlas.java"));
		String wholeFrameTiming = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/RustGalDeterministicTiming.java"));

		assertTrue(mode.contains("mattmc.dev.rustGalVulkanWholeFrame"));
		assertTrue(mode.contains("isRustPresentationActive"));
		assertTrue(mode.contains("activateRustPresentation"));
		assertTrue(bridge.contains("mattmc_vulkanic_gal_context_create_windowed_vulkan"));
		assertTrue(bridge.contains("WINDOWED_VULKAN_CONTEXT_CREATE(49)"));
		assertTrue(queue.contains("createWindowedVulkan"));
		assertTrue(queue.contains("executeWholeFrameVulkan"));
		assertTrue(queue.contains("enum BridgeMode"));
		assertTrue(queue.contains("WINDOWED_VULKAN"));
		assertTrue(queue.contains("RustGalVulkanWholeFrameMode.activateRustPresentation"));
		assertTrue(queue.contains("whole-frame execution cannot reuse a"),
			"whole-frame Vulkan must fail closed instead of reusing a borrowed OpenGL bridge");
		assertTrue(queue.contains("borrowed OpenGL execution cannot reuse a"),
			"the partial-frame route must likewise reject a native Vulkan bridge");
		assertFalse(queue.contains("GLFWNativeX11.glfwGetX11Window"));
		assertFalse(queue.contains("GLFWNativeWayland.glfwGetWaylandWindow"));
		assertTrue(bridge.contains("GLFWNativeX11.glfwGetX11Window"));
		assertTrue(bridge.contains("GLFWNativeWayland.glfwGetWaylandWindow"));
		assertTrue(minecraft.contains("renderRustVulkanWholeFrameShell"));
		assertTrue(minecraft.contains("game.rendering.rust-vulkan-whole-frame"));
		int fpsHandleLookup = minecraft.indexOf("net.vulkanic.VulkanicCoreAPI.textureId(mainColorTexture)");
		int fpsWholeFrameGuard = minecraft.lastIndexOf("RustGalVulkanWholeFrameMode.enabled()", fpsHandleLookup);
		assertTrue(fpsHandleLookup > 0 && fpsWholeFrameGuard >= 0 && fpsWholeFrameGuard < fpsHandleLookup
			&& minecraft.contains("rust-semantic-frame-target"),
			"whole-frame diagnostics must not query a Java main-target native texture handle");
		int irisKeybinds = minecraft.indexOf("net.irisshaders.iris.Iris.handleKeybinds(this)");
		int irisKeybindGuard = minecraft.lastIndexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", irisKeybinds);
		assertTrue(irisKeybinds > 0 && irisKeybindGuard >= 0 && irisKeybindGuard < irisKeybinds,
			"whole-frame Vulkan must not invoke Iris's Java shader-toggle runtime");
		assertTrue(minecraft.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()")
			&& minecraft.contains("preloadUiShader"),
			"Rust whole-frame startup must not precompile Java GUI pipelines");
		assertTrue(minecraft.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled() && !iris$initialized")
			&& minecraft.contains("Whole-frame Vulkan does not borrow Iris's renderer lifecycle"),
			"Rust whole-frame startup must not initialize Iris before presentation ownership transfers");
		int voxelMapBootstrap = minecraft.indexOf("VoxelMap still owns Java offscreen textures and render passes");
		assertTrue(voxelMapBootstrap >= 0
			&& minecraft.indexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", voxelMapBootstrap) > voxelMapBootstrap
			&& minecraft.indexOf("VoxelMapInitializer.initialize();", voxelMapBootstrap) > voxelMapBootstrap,
			"whole-frame startup must keep VoxelMap's Java renderer unavailable");
		assertTrue(gameRenderer.contains("rustVulkanWholeFrameGuiExtraction"));
		int legacyIrisFrameState = gameRenderer.indexOf("net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setRealTickDelta");
		assertTrue(gameRenderer.contains("RustGalDeterministicTiming.partialTick(deltaTracker)")
			&& legacyIrisFrameState > gameRenderer.indexOf("if (!rustWholeFrame) {"),
			"the whole-frame route must use renderer-neutral timing before Iris frame state is touched");
		assertTrue(wholeFrameTiming.contains("mattmc.vulkan.deterministicTemporalParity.partialTick")
			&& !wholeFrameTiming.contains("net.irisshaders"),
			"whole-frame deterministic timing must preserve parity fixture settings without Iris runtime internals");
		assertTrue(gameRenderer.contains("gui.screen-semantic-extraction"),
			"the Rust whole-frame shell must extract screen semantics rather than leaving the main menu unrendered");
		assertTrue(gameRenderer.contains("gui.loading-overlay-semantic-extraction"),
			"startup overlay primitives must be submitted to Rust rather than skipped before the title screen");
		assertTrue(Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/LoadingOverlay.java"))
			.contains("renderCompatibleScreen"),
			"loading-overlay screen delegation must fail closed outside the admitted title route");
		String titleScreen = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/TitleScreen.java"));
		assertTrue(titleScreen.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled() && !iris$hasFirstInit"),
			"the admitted title route must not initialize Iris renderer runtime state");
		assertTrue(hud.contains("boolean legacyIrisDebugGroup = !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()")
			&& hud.contains("if (legacyIrisDebugGroup) {"),
			"whole-frame HUD extraction must not enter Iris GL debug state");
		assertTrue(hud.contains("no semantic VoxelMap minimap route; refusing its Java GPU renderer"),
			"a Java-GPU HUD hook must fail explicitly instead of being swallowed by the legacy HUD-hook catch block");
		assertTrue(gameRenderer.contains("renderWithTooltipAndSubtitles(guiGraphics"));
		assertTrue(gameRenderer.contains("instanceof net.minecraft.client.gui.screens.TitleScreen"),
			"screens without an established semantic route must remain fail-closed");
		assertTrue(gameRenderer.contains("instanceof net.minecraft.client.gui.screens.LevelLoadingScreen")
			&& Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/LoadingOverlay.java"))
				.contains("instanceof LevelLoadingScreen"),
			"world-load progress must use the established semantic GUI extraction path rather than a Java renderer");
		assertTrue(cubeMap.contains("Java cube-map rendering is unavailable while Rust owns whole-frame Vulkan presentation"),
			"the incomplete title panorama must fail closed instead of executing Java rendering in a Rust-owned frame");
		assertTrue(panoramaRenderer.contains("RustGalPanoramaRenderer.enqueue"));
		assertTrue(semanticPanorama.contains("resolveCubeMap") && semanticPanorama.contains("enqueueGuiMeshItemRequest"),
			"the title panorama must cross the boundary as copied semantic image data and Rust-owned mesh work");
		assertTrue(gameRenderer.contains("gui.rectangle-semantic-enqueue"));
		assertTrue(guiRenderer.contains("collectRustGalRectangleSemantics()"));
		assertTrue(guiRenderer.contains("ColoredRectangleRenderState rectangle"));
		assertTrue(guiState.contains("this.current = currentNode;"),
			"semantic GUI extraction must retain the source stratum when it appends explicit commands");
		assertTrue(guiState.contains("currentSemanticLayerOrder(GuiRenderState.SemanticPhase phase)"));
		assertTrue(guiState.contains("ELEMENTS(0)") && guiState.contains("ITEMS(1)") && guiState.contains("TEXT(2)"),
			"semantic GUI ordering must mirror GuiRenderer's element/item/text preparation phases");
		assertTrue(guiRenderer.contains("currentSemanticLayerOrder(GuiRenderState.SemanticPhase.TEXT)"));
		assertTrue(guiRenderer.contains("currentSemanticLayerOrder(GuiRenderState.SemanticPhase.ELEMENTS)"));
		assertTrue(queue.contains("String semanticLayerId") && queue.contains("int semanticLayerOrder"),
			"whole-frame GUI requests must carry an explicit source-layer order rather than a fixed HUD-only stratum");
		assertTrue(blitState.contains("@Nullable ResourceLocation semanticTexture"),
			"generic GUI blits must retain a resource identity rather than requiring a GPU-view lookup");
		assertTrue(guiGraphics.contains("submitBlit(renderPipeline, gpuTextureView, resourceLocation"),
			"GuiGraphics must attach the original resource location at the semantic callsite");
		assertTrue(guiGraphics.contains("RustGalGuiRenderer.isWholeFrameVulkanActive()")
			&& guiGraphics.contains("TextureSetup.noTexture()"),
			"whole-frame semantic blits must not materialize Java texture views before Rust copies their resource bytes");
		assertTrue(guiGraphics.contains("no semantic map-GUI route; refusing Java texture-view rendering"),
			"map GUI must remain unavailable until it has a semantic image route rather than creating a Java texture view");
		assertTrue(guiSemanticRenderer.contains("semanticSingleTexture"),
			"the Rust semantic GUI frontend must accept explicit resource identities without Java texture views");
		assertTrue(wholeFrameTextureAtlas.contains("Semantic GUI consumers retain only the stitched CPU source")
			&& wholeFrameTextureAtlas.contains("if (!rustWholeFrame) {")
			&& wholeFrameTextureAtlas.contains("this.createTexture(preparations.width(), preparations.height(), preparations.mipLevel());"),
			"whole-frame GUI atlases must not allocate Java GPU textures before Rust stages the snapshot");
		assertTrue(guiSemanticRenderer.contains("tryEnqueueUniformRectangle("));
		assertTrue(guiSemanticRenderer.contains("currentExecutionRoute() != GuiExecutionRoute.RUST_VULKAN_WHOLE_FRAME"),
			"rectangle semantics must not leak into the borrowed OpenGL GUI route");
		assertTrue(guiSemanticRenderer.contains("tryEnqueueVerticalGradientRectangle"),
			"gradient rectangles must use Rust-owned mesh interpolation rather than a Java rendering fallback");
		assertTrue(guiSemanticRenderer.contains("RECTANGLE_PRODUCER + \".gradient\""));
		assertTrue(guiSemanticRenderer.contains("SOLID_WHITE_ASSET_ID"));
		assertTrue(guiStratum.contains("GUI_RECTANGLES(\"gui.rectangles\", 100)"));
		assertTrue(gameRenderer.contains("enqueueRustGalIndexedMeshFeaturesForWholeFrame"));
		assertTrue(levelRenderer.contains("enqueueRustGalIndexedMeshFeaturesForWholeFrame"));
		assertTrue(levelRenderer.contains("this.entityRenderDispatcher"));
		assertTrue(levelRenderer.contains(".submit("));
		assertTrue(levelRenderer.contains("rustWorldTextSubmitNodeStorage"));
		assertTrue(levelRenderer.contains("submitWholeFrameWorldText"));
		assertTrue(levelRenderer.contains("WorldTextSubmitSemanticCollector"));
		assertTrue(levelRenderer.contains("this.entityRenderDispatcher.submitSemantic("));
		assertTrue(levelRenderer.contains("this.blockEntityRenderDispatcher.submitSemantic("));
		assertTrue(levelRenderer.contains("this.target.submitNameTag("));
		assertTrue(levelRenderer.contains("this.target.submitTextSemantic("));
		assertTrue(worldPrimitiveRenderer.contains("recordWorldTextTextSnapshot(submits, result)"));
		assertTrue(featureRenderDispatcher.contains("collectRustWorldTextSemanticsForWholeFrame(SubmitNodeStorage textSubmitStorage)"));
		assertTrue(featureRenderDispatcher.contains("textSubmitStorage.getSubmitsPerOrder()"));
		int rustWorldTextRoute = featureRenderDispatcher.indexOf("if (WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan())");
		int javaWorldTextCompatibility = featureRenderDispatcher.indexOf("} else if (!WorldRenderRoutePolicy.currentWorldTextRoute().equals(WorldRenderRoutePolicy.Route.DISABLED))", rustWorldTextRoute);
		int javaTextDraw = featureRenderDispatcher.indexOf("this.textFeatureRenderer.render", rustWorldTextRoute);
		assertTrue(rustWorldTextRoute >= 0 && javaWorldTextCompatibility > rustWorldTextRoute);
		assertTrue(javaTextDraw > javaWorldTextCompatibility,
			"the selected Rust world-text route must enqueue semantics rather than invoke Java text rendering");
		String worldTextCaptureSource = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
		assertTrue(worldTextCaptureSource.contains("mattmc.dev.rustGalWorldText.requireSourceCapture"));
		assertTrue(worldTextCaptureSource.contains("new Display.TextDisplay(EntityType.TEXT_DISPLAY"));
		assertTrue(worldTextCaptureSource.contains("Display.TextDisplay.FLAG_SEE_THROUGH"));
		assertTrue(worldTextCaptureSource.contains("mattmc.dev.rustGalWorldItemEntity.requireSourceCapture"));
		assertTrue(worldTextCaptureSource.contains("configureWorldTextCaptureCarrier(entity, scenario)"));
		assertTrue(worldTextCaptureSource.contains("world_text_execution")
			&& worldTextCaptureSource.contains("readJsonLongField(json.substring(textStart), \"draws\", 0L) <= 0L"),
			"the source capture must require executed Rust text, not just a name-tag producer callback");
		assertTrue(worldTextCaptureSource.contains("minecraft:item_entity/ground"),
			"the source capture must require the semantic dropped-item identity, not unrelated entity meshes");
		int wholeFrameAssetFlush = queue.indexOf("flushPendingWorldAssetsLocked();", queue.indexOf("if (wholeFrameVulkan) {"));
		int wholeFrameFrameConsume = queue.indexOf("primitiveFrame = RustGalWorldPrimitiveRenderer.consumeFrame();", wholeFrameAssetFlush);
		assertTrue(wholeFrameAssetFlush >= 0 && wholeFrameFrameConsume > wholeFrameAssetFlush);
		int lateWorldAssetFlush = queue.indexOf("flushPendingWorldAssetsAfterFrameConsumeLocked();", wholeFrameFrameConsume);
		assertTrue(lateWorldAssetFlush > wholeFrameFrameConsume,
			"resources discovered while freezing a whole frame must publish before native submission");
		assertTrue(queue.contains("flushPendingWorldAssetsLocked(true);"),
			"the post-consume flush must include mesh assets and textures rather than leaving a stale native registry");
		int shellBlockDisplaysStart = levelRenderer.indexOf("enqueueRustGalIndexedMeshFeaturesForWholeFrame");
		int shellBlockDisplaysEnd = levelRenderer.indexOf("public void extractVisibleBlockEntities", shellBlockDisplaysStart);
		String shellBlockDisplays = levelRenderer.substring(shellBlockDisplaysStart, shellBlockDisplaysEnd);
		assertFalse(shellBlockDisplays.contains("isSectionCompiled"));
		assertTrue(shellBlockDisplays.contains("BlockDisplayEntityRenderState"));
		assertTrue(shellBlockDisplays.contains("FallingBlockRenderState"));
		assertTrue(shellBlockDisplays.contains("TntRenderState"));
		assertTrue(shellBlockDisplays.contains("currentPrimedTntRoute()"));
		String deterministicCapture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
		assertTrue(deterministicCapture.contains("RUST_FINAL_OUTPUT_EVERY_POSE"));
		assertTrue(deterministicCapture.contains("captureWholeFrameAttachmentsForPose"));
		assertTrue(deterministicCapture.contains("resetWholeFrameAttachmentCaptureState();"));
		assertTrue(shellBlockDisplays.contains("submitPistonMovingBlocksForWholeFrame"));
		assertTrue(shellBlockDisplays.contains("ensurePistonMovingBlocksPresentForWholeFrame"));
		assertTrue(shellBlockDisplays.contains("currentPistonMovingBlockRoute()"));
		assertTrue(levelRenderer.contains("PistonHeadRenderState"));
		assertTrue(levelRenderer.contains("this.extractVisibleBlockEntities(camera, deltaTracker.getGameTimeDeltaPartialTick(false), this.levelRenderState);"));
		assertTrue(levelRenderer.contains("PistonMovingBlockEntity pistonMovingBlockEntity"));
		assertTrue(levelRenderer.contains("this.blockEntityRenderDispatcher.tryExtractRenderState"));
		assertTrue(levelRenderer.contains("this.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)"));
		assertTrue(levelRenderer.contains("RustGalWorldPrimitiveRenderer.recordMovingBlockShellScan"));
		assertTrue(featureRenderDispatcher.contains("renderBlockFeaturesOnly"));
		assertTrue(featureRenderDispatcher.contains("blockFeatureRenderer.render"));
		assertTrue(gameRenderer.contains("RustGalFrameCoordinator.executeWholeFrameVulkan"));
		int shellStart = gameRenderer.indexOf("renderRustVulkanWholeFrameShell");
		int shellEnd = gameRenderer.indexOf("private void tryTakeScreenshotIfNeeded", shellStart);
		String shell = gameRenderer.substring(shellStart, shellEnd);
		assertFalse(shell.contains("renderLevel(deltaTracker)"));
		assertTrue(shell.contains("this.renderDistance = this.minecraft.options.getEffectiveRenderDistance() * 16;"),
			"the whole-frame shell must initialize the same projection depth distance as the normal world renderer");
		assertTrue(shell.contains("RustShaderPackSourceCollector.activeConfiguredPackName().isPresent()"),
			"whole-frame camera semantics must use the bounded shader-pack source readiness signal");
		assertFalse(shell.contains("Iris.isPackInUseQuick()"),
			"whole-frame camera semantics must not query Iris renderer runtime state");
		assertTrue(renderSystem.contains("RustGalVulkanWholeFrameMode.enabledForBackend"));
		assertTrue(renderSystem.indexOf("RustGalVulkanWholeFrameMode.enabledForBackend") < renderSystem.indexOf("VulkanicAPI.beginFrame()"));
		assertTrue(window.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(vulkanicApi.contains("Java Vulkan beginFrame is disabled while Rust owns whole-frame Vulkan presentation"));
		int fencedTaskDrain = vulkanicApi.indexOf("public static void executePendingFenceTasks()");
		int wholeFrameFenceGuard = vulkanicApi.indexOf("RustGalVulkanWholeFrameMode.enabled()", fencedTaskDrain);
		int pendingFenceContext = vulkanicApi.indexOf("CommandContext ctx = getCommandContext();", fencedTaskDrain);
		assertTrue(fencedTaskDrain >= 0 && wholeFrameFenceGuard > fencedTaskDrain && pendingFenceContext > wholeFrameFenceGuard,
			"whole-frame Vulkan must reject Java fenced callbacks before querying a Java command context");
		assertTrue(vulkanicApi.contains("Java Vulkan presentTextureToScreen is disabled while Rust owns whole-frame Vulkan presentation"));
		assertTrue(vulkanicApi.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.isRustPresentationActive()"),
			"the Java OpenGL bootstrap backend must not remain reachable after Rust owns presentation");
		assertTrue(vulkanicApi.contains("The whole-frame route uses only semantic CPU buffers here"),
			"Rust presentation must not keep Java's dynamic-uniform GL ring active");
		assertTrue(vulkanicApi.contains("return getDevice().createCommandEncoder();"),
			"whole-frame shared resource helpers must use the semantic device, not an OpenGL compatibility backend");
		assertTrue(vulkanicApi.contains("Java Vulkan backend method '"),
			"the Java Vulkan proxy must become non-rendering once Rust owns presentation");
		assertTrue(vulkanicApi.contains("!method.getName().equals(\"getBackendType\")"),
			"only backend identity may remain observable after the Rust handoff");
		String compatibilityDevice = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanCompatibilityGpuDevice.java"));
		assertTrue(compatibilityDevice.contains("Java OpenGL compatibility device ")
			&& compatibilityDevice.contains("cannot execute rendering work. Port this callsite to explicit VulkanicGAL semantics."),
			"a live Rust presenter must reject, not execute, Java OpenGL compatibility rendering");
		assertTrue(compatibilityDevice.contains("withCompatibilityBackendForTeardown"),
			"the only post-handoff compatibility action must be bounded bootstrap teardown");
		assertTrue(vulkanBackend.contains("skipping Java Vulkan and Iris GPU renderer startup"),
			"the Rust whole-frame shell must not initialize Iris GPU state");
		assertFalse(vulkanBackend.contains("initializeVulkanCompatibilityHooks"),
			"the Rust whole-frame shell must not retain a Java/Iris GL initialization hook");
		assertTrue(vulkanBackend.contains("new VulkanWholeFrameSemanticGpuDevice()"),
			"whole-frame renderer startup must use a non-rendering semantic device instead of GlDevice");
		String semanticDevice = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanWholeFrameSemanticGpuDevice.java"));
		String fontTexture = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/font/FontTexture.java"));
		String textureAtlas = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/TextureAtlas.java"));
		String dynamicTexture = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/DynamicTexture.java"));
		String textureManager = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/TextureManager.java"));
		String rawImages = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRawImageAssets.java"));
		String sodiumGpuSync = Files.readString(Path.of("src/main/java/net/sodium/fabric/SodiumGpuSyncHelper.java"));
		String shaderManager = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/ShaderManager.java"));
		String itemBlockRenderTypes = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/ItemBlockRenderTypes.java"));
		String skyRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SkyRenderer.java"));
		String lightTexture = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LightTexture.java"));
		String fogRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/fog/FogRenderer.java"));
		assertTrue(semanticDevice.contains("semantic-only; port this callsite to explicit VulkanicGAL semantics"),
			"the whole-frame bootstrap device must reject Java rendering rather than emulate it");
		assertFalse(semanticDevice.contains("org.lwjgl") || semanticDevice.contains("IrisRenderSystem"),
			"the whole-frame bootstrap device must own no native GL/Vulkan or Iris GPU state");
		assertTrue(fontTexture.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"),
			"whole-frame font-atlas updates must bypass Java texture upload");
		assertTrue(fontTexture.contains("uploaded through VulkanicGAL by the text collector"),
			"whole-frame font glyphs must retain a copied semantic atlas path");
		assertTrue(fontTexture.contains("Objects.requireNonNull(this.textureView, \"semantic font texture view\")")
			&& fontTexture.indexOf("semantic font texture view") < fontTexture.indexOf(": this.getTextureView()"),
			"whole-frame font stitching must retain metadata directly and avoid AbstractTexture's Iris texture tracker");
		assertTrue(textureAtlas.contains("boolean rustWholeFrame = net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"),
			"whole-frame texture-atlas setup must make its non-rendering route explicit");
		assertTrue(textureAtlas.contains("TextureAtlasSprite.Ticker ticker = rustWholeFrame ? null"),
			"whole-frame texture-atlas animation must not perform Java texture uploads");
		assertTrue(dynamicTexture.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& dynamicTexture.contains("RustGalGuiRawImageAssets.stageDynamicTexture(this)")
			&& dynamicTexture.indexOf("stageDynamicTexture(this)") < dynamicTexture.indexOf("writeToTexture(this.texture, this.pixels)"),
			"whole-frame DynamicTexture updates must become copied semantic image assets before any Java upload path");
		assertTrue(textureManager.contains("RustGalGuiRawImageAssets.registerDynamicTexture(resourceLocation, dynamicTexture)")
			&& textureManager.contains("RustGalGuiRawImageAssets.unregisterDynamicTexture(resourceLocation, dynamicTexture)"),
			"DynamicTexture resource identities must be bound and retired by the texture manager, not by Java GPU handles");
		assertTrue(rawImages.contains("MemoryUtil.memByteBuffer(image.getPointer(), pixels.length).get(pixels)")
			&& rawImages.contains("stage(asset)")
			&& rawImages.contains("DYNAMIC_TEXTURES"),
			"whole-frame dynamic images must copy bounded CPU pixels into the existing VulkanicGAL raw-image queue");
		assertTrue(bridge.contains("MAX_RAW_IMAGE_BYTES = 64 * 1024 * 1024")
			&& rustFfi.contains("FFI_MAX_GUI_ASSET_BYTES: usize = 64 * 1024 * 1024"),
			"Java and Rust raw-image boundaries must share the explicit 64 MiB semantic-image limit");
		assertTrue(rustGuiFrontend.contains("if batches.is_empty()"));
		String rustGuiRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		assertTrue(rustGuiRenderer.contains("admissibleAffineQuad(")
			&& rustGuiRenderer.contains("copied-blit-outside-affine-contract")
			&& rustGuiRenderer.indexOf("admissibleAffineQuad(") < rustGuiRenderer.indexOf("new VulkanicGalBridge.GuiAffineQuadRecord(", rustGuiRenderer.indexOf("tryEnqueueCopiedBlit")),
			"whole-frame copied blits must decline an unrepresentable affine contract before semantic submission");
		assertTrue(sodiumGpuSync.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& sodiumGpuSync.indexOf("RustGalVulkanWholeFrameMode.enabled()") < sodiumGpuSync.indexOf("VulkanicAPI.getCommandContext()")
			&& sodiumGpuSync.contains("Rust owns the Vulkan queue, submission completion, and pacing."),
			"Sodium's Java GL-style fence queue must be unavailable when Rust owns whole-frame Vulkan completion");
		int wholeFrameShaderManagerGuard = shaderManager.indexOf("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())");
		int javaPipelineCacheClear = shaderManager.indexOf("VulkanicAPI.clearBackendPipelineCache()");
		assertTrue(wholeFrameShaderManagerGuard >= 0 && javaPipelineCacheClear > wholeFrameShaderManagerGuard
			&& shaderManager.contains("Java render-pipeline compilation/cache ownership ends at the\n\t\t\t// whole-frame handoff."),
			"whole-frame resource reload must not compile or clear Java rendering pipelines after the Rust handoff");
		int irisMaterialMap = itemBlockRenderTypes.indexOf("WorldRenderingSettings.INSTANCE.getBlockTypeIds()");
		int terrainLayerGuard = itemBlockRenderTypes.lastIndexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", irisMaterialMap);
		assertTrue(irisMaterialMap > 0 && terrainLayerGuard >= 0 && terrainLayerGuard < irisMaterialMap
			&& itemBlockRenderTypes.contains("whole-frame terrain receives the ordinary semantic layer below"),
			"whole-frame terrain material classification must not read Iris's Java material map");
		int fabulousIrisConfig = levelRenderer.indexOf("net.irisshaders.iris.Iris.getIrisConfig().areShadersEnabled()");
		int fabulousWholeFrameGuard = levelRenderer.lastIndexOf("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", fabulousIrisConfig);
		assertTrue(fabulousIrisConfig > 0 && fabulousWholeFrameGuard >= 0 && fabulousWholeFrameGuard < fabulousIrisConfig
			&& levelRenderer.contains("Rust owns shader-pack admission for whole-frame Vulkan"),
			"whole-frame resource reload must not inspect Iris configuration while Rust owns shader-pack admission");
		String clientPacketListener = Files.readString(Path.of("src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java"));
		int dhIrisCompatibility = clientPacketListener.indexOf("net.irisshaders.iris.Iris.loadedIncompatiblePack()");
		int dhWholeFrameGuard = clientPacketListener.lastIndexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()", dhIrisCompatibility);
		assertTrue(dhIrisCompatibility > 0 && dhWholeFrameGuard >= 0 && dhWholeFrameGuard < dhIrisCompatibility
			&& clientPacketListener.contains("copied semantic configuration instead"),
			"whole-frame login must not invoke Iris/Distant Horizons Java compatibility state");
		String benchmark = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/GraphicsFrameBenchmark.java"));
		assertTrue(benchmark.contains("!minecraft.getConnection().isAcceptingMessages()")
			&& benchmark.indexOf("!minecraft.getConnection().isAcceptingMessages()") < benchmark.indexOf("minecraft.setScreen(null);"),
			"benchmark cleanup must not mutate GUI state after connection teardown begins");
		int skyTextureInit = skyRenderer.indexOf("this.endSkyTexture = this.getTexture(END_SKY_LOCATION);");
		int skyWholeFrameGuard = skyRenderer.lastIndexOf("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", skyTextureInit);
		assertTrue(skyTextureInit > 0 && skyWholeFrameGuard >= 0 && skyWholeFrameGuard < skyTextureInit
			&& skyRenderer.contains("copies its celestial source assets"),
			"whole-frame sky reload must use the Rust-owned copied celestial assets instead of Java texture uploads");
		int sodiumSetLevel = levelRenderer.indexOf("this.renderer.setLevel(clientLevel);");
		int sodiumSetLevelGuard = levelRenderer.lastIndexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", sodiumSetLevel);
		int sodiumReload = levelRenderer.indexOf("this.renderer.reload();");
		int sodiumReloadGuard = levelRenderer.lastIndexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", sodiumReload);
		assertTrue(sodiumSetLevel > 0 && sodiumSetLevelGuard >= 0 && sodiumSetLevelGuard < sodiumSetLevel
			&& sodiumReload > 0 && sodiumReloadGuard >= 0 && sodiumReloadGuard < sodiumReload,
			"whole-frame world changes must not initialize Sodium's Java GL render device");
		int sodiumCullSetup = levelRenderer.indexOf("this.renderer.setupTerrain(camera, viewport");
		int wholeFrameCullGuard = levelRenderer.lastIndexOf("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", sodiumCullSetup);
		assertTrue(sodiumCullSetup > 0 && wholeFrameCullGuard >= 0 && wholeFrameCullGuard < sodiumCullSetup
			&& levelRenderer.contains("this.applyFrustum(frustum);"),
			"whole-frame terrain visibility must remain CPU semantic state without initializing Sodium's Java GL device");
		int sodiumSectionReady = levelRenderer.indexOf("this.renderer.isSectionReady(");
		int wholeFrameSectionGuard = levelRenderer.lastIndexOf("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", sodiumSectionReady);
		assertTrue(sodiumSectionReady > 0 && wholeFrameSectionGuard >= 0 && wholeFrameSectionGuard < sodiumSectionReady
			&& levelRenderer.contains("section.getSectionMesh() != CompiledSectionMesh.UNCOMPILED"),
			"whole-frame entity extraction must use CPU section readiness instead of Sodium's Java GL manager");
		int sodiumBlockEntities = levelRenderer.indexOf("this.renderer.extractBlockEntities(camera, f, this.destructionProgress, levelRenderState);");
		int wholeFrameBlockEntityGuard = levelRenderer.lastIndexOf("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", sodiumBlockEntities);
		assertTrue(sodiumBlockEntities > 0 && wholeFrameBlockEntityGuard >= 0 && wholeFrameBlockEntityGuard < sodiumBlockEntities
			&& levelRenderer.contains("compiled.getRenderableBlockEntities()")
			&& levelRenderer.contains("extractWholeFrameBlockEntity"),
			"whole-frame block entities must extract semantic state from CPU compiled sections without Sodium GL render lists");
		int sodiumRebuild = levelRenderer.indexOf("this.renderer.scheduleRebuildForChunk(x, y, z, important);");
		int wholeFrameDirtyGuard = levelRenderer.lastIndexOf("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", sodiumRebuild);
		assertTrue(sodiumRebuild > 0 && wholeFrameDirtyGuard >= 0 && wholeFrameDirtyGuard < sodiumRebuild
			&& levelRenderer.contains("this.viewArea.setDirty(x, y, z, important)"),
			"packet-driven world updates must mark CPU semantic sections dirty before any Sodium GL rebuild path");
		assertTrue(lightTexture.contains("this.rustSemanticLightmapInputs = this.computeRustSemanticLightmapInputs(")
			&& lightTexture.contains("clientLevel, this.minecraft.player, f, false"),
			"whole-frame lightmap updates must publish gameplay scalars rather than a Java GPU texture");
		int wholeFrameLightmap = lightTexture.indexOf("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", lightTexture.indexOf("public void updateLightTexture"));
		int irisLightmapState = lightTexture.indexOf("CapturedRenderingState.INSTANCE.setDarknessLightFactor(0.0F)", wholeFrameLightmap);
		assertTrue(wholeFrameLightmap >= 0 && irisLightmapState > wholeFrameLightmap
			&& lightTexture.substring(wholeFrameLightmap, irisLightmapState).contains("return;"),
			"the whole-frame lightmap path must return before Java Iris and GPU lightmap work");
		assertTrue(lightTexture.contains("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {\n\t\t\tVulkanicAPI.createCommandEncoder().clearColorTexture"),
			"whole-frame lightmap construction must not submit a Java texture clear");
		assertTrue(fogRenderer.contains("return this.computeFogParameters(camera, i, bl, deltaTracker, f, clientLevel, false).parameters();"),
			"whole-frame fog extraction must use the semantic-only calculation path");
		assertTrue(fogRenderer.contains("computeFogParameters(camera, i, bl, deltaTracker, f, clientLevel, true)"),
			"the normal Java fog renderer must retain its legacy-Iris side effect explicitly");
		assertTrue(fogRenderer.contains("if (updateLegacyIrisFogState && camera.getFluidInCamera()"),
			"the copied whole-frame fog record must not update Iris runtime state");
		assertTrue(vulkanicApi.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(vulkanicApi.indexOf("RustGalVulkanWholeFrameMode.enabled()") < vulkanicApi.indexOf("if (configuredValue == null)"));
		assertTrue(rustBackends.contains("create_native_windowed_vulkan_backend"));
		assertTrue(rustVulkan.contains("struct NativeWindowSurface"));
		assertTrue(rustVulkan.contains("WINDOW_PLATFORM_X11"));
		assertTrue(rustVulkan.contains("WINDOW_PLATFORM_WAYLAND"));
		assertTrue(rustGuiFrontend.contains("if batches.is_empty()"));
		assertTrue(rustGuiFrontend.contains("gal.pass_target_color_format(frame_target)?"),
			"GUI pass creation must derive its format from the explicit GAL frame target");
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
		assertTrue(frameCoordinator.contains("RustGalFrameScheduler<QueuedGuiRequest>"));
		assertTrue(frameCoordinator.contains("QueuedGuiRequest"));
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
