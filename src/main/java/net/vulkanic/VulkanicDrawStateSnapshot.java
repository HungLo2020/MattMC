package net.vulkanic;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of the effective state associated with one draw submission.
 */
public record VulkanicDrawStateSnapshot(
    String backend,
    String path,
    String pipeline,
    String renderTarget,
    int framebuffer,
    boolean hasDepthAttachment,
    int colorAttachmentCount,
    String requestedDepthTest,
    boolean requestedDepthWrite,
    boolean requestedCull,
    String requestedPolygonMode,
    boolean requestedBlend,
    boolean requestedColorWrite,
    boolean requestedAlphaWrite,
    String requestedColorLogic,
    TranslatedPipelineState translatedState,
    ScissorStateSnapshot scissor,
    DrawCall draw,
    ResourceState resources
) {
    public VulkanicDrawStateSnapshot {
        backend = Objects.requireNonNull(backend, "backend must not be null");
        path = Objects.requireNonNull(path, "path must not be null");
        pipeline = Objects.requireNonNull(pipeline, "pipeline must not be null");
        renderTarget = Objects.requireNonNull(renderTarget, "renderTarget must not be null");
        requestedDepthTest = Objects.requireNonNull(requestedDepthTest, "requestedDepthTest must not be null");
        requestedPolygonMode = Objects.requireNonNull(requestedPolygonMode, "requestedPolygonMode must not be null");
        requestedColorLogic = Objects.requireNonNull(requestedColorLogic, "requestedColorLogic must not be null");
        translatedState = Objects.requireNonNull(translatedState, "translatedState must not be null");
        scissor = Objects.requireNonNull(scissor, "scissor must not be null");
        draw = Objects.requireNonNull(draw, "draw must not be null");
        resources = Objects.requireNonNull(resources, "resources must not be null");
    }

    public static VulkanicDrawStateSnapshot create(
        String backend,
        String path,
        RenderPipeline pipeline,
        String renderTarget,
        int framebuffer,
        boolean hasDepthAttachment,
        int colorAttachmentCount,
        TranslatedPipelineState translatedState,
        ScissorStateSnapshot scissor,
        DrawCall draw,
        ResourceState resources
    ) {
        Objects.requireNonNull(pipeline, "pipeline must not be null");
        return new VulkanicDrawStateSnapshot(
            backend,
            path,
            pipeline.getLocation().toString(),
            renderTarget,
            framebuffer,
            hasDepthAttachment,
            colorAttachmentCount,
            pipeline.getDepthTestFunction().name(),
            pipeline.isWriteDepth(),
            pipeline.isCull(),
            pipeline.getPolygonMode().name(),
            pipeline.getBlendFunction().isPresent(),
            pipeline.isWriteColor(),
            pipeline.isWriteAlpha(),
            pipeline.getColorLogic().name(),
            translatedState,
            scissor,
            draw,
            resources
        );
    }

    public String toLogFields() {
        return "backend=" + backend
            + " path=" + path
            + " pipeline=" + pipeline
            + " framebuffer=" + framebuffer
            + " renderTarget=\"" + renderTarget + "\""
            + " colorAttachments=" + colorAttachmentCount
            + " hasDepth=" + hasDepthAttachment
            + " requested{depthTest=" + requestedDepthTest
            + ",depthWrite=" + requestedDepthWrite
            + ",cull=" + requestedCull
            + ",polygonMode=" + requestedPolygonMode
            + ",blend=" + requestedBlend
            + ",colorWrite=" + requestedColorWrite
            + ",alphaWrite=" + requestedAlphaWrite
            + ",logic=" + requestedColorLogic + "}"
            + " translated{" + translatedState.toLogFields() + "}"
            + " scissor{" + scissor.toLogFields() + "}"
            + " draw{" + draw.toLogFields() + "}"
            + " resources{" + resources.toLogFields() + "}";
    }

    public record TranslatedPipelineState(
        String cullMode,
        String frontFace,
        boolean depthTestEnabled,
        boolean depthWriteEnabled,
        String depthCompareOp,
        String colorWriteMask,
        int blendAttachmentCount
    ) {
        public TranslatedPipelineState {
            cullMode = Objects.requireNonNull(cullMode, "cullMode must not be null");
            frontFace = Objects.requireNonNull(frontFace, "frontFace must not be null");
            depthCompareOp = Objects.requireNonNull(depthCompareOp, "depthCompareOp must not be null");
            colorWriteMask = Objects.requireNonNull(colorWriteMask, "colorWriteMask must not be null");
        }

        public static TranslatedPipelineState opengl(RenderPipeline pipeline) {
            return new TranslatedPipelineState(
                pipeline.isCull() ? "GL_CULL_ENABLED" : "GL_CULL_DISABLED",
                "OPENGL_CURRENT",
                pipeline.getDepthTestFunction() != net.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST,
                pipeline.isWriteDepth(),
                pipeline.getDepthTestFunction().name(),
                VulkanicDrawStateSnapshot.colorWriteMask(pipeline.isWriteColor(), pipeline.isWriteAlpha()),
                pipeline.getBlendFunction().isPresent() ? 1 : 0
            );
        }

        public String toLogFields() {
            return "cullMode=" + cullMode
                + ",frontFace=" + frontFace
                + ",depthTest=" + depthTestEnabled
                + ",depthWrite=" + depthWriteEnabled
                + ",depthCompare=" + depthCompareOp
                + ",colorWriteMask=" + colorWriteMask
                + ",blendAttachments=" + blendAttachmentCount;
        }
    }

    public record ScissorStateSnapshot(boolean enabled, int x, int y, int width, int height) {
        public static ScissorStateSnapshot disabled() {
            return new ScissorStateSnapshot(false, 0, 0, 0, 0);
        }

        public String toLogFields() {
            return "enabled=" + enabled
                + ",x=" + x
                + ",y=" + y
                + ",width=" + width
                + ",height=" + height;
        }
    }

    public record DrawCall(
        boolean indexed,
        int firstVertex,
        int baseVertex,
        int firstIndex,
        int indexCount,
        int vertexCount,
        int instanceCount,
        @Nullable VertexFormat.IndexType indexType
    ) {
        public String toLogFields() {
            return "indexed=" + indexed
                + ",firstVertex=" + firstVertex
                + ",baseVertex=" + baseVertex
                + ",firstIndex=" + firstIndex
                + ",indexCount=" + indexCount
                + ",vertexCount=" + vertexCount
                + ",instances=" + instanceCount
                + ",indexType=" + (indexType == null ? "none" : indexType.name());
        }
    }

    public record ResourceState(
        int reflectedResourceCount,
        int submittedResourceCount,
        int boundSamplerCount,
        int boundUniformCount,
        List<String> missingResources
    ) {
        public ResourceState {
            missingResources = List.copyOf(Objects.requireNonNull(missingResources, "missingResources must not be null"));
        }

        public String toLogFields() {
            return "reflected=" + reflectedResourceCount
                + ",submitted=" + submittedResourceCount
                + ",samplers=" + boundSamplerCount
                + ",uniforms=" + boundUniformCount
                + ",missing=" + missingResources;
        }
    }

    public static String colorWriteMask(boolean color, boolean alpha) {
        StringBuilder builder = new StringBuilder(4);
        if (color) {
            builder.append("RGB");
        }
        if (alpha) {
            builder.append('A');
        }
        return builder.isEmpty() ? "NONE" : builder.toString();
    }
}
