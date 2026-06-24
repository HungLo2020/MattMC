package net.vulkanic;

import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanicDrawStateSnapshotTest {

    @Test
    public void testSnapshotLogCapturesRequestedTranslatedDrawAndResourceState() {
        RenderPipeline pipeline = pipelineBuilder()
            .withBlend(BlendFunction.TRANSLUCENT)
            .build();
        VulkanicDrawStateSnapshot snapshot = VulkanicDrawStateSnapshot.create(
            "vulkan",
            "test-path",
            pipeline,
            "extent=16x16 colors=1 depth{tex=2}",
            7,
            true,
            1,
            new VulkanicDrawStateSnapshot.TranslatedPipelineState(
                "VK_CULL_MODE_NONE",
                "VK_FRONT_FACE_CLOCKWISE",
                true,
                true,
                "VK_COMPARE_OP_LESS",
                "RGBA",
                1
            ),
            new VulkanicDrawStateSnapshot.ScissorStateSnapshot(true, 1, 2, 3, 4),
            new VulkanicDrawStateSnapshot.DrawCall(true, 0, 5, 6, 7, 0, 1, VertexFormat.IndexType.INT),
            new VulkanicDrawStateSnapshot.ResourceState(4, 3, 2, 1, List.of("Sampler2"))
        );

        String logFields = snapshot.toLogFields();

        assertTrue(logFields.contains("backend=vulkan"));
        assertTrue(logFields.contains("pipeline=minecraft:pipeline/draw_state_snapshot_test"));
        assertTrue(logFields.contains("requested{depthTest=LESS_DEPTH_TEST,depthWrite=true,cull=true"));
        assertTrue(logFields.contains("translated{cullMode=VK_CULL_MODE_NONE,frontFace=VK_FRONT_FACE_CLOCKWISE"));
        assertTrue(logFields.contains("scissor{enabled=true,x=1,y=2,width=3,height=4}"));
        assertTrue(logFields.contains("draw{indexed=true,firstVertex=0,baseVertex=5,firstIndex=6,indexCount=7"));
        assertTrue(logFields.contains("resources{reflected=4,submitted=3,samplers=2,uniforms=1,missing=[Sampler2]}"));
    }

    @Test
    public void testOpenGlTranslatedStateReflectsPipelineRequest() {
        RenderPipeline pipeline = pipelineBuilder()
            .withBlend(BlendFunction.TRANSLUCENT)
            .withColorWrite(true, false)
            .build();

        VulkanicDrawStateSnapshot.TranslatedPipelineState state =
            VulkanicDrawStateSnapshot.TranslatedPipelineState.opengl(pipeline);

        assertEquals("GL_CULL_ENABLED", state.cullMode());
        assertEquals("OPENGL_CURRENT", state.frontFace());
        assertTrue(state.depthTestEnabled());
        assertTrue(state.depthWriteEnabled());
        assertEquals("LESS_DEPTH_TEST", state.depthCompareOp());
        assertEquals("RGB", state.colorWriteMask());
        assertEquals(1, state.blendAttachmentCount());
    }

    private static RenderPipeline.Builder pipelineBuilder() {
        return RenderPipeline.builder()
            .withLocation(ResourceLocation.withDefaultNamespace("pipeline/draw_state_snapshot_test"))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/test_vertex"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace("core/test_fragment"))
            .withDepthTestFunction(DepthTestFunction.LESS_DEPTH_TEST)
            .withCull(true)
            .withDepthWrite(true)
            .withColorWrite(true, true)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES);
    }
}
