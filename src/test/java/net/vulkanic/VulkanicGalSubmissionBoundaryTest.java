package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanicGalSubmissionBoundaryTest {
    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @Test
    void legacyDrawRequestsAreImmutableAndCarryExplicitResourcePlan() {
        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyIndexed(
                "world:terrain",
                VulkanicPrimitiveMode.TRIANGLES,
                12,
                VulkanicIndexType.SHORT,
                8L,
                1,
                4
            );

        assertEquals(VulkanicGalExecutionRequest.DrawCommandKind.INDEXED, request.command().kind());
        assertEquals("legacy-current-index-buffer", request.resourcePlan().orderedUses().get(0).resource().stableKey());
        assertThrows(UnsupportedOperationException.class, () -> request.descriptors().add(null));
        assertThrows(UnsupportedOperationException.class, () -> request.resourcePlan().orderedUses().add(null));
    }

    @Test
    void capturedGraphicsCompatibilitySnapshotReplacesLegacyPlaceholdersImmutably() {
        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyArrays(
                "gui:item",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                6,
                1
            );
        assertEquals("unresolved-legacy-compatibility", request.compatibilitySnapshot().source());

        List<VulkanicLegacyCompatibilityAdapter.VertexBufferSnapshot> vertexBuffers = new ArrayList<>();
        vertexBuffers.add(new VulkanicLegacyCompatibilityAdapter.VertexBufferSnapshot(
            0,
            "gui-item-vertices",
            64L,
            28,
            false
        ));
        List<VulkanicPassResourceModel.ResourceUse> resourceUses = new ArrayList<>();
        resourceUses.add(VulkanicLegacyCompatibilityAdapter.bufferUse(
            "vertex-buffer-0",
            VulkanicPassResourceModel.ResourceKind.VERTEX_BUFFER,
            "gui-item-vertices",
            VulkanicPassResourceModel.Access.READ,
            64L,
            168L,
            VulkanicResourceUsage.INFERRED,
            "gui:item:vertex-buffer:0",
            0
        ));
        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot snapshot =
            new VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot(
                Optional.empty(),
                new VulkanicGalExecutionRequest.VertexInputSnapshot(vertexBuffers, Optional.empty()),
                resourceUses,
                Optional.empty(),
                List.of(),
                "captured-test"
            );

        VulkanicGalExecutionRequest.GraphicsDrawRequest captured = request.withCompatibilitySnapshot(snapshot);
        vertexBuffers.clear();
        resourceUses.clear();

        assertEquals("captured-test", captured.compatibilitySnapshot().source());
        assertEquals(1, captured.vertexInput().vertexBuffers().size());
        assertEquals("gui-item-vertices", captured.vertexInput().vertexBuffers().get(0).stableKey());
        assertEquals(1, captured.resourcePlan().orderedUses().size());
        assertEquals("gui-item-vertices", captured.resourcePlan().orderedUses().get(0).resource().stableKey());
        assertTrue(captured.compatibilitySnapshot().resourceBindingPlan().isEmpty());
        assertEquals("unresolved-legacy-compatibility", request.compatibilitySnapshot().source());
        assertEquals(
            "current-legacy",
            request.vertexInput().vertexBuffers().isEmpty() ? "current-legacy" : "captured",
            "legacy compatibility request must remain unchanged after capture"
        );
    }

    @Test
    void clearMaskTranslationRejectsUnknownLegacyBits() {
        assertEquals(List.of(VulkanicClearBuffer.COLOR), VulkanicClearBuffer.fromLegacyGlMask(VulkanicAPI.GL_COLOR_BUFFER_BIT));
        assertEquals(
            List.of(VulkanicClearBuffer.COLOR, VulkanicClearBuffer.DEPTH),
            VulkanicClearBuffer.fromLegacyGlMask(VulkanicAPI.GL_COLOR_BUFFER_BIT | VulkanicAPI.GL_DEPTH_BUFFER_BIT)
        );
        assertTrue(VulkanicClearBuffer.fromLegacyGlMask(0x40000000).isEmpty());
    }

    @Test
    void frontendRoutesMajorCommandFamiliesThroughExplicitBoundary() throws Exception {
        String source = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicAPI.java"));

        assertTrue(source.contains("executeGalGraphicsDraw(ctx, VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyArrays("));
        assertTrue(source.contains("executeGalGraphicsDraw(ctx, VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyIndexed("));
        assertTrue(source.contains("executeGalComputeDispatch(ctx, VulkanicGalExecutionRequest.ComputeDispatchRequest.legacyDirect("));
        assertTrue(source.contains("executeGalComputeDispatch(ctx, VulkanicGalExecutionRequest.ComputeDispatchRequest.legacyIndirect("));
        assertTrue(source.contains("private static final VulkanicCompatibilityState compatibilityState"));
        assertTrue(source.contains("VulkanicGalSnapshotBuilder.captureGraphicsDraw(ctx, backend, compatibilityState, request)"));
        assertTrue(source.contains("VulkanicGalSnapshotBuilder.captureComputeDispatch(ctx, backend, compatibilityState, request)"));
        assertTrue(source.contains("executeClear(ctx, VulkanicGalExecutionRequest.ClearRequest.legacy("));
        assertTrue(source.contains("executeTransfer(ctx, VulkanicGalExecutionRequest.TransferRequest.legacy("));
        assertTrue(source.contains("snapshotMultiDrawElementsBaseVertex("));
    }

    @Test
    void bothBackendsExposeConcreteExplicitConsumers() throws Exception {
        String vulkanSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String openglSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java"));
        String backendContract = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/GraphicsBackend.java"));

        assertTrue(backendContract.contains("extends VulkanicGalExecutor"));
        assertTrue(vulkanSource.contains("public void executeGraphicsDraw("));
        assertTrue(vulkanSource.contains("public void executeComputeDispatch("));
        assertTrue(vulkanSource.contains("public void executeClear("));
        assertTrue(vulkanSource.contains("public void executeTransfer("));
        assertTrue(openglSource.contains("public void executeGraphicsDraw("));
        assertTrue(openglSource.contains("public void executeComputeDispatch("));
        assertTrue(openglSource.contains("public void executeClear("));
    }

    @Test
    void vulkanMaterializationConsumesRequestOwnedResourceBindingPlans() throws Exception {
        String vulkanSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String builderSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicGalSnapshotBuilder.java"));

        assertTrue(vulkanSource.contains("capturedGalRequest.compatibilitySnapshot().resourceBindingPlan().orElse(null)"),
            "Graphics descriptor materialization should consume the request-owned resource binding plan");
        assertTrue(vulkanSource.contains("capturedRequest.resourceBindingPlan().orElse(null)"),
            "Compute descriptor materialization should consume the request-owned resource binding plan");
        assertTrue(vulkanSource.contains("request.compatibilitySnapshot().sharedCompatibilityState()"),
            "Compute dispatch should prefer frontend-owned shared compatibility snapshots before native command emission");
        assertTrue(vulkanSource.contains("buildSharedProgramResourcePlan(ctx, commandBufferHandle, descriptor, programId, sharedSnapshot)"),
            "Compute descriptor planning should lower frontend-owned compute snapshots when available");
        assertTrue(vulkanSource.contains("snapshot.bufferBindings().getOrDefault(VulkanicAPI.GL_DISPATCH_INDIRECT_BUFFER, 0)"),
            "Indirect compute dispatch should use the captured dispatch-indirect buffer binding when available");
        assertTrue(vulkanSource.contains("sharedTextureIdForSampler(sharedSnapshot.textureBindingsByKey(), binding, unit)"),
            "Shared resource lowering should resolve sampler textures from captured target-aware texture bindings");
        assertTrue(vulkanSource.contains("captureGraphicsRequest("),
            "Vulkan should provide a capture hook invoked before explicit graphics execution");
        assertTrue(vulkanSource.contains("request.compatibilitySnapshot().sharedCompatibilityState()"),
            "Vulkan graphics capture should consume shared frontend compatibility state when available");
        assertTrue(vulkanSource.contains("captureSharedGalDraw("),
            "Vulkan should lower shared semantic draw snapshots instead of always rebuilding from Vulkan-owned state");
        assertTrue(vulkanSource.contains("sharedDrawResourceSnapshot("),
            "Vulkan draw resources should be derived from shared VAO/index snapshots when available");
        assertTrue(vulkanSource.contains("sharedRenderStateSnapshot("),
            "Vulkan fixed-function draw state should be derived from shared snapshots when available");
        assertTrue(vulkanSource.contains("fixed.logicOpEnabled() ? legacyLogicOp(fixed.logicOp())"),
            "Vulkan fixed-function lowering should consume request-owned logic-op state");
        assertTrue(vulkanSource.contains("legacyPolygonMode(fixed.polygonMode())"),
            "Vulkan fixed-function lowering should consume request-owned polygon mode state");
        assertTrue(vulkanSource.contains("fixed.polygonOffsetEnabled() ? fixed.polygonOffsetFactor()"),
            "Vulkan fixed-function lowering should consume request-owned polygon offset state");
        assertTrue(vulkanSource.contains("pollCapturedLegacyGalDraw(galRequest, request)"),
            "Vulkan draw execution should consume the frontend-captured draw plan");
        assertTrue(builderSource.contains("legacyGraphicsSnapshot("),
            "Shared Vulkanic snapshot builder should own legacy graphics snapshot assembly");
        assertTrue(builderSource.contains("VulkanicCompatibilityState compatibilityState"),
            "Shared Vulkanic snapshot builder should accept frontend-owned compatibility state");
        assertTrue(vulkanSource.contains("Objects.requireNonNull(capturedGalRequest, \"capturedGalRequest\")"),
            "Vulkan graphics materialization should not accept unresolved/null GAL requests");
        assertTrue(vulkanSource.contains("withResourceBindingPlan(resourceBindingPlan)"),
            "Compute GAL requests should become immutable resource snapshots before materialization");
    }

    @Test
    void invalidRequestsFailBeforeBackendExecution() {
        assertThrows(IllegalArgumentException.class, () ->
            VulkanicGalExecutionRequest.GraphicsDrawRequest.legacyArrays(
                "bad-arrays",
                VulkanicPrimitiveMode.TRIANGLES,
                -1,
                3,
                1
            )
        );
        assertThrows(IllegalArgumentException.class, () ->
            VulkanicGalExecutionRequest.ComputeDispatchRequest.legacyIndirect("bad-dispatch", -1L)
        );
        assertThrows(IllegalArgumentException.class, () ->
            VulkanicGalExecutionRequest.ClearRequest.legacy("bad-clear")
        );
    }
}
