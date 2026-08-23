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
	void wholeFrameGuiPostEffectsAreEnqueuedExactlyOnce() throws Exception {
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		assertEquals(1, occurrences(gameRenderer, "RustGalGuiRenderer.enqueuePostEffectInvert"));
		assertEquals(1, occurrences(gameRenderer, "RustGalGuiRenderer.enqueuePostEffectCreeper"));
		assertEquals(1, occurrences(gameRenderer, "RustGalGuiRenderer.enqueuePostEffectSpider"));
	}

	@Test
	void javaPostChainCannotAdmitOrProcessPassesDuringRustPresentation() throws Exception {
		String postChain = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/PostChain.java"));
		int addToFrame = postChain.indexOf("public void addToFrame");
		int addGuard = postChain.indexOf("Java post-chain admission is unavailable while Rust owns whole-frame presentation", addToFrame);
		int process = postChain.indexOf("public void process");
		int processGuard = postChain.indexOf("Java post-chain processing is unavailable while Rust owns whole-frame presentation", process);
		assertTrue(addToFrame >= 0 && addGuard > addToFrame,
			"Java post-chain frame-graph admission must fail closed during Rust presentation");
		assertTrue(process >= 0 && processGuard > process,
			"deprecated Java post-chain execution must fail closed during Rust presentation");
	}

	@Test
	void wholeFrameMinecraftLoopDoesNotClearOrPresentThroughJava() throws Exception {
		String minecraft = Files.readString(Path.of("src/main/java/net/minecraft/client/Minecraft.java"));
		int shell = minecraft.indexOf("boolean rustWholeFrameShell");
		int compatibilityBranch = minecraft.indexOf("} else {", shell);
		assertTrue(shell >= 0 && compatibilityBranch > shell, "frame loop must branch on the Rust whole-frame shell");
		int javaClear = minecraft.indexOf("createCommandEncoder().clearColorAndDepthTextures", compatibilityBranch);
		int javaPresent = minecraft.indexOf("renderTarget.blitToScreen()", compatibilityBranch);
		assertTrue(javaClear > compatibilityBranch, "Java clear must remain compatibility-only");
		assertTrue(javaPresent > compatibilityBranch, "Java presentation must remain compatibility-only");
		assertTrue(minecraft.indexOf("createCommandEncoder().clearColorAndDepthTextures", shell) == javaClear,
			"whole-frame shell must not acquire a Java command encoder before the compatibility branch");
	}

	@Test
	void wholeFrameScreenEffectsUseSemanticGuiSubmissions() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/ScreenEffectRenderer.java"));
		int semanticStart = source.indexOf("renderRustVulkanScreenEffects");
		int semanticEnd = source.indexOf("private void renderItemActivationAnimation", semanticStart);
		assertTrue(semanticStart >= 0 && semanticEnd > semanticStart, "Rust screen-effect producer must remain explicit");
		String semantic = source.substring(semanticStart, semanticEnd);
		assertTrue(semantic.contains("submitRustSemanticTiledBlit"));
		assertTrue(semantic.contains("submitRustSemanticBlit"));
		assertFalse(semantic.contains("RenderType."), "Rust screen effects must not construct Java RenderType draws");
		assertFalse(semantic.contains("getBuffer("), "Rust screen effects must not acquire Java vertex consumers");
	}

	@Test
	void wholeFramePanoramaCannotFallBackToJavaCubeMapRendering() throws Exception {
		String panorama = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/PanoramaRenderer.java"));
		int enqueue = panorama.indexOf("RustGalPanoramaRenderer.enqueue");
		int fallback = panorama.indexOf("this.cubeMap.render", enqueue);
		assertTrue(enqueue >= 0 && fallback > enqueue, "panorama must retain the non-Vulkan compatibility renderer");
		String branch = panorama.substring(enqueue, fallback);
		assertTrue(branch.contains("isWholeFrameVulkanActive()"),
			"Rust whole-frame panorama admission must guard the Java compatibility renderer");
		assertTrue(branch.contains("panorama asset is unavailable"),
			"an unavailable Rust panorama must fail closed rather than silently render in Java");
	}

	@Test
	void wholeFrameGuardianBeamCannotFallBackToJavaCustomGeometry() throws Exception {
		String guardian = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/GuardianRenderer.java"));
		int admission = guardian.indexOf("submitNodeCollector.submitGuardianBeam");
		int fallback = guardian.indexOf("submitCustomGeometry", admission);
		assertTrue(admission >= 0 && fallback > admission, "Guardian beam must retain compatibility geometry after semantic admission");
		String branch = guardian.substring(admission, fallback);
		assertTrue(branch.contains("currentGuardianBeamRoute().usesRustWholeFrameVulkan()"));
		assertTrue(branch.contains("Rust whole-frame Guardian beam route rejected"));
	}

	@Test
	void wholeFrameEndCrystalBeamCannotFallBackToJavaCustomGeometry() throws Exception {
		String dragon = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/EnderDragonRenderer.java"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		String rustMaterials = Files.readString(Path.of("src/main/rust/render/vulkanic/world_primitive_frontend/material_registry.rs"));
		int admission = dragon.indexOf("submitNodeCollector.submitCrystalBeam");
		int fallback = dragon.indexOf("submitCustomGeometry", admission);
		assertTrue(admission >= 0 && fallback > admission, "End Crystal beam must retain compatibility geometry after semantic admission");
		String branch = dragon.substring(admission, fallback);
		assertTrue(branch.contains("if (rustCrystalBeam)"));
		assertTrue(branch.contains("End Crystal beam route rejected"));
		assertTrue(levelRenderer.contains("state instanceof net.minecraft.client.renderer.entity.state.EndCrystalRenderState"));
		assertTrue(levelRenderer.contains("\"model-mesh\", \"rust-vulkan-whole-frame:end-crystal\""));
		assertTrue(levelRenderer.contains("\"conduit\".equals(MODEL_MESH_SCENARIO)"));
		assertTrue(rustMaterials.contains("WORLD_MATERIAL_TEXTURE_CRYSTAL_BEAM"));
	}

	@Test
	void conduitModelPartCaptureSeparatesGameplayIdentityFromAtlasDiagnostic() throws Exception {
		String capture = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));
		int modelPartGate = capture.indexOf("hasCurrentModelPartMeshTraversal");
		int queuedCheck = capture.indexOf("boolean queued =", modelPartGate);
		assertTrue(modelPartGate >= 0 && queuedCheck > modelPartGate);
		String gate = capture.substring(queuedCheck, Math.min(capture.length(), queuedCheck + 500));
		assertTrue(gate.contains("expectedModelMeshDiagnosticTextureId().equals(diagnostic.textureId())"));
		assertTrue(gate.contains("\"model-part\".equals(diagnostic.provenance())"));
	}

	@Test
	void disabledWholeFrameDebugGeometryCannotFallBackToJava() throws Exception {
		String boxes = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/blockentity/BlockEntityWithBoundingBoxRenderer.java"));
		assertTrue(boxes.contains("Rust whole-frame bounding-box route is unavailable"));
		assertTrue(boxes.contains("Rust whole-frame invisible-block route is unavailable"));
		String testInstance = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/blockentity/TestInstanceRenderer.java"));
		assertTrue(testInstance.contains("Rust whole-frame error-marker route is unavailable"));
	}

	@Test
	void wholeFrameMapGeometryCannotFallBackToJava() throws Exception {
		String map = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/MapRenderer.java"));
		assertTrue(map.contains("Rust whole-frame map route is unavailable"));
		assertTrue(map.contains("Rust whole-frame map-decoration route is unavailable"));
		String hand = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java"));
		assertTrue(hand.contains("Rust whole-frame first-person map route is unavailable"));
	}

	@Test
	void disabledWholeFrameTaczRouteCannotFallBackToJavaGeometry() throws Exception {
		String tacz = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"));
		assertTrue(tacz.contains("Rust whole-frame TACZ route is unavailable"));
		assertTrue(tacz.contains("RustGalVulkanWholeFrameMode.enabled()"));
	}

	@Test
	void wholeFrameVoxelMapWaypointsCannotSilentlyDropRejectedSemanticQuads() throws Exception {
		String voxel = Files.readString(Path.of("src/main/java/net/voxelmap/VoxelConstants.java"));
		int waypointStart = voxel.indexOf("submitRustWaypointSemantics");
		assertTrue(waypointStart >= 0);
		String source = voxel.substring(waypointStart);
		assertTrue(source.contains("Rust whole-frame waypoint icon route rejected semantic quad"));
		assertTrue(source.contains("Rust whole-frame waypoint label route rejected semantic background"));
	}

	@Test
	void disabledWholeFrameShadowAndLeashRoutesCannotSilentlyDropSubmissions() throws Exception {
		String dispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		assertTrue(dispatcher.contains("Rust whole-frame entity-shadow route is unavailable"));
		assertTrue(dispatcher.contains("Rust whole-frame entity-leash route is unavailable"));
	}

	@Test
	void disabledWholeFrameDebugHitboxRouteCannotSilentlyDropSubmissions() throws Exception {
		String dispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		assertTrue(dispatcher.contains("validateRustHitboxRoute"));
		assertTrue(dispatcher.contains("Rust whole-frame debug-hitbox route is unavailable"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		assertTrue(levelRenderer.contains("featureRenderDispatcher.validateRustHitboxRoute"));
	}

	@Test
	void blockOnlyFeatureExtractionCannotSilentlyDropDisabledRustFeatures() throws Exception {
		String dispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		int method = dispatcher.indexOf("void renderBlockFeaturesOnly");
		assertTrue(method >= 0);
		String source = dispatcher.substring(method);
		assertTrue(source.contains("Rust whole-frame entity-shadow route is unavailable"));
		assertTrue(source.contains("Rust whole-frame entity-flame route is unavailable"));
		assertTrue(source.contains("Rust whole-frame entity-leash route is unavailable"));
	}

	@Test
	void overlayTextureDoesNotTouchIrisStateDuringRustWholeFrame() throws Exception {
		String overlay = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/OverlayTexture.java"));
		assertEquals(3, occurrences(overlay, "RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(overlay.contains("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())"),
			"semantic whole-frame entity data must not bind or unbind the Java/Iris overlay texture");
	}

	@Test
	void semanticTextureViewsDoNotPublishJavaTexturesToIrisTracker() throws Exception {
		String abstractTexture = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/AbstractTexture.java"));
		String reloadableTexture = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/ReloadableTexture.java"));
		assertEquals(2, occurrences(abstractTexture, "RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(abstractTexture.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(reloadableTexture.contains("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())"),
			"semantic resource-pack uploads must not register Java GPU handles with Iris");
		String renderStateShard = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/RenderStateShard.java"));
		assertEquals(4, occurrences(renderStateShard, "RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(renderStateShard.contains("var ctx = VulkanicAPI.getCommandContext();"),
			"the compatibility texture setup must remain isolated behind its whole-frame guard");
	}

	@Test
	void rustWholeFrameRenderStateShardsCannotBindJavaTextureUnits() throws Exception {
		String renderStateShard = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/RenderStateShard.java"));
		int textureShard = renderStateShard.indexOf("public static class TextureStateShard");
		int multiShard = renderStateShard.indexOf("public static class MultiTextureStateShard");
		assertTrue(textureShard >= 0 && multiShard >= 0);
		String textureSource = renderStateShard.substring(textureShard);
		String multiSource = renderStateShard.substring(multiShard, textureShard);
		assertTrue(textureSource.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& textureSource.contains("return;"),
			"single-texture compatibility setup must be inert while Rust owns the frame");
		assertTrue(multiSource.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& multiSource.contains("return;"),
			"multi-texture compatibility setup must be inert while Rust owns the frame");
		String renderType = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/RenderType.java"));
		assertTrue(renderType.contains("Java immediate RenderType drawing is unavailable while Rust owns whole-frame presentation"),
			"immediate Java RenderType submission must remain unavailable on the Rust route");
	}

	@Test
	void rustWholeFrameItemSubmissionDoesNotPublishIrisCapturedState() throws Exception {
		String itemState = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/item/ItemStackRenderState.java"));
		int setup = itemState.indexOf("boolean captureIrisRenderState =");
		int restore = itemState.indexOf("CapturedRenderingState.INSTANCE.setCurrentBlockEntity(lastBState)", setup);
		int helper = itemState.indexOf("private void iris$setupId", setup);
		assertTrue(setup >= 0 && restore > setup && helper > restore);
		String submission = itemState.substring(setup, helper);
		assertTrue(submission.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"),
			"Rust item semantics must disable Iris captured-state save/restore");
		assertTrue(submission.contains("if (captureIrisRenderState)"),
			"Iris item identity publication must remain compatibility-only");
		String helperSource = itemState.substring(helper);
		assertTrue(helperSource.contains("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) return;"),
			"the Iris item identity helper must fail closed under Rust presentation");
	}

	@Test
	void rustWholeFrameEntityItemLayersDoNotPublishIrisCapturedState() throws Exception {
		for (String file : List.of(
			"CapeLayer.java", "EquipmentLayerRenderer.java", "SimpleEquipmentLayer.java", "WingsLayer.java")) {
			String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/layers", file));
			assertTrue(source.contains("EntityRenderDispatcher.isSemanticSubmission()"),
				file + " must distinguish semantic collection from compatibility rendering");
			assertTrue(source.contains("RustGalVulkanWholeFrameMode.enabled()"),
				file + " must suppress Iris item identity publication on the Rust route");
		}
	}

	@Test
	void rustWholeFrameModelStorageShimsAreInert() throws Exception {
		String storage = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeStorage.java"));
		assertTrue(storage.contains("private static boolean rustWholeFrame()"),
			"semantic storage must expose one explicit Rust ownership predicate");
		assertEquals(10, occurrences(storage, "if (SubmitNodeStorage.rustWholeFrame()) return;"),
			"every compatibility ModelStorage capture/set hook must fail closed for Rust");
	}

	@Test
	void rustWholeFrameBlockLayerLookupLazilyLoadsIrisMapping() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/ItemBlockRenderTypes.java"));
		assertTrue(source.contains("private static final class IrisLayerSet"),
			"Iris block-layer mapping must be isolated behind a lazy compatibility holder");
		assertTrue(source.contains("private static final ChunkSectionLayer[] VALUE = createIrisLayerSet()"),
			"the compatibility mapping must initialize only when its holder is touched");
		assertFalse(source.contains("static {\n\t\tLAYER_SET_VANILLA"),
			"Rust terrain classification must not eagerly initialize Iris block material mappings");
		int rustGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()");
		int holderUse = source.indexOf("IrisLayerSet.VALUE", rustGuard);
		assertTrue(rustGuard >= 0 && holderUse > rustGuard,
			"Iris block-layer mapping must remain behind the Rust whole-frame guard");
	}

	@Test
	void rustWholeFrameRejectsJavaBufferAcquisitionBeforePresentationActivation() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/MultiBufferSource.java"));
		int method = source.indexOf("public VertexConsumer getBuffer(RenderType renderType)");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int builder = source.indexOf("new BufferBuilder", method);
		assertTrue(method >= 0 && guard > method && builder > guard,
			"Java buffer construction must be fenced before Rust presentation activation completes");
		assertTrue(source.contains("Java Vulkan buffer-source rendering is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void abortedWholeFrameCancelsOutstandingSchedulerWork() throws Exception {
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		assertTrue(coordinator.contains("frameCancelled = false"));
		assertTrue(coordinator.contains("SCHEDULER.cancelFrame(frameId, \"frame-aborted\")"),
			"submit/present failures must cancel the acquired frame's queued semantic work");
		assertTrue(coordinator.contains("!executeCounted && frameId != 0L && !frameCancelled"),
			"aborted-frame cleanup must not double-cancel an acquire-skipped frame");
		assertTrue(coordinator.contains("bridge.cancelFrame(frameId, correlationId)"),
			"aborted frames must release the native acquired presentation image, not only scheduler work");
		String frameFfi = readRustFfiModules();
		assertTrue(frameFfi.contains("mattmc_vulkanic_gal_frame_cancel"),
			"the native frame cancellation ABI must be present");
	}

	private static int occurrences(String source, String needle) {
		int count = 0;
		for (int offset = 0; (offset = source.indexOf(needle, offset)) >= 0; offset += needle.length()) count++;
		return count;
	}

	@Test
	void lateDebugFrameGraphPassCannotReopenJavaVulkanAfterRustPresentation() throws Exception {
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		assertTrue(levelRenderer.contains("Java late-debug pass is unavailable while Rust owns presentation"),
			"late debug Java draws must fail closed if the Rust shell is already presenting");
		assertTrue(levelRenderer.contains("Java main world pass is unavailable while Rust owns presentation"),
			"main world Java draws must fail closed if the Rust shell is already presenting");
		assertTrue(levelRenderer.contains("Java LevelRenderer.renderLevel is unavailable while Rust owns whole-frame Vulkan"),
			"legacy LevelRenderer entry must not become a hidden Java Vulkan presenter");
		int renderLevel = levelRenderer.indexOf("public void renderLevel(");
		int selectedGuard = levelRenderer.indexOf("RustGalVulkanWholeFrameMode.enabled()", renderLevel);
		int irisSetup = levelRenderer.indexOf("DHCompat.checkFrame()", renderLevel);
		assertTrue(renderLevel >= 0 && selectedGuard > renderLevel && irisSetup > selectedGuard,
			"selected Vulkan must reject LevelRenderer before any Iris frame state is touched");
		String lightTexture = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LightTexture.java"));
		assertTrue(lightTexture.contains("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())"),
			"selected Vulkan lightmap updates must stay on semantic inputs instead of Java GPU/Iris state");
	}

	@Test
	void guiBlurBoundaryTravelsAsSemanticWholeFrameDataAndNeverRunsJavaPostProcess() throws Exception {
		String bridge = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		assertTrue(bridge.contains("guiBlurBeforeStratum"));
		assertTrue(bridge.contains("Struct.WHOLE_FRAME_SUBMIT.setInt(request, 31, guiBlurBeforeStratum)"));
		assertTrue(bridge.contains("Struct.WHOLE_FRAME_SUBMIT.setInt(request, 32, guiBlurRadius)"));
		assertTrue(coordinator.contains("renderState.blurBeforeStratumIndex()"));
		assertTrue(coordinator.contains("guiBlurBeforeStratum"));
		assertTrue(coordinator.contains("getMenuBackgroundBlurriness()"));
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		assertTrue(gameRenderer.contains("blur boundary is semantic frame data"));
		assertFalse(gameRenderer.contains("gui-blur-post-process"));
	}

	@Test
	void fabulousTransparencyCannotSilentlyCollapseIntoTheSingleTargetWholeFrameGraph() throws Exception {
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		assertTrue(gameRenderer.contains("Minecraft.useShaderTransparency()"));
		assertTrue(gameRenderer.contains("six distinct") && gameRenderer.contains("Rust-owned external"));
		assertTrue(gameRenderer.contains("Fabulous transparency is unavailable"));
		int shellStart = gameRenderer.indexOf("renderRustVulkanWholeFrameShell");
		int transparencyGate = gameRenderer.indexOf("Fabulous transparency is unavailable", shellStart);
		int profilerSetup = gameRenderer.indexOf("ProfilerFiller profilerFiller", shellStart);
		int indexedMeshEnqueue = gameRenderer.indexOf("enqueueRustGalIndexedMeshFeaturesForWholeFrame", shellStart);
		int guiExtraction = gameRenderer.indexOf("semantic-gui-extraction", shellStart);
		assertTrue(shellStart >= 0 && transparencyGate > indexedMeshEnqueue && guiExtraction > transparencyGate
			&& profilerSetup > shellStart,
			"the Fabulous gate must inspect copied semantic work before GUI submission, while allowing opaque-only extraction");
	}

	@Test
	void legacyGameRendererEntryPointCannotBecomeASecondVulkanPresenter() throws Exception {
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		int renderStart = gameRenderer.indexOf("public void render(DeltaTracker deltaTracker, boolean bl)");
		int guard = gameRenderer.indexOf("Java GameRenderer.render is unavailable while Rust Vulkan owns the whole frame", renderStart);
		int legacyWorld = gameRenderer.indexOf("this.renderLevel(deltaTracker)", renderStart);
		assertTrue(renderStart >= 0 && guard > renderStart,
			"the legacy renderer must fail closed when Rust owns Vulkan presentation");
		assertTrue(legacyWorld < 0 || guard < legacyWorld,
			"the Java renderer must not reach world rendering before the ownership guard");
	}

	@Test
	void worldBorderCannotFallBackToJavaVulkanAfterRustSemanticRejection() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/WorldBorderRenderer.java"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		int enqueue = source.indexOf("RustGalWorldPrimitiveRenderer.enqueueWorldBorder");
		int ownership = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", enqueue);
		int javaPass = source.indexOf("VulkanicAPI.createRenderPass", ownership);
		assertTrue(enqueue >= 0 && ownership > enqueue && javaPass > ownership,
			"world-border Java rendering must be behind an explicit non-Rust ownership gate");
		assertTrue(source.contains("Java Vulkan fallback is unavailable"),
			"an admitted Rust frame must fail closed when world-border semantic admission fails");
		assertTrue(levelRenderer.contains("boolean accepted = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueWorldBorder"),
			"the whole-frame world-border caller must retain the semantic admission result");
		assertTrue(levelRenderer.contains("Rust whole-frame world-border route rejected visible semantic work"),
			"visible rejected world-border work must not be silently omitted");
	}

	@Test
	void wholeFrameFingerprintSeparatesParticleMaterialWorkFromGenericMaterialWork() throws Exception {
		String coordinator = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		assertTrue(coordinator.contains("long particleQuads = frame.materialQuads().stream()"));
		assertTrue(coordinator.contains("RustGalWorldPrimitiveRenderer.MATERIAL_SOURCE_PARTICLES"));
		assertTrue(coordinator.contains("+ \" particle_quads=\" + particleQuads"));
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
			RustGalGuiRenderer.GuiExecutionRoute.DISABLED,
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
			RustGalGuiRenderer.selectExecutionRouteForTests(true, true, false, true),
			"GUI legacy controls must fail closed while Rust Vulkan owns the frame"
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
			WorldRenderRoutePolicy.Route.DISABLED,
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
		String shaderAffectedRoutePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		assertTrue(shaderAffectedRoutePolicy.contains("BooleanSupplier irisPackActive"),
			"shader-affected routing must keep Iris state behind a lazy supplier boundary");
		assertTrue(shaderAffectedRoutePolicy.contains("net.irisshaders.iris.Iris::isPackInUseQuick"),
			"borrowed OpenGL may query Iris only through the lazy compatibility supplier");
		assertTrue(shaderAffectedRoutePolicy.contains("if (!selected.usesRustOpenGl())"),
			"both Vulkan routes must return before consulting Iris runtime state");
		assertTrue(shaderAffectedRoutePolicy.contains("irisPackActive.getAsBoolean() ? Route.JAVA_COMPATIBILITY : selected"),
			"only the borrowed OpenGL route may resolve Iris pack activity");
		assertFalse(shaderAffectedRoutePolicy.contains("if (selected.usesRustOpenGl() && irisPackActive)"),
			"shader-aware routing must not restore the old eagerly-evaluated Iris boolean path");
		assertTrue(
			Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"))
				.contains("currentPistonMovingBlockRoute()")
		);
		String encoder = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanNativeCommandEncoder.java"));
		assertTrue(encoder.contains("private static boolean legacyImmediatePassIgnored()"),
			"the Java Vulkan encoder must isolate Iris ImmediateState behind a legacy-only helper");
		assertTrue(encoder.contains("if (RustGalVulkanWholeFrameMode.enabled()) {\n            return null;\n        }\n        int samplerObject = IrisRenderSystem.getBoundSamplerOnUnit(samplerUnit);"),
			"Rust whole-frame mode must not recover Iris sampler-object state");
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
		String vanillaWeatherRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/WeatherEffectRenderer.java"));
		assertTrue(weatherRenderer.contains("enqueueWorldWeather(WeatherRenderState state, Vec3 cameraPos, boolean depthWrite)"));
		assertTrue(weatherRenderer.contains("refreshBorrowedOpenGlFrameSeed"));
		assertTrue(weatherRenderer.contains("route=rust-vulkan-whole-frame"));
		assertTrue(weatherRenderer.contains("Rust VulkanicGAL weather submission failed after Rust route selection"));
		assertTrue(vanillaWeatherRenderer.contains("Rust whole-frame weather route is unavailable while Rust owns presentation"),
			"disabled weather must not reopen Java rendering after Rust presentation begins");
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
				.contains("markRustNonWaterRouteSelected()"),
			"DH must select Rust only after visible material streams pass the explicit semantic admission"
		);
		String cloudRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/CloudRenderer.java"));
		assertTrue(cloudRenderer.contains("enqueueRustGalClouds"));
		assertTrue(cloudRenderer.contains("this.texture.cells()"));
		assertFalse(cloudRenderer.contains("enqueueWorldCloudFaces(\n\t\t\tthis.texture,"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		assertTrue(levelRenderer.contains("currentCloudRoute().usesRustWholeFrameVulkan()"));
		assertTrue(levelRenderer.contains("Rust whole-frame cloud route is unavailable while Rust owns presentation"),
			"disabled clouds must not reopen Java cloud rendering after Rust presentation begins");
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
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, false, false, false, false),
			"unadmitted Vulkan must remain unavailable rather than reopening Java rendering"
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
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectShaderAffectedRouteForTests(true, true, false, false, true)
			, "legacy route controls must fail closed while Rust Vulkan owns the whole frame"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectRouteForTests(true, true, false, true),
			"generic legacy route controls must not reopen Java Vulkan rendering"
		);
		assertEquals(
			WorldRenderRoutePolicy.Route.DISABLED,
			WorldRenderRoutePolicy.selectWholeFrameRouteForTests(true, true, false, true),
			"whole-frame legacy route controls must remain unavailable under Rust Vulkan"
		);
	}

	@Test
	void arrowRouteIsWholeFrameOnlyAndStaysExplicitOutsideItsBoundedEligibility() throws Exception {
		assertEquals(
			WorldRenderRoutePolicy.Route.JAVA_COMPATIBILITY,
			WorldRenderRoutePolicy.currentArrowRoute(false),
			"per-Arrow admission remains unavailable when the current state is unsupported"
		);
		String routePolicy = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java"));
		String arrowRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/ArrowRenderer.java"));
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		assertTrue(routePolicy.contains("currentArrowOwnershipRoute()"),
			"Arrow ownership must be independent of per-state admission");
		assertTrue(routePolicy.contains("currentArrowRoute(boolean eligible)"));
		assertTrue(routePolicy.contains("return selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive());"));
		assertTrue(arrowRenderer.contains("WorldRenderRoutePolicy.currentArrowOwnershipRoute()"));
		assertTrue(arrowRenderer.contains("ArrowSubmitDisposition.RUST_UNAVAILABLE"));
		assertTrue(arrowRenderer.contains("\"rust-vulkan-unavailable\""));
		assertTrue(arrowRenderer.contains("enqueueArrowModel("));
		assertTrue(arrowRenderer.contains("isSemanticCoverageOnly()"));
		assertFalse(arrowRenderer.contains("Rust whole-frame Arrow encountered unsupported semantic state before route selection"),
			"unsupported Rust-owned Arrow state must be explicit unavailable work, not crash-as-routing-control-flow");
		int arrowOwnership = arrowRenderer.indexOf("WorldRenderRoutePolicy.currentArrowOwnershipRoute()");
		int arrowUnavailable = arrowRenderer.indexOf("ArrowSubmitDisposition.RUST_UNAVAILABLE", arrowOwnership);
		int arrowJavaSubmit = arrowRenderer.indexOf("submitNodeCollector.submitModel(", arrowUnavailable);
		assertTrue(arrowOwnership >= 0 && arrowUnavailable > arrowOwnership && arrowJavaSubmit > arrowUnavailable,
			"Java Arrow submission must remain outside the Rust-unavailable branch");
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
		assertTrue(worldRenderer.contains("isStandaloneTextureIdentity(textureIdentity)"),
			"generic standalone models must admit copied resource-pack and mod texture namespaces");
		assertTrue(worldRenderer.contains("!textureIdentity.getNamespace().isBlank()"),
			"standalone texture admission must still reject malformed identities");
		assertTrue(worldRenderer.contains("enqueueStandaloneModelMesh("));
		assertTrue(worldRenderer.contains("readModelTexturePayload(textureIdentity)"));
		assertTrue(worldRenderer.contains("getResourceStack(textureLocation)"),
			"semantic model textures must resolve through the complete resource-pack stack");
		assertTrue(worldRenderer.contains("resources.reversed()"),
			"resource-pack precedence must be preserved while retrying lower layers");
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
		assertTrue(worldRenderer.contains("isVanillaEndermanModelMeshEligible("));
		assertFalse(worldRenderer.contains("&& state.carriedBlock == null"),
			"Enderman carried blocks are separate semantic layer work and must not reject the Rust-owned base body");
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
		assertTrue(worldRenderer.contains("if (renderType.isOutline()) return \"outline-render-type\";"),
			"outline-only render types must remain fail-closed while regular outlined meshes use Rust metadata");
		assertTrue(worldRenderer.contains("outlineColor\n\t\t\t\t));"),
			"Rust ModelPart mesh instances must carry the semantic outline color");
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
		assertTrue(levelRenderer.contains("instanceof net.minecraft.client.renderer.entity.state.LlamaSpitRenderState"));
		assertTrue(levelRenderer.contains("sprite.contents().name().getPath().startsWith(\"entity/bed/\")"));
		assertTrue(levelRenderer.contains("hasCurrentFrameRustModelMeshDecision(model, sprite)"),
			"selected-source coverage must only exempt a model after the real same-frame Rust submit queued it");
		assertTrue(worldRenderer.contains("RustGalFrameCoordinator.isRustShaderPackSourceReady()"),
			"automatic staged source admission must activate the matching Java semantic coverage pass");
		String llamaSpitRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/LlamaSpitRenderer.java"));
		assertTrue(llamaSpitRenderer.contains("enqueueStandaloneTranslucentModelMesh("));
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
		assertTrue(deterministicCapture.contains("\"end-crystal\".equals(MODEL_MESH_SCENARIO)"));
		assertTrue(deterministicCapture.contains("crystal.setBeamTarget"));
		assertTrue(deterministicCapture.contains("EntityType.END_CRYSTAL"));
		assertTrue(modelSubmitter.contains("recordSubmittedWorkIdentity(\n\t\t\t\"crystal-beam\""),
			"End Crystal beam traversal must publish a strict semantic receipt");
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
		assertTrue(orbRenderer.contains("boolean rustWholeFrame = route.usesRustWholeFrameVulkan()"));
		assertTrue(orbRenderer.contains("!submitNodeCollector.isSemanticCoverageOnly() && rustWholeFrame"));
		assertTrue(orbRenderer.contains("Rust whole-frame experience-orb route selected without a semantic material request"));
		assertTrue(orbRenderer.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(orbRenderer.contains("!submitNodeCollector.isSemanticCoverageOnly()"));
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
		String beaconEntity = Files.readString(Path.of("src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java"));

		assertTrue(routePolicy.contains("currentBeaconBeamRoute()"));
		assertTrue(routePolicy.contains("mattmc.dev.rustGalWorldBeaconBeam.disabled"));
		assertTrue(routePolicy.contains("selectWholeFrameRoute(VulkanicAPI.isVulkanBackendSelected(), rustWholeFrameShellActive())"));
		assertTrue(beaconRenderer.contains("enqueueBeaconBeam("));
		assertTrue(beaconRenderer.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& beaconRenderer.contains("Rust whole-frame beacon-beam route is unavailable while Rust owns presentation"),
			"disabled beacon semantics must not silently disappear after Rust presentation begins");
		assertTrue(beaconRenderer.contains("!submitNodeCollector.isSemanticCoverageOnly()"));
		assertTrue(beaconRenderer.contains("BEAM_LOCATION.equals(resourceLocation)"));
		assertTrue(worldRenderer.contains("MATERIAL_TEXTURE_BEACON_BEAM"));
		assertTrue(worldRenderer.contains("MATERIAL_MODE_TRANSLUCENT"));
		assertTrue(worldRenderer.contains("MATERIAL_SOURCE_UV_LOCAL_TEXTURE"));
		assertTrue(worldRenderer.contains("recordWholeFrameBeaconBeamExecution"));
		assertTrue(coordinator.contains("recordWholeFrameBeaconBeamExecution("));
		assertTrue(capture.contains("setupBeaconBeamScenario"));
		assertTrue(capture.contains("hasCurrentBeaconBeamRoute"));
		assertTrue(beaconEntity.contains("this.levels = valueInput.getIntOr(\"Levels\", 0)"),
			"vanilla beacon activation level must synchronize through the ordinary block-entity update packet");
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
		assertTrue(submitCollection.contains("isIndexedItemSubmissionActive()"));
		assertTrue(submitCollection.contains("itemEntityMeshIneligibility("));
		assertTrue(submitCollection.contains("currentItemEntityMeshRoute(rustEligible)"));
		assertTrue(submitCollection.contains("currentItemEntityOwnershipRoute().usesRustWholeFrameVulkan()")
			&& submitCollection.contains("Rust whole-frame item-entity route has no semantic mesh"),
			"unavailable Rust-owned dropped items must abort instead of silently disappearing");
		assertTrue(submitCollection.contains("enqueueItemEntityMesh("));
		assertTrue(levelRenderer.contains("currentItemEntityMeshRoute(true).usesRustWholeFrameVulkan()"));
		assertTrue(levelRenderer.contains("itemEntities && entityRenderState instanceof ItemEntityRenderState"));
		assertTrue(worldRenderer.contains("displayContext != ItemDisplayContext.GROUND"));
		assertTrue(worldRenderer.contains("foilType == ItemStackRenderState.FoilType.SPECIAL"));
		assertTrue(worldRenderer.contains("ItemStackRenderState deliberately keeps an empty tint array"));
		assertTrue(worldRenderer.contains("itemQuadTintColor(bakedQuad, tintLayers)"));
		assertTrue(worldRenderer.contains("extractItemQuadMesh("));
		assertTrue(worldRenderer.contains("MATERIAL_ID_TRANSLUCENT_TEXTURED"));
		assertTrue(worldRenderer.contains("MATERIAL_ID_GLINT_TEXTURED"));
		assertTrue(worldRenderer.contains("itemIdentity + \":glint\""));
		assertTrue(worldRenderer.contains("firstPersonItemMeshIneligibility"));
		assertTrue(worldRenderer.contains("hasFoil && readTexturePayloadForResource(ItemRenderer.ENCHANTED_GLINT_ITEM)"));
		assertTrue(worldRenderer.contains("extractModelPartMesh(modelPart, textureIdentity, sprite"));
		assertTrue(worldRenderer.contains("DEPTH_POLICY_TEST_NO_WRITE"));
		assertTrue(worldRenderer.contains("PendingMeshProducer.ITEM_ENTITY")
			&& worldRenderer.contains("PendingMeshProducer.BLOCK_ENTITY_ITEM"));
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
	void specialItemSemanticDispatchPreservesFoilState() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/item/ItemStackRenderState.java"));
		assertTrue(source.contains("layer.foilType != FoilType.NONE"),
			"special renderer semantic dispatch must preserve the resolved layer foil state");
		assertTrue(source.contains("this.foilType != FoilType.NONE"),
			"item-entity special renderer dispatch must preserve foil state for Rust glint extraction");
	}

	@Test
	void mapRendererCannotFallThroughToJavaGeometryOnRustWholeFrame() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/MapRenderer.java"));
		assertTrue(source.contains("Rust whole-frame map route rejected the copied map quad"));
		assertTrue(source.contains("Rust whole-frame map route rejected a copied decoration quad"));
		assertTrue(source.contains("currentTexturedBillboardRoute().usesRustWholeFrameVulkan()"));
		assertTrue(source.contains("boolean mapAccepted"));
		assertTrue(source.contains("boolean decorationAccepted"));
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
		assertTrue(collector.contains("activeVanillaPostEffectId()"));
		assertTrue(collector.contains("collectVanillaPostEffect("));
		assertTrue(collector.contains("withActiveVanillaPostEffectResources("));
		assertTrue(collector.contains("Iris-owned files win on path collisions"));
		assertTrue(collector.contains("sourceGenerationKey("));
		assertTrue(coordinator.contains("RustShaderPackSourceCollector.collectConfiguredPack(sourceGeneration)"));
		assertTrue(coordinator.contains("pendingShaderPackSourceName = selectionKey"));
		assertTrue(collector.contains("getResourceManager()"));
		assertTrue(collector.contains("post_effect/"));
		assertTrue(collector.contains("readBoundedResource"));
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
		assertTrue(frameCoordinator.contains("assertWholeFrameFeatureCoverage(primitiveFrame.featureCoverage())"),
			"whole-frame submission must inspect copied feature-family coverage before native submission");
		assertTrue(frameCoordinator.contains("Rust whole-frame feature coverage contains unadmitted semantic families"),
			"unadmitted feature families must fail closed before the single Rust presenter");
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
		assertTrue(wholeFrameTerrainSource.contains("MAX_SEMANTIC_MESH_WORKERS = 8"));
		assertTrue(wholeFrameTerrainSource.contains("new ChunkBuilder(level, ChunkMeshFormats.COMPACT, false, MAX_SEMANTIC_MESH_WORKERS)"));
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
			assertTrue(worldRenderer.contains("Rust whole-frame outline route rejected visible semantic work"),
				"visible rejected outline work must not be silently omitted while Rust owns presentation");
		assertTrue(gameRenderer.contains("RustGalWorldPrimitiveRenderer.enqueueWorldBackground"));
		assertTrue(worldRenderer.contains("Rust whole-frame background route is unavailable"),
			"an active Rust shell must not silently omit the background when its route is unavailable");
		assertTrue(worldRenderer.contains("Rust whole-frame weather route rejected visible semantic work"),
			"visible rejected weather work must not silently fall through to the absent Java frame graph");
		assertTrue(worldRenderer.contains("Rust whole-frame cloud route rejected visible semantic work"),
			"visible rejected cloud work must not silently fall through to the absent Java frame graph");
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
			assertTrue(worldRenderer.contains("Rust whole-frame crack route rejected visible semantic work"),
				"visible rejected crack work must not be silently omitted while Rust owns presentation");
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
		assertTrue(worldRenderer.contains("int flameSubmits = WorldRenderRoutePolicy.currentEntityFlameRoute()"),
			"Rust-owned entity flames must be removed from unsupported-family coverage after semantic collection");
		assertTrue(levelRenderer.contains("WorldFeatureCoverageCollector"));
		assertTrue(levelRenderer.contains("submitSemantic("));
		assertTrue(levelRenderer.contains("boolean arrows"),
			"selected-source coverage must receive the same Arrow admission flag as the real Rust traversal");
		int coverageStart = levelRenderer.indexOf("private void collectUnsupportedWholeFrameFeatures");
		int coverageEnd = levelRenderer.indexOf("private static final class WorldFeatureCoverageCollector", coverageStart);
		String coverageMethod = levelRenderer.substring(coverageStart, coverageEnd);
		assertFalse(coverageMethod.contains("renderAllFeatures()"),
			"whole-frame feature coverage must collect semantic counts without issuing Java feature draws");
		assertTrue(coverageMethod.contains("arrows && entityRenderState instanceof net.minecraft.client.renderer.entity.state.ArrowRenderState"));
		assertTrue(coverageMethod.contains("entityRenderState instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState"));
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
		assertTrue(worldRenderer.contains("WorldBackgroundRecord initialBackground = pendingBackground")
			&& worldRenderer.contains("pendingBackground = initialBackground"),
			"sky semantic admission must roll back scalar background state together with failed celestial quads");
		assertTrue(levelRenderer.contains("Rust whole-frame background route is unavailable while Rust owns presentation"),
			"disabled sky must not reopen Java sky rendering after Rust presentation begins");
		assertTrue(levelRenderer.contains("this.cullTerrain(camera, frustum, this.minecraft.player.isSpectator())"));
		assertTrue(levelRenderer.contains("this.rustGalWholeFrameTerrainSource.enqueue("));
		assertFalse(levelRenderer.contains("this.renderer.enqueueRustGalStaticTerrain(camera)"),
			"whole-frame Vulkan terrain must not source visibility from Sodium's GL renderer");
		assertTrue(sodiumWorldRenderer.contains("enqueueRustGalStaticTerrain"));
		assertTrue(renderSectionManager.contains("RustGalTerrainRenderer.acceptChunkBuildOutput(chunkBuildOutput)"));
		assertTrue(wholeFrameTerrainSource.contains("new ChunkBuilder(level, ChunkMeshFormats.COMPACT, false, MAX_SEMANTIC_MESH_WORKERS)"));
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
		int genericBuildEntry = terrainRenderer.indexOf("public static void acceptChunkBuildOutput(ChunkBuildOutput output)");
		int backendCheck = terrainRenderer.indexOf("VulkanicAPI.isVulkanBackendSelected()", genericBuildEntry);
		int routeCheck = terrainRenderer.indexOf("currentStaticTerrainRoute().usesRustWholeFrameVulkan()", genericBuildEntry);
		int irisLayout = terrainRenderer.indexOf("TerrainMeshLayout.activeIrisCompatible()", genericBuildEntry);
		assertTrue(genericBuildEntry >= 0 && backendCheck > genericBuildEntry && routeCheck > backendCheck && irisLayout > routeCheck,
			"generic terrain build admission must reject unadmitted Vulkan before any Iris layout lookup");
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
	void rustParticleAdmissionRejectsUnsupportedQuadLayersWithoutSilentDrop() throws Exception {
		String particleRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/ParticleFeatureRenderer.java"));
		String worldRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		assertTrue(particleRenderer.contains("quad.rustGalUnsupportedLayerCount() > 0"),
			"Rust particle admission must reject QuadParticle layers outside the copied atlas contract");
		assertTrue(particleRenderer.contains("renderer instanceof QuadParticleRenderState quad"),
			"particle admission must inspect semantic QuadParticle state rather than only callback type");
		assertTrue(particleRenderer.contains("rust-vulkan-unavailable"),
			"unsupported particle layers must remain explicit unavailable work");
		assertTrue(worldRenderer.contains("texture instanceof DynamicTexture")
			&& worldRenderer.contains("registerDynamicTextureAsset(atlasLocation, textureId)"),
			"dynamic resource-pack particle textures must use the copied Rust asset uploader");
	}

	@Test
	void rustParticleRouteDoesNotRetainJavaParticleCallbacks() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		int method = source.indexOf("void submitParticleGroup");
		int rustGate = source.indexOf("currentMaterialRoute().usesRustWholeFrameVulkan()", method);
		int callbackAppend = source.indexOf("particleGroupRenderers.add", method);
		int returnBeforeAppend = source.indexOf("return;", rustGate);
		assertTrue(method >= 0 && rustGate > method && returnBeforeAppend > rustGate && callbackAppend > returnBeforeAppend,
			"Rust particle ownership must discard Java callbacks before the legacy callback list is appended");
		assertTrue(source.contains("recordUnsupportedParticleGroup()"),
			"discarded unsupported particle callbacks must remain diagnostically visible");
	}

	@Test
	void rustWholeFrameExtractsNonQuadParticleFamiliesThroughSemanticModels() throws Exception {
		String engine = Files.readString(Path.of("src/main/java/net/minecraft/client/particle/ParticleEngine.java"));
		String renderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		assertTrue(engine.contains("enqueueRustGalModelParticles")
			&& engine.contains("ItemPickupParticleGroup")
			&& engine.contains("ElderGuardianParticleGroup")
			&& engine.contains("state.submit(submitNodeStorage, cameraRenderState)"),
			"Rust whole-frame particle extraction must submit item-pickup and elder-guardian states through the semantic collector");
		assertTrue(renderer.contains("particleEngine.enqueueRustGalModelParticles("),
			"Rust whole-frame GameRenderer extraction must invoke the non-quad particle semantic route");
	}

	@Test
	void rustWholeFrameKeepsSpecialModelPartFamiliesOnSemanticRoutes() throws Exception {
		String shield = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/special/ShieldSpecialRenderer.java"));
		String conduit = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/special/ConduitSpecialRenderer.java"));
		String trident = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/special/TridentSpecialRenderer.java"));
		String collector = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		String primitive = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		assertTrue(shield.contains("submitNodeCollector.submitModelPart(")
			&& conduit.contains("submitNodeCollector.submitModelPart("),
			"atlas-backed shield and conduit special models must remain semantic ModelPart producers");
		assertTrue(trident.contains("submitModelSemanticTexture(")
			&& trident.contains("enqueueStandaloneGlintModelMesh("),
			"sprite-less trident geometry must use the direct-texture Rust model route and explicit glint mesh");
		assertTrue(collector.contains("enqueueModelPartMesh(")
			&& primitive.contains("extractModelPartMesh("),
			"Rust whole-frame ModelPart submissions must copy indexed semantic geometry before backend submission");
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		assertTrue(levelRenderer.contains("EndGatewayRenderState")
			&& levelRenderer.contains("submitSelectedWholeFrameModelBlockEntities"),
			"end-gateway block entities must remain explicitly admitted through the Rust semantic block-entity traversal");
	}

	@Test
	void rustWorldTextCollectionDoesNotCaptureIrisModelState() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		int submitText = source.indexOf("void submitText(");
		int rustGate = source.indexOf("rustWholeFrameText", submitText);
		int capture = source.indexOf("textSubmit).iris$capture()", submitText);
		assertTrue(submitText >= 0 && rustGate > submitText && capture > rustGate,
			"text capture must be explicitly gated by Rust whole-frame ownership");
		assertTrue(source.contains("!rustWholeFrameText && !net.vulkanic.world.RustGalWorldPrimitiveRenderer.isFirstPersonGuiCaptureActive()"),
			"Rust semantic text must not retain Iris ModelStorage state");
	}

	@Test
	void rustWholeFrameModelCollectionsDoNotCaptureIrisModelState() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		assertTrue(source.contains("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())")
			&& source.contains("modelSubmit).iris$capture()")
			&& source.contains("modelPartSubmit).iris$capture()")
			&& source.contains("itemSubmit).iris$capture()"),
			"Iris ModelStorage capture must remain compatibility-only for model, ModelPart, and item records");
	}

	@Test
	void rustWholeFrameGuiDoesNotQueryIrisHudVisibility() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		int hudCheck = source.indexOf("HudHideable");
		int rustGate = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", hudCheck - 400);
		assertTrue(hudCheck >= 0 && rustGate >= 0 && rustGate < hudCheck,
			"Rust whole-frame GUI must gate Iris HUD visibility checks behind compatibility ownership");
	}

	@Test
	void rustPresentationNeverQueriesIrisFromSemanticEntityAndPortalCallsites() throws Exception {
		String dragon = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/EnderDragonRenderer.java"));
		String hand = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java"));
		String portal = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/blockentity/AbstractEndPortalRenderer.java"));
		String gateway = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/blockentity/TheEndGatewayRenderer.java"));
		assertTrue(dragon.contains("boolean rustPresentation = net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()")
			&& dragon.contains("if (!rustPresentation && !rustCrystalBeam"),
			"End Crystal semantic collection must fence Iris entity IDs with Rust presentation ownership");
		assertTrue(hand.contains("boolean rustPresentation = net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()")
			&& hand.contains("if (!rustPresentation && !rustWholeFrame"),
			"semantic first-person hand collection must fence Iris hand state with Rust presentation ownership");
		assertTrue(portal.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()")
			&& gateway.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"),
			"portal RenderType compatibility checks must not query Iris while Rust owns presentation");
	}

	@Test
	void rustWholeFrameTextureReloadDoesNotTouchIrisPbrRegistry() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/TextureManager.java"));
		int firstPbrCall = source.indexOf("PBRTextureManager.INSTANCE");
		assertTrue(firstPbrCall >= 0, "compatibility PBR lifecycle calls must remain explicit");
		int cursor = firstPbrCall;
		while (cursor >= 0) {
			int guard = source.lastIndexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", cursor);
			assertTrue(guard >= 0 && cursor - guard < 220,
				"every TextureManager Iris PBR lifecycle call must be behind the Rust whole-frame guard");
			cursor = source.indexOf("PBRTextureManager.INSTANCE", cursor + 1);
		}
	}

	@Test
	void rustWholeFrameSpriteReloadUsesCopiedVanillaMipmapsWithoutPbrRuntimeHooks() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/SpriteContents.java"));
		int provider = source.indexOf("CustomMipmapGenerator.Provider provider");
		int pbrBranch = source.indexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()", provider - 160);
		assertTrue(provider >= 0 && pbrBranch >= 0 && pbrBranch < provider,
			"Rust semantic sprite reload must bypass Iris custom mipmap generators");
		int active = source.indexOf("public void sodium$setActive");
		int activeGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", active);
		assertTrue(active >= 0 && activeGuard > active,
			"Rust semantic sprite activation must not publish PBR sprite state");
	}

	@Test
	void rustWholeFrameLanguageReloadDoesNotQueryIrisPackTranslations() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/resources/language/ClientLanguage.java"));
		int lookup = source.indexOf("Iris.getCurrentPack().orElse(null)");
		int guard = source.lastIndexOf("RustGalVulkanWholeFrameMode.enabled()", lookup);
		assertTrue(lookup >= 0 && guard >= 0 && lookup - guard < 240,
			"Rust whole-frame language lookup must leave shader-pack translation resolution to copied resources");
		int builtin = source.indexOf("Iris.class.getResource");
		int builtinGuard = source.lastIndexOf("RustGalVulkanWholeFrameMode.enabled()", builtin);
		assertTrue(builtin >= 0 && builtinGuard >= 0 && builtin - builtinGuard < 240,
			"Rust whole-frame language reload must not load Iris built-in language resources");
	}

	@Test
	void rustWholeFrameBufferConstructionNeverExtendsFormatsFromIrisState() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/blaze3d/vertex/BufferBuilder.java"));
		int irisCheck = source.indexOf("Iris.isPackInUseQuick()");
		int rustGuard = source.lastIndexOf("RustGalVulkanWholeFrameMode.enabled()", irisCheck);
		assertTrue(irisCheck >= 0 && rustGuard >= 0 && irisCheck - rustGuard < 180,
			"Rust semantic buffer construction must bypass Iris vertex-format extension state");
	}

	@Test
	void disabledVulkanMaterialRouteCannotRetainJavaCustomGeometryCallbacks() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		int method = source.indexOf("void submitCustomGeometry");
		int backendGate = source.indexOf("isVulkanBackendSelected()", method);
		int callbackAppend = source.indexOf("customGeometrySubmits.add", method);
		assertTrue(method >= 0 && backendGate > method && callbackAppend > backendGate,
			"Vulkan custom geometry must be gated before Java callback retention");
		assertTrue(source.contains("boolean vulkanSelected"),
			"custom geometry admission must explicitly classify Vulkan selection");
		assertTrue(source.contains("if (vulkanSelected ||"),
			"all selected Vulkan routes must fail closed, including not-yet-admitted material routes");
		assertTrue(source.contains("RustGalVulkanWholeFrameMode.enabled()"),
			"active Rust presentation must fail closed even if a stale route is misclassified as Java-compatible");
	}

	@Test
	void wholeFrameCoverageAcceptsRecordedDirectTextureModelDecisions() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		int semanticTexture = source.indexOf("submitModelSemanticTexture");
		int decisionCheck = source.indexOf("hasCurrentFrameRustModelMeshDecision(model, textureIdentity)", semanticTexture);
		assertTrue(semanticTexture >= 0 && decisionCheck > semanticTexture,
			"coverage replay must accept copied direct-texture model decisions regardless of EntityRenderState subtype");
		String decisionPrefix = source.substring(Math.max(semanticTexture, decisionCheck - 240), decisionCheck);
		assertFalse(decisionPrefix.contains("state instanceof EntityRenderState"),
			"direct-texture coverage must not discard Unit or block-entity state decisions before Rust admission");
	}

	@Test
	void rustWholeFrameCustomGeometryCannotPresentAfterDroppingACallback() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		int routeBranch = source.indexOf("if (vulkanSelected ||");
		int throwSite = source.indexOf("Java custom geometry is unavailable on Vulkan", routeBranch);
		assertTrue(routeBranch >= 0 && throwSite > routeBranch,
			"Vulkan custom geometry must abort rather than present incomplete geometry");
		assertTrue(source.contains("recordUnsupportedCustomGeometry()"),
			"the rejected callback must remain diagnostically visible");
	}

	@Test
	void rustWholeFrameParticlesCannotPresentAfterDroppingAnUnsupportedGroup() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/ParticleFeatureRenderer.java"));
		String levelRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		int unsupported = source.indexOf("recordUnsupportedParticleGroup()");
		int throwSite = source.indexOf("Rust whole-frame particle route encountered unsupported particle-group semantics", unsupported);
		assertTrue(unsupported >= 0 && throwSite > unsupported,
			"unsupported Rust particle groups must abort rather than silently omit particles");
		assertTrue(source.contains("rust-vulkan-unavailable"),
			"the unavailable particle route must remain diagnostically visible");
		assertTrue(levelRenderer.contains("Rust whole-frame material route is unavailable while Rust owns presentation"),
			"disabled material routes must not reopen Java particle rendering after Rust presentation begins");
	}

	@Test
	void rustWholeFrameFeatureDispatchSkipsJavaModelAndCustomRenderers() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		int modelCall = source.indexOf("modelFeatureRenderer.render");
		int modelGate = source.lastIndexOf("currentMaterialRoute().usesJavaCompatibility()", modelCall);
		int customCall = source.indexOf("customFeatureRenderer.render");
		int customGate = source.lastIndexOf("currentMaterialRoute().usesJavaCompatibility()", customCall);
		assertTrue(modelGate >= 0 && modelCall > modelGate && customGate >= 0 && customCall > customGate,
			"Java model/custom feature draws must be explicitly gated away from Rust whole-frame ownership");
		assertTrue(source.contains("currentMaterialRoute().usesJavaCompatibility()"),
			"disabled Vulkan material routes must not fall through to Java feature draws");
		String block = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/BlockFeatureRenderer.java"));
		assertTrue(block.contains("boolean semanticOnly")
			&& block.contains("Java block-feature rendering is unavailable while Rust owns whole-frame presentation")
			&& block.contains("Rust semantic block-feature collection requires complete Rust ownership for every block-feature family")
			&& source.contains("this.blockFeatureRenderer.render(\n\t\t\t\tsubmitNodeCollection, this.bufferSource, this.blockRenderDispatcher, this.outlineBufferSource, true"),
			"Rust block-feature collection must use the explicit semantic-only entrypoint and never Java-render directly");
	}

	@Test
	void disabledVulkanMaterialRouteCannotFallThroughToJavaBlockModelDraw() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/BlockFeatureRenderer.java"));
		String submit = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		String avatar = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/entity/player/AvatarRenderer.java"));
		String trident = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/special/TridentSpecialRenderer.java"));
		assertTrue(submit.contains("object == net.minecraft.util.Unit.INSTANCE")
			&& submit.contains("enqueueStandaloneTranslucentModelMesh")
			&& avatar.contains("submitModelSemanticTexture")
			&& trident.contains("enqueueStandaloneGlintModelMesh")
			&& submit.contains("armor_entity_glint"),
			"direct-texture model parts must use the Rust semantic model/glint contracts");
		int route = source.indexOf("WorldRenderRoutePolicy.currentMaterialRoute()", source.indexOf("BlockModelSubmit"));
		int gate = source.indexOf("!blockModelRoute.usesJavaCompatibility()", route);
		int javaDraw = source.indexOf("ModelBlockRenderer.renderModel", gate);
		assertTrue(route >= 0 && gate > route && javaDraw > gate,
			"disabled Vulkan block-model routes must be gated before Java model rendering");
	}

	@Test
	void rustWholeFrameBlockSubmissionsCannotPresentAfterDroppingSemanticGeometry() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/BlockFeatureRenderer.java"));
		String collectorSource = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java"));
		String worldSource = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		assertTrue(worldSource.contains("BlendFunction.TRANSLUCENT.equals(blend.get())")
			&& worldSource.contains("MATERIAL_ID_TRANSLUCENT_TEXTURED, MATERIAL_MODE_TRANSLUCENT"),
			"semantic moving/block-model admission must retain translucent material semantics");
		int displayUnavailable = source.indexOf("recordSubmittedWorkIdentity(\"block-display\", \"rust-vulkan-unavailable");
		int displayAbort = source.indexOf("Rust whole-frame block-display route has no semantic mesh", displayUnavailable);
		int modelUnavailable = source.indexOf("queued ? \"rust-vulkan-whole-frame\" : \"rust-vulkan-unavailable\"");
		int modelAbort = source.indexOf("Rust whole-frame block-model route has no semantic mesh", modelUnavailable);
		assertTrue(displayUnavailable >= 0 && displayAbort > displayUnavailable,
			"unsupported block-display geometry must abort rather than silently omit the submission");
		assertTrue(modelUnavailable >= 0 && modelAbort > modelUnavailable,
			"unsupported block-model geometry must abort rather than silently omit the submission");
		assertTrue(source.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& source.contains("Rust whole-frame block-model route is unavailable while Rust owns presentation"),
			"disabled block-model routes must not silently omit geometry after Rust presentation begins");
		assertTrue(source.contains("Rust whole-frame Primed TNT route is unavailable while Rust owns presentation"),
			"disabled Primed TNT routes must not silently omit geometry after Rust presentation begins");
		assertTrue(source.contains("Rust whole-frame block-display route is unavailable while Rust owns presentation"),
			"disabled block-display routes must not silently omit geometry after Rust presentation begins");
		assertTrue(source.contains("Rust whole-frame ")
			&& source.contains("is unavailable while Rust owns presentation"),
			"disabled moving-block routes must not silently omit geometry after Rust presentation begins");
		int collectorRoute = collectorSource.indexOf("enqueueBlockModelMesh(semanticSubmit)");
		int collectorAbort = collectorSource.indexOf("Rust whole-frame block-model route has no semantic mesh", collectorRoute);
		assertTrue(collectorRoute >= 0 && collectorAbort > collectorRoute,
			"collector block-model submissions must abort when semantic enqueue is rejected");
	}

	@Test
	void rustWholeFrameMovingAndItemRoutesCannotSilentlyDropSemanticSubmissions() throws Exception {
		String blockSource = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/BlockFeatureRenderer.java"));
		String itemSource = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/item/ItemStackRenderState.java"));
		assertTrue(blockSource.contains("Rust whole-frame moving-block route has no semantic source"));
		assertTrue(blockSource.contains("Rust whole-frame Primed TNT route has no semantic mesh"));
		assertTrue(itemSource.contains("Rust whole-frame TACZ item route produced no semantic mesh"));
		assertTrue(itemSource.contains("Rust whole-frame special-item route produced no semantic mesh"));
		assertTrue(itemSource.contains("Rust whole-frame item route has no semantic mesh"));
	}

	@Test
	void unknownMovingBlockCannotFallThroughToJavaVulkanTessellation() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/BlockFeatureRenderer.java"));
		int guard = source.indexOf("!fallingBlock && !piston");
		int tessellate = source.indexOf("tesselateBlock", guard);
		assertTrue(guard >= 0 && tessellate > guard,
			"unknown moving blocks must be rejected before Java tessellation on Vulkan");
	}

	@Test
	void rustWholeFrameDoesNotSilentlyDropVoxelMapThreeDWaypoints() throws Exception {
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		String voxelConstants = Files.readString(Path.of("src/main/java/net/voxelmap/VoxelConstants.java"));
		String waypointManager = Files.readString(Path.of("src/main/java/net/voxelmap/WaypointManager.java"));
		assertTrue(gameRenderer.contains("assertRustWholeFrameWaypointsSupported()"),
			"Rust whole-frame extraction must account for VoxelMap 3D waypoint ownership");
		assertTrue(voxelConstants.contains("submitRustWaypointSemantics"),
			"VoxelMap waypoint signs/icons/labels must enter the Rust semantic world-text route");
		assertTrue(waypointManager.contains("hasRenderableWaypoints()"),
			"waypoint activity must be derived from semantic waypoint state");
		assertTrue(voxelConstants.contains("enqueueVoxelMapBeaconSegments")
			&& voxelConstants.contains("showBeacons && !manager.options.showWaypoints"),
			"beacon-only VoxelMap overlays must use the explicit Rust line route when waypoint labels are hidden");
	}

	@Test
	void rustWholeFrameOwnsVanillaThreeDDebugCrosshairSemantics() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		int debugCheck = source.indexOf("DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR", source.indexOf("renderRustVulkanWholeFrameShell"));
		int enqueue = source.indexOf("enqueueThreeDimensionalDebugCrosshair", debugCheck);
		String primitive = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		assertTrue(debugCheck >= 0 && enqueue > debugCheck && primitive.contains("enqueueThreeDimensionalDebugCrosshair"),
			"Rust whole-frame must submit the 3D debug crosshair as explicit line semantics");
	}

	@Test
	void disabledVulkanMaterialRouteSkipsJavaHitboxAndItemFeatureRenderers() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		int hitbox = source.indexOf("hitboxFeatureRenderer.render");
		int hitboxGate = source.lastIndexOf("isVulkanBackendSelected()", hitbox);
		int item = source.indexOf("itemFeatureRenderer.render");
		int itemGate = source.lastIndexOf("isVulkanBackendSelected()", item);
		assertTrue(hitboxGate >= 0 && hitbox > hitboxGate && itemGate >= 0 && item > itemGate,
			"disabled Vulkan routes must not invoke Java hitbox or item feature draws");
	}

	@Test
	void rustBridgeIsOnlyRoutedFromSubsystemBenchmarkControls() throws Exception {
		String subsystem = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/GraphicsSubsystemBenchmark.java"));
		String bridge = Files.readString(Path.of("src/main/java/net/minecraft/client/dev/RustGraphicsSubsystemBenchmark.java"));

		assertTrue(subsystem.contains("rust-vulkan") && subsystem.contains("rust-opengl"));
		assertTrue(subsystem.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& subsystem.contains("RustGraphicsSubsystemBenchmark.run(minecraft, STATUS_PATH, ITERATIONS, \"rust-vulkan\")"),
			"whole-frame Vulkan must redirect the developer benchmark away from Java render passes");
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

		assertEquals(25, VulkanicGalBridge.ABI_VERSION);
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
		assertTrue(world.contains("fogRenderer.computeFogColorSemantic("),
			"Rust shader-environment fog extraction must use the Iris-free semantic color path");
		assertTrue(world.contains("shaderPackBiomePrecipitation(level, camera)"));
		assertTrue(world.contains("shaderPackBiomeResourceLocation(level, camera)"));
		assertTrue(world.contains("shaderPackHeldItemModelResourceLocation("));
		assertTrue(world.contains("shaderPackHeldItemLightEmission("));
		assertFalse(world.contains("IrisItemLightProvider"),
			"Rust held-item light semantics must use vanilla item data rather than an Iris API");
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
		assertTrue(guiRenderer.contains("!RustGalGuiRenderer.isWholeFrameVulkanActive()"));
		assertTrue(
			guiRenderer.indexOf("!RustGalGuiRenderer.isWholeFrameVulkanActive()")
				< guiRenderer.indexOf("this.prepareItemsViaPictureInPicture(i)"),
			"whole-frame GUI item collection must not prepare Java off-screen item/PIP renderers"
		);
		assertTrue(guiRenderer.contains("RustGalGuiRenderer.isWholeFrameVulkanActive()"));
		assertTrue(guiRenderer.contains("RustGalGuiRenderer.recordUnsupportedElement(\"text\")"));
		assertTrue(
			guiRenderer.indexOf("RustGalGuiRenderer.isWholeFrameVulkanActive()")
				< guiRenderer.indexOf("RustGalGuiRenderer.recordUnsupportedElement(\"text\")"),
			"whole-frame GUI text extraction misses must be admitted as unsupported semantics before any Java glyph state is submitted"
		);
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
	void rejectedGuiPictureInPictureFamiliesRemainExplicitlyDiagnosed() throws Exception {
		String guiRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));
		assertTrue(guiRenderer.contains("picture-in-picture:entity"));
		assertTrue(guiRenderer.contains("picture-in-picture:skin"));
		assertTrue(guiRenderer.contains("picture-in-picture:book"));
		assertTrue(guiRenderer.contains("picture-in-picture:sign"));
		assertTrue(guiRenderer.contains("picture-in-picture:banner"));
		assertTrue(guiRenderer.contains("picture-in-picture:oversized-item"));
	}

	@Test
	void taczFirstPersonUsesItsSemanticBedrockCollectorOnRustWholeFrame() throws Exception {
		String handRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java"));
		String taczRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"));
		assertTrue(taczRenderer.contains("submitSemanticBedrockRoots")
			&& taczRenderer.contains("submitTexturedQuads"),
			"TACZ must provide copied semantic Bedrock roots rather than a Java Vulkan draw");
		assertFalse(handRenderer.contains("These hand families have no Rust semantic collector yet"),
			"the first-person TACZ callsite must not discard an already implemented semantic collector");
		assertTrue(handRenderer.contains("renderTaczGlockFirstPerson"),
			"Rust whole-frame TACZ ownership must reach the semantic first-person producer");
		assertTrue(handRenderer.contains("if (itemStack.getItem() instanceof TaczMvpGunItem)"),
			"TACZ first-person ownership must invoke the semantic producer instead of recording an unavailable item");
	}

	@Test
	void taczItemEntitySpecialRendererUsesSemanticDispatchBeforeGenericRejection() throws Exception {
		String itemState = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/item/ItemStackRenderState.java"));
		assertTrue(itemState.contains("instanceof net.minecraft.client.renderer.special.TaczGlock17SpecialRenderer taczRenderer"),
			"TACZ item entities must reach their copied semantic producer");
		assertTrue(itemState.contains("taczRenderer.submit(") && itemState.contains("special-renderer"),
			"special item renderers must retain an explicit unsupported diagnostic boundary");
		assertTrue(itemState.contains("pendingIndexedItemMeshCount()")
			&& itemState.contains("special-renderer-semantic-receipt"),
			"vanilla special renderers must be admitted only after a copied Rust mesh receipt");
	}

	@Test
	void guiBlurTransportRetainsTheSemanticSourceStratum() throws Exception {
		String state = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/state/GuiRenderState.java"));
		String renderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		assertTrue(state.contains("blurBeforeStratumIndex()"));
		assertTrue(state.contains("return this.firstStratumAfterBlur == Integer.MAX_VALUE ? -1 : this.firstStratumAfterBlur;"));
		assertTrue(renderer.contains("blur boundary is semantic frame data"));
		assertFalse(renderer.contains("gui-blur-post-process"));
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
	void wholeFrameHudVignetteDoesNotQueryIrisPipelineState() throws Exception {
		String gui = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/Gui.java"));
		int method = gui.indexOf("private void renderVignette");
		int iris = gui.indexOf("Iris.getPipelineManager().getPipelineNullable()", method);
		assertTrue(method >= 0 && iris > method);
		String prefix = gui.substring(method, iris);
		assertTrue(prefix.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(prefix.contains("? null"));
	}

	@Test
	void legacySkyDrawMethodsFailClosedDuringRustWholeFrame() throws Exception {
		String sky = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SkyRenderer.java"));
		assertEquals(6, occurrences(sky, "ensureJavaSkyRenderingAvailable();"));
		assertTrue(sky.contains("Java sky rendering is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void legacyDebugDispatcherFailsClosedDuringRustWholeFrame() throws Exception {
		String debug = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/debug/DebugRenderer.java"));
		int method = debug.indexOf("public void render(");
		int minecraft = debug.indexOf("Minecraft minecraft", method);
		assertTrue(method >= 0 && minecraft > method);
		String body = debug.substring(method, minecraft);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("Java debug rendering is unavailable"));
	}

	@Test
	void gameTestBlockHighlightsCannotReopenJavaBuffersDuringRustWholeFrame() throws Exception {
		String highlights = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/debug/GameTestBlockHighlightRenderer.java"));
		int method = highlights.indexOf("public void render(");
		int clock = highlights.indexOf("long l = Util.getMillis();", method);
		assertTrue(method >= 0 && clock > method);
		String body = highlights.substring(method, clock);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("Java game-test block highlights are unavailable"));
	}

	@Test
	void wholeFrameResourceTextureUploadsStayOutOfJavaEncoders() throws Exception {
		String reloadable = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/ReloadableTexture.java"));
		String sprite = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/SpriteContents.java"));
		String cubeMap = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/CubeMapTexture.java"));
		assertTrue(reloadable.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(sprite.contains("net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()")
			&& sprite.contains("Rust consumes the copied atlas/resource-pack pixels"));
		assertTrue(cubeMap.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"));
	}

	@Test
	void wholeFrameFontAtlasBakingKeepsGlyphPixelsCpuOwned() throws Exception {
		String truetype = Files.readString(Path.of("src/main/java/net/blaze3d/font/TrueTypeGlyphProvider.java"));
		String bitmap = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/font/providers/BitmapProvider.java"));
		String unihex = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/font/providers/UnihexProvider.java"));
		String special = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/font/glyphs/SpecialGlyphs.java"));
		assertTrue(truetype.contains("&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(bitmap.contains("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())"));
		assertTrue(unihex.contains("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())"));
		assertTrue(special.contains("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())"));
		assertTrue(truetype.contains("copyTo(NativeImage"));
		assertTrue(bitmap.contains("copyTo(NativeImage"));
		assertTrue(unihex.contains("copyTo(NativeImage"));
		assertTrue(special.contains("copyTo(NativeImage"));
	}

	@Test
	void wholeFrameChunkUploadsDoNotCreateJavaSectionBuffers() throws Exception {
		String dispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/chunk/SectionRenderDispatcher.java"));
		int meshUpload = dispatcher.indexOf("public CompletableFuture<Void> upload(");
		int indexUpload = dispatcher.indexOf("public CompletableFuture<Void> uploadSectionIndexBuffer(");
		assertTrue(meshUpload >= 0 && indexUpload > meshUpload);
		String meshBody = dispatcher.substring(meshUpload, indexUpload);
		String indexBody = dispatcher.substring(indexUpload);
		assertTrue(meshBody.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(meshBody.contains("map.values().forEach(MeshData::close)"));
		assertTrue(indexBody.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(indexBody.contains("result.close()"));
	}

	@Test
	void wholeFramePanoramaDoesNotAllocateJavaProjectionUbo() throws Exception {
		String cubeMap = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/CubeMap.java"));
		int constructor = cubeMap.indexOf("public CubeMap(ResourceLocation resourceLocation)");
		int textureRegistration = cubeMap.indexOf("public void registerTextures", constructor);
		assertTrue(constructor >= 0 && textureRegistration > constructor);
		String body = cubeMap.substring(constructor, textureRegistration);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("? null"));
		assertTrue(cubeMap.contains("if (this.projectionMatrixUbo != null)"));
	}

	@Test
	void wholeFrameGuiRendererDoesNotAllocateJavaProjectionUbos() throws Exception {
		String renderer = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));
		int constructor = renderer.indexOf("public GuiRenderer(");
		int builder = renderer.indexOf("Builder<Class<? extends PictureInPictureRenderState>", constructor);
		assertTrue(constructor >= 0 && builder > constructor);
		String body = renderer.substring(constructor, builder);
		assertTrue(body.contains("RustGalGuiRenderer.isWholeFrameVulkanActive()"));
		assertTrue(body.contains("this.guiProjectionMatrixBuffer = null"));
		assertTrue(body.contains("this.itemsProjectionMatrixBuffer = null"));
		assertTrue(renderer.contains("if (this.guiProjectionMatrixBuffer != null)"));
		assertTrue(renderer.contains("if (this.itemsProjectionMatrixBuffer != null)"));
	}

	@Test
	void wholeFramePipRenderersDoNotAllocateJavaProjectionUbos() throws Exception {
		String pip = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/pip/PictureInPictureRenderer.java"));
		int constructor = pip.indexOf("protected PictureInPictureRenderer(");
		int prepare = pip.indexOf("public void prepare(", constructor);
		assertTrue(constructor >= 0 && prepare > constructor);
		String body = pip.substring(constructor, prepare);
		assertTrue(body.contains("RustGalGuiRenderer.isWholeFrameVulkanActive()"));
		assertTrue(body.contains("? null"));
		assertTrue(pip.contains("if (this.projectionMatrixBuffer != null)"));
		assertTrue(pip.contains("Java GUI picture-in-picture rendering is unavailable while Rust owns whole-frame presentation"),
			"Java PIP preparation must fail closed even if called outside the normal GUI dispatcher");
	}

	@Test
	void wholeFrameGameRendererDoesNotAllocateJavaHudProjectionUbo() throws Exception {
		String renderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		int constructor = renderer.indexOf("public GameRenderer(");
		int lightTexture = renderer.indexOf("this.lightTexture =", constructor);
		assertTrue(constructor >= 0 && lightTexture > constructor);
		String body = renderer.substring(constructor, lightTexture);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("? null"));
		assertTrue(renderer.contains("if (this.hud3dProjectionMatrixBuffer != null)"));
	}

	@Test
	void wholeFrameLightingDoesNotAllocateOrSubmitJavaUbo() throws Exception {
		String lighting = Files.readString(Path.of("src/main/java/net/blaze3d/platform/Lighting.java"));
		int constructor = lighting.indexOf("public Lighting()");
		int updateBuffer = lighting.indexOf("private void updateBuffer", constructor);
		assertTrue(constructor >= 0 && updateBuffer > constructor);
		String body = lighting.substring(constructor, updateBuffer);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("this.buffer = null"));
		assertTrue(lighting.contains("if (this.buffer == null)"));
		assertTrue(lighting.contains("if (this.buffer != null)"));
	}

	@Test
	void wholeFrameGameRendererDoesNotAllocateLegacyWorldHandOrSettingsUbos() throws Exception {
		String renderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		int constructor = renderer.indexOf("public GameRenderer(");
		int lightTexture = renderer.indexOf("this.lightTexture =", constructor);
		assertTrue(constructor >= 0 && lightTexture > constructor);
		String body = renderer.substring(constructor, lightTexture);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("this.globalSettingsUniform = null"));
		assertTrue(body.contains("this.levelProjectionMatrixBuffer = null"));
		assertTrue(body.contains("this.handProjectionMatrixBuffer = null"));
		assertTrue(renderer.contains("if (this.globalSettingsUniform != null)"));
		assertTrue(renderer.contains("if (this.levelProjectionMatrixBuffer != null)"));
		assertTrue(renderer.contains("if (this.handProjectionMatrixBuffer != null)"));
	}

	@Test
	void wholeFrameGameRendererDoesNotTouchIrisHardwareDiagnostics() throws Exception {
		String renderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		int diagnostics = renderer.indexOf("net.irisshaders.iris.Iris.logger.info(\"Hardware information:\")");
		int screenEffects = renderer.indexOf("this.screenEffectRenderer =", diagnostics);
		assertTrue(diagnostics >= 0 && screenEffects > diagnostics);
		int guard = renderer.lastIndexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", diagnostics);
		String body = renderer.substring(guard, screenEffects);
		assertTrue(body.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"));
	}

	@Test
	void wholeFrameSkyDoesNotAllocateLegacyVertexBuffers() throws Exception {
		String sky = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/SkyRenderer.java"));
		int constructor = sky.indexOf("public SkyRenderer()");
		int buildStars = sky.indexOf("this.starBuffer = this.buildStars()", constructor);
		assertTrue(constructor >= 0 && buildStars > constructor);
		String body = sky.substring(constructor, buildStars);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("this.starBuffer = null"));
		assertTrue(body.contains("this.endFlashBuffer = null"));
		assertTrue(sky.contains("if (this.sunBuffer != null)"));
	}

	@Test
	void wholeFrameFogDoesNotAllocateOrPublishJavaUbos() throws Exception {
		String fog = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/fog/FogRenderer.java"));
		int constructor = fog.indexOf("public FogRenderer()");
		int close = fog.indexOf("public void close()", constructor);
		assertTrue(constructor >= 0 && close > constructor);
		String body = fog.substring(constructor, close);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("this.regularBuffer = null"));
		assertTrue(body.contains("this.emptyBuffer = null"));
		assertTrue(fog.contains("Java fog UBO rendering is unavailable"));
		assertTrue(fog.contains("public Vector4f setupFog(")
			&& fog.substring(fog.indexOf("public Vector4f setupFog("))
				.contains("RustGalVulkanWholeFrameMode.enabled()"),
			"the Java fog setup entrypoint must fail closed before touching the absent UBO");
	}

	@Test
	void wholeFrameCloudsDoNotAllocateLegacyUbo() throws Exception {
		String clouds = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/CloudRenderer.java"));
		int constructor = clouds.indexOf("public CloudRenderer()");
		int prepare = clouds.indexOf("protected Optional<CloudRenderer.TextureData> prepare", constructor);
		assertTrue(constructor >= 0 && prepare > constructor);
		String body = clouds.substring(constructor, prepare);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("? null"));
		assertTrue(clouds.contains("if (this.ubo != null)"));
	}

	@Test
	void wholeFrameWorldBorderDoesNotAllocateLegacyVertexBuffer() throws Exception {
		String border = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/WorldBorderRenderer.java"));
		int constructor = border.indexOf("public WorldBorderRenderer()");
		int render = border.indexOf("public void render(", constructor);
		assertTrue(constructor >= 0 && render > constructor);
		String body = border.substring(constructor, render);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("? null"));
		assertTrue(border.contains("Java world-border rendering is unavailable"));
	}

	@Test
	void wholeFrameLightTextureKeepsOnlySemanticInputsAndNoJavaUbo() throws Exception {
		String light = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LightTexture.java"));
		int constructor = light.indexOf("public LightTexture(");
		int textureView = light.indexOf("public GpuTextureView getTextureView", constructor);
		assertTrue(constructor >= 0 && textureView > constructor);
		String body = light.substring(constructor, textureView);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("? null"));
		assertTrue(light.contains("if (this.ubo != null)"));
		assertTrue(light.contains("ensureRustSemanticLightmapInputs"));
		int darkness = light.indexOf("setDarknessLightFactor");
		int darknessGuard = light.lastIndexOf("RustGalVulkanWholeFrameMode.enabled()", darkness);
		assertTrue(darkness >= 0 && darknessGuard >= 0 && darknessGuard < darkness,
			"whole-frame lightmap calculation must not publish Iris darkness state");
	}

	@Test
	void wholeFramePostPassDoesNotAllocateJavaUniformBuffers() throws Exception {
		String post = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/PostPass.java"));
		int constructor = post.indexOf("public PostPass(");
		int uniforms = post.indexOf("for (Entry<String, List<UniformValue>> entry", constructor);
		assertTrue(constructor >= 0 && uniforms > constructor);
		String body = post.substring(constructor, uniforms);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("this.infoUbo = null"));
		assertTrue(post.contains("Java post-pass rendering is unavailable"));
	}

	@Test
	void wholeFrameEndFrameDoesNotRotateJavaCloudOrSodiumBuffers() throws Exception {
		String level = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/LevelRenderer.java"));
		String clouds = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/CloudRenderer.java"));
		int levelMethod = level.indexOf("public void endFrame()");
		int cloudCall = level.indexOf("this.cloudRenderer.endFrame()", levelMethod);
		assertTrue(levelMethod >= 0 && cloudCall > levelMethod);
		assertTrue(level.substring(levelMethod, cloudCall).contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(clouds.contains("RustGalVulkanWholeFrameMode.enabled()"));
	}

	@Test
	void wholeFrameRenderTargetCompatibilityHelpersFailClosed() throws Exception {
		String target = Files.readString(Path.of("src/main/java/net/blaze3d/pipeline/RenderTarget.java"));
		assertTrue(target.contains("rejectRustWholeFrameOperation(\"depth-copy\")"));
		assertTrue(target.contains("rejectRustWholeFrameOperation(\"render-target blend\")"));
		assertTrue(target.contains("Java RenderTarget " ));
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
		String modelFeatureRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/ModelFeatureRenderer.java"));
		String itemFeatureRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/ItemFeatureRenderer.java"));
		String textFeatureRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/TextFeatureRenderer.java"));
		String worldPrimitiveRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java"));
		assertTrue(worldPrimitiveRenderer.contains("MAX_WORLD_MESH_TEXTURE_PNG_BYTES = 4 * 1024 * 1024")
			&& worldPrimitiveRenderer.contains("MAX_WORLD_BORDER_ASSET_BYTES = 2 * 1024 * 1024")
			&& worldPrimitiveRenderer.contains("MAX_WORLD_AUXILIARY_ASSET_BYTES = 4 * 1024 * 1024")
			&& worldPrimitiveRenderer.contains("readBoundedResourceBytes(input")
			&& worldPrimitiveRenderer.contains("semantic sky texture")
			&& worldPrimitiveRenderer.contains("exceeds the \" + maximumBytes + \" byte bound"),
			"world semantic resource copies must enforce the Rust-owned bounded payload contracts before FFI");
		String renderSystem = Files.readString(Path.of("src/main/java/net/blaze3d/systems/RenderSystem.java"));
		String renderStateShard = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/RenderStateShard.java"));
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
		int setLevelIrisDimension = minecraft.indexOf("net.irisshaders.iris.Iris.lastDimension =");
		int setLevelIrisGuard = minecraft.lastIndexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", setLevelIrisDimension);
		assertTrue(setLevelIrisDimension > 0 && setLevelIrisGuard >= 0 && setLevelIrisGuard < setLevelIrisDimension,
			"whole-frame level changes must not publish Java Iris dimension state");
		int pipelineDimension = minecraft.indexOf("net.irisshaders.iris.Iris.getPipelineManager().destroyPipeline()");
		int pipelineDimensionGuard = minecraft.lastIndexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", pipelineDimension);
		assertTrue(pipelineDimension > 0 && pipelineDimensionGuard >= 0 && pipelineDimensionGuard < pipelineDimension,
			"whole-frame level changes must not destroy or prepare Java Iris pipelines");
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
			"loading-overlay screen delegation must remain on the semantic GUI extraction path");
		String titleScreen = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/TitleScreen.java"));
		assertTrue(titleScreen.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled() && !iris$hasFirstInit"),
			"the admitted title route must not initialize Iris renderer runtime state");
		assertTrue(hud.contains("boolean legacyIrisDebugGroup = !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()")
			&& hud.contains("if (legacyIrisDebugGroup) {"),
			"whole-frame HUD extraction must not enter Iris GL debug state");
		assertTrue(hud.contains("VoxelMap") && hud.contains("semantic producer owns fail-closed admission"),
			"the Rust Vulkan HUD path must stay semantic and fail closed without reopening Java GPU rendering");
		assertTrue(gameRenderer.contains("renderWithTooltipAndSubtitles(guiGraphics"));
		assertFalse(gameRenderer.contains("instanceof net.minecraft.client.gui.screens.TitleScreen"),
			"the Rust whole-frame shell must not restrict semantic extraction to the title screen");
		assertFalse(gameRenderer.contains("instanceof net.minecraft.client.gui.screens.LevelLoadingScreen"),
			"world-load progress must use the broad semantic GUI extraction path rather than a screen-specific Java renderer");
		String loadingOverlay = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/LoadingOverlay.java"));
		assertTrue(loadingOverlay.contains("RustGalFrameCoordinator rejects any element family")
			&& !loadingOverlay.contains("instanceof LevelLoadingScreen"),
			"loading overlay must use broad semantic screen extraction rather than a Java screen whitelist");
		assertTrue(cubeMap.contains("RustGalPanoramaRenderer.enqueue")
			&& cubeMap.contains("Java cube-map rendering is not a fallback"),
			"direct CubeMap calls must use copied Rust panorama semantics and fail closed without Java rendering");
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
		assertTrue(guiGraphics.contains("ResourceLocation mapTexture = mapRenderState.texture")
			&& guiGraphics.contains("atlasTexture = textureAtlasSprite.atlasLocation()")
			&& guiGraphics.contains("decoration.atlasSprite"),
			"map GUI must use copied semantic blits for the map texture and decorations rather than Java texture views");
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
		assertTrue(modelFeatureRenderer.contains("Java model feature rendering is unavailable while Rust owns whole-frame presentation")
			&& itemFeatureRenderer.contains("Java item feature rendering is unavailable while Rust owns whole-frame presentation")
			&& textFeatureRenderer.contains("Java text feature rendering is unavailable while Rust owns whole-frame presentation"),
			"Java model, item, and text feature renderers must fail closed after Rust presentation ownership transfers");
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
		assertTrue(shellBlockDisplays.contains("entityShadows")
			&& shellBlockDisplays.contains("entityFlames")
			&& shellBlockDisplays.contains("entityLeashes")
			&& shellBlockDisplays.contains("debugLines")
			&& shellBlockDisplays.contains("|| entityShadows || entityFlames || entityLeashes || debugLines"),
			"indexed semantic extraction must not early-return before dispatcher-owned shadow, flame, leash, or debug-line routes");
		assertTrue(shellBlockDisplays.contains("blockEntitySemanticFamilies")
			&& shellBlockDisplays.contains("currentBeaconBeamRoute()")
			&& shellBlockDisplays.contains("currentGuardianBeamRoute()")
			&& shellBlockDisplays.contains("currentCrystalBeamRoute()"),
			"block-entity semantic traversal must remain live for standalone beam and billboard routes");
		assertTrue(levelRenderer.contains("PistonHeadRenderState"));
		assertTrue(levelRenderer.contains("ZombieVillagerModel")
			&& levelRenderer.contains("SheepModel")
			&& levelRenderer.contains("textures/entity/sheep/sheep.png"),
			"selected-source coverage must recognize Rust-owned zombie-villager and sheep body meshes");
		assertTrue(levelRenderer.contains("BeaconRenderState")
			&& levelRenderer.contains("EndPortalRenderState")
			&& levelRenderer.contains("CondiutRenderState")
			&& levelRenderer.contains("SpawnerRenderState")
			&& levelRenderer.contains("BlockEntityWithBoundingBoxRenderState")
			&& levelRenderer.contains("TestInstanceRenderState")
			&& levelRenderer.contains("CampfireRenderState")
			&& levelRenderer.contains("BrushableBlockRenderState")
			&& levelRenderer.contains("ShelfRenderState")
			&& levelRenderer.contains("VaultRenderState"),
			"Rust whole-frame block-entity replay must admit semantic geometry and indexed-item producers");
		String blockEntityDispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher.java"));
		assertTrue(blockEntityDispatcher.contains("beginBlockEntityItemSubmission()")
			&& blockEntityDispatcher.contains("endBlockEntityItemSubmission()")
			&& blockEntityDispatcher.contains("!submitNodeCollector.isSemanticCoverageOnly()"),
			"block-entity item scopes must be explicit, balanced, and absent from coverage-only traversal");
		assertTrue(worldPrimitiveRenderer.contains("BLOCK_ENTITY_ITEM")
			&& worldPrimitiveRenderer.contains("isBlockEntityItemSubmissionActive()"),
			"block-entity item meshes must retain a distinct semantic producer identity");
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
		assertTrue(shell.contains("RustGalFrameCoordinator.isRustShaderPackSourceReady()"),
			"whole-frame camera semantics must use the staged Rust-owned shader-pack source readiness signal");
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
		assertEquals(2, occurrences(vulkanicApi, "Java Vulkan texture-unit binding is unavailable; Rust owns the selected Vulkan route"),
			"both Java Vulkan texture binding overloads must fail closed for the selected Rust route");
		assertTrue(renderStateShard.contains("Java Vulkan render-state setup is unavailable while Rust owns whole-frame presentation")
			&& renderStateShard.contains("Java Vulkan render-state cleanup is unavailable while Rust owns whole-frame presentation"),
			"Java RenderStateShard compatibility callbacks must fail closed after Rust presentation ownership transfers");
		assertTrue(vulkanicApi.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& !vulkanicApi.contains("shellCompatibilityBackend()"),
			"the Java OpenGL bootstrap backend must not remain reachable after Rust owns Vulkan selection");
		assertTrue(vulkanicApi.contains("The whole-frame route uses only semantic CPU buffers here"),
			"Rust presentation must not keep Java's dynamic-uniform GL ring active");
		assertFalse(vulkanicApi.contains("return getDevice().createCommandEncoder();"),
			"whole-frame command-encoder creation must fail closed instead of using a Java compatibility device");
		assertTrue(vulkanicApi.contains("Java Vulkan backend method '"),
			"the Java Vulkan proxy must become non-rendering once Rust owns presentation");
		assertTrue(vulkanicApi.contains("isRustWholeFrameBootstrapMethod")
			&& vulkanicApi.contains("prepareRendererBootstrapWindow")
			&& vulkanicApi.contains("createRendererDevice"),
			"only backend identity and explicit bootstrap may remain observable after the Rust handoff");
		String compatibilityDevice = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanCompatibilityGpuDevice.java"));
		assertTrue(compatibilityDevice.contains("Java OpenGL compatibility device ")
			&& compatibilityDevice.contains("cannot execute rendering work. Port this callsite to explicit VulkanicGAL semantics."),
			"a live Rust presenter must reject, not execute, Java OpenGL compatibility rendering");
		assertTrue(compatibilityDevice.contains("withCompatibilityBackendForTeardown"),
			"the only post-handoff compatibility action must be bounded bootstrap teardown");
		String nativeEncoder = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanNativeCommandEncoder.java"));
		assertEquals(21, occurrences(nativeEncoder, "ensureJavaVulkanRenderingAvailable();"),
			"every Java Vulkan render-pass, transfer, mapping, fence, and direct-clear entry point must be fenced after Rust presentation takes ownership");
		assertTrue(nativeEncoder.contains("public void writeToBuffer(GpuBufferSlice slice, ByteBuffer data) {\n        this.ensureJavaVulkanRenderingAvailable();")
			&& nativeEncoder.contains("public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice target) {\n        this.ensureJavaVulkanRenderingAvailable();")
			&& nativeEncoder.contains("public GpuFence createFence() {\n        this.ensureJavaVulkanRenderingAvailable();"),
			"stale Java Vulkan buffer transfers and fences must fail before touching native state");
		assertTrue(nativeEncoder.contains("private void checkOpen() {\n            VulkanNativeCommandEncoder.this.ensureJavaVulkanRenderingAvailable();"),
			"stale Java Vulkan render-pass objects must fail before issuing resource or draw commands");
		assertTrue(nativeEncoder.contains("MAX_RENDER_PASS_SAMPLERS = 128")
			&& nativeEncoder.contains("MAX_RENDER_PASS_UNIFORMS = 256")
			&& nativeEncoder.contains("MAX_RENDER_PASS_IRIS_PROGRAM_STATES = 128")
			&& nativeEncoder.contains("ensureBindingCapacity"),
			"transitional Java Vulkan render passes must keep sampler, uniform, and Iris-state resources bounded");
		assertTrue(nativeEncoder.contains("Java Vulkan render passes are unavailable while Rust owns whole-frame presentation"),
			"Java Vulkan render passes must fail closed instead of becoming a hidden fallback");
		assertTrue(vulkanBackend.contains("Rust Vulkan route selected; skipping Java Vulkan and Iris GPU renderer startup."),
			"the selected Rust Vulkan route must not initialize Iris GPU state");
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
		String worldTextCollector = Files.readString(Path.of("src/main/java/net/vulkanic/world/WorldTextSemanticCollector.java"));
		String playerGlyphProvider = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/font/PlayerGlyphProvider.java"));
		String skinDownloader = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/SkinTextureDownloader.java"));
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
		assertTrue(fontTexture.contains("? this.textureView")
			&& fontTexture.indexOf("VulkanicAPI.createTexture") > fontTexture.indexOf("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"),
			"whole-frame font stitching must retain semantic metadata without allocating or tracking a Java atlas texture");
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
			&& rawImages.contains("DYNAMIC_TEXTURES")
			&& rawImages.contains("EARLY_VANILLA_CACHE.clear()")
			&& rawImages.contains("source.getPath().startsWith(\"textures/atlas/\")")
			&& rawImages.contains("resolveAtlas(source)"),
			"whole-frame dynamic images must copy bounded CPU pixels into the existing VulkanicGAL raw-image queue");
		assertTrue(rawImages.contains("SemanticRawImageSnapshot")
			&& rawImages.contains("public static SemanticRawImageSnapshot semanticSnapshot")
			&& worldTextCollector.contains("semanticRawImageSnapshot(glyph.atlasIdentity())"),
			"world text must consume dynamic skin images through the bounded semantic raw-image contract");
		assertTrue(playerGlyphProvider.contains("collectSemanticQuads")
			&& playerGlyphProvider.contains("textureIdentity")
			&& skinDownloader.contains("RustGalGuiRawImageAssets.registerDynamicTexture(texture.texturePath(), dynamicTexture)"),
			"player-skin glyphs must publish semantic quads and a copied CPU skin identity instead of remaining Java-only text");
		assertTrue(bridge.contains("MAX_RAW_IMAGE_BYTES = 64 * 1024 * 1024")
			&& rustFfi.contains("FFI_MAX_GUI_ASSET_BYTES: usize = 64 * 1024 * 1024"),
			"Java and Rust raw-image boundaries must share the explicit 64 MiB semantic-image limit");
		assertTrue(rustGuiFrontend.contains("if batches.is_empty()"));
		String rustGuiRenderer = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		assertTrue(rustGuiRenderer.contains("MAX_GUI_ASSET_BYTES = 64 * 1024 * 1024")
			&& rustGuiRenderer.contains("input.readNBytes(MAX_GUI_ASSET_BYTES + 1)")
			&& rustGuiRenderer.contains("GUI asset exceeds the \" + MAX_GUI_ASSET_BYTES + \" byte bound"),
			"whole-frame GUI asset copies must enforce the Rust-owned bounded payload contract before FFI");
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
		assertTrue(benchmark.contains("scenarioRequiresProducerTraversal(PRIMED_TNT_SCENARIO)")
			&& benchmark.contains("routeObservedForProvenance(\"primed-tnt\")")
			&& benchmark.contains("scenarioRequiresProducerTraversal(ITEM_ENTITY_SCENARIO)")
			&& benchmark.contains("experienceOrbRouteDecisions()"),
			"shared gameplay readiness must require semantic traversal receipts for moving/entity producer families");
		int skyTextureInit = skyRenderer.indexOf("this.endSkyTexture = this.getTexture(END_SKY_LOCATION);");
		int skyWholeFrameGuard = skyRenderer.lastIndexOf("if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", skyTextureInit);
		assertTrue(skyTextureInit > 0 && skyWholeFrameGuard >= 0 && skyWholeFrameGuard < skyTextureInit
			&& skyRenderer.contains("copies its celestial source assets"),
			"whole-frame sky reload must use the Rust-owned copied celestial assets instead of Java texture uploads");
		assertTrue(worldPrimitiveRenderer.contains("Mth.sin(state.sunAngle) < 0.0F")
			&& worldPrimitiveRenderer.contains("enqueueVanillaDarkDiscLocked(camera)"),
			"Rust sky semantics must preserve vanilla sunrise orientation and below-horizon dark-disc coverage");
		assertTrue(worldPrimitiveRenderer.contains("Rust Vulkan whole-frame sky requires copied semantic sun and moon texture assets")
			&& worldPrimitiveRenderer.contains("Rust Vulkan whole-frame End sky requires a copied semantic sky texture asset"),
			"Rust sky admission must fail closed when copied celestial assets are unavailable");
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

	@Test
	void wholeFrameClientLevelShadingDoesNotReadIrisRuntimeState() throws Exception {
		String clientLevel = Files.readString(Path.of("src/main/java/net/minecraft/client/multiplayer/ClientLevel.java"));
		int irisRead = clientLevel.indexOf("WorldRenderingSettings.INSTANCE.shouldDisableDirectionalShading()");
		int guard = clientLevel.lastIndexOf("!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()", irisRead);
		assertTrue(irisRead >= 0 && guard >= 0 && guard < irisRead,
			"semantic terrain shading must not read Iris material settings after Vulkan selection");
	}

	@Test
	void wholeFrameTerrainBuildsSkipJavaGpuUploads() throws Exception {
		String renderSections = Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/RenderSectionManager.java"));
		int upload = renderSections.indexOf("this.regions.uploadResults(");
		int uploadGuard = renderSections.lastIndexOf("if (!rustWholeFrame)", upload);
		assertTrue(upload >= 0 && uploadGuard >= 0 && uploadGuard < upload,
			"Rust whole-frame terrain must not upload chunk meshes through Java RenderDevice");
		assertTrue(renderSections.contains("RustGalTerrainRenderer.acceptWholeFrameChunkBuildOutput(chunkBuildOutput)"),
			"Rust whole-frame terrain must admit compact CPU mesh semantics explicitly");
	}

	@Test
	void wholeFrameCannotEnterJavaBufferSourceRendering() throws Exception {
		String buffers = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/MultiBufferSource.java"));
		assertTrue(buffers.contains("Java Vulkan buffer-source rendering is unavailable while Rust owns whole-frame presentation"),
			"Java buffer-source acquisition must fail closed after Rust presentation ownership transfers");
	}

	@Test
	void wholeFrameSodiumOptionsDoNotProbeJavaRenderDevice() throws Exception {
		String options = Files.readString(Path.of("src/main/java/net/sodium/client/gui/SodiumGameOptionPages.java"));
		int probe = options.indexOf("MappedStagingBuffer.isSupported(RenderDevice.instance())");
		int guard = options.lastIndexOf("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()", probe);
		assertTrue(probe >= 0 && guard >= 0 && guard < probe,
			"Rust whole-frame menu setup must not probe Java RenderDevice capabilities");
	}

	@Test
	void wholeFrameSodiumWorldRendererCannotCreateJavaGraph() throws Exception {
		String sodium = Files.readString(Path.of("src/main/java/net/sodium/client/render/SodiumWorldRenderer.java"));
		int setLevel = sodium.indexOf("public void setLevel(ClientLevel level)");
		int setLevelGuard = sodium.indexOf("RustGalVulkanWholeFrameMode.enabled()", setLevel);
		int reload = sodium.indexOf("public void reload()");
		int reloadGuard = sodium.indexOf("RustGalVulkanWholeFrameMode.enabled()", reload);
		assertTrue(setLevel >= 0 && setLevelGuard > setLevel && reload >= 0 && reloadGuard > reload,
			"Rust whole-frame terrain must not construct or reload Sodium's Java GPU graph");
	}

	@Test
	void wholeFrameSodiumWorldRendererCannotDrawJavaTerrain() throws Exception {
		String sodium = Files.readString(Path.of("src/main/java/net/sodium/client/render/SodiumWorldRenderer.java"));
		int draw = sodium.indexOf("public void drawChunkLayer(");
		int guard = sodium.indexOf("RustGalVulkanWholeFrameMode.enabled()", draw);
		int drawCall = sodium.indexOf("renderSectionManager.renderLayer", draw);
		assertTrue(draw >= 0 && guard > draw && drawCall > guard,
			"Rust whole-frame terrain must fail closed before Sodium can issue Java draw passes");
	}

	@Test
	void wholeFrameSodiumSectionManagerCannotOpenJavaTerrainPass() throws Exception {
		String sections = Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/RenderSectionManager.java"));
		int render = sections.indexOf("public void renderLayer(");
		int guard = sections.indexOf("RustGalVulkanWholeFrameMode.enabled()", render);
		int commandList = sections.indexOf("createCommandList()", render);
		assertTrue(render >= 0 && guard > render && commandList > guard,
			"Rust whole-frame terrain must reject direct Sodium section-manager draws before opening a Java command list");
	}

	@Test
	void wholeFrameGameRendererCannotReenterLegacyLevelRendering() throws Exception {
		String gameRenderer = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GameRenderer.java"));
		int renderLevel = gameRenderer.indexOf("public void renderLevel(DeltaTracker deltaTracker)");
		int guard = gameRenderer.indexOf("RustGalVulkanWholeFrameMode.enabled()", renderLevel);
		int legacyLevelCall = gameRenderer.indexOf("levelRenderer\n\t\t\t.renderLevel", renderLevel);
		assertTrue(renderLevel >= 0 && guard > renderLevel && legacyLevelCall > guard,
			"Rust whole-frame mode must reject direct legacy GameRenderer level rendering before Java GPU setup");
		int constructor = gameRenderer.indexOf("public GameRenderer(");
		int firstProjectionBuffer = gameRenderer.indexOf("new PerspectiveProjectionMatrixBuffer", constructor);
		int selectedConstructorGuard = gameRenderer.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
		assertTrue(constructor >= 0 && selectedConstructorGuard > constructor && firstProjectionBuffer > selectedConstructorGuard,
			"selected Vulkan must avoid allocating Java projection UBOs during GameRenderer construction");
	}

	@Test
	void wholeFrameFeatureDispatcherCannotReopenJavaSubmissions() throws Exception {
		String dispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		int all = dispatcher.indexOf("public void renderAllFeatures()");
		int allGuard = dispatcher.indexOf("RustGalVulkanWholeFrameMode.enabled()", all);
		int allLoop = dispatcher.indexOf("getSubmitsPerOrder()", all);
		int blocks = dispatcher.indexOf("public void renderBlockFeaturesOnly()");
		int blockLoop = dispatcher.indexOf("getSubmitsPerOrder()", blocks);
		assertTrue(all >= 0 && allGuard > all && allLoop > allGuard
			&& blocks >= 0 && blockLoop > blocks
			&& dispatcher.indexOf("WorldRenderRoutePolicy.currentEntityShadowRoute()", blocks) > blockLoop,
			"Rust whole-frame feature dispatch must consume routed semantic features instead of aborting before submission");
	}

	@Test
	void wholeFrameFeatureDispatcherDoesNotRotateJavaParticleBuffers() throws Exception {
		String dispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		int endFrame = dispatcher.indexOf("public void endFrame()");
		int guard = dispatcher.indexOf("RustGalVulkanWholeFrameMode.enabled()", endFrame);
		int rotate = dispatcher.indexOf("particleFeatureRenderer.endFrame()", endFrame);
		assertTrue(endFrame >= 0 && guard > endFrame && rotate > guard,
			"Rust whole-frame cleanup must not rotate Java particle GPU buffers");
	}

	@Test
	void wholeFrameHandsCannotReopenJavaFirstPersonRenderer() throws Exception {
		String hands = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java"));
		int render = hands.indexOf("public void renderHandsWithItems(");
		int guard = hands.indexOf("RustGalVulkanWholeFrameMode.enabled()", render);
		int arm = hands.indexOf("renderArmWithItem", render);
		assertTrue(render >= 0 && guard > render && arm > guard,
			"Rust whole-frame first-person rendering must reject the Java hand entrypoint before arm submission");
	}

	@Test
	void wholeFrameParticleRendererDoesNotRotateJavaBuffersDirectly() throws Exception {
		String particles = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/ParticleFeatureRenderer.java"));
		int endFrame = particles.indexOf("public void endFrame()");
		int guard = particles.indexOf("RustGalVulkanWholeFrameMode.enabled()", endFrame);
		int rotate = particles.indexOf("particleBufferCache.rotate()", endFrame);
		assertTrue(endFrame >= 0 && guard > endFrame && rotate > guard,
			"Rust whole-frame particle lifecycle must reject direct Java buffer rotation");
	}

	@Test
	void wholeFrameHitboxCollectorCannotSilentlyDropDisabledRoute() throws Exception {
		String dispatcher = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java"));
		int collector = dispatcher.indexOf("public void collectRustHitboxSemantics(");
		int activeGuard = dispatcher.indexOf("RustGalVulkanWholeFrameMode.enabled()", collector);
		int unavailable = dispatcher.indexOf("Rust whole-frame debug-hitbox route is unavailable while Rust owns presentation", collector);
		assertTrue(collector >= 0 && activeGuard > collector && unavailable > activeGuard,
			"direct Rust hitbox collection must reject disabled work while Rust owns presentation");
	}

	@Test
	void activeRustPresentationCannotMisrouteParticlesToJava() throws Exception {
		String particles = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/feature/ParticleFeatureRenderer.java"));
		int render = particles.indexOf("public void render(SubmitNodeCollection");
		int activeGuard = particles.indexOf("RustGalVulkanWholeFrameMode.enabled()", render);
		int javaPass = particles.indexOf("createRenderPass", render);
		assertTrue(render >= 0 && activeGuard > render && javaPass > activeGuard,
			"Rust presentation ownership must reject a misclassified particle route before Java pass creation");
	}

	@Test
	void wholeFrameCannotUseJavaTracyCaptureOrUpload() throws Exception {
		String tracy = Files.readString(Path.of("src/main/java/net/blaze3d/TracyFrameCapture.java"));
		int capture = tracy.indexOf("public void capture(");
		int captureGuard = tracy.indexOf("RustGalVulkanWholeFrameMode.enabled()", capture);
		int capturePass = tracy.indexOf("createRenderPass", capture);
		int upload = tracy.indexOf("public void upload()");
		int uploadGuard = tracy.indexOf("RustGalVulkanWholeFrameMode.enabled()", upload);
		int uploadEncoder = tracy.indexOf("createCommandEncoder", upload);
		assertTrue(capture >= 0 && captureGuard > capture && capturePass > captureGuard
			&& upload >= 0 && uploadGuard > upload && uploadEncoder > uploadGuard,
			"Rust whole-frame diagnostics must not reopen Java capture passes or encoders");
	}

	@Test
	void wholeFrameChunkSectionDrawCannotOpenJavaTerrainPass() throws Exception {
		String sections = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/chunk/ChunkSectionsToRender.java"));
		assertTrue(sections.contains("Java chunk-section rendering is unavailable while Rust owns whole-frame presentation"),
			"vanilla chunk-section draw entry must fail closed after Rust presentation ownership transfers");
	}

	@Test
	void wholeFrameDistantHorizonsBuffersDoNotTouchIrisTracking() throws Exception {
		String buffers = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/buffer/GLBuffer.java"));
		String boxes = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/generic/RenderableBoxGroup.java"));
		assertTrue(buffers.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()")
			&& boxes.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"),
			"Distant Horizons compatibility buffer tracking must not mutate Iris state in Rust whole-frame mode");
	}

	@Test
	void selectedVulkanDistantHorizonsProxyDoesNotBorrowOpenGlCapabilities() throws Exception {
		String proxy = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/GLProxy.java"));
		assertTrue(proxy.contains("vulkanBackend ? null : VulkanicAPI.getGLCapabilities()"));
		assertTrue(proxy.contains("!vulkanBackend && VulkanicAPI.getNamedBufferDataPointer()"));
		assertTrue(proxy.contains("!vulkanBackend && VulkanicAPI.getBufferStoragePointer()"));
		assertTrue(proxy.contains("!vulkanBackend && VulkanicAPI.getBindVertexBufferPointer()"),
			"selected Rust Vulkan DH capability setup must not probe Java OpenGL function state");
		assertTrue(proxy.contains("Java Distant Horizons upload-task draining is unavailable while Rust owns whole-frame presentation"),
			"selected Rust Vulkan must not drain queued Java DH upload work");
	}

	@Test
	void wholeFrameGuiCannotAllocateJavaMappableRingBuffers() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/MappableRingBuffer.java"));
		int constructor = source.indexOf("public MappableRingBuffer(");
		int constructorGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
		int rotate = source.indexOf("public void rotate()");
		int rotateGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", rotate);
		assertTrue(constructor >= 0 && constructorGuard > constructor,
			"Rust whole-frame GUI must not allocate Java mappable ring buffers");
		assertTrue(rotate >= 0 && rotateGuard > rotate,
			"Rust whole-frame GUI must not submit Java ring-buffer fences");
	}

	@Test
	void wholeFrameDebugOverlayDoesNotAllocateJavaCrosshairBuffer() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/components/DebugScreenOverlay.java"));
		int constructor = source.indexOf("public DebugScreenOverlay(");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
		int nullBuffer = source.indexOf("this.crosshairBuffer = null", guard);
		int createBuffer = source.indexOf("VulkanicAPI.createBuffer", constructor);
		assertTrue(constructor >= 0 && guard > constructor && nullBuffer > guard,
			"whole-frame debug overlay must leave its Java crosshair buffer absent");
		assertTrue(createBuffer < 0 || createBuffer > nullBuffer,
			"whole-frame debug overlay must not allocate the Java crosshair buffer before its guard");
	}

	@Test
	void wholeFrameSodiumAndIrisCannotConstructCompatibilityBuffers() throws Exception {
		String sodium = Files.readString(Path.of("src/main/java/net/sodium/client/gl/buffer/GlBuffer.java"));
		String iris = Files.readString(Path.of("src/main/java/net/irisshaders/iris/gl/buffer/ShaderStorageBuffer.java"));
		String irisRenderSystem = Files.readString(Path.of("src/main/java/net/irisshaders/iris/gl/IrisRenderSystem.java"));
		assertTrue(sodium.contains("Java Sodium buffers are unavailable while Rust owns whole-frame presentation"));
		assertTrue(iris.contains("Java Iris shader-storage buffers are unavailable while Rust owns whole-frame presentation"),
			"compatibility buffer constructors must fail closed before Java GPU allocation");
		assertTrue(iris.contains("Java Iris shader-storage buffer resizing is unavailable while Rust owns whole-frame presentation"),
			"compatibility buffer resizing must not reopen Java GPU allocation after construction");
		assertTrue(irisRenderSystem.contains("public static void genBuffers(int[] buffers) {\n\t\trejectJavaGpuObjectCreation(\"buffer\");"),
			"legacy Iris buffer generation must fail closed before Java GPU allocation");
		assertTrue(irisRenderSystem.contains("public static int bufferStorage(int target, float[] data, int usage) {\n\t\tRenderSystem.assertOnRenderThread();\n\t\trejectJavaGpuObjectCreation(\"buffer\");"),
			"legacy Iris bufferStorage overload must fail closed before DSA allocation");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"texture upload\");"),
			"legacy Iris texture uploads must fail closed during Rust whole-frame rendering");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"buffer upload\");"),
			"legacy Iris buffer uploads must fail closed during Rust whole-frame rendering");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"texture parameter update\");"),
			"legacy Iris texture parameter updates must fail closed during Rust whole-frame rendering");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"mipmap generation\");"),
			"legacy Iris mipmap generation must fail closed during Rust whole-frame rendering");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"buffer binding\");"),
			"legacy Iris buffer bindings must fail closed during Rust whole-frame rendering");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"sampler binding\");"),
			"legacy Iris sampler bindings must fail closed during Rust whole-frame rendering");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"compute dispatch\");"),
			"legacy Iris compute dispatch must fail closed during Rust whole-frame rendering");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"buffer clear\");"),
			"legacy Iris buffer clears must fail closed during Rust whole-frame rendering");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"program binding\");"),
			"legacy Iris program binding must fail closed during Rust whole-frame rendering");
		assertTrue(irisRenderSystem.contains("rejectJavaGpuMutation(\"sampler parameter update\");"),
			"legacy Iris sampler parameter updates must fail closed during Rust whole-frame rendering");
		String uploadHelper = Files.readString(Path.of("src/main/java/net/irisshaders/iris/gl/texture/TextureUploadHelper.java"));
		assertTrue(uploadHelper.contains("Java Iris texture-upload state is unavailable while Rust owns whole-frame presentation"),
			"legacy Iris pixel-store setup must fail closed during Rust whole-frame rendering");
	}

	@Test
	void wholeFrameSharedTerrainAndUniformHelpersCannotAllocateJavaResources() throws Exception {
		String uniform = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/GlobalSettingsUniform.java"));
		String mesh = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/chunk/CompiledSectionMesh.java"));
		assertTrue(uniform.contains("Java global-settings UBO is unavailable while Rust owns whole-frame presentation"));
		assertTrue(mesh.contains("Java terrain mesh uploads are unavailable while Rust owns whole-frame presentation"));
		assertTrue(mesh.contains("Java terrain index uploads are unavailable while Rust owns whole-frame presentation"),
			"shared terrain upload helpers must fail closed before Java command encoders are created");
	}

	@Test
	void wholeFrameDistantHorizonsRenderHookSkipsJavaProxySetup() throws Exception {
		String clientApi = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/api/internal/ClientApi.java"));
		int method = clientApi.indexOf("private void renderLodLayer(boolean renderingDeferredLayer)");
		int guard = clientApi.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int proxy = clientApi.indexOf("GLProxy.getInstance()", method);
		assertTrue(method >= 0 && guard > method && proxy > guard,
			"Rust whole-frame DH hooks must skip Java GLProxy setup and upload-task draining");
	}

	@Test
	void wholeFrameDistantHorizonsFadeHooksCannotDrawJavaPasses() throws Exception {
		String clientApi = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/api/internal/ClientApi.java"));
		int opaque = clientApi.indexOf("public void renderFadeOpaque()");
		int transparent = clientApi.indexOf("public void renderFadeTransparent()");
		assertTrue((clientApi.indexOf("RustGalVulkanWholeFrameMode.enabled()", opaque) > opaque
			|| clientApi.indexOf("RustGalVulkanWholeFrameMode.enabled()", opaque) > opaque)
			&& (clientApi.indexOf("RustGalVulkanWholeFrameMode.enabled()", transparent) > transparent
			|| clientApi.indexOf("RustGalVulkanWholeFrameMode.enabled()", transparent) > transparent),
			"Rust whole-frame DH hooks must not execute Java fade renderers");
	}

	@Test
	void wholeFrameDistantHorizonsLevelHookDoesNotQueryIrisCompat() throws Exception {
		String hook = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/fabric/hooks/DistantHorizonsLevelRenderHook.java"));
		int overrideQuery = hook.indexOf("DHCompatInternal.shouldUseShaderOverrides()");
		int guard = hook.lastIndexOf("RustGalVulkanWholeFrameMode.enabled()", overrideQuery);
		assertTrue(overrideQuery >= 0 && guard >= 0 && guard < overrideQuery,
			"normal DH level hooks must not query Iris compatibility state in Rust whole-frame mode");
	}

	@Test
	void wholeFrameDistantHorizonsTextureStateIsCompatibilityOnly() throws Exception {
		String textureState = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/DhTextureState.java"));
		assertTrue(textureState.contains("Java DH texture state is unavailable while Rust owns whole-frame presentation"),
			"Distant Horizons texture-unit compatibility helpers must fail closed after Rust presentation ownership transfers");
	}

	@Test
	void wholeFrameDistantHorizonsCustomRenderersCannotSubmitJavaPasses() throws Exception {
		String generic = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java"));
		String screenQuad = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/ScreenQuad.java"));
		assertTrue(generic.contains("Java DH generic-object rendering is unavailable while Rust owns whole-frame presentation"),
			"DH generic-object entry points must fail closed under Rust whole-frame presentation");
		assertTrue(screenQuad.contains("Java DH screen-quad rendering is unavailable while Rust owns whole-frame presentation"),
			"DH screen-quad entry points must fail closed under Rust whole-frame presentation");
	}

	@Test
	void wholeFramePublicPresentersFailClosedAtTheirOwnEntryPoints() throws Exception {
		String gui = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java"));
		String clouds = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/CloudRenderer.java"));
		String composite = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pipeline/CompositeRenderer.java"));
		String finalPass = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pipeline/FinalPassRenderer.java"));
		String pipeline = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java"));
		assertTrue(gui.contains("Java GUI rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(clouds.contains("Java cloud rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(composite.contains("Java Iris composite rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(finalPass.contains("Java Iris final-pass rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(pipeline.contains("Java Iris shadow rendering is unavailable while Rust owns whole-frame presentation")
			&& pipeline.contains("Java Iris deferred rendering is unavailable while Rust owns whole-frame presentation")
			&& pipeline.contains("Java Iris composite/final rendering is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void endPortalGuiUsesTheCopiedRustMeshContract() throws Exception {
		String graphics = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/GuiGraphics.java"));
		String renderer = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		String loading = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/LevelLoadingScreen.java"));
		String win = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/WinScreen.java"));
		assertTrue(graphics.contains("tryEnqueueEndPortal") && graphics.contains("submitGuiElement(element)"),
			"End Portal GUI must enter Rust through copied semantic mesh elements");
		assertTrue(renderer.contains("Per-vertex UVs are retained")
			&& renderer.contains("END_SKY_LOCATION")
			&& renderer.contains("END_PORTAL_LOCATION")
			&& renderer.contains("enqueueGuiMeshItemRequest"),
			"Rust End Portal GUI admission must preserve both textures and rotated layer UVs");
		assertTrue(loading.contains("guiGraphics.submitRustEndPortal(gameTime)")
			&& win.contains("guiGraphics.submitRustEndPortal(gameTime)"),
			"loading and credits screens must not reopen the Java two-texture portal pass");
	}

	@Test
	void voxelMapGradientUsesRustOwnedSemanticMeshInterpolation() throws Exception {
		String voxelMap = Files.readString(Path.of("src/main/java/net/voxelmap/util/VoxelMapGuiGraphics.java"));
		String graphics = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/GuiGraphics.java"));
		String renderer = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java"));
		assertTrue(voxelMap.contains("submitRustSemanticGradientBlit"),
			"VoxelMap gradients must use the semantic GUI path under Rust Vulkan");
		assertTrue(graphics.contains("tryEnqueueGradientBlit") && renderer.contains("tryEnqueueGradientBlit")
			&& renderer.contains("GuiMeshVertexRecord"),
			"gradient colors must be carried as explicit Rust GUI mesh vertex semantics");
		assertTrue(voxelMap.contains("isWholeFrameVulkanActive())")
			&& voxelMap.contains("graphics.submitRustSemanticBlit(texture"),
			"resource-backed VoxelMap blits must not retain a Java texture-view path under Rust Vulkan");
	}

	@Test
	void wholeFrameIrisAuxiliaryResourcesCannotBeConstructedOrDrawn() throws Exception {
		String depth = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pathways/CenterDepthSampler.java"));
		String depthCopy = Files.readString(Path.of("src/main/java/net/irisshaders/iris/gl/texture/DepthCopyStrategy.java"));
		String color = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pathways/colorspace/ColorSpaceFragmentConverter.java"));
		String horizon = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pathways/HorizonRenderer.java"));
		assertTrue(depth.contains("Java Iris center-depth resources are unavailable while Rust owns whole-frame presentation"));
		assertTrue(depthCopy.contains("Java Iris depth snapshots are unavailable while Rust owns whole-frame presentation"),
			"Iris depth-copy compatibility strategies must fail closed before touching Java framebuffer state");
		assertTrue(color.contains("Java Iris color-space resources are unavailable while Rust owns whole-frame presentation"));
		assertTrue(horizon.contains("Java Iris horizon resources are unavailable while Rust owns whole-frame presentation")
			&& horizon.contains("Java Iris horizon rendering is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void wholeFrameDoesNotConstructJavaTracyCaptureResources() throws Exception {
		String minecraft = Files.readString(Path.of("src/main/java/net/minecraft/client/Minecraft.java"));
		int tracy = minecraft.indexOf("TracyCompat.isAvailable() && gameConfig.game.captureTracyImages");
		int guard = minecraft.indexOf("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()", tracy);
		assertTrue(tracy >= 0 && guard > tracy,
			"Rust whole-frame startup must not allocate Java Tracy GPU capture resources");
	}

	@Test
	void wholeFrameTaczImmediateMeshHelperCannotOpenJavaPass() throws Exception {
		String tacz = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"));
		assertTrue(tacz.contains("Java TACZ immediate mesh rendering is unavailable while Rust owns whole-frame presentation"),
			"TACZ's final Java immediate-mesh helper must fail closed under Rust whole-frame presentation");
		int immediateGuard = tacz.indexOf("Java TACZ immediate mesh rendering is unavailable");
		int irisTextureLookup = tacz.indexOf("IrisRenderSystem.getTextureBinding", immediateGuard);
		assertTrue(immediateGuard >= 0 && irisTextureLookup > immediateGuard,
			"TACZ must reject Java immediate rendering before consulting Iris texture-unit state");
	}

	@Test
	void wholeFrameRemainingJavaPresenterHelpersFailClosed() throws Exception {
		String debug = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/components/DebugScreenOverlay.java"));
		String post = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/PostPass.java"));
		String renderType = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/RenderType.java"));
		assertTrue(debug.contains("Java 3D crosshair rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(post.contains("Java post-pass rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(renderType.contains("Java immediate RenderType drawing is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void wholeFrameScreenshotReadbackCannotOpenJavaCommandEncoder() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/Screenshot.java"));
		int method = source.indexOf("public static void takeScreenshot(RenderTarget renderTarget, int i");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int createEncoder = source.indexOf("VulkanicAPI.createCommandEncoder()", method);
		assertTrue(method >= 0 && guard > method,
			"screenshot readback must fail closed before Java GPU work on whole-frame Vulkan");
		assertTrue(createEncoder < 0 || createEncoder > guard,
			"whole-frame screenshot readback must not open a Java command encoder");
	}

	@Test
	void wholeFrameSodiumRendererCannotSelectJavaVulkanImplementation() throws Exception {
		String sodium = Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/DefaultChunkRenderer.java"));
		int render = sodium.indexOf("public void render(ChunkRenderMatrices");
		int guard = sodium.indexOf("Java Sodium chunk rendering is unavailable while Rust owns whole-frame presentation", render);
		int backendBranch = sodium.indexOf("VulkanicAPI.isVulkanBackendSelected()", render);
		assertTrue(render >= 0 && guard > render && backendBranch > guard,
			"Sodium's Java Vulkan terrain branch must be fenced before backend selection");
	}

	@Test
	void wholeFrameSodiumEntityHookCannotReopenJavaShadowBuffers() throws Exception {
		String hook = Files.readString(Path.of("src/main/java/net/sodium/fabric/SodiumEntityRenderHook.java"));
		int method = hook.indexOf("onRenderEntityShadows(");
		int buffer = hook.indexOf("bufferSource.getBuffer", method);
		assertTrue(method >= 0 && buffer > method);
		String body = hook.substring(method, buffer);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("Java Sodium entity-shadow hook is unavailable"));
	}

	@Test
	void wholeFrameDistantHorizonsLayerHookCannotReopenJavaFadePasses() throws Exception {
		String hook = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/fabric/hooks/DistantHorizonsChunkRenderHook.java"));
		int method = hook.indexOf("onBeforeRenderLayer(");
		int state = hook.indexOf("ClientApi.RENDER_STATE", method);
		assertTrue(method >= 0 && state > method);
		String body = hook.substring(method, state);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("Java Distant Horizons layer hook is unavailable"));
	}

	@Test
	void wholeFrameDistantHorizonsCompatibilityPassCannotCreateJavaVulkanState() throws Exception {
		String lod = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/renderer/LodRenderer.java"));
		int method = lod.indexOf("private RenderPass createVulkanCompatibilityRenderPass(");
		int backend = lod.indexOf("VulkanicAPI.isVulkanBackendSelected()", method);
		assertTrue(method >= 0 && backend > method);
		String body = lod.substring(method, backend);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("Java Distant Horizons compatibility render pass is unavailable"));
	}

	@Test
	void wholeFrameRenderTargetCannotBecomeASecondPresenter() throws Exception {
		String target = Files.readString(Path.of("src/main/java/net/blaze3d/pipeline/RenderTarget.java"));
		int blit = target.indexOf("public void blitToScreen()");
		int guard = target.indexOf("Java RenderTarget presentation is unavailable while Rust owns whole-frame presentation", blit);
		assertTrue(blit >= 0 && guard > blit,
			"RenderTarget's legacy presentation entry must fail closed under Rust whole-frame ownership");
		assertTrue(target.contains("rejectRustWholeFrameOperation(\"Iris framebuffer binding\")"),
			"RenderTarget Iris framebuffer binding must fail closed under Rust whole-frame ownership");
		String api = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		assertTrue(api.contains("rejectJavaVulkanWholeFrameOperation(\"framebuffer binding\")"),
			"VulkanicAPI framebuffer binding must fail closed before Java compatibility state is touched");
		assertTrue(api.contains("rejectJavaVulkanWholeFrameOperation(\"render-target binding\")"),
			"VulkanicAPI render-target binding must fail closed before backend dispatch");
	}

	@Test
	void wholeFrameJavaFrameGraphCannotReopenVanillaPasses() throws Exception {
		String frameGraph = Files.readString(Path.of("src/main/java/net/blaze3d/framegraph/FrameGraphBuilder.java"));
		assertTrue(frameGraph.contains("Java frame-graph execution is unavailable while Rust owns whole-frame presentation"),
			"FrameGraphBuilder must fail closed before Java post/sky/weather execution under Rust ownership");
	}

	@Test
	void wholeFrameCannotPublishJavaNativeGpuHandles() throws Exception {
		String api = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		assertTrue(api.contains("Java Vulkan texture handles are unavailable while Rust owns whole-frame presentation"));
		assertTrue(api.contains("Java Vulkan buffer handles are unavailable while Rust owns whole-frame presentation"));
		assertTrue(api.contains("Java Vulkan framebuffer handles are unavailable while Rust owns whole-frame presentation"));
		assertTrue(api.contains("Java Vulkan legacy texture handles are unavailable while Rust owns the selected Vulkan route"));
	}

	@Test
	void selectedVulkanCannotReconstructLegacyGlBuffersForDhParity() throws Exception {
		String api = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		int helper = api.indexOf("private static @Nullable GpuBuffer shaderInputParityLegacyDrawBuffer");
		int branch = api.indexOf("case VULKAN -> null", helper);
		assertTrue(helper >= 0 && branch > helper,
			"Selected Vulkan must leave legacy DH GL-handle parity probing unavailable");
		assertFalse(api.substring(helper, Math.min(api.length(), branch + 160)).contains("new net.blaze3d.opengl.LegacyHandleGlBuffer"),
			"Vulkan parity probing must not construct Java LegacyHandleGlBuffer wrappers");
	}

	@Test
	void wholeFrameIrisShadowCompositeCannotReopenJavaPasses() throws Exception {
		String shadow = Files.readString(Path.of("src/main/java/net/irisshaders/iris/shadows/ShadowCompositeRenderer.java"));
		int render = shadow.indexOf("public void renderAll()");
		int guard = shadow.indexOf("Java Iris shadow-composite rendering is unavailable while Rust owns whole-frame presentation", render);
		assertTrue(render >= 0 && guard > render,
			"Iris shadow-composite entry must fail closed under Rust whole-frame ownership");
	}

	@Test
	void wholeFrameIrisPipelineCannotBindJavaFramebuffersOrSkyPasses() throws Exception {
		String pipeline = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java"));
		String iris = Files.readString(Path.of("src/main/java/net/irisshaders/iris/Iris.java"));
		assertTrue(iris.contains("isVulkanBackendInitializedAndSelected()")
			&& iris.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& iris.indexOf("return new VanillaRenderingPipeline()") < iris.indexOf("new IrisRenderingPipeline(programs)"),
			"Iris must not construct its Java shader pipeline after Rust Vulkan ownership is selected");
		assertTrue(pipeline.contains("Java Iris level begin/clear passes are unavailable while Rust owns whole-frame presentation"));
		assertTrue(pipeline.contains("Java Iris depth-copy hand prepass is unavailable while Rust owns whole-frame presentation"));
		assertTrue(pipeline.contains("Java Iris color-space post-processing is unavailable while Rust owns whole-frame presentation"));
		assertTrue(pipeline.contains("Java Iris sky clear/horizon rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(pipeline.contains("Java Iris default framebuffer binding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(pipeline.contains("Java Iris sky render-pass creation is unavailable while Rust owns whole-frame presentation"));
		assertTrue(pipeline.contains("Java Iris shadow framebuffer binding is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void wholeFrameDistantHorizonsCannotBindIrisJavaProgramsOrFramebuffers() throws Exception {
		String generic = Files.readString(Path.of("src/main/java/net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java"));
		String lod = Files.readString(Path.of("src/main/java/net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java"));
		String compat = Files.readString(Path.of("src/main/java/net/irisshaders/iris/compat/dh/DHCompatInternal.java"));
		String framebuffer = Files.readString(Path.of("src/main/java/net/irisshaders/iris/compat/dh/DhFrameBufferWrapper.java"));
		assertTrue(generic.contains("Java Iris Distant Horizons shader binding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(generic.contains("Java Iris Distant Horizons shader unbinding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(generic.contains("Java Iris Distant Horizons vertex-buffer binding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(lod.contains("Java Iris Distant Horizons shader binding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(lod.contains("Java Iris Distant Horizons shader unbinding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(compat.contains("isVulkanBackendInitializedAndSelected()")
			&& compat.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& compat.indexOf("incompatible = true") < compat.indexOf("createDepthTex("),
			"DH Iris compatibility construction must fail closed before Java depth/program allocation");
		assertTrue(framebuffer.contains("Java Iris Distant Horizons framebuffer binding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(framebuffer.contains("Java Iris Distant Horizons framebuffer attachment mutation is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void wholeFrameIrisShadowRendererCannotEnterJavaShadowPass() throws Exception {
		String shadow = Files.readString(Path.of("src/main/java/net/irisshaders/iris/shadows/ShadowRenderer.java"));
		int render = shadow.indexOf("public void renderShadows(LevelRenderer levelRenderer");
		int guard = shadow.indexOf("Java Iris shadow renderer entrypoint is unavailable while Rust owns whole-frame presentation", render);
		assertTrue(render >= 0 && guard > render);
	}

	@Test
	void wholeFrameDistantHorizonsCannotBorrowJavaLightmapOrTargetHandles() throws Exception {
		String lightmap = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/common/wrappers/misc/LightMapWrapper.java"));
		String render = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/common/wrappers/minecraft/MinecraftRenderWrapper.java"));
		String hook = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/fabric/hooks/DhLightTextureHook.java"));
		assertTrue(lightmap.contains("Java Distant Horizons lightmap binding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(lightmap.contains("Java Distant Horizons lightmap unbinding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(render.contains("Java Distant Horizons render-target binding is unavailable while Rust owns whole-frame presentation"));
		assertTrue(render.contains("Java Distant Horizons render-target identity is unavailable while Rust owns whole-frame presentation"));
		assertTrue(render.contains("Java Distant Horizons framebuffer handles are unavailable while Rust owns whole-frame presentation"));
		assertTrue(render.contains("Java Distant Horizons depth-texture handles are unavailable while Rust owns whole-frame presentation"));
		assertTrue(render.contains("Java Distant Horizons color-texture handles are unavailable while Rust owns whole-frame presentation"));
		assertTrue(hook.contains("Rust owns the copied semantic lightmap inputs"),
			"DH lightmap hooks must not convert Java lightmap state into handles on the Rust route");
	}

	@Test
	void wholeFrameDistantHorizonsApiCannotReopenJavaLodOrFadeRendering() throws Exception {
		String clientApi = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/api/internal/ClientApi.java"));
		assertTrue(clientApi.contains("Java Distant Horizons LOD rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(clientApi.contains("Java Distant Horizons deferred LOD rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(clientApi.contains("Java Distant Horizons opaque fade rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(clientApi.contains("Java Distant Horizons transparent fade rendering is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void wholeFrameDistantHorizonsCannotConstructLegacyGpuObjects() throws Exception {
		String color = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/texture/DhColorTexture.java"));
		String depth = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/texture/DHDepthTexture.java"));
		String framebuffer = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/texture/DhFramebuffer.java"));
		String shader = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/shader/Shader.java"));
		String shaderProgram = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java"));
		String vertexAttribute = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/vertexAttribute/AbstractVertexAttribute.java"));
		String buffer = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/buffer/GLBuffer.java"));
		String state = Files.readString(Path.of("src/main/java/com/seibel/distanthorizons/core/render/glObject/GLState.java"));
		assertTrue(color.contains("Java Distant Horizons color textures are unavailable while Rust owns whole-frame presentation"));
		assertTrue(depth.contains("Java Distant Horizons depth textures are unavailable while Rust owns whole-frame presentation"));
		assertTrue(framebuffer.contains("Java Distant Horizons framebuffers are unavailable while Rust owns whole-frame presentation"));
		assertTrue(color.contains("isVulkanBackendInitializedAndSelected()") && depth.contains("isVulkanBackendInitializedAndSelected()"));
		assertTrue(framebuffer.contains("isVulkanBackendInitializedAndSelected()"),
			"DH legacy GPU object constructors must reject selected Vulkan before Java handles are created");
		assertTrue(shader.contains("Java Distant Horizons shaders are unavailable while Rust owns whole-frame presentation"));
		assertTrue(shaderProgram.contains("Java Distant Horizons shader programs are unavailable while Rust owns whole-frame presentation"));
		assertTrue(vertexAttribute.contains("Java Distant Horizons vertex arrays are unavailable while Rust owns whole-frame presentation"),
			"DH shader and vertex-array constructors must not create Java GPU objects on selected Vulkan");
		assertTrue(buffer.contains("Java Distant Horizons buffers are unavailable while Rust owns whole-frame presentation"));
		assertTrue(state.contains("Java Distant Horizons GL state is unavailable while Rust owns whole-frame presentation"),
			"DH compatibility state snapshots must not borrow Java GPU state on selected Vulkan");
	}

	@Test
	void wholeFrameVoxelMapCannotReopenJavaWaypointBuffers() throws Exception {
		String waypoints = Files.readString(Path.of("src/main/java/net/voxelmap/util/WaypointContainer.java"));
		assertTrue(waypoints.contains("Java VoxelMap waypoint rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(waypoints.contains("Java VoxelMap waypoint beam rendering is unavailable while Rust owns whole-frame presentation"));
		assertTrue(waypoints.contains("Java VoxelMap waypoint label rendering is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void wholeFrameVoxelMapEntityImageRendererCannotAllocateJavaFbo() throws Exception {
		String manager = Files.readString(Path.of("src/main/java/net/voxelmap/entityrender/EntityMapImageManager.java"));
		int constructor = manager.indexOf("public EntityMapImageManager()");
		int guard = manager.indexOf("Java VoxelMap entity-image rendering is unavailable while Rust owns whole-frame presentation", constructor);
		int allocation = manager.indexOf("VulkanicAPI.createTexture(\"voxelmap-radarfbotexture\"", constructor);
		assertTrue(constructor >= 0 && guard > constructor && allocation > guard,
			"VoxelMap entity-image Java FBO allocation must be unavailable under Rust whole-frame ownership");
	}

	@Test
	void wholeFrameVoxelMapDoesNotConstructJavaOffscreenRenderer() throws Exception {
		String map = Files.readString(Path.of("src/main/java/net/voxelmap/Map.java"));
		int resource = map.indexOf("VulkanicAPI.createTexture(\"voxelmap-fbotexture\"");
		int guard = map.lastIndexOf("!RustGalVulkanWholeFrameMode.enabled()", resource);
		assertTrue(resource >= 0 && guard >= 0 && guard < resource,
			"Rust whole-frame VoxelMap construction must not allocate the legacy Java offscreen target");
		assertTrue(map.contains("Java VoxelMap minimap rendering is unavailable while Rust owns whole-frame presentation"),
			"VoxelMap's legacy Java minimap entry must fail closed after Rust presentation ownership transfers");
		String cachedRegion = Files.readString(Path.of("src/main/java/net/voxelmap/persistent/CachedRegion.java"));
		String image = Files.readString(Path.of("src/main/java/net/voxelmap/persistent/CompressibleGLBufferedImage.java"));
		assertTrue(cachedRegion.contains("Java VoxelMap persistent-region rendering is unavailable while Rust owns whole-frame presentation")
			&& image.contains("Java VoxelMap persistent-image upload is unavailable while Rust owns whole-frame presentation"),
			"VoxelMap world-map persistence must not upload Java textures in Rust whole-frame mode");
		String projection = Files.readString(Path.of("src/main/java/net/voxelmap/util/VoxelMapCachedOrthoProjectionMatrixBuffer.java"));
		assertTrue(projection.contains("Java VoxelMap projection UBO rendering is unavailable while Rust owns whole-frame presentation"),
			"VoxelMap projection UBO construction must fail closed under Rust whole-frame ownership");
	}

	@Test
	void wholeFrameVulkanBackendCannotBorrowIrisTextureUnitState() throws Exception {
		String backend = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
		String encoder = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanNativeCommandEncoder.java"));
		int plan = backend.indexOf("private PipelineResourcePlanner.Plan buildLegacyProgramResourcePlan(");
		int planGuard = backend.indexOf("Legacy Java shader-resource reconstruction is not part of the Rust-owned", plan);
		int resolve = backend.indexOf("VulkanicTextureView resolveLegacySamplerViewForProgram(");
		int resolveGuard = backend.indexOf("borrowed Iris GPU state is", resolve);
		int recover = encoder.indexOf("private GpuTextureView recoverSamplerView(");
		int recoverGuard = encoder.indexOf("never recover bindings from Iris' runtime texture cache", recover);
		assertTrue(plan >= 0 && planGuard > plan && backend.indexOf("isVulkanBackendSelected()", plan) < planGuard,
			"legacy Vulkan resource planning must be fenced whenever Vulkan is selected");
		assertTrue(resolve >= 0 && resolveGuard > resolve && backend.indexOf("isVulkanBackendSelected()", resolve) < resolveGuard,
			"legacy Vulkan sampler resolution must be fenced whenever Vulkan is selected");
		assertTrue(recover >= 0 && recoverGuard > recover, "native encoder sampler recovery must not borrow Iris state");
		assertTrue(backend.contains("Java Vulkan command encoder creation is unavailable while Rust owns whole-frame presentation"),
			"direct Java Vulkan command-encoder construction must fail closed under Rust whole-frame ownership");
		assertTrue(backend.contains("Java Vulkan terrain command encoder creation is unavailable while Rust owns whole-frame presentation"),
			"direct Java Vulkan terrain encoder construction must fail closed under Rust whole-frame ownership");
		assertTrue(backend.contains("private static void rejectJavaWholeFramePass(String operation)")
			&& backend.contains("rejectJavaWholeFramePass(\"createRenderPass\")")
			&& backend.contains("rejectJavaWholeFramePass(\"beginCommandBuffer\")")
			&& backend.contains("rejectJavaWholeFramePass(\"submitCommandBuffer\")")
			&& backend.contains("rejectJavaWholeFramePass(\"beginFrame\")")
			&& backend.contains("rejectJavaWholeFramePass(\"endFrame\")")
			&& backend.contains("rejectJavaWholeFramePass(\"beginRenderPass\")")
			&& backend.contains("rejectJavaWholeFramePass(\"beginRenderPass(framebuffer)\")")
			&& backend.contains("rejectJavaWholeFramePass(\"beginRenderPass(renderTargetDescriptor)\")"),
			"direct Java Vulkan pass/frame lifecycle entry points must fail closed under Rust whole-frame ownership");
	}

	@Test
	void wholeFrameVulkanBackendCannotConstructJavaGpuObjects() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
		assertGuarded(source, "public GpuTexture createTexture(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public GpuBuffer createBuffer(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public GpuTextureView createTextureView(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public VulkanicBuffer createManagedBuffer(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public VulkanicTexture createManagedTexture(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public VulkanicTextureView createManagedTextureView(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public int createShader(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public int createShaderProgram(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public void linkProgram(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public PipelineHandle createPipeline(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public net.vulkanic.DescriptorPoolHandle createDescriptorPool(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public net.vulkanic.DescriptorSetHandle allocateDescriptorSet(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public void updateDescriptorSet(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public void bindDescriptorSet(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public void executeGraphicsDraw(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public void executeComputeDispatch(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public int resolveBufferHandle(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public int resolveTextureHandle(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public int createTextures(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public int createFramebuffer(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public int createVertexArray(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public int createSampler(", "rejectJavaWholeFramePass");
		assertGuarded(source, "public long createFenceSync(", "rejectJavaWholeFramePass");
		int commandGuard = source.indexOf("private static long requireVulkanCommandBufferHandle");
		int commandReject = source.indexOf("rejectJavaWholeFramePass(operation)", commandGuard);
		assertTrue(commandGuard >= 0 && commandReject > commandGuard,
			"all legacy Vulkan command-context entry points must share the whole-frame ownership guard");
		String texture = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanGpuTexture.java"));
		int handle = texture.indexOf("public int getGlHandle()");
		int handleGuard = texture.indexOf("RustGalVulkanWholeFrameMode.enabled()", handle);
		assertTrue(handle >= 0 && handleGuard > handle,
			"Java Vulkan texture native-handle access must fail closed while Rust owns whole-frame presentation");
	}

	@Test
	void semanticWorldBackgroundCannotSilentlyUseDiagnosticFallback() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		int result = source.indexOf("wholeFrameResult = bridge.submitWholeFrameWithAffineGuiAndWorldTextAndFirstPerson(");
		int guard = source.indexOf("worldBackgroundDiagnosticFallbackCount()", result);
		assertTrue(result >= 0 && guard > result,
			"semantic world frames must reject native diagnostic-background fallback counts");
	}

	@Test
	void wholeFrameTerrainSourceRetainsFailureReasonInsteadOfSwallowingIt() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java"));
		assertTrue(source.contains("lastWholeFrameTerrainFailure"),
			"whole-frame terrain source must retain a bounded failure reason");
		assertTrue(source.contains("recordTerrainFailure(\"prepare\""),
			"terrain snapshot failures must be diagnosed at the prepare boundary");
		assertTrue(source.contains("recordTerrainFailure(\"unwrap\""),
			"terrain worker failures must be diagnosed at completion unwrap");
	}

	@Test
	void wholeFrameJavaTextureTeardownCannotMutateCompatibilityState() throws Exception {
		String api = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		int delete = api.indexOf("public static void deleteTexture(");
		int guard = api.indexOf("rejectJavaVulkanWholeFrameOperation", delete);
		assertTrue(delete >= 0 && guard > delete,
			"Java texture teardown must be rejected before touching compatibility state");
		assertApiDeletionGuarded(api, "public static void deleteBuffer(");
		assertApiDeletionGuarded(api, "public static void deleteFramebuffer(");
		assertApiDeletionGuarded(api, "public static void deleteProgram(");
		assertApiDeletionGuarded(api, "public static void deleteVertexArrays(");
	}

	private static void assertApiDeletionGuarded(String source, String method) {
		int start = source.indexOf(method);
		int guard = source.indexOf("rejectJavaVulkanWholeFrameOperation", start);
		assertTrue(start >= 0 && guard > start, method + " must reject before compatibility-state deletion");
	}

	@Test
	void sodiumFogCullingChecksVulkanOwnershipBeforeIrisPackState() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/RenderSectionManager.java"));
		int method = source.indexOf("private float getSearchDistance(");
		int vulkan = source.indexOf("boolean useFogOcclusion = VulkanicAPI.isVulkanBackendSelected()", method);
		int iris = source.indexOf("net.irisshaders.iris.Iris.getCurrentPack().isPresent()", vulkan);
		assertTrue(method >= 0 && vulkan > method && iris > vulkan,
			"Sodium fog culling must short-circuit Vulkan ownership before consulting Iris pack state");
	}

	@Test
	void wholeFramePbrSetupCannotReopenJavaTextureState() throws Exception {
		String cache = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pbr/TextureInfoCache.java"));
		String manager = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pbr/texture/PBRTextureManager.java"));
		int image = cache.indexOf("public void onTexImage2D(");
		int imageGuard = cache.indexOf("RustGalVulkanWholeFrameMode.enabled()", image);
		int holder = manager.indexOf("public PBRTextureHolder getOrLoadHolder(");
		int holderGuard = manager.indexOf("RustGalVulkanWholeFrameMode.enabled()", holder);
		assertTrue(image >= 0 && imageGuard > image, "PBR metadata callbacks must be inert in Rust whole-frame mode");
		assertTrue(holder >= 0 && holderGuard > holder, "PBR holder loading must not consult Java texture state in Rust whole-frame mode");
	}

	@Test
	void wholeFrameIrisJavaTextureObjectsStayUnavailable() throws Exception {
		String custom = Files.readString(Path.of("src/main/java/net/irisshaders/iris/targets/backed/NativeImageBackedCustomTexture.java"));
		String noise = Files.readString(Path.of("src/main/java/net/irisshaders/iris/targets/backed/NativeImageBackedNoiseTexture.java"));
		int customConstructor = custom.indexOf("public NativeImageBackedCustomTexture(");
		int customConstructorGuard = custom.indexOf("RustGalVulkanWholeFrameMode.enabled()", customConstructor);
		int customUpload = custom.indexOf("public void upload()");
		int customUploadGuard = custom.indexOf("RustGalVulkanWholeFrameMode.enabled()", customUpload);
		int noiseConstructor = noise.indexOf("public NativeImageBackedNoiseTexture(");
		int noiseConstructorGuard = noise.indexOf("RustGalVulkanWholeFrameMode.enabled()", noiseConstructor);
		int noiseUpload = noise.indexOf("public void upload()");
		int noiseUploadGuard = noise.indexOf("RustGalVulkanWholeFrameMode.enabled()", noiseUpload);
		assertTrue(customConstructor >= 0 && customConstructorGuard > customConstructor,
			"Iris custom textures must remain unavailable while Rust owns whole-frame presentation");
		assertTrue(customUpload >= 0 && customUploadGuard > customUpload,
			"Iris custom texture uploads must remain unavailable while Rust owns whole-frame presentation");
		assertTrue(noiseConstructor >= 0 && noiseConstructorGuard > noiseConstructor,
			"Iris noise textures must remain unavailable while Rust owns whole-frame presentation");
		assertTrue(noiseUpload >= 0 && noiseUploadGuard > noiseUpload,
			"Iris noise texture uploads must remain unavailable while Rust owns whole-frame presentation");
	}

	@Test
	void wholeFrameIrisUniformHelpersDoNotQueryJavaTextureBindings() throws Exception {
		String uniforms = Files.readString(Path.of("src/main/java/net/irisshaders/iris/uniforms/CommonUniforms.java"));
		String shader = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pipeline/programs/ExtendedShader.java"));
		int atlas = uniforms.indexOf("uniforms.uniform2i(\"atlasSize\"");
		int atlasGuard = uniforms.indexOf("RustGalVulkanWholeFrameMode.enabled()", atlas);
		int gtexture = uniforms.indexOf("uniforms.uniform2i(\"gtextureSize\"");
		int gtextureGuard = uniforms.indexOf("RustGalVulkanWholeFrameMode.enabled()", gtexture);
		int swizzle = shader.indexOf("if (intensitySwizzle");
		int swizzleGuard = shader.indexOf("RustGalVulkanWholeFrameMode.enabled()", swizzle);
		assertTrue(atlas >= 0 && atlasGuard > atlas, "atlas-size uniform must not query Iris texture state in whole-frame mode");
		assertTrue(gtexture >= 0 && gtextureGuard > gtexture, "gtexture-size uniform must not query Iris texture state in whole-frame mode");
		assertTrue(swizzle >= 0 && swizzleGuard > swizzle, "Iris intensity swizzle must not mutate Java texture state in whole-frame mode");
	}

	@Test
	void wholeFrameIrisSamplerCompatibilityPathsStayClosed() throws Exception {
		String samplers = Files.readString(Path.of("src/main/java/net/irisshaders/iris/gl/program/ProgramSamplers.java"));
		String custom = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pipeline/CustomTextureManager.java"));
		String tracker = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pbr/TextureTracker.java"));
		int update = samplers.indexOf("public void update()");
		int updateGuard = samplers.indexOf("RustGalVulkanWholeFrameMode.enabled()", update);
		int bind = samplers.indexOf("public void bindToRenderPass(");
		int bindGuard = samplers.indexOf("RustGalVulkanWholeFrameMode.enabled()", bind);
		assertTrue(update >= 0 && updateGuard > update, "Iris sampler updates must fail closed under Rust whole-frame ownership");
		assertTrue(bind >= 0 && bindGuard > bind, "Iris render-pass sampler binding must fail closed under Rust whole-frame ownership");
		assertTrue(custom.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()"),
			"Iris custom texture wrappers must not bind Java texture units in whole-frame mode");
		int setTexture = tracker.indexOf("public void onSetShaderTexture(");
		int trackerGuard = tracker.indexOf("RustGalVulkanWholeFrameMode.enabled()", setTexture);
		assertTrue(setTexture >= 0 && trackerGuard > setTexture,
			"Iris texture tracking must not publish Java shader bindings in whole-frame mode");
		int textureView = tracker.indexOf("public GpuTextureView getTextureView(");
		int textureViewGuard = tracker.indexOf("RustGalVulkanWholeFrameMode.enabled()", textureView);
		int shaderTexture = tracker.indexOf("public GpuTextureView getShaderTexture(");
		int shaderTextureGuard = tracker.indexOf("RustGalVulkanWholeFrameMode.enabled()", shaderTexture);
		assertTrue(textureView >= 0 && textureViewGuard > textureView,
			"Iris texture-view lookup must not expose Java GPU views in whole-frame mode");
		assertTrue(shaderTexture >= 0 && shaderTextureGuard > shaderTexture,
			"Iris shader-texture lookup must not expose Java GPU views in whole-frame mode");
	}

	@Test
	void wholeFrameIrisTextureUnitStateCannotBorrowJavaBindings() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/irisshaders/iris/gl/IrisRenderSystem.java"));
		int getBinding = source.indexOf("public static int getTextureBinding(");
		int getGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", getBinding);
		int setBinding = source.indexOf("public static void setTextureBinding(");
		int setGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", setBinding);
		int bindUnit = source.indexOf("public static void bindTextureToUnit(int target");
		int bindGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", bindUnit);
		int setup = source.indexOf("public static void bindTextureForSetup(int glType");
		int setupGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", setup);
		int createFramebuffer = source.indexOf("public static int createFramebuffer()");
		int createFramebufferGuard = source.indexOf("rejectJavaGpuObjectCreation", createFramebuffer);
		int createTexture = source.indexOf("public static int createTexture(int target)");
		int createTextureGuard = source.indexOf("rejectJavaGpuObjectCreation", createTexture);
		int createBuffers = source.indexOf("public static int createBuffers()");
		int createBuffersGuard = source.indexOf("rejectJavaGpuObjectCreation", createBuffers);
		assertTrue(getBinding >= 0 && getGuard > getBinding,
			"Iris texture binding reads must remain inert while Rust owns whole-frame presentation");
		assertTrue(setBinding >= 0 && setGuard > setBinding,
			"Iris texture binding writes must remain inert while Rust owns whole-frame presentation");
		assertTrue(bindUnit >= 0 && bindGuard > bindUnit,
			"Iris texture-unit binds must not reach Java GPU state while Rust owns whole-frame presentation");
		assertTrue(setup >= 0 && setupGuard > setup,
			"Iris texture setup must not reach Java GPU state while Rust owns whole-frame presentation");
		assertTrue(createFramebuffer >= 0 && createFramebufferGuard > createFramebuffer,
			"Iris framebuffer creation must remain unavailable while Rust owns whole-frame presentation");
		assertTrue(createTexture >= 0 && createTextureGuard > createTexture,
			"Iris texture creation must remain unavailable while Rust owns whole-frame presentation");
		assertTrue(createBuffers >= 0 && createBuffersGuard > createBuffers,
			"Iris buffer creation must remain unavailable while Rust owns whole-frame presentation");
	}

	@Test
	void wholeFrameIrisGpuMutatorsFailClosed() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/irisshaders/iris/gl/IrisRenderSystem.java"));
		assertGuarded(source, "public static void destroySampler(", "rejectJavaGpuMutation");
		assertGuarded(source, "public static void deleteBuffers(", "rejectJavaGpuMutation");
		assertGuarded(source, "public static void dispatchComputeIndirect(", "rejectJavaGpuMutation");
		assertGuarded(source, "public static void copyImageSubData(", "rejectJavaGpuMutation");
		assertGuarded(source, "public static void deleteTextureId(", "rejectJavaGpuMutation");
		assertGuarded(source, "public static void clearColor(", "rejectJavaGpuMutation");
		assertGuarded(source, "public static void blitFramebuffer(", "rejectJavaGpuMutation");
	}

	private static void assertGuarded(String source, String method, String guard) {
		int start = source.indexOf(method);
		int next = source.indexOf("\n\tpublic ", start + method.length());
		int guardAt = source.indexOf(guard, start);
		assertTrue(start >= 0 && guardAt > start && (next < 0 || guardAt < next),
			method + " must reject Java GPU ownership mutations in whole-frame mode");
	}

	@Test
	void wholeFrameItemModelExtractionDoesNotPublishIrisDisplayContext() throws Exception {
		String resolver = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/item/ItemModelResolver.java"));
		int context = resolver.indexOf("ItemContextState");
		int guard = resolver.lastIndexOf("RustGalVulkanWholeFrameMode.enabled()", context);
		assertTrue(context >= 0 && guard >= 0 && guard < context,
			"semantic item extraction must not publish Iris display-item runtime state");
	}

	@Test
	void wholeFrameFireworkParticlesDoNotReadIrisLayerOverrides() throws Exception {
		String fireworks = Files.readString(Path.of("src/main/java/net/minecraft/client/particle/FireworkParticles.java"));
		int iris = fireworks.indexOf("net.irisshaders.iris.Iris.IS_FOOL");
		int guard = fireworks.lastIndexOf("RustGalVulkanWholeFrameMode.enabled()", iris);
		assertTrue(iris >= 0 && guard >= 0 && guard < iris,
			"Rust particle extraction must not consult Iris firework layer overrides");
	}

	@Test
	void wholeFrameCloudOptionDoesNotQueryIrisPipeline() throws Exception {
		String options = Files.readString(Path.of("src/main/java/net/minecraft/client/Options.java"));
		int method = options.indexOf("public CloudStatus getCloudsType()");
		int iris = options.indexOf("Iris.getPipelineManager()", method);
		int guard = options.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		assertTrue(method >= 0 && iris > method && guard > method && guard < iris,
			"Rust cloud extraction must use the copied option without querying Iris pipeline state");
	}

	@Test
	void wholeFrameGlyphSelectionDoesNotReadIrisImmediateState() throws Exception {
		String glyphs = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/font/GlyphRenderTypes.java"));
		int immediate = glyphs.indexOf("ImmediateState.isRenderingBEs");
		int guard = glyphs.lastIndexOf("RustGalVulkanWholeFrameMode.enabled()", immediate);
		assertTrue(immediate >= 0 && guard >= 0 && guard < immediate,
			"Rust semantic glyph selection must not consult Iris ImmediateState");
	}

	@Test
	void resourceAtlasPbrFilteringDoesNotDependOnIrisRuntimeClasses() throws Exception {
		String lister = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/atlas/sources/DirectoryLister.java"));
		assertTrue(lister.contains("removePbrSuffix(resourceLocation.getPath())"));
		assertFalse(lister.contains("net.irisshaders.iris.pbr.texture.PBRType"),
			"resource-pack atlas filtering must remain backend-neutral and Rust-owned");
	}

	@Test
	void wholeFrameIrisRenderTargetContractCannotUseFramebufferFallback() throws Exception {
		String contract = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pipeline/IrisVulkanRenderTargetContract.java"));
		int recorded = contract.indexOf("boolean vulkanRecordedPass");
		int fallback = contract.indexOf("Iris framebuffer-compatible render-target fallback is unavailable", recorded);
		assertTrue(recorded >= 0 && fallback > recorded);
		String body = contract.substring(recorded, fallback);
		assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
		assertTrue(body.contains("!descriptorPathEnabled || !vulkanRecordedPass"));
		assertTrue(contract.contains("Iris render-target descriptor is incompatible with Rust whole-frame presentation"));
		assertTrue(contract.contains("Iris Java Vulkan pipeline fallback is unavailable while Rust owns whole-frame presentation"));
		assertTrue(contract.contains("Iris Java Vulkan render-pass fallback is unavailable while Rust owns whole-frame presentation"));
	}

	@Test
	void wholeFrameCannotReachJavaVulkanPresenterInternals() throws Exception {
		String backend = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
		String encoder = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanNativeCommandEncoder.java"));
		int backendPresent = backend.indexOf("public void presentTextureToScreen(CommandContext ctx, GpuTextureView textureView)");
		int backendGuard = backend.indexOf("Java Vulkan backend presentation is unavailable while Rust owns whole-frame presentation", backendPresent);
		int encoderPresent = encoder.indexOf("public void presentTexture(GpuTextureView textureView)");
		int encoderGuard = encoder.indexOf("Java Vulkan command-encoder presentation is unavailable while Rust owns whole-frame presentation", encoderPresent);
		assertTrue(backendPresent >= 0 && backendGuard > backendPresent, "Vulkan backend presenter must fail closed in Rust whole-frame mode");
		assertTrue(encoderPresent >= 0 && encoderGuard > encoderPresent, "Vulkan native encoder presenter must fail closed in Rust whole-frame mode");
	}

	@Test
	void wholeFrameJavaVulkanEncoderCannotBorrowIrisPipelineOverrides() throws Exception {
		String encoder = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanNativeCommandEncoder.java"));
		int resolver = encoder.indexOf("private static GlProgram resolveIrisOverrideProgram(");
		int guard = encoder.indexOf("do not\n            // inspect Iris' live pipeline manager", resolver);
		int manager = encoder.indexOf("Iris.getPipelineManager()", resolver);
		assertTrue(resolver >= 0 && guard > resolver && manager > guard,
			"Java Vulkan pipeline binding must fence Iris override lookup before touching the pipeline manager");
	}

	@Test
	void wholeFrameCommandEncoderCannotUseBootstrapJavaCompatibilityDevice() throws Exception {
		String api = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		String backend = Files.readString(Path.of("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
		int create = api.indexOf("public static CommandEncoder createCommandEncoder()");
		int guard = api.indexOf("RustGalVulkanWholeFrameMode.enabled()", create);
		int compatibilityBranch = api.indexOf("getDevice().createCommandEncoder()", create);
		assertTrue(create >= 0 && guard > create, "command encoder creation must fail closed from Rust whole-frame selection");
		assertTrue(compatibilityBranch < 0 || compatibilityBranch < guard,
			"whole-frame command encoder creation must not fall through to the Java compatibility device");
		assertTrue(backend.contains("Java Vulkan compatibility backend access is unavailable while Rust owns whole-frame presentation"),
			"the public compatibility wrapper must not become a whole-frame Java rendering escape hatch");
		assertTrue(api.contains("rejectJavaVulkanWholeFrameOperation(\"command-buffer submission\")")
			&& api.contains("rejectJavaVulkanWholeFrameOperation(\"GPU completion fence creation\")"),
			"stale Java command buffers and GPU fences must not execute after Rust takes whole-frame ownership");
	}

	@Test
	void wholeFrameTaczPipelinePrecompileCannotReopenJavaVulkan() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java"));
		int helper = source.indexOf("private static void ensureImmediatePipelineReady(");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", helper);
		int precompile = source.indexOf("VulkanicAPI.precompileRenderPipeline", helper);
		assertTrue(helper >= 0 && guard > helper && precompile > guard,
			"TACZ pipeline precompilation must not enter Java Vulkan during Rust whole-frame rendering");
	}

	@Test
	void wholeFrameDynamicUniformResetNeverUsesCompatibilityBackendWrapper() throws Exception {
		String api = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		int reset = api.indexOf("public static void resetDynamicUniforms()");
		int guard = api.indexOf("RustGalVulkanWholeFrameMode.enabled()", reset);
		int wrapper = api.indexOf("rawVulkanBackend.withCompatibilityBackend", reset);
		assertTrue(reset >= 0 && guard > reset, "dynamic uniform reset must select semantic CPU state in whole-frame mode");
		assertTrue(wrapper < 0 || wrapper < guard,
			"whole-frame dynamic uniform reset must not enter the Java compatibility backend wrapper");
	}

	@Test
	void wholeFrameDynamicUniformInitializationDoesNotAllocateJavaRing() throws Exception {
		String api = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicAPI.java"));
		int initialize = api.indexOf("public static void initializeDynamicUniforms()");
		int guard = api.indexOf("RustGalVulkanWholeFrameMode.enabled()", initialize);
		int absent = api.indexOf("dynamicUniforms = null", guard);
		int branchEnd = api.indexOf("\n        }", absent);
		int allocate = api.indexOf("dynamicUniforms = new DynamicUniforms()", guard);
		assertTrue(initialize >= 0 && guard > initialize && absent > guard,
			"whole-frame dynamic uniform initialization must leave the Java ring absent");
		assertTrue(allocate < 0 || allocate < guard || allocate > branchEnd,
			"whole-frame dynamic uniform initialization must not construct a Java GPU ring");
	}

	@Test
	void wholeFrameDynamicUniformStorageCannotBypassRingOwnershipGuard() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/DynamicUniformStorage.java"));
		int constructor = source.indexOf("public DynamicUniformStorage(");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
		int ring = source.indexOf("new MappableRingBuffer", constructor);
		assertTrue(constructor >= 0 && guard > constructor,
			"dynamic uniform storage must reject whole-frame Vulkan explicitly");
		assertTrue(ring < 0 || ring > guard,
			"dynamic uniform storage must not allocate a Java ring before its ownership guard");
	}

	@Test
	void wholeFrameProjectionBufferFamiliesDoNotAllocateJavaUbos() throws Exception {
		for (String name : new String[] {
			"PerspectiveProjectionMatrixBuffer.java",
			"CachedPerspectiveProjectionMatrixBuffer.java",
			"CachedOrthoProjectionMatrixBuffer.java"
		}) {
			String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer", name));
			int constructor = source.indexOf("public " + name.substring(0, name.length() - 5) + "(");
			int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
			int nullBuffer = source.indexOf("this.buffer = null", guard);
			int create = source.indexOf("VulkanicAPI.createBuffer", guard);
			assertTrue(constructor >= 0 && guard > constructor && nullBuffer > guard,
				name + " must leave its Java UBO absent for Rust whole-frame presentation");
			assertTrue(create < 0 || create > nullBuffer,
				name + " must not allocate its Java UBO before the whole-frame guard");
		}
	}

	@Test
	void wholeFrameSodiumChunkRendererDoesNotAllocateJavaTerrainBuffers() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/DefaultChunkRenderer.java"));
		int constructor = source.indexOf("public DefaultChunkRenderer(");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
		int nullIndex = source.indexOf("this.sharedIndexBuffer = null", guard);
		int commandList = source.indexOf("device.createCommandList()", guard);
		int createBuffer = source.indexOf("VulkanicAPI.createBuffer", guard);
		assertTrue(constructor >= 0 && guard > constructor && nullIndex > guard,
			"Sodium terrain renderer must retain no Java GPU resources for Rust whole-frame Vulkan");
		assertTrue(commandList < 0 || commandList > nullIndex,
			"whole-frame Sodium construction must not create a Java command list");
		assertTrue(createBuffer < 0 || createBuffer > nullIndex,
			"whole-frame Sodium construction must not create a Java terrain UBO");
	}

	@Test
	void wholeFrameSodiumChunkArenasDoNotReserveJavaGpuMemory() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/sodium/client/render/chunk/buffer/GpuChunkBufferArena.java"));
		int constructor = source.indexOf("public GpuChunkBufferArena(");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
		int nullBuffer = source.indexOf("this.arenaBuffer = null", guard);
		int create = source.indexOf("createBuffer(this.label", guard);
		int disabledBranch = source.indexOf("if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())", constructor);
		assertTrue(constructor >= 0 && guard > constructor && nullBuffer > guard,
			"whole-frame Sodium arenas must keep Java GPU memory unallocated");
		assertTrue(disabledBranch > constructor && create > disabledBranch && create < nullBuffer,
			"Java Sodium arena allocation must remain exclusively in the non-whole-frame branch");
	}

	@Test
	void wholeFrameDynamicTexturesRemainCpuOnly() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/DynamicTexture.java"));
		int supplier = source.indexOf("private void createTexture(Supplier<String> supplier)");
		int supplierGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", supplier);
		int supplierCreate = source.indexOf("VulkanicAPI.createTexture", supplier);
		int string = source.indexOf("private void createTexture(String string)");
		int stringGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", string);
		int stringCreate = source.indexOf("VulkanicAPI.createTexture", string);
		assertTrue(supplier >= 0 && supplierGuard > supplier && supplierCreate > supplierGuard,
			"supplier dynamic textures must guard Java GPU creation for Rust whole-frame Vulkan");
		assertTrue(string >= 0 && stringGuard > string && stringCreate > stringGuard,
			"named dynamic textures must guard Java GPU creation for Rust whole-frame Vulkan");
	}

	@Test
	void wholeFrameRenderTargetsRetainDimensionsWithoutJavaAttachments() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/blaze3d/pipeline/RenderTarget.java"));
		int create = source.indexOf("public void createBuffers(int i, int j)");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", create);
		int returnIndex = source.indexOf("return;", guard);
		int depthCreate = source.indexOf("VulkanicAPI.createTexture", guard);
		assertTrue(create >= 0 && guard > create && returnIndex > guard,
			"whole-frame RenderTarget creation must retain layout dimensions and return before Java attachments");
		assertTrue(depthCreate < 0 || depthCreate > returnIndex,
			"whole-frame RenderTarget creation must not allocate Java color/depth textures");
	}

	@Test
	void wholeFrameIrisFullscreenQuadDoesNotAllocateJavaVertexBuffer() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/irisshaders/iris/pathways/FullScreenQuadRenderer.java"));
		int constructor = source.indexOf("private FullScreenQuadRenderer()");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
		int nullQuad = source.indexOf("this.quad = null", guard);
		int create = source.indexOf("VulkanicAPI.createBuffer", guard);
		assertTrue(constructor >= 0 && guard > constructor && nullQuad > guard,
			"Iris fullscreen quad must remain absent in Rust whole-frame Vulkan");
		assertTrue(create < 0 || create > nullQuad,
			"whole-frame Iris fullscreen quad construction must not allocate a Java vertex buffer");
	}

	@Test
	void wholeFrameFontTexturesKeepOnlySemanticAtlasPixels() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/font/FontTexture.java"));
		int constructor = source.indexOf("public FontTexture(");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
		int create = source.indexOf("VulkanicAPI.createTexture", guard);
		int glyphGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", source.indexOf("GpuTextureView glyphTextureView"));
		assertTrue(constructor >= 0 && guard > constructor && create > guard,
			"font atlas Java texture creation must remain outside Rust whole-frame construction");
		assertTrue(glyphGuard > 0 && source.contains("? this.textureView"),
			"whole-frame glyphs must retain semantic atlas identity without requiring a Java texture view");
	}

	@Test
	void wholeFrameAtlasGlyphsDoNotRequireJavaAtlasViews() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/font/AtlasGlyphProvider.java"));
		int construction = source.indexOf("new AtlasGlyphProvider.Instance(");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", construction);
		int atlasView = source.indexOf("atlas.getTextureView()", guard);
		assertTrue(construction >= 0 && guard > construction && atlasView > guard,
			"resource-pack glyphs must carry a nullable Java view while preserving semantic atlas identity");
		assertTrue(source.contains("@Nullable GpuTextureView textureView"),
			"atlas glyph compatibility views must be nullable on the semantic route");
	}

	@Test
	void wholeFrameOverlayTextureStagesPixelsWithoutJavaTextureOperations() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/texture/OverlayTexture.java"));
		int constructor = source.indexOf("public OverlayTexture()");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", constructor);
		int stage = source.indexOf("stageDynamicTexture(this.texture)", guard);
		int clamp = source.indexOf("this.texture.setClamp(true)", guard);
		assertTrue(constructor >= 0 && guard > constructor && stage > guard,
			"whole-frame overlay must stage its completed CPU pixels into Rust semantic assets");
		assertTrue(clamp > stage,
			"Java overlay texture operations must remain in the non-whole-frame branch");
	}

	@Test
	void wholeFramePlayerGlyphsDoNotDereferenceSkinTextureViews() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/font/PlayerGlyphProvider.java"));
		int method = source.indexOf("public GpuTextureView textureView()");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int skinView = source.indexOf("skin.get()).textureView()", guard);
		assertTrue(method >= 0 && guard > method && skinView > guard,
			"whole-frame player glyphs must not dereference Java skin texture views");
		assertTrue(source.contains("@Nullable\n\t\tpublic GpuTextureView textureView()"),
			"player glyph compatibility views must be nullable on the semantic route");
	}

	@Test
	void wholeFramePlayerSkinCacheDoesNotMaterializeJavaTextureViews() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/PlayerSkinRenderCache.java"));
		int method = source.indexOf("public GpuTextureView textureView()");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int manager = source.indexOf("textureManager.getTexture", guard);
		assertTrue(method >= 0 && guard > method && manager > guard,
			"whole-frame skin cache must avoid Java texture materialization");
		assertTrue(source.contains("@Nullable\n\t\tpublic GpuTextureView textureView()"),
			"player skin cache compatibility views must be nullable on the semantic route");
	}

	@Test
	void wholeFrameIrisWidgetBindingDoesNotMaterializeJavaTextureViews() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/irisshaders/iris/gui/GuiUtil.java"));
		int method = source.indexOf("public static void bindIrisWidgetsTexture()");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int textureView = source.indexOf("getTexture(IRIS_WIDGETS_TEX).getTextureView()", guard);
		assertTrue(method >= 0 && guard > method && textureView > guard,
			"whole-frame Iris widgets must not acquire a Java texture view");
	}

	@Test
	void wholeFrameSodiumTerrainAtlasViewIsUnavailable() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/minecraft/client/renderer/chunk/ChunkSectionLayer.java"));
		int method = source.indexOf("public GpuTextureView textureView()");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int manager = source.indexOf("textureManager.getTexture", guard);
		assertTrue(method >= 0 && guard > method && manager > guard,
			"whole-frame Sodium terrain must not materialize the Java atlas view");
		assertTrue(source.contains("@Nullable\n\tpublic GpuTextureView textureView()"),
			"Sodium terrain compatibility atlas views must be nullable");
	}

	@Test
	void endPortalScreensCannotFallBackToJavaTextureViewsOnWholeFrame() throws Exception {
		String loading = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/LevelLoadingScreen.java"));
		int loadingCase = loading.indexOf("case END_PORTAL:");
		int loadingGuard = loading.indexOf("RustGalVulkanWholeFrameMode.enabled()", loadingCase);
		int loadingView = loading.indexOf("getTextureView()", loadingGuard);
		assertTrue(loadingCase >= 0 && loadingGuard > loadingCase && loadingView > loadingGuard,
			"whole-frame level loading must reject end-portal Java texture views before acquisition");

		String win = Files.readString(Path.of("src/main/java/net/minecraft/client/gui/screens/WinScreen.java"));
		int poem = win.indexOf("if (this.poem)");
		int winGuard = win.indexOf("RustGalVulkanWholeFrameMode.enabled()", poem);
		int winView = win.indexOf("getTextureView()", winGuard);
		assertTrue(poem >= 0 && winGuard > poem && winView > winGuard,
			"whole-frame win screen must reject end-portal Java texture views before acquisition");
	}

	@Test
	void wholeFrameVoxelMapBlitsUseSemanticResourceIdentity() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/voxelmap/util/VoxelMapGuiGraphics.java"));
		int method = source.indexOf("blitFloatGradient(GuiGraphics graphics, RenderPipeline pipeline, ResourceLocation texture");
		int guard = source.indexOf("isWholeFrameVulkanActive()", method);
		int semantic = source.indexOf("submitRustSemanticBlit", guard);
		int javaView = source.indexOf("getTexture(texture).getTextureView()", guard);
		assertTrue(method >= 0 && guard > method && semantic > guard && javaView > semantic,
			"whole-frame VoxelMap resource blits must carry semantic texture identity before Java view acquisition");
	}

	@Test
	void wholeFrameVoxelMapOverlayUsesExplicitSemanticBlits() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/voxelmap/Map.java"));
		int minimap = source.indexOf("RUST_SEMANTIC_MINIMAP");
		int minimapBlit = source.indexOf("submitRustSemanticBlit", minimap);
		int waypoint = source.indexOf("private void drawWaypointSemantic");
		int waypointBlit = source.indexOf("submitRustSemanticBlit", waypoint);
		int arrow = source.indexOf("private void drawArrow");
		int arrowBlit = source.indexOf("submitRustSemanticBlit", arrow);
		int frame = source.indexOf("private void drawMapFrame");
		int frameBlit = source.indexOf("submitRustSemanticBlit", frame);
		assertTrue(minimap >= 0 && minimapBlit > minimap
			&& waypoint >= 0 && waypointBlit > waypoint
			&& arrow >= 0 && arrowBlit > arrow
			&& frame >= 0 && frameBlit > frame,
			"Rust VoxelMap minimap, frame, arrow, and waypoint icons must use explicit semantic blits");
	}

	@Test
	void wholeFrameCannotConvertJavaTexturesToLegacyNativeHandles() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicCoreAPI.java"));
		int method = source.indexOf("public static int textureId(GpuTexture texture)");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int handle = source.indexOf("VulkanicAPI.getTextureHandle(texture)", guard);
		assertTrue(method >= 0 && guard > method && handle > guard,
			"whole-frame Vulkan must reject Java texture-to-native-handle conversion");
	}

	@Test
	void wholeFrameCoreApiCannotBecomeASecondPresenter() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicCoreAPI.java"));
		int method = source.indexOf("public static void presentTextureToScreen(CommandContext ctx, GpuTextureView textureView)");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int delegate = source.indexOf("VulkanicAPI.presentTextureToScreen(ctx, textureView)", guard);
		assertTrue(method >= 0 && guard > method && delegate > guard,
			"the typed core presenter must fail closed before delegating while Rust owns whole-frame presentation");
	}

	@Test
	void wholeFrameTargetAuditCannotDescribeAJavaDiagnosticShell() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java"));
		int method = source.indexOf("private static void auditWholeFrameTarget");
		int guard = source.indexOf("Rust Vulkan whole-frame target audit requires a consumed semantic primitive frame", method);
		assertTrue(method >= 0 && guard > method,
			"selected Rust Vulkan presentation must fail closed if its semantic primitive frame disappears before target audit");
	}

	@Test
	void wholeFrameCannotConvertJavaBuffersToLegacyNativeHandles() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicCoreAPI.java"));
		int method = source.indexOf("public static int bufferId(net.blaze3d.buffers.GpuBuffer buffer)");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", method);
		int handle = source.indexOf("VulkanicAPI.getBufferHandle(buffer)", guard);
		assertTrue(method >= 0 && guard > method && handle > guard,
			"whole-frame Vulkan must reject Java buffer-to-native-handle conversion");
	}

	@Test
	void wholeFrameVoxelMapUploadsDoNotOpenJavaCommandContexts() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/voxelmap/Map.java"));
		int firstUpload = source.indexOf("this.mapImages[this.zoom].upload()");
		int firstGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", firstUpload);
		int firstBarrier = source.indexOf("applyResourceBarriers(VulkanicAPI.getCommandContext()", firstGuard);
		int secondUpload = source.indexOf("this.mapImages[this.zoom].upload()", firstUpload + 1);
		int secondGuard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", secondUpload);
		int secondBarrier = source.indexOf("applyResourceBarriers(VulkanicAPI.getCommandContext()", secondGuard);
		assertTrue(firstUpload >= 0 && firstGuard > firstUpload && firstBarrier > firstGuard,
			"whole-frame VoxelMap mini-map uploads must not reopen Java command contexts");
		assertTrue(secondUpload >= 0 && secondGuard > secondUpload && secondBarrier > secondGuard,
			"whole-frame VoxelMap full-map uploads must not reopen Java command contexts");
	}

	@Test
	void wholeFrameSodiumCloudOptionDoesNotClearJavaRenderTargets() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/sodium/client/gui/SodiumGameOptionPages.java"));
		int binding = source.indexOf("opts.cloudStatus().set(value)");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", binding);
		int clear = source.indexOf("createCommandEncoder().clearColorAndDepthTextures", guard);
		assertTrue(binding >= 0 && guard > binding && clear > guard,
			"whole-frame Sodium cloud setting changes must not clear Java render targets");
	}

	@Test
	void wholeFrameImmediateVertexUploadsCannotReuseThroughJavaEncoder() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/blaze3d/vertex/VertexFormat.java"));
		int upload = source.indexOf("private static GpuBuffer uploadToBuffer(");
		int guard = source.indexOf("RustGalVulkanWholeFrameMode.enabled()", upload);
		int create = source.indexOf("VulkanicAPI.createBuffer", guard);
		int encoder = source.indexOf("VulkanicAPI.createCommandEncoder()", upload);
		assertTrue(upload >= 0 && guard > upload && create > guard,
			"whole-frame immediate uploads must select fresh semantic buffers");
		assertTrue(encoder > create, "Java command-encoder reuse must remain outside the whole-frame fresh-buffer branch");
	}

	@Test
	void frameAbiPayloadsUseConfinedPerSubmitArenas() throws Exception {
		String source = Files.readString(Path.of("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java"));
		int wholeFrame = source.indexOf("private WholeFrameSubmitResult submitWorldFrame(");
		int wholeFrameArena = source.indexOf("Arena frameArena = Arena.ofConfined()", wholeFrame);
		int guiFrame = source.indexOf("List<GuiMeshBatchRecord> meshBatches");
		int guiFrameArena = source.indexOf("Arena frameArena = Arena.ofConfined()", guiFrame);
		assertTrue(wholeFrame >= 0 && wholeFrameArena > wholeFrame,
			"whole-frame ABI payloads must not accumulate in the context arena");
		assertTrue(guiFrame >= 0 && guiFrameArena > guiFrame,
			"GUI ABI payloads must not accumulate in the context arena");
	}
}
