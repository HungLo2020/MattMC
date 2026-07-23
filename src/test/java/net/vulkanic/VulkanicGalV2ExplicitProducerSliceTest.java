package net.vulkanic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VulkanicGalV2ExplicitProducerSliceTest {
    @BeforeEach
    void clearGalV2Registries() {
        VulkanicGalV2.clearForTests();
    }

    @Test
    void explicitProducerObjectsReuseEquivalentSemanticState() {
        VulkanicGalV2.ExplicitGraphicsObjects first =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("terrain-slice", resourceBindings(7L)));
        VulkanicGalV2.ExplicitGraphicsObjects second =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("terrain-slice", resourceBindings(7L)));

        assertSame(first, second);
        assertEquals(first.handle(), second.handle());
        assertEquals(1, VulkanicGalV2.graphicsObjectCountForTests());
    }

    @Test
    void explicitProducerResourceGenerationChangesOnlyResourceSetIdentity() {
        VulkanicGalV2.ExplicitGraphicsObjects first =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("terrain-slice", resourceBindings(7L)));
        VulkanicGalV2.ExplicitGraphicsObjects changed =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("terrain-slice", resourceBindings(8L)));

        assertEquals(first.vertexLayoutHandle(), changed.vertexLayoutHandle());
        assertEquals(first.pipeline(), changed.pipeline());
        assertNotEquals(first.resourceSet(), changed.resourceSet());
    }

    @Test
    void resourceSetSupportsNameBasedUniformBufferLookup() {
        VulkanicGalV2.ExplicitGraphicsObjects objects =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("terrain-slice", resourceBindings(7L)));
        VulkanicGalV2.ResourceSet set = VulkanicGalV2.requireResourceSet(objects.resourceSet());

        assertTrue(set.bufferRangeBindingOrNull("DynamicTransforms") != null);
        assertFalse(set.resourceBinding("missing").isPresent());
    }

    @Test
    void explicitUniformPayloadIsImmutableAndGenerationVersioned() {
        Map<Integer, VulkanicCompatibilityState.UniformValue> uniforms = Map.of(
            3,
            new VulkanicCompatibilityState.UniformValue("float4", new int[0], new float[] {1.0F, 2.0F, 3.0F, 4.0F}, false, 4, 1)
        );

        VulkanicGalV2.UniformPayload payload =
            VulkanicGalV2.uniformPayloadForExplicitProgram(12, 4L, uniforms, "terrain-uniforms");

        assertEquals(12, payload.programId());
        assertEquals(4L, payload.payloadVersion());
        assertEquals(1, payload.uniformsByLocation().size());
        assertEquals(VulkanicAPI.generatedStandaloneUniformBlockName(),
            VulkanicGalV2.requireUniformBinding(payload.binding()).bindingName());
    }

    @Test
    void descriptorBackedExplicitObjectsUseNoopUniformPayload() {
        PipelineDescriptor descriptor = minimalPipelineDescriptor();
        VulkanicGalV2.ExplicitGraphicsObjects objects = VulkanicGalV2.registerExplicitGraphicsObjects(
            descriptor("gui-text-slice", resourceBindings(7L), 0, descriptor)
        );
        VulkanicGalV2.ProgramState program = VulkanicGalV2.requireGraphicsObjects(objects.handle()).programState();
        VulkanicGalV2.UniformPayload payload = VulkanicGalV2.emptyUniformPayload("gui-text-slice");

        assertEquals(0, program.programId());
        assertSame(descriptor, program.pipelineDescriptor());
        assertEquals(0, payload.programId());
        assertTrue(payload.uniformsByLocation().isEmpty());
    }

    @Test
    void explicitDrawRequestCarriesNoBroadCompatibilitySnapshot() {
        VulkanicGalV2.ExplicitGraphicsObjects objects =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("terrain-slice", resourceBindings(7L)));
        VulkanicGalV2.ResourceSet set = VulkanicGalV2.requireResourceSet(objects.resourceSet());
        VulkanicGalV2.UniformPayload payload =
            VulkanicGalV2.uniformPayloadForExplicitProgram(12, 1L, Map.of(), "terrain-uniforms");
        VulkanicGalExecutionRequest.GraphicsDrawCommand command =
            VulkanicGalExecutionRequest.GraphicsDrawCommand.multiIndexedBaseVertex(
                VulkanicPrimitiveMode.TRIANGLES,
                VulkanicIndexType.INT,
                List.of(new VulkanicGalExecutionRequest.IndexedDraw(0, 6, 0))
            );
        VulkanicGalV2.VertexStreamBindings streams = new VulkanicGalV2.VertexStreamBindings(
            List.of(new VulkanicGalV2.VertexStream(0, 42, 0L, false)),
            Optional.of(new VulkanicGalV2.IndexStream(43, VulkanicIndexType.INT, 0L))
        );
        VulkanicPassResourceModel.PassExecutionPlan plan = emptyPlan("terrain-draw");

        VulkanicGalV2.ExplicitGraphicsDrawRequest request = new VulkanicGalV2.ExplicitGraphicsDrawRequest(
            identity("terrain-draw"),
            objects.handle(),
            set.handle(),
            payload,
            VulkanicGalV2.GraphicsCommandStream.drawOnly(command),
            command,
            streams,
            plan,
            "terrain-plan"
        );

        assertEquals(objects.handle(), request.graphicsObjects());
        assertEquals(set.handle(), request.resourceSet());
        assertEquals(streams, request.vertexStreams());
    }

    @Test
    void retainedExplicitObjectsSurviveBoundedRegistryPrune() {
        VulkanicGalV2.ExplicitGraphicsObjects retained =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("retained-slice", resourceBindings(7L)));
        try (VulkanicGalV2.RetainedHandle ignored = VulkanicGalV2.retain(retained.handle(), "test-owner")) {
            for (int index = 0; index < 16; index++) {
                VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("unretained-" + index, resourceBindings(index + 1L)));
            }

            VulkanicGalV2.pruneGlobalRegistriesForTests(1);

            assertSame(retained, VulkanicGalV2.requireGraphicsObjects(retained.handle()));
            assertEquals(1, VulkanicGalV2.retainedHandleCountForTests());
        }
    }

    @Test
    void releasedExplicitObjectsMayBePruned() {
        VulkanicGalV2.ExplicitGraphicsObjects objects =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("released-slice", resourceBindings(7L)));
        VulkanicGalV2.RetainedHandle retained = VulkanicGalV2.retain(objects.handle(), "test-owner");
        retained.close();
        retained.close();

        VulkanicGalV2.pruneGlobalRegistriesForTests(1);

        assertEquals(0, VulkanicGalV2.graphicsObjectCountForTests());
        assertEquals(0, VulkanicGalV2.retainedHandleCountForTests());
    }

    @Test
    void invalidationMakesGenerationStalePredictably() {
        VulkanicGalV2.ExplicitGraphicsObjects objects =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("stale-slice", resourceBindings(7L)));

        VulkanicGalV2.invalidate(objects.handle(), "test-delete");

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> VulkanicGalV2.requireGraphicsObjects(objects.handle())
        );
    }

    @Test
    void deviceLossOrShutdownInvalidatesOutstandingHandles() {
        VulkanicGalV2.ExplicitGraphicsObjects objects =
            VulkanicGalV2.registerExplicitGraphicsObjects(descriptor("shutdown-slice", resourceBindings(7L)));
        VulkanicGalV2.retain(objects.handle(), "test-owner");

        VulkanicGalV2.invalidateAllForDeviceLossOrShutdown();

        assertEquals(0, VulkanicGalV2.graphicsObjectCountForTests());
        assertEquals(0, VulkanicGalV2.retainedHandleCountForTests());
    }

    private static VulkanicGalV2.ExplicitGraphicsDescriptor descriptor(
        String key,
        List<VulkanicGalV2.ResourceBinding> bindings
    ) {
        return descriptor(key, bindings, 12, null);
    }

    private static VulkanicGalV2.ExplicitGraphicsDescriptor descriptor(
        String key,
        List<VulkanicGalV2.ResourceBinding> bindings,
        int programId,
        PipelineDescriptor pipelineDescriptor
    ) {
        VulkanicCompatibilityState.FixedFunctionSnapshot fixed = fixedFunction(key);
        VulkanicCompatibilityState.FramebufferSnapshot framebuffer = framebuffer(key);
        String resourceLayoutKey = "resource-layout:" + key;
        String resourceSetKey = "resource-set:" + key + ":" + bindings.hashCode();
        return new VulkanicGalV2.ExplicitGraphicsDescriptor(
            programId,
            1L,
            "program:" + key,
            pipelineDescriptor,
            fixed,
            "fixed:" + key,
            "topology:triangles",
            "pipeline:" + key,
            3,
            framebuffer,
            "target:" + key,
            vertexLayout(),
            "vertex-layout:chunk",
            List.of(
                new VulkanicGalV2.ResourceLayoutBinding(
                    "DynamicTransforms",
                    VulkanicPassResourceModel.BindingKind.BUFFER_RANGE,
                    VulkanicPassResourceModel.ResourceKind.UNIFORM_BUFFER,
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty()
                )
            ),
            resourceLayoutKey,
            bindings,
            resourceSetKey,
            "objects:" + key
        );
    }

    private static PipelineDescriptor minimalPipelineDescriptor() {
        PipelineDescriptor.PortableState state = new PipelineDescriptor.PortableState(
            ResourceLocation.withDefaultNamespace("pipeline/gui_text_test"),
            ResourceLocation.withDefaultNamespace("pipeline/gui_text_test/vertex"),
            ResourceLocation.withDefaultNamespace("pipeline/gui_text_test/fragment"),
            Map.of(),
            java.util.Set.of(),
            List.of("Sampler0"),
            List.of(),
            Optional.empty(),
            DepthTestFunction.NO_DEPTH_TEST,
            PolygonMode.FILL,
            false,
            VulkanicAPI.GL_BACK,
            true,
            true,
            false,
            net.blaze3d.platform.LogicOp.NONE,
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.TRIANGLES,
            0.0f,
            0.0f
        );
        return PipelineDescriptor.fromPortableState(state).withResourceLayout(new PipelineDescriptor.ResourceLayout(List.of(
            new PipelineDescriptor.ResourceBinding(
                0,
                0,
                "DynamicTransforms",
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
                null
            )
        )));
    }

    private static List<VulkanicGalV2.ResourceBinding> resourceBindings(long generation) {
        VulkanicPassResourceModel.CanonicalResourceReference reference =
            VulkanicPassResourceModel.CanonicalResourceReference.bufferRange(
                "DynamicTransforms",
                VulkanicPassResourceModel.ResourceKind.UNIFORM_BUFFER,
                "dynamic-transforms",
                0L,
                128L,
                VulkanicPassResourceModel.Access.READ,
                VulkanicResourceUsage.INFERRED,
                OptionalInt.empty(),
                OptionalInt.of(99),
                generation
            );
        return List.of(new VulkanicGalV2.ResourceBinding(
            "DynamicTransforms",
            reference.asResourceUse("DynamicTransforms", false, 0),
            OptionalInt.empty(),
            OptionalInt.empty(),
            Optional.of(reference),
            Optional.empty()
        ));
    }

    private static VulkanicGalV2.VertexLayout vertexLayout() {
        return new VulkanicGalV2.VertexLayout(
            List.of(new VulkanicGalV2.VertexBindingLayout(0, 32, 0)),
            List.of(
                new VulkanicGalV2.VertexAttributeLayout(0, 0, 3, VulkanicAPI.GL_FLOAT, false, false, 0, 0),
                new VulkanicGalV2.VertexAttributeLayout(1, 0, 4, VulkanicAPI.GL_UNSIGNED_BYTE, true, false, 12, 0)
            ),
            Map.of(),
            false
        );
    }

    private static VulkanicCompatibilityState.FramebufferSnapshot framebuffer(String key) {
        return new VulkanicCompatibilityState.FramebufferSnapshot(
            3,
            Map.of(VulkanicAPI.GL_COLOR_ATTACHMENT0,
                new VulkanicCompatibilityState.AttachmentState(VulkanicAPI.GL_COLOR_ATTACHMENT0, 4, 0)),
            List.of(VulkanicAPI.GL_COLOR_ATTACHMENT0),
            VulkanicAPI.GL_COLOR_ATTACHMENT0,
            "framebuffer:" + key
        );
    }

    private static VulkanicCompatibilityState.FixedFunctionSnapshot fixedFunction(String key) {
        return new VulkanicCompatibilityState.FixedFunctionSnapshot(
            Optional.empty(),
            Optional.empty(),
            false,
            1,
            0,
            1,
            0,
            VulkanicAPI.GL_FUNC_ADD,
            VulkanicAPI.GL_FUNC_ADD,
            true,
            515,
            true,
            true,
            VulkanicAPI.GL_BACK,
            false,
            false,
            false,
            0,
            VulkanicAPI.GL_FRONT_AND_BACK,
            VulkanicAPI.GL_FILL,
            false,
            0.0F,
            0.0F,
            true,
            true,
            true,
            true,
            Map.of(),
            Map.of(),
            Map.of(),
            "fixed:" + key
        );
    }

    private static VulkanicPassResourceModel.PassExecutionPlan emptyPlan(String key) {
        VulkanicPassResourceModel.PassRequest request = new VulkanicPassResourceModel.PassRequest(
            VulkanicPassResourceModel.PassKind.RENDER,
            key,
            List.of(),
            List.of(),
            List.of(),
            List.of(new VulkanicPassResourceModel.Command(key, OptionalInt.of(1), OptionalInt.empty())),
            List.of(),
            false,
            false
        );
        return new VulkanicPassResourceModel.PassExecutionPlan(request, List.of(), List.of());
    }

    private static VulkanicGalExecutionRequest.SemanticIdentity identity(String key) {
        return new VulkanicGalExecutionRequest.SemanticIdentity(
            "sodium-terrain",
            "solid",
            "minecraft:terrain",
            "chunk",
            "main",
            key,
            0
        );
    }
}
