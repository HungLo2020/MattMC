package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanicGalSubmissionBoundaryTest {
    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    @Test
    void legacyDrawRequestsAreImmutableAndCarryExplicitResourcePlan() {
        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.indexed(
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
    void transferRequestsFreezeUploadAndClearPayloads() {
        ByteBuffer source = ByteBuffer.allocateDirect(4);
        source.put(new byte[] {1, 2, 3, 4});
        source.flip();
        VulkanicGalExecutionRequest.TransferRequest upload =
            VulkanicGalExecutionRequest.TransferRequest.of(
                "uploadTexture2D",
                new VulkanicGalExecutionRequest.UploadTexture2D(3553, 0, 32856, 1, 1, 0, 6408, 5121, source),
                VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                "legacy-bound-texture-target:3553:level:0",
                VulkanicPassResourceModel.Access.WRITE,
                VulkanicResourceUsage.TRANSFER_DST
            );
        source.put(0, (byte) 99);
        assertEquals(1, Byte.toUnsignedInt(upload.bytePayload().get(0)));

        float[] clearFloats = {0.25f, 0.5f, 0.75f, 1.0f};
        VulkanicGalExecutionRequest.TransferRequest clear =
            VulkanicGalExecutionRequest.TransferRequest.of(
                "clearNamedFramebufferfv",
                new VulkanicGalExecutionRequest.ClearNamedFramebufferFloat(4, 6144, 0, clearFloats),
                VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                "legacy-framebuffer:4:buffer:6144:draw:0",
                VulkanicPassResourceModel.Access.WRITE,
                VulkanicResourceUsage.TRANSFER_DST
            );
        clearFloats[0] = 9.0f;
        assertEquals(0.25f, clear.floatPayload()[0], 0.0f);
    }

    @Test
    void capturedGraphicsCompatibilitySnapshotReplacesLegacyPlaceholdersImmutably() {
        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "gui:item",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                6,
                1
            );
        assertEquals("frontend-compatibility-draft", request.compatibilitySnapshot().source());

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
        assertEquals("frontend-compatibility-draft", request.compatibilitySnapshot().source());
        assertEquals(
            "current-legacy",
            request.vertexInput().vertexBuffers().isEmpty() ? "current-legacy" : "captured",
            "legacy compatibility request must remain unchanged after capture"
        );
    }

    @Test
    void sharedGraphicsSnapshotsCaptureValidDefaultFixedFunctionState() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "defaults",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );

        VulkanicCompatibilityState.GraphicsSnapshot snapshot =
            state.compatibilitySnapshotFor(request).sharedCompatibilityState().orElseThrow();

        assertEquals(1, snapshot.fixedFunction().blendSrcRgb());
        assertEquals(0, snapshot.fixedFunction().blendDstRgb());
        assertEquals(1, snapshot.fixedFunction().blendSrcAlpha());
        assertEquals(0, snapshot.fixedFunction().blendDstAlpha());
        assertEquals(0x8006, snapshot.fixedFunction().blendEquationRgb());
        assertEquals(0x8006, snapshot.fixedFunction().blendEquationAlpha());
        assertEquals(0x0201, snapshot.fixedFunction().depthFunc());
    }

    @Test
    void sharedGraphicsSnapshotsCarrySamplerImageAliasesWithoutPassConflictValidation() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindTextureUnit(4, 69);
        state.bindImageTexture(1, 69, 0, false, 0, VulkanicAPI.GL_READ_WRITE, VulkanicAPI.GL_RGBA8);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "shaderpack:composite",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );

        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot snapshot =
            assertDoesNotThrow(() -> state.compatibilitySnapshotFor(request));

        assertTrue(snapshot.descriptorBindings().stream().anyMatch(binding ->
            binding.resourceUse().resource().stableKey().equals("legacy-texture:69")
                && binding.resourceUse().kind() == VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE
                && binding.resourceUse().feedbackLoop()
        ));
        assertTrue(snapshot.descriptorBindings().stream().anyMatch(binding ->
            binding.resourceUse().resource().stableKey().equals("legacy-texture:69")
                && binding.resourceUse().kind() == VulkanicPassResourceModel.ResourceKind.STORAGE_TEXTURE
                && binding.resourceUse().feedbackLoop()
        ));
        assertDoesNotThrow(() -> request.withCompatibilitySnapshot(snapshot));
    }

    @Test
    void sharedGraphicsSnapshotsReplacePlaceholderIndexResourceWithCapturedVaoBuffers() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindVertexArray(7);
        state.bindBuffer(0x8892, 11);
        state.setVertexAttribPointer(0, 3, VulkanicAPI.GL_FLOAT, false, false, 12, 0L);
        state.enableVertexAttribArray(0);
        state.bindBuffer(0x8893, 12);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.indexed(
                "dh:lod",
                VulkanicPrimitiveMode.TRIANGLES,
                6,
                VulkanicIndexType.INT,
                8L,
                1,
                0
            );
        VulkanicGalExecutionRequest.GraphicsDrawRequest captured =
            request.withCompatibilitySnapshot(state.compatibilitySnapshotFor(request));

        assertTrue(captured.resourcePlan().orderedUses().stream().anyMatch(use ->
            use.resource().stableKey().equals("legacy-buffer:11")
                && use.kind() == VulkanicPassResourceModel.ResourceKind.VERTEX_BUFFER
        ));
        assertTrue(captured.resourcePlan().orderedUses().stream().anyMatch(use ->
            use.resource().stableKey().equals("legacy-buffer:12")
                && use.kind() == VulkanicPassResourceModel.ResourceKind.INDEX_BUFFER
                && use.subresource().baseMipLevel() == 8
        ));
        assertTrue(captured.resourcePlan().orderedUses().stream().noneMatch(use ->
            use.resource().stableKey().equals("legacy-current-index-buffer")
        ));
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

        assertTrue(source.contains("executeGalGraphicsDraw(ctx, VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays("));
        assertTrue(source.contains("executeGalGraphicsDraw(ctx, VulkanicGalExecutionRequest.GraphicsDrawRequest.indexed("));
        assertTrue(source.contains("executeGalComputeDispatch(ctx, VulkanicGalExecutionRequest.ComputeDispatchRequest.direct("));
        assertTrue(source.contains("executeGalComputeDispatch(ctx, VulkanicGalExecutionRequest.ComputeDispatchRequest.indirect("));
        assertTrue(source.contains("private static final VulkanicCompatibilityState compatibilityState"));
        assertTrue(source.contains("VulkanicGalSnapshotBuilder.captureGraphicsDraw(ctx, backend, compatibilityState, request)"));
        assertTrue(source.contains("VulkanicGalSnapshotBuilder.captureComputeDispatch(ctx, backend, compatibilityState, request)"));
        assertTrue(source.contains("VulkanicGalSnapshotBuilder.captureTransfer(ctx, backend, compatibilityState, request)"));
        assertTrue(source.contains("executeGalClear(ctx, VulkanicGalExecutionRequest.ClearRequest.of("));
        assertFalse(source.contains("GraphicsDrawRequest.legacy"));
        assertFalse(source.contains("ComputeDispatchRequest.legacy"));
        assertFalse(source.contains("ComputePassBeginRequest.legacy"));
        assertFalse(source.contains("ClearRequest.legacy"));
        assertTrue(source.contains("executeGalTransfer(ctx, VulkanicGalExecutionRequest.TransferRequest.of("));
        assertFalse(source.contains("TransferRequest.legacy("));
        assertFalse(source.contains("TransferRequest.legacyWith"));
        assertTrue(source.contains("new VulkanicGalExecutionRequest.UploadTexture2D("));
        assertTrue(source.contains("new VulkanicGalExecutionRequest.UploadTexture2DSubImageBuffer("));
        assertTrue(source.contains("new VulkanicGalExecutionRequest.UploadTexture2DSubImagePointer("));
        assertTrue(source.contains("new VulkanicGalExecutionRequest.BufferSubData("));
        assertTrue(source.contains("new VulkanicGalExecutionRequest.NamedBufferSubData("));
        assertTrue(source.contains("new VulkanicGalExecutionRequest.ClearNamedFramebufferFloat("));
        assertTrue(source.contains("new VulkanicGalExecutionRequest.ClearBufferSubDataInt("));
        assertTrue(source.contains("new VulkanicGalExecutionRequest.ReadPixelsFloatArray("));
        assertTrue(source.contains("new VulkanicGalExecutionRequest.GenerateTextureMipmap("));
        assertTrue(source.contains("snapshotMultiDrawElementsBaseVertex("));
        assertTrue(source.contains("executeGalRenderPassBegin("));
        assertTrue(source.contains("VulkanicGalSnapshotBuilder.captureRenderPassBegin(ctx, backend, request)"));
        assertTrue(source.contains("backend.executeRenderPassBegin("));
        assertTrue(source.contains("VulkanicGalSnapshotBuilder.captureRenderPassEnd(ctx, backend, endRequest)"));
        assertTrue(source.contains("backend.executeRenderPassEnd(ctx, capturedEndRequest, delegate)"));
        assertTrue(source.contains("VulkanicGalSnapshotBuilder.captureComputePassBegin("));
        assertTrue(source.contains("backend.executeComputePassBegin(ctx, beginRequest)"));
        assertTrue(source.contains("VulkanicGalSnapshotBuilder.captureComputePassEnd("));
        assertTrue(source.contains("backend.executeComputePassEnd(ctx, capturedEndRequest)"));
        assertTrue(source.contains("compatibilityState.bindFramebuffer(GL_FRAMEBUFFER, framebuffer);"),
            "Texture-backed render-target binding must update the shared compatibility framebuffer used by immutable graphics snapshots");
        assertTrue(source.contains("publishPipelineResourcesToCompatibilityState(ctx, descriptor, bindings)")
                && source.contains("resourceBinding.type() != PipelineDescriptor.ResourceType.UNIFORM_BUFFER")
                && source.contains("compatibilityState.bindBufferRange("),
            "Descriptor-style UBO bindings must update shared compatibility state before immutable graphics snapshots are captured");
    }

    @Test
    void requestModelDoesNotExposeWeakTransferPayloadArraysOrLegacyFactories() throws Exception {
        String requestSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicGalExecutionRequest.java"));

        assertFalse(requestSource.contains("intArgs"));
        assertFalse(requestSource.contains("longArgs"));
        assertFalse(requestSource.contains("LegacyCompatibilityMetadata"));
        assertFalse(requestSource.contains("legacyMetadata"));
        assertFalse(requestSource.contains("unresolved-legacy"));
        assertFalse(requestSource.contains("unresolvedLegacy"));
        assertFalse(requestSource.contains("legacyCurrent()"));
        assertFalse(requestSource.contains("currentLegacy()"));
        assertFalse(requestSource.contains("TransferRequest.legacy("));
        assertFalse(requestSource.contains("legacyWithBytePayload"));
        assertFalse(requestSource.contains("legacyWithFloatPayload"));
        assertFalse(requestSource.contains("legacyWithIntPayload"));
        assertFalse(requestSource.contains("legacyWithFloatArrayOutput"));
        assertFalse(requestSource.contains("public static GraphicsDrawRequest legacy"));
        assertFalse(requestSource.contains("public static ComputeDispatchRequest legacy"));
        assertFalse(requestSource.contains("public static ComputePassBeginRequest legacy"));
        assertFalse(requestSource.contains("public static ClearRequest legacy"));
        assertTrue(requestSource.contains("sealed interface TransferOperation"));
        assertTrue(requestSource.contains("record UploadTexture2D("));
        assertTrue(requestSource.contains("record CopyImageSubData("));
        assertTrue(requestSource.contains("record BlitFramebuffer("));
        assertTrue(requestSource.contains("record ReadPixelsFloatArray("));
        assertTrue(requestSource.contains("record GenerateTextureMipmap("));
        assertTrue(requestSource.contains("sealed interface ExecutionResult"));
        assertTrue(requestSource.contains("record ExecutionSuccess("));
        assertTrue(requestSource.contains("record ExecutionFailure("));
        assertTrue(requestSource.contains("ExecutionStatus.STALE_RESOURCE"));
        assertTrue(requestSource.contains("ExecutionStatus.DEVICE_LOST"));
    }

    @Test
    void executableValidationRejectsDraftRequestsBeforeBackendEntry() {
        VulkanicGalExecutionRequest.GraphicsDrawRequest draftDraw =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "draft-draw",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicGalExecutionRequest.ExecutionResult drawResult =
            VulkanicGalExecutionRequest.validateGraphicsDraw(draftDraw);
        assertFalse(drawResult.successful());
        assertEquals(VulkanicGalExecutionRequest.ExecutionStatus.REJECTED, drawResult.status());

        VulkanicGalExecutionRequest.TransferRequest draftTransfer =
            VulkanicGalExecutionRequest.TransferRequest.of(
                "draft-transfer",
                new VulkanicGalExecutionRequest.GenerateMipmap(3553),
                VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                "legacy-bound-texture-target:3553",
                VulkanicPassResourceModel.Access.WRITE,
                VulkanicResourceUsage.TRANSFER_DST
            );
        VulkanicGalExecutionRequest.ExecutionResult transferResult =
            VulkanicGalExecutionRequest.validateTransfer(draftTransfer);
        assertFalse(transferResult.successful());
        assertEquals(VulkanicGalExecutionRequest.ExecutionStatus.REJECTED, transferResult.status());
    }

    @Test
    void sharedComputeSnapshotsReplaceDraftPipelineBeforeValidation() {
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(77);
        VulkanicGalExecutionRequest.ComputeDispatchRequest request =
            VulkanicGalExecutionRequest.ComputeDispatchRequest.direct("shaderpack:shadow-composite", 1, 2, 3);

        VulkanicGalExecutionRequest.ComputeDispatchRequest captured =
            request.withCompatibilitySnapshot(state.compatibilitySnapshotFor(request));

        assertEquals("legacy-program:77", captured.pipeline().stableKey());
        assertTrue(captured.compatibilitySnapshot().sharedCompatibilityState().isPresent());
        assertTrue(VulkanicGalExecutionRequest.validateComputeDispatch(captured).successful());
    }

    @Test
    void bothBackendsExposeConcreteExplicitConsumers() throws Exception {
        String vulkanSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String openglSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java"));
        String backendContract = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/GraphicsBackend.java"));

        assertTrue(backendContract.contains("extends VulkanicGalExecutor"));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDraw("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeComputeDispatch("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeClear("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeTransfer("));
        assertTrue(vulkanSource.contains("public net.vulkanic.VulkanicRenderPass executeRenderPassBegin("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeRenderPassEnd("));
        assertTrue(openglSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDraw("));
        assertTrue(openglSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeComputeDispatch("));
        assertTrue(openglSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeClear("));
        assertTrue(openglSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeTransfer("));
        assertTrue(openglSource.contains("public net.vulkanic.VulkanicRenderPass executeRenderPassBegin("));
        assertTrue(vulkanSource.contains("case VulkanicGalExecutionRequest.UploadTexture2D o -> uploadTexture2D("));
        assertTrue(openglSource.contains("case VulkanicGalExecutionRequest.UploadTexture2D o -> uploadTexture2D("));
        assertTrue(vulkanSource.contains("request.requireTransferSnapshot()"));
        assertTrue(openglSource.contains("request.requireTransferSnapshot()"));
        assertTrue(vulkanSource.contains("case VulkanicGalExecutionRequest.ClearNamedFramebufferFloat o -> clearNamedFramebufferfv("));
        assertTrue(openglSource.contains("case VulkanicGalExecutionRequest.ClearNamedFramebufferFloat o -> clearNamedFramebufferfv("));
        assertTrue(vulkanSource.contains("spine.executeRenderPassDraw("));
        assertTrue(vulkanSource.contains("spine.executeRenderPassEnd("));
    }

    @Test
    void renderPassBeginRequestsFreezeMutableDescriptorLabels() {
        AtomicReference<String> label = new AtomicReference<>("first-label");
        VulkanicTextureView view = new FakeTextureView(32, 16);
        VulkanicRenderPassDescriptor descriptor = VulkanicRenderPassDescriptor.color(
            label::get,
            view,
            OptionalInt.of(0xFF00FF00)
        );

        VulkanicGalExecutionRequest.RenderPassBeginRequest request =
            VulkanicGalExecutionRequest.RenderPassBeginRequest.descriptor("render-pass-test", descriptor);
        label.set("mutated-label");

        assertEquals("first-label", request.label());
        assertEquals("first-label", request.descriptor().orElseThrow().label().get());
        assertEquals(VulkanicGalExecutionRequest.RenderPassBeginKind.DESCRIPTOR, request.kind());
        assertEquals(VulkanicPassResourceModel.PassKind.RENDER, request.resourcePlan().request().kind());
        assertEquals(1, request.resourcePlan().request().attachments().size());
        assertEquals(0, request.resourcePlan().request().resources().size());
        assertEquals(1, request.resourcePlan().orderedUses().size());
        assertEquals("attachment[0]", request.resourcePlan().orderedUses().get(0).role());
    }

    @Test
    void renderPassLifecycleRequestsRejectContradictoryShapes() {
        assertThrows(IllegalArgumentException.class, () -> new VulkanicGalExecutionRequest.RenderPassBeginRequest(
            VulkanicGalExecutionRequest.SemanticIdentity.legacy("bad-pass"),
            VulkanicGalExecutionRequest.RenderPassBeginKind.FRAMEBUFFER,
            "bad-pass",
            Optional.empty(),
            Optional.empty(),
            OptionalInt.empty(),
            true,
            VulkanicGalExecutionRequest.RenderPassBeginRequest.framebuffer("good", () -> "good", 1, true).resourcePlan()
        ));
        assertThrows(IllegalArgumentException.class, () ->
            VulkanicGalExecutionRequest.RenderPassBeginRequest.framebuffer("bad-framebuffer", () -> "bad", -1, true)
        );
    }

    @Test
    void computePassLifecycleRequestsAreExplicitAndImmutable() {
        VulkanicGalExecutionRequest.ComputePassBeginRequest begin =
            VulkanicGalExecutionRequest.ComputePassBeginRequest.begin("dispatchCompute");
        VulkanicGalExecutionRequest.ComputePassEndRequest end =
            VulkanicGalExecutionRequest.ComputePassEndRequest.complete("dispatchCompute");
        VulkanicGalExecutionRequest.ComputePassEndRequest abandoned =
            VulkanicGalExecutionRequest.ComputePassEndRequest.abandoned("dispatchCompute", "boom");

        assertEquals(VulkanicPassResourceModel.PassKind.COMPUTE, begin.resourcePlan().request().kind());
        assertEquals(VulkanicPassResourceModel.PassKind.COMPUTE, end.resourcePlan().request().kind());
        assertEquals("dispatchCompute", begin.label());
        assertTrue(end.failureReason().isEmpty());
        assertTrue(abandoned.abandoned());
        assertEquals("boom", abandoned.failureReason().orElseThrow());
    }

    @Test
    void vulkanMaterializationConsumesRequestOwnedResourceBindingPlans() throws Exception {
        String vulkanSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String builderSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicGalSnapshotBuilder.java"));

        assertTrue(vulkanSource.contains("PipelineResourcePlanner.Plan resourcePlan = capturedResourceBindingPlan;"),
            "Graphics descriptor materialization should consume the resource plan derived from the immutable request snapshot");
        assertTrue(vulkanSource.contains("requestOwnedComputeResourceBindingPlan(")
                && vulkanSource.contains("materializeLegacyComputePipelineForDispatch(commandBufferHandle, request, requestOwnedResourcePlan)"),
            "Compute descriptor materialization should lower the request-owned shared snapshot without enriching the request");
        assertTrue(vulkanSource.contains("request.compatibilitySnapshot().sharedCompatibilityState()"),
            "Compute dispatch should prefer frontend-owned shared compatibility snapshots before native command emission");
        assertTrue(vulkanSource.contains("buildSharedProgramResourcePlan(ctx, commandBufferHandle, descriptor, programId, sharedSnapshot)"),
            "Compute descriptor planning should lower frontend-owned compute snapshots when available");
        assertTrue(vulkanSource.contains("snapshot.bufferBindings().getOrDefault(VulkanicAPI.GL_DISPATCH_INDIRECT_BUFFER, 0)"),
            "Indirect compute dispatch should use the captured dispatch-indirect buffer binding when available");
        assertTrue(vulkanSource.contains("sharedTextureIdForSampler(")
                && vulkanSource.contains("sharedSnapshot.textureUnitBindings()")
                && vulkanSource.contains("sharedSnapshot.textureBindingsByKey()"),
            "Shared resource lowering should resolve sampler textures from captured direct and target-aware texture bindings");
        assertTrue(builderSource.contains("return request.withCompatibilitySnapshot(")
                && builderSource.contains("compatibilityState.compatibilitySnapshotFor(request)"),
            "Shared Vulkanic snapshot builder should capture graphics compatibility state before backend entry");
        assertTrue(vulkanSource.contains("galRequest.compatibilitySnapshot().sharedCompatibilityState()"),
            "Vulkan graphics execution should consume shared frontend compatibility state from the request");
        assertTrue(vulkanSource.contains("captureRequestOwnedGalDraw("),
            "Vulkan should lower request-owned semantic draw snapshots instead of rebuilding from Vulkan-owned state");
        assertTrue(!vulkanSource.contains("pendingCapturedGalDraws"),
            "Vulkan graphics execution must not keep a backend-owned request enrichment queue");
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
        assertTrue(vulkanSource.contains("captureRequestOwnedGalDraw("),
            "Vulkan draw execution should consume the immutable request-owned draw snapshot");
        assertTrue(builderSource.contains("legacyGraphicsSnapshot("),
            "Shared Vulkanic snapshot builder should own legacy graphics snapshot assembly");
        assertTrue(builderSource.contains("VulkanicCompatibilityState compatibilityState"),
            "Shared Vulkanic snapshot builder should accept frontend-owned compatibility state");
        assertTrue(vulkanSource.contains("Objects.requireNonNull(capturedGalRequest, \"capturedGalRequest\")"),
            "Vulkan graphics materialization should not accept unresolved/null GAL requests");
        assertTrue(vulkanSource.contains("requestOwnedComputeResourceBindingPlan(")
                && vulkanSource.contains("capturedRequest.compatibilitySnapshot().sharedCompatibilityState().isEmpty()"),
            "Compute GAL lowering should reject uncaptured requests and derive native bindings from request-owned snapshots");
        assertTrue(vulkanSource.contains("containsEquivalentResourceUse(capturedUses, use)"),
            "Vulkan graphics request validation should compare semantic resource-use equivalence, not raw record order/labels");
    }

    @Test
    void openGlRequestLoweringPreservesDirectTextureUnitTargets() throws Exception {
        String openGlSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java"));
        String stateSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicCompatibilityState.java"));

        assertTrue(stateSource.contains("private final Map<Integer, Integer> textureUnitBindings"),
            "Shared compatibility state must keep targetless glBindTextureUnit bindings separate from target-specific bindings");
        assertTrue(stateSource.contains("textureUnitBindings.put(unitIndex, texture)")
                && stateSource.contains("textureBindings.keySet().removeIf(key -> key.unit() == unitIndex)"),
            "Targetless texture-unit binding capture must replace target-aware state instead of manufacturing a GL_TEXTURE_2D target");
        assertTrue(openGlSource.contains("snapshot.textureUnitBindings().entrySet()")
                && openGlSource.contains("ARBDirectStateAccess.glBindTextureUnit(unit, texture)"),
            "OpenGL request lowering must replay targetless texture-unit snapshots with glBindTextureUnit");
        assertTrue(openGlSource.contains("snapshot.textureBindingsByKey().entrySet()")
                && openGlSource.contains("GL11.glBindTexture(key.target(), texture)"),
            "OpenGL request lowering must preserve target-aware legacy bindings separately");
        assertTrue(openGlSource.contains("int previousActiveTextureUnit = activeTextureUnitIndex")
                && openGlSource.contains("restoreActiveTextureUnit(previousActiveTextureUnit)"),
            "OpenGL request lowering must restore the active texture unit after replaying immutable texture snapshots");
        assertTrue(openGlSource.contains("normalizeDrawBufferForFramebuffer(snapshot.drawFramebuffer(), buffer)")
                && openGlSource.contains("GL11.glDrawBuffer(drawBuffers[0])"),
            "OpenGL request lowering must restore default-framebuffer draw routing from immutable snapshots");
        assertTrue(openGlSource.contains("bindTexture(ctx, textureTarget, textureHandle)")
                && openGlSource.contains("VulkanicTextureTarget.fromLegacyGlTarget"),
            "OpenGL descriptor lowering must bind resources through the canonical captured texture target");
        assertTrue(!openGlSource.contains("VulkanicAPI.setActiveTextureUnitIndex(ctx, samplerBinding.textureUnit())"),
            "OpenGL descriptor lowering must not mutate shared legacy active-texture state while executing an immutable request");
        assertTrue(openGlSource.contains("setActiveTextureUnit(ctx, GL13.GL_TEXTURE0 + samplerBinding.textureUnit())"),
            "OpenGL descriptor lowering should apply the texture unit locally before binding request-owned resources");
        assertTrue(openGlSource.contains("VulkanicAPI.setDrawBufferColorAttachment0(ctx)")
                && openGlSource.contains("VulkanicAPI.setReadBufferColorAttachment(ctx, 0)"),
            "OpenGL texture-backed render passes must select their color attachment through shared compatibility state");
        assertTrue(openGlSource.contains("GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0)")
                && openGlSource.contains("GL11.glDrawBuffer(GL11.GL_BACK)"),
            "OpenGL presentation must restore explicit read/draw buffer routing after shaderpack MRT passes");
    }

    @Test
    void invalidRequestsFailBeforeBackendExecution() {
        assertThrows(IllegalArgumentException.class, () ->
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "bad-arrays",
                VulkanicPrimitiveMode.TRIANGLES,
                -1,
                3,
                1
            )
        );
        assertThrows(IllegalArgumentException.class, () ->
            VulkanicGalExecutionRequest.ComputeDispatchRequest.indirect("bad-dispatch", -1L)
        );
        assertThrows(IllegalArgumentException.class, () ->
            VulkanicGalExecutionRequest.ClearRequest.of("bad-clear")
        );
    }

    private static final class FakeTextureView extends VulkanicTextureView {
        private final int width;
        private final int height;

        private FakeTextureView(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public VulkanicTexture texture() {
            throw new UnsupportedOperationException("test view has no native texture");
        }

        @Override
        public int getBaseMipLevel() {
            return 0;
        }

        @Override
        public int getMipLevelCount() {
            return 1;
        }

        @Override
        public int getWidth(int mipLevel) {
            return width;
        }

        @Override
        public int getHeight(int mipLevel) {
            return height;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
