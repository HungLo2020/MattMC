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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        assertTrue(backendContract.contains("executeGraphicsDrawV2(")
                || Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicGalExecutor.java"))
                    .contains("executeGraphicsDrawV2("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDraw("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDrawV2("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeComputeDispatch("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeClear("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeTransfer("));
        assertTrue(vulkanSource.contains("public net.vulkanic.VulkanicRenderPass executeRenderPassBegin("));
        assertTrue(vulkanSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeRenderPassEnd("));
        assertTrue(openglSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDraw("));
        assertTrue(openglSource.contains("public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDrawV2("));
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
    void galV2HasSeparateVersionedContractFingerprint() {
        assertEquals("2.0.0", VulkanicGalV2.CONTRACT_VERSION);
        assertTrue(VulkanicGalV2.contractSchema().startsWith("vulkanic-gal-v2-contract 2.0.0"));
        assertEquals(VulkanicGalV2.contractSchemaFingerprint(), VulkanicGalV2.contractSchemaFingerprint());
        assertFalse(VulkanicGalV2.contractSchemaFingerprint().equals(VulkanicGalExecutionRequest.contractSchemaFingerprint()));
    }

    @Test
    void hotLegacyProgramDrawsProduceStableExplicitV2Objects() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(11);
        state.setUniformFloat(-1, 0.25f);
        state.setUniformInt(3, 7);

        VulkanicGalExecutionRequest.GraphicsDrawRequest draft =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "drawArrays",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                6,
                1
            );
        VulkanicGalExecutionRequest.GraphicsDrawRequest first =
            VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft);
        VulkanicGalExecutionRequest.GraphicsDrawRequest second =
            VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft);

        VulkanicGalV2.ExplicitGraphicsDrawRequest firstV2 =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(first).orElseThrow();
        VulkanicGalV2.ExplicitGraphicsDrawRequest secondV2 =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(second).orElseThrow();

        assertEquals(firstV2.graphicsObjects(), secondV2.graphicsObjects());
        assertEquals(firstV2.uniformPayload(), secondV2.uniformPayload());
        assertEquals(1, VulkanicGalV2.graphicsObjectCountForTests());
        assertEquals(0, VulkanicGalV2.uniformPayloadCountForTests());

        state.setUniformInt(3, 7);
        VulkanicGalV2.ExplicitGraphicsDrawRequest unchangedV2 =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();
        assertEquals(firstV2.uniformPayload(), unchangedV2.uniformPayload());
        assertEquals(0, VulkanicGalV2.uniformPayloadCountForTests());

        state.setUniformInt(3, 8);
        VulkanicGalExecutionRequest.GraphicsDrawRequest changed =
            VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft);
        VulkanicGalV2.ExplicitGraphicsDrawRequest changedV2 =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(changed).orElseThrow();

        assertEquals(firstV2.graphicsObjects(), changedV2.graphicsObjects());
        assertEquals(1, VulkanicGalV2.graphicsObjectCountForTests());
        assertEquals(firstV2.resourceSet(), changedV2.resourceSet());
        assertFalse(firstV2.uniformPayload().equals(changedV2.uniformPayload()));
        assertEquals(0, VulkanicGalV2.uniformPayloadCountForTests());
        VulkanicGalV2.ResourceSet resourceSet = VulkanicGalV2.requireResourceSet(firstV2.resourceSet());
        VulkanicGalV2.ResourceBinding standalone =
            resourceSet.uniformBinding(VulkanicAPI.generatedStandaloneUniformBlockName()).orElseThrow();
        VulkanicGalV2.UniformBinding uniformBinding =
            VulkanicGalV2.requireUniformBinding(standalone.uniformBinding().orElseThrow());
        assertEquals(11, uniformBinding.programId());
        assertEquals(VulkanicAPI.generatedStandaloneUniformBlockName(), uniformBinding.bindingName());
        assertEquals(1, VulkanicGalV2.uniformBindingCountForTests());
        VulkanicGalV2.ExplicitGraphicsObjects changedObjects =
            VulkanicGalV2.requireGraphicsObjects(changedV2.graphicsObjects());
        assertEquals(11, changedObjects.programState().programId());
        assertEquals(8, changedV2
            .uniformPayload()
            .uniformsByLocation()
            .get(3)
            .ints()[0]);
        assertEquals(0.25f, changedV2
            .uniformPayload()
            .uniformsByLocation()
            .get(-1)
            .floats()[0]);
    }

    @Test
    void galV2LegacyProgramSupportCoversAllConcreteLegacyProgramsByDefault() {
        assertTrue(VulkanicGalV2.supportsLegacyProgramId(11));
        assertTrue(VulkanicGalV2.supportsLegacyProgramId(9));
        assertFalse(VulkanicGalV2.supportsLegacyProgramId(0));
        assertTrue(VulkanicGalV2.supportsLegacyProgramId(147));
    }

    @Test
    void galV2OpenGlNonEagerCaptureDoesNotDependOnVulkanProgramIds() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(147);
        state.bindTexture(3, VulkanicAPI.GL_TEXTURE_2D, 41);
        state.bindBufferRange(VulkanicAPI.GL_UNIFORM_BUFFER, 2, 52, 16L, 32L);

        VulkanicGalExecutionRequest.GraphicsDrawRequest draft =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "opengl-legacy-program-id-regression",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicCompatibilityState.GraphicsSnapshot snapshot = state.captureGraphics(draft);

        VulkanicGalV2.ExplicitGraphicsDrawRequest first =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(snapshot, draft, false).orElseThrow();
        assertTrue(VulkanicGalV2.tryCaptureLegacyProgramSlice(snapshot, draft, true).isPresent());
        VulkanicGalV2.ResourceSet firstResources = VulkanicGalV2.requireResourceSet(first.resourceSet());
        assertTrue(firstResources.sampledTextureBinding(3).isPresent());
        assertTrue(firstResources.bufferRangeBinding(2).isPresent());

        state.bindTexture(3, VulkanicAPI.GL_TEXTURE_2D, 42);
        VulkanicGalV2.ExplicitGraphicsDrawRequest changed =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(state.captureGraphics(draft), draft, false).orElseThrow();
        assertEquals(first.graphicsObjects(), changed.graphicsObjects());
        assertFalse(first.resourceSet().equals(changed.resourceSet()));
        VulkanicGalV2.ResourceSet changedResources = VulkanicGalV2.requireResourceSet(changed.resourceSet());
        assertEquals(42, changedResources.sampledTextureBinding(3)
            .orElseThrow()
            .resourceReference()
            .orElseThrow()
            .legacyId()
            .orElseThrow());
    }

    @Test
    void galV2OpenGlNonEagerCapturePreservesDynamicTransformsBindingName() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(11);
        state.bindNamedBufferRange(
            VulkanicAPI.GL_UNIFORM_BUFFER,
            0,
            52,
            16L,
            164L,
            "DynamicTransforms"
        );

        VulkanicGalExecutionRequest.GraphicsDrawRequest draft =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "dynamic-transform-draw",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );

        VulkanicGalV2.ExplicitGraphicsDrawRequest first =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(state.captureGraphics(draft), draft, false).orElseThrow();
        VulkanicGalV2.ResourceSet firstResources = VulkanicGalV2.requireResourceSet(first.resourceSet());
        VulkanicGalV2.ResourceBinding dynamicTransforms = firstResources.bufferRangeBinding(0).orElseThrow();
        assertEquals("DynamicTransforms", dynamicTransforms.name());
        assertEquals(52, dynamicTransforms.resourceReference().orElseThrow().legacyId().orElseThrow());
        assertEquals(16, dynamicTransforms.resourceReference().orElseThrow().subresource().baseMipLevel());
        assertEquals(164, dynamicTransforms.resourceReference().orElseThrow().subresource().levelCount());

        state.bindNamedBufferRange(
            VulkanicAPI.GL_UNIFORM_BUFFER,
            0,
            52,
            180L,
            164L,
            "DynamicTransforms"
        );
        VulkanicGalV2.ExplicitGraphicsDrawRequest offsetChanged =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(state.captureGraphics(draft), draft, false).orElseThrow();
        assertEquals(first.graphicsObjects(), offsetChanged.graphicsObjects());
        assertFalse(first.resourceSet().equals(offsetChanged.resourceSet()));
        VulkanicGalV2.ResourceBinding changedDynamicTransforms =
            VulkanicGalV2.requireResourceSet(offsetChanged.resourceSet()).bufferRangeBinding(0).orElseThrow();
        assertEquals("DynamicTransforms", changedDynamicTransforms.name());
        assertEquals(180, changedDynamicTransforms.resourceReference().orElseThrow().subresource().baseMipLevel());
    }

    @Test
    void galV2ResourceSetsReuseEquivalentBindingsAndTrackGenerations() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(11);
        state.bindTexture(4, VulkanicAPI.GL_TEXTURE_2D, 91);
        state.bindSampler(4, 7);
        state.bindImageTexture(2, 92, 1, false, 0, VulkanicAPI.GL_READ_WRITE, VulkanicAPI.GL_RGBA8);
        state.bindBufferRange(VulkanicAPI.GL_UNIFORM_BUFFER, 3, 81, 16L, 64L);

        VulkanicGalExecutionRequest.GraphicsDrawRequest draft =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "drawArrays",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );

        VulkanicGalV2.ExplicitGraphicsDrawRequest first =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();
        int resourceSetsAfterFirstDraw = VulkanicGalV2.resourceSetCountForTests();
        VulkanicGalV2.ExplicitGraphicsDrawRequest second =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();

        assertEquals(first.resourceSet(), second.resourceSet());
        assertEquals(resourceSetsAfterFirstDraw, VulkanicGalV2.resourceSetCountForTests());
        VulkanicGalV2.ResourceSet resourceSet = VulkanicGalV2.requireResourceSet(first.resourceSet());
        VulkanicGalV2.ResourceBinding sampler = resourceSet.sampledTextureBinding(4).orElseThrow();
        assertEquals(91, sampler.resourceReference().orElseThrow().legacyId().orElseThrow());
        assertEquals(VulkanicAPI.GL_TEXTURE_2D, sampler.resourceReference().orElseThrow().legacyTarget().orElseThrow());
        assertEquals(7, sampler.resourceReference().orElseThrow().samplerObject().orElseThrow());
        assertTrue(resourceSet.storageImageBinding(2).isPresent());
        assertTrue(resourceSet.bufferRangeBinding(3).isPresent());

        state.setActiveTextureUnitIndex(4);
        state.markBoundTextureStorageReplaced(VulkanicAPI.GL_TEXTURE_2D);
        VulkanicGalV2.ExplicitGraphicsDrawRequest replacedTexture =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();
        assertFalse(first.resourceSet().equals(replacedTexture.resourceSet()));
        assertEquals(resourceSetsAfterFirstDraw + 1, VulkanicGalV2.resourceSetCountForTests());
    }

    @Test
    void galV2EagerResourceCaptureDoesNotDoubleDeclareStorageImageBindings() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(11);
        state.bindBuffer(VulkanicBufferTarget.INDEX, 74);
        state.bindImageTexture(0, 73, 0, false, 0, VulkanicAPI.GL_READ_WRITE, VulkanicAPI.GL_RGBA8);

        VulkanicGalExecutionRequest.GraphicsDrawRequest draft =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.indexed(
                "dh-lod-image-unit-regression",
                VulkanicPrimitiveMode.TRIANGLES,
                6,
                VulkanicIndexType.INT,
                0L,
                1,
                0
            );
        VulkanicCompatibilityState.GraphicsSnapshot snapshot = state.captureGraphics(draft);

        VulkanicGalV2.ExplicitGraphicsDrawRequest explicit =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(snapshot, draft, true).orElseThrow();

        long storageImageUses = explicit.resourcePlan().orderedUses().stream()
            .filter(use -> use.resource().stableKey().equals("legacy-texture:73"))
            .filter(use -> use.kind() == VulkanicPassResourceModel.ResourceKind.STORAGE_TEXTURE)
            .count();
        assertEquals(1, storageImageUses);
        assertTrue(VulkanicGalV2.requireResourceSet(explicit.resourceSet()).storageImageBinding(0).isPresent());
    }

    @Test
    void galV2ResourceLayoutSurvivesResourceAndUniformContentChanges() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(9);
        state.bindTexture(1, VulkanicAPI.GL_TEXTURE_2D, 101);
        state.bindSampler(1, 3);
        state.setUniformInt(5, 10);

        VulkanicGalExecutionRequest.GraphicsDrawRequest draft =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "drawArrays",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );

        VulkanicGalV2.ExplicitGraphicsDrawRequest first =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();
        VulkanicGalV2.ExplicitGraphicsObjects firstObjects =
            VulkanicGalV2.requireGraphicsObjects(first.graphicsObjects());

        state.setUniformInt(5, 11);
        state.bindTexture(1, VulkanicAPI.GL_TEXTURE_2D, 102);
        VulkanicGalV2.ExplicitGraphicsDrawRequest changed =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();
        VulkanicGalV2.ExplicitGraphicsObjects changedObjects =
            VulkanicGalV2.requireGraphicsObjects(changed.graphicsObjects());

        assertEquals(firstObjects.resourceLayout(), changedObjects.resourceLayout());
        assertFalse(first.resourceSet().equals(changed.resourceSet()));
        assertEquals(11, changed
            .uniformPayload()
            .uniformsByLocation()
            .get(5)
            .ints()[0]);
    }

    @Test
    void galV2StandaloneUniformPublicationDoesNotUsePerDrawSliceLookup() throws Exception {
        String vulkanSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        Matcher v2ResolveMatcher = Pattern.compile(
            "private\\s+VulkanCompactResourceBindingTable\\s+resolveCompact\\s*\\(\\s*"
                + "CommandContext\\s+ctx\\s*,\\s*"
                + "long\\s+commandBufferHandle\\s*,\\s*"
                + "PipelineDescriptor\\s+descriptor\\s*,\\s*"
                + "VulkanDrawExecutionCoordinator\\.LegacyProgramSnapshot\\s+programSnapshot\\s*,\\s*"
                + "VulkanicGalV2\\.ResourceSet\\s+resourceSet\\s*\\)",
            Pattern.DOTALL
        ).matcher(vulkanSource);
        assertTrue(v2ResolveMatcher.find());
        int v2ResolveStart = v2ResolveMatcher.start();
        int v2ResolveEnd = vulkanSource.indexOf("private VulkanStandaloneUniformBinding resolveTemplateStandaloneUniformBinding", v2ResolveStart);
        assertTrue(v2ResolveEnd > v2ResolveStart);
        String v2Resolve = vulkanSource.substring(v2ResolveStart, v2ResolveEnd);

        assertTrue(v2Resolve.contains("resolveTemplateStandaloneUniformBinding(programSnapshot.programId(), binding.name(), resourceSet)"));
        assertFalse(v2Resolve.contains("getStandaloneUniformBufferSlice("));
        assertTrue(Pattern.compile(
            "materializeStandaloneUniformArenaBinding\\s*\\(\\s*"
                + "frameSlot\\s*,\\s*"
                + "pipelineLocation\\s*,\\s*"
                + "binding\\.name\\(\\)\\s*,\\s*"
                + "standaloneBinding\\s*\\)",
            Pattern.DOTALL
        ).matcher(vulkanSource).find());
        assertTrue(vulkanSource.contains("bindingTable.withUniformBufferBindings(dynamicUniformSlices(dynamicUniformStates))"));
    }

    @Test
    void galV2SeparatesVertexLayoutFromOffsetOnlyStreamChanges() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(11);
        state.bindVertexArray(2);
        state.setVertexAttribFormat(0, 3, VulkanicAPI.GL_FLOAT, false, false, 4);
        state.setVertexAttribBinding(0, 0);
        state.bindVertexBuffer(0, 41, 4L, 20);
        state.enableVertexAttribArray(0);
        state.setVertexAttribDivisor(0, 1);
        state.bindBuffer(0x8893, 55);

        VulkanicGalExecutionRequest.GraphicsDrawRequest draft =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.indexed(
                "drawElementsBaseVertex",
                VulkanicPrimitiveMode.TRIANGLES,
                6,
                VulkanicIndexType.SHORT,
                8L,
                2,
                3
            );
        VulkanicGalV2.ExplicitGraphicsDrawRequest first =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();

        state.bindVertexBuffer(0, 41, 36L, 20);
        VulkanicGalV2.ExplicitGraphicsDrawRequest offsetChanged =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();

        VulkanicGalV2.ExplicitGraphicsObjects firstObjects =
            VulkanicGalV2.requireGraphicsObjects(first.graphicsObjects());
        VulkanicGalV2.ExplicitGraphicsObjects offsetObjects =
            VulkanicGalV2.requireGraphicsObjects(offsetChanged.graphicsObjects());
        assertEquals(firstObjects.vertexLayoutHandle(), offsetObjects.vertexLayoutHandle());
        assertEquals(firstObjects.pipeline(), offsetObjects.pipeline());
        assertEquals(4L, first.vertexStreams().vertexStreams().get(0).baseOffset());
        assertEquals(36L, offsetChanged.vertexStreams().vertexStreams().get(0).baseOffset());
        assertEquals(55, offsetChanged.vertexStreams().indexStream().orElseThrow().buffer());
        assertEquals(8L, offsetChanged.vertexStreams().indexStream().orElseThrow().baseOffset());
    }

    @Test
    void galV2VertexLayoutChangesWhenAttributeRelativeOffsetChanges() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(11);
        state.bindVertexArray(3);
        state.bindBuffer(0x8892, 42);
        state.setVertexAttribPointer(0, 3, VulkanicAPI.GL_FLOAT, false, false, 20, 4L);
        state.enableVertexAttribArray(0);

        VulkanicGalExecutionRequest.GraphicsDrawRequest draft =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "drawArrays",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );
        VulkanicGalV2.ExplicitGraphicsDrawRequest first =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();

        state.setVertexAttribFormat(0, 3, VulkanicAPI.GL_FLOAT, false, false, 12);
        VulkanicGalV2.ExplicitGraphicsDrawRequest changed =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();

        VulkanicGalV2.ExplicitGraphicsObjects firstObjects =
            VulkanicGalV2.requireGraphicsObjects(first.graphicsObjects());
        VulkanicGalV2.ExplicitGraphicsObjects changedObjects =
            VulkanicGalV2.requireGraphicsObjects(changed.graphicsObjects());
        assertFalse(firstObjects.vertexLayoutHandle().equals(changedObjects.vertexLayoutHandle()));
    }

    @Test
    void galV2LegacyVertexAttribPointerStoresPointerAsStreamOffset() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(11);
        state.bindVertexArray(4);
        state.bindBuffer(0x8892, 77);
        state.setVertexAttribPointer(2, 4, VulkanicAPI.GL_UNSIGNED_BYTE, true, false, 28, 4096L);
        state.enableVertexAttribArray(2);

        VulkanicGalExecutionRequest.GraphicsDrawRequest draft =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "drawArrays",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );

        VulkanicGalV2.ExplicitGraphicsDrawRequest request =
            VulkanicGalV2.tryCaptureLegacyProgramSlice(
                VulkanicGalSnapshotBuilder.captureGraphicsDraw(immediateContext(), new NoopGalExecutor(), state, draft)
            ).orElseThrow();
        VulkanicGalV2.ExplicitGraphicsObjects objects =
            VulkanicGalV2.requireGraphicsObjects(request.graphicsObjects());

        VulkanicGalV2.VertexAttributeLayout attribute = objects.vertexLayout()
            .attributes()
            .stream()
            .filter(candidate -> candidate.location() == 2)
            .findFirst()
            .orElseThrow();
        VulkanicGalV2.VertexStream stream = request.vertexStreams()
            .vertexStreams()
            .stream()
            .filter(candidate -> candidate.binding() == 2)
            .findFirst()
            .orElseThrow();

        assertEquals(0, attribute.relativeOffset());
        assertEquals(4096L, stream.baseOffset());
        assertEquals(77, stream.buffer());
    }

    private static CommandContext immediateContext() {
        return new CommandContext() {
            @Override
            public boolean isImmediate() {
                return true;
            }

            @Override
            public long getHandle() {
                return 0L;
            }

            @Override
            public String getDebugName() {
                return "test-immediate";
            }
        };
    }

    private static final class NoopGalExecutor implements VulkanicGalExecutor {
        @Override
        public VulkanicRenderPass executeRenderPassBegin(
            CommandContext ctx,
            VulkanicGalExecutionRequest.RenderPassBeginRequest request
        ) {
            throw new UnsupportedOperationException("not used");
        }
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

        assertTrue(vulkanSource.contains("VulkanCompactResourceBindingTable resourceTable = capturedResourceBindingTable;")
                && vulkanSource.contains("spine.materializeGraphicsPipelineBinding(")
                && vulkanSource.contains("resourceTable"),
            "Graphics descriptor materialization should consume the compact resource table derived from the immutable request snapshot");
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
        assertTrue(vulkanSource.contains("Objects.requireNonNull(capturedUses, \"capturedUses\")"),
            "Vulkan graphics materialization should validate request-owned resource uses without requiring a v1 wrapper request");
        assertTrue(vulkanSource.contains("VulkanicGalV2.requireResourceSet(explicitRequest.resourceSet())"),
            "GAL v2 graphics lowering should resolve resources from the request-owned v2 resource set");
        String galV2Source = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicGalV2.java"));
        int explicitDrawStart = galV2Source.indexOf("public record ExplicitGraphicsDrawRequest(");
        int explicitDrawEnd = galV2Source.indexOf(
            "public static Optional<ExplicitGraphicsDrawRequest>",
            explicitDrawStart
        );
        assertTrue(explicitDrawStart >= 0 && explicitDrawEnd > explicitDrawStart);
        String explicitDrawRecord = galV2Source.substring(explicitDrawStart, explicitDrawEnd);
        assertFalse(explicitDrawRecord.contains("GraphicsSnapshot compatibilitySnapshot"),
            "Migrated GAL v2 draws must not carry a broad compatibility snapshot per command");
        assertFalse(explicitDrawRecord.contains("ProgramSnapshot programUniforms"),
            "Migrated GAL v2 draws must not carry compatibility ProgramSnapshot uniform payloads");
        assertTrue(explicitDrawRecord.contains("UniformPayload uniformPayload"),
            "GAL v2 draws should carry persistent uniform payload handles separately from reusable graphics-object shape");
        assertTrue(galV2Source.contains("public record UniformPayload("),
            "GAL v2 should represent standalone uniforms with persistent uniform payload objects");
        String openGlSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java"));
        assertTrue(openGlSource.contains("applyUniformPayloadV2(request.uniformPayload())"),
            "OpenGL GAL v2 lowering must apply the draw request's persistent uniform payload");
        assertTrue(openGlSource.contains("payload.semanticKey().equals(appliedGalV2UniformPayloadKey)"),
            "OpenGL GAL v2 lowering should skip uniform replay when the persistent payload is unchanged");
        String apiSource = Files.readString(PROJECT_ROOT.resolve("src/main/java/net/vulkanic/VulkanicAPI.java"));
        String executeDraw = apiSource.substring(
            apiSource.indexOf("private static void executeGalGraphicsDraw("),
            apiSource.indexOf("private static void executeGalComputeDispatch(")
        );
        assertFalse(executeDraw.contains("captureGraphics("),
            "VulkanicAPI should delegate v2 draw capture to frontend-owned compatibility state instead of calling captureGraphics directly");
        assertTrue(vulkanSource.contains("galV2ResourcePlanTemplate(descriptor, programSnapshot, resourceSet.layout())")
                && vulkanSource.contains("record GalV2ResourcePlanTemplateKey("),
            "GAL v2 graphics lowering should cache immutable resource-plan templates by v2 resource-layout semantics");
        assertFalse(vulkanSource.contains("compatibilityRequestForExplicitV2Draw"));
        assertFalse(vulkanSource.contains("gal-v2-explicit-compatibility-wrapper"));
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
