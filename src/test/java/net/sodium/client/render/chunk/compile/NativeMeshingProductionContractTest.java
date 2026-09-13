package net.sodium.client.render.chunk.compile;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NativeMeshingProductionContractTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void compactSnapshotsAreRejectedAfterModelCacheReload() throws IOException {
        String snapshot = source("src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeSectionSnapshot.java");
        String registry = source("src/main/java/net/sodium/client/render/chunk/compile/pipeline/NativeStaticBlockModelRegistry.java");

        assertTrue(registry.contains("reloadGeneration++"));
        assertTrue(registry.contains("NativeStaticBlockModelCache.clear()"));
        assertTrue(snapshot.contains("this.modelReloadGeneration = NativeStaticBlockModelRegistry.reloadGeneration()"));
        assertTrue(snapshot.contains("this.modelReloadGeneration != NativeStaticBlockModelRegistry.reloadGeneration()"));
        assertTrue(snapshot.contains("throw new IllegalStateException(\"Native section snapshot was built against stale native model metadata\")"));
        assertTrue(snapshot.indexOf("this.modelReloadGeneration != NativeStaticBlockModelRegistry.reloadGeneration()")
                < snapshot.indexOf("appendCompactNativeSectionSnapshotAllPasses"));
    }

    @Test
    void forcedJavaSwitchesRouteProducersModelsAndFluidsToFallback() throws IOException {
        String task = source("src/main/java/net/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask.java");
        String diagnostics = source("src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeMeshingDiagnostics.java");

        assertTrue(diagnostics.contains("mattmc.nativeMeshing.forceJavaProducers"));
        assertTrue(diagnostics.contains("mattmc.nativeMeshing.forceJavaModels"));
        assertTrue(diagnostics.contains("mattmc.nativeMeshing.forceJavaFluids"));
        assertTrue(task.contains("boolean forceJavaProducers = NativeMeshingDiagnostics.forceJavaProducers()"));
        assertTrue(task.contains("boolean forceJavaModels = NativeMeshingDiagnostics.forceJavaModels()"));
        assertTrue(task.contains("boolean forceJavaFluids = NativeMeshingDiagnostics.forceJavaFluids()"));
        assertTrue(task.contains("if (!forceJavaProducers)"));
        assertTrue(task.contains("forceJavaProducers ? new int[] { 0, 0, 0 } : nativeSectionSnapshot.flushAll"));
        assertTrue(task.contains("boolean nativeModel = modelState && !forceJavaProducers && !forceJavaModels"));
        assertTrue(task.contains("&& NativeStaticBlockModelRegistry.hasNativeModel(blockState)"));
        assertTrue(task.contains("if (!nativeModel)"));
        assertTrue(task.contains("&& (forceJavaProducers || forceJavaFluids || !nativeFluidSupported)"));
        assertTrue(task.contains("NativeMeshingCompatibilityFallback.renderModel"));
        assertTrue(task.contains("NativeMeshingCompatibilityFallback.renderFluid"));
    }

    @Test
    void bambooTintIndexedMultipartModelsUseExplicitUntintedRustSemantics() throws IOException {
        String registry = source("src/main/java/net/sodium/client/render/chunk/compile/pipeline/NativeStaticBlockModelRegistry.java");
        assertTrue(registry.contains("Blocks.BAMBOO || block == Blocks.POTTED_BAMBOO"));
        assertTrue(registry.contains("explicit constant/no-tint state"));
    }

    @Test
    void azaleaLeafTintedWeightedModelsUseRustFoliageSemantics() throws IOException {
        String registry = source("src/main/java/net/sodium/client/render/chunk/compile/pipeline/NativeStaticBlockModelRegistry.java");
        assertTrue(registry.contains("Blocks.AZALEA_LEAVES"));
        assertTrue(registry.contains("Blocks.FLOWERING_AZALEA_LEAVES"));
        assertTrue(registry.contains("return TINT_FOLIAGE"));
    }

    @Test
    void rustWholeFrameRejectsJavaMeshProductionOverrides() throws IOException {
        String task = source("src/main/java/net/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask.java");
        int route = task.indexOf("boolean rustStaticTerrainRoute");
        int guard = task.indexOf("Rust whole-frame terrain cannot enable Java mesh-production overrides", route);
        int fallback = task.indexOf("NativeMeshingCompatibilityFallback.renderFluid", guard);
        assertTrue(route >= 0 && guard > route && fallback > guard,
            "Rust terrain must reject Java override flags before fallback producers can run");
        assertTrue(task.substring(route, guard).contains("forceJavaProducers")
            && task.substring(route, guard).contains("forceJavaModels")
            && task.substring(route, guard).contains("forceJavaFluids"));
    }

    @Test
    void vanillaClientRenderPassProducersDeclareRustOwnershipBoundary() throws IOException {
        Path rendererRoot = ROOT.resolve("src/main/java/net/minecraft/client");
        try (var paths = Files.walk(rendererRoot)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) {
                    continue;
                }
                String text = Files.readString(path);
                if (text.contains("createRenderPass(")) {
                    assertTrue(
                        text.contains("RustGalVulkanWholeFrameMode")
                            || text.contains("usesRustWholeFrameVulkan()")
                            || text.contains("isVulkanBackendSelected()"),
                        () -> "Java render-pass producer lacks Rust ownership boundary: " + path
                    );
                }
            }
        }
    }

    @Test
    void rustWholeFrameLightmapSkipsJavaGpuEncoding() throws IOException {
        String lightTexture = source("src/main/java/net/minecraft/client/renderer/LightTexture.java");
        int rustBranch = lightTexture.indexOf("RustGalVulkanWholeFrameMode.enabled()");
        int javaEncoder = lightTexture.indexOf("CommandEncoder commandEncoder = VulkanicAPI.createCommandEncoder();");
        assertTrue(rustBranch >= 0);
        assertTrue(javaEncoder > rustBranch);
        assertTrue(lightTexture.indexOf("return;", rustBranch) < javaEncoder);
    }

    @Test
    void customGeometryProducersDeclareRustWholeFrameBoundary() throws IOException {
        Path sourceRoot = ROOT.resolve("src/main/java");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) continue;
                String normalized = path.toString().replace('\\', '/');
                if (normalized.contains("/SubmitNodeCollector.java")
                    || normalized.contains("/OrderedSubmitNodeCollector.java")
                    || normalized.contains("/SubmitNodeCollection.java")
                    || normalized.contains("/SubmitNodeStorage.java")) continue;
                String text = Files.readString(path);
                if (!text.contains("submitCustomGeometry(")) continue;
                assertTrue(
                    text.contains("RustGalVulkanWholeFrameMode")
                        || text.contains("usesRustWholeFrameVulkan()")
                        || text.contains("semantic capture encountered arbitrary custom geometry"),
                    () -> "custom-geometry producer lacks Rust ownership boundary: " + path
                );
            }
        }
    }

    @Test
    void rustWorldTextNameTagsHaveBoundedSemanticStorage() throws IOException {
        String source = source("src/main/java/net/minecraft/client/renderer/feature/NameTagFeatureRenderer.java");
        assertTrue(source.contains("MAX_RUST_SEMANTIC_SUBMITS"));
        assertTrue(source.contains("totalSubmitCount() + submitCount > MAX_RUST_SEMANTIC_SUBMITS"));
        assertTrue(source.contains("Rust whole-frame world-text route exceeded bounded name-tag submit capacity"));
    }

    @Test
    void rustWorldTextSubmitsHaveBoundedSemanticStorage() throws IOException {
        String source = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        assertTrue(source.contains("MAX_RUST_SEMANTIC_TEXT_SUBMITS"));
        assertTrue(source.contains("Rust whole-frame world-text route exceeded bounded text submit capacity"));
    }

    @Test
    void selectedVulkanWorldTextCannotRetainJavaOrIrisFallbackState() throws IOException {
        String source = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        assertTrue(source.contains("ensureSelectedVulkanWorldTextRoute();"));
        assertTrue(source.contains("Rust Vulkan world-text route is unavailable; Java text and Iris capture are not fallbacks"));
        int guard = source.indexOf("private void ensureSelectedVulkanWorldTextRoute()");
        int irisCapture = source.indexOf("iris$capture()", source.indexOf("public void submitText("));
        assertTrue(guard >= 0 && irisCapture > source.indexOf("public void submitText("),
            "world-text submissions must retain the selected-Vulkan guard before Iris capture");
    }

    @Test
    void selectedVulkanFeatureQueuesCannotRetainUnavailableJavaState() throws IOException {
        String source = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        assertTrue(source.contains("currentDebugLineRoute(), \"debug-hitbox\""));
        assertTrue(source.contains("currentEntityShadowRoute(), \"entity-shadow\""));
        assertTrue(source.contains("currentEntityFlameRoute(), \"entity-flame\""));
        assertTrue(source.contains("currentEntityLeashRoute(), \"entity-leash\""));
        assertTrue(source.contains("Java feature state is not a fallback"));
    }

    @Test
    void selectedVulkanCannotBypassSemanticTextThroughFontBuffers() throws IOException {
        String source = source("src/main/java/net/minecraft/client/gui/Font.java");
        assertTrue(source.contains("ensureJavaTextDrawAvailable();"));
        assertTrue(source.contains("Java Font buffer rendering is unavailable while Rust owns whole-frame Vulkan"));
        assertTrue(source.contains("public void drawInBatch8xOutline("));
    }

    @Test
    void worldTextGlyphExtractionBoundsBeforeListGrowth() throws IOException {
        String source = source("src/main/java/net/vulkanic/world/WorldTextSemanticCollector.java");
        assertTrue(source.contains("appendBoundedGlyph"));
        assertTrue(source.contains("SemanticGlyphLimitExceeded"));
        assertTrue(source.contains("collectSemanticQuads(glyph -> appendBoundedGlyph(glyphs, glyph))"));
    }

    @Test
    void distantHorizonsPendingVisibleColumnBookkeepingIsBounded() throws IOException {
        String source = source("src/main/java/net/vulkanic/world/DistantHorizonsSemanticCollector.java");
        assertTrue(source.contains("MAX_PENDING_VISIBLE_COLUMN_KEYS = 16_384"));
        assertTrue(source.contains("markPendingVisibleColumnLocked(columnKey)"));
        assertTrue(source.contains("pending visible-column admission exceeds"));
    }

    @Test
    void rejectedWorldCallbacksHaveBoundedPerFrameAccounting() throws IOException {
        String source = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(source.contains("MAX_UNSUPPORTED_CALLBACKS_PER_FRAME = 4_096"));
        assertTrue(source.contains("pendingUnsupportedFirstPersonItems < MAX_UNSUPPORTED_CALLBACKS_PER_FRAME"));
        assertTrue(source.contains("pendingUnsupportedCustomGeometry < MAX_UNSUPPORTED_CALLBACKS_PER_FRAME"));
        assertTrue(source.contains("pendingUnsupportedParticleGroups < MAX_UNSUPPORTED_CALLBACKS_PER_FRAME"));
        assertTrue(source.contains("pendingUnsupportedWorldTextSubmits = (int)Math.min"));
        assertTrue(source.contains("(long)pendingUnsupportedWorldTextSubmits + count"));
        assertTrue(source.contains("MAX_UNSUPPORTED_CALLBACKS_PER_FRAME"));
    }

    @Test
    void rustEntityFeatureSubmitsHaveBoundedSemanticStorage() throws IOException {
        String source = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        assertTrue(source.contains("MAX_RUST_SEMANTIC_FEATURE_SUBMITS"));
        assertTrue(source.contains("Rust whole-frame entity-shadow route exceeded bounded submit capacity"));
        assertTrue(source.contains("Rust whole-frame entity-flame route exceeded bounded submit capacity"));
        assertTrue(source.contains("Rust whole-frame entity-leash route exceeded bounded submit capacity"));
        assertTrue(source.contains("Rust whole-frame debug-hitbox route exceeded bounded submit capacity"));
    }

    @Test
    void rustBlockFeatureSubmitsHaveBoundedSemanticStorage() throws IOException {
        String source = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        assertTrue(source.contains("MAX_RUST_SEMANTIC_BLOCK_SUBMITS"));
        assertTrue(source.contains("ensureRustBlockSubmitCapacity"));
        assertTrue(source.contains("Rust whole-frame moving-block route exceeded bounded submit capacity"));
    }

    @Test
    void rustParticleExtractionHasBoundedSemanticStorage() throws IOException {
        String source = source("src/main/java/net/minecraft/client/renderer/state/QuadParticleRenderState.java");
        assertTrue(source.contains("MAX_RUST_SEMANTIC_PARTICLES"));
        assertTrue(source.contains("Rust whole-frame particle route exceeded bounded semantic particle capacity"));
        assertTrue(source.contains("particleCount >= MAX_RUST_SEMANTIC_PARTICLES"));
    }

    @Test
    void rustGuiTextExtractionHasBoundedSemanticStorage() throws IOException {
        String source = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
        assertTrue(source.contains("MAX_RUST_GUI_TEXT_QUADS"));
        assertTrue(source.contains("text-quad-cap="));
        assertTrue(source.contains("direct-glyph-quad-cap="));
    }

    @Test
    void rustGuiAffineRequestsHaveMatchingJavaAndRustBounds() throws IOException {
        String coordinator = source("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java");
        String ffi = source("src/main/rust/render/vulkanic/ffi/gui.rs");
        assertTrue(coordinator.contains("MAX_RUST_GUI_AFFINE_QUADS = 65_536"));
        assertTrue(coordinator.contains("GUI affine-quad capacity exceeded"));
        String frontend = source("src/main/rust/render/vulkanic/gui_frontend.rs");
        assertTrue(ffi.contains("GUI_MAX_AFFINE_QUADS: usize = super::super::gui_frontend::GUI_MAX_EXPANDED_AFFINE_QUADS"));
        assertTrue(frontend.contains("GUI_MAX_EXPANDED_AFFINE_QUADS: usize = 65_536"));
        assertTrue(ffi.contains("quads.len() > GUI_MAX_AFFINE_QUADS"));
    }

    @Test
	void rustGuiMeshAggregationMatchesRustResourceBounds() throws IOException {
		String coordinator = source("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java");
		String meshFrontend = source("src/main/rust/render/vulkanic/gui_mesh_frontend.rs");
		String guiFrontend = source("src/main/rust/render/vulkanic/gui_frontend.rs");
		assertTrue(coordinator.contains("MAX_RUST_GUI_MESH_BATCHES = 16_384"));
		assertTrue(meshFrontend.contains("GUI_MESH_MAX_BATCHES: usize = 16_384"));
		assertTrue(guiFrontend.contains("GUI_MAX_MESH_BATCHES: usize = 16_384"));
		assertTrue(coordinator.contains("MAX_RUST_GUI_MESH_VERTICES = 1_048_576"));
		assertTrue(coordinator.contains("MAX_RUST_GUI_MESH_INDICES = 3_145_728"));
        assertTrue(coordinator.contains("Rust whole-frame GUI mesh capacity exceeded"));
    }

    @Test
    void irisJavaPassProducersFailClosedUnderRustWholeFrame() throws IOException {
        String contract = source("src/main/java/net/irisshaders/iris/pipeline/IrisVulkanRenderTargetContract.java");
        assertTrue(contract.contains("Iris Java Vulkan render-pass fallback is unavailable while Rust owns whole-frame presentation"));
        assertTrue(contract.contains("Iris framebuffer-compatible render-target fallback is unavailable while Rust owns whole-frame presentation"));
        for (String producer : new String[] {
            "src/main/java/net/irisshaders/iris/pipeline/FinalPassRenderer.java",
            "src/main/java/net/irisshaders/iris/pipeline/CompositeRenderer.java",
            "src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java"
        }) {
            String source = source(producer);
            assertTrue(source.contains("RustGalVulkanWholeFrameMode"),
                () -> "Iris pass producer lacks Rust whole-frame guard: " + producer);
        }
    }

    @Test
    void everyEyesLayerDeclaresSemanticTextureIdentity() throws IOException {
        Path sourceRoot = ROOT.resolve("src/main/java");
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) continue;
                String text = Files.readString(path);
                if (text.contains("extends EyesLayer")) {
                    assertTrue(text.contains("semanticTexture"),
                        () -> "Eyes layer lacks a Rust semantic texture identity: " + path);
                }
            }
        }
    }

    @Test
    void rustWholeFrameCannotSilentlyDropFabricItemCommands() throws IOException {
        String source = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        assertTrue(source.contains("enqueueFabricMeshItem"));
        assertTrue(source.contains("Rust whole-frame Fabric item route rejected semantic MeshView quads"));
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(rust.contains("public static boolean enqueueFabricMeshItem"));
        assertTrue(rust.contains("quad.toVanilla(vertices, 0)"));
        assertTrue(rust.contains("vanillaQuads.size() > 4_096"));
        assertTrue(rust.contains("meshQuadCount[0] > 4_096"));
    }

    @Test
    void unsupportedCallbacksCustomFluidsAppendersAndBlockEntitiesStayJavaOwned() throws IOException {
        String task = source("src/main/java/net/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask.java");
        String fallback = source("src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeMeshingCompatibilityFallback.java");
        String registry = source("src/main/java/net/sodium/client/render/chunk/compile/pipeline/NativeStaticBlockModelRegistry.java");

        assertTrue(registry.contains("SELECTOR_IDS.remove(key)"));
        assertTrue(registry.contains("return MISSING_ID"));
        assertTrue(registry.contains("FluidRenderHandlerRegistry.INSTANCE.getOverride(fluidState.getType()) == null"));
        assertTrue(task.contains("NativeStaticBlockModelRegistry.hasNativeModel(blockState)"));
        assertTrue(task.contains("if (!nativeModel)"));
        assertTrue(task.contains("!nativeFluidSupported"));
        assertTrue(task.contains("if (blockState.hasBlockEntity())"));
        assertTrue(task.contains("renderData.addBlockEntity"));
        assertTrue(task.contains("NativeMeshingCompatibilityFallback.runMeshAppenders"));
        assertTrue(fallback.contains("PlatformLevelRenderHooks.INSTANCE.runChunkMeshAppenders"));
        assertTrue(fallback.contains("asFallbackVertexConsumer"));
        assertTrue(fallback.contains("rejectRustWholeFrameFluidFallback"),
                "custom fluid compatibility fallback must be unavailable on Rust whole-frame Vulkan");
        assertTrue(fallback.contains("Rust whole-frame terrain cannot execute a Java \" + family + \" fallback"),
                "selected Vulkan must fail closed instead of rendering custom fluids through Java");
        assertTrue(fallback.contains("rejectRustWholeFrameFallback(\"block model state=\"")
                        && fallback.contains("model.getClass().getName()"),
                "unsupported block-model fallback must be unavailable on Rust whole-frame Vulkan");
        assertTrue(fallback.contains("rejectRustWholeFrameFallback(\"platform mesh appender\")"),
                "platform mesh appenders must be unavailable on Rust whole-frame Vulkan");
    }

    @Test
    void supportedModelsAndBuiltInFluidsRemainNative() throws IOException {
        String task = source("src/main/java/net/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask.java");
        String registry = source("src/main/java/net/sodium/client/render/chunk/compile/pipeline/NativeStaticBlockModelRegistry.java");

        assertTrue(registry.contains("model instanceof SingleVariant"));
        assertTrue(registry.contains("model instanceof WeightedVariants"));
        assertTrue(registry.contains("model instanceof MultiPartModel"));
        assertTrue(registry.contains("return builtIn && FluidRenderHandlerRegistry.INSTANCE.getOverride(fluidState.getType()) == null"));
        assertTrue(registry.contains("fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER)"));
        assertTrue(registry.contains("fluidState.is(Fluids.LAVA) || fluidState.is(Fluids.FLOWING_LAVA)"));
        assertTrue(registry.contains("TINT_GRASS"));
        assertTrue(registry.contains("TINT_WATER"));
        assertTrue(task.contains("nativeSectionSnapshot.appendBlock"));
        assertTrue(task.contains("fallbackStats.recordNativeModelBlock()"));
        assertTrue(task.contains("fallbackStats.recordNativeFluidBlock(fluidState)"));
        assertTrue(task.contains("boolean rustFluidSupported = nativeFluidSupported"));
        assertTrue(task.contains("builtInWater || builtInLava"));
        assertTrue(task.contains("} else if (rustFluidSupported)"));
    }

    @Test
    void kangarooBodyUsesTheSharedRustIndexedModelAdmission() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String kangaroo = source("src/main/java/net/alexsmobs/client/render/KangarooRenderer.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.KangarooModel"));
        assertTrue(kangaroo.contains("textures/entity/kangaroo.png"));
        assertTrue(kangaroo.contains("extends MobRenderer<EntityKangaroo, KangarooRenderState, KangarooModel>"));
    }

    @Test
    void alligatorSnappingTurtleBodyAndMossUseRustIndexedModelAdmission() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String alligator = source("src/main/java/net/alexsmobs/client/render/RenderAlligatorSnappingTurtle.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelAlligatorSnappingTurtle"));
        assertTrue(alligator.contains("textures/entity/alligator_snapping_turtle.png"));
        assertTrue(alligator.contains("textures/entity/alligator_snapping_turtle_moss.png"));
        assertTrue(alligator.contains("submitModelSemanticTexture"));
    }

    @Test
    void caimanBodyUsesTheSharedRustIndexedModelAdmission() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String caiman = source("src/main/java/net/alexsmobs/client/render/RenderCaiman.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCaiman"));
        assertTrue(caiman.contains("textures/entity/caiman.png"));
        assertTrue(caiman.contains("extends MobRenderer<EntityCaiman, CaimanRenderState, ModelCaiman>"));
    }

    @Test
    void anteaterBodyUsesRustIndexedAdmissionWithSemanticTongueItemState() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String anteater = source("src/main/java/net/alexsmobs/client/render/RenderAnteater.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelAnteater"));
        assertTrue(anteater.contains("textures/entity/anteater.png"));
        assertTrue(anteater.contains("LayerAnteaterTongueItem"));
        assertTrue(anteater.contains("itemModelResolver.updateForLiving"));
    }

    @Test
    void blobfishModelVariantsUseRustIndexedAdmissionWithCopiedTextureChoice() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String blobfish = source("src/main/java/net/alexsmobs/client/render/RenderBlobfish.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelBlobfish"));
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelBlobfishDepressurized"));
        assertTrue(blobfish.contains("textures/entity/blobfish.png"));
        assertTrue(blobfish.contains("textures/entity/blobfish_depressurized.png"));
        assertTrue(blobfish.contains("this.model = modelDepressurized"));
    }

    @Test
    void gazelleBodyUsesRustIndexedAdmissionWithCopiedAnimationState() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String gazelle = source("src/main/java/net/alexsmobs/client/render/RenderGazelle.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelGazelle"));
        assertTrue(gazelle.contains("textures/entity/gazelle.png"));
        assertTrue(gazelle.contains("renderState.currentAnimationId"));
        assertTrue(gazelle.contains("extends MobRenderer<EntityGazelle, GazelleRenderState, ModelGazelle>"));
    }

    @Test
    void jerboaVariantsUseRustIndexedAdmissionWithCopiedSleepingTextureChoice() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String jerboa = source("src/main/java/net/alexsmobs/client/render/RenderJerboa.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelJerboa"));
        assertTrue(jerboa.contains("textures/entity/jerboa.png"));
        assertTrue(jerboa.contains("textures/entity/jerboa_sleeping.png"));
        assertTrue(jerboa.contains("return renderState.isSleeping ? TEXTURE_SLEEPING : TEXTURE"));
    }

    @Test
    void cachalotWhaleVariantsUseRustIndexedAdmissionAndSemanticCapturedSquidDispatch() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String whale = source("src/main/java/net/alexsmobs/client/render/RenderCachalotWhale.java");
        String squidLayer = source("src/main/java/net/alexsmobs/client/render/layer/LayerCachalotWhaleCapturedSquid.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCachalotWhale"));
        assertTrue(whale.contains("textures/entity/cachalot/cachalot_whale.png"));
        assertTrue(whale.contains("textures/entity/cachalot/cachalot_whale_sleeping.png"));
        assertTrue(whale.contains("textures/entity/cachalot/cachalot_whale_albino.png"));
        assertTrue(whale.contains("textures/entity/cachalot/cachalot_whale_albino_sleeping.png"));
        assertTrue(squidLayer.contains("dispatcher.submitSemantic"));
    }

    @Test
    void shaderPackDepthFarMatchesVanillaProjectionContract() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("shaderPackDepthFar(),"));
        assertTrue(renderer.contains("shaderPackDepthFarForRenderDistance("));
        assertTrue(renderer.contains("effectiveRenderDistance * 16.0F"));
        assertFalse(renderer.contains("gameRenderer.getDepthFar()"),
                "shader-pack far must not silently substitute the projection clip far plane");
        assertTrue(renderer.contains("shader-pack {@code far} semantic is the projection depth-far value"));
    }

    @Test
    void crowBodyUsesRustIndexedAdmissionWithSemanticHeldItemState() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String crow = source("src/main/java/net/alexsmobs/client/render/RenderCrow.java");
        String itemLayer = source("src/main/java/net/alexsmobs/client/render/layer/LayerCrowItem.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCrow"));
        assertTrue(crow.contains("textures/entity/crow.png"));
        assertTrue(crow.contains("itemModelResolver.updateForLiving"));
        assertTrue(itemLayer.contains("renderState.heldItem.submit"));
    }

    @Test
    void hummingbirdVariantsUseRustIndexedAdmissionWithCopiedTextureSelection() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String hummingbird = source("src/main/java/net/alexsmobs/client/render/RenderHummingbird.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelHummingbird"));
        assertTrue(hummingbird.contains("textures/entity/hummingbird_0.png"));
        assertTrue(hummingbird.contains("textures/entity/hummingbird_1.png"));
        assertTrue(hummingbird.contains("textures/entity/hummingbird_2.png"));
        assertTrue(hummingbird.contains("renderState.variant == 0"));
    }

    @Test
    void potooBodyUsesRustIndexedAdmissionWithCopiedTextureAndStateExtraction() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String potoo = source("src/main/java/net/alexsmobs/client/render/RenderPotoo.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelPotoo"));
        assertTrue(potoo.contains("textures/entity/potoo.png"));
        assertTrue(potoo.contains("renderState.flyProgress"));
        assertTrue(potoo.contains("renderState.isSleeping = entity.isSleeping()"));
    }

    @Test
    void mudskipperBodyUsesRustIndexedAdmissionWithCopiedMouthTextureSelection() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String mudskipper = source("src/main/java/net/alexsmobs/client/render/RenderMudskipper.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelMudskipper"));
        assertTrue(mudskipper.contains("textures/entity/mudskipper.png"));
        assertTrue(mudskipper.contains("textures/entity/mudskipper_spit.png"));
        assertTrue(mudskipper.contains("renderState.mouthOpen ? TEXTURE_SPIT : TEXTURE"));
    }

    @Test
    void combJellyBodyUsesRustIndexedAdmissionWithCopiedVariantTexturesAndScaleState() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String combJelly = source("src/main/java/net/alexsmobs/client/render/RenderCombJelly.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCombJelly"));
        assertTrue(combJelly.contains("textures/entity/comb_jelly_blue.png"));
        assertTrue(combJelly.contains("textures/entity/comb_jelly_green.png"));
        assertTrue(combJelly.contains("textures/entity/comb_jelly_red.png"));
        assertTrue(combJelly.contains("renderState.variant = entity.getVariant()"));
        assertTrue(combJelly.contains("matrixStackIn.scale(jellyScale, jellyScale, jellyScale)"));
    }

    @Test
    void emuBodyUsesRustIndexedAdmissionWithCopiedVariantAndBabyTextures() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String emu = source("src/main/java/net/alexsmobs/client/render/RenderEmu.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelEmu"));
        assertTrue(emu.contains("textures/entity/emu.png"));
        assertTrue(emu.contains("textures/entity/emu_baby.png"));
        assertTrue(emu.contains("textures/entity/emu_blonde.png"));
        assertTrue(emu.contains("textures/entity/emu_baby_blonde.png"));
        assertTrue(emu.contains("textures/entity/emu_blue.png"));
        assertTrue(emu.contains("state.variant = entity.getVariant()"));
    }

    @Test
    void flyingFishBodyUsesRustIndexedAdmissionWithCopiedVariantTextures() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String flyingFish = source("src/main/java/net/alexsmobs/client/render/RenderFlyingFish.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelFlyingFish"));
        assertTrue(flyingFish.contains("textures/entity/flying_fish_0.png"));
        assertTrue(flyingFish.contains("textures/entity/flying_fish_1.png"));
        assertTrue(flyingFish.contains("textures/entity/flying_fish_2.png"));
        assertTrue(flyingFish.contains("renderState.variant = entity.getVariant()"));
    }

    @Test
    void cockroachBodyUsesRustIndexedAdmissionWithCopiedTextureAndStateFlags() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String cockroach = source("src/main/java/net/alexsmobs/client/render/RenderCockroach.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCockroach"));
        assertTrue(cockroach.contains("textures/entity/cockroach.png"));
        assertTrue(cockroach.contains("renderState.hasMaracas = entity.hasMaracas()"));
        assertTrue(cockroach.contains("renderState.isHeadless = entity.isHeadless()"));
        assertTrue(cockroach.contains("renderState.isBaby = entity.isBaby()"));
    }

    @Test
    void rainFrogBodyUsesRustIndexedAdmissionWithCopiedVariantTextures() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String rainFrog = source("src/main/java/net/alexsmobs/client/render/RenderRainFrog.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelRainFrog"));
        assertTrue(rainFrog.contains("textures/entity/rain_frog_0.png"));
        assertTrue(rainFrog.contains("textures/entity/rain_frog_1.png"));
        assertTrue(rainFrog.contains("textures/entity/rain_frog_2.png"));
        assertTrue(rainFrog.contains("state.variant = entity.getVariant()"));
    }

    @Test
    void skunkBodyUsesRustIndexedAdmissionWithCopiedTextureAndSprayState() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String skunk = source("src/main/java/net/alexsmobs/client/render/RenderSkunk.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelSkunk"));
        assertTrue(skunk.contains("textures/entity/skunk.png"));
        assertTrue(skunk.contains("state.sprayProgress = entity.sprayProgress"));
        assertTrue(skunk.contains("state.prevSprayProgress = entity.prevSprayProgress"));
        assertTrue(skunk.contains("state.isBaby = entity.isBaby()"));
    }

    @Test
    void roadrunnerBodyUsesRustIndexedAdmissionWithCopiedMeepTextureSelection() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String roadrunner = source("src/main/java/net/alexsmobs/client/render/RenderRoadrunner.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelRoadrunner"));
        assertTrue(roadrunner.contains("textures/entity/roadrunner.png"));
        assertTrue(roadrunner.contains("textures/entity/roadrunner_meep.png"));
        assertTrue(roadrunner.contains("renderState.isMeep = entity.isMeep()"));
        assertTrue(roadrunner.contains("renderState.isMeep ? TEXTURE_MEEP : TEXTURE"));
    }

    @Test
    void rattlesnakeBodyUsesRustIndexedAdmissionWithCopiedTextureAndAnimationState() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String rattlesnake = source("src/main/java/net/alexsmobs/client/render/RenderRattlesnake.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelRattlesnake"));
        assertTrue(rattlesnake.contains("textures/entity/rattlesnake.png"));
        assertTrue(rattlesnake.contains("state.curlProgress = entity.curlProgress"));
        assertTrue(rattlesnake.contains("state.isRattling = entity.isRattling()"));
    }

    @Test
    void sugarGliderBodyUsesRustIndexedAdmissionWithCopiedTextureAndAttachmentState() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String sugarGlider = source("src/main/java/net/alexsmobs/client/render/RenderSugarGlider.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelSugarGlider"));
        assertTrue(sugarGlider.contains("textures/entity/sugar_glider.png"));
        assertTrue(sugarGlider.contains("state.attachmentFacing = entity.getAttachmentFacing()"));
        assertTrue(sugarGlider.contains("state.attachChangeProgress"));
        assertTrue(sugarGlider.contains("state.isPassenger = entity.isPassenger()"));
    }

    @Test
    void shoebillBodyUsesRustIndexedAdmissionWithCopiedTextureAndAnimationId() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String shoebill = source("src/main/java/net/alexsmobs/client/render/RenderShoebill.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelShoebill"));
        assertTrue(shoebill.contains("textures/entity/shoebill.png"));
        assertTrue(shoebill.contains("state.animationTick = entity.getAnimationTick()"));
        assertTrue(shoebill.contains("state.currentAnimationId = 1"));
        assertTrue(shoebill.contains("state.currentAnimationId = 3"));
    }

    @Test
    void terrapinBodyUsesRustIndexedAdmissionWithCopiedVariantTextureSelection() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String terrapin = source("src/main/java/net/alexsmobs/client/render/RenderTerrapin.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelTerrapin"));
        assertTrue(terrapin.contains("renderState.turtleType = entity.getTurtleType()"));
        assertTrue(terrapin.contains("renderState.isKoopa = entity.isKoopa()"));
        assertTrue(terrapin.contains("TerrapinTypes.KOOPA.getTexture()"));
        assertTrue(terrapin.contains("renderState.turtleType.getTexture()"));
    }

    @Test
    void anacondaHeadAndPartModelsUseRustIndexedAdmissionWithCopiedTextureVariants() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String anaconda = source("src/main/java/net/alexsmobs/client/render/RenderAnaconda.java");
        String part = source("src/main/java/net/alexsmobs/client/render/RenderAnacondaPart.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelAnaconda"));
        assertTrue(anaconda.contains("AnacondaPartIndex.HEAD"));
        assertTrue(anaconda.contains("textures/entity/anaconda.png"));
        assertTrue(anaconda.contains("textures/entity/anaconda_shedding.png"));
        assertTrue(anaconda.contains("textures/entity/anaconda_yellow.png"));
        assertTrue(anaconda.contains("textures/entity/anaconda_yellow_shedding.png"));
        assertTrue(part.contains("new ModelAnaconda(AnacondaPartIndex.NECK)"));
        assertTrue(part.contains("new ModelAnaconda(AnacondaPartIndex.BODY)"));
        assertTrue(part.contains("new ModelAnaconda(AnacondaPartIndex.TAIL)"));
        assertTrue(part.contains("this.model = getModelForType(renderState.partType)"));
    }

    @Test
    void capuchinMonkeyBodyUsesRustIndexedAdmissionWithCopiedVariantAndAnimationState() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String monkey = source("src/main/java/net/alexsmobs/client/render/RenderCapuchinMonkey.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCapuchinMonkey"));
        assertTrue(monkey.contains("textures/entity/capuchin_monkey_0.png"));
        assertTrue(monkey.contains("textures/entity/capuchin_monkey_3.png"));
        assertTrue(monkey.contains("state.variant = entity.getVariant()"));
        assertTrue(monkey.contains("state.currentAnimation = entity.getAnimation()"));
        assertTrue(monkey.contains("state.hasDart = entity.hasDart()"));
    }

    @Test
    void tarantulaHawkBodyUsesRustIndexedAdmissionWithCopiedEnvironmentAndAngerTextures() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String hawk = source("src/main/java/net/alexsmobs/client/render/RenderTarantulaHawk.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelTarantulaHawk"));
        assertTrue(hawk.contains("textures/entity/tarantula_hawk.png"));
        assertTrue(hawk.contains("textures/entity/tarantula_hawk_angry.png"));
        assertTrue(hawk.contains("textures/entity/tarantula_hawk_nether.png"));
        assertTrue(hawk.contains("textures/entity/tarantula_hawk_nether_angry.png"));
        assertTrue(hawk.contains("textures/entity/tarantula_hawk_baby.png"));
        assertTrue(hawk.contains("renderState.isNether = entity.isNether()"));
        assertTrue(hawk.contains("renderState.isAngry = entity.isAngry()"));
    }

    @Test
    void geladaMonkeyBodyUsesRustIndexedAdmissionWithCopiedLeaderAndAggroTextures() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String monkey = source("src/main/java/net/alexsmobs/client/render/RenderGeladaMonkey.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelGeladaMonkey"));
        assertTrue(monkey.contains("textures/entity/gelada_monkey.png"));
        assertTrue(monkey.contains("textures/entity/gelada_monkey_angry.png"));
        assertTrue(monkey.contains("textures/entity/gelada_monkey_leader.png"));
        assertTrue(monkey.contains("textures/entity/gelada_monkey_leader_angry.png"));
        assertTrue(monkey.contains("state.isLeader = entity.isLeader()"));
        assertTrue(monkey.contains("state.isAggro = entity.isAggro()"));
    }

    @Test
    void snowLeopardBodyUsesRustIndexedAdmissionWithCopiedSleepingTextureSelection() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String leopard = source("src/main/java/net/alexsmobs/client/render/RenderSnowLeopard.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelSnowLeopard"));
        assertTrue(leopard.contains("textures/entity/snow_leopard.png"));
        assertTrue(leopard.contains("textures/entity/snow_leopard_sleeping.png"));
        assertTrue(leopard.contains("renderState.isSleeping = entity.isSleeping()"));
        assertTrue(leopard.contains("renderState.isSleeping ? TEXTURE_SLEEPING : TEXTURE"));
    }

    @Test
    void mooseBodyUsesRustIndexedAdmissionWithCopiedSnowyAndAntlerTextureSelection() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String moose = source("src/main/java/net/alexsmobs/client/render/RenderMoose.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelMoose"));
        assertTrue(moose.contains("textures/entity/moose.png"));
        assertTrue(moose.contains("textures/entity/moose_antlered.png"));
        assertTrue(moose.contains("textures/entity/moose_snowy.png"));
        assertTrue(moose.contains("textures/entity/moose_snowy_antlered.png"));
        assertTrue(moose.contains("state.antlered = entity.isAntlered()"));
        assertTrue(moose.contains("state.snowy = entity.isSnowy()"));
    }

    @Test
    void hammerheadSharkBodyUsesRustIndexedAdmissionWithCopiedTexture() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String shark = source("src/main/java/net/alexsmobs/client/render/RenderHammerheadShark.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelHammerheadShark"));
        assertTrue(shark.contains("textures/entity/hammerhead_shark.png"));
        assertTrue(shark.contains("new ModelHammerheadShark()"));
    }

    @Test
    void gorillaBodyUsesRustIndexedAdmissionWithSemanticHeldItemLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String gorilla = source("src/main/java/net/alexsmobs/client/render/RenderGorilla.java");
        String itemLayer = source("src/main/java/net/alexsmobs/client/render/layer/LayerGorillaItem.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelGorilla"));
        assertTrue(gorilla.contains("textures/entity/gorilla.png"));
        assertTrue(gorilla.contains("textures/entity/gorilla_silverback.png"));
        assertTrue(gorilla.contains("textures/entity/gorilla_dk.png"));
        assertTrue(gorilla.contains("textures/entity/gorilla_funky.png"));
        assertTrue(gorilla.contains("HoldingEntityRenderState.extractHoldingEntityRenderState"));
        assertTrue(itemLayer.contains("state.heldItem.submit"));
        assertTrue(itemLayer.contains("OverlayTexture.NO_OVERLAY"));
    }

    @Test
    void bisonBodyUsesRustIndexedAdmissionWithCopiedShearedAndSnowyTextures() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String bison = source("src/main/java/net/alexsmobs/client/render/RenderBison.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelBison"));
        assertTrue(bison.contains("textures/entity/bison.png"));
        assertTrue(bison.contains("textures/entity/bison_sheared.png"));
        assertTrue(bison.contains("textures/entity/bison_snowy.png"));
        assertTrue(bison.contains("textures/entity/bison_baby.png"));
        assertTrue(bison.contains("state.isSheared = entity.isSheared()"));
        assertTrue(bison.contains("state.isSnowy = entity.isSnowy()"));
        assertTrue(bison.contains("state.isBaby = entity.isBaby()"));
    }

    @Test
    void catfishSizeModelsUseRustIndexedAdmissionWithCopiedSpitTextureVariants() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String catfish = source("src/main/java/net/alexsmobs/client/render/RenderCatfish.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCatfishSmall"));
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCatfishMedium"));
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCatfishLarge"));
        assertTrue(catfish.contains("textures/entity/catfish_small.png"));
        assertTrue(catfish.contains("textures/entity/catfish_medium.png"));
        assertTrue(catfish.contains("textures/entity/catfish_large.png"));
        assertTrue(catfish.contains("textures/entity/catfish_small_spit.png"));
        assertTrue(catfish.contains("textures/entity/catfish_large_spit.png"));
        assertTrue(catfish.contains("model = modelLarge"));
        assertTrue(catfish.contains("renderState.isSpitting"));
    }

    @Test
    void orcaBodyUsesRustIndexedAdmissionWithCopiedVariantTexturesAndAnimationState() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String orca = source("src/main/java/net/alexsmobs/client/render/RenderOrca.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelOrca"));
        assertTrue(orca.contains("textures/entity/orca_ne.png"));
        assertTrue(orca.contains("textures/entity/orca_nw.png"));
        assertTrue(orca.contains("textures/entity/orca_se.png"));
        assertTrue(orca.contains("textures/entity/orca_sw.png"));
        assertTrue(orca.contains("state.variant = entity.getVariant()"));
        assertTrue(orca.contains("state.currentAnimation = entity.getAnimation()"));
    }

    @Test
    void crocodileBodyUsesRustIndexedAdmissionWithSemanticCrownLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String crocodile = source("src/main/java/net/alexsmobs/client/render/RenderCrocodile.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCrocodile"));
        assertTrue(crocodile.contains("textures/entity/crocodile_0.png"));
        assertTrue(crocodile.contains("textures/entity/crocodile_1.png"));
        assertTrue(crocodile.contains("textures/entity/crocodile_crown.png"));
        assertTrue(crocodile.contains("state.isDesert = entity.isDesert()"));
        assertTrue(crocodile.contains("submitModelSemanticTexture"));
        assertTrue(crocodile.contains("OverlayTexture.NO_OVERLAY"));
    }

    @Test
    void seagullBodyUsesRustIndexedAdmissionWithCopiedWingullTextureSelection() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String seagull = source("src/main/java/net/alexsmobs/client/render/RenderSeagull.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelSeagull"));
        assertTrue(seagull.contains("textures/entity/seagull.png"));
        assertTrue(seagull.contains("textures/entity/seagull_wingull.png"));
        assertTrue(seagull.contains("state.isWingull = entity.isWingull()"));
        assertTrue(seagull.contains("state.isWingull ? TEXTURE_WINGULL : TEXTURE"));
    }

    @Test
    void bunfungusBodyUsesRustIndexedAdmissionWithSemanticHeldItemAndSleepingTexture() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String bunfungus = source("src/main/java/net/alexsmobs/client/render/RenderBunfungus.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelBunfungus"));
        assertTrue(bunfungus.contains("textures/entity/bunfungus.png"));
        assertTrue(bunfungus.contains("textures/entity/bunfungus_sleeping.png"));
        assertTrue(bunfungus.contains("renderState.isSleeping ? TEXTURE_SLEEPING : TEXTURE"));
        assertTrue(bunfungus.contains("itemModelResolver.updateForLiving"));
        assertTrue(bunfungus.contains("renderState.mainHandItem.submit"));
        assertTrue(bunfungus.contains("OverlayTexture.NO_OVERLAY"));
    }

    @Test
    void giantSquidBodyUsesRustIndexedAdmissionWithSemanticDepressurizationLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String squid = source("src/main/java/net/alexsmobs/client/render/RenderGiantSquid.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelGiantSquid"));
        assertTrue(squid.contains("textures/entity/giant_squid.png"));
        assertTrue(squid.contains("textures/entity/giant_squid_blue.png"));
        assertTrue(squid.contains("textures/entity/giant_squid_depressurized.png"));
        assertTrue(squid.contains("renderState.isBlue = entity.isBlue()"));
        assertTrue(squid.contains("submitModelSemanticTexture"));
        assertTrue(squid.contains("OverlayTexture.NO_OVERLAY"));
    }

    @Test
    void mimicOctopusBodyUsesRustIndexedAdmissionWithSemanticOverlayAndGuardianBeam() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String octopus = source("src/main/java/net/alexsmobs/client/render/RenderMimicOctopus.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelMimicOctopus"));
        assertTrue(octopus.contains("textures/entity/mimic_octopus.png"));
        assertTrue(octopus.contains("textures/entity/mimic_octopus_overlay.png"));
        assertTrue(octopus.contains("textures/entity/mimic_octopus_guardian.png"));
        assertTrue(octopus.contains("textures/entity/mimic_octopus_eyes.png"));
        assertTrue(octopus.contains("submitModelSemanticTexture"));
        assertTrue(octopus.contains("submitGuardianBeam"));
        assertTrue(octopus.contains("renderState.guardianLaserTargetPresent"));
    }

    @Test
    void mantisShrimpBodyUsesRustIndexedAdmissionWithVariantTexturesAndSemanticHeldItem() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String shrimp = source("src/main/java/net/alexsmobs/client/render/RenderMantisShrimp.java");
        String itemLayer = source("src/main/java/net/alexsmobs/client/render/layer/LayerMantisShrimpItem.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelMantisShrimp"));
        assertTrue(shrimp.contains("textures/entity/mantis_shrimp_0.png"));
        assertTrue(shrimp.contains("textures/entity/mantis_shrimp_3.png"));
        assertTrue(shrimp.contains("state.variant = entity.getVariant()"));
        assertTrue(shrimp.contains("itemModelResolver.updateForLiving"));
        assertTrue(itemLayer.contains("renderState.mainHandItem.submit"));
        assertTrue(itemLayer.contains("OverlayTexture.NO_OVERLAY"));
    }

    @Test
    void mimicubeBodyUsesRustIndexedAdmissionWithSemanticItemHelmetAndTextureLayers() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String mimicube = source("src/main/java/net/alexsmobs/client/render/RenderMimicube.java");
        String heldItem = source("src/main/java/net/alexsmobs/client/render/layer/LayerMimicubeHeldItem.java");
        String helmet = source("src/main/java/net/alexsmobs/client/render/layer/LayerMimicubeHelmet.java");
        String texture = source("src/main/java/net/alexsmobs/client/render/layer/LayerMimicubeTexture.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelMimicube"));
        assertTrue(mimicube.contains("textures/entity/mimicube.png"));
        assertTrue(mimicube.contains("itemModelResolver.updateForLiving"));
        assertTrue(heldItem.contains("item.submit"));
        assertTrue(helmet.contains("headItem.submit"));
        assertTrue(texture.contains("submitModelSemanticTexture"));
    }

    @Test
    void cosmicCodBodyUsesRustIndexedAdmissionWithSemanticGlowLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String cod = source("src/main/java/net/alexsmobs/client/render/RenderCosmicCod.java");
        String glow = source("src/main/java/net/alexsmobs/client/render/layer/LayerBasicGlow.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCosmicCod"));
        assertTrue(cod.contains("textures/entity/cosmic_cod.png"));
        assertTrue(cod.contains("textures/entity/cosmic_cod_eyes.png"));
        assertTrue(cod.contains("new ModelCosmicCod()"));
        assertTrue(glow.contains("submitModelSemanticTexture"));
    }

    @Test
    void rhinocerosBodyUsesRustIndexedAdmissionWithSemanticPotionLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String rhino = source("src/main/java/net/alexsmobs/client/render/RenderRhinoceros.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelRhinoceros"));
        assertTrue(rhino.contains("textures/entity/rhinoceros.png"));
        assertTrue(rhino.contains("textures/entity/rhinoceros_angry.png"));
        assertTrue(rhino.contains("textures/entity/rhinoceros_potion.png"));
        assertTrue(rhino.contains("state.isAngry = entity.isAngry()"));
        assertTrue(rhino.contains("submitModelSemanticTexture"));
        assertTrue(rhino.contains("state.potionColor"));
    }

    @Test
    void komodoDragonBodyUsesRustIndexedAdmissionWithSemanticSaddleAndMaidLayers() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String komodo = source("src/main/java/net/alexsmobs/client/render/RenderKomodoDragon.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelKomodoDragon"));
        assertTrue(komodo.contains("textures/entity/komodo_dragon.png"));
        assertTrue(komodo.contains("textures/entity/komodo_dragon_saddle.png"));
        assertTrue(komodo.contains("textures/entity/komodo_dragon_maid.png"));
        assertTrue(komodo.contains("renderState.isSaddled = komodo.isSaddled()"));
        assertTrue(komodo.contains("renderState.isMaid = komodo.isMaid()"));
        assertTrue(komodo.contains("submitModelSemanticTexture"));
    }

    @Test
    void elephantBodyUsesRustIndexedAdmissionWithSemanticOverlayAndHeldItemLayers() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String elephant = source("src/main/java/net/alexsmobs/client/render/RenderElephant.java");
        String overlays = source("src/main/java/net/alexsmobs/client/render/layer/LayerElephantOverlays.java");
        String item = source("src/main/java/net/alexsmobs/client/render/layer/LayerElephantItem.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelElephant"));
        assertTrue(elephant.contains("textures/entity/elephant/elephant.png"));
        assertTrue(elephant.contains("textures/entity/elephant/elephant_tusks.png"));
        assertTrue(elephant.contains("itemModelResolver.updateForLiving"));
        assertTrue(overlays.contains("submitModelSemanticTexture"));
        assertTrue(item.contains("state.mainHandItem.submit"));
        assertTrue(item.contains("OverlayTexture.NO_OVERLAY"));
    }

    @Test
    void baldEagleBodyUsesRustIndexedAdmissionWithSemanticCapLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String eagle = source("src/main/java/net/alexsmobs/client/render/RenderBaldEagle.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelBaldEagle"));
        assertTrue(eagle.contains("textures/entity/bald_eagle.png"));
        assertTrue(eagle.contains("textures/entity/bald_eagle_hood.png"));
        assertTrue(eagle.contains("submitModelSemanticTexture"));
        assertTrue(eagle.contains("state.hasCap = entity.hasCap()"));
    }

    @Test
    void grizzlyBearBodyUsesRustIndexedAdmissionWithSemanticSnowHoneyEyesAndItemLayers() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String bear = source("src/main/java/net/alexsmobs/client/render/RenderGrizzlyBear.java");
        String honey = source("src/main/java/net/alexsmobs/client/render/layer/LayerGrizzlyHoney.java");
        String item = source("src/main/java/net/alexsmobs/client/render/layer/LayerGrizzlyItem.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelGrizzlyBear"));
        assertTrue(bear.contains("textures/entity/grizzly_bear.png"));
        assertTrue(bear.contains("textures/entity/grizzly_bear_snowy.png"));
        assertTrue(bear.contains("textures/entity/grizzly_bear_freddy_eyes.png"));
        assertTrue(bear.contains("submitModelSemanticTexture"));
        assertTrue(honey.contains("submitModelSemanticTexture"));
        assertTrue(item.contains("state.heldItem.submit"));
        assertTrue(item.contains("OverlayTexture.NO_OVERLAY"));
    }

    @Test
    void tigerBodyUsesRustIndexedAdmissionWithCopiedWhiteAngrySleepingTexturesAndEyes() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String tiger = source("src/main/java/net/alexsmobs/client/render/RenderTiger.java");
        String eyes = source("src/main/java/net/alexsmobs/client/render/layer/LayerTigerEyes.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelTiger"));
        assertTrue(tiger.contains("textures/entity/tiger/tiger.png"));
        assertTrue(tiger.contains("textures/entity/tiger/tiger_angry.png"));
        assertTrue(tiger.contains("textures/entity/tiger/tiger_sleeping.png"));
        assertTrue(tiger.contains("textures/entity/tiger/tiger_white_sleeping.png"));
        assertTrue(tiger.contains("state.isWhite = entity.isWhite()"));
        assertTrue(eyes.contains("submitModelSemanticTexture"));
    }

    @Test
    void caveCentipedeHeadBodyAndTailUseRustIndexedAdmissionWithSemanticEyes() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String head = source("src/main/java/net/alexsmobs/client/render/RenderCentipedeHead.java");
        String body = source("src/main/java/net/alexsmobs/client/render/RenderCentipedeBody.java");
        String tail = source("src/main/java/net/alexsmobs/client/render/RenderCentipedeTail.java");
        String eyes = source("src/main/java/net/alexsmobs/client/render/layer/LayerCentipedeHeadEyes.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCaveCentipede"));
        assertTrue(head.contains("new ModelCaveCentipede(0)"));
        assertTrue(body.contains("new ModelCaveCentipede(1)"));
        assertTrue(tail.contains("new ModelCaveCentipede(2)"));
        assertTrue(head.contains("textures/entity/cave_centipede.png"));
        assertTrue(eyes.contains("submitModelSemanticTexture"));
    }

    @Test
    void sunbirdBodyUsesRustIndexedAdmissionWithSemanticScorchGlowLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String sunbird = source("src/main/java/net/alexsmobs/client/render/RenderSunbird.java");
        String scorch = source("src/main/java/net/alexsmobs/client/render/layer/LayerSunbirdScorch.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelSunbird"));
        assertTrue(sunbird.contains("textures/entity/sunbird.png"));
        assertTrue(sunbird.contains("textures/entity/sunbird_glow.png"));
        assertTrue(sunbird.contains("state.scorchProgress"));
        assertTrue(scorch.contains("submitModelSemanticTexture"));
    }

    @Test
    void skelewagBodyUsesRustIndexedAdmissionWithCopiedVariantTexturesAndAnimationId() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String skelewag = source("src/main/java/net/alexsmobs/client/render/RenderSkelewag.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelSkelewag"));
        assertTrue(skelewag.contains("textures/entity/skelewag_0.png"));
        assertTrue(skelewag.contains("textures/entity/skelewag_1.png"));
        assertTrue(skelewag.contains("state.variant = entity.getVariant()"));
        assertTrue(skelewag.contains("state.currentAnimationId"));
    }

    @Test
    void mungusBodyUsesRustIndexedAdmissionWithSemanticOverlaysAndBeamQuads() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String mungus = source("src/main/java/net/alexsmobs/client/render/RenderMungus.java");
        String beam = source("src/main/java/net/alexsmobs/client/render/layer/MungusBeamLayer.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelMungus"));
        assertTrue(mungus.contains("textures/entity/mungus.png"));
        assertTrue(mungus.contains("textures/entity/mungus_sack.png"));
        assertTrue(mungus.contains("submitModelSemanticTexture"));
        assertTrue(beam.contains("submitTranslucentTexturedQuadSemantic"));
        assertTrue(beam.contains("textures/entity/mungus_beam.png"));
    }

    @Test
    void warpedToadUsesRustIndexedAdmissionWithVariantAndGlowTextures() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String toad = source("src/main/java/net/alexsmobs/client/render/RenderWarpedToad.java");
        String glow = source("src/main/java/net/alexsmobs/client/render/layer/LayerWarpedToadGlow.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelWarpedToad"));
        assertTrue(toad.contains("textures/entity/warped_toad.png"));
        assertTrue(toad.contains("textures/entity/warped_toad_blink.png"));
        assertTrue(toad.contains("textures/entity/warped_toad_pepe.png"));
        assertTrue(toad.contains("textures/entity/warped_toad_pepe_blink.png"));
        assertTrue(glow.contains("submitModelSemanticTexture"));
        assertTrue(glow.contains("textures/entity/warped_toad_glow.png"));
        assertTrue(glow.contains("textures/entity/warped_toad_glow_blink.png"));
    }

    @Test
    void terrapinUsesRustIndexedAdmissionWithStateDrivenTextureSelection() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String terrapin = source("src/main/java/net/alexsmobs/client/render/RenderTerrapin.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelTerrapin"));
        assertTrue(terrapin.contains("renderState.turtleType = entity.getTurtleType()"));
        assertTrue(terrapin.contains("renderState.isKoopa = entity.isKoopa()"));
        assertTrue(terrapin.contains("TerrapinTypes.KOOPA.getTexture()"));
        assertTrue(terrapin.contains("return renderState.turtleType.getTexture()"));
    }

    @Test
    void toucanUsesRustIndexedAdmissionWithVariantAndSemanticGlint() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String toucan = source("src/main/java/net/alexsmobs/client/render/RenderToucan.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelToucan"));
        assertTrue(toucan.contains("textures/entity/toucan/toucan_0.png"));
        assertTrue(toucan.contains("textures/entity/toucan/toucan_3.png"));
        assertTrue(toucan.contains("textures/entity/toucan/toucan_gold.png"));
        assertTrue(toucan.contains("textures/entity/toucan/toucan_sam.png"));
        assertTrue(toucan.contains("submitModelSemanticTexture"));
        assertTrue(toucan.contains("state.isEnchanted"));
    }

    @Test
    void leafcutterAntUsesRustIndexedAdmissionForWorkerQueenAndSemanticLeafLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String ant = source("src/main/java/net/alexsmobs/client/render/RenderLeafcutterAnt.java");
        String leaf = source("src/main/java/net/alexsmobs/client/render/layer/LayerLeafcutterAntLeaf.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelLeafcutterAnt"));
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelLeafcutterAntQueen"));
        assertTrue(ant.contains("model = state.isQueen ? modelQueen : modelAnt"));
        assertTrue(ant.contains("textures/entity/leafcutter_ant.png"));
        assertTrue(ant.contains("textures/entity/leafcutter_ant_queen_angry.png"));
        assertTrue(leaf.contains("submitModelSemanticTexture"));
    }

    @Test
    void platypusUsesRustIndexedAdmissionWithPerryAndSemanticFedora() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String platypus = source("src/main/java/net/alexsmobs/client/render/RenderPlatypus.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelPlatypus"));
        assertTrue(platypus.contains("textures/entity/platypus.png"));
        assertTrue(platypus.contains("textures/entity/platypus_perry.png"));
        assertTrue(platypus.contains("textures/entity/platypus_fedora.png"));
        assertTrue(platypus.contains("state.hasFedora"));
        assertTrue(platypus.contains("submitModelSemanticTexture"));
    }

    @Test
    void cosmawUsesRustIndexedAdmissionWithSemanticGlowAndHeldItem() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String cosmaw = source("src/main/java/net/alexsmobs/client/render/RenderCosmaw.java");
        String glow = source("src/main/java/net/alexsmobs/client/render/layer/LayerBasicGlow.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelCosmaw"));
        assertTrue(cosmaw.contains("textures/entity/cosmaw.png"));
        assertTrue(cosmaw.contains("textures/entity/cosmaw_glow.png"));
        assertTrue(cosmaw.contains("state.mainHandItem.submit"));
        assertTrue(glow.contains("submitModelSemanticTexture"));
    }

    @Test
    void endergradeUsesRustIndexedAdmissionWithTranslucentBodyAndSemanticSaddle() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String endergrade = source("src/main/java/net/alexsmobs/client/render/RenderEndergrade.java");
        String saddle = source("src/main/java/net/alexsmobs/client/render/layer/LayerEndergradeSaddle.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelEndergrade"));
        assertTrue(endergrade.contains("textures/entity/endergrade.png"));
        assertTrue(endergrade.contains("RenderType.entityTranslucent"));
        assertTrue(endergrade.contains("state.isSaddled = entity.isSaddled()"));
        assertTrue(saddle.contains("textures/entity/endergrade_saddle.png"));
        assertTrue(saddle.contains("submitModelSemanticTexture"));
    }

    @Test
    void tasmanianDevilUsesRustIndexedAdmissionWithAnimationDrivenAngryTexture() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String devil = source("src/main/java/net/alexsmobs/client/render/RenderTasmanianDevil.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelTasmanianDevil"));
        assertTrue(devil.contains("textures/entity/tasmanian_devil.png"));
        assertTrue(devil.contains("textures/entity/tasmanian_devil_angry.png"));
        assertTrue(devil.contains("EntityTasmanianDevil.ANIMATION_HOWL"));
        assertTrue(devil.contains("renderState.animationTick < 34"));
    }

    @Test
    void blueJayUsesRustIndexedAdmissionWithSemanticShinyLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String blueJay = source("src/main/java/net/alexsmobs/client/render/RenderBlueJay.java");
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/RenderLayer.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelBlueJay"));
        assertTrue(blueJay.contains("textures/entity/blue_jay.png"));
        assertTrue(blueJay.contains("textures/entity/blue_jay_shiny.png"));
        assertTrue(blueJay.contains("coloredCutoutModelCopyLayerRender"));
        assertTrue(layer.contains("submitModelSemanticTexture"));
    }

    @Test
    void underminerUsesRustIndexedDwarfAdmissionWithSemanticTransparencyAndItemLayers() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String wrapper = source("src/main/java/net/alexsmobs/client/model/ModelUnderminerWrapper.java");
        String transparency = source("src/main/java/net/alexsmobs/client/render/layer/LayerUnderminerTransparency.java");
        String item = source("src/main/java/net/alexsmobs/client/render/layer/LayerUnderminerItem.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelUnderminerDwarf"));
        assertTrue(wrapper.contains("getDwarfModel()"));
        assertTrue(wrapper.contains("getTallModel()"));
        assertTrue(transparency.contains("submitModelSemanticTexture"));
        assertTrue(transparency.contains("getDwarfModel()"));
        assertTrue(transparency.contains("getTallModel()"));
        assertTrue(item.contains("renderState.getMainHandItem().submit"));
    }

    @Test
    void everyReachableAlexModelHasAnExplicitRustAdmission() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        Path modelRoot = ROOT.resolve("src/main/java/net/alexsmobs/client/model");
        try (var paths = Files.list(modelRoot)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(path) || !path.getFileName().toString().startsWith("Model")
                    || !path.getFileName().toString().endsWith(".java")) {
                    continue;
                }
                String modelName = path.getFileName().toString().replaceFirst("\\.java$", "");
                // The wrapper is intentionally not a submitted mesh: RenderUnderminer's
                // base route is null and its two semantic layers submit the concrete
                // dwarf/humanoid models independently.
                if (modelName.equals("ModelUnderminerWrapper")) continue;
                assertTrue(
                    renderer.contains("net.alexsmobs.client.model." + modelName),
                    () -> "Alex model lacks an explicit Rust mesh admission: " + modelName
                );
            }
        }
    }

    @Test
    void bisonAndTarantulaHawkBabyModelsUseRustIndexedDynamicSelection() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String bison = source("src/main/java/net/alexsmobs/client/render/RenderBison.java");
        String hawk = source("src/main/java/net/alexsmobs/client/render/RenderTarantulaHawk.java");
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelBisonBaby"));
        assertTrue(renderer.contains("model instanceof net.alexsmobs.client.model.ModelTarantulaHawkBaby"));
        assertTrue(bison.contains("this.model = state.isBaby ? modelBaby : modelBison"));
        assertTrue(hawk.contains("this.model = renderState.isBaby ? modelBaby : modelAdult"));
        assertTrue(bison.contains("textures/entity/bison_baby.png"));
        assertTrue(hawk.contains("textures/entity/tarantula_hawk_baby.png"));
    }

    @Test
    void rustTerrainExecutionReceiptsRetainSourceAnimatedSpriteIdentity() throws IOException {
        String diagnostics = source("src/main/java/net/sodium/client/render/StaticTerrainParityDiagnostics.java");

        assertTrue(diagnostics.contains(
                "MeshCoverage sourceCoverage = SOURCE_MESHES.get(new CoverageKey(sectionKey, normalizedLayer))"));
        assertTrue(diagnostics.contains(
                "sourceCoverage == null ? \"\" : sourceCoverage.sectionAnimatedSpriteIdentities()"));
    }

    @Test
    void staticTerrainParityReadinessUsesStableRenderedFramesWhenTimeIsFrozen() throws IOException {
        String diagnostics = source("src/main/java/net/sodium/client/render/StaticTerrainParityDiagnostics.java");

        assertTrue(diagnostics.contains("stableSolidFrames >= Math.max(1, requiredFrames)"));
        assertFalse(diagnostics.contains("readySolidGameFrames"));
        assertFalse(diagnostics.contains("readySolidLastGameTime"));
    }

    @Test
    void staticTerrainParityReadinessContinuesAfterBoundedReceiptOutput() throws IOException {
        String diagnostics = source("src/main/java/net/sodium/client/render/StaticTerrainParityDiagnostics.java");

        assertTrue(diagnostics.contains("maxVisibleListEvents"));
        assertTrue(diagnostics.contains("boolean writeEvent = eventIndex <= MAX_VISIBLE_LIST_EVENTS"));
        int readinessUpdate = diagnostics.indexOf("latestSolidGameTime = gameTime;");
        int receiptBudgetReturn = diagnostics.indexOf("if (!writeEvent && !writeReadyEvent)");
        assertTrue(readinessUpdate >= 0);
        assertTrue(receiptBudgetReturn > readinessUpdate);
        assertTrue(diagnostics.contains("READY_VISIBLE_LIST_EVENTS.incrementAndGet() <= MAX_READY_VISIBLE_LIST_EVENTS"));
    }

    @Test
    void staticTerrainParityCoverageUsesTheInProgressCaptureCorrelation() throws IOException {
        String diagnostics = source("src/main/java/net/sodium/client/render/StaticTerrainParityDiagnostics.java");

        assertTrue(diagnostics.contains("currentCaptureCorrelationRenderedFrameIndex()"));
        assertFalse(diagnostics.contains("currentRenderedFrameIndex()).append(\", \")"));
    }

    @Test
    void rustWholeFrameIneligibleModelSubmitsFailClosedBeforeJavaCollection() throws IOException {
        String policy = source("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java");
        String submits = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");

        assertTrue(policy.contains("!eligible && ownership.usesRustWholeFrameVulkan() ? Route.DISABLED : ownership"));
        assertTrue(submits.contains("rust-vulkan-unavailable"));
        assertTrue(submits.contains("VulkanicAPI.isVulkanBackendSelected()"));
    }

    @Test
    void rustWholeFrameMovingBlocksNeverFallThroughToJavaAfterEnqueueFailure() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/feature/BlockFeatureRenderer.java");

        assertTrue(renderer.contains("!blockDisplayRoute.usesRustWholeFrameVulkan()"));
        assertTrue(renderer.contains("\"rust-vulkan-unavailable:\" + blockSubmit.state().getBlockHolder().getRegisteredName()"));
        assertTrue(renderer.contains("if (route.usesRustWholeFrameVulkan())"));
        assertTrue(renderer.contains("\"rust-vulkan-unavailable:\" + this.blockIdentity(blockState)"));
        assertTrue(renderer.contains("&& !route.usesRustWholeFrameVulkan()"));
    }

    @Test
    void unsupportedJavaGeometryAndParticleCallbacksStayUnavailableOnRustVulkan() throws IOException {
        String submits = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String particles = source("src/main/java/net/minecraft/client/renderer/feature/ParticleFeatureRenderer.java");

        assertTrue(submits.contains("submitCustomGeometry"));
        assertTrue(submits.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
        assertTrue(submits.contains("\"custom-geometry\", \"rust-vulkan-unavailable\""));
        assertTrue(particles.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
        assertTrue(particles.contains("\"particles\", \"rust-vulkan-unavailable\""));
        assertTrue(particles.contains("return;"));
    }

    @Test
    void genericFeatureQueuesDoNotOpenJavaDrawsUnderRustWholeFrame() throws IOException {
        String blockFeatures = source("src/main/java/net/minecraft/client/renderer/feature/BlockFeatureRenderer.java");
        String dispatcher = source("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java");

        assertTrue(blockFeatures.contains("WorldRenderRoutePolicy.currentMaterialRoute()"));
        assertTrue(dispatcher.contains("currentMaterialRoute().usesJavaCompatibility()"));
        assertTrue(dispatcher.contains("this.hitboxFeatureRenderer.render"));
        assertTrue(dispatcher.contains("this.itemFeatureRenderer.render"));
    }

    @Test
	void sheepBodyAndWoolLayersUseRustIndexedModelRoutesForAdultAndBabyStates() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
		String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
		String wool = source("src/main/java/net/minecraft/client/renderer/entity/layers/SheepWoolLayer.java");
		String undercoat = source("src/main/java/net/minecraft/client/renderer/entity/layers/SheepWoolUndercoatLayer.java");
		String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
		String harness = source("DevUtils/Common/graphics_harness.py");
		String coordinator = source("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java");
		int sheepStart = renderer.indexOf("isVanillaSheepModelMeshEligible");
		int sheepEnd = renderer.indexOf("\n\tpublic static boolean", sheepStart + 1);
		String sheepContract = renderer.substring(sheepStart, sheepEnd > sheepStart ? sheepEnd : renderer.length());

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.SheepModel"));
        assertTrue(renderer.contains("isVanillaSheepModelMeshEligible"));
		assertTrue(renderer.contains("/** Vanilla sheep body geometry; wool feature layers are copied separately. */"));
		assertFalse(sheepContract.contains("!state.isBaby"));
        assertTrue(renderer.contains("textures/entity/sheep/sheep.png"));
        assertTrue(living.contains("SheepRenderState sheepRenderState"));
        assertTrue(living.contains("isVanillaSheepModelMeshEligible"));
		assertTrue(wool.contains("enqueueStandaloneModelMesh"));
		assertTrue(undercoat.contains("enqueueStandaloneModelMesh"));
		assertTrue(wool.contains("j, sheepRenderState.getWoolColor(), sheepRenderState.outlineColor"),
			"wool must preserve vanilla overlay coordinates and put wool color in the tint slot");
		assertTrue(undercoat.contains("LivingEntityRenderer.getOverlayCoords(sheepRenderState, 0.0F), sheepRenderState.getWoolColor()"),
			"undercoat must preserve vanilla overlay coordinates and put wool color in the tint slot");
		assertTrue(wool.contains("recordModelMeshRouteDecision"));
		assertTrue(undercoat.contains("recordModelMeshRouteDecision"));
		assertFalse(undercoat.contains("!sheepRenderState.isBaby"),
			"baby sheep undercoat must use the same Rust indexed-model route as adult sheep");
		assertTrue(capture.contains("sheep.setSheared(false)"));
		assertTrue(capture.contains("sheep.setColor(\"sheep-red\".equals"));
		assertTrue(capture.contains("\"sheep-red\""));
		assertTrue(capture.contains("DyeColor.RED"));
		assertTrue(capture.contains("redSheepFixtureJson"));
		assertTrue(capture.contains("sheepFixtureJson"));
		assertTrue(capture.contains("rustGalWorldModelCompositionExecution"));
		assertTrue(renderer.contains("recordWholeFrameModelCompositionExecution"));
		assertTrue(renderer.contains("countsByComposition.get(composition)"));
		assertTrue(renderer.contains("byEntity.getOrDefault(instance.entityId()"));
		assertTrue(coordinator.contains("recordWholeFrameModelCompositionExecution"));
		assertTrue(harness.contains("f\"missing_{prefix}_{layer_name}_route_or_mesh\""));
		assertTrue(harness.contains("f\"missing_{prefix}_composition_execution\""));
		assertTrue(harness.contains("minecraft:textures/entity/sheep/sheep_wool.png"));
		assertTrue(harness.contains("minecraft:textures/entity/sheep/sheep_wool_undercoat.png"));
		assertTrue(harness.contains("base_undercoat_and_wool_emission_observed"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:sheep\", \"minecraft:sheep_wool\")"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:sheep\", \"minecraft:sheep_wool_undercoat\")"));
	}

	@Test
	void villagerTypeProfessionAndLevelUseDistinctRustSemanticMeshes() throws IOException {
		String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/VillagerProfessionLayer.java");
		String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
		String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
		String harness = source("DevUtils/Common/graphics_harness.py");

		assertTrue(layer.contains("string + \"_type\""));
		assertTrue(layer.contains("string + \"_profession\""));
		assertTrue(layer.contains("string + \"_profession_level\""));
		assertTrue(layer.contains("enqueueStandaloneModelMesh"));
		assertTrue(layer.contains("recordModelMeshRouteDecision"));
		assertTrue(layer.contains("rust-vulkan-unavailable"));
		assertTrue(layer.contains("throw new IllegalStateException"));
		assertTrue(layer.contains("renderColoredCutoutModel(model, texture"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:villager\", \"minecraft:villager_type\")"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:villager\", \"minecraft:villager_profession\")"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:villager\", \"minecraft:villager_profession_level\")"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:zombie_villager\", \"minecraft:zombie_villager_type\")"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:zombie_villager\", \"minecraft:zombie_villager_profession\")"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:zombie_villager\", \"minecraft:zombie_villager_profession_level\")"));
		assertTrue(capture.contains("VillagerType.PLAINS"));
		assertTrue(capture.contains("VillagerProfession.FARMER"));
		assertTrue(capture.contains("withLevel(5)"));
		assertTrue(capture.contains("villagerFixtureJson"));
		assertTrue(capture.contains("zombieVillagerFixtureJson"));
		assertTrue(harness.contains("frozen_villager_emission_evidence"));
		assertTrue(harness.contains("frozen_zombie_villager_emission_evidence"));
		assertTrue(harness.contains("base_type_profession_and_level_emission_observed"));
		assertTrue(harness.contains("minecraft:villager_profession_level"));
		assertTrue(harness.contains("minecraft:zombie_villager_profession_level"));
	}

	@Test
	void emissiveEntityLayersUseExplicitRustMaterialAndSemanticIdentities() throws IOException {
		String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/LivingEntityEmissiveLayer.java");
		String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
		String registry = source("src/main/rust/render/vulkanic/world_primitive_frontend/material_registry.rs");
		String frontend = source("src/main/rust/render/vulkanic/world_primitive_frontend.rs");
		String shaders = source("src/main/rust/render/vulkanic/shader_pack/programs.rs");
		String warden = source("src/main/java/net/minecraft/client/renderer/entity/WardenRenderer.java");
		String copperGolem = source("src/main/java/net/minecraft/client/renderer/entity/CopperGolemRenderer.java");
		String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
		String harness = source("DevUtils/Common/graphics_harness.py");

		assertTrue(layer.contains("enqueueStandaloneTranslucentModelMesh"));
		assertTrue(layer.contains("this.semanticIdentity"));
		assertTrue(layer.contains("rust-vulkan-unavailable"));
		assertTrue(layer.contains("throw new IllegalStateException"));
		assertTrue(renderer.contains("RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE"));
		assertTrue(renderer.contains("MATERIAL_ID_MODEL_TRANSLUCENT_EMISSIVE"));
		assertTrue(registry.contains("WORLD_MATERIAL_ID_MODEL_TRANSLUCENT_EMISSIVE"));
		assertTrue(registry.contains("fullbright_with_cardinal_lighting"));
		assertTrue(frontend.contains("{ 512 }"));
		assertTrue(shaders.contains("(material_semantics & (128u | 512u)) != 0u"));
		for (String identity : new String[] {"warden_bioluminescent", "warden_pulsating_spots_1",
			"warden_pulsating_spots_2", "warden_tendrils", "warden_heart"}) {
			assertTrue(warden.contains(identity), identity);
		}
		assertTrue(copperGolem.contains("copper_golem_eyes"));
		assertTrue(capture.contains("copper-golem"));
		assertTrue(capture.contains("WeatheringCopper.WeatherState.UNAFFECTED"));
		assertTrue(capture.contains("copperGolemFixtureJson"));
		assertTrue(harness.contains("frozen_copper_golem_emission_evidence"));
		assertTrue(harness.contains("base_and_emissive_eyes_emission_observed"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:copper_golem\", \"minecraft:copper_golem_eyes\")"));
		assertTrue(capture.contains("wardenFixtureJson"));
		assertTrue(capture.contains("serverLevel.broadcastEntityEvent(warden, (byte)61)"));
		assertTrue(harness.contains("frozen_warden_emission_evidence"));
		assertTrue(harness.contains("base_and_all_five_emissive_phases_observed"));
		for (String identity : new String[] {"warden_bioluminescent", "warden_pulsating_spots_1",
			"warden_pulsating_spots_2", "warden_tendrils", "warden_heart"}) {
			assertTrue(renderer.contains("new ModelComposition(\"minecraft:warden\", \"minecraft:" + identity + "\")"), identity);
		}
	}

	@Test
	void tropicalFishPatternFixtureAndLayerStayOnRustSemanticMeshes() throws IOException {
		String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
		String harness = source("DevUtils/Common/graphics_harness.py");
		String pattern = source("src/main/java/net/minecraft/client/renderer/entity/layers/TropicalFishPatternLayer.java");
		String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
		String coordinator = source("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java");
		assertTrue(capture.contains("tropical-fish"));
		assertTrue(capture.contains("EntityType.TROPICAL_FISH"));
		assertTrue(capture.contains("configureTropicalFishFixture"));
		assertTrue(capture.contains("TROPICAL_FISH_PATTERN, TropicalFish.Pattern.KOB"));
		assertTrue(capture.contains("TROPICAL_FISH_BASE_COLOR, DyeColor.ORANGE"));
		assertTrue(capture.contains("TROPICAL_FISH_PATTERN_COLOR, DyeColor.WHITE"));
		assertTrue(capture.contains("tropicalFishFixtureJson"));
		assertTrue(harness.contains("\"tropical-fish\""));
		assertTrue(harness.contains("f\"missing_{prefix}_{layer_name}_route_or_mesh\""));
		assertTrue(harness.contains("f\"missing_{prefix}_composition_execution\""));
		assertTrue(harness.contains("minecraft:textures/entity/fish/tropical_a_pattern_1.png"));
		assertTrue(pattern.contains("isVanillaTropicalFishPatternModelMeshEligible"));
		assertTrue(pattern.contains("tropical_fish_pattern"));
		assertTrue(renderer.contains("new ModelComposition(\"minecraft:tropical_fish\", \"minecraft:tropical_fish_pattern\")"));
		assertTrue(renderer.contains("instance.entityId()"));
		assertTrue(coordinator.contains("recordWholeFrameModelCompositionExecution"));
	}

    @Test
    void ordinaryCreepersUseRustMeshOnlyWithoutPoweredFeatureLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.CreeperModel"));
        assertTrue(renderer.contains("isVanillaCreeperModelMeshEligible"));
        assertTrue(renderer.contains("&& !state.isPowered"));
        assertTrue(renderer.contains("&& state.swelling <= 0.0F"));
        assertTrue(renderer.contains("textures/entity/creeper/creeper.png"));
        assertTrue(living.contains("CreeperRenderState creeperRenderState"));
        assertTrue(living.contains("isVanillaCreeperModelMeshEligible"));
    }

    @Test
    void slimeInnerAndTranslucentOuterLayersUseSeparateRustMeshes() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String outer = source("src/main/java/net/minecraft/client/renderer/entity/layers/SlimeOuterLayer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.SlimeModel"));
        assertTrue(renderer.contains("isVanillaSlimeModelMeshEligible"));
        assertTrue(renderer.contains("textures/entity/slime/slime.png"));
        assertTrue(living.contains("SlimeRenderState slimeRenderState"));
        assertTrue(living.contains("isVanillaSlimeModelMeshEligible"));
        assertTrue(outer.contains("RenderType.entityTranslucent(SlimeRenderer.SLIME_LOCATION)"));
        assertTrue(outer.contains("enqueueStandaloneModelMesh"));
    }

    @Test
    void simpleEndermiteAndSilverfishBodiesUseRustIndexedModelRoutes() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.EndermiteModel"));
        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.SilverfishModel"));
        assertTrue(renderer.contains("isVanillaEndermiteModelMeshEligible"));
        assertTrue(renderer.contains("isVanillaSilverfishModelMeshEligible"));
        assertTrue(renderer.contains("textures/entity/endermite.png"));
        assertTrue(renderer.contains("textures/entity/silverfish.png"));
        assertTrue(living.contains("EndermiteModel.class"));
        assertTrue(living.contains("SilverfishModel.class"));
        assertTrue(living.contains("isVanillaEndermiteModelMeshEligible"));
        assertTrue(living.contains("isVanillaSilverfishModelMeshEligible"));
    }

    @Test
    void vanillaBatBodyUsesRustIndexedModelRoute() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.BatModel"));
        assertTrue(renderer.contains("isVanillaBatModelMeshEligible"));
        assertTrue(renderer.contains("textures/entity/bat.png"));
        assertTrue(living.contains("BatRenderState batRenderState"));
        assertTrue(living.contains("isVanillaBatModelMeshEligible"));
    }

    @Test
    void oversizedGuiItemsUseRustSemanticPipRouteWithoutJavaOffscreenPreparation() throws IOException {
        String gui = source("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java");
        String game = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");

        assertTrue(gui.contains("collectRustGalPictureInPictureSemantics"));
        assertTrue(gui.contains("instanceof OversizedItemRenderState oversized"));
        assertTrue(gui.contains("item.itemStackRenderState().hasSpecialRenderer()"));
        assertTrue(gui.contains("tryEnqueueSpecialItem(\n\t\t\t\t\titem, guiWidth, guiHeight, dynamicLayerOrder"));
        assertTrue(gui.contains("tryEnqueueStandard3dItem(item"));
        assertTrue(gui.contains("tryEnqueueFlatItem(item"));
        assertTrue(game.contains("this.guiRenderer.collectRustGalPictureInPictureSemantics()"));
    }

    @Test
    void animatedGuiItemsUseStableRustOwnedFrameAssets() throws IOException {
        String item = source("src/main/java/net/vulkanic/gui/RustGalGuiItemRenderer.java");
        String assets = source("src/main/java/net/vulkanic/gui/RustGalGuiRawImageAssets.java");
        String mesh = source("src/main/java/net/vulkanic/gui/GuiItemMeshSemanticCollector.java");

        assertTrue(item.contains("resolveAnimatedSprite(sprite)"));
        assertTrue(item.contains("stageSemanticImage(TextureAtlasSprite sprite)"));
        assertTrue(assets.contains("semanticFrameIndex()"));
        assertTrue(assets.contains("animated-sprite:"));
        assertTrue(assets.contains("frame-independent"));
        assertTrue(mesh.contains("resolveAnimatedSprite(sprite)")
            || mesh.contains("RustGalGuiRawImageAssets.resolve(spriteIdentity)"));
        assertTrue(!item.contains("recordDiagnostic(\"animated-item\")"));
    }

    @Test
    void mungusBeamUsesSemanticTexturedQuadsOnRustWholeFrame() throws IOException {
        String beam = source("src/main/java/net/alexsmobs/client/render/layer/MungusBeamLayer.java");
        assertTrue(beam.contains("currentTexturedBillboardRoute().usesRustWholeFrameVulkan()"));
        assertTrue(beam.contains("submitNodeCollector.submitTranslucentTexturedQuadSemantic"));
        assertTrue(beam.contains("new float[] {f19, f4, f20"));
        assertTrue(beam.contains("new float[] {f11, f4, f12"));
        assertTrue(beam.contains("Rust whole-frame Mungus beam route rejected"));
    }

    @Test
    void codAndSalmonVariantsUseRustIndexedModelRoutes() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.CodModel"));
        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.SalmonModel"));
        assertTrue(renderer.contains("isVanillaCodModelMeshEligible"));
        assertTrue(renderer.contains("isVanillaSalmonModelMeshEligible"));
        assertTrue(renderer.contains("textures/entity/fish/cod.png"));
        assertTrue(renderer.contains("textures/entity/fish/salmon.png"));
        assertTrue(renderer.contains("&& state.variant != null"));
        assertTrue(living.contains("SalmonRenderState salmonRenderState"));
        assertTrue(living.contains("isVanillaCodModelMeshEligible"));
        assertTrue(living.contains("isVanillaSalmonModelMeshEligible"));
    }

    @Test
    void pufferfishVariantsAndTadpolesUseRustIndexedModelRoutes() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.PufferfishBigModel"));
        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.PufferfishMidModel"));
        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.PufferfishSmallModel"));
        assertTrue(renderer.contains("isVanillaPufferfishModelMeshEligible"));
        assertTrue(renderer.contains("isVanillaTadpoleModelMeshEligible"));
        assertTrue(renderer.contains("textures/entity/fish/pufferfish.png"));
        assertTrue(renderer.contains("textures/entity/tadpole/tadpole.png"));
        assertTrue(living.contains("PufferfishRenderState pufferfishRenderState"));
        assertTrue(living.contains("isVanillaTadpoleModelMeshEligible"));
    }

    @Test
	void ocelotsUseRustIndexedModelRouteForAdultAndBabyStates() throws IOException {
		String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
		String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
		int ocelotStart = renderer.indexOf("isVanillaOcelotModelMeshEligible");
		int ocelotEnd = renderer.indexOf("\n\tpublic static boolean", ocelotStart + 1);
		String ocelotContract = renderer.substring(ocelotStart, ocelotEnd > ocelotStart ? ocelotEnd : renderer.length());

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.OcelotModel"));
        assertTrue(renderer.contains("isVanillaOcelotModelMeshEligible"));
		assertFalse(ocelotContract.contains("!state.isBaby"));
        assertTrue(renderer.contains("textures/entity/cat/ocelot.png"));
        assertTrue(living.contains("FelineRenderState felineRenderState"));
        assertTrue(living.contains("isVanillaOcelotModelMeshEligible"));
    }

    @Test
    void adultGoatsUseRustIndexedModelRoute() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.GoatModel"));
        assertTrue(renderer.contains("isVanillaGoatModelMeshEligible"));
        assertTrue(renderer.contains("&& !state.isBaby"));
        assertTrue(renderer.contains("textures/entity/goat/goat.png"));
        assertTrue(living.contains("GoatRenderState goatRenderState"));
        assertTrue(living.contains("isVanillaGoatModelMeshEligible"));
    }

    @Test
    void profilerChartPipUsesBoundedExplicitRustGuiMesh() throws IOException {
        String gui = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
        String renderer = source("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java");

        assertTrue(gui.contains("tryEnqueueProfilerChart"));
        assertTrue(gui.contains("chart.chartData().size() > 128"));
        assertTrue(gui.contains("GuiMeshBatchRecord"));
        assertTrue(gui.contains("addProfilerTriangle"));
        assertTrue(gui.contains("addProfilerBar"));
        assertTrue(gui.contains("ScreenRectangle scissor = chart.scissorArea()"));
        assertTrue(gui.contains("scissor == null ? 0 : 1"));
        assertTrue(renderer.contains("GuiProfilerChartRenderState chart"));
        assertTrue(renderer.contains("RustGalGuiRenderer.tryEnqueueProfilerChart"));
    }

    @Test
    void gradientRectanglesPreserveScissorThroughRustGuiMeshClip() throws IOException {
        String gui = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
        assertTrue(gui.contains("tryEnqueueVerticalGradientRectangle"));
        assertTrue(gui.contains("ScreenRectangle scissor = rectangle.scissorArea()"));
        assertTrue(gui.contains("scissor == null ? 0 : 1"));
        assertTrue(gui.contains("scissor.width() > guiWidth - scissor.left()"));
    }

    @Test
    void modelBackedGuiPipsUseCopiedRustMeshSemantics() throws IOException {
        String gui = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
        String collector = source("src/main/java/net/vulkanic/gui/GuiModelPipSemanticCollector.java");
        String renderer = source("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java");
        assertTrue(gui.contains("tryEnqueueModelPip"));
        assertTrue(gui.contains("GuiModelPipSemanticCollector.collect"));
        assertTrue(collector.contains("model.renderToBuffer"));
        assertTrue(collector.contains("GuiMeshBatchRecord"));
        assertTrue(collector.contains("List<RustGalGuiRawImageAssets.Asset> assets")
            && collector.contains("List.of(asset)"));
        assertTrue(collector.contains("clipMode = clip == null ? 0 : 1"));
        assertTrue(renderer.contains("GuiSkinRenderState skin"));
        assertTrue(renderer.contains("GuiBookModelRenderState book"));
        assertTrue(renderer.contains("GuiSignRenderState sign"));
        assertTrue(renderer.contains("RustGalGuiRenderer.tryEnqueueModelPip"));
    }

    @Test
    void bannerGuiPipCopiesBaseAndPatternLayersToRustMeshes() throws IOException {
        String gui = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
        String renderer = source("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java");
        assertTrue(gui.contains("tryEnqueueBannerPip"));
        assertTrue(gui.contains("BANNER_BASE.texture()"));
        assertTrue(gui.contains("getBannerMaterial(layer.pattern())"));
		assertTrue(gui.contains("patternCount > 16"));
		assertTrue(gui.contains("banner-pip-rejected=pattern-cap-"));
        assertTrue(gui.contains("layer.color().getTextureDiffuseColor()"));
        assertTrue(renderer.contains("GuiBannerResultRenderState banner"));
        assertTrue(renderer.contains("RustGalGuiRenderer.tryEnqueueBannerPip"));
        assertTrue(renderer.contains("rustOwnedPictureInPictureStates.add(pictureInPictureRenderState)"));
    }

    @Test
    void livingEntityGuiPipUsesRendererModelAndTextureSemantics() throws IOException {
        String gui = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
        String renderer = source("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        assertTrue(gui.contains("tryEnqueueEntityPip"));
        assertTrue(gui.contains("getEntityRenderDispatcher()"));
        assertTrue(gui.contains("getTextureLocation"));
        assertTrue(gui.contains("living.getModelForSemanticState(livingState)"));
        assertTrue(gui.contains("layerModel.setupAndObserve(pose.last())"));
        assertTrue(renderer.contains("GuiEntityRenderState entityPip"));
        assertTrue(renderer.contains("RustGalGuiRenderer.tryEnqueueEntityPip"));
        assertTrue(living.contains("applySemanticModelPose"));
        assertTrue(gui.contains("this.setupModel.run()"));
        assertTrue(gui.contains("this.observeModel.accept(sourcePose)"));
    }

    @Test
    void rustOwnedGuiPipsNeverPrepareJavaOffscreenTargets() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java");
        assertTrue(renderer.contains("rustOwnedPictureInPictureStates"));
        assertTrue(renderer.contains("rustOwnedPictureInPictureStates.contains(pictureInPictureRenderState)"));
        assertTrue(renderer.contains("this.rustOwnedPictureInPictureStates.add(pictureInPictureRenderState)"));
        assertTrue(renderer.contains("this.rustOwnedStandard3dItems.add(item)"));
        assertTrue(renderer.contains("!RustGalGuiRenderer.isWholeFrameVulkanActive()"));
    }

    @Test
    void tropicalFishBaseAndPatternModelsUseRustIndexedRoutes() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/TropicalFishPatternLayer.java");

        assertTrue(renderer.contains("TropicalFishModelA"));
        assertTrue(renderer.contains("TropicalFishModelB"));
        assertTrue(renderer.contains("isVanillaTropicalFishModelMeshEligible"));
        assertTrue(renderer.contains("vanillaTropicalFishPatternTextureIdentity"));
        assertTrue(living.contains("TropicalFishRenderState tropicalFishRenderState"));
        assertTrue(layer.contains("enqueueStandaloneModelMesh"));
        assertTrue(layer.contains("isVanillaTropicalFishPatternModelMeshEligible"));
        assertTrue(layer.contains("tropicalFishRenderState.patternColor"));
        assertTrue(layer.contains("rust-vulkan-unavailable"));
        assertTrue(layer.contains("return;"));
    }

    @Test
    void adultPolarBearsUseRustIndexedModelRoute() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.PolarBearModel"));
        assertTrue(renderer.contains("isVanillaPolarBearModelMeshEligible"));
        assertTrue(renderer.contains("textures/entity/bear/polarbear.png"));
        assertTrue(living.contains("PolarBearRenderState polarBearRenderState"));
        assertTrue(living.contains("isVanillaPolarBearModelMeshEligible"));
    }

    @Test
	void dolphinsUseRustIndexedBodyRouteForAdultAndBabyStatesWithSeparateHeldItemLayer() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/DolphinCarryingItemLayer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.DolphinModel"));
        assertTrue(renderer.contains("isVanillaDolphinModelMeshEligible"));
        assertTrue(renderer.contains("textures/entity/dolphin.png"));
        assertTrue(living.contains("DolphinRenderState dolphinRenderState"));
        assertTrue(living.contains("isVanillaDolphinModelMeshEligible"));
        assertTrue(layer.contains("dolphinRenderState.heldItem"));
    }

    @Test
    void adultTurtlesUseRustIndexedModelRoute() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.TurtleModel"));
        assertTrue(renderer.contains("isVanillaTurtleModelMeshEligible"));
        assertTrue(renderer.contains("textures/entity/turtle/big_sea_turtle.png"));
        assertTrue(living.contains("TurtleRenderState turtleRenderState"));
        assertTrue(living.contains("isVanillaTurtleModelMeshEligible"));
    }

    @Test
    void adultPandasUseGeneTextureRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");

        assertTrue(renderer.contains("model instanceof net.minecraft.client.model.PandaModel"));
        assertTrue(renderer.contains("isVanillaPandaModelMeshEligible"));
        assertTrue(renderer.contains("vanillaPandaTextureIdentity"));
        assertTrue(renderer.contains("textures/entity/panda/aggressive_panda.png"));
        assertTrue(living.contains("PandaRenderState pandaRenderState"));
        assertTrue(living.contains("isVanillaPandaModelMeshEligible"));
    }

    @Test
    void adultBeesUseAngerAndNectarTextureRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("BeeModel.class"));
        assertTrue(renderer.contains("BeeRenderState beeRenderState"));
        assertTrue(renderer.contains("isVanillaBeeModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.BeeModel.class"));
        assertTrue(rust.contains("bee/bee_angry_nectar.png"));
        assertTrue(rust.contains("bee/bee_nectar.png"));
    }

    @Test
    void adultAxolotlsUseVariantTextureRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("AxolotlModel.class"));
        assertTrue(renderer.contains("AxolotlRenderState axolotlRenderState"));
        assertTrue(renderer.contains("isVanillaAxolotlModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.AxolotlModel.class"));
        assertTrue(rust.contains("axolotl/axolotl_lucy.png"));
        assertTrue(rust.contains("axolotl/axolotl_blue.png"));
    }

    @Test
    void frogsUseSemanticVariantTextureRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("FrogModel.class"));
        assertTrue(renderer.contains("FrogRenderState frogRenderState"));
        assertTrue(renderer.contains("isVanillaFrogModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.FrogModel.class"));
        assertTrue(rust.contains("Objects.equals(textureIdentity, state.texture)"));
    }

    @Test
    void squidsUseExactOrdinaryOrGlowTextureRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("SquidModel.class"));
        assertTrue(renderer.contains("SquidRenderState squidRenderState"));
        assertTrue(renderer.contains("isVanillaSquidModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.SquidModel.class"));
        assertTrue(rust.contains("textures/entity/squid/squid.png"));
        assertTrue(rust.contains("textures/entity/squid/glow_squid.png"));
    }

    @Test
    void guardiansUseExactOrdinaryOrElderTextureRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("GuardianModel.class"));
        assertTrue(renderer.contains("GuardianRenderState guardianRenderState"));
        assertTrue(renderer.contains("isVanillaGuardianModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.GuardianModel.class"));
        assertTrue(rust.contains("textures/entity/guardian.png"));
        assertTrue(rust.contains("textures/entity/guardian_elder.png"));
    }

    @Test
    void spidersUseExactOrdinaryOrCaveTextureRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String submit = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");

        assertTrue(renderer.contains("SpiderModel.class"));
        assertTrue(renderer.contains("LivingEntityRenderState spiderRenderState"));
        assertTrue(renderer.contains("isVanillaSpiderModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.SpiderModel.class"));
        assertTrue(rust.contains("textures/entity/spider/spider.png"));
        assertTrue(rust.contains("textures/entity/spider/cave_spider.png"));
        assertTrue(rust.contains("enqueueStandaloneTranslucentModelMesh"));
        assertTrue(submit.contains("textures/entity/spider_eyes.png"));
        assertTrue(submit.contains("model instanceof net.minecraft.client.model.SpiderModel"));
    }

    @Test
    void snowGolemsUseRustBodyRouteWithSeparatePumpkinFeature() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");

        assertTrue(renderer.contains("SnowGolemModel.class"));
        assertTrue(renderer.contains("SnowGolemRenderState snowGolemRenderState"));
        assertTrue(renderer.contains("isVanillaSnowGolemModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.SnowGolemModel.class"));
        assertTrue(rust.contains("textures/entity/snow_golem.png"));
        assertTrue(capture.contains("private static String snowGolemFixtureJson()"));
        assertTrue(capture.contains("!golem.isInvisible() && golem.hasPumpkin()"));
        assertTrue(capture.contains("rustGalWorldBlockAttachedCompositionExecution"));
        assertTrue(rust.contains("BlockAttachedCompositionExecutionDiagnostic"));
        assertTrue(rust.contains("\"minecraft:snow_golem\", \"minecraft:carved_pumpkin\""));
        assertTrue(rust.contains("instance.entityId() == body.entityId()"));
        assertTrue(rust.contains("bodyInstances, blockInstances"));
    }

	@Test
	void itemFrameBackingUsesIdentifiedSyntheticBlockModelRoute() throws IOException {
		String renderer = source("src/main/java/net/minecraft/client/renderer/entity/ItemFrameRenderer.java");
		String mapRenderer = source("src/main/java/net/minecraft/client/renderer/MapRenderer.java");
		String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
		String storage = source("src/main/java/net/minecraft/client/renderer/SubmitNodeStorage.java");
		String state = source("src/main/java/net/minecraft/client/renderer/entity/state/ItemFrameRenderState.java");
		String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
		String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");

		assertTrue(renderer.contains("submitBlockModelSemantic("));
		assertTrue(renderer.contains("itemFrameRenderState.isGlowFrame ? \"glow_item_frame\" : \"item_frame\""));
		assertTrue(renderer.contains("beginItemFrameItemSubmission("));
		assertTrue(renderer.contains("BuiltInRegistries.ITEM.getKey(itemStack.getItem())"));
		assertTrue(state.contains("ResourceLocation itemIdentity"));
		assertFalse(renderer.contains("submitBlockDisplaySemantic("));
		assertTrue(storage.contains("ResourceLocation semanticIdentity"));
		assertTrue(collector.contains("semanticIdentity"));
		assertTrue(rust.contains("recordBlockModelDiagnostic(submit.semanticIdentity()"));
		assertTrue(rust.contains("BlockModelExecutionDiagnostic"));
		assertTrue(rust.contains("PendingMeshProducer.ITEM_FRAME_ITEM"));
		assertTrue(rust.contains("ItemFrameItemExecutionDiagnostic"));
		assertTrue(renderer.contains("beginItemFrameMapSubmission("));
		assertTrue(rust.contains("ItemFrameMapExecutionDiagnostic"));
		assertTrue(rust.contains("recordWholeFrameItemFrameMapExecution("));
		assertTrue(capture.contains("case \"item-frame\", \"item-frame-invisible\", \"glow-item-frame\", \"glow-item-frame-invisible\", \"item-frame-item\", \"item-frame-map\", \"item-frame-item-rotated\", \"item-frame-map-rotated\", \"item-frame-map-decorated\", \"item-frame-item-invisible\", \"glow-item-frame-item-invisible\", \"glow-item-frame-item\", \"glow-item-frame-map\", \"glow-item-frame-item-rotated\", \"glow-item-frame-map-rotated\", \"item-frame-map-invisible\", \"glow-item-frame-map-invisible\" ->"));
		assertTrue(capture.contains("new ItemFrame(serverLevel, framePos, facing)"));
		assertTrue(capture.contains("new GlowItemFrame(serverLevel, framePos, facing)"));
		assertTrue(capture.contains("case \"glow-item-frame\", \"glow-item-frame-invisible\" -> EntityType.GLOW_ITEM_FRAME"));
		assertTrue(capture.contains("LightTexture.block(diagnostic.lightCoords()) >= 5"));
		assertTrue(capture.contains("\"minecraft:diamond\".equals(diagnostic.semanticIdentity())"));
		assertTrue(capture.contains("MapItem.create(serverLevel"));
		assertTrue(capture.contains("rustGalWorldItemFrameMapExecutions"));
		assertTrue(renderer.contains("itemFrameRenderState.rotation"));
		assertTrue(rust.contains("diagnostic.itemFrameRotation()"));
		assertTrue(rust.contains("diagnostic.rotation()"));
		assertTrue(capture.contains("expectedItemFrameRotation()"));
		assertTrue(renderer.contains("itemFrameRenderState.isInvisible ? 0.5F : 0.4375F"));
		assertTrue(rust.contains("diagnostic.itemFrameInvisible()"));
		assertTrue(rust.contains("diagnostic.itemFrameContentOffset()"));
		assertTrue(rust.contains("diagnostic.invisibleFrame()"));
		assertTrue(rust.contains("diagnostic.contentOffset()"));
		assertTrue(mapRenderer.contains("beginItemFrameMapDecorationSubmission("));
		assertTrue(rust.contains("ItemFrameMapDecorationExecutionDiagnostic"));
		assertTrue(capture.contains("minecraft:red_x"));
		assertTrue(capture.contains("itemFrameFixtureJson"));
		assertTrue(capture.contains("rustGalWorldBlockModels"));
	}

    @Test
    void ironGolemsUseRustBodyRouteWithSeparateFeatureLayers() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("IronGolemModel.class"));
        assertTrue(renderer.contains("IronGolemRenderState ironGolemRenderState"));
        assertTrue(renderer.contains("isVanillaIronGolemModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.IronGolemModel.class"));
        assertTrue(rust.contains("textures/entity/iron_golem/iron_golem.png"));
    }

    @Test
    void ravagersUseSelfContainedRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("RavagerModel.class"));
        assertTrue(renderer.contains("RavagerRenderState ravagerRenderState"));
        assertTrue(renderer.contains("isVanillaRavagerModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.RavagerModel.class"));
        assertTrue(rust.contains("textures/entity/illager/ravager.png"));
    }

    @Test
    void vexesUseChargingTextureRustBodyRouteWithSeparateHeldItemLayer() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("VexModel.class"));
        assertTrue(renderer.contains("VexRenderState vexRenderState"));
        assertTrue(renderer.contains("isVanillaVexModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.VexModel.class"));
        assertTrue(rust.contains("textures/entity/illager/vex_charging.png"));
    }

    @Test
    void allaysUseRustBodyRouteWithSeparateHeldItemLayer() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("AllayModel.class"));
        assertTrue(renderer.contains("AllayRenderState allayRenderState"));
        assertTrue(renderer.contains("isVanillaAllayModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.AllayModel.class"));
        assertTrue(rust.contains("textures/entity/allay/allay.png"));
    }

    @Test
    void witchesUseRustBodyRouteWithSeparateHeldItemLayer() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("WitchModel.class"));
        assertTrue(renderer.contains("WitchRenderState witchRenderState"));
        assertTrue(renderer.contains("isVanillaWitchModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.WitchModel.class"));
        assertTrue(rust.contains("textures/entity/witch.png"));
    }

    @Test
    void foxesUseExactAdultVariantAndSleepTextureRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("FoxModel.class"));
        assertTrue(renderer.contains("FoxRenderState foxRenderState"));
        assertTrue(renderer.contains("isVanillaFoxModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.FoxModel.class"));
        assertTrue(rust.contains("textures/entity/fox/fox_sleep.png"));
        assertTrue(rust.contains("textures/entity/fox/snow_fox_sleep.png"));
    }

    @Test
    void collaredCatsComposeWithTheRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        int catStart = rust.indexOf("isVanillaCatModelMeshEligible");
        int catEnd = rust.indexOf("\n\tpublic static boolean", catStart + 1);
        String catContract = rust.substring(catStart, catEnd > catStart ? catEnd : rust.length());

        assertTrue(renderer.contains("CatModel.class"));
        assertTrue(renderer.contains("CatRenderState catRenderState"));
        assertTrue(renderer.contains("isVanillaCatModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.CatModel.class"));
        assertTrue(source("src/main/java/net/minecraft/client/renderer/entity/layers/RenderLayer.java").contains("submitModelSemanticTexture"));
        assertTrue(rust.contains("textures/entity/cat/"));
        assertFalse(catContract.contains("!state.isBaby"));
    }

    @Test
    void armoredOrCollaredWolvesComposeWithTheRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("WolfModel.class"));
        assertTrue(renderer.contains("WolfRenderState wolfRenderState"));
        assertTrue(renderer.contains("isVanillaWolfModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.WolfModel.class"));
        assertTrue(source("src/main/java/net/minecraft/client/renderer/entity/layers/WolfCollarLayer.java").contains("submitModelSemanticTexture"));
        assertTrue(source("src/main/java/net/minecraft/client/renderer/entity/layers/WolfArmorLayer.java").contains("submitModelSemanticTexture"));
        assertTrue(rust.contains("textures/entity/wolf/"));
    }

    @Test
    void parrotsUseExactFiveVariantRustBodyTextures() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("ParrotModel.class"));
        assertTrue(renderer.contains("ParrotRenderState parrotRenderState"));
        assertTrue(renderer.contains("isVanillaParrotModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.ParrotModel.class"));
        assertTrue(rust.contains("textures/entity/parrot/parrot_red_blue.png"));
        assertTrue(rust.contains("textures/entity/parrot/parrot_yellow_blue.png"));
        assertTrue(rust.contains("textures/entity/parrot/parrot_grey.png"));
    }

    @Test
    void ghastsUseExactOrdinaryOrChargingRustBodyTextureRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("GhastModel.class"));
        assertTrue(renderer.contains("GhastRenderState ghastRenderState"));
        assertTrue(renderer.contains("isVanillaGhastModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.GhastModel.class"));
        assertTrue(rust.contains("textures/entity/ghast/ghast.png"));
        assertTrue(rust.contains("textures/entity/ghast/ghast_shooting.png"));
    }

    @Test
    void blazesUseSingleOpaqueRustBodyTextureRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("BlazeModel.class"));
        assertTrue(renderer.contains("isVanillaBlazeModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.BlazeModel.class"));
        assertTrue(rust.contains("textures/entity/blaze.png"));
    }

    @Test
    void magmaCubesUseDistinctLavaModelAndTextureRustRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("LavaSlimeModel.class"));
        assertTrue(renderer.contains("isVanillaMagmaCubeModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.LavaSlimeModel.class"));
        assertTrue(rust.contains("textures/entity/slime/magmacube.png"));
    }

    @Test
    void adultHorsesComposeVariantRustBodyWithSemanticMarkingsAndEquipment() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String markings = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");

        assertTrue(renderer.contains("HorseModel.class"));
        assertTrue(renderer.contains("HorseRenderState horseRenderState"));
        assertTrue(renderer.contains("isVanillaHorseModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.HorseModel.class"));
        int horseStart = rust.indexOf("isVanillaHorseModelMeshEligible");
        int horseEnd = rust.indexOf("\n\tpublic static boolean", horseStart + 1);
        assertFalse(rust.substring(horseStart, horseEnd).contains("state.markings"));
        assertTrue(markings.contains("submitModelSemanticTexture"));
        assertTrue(markings.contains("getParentModel(horseRenderState)"));
        assertTrue(markings.contains("resourceLocation"));
        assertTrue(rust.contains("textures/entity/horse/horse_"));
    }

    @Test
    void adultDonkeysAndMulesUseChestAwareRustBodyRouteWithoutSaddles() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("DonkeyModel.class"));
        assertTrue(renderer.contains("DonkeyRenderState donkeyRenderState"));
        assertTrue(renderer.contains("isVanillaDonkeyModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.DonkeyModel.class"));
        assertTrue(rust.contains("textures/entity/horse/donkey.png"));
        assertTrue(rust.contains("textures/entity/horse/mule.png"));
    }

    @Test
    void pigsComposeVariantRustBodyWithSemanticSaddleLayer() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/PigRenderer.java");
        String livingRenderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        int pigStart = rust.indexOf("isVanillaPigModelMeshEligible");
        int pigEnd = rust.indexOf("\n\tpublic static boolean", pigStart + 1);
        assertTrue(pigStart >= 0 && pigEnd > pigStart,
            "pig semantic admission predicate must remain present and bounded");
        assertFalse(rust.substring(pigStart, pigEnd).contains("state.saddle"),
            "pig saddles must not suppress the Rust base body");
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.PIG_SADDLE"),
            "pig saddle must remain an explicit equipment layer");
        assertTrue(rust.contains("textures/entity/pig/cold_pig.png"),
            "cold pig variants must retain their explicit Rust texture identity");
        assertTrue(livingRenderer.contains("this.model instanceof PigModel"),
            "pig ownership must admit the ColdPigModel subclass");
    }

    @Test
    void skeletonFamilyBodiesComposeWithSemanticArmorAndClothingLayers() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String armor = source("src/main/java/net/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer.java");
        String clothing = source("src/main/java/net/minecraft/client/renderer/entity/layers/SkeletonClothingLayer.java");
        for (String predicateName : new String[] {
            "isVanillaSkeletonModelMeshEligible",
            "isVanillaStrayModelMeshEligible",
            "isVanillaBoggedModelMeshEligible"
        }) {
            int start = rust.indexOf(predicateName);
            int end = rust.indexOf("\n\tpublic static boolean", start + 1);
            assertTrue(start >= 0 && end > start, predicateName + " must remain present and bounded");
            String predicate = rust.substring(start, end);
            assertFalse(predicate.contains("state.headEquipment"), predicateName + " must compose head armor");
            assertFalse(predicate.contains("state.chestEquipment"), predicateName + " must compose chest armor");
            assertFalse(predicate.contains("state.legsEquipment"), predicateName + " must compose leg armor");
            assertFalse(predicate.contains("state.feetEquipment"), predicateName + " must compose feet armor");
            assertTrue(predicate.contains("state.rightHandItem.isEmpty()"), predicateName + " must remain fail-closed for held items");
        }
        assertTrue(armor.contains("submitModelSemanticTexture"), "humanoid armor must retain semantic texture identity");
        assertTrue(clothing.contains("submitModelSemanticTexture"), "skeleton clothing must retain semantic texture identity");
    }

    @Test
    void zombieFamilyBodiesComposeWithSemanticArmorAndOuterLayers() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String armor = source("src/main/java/net/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer.java");
        String outer = source("src/main/java/net/minecraft/client/renderer/entity/layers/DrownedOuterLayer.java");
        for (String predicateName : new String[] {
            "isVanillaZombieModelMeshEligible",
            "isVanillaDrownedModelMeshEligible"
        }) {
            int start = rust.indexOf(predicateName);
            int end = rust.indexOf("\n\tpublic static boolean", start + 1);
            assertTrue(start >= 0 && end > start, predicateName + " must remain present and bounded");
            String predicate = rust.substring(start, end);
            assertFalse(predicate.contains("state.headEquipment"), predicateName + " must compose head armor");
            assertFalse(predicate.contains("state.chestEquipment"), predicateName + " must compose chest armor");
            assertFalse(predicate.contains("state.legsEquipment"), predicateName + " must compose leg armor");
            assertFalse(predicate.contains("state.feetEquipment"), predicateName + " must compose feet armor");
        }
        assertTrue(armor.contains("submitModelSemanticTexture"), "zombie armor must retain semantic texture identity");
        assertTrue(outer.contains("submitModelSemanticTexture"), "drowned outer layer must retain semantic texture identity");
    }

    @Test
    void adultLlamasUseVariantRustBodyRouteWithSeparateDecor() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("LlamaModel.class"));
        assertTrue(renderer.contains("LlamaRenderState llamaRenderState"));
        assertTrue(renderer.contains("isVanillaLlamaModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.LlamaModel.class"));
        assertTrue(source("src/main/java/net/minecraft/client/renderer/entity/layers/LlamaDecorLayer.java").contains("renderLayers"));
        assertTrue(rust.contains("textures/entity/llama/creamy.png"));
    }

    @Test
    void unsaddledAdultStridersUseWarmOrColdRustBodyTextureRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("StriderModel.class"));
        assertTrue(renderer.contains("StriderRenderState striderRenderState"));
        assertTrue(renderer.contains("isVanillaStriderModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.StriderModel.class"));
        assertTrue(rust.contains("textures/entity/strider/strider_cold.png"));
    }

    @Test
    void adultHoglinsAndZoglinsUseSharedRustBodyModelWithExactTextures() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("HoglinModel.class"));
        assertTrue(renderer.contains("HoglinRenderState hoglinRenderState"));
        assertTrue(renderer.contains("isVanillaHoglinModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.HoglinModel.class"));
        assertTrue(rust.contains("textures/entity/hoglin/hoglin.png"));
        assertTrue(rust.contains("textures/entity/hoglin/zoglin.png"));
    }

    @Test
    void unsaddledAdultCamelsUseAnimatedRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("CamelModel.class"));
        assertTrue(renderer.contains("CamelRenderState camelRenderState"));
        assertTrue(renderer.contains("isVanillaCamelModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.CamelModel.class"));
        assertTrue(rust.contains("textures/entity/camel/camel.png"));
    }

    @Test
    void plainPiglinBodiesUseExactRustTexturesWithoutEquipmentOrHeldItems() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("PiglinModel.class"));
        assertTrue(renderer.contains("ZombifiedPiglinModel.class"));
        assertTrue(renderer.contains("isVanillaPiglinModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.PiglinModel.class"));
        assertTrue(rust.contains("textures/entity/piglin/piglin_brute.png"));
        assertTrue(rust.contains("textures/entity/piglin/zombified_piglin.png"));
    }

    @Test
    void plainSkeletonAndWitherSkeletonBodiesUseSharedRustModelTextures() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("SkeletonModel.class"));
        assertTrue(renderer.contains("SkeletonRenderState skeletonRenderState"));
        assertTrue(renderer.contains("isVanillaSkeletonModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.SkeletonModel.class"));
        assertTrue(rust.contains("textures/entity/skeleton/skeleton.png"));
        assertTrue(rust.contains("textures/entity/skeleton/wither_skeleton.png"));
    }

    @Test
    void boggedBodiesUseRustRouteWithSeparateMossAndEquipmentBoundaries() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("BoggedModel.class"));
        assertTrue(renderer.contains("BoggedRenderState boggedRenderState"));
        assertTrue(renderer.contains("isVanillaBoggedModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.BoggedModel.class"));
        assertTrue(rust.contains("moss clothing is a separate semantic layer"));
        assertTrue(rust.contains("state.rightHandItem.isEmpty()"));
        assertTrue(rust.contains("textures/entity/skeleton/bogged.png"));
    }

    @Test
    void unequippedGiantsUseGiantZombieRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("GiantZombieModel.class"));
        assertTrue(renderer.contains("isVanillaGiantModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.GiantZombieModel.class"));
        assertTrue(rust.contains("textures/entity/zombie/zombie.png"));
    }

    @Test
    void adultArmadillosUseShellAnimationRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("ArmadilloModel.class"));
        assertTrue(renderer.contains("ArmadilloRenderState armadilloRenderState"));
        assertTrue(renderer.contains("isVanillaArmadilloModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.ArmadilloModel.class"));
        assertTrue(rust.contains("textures/entity/armadillo.png"));
    }

    @Test
    void adultSniffersUseModelOwnedAnimationRustBodyRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("SnifferModel.class"));
        assertTrue(renderer.contains("SnifferRenderState snifferRenderState"));
        assertTrue(renderer.contains("isVanillaSnifferModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.SnifferModel.class"));
        assertTrue(rust.contains("textures/entity/sniffer/sniffer.png"));
    }

    @Test
    void unsaddledAdultNautilusesUseNormalModelResourceTextureRustRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("NautilusModel.class"));
        assertTrue(renderer.contains("NautilusRenderState nautilusRenderState"));
        assertTrue(renderer.contains("isVanillaNautilusModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.animal.nautilus.NautilusModel.class"));
        assertTrue(rust.contains("textures/entity/nautilus/"));
    }

    @Test
    void unequippedHusksUseTheSharedZombieRustBodyRoute() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(rust.contains("textures/entity/zombie/husk.png"));
        assertTrue(rust.contains("isVanillaZombieModelMeshEligible"));
    }

    @Test
    void poweredCreepersUseBoundedRustTranslucentArmorOverlay() throws IOException {
        String submits = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(submits.contains("CreeperRenderState creeperState"));
        assertTrue(submits.contains("creeperState.isPowered"));
        assertTrue(submits.contains("textures/entity/creeper/creeper_armor.png"));
        assertTrue(rust.contains("enqueueStandaloneTranslucentModelMesh"));
    }

    @Test
    void phantomsUseRustBodyAndSeparateEyesOverlayRoutes() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String submits = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("PhantomModel.class"));
        assertTrue(renderer.contains("isVanillaPhantomModelMeshEligible"));
        assertTrue(submits.contains("PhantomRenderState phantomState"));
        assertTrue(submits.contains("textures/entity/phantom_eyes.png"));
        assertTrue(rust.contains("textures/entity/phantom.png"));
    }

    @Test
    void wardenEmissiveLayersUseExplicitSemanticTextureSubmission() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/LivingEntityEmissiveLayer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(layer.contains("submitModelSemanticTexture"));
        assertTrue(collector.contains("WardenRenderState wardenState"));
        assertTrue(collector.contains("textures/entity/warden/"));
        assertTrue(rust.contains("isVanillaWardenModelMeshEligible"));
    }

    @Test
    void creakingBodiesUseRustRouteWithActiveEyesOverlay() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("CreakingModel.class"));
        assertTrue(renderer.contains("isVanillaCreakingModelMeshEligible"));
        assertTrue(collector.contains("CreakingRenderState creakingState"));
        assertTrue(collector.contains("textures/entity/creaking/creaking_eyes.png"));
        assertTrue(rust.contains("textures/entity/creaking/creaking.png"));
    }

    @Test
    void breezeBodyWindAndEyesUseExplicitSemanticTextureRoutes() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String wind = source("src/main/java/net/minecraft/client/renderer/entity/layers/BreezeWindLayer.java");
        String eyes = source("src/main/java/net/minecraft/client/renderer/entity/layers/BreezeEyesLayer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");

        assertTrue(renderer.contains("isVanillaBreezeModelMeshEligible"));
        assertTrue(wind.contains("submitModelSemanticTexture"));
        assertTrue(eyes.contains("submitModelSemanticTexture"));
        assertTrue(collector.contains("textures/entity/breeze/breeze_wind.png"));
        assertTrue(collector.contains("textures/entity/breeze/breeze_eyes.png"));
    }

    @Test
    void endermenUseRustBodyAndSeparateEyesRoutesWithoutCarriedBlocks() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("EndermanModel.class"));
        assertTrue(renderer.contains("isVanillaEndermanModelMeshEligible"));
        assertTrue(collector.contains("EndermanRenderState endermanState"));
        assertTrue(collector.contains("textures/entity/enderman/enderman_eyes.png"));
        assertTrue(rust.contains("textures/entity/enderman/enderman.png"));
        // Carried blocks are submitted by CarriedBlockLayer; they must not disable
        // the explicit Rust body route.
        assertTrue(rust.contains("state.rightHandItem.isEmpty()"));
    }

    @Test
    void vanillaEyesLayersDeclareSemanticTextureIdentity() throws IOException {
        String eyes = source("src/main/java/net/minecraft/client/renderer/entity/layers/EyesLayer.java");
        String enderman = source("src/main/java/net/minecraft/client/renderer/entity/layers/EnderEyesLayer.java");
        String phantom = source("src/main/java/net/minecraft/client/renderer/entity/layers/PhantomEyesLayer.java");
        String spider = source("src/main/java/net/minecraft/client/renderer/entity/layers/SpiderEyesLayer.java");
        assertTrue(eyes.contains("semanticTexture()"));
        assertTrue(eyes.contains("submitModelSemanticTexture"));
        assertTrue(enderman.contains("semanticTexture"));
        assertTrue(phantom.contains("semanticTexture"));
        assertTrue(spider.contains("semanticTexture"));
    }

    @Test
    void spiderFixtureRequiresDistinctRustBodyAndEyesComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/SpiderEyesLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("enqueueStandaloneTranslucentModelMesh"));
        assertTrue(layer.contains("SPIDER_EYES_IDENTITY"));
        assertTrue(layer.contains("spider_eyes"));
        assertTrue(layer.contains("rust-vulkan-unavailable"));
        assertTrue(capture.contains("new Spider(EntityType.SPIDER"));
        assertTrue(capture.contains("spiderFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:spider\", \"minecraft:spider_eyes\")"));
		assertTrue(rust.contains("MATERIAL_ID_MODEL_EYES"));
		assertTrue(rust.contains("renderType.pipeline() == RenderPipelines.EYES"));
        assertTrue(harness.contains("minecraft:textures/entity/spider/spider.png"));
        assertTrue(harness.contains("minecraft:textures/entity/spider_eyes.png"));
        assertTrue(harness.contains("base_and_eyes_emission_observed"));
    }

    @Test
    void endermanFixtureRequiresDistinctRustBodyAndEyesComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/EnderEyesLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("enqueueStandaloneTranslucentModelMesh"));
        assertTrue(layer.contains("ENDERMAN_EYES_IDENTITY"));
        assertTrue(layer.contains("enderman_eyes"));
        assertTrue(layer.contains("rust-vulkan-unavailable"));
        assertTrue(capture.contains("new EnderMan(EntityType.ENDERMAN"));
        assertTrue(capture.contains("endermanFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:enderman\", \"minecraft:enderman_eyes\")"));
        assertTrue(harness.contains("minecraft:textures/entity/enderman/enderman.png"));
        assertTrue(harness.contains("minecraft:textures/entity/enderman/enderman_eyes.png"));
        assertTrue(harness.contains("frozen_enderman_emission_evidence"));
    }

    @Test
    void phantomFixtureRequiresDistinctRustBodyAndEyesComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/PhantomEyesLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("enqueueStandaloneTranslucentModelMesh"));
        assertTrue(layer.contains("PHANTOM_EYES_IDENTITY"));
        assertTrue(layer.contains("phantom_eyes"));
        assertTrue(layer.contains("rust-vulkan-unavailable"));
        assertTrue(capture.contains("new Phantom(EntityType.PHANTOM"));
        assertTrue(capture.contains("phantomFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:phantom\", \"minecraft:phantom_eyes\")"));
        assertTrue(harness.contains("minecraft:textures/entity/phantom.png"));
        assertTrue(harness.contains("minecraft:textures/entity/phantom_eyes.png"));
        assertTrue(harness.contains("frozen_phantom_emission_evidence"));
    }

    @Test
    void creakingFixtureRequiresDistinctRustBodyAndEyesComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/CreakingEyesLayer.java");
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/CreakingRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("new CreakingEyesLayer"));
        assertTrue(layer.contains("enqueueStandaloneTranslucentModelMesh"));
        assertTrue(layer.contains("IDENTITY"));
        assertTrue(layer.contains("creaking_eyes"));
        assertTrue(layer.contains("rust-vulkan-unavailable"));
        assertTrue(capture.contains("new Creaking(EntityType.CREAKING"));
        assertTrue(capture.contains("creakingFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:creaking\", \"minecraft:creaking_eyes\")"));
        assertTrue(harness.contains("minecraft:textures/entity/creaking/creaking.png"));
        assertTrue(harness.contains("minecraft:textures/entity/creaking/creaking_eyes.png"));
        assertTrue(harness.contains("frozen_creaking_emission_evidence"));
    }

    @Test
    void breezeFixtureRequiresBodyScrollingWindAndEyesComposition() throws IOException {
        String wind = source("src/main/java/net/minecraft/client/renderer/entity/layers/BreezeWindLayer.java");
        String eyes = source("src/main/java/net/minecraft/client/renderer/entity/layers/BreezeEyesLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(wind.contains("enqueueStandaloneScrollingTranslucentModelMesh"));
        assertTrue(wind.contains("BREEZE_WIND_IDENTITY"));
        assertTrue(wind.contains("offsetU"));
        assertTrue(wind.contains("rust-vulkan-unavailable"));
        assertTrue(eyes.contains("enqueueStandaloneTranslucentModelMesh"));
        assertTrue(eyes.contains("BREEZE_EYES_IDENTITY"));
        assertTrue(eyes.contains("rust-vulkan-unavailable"));
        assertTrue(capture.contains("new Breeze(EntityType.BREEZE"));
        assertTrue(capture.contains("breezeFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:breeze\", \"minecraft:breeze_wind\")"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:breeze\", \"minecraft:breeze_eyes\")"));
        assertTrue(harness.contains("minecraft:textures/entity/breeze/breeze.png"));
        assertTrue(harness.contains("minecraft:textures/entity/breeze/breeze_wind.png"));
        assertTrue(harness.contains("minecraft:textures/entity/breeze/breeze_eyes.png"));
        assertTrue(harness.contains("frozen_breeze_emission_evidence"));
    }

    @Test
    void boggedFixtureRequiresBodyAndMossOverlayComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/SkeletonClothingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("enqueueStandaloneModelMesh"));
        assertTrue(layer.contains("BOGGED_OVERLAY_IDENTITY"));
        assertTrue(layer.contains("bogged_overlay"));
        assertTrue(layer.contains("rust-vulkan-unavailable"));
        assertTrue(capture.contains("new Bogged(EntityType.BOGGED"));
        assertTrue(capture.contains("boggedFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:bogged\", \"minecraft:bogged_overlay\")"));
        assertTrue(harness.contains("minecraft:textures/entity/skeleton/bogged.png"));
        assertTrue(harness.contains("minecraft:textures/entity/skeleton/bogged_overlay.png"));
        assertTrue(harness.contains("frozen_bogged_emission_evidence"));
    }

    @Test
    void strayFixtureRequiresBodyAndClothingOverlayComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/SkeletonClothingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("STRAY_OVERLAY_IDENTITY"));
        assertTrue(layer.contains("enqueueStandaloneModelMesh"));
        assertTrue(layer.contains("Rust whole-frame stray-overlay route has no copied semantic mesh"));
        assertTrue(capture.contains("new Stray(EntityType.STRAY"));
        assertTrue(capture.contains("strayFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:stray\", \"minecraft:stray_overlay\")"));
        assertTrue(harness.contains("minecraft:textures/entity/skeleton/stray.png"));
        assertTrue(harness.contains("minecraft:textures/entity/skeleton/stray_overlay.png"));
        assertTrue(harness.contains("frozen_stray_emission_evidence"));
    }

    @Test
    void drownedFixtureRequiresBodyAndOuterLayerComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/DrownedOuterLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("enqueueStandaloneModelMesh"));
        assertTrue(layer.contains("DROWNED_OUTER_IDENTITY"));
        assertTrue(layer.contains("Rust whole-frame drowned-outer route has no copied semantic mesh"));
        assertTrue(capture.contains("new Drowned(EntityType.DROWNED"));
        assertTrue(capture.contains("drownedFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:drowned\", \"minecraft:drowned_outer\")"));
        assertTrue(harness.contains("minecraft:textures/entity/zombie/drowned.png"));
        assertTrue(harness.contains("minecraft:textures/entity/zombie/drowned_outer_layer.png"));
        assertTrue(harness.contains("frozen_drowned_emission_evidence"));
    }

    @Test
    void slimeFixtureRequiresBodyAndTranslucentOuterComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/SlimeOuterLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("enqueueStandaloneModelMesh"));
        assertTrue(layer.contains("ResourceLocation.withDefaultNamespace(\"slime_outer\")"));
        assertTrue(layer.contains("j, -1, slimeRenderState.outlineColor"),
            "the Rust shell must preserve vanilla overlay coordinates and neutral tint");
        assertTrue(layer.contains("recordModelMeshRouteDecision"));
        assertTrue(capture.contains("new Slime(EntityType.SLIME"));
        assertTrue(capture.contains("slime.setSize(2, true)"));
        assertTrue(capture.contains("slimeFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:slime\", \"minecraft:slime_outer\")"));
        assertTrue(harness.contains("frozen_slime_emission_evidence"));
    }

    @Test
    void catFixtureRequiresTintedCollarComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/CatCollarLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("enqueueStandaloneModelMesh"));
        assertTrue(layer.contains("ResourceLocation.withDefaultNamespace(\"cat_collar\")"));
        assertTrue(layer.contains("LivingEntityRenderer.getOverlayCoords(catRenderState, 0.0F), j"));
        assertTrue(layer.contains("recordModelMeshRouteDecision"));
        assertTrue(capture.contains("new Cat(EntityType.CAT"));
        assertTrue(capture.contains("DataComponents.CAT_COLLAR, DyeColor.RED"));
        assertTrue(capture.contains("catFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:cat\", \"minecraft:cat_collar\")"));
        assertTrue(harness.contains("frozen_cat_emission_evidence"));
    }

    @Test
    void striderFixtureRequiresWarmAdultAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/StriderRenderer.java");
        String equipment = source("src/main/java/net/minecraft/client/resources/model/EquipmentClientInfo.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.STRIDER_SADDLE"));
        assertTrue(equipment.contains("textures/entity/equipment/\" + layerType.getSerializedName()"));
        assertTrue(capture.contains("\"strider-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("strider.setSuffocating(false)"));
        assertTrue(capture.contains("EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE)"));
        assertTrue(capture.contains("striderSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/strider_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_strider_saddle_emission_evidence"));
        assertTrue(harness.contains("strider_saddle_fixture_equivalence"));
        assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
        assertTrue(harness.contains("-Dmattmc.dev.rustArmorFoil=true"));
    }

    @Test
    void pigFixtureRequiresAdultAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/PigRenderer.java");
        String equipment = source("src/main/java/net/minecraft/client/resources/model/EquipmentClientInfo.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.PIG_SADDLE"));
        assertTrue(equipment.contains("textures/entity/equipment/\" + layerType.getSerializedName()"));
        assertTrue(capture.contains("\"pig-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("pig.setBaby(false)"));
        assertTrue(capture.contains("EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE)"));
        assertTrue(capture.contains("pigSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/pig_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_pig_saddle_emission_evidence"));
        assertTrue(harness.contains("pig_saddle_fixture_equivalence"));
        assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
    }

    @Test
    void horseFixtureRequiresTamedUnarmoredWhiteAdultAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String equipment = source("src/main/java/net/minecraft/client/resources/model/EquipmentClientInfo.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(equipment.contains("textures/entity/equipment/\" + layerType.getSerializedName()"));
        assertTrue(capture.contains("\"horse-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horse.setBaby(\"horse-baby-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-diamond-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-copper-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-iron-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gold-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-netherite-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO))"));
        assertTrue(capture.contains("horse.setTamed(true)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.WHITE, Markings.NONE)"));
        assertTrue(capture.contains("tag.putInt(\"Variant\", variant.getId() | markings.getId() << 8)"));
        assertTrue(capture.contains("horse.saveWithoutId(output)"));
        assertTrue(capture.contains("horse.load(net.minecraft.world.level.storage.TagValueInput.create("));
        assertTrue(capture.contains("horse.getVariant() == Variant.WHITE"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.NONE"));
        assertTrue(capture.contains("EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE)"));
        assertTrue(capture.contains("horseSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_saddle_fixture_equivalence"));
        assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
    }

    @Test
    void babyHorseFixtureRequiresBabyModelsAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horse.setBaby(\"horse-baby-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-diamond-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-copper-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-iron-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gold-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-netherite-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO))"));
        assertTrue(capture.contains("horseBabySaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_white.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_saddle_fixture_equivalence"));
    }

    @Test
    void babyCreamyHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-creamy-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CREAMY, Markings.NONE)"));
        assertTrue(capture.contains("horseBabyCreamySaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_creamy.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_creamy_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_creamy_saddle_fixture_equivalence"));
    }
    @Test
    void adultCreamyHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-creamy-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("case \"horse-creamy-saddled\", \"horse-baby-creamy-saddled\""));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CREAMY, Markings.NONE)"));
        assertTrue(capture.contains("horseCreamySaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_creamy.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_saddle_fixture_equivalence"));
    }
    @Test
    void adultChestnutHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-chestnut-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("case \"horse-chestnut-saddled\", \"horse-baby-chestnut-saddled\""));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CHESTNUT, Markings.NONE)"));
        assertTrue(capture.contains("horseChestnutSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_saddle_fixture_equivalence"));
    }
    @Test
    void adultBrownHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-brown-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("case \"horse-brown-saddled\", \"horse-baby-brown-saddled\""));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BROWN, Markings.NONE)"));
        assertTrue(capture.contains("horseBrownSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_brown_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_brown_saddle_fixture_equivalence"));
    }
    @Test
    void adultBlackHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-black-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("case \"horse-black-saddled\", \"horse-baby-black-saddled\""));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BLACK, Markings.NONE)"));
        assertTrue(capture.contains("horseBlackSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_black.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_black_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_black_saddle_fixture_equivalence"));
    }
    @Test
    void adultGrayHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-gray-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("case \"horse-gray-saddled\", \"horse-baby-gray-saddled\""));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.GRAY, Markings.NONE)"));
        assertTrue(capture.contains("horseGraySaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_gray_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_gray_saddle_fixture_equivalence"));
    }
    @Test
    void adultDarkBrownHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-dark-brown-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("case \"horse-dark-brown-saddled\", \"horse-baby-dark-brown-saddled\""));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.DARK_BROWN, Markings.NONE)"));
        assertTrue(capture.contains("horseDarkBrownSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_dark_brown_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_dark_brown_saddle_fixture_equivalence"));
    }
    @Test
    void babyChestnutHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-chestnut-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CHESTNUT, Markings.NONE)"));
        assertTrue(capture.contains("horseBabyChestnutSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_chestnut_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_chestnut_saddle_fixture_equivalence"));
    }

    @Test
    void babyBrownHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-brown-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BROWN, Markings.NONE)"));
        assertTrue(capture.contains("horseBabyBrownSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_brown_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_brown_saddle_fixture_equivalence"));
    }

    @Test
    void babyBlackHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-black-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BLACK, Markings.NONE)"));
        assertTrue(capture.contains("horseBabyBlackSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_black.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_black_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_black_saddle_fixture_equivalence"));
    }

    @Test
    void babyGrayHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-gray-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.GRAY, Markings.NONE)"));
        assertTrue(capture.contains("horseBabyGraySaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_gray_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_gray_saddle_fixture_equivalence"));
    }

    @Test
    void babyDarkBrownHorseFixtureRequiresExactCoatAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-dark-brown-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.DARK_BROWN, Markings.NONE)"));
        assertTrue(capture.contains("horseBabyDarkBrownSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_dark_brown_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_dark_brown_saddle_fixture_equivalence"));
    }


    @Test
    void donkeyFixtureRequiresTamedUnchestedAdultAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DonkeyRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("new DonkeyModel"));
        assertTrue(renderer.contains("new EquineSaddleModel"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.DONKEY_SADDLE"));
        assertTrue(capture.contains("\"donkey-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("Donkey donkey = new Donkey(EntityType.DONKEY, serverLevel)"));
        assertTrue(capture.contains("donkey.setBaby(false)"));
        assertTrue(capture.contains("donkey.setTamed(true)"));
        assertTrue(capture.contains("donkey.setChest(false)"));
        assertTrue(capture.contains("donkey.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("donkeySaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/donkey.png"));
        assertTrue(rust.contains("textures/entity/equipment/donkey_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_donkey_saddle_emission_evidence"));
        assertTrue(harness.contains("donkey_saddle_fixture_equivalence"));
    }

    @Test
    void babyDonkeyFixtureRequiresBabyModelsAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DonkeyRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.DONKEY_BABY"));
        assertTrue(renderer.contains("ModelLayers.DONKEY_BABY_SADDLE"));
        assertTrue(capture.contains("\"donkey-baby-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("donkey.setBaby(true)"));
        assertTrue(capture.contains("donkey.setTamed(true)"));
        assertTrue(capture.contains("donkey.setChest(false)"));
        assertTrue(capture.contains("donkeyBabySaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/donkey.png"));
        assertTrue(rust.contains("textures/entity/equipment/donkey_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_donkey_baby_saddle_emission_evidence"));
        assertTrue(harness.contains("donkey_baby_saddle_fixture_equivalence"));
    }

    @Test
    void chestedDonkeyFixtureRequiresAdultChestGeometryAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DonkeyRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("new DonkeyModel"));
        assertTrue(renderer.contains("new EquineSaddleModel"));
        assertTrue(renderer.contains("donkeyRenderState.hasChest = abstractChestedHorse.hasChest()"));
        assertTrue(capture.contains("\"donkey-chested-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("Donkey donkey = new Donkey(EntityType.DONKEY, serverLevel)"));
        assertTrue(capture.contains("donkey.setBaby(false)"));
        assertTrue(capture.contains("donkey.setTamed(true)"));
        assertTrue(capture.contains("donkey.setChest(true)"));
        assertTrue(capture.contains("donkey.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("donkeyChestedSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/donkey.png"));
        assertTrue(rust.contains("textures/entity/equipment/donkey_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_donkey_chested_saddle_emission_evidence"));
        assertTrue(harness.contains("donkey_chested_saddle_fixture_equivalence"));
    }

    @Test
    void babyChestedDonkeyFixtureRequiresBabyChestGeometryAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DonkeyRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.DONKEY_BABY"));
        assertTrue(renderer.contains("ModelLayers.DONKEY_BABY_SADDLE"));
        assertTrue(renderer.contains("donkeyRenderState.hasChest = abstractChestedHorse.hasChest()"));
        assertTrue(capture.contains("\"donkey-baby-chested-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("donkey.setBaby(true)"));
        assertTrue(capture.contains("donkey.setTamed(true)"));
        assertTrue(capture.contains("donkey.setChest(true)"));
        assertTrue(capture.contains("donkeyBabyChestedSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/donkey.png"));
        assertTrue(rust.contains("textures/entity/equipment/donkey_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_donkey_baby_chested_saddle_emission_evidence"));
        assertTrue(harness.contains("donkey_baby_chested_saddle_fixture_equivalence"));
    }

    @Test
    void muleFixtureRequiresTamedUnchestedAdultAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DonkeyRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("new DonkeyModel"));
        assertTrue(renderer.contains("new EquineSaddleModel"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.MULE_SADDLE"));
        assertTrue(capture.contains("\"mule-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("Mule mule = new Mule(EntityType.MULE, serverLevel)"));
        assertTrue(capture.contains("mule.setBaby(false)"));
        assertTrue(capture.contains("mule.setTamed(true)"));
        assertTrue(capture.contains("mule.setChest(false)"));
        assertTrue(capture.contains("mule.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("muleSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/mule.png"));
        assertTrue(rust.contains("textures/entity/equipment/mule_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_mule_saddle_emission_evidence"));
        assertTrue(harness.contains("mule_saddle_fixture_equivalence"));
    }

    @Test
    void chestedMuleFixtureRequiresAdultChestGeometryAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DonkeyRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("donkeyRenderState.hasChest = abstractChestedHorse.hasChest()"));
        assertTrue(capture.contains("\"mule-chested-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("mule.setBaby(false)"));
        assertTrue(capture.contains("mule.setTamed(true)"));
        assertTrue(capture.contains("mule.setChest(true)"));
        assertTrue(capture.contains("muleChestedSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/mule.png"));
        assertTrue(rust.contains("textures/entity/equipment/mule_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_mule_chested_saddle_emission_evidence"));
        assertTrue(harness.contains("mule_chested_saddle_fixture_equivalence"));
    }

    @Test
    void babyChestedMuleFixtureRequiresBabyChestGeometryAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DonkeyRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(renderer.contains("ModelLayers.MULE_BABY"));
        assertTrue(renderer.contains("ModelLayers.MULE_BABY_SADDLE"));
        assertTrue(renderer.contains("donkeyRenderState.hasChest = abstractChestedHorse.hasChest()"));
        assertTrue(capture.contains("\"mule-baby-chested-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("mule.setBaby(true)"));
        assertTrue(capture.contains("mule.setChest(true)"));
        assertTrue(capture.contains("muleBabyChestedSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/mule.png"));
        assertTrue(rust.contains("textures/entity/equipment/mule_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_mule_baby_chested_saddle_emission_evidence"));
        assertTrue(harness.contains("mule_baby_chested_saddle_fixture_equivalence"));
    }

    @Test
    void babyMuleFixtureRequiresBabyModelsAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DonkeyRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.MULE_BABY"));
        assertTrue(renderer.contains("ModelLayers.MULE_BABY_SADDLE"));
        assertTrue(capture.contains("\"mule-baby-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("mule.setBaby(true)"));
        assertTrue(capture.contains("mule.setTamed(true)"));
        assertTrue(capture.contains("mule.setChest(false)"));
        assertTrue(capture.contains("muleBabySaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/horse/mule.png"));
        assertTrue(rust.contains("textures/entity/equipment/mule_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_mule_baby_saddle_emission_evidence"));
        assertTrue(harness.contains("mule_baby_saddle_fixture_equivalence"));
    }

    @Test
    void babyDiamondHorseFixtureRequiresBabyBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_ARMOR"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-diamond-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horse.setBaby(\"horse-baby-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-diamond-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-copper-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-iron-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gold-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-netherite-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-creamy-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-chestnut-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-black-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-gray-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO) || \"horse-baby-dark-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO))"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseBabyDiamondEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/diamond.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_diamond_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_baby_diamond_equipment_fixture_equivalence"));
    }

    @Test
    void diamondHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-diamond-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.DIAMOND_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseDiamondEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/diamond.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_diamond_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_diamond_equipment_fixture_equivalence"));
        assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
    }

    @Test
    void creamyDiamondHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-creamy-diamond-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.DIAMOND_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseCreamyDiamondEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/diamond.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_diamond_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_diamond_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-diamond-equipped\": \"horse_creamy_diamond_equipment\""));
    }

    @Test
    void babyHorseEquipmentSetupRoutesEachMaterialToItsOwnArmorBranch() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");

        String diamondAssignment = "horse.setItemSlot(net.minecraft.world.entity.EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR));";
        int cursor = 0;
        int diamondBranches = 0;
        while ((cursor = capture.indexOf(diamondAssignment, cursor)) >= 0) {
            int branchStart = capture.lastIndexOf("if (", cursor);
            String condition = capture.substring(branchStart, cursor);
            assertFalse(condition.contains("horse-baby-copper-equipped"));
            assertFalse(condition.contains("horse-baby-iron-equipped"));
            assertFalse(condition.contains("horse-baby-gold-equipped"));
            assertFalse(condition.contains("horse-baby-netherite-equipped"));
            assertFalse(condition.contains("horse-baby-dyed-leather-equipped"));
            diamondBranches++;
            cursor += diamondAssignment.length();
        }
        assertTrue(diamondBranches == 2);
        assertTrue(capture.contains("\"horse-baby-copper-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-copper-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("\"horse-baby-iron-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-iron-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("\"horse-baby-gold-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-gold-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("\"horse-baby-netherite-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-netherite-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("\"horse-baby-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO) || \"horse-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO)"));
    }

    @Test
    void chestnutDiamondHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-chestnut-diamond-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.DIAMOND_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseChestnutDiamondEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/diamond.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_diamond_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_diamond_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-diamond-equipped\": \"horse_chestnut_diamond_equipment\""));
    }

    @Test
    void chestnutCopperHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-chestnut-copper-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.COPPER_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.COPPER_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseChestnutCopperEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/copper.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_copper_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_copper_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-copper-equipped\": \"horse_chestnut_copper_equipment\""));
    }

    @Test
    void chestnutIronHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-chestnut-iron-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.IRON_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.IRON_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseChestnutIronEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/iron.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_iron_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_iron_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-iron-equipped\": \"horse_chestnut_iron_equipment\""));
    }

    @Test
    void chestnutGoldHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-chestnut-gold-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.GOLDEN_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseChestnutGoldEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/gold.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_gold_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_gold_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-gold-equipped\": \"horse_chestnut_gold_equipment\""));
    }

    @Test
    void chestnutNetheriteHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-chestnut-netherite-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.NETHERITE_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.NETHERITE_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseChestnutNetheriteEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/netherite.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_netherite_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_netherite_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-netherite-equipped\": \"horse_chestnut_netherite_equipment\""));
    }

    @Test
    void creamyCopperHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-creamy-copper-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.COPPER_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.COPPER_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseCreamyCopperEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/copper.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_copper_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_copper_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-copper-equipped\": \"horse_creamy_copper_equipment\""));
    }

    @Test
    void creamyIronHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-creamy-iron-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.IRON_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.IRON_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseCreamyIronEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/iron.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_iron_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_iron_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-iron-equipped\": \"horse_creamy_iron_equipment\""));
    }

    @Test
    void creamyGoldHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-creamy-gold-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.GOLDEN_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseCreamyGoldEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/gold.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_gold_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_gold_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-gold-equipped\": \"horse_creamy_gold_equipment\""));
    }

    @Test
    void creamyNetheriteHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-creamy-netherite-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.NETHERITE_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.NETHERITE_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseCreamyNetheriteEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/netherite.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_netherite_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_netherite_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-netherite-equipped\": \"horse_creamy_netherite_equipment\""));
    }

    @Test
    void babyCopperHorseFixtureRequiresBabyBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_ARMOR"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-copper-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.COPPER_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseBabyCopperEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/copper.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_copper_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_baby_copper_equipment_fixture_equivalence"));
    }

    @Test
    void copperHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-copper-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.COPPER_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.COPPER_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseCopperEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/copper.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_copper_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_copper_equipment_fixture_equivalence"));
		assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
    }

    @Test
    void babyIronHorseFixtureRequiresBabyBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_ARMOR"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-iron-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.IRON_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseBabyIronEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/iron.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_iron_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_baby_iron_equipment_fixture_equivalence"));
    }

    @Test
    void ironHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-iron-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.IRON_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.IRON_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseIronEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/iron.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_iron_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_iron_equipment_fixture_equivalence"));
		assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
    }

    @Test
    void babyGoldHorseFixtureRequiresBabyBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_ARMOR"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-gold-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseBabyGoldEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/gold.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_gold_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_baby_gold_equipment_fixture_equivalence"));
    }

    @Test
    void goldHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-gold-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.GOLDEN_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseGoldEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/gold.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_gold_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_gold_equipment_fixture_equivalence"));
		assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
    }

    @Test
    void babyNetheriteHorseFixtureRequiresBabyBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_ARMOR"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-netherite-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.NETHERITE_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseBabyNetheriteEquipmentFixtureJson"));
        assertTrue(capture.contains("!\"horse-baby-copper-equipped\".equals(MODEL_MESH_SCENARIO) && !\"horse-baby-iron-equipped\".equals(MODEL_MESH_SCENARIO) && !\"horse-baby-gold-equipped\".equals(MODEL_MESH_SCENARIO) && !\"horse-baby-netherite-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/netherite.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_netherite_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_baby_netherite_equipment_fixture_equivalence"));
    }

    @Test
    void netheriteHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-netherite-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.NETHERITE_HORSE_ARMOR)"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.NETHERITE_HORSE_ARMOR)"));
        assertTrue(capture.contains("horseNetheriteEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/netherite.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_netherite_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_netherite_equipment_fixture_equivalence"));
		assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
    }

    @Test
    void babyDyedLeatherHorseFixtureRequiresBabyBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_ARMOR"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(capture.contains("\"horse-baby-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("new ItemStack(Items.LEATHER_HORSE_ARMOR)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, leatherArmor"));
        assertTrue(capture.contains("clientBodyDyeRgb"));
        assertTrue(capture.contains("0x3366CC"));
        assertTrue(capture.contains("horseBabyDyedLeatherEquipmentFixtureJson"));
        assertTrue(capture.contains("!\"horse-baby-netherite-equipped\".equals(MODEL_MESH_SCENARIO) && !\"horse-baby-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/leather.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_dyed_leather_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_baby_dyed_leather_equipment_fixture_equivalence"));
    }

    @Test
    void babyMarkedHorseFixtureRequiresBabyMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-baby-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.WHITE, Markings.WHITE_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-marked-saddled\": \"horse_baby_marked_saddle\""));
        assertTrue(harness.contains("else \"marking\" if scenario_name in {\"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\"}"));
    }

    @Test
    void adultWhiteDotsMarkedHorseFixtureRequiresAdultMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-white-dots-marked-saddled\": \"horse_white_dots_marked_saddle\""));
    }

    @Test
    void adultCreamyWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-creamy-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseCreamyWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_creamy.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-white-dots-marked-saddled\": \"horse_creamy_white_dots_marked_saddle\""));
    }

    @Test
    void adultBrownWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseBrownWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_brown_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_brown_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-brown-white-dots-marked-saddled\": \"horse_brown_white_dots_marked_saddle\""));
    }

    @Test
    void adultBlackWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-black-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseBlackWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_black.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_black_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_black_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-black-white-dots-marked-saddled\": \"horse_black_white_dots_marked_saddle\""));
    }

    @Test
    void adultGrayWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-gray-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseGrayWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_gray_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_gray_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-gray-white-dots-marked-saddled\": \"horse_gray_white_dots_marked_saddle\""));
    }

    @Test
    void adultDarkBrownWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-dark-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseDarkBrownWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_dark_brown_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_dark_brown_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-dark-brown-white-dots-marked-saddled\": \"horse_dark_brown_white_dots_marked_saddle\""));
    }

    @Test
    void adultChestnutWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-chestnut-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseChestnutWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-white-dots-marked-saddled\": \"horse_chestnut_white_dots_marked_saddle\""));
    }

    @Test
    void adultCreamyWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-creamy-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseCreamyWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_creamy.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-white-marked-saddled\": \"horse_creamy_white_marked_saddle\""));
    }

    @Test
    void adultBrownWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseBrownWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_brown_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_brown_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-brown-white-marked-saddled\": \"horse_brown_white_marked_saddle\""));
    }

    @Test
    void adultBlackWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-black-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseBlackWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_black.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_black_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_black_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-black-white-marked-saddled\": \"horse_black_white_marked_saddle\""));
    }

    @Test
    void adultGrayWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-gray-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseGrayWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_gray_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_gray_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-gray-white-marked-saddled\": \"horse_gray_white_marked_saddle\""));
    }

    @Test
    void adultDarkBrownWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-dark-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseDarkBrownWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_dark_brown_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_dark_brown_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-dark-brown-white-marked-saddled\": \"horse_dark_brown_white_marked_saddle\""));
    }

    @Test
    void adultChestnutWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-chestnut-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseChestnutWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-white-marked-saddled\": \"horse_chestnut_white_marked_saddle\""));
    }

    @Test
    void adultCreamyWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-creamy-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseCreamyWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_creamy.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-white-field-marked-saddled\": \"horse_creamy_white_field_marked_saddle\""));
    }

    @Test
    void adultBrownWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseBrownWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_brown_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_brown_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-brown-white-field-marked-saddled\": \"horse_brown_white_field_marked_saddle\""));
    }

    @Test
    void adultBlackWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-black-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseBlackWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_black.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_black_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_black_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-black-white-field-marked-saddled\": \"horse_black_white_field_marked_saddle\""));
    }

    @Test
    void adultGrayWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-gray-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseGrayWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_gray_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_gray_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-gray-white-field-marked-saddled\": \"horse_gray_white_field_marked_saddle\""));
    }

    @Test
    void adultDarkBrownWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-dark-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseDarkBrownWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_dark_brown_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_dark_brown_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-dark-brown-white-field-marked-saddled\": \"horse_dark_brown_white_field_marked_saddle\""));
    }

    @Test
    void adultChestnutWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-chestnut-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseChestnutWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-white-field-marked-saddled\": \"horse_chestnut_white_field_marked_saddle\""));
    }

    @Test
    void adultCreamyBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-creamy-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseCreamyBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_creamy.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-black-dots-marked-saddled\": \"horse_creamy_black_dots_marked_saddle\""));
    }

    @Test
    void adultBrownBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseBrownBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_brown_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_brown_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-brown-black-dots-marked-saddled\": \"horse_brown_black_dots_marked_saddle\""));
    }

    @Test
    void adultBlackBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-black-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseBlackBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_black.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_black_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_black_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-black-black-dots-marked-saddled\": \"horse_black_black_dots_marked_saddle\""));
    }

    @Test
    void adultGrayBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-gray-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseGrayBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_gray_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_gray_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-gray-black-dots-marked-saddled\": \"horse_gray_black_dots_marked_saddle\""));
    }

    @Test
    void adultDarkBrownBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-dark-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseDarkBrownBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_dark_brown_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_dark_brown_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-dark-brown-black-dots-marked-saddled\": \"horse_dark_brown_black_dots_marked_saddle\""));
    }

    @Test
    void everyNonWhiteMarkedHorseFixtureSelectsItsExactCoatTexture() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        int selectorStart = capture.indexOf("private static String expectedModelMeshTextureId()");
        int selectorEnd = capture.indexOf("private static String expectedModelMeshDiagnosticTextureId()", selectorStart);
        assertTrue(selectorStart >= 0 && selectorEnd > selectorStart);
        String selector = capture.substring(selectorStart, selectorEnd);
        String[][] coats = {
            {"creamy", "horse_creamy.png"}, {"chestnut", "horse_chestnut.png"},
            {"brown", "horse_brown.png"}, {"black", "horse_black.png"},
            {"gray", "horse_gray.png"}, {"dark-brown", "horse_darkbrown.png"}
        };
        String[] markings = {"white-dots", "white", "white-field", "black-dots"};
        for (String[] coat : coats) {
            String texture = "minecraft:textures/entity/horse/" + coat[1];
            for (String agePrefix : new String[] {"horse-", "horse-baby-"}) {
                for (String marking : markings) {
                    String scenario = agePrefix + coat[0] + "-" + marking + "-marked-saddled";
                    assertTrue(selector.lines().anyMatch(line -> line.contains("\"" + scenario + "\"")
                        && line.contains("-> \"" + texture + "\";")), scenario + " must select " + texture);
                }
            }
        }
    }

    @Test
    void adultChestnutBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-chestnut-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseChestnutBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-black-dots-marked-saddled\": \"horse_chestnut_black_dots_marked_saddle\""));
    }

    @Test
    void adultWhiteMarkedHorseFixtureRequiresAdultMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-white-marked-saddled\": \"horse_white_marked_saddle\""));
    }

    @Test
    void adultWhiteFieldMarkedHorseFixtureRequiresAdultMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-white-field-marked-saddled\": \"horse_white_field_marked_saddle\""));
    }

    @Test
    void adultBlackDotsMarkedHorseFixtureRequiresAdultMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE"));
        assertTrue(renderer.contains("ModelLayers.HORSE_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("horseBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-black-dots-marked-saddled\": \"horse_black_dots_marked_saddle\""));
    }

    @Test
    void babyCreamyWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-baby-creamy-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CREAMY, Markings.WHITE_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyCreamyWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_creamy.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_creamy_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_creamy_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-creamy-white-dots-marked-saddled\": \"horse_baby_creamy_white_dots_marked_saddle\""));
    }

    @Test
    void babyCreamyWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
		assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-baby-creamy-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CREAMY, Markings.WHITE)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyCreamyWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_creamy.png"));
		assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_creamy_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_creamy_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-creamy-white-marked-saddled\": \"horse_baby_creamy_white_marked_saddle\""));
    }

    @Test
    void babyCreamyWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-baby-creamy-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CREAMY, Markings.WHITE_FIELD)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_FIELD"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyCreamyWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_creamy.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_creamy_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_creamy_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-creamy-white-field-marked-saddled\": \"horse_baby_creamy_white_field_marked_saddle\""));
    }

    @Test
    void babyCreamyBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-baby-creamy-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CREAMY, Markings.BLACK_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.BLACK_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyCreamyBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_creamy.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_creamy_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_creamy_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-creamy-black-dots-marked-saddled\": \"horse_baby_creamy_black_dots_marked_saddle\""));
    }

    @Test
    void babyChestnutWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-baby-chestnut-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CHESTNUT, Markings.WHITE_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyChestnutWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_chestnut_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_chestnut_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-chestnut-white-dots-marked-saddled\": \"horse_baby_chestnut_white_dots_marked_saddle\""));
    }

    @Test
    void babyChestnutWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-baby-chestnut-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CHESTNUT, Markings.WHITE)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyChestnutWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_chestnut_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_chestnut_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-chestnut-white-marked-saddled\": \"horse_baby_chestnut_white_marked_saddle\""));
    }

    @Test
    void babyChestnutWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-baby-chestnut-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CHESTNUT, Markings.WHITE_FIELD)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_FIELD"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyChestnutWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_chestnut_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_chestnut_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-chestnut-white-field-marked-saddled\": \"horse_baby_chestnut_white_field_marked_saddle\""));
    }

    @Test
    void babyChestnutBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-baby-chestnut-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.CHESTNUT, Markings.BLACK_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.BLACK_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyChestnutBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_chestnut.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_chestnut_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_chestnut_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-chestnut-black-dots-marked-saddled\": \"horse_baby_chestnut_black_dots_marked_saddle\""));
    }

    @Test
    void babyBlackWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-baby-black-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BLACK, Markings.WHITE_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyBlackWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_black.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_black_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_black_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-black-white-dots-marked-saddled\": \"horse_baby_black_white_dots_marked_saddle\""));
    }

    @Test
    void babyBlackWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-baby-black-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BLACK, Markings.WHITE)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyBlackWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_black.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_black_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_black_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-black-white-marked-saddled\": \"horse_baby_black_white_marked_saddle\""));
    }

    @Test
    void babyBlackWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-baby-black-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BLACK, Markings.WHITE_FIELD)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_FIELD"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyBlackWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_black.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_black_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_black_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-black-white-field-marked-saddled\": \"horse_baby_black_white_field_marked_saddle\""));
    }

    @Test
    void babyBlackBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-baby-black-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BLACK, Markings.BLACK_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.BLACK_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyBlackBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_black.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_black_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_black_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-black-black-dots-marked-saddled\": \"horse_baby_black_black_dots_marked_saddle\""));
    }
    @Test
    void babyGrayWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-baby-gray-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.GRAY, Markings.WHITE_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyGrayWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_gray_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_gray_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-gray-white-dots-marked-saddled\": \"horse_baby_gray_white_dots_marked_saddle\""));
    }
    @Test
    void babyGrayWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-baby-gray-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.GRAY, Markings.WHITE)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyGrayWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_gray_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_gray_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-gray-white-marked-saddled\": \"horse_baby_gray_white_marked_saddle\""));
    }
    @Test
    void babyGrayWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-baby-gray-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.GRAY, Markings.WHITE_FIELD)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_FIELD"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyGrayWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_gray_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_gray_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-gray-white-field-marked-saddled\": \"horse_baby_gray_white_field_marked_saddle\""));
    }
    @Test
    void babyGrayBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-baby-gray-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.GRAY, Markings.BLACK_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.BLACK_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyGrayBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_gray.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_gray_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_gray_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-gray-black-dots-marked-saddled\": \"horse_baby_gray_black_dots_marked_saddle\""));
    }
    @Test
    void babyDarkBrownWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-baby-dark-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.DARK_BROWN, Markings.WHITE_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyDarkBrownWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_dark_brown_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_dark_brown_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-dark-brown-white-dots-marked-saddled\": \"horse_baby_dark_brown_white_dots_marked_saddle\""));
    }
    @Test
    void babyDarkBrownWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-baby-dark-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.DARK_BROWN, Markings.WHITE)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyDarkBrownWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_dark_brown_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_dark_brown_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-dark-brown-white-marked-saddled\": \"horse_baby_dark_brown_white_marked_saddle\""));
    }
    @Test
    void babyDarkBrownWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-baby-dark-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.DARK_BROWN, Markings.WHITE_FIELD)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_FIELD"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyDarkBrownWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_dark_brown_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_dark_brown_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-dark-brown-white-field-marked-saddled\": \"horse_baby_dark_brown_white_field_marked_saddle\""));
    }
    @Test
    void babyDarkBrownBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-baby-dark-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.DARK_BROWN, Markings.BLACK_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.BLACK_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyDarkBrownBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_darkbrown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_dark_brown_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_dark_brown_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-dark-brown-black-dots-marked-saddled\": \"horse_baby_dark_brown_black_dots_marked_saddle\""));
    }

    @Test
    void babyBrownWhiteDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitedots.png"));
        assertTrue(capture.contains("\"horse-baby-brown-white-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BROWN, Markings.WHITE_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyBrownWhiteDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitedots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_brown_white_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_brown_white_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-brown-white-dots-marked-saddled\": \"horse_baby_brown_white_dots_marked_saddle\""));
    }

    @Test
    void babyBrownWhiteMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-baby-brown-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BROWN, Markings.WHITE)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyBrownWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_brown_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_brown_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-brown-white-marked-saddled\": \"horse_baby_brown_white_marked_saddle\""));
    }

    @Test
    void babyBrownWhiteFieldMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-baby-brown-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BROWN, Markings.WHITE_FIELD)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_FIELD"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyBrownWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_brown_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_brown_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-brown-white-field-marked-saddled\": \"horse_baby_brown_white_field_marked_saddle\""));
    }

    @Test
    void babyBrownBlackDotsMarkedHorseFixtureRequiresCoatMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-baby-brown-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.BROWN, Markings.BLACK_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.BLACK_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyBrownBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_brown.png"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_brown_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_brown_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-brown-black-dots-marked-saddled\": \"horse_baby_brown_black_dots_marked_saddle\""));
    }

    @Test
    void babyWhiteMarkedHorseFixtureRequiresBabyMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_white.png"));
        assertTrue(capture.contains("\"horse-baby-white-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.WHITE, Markings.WHITE)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyWhiteMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_white.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_white_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_white_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-white-marked-saddled\": \"horse_baby_white_marked_saddle\""));
        assertTrue(harness.contains("else \"marking\" if scenario_name in {\"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\"}"));
    }

    @Test
    void babyWhiteFieldMarkedHorseFixtureRequiresBabyMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_whitefield.png"));
        assertTrue(capture.contains("\"horse-baby-white-field-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.WHITE, Markings.WHITE_FIELD)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.WHITE_FIELD"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyWhiteFieldMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_whitefield.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_white_field_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_white_field_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-white-field-marked-saddled\": \"horse_baby_white_field_marked_saddle\""));
        assertTrue(harness.contains("else \"marking\" if scenario_name in {\"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\"}"));
    }

    @Test
    void babyBlackDotsMarkedHorseFixtureRequiresBabyMarkingAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String markingLayer = source("src/main/java/net/minecraft/client/renderer/entity/layers/HorseMarkingLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String worldRenderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.HORSE_BABY"));
        assertTrue(renderer.contains("ModelLayers.HORSE_BABY_SADDLE"));
        assertTrue(markingLayer.contains("horse_markings_blackdots.png"));
        assertTrue(capture.contains("\"horse-baby-black-dots-marked-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("configureHorseVariantAndMarkings(horse, Variant.WHITE, Markings.BLACK_DOTS)"));
        assertTrue(capture.contains("horse.getMarkings() == Markings.BLACK_DOTS"));
        assertTrue(capture.contains("horse.getBodyArmorItem().isEmpty()"));
        assertTrue(capture.contains("horseBabyBlackDotsMarkedSaddleFixtureJson"));
        assertTrue(worldRenderer.contains("textures/entity/horse/horse_markings_blackdots.png"));
        assertTrue(worldRenderer.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_baby_black_dots_marked_saddle_emission_evidence"));
        assertTrue(harness.contains("horse_baby_black_dots_marked_saddle_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-baby-black-dots-marked-saddled\": \"horse_baby_black_dots_marked_saddle\""));
        assertTrue(harness.contains("else \"marking\" if scenario_name in {\"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\"}"));
    }

    @Test
    void dyedLeatherHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("new ItemStack(Items.LEATHER_HORSE_ARMOR)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, leatherArmor"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.LEATHER_HORSE_ARMOR)"));
        assertTrue(capture.contains("clientBodyDyeRgb"));
        assertTrue(capture.contains("0x3366CC"));
        assertTrue(capture.contains("horseDyedLeatherEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/leather.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_dyed_leather_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_dyed_leather_equipment_fixture_equivalence"));
		assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
    }

    @Test
    void creamyDyedLeatherHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-creamy-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("new ItemStack(Items.LEATHER_HORSE_ARMOR)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, leatherArmor"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.LEATHER_HORSE_ARMOR)"));
        assertTrue(capture.contains("clientBodyDyeRgb"));
        assertTrue(capture.contains("0x3366CC"));
        assertTrue(capture.contains("horseCreamyDyedLeatherEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/leather.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_creamy_dyed_leather_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_creamy_dyed_leather_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-creamy-dyed-leather-equipped\": \"horse_creamy_dyed_leather_equipment\""));
    }

    @Test
    void chestnutDyedLeatherHorseFixtureRequiresExactBodyArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/HorseRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.HORSE_SADDLE"));
        assertTrue(capture.contains("\"horse-chestnut-dyed-leather-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("new ItemStack(Items.LEATHER_HORSE_ARMOR)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, leatherArmor"));
        assertTrue(capture.contains("horse.getBodyArmorItem().is(Items.LEATHER_HORSE_ARMOR)"));
        assertTrue(capture.contains("clientBodyDyeRgb"));
        assertTrue(capture.contains("0x3366CC"));
        assertTrue(capture.contains("horseChestnutDyedLeatherEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/horse_body/leather.png"));
        assertTrue(rust.contains("textures/entity/equipment/horse_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_horse_chestnut_dyed_leather_equipment_emission_evidence"));
        assertTrue(harness.contains("horse_chestnut_dyed_leather_equipment_fixture_equivalence"));
        assertTrue(harness.contains("\"horse-chestnut-dyed-leather-equipped\": \"horse_chestnut_dyed_leather_equipment\""));
    }

    @Test
    void camelFixtureRequiresStableStandingAdultAndExactSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/CamelRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.CAMEL_SADDLE"));
        assertTrue(renderer.contains("new CamelSaddleModel"));
        assertTrue(capture.contains("\"camel-saddled\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("camel.standUpInstantly()"));
        assertTrue(capture.contains("camel.setDashing(false)"));
        assertTrue(capture.contains("camel.getJumpCooldown() == 0"));
        assertTrue(capture.contains("camelSaddleFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/camel_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_camel_saddle_emission_evidence"));
        assertTrue(harness.contains("camel_saddle_fixture_equivalence"));
        assertTrue(harness.contains("args.world_mesh_model_scenario in {\"pig-saddled\", \"horse-saddled\", \"horse-creamy-saddled\", \"horse-chestnut-saddled\", \"horse-brown-saddled\", \"horse-black-saddled\", \"horse-gray-saddled\", \"horse-dark-brown-saddled\", \"horse-baby-saddled\", \"horse-baby-creamy-saddled\", \"horse-baby-chestnut-saddled\", \"horse-baby-brown-saddled\", \"horse-baby-black-saddled\", \"horse-baby-gray-saddled\", \"horse-baby-dark-brown-saddled\", \"horse-baby-diamond-equipped\", \"horse-baby-copper-equipped\", \"horse-baby-iron-equipped\", \"horse-baby-gold-equipped\", \"horse-baby-netherite-equipped\", \"horse-baby-dyed-leather-equipped\", \"horse-white-dots-marked-saddled\", \"horse-white-marked-saddled\", \"horse-white-field-marked-saddled\", \"horse-black-dots-marked-saddled\", \"horse-creamy-white-dots-marked-saddled\", \"horse-brown-white-dots-marked-saddled\", \"horse-black-white-dots-marked-saddled\", \"horse-gray-white-dots-marked-saddled\", \"horse-dark-brown-white-dots-marked-saddled\", \"horse-chestnut-white-dots-marked-saddled\", \"horse-creamy-white-marked-saddled\", \"horse-brown-white-marked-saddled\", \"horse-black-white-marked-saddled\", \"horse-gray-white-marked-saddled\", \"horse-dark-brown-white-marked-saddled\", \"horse-chestnut-white-marked-saddled\", \"horse-creamy-white-field-marked-saddled\", \"horse-brown-white-field-marked-saddled\", \"horse-black-white-field-marked-saddled\", \"horse-gray-white-field-marked-saddled\", \"horse-dark-brown-white-field-marked-saddled\", \"horse-chestnut-white-field-marked-saddled\", \"horse-creamy-black-dots-marked-saddled\", \"horse-brown-black-dots-marked-saddled\", \"horse-black-black-dots-marked-saddled\", \"horse-gray-black-dots-marked-saddled\", \"horse-dark-brown-black-dots-marked-saddled\", \"horse-chestnut-black-dots-marked-saddled\", \"horse-baby-marked-saddled\", \"horse-baby-white-marked-saddled\", \"horse-baby-white-field-marked-saddled\", \"horse-baby-black-dots-marked-saddled\", \"horse-baby-creamy-white-dots-marked-saddled\", \"horse-baby-creamy-white-marked-saddled\", \"horse-baby-creamy-white-field-marked-saddled\", \"horse-baby-creamy-black-dots-marked-saddled\", \"horse-baby-chestnut-white-dots-marked-saddled\", \"horse-baby-chestnut-white-marked-saddled\", \"horse-baby-chestnut-white-field-marked-saddled\", \"horse-baby-chestnut-black-dots-marked-saddled\", \"horse-baby-black-white-dots-marked-saddled\", \"horse-baby-black-white-marked-saddled\", \"horse-baby-black-white-field-marked-saddled\", \"horse-baby-black-black-dots-marked-saddled\", \"horse-baby-gray-white-dots-marked-saddled\", \"horse-baby-gray-white-marked-saddled\", \"horse-baby-gray-white-field-marked-saddled\", \"horse-baby-gray-black-dots-marked-saddled\", \"horse-baby-dark-brown-white-dots-marked-saddled\", \"horse-baby-dark-brown-white-marked-saddled\", \"horse-baby-dark-brown-white-field-marked-saddled\", \"horse-baby-dark-brown-black-dots-marked-saddled\", \"horse-baby-brown-white-dots-marked-saddled\", \"horse-baby-brown-white-marked-saddled\", \"horse-baby-brown-white-field-marked-saddled\", \"horse-baby-brown-black-dots-marked-saddled\", \"horse-diamond-equipped\", \"horse-creamy-diamond-equipped\", \"horse-chestnut-diamond-equipped\", \"horse-chestnut-copper-equipped\", \"horse-chestnut-iron-equipped\", \"horse-chestnut-gold-equipped\", \"horse-chestnut-netherite-equipped\", \"horse-creamy-copper-equipped\", \"horse-creamy-iron-equipped\", \"horse-creamy-gold-equipped\", \"horse-creamy-netherite-equipped\", \"horse-copper-equipped\", \"horse-iron-equipped\", \"horse-gold-equipped\", \"horse-netherite-equipped\", \"horse-dyed-leather-equipped\", \"horse-creamy-dyed-leather-equipped\", \"horse-chestnut-dyed-leather-equipped\", \"donkey-saddled\", \"donkey-baby-saddled\", \"donkey-chested-saddled\", \"donkey-baby-chested-saddled\", \"mule-saddled\", \"mule-chested-saddled\", \"mule-baby-chested-saddled\", \"mule-baby-saddled\", \"strider-saddled\", \"camel-saddled\", \"nautilus-equipped\", \"nautilus-baby-equipped\", \"nautilus-copper-equipped\", \"nautilus-iron-equipped\", \"nautilus-gold-equipped\", \"nautilus-netherite-equipped\", \"zombie-nautilus-equipped\", \"zombie-nautilus-coral-equipped\"}"));
    }

    @Test
    void nautilusFixtureRequiresAdultTameDiamondArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/NautilusRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.NAUTILUS_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.NAUTILUS_SADDLE"));
        assertTrue(renderer.contains("new NautilusArmorModel"));
        assertTrue(renderer.contains("new NautilusSaddleModel"));
        assertTrue(capture.contains("\"nautilus-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("nautilus.setTame(true, true)"));
        assertTrue(capture.contains("EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_NAUTILUS_ARMOR)"));
        assertTrue(capture.contains("EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE)"));
        assertTrue(capture.contains("nautilusEquipmentFixtureJson"));
        assertTrue(rust.contains("textures/entity/equipment/nautilus_body/diamond.png"));
        assertTrue(rust.contains("textures/entity/equipment/nautilus_saddle/saddle.png"));
        assertTrue(harness.contains("frozen_nautilus_equipment_emission_evidence"));
        assertTrue(harness.contains("nautilus_equipment_fixture_equivalence"));
        assertTrue(harness.contains("additional_composition_layer_identities = [layer_identity]"));
    }

    @Test
    void babyNautilusFixtureRequiresBabyModelTextureAndEquipmentComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/NautilusRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("ModelLayers.NAUTILUS_BABY"));
        assertTrue(renderer.contains("textures/entity/nautilus/nautilus_baby.png"));
        assertTrue(capture.contains("\"nautilus-baby-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("nautilusBabyEquipmentFixtureJson"));
        assertTrue(rust.contains("minecraft:textures/entity/nautilus/nautilus_baby.png"));
        assertTrue(harness.contains("frozen_nautilus_baby_equipment_emission_evidence"));
        assertTrue(harness.contains("nautilus_baby_equipment_fixture_equivalence"));
    }

    @Test
    void copperNautilusFixtureRequiresExactArmorItemTextureAndComposition() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(capture.contains("\"nautilus-copper-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("Items.COPPER_NAUTILUS_ARMOR"));
        assertTrue(capture.contains("nautilusCopperEquipmentFixtureJson"));
        assertTrue(rust.contains("minecraft:textures/entity/equipment/nautilus_body/copper.png"));
        assertTrue(harness.contains("frozen_nautilus_copper_equipment_emission_evidence"));
        assertTrue(harness.contains("nautilus_copper_equipment_fixture_equivalence"));
    }

    @Test
    void ironNautilusFixtureRequiresExactArmorItemTextureAndComposition() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(capture.contains("\"nautilus-iron-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("Items.IRON_NAUTILUS_ARMOR"));
        assertTrue(capture.contains("nautilusIronEquipmentFixtureJson"));
        assertTrue(rust.contains("minecraft:textures/entity/equipment/nautilus_body/iron.png"));
        assertTrue(harness.contains("frozen_nautilus_iron_equipment_emission_evidence"));
        assertTrue(harness.contains("nautilus_iron_equipment_fixture_equivalence"));
    }

    @Test
    void goldNautilusFixtureRequiresGoldenItemAndGoldTextureComposition() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(capture.contains("\"nautilus-gold-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("Items.GOLDEN_NAUTILUS_ARMOR"));
        assertTrue(capture.contains("nautilusGoldEquipmentFixtureJson"));
        assertTrue(rust.contains("minecraft:textures/entity/equipment/nautilus_body/gold.png"));
        assertTrue(harness.contains("minecraft:golden_nautilus_armor"));
        assertTrue(harness.contains("frozen_nautilus_gold_equipment_emission_evidence"));
        assertTrue(harness.contains("nautilus_gold_equipment_fixture_equivalence"));
    }

    @Test
    void netheriteNautilusFixtureRequiresNetheriteItemAndTextureComposition() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(capture.contains("\"nautilus-netherite-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("Items.NETHERITE_NAUTILUS_ARMOR"));
        assertTrue(capture.contains("nautilusNetheriteEquipmentFixtureJson"));
        assertTrue(rust.contains("minecraft:textures/entity/equipment/nautilus_body/netherite.png"));
        assertTrue(harness.contains("minecraft:netherite_nautilus_armor"));
        assertTrue(harness.contains("frozen_nautilus_netherite_equipment_emission_evidence"));
        assertTrue(harness.contains("nautilus_netherite_equipment_fixture_equivalence"));
    }

    @Test
    void zombieNautilusFixtureRequiresTemperateAdultDiamondArmorAndSaddleComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/ZombieNautilusRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.NAUTILUS_BODY"));
        assertTrue(renderer.contains("EquipmentClientInfo.LayerType.NAUTILUS_SADDLE"));
        assertTrue(capture.contains("\"zombie-nautilus-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("ZombieNautilusVariants.TEMPERATE"));
        assertTrue(capture.contains("zombieNautilusEquipmentFixtureJson"));
        assertTrue(rust.contains("minecraft:textures/entity/nautilus/zombie_nautilus.png"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:zombie_nautilus\", \"minecraft:zombie_nautilus\""));
        assertTrue(harness.contains("frozen_zombie_nautilus_equipment_emission_evidence"));
        assertTrue(harness.contains("zombie_nautilus_equipment_fixture_equivalence"));
    }

    @Test
    void coralZombieNautilusFixtureRequiresWarmVariantModelAndEquipmentComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/ZombieNautilusRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(renderer.contains("new ZombieNautilusCoralModel"));
        assertTrue(renderer.contains("ModelType.WARM"));
        assertTrue(capture.contains("\"zombie-nautilus-coral-equipped\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("ZombieNautilusVariants.WARM"));
        assertTrue(capture.contains("zombieNautilusCoralEquipmentFixtureJson"));
        assertTrue(rust.contains("minecraft:textures/entity/nautilus/zombie_nautilus_coral.png"));
        assertTrue(harness.contains("frozen_zombie_nautilus_coral_equipment_emission_evidence"));
        assertTrue(harness.contains("zombie_nautilus_coral_equipment_fixture_equivalence"));
    }

    @Test
    void wolfCollarFixtureRequiresTintedUnarmoredComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/WolfCollarLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("enqueueStandaloneModelMesh"));
        assertTrue(layer.contains("ResourceLocation.withDefaultNamespace(\"wolf_collar\")"));
        assertTrue(layer.contains("OverlayTexture.NO_OVERLAY, j, wolfRenderState.outlineColor"));
        assertTrue(layer.contains("recordModelMeshRouteDecision"));
        assertTrue(capture.contains("\"wolf-collar\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("DataComponents.WOLF_COLLAR, DyeColor.RED"));
        assertTrue(capture.contains("wolfCollarFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:wolf\", \"minecraft:wolf_collar\")"));
        assertTrue(harness.contains("frozen_wolf_collar_emission_evidence"));
    }

    @Test
    void ironGolemHighCracksRequireBodyAndOverlayComposition() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/IronGolemCrackinessLayer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");

        assertTrue(layer.contains("enqueueStandaloneModelMesh"));
        assertTrue(layer.contains("ResourceLocation.withDefaultNamespace(\"iron_golem_cracks\")"));
        assertTrue(layer.contains("LivingEntityRenderer.getOverlayCoords(ironGolemRenderState, 0.0F), -1"));
        assertTrue(layer.contains("Rust whole-frame iron-golem-cracks route has no copied semantic mesh"));
        assertTrue(capture.contains("new IronGolem(EntityType.IRON_GOLEM"));
        assertTrue(capture.contains("Crackiness.Level.HIGH"));
        assertTrue(capture.contains("ironGolemCracksFixtureJson"));
        assertTrue(rust.contains("new ModelComposition(\"minecraft:iron_golem\", \"minecraft:iron_golem_cracks\")"));
        assertTrue(harness.contains("frozen_iron_golem_cracks_emission_evidence"));
    }

    @Test
    void stuckInBodyProjectileLayersRetainExplicitTextureIdentity() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/StuckInBodyLayer.java");
        String bee = source("src/main/java/net/minecraft/client/renderer/entity/layers/BeeStingerLayer.java");
        String arrow = source("src/main/java/net/minecraft/client/renderer/entity/layers/ArrowLayer.java");
        assertTrue(layer.contains("submitModelSemanticTexture"));
        assertTrue(layer.contains("this.texture"));
        assertTrue(bee.contains("BEE_STINGER_LOCATION"));
        assertTrue(arrow.contains("NORMAL_ARROW_LOCATION"));
    }

    @Test
    void foilEquipmentKeepsItsBaseLayerAndExplicitGlintRoute() throws IOException {
        String equipment = source("src/main/java/net/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer.java");
        String submit = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        assertTrue(equipment.contains("RenderType.armorCutoutNoCull(resourceLocation2)"));
        assertTrue(equipment.contains("submitModelSemanticTexture"));
        assertTrue(equipment.contains("RenderType.armorEntityGlint()"),
            "glint remains a distinct semantic material selector");
        assertTrue(submit.contains("armor_entity_glint")
                && submit.contains("enqueueStandaloneGlintModelMesh"),
            "armor glint must be admitted through the explicit Rust glint mesh contract");
    }

    @Test
    void spinAttackOverlayDeclaresItsFixedSemanticTexture() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/SpinAttackEffectLayer.java");
        assertTrue(layer.contains("submitModelSemanticTexture"));
        assertTrue(layer.contains("TEXTURE"));
    }

    @Test
    void happyGhastRopesCarryExplicitTextureIdentityBeforeModelAdmission() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/RopesLayer.java");
        assertTrue(layer.contains("ropeTexture"));
        assertTrue(layer.contains("submitModelSemanticTexture"));
        assertTrue(layer.contains("this.ropeTexture"));
    }

    @Test
    void happyGhastsComposeRustBodyVariantsWithSemanticHarnessAndRopes() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String happyRenderer = source("src/main/java/net/minecraft/client/renderer/entity/HappyGhastRenderer.java");
        assertTrue(renderer.contains("isVanillaHappyGhastModelMeshEligible"));
        assertTrue(rust.contains("HappyGhastModel.class"));
        assertTrue(rust.contains("HappyGhastHarnessModel"));
        assertTrue(rust.contains("happy_ghast.png"));
        assertTrue(rust.contains("happy_ghast_baby.png"));
        assertFalse(rust.substring(rust.indexOf("isVanillaHappyGhastModelMeshEligible"), rust.indexOf("\n\tpublic static boolean", rust.indexOf("isVanillaHappyGhastModelMeshEligible") + 1)).contains("state.bodyItem"));
        assertTrue(happyRenderer.contains("EquipmentClientInfo.LayerType.HAPPY_GHAST_BODY"));
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        assertTrue(level.contains("HappyGhastRenderState happyGhastRenderState"));
        assertFalse(level.contains("pigRenderState.saddle.isEmpty()"));
    }

    @Test
    void slimeOuterFallbackRetainsExplicitTextureIdentity() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/SlimeOuterLayer.java");
        assertTrue(layer.contains("enqueueStandaloneModelMesh"));
        assertTrue(layer.contains("submitModelSemanticTexture"));
        assertTrue(layer.contains("SlimeRenderer.SLIME_LOCATION"));
    }

    @Test
    void shoulderParrotsDeclareVariantTextureIdentity() throws IOException {
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/ParrotOnShoulderLayer.java");
        assertTrue(layer.contains("submitModelSemanticTexture"));
        assertTrue(layer.contains("ResourceLocation textureIdentity"));
        assertTrue(layer.contains("ParrotRenderer.getVariantTexture(variant)"));
    }

    @Test
    void playerCapeAndEarsCompatibilitySubmitsRetainDynamicTextureIdentity() throws IOException {
        String cape = source("src/main/java/net/minecraft/client/renderer/entity/layers/CapeLayer.java");
        String ears = source("src/main/java/net/minecraft/client/renderer/entity/layers/Deadmau5EarsLayer.java");
        assertTrue(cape.contains("submitModelSemanticTexture"));
        assertTrue(cape.contains("texture"));
        assertTrue(ears.contains("submitModelSemanticTexture"));
        assertTrue(ears.contains("texture"));
    }

    @Test
    void bookBlockEntitiesUseRustAnimatedBookModelRoute() throws IOException {
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String enchant = source("src/main/java/net/minecraft/client/renderer/blockentity/EnchantTableRenderer.java");
        String lectern = source("src/main/java/net/minecraft/client/renderer/blockentity/LecternRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.BookModel"));
        assertTrue(level.contains("EnchantTableRenderState"));
        assertTrue(level.contains("LecternRenderState"));
        assertTrue(level.contains("enchanting_table_book"));
        assertTrue(enchant.contains("BOOK_LOCATION"));
        assertTrue(lectern.contains("EnchantTableRenderer.BOOK_LOCATION"));
        assertTrue(capture.contains("case \"enchanting-table\" -> Blocks.ENCHANTING_TABLE.defaultBlockState()"));
        assertTrue(capture.contains("case \"enchanting-table\", \"lectern\" -> \"minecraft:entity/enchanting_table_book\""));
        assertTrue(capture.contains("case \"enchanting-table\", \"lectern\" -> \"minecraft:textures/atlas/blocks.png\""));
        assertTrue(capture.contains("case \"lectern\" -> Blocks.LECTERN.defaultBlockState()"));
        assertTrue(capture.contains("lectern.setBook(new ItemStack(Items.WRITABLE_BOOK))"));
    }

    @Test
    void armorTrimSubmitsUseTheExplicitAtlasModelContract() throws IOException {
        String equipment = source("src/main/java/net/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String sheets = source("src/main/java/net/minecraft/client/renderer/Sheets.java");
        assertTrue(equipment.contains("Sheets.armorTrimsSheet"));
        assertTrue(equipment.contains("textureAtlasSprite"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.HumanoidModel"));
        assertTrue(rust.contains("armor_decal_cutout_no_cull"));
        assertTrue(rust.contains("RenderPipelines.ARMOR_DECAL_CUTOUT_NO_CULL"));
        assertTrue(rust.contains("sprite.sodium$hasUnknownImageContents()"));
        assertTrue(sheets.contains("ARMOR_TRIMS_SHEET"));
    }

    @Test
    void signsUseRustBoardMeshesWithSemanticText() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String signs = source("src/main/java/net/minecraft/client/renderer/blockentity/AbstractSignRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(rust.contains("model instanceof Model.Simple"));
        assertTrue(level.contains("state == net.minecraft.util.Unit.INSTANCE"));
        assertTrue(level.contains("entity/signs/"));
        assertTrue(level.contains("SignRenderState"));
        assertTrue(signs.contains("this.submitSignText"));
        assertTrue(capture.contains("case \"oak-sign\", \"oak-sign-glowing-back\", \"oak-sign-breaking\" -> Blocks.OAK_SIGN.defaultBlockState()"));
        assertTrue(capture.contains("Component.literal(\"Rust Vulkan\")"));
        assertTrue(capture.contains("Component.literal(\"Frozen parity\")"));
        assertTrue(capture.contains("Component.literal(\"Back face\")"));
        assertTrue(capture.contains("Component.literal(\"Glow parity\")"));
        assertTrue(capture.contains("setHasGlowingText(true)"));
		assertTrue(capture.contains("destroyBlockProgress(-0x4d415454, modelMeshSetupPosition, MODEL_MESH_DESTROY_STAGE)"));
		assertTrue(capture.contains("mattmc.dev.rustGalWorldMesh.destroyStage"));
		assertTrue(capture.contains("stage < 0 || stage > 9"));
		assertTrue(capture.contains("modelMeshDestroyTextureId().equals(diagnostic.textureId())"));
        assertTrue(rust.contains("fullBrightSubmits"));
        assertTrue(rust.contains("outlinedSubmits"));
    }

    @Test
    void campfireItemsUseTheBlockEntityIndexedItemContract() throws IOException {
        String dispatcher = source("src/main/java/net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher.java");
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/CampfireRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(dispatcher.contains("beginBlockEntityItemSubmission"));
        assertTrue(dispatcher.contains("beginBlockEntitySubmission(blockEntityId)"));
        assertTrue(renderer.contains("itemStackRenderState.submit"));
        assertTrue(rust.contains("PendingMeshProducer.BLOCK_ENTITY_ITEM"));
        assertTrue(rust.contains("producerSemanticIdentity = BuiltInRegistries.BLOCK.getKey"));
        assertTrue(rust.contains("|| \"block-entity-item\".equals(producer)"));
        assertTrue(capture.contains("case \"campfire\" -> Blocks.CAMPFIRE.defaultBlockState()"));
        assertTrue(capture.contains("campfire.getItems().set(0, new ItemStack(Items.BEEF))"));
    }

    @Test
    void shelfItemsUseTheirOwnBlockEntityIdentityAndStableCenterSlot() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/ShelfRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("itemStackRenderState.submit"));
        assertTrue(rust.contains("PendingMeshProducer.BLOCK_ENTITY_ITEM"));
        assertTrue(capture.contains("case \"oak-shelf\" -> Blocks.OAK_SHELF.defaultBlockState()"));
        assertTrue(capture.contains("ShelfBlock.FACING, Direction.SOUTH"));
        assertTrue(capture.contains("shelf.getItems().set(1, new ItemStack(Items.BEEF))"));
        assertTrue(capture.contains("\"minecraft:oak_shelf\""));
    }

    @Test
    void brushableItemsUseTheirOwnBlockEntityIdentityAndExposedState() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/BrushableBlockRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("brushableBlockRenderState.itemState.submit"));
        assertTrue(capture.contains("case \"brushable-block\" -> Blocks.SUSPICIOUS_SAND.defaultBlockState()"));
        assertTrue(capture.contains("BlockStateProperties.DUSTED, 3"));
        assertTrue(capture.contains("output.store(\"item\", ItemStack.CODEC, new ItemStack(Items.BEEF))"));
        assertTrue(capture.contains("output.store(\"hit_direction\", Direction.LEGACY_ID_CODEC, Direction.SOUTH)"));
        assertTrue(capture.contains("\"minecraft:suspicious_sand\""));
    }

    @Test
    void vaultItemsUseTheirOwnBlockEntityIdentityAndStableUnlockingState() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/VaultRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("ItemEntityRenderer.renderMultipleFromCount"));
        assertTrue(capture.contains("case \"vault-unlocking\" -> Blocks.VAULT.defaultBlockState()"));
        assertTrue(capture.contains("VaultState.UNLOCKING"));
        assertTrue(capture.contains("serverData.putLong(\"state_updating_resumes_at\", Long.MAX_VALUE)"));
        assertTrue(capture.contains("vault.getSharedData().setDisplayItem(new ItemStack(Items.BEEF))"));
        assertTrue(capture.contains("case \"vault-unlocking\" -> \"minecraft:vault\""));
    }

    @Test
    void bellUsesStableFloorStateAndCompletedBlockEntityScopedModelMesh() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/BellRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("submitNodeCollector.submitModelSemantic("));
        assertTrue(capture.contains("case \"bell\", \"bell-shaking\" -> Blocks.BELL.defaultBlockState()"));
        assertTrue(capture.contains("BellBlock.FACING, Direction.SOUTH"));
        assertTrue(capture.contains("BellBlock.ATTACHMENT"));
        assertTrue(capture.contains("BellAttachType.FLOOR"));
        assertTrue(capture.contains("\"bell-shaking\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("bell.onHit(Direction.EAST)"));
        assertTrue(capture.contains("bell.shaking && bell.clickDirection == Direction.EAST"));
        assertTrue(capture.contains("isBlockEntityModelScenario()"));
        assertTrue(capture.contains("!isBlockEntityModelScenario() || diagnostic.blockEntityId() >= 0"));
    }

    @Test
    void bedFixtureRequiresRealHeadAndFootBlockEntitiesAndCompletedRustMeshes() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/BedRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("submitNodeCollector.submitModelSemantic("));
        assertTrue(capture.contains("case \"bed\" -> Blocks.RED_BED.defaultBlockState()"));
        assertTrue(capture.contains("BedPart.FOOT"));
        assertTrue(capture.contains("BedPart.HEAD"));
        assertTrue(capture.contains("position.relative(Direction.SOUTH)"));
        assertTrue(capture.contains("copiedBlockEntities.size() != 2"));
        assertTrue(capture.contains("executedBlockEntities.containsAll(copiedBlockEntities)"));
    }

    @Test
    void chestFixtureRequiresClosedSingleChestAndCompletedBlockEntityScopedModel() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/ChestRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("submitNodeCollector.submitModelSemantic("));
        assertTrue(capture.contains("case \"chest\" -> Blocks.CHEST.defaultBlockState()"));
        assertTrue(capture.contains("ChestBlock.FACING, Direction.SOUTH"));
        assertTrue(capture.contains("ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.SINGLE"));
        assertTrue(capture.contains("isChestModelScenario() || isBellModelScenario() || isBedModelScenario()"));
        assertTrue(capture.contains("!isBlockEntityModelScenario() || diagnostic.blockEntityId() >= 0"));
    }

    @Test
    void doubleChestFixtureRequiresLinkedLeftAndRightBlockEntitiesAndCompletedRustMeshes() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/ChestRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("this.doubleLeftModel"));
        assertTrue(renderer.contains("this.doubleRightModel"));
        assertTrue(capture.contains("case \"chest-double\" -> Blocks.CHEST.defaultBlockState()"));
        assertTrue(capture.contains("ChestType.RIGHT"));
        assertTrue(capture.contains("ChestType.LEFT"));
        assertTrue(capture.contains("position.relative(Direction.EAST)"));
        assertTrue(capture.contains("isBedModelScenario() || isDoubleChestModelScenario()"));
        assertTrue(capture.contains("copiedBlockEntities.size() != 2"));
        assertTrue(capture.contains("executedBlockEntities.containsAll(copiedBlockEntities)"));
    }

    @Test
    void trappedChestFixtureRequiresRegisteredBlockEntityAndTrappedMaterial() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/ChestRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("blockEntity instanceof TrappedChestBlockEntity"));
        assertTrue(renderer.contains("ChestRenderState.ChestMaterialType.TRAPPED"));
        assertTrue(capture.contains("case \"chest-trapped\" -> Blocks.TRAPPED_CHEST.defaultBlockState()"));
        assertTrue(capture.contains("instanceof net.minecraft.world.level.block.entity.TrappedChestBlockEntity"));
        assertTrue(capture.contains("case \"chest-trapped\" -> \"minecraft:entity/chest/trapped\""));
        assertTrue(capture.contains("isTrappedChestModelScenario()"));
    }

    @Test
    void enderChestFixtureRequiresRegisteredBlockEntityAndEnderMaterial() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/ChestRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("blockEntity instanceof EnderChestBlockEntity"));
        assertTrue(renderer.contains("ChestRenderState.ChestMaterialType.ENDER_CHEST"));
        assertTrue(capture.contains("case \"chest-ender\" -> Blocks.ENDER_CHEST.defaultBlockState()"));
        assertTrue(capture.contains("instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity"));
        assertTrue(capture.contains("case \"chest-ender\" -> \"minecraft:entity/chest/ender\""));
        assertTrue(capture.contains("isEnderChestModelScenario()"));
    }

    @Test
    void shulkerFixtureRequiresClosedPurpleBlockEntityAndCompletedScopedModel() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/ShulkerBoxRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("submitNodeCollector.submitModelSemantic("));
        assertTrue(capture.contains("case \"shulker\", \"shulker-open-east\" -> Blocks.PURPLE_SHULKER_BOX.defaultBlockState()"));
        assertTrue(capture.contains("case \"shulker-default\" -> Blocks.SHULKER_BOX.defaultBlockState()"));
        assertTrue(capture.contains("isOpenedEastShulkerModelScenario() ? Direction.EAST : Direction.UP"));
        assertTrue(capture.contains("shulker.getColor() == net.minecraft.world.item.DyeColor.PURPLE"));
        assertTrue(capture.contains("ShulkerBoxBlockEntity.AnimationStatus.CLOSED"));
        assertTrue(capture.contains("shulker.getProgress(1.0F) == (openedEast ? 1.0F : 0.0F)"));
        assertTrue(capture.contains("ShulkerBoxBlockEntity.AnimationStatus.OPENED"));
        assertTrue(capture.contains("serverLevel.blockEvent(position, state.getBlock(), 1, 1)"));
        assertTrue(capture.contains("case \"shulker-default\" -> \"minecraft:entity/shulker/shulker\""));
        assertTrue(capture.contains("|| isShulkerModelScenario()"));
    }

    @Test
    void decoratedPotFixtureRequiresEmptySevenPartCompositionAndScopedCompletion() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/DecoratedPotRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("this.neck, poseStack, renderType"));
        assertTrue(renderer.contains("this.top, poseStack, renderType"));
        assertTrue(renderer.contains("this.bottom, poseStack, renderType"));
        assertTrue(renderer.contains("this.frontSide, poseStack, material.renderType"));
        assertTrue(renderer.contains("this.backSide, poseStack, material2.renderType"));
        assertTrue(renderer.contains("this.leftSide, poseStack, material3.renderType"));
        assertTrue(renderer.contains("this.rightSide, poseStack, material4.renderType"));
        assertTrue(capture.contains("\"decorated-pot-north\", \"decorated-pot-east\", \"decorated-pot-west\" -> Blocks.DECORATED_POT.defaultBlockState()"));
        assertTrue(capture.contains("BlockStateProperties.HORIZONTAL_FACING, expectedDecoratedPotFacing()"));
        assertTrue(capture.contains("pot.getDecorations().equals(expectedDecoratedPotDecorations())"));
        assertTrue(capture.contains("pot.lastWobbleStyle == null"));
        assertTrue(capture.contains("baseParts < 3L || sideParts < 4L || !sidesComplete"));
        assertTrue(capture.contains("copied.size() < 7"));
        assertTrue(capture.contains("diagnostic.instances() >= (isDecoratedPotModelScenario() ? 7 : isActiveConduitModelScenario() ? 4"));
        assertTrue(capture.contains("|| isDecoratedPotModelScenario()"));
        assertTrue(capture.contains("Items.ANGLER_POTTERY_SHERD, Items.ARCHER_POTTERY_SHERD"));
        assertTrue(capture.contains("Items.ARMS_UP_POTTERY_SHERD, Items.BLADE_POTTERY_SHERD"));
        assertTrue(capture.contains("minecraft:entity/decorated_pot/blade_pottery_pattern"));
        assertTrue(capture.contains("pot.applyComponentsFromItemStack(potItem)"));
        assertTrue(capture.contains("pot.wobble(expectedDecoratedPotWobbleStyle())"));
        assertTrue(capture.contains("WobbleStyle.NEGATIVE"));
        assertTrue(capture.contains("WobbleStyle.POSITIVE"));
        assertTrue(capture.contains("progress > 0.0F && progress <= 0.75F"));
        assertTrue(capture.contains("maintainDecoratedPotWobbleFixture(minecraft)"));
        assertTrue(capture.contains("case \"decorated-pot-north\" -> Direction.NORTH"));
        assertTrue(capture.contains("case \"decorated-pot-east\" -> Direction.EAST"));
        assertTrue(capture.contains("case \"decorated-pot-west\" -> Direction.WEST"));
        assertTrue(capture.contains("decoratedPotFacing\", modelMeshDecoratedPotFacing()"));
    }

    @Test
    void conduitFixtureRequiresRealInactiveBlockEntityAndBaseShellExecution() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/ConduitRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("this.shell,"));
        assertTrue(renderer.contains("SHELL_TEXTURE.renderType(RenderType::entitySolid)"));
        assertTrue(renderer.contains("submitNodeCollector.submitModelPartSemantic("));
        assertTrue(capture.contains("case \"conduit\", \"conduit-breaking\", \"conduit-active\", \"conduit-hunting\" -> Blocks.CONDUIT.defaultBlockState()"));
        assertTrue(capture.contains("instanceof net.minecraft.world.level.block.entity.ConduitBlockEntity conduit"));
        assertTrue(capture.contains("conduit.isActive() == isActiveConduitModelScenario()"));
        assertTrue(capture.contains("conduit.isHunting() == isHuntingConduitModelScenario()"));
        assertTrue(capture.contains("modelMeshConduitActive())"));
        assertTrue(capture.contains("modelMeshConduitHunting())"));
        assertTrue(capture.contains("case \"conduit\", \"conduit-breaking\" -> \"minecraft:entity/conduit/base\""));
        assertTrue(capture.contains("\"model-part\".equals(diagnostic.provenance())"));
        assertTrue(capture.contains("|| isConduitModelScenario()"));
    }

    @Test
    void activeConduitFixtureRequiresVanillaFrameAndFourPartClosedEyeComposition() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/ConduitRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("this.cage,"));
        assertTrue(renderer.contains("ACTIVE_SHELL_TEXTURE.renderType(RenderType::entityCutoutNoCull)"));
        assertTrue(renderer.contains("this.wind, poseStack, renderType"));
        assertTrue(renderer.contains("condiutRenderState.isHunting ? OPEN_EYE_TEXTURE : CLOSED_EYE_TEXTURE"));
        assertTrue(capture.contains("case \"conduit-active\", \"conduit-hunting\" -> \"minecraft:entity/conduit/cage\""));
        assertTrue(capture.contains("case \"conduit\", \"conduit-breaking\", \"conduit-active\", \"conduit-hunting\" -> Blocks.CONDUIT.defaultBlockState()"));
        assertTrue(capture.contains("prepareActiveConduitFixture(serverLevel, position)"));
        assertTrue(capture.contains("isHuntingConduitModelScenario() ? 42 : 16"));
        assertTrue(capture.contains("Blocks.WATER.defaultBlockState()"));
        assertTrue(capture.contains("Blocks.PRISMARINE.defaultBlockState()"));
        assertTrue(capture.contains("cageParts < 1L || windParts < 2L || !eyePresent"));
        assertTrue(capture.contains("copied.size() < 4"));
        assertTrue(capture.contains("isActiveConduitModelScenario() ? 4"));
    }

    @Test
    void huntingConduitFixtureRequiresFortyTwoFrameBlocksAndOpenEye() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("case \"conduit-active\", \"conduit-hunting\" -> \"minecraft:entity/conduit/cage\""));
        assertTrue(capture.contains("return \"conduit-hunting\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("conduit.isHunting() == isHuntingConduitModelScenario()"));
        assertTrue(capture.contains("? \"minecraft:entity/conduit/open_eye\" : \"minecraft:entity/conduit/closed_eye\""));
        assertTrue(capture.contains("int requiredFrameBlocks = isHuntingConduitModelScenario() ? 42 : 16"));
    }

    @Test
    void plainStandingWhiteBannerRequiresThreeScopedSemanticModels() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/BannerRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("submitNodeCollector.submitModelSemantic("));
        assertTrue(renderer.contains("ModelBakery.BANNER_BASE"));
        assertTrue(renderer.contains("Sheets.BANNER_BASE"));
        assertTrue(capture.contains("case \"white-banner\", \"white-banner-red-cross\", \"white-banner-two-patterns\" -> Blocks.WHITE_BANNER.defaultBlockState()"));
        assertTrue(capture.contains("getValue(net.minecraft.world.level.block.BannerBlock.ROTATION) == 8"));
        assertTrue(capture.contains("banner.getBaseColor() == expectedBannerBaseColor()"));
        assertTrue(capture.contains("banner.getPatterns().equals(expectedBannerPatterns())"));
        assertTrue(capture.contains("\"white-wall-banner-east\", \"white-wall-banner-west\" -> \"minecraft:entity/banner_base\""));
        assertTrue(capture.contains("\"white-wall-banner-east\", \"white-wall-banner-west\" -> \"minecraft:textures/atlas/banner_patterns.png\""));
        assertTrue(capture.contains("baseModels >= 2L && dyedFlags >= 1L"));
        assertTrue(capture.contains("int expectedModels = 3 + expectedBannerPatterns().layers().size()"));
        assertTrue(capture.contains("diagnostic.instances() >= expectedModels"));
        assertTrue(capture.contains("return \"white-banner\".equals(MODEL_MESH_SCENARIO) || \"red-banner\".equals(MODEL_MESH_SCENARIO)"));
    }

    @Test
    void patternedWhiteBannerAuthorsAndRequiresRedCrossLayer() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("return \"white-banner-red-cross\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("ResourceLocation.withDefaultNamespace(\"cross\")"));
        assertTrue(capture.contains("net.minecraft.world.item.DyeColor.RED"));
        assertTrue(capture.contains("bannerItem.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, expectedBannerPatterns())"));
        assertTrue(capture.contains("banner.applyComponentsFromItemStack(bannerItem)"));
        assertTrue(capture.contains("\"minecraft:entity/banner/cross\".equals(decision.textureId())"));
        assertTrue(capture.contains("!isPatternedBannerModelScenario() || crossPatterns >= 1L"));
    }

    @Test
    void twoPatternWhiteBannerRequiresCrossThenBorderComposition() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("return \"white-banner-two-patterns\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("ResourceLocation.withDefaultNamespace(\"border\")"));
        assertTrue(capture.contains("net.minecraft.world.item.DyeColor.BLUE"));
        assertTrue(capture.contains("List.of(cross, border)"));
        assertTrue(capture.contains("\"minecraft:entity/banner/border\".equals(decision.textureId())"));
        assertTrue(capture.contains("!isTwoPatternBannerModelScenario() || borderPatterns >= 1L"));
    }

    @Test
    void plainRedBannerRequiresRealRedBlockAndTintState() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("case \"red-banner\" -> Blocks.RED_BANNER.defaultBlockState()"));
        assertTrue(capture.contains("return \"red-banner\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("? net.minecraft.world.item.DyeColor.RED : net.minecraft.world.item.DyeColor.WHITE"));
        assertTrue(capture.contains("banner.getBaseColor() == expectedBannerBaseColor()"));
        assertTrue(capture.contains("state.is(\"red-banner\".equals(MODEL_MESH_SCENARIO) ? Blocks.RED_BANNER : Blocks.WHITE_BANNER)"));
    }

    @Test
    void northFacingWhiteWallBannerRequiresWallModelBranch() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("MODEL_MESH_SCENARIO.equals(\"white-wall-banner\")"));
        assertTrue(capture.contains("MODEL_MESH_SCENARIO.startsWith(\"white-wall-banner-\")"));
        assertTrue(capture.contains("case \"white-wall-banner\", \"white-wall-banner-south\", \"white-wall-banner-east\""));
        assertTrue(capture.contains("WallBannerBlock.FACING, expectedWallBannerFacing()"));
        assertTrue(capture.contains("position.relative(expectedWallBannerFacing().getOpposite())"));
        assertTrue(capture.contains("state.is(Blocks.WHITE_WALL_BANNER)"));
        assertTrue(capture.contains("WallBannerBlock.FACING) == expectedWallBannerFacing()"));
        assertTrue(capture.contains("case \"white-wall-banner-south\" -> Direction.SOUTH"));
        assertTrue(capture.contains("case \"white-wall-banner-east\" -> Direction.EAST"));
        assertTrue(capture.contains("case \"white-wall-banner-west\" -> Direction.WEST"));
        assertTrue(capture.contains("modelMeshBannerFacing()"));
    }

    @Test
    void standingSkeletonSkullRequiresExactSemanticTextureAndBlockEntityScope() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/SkullBlockRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("submitNodeCollector.submitModelSemanticTexture("));
        assertTrue(renderer.contains("textures/entity/skeleton/skeleton.png"));
        assertTrue(capture.contains("case \"skeleton-skull\", \"skeleton-skull-breaking\" -> Blocks.SKELETON_SKULL.defaultBlockState()"));
        assertTrue(capture.contains("getValue(net.minecraft.world.level.block.SkullBlock.ROTATION) == 8"));
        assertTrue(capture.contains("getValue(net.minecraft.world.level.block.AbstractSkullBlock.POWERED)"));
        assertTrue(capture.contains("skull.getAnimation(0.0F) == 0.0F"));
        assertTrue(capture.contains(
            "\"skeleton-wall-skull-east\", \"skeleton-wall-skull-west\" -> \"minecraft:textures/entity/skeleton/skeleton.png\""));
        assertTrue(capture.contains("|| isSkullModelScenario()"));
        assertTrue(capture.contains("skullType\", modelMeshSkullType()"));
    }

    @Test
    void wallSkeletonSkullGridRequiresRealFacingAndSupport() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("MODEL_MESH_SCENARIO.equals(\"skeleton-wall-skull\")"));
        assertTrue(capture.contains("MODEL_MESH_SCENARIO.startsWith(\"skeleton-wall-skull-\")"));
        assertTrue(capture.contains("case \"skeleton-wall-skull-south\" -> Direction.SOUTH"));
        assertTrue(capture.contains("case \"skeleton-wall-skull-east\" -> Direction.EAST"));
        assertTrue(capture.contains("case \"skeleton-wall-skull-west\" -> Direction.WEST"));
        assertTrue(capture.contains("Blocks.SKELETON_WALL_SKULL.defaultBlockState()"));
        assertTrue(capture.contains("WallSkullBlock.FACING, expectedWallSkullFacing()"));
        assertTrue(capture.contains("position.relative(expectedWallSkullFacing().getOpposite())"));
        assertTrue(capture.contains("WallSkullBlock.FACING) == expectedWallSkullFacing()"));
        assertTrue(capture.contains("modelMeshSkullFacing()"));
    }

    @Test
    void staticSkullTypeGridRequiresDistinctBlocksModelsAndTextures() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("case \"wither-skeleton-skull\" -> Blocks.WITHER_SKELETON_SKULL"));
        assertTrue(capture.contains("case \"zombie-head\" -> Blocks.ZOMBIE_HEAD"));
        assertTrue(capture.contains("case \"creeper-head\" -> Blocks.CREEPER_HEAD"));
        assertTrue(capture.contains("SkullBlock.Types.WITHER_SKELETON"));
        assertTrue(capture.contains("SkullBlock.Types.ZOMBIE"));
        assertTrue(capture.contains("SkullBlock.Types.CREEPER"));
        assertTrue(capture.contains("minecraft:textures/entity/skeleton/wither_skeleton.png"));
        assertTrue(capture.contains("minecraft:textures/entity/zombie/zombie.png"));
        assertTrue(capture.contains("minecraft:textures/entity/creeper/creeper.png"));
        assertTrue(capture.contains("getType() == expectedSkullType()"));
    }

    @Test
    void poweredDragonAndPiglinHeadsRequireLiveAnimation() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("case \"dragon-head-animated\" -> Blocks.DRAGON_HEAD"));
        assertTrue(capture.contains("case \"piglin-head-animated\" -> Blocks.PIGLIN_HEAD"));
        assertTrue(capture.contains("SkullBlock.Types.DRAGON"));
        assertTrue(capture.contains("SkullBlock.Types.PIGLIN"));
        assertTrue(capture.contains("minecraft:textures/entity/enderdragon/dragon.png"));
        assertTrue(capture.contains("minecraft:textures/entity/piglin/piglin.png"));
        assertTrue(capture.contains("POWERED) == isAnimatedSkullModelScenario()"));
        assertTrue(capture.contains("isAnimatedSkullModelScenario() ? skull.getAnimation(0.0F) > 0.0F"));
        assertTrue(capture.contains("modelMeshSkullPowered()"));
    }

    @Test
    void stableWallSkullTypesRequireExactBlocksModelsTexturesAndSupport() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("case \"wither-skeleton-wall-skull\" -> Blocks.WITHER_SKELETON_WALL_SKULL"));
        assertTrue(capture.contains("case \"zombie-wall-head\" -> Blocks.ZOMBIE_WALL_HEAD"));
        assertTrue(capture.contains("case \"creeper-wall-head\" -> Blocks.CREEPER_WALL_HEAD"));
        assertTrue(capture.contains("state.is(expectedWallSkullBlock())"));
        assertTrue(capture.contains("position.relative(expectedWallSkullFacing().getOpposite())"));
        assertTrue(capture.contains("\"wither-skeleton-skull\", \"wither-skeleton-wall-skull\""));
        assertTrue(capture.contains("\"zombie-head\", \"zombie-wall-head\""));
        assertTrue(capture.contains("\"creeper-head\", \"creeper-wall-head\""));
        assertTrue(capture.contains("modelMeshSkullFacing()"));
    }

    @Test
    void poweredDragonAndPiglinWallHeadsRequireFacingSupportAndLiveAnimation() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("case \"dragon-wall-head-animated\" -> Blocks.DRAGON_WALL_HEAD"));
        assertTrue(capture.contains("case \"piglin-wall-head-animated\" -> Blocks.PIGLIN_WALL_HEAD"));
        assertTrue(capture.contains("\"dragon-head-animated\", \"dragon-wall-head-animated\""));
        assertTrue(capture.contains("\"piglin-head-animated\", \"piglin-wall-head-animated\""));
        assertTrue(capture.contains("POWERED) == isAnimatedSkullModelScenario()"));
        assertTrue(capture.contains("isAnimatedSkullModelScenario() ? skull.getAnimation(0.0F) > 0.0F"));
        assertTrue(capture.contains("state.is(expectedWallSkullBlock())"));
        assertTrue(capture.contains("position.relative(expectedWallSkullFacing().getOpposite())"));
    }

    @Test
    void playerHeadsRequireResolvedProfileAndExactDefaultSkin() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/SkullBlockRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("playerSkinRenderCache.getOrDefault(ownerProfile).playerSkin().body().texturePath()"));
        assertTrue(renderer.contains("submitModelSemanticTexture("));
        assertTrue(capture.contains("case \"player-head-profile\" -> Blocks.PLAYER_HEAD"));
        assertTrue(capture.contains("case \"player-wall-head-profile\" -> Blocks.PLAYER_WALL_HEAD"));
        assertTrue(capture.contains("PlayerProfile.createOffline(\"MattMCFixture\")"));
        assertTrue(capture.contains("expectedPlayerHeadProfile().equals(skull.getOwnerProfile())"));
        assertTrue(capture.contains("minecraft:entity/player/wide/kai"));
        assertTrue(capture.contains("modelMeshSkullProfileName()"));
        assertTrue(capture.contains("modelMeshSkullProfileId()"));
    }

    @Test
    void oakHangingSignRequiresCeilingChainModelAuthoredTextAndScope() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/HangingSignRenderer.java");
        String abstractRenderer = source("src/main/java/net/minecraft/client/renderer/blockentity/AbstractSignRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("HANGING_SIGN_MAPPER") || renderer.contains("getHangingSignMaterial"));
        assertTrue(renderer.contains("AttachmentType.CEILING"));
        assertTrue(renderer.contains("normalChains"));
        assertTrue(abstractRenderer.contains("submitNodeCollector.submitModelSemantic("));
        assertTrue(abstractRenderer.contains("submitNodeCollector.submitTextSemantic("));
        assertTrue(capture.contains("case \"oak-hanging-sign\", \"oak-hanging-sign-attached\" -> Blocks.OAK_HANGING_SIGN.defaultBlockState()"));
        assertTrue(capture.contains("CeilingHangingSignBlock.ROTATION, 8"));
        assertTrue(capture.contains("CeilingHangingSignBlock.ATTACHED,"));
        assertTrue(capture.contains("isAttachedOakHangingSignModelScenario()"));
        assertTrue(capture.contains("isWallOakHangingSignModelScenario() ? position.east() : position.above()"));
        assertTrue(capture.contains("instanceof net.minecraft.world.level.block.entity.HangingSignBlockEntity sign"));
        assertTrue(capture.contains("case \"oak-hanging-sign\", \"oak-hanging-sign-attached\", \"oak-wall-hanging-sign\" -> \"minecraft:entity/signs/hanging/oak\""));
        assertTrue(capture.contains("modelMeshHangingSignTextReady()"));
        assertTrue(capture.contains("|| isOakHangingSignModelScenario()"));
    }

    @Test
    void attachedOakHangingSignRequiresVerticalChainsAuthoredTextAndScope() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/HangingSignRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("AttachmentType.CEILING_MIDDLE"));
        assertTrue(renderer.contains("vChains"));
        assertTrue(capture.contains("\"oak-hanging-sign-attached\""));
        assertTrue(capture.contains("isAttachedOakHangingSignModelScenario()"));
        assertTrue(capture.contains("CeilingHangingSignBlock.ATTACHED,"));
        assertTrue(capture.contains("modelMeshHangingSignAttached()"));
        assertTrue(capture.contains("modelMeshHangingSignTextReady()"));
    }

    @Test
    void wallOakHangingSignRequiresPlankFacingLateralSupportTextAndScope() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/HangingSignRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("AttachmentType.WALL"));
        assertTrue(renderer.contains("\"plank\""));
        assertTrue(renderer.contains("normalChains"));
        assertTrue(capture.contains("case \"oak-wall-hanging-sign\" -> Blocks.OAK_WALL_HANGING_SIGN"));
        assertTrue(capture.contains("WallHangingSignBlock.FACING, Direction.NORTH"));
        assertTrue(capture.contains("position.east()"));
        assertTrue(capture.contains("modelMeshHangingSignWall()"));
        assertTrue(capture.contains("modelMeshHangingSignAttachment()"));
        assertTrue(capture.contains("modelMeshHangingSignFacing()"));
    }

    @Test
    void spawnersRetainNestedEntityAndBlockEntitySemantics() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/SpawnerRenderer.java");
        String dispatcher = source("src/main/java/net/minecraft/client/renderer/entity/EntityRenderDispatcher.java");
        String bridge = source("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("entityRenderDispatcher.submitSemantic"));
        assertTrue(dispatcher.contains("isSemanticSubmission()"));
        assertTrue(bridge.contains("activeSemanticBlockEntityId()"));
        assertTrue(rust.contains("currentBlockEntityId()"));
        assertTrue(capture.contains("case \"spawner\" -> Blocks.SPAWNER.defaultBlockState()"));
        assertTrue(capture.contains("spawner.setEntityId(EntityType.PIG, serverLevel.getRandom())"));
        assertTrue(capture.contains("spawner.getTrialSpawner().setPlayerDetector"));
        assertTrue(capture.contains("diagnostic.blockEntityId() >= 0"));
    }

    @Test
    void trialSpawnersRetainTheirDistinctBlockEntityFixtureAndNestedPig() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/TrialSpawnerRenderer.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(renderer.contains("SpawnerRenderer.submitEntityInSpawner"));
        assertTrue(capture.contains("case \"trial-spawner\", \"trial-spawner-ominous\", \"trial-spawner-active\" -> Blocks.TRIAL_SPAWNER.defaultBlockState()"));
        assertTrue(capture.contains("TrialSpawnerState.WAITING_FOR_PLAYERS"));
        assertTrue(capture.contains("\"trial-spawner-ominous\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("spawner.getTrialSpawner().applyOminous(serverLevel, position)"));
        assertTrue(capture.contains("stabilizeActiveTrialSpawner(spawner, player.getUUID())"));
        assertTrue(capture.contains("Long.MAX_VALUE"));
        assertTrue(capture.contains("instanceof net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity spawner"));
        assertTrue(capture.contains("spawner.setEntityId(EntityType.PIG, serverLevel.getRandom())"));
        assertTrue(capture.contains("isSpawnerModelScenario()"));
        int fixture = capture.indexOf("if (isTrialSpawnerModelScenario()");
        int pig = capture.indexOf("spawner.setEntityId(EntityType.PIG, serverLevel.getRandom())", fixture);
        int waiting = capture.indexOf("spawner.setState(serverLevel", pig);
        assertTrue(fixture >= 0 && pig > fixture && waiting > pig,
            "Trial spawner must restore the visible waiting state after setEntityId resets it inactive");
        int ominous = capture.indexOf("spawner.getTrialSpawner().applyOminous(serverLevel, position)", waiting);
        assertTrue(ominous > waiting,
            "Ominous configuration must be applied only after the nested pig and waiting state are restored");
    }

    @Test
    void endPortalsRetainProceduralLayerAndBlockEntityExecutionEvidence() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String coordinator = source("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(rust.contains("faceCount * 17"));
        assertTrue(rust.contains("portal != sky * 16"));
        assertTrue(rust.contains("quad.blockEntityId()"));
        assertTrue(rust.contains("new EndPortalExecutionDiagnostic"));
        assertTrue(coordinator.contains("recordWholeFrameEndPortalExecution"));
        assertTrue(capture.contains("case \"end-portal\" -> Blocks.END_PORTAL.defaultBlockState()"));
        assertTrue(capture.contains("execution.skyQuads() == 2 && execution.portalQuads() == 32"));
    }

    @Test
    void endGatewayCarriesFullHeightPortalAndCorrelatedBeamSemantics() throws IOException {
        String portal = source("src/main/java/net/minecraft/client/renderer/blockentity/AbstractEndPortalRenderer.java");
        String gateway = source("src/main/java/net/minecraft/client/renderer/blockentity/TheEndGatewayRenderer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String coordinator = source("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(portal.contains("this.getOffsetDown(), this.getOffsetUp()"));
        assertTrue(gateway.contains("return 1.0F") && gateway.contains("return 0.0F"));
        assertTrue(collector.contains("faces, offsetDown, offsetUp, gameTime, lightCoords"));
        assertTrue(rust.contains("{0, offsetDown, 0") && rust.contains("{0, offsetUp, 1"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_END_GATEWAY_BEAM"));
        assertTrue(rust.contains("new EndGatewayBeamExecutionDiagnostic"));
        assertTrue(coordinator.contains("recordWholeFrameEndGatewayBeamExecution"));
        assertTrue(capture.contains("case \"end-gateway\" -> Blocks.END_GATEWAY.defaultBlockState()"));
        assertTrue(capture.contains("portal.offsetDown() == 0.0F && portal.offsetUp() == 1.0F"));
    }

    @Test
    void bellAndShulkerBlockEntitiesUseRustAtlasModelMeshes() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String bell = source("src/main/java/net/minecraft/client/renderer/blockentity/BellRenderer.java");
        String shulker = source("src/main/java/net/minecraft/client/renderer/blockentity/ShulkerBoxRenderer.java");
        assertTrue(rust.contains("model instanceof BellModel"));
        assertTrue(rust.contains("ShulkerBoxRenderer.ShulkerBoxModel"));
        assertTrue(level.contains("BellModel.State"));
        assertTrue(level.contains("entity/shulker/"));
        assertTrue(bell.contains("BELL_RESOURCE_LOCATION"));
        assertTrue(shulker.contains("DEFAULT_SHULKER_TEXTURE_LOCATION"));
    }

    @Test
    void nonPlayerSkullsUseStableRustTextureIdentityRoute() throws IOException {
        String state = source("src/main/java/net/minecraft/client/renderer/blockentity/state/SkullBlockRenderState.java");
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/SkullBlockRenderer.java");
        String submit = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(state.contains("textureIdentity"));
        assertTrue(renderer.contains("SKIN_BY_TYPE.get"));
        assertTrue(submit.contains("SkullModelBase"));
        assertTrue(submit.contains("crumblingOverlay == null"));
        assertTrue(submit.contains("block_entity/skull"));
        assertTrue(level.contains("SkullBlockRenderState"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.SkullModelBase"));
    }

    @Test
    void bannerBaseAndPatternLayersUseRustAtlasMeshesBeforeGlintAdmission() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String banner = source("src/main/java/net/minecraft/client/renderer/blockentity/BannerRenderer.java");
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.BannerModel"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.BannerFlagModel"));
        assertTrue(level.contains("entity/banner_base"));
        assertTrue(level.contains("BannerRenderState"));
        assertTrue(banner.contains("submitPatternLayer"));
        assertTrue(banner.contains("RenderType.entityGlint()"));
    }

    @Test
    void modelDestructionOverlayUsesExplicitRustCrumblingSemantics() throws IOException {
        String submit = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String resources = source("src/main/rust/render/vulkanic/resources.rs");
        String vulkan = source("src/main/rust/render/vulkanic/backends/vulkan/resources.rs");
        String frontend = source("src/main/rust/render/vulkanic/world_primitive_frontend.rs");

        assertTrue(submit.contains("i, j, k, l, crumblingOverlay"));
        assertTrue(rust.contains("enqueueEligibleCrumblingModelMesh"));
        assertTrue(rust.contains("BlockMeshExtraction crumblingExtraction = crumblingOverlay == null ? null"));
        assertTrue(rust.contains("modelPartEntityIdentity(textureIdentity) + \"/crumbling-stage-\""));
        assertTrue(rust.contains("\"model-part-crumbling\""));
        assertTrue(rust.contains("Direct-texture model submission plus Vanilla's optional projected destruction layer"));
        assertTrue(rust.contains("queued && crumblingOverlay != null && renderType.affectsCrumbling()"));
        assertTrue(submit.contains("block_entity/skull\"),\n\t\t\t\ti, j, k, l, crumblingOverlay"));
        assertTrue(rust.contains("new CrumblingProjection(entityPose, overlay.cameraPose(), overlay.progress())"));
        assertTrue(rust.contains("Direction.getApproximateNearest"));
        assertTrue(rust.contains("projected.rotateY((float)Math.PI)"));
        assertTrue(rust.contains("projected.rotateX((float)(-Math.PI / 2.0))"));
        assertTrue(rust.contains("DEPTH_POLICY_TEST_NO_WRITE"));
        assertTrue(resources.contains("Crumbling = 12"));
        assertTrue(vulkan.contains("BlendMode::Crumbling"));
        assertTrue(vulkan.contains("dst_color_blend_factor(vk::BlendFactor::SRC_COLOR)"));
        assertTrue(frontend.contains("constant_factor: -1.0"));
        assertTrue(frontend.contains("slope_factor: -10.0"));
		for (int stage = 0; stage <= 9; stage++) {
			assertTrue(rust.contains("textures/block/destroy_stage_" + stage + ".png"),
				"Rust destruction texture inventory must include stage " + stage);
		}
    }

	@Test
	void dynamicModelMeshesUseGenerationGuardedFrameLifetimeRetirement() throws IOException {
		String lifetime = source("src/main/java/net/vulkanic/world/DynamicWorldMeshLifetime.java");
		String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
		String nativeFrontend = source("src/main/rust/render/vulkanic/world_primitive_frontend.rs");

		assertTrue(lifetime.contains("IDLE_FRAME_GRACE = 1L"));
		assertTrue(lifetime.contains("record Retirement(long meshKey, long meshGeneration)"));
		assertTrue(rust.contains("retireIdleDynamicWorldMeshesLocked();"));
		assertTrue(rust.contains("DYNAMIC_WORLD_MESH_LIFETIME.observe("));
		assertTrue(rust.contains("PENDING_WORLD_MESH_RETIREMENTS.put(retirement.meshKey(), retirement.meshGeneration())"));
		assertTrue(rust.contains("DYNAMIC_WORLD_MESH_LIFETIME.restore(checkpoint.dynamicMeshLifetime)"));
		assertTrue(rust.contains("PENDING_WORLD_MESH_RETIREMENTS.putAll(checkpoint.pendingMeshRetirements)"));
		assertTrue(rust.contains("\" dynamic_meshes=\" + DYNAMIC_WORLD_MESH_LIFETIME.size()"));
		assertTrue(rust.contains("\" pending_retirements=\" + PENDING_WORLD_MESH_RETIREMENTS.size()"));
		assertTrue(nativeFrontend.contains("model_crumbling_mesh_replacements_stay_bounded_and_retire"));
	}

    @Test
    void breakingConduitFixtureRequiresSelectedModelPartCrumblingStage() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("return \"conduit-breaking\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("destroyBlockProgress(-0x434f4e44, modelMeshSetupPosition, MODEL_MESH_DESTROY_STAGE)"));
        assertTrue(capture.contains("modelMeshDestroyTextureId().equals(diagnostic.textureId())"));
        assertTrue(capture.contains("conduitBreaking"));
        assertTrue(capture.contains("conduitDestroyStage"));
        assertTrue(capture.contains("isBreakingOakSignModelScenario() || isBreakingConduitModelScenario() ? 2"));
    }

    @Test
    void breakingSkeletonSkullFixtureRequiresSelectedDirectTextureCrumblingStage() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        assertTrue(capture.contains("return \"skeleton-skull-breaking\".equals(MODEL_MESH_SCENARIO)"));
        assertTrue(capture.contains("destroyBlockProgress(-0x534b554c, modelMeshSetupPosition, MODEL_MESH_DESTROY_STAGE)"));
        assertTrue(capture.contains("modelMeshDestroyTextureId().equals(diagnostic.textureId())"));
        assertTrue(capture.contains("skullBreaking"));
        assertTrue(capture.contains("skullDestroyStage"));
        assertTrue(capture.contains("diagnostic.instances() >= (isBreakingSkullModelScenario() ? 2 : 1)"));
    }

    @Test
    void blockEntityDestructionForwardersReachAllThreeRustModelFamilies() throws IOException {
        for (String renderer : new String[] {
            "LecternRenderer.java", "EnchantTableRenderer.java", "BellRenderer.java",
            "BedRenderer.java", "ChestRenderer.java", "BannerRenderer.java",
            "AbstractSignRenderer.java", "ShulkerBoxRenderer.java"
        }) {
            String source = source("src/main/java/net/minecraft/client/renderer/blockentity/" + renderer);
            assertTrue(source.contains("breakProgress"), renderer + " must forward its extracted destruction state");
            assertTrue(source.contains("submitModel"), renderer + " must submit that state through a Model family");
        }
        String conduit = source("src/main/java/net/minecraft/client/renderer/blockentity/ConduitRenderer.java");
        assertTrue(conduit.contains("submitModelPartSemantic("));
        assertTrue(conduit.contains("condiutRenderState.breakProgress"));
        String copper = source("src/main/java/net/minecraft/client/renderer/blockentity/CopperGolemStatueBlockRenderer.java");
        String skull = source("src/main/java/net/minecraft/client/renderer/blockentity/SkullBlockRenderer.java");
        assertTrue(copper.contains("copperGolemStatueRenderState.breakProgress"));
        assertTrue(copper.contains("submitModelSemanticTexture("));
        assertTrue(skull.contains("skullBlockRenderState.breakProgress"));
        assertTrue(skull.contains("submitModelSemanticTexture("));
    }

    @Test
    void copperGolemStatuesUseOxidationAwareRustTextureMeshes() throws IOException {
        String state = source("src/main/java/net/minecraft/client/renderer/blockentity/state/CopperGolemStatueRenderState.java");
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/CopperGolemStatueBlockRenderer.java");
        String submit = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(state.contains("textureIdentity"));
        assertTrue(renderer.contains("getOxidationLevel"));
        assertTrue(submit.contains("CopperGolemStatueModel"));
        assertTrue(submit.contains("block_entity/copper_golem_statue"));
        assertTrue(level.contains("CopperGolemStatueRenderState"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.CopperGolemStatueModel"));
    }

    @Test
    void copperGolemStatueFixtureRequiresRegisteredVariantProducer() throws IOException {
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/CopperGolemStatueBlockRenderer.java");
        String harness = source("DevUtils/Common/graphics_harness.py");
        assertTrue(capture.contains("configuredModelMeshStatuePose()"));
        assertTrue(capture.contains("configuredModelMeshStatueWeathering()"));
        assertTrue(capture.contains("expectedCopperGolemStatueBlock().defaultBlockState()"));
        assertTrue(capture.contains("Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE"));
        assertTrue(capture.contains("expectedCopperGolemStatueTextureId()"));
        assertTrue(capture.contains("isCopperGolemStatueModelScenario()"));
        assertTrue(renderer.contains("submitModelSemanticTexture("));
        assertTrue(renderer.contains("copperGolemStatueRenderState.textureIdentity"));
        assertTrue(harness.contains("frozen_copper_golem_statue_emission_evidence"));
        assertTrue(harness.contains("copper_golem_statue_emission_observed"));
    }

    @Test
    void leashKnotsUseFixedRustTextureMeshRoute() throws IOException {
        String submit = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LeashKnotRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("submitModelSemanticTexture"));
        assertTrue(submit.contains("LeashKnotModel") || rust.contains("LeashKnotModel"));
        assertTrue(submit.contains("textures/entity/lead_knot.png") || renderer.contains("KNOT_LOCATION"));
        assertTrue(level.contains("LeashKnotModel"));
        assertTrue(renderer.contains("KNOT_LOCATION"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.LeashKnotModel"));
    }

    @Test
    void shulkerBulletsPreserveOpaqueAndTranslucentRustMaterialModes() throws IOException {
        String submit = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/ShulkerBulletRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("submitModelSemanticTexture"));
        assertTrue(submit.contains("ShulkerBulletModel") || rust.contains("ShulkerBulletModel"));
        assertTrue(submit.contains("enqueueStandaloneTranslucentModelMesh") || renderer.contains("TEXTURE_LOCATION"));
        assertTrue(level.contains("ShulkerBulletRenderState"));
        assertTrue(renderer.contains("RENDER_TYPE"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.ShulkerBulletModel"));
    }

    @Test
    void tridentBaseCarriesSemanticTextureBeforeFoilAdmission() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/ThrownTridentRenderer.java");
        String submit = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("submitModelSemanticTexture"));
        assertTrue(renderer.contains("TRIDENT_LOCATION"));
        assertTrue(submit.contains("model instanceof net.minecraft.client.model.TridentModel"));
        assertTrue(level.contains("TridentModel"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.TridentModel"));
    }

    @Test
    void boatAndRaftHullsUseSemanticTexturesBeforeWaterMaskAdmission() throws IOException {
        String abstractBoat = source("src/main/java/net/minecraft/client/renderer/entity/AbstractBoatRenderer.java");
        String boat = source("src/main/java/net/minecraft/client/renderer/entity/BoatRenderer.java");
        String raft = source("src/main/java/net/minecraft/client/renderer/entity/RaftRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(abstractBoat.contains("submitModelSemanticTexture"));
        assertTrue(abstractBoat.contains("textureLocation"));
        assertTrue(boat.contains("protected ResourceLocation textureLocation"));
        assertTrue(raft.contains("protected ResourceLocation textureLocation"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.BoatModel"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.RaftModel"));
    }

    @Test
    void minecartHullsUseSemanticTextureWhileDisplayBlocksRemainSeparate() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/AbstractMinecartRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("submitModelSemanticTexture"));
        assertTrue(renderer.contains("MINECART_LOCATION"));
        assertTrue(renderer.contains("submitMinecartContents"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.MinecartModel"));
    }

    @Test
    void endCrystalBodyUsesSemanticTextureAlongsideRustBeamQuads() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/EndCrystalRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("submitModelSemanticTexture"));
        assertTrue(renderer.contains("END_CRYSTAL_LOCATION"));
        assertTrue(renderer.contains("submitCrystalBeams"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.EndCrystalModel"));
    }

    @Test
    void windChargeCarriesBreezeWindUvOffsetThroughBoundedRustQuads() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/WindChargeRenderer.java");
        String renderType = source("src/main/java/net/minecraft/client/renderer/RenderType.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("RenderType.breezeWind"));
        assertTrue(renderType.contains("setTexturingState(new RenderStateShard.OffsetTexturingStateShard"));
		assertTrue(renderer.contains("enqueueWindChargeModel"));
		assertTrue(renderer.contains("Rust whole-frame wind-charge route has no semantic mesh"));
		assertTrue(rust.contains("enqueueWindChargeModel"));
		assertTrue(rust.contains("uvOffsetU"));
		assertTrue(rust.contains("enqueueEntityModelTranslucentTexturedQuad"));
		assertTrue(rust.contains("MATERIAL_SOURCE_ENTITY_MODEL"));
		assertTrue(rust.contains("MATERIAL_SOURCE_ENTITY_MODEL = 5"));
		assertTrue(source("src/main/rust/render/vulkanic/world_primitive_frontend.rs")
			.contains("WORLD_MATERIAL_SOURCE_ENTITY_MODEL: u32 = 5"));
		assertTrue(source("src/main/rust/render/vulkanic/world_primitive_frontend/material.rs")
			.contains("entity-model material quads require Rust-owned local texture UV semantics"));
    }

    @Test
    void copperGolemsUseOxidationAwareRustBodiesAndSemanticEyes() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String emissive = source("src/main/java/net/minecraft/client/renderer/entity/layers/LivingEntityEmissiveLayer.java");

        assertTrue(renderer.contains("CopperGolemModel.class"));
        assertTrue(renderer.contains("isVanillaCopperGolemModelMeshEligible"));
        assertTrue(collector.contains("CopperGolemRenderState copperGolemState"));
        assertTrue(collector.contains("textures/entity/copper_golem/"));
        assertTrue(emissive.contains("submitModelSemanticTexture"));
        assertTrue(rust.contains("CopperGolemOxidationLevels.getOxidationLevel(state.weathering)"));
        assertTrue(rust.contains("state.blockOnAntenna.isEmpty()"));
    }

    @Test
    void witherBodiesAndEnergyArmorUseRustAnimatedSemanticRoutes() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String swirl = source("src/main/java/net/minecraft/client/renderer/entity/layers/EnergySwirlLayer.java");
        String witherArmor = source("src/main/java/net/minecraft/client/renderer/entity/layers/WitherArmorLayer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");

        assertTrue(renderer.contains("WitherBossModel.class"));
        assertTrue(renderer.contains("WitherRenderState witherRenderState"));
        assertTrue(renderer.contains("isVanillaWitherModelMeshEligible"));
        assertTrue(rust.contains("textures/entity/wither/wither.png"));
        assertTrue(rust.contains("wither_invulnerable.png"));
        assertTrue(rust.contains("!state.isPowered"));
        assertTrue(swirl.contains("submitAnimatedModelSemanticTexture"));
        assertTrue(witherArmor.contains("textures/entity/wither/wither_armor.png"));
        assertTrue(collector.contains("enqueueEnergySwirlModel"));
        assertFalse(collector.contains("witherState.invulnerableTicks <= 0.0F"));
        assertTrue(rust.contains("uvOffsetU"));
        assertTrue(capture.contains("case \"powered-wither\" ->"));
        assertTrue(capture.contains("wither.setInvulnerableTicks(0)"));
        assertTrue(capture.contains("wither.setHealth(wither.getMaxHealth() * 0.25F)"));
        assertTrue(capture.contains("wither.isPowered() && wither.getInvulnerableTicks() == 0"));
        assertTrue(capture.contains("case \"invulnerable-wither\" ->"));
        assertTrue(capture.contains("wither.setInvulnerableTicks(220)"));
        assertTrue(capture.contains("wither.setHealth(wither.getMaxHealth())"));
        assertTrue(capture.contains("!wither.isPowered() && wither.getInvulnerableTicks() > 80"));
        assertTrue(capture.contains("mattmc.dev.graphicsAuditEnergySwirlOutline"));
        assertTrue(capture.contains("entity.isCurrentlyGlowing()"));
        assertTrue(rust.contains("outlinedMeshInstances"));
        assertTrue(collector.contains("RenderType.energySwirl declares OutlineProperty.NONE"));
    }

    @Test
    void adultDrownedBodiesAndOuterLayersUseExplicitRustTextures() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/DrownedOuterLayer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("DrownedModel.class"));
        assertTrue(renderer.contains("isVanillaDrownedModelMeshEligible"));
        assertTrue(layer.contains("submitModelSemanticTexture"));
        assertTrue(collector.contains("drowned_outer_layer.png"));
        assertTrue(rust.contains("textures/entity/zombie/drowned.png"));
        assertTrue(rust.contains("net.minecraft.client.model.DrownedModel.class"));
    }

    @Test
    void adultStraysUseSkeletonBodyAndExplicitClothingRoutes() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String layer = source("src/main/java/net/minecraft/client/renderer/entity/layers/SkeletonClothingLayer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("isVanillaStrayModelMeshEligible"));
        assertTrue(layer.contains("submitModelSemanticTexture"));
        assertTrue(collector.contains("stray_overlay.png"));
        assertTrue(rust.contains("textures/entity/skeleton/stray.png"));
        assertTrue(rust.contains("isVanillaStrayModelMeshEligible"));
    }

    @Test
    void guardianBeamUsesCopiedSemanticQuadsInsteadOfJavaCustomGeometry() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/GuardianRenderer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String policy = source("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java");

        assertTrue(renderer.contains("submitGuardianBeam"));
        assertTrue(collector.contains("enqueueGuardianBeam"));
        assertTrue(ordered.contains("submitGuardianBeam"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_GUARDIAN_BEAM"));
        assertTrue(rust.contains("textures/entity/guardian_beam.png"));
        assertTrue(rust.contains("vertices.length != 24 && vertices.length != 36"));
        assertTrue(rust.contains("vertices.length / 12"));
        assertTrue(policy.contains("currentGuardianBeamRoute"));
    }

    @Test
    void dragonFireballsUseExplicitRustTexturedBillboards() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DragonFireballRenderer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

		assertTrue(renderer.contains("submitTranslucentTexturedQuadSemantic"));
        assertTrue(collector.contains("enqueueTexturedQuad"));
        assertTrue(ordered.contains("submitTexturedQuad"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_DRAGON_FIREBALL"));
        assertTrue(rust.contains("textures/entity/enderdragon/dragon_fireball.png"));
    }

    @Test
    void fishingHookBillboardUsesSharedTexturedQuadBoundary() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/FishingHookRenderer.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("submitTexturedQuad"));
        assertTrue(renderer.contains("textures/entity/fishing_hook.png"));
        assertTrue(ordered.contains("submitTexturedQuad"));
        assertTrue(rust.contains("enqueueTexturedQuad"));
    }

    @Test
    void translucentTexturedBillboardsPreserveExplicitBlendMaterial() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("submitTranslucentTexturedQuad"));
        assertTrue(ordered.contains("submitTranslucentTexturedQuad"));
        assertTrue(collector.contains("enqueueTranslucentTexturedQuad"));
        assertTrue(rust.contains("MATERIAL_MODE_TRANSLUCENT"));
        assertTrue(rust.contains("enqueueTranslucentTexturedQuad"));
    }

    @Test
    void llamaSpitUsesRustStandaloneModelMeshAdmission() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LlamaSpitRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("isStandaloneModelMeshEligible"));
        assertTrue(renderer.contains("enqueueStandaloneModelMesh"));
        assertTrue(renderer.contains("LLAMA_SPIT_LOCATION"));
        assertTrue(rust.contains("enqueueStandaloneModelMesh"));
    }

    @Test
    void structureBoundingBoxesUseRustSemanticDebugLines() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/BlockEntityWithBoundingBoxRenderer.java");
        assertTrue(renderer.contains("currentDebugLineRoute"));
        assertTrue(renderer.contains("enqueueDebugLineSegments"));
        assertTrue(renderer.contains("boxEdges"));
        assertTrue(renderer.contains("invisible-block edges"));
    }

    @Test
    void testInstanceErrorMarkersUseSemanticColoredBoxQuads() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/blockentity/TestInstanceRenderer.java");
        assertTrue(renderer.contains("currentProceduralQuadRoute"));
        assertTrue(renderer.contains("submitColoredQuads"));
        assertTrue(renderer.contains("boxVertices"));
        assertTrue(renderer.contains("error-marker route rejected"));
    }

    @Test
    void texturedQuadBatchAbiIsExplicitAndRustOwned() throws IOException {
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String storage = source("src/main/java/net/minecraft/client/renderer/SubmitNodeStorage.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(ordered.contains("submitTexturedQuads"));
        assertTrue(storage.contains("submitTexturedQuads"));
        assertTrue(collector.contains("enqueueTexturedQuads"));
        assertTrue(rust.contains("enqueueTexturedQuads"));
        assertTrue(rust.contains("textured quad batches require aligned quad data"));
    }

    @Test
    void taczBedrockMeshesUseSemanticQuadBatchesOnRustRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java");
        assertTrue(renderer.contains("submitSemanticBedrockRoots"));
        assertTrue(renderer.contains("collectSemanticBedrockNode"));
        assertTrue(renderer.contains("submitTexturedQuads"));
        assertTrue(renderer.contains("identityPoseStack"));
        assertTrue(renderer.contains("!submitNodeCollector.isSemanticCoverageOnly()"));
        assertTrue(renderer.contains("MAX_SEMANTIC_BEDROCK_QUADS"));
        assertTrue(renderer.contains("submitSemanticOpticalAttachment"));
        assertTrue(renderer.contains("submitOpticalTexturedQuads"));
        assertTrue(renderer.contains("MATERIAL_MODE_OPTICAL_STENCIL_WRITE"));
        assertTrue(renderer.contains("MATERIAL_MODE_OPTICAL_STENCIL_TEST"));
        assertTrue(renderer.contains("requiresOpticalStencil(attachmentData)"));
        assertTrue(renderer.contains("!data.ocularNodes().isEmpty() || !data.geometry().divisionNodeGroups().isEmpty()"));
    }

    @Test
    void firstPersonTaczQuadBatchesUseOwnedMeshTransport() throws IOException {
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(collector.contains("isFirstPersonGuiCaptureActive()"));
        assertTrue(collector.contains("enqueueFirstPersonTexturedQuads"));
        assertTrue(collector.contains("Rust first-person semantic quad route rejected copied geometry"));
        assertTrue(rust.contains("MAX_FIRST_PERSON_SEMANTIC_QUADS"));
        assertTrue(rust.contains("PENDING_FIRST_PERSON_MESH_INSTANCES"));
        assertTrue(rust.contains("MATERIAL_ID_CUTOUT_TEXTURED"));
        assertTrue(rust.contains("isWorldMeshGenerationAndTexturesUploadedLocked"));
        assertTrue(rust.contains("registerDynamicTextureAsset(textureIdentity, stableTextureId(textureIdentity))"));
        assertTrue(rust.contains("copied == null ? null : copied.pngBytes()"));
        assertTrue(rust.contains("pendingFirstPersonSemanticItemIdentity"));
        assertTrue(rust.contains("beginFirstPersonSemanticItem"));
        assertTrue(rust.contains("endFirstPersonSemanticItem"));
        String hands = source("src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java");
        assertTrue(hands.contains("beginFirstPersonSemanticItem(itemStack)"));
        assertTrue(hands.contains("endFirstPersonSemanticItem()"));
    }

    @Test
    void fishingHookLineUsesExplicitSemanticSegments() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/FishingHookRenderer.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("submitLineSegments"));
        assertTrue(ordered.contains("submitLineSegments"));
        assertTrue(collector.contains("enqueueLineSegments"));
        assertTrue(rust.contains("currentFishingLineRoute"));
		assertTrue(renderer.contains("Rust whole-frame fishing-hook route rejected semantic billboard"));
		assertTrue(renderer.contains("Rust whole-frame fishing-hook route rejected semantic line segments"));
    }

	@Test
	void dragonFireballBillboardFailsClosedOnRustRoute() throws IOException {
		String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DragonFireballRenderer.java");
		assertTrue(renderer.contains("submitTranslucentTexturedQuadSemantic"));
		assertTrue(renderer.contains("Rust whole-frame dragon-fireball route rejected semantic billboard"));
	}

    @Test
    void debugHitboxesUseRustOwnedLineSemanticsWhenWholeFrameOwnsTheRoute() throws IOException {
        String dispatcher = source("src/main/java/net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String policy = source("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(dispatcher.contains("collectRustHitboxSemantics"));
        assertTrue(dispatcher.contains("enqueueDebugLineSegments"));
        assertTrue(level.contains("collectRustHitboxSemantics(this.submitNodeStorage)"));
        assertTrue(policy.contains("currentDebugLineRoute"));
        assertTrue(rust.contains("enqueueDebugLineSegments"));
    }

	@Test
	void directJavaHitboxRendererCannotBypassRustPresentationOwnership() throws IOException {
		String renderer = source("src/main/java/net/minecraft/client/renderer/feature/HitboxFeatureRenderer.java");
		int render = renderer.indexOf("public void render(");
		int guard = renderer.indexOf("RustGalVulkanWholeFrameMode.enabled()", render);
		int javaLoop = renderer.indexOf("getHitboxSubmits()", render);
		assertTrue(render >= 0 && guard > render && javaLoop > guard,
			"direct hitbox invocation must fail closed before Java line geometry while Rust owns presentation");
	}

	@Test
	void itemRendererJavaHelpersFailClosedAfterVulkanSelection() throws IOException {
		String renderer = source("src/main/java/net/minecraft/client/renderer/entity/ItemRenderer.java");
		int render = renderer.indexOf("public static void renderItem(");
		int guard = renderer.indexOf("ensureJavaItemRoute();", render);
		int special = renderer.indexOf("public static VertexConsumer getSpecialFoilBuffer(");
		int specialGuard = renderer.indexOf("ensureJavaItemRoute();", special);
		int ordinary = renderer.indexOf("public static VertexConsumer getFoilBuffer(");
		int ordinaryGuard = renderer.indexOf("ensureJavaItemRoute();", ordinary);
		assertTrue(render >= 0 && guard > render && special >= 0 && specialGuard > special
			&& ordinary >= 0 && ordinaryGuard > ordinary
			&& renderer.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& renderer.contains("Java item rendering is unavailable while Rust owns Vulkan presentation"),
			"all public Java item buffer helpers must fail closed while Rust owns Vulkan presentation");
	}

	@Test
	void blockDispatcherJavaGeometryHelpersFailClosedAfterVulkanSelection() throws IOException {
		String dispatcher = source("src/main/java/net/minecraft/client/renderer/block/BlockRenderDispatcher.java");
		String[] methods = {"renderBreakingTexture(", "renderBatched(", "renderLiquid(", "renderSingleBlock("};
		for (String method : methods) {
			int start = dispatcher.indexOf(method);
			assertTrue(start >= 0, "missing block dispatcher method " + method);
			assertTrue(dispatcher.indexOf("ensureJavaBlockRoute();", start) > start,
				"block dispatcher method must fail closed after Vulkan selection: " + method);
		}
		assertTrue(dispatcher.contains("RustGalVulkanWholeFrameMode.enabled()")
			&& dispatcher.contains("Java block geometry rendering is unavailable while Rust owns Vulkan presentation"));
	}

	@Test
	void mapRendererCannotFallThroughToJavaGeometryAfterVulkanSelection() throws IOException {
		String renderer = source("src/main/java/net/minecraft/client/renderer/MapRenderer.java");
		assertTrue(renderer.contains("if (!mapAccepted && net.vulkanic.VulkanicAPI.isVulkanBackendSelected())"));
		assertTrue(renderer.contains("if (!decorationAccepted && net.vulkanic.VulkanicAPI.isVulkanBackendSelected())"));
		assertTrue(renderer.contains("Rust whole-frame map-label route is unavailable; Java map text is not a fallback"));
	}

	@Test
	void mapTextureManagerUsesCpuSemanticPixelsWhileRustOwnsPresentation() throws IOException {
		String manager = source("src/main/java/net/minecraft/client/resources/MapTextureManager.java");
		assertTrue(manager.contains("private boolean semanticRustRoute()")
			&& manager.contains("if (semanticRustRoute())")
			&& manager.contains("stageCpuRgba8(this.location, 128, 128, pixels)")
			&& manager.contains("this.texture = semanticRustRoute()")
			&& manager.contains("new DynamicTexture")
			&& manager.contains("this.data.colors.length != 128 * 128")
			&& manager.contains("requires exactly 128x128 map color data"),
			"map preparation must stage bounded CPU pixels and avoid Java DynamicTexture allocation on Rust Vulkan");
	}

	@Test
	void selectedVulkanBillboardCallsitesCannotUseJavaCustomGeometryFallbacks() throws IOException {
		String hand = source("src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java");
		String hook = source("src/main/java/net/minecraft/client/renderer/entity/FishingHookRenderer.java");
		String fireball = source("src/main/java/net/minecraft/client/renderer/entity/DragonFireballRenderer.java");
		assertTrue(hand.contains("!submitNodeCollector.submitTranslucentTexturedQuadSemantic")
			&& hand.contains("net.vulkanic.VulkanicAPI.isVulkanBackendSelected()"));
		assertTrue(hook.contains("!rustBillboard && (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()")
			&& hook.contains("if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()"));
		assertTrue(fireball.contains("net.vulkanic.VulkanicAPI.isVulkanBackendSelected()"));
	}

	@Test
	void selectedVulkanDisplayAndGuardianCallsitesCannotUseJavaCustomGeometryFallbacks() throws IOException {
		String display = source("src/main/java/net/minecraft/client/renderer/entity/DisplayRenderer.java");
		String guardian = source("src/main/java/net/minecraft/client/renderer/entity/GuardianRenderer.java");
		assertTrue(display.contains("net.vulkanic.VulkanicAPI.isVulkanBackendSelected()")
			&& display.contains("Rust whole-frame display-text route rejected semantic background quad"));
		assertTrue(guardian.contains("net.vulkanic.VulkanicAPI.isVulkanBackendSelected()")
			&& guardian.contains("Rust whole-frame Guardian beam route rejected semantic quads"));
	}

	@Test
	void selectedVulkanTaczRendererCannotUseJavaCustomGunGeometryFallback() throws IOException {
		String renderer = source("src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java");
		assertTrue(renderer.contains("VulkanicAPI.isVulkanBackendSelected()")
			&& renderer.contains("&& !rustWholeFrame")
			&& renderer.contains("Rust whole-frame TACZ route is unavailable; Java custom gun geometry is not a fallback"));
		assertTrue(renderer.contains("Rust whole-frame TACZ attachment route is unavailable; Java custom gun geometry is not a fallback"));
	}

	@Test
	void selectedVulkanCannotEnterJavaDebugRendererDispatcher() throws IOException {
		String renderer = source("src/main/java/net/minecraft/client/renderer/debug/DebugRenderer.java");
		int render = renderer.indexOf("public void render(");
		int guard = renderer.indexOf("net.vulkanic.VulkanicAPI.isVulkanBackendSelected()", render);
		int javaLoop = renderer.indexOf("for (DebugRenderer.SimpleDebugRenderer", render);
		assertTrue(render >= 0 && guard > render && javaLoop > guard,
			"selected Vulkan must reject the Java debug renderer dispatcher before child buffer emission");
	}

	@Test
	void selectedVulkanEntityEffectsCannotUseJavaCustomGeometryFallbacks() throws IOException {
		String lightning = source("src/main/java/net/minecraft/client/renderer/entity/LightningBoltRenderer.java");
		String dragon = source("src/main/java/net/minecraft/client/renderer/entity/EnderDragonRenderer.java");
		assertTrue(lightning.contains("boolean rustPresentation = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()"));
		assertTrue(dragon.contains("boolean rustPresentation = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()"));
		assertTrue(dragon.contains("Rust whole-frame End Dragon ray route is unavailable; Java custom geometry is not a fallback"));
		assertTrue(dragon.contains("Rust whole-frame End Crystal beam route is unavailable; Java custom geometry is not a fallback"));
	}

	@Test
	void semanticCollectorRejectsDisabledSelectedVulkanPrimitiveRoutes() throws IOException {
		String collection = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
		assertTrue(collection.contains("Rust Vulkan textured-billboard route is unavailable; Java geometry is not a fallback"));
		assertTrue(collection.contains("Rust Vulkan translucent-billboard route is unavailable; Java geometry is not a fallback"));
		assertTrue(collection.contains("Rust Vulkan textured-quad route is unavailable; Java geometry is not a fallback"));
		assertTrue(collection.contains("Rust Vulkan line route is unavailable; Java geometry is not a fallback"));
		assertTrue(collection.contains("Rust Vulkan procedural-quad route is unavailable; Java geometry is not a fallback"));
	}

	@Test
	void selectedVulkanCannotRetainJavaParticleCallbacks() throws IOException {
		String collection = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
		int method = collection.indexOf("public void submitParticleGroup(");
		int selectedGuard = collection.indexOf("VulkanicAPI.isVulkanBackendSelected()", method);
		int javaRetention = collection.indexOf("this.particleGroupRenderers.add", method);
		assertTrue(method >= 0 && selectedGuard > method && javaRetention > selectedGuard,
			"selected Vulkan particle collection must reject before retaining Java callbacks");
		assertTrue(collection.contains("Rust Vulkan particle route is unavailable; Java particle callbacks are not a fallback"));
	}

	@Test
	void selectedVulkanCannotRetainJavaBlockModelSubmissions() throws IOException {
		String collection = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
		int method = collection.indexOf("public void submitBlockModel(");
		int selectedGuard = collection.indexOf("VulkanicAPI.isVulkanBackendSelected()", method);
		int javaStorage = collection.indexOf("this.blockModelSubmits.add", method);
		assertTrue(method >= 0 && selectedGuard > method && javaStorage > selectedGuard,
			"selected Vulkan block-model collection must reject before Java submission storage");
		assertTrue(collection.contains("Rust Vulkan block-model route is unavailable; Java block-model storage is not a fallback"));
	}

	@Test
	void selectedVulkanCannotRetainJavaBlockFeatureSubmissions() throws IOException {
		String collection = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
		assertMethodRejectsBeforeStorage(collection, "public void submitBlock(",
			"Rust Vulkan block-display route is unavailable; Java block geometry is not a fallback");
		assertMethodRejectsBeforeStorage(collection, "public void submitBlockDisplay(",
			"Rust Vulkan block-display route is unavailable; Java block geometry is not a fallback");
		assertMethodRejectsBeforeStorage(collection, "public void submitPrimedTntBlock(",
			"Rust Vulkan primed-TNT route is unavailable; Java block geometry is not a fallback");
	}

	private static void assertMethodRejectsBeforeStorage(String source, String signature, String message) {
		int method = source.indexOf(signature);
		int selectedGuard = source.indexOf("VulkanicAPI.isVulkanBackendSelected()", method);
		int javaStorage = source.indexOf("this.blockSubmits.add", method);
		assertTrue(method >= 0 && selectedGuard > method && javaStorage > selectedGuard,
			"selected Vulkan " + signature + " must reject before Java block storage");
		assertTrue(source.contains(message));
	}

	@Test
	void selectedVulkanCannotRetainJavaMovingBlockSubmissions() throws IOException {
		String collection = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
		int method = collection.indexOf("public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, SubmitNodeStorage.MovingBlockSubmitSource source)");
		int selectedGuard = collection.indexOf("VulkanicAPI.isVulkanBackendSelected()", method);
		int javaStorage = collection.indexOf("this.movingBlockSubmits.add", method);
		assertTrue(method >= 0 && selectedGuard > method && javaStorage > selectedGuard,
			"selected Vulkan moving-block collection must reject before Java submission storage");
		assertTrue(collection.contains("Rust Vulkan moving-block route is unavailable; Java moving-block geometry is not a fallback"));
	}

    @Test
    void sourceCoverageCollectorAcknowledgesOnlyExistingRustProceduralFrontends() throws IOException {
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        assertTrue(level.contains("submitGuardianBeam"));
        assertTrue(level.contains("currentGuardianBeamRoute().usesRustWholeFrameVulkan()"));
        assertTrue(level.contains("submitCrystalBeam"));
        assertTrue(level.contains("currentCrystalBeamRoute().usesRustWholeFrameVulkan()"));
        assertTrue(level.contains("submitTexturedQuad"));
        assertTrue(level.contains("currentTexturedBillboardRoute().usesRustWholeFrameVulkan()"));
        assertTrue(level.contains("submitColoredQuads"));
        assertTrue(level.contains("currentProceduralQuadRoute().usesRustWholeFrameVulkan()"));
        assertTrue(level.contains("instanceof net.minecraft.client.renderer.state.QuadParticleRenderState"));
    }

    @Test
    void sourceCoverageDoesNotRecountRustOwnedBeaconOrPortalCallbacks() throws IOException {
        String beacon = source("src/main/java/net/minecraft/client/renderer/blockentity/BeaconRenderer.java");
        String portal = source("src/main/java/net/minecraft/client/renderer/blockentity/AbstractEndPortalRenderer.java");
        assertTrue(beacon.contains("rustRoute.usesRustWholeFrameVulkan() && submitNodeCollector.isSemanticCoverageOnly()"));
        assertTrue(portal.contains("rustWholeFrame && submitNodeCollector.isSemanticCoverageOnly()"));
    }

    @Test
    void sourceCoverageDoesNotRecountRustOwnedExperienceOrbBillboards() throws IOException {
        String orb = source("src/main/java/net/minecraft/client/renderer/entity/ExperienceOrbRenderer.java");
        assertTrue(orb.contains("!submitNodeCollector.isSemanticCoverageOnly() && RustGalWorldPrimitiveRenderer.nativeExperienceOrbGeometryEnabled()"));
        int guard = orb.indexOf("if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()");
        int coverage = orb.indexOf("if (!submitNodeCollector.isSemanticCoverageOnly())", guard);
        int stop = orb.indexOf("return;", coverage);
        int billboard = orb.indexOf("poseStack.pushPose()", stop);
        int callback = orb.indexOf("submitCustomGeometrySemantic(", billboard);
        assertTrue(guard >= 0 && coverage > guard && stop > coverage && billboard > stop && callback > billboard,
            "Vulkan coverage must return before Java billboard expansion and callback admission");
    }

    @Test
    void endCrystalBeamsUseCopiedSemanticQuadsWithoutIrisStateOnRustRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/EnderDragonRenderer.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String policy = source("src/main/java/net/vulkanic/world/WorldRenderRoutePolicy.java");

        assertTrue(renderer.contains("submitCrystalBeam"));
        assertTrue(renderer.contains("rustCrystalBeam"));
        assertTrue(ordered.contains("submitCrystalBeam"));
        assertTrue(collector.contains("enqueueCrystalBeam"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_CRYSTAL_BEAM"));
        assertTrue(rust.contains("end_crystal/end_crystal_beam.png"));
        assertTrue(policy.contains("currentCrystalBeamRoute"));
    }

    @Test
    void enderDragonNeverFallsBackToJavaGeometryWhileRustOwnsPresentation() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/EnderDragonRenderer.java");
        assertTrue(renderer.contains("Rust whole-frame End Dragon ray route is unavailable; Java custom geometry is not a fallback"));
        assertTrue(renderer.contains("Rust whole-frame End Crystal beam route is unavailable; Java custom geometry is not a fallback"));
    }

    @Test
    void remainingProceduralEntityCallsitesFenceDisabledRustRoutes() throws IOException {
        String dragonFireball = source("src/main/java/net/minecraft/client/renderer/entity/DragonFireballRenderer.java");
        String fishingHook = source("src/main/java/net/minecraft/client/renderer/entity/FishingHookRenderer.java");
        String guardian = source("src/main/java/net/minecraft/client/renderer/entity/GuardianRenderer.java");
        String lightning = source("src/main/java/net/minecraft/client/renderer/entity/LightningBoltRenderer.java");
        String display = source("src/main/java/net/minecraft/client/renderer/entity/DisplayRenderer.java");
        assertTrue(dragonFireball.contains("RustGalVulkanWholeFrameMode.enabled()"));
        assertTrue(fishingHook.contains("RustGalVulkanWholeFrameMode.enabled()"));
        assertTrue(guardian.contains("RustGalVulkanWholeFrameMode.enabled()"));
        assertTrue(lightning.contains("Rust whole-frame lightning route is unavailable; Java custom geometry is not a fallback"));
        assertTrue(display.contains("RustGalVulkanWholeFrameMode.enabled()"));
    }

    @Test
    void alexsMobsBeamCallsitesCannotReopenJavaGeometryOnRustWholeFrame() throws IOException {
        String echo = source("src/main/java/net/alexsmobs/client/render/RenderCachalotEcho.java");
        String mungus = source("src/main/java/net/alexsmobs/client/render/layer/MungusBeamLayer.java");
        assertTrue(echo.contains("Rust whole-frame Cachalot Echo route rejected semantic textured quad"));
        assertTrue(mungus.contains("Rust whole-frame Mungus beam route rejected semantic textured quads"));
        assertTrue(echo.contains("if (rustWholeFrame && !accepted)"));
        assertTrue(mungus.contains("if (!accepted)"));
    }

    @Test
    void disabledRustModelRoutesCannotReopenWindChargeOrSheepJavaGeometry() throws IOException {
        String windCharge = source("src/main/java/net/minecraft/client/renderer/entity/WindChargeRenderer.java");
        String sheep = source("src/main/java/net/minecraft/client/renderer/entity/layers/SheepWoolLayer.java");
        assertTrue(windCharge.contains("Rust whole-frame wind-charge route is unavailable; Java model geometry is not a fallback"));
        assertTrue(sheep.contains("Rust whole-frame sheep-wool outline route is unavailable; Java model geometry is not a fallback"));
    }

    @Test
    void glowingSheepWoolUsesRustOutlineMetadataOnTheCopiedMaterialMesh() throws IOException {
        String sheep = source("src/main/java/net/minecraft/client/renderer/entity/layers/SheepWoolLayer.java");
        assertTrue(sheep.contains("RenderType.entityCutoutNoCull(SHEEP_WOOL_LOCATION)"));
        assertTrue(sheep.contains("SHEEP_WOOL_LOCATION, ResourceLocation.withDefaultNamespace(\"sheep_wool\")"));
        assertTrue(sheep.contains("sheepRenderState.outlineColor"));
        assertTrue(!sheep.contains("RenderType.outline(SHEEP_WOOL_LOCATION),\n\t\t\t\t\t\t\tSHEEP_WOOL_LOCATION, ResourceLocation.withDefaultNamespace(\"sheep_wool_outline\")"),
            "selected Rust sheep wool must not use the Java outline RenderType as its semantic material");
    }

    @Test
    void glowingSlimeOuterUsesRustOutlineMetadataOnTheTranslucentMesh() throws IOException {
        String slime = source("src/main/java/net/minecraft/client/renderer/entity/layers/SlimeOuterLayer.java");
        assertTrue(slime.contains("RenderType.entityTranslucent(SlimeRenderer.SLIME_LOCATION)"));
        assertTrue(slime.contains("ResourceLocation.withDefaultNamespace(\"slime_outer\")"));
        assertTrue(slime.contains("slimeRenderState.outlineColor"));
        assertTrue(!slime.contains("ResourceLocation.withDefaultNamespace(\"slime_outer_outline\")"),
            "selected Rust slime outline must use the copied translucent material, not a Java outline texture identity");
    }

    @Test
    void genericLivingRustOutlinesUseTheCopiedModelMaterial() throws IOException {
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        assertTrue(living.contains("renderType.isOutline() && textureIdentity != null"));
        assertTrue(living.contains("RenderType semanticOutlineMaterial = this.model.renderType(textureIdentity)"));
        assertTrue(living.contains("!semanticOutlineMaterial.isOutline()"));
        assertTrue(living.contains("livingEntityRenderState.outlineColor"),
            "outline color must remain explicit instance metadata after material normalization");
    }

    @Test
    void visibleGlowingSheepAndSlimeBodiesRemainRustOwned() throws IOException {
        String world = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        int sheepStart = world.indexOf("public static boolean isVanillaSheepModelMeshEligible(");
        int creeperStart = world.indexOf("public static boolean isVanillaCreeperModelMeshEligible(");
        int slimeStart = world.indexOf("public static boolean isVanillaSlimeModelMeshEligible(");
        int magmaStart = world.indexOf("public static boolean isVanillaMagmaCubeModelMeshEligible(");
        assertTrue(sheepStart >= 0 && creeperStart > sheepStart && slimeStart > creeperStart && magmaStart > slimeStart);
        String sheep = world.substring(sheepStart, creeperStart);
        String creeper = world.substring(creeperStart, slimeStart);
        String slime = world.substring(slimeStart, magmaStart);
        assertTrue(creeper.contains("(!state.isPowered || Boolean.getBoolean(\"mattmc.dev.rustEnergySwirl\"))"),
            "a powered creeper base must be admitted only with its matching gated energy layer");
        assertTrue(!sheep.contains("&& !glowing"),
            "visible glowing sheep bodies must use outline metadata on the Rust mesh");
        assertTrue(!slime.contains("&& !glowing"),
            "visible glowing slime bodies must use outline metadata on the Rust mesh");
        assertTrue(!world.contains("&& !glowing") && !world.contains("&& !translucentBody && !glowing"),
            "copied living-model predicates must not reject visible glowing bodies before Rust submission");
    }

    @Test
    void invisibleGlowingLivingBodiesUseRustOutlineOnlyMeshInstances() throws IOException {
        String living = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String world = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String bridge = source("src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java");
        assertTrue(living.contains("rustOutlineOnlyLivingBody")
                && living.contains("enqueueStandaloneModelMeshOutlineOnly")
                && living.contains("Rust whole-frame invisible-glowing living model has no semantic outline mesh"));
        assertTrue(world.contains("WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY")
                && world.contains("Copies an invisible glowing living body solely for Rust's outline mask"));
        assertTrue(bridge.contains("int flags") && bridge.contains("instance.flags()"),
            "outline-only state must cross the explicit mesh-instance ABI");
    }

    @Test
    void disabledRustEndPortalRouteCannotReopenJavaCubeGeometry() throws IOException {
        String portal = source("src/main/java/net/minecraft/client/renderer/blockentity/AbstractEndPortalRenderer.java");
        assertTrue(portal.contains("Rust whole-frame End Portal route is unavailable; Java custom geometry is not a fallback"));
    }

    @Test
    void disabledRustPaintingRouteCannotReopenJavaGeometry() throws IOException {
        String painting = source("src/main/java/net/minecraft/client/renderer/entity/PaintingRenderer.java");
        assertTrue(painting.contains("Rust whole-frame painting route is unavailable; Java custom geometry is not a fallback"));
    }

    @Test
    void rustLinePrimitiveAppliesItsFrameBoundBeforeAppendingPairs() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(rust.contains("PENDING_SEGMENTS.size(), endpoints.length / 6, MAX_WORLD_LINE_SEGMENTS, \"line-segment\""));
    }

    @Test
    void rustFirstPersonMeshQueueAppliesItsFrameBoundBeforeAppending() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(rust.contains("PENDING_FIRST_PERSON_MESH_INSTANCES.size(), 1, MAX_RUST_WORLD_MESH_INSTANCES, \"first-person-mesh-instance\""));
        assertTrue(rust.contains("PENDING_FIRST_PERSON_MESH_INSTANCES.size(), extractions.size()"));
    }

    @Test
	void lightningUsesBoundedSemanticProceduralQuads() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LightningBoltRenderer.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("submitColoredQuads"));
		assertTrue(renderer.contains("SEMANTIC_LIGHTNING_QUADS = 4 * (8 + 3 + 3) * 4"));
		assertTrue(renderer.contains("new float[SEMANTIC_LIGHTNING_QUADS * 12]"));
		assertTrue(renderer.contains("Rust whole-frame lightning route rejected semantic quads"));
        assertTrue(ordered.contains("submitColoredQuads"));
        assertTrue(collector.contains("enqueueProceduralQuads"));
		assertTrue(rust.contains("MATERIAL_TEXTURE_GENERATED_WHITE"));
	}

	@Test
	void completedRustFramesRecordProceduralQuadExecutionReceipts() throws IOException {
		String coordinator = source("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java");
		String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
		String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
		assertTrue(coordinator.contains("recordWholeFrameProceduralQuadExecution"),
			"completed Rust submissions must expose generic procedural-quad execution");
		assertTrue(capture.contains("rustGalWorldProceduralQuadExecution"),
			"capture metadata must persist the procedural execution receipt");
		assertTrue(renderer.contains("quad.textureId() == MATERIAL_TEXTURE_GENERATED_WHITE")
			&& renderer.contains("ProceduralQuadExecutionDiagnostic"),
			"procedural receipts must identify the Rust-owned generated-white material path");
	}

	@Test
	void evokerFangsDeterministicFixtureUsesVanillaImmediateAttackReplication() throws IOException {
		String capture = source("src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java");
		assertTrue(capture.contains("new EvokerFangs(serverLevel, origin.x, origin.y - 1.0, origin.z")
			&& capture.contains("Math.toRadians(player.getYRot()), 0, serverPlayer")
			&& capture.contains("Math.toRadians(playerYaw), 1000, serverPlayer"),
			"the deterministic fangs fixture must use vanilla immediate warmup and a long-lived retry");
		assertTrue(capture.contains("serverLevel.broadcastEntityEvent(fangs, (byte)4)"),
			"the fixture must replicate the short-lived vanilla attack event through the server level");
		assertTrue(capture.contains("modelMeshSetupEvokerEventSent")
			&& capture.contains("evoker_fangs_attack_activated_after_settled")
			&& capture.contains("serverLevel.broadcastEntityEvent(fangs, (byte)4)"),
			"the attack event must be activated only after settled replication is ready");
	}

    @Test
    void dragonDeathRaysUseSemanticDegenerateQuadsOnRustRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/EnderDragonRenderer.java");
		String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("rustProcedural"));
        assertTrue(renderer.contains("submitColoredQuads"));
        assertTrue(renderer.contains("rayCount"));
        assertTrue(renderer.contains("0xFFFF00FF"));
		assertTrue(rust.contains("net.minecraft.client.model.dragon.EnderDragonModel"));
		assertTrue(rust.contains("net.minecraft.client.model.ArrowModel"));
    }

    @Test
    void arrowSemanticMeshAcceptsBoundedResourcePackTextureIdentities() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(rust.contains("textureLocation.getNamespace().isBlank()"));
        assertTrue(rust.contains("textureLocation.getPath().isBlank()"));
        assertTrue(rust.contains("do not reject representable non-minecraft paths"));
        assertTrue(rust.contains("&& state.entityType != null"));
    }

    @Test
    void dragonHurtOverlayKeepsDirectTextureSemanticMeshRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/EnderDragonRenderer.java");
        assertTrue(renderer.contains("submitModelSemanticTexture"));
        assertTrue(renderer.contains("DRAGON_LOCATION"));
        assertTrue(renderer.contains("Overlay coordinates are copied into the Rust mesh instance"));
    }

    @Test
    void displayTextBackgroundUsesSemanticColoredQuadBoundary() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DisplayRenderer.java");
        assertTrue(renderer.contains("submitColoredQuads"));
        assertTrue(renderer.contains("backgroundVertices"));
        assertTrue(renderer.contains("textBackgroundSeeThrough"));
		assertTrue(renderer.contains("Rust whole-frame display-text route rejected semantic background quad"));
    }

    @Test
    void submitNodeStorageForwardsAllExplicitPrimitiveBoundaries() throws IOException {
        String storage = source("src/main/java/net/minecraft/client/renderer/SubmitNodeStorage.java");
        assertTrue(storage.contains("submitGuardianBeam"));
        assertTrue(storage.contains("submitCrystalBeam"));
        assertTrue(storage.contains("submitTexturedQuad"));
        assertTrue(storage.contains("submitLineSegments"));
        assertTrue(storage.contains("submitColoredQuads"));
    }

    @Test
    void rustWorldTextCollectorPreservesDisplayBackgroundQuads() throws IOException {
        String levelRenderer = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        assertTrue(levelRenderer.contains("submitColoredQuads"));
        assertTrue(levelRenderer.contains("this.target.submitColoredQuads"));
    }

    @Test
    void firstPersonMapUsesSemanticTextureQuadsForBackgroundAndDynamicMapAssets() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java");
        String mapRenderer = source("src/main/java/net/minecraft/client/renderer/MapRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("mapBackgroundVertices"));
		assertTrue(renderer.contains("submitTranslucentTexturedQuadSemantic"));
        assertTrue(renderer.contains("mapRenderer.extractRenderState"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_MAP_BACKGROUND"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_MAP_CHECKERBOARD"));
        assertTrue(rust.contains("textures/map/map_background_checkerboard.png"));
		assertTrue(mapRenderer.contains("submitTranslucentTexturedQuadSemantic"));
        assertTrue(mapRenderer.contains("textureAtlasSprite.atlasLocation()"));
        assertTrue(rust.contains("registerDynamicTextureAsset"));
        assertTrue(rust.contains("DynamicTexture"));
		assertTrue(renderer.contains("Rust whole-frame first-person map route rejected semantic background quad"));
    }

    @Test
    void paintingsUseCopiedAtlasQuadsOnTheRustWholeFrameRoute() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/PaintingRenderer.java");
        assertTrue(renderer.contains("renderPaintingSemantic"));
        assertTrue(renderer.contains("submitPaintingQuad"));
        assertTrue(renderer.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
        assertTrue(renderer.contains("textureAtlasSprite2.atlasLocation()"));
		assertTrue(renderer.contains("Rust whole-frame painting route rejected semantic quads"));
    }

	@Test
    void paintingsPreflightTheCompleteRustQuadPayloadBeforeEnqueueing() throws IOException {
		String renderer = source("src/main/java/net/minecraft/client/renderer/entity/PaintingRenderer.java");
		assertTrue(renderer.contains("MAX_RUST_PAINTING_QUADS = 65_536"));
		assertTrue(renderer.contains("long requiredQuads = 6L * width * height"));
		assertTrue(renderer.contains("requiredQuads > MAX_RUST_PAINTING_QUADS"));
		assertTrue(renderer.contains("partial Rust material work"));
	}

	@Test
	void dynamicRustBillboardAssetsBoundAtlasPixelsAndEncodedPayloads() throws IOException {
		String renderer = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
		String rust = source("src/main/rust/render/vulkanic/world_primitive_frontend.rs");
		assertTrue(renderer.contains("MAX_DYNAMIC_WORLD_ASSET_PIXELS = 16L * 1024L * 1024L"));
		assertTrue(renderer.contains("pixelCount > MAX_DYNAMIC_WORLD_ASSET_PIXELS"));
		assertTrue(renderer.contains("snapshot.pixels().length != pixelCount * 4L"));
		assertTrue(renderer.contains("payload.length > MAX_WORLD_MESH_TEXTURE_PNG_BYTES"));
		assertTrue(rust.contains("MAX_DECODED_TEXTURE_PIXELS: u64 = 16 * 1024 * 1024"));
		assertTrue(rust.contains("decoded pixel count {pixel_count} exceeds"));
	}

	@Test
	void rustGuiImageAdmissionBoundsDecodedPixelsBeforeRetention() throws IOException {
		String gui = source("src/main/rust/render/vulkanic/gui_frontend.rs");
		assertTrue(gui.contains("GUI_MAX_RAW_IMAGE_PIXELS: usize = 16 * 1024 * 1024"));
		assertTrue(gui.contains("raw GUI image {} has {pixel_count} pixels; maximum is"));
		assertTrue(gui.contains("let header = reader.info()"));
		assertTrue(gui.contains("header.width != sprite.width || header.height != sprite.height"));
	}

	@Test
    void rustOwnedWoolLayersFailClosedWhenCopiedMeshesAreUnavailable() throws IOException {
		String sheep = source("src/main/java/net/minecraft/client/renderer/entity/layers/SheepWoolLayer.java");
		String undercoat = source("src/main/java/net/minecraft/client/renderer/entity/layers/SheepWoolUndercoatLayer.java");
		String slime = source("src/main/java/net/minecraft/client/renderer/entity/layers/SlimeOuterLayer.java");
		assertTrue(sheep.contains("Rust whole-frame sheep-wool route has no semantic mesh"));
		assertTrue(sheep.contains("Rust whole-frame sheep-wool outline route has no semantic mesh"));
		assertTrue(undercoat.contains("Rust whole-frame sheep-wool undercoat route has no semantic mesh"));
		assertTrue(slime.contains("Rust whole-frame slime route has no semantic mesh"));
		assertTrue(slime.contains("Rust whole-frame slime-outline route has no semantic mesh"));
    }

	@Test
	void animatedModelSubmissionCannotLoseUvAnimationOnRustFallback() throws IOException {
		String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
		assertTrue(collector.contains("submitAnimatedModelSemanticTexture"));
		assertTrue(collector.contains("\"rust-vulkan-unavailable\", textureIdentity"));
		assertTrue(collector.contains("Rust whole-frame animated model route has no semantic UV-animation mesh"));
	}

    @Test
    void rustEntityDispatchSkipsIrisTrackingButRetainsRustShadowSubmission() throws IOException {
        String dispatcher = source("src/main/java/net/minecraft/client/renderer/entity/EntityRenderDispatcher.java");
        assertTrue(dispatcher.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
        assertTrue(dispatcher.contains("submitInternal(entityRenderState, cameraRenderState, d, e, f, poseStack, submitNodeCollector,")
            && dispatcher.contains("!selectedVulkan && !rustWholeFrame"));
        assertTrue(dispatcher.contains("captureIrisRenderState\n\t\t\t\t\t?"));
    }

    @Test
    void rustWholeFrameSkipsIrisHandAndBeaconShadowState() throws IOException {
        String hand = source("src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java");
        String beacon = source("src/main/java/net/minecraft/client/renderer/blockentity/BeaconRenderer.java");
        assertTrue(hand.contains("!rustWholeFrame && net.irisshaders.iris.Iris.isPackInUseQuick()"));
        assertTrue(beacon.contains("!rustWholeFrame && net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()"));
    }

    @Test
    void rustWeatherSkipsIrisWeatherAndParticlePolicyReads() throws IOException {
        String weather = source("src/main/java/net/minecraft/client/renderer/WeatherEffectRenderer.java");
        assertTrue(weather.contains("currentWeatherRoute().usesRustWholeFrameVulkan()"));
        assertTrue(weather.contains("Java weather rendering is unavailable while Rust owns whole-frame presentation"));
        assertTrue(weather.contains("!rustWeather && !net.irisshaders.iris.Iris.getPipelineManager()"));
        assertTrue(weather.contains("!rustWeather && net.irisshaders.iris.Iris.getPipelineManager()"));
    }

    @Test
    void rustBlockEntityDispatchSkipsIrisTracking() throws IOException {
        String dispatcher = source("src/main/java/net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher.java");
        assertTrue(dispatcher.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
        assertTrue(dispatcher.contains("cameraRenderState,")
            && dispatcher.contains("!selectedVulkan && !rustWholeFrame"));
    }

    @Test
    void rustPortalBlockEntitiesDoNotQueryIrisPackState() throws IOException {
        String portal = source("src/main/java/net/minecraft/client/renderer/blockentity/AbstractEndPortalRenderer.java");
        String gateway = source("src/main/java/net/minecraft/client/renderer/blockentity/TheEndGatewayRenderer.java");
        assertTrue(portal.contains("rustWholeFrame"));
        assertTrue(portal.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
        assertTrue(gateway.contains("super.submit(endGatewayRenderState")
            && portal.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
    }

    @Test
    void endPortalUsesCopiedLayerTexturesThroughRustSemanticMaterialQuads() throws IOException {
        String portal = source("src/main/java/net/minecraft/client/renderer/blockentity/AbstractEndPortalRenderer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String registry = source("src/main/rust/render/vulkanic/world_primitive_frontend/material_registry.rs");

        assertTrue(portal.contains("submitEndPortal"));
        assertTrue(portal.contains("Rust whole-frame End Portal route unavailable"));
        assertTrue(ordered.contains("submitEndPortal"));
        assertTrue(collector.contains("enqueueEndPortal"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_END_SKY"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_END_PORTAL"));
        assertTrue(rust.contains("for (int layer = 1; layer <= 16; layer++)"));
        assertTrue(registry.contains("WORLD_MATERIAL_TEXTURE_END_SKY"));
        assertTrue(registry.contains("WORLD_MATERIAL_TEXTURE_END_PORTAL"));
    }

    @Test
    void rustWholeFrameSkipsJavaSkyWeatherAndParticlePasses() throws IOException {
        String levelRenderer = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        assertTrue(levelRenderer.contains("private void addParticlesPass"));
        assertTrue(levelRenderer.contains("private void addWeatherPass"));
        assertTrue(levelRenderer.contains("private void addSkyPass"));
        assertTrue(levelRenderer.contains("currentBackgroundRoute().usesRustWholeFrameVulkan()"));
        assertTrue(levelRenderer.contains("currentWeatherRoute().usesRustWholeFrameVulkan()"));
        assertTrue(levelRenderer.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
    }

    @Test
    void rustWholeFrameOwnsGameRendererAndPresentationBoundary() throws IOException {
        String minecraft = source("src/main/java/net/minecraft/client/Minecraft.java");
        String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
        String coordinator = source("src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java");
        assertTrue(minecraft.contains("if (rustWholeFrameShell)"));
        assertTrue(minecraft.contains("this.gameRenderer.renderRustVulkanWholeFrameShell(this.deltaTracker, bl)"));
        assertTrue(minecraft.contains("} else {\n\t\t\t\tnet.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase(\"command.recording.clear\")"));
        assertTrue(gameRenderer.contains("executeWholeFrameVulkan(this.minecraft, this.guiRenderState)"));
        assertTrue(coordinator.contains("bridge.presentFrame(frameId, correlationId, submissionId)"));
    }

    @Test
    void rustWholeFrameExtractsItemActivationWithoutJavaScreenEffectBuffers() throws IOException {
        String effects = source("src/main/java/net/minecraft/client/renderer/ScreenEffectRenderer.java");
        String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
        assertTrue(effects.contains("renderRustVulkanItemActivation"));
        assertTrue(effects.contains("renderItemActivationAnimation(new PoseStack(), partialTick, submitNodeCollector)"));
        assertTrue(gameRenderer.contains("renderRustVulkanItemActivation(f, this.submitNodeStorage)"));
        assertTrue(gameRenderer.contains("without invoking ScreenEffectRenderer's Java buffer-backed"));
    }

    @Test
    void rustWholeFrameScreensStaySemanticAcrossGameplayMenus() throws IOException {
        String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
        assertTrue(gameRenderer.contains("Every screen is extracted into GuiRenderState here"));
        assertTrue(gameRenderer.contains("this.minecraft.screen.renderWithTooltipAndSubtitles"));
        assertTrue(!gameRenderer.contains("Rust whole-frame Vulkan has no complete semantic route for screen"));
    }

	@Test
	void rustWholeFrameRefreshesDynamicLightmapBeforeSemanticWorldSeed() throws IOException {
		String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
		int shellStart = gameRenderer.indexOf("public boolean renderRustVulkanWholeFrameShell");
		int partialTick = gameRenderer.indexOf("float f = net.vulkanic.bridge.RustGalDeterministicTiming.partialTick(deltaTracker);", shellStart);
		int lightmapUpdate = gameRenderer.indexOf("this.lightTexture.updateLightTexture(f);", shellStart);
		int semanticPrime = gameRenderer.indexOf("RustGalWorldPrimitiveRenderer.primeWorldSemanticState(", shellStart);

		assertTrue(shellStart >= 0 && partialTick > shellStart);
		assertTrue(partialTick < lightmapUpdate && lightmapUpdate < semanticPrime,
			"time-of-day lightmap changes must be visible to the first Rust semantic seed");
	}

    @Test
    void legacyGameRendererEntryPointFailsClosedWhenRustOwnsWholeFrame() throws IOException {
        String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
        int renderStart = gameRenderer.indexOf("public void render(DeltaTracker deltaTracker, boolean bl)");
        int shellStart = gameRenderer.indexOf("public boolean renderRustVulkanWholeFrameShell");
        assertTrue(renderStart >= 0 && shellStart > renderStart);
        String legacyEntryPoint = gameRenderer.substring(renderStart, shellStart);
        assertTrue(legacyEntryPoint.contains("RustGalVulkanWholeFrameMode.enabled()"));
        assertTrue(legacyEntryPoint.contains("Java GameRenderer.render is unavailable while Rust Vulkan owns the whole frame"));
        assertTrue(legacyEntryPoint.indexOf("Java GameRenderer.render is unavailable")
                < legacyEntryPoint.indexOf("this.renderLevel(deltaTracker)"));
        assertTrue(legacyEntryPoint.indexOf("Java GameRenderer.render is unavailable")
                < legacyEntryPoint.indexOf("PostChain postChain"));
    }

    @Test
    void rustWholeFrameDoesNotSilentlyDropJavaPostEffects() throws IOException {
        String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
        assertTrue(gameRenderer.contains("this.effectActive && this.postEffectId != null"));
        String ffi = source("src/main/rust/render/vulkanic/ffi/world.rs");
        assertTrue(ffi.contains(".validate_post_effect_request_with_globals(&post_effect_id, world_frame.engine_globals)?;"));
        assertTrue(gameRenderer.contains("Java GUI blur post-process is unavailable while Rust owns whole-frame Vulkan"));
    }

    @Test
    void rustWholeFrameOwnsEntityOutlinePostEffectWithoutJavaTargetOrPostChain() throws IOException {
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        assertTrue(level.contains("Rust owns the outline mask, intermediate targets, and post effect"));
        assertTrue(level.contains("A route switch can occur after resource reload"));
        assertTrue(level.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled() && this.entityOutlineTarget != null"));
        assertTrue(level.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()")
            && level.contains("!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()")
            && level.contains("this.levelRenderState.haveGlowingEntities"));
        assertTrue(level.contains("RustGalVulkanWholeFrameMode.enabled() || this.entityOutlineTarget != null"));
    }

    @Test
    void unsupportedGuiElementsAreDiagnosedWithoutJavaFallback() throws IOException {
        String guiRenderer = source("src/main/java/net/minecraft/client/gui/render/GuiRenderer.java");
        String rustGui = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
        assertTrue(guiRenderer.contains("recordUnsupportedElement(\"text\")"));
        assertTrue(guiRenderer.contains("recordUnsupportedElement(\"blit\")"));
        assertTrue(guiRenderer.contains("recordUnsupportedElement(\"tiled-blit\")"));
        assertTrue(guiRenderer.contains("recordUnsupportedElement(\"rectangle\")"));
        assertTrue(rustGui.contains("unsupported-element="));
    }

    @Test
    void guiUnsupportedAdmissionDiagnosticsAreBoundedPerFrame() throws IOException {
        String rustGui = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
        assertTrue(rustGui.contains("MAX_GUI_UNSUPPORTED_ELEMENTS = 4_096"));
        assertTrue(rustGui.contains("wholeFrameUnsupportedElementCount < MAX_GUI_UNSUPPORTED_ELEMENTS"));
    }

    @Test
    void underwaterScreenEffectUsesRustSemanticTiledBlit() throws IOException {
        String effects = source("src/main/java/net/minecraft/client/renderer/ScreenEffectRenderer.java");
        String gui = source("src/main/java/net/minecraft/client/gui/GuiGraphics.java");
        String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
        assertTrue(effects.contains("renderRustVulkanScreenEffects"));
        assertTrue(effects.contains("UNDERWATER_LOCATION"));
        assertTrue(gui.contains("submitRustSemanticTiledBlit"));
        assertTrue(gameRenderer.contains("screenEffectRenderer.renderRustVulkanScreenEffects(guiGraphics)"));
    }

    @Test
    void fireAndViewBlockingOverlaysUseRustSemanticAtlasBlits() throws IOException {
        String effects = source("src/main/java/net/minecraft/client/renderer/ScreenEffectRenderer.java");
        String gui = source("src/main/java/net/minecraft/client/gui/GuiGraphics.java");
        assertTrue(effects.contains("fire.atlasLocation()"));
        assertTrue(effects.contains("blocking.atlasLocation()"));
        assertTrue(effects.contains("submitRustSemanticBlit"));
        assertTrue(gui.contains("Semantic affine blit for atlas-backed Rust-owned screen effects"));
    }

    @Test
    void legacyScreenEffectEntryPointFailsClosedDuringRustWholeFrame() throws IOException {
        String effects = source("src/main/java/net/minecraft/client/renderer/ScreenEffectRenderer.java");
        int method = effects.indexOf("public void renderScreenEffect");
        int pose = effects.indexOf("PoseStack poseStack", method);
        assertTrue(method >= 0 && pose > method);
        String body = effects.substring(method, pose);
        assertTrue(body.contains("RustGalVulkanWholeFrameMode.enabled()"));
        assertTrue(body.contains("Java screen-effect rendering is unavailable"));
    }

    @Test
    void endermanInvertPostEffectUsesCopiedRustResourceGraph() throws IOException {
        String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
        String rust = source("src/main/rust/render/vulkanic/world_primitive_frontend.rs");
        assertTrue(gameRenderer.contains("this.postEffectId.toString()"));
        assertFalse(gameRenderer.contains("enqueuePostEffectInvert"));
        assertTrue(rust.contains("self.custom_post_effect_sources_with_globals(identity, globals)"));
        assertFalse(rust.contains("let invert_requested ="));
        assertTrue(rust.contains("append_custom_post_effect_with_external_targets"));
    }

    @Test
    void generatedInvertAssetDoesNotClaimJavaResourcePackResidency() throws IOException {
        String gui = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
		assertTrue(gui.contains("sprite == GuiSprite.POST_EFFECT_INVERT"));
        assertTrue(gui.contains("generated and owned by the Rust GUI"));
    }

    @Test
	void guiBlurBoundaryUsesTheRustSemanticBlurGraph() throws IOException {
		String state = source("src/main/java/net/minecraft/client/gui/render/state/GuiRenderState.java");
		String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
		assertTrue(state.contains("hasBlurBeforeStratum"));
		assertTrue(gameRenderer.contains("blur boundary is semantic frame data"));
		assertFalse(gameRenderer.contains("gui-blur-post-process"));
    }

    @Test
    void modelPipCaptureHasAClosedVertexBound() throws IOException {
        String collector = source("src/main/java/net/vulkanic/gui/GuiModelPipSemanticCollector.java");
        assertTrue(collector.contains("MAX_CAPTURED_VERTICES"));
        assertTrue(collector.contains("MAX_CAPTURED_VERTICES = 65_536"),
            "Java model PIP capture must stay within Rust GUI mesh admission bounds");
        assertTrue(collector.contains("capture.overflowed"));
        assertTrue(collector.contains("overflowed = true"));
        assertTrue(collector.contains("guiPose.length != 6"));
        assertTrue(collector.contains("Float.isFinite(value)"));
    }

    @Test
    void evokersUseRustIllagerBodyRouteWithSeparateHeldItemLayer() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("instanceof IllagerModel"));
        assertTrue(renderer.contains("EvokerRenderState evokerRenderState"));
        assertTrue(renderer.contains("isVanillaEvokerModelMeshEligible"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.IllagerModel"));
        assertTrue(rust.contains("textures/entity/illager/evoker.png"));
    }

    @Test
    void vindicatorsAndPillagersUseExactIllagerBodyTextures() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("IllagerRenderState illagerRenderState"));
        assertTrue(renderer.contains("isVanillaVindicatorOrPillagerModelMeshEligible"));
        assertTrue(rust.contains("textures/entity/illager/vindicator.png"));
        assertTrue(rust.contains("textures/entity/illager/pillager.png"));
        assertTrue(rust.contains("textures/entity/illager/illusioner.png"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
