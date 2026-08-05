package net.sodium.client.render.chunk.compile;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
