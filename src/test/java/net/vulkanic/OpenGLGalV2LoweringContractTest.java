package net.vulkanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OpenGLGalV2LoweringContractTest {
    private static final Path REPO = Path.of("").toAbsolutePath();

    @Test
    void multiIndexedBaseVertexRequestPreservesSubdrawsAsOneSemanticCommand() {
        VulkanicGalExecutionRequest.GraphicsDrawCommand command =
            VulkanicGalExecutionRequest.GraphicsDrawCommand.multiIndexedBaseVertex(
                VulkanicPrimitiveMode.TRIANGLES,
                VulkanicIndexType.INT,
                List.of(
                    new VulkanicGalExecutionRequest.IndexedDraw(4, 12, 3),
                    new VulkanicGalExecutionRequest.IndexedDraw(9, 18, -2)
                )
            );

        assertEquals(VulkanicGalExecutionRequest.DrawCommandKind.MULTI_INDEXED_BASE_VERTEX, command.kind());
        assertEquals(2, command.indexedDraws().size());
        assertEquals(4, command.indexedDraws().get(0).firstIndex());
        assertEquals(12, command.indexedDraws().get(0).indexCount());
        assertEquals(3, command.indexedDraws().get(0).baseVertex());
        assertEquals(9, command.indexedDraws().get(1).firstIndex());
        assertEquals(18, command.indexedDraws().get(1).indexCount());
        assertEquals(-2, command.indexedDraws().get(1).baseVertex());
    }

    @Test
    void openglGalV2LowersMultidrawWithNativeMultidrawCommand() throws IOException {
        String source = Files.readString(REPO.resolve(
            "src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java"));
        String v2Body = source.substring(
            source.indexOf("public VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDrawV2"),
            source.indexOf("private void applyGraphicsRequestState")
        );

        assertTrue(v2Body.contains("drawMultiIndexedBaseVertexRequest"),
            "OpenGL v2 lowering should keep a dedicated multidraw lowering path");
        assertTrue(source.contains("GL32C.nglMultiDrawElementsBaseVertex"),
            "OpenGL v2 multidraw should emit one native multi-draw command");
        assertFalse(v2Body.contains("case MULTI_INDEXED_BASE_VERTEX -> {"),
            "OpenGL v2 multidraw must not inline-expand subdraws in the switch body");
    }

    @Test
    void openglGalV2ResourceSetLoweringIsGenerationKeyed() throws IOException {
        String source = Files.readString(REPO.resolve(
            "src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java"));

        assertTrue(source.contains("appliedGalV2ResourceSetKey"),
            "OpenGL v2 resource lowering should track the applied resource-set semantic key");
        assertTrue(source.contains("resourceSet.semanticKey().equals(appliedGalV2ResourceSetKey)"),
            "Equivalent v2 resource sets should skip redundant GL binding replay");
        assertTrue(source.contains("appliedGalV2ResourceCoverage"),
            "Skipping v2 resource-set replay must still preserve legacy-missing-binding coverage");
    }

    @Test
    void openglGalV2SeparatesVertexLayoutFromStreamOffsets() throws IOException {
        String source = Files.readString(REPO.resolve(
            "src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java"));
        String v2VertexBody = source.substring(
            source.indexOf("private void applyVertexInputV2"),
            source.indexOf("@Nullable\n    private static VulkanicGalV2.VertexStream vertexStreamForBinding")
        );

        assertTrue(v2VertexBody.contains("configureGalV2VertexLayout"),
            "OpenGL v2 should configure immutable vertex layout separately from stream offsets");
        assertTrue(v2VertexBody.contains("GL43C.glBindVertexBuffer"),
            "OpenGL v2 stream offset changes should lower through vertex-buffer bindings");
        assertFalse(v2VertexBody.contains("glVertexAttribPointer("),
            "OpenGL v2 stream offset changes must not rebuild attribute pointers");
        assertFalse(v2VertexBody.contains("glVertexAttribIPointer("),
            "OpenGL v2 stream offset changes must not rebuild integer attribute pointers");
        assertFalse(v2VertexBody.contains("new java.util.HashMap"),
            "OpenGL v2 vertex lowering should not allocate a per-draw binding lookup map");
        assertTrue(source.contains("galV2VertexArrayBindingStates"),
            "OpenGL v2 should keep generated VAO stream/index binding state backend-owned");
        assertTrue(v2VertexBody.contains("appliedGalV2IndexStream.copyFrom(vaoBindingState.indexStream)")
                && v2VertexBody.contains("appliedGalV2VertexStreams.put(binding, state.copy())"),
            "Switching back to a generated GAL v2 VAO should restore its cached stream bindings instead of replaying all streams");
        assertTrue(v2VertexBody.contains("vaoBindingState.vertexStreams")
                && v2VertexBody.contains(".set(buffer, offset, binding.stride())"),
            "OpenGL v2 stream updates should update the generated VAO binding cache");
        assertTrue(source.contains("private void invalidateAppliedGalV2VertexArraySelection()"),
            "OpenGL v2 should have a narrow invalidation path for raw compatibility VAO changes");
        assertTrue(source.contains("if (target == GL15.GL_ELEMENT_ARRAY_BUFFER)")
                && source.contains("invalidateGalV2GeneratedVertexArrayBindingCaches();"),
            "Raw element-buffer binds can mutate the current VAO and must invalidate generated GAL v2 binding assumptions");
        assertTrue(source.contains("pendingCompatibilityVertexArray = vao;"),
            "Raw compatibility VAO binds should be deferred until a raw VAO operation actually needs native state");
        assertTrue(v2VertexBody.contains("discardPendingCompatibilityVertexArrayForGalV2Draw();"),
            "Migrated GAL v2 draws should consume explicit vertex state without materializing pending raw VAO binds");
        assertTrue(source.contains("materializePendingCompatibilityVertexArray();\n        GL20.glVertexAttribPointer")
                && source.contains("if (target == GL15.GL_ELEMENT_ARRAY_BUFFER) {\n            materializePendingCompatibilityVertexArray();"),
            "Raw VAO-mutating operations must materialize deferred compatibility VAO state before touching native OpenGL state");
    }

    @Test
    void galV2HasPersistentDrawTemplatesAndDisabledDefaultsInVertexLayoutIdentity() throws IOException {
        String source = Files.readString(REPO.resolve("src/main/java/net/vulkanic/VulkanicGalV2.java"));

        assertTrue(VulkanicGalV2.contractSchema().contains("PersistentDrawTemplate"),
            "GAL v2 contract should expose persistent draw-template objects");
        assertTrue(VulkanicGalV2.contractSchema().contains("GraphicsCommandStream"),
            "GAL v2 contract should expose compact graphics command streams");
        assertTrue(source.contains("DRAW_TEMPLATES_BY_KEY"),
            "GAL v2 should cache persistent draw templates by semantic key");
        assertTrue(source.contains("recordGalV2DrawTemplateLookup"),
            "Persistent template reuse should be visible in bounded performance diagnostics");
        assertTrue(source.contains("vao.defaultAttributes().entrySet()"),
            "Disabled attribute defaults are part of vertex-layout semantics and must affect identity");
        String explicitObjectsRecord = source.substring(
            source.indexOf("public record ExplicitGraphicsObjects("),
            source.indexOf("public record ProgramState(")
        );
        assertFalse(explicitObjectsRecord.contains("GraphicsSnapshot"),
            "Migrated GAL v2 graphics objects must not retain broad GraphicsSnapshot seeds");
    }

    @Test
    void normalGalV2DrawPathDoesNotReachBroadGraphicsSnapshotCapture() throws IOException {
        String stateSource = Files.readString(REPO.resolve(
            "src/main/java/net/vulkanic/VulkanicCompatibilityState.java"));
        String normalCaptureBody = stateSource.substring(
            stateSource.indexOf("public Optional<VulkanicGalV2.ExplicitGraphicsDrawRequest> tryCaptureGalV2GraphicsDraw("),
            stateSource.indexOf("public ComputeSnapshot captureCompute(")
        );

        assertTrue(normalCaptureBody.contains("v2GraphicsState"),
            "Normal migrated GAL v2 draws should consume mutation-time persistent v2 state");
        assertTrue(stateSource.contains("private volatile V2GraphicsState v2GraphicsState"),
            "Mutation-time v2 graphics state should be safely published as an immutable volatile snapshot");
        assertFalse(normalCaptureBody.contains("synchronized (lock)"),
            "Normal migrated GAL v2 draws should not take the broad compatibility-state lock just to read an immutable snapshot");
        assertFalse(normalCaptureBody.contains("captureGraphics("),
            "Normal migrated GAL v2 draws must not call broad graphics snapshot capture");
        assertFalse(normalCaptureBody.contains("GraphicsSnapshot"),
            "Normal migrated GAL v2 draws must not consume GraphicsSnapshot");

        String galV2Source = Files.readString(REPO.resolve("src/main/java/net/vulkanic/VulkanicGalV2.java"));
        String normalSliceBody = galV2Source.substring(
            galV2Source.indexOf("public static Optional<ExplicitGraphicsDrawRequest> tryCaptureLegacyProgramSlice(\n        VulkanicCompatibilityState.GraphicsStateView"),
            galV2Source.indexOf("private static VulkanicPassResourceModel.PassExecutionPlan nonEagerResourcePlan()")
        );
        assertFalse(normalSliceBody.contains("GraphicsSnapshot"),
            "The normal GAL v2 slice builder should use the persistent graphics-state view, not GraphicsSnapshot");
        assertFalse(normalSliceBody.contains("captureGraphics("),
            "The normal GAL v2 slice builder must not trigger broad compatibility capture");
    }

    @Test
    void normalGalV2DrawsEncodeRedundantBindsOnlyWhenSemanticStateChanges() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(11);
        state.bindVertexArray(3);
        state.bindBuffer(0x8892, 7);
        state.setVertexAttribPointer(0, 3, VulkanicAPI.GL_FLOAT, false, false, 12, 0L);
        state.enableVertexAttribArray(0);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "terrain:v2",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );

        VulkanicGalV2.ExplicitGraphicsDrawRequest first =
            state.tryCaptureGalV2GraphicsDraw(request, false).orElseThrow();
        VulkanicGalV2.ExplicitGraphicsDrawRequest second =
            state.tryCaptureGalV2GraphicsDraw(request, false).orElseThrow();

        assertTrue(first.commandStream().commands().stream()
                .anyMatch(command -> command.kind() == VulkanicGalV2.GraphicsCommandKind.BIND_GRAPHICS_PIPELINE),
            "The first command stream must bind persistent state");
        assertEquals(
            List.of(VulkanicGalV2.GraphicsCommandKind.DRAW),
            second.commandStream().commands().stream().map(VulkanicGalV2.GraphicsCommand::kind).toList(),
            "An unchanged migrated v2 draw should encode only the draw command after persistent state is current"
        );

        state.resetGalV2CommandEncoder();
        VulkanicGalV2.ExplicitGraphicsDrawRequest afterReset =
            state.tryCaptureGalV2GraphicsDraw(request, false).orElseThrow();
        assertTrue(afterReset.commandStream().commands().stream()
                .anyMatch(command -> command.kind() == VulkanicGalV2.GraphicsCommandKind.BIND_GRAPHICS_PIPELINE),
            "Resetting the command encoder must force persistent state to be rebound");
    }

    @Test
    void deterministicSettledReadyMinimumCountsAcceptsScriptDelimiter() throws IOException {
        String source = Files.readString(REPO.resolve(
            "src/main/java/net/minecraft/client/dev/DeterministicCameraCapture.java"));

        assertTrue(source.contains("raw.split(\"[,;]\")"),
            "Performance ready-gate minimum counts must accept both comma and semicolon delimiters used by shell/script invocations");
        assertTrue(source.contains("minimumCount <= 0"),
            "Families without an explicit positive minimum should remain observational and must not block readiness");
    }

    @Test
    void perfAuditBucketsGalV2SemanticKeysWithoutRetainingFullDrawIdentity() throws Exception {
        Method descriptorFamily = VulkanPerfAudit.class.getDeclaredMethod("descriptorFamily", String.class);
        descriptorFamily.setAccessible(true);

        String key = (String)descriptorFamily.invoke(
            null,
            "gal-v2_legacy-program_11_uniform-shape_abcd_pipeline_11_mode_TRIANGLES"
                + "_resource-set_program_11_bindings_1234_framebuffer_99_state_ffff"
        );

        assertEquals("vulkanic_legacy_program_11", key);
    }

    @Test
    void galV2ResourceSetsUseSemanticBindingIdentityNotMutationVersion() {
        VulkanicGalV2.clearForTests();
        VulkanicCompatibilityState state = new VulkanicCompatibilityState();
        state.bindProgram(11);
        state.bindTextureUnit(0, 44);

        VulkanicGalExecutionRequest.GraphicsDrawRequest request =
            VulkanicGalExecutionRequest.GraphicsDrawRequest.arrays(
                "terrain:v2-resource",
                VulkanicPrimitiveMode.TRIANGLES,
                0,
                3,
                1
            );

        state.tryCaptureGalV2GraphicsDraw(request, true).orElseThrow();
        int resourceSetsAfterFirstCapture = VulkanicGalV2.resourceSetCountForTests();
        state.bindTextureUnit(0, 44);
        state.tryCaptureGalV2GraphicsDraw(request, true).orElseThrow();

        assertEquals(
            resourceSetsAfterFirstCapture,
            VulkanicGalV2.resourceSetCountForTests(),
            "Rebinding the same semantic resource must not fragment persistent resource-set identity"
        );
    }
}
