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
    String requestedBlendState,
    boolean requestedColorWrite,
    boolean requestedAlphaWrite,
    String requestedColorLogic,
    String topology,
    float depthBiasScaleFactor,
    float depthBiasConstant,
    float lineWidth,
    String multisampling,
    TranslatedPipelineState translatedState,
    ViewportStateSnapshot viewport,
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
        requestedBlendState = Objects.requireNonNull(requestedBlendState, "requestedBlendState must not be null");
        requestedColorLogic = Objects.requireNonNull(requestedColorLogic, "requestedColorLogic must not be null");
        topology = Objects.requireNonNull(topology, "topology must not be null");
        multisampling = Objects.requireNonNull(multisampling, "multisampling must not be null");
        translatedState = Objects.requireNonNull(translatedState, "translatedState must not be null");
        viewport = Objects.requireNonNull(viewport, "viewport must not be null");
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
        ViewportStateSnapshot viewport,
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
            pipeline.getBlendFunction()
                .map(blend -> "srcColor=" + blend.sourceColor().name()
                    + ",dstColor=" + blend.destColor().name()
                    + ",srcAlpha=" + blend.sourceAlpha().name()
                    + ",dstAlpha=" + blend.destAlpha().name())
                .orElse("disabled"),
            pipeline.isWriteColor(),
            pipeline.isWriteAlpha(),
            pipeline.getColorLogic().name(),
            pipeline.getVertexFormatMode().name(),
            pipeline.getDepthBiasScaleFactor(),
            pipeline.getDepthBiasConstant(),
            1.0F,
            "samples=1,sampleShading=false",
            translatedState,
            viewport,
            scissor,
            draw,
            resources
        );
    }

    public String toLogFields() {
        return "backend=" + backend
            + " " + net.minecraft.client.dev.DeterministicCameraCapture.shaderInputParityContextFields()
            + " " + VulkanicAPI.currentShaderInputParitySemanticDrawContextFields()
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
            + ",blendState=" + requestedBlendState
            + ",colorWrite=" + requestedColorWrite
            + ",alphaWrite=" + requestedAlphaWrite
            + ",logic=" + requestedColorLogic
            + ",topology=" + topology
            + ",depthBiasScale=" + depthBiasScaleFactor
            + ",depthBiasConstant=" + depthBiasConstant
            + ",lineWidth=" + lineWidth
            + ",multisampling=" + multisampling + "}"
            + " translated{" + translatedState.toLogFields() + "}"
            + " viewport{" + viewport.toLogFields() + "}"
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
        int blendAttachmentCount,
        String polygonMode,
        String topology,
        boolean depthBiasEnabled,
        float depthBiasConstantFactor,
        float depthBiasSlopeFactor,
        String multisampling,
        float lineWidth,
        boolean stencilTestEnabled
    ) {
        public TranslatedPipelineState {
            cullMode = Objects.requireNonNull(cullMode, "cullMode must not be null");
            frontFace = Objects.requireNonNull(frontFace, "frontFace must not be null");
            depthCompareOp = Objects.requireNonNull(depthCompareOp, "depthCompareOp must not be null");
            colorWriteMask = Objects.requireNonNull(colorWriteMask, "colorWriteMask must not be null");
            polygonMode = Objects.requireNonNull(polygonMode, "polygonMode must not be null");
            topology = Objects.requireNonNull(topology, "topology must not be null");
            multisampling = Objects.requireNonNull(multisampling, "multisampling must not be null");
        }

        public static TranslatedPipelineState opengl(RenderPipeline pipeline) {
            return new TranslatedPipelineState(
                pipeline.isCull() ? "GL_CULL_ENABLED" : "GL_CULL_DISABLED",
                "OPENGL_CURRENT",
                pipeline.getDepthTestFunction() != net.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST,
                pipeline.isWriteDepth(),
                pipeline.getDepthTestFunction().name(),
                VulkanicDrawStateSnapshot.colorWriteMask(pipeline.isWriteColor(), pipeline.isWriteAlpha()),
                pipeline.getBlendFunction().isPresent() ? 1 : 0,
                pipeline.getPolygonMode().name(),
                pipeline.getVertexFormatMode().name(),
                pipeline.getDepthBiasConstant() != 0.0F || pipeline.getDepthBiasScaleFactor() != 0.0F,
                pipeline.getDepthBiasConstant(),
                pipeline.getDepthBiasScaleFactor(),
                "samples=1,sampleShading=false",
                1.0F,
                false
            );
        }

        public String toLogFields() {
            return "cullMode=" + cullMode
                + ",frontFace=" + frontFace
                + ",depthTest=" + depthTestEnabled
                + ",depthWrite=" + depthWriteEnabled
                + ",depthCompare=" + depthCompareOp
                + ",colorWriteMask=" + colorWriteMask
                + ",blendAttachments=" + blendAttachmentCount
                + ",polygonMode=" + polygonMode
                + ",topology=" + topology
                + ",depthBias=" + depthBiasEnabled
                + ",depthBiasConstant=" + depthBiasConstantFactor
                + ",depthBiasSlope=" + depthBiasSlopeFactor
                + ",multisampling=" + multisampling
                + ",lineWidth=" + lineWidth
                + ",stencilTest=" + stencilTestEnabled;
        }
    }

    public record ViewportStateSnapshot(boolean known, int x, int y, int width, int height) {
        public static ViewportStateSnapshot unknown() {
            return new ViewportStateSnapshot(false, 0, 0, 0, 0);
        }

        public String toLogFields() {
            return "known=" + known
                + ",x=" + x
                + ",y=" + y
                + ",width=" + width
                + ",height=" + height;
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
