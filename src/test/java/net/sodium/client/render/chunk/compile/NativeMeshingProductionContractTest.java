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
    void rustTerrainExecutionReceiptsRetainSourceAnimatedSpriteIdentity() throws IOException {
        String diagnostics = source("src/main/java/net/sodium/client/render/StaticTerrainParityDiagnostics.java");

        assertTrue(diagnostics.contains(
                "MeshCoverage sourceCoverage = SOURCE_MESHES.get(new CoverageKey(sectionKey, normalizedLayer))"));
        assertTrue(diagnostics.contains(
                "sourceCoverage == null ? \"\" : sourceCoverage.sectionAnimatedSpriteIdentities()"));
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
        assertTrue(mesh.contains("stageSemanticImage(sprite)"));
        assertTrue(!item.contains("recordDiagnostic(\"animated-item\")"));
    }

    @Test
    void mungusBeamUsesSemanticTexturedQuadsOnRustWholeFrame() throws IOException {
        String beam = source("src/main/java/net/alexsmobs/client/render/layer/MungusBeamLayer.java");
        assertTrue(beam.contains("currentTexturedBillboardRoute().usesRustWholeFrameVulkan()"));
        assertTrue(beam.contains("submitNodeCollector.submitTexturedQuad"));
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
        assertTrue(collector.contains("RustGalGuiRawImageAssets.stage"));
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
        assertTrue(gui.contains("Math.min(16"));
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
        assertTrue(gui.contains("living.getModel()"));
        assertTrue(renderer.contains("GuiEntityRenderState entityPip"));
        assertTrue(renderer.contains("RustGalGuiRenderer.tryEnqueueEntityPip"));
        assertTrue(living.contains("applySemanticModelPose"));
        assertTrue(living.contains("this.model.setupAnim(state)"));
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

        assertTrue(renderer.contains("SnowGolemModel.class"));
        assertTrue(renderer.contains("SnowGolemRenderState snowGolemRenderState"));
        assertTrue(renderer.contains("isVanillaSnowGolemModelMeshEligible"));
        assertTrue(rust.contains("model.getClass() == net.minecraft.client.model.SnowGolemModel.class"));
        assertTrue(rust.contains("textures/entity/snow_golem.png"));
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
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.BookModel"));
        assertTrue(level.contains("EnchantTableRenderState"));
        assertTrue(level.contains("LecternRenderState"));
        assertTrue(level.contains("enchanting_table_book"));
        assertTrue(enchant.contains("BOOK_LOCATION"));
        assertTrue(lectern.contains("EnchantTableRenderer.BOOK_LOCATION"));
    }

    @Test
    void armorTrimSubmitsUseTheExplicitAtlasModelContract() throws IOException {
        String equipment = source("src/main/java/net/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String sheets = source("src/main/java/net/minecraft/client/renderer/Sheets.java");
        assertTrue(equipment.contains("Sheets.armorTrimsSheet"));
        assertTrue(equipment.contains("textureAtlasSprite"));
        assertTrue(rust.contains("model instanceof net.minecraft.client.model.HumanoidModel"));
        assertTrue(rust.contains("entity_decal"));
        assertTrue(rust.contains("sprite.sodium$hasUnknownImageContents()"));
        assertTrue(sheets.contains("ARMOR_TRIMS_SHEET"));
    }

    @Test
    void signsUseRustBoardMeshesWithSemanticText() throws IOException {
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        String signs = source("src/main/java/net/minecraft/client/renderer/blockentity/AbstractSignRenderer.java");
        assertTrue(rust.contains("model instanceof Model.Simple"));
        assertTrue(level.contains("state == net.minecraft.util.Unit.INSTANCE"));
        assertTrue(level.contains("entity/signs/"));
        assertTrue(level.contains("SignRenderState"));
        assertTrue(signs.contains("this.submitSignText"));
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
        assertTrue(submit.contains("block_entity/skull/"));
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
		assertTrue(rust.contains("enqueueTranslucentTexturedQuad"));
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
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");

        assertTrue(renderer.contains("WitherBossModel.class"));
        assertTrue(renderer.contains("WitherRenderState witherRenderState"));
        assertTrue(renderer.contains("isVanillaWitherModelMeshEligible"));
        assertTrue(rust.contains("textures/entity/wither/wither.png"));
        assertTrue(rust.contains("wither_invulnerable.png"));
        assertTrue(rust.contains("!state.isPowered"));
        assertTrue(swirl.contains("submitAnimatedModelSemanticTexture"));
        assertTrue(collector.contains("textures/entity/wither/wither_armor.png"));
        assertTrue(collector.contains("witherState.invulnerableTicks <= 0.0F"));
        assertTrue(collector.contains("enqueueEnergySwirlModel"));
        assertTrue(rust.contains("uvOffsetU"));
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
        assertTrue(policy.contains("currentGuardianBeamRoute"));
    }

    @Test
    void dragonFireballsUseExplicitRustTexturedBillboards() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/DragonFireballRenderer.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("submitTexturedQuad"));
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
    void llamaSpitUsesRustTranslucentStandaloneMeshAdmission() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LlamaSpitRenderer.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");
        assertTrue(renderer.contains("isStandaloneTranslucentModelMeshEligible"));
        assertTrue(renderer.contains("enqueueStandaloneTranslucentModelMesh"));
        assertTrue(renderer.contains("LLAMA_SPIT_LOCATION"));
        assertTrue(rust.contains("enqueueStandaloneTranslucentModelMesh"));
        assertTrue(rust.contains("MATERIAL_MODE_TRANSLUCENT"));
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
		assertTrue(renderer.contains("submitTexturedQuad"));
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
        assertTrue(orb.contains("!(route.usesRustWholeFrameVulkan() && submitNodeCollector.isSemanticCoverageOnly())"));
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
    void lightningUsesBoundedSemanticProceduralQuads() throws IOException {
        String renderer = source("src/main/java/net/minecraft/client/renderer/entity/LightningBoltRenderer.java");
        String ordered = source("src/main/java/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java");
        String collector = source("src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java");
        String rust = source("src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java");

        assertTrue(renderer.contains("submitColoredQuads"));
        assertTrue(renderer.contains("new float[56 * 12]"));
		assertTrue(renderer.contains("Rust whole-frame lightning route rejected semantic quads"));
        assertTrue(ordered.contains("submitColoredQuads"));
        assertTrue(collector.contains("enqueueProceduralQuads"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_GENERATED_WHITE"));
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
        assertTrue(renderer.contains("submitTexturedQuad"));
        assertTrue(renderer.contains("mapRenderer.extractRenderState"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_MAP_BACKGROUND"));
        assertTrue(rust.contains("MATERIAL_TEXTURE_MAP_CHECKERBOARD"));
        assertTrue(rust.contains("textures/map/map_background_checkerboard.png"));
        assertTrue(mapRenderer.contains("submitTexturedQuad"));
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
        assertTrue(dispatcher.contains("submitInternal(entityRenderState, cameraRenderState, d, e, f, poseStack, submitNodeCollector, !rustWholeFrame)"));
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
        assertTrue(dispatcher.contains("cameraRenderState, !rustWholeFrame"));
    }

    @Test
    void rustPortalBlockEntitiesDoNotQueryIrisPackState() throws IOException {
        String portal = source("src/main/java/net/minecraft/client/renderer/blockentity/AbstractEndPortalRenderer.java");
        String gateway = source("src/main/java/net/minecraft/client/renderer/blockentity/TheEndGatewayRenderer.java");
        assertTrue(portal.contains("rustWholeFrame"));
        assertTrue(portal.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
        assertTrue(gateway.contains("currentMaterialRoute().usesRustWholeFrameVulkan()"));
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
        assertTrue(gameRenderer.contains("Rust whole-frame Vulkan post effect is unavailable"));
        assertTrue(gameRenderer.contains("Java GUI blur post-process is unavailable while Rust owns whole-frame Vulkan"));
    }

    @Test
    void rustWholeFrameOwnsEntityOutlinePostEffectWithoutJavaTargetOrPostChain() throws IOException {
        String level = source("src/main/java/net/minecraft/client/renderer/LevelRenderer.java");
        assertTrue(level.contains("Rust owns the outline mask, intermediate targets, and post effect"));
        assertTrue(level.contains("A route switch can occur after resource reload"));
        assertTrue(level.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled() && this.entityOutlineTarget != null"));
        assertTrue(level.contains("!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()\n\t\t\t&& this.levelRenderState.haveGlowingEntities && postChain2 != null"));
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
    void endermanInvertPostEffectUsesExplicitRustInvertBlend() throws IOException {
        String gameRenderer = source("src/main/java/net/minecraft/client/renderer/GameRenderer.java");
        String gui = source("src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java");
        String rust = source("src/main/rust/render/vulkanic/gui_frontend.rs");
		assertTrue(gameRenderer.contains("rustSemanticPostEffect"));
        assertTrue(gameRenderer.contains("enqueuePostEffectInvert"));
        assertTrue(gui.contains("POST_EFFECT_INVERT"));
        assertTrue(rust.contains("GUI_POST_EFFECT_INVERT_ID"));
        assertTrue(rust.contains("group(true)"));
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
