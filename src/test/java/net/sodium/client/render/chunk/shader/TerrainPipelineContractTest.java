package net.sodium.client.render.chunk.shader;

import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.blending.BlendMode;
import net.irisshaders.iris.gl.blending.BufferBlendInformation;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TerrainPipelineContractTest {
    @Test
    public void testSolidContractCapturesExplicitNoCullDepthWritingState() {
        TerrainPipelineContract.PassState state = TerrainPipelineContract.PassState.from(testPipeline("solid"), false);

        assertFalse(state.cull(), "Vulkan terrain contract should keep conservative no-cull behavior");
        assertTrue(state.writeColor());
        assertTrue(state.writeAlpha());
        assertTrue(state.writeDepth());
        assertTrue(state.blend().isEmpty());
    }

    @Test
    public void testTranslucentContractUsesBlendAndPreservesPipelineDepthWrites() {
        TerrainPipelineContract.PassState state = TerrainPipelineContract.PassState.from(testPipeline("translucent"), true);

        assertFalse(state.cull());
        assertTrue(state.blend().isPresent(), "Translucent terrain must retain an explicit blend state");
        assertEquals(BlendFunction.TRANSLUCENT, state.blend().get());
        assertTrue(state.writeDepth(), "Translucent terrain should preserve the RenderPipeline depth-write contract");
    }

    @Test
    public void testSharedPipelineLocationIsSemanticAndStable() {
        TerrainPipelineContract contract = new TerrainPipelineContract(
            7,
            false,
            DefaultVertexFormat.POSITION,
            java.util.List.of("Sampler0", "Sampler2"),
            TerrainPipelineContract.PassKind.SOLID,
            TerrainPipelineContract.PassState.from(testPipeline("solid"), false),
            ResourceLocation.withDefaultNamespace("pipeline/solid")
        );

        String location = contract.sharedPipelineLocation().toString();
        assertTrue(location.startsWith("sodium:pipeline/shared_chunk_solid_v7_"));
        assertEquals(location, contract.sharedPipelineLocation().toString());
    }

    @Test
    public void testIndexedBlendOverrideIsCapturedByAttachmentIndex() {
        TerrainPipelineContract.PassState state = TerrainPipelineContract.PassState.from(
            testPipeline("translucent"),
            true,
            List.of(new BufferBlendInformation(1, new BlendMode(
                VulkanicAPI.GL_SRC_ALPHA,
                VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA,
                VulkanicAPI.GL_ONE,
                VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA
            )))
        );

        Optional<Optional<BlendFunction>> attachmentOverride = state.blendOverrideForAttachment(1);

        assertTrue(attachmentOverride.isPresent());
        assertTrue(attachmentOverride.get().isPresent());
        assertEquals(BlendFunction.TRANSLUCENT, attachmentOverride.get().get());
        assertTrue(state.blendOverrideForAttachment(0).isEmpty());
    }

    @Test
    public void testIndexedBlendOffIsPreservedAsExplicitOverride() {
        TerrainPipelineContract.PassState state = TerrainPipelineContract.PassState.from(
            testPipeline("translucent"),
            true,
            List.of(new BufferBlendInformation(1, null))
        );

        Optional<Optional<BlendFunction>> attachmentOverride = state.blendOverrideForAttachment(1);

        assertTrue(attachmentOverride.isPresent());
        assertTrue(attachmentOverride.get().isEmpty());
    }

    private static RenderPipeline testPipeline(String path) {
        return RenderPipeline.builder()
            .withLocation(ResourceLocation.withDefaultNamespace("vulkanic_contract_test/" + path))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/position"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace("core/position"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
            .build();
    }
}
