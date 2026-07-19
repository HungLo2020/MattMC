package net.vulkanic.backends.vulkan;

import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.vertex.VertexFormat;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanDrawExecutionCoordinatorTest {
    private final VulkanDrawExecutionCoordinator coordinator = new VulkanDrawExecutionCoordinator();

    @Test
    void arraysDrawPlanCapturesProgramVertexBuffersTopologyAndCommandShape() {
        VulkanDrawExecutionCoordinator.DrawExecutionPlan plan = coordinator.planLegacyDraw(
            VulkanDrawExecutionCoordinator.SemanticDrawRequest.arrays("world", VulkanicAPI.GL_TRIANGLES, 4, 12, 1),
            program(
                41,
                List.of(new VulkanDrawExecutionCoordinator.ReflectedVertexInputSnapshot(0, "vec3")),
                Map.of()
            ),
            vao(
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot(
                    0, 0, 3, VulkanicAPI.GL_FLOAT, false, false, 0, 0
                )),
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot(0, 12, 8L, 0, 77)),
                List.of(
                    new VulkanDrawExecutionCoordinator.VertexBufferBindingPlan(
                        VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING, 0, 0L, true
                    ),
                    new VulkanDrawExecutionCoordinator.VertexBufferBindingPlan(0, 77, 8L, false)
                )
            ),
            renderState()
        );

        assertFalse(plan.command().indexed());
        assertEquals(4, plan.command().firstVertex());
        assertEquals(12, plan.command().vertexCount());
        assertEquals(1, plan.command().instanceCount());
        assertNotNull(plan.descriptor());
        assertEquals(VertexFormat.Mode.TRIANGLES, plan.descriptor().getPortableState().vertexFormatMode());
        assertEquals(2, plan.vertexStream().vertexBuffers().size());
        assertEquals(77, plan.vertexStream().vertexBuffers().get(1).bufferId());
        assertEquals(8L, plan.vertexStream().vertexBuffers().get(1).offset());
        assertEquals(1, plan.resourcePlan().orderedUses().size());
        assertEquals(VulkanicPassResourceModel.ResourceKind.VERTEX_BUFFER, plan.resourcePlan().orderedUses().get(0).kind());
        assertEquals("legacy-buffer:77", plan.resourcePlan().orderedUses().get(0).resource().stableKey());
        PipelineDescriptor.VertexInputAttribute position = attribute(plan.vertexStream().vertexInputState(), 0);
        assertEquals(PipelineDescriptor.VertexAttributeFormat.R32G32B32_SFLOAT, position.format());
    }

    @Test
    void indexedDrawPlanAccountsForByteOffsetBaseVertexAndBoundRange() {
        VulkanDrawExecutionCoordinator.DrawExecutionPlan plan = coordinator.planLegacyDraw(
            VulkanDrawExecutionCoordinator.SemanticDrawRequest.indexed(
                "indexed",
                VulkanicAPI.GL_TRIANGLES,
                6,
                VulkanicIndexType.SHORT,
                4L,
                3,
                -2
            ),
            program(
                42,
                List.of(new VulkanDrawExecutionCoordinator.ReflectedVertexInputSnapshot(0, "vec3")),
                Map.of()
            ),
            indexedVao(
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot(
                    0, 0, 3, VulkanicAPI.GL_FLOAT, false, false, 0, 0
                )),
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot(0, 12, 0L, 0, 9)),
                List.of(new VulkanDrawExecutionCoordinator.VertexBufferBindingPlan(0, 9, 0L, false)),
                5,
                0x1234L,
                16
            ),
            renderState()
        );

        assertTrue(plan.command().indexed());
        assertEquals(2, plan.command().firstIndex());
        assertEquals(6, plan.command().indexCount());
        assertEquals(-2, plan.command().baseVertex());
        assertEquals(3, plan.command().instanceCount());
        assertEquals(2, plan.resourcePlan().orderedUses().size());
        assertEquals(VulkanicPassResourceModel.ResourceKind.INDEX_BUFFER, plan.resourcePlan().orderedUses().get(1).kind());
        assertEquals("legacy-buffer:5", plan.resourcePlan().orderedUses().get(1).resource().stableKey());
        coordinator.validateBoundIndexRange(
            new VulkanDrawExecutionCoordinator.BoundIndexStream(0x1234L, 16, VulkanicIndexType.SHORT),
            plan.indexStream()
        );
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> coordinator.validateBoundIndexRange(
                new VulkanDrawExecutionCoordinator.BoundIndexStream(0x1234L, 15, VulkanicIndexType.SHORT),
                plan.indexStream()
            )
        );
        assertTrue(exception.getMessage().contains("Indexed draw exceeds bound index buffer range"));
    }

    @Test
    void missingReflectedInputsUseDefaultAttributeBinding() {
        VulkanDrawExecutionCoordinator.DrawExecutionPlan plan = coordinator.planLegacyDraw(
            VulkanDrawExecutionCoordinator.SemanticDrawRequest.arrays("defaults", VulkanicAPI.GL_TRIANGLES, 0, 3, 1),
            program(
                43,
                List.of(new VulkanDrawExecutionCoordinator.ReflectedVertexInputSnapshot(4, "uvec4")),
                Map.of("iris_color", 7)
            ),
            vao(
                List.of(),
                List.of(),
                List.of()
            ),
            renderState()
        );

        PipelineDescriptor.VertexInputState vertexInput = plan.vertexStream().vertexInputState();
        PipelineDescriptor.VertexInputBinding defaultBinding = binding(
            vertexInput,
            VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING
        );
        assertEquals(16, defaultBinding.stride());
        assertEquals(PipelineDescriptor.VertexInputRate.INSTANCE, defaultBinding.inputRate());
        assertEquals(PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_UINT, attribute(vertexInput, 4).format());
        assertEquals(PipelineDescriptor.VertexAttributeFormat.R32G32B32A32_SFLOAT, attribute(vertexInput, 7).format());
        assertEquals(1, plan.vertexStream().vertexBuffers().size());
        assertEquals(
            VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING,
            plan.vertexStream().vertexBuffers().get(0).binding()
        );
        assertTrue(plan.vertexStream().vertexBuffers().get(0).defaultAttributeBuffer());
    }

    @Test
    void instancedAttributeDivisorsProduceInstanceBindings() {
        PipelineDescriptor.VertexInputState vertexInput = coordinator.planLegacyVertexInput(
            program(
                44,
                List.of(new VulkanDrawExecutionCoordinator.ReflectedVertexInputSnapshot(2, "vec2")),
                Map.of()
            ),
            vao(
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot(
                    2, 3, 2, VulkanicAPI.GL_FLOAT, false, false, 16, 1
                )),
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot(3, 32, 0L, 1, 82)),
                List.of(new VulkanDrawExecutionCoordinator.VertexBufferBindingPlan(3, 82, 0L, false))
            ).vertexArray()
        );

        assertNotNull(vertexInput);
        assertEquals(PipelineDescriptor.VertexInputRate.INSTANCE, binding(vertexInput, 3).inputRate());
        assertEquals(32, binding(vertexInput, 3).stride());
        assertEquals(16, attribute(vertexInput, 2).offset());
    }

    @Test
    void attributeMappingPreservesIntegerNormalizedAndFloatSemantics() {
        assertEquals(
            PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_UNORM,
            VulkanDrawExecutionCoordinator.legacyVertexAttributeFormatForShaderInput(
                VulkanicAPI.GL_UNSIGNED_BYTE, 4, true, false, "vec4"
            )
        );
        assertEquals(
            PipelineDescriptor.VertexAttributeFormat.R8G8B8A8_UINT,
            VulkanDrawExecutionCoordinator.legacyVertexAttributeFormatForShaderInput(
                VulkanicAPI.GL_UNSIGNED_BYTE, 4, true, false, "uvec4"
            )
        );
        assertEquals(
            PipelineDescriptor.VertexAttributeFormat.R32G32B32_SINT,
            VulkanDrawExecutionCoordinator.legacyVertexAttributeFormatForShaderInput(
                VulkanicAPI.GL_INT, 3, false, true, "ivec3"
            )
        );
        assertEquals(
            PipelineDescriptor.VertexAttributeFormat.R32G32B32_SFLOAT,
            VulkanDrawExecutionCoordinator.legacyVertexAttributeFormatForShaderInput(
                VulkanicAPI.GL_FLOAT, 3, false, false, "vec3"
            )
        );
    }

    @Test
    void topologyAndVertexLayoutChangesAffectSemanticPipelinePlan() {
        VulkanDrawExecutionCoordinator.DrawExecutionPlan triangles = coordinator.planLegacyDraw(
            VulkanDrawExecutionCoordinator.SemanticDrawRequest.arrays("triangles", VulkanicAPI.GL_TRIANGLES, 0, 3, 1),
            program(45, List.of(new VulkanDrawExecutionCoordinator.ReflectedVertexInputSnapshot(0, "vec3")), Map.of()),
            vao(
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot(
                    0, 0, 3, VulkanicAPI.GL_FLOAT, false, false, 0, 0
                )),
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot(0, 12, 0L, 0, 10)),
                List.of(new VulkanDrawExecutionCoordinator.VertexBufferBindingPlan(0, 10, 0L, false))
            ),
            renderState()
        );
        VulkanDrawExecutionCoordinator.DrawExecutionPlan quadsWithDifferentFormat = coordinator.planLegacyDraw(
            VulkanDrawExecutionCoordinator.SemanticDrawRequest.arrays("quads", 0x0007, 0, 4, 1),
            program(45, List.of(new VulkanDrawExecutionCoordinator.ReflectedVertexInputSnapshot(0, "uvec4")), Map.of()),
            vao(
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot(
                    0, 0, 4, VulkanicAPI.GL_UNSIGNED_BYTE, true, false, 0, 0
                )),
                List.of(new VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot(0, 4, 0L, 0, 10)),
                List.of(new VulkanDrawExecutionCoordinator.VertexBufferBindingPlan(0, 10, 0L, false))
            ),
            renderState()
        );

        assertEquals(VertexFormat.Mode.TRIANGLES, triangles.descriptor().getPortableState().vertexFormatMode());
        assertEquals(VertexFormat.Mode.QUADS, quadsWithDifferentFormat.descriptor().getPortableState().vertexFormatMode());
        assertNotEquals(
            triangles.descriptor().getVertexInputState(),
            quadsWithDifferentFormat.descriptor().getVertexInputState()
        );
        assertEquals(VertexFormat.Mode.QUADS, VulkanDrawExecutionCoordinator.legacyVertexMode(0x0007));
    }

    private static VulkanDrawExecutionCoordinator.LegacyProgramSnapshot program(
        int programId,
        List<VulkanDrawExecutionCoordinator.ReflectedVertexInputSnapshot> vertexInputs,
        Map<String, Integer> attributesByName
    ) {
        return new VulkanDrawExecutionCoordinator.LegacyProgramSnapshot(
            programId,
            true,
            "program-" + programId,
            List.of(
                module(VulkanicShaderStage.VERTEX, programId),
                module(VulkanicShaderStage.FRAGMENT, programId + 1)
            ),
            vertexInputs,
            attributesByName,
            new PipelineDescriptor.ResourceLayout(List.of(new PipelineDescriptor.ResourceBinding(
                0,
                0,
                "Sampler0",
                PipelineDescriptor.ResourceType.SAMPLER,
                null,
                Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT)
            )))
        );
    }

    private static VulkanicSpirvModule module(VulkanicShaderStage stage, int value) {
        return new VulkanicSpirvModule(stage, "main", new byte[]{(byte) value}, stage.name().toLowerCase(), "test");
    }

    private static VulkanDrawExecutionCoordinator.LegacyRenderStateSnapshot renderState() {
        return new VulkanDrawExecutionCoordinator.LegacyRenderStateSnapshot(
            false,
            0,
            0,
            0,
            0,
            true,
            0x0203,
            true,
            true,
            VulkanicAPI.GL_BACK,
            true,
            true,
            true,
            true,
            LogicOp.NONE,
            PolygonMode.FILL,
            0.0F,
            0.0F
        );
    }

    private static VulkanDrawExecutionCoordinator.DrawResourceSnapshot vao(
        List<VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot> attributes,
        List<VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot> bindings,
        List<VulkanDrawExecutionCoordinator.VertexBufferBindingPlan> vertexBuffers
    ) {
        return new VulkanDrawExecutionCoordinator.DrawResourceSnapshot(
            new VulkanDrawExecutionCoordinator.LegacyVaoSnapshot(attributes, bindings, vertexBuffers),
            null
        );
    }

    private static VulkanDrawExecutionCoordinator.DrawResourceSnapshot indexedVao(
        List<VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot> attributes,
        List<VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot> bindings,
        List<VulkanDrawExecutionCoordinator.VertexBufferBindingPlan> vertexBuffers,
        int indexBufferId,
        long indexBufferHandle,
        int indexBufferSize
    ) {
        return new VulkanDrawExecutionCoordinator.DrawResourceSnapshot(
            new VulkanDrawExecutionCoordinator.LegacyVaoSnapshot(attributes, bindings, vertexBuffers),
            new VulkanDrawExecutionCoordinator.IndexBufferSnapshot(indexBufferId, indexBufferHandle, indexBufferSize)
        );
    }

    private static PipelineDescriptor.VertexInputAttribute attribute(
        PipelineDescriptor.VertexInputState vertexInput,
        int location
    ) {
        assertNotNull(vertexInput);
        return vertexInput.attributes().stream()
            .filter(attribute -> attribute.location() == location)
            .findFirst()
            .orElseThrow();
    }

    private static PipelineDescriptor.VertexInputBinding binding(
        PipelineDescriptor.VertexInputState vertexInput,
        int binding
    ) {
        assertNotNull(vertexInput);
        return vertexInput.bindings().stream()
            .filter(candidate -> candidate.binding() == binding)
            .findFirst()
            .orElseThrow();
    }
}
