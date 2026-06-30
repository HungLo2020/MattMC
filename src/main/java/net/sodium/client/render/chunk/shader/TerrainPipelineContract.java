package net.sodium.client.render.chunk.shader;

import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.DestFactor;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.platform.SourceFactor;
import net.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.blending.BlendModeStorage;
import net.irisshaders.iris.gl.blending.BufferBlendInformation;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.minecraft.resources.ResourceLocation;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.vulkanic.VulkanicBlendFactor;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend-neutral description of the state Sodium/Iris expects for one chunk terrain pass.
 *
 * <p>The Vulkan path still consumes the active Iris/Sodium shader program through a temporary bridge,
 * but render-pass state should flow through this explicit contract instead of being reconstructed in
 * backend code from mutable GL-style state.</p>
 */
public record TerrainPipelineContract(
    int shaderReloadVersion,
    boolean shadowPass,
    VertexFormat vertexFormat,
    List<String> samplerNames,
    PassKind passKind,
    PassState passState,
    ResourceLocation sourcePipelineLocation
) {
    public TerrainPipelineContract {
        vertexFormat = Objects.requireNonNull(vertexFormat, "vertexFormat must not be null");
        samplerNames = List.copyOf(Objects.requireNonNull(samplerNames, "samplerNames must not be null"));
        passKind = Objects.requireNonNull(passKind, "passKind must not be null");
        passState = Objects.requireNonNull(passState, "passState must not be null");
        sourcePipelineLocation = Objects.requireNonNull(sourcePipelineLocation, "sourcePipelineLocation must not be null");
    }

    public static TerrainPipelineContract from(
        TerrainRenderPass pass,
        int shaderReloadVersion,
        boolean shadowPass,
        VertexFormat vertexFormat,
        List<String> samplerNames
    ) {
        return from(pass, shaderReloadVersion, shadowPass, vertexFormat, samplerNames, List.of());
    }

    public static TerrainPipelineContract from(
        TerrainRenderPass pass,
        int shaderReloadVersion,
        boolean shadowPass,
        VertexFormat vertexFormat,
        List<String> samplerNames,
        Collection<BufferBlendInformation> indexedBlendOverrides
    ) {
        Objects.requireNonNull(pass, "pass must not be null");
        RenderPipeline pipeline = pass.getPipeline();
        return new TerrainPipelineContract(
            shaderReloadVersion,
            shadowPass,
            vertexFormat,
            samplerNames,
            PassKind.from(pass),
            PassState.from(pipeline, pass.isTranslucent(), indexedBlendOverrides),
            pipeline.getLocation()
        );
    }

    public ResourceLocation sharedPipelineLocation() {
        return ResourceLocation.fromNamespaceAndPath(
            "sodium",
            "pipeline/shared_chunk_" + this.passKind.id() + "_v" + this.shaderReloadVersion + "_" + this.signature()
        );
    }

    private String signature() {
        return Integer.toUnsignedString(Objects.hash(
            this.vertexFormat,
            this.samplerNames,
            this.shadowPass,
            this.passKind,
            this.passState
        ), 36);
    }

    public enum PassKind {
        SOLID("solid", false),
        CUTOUT("cutout", true),
        TRANSLUCENT("translucent", false);

        private final String id;
        private final boolean fragmentDiscard;

        PassKind(String id, boolean fragmentDiscard) {
            this.id = id;
            this.fragmentDiscard = fragmentDiscard;
        }

        private static PassKind from(TerrainRenderPass pass) {
            if (pass.isTranslucent()) {
                return TRANSLUCENT;
            }
            return pass.supportsFragmentDiscard() ? CUTOUT : SOLID;
        }

        public String id() {
            return this.id;
        }

        public boolean fragmentDiscard() {
            return this.fragmentDiscard;
        }
    }

    public record PassState(
        Optional<BlendFunction> blend,
        DepthTestFunction depthTest,
        PolygonMode polygonMode,
        boolean cull,
        boolean writeColor,
        boolean writeAlpha,
        boolean writeDepth,
        float depthBiasScaleFactor,
        float depthBiasConstant,
        List<IndexedBlendState> indexedBlendStates
    ) {
        public PassState {
            blend = Objects.requireNonNull(blend, "blend must not be null");
            depthTest = Objects.requireNonNull(depthTest, "depthTest must not be null");
            polygonMode = Objects.requireNonNull(polygonMode, "polygonMode must not be null");
            indexedBlendStates = List.copyOf(Objects.requireNonNull(indexedBlendStates, "indexedBlendStates must not be null"));
        }

        public static PassState from(RenderPipeline pipeline, boolean translucentPass) {
            return from(pipeline, translucentPass, List.of());
        }

        public static PassState from(
            RenderPipeline pipeline,
            boolean translucentPass,
            Collection<BufferBlendInformation> indexedBlendOverrides
        ) {
            Objects.requireNonNull(pipeline, "pipeline must not be null");
            return new PassState(
                currentBlend(pipeline, translucentPass),
                pipeline.getDepthTestFunction(),
                pipeline.getPolygonMode(),
                // Sodium chunk meshes already encode visible block faces; Vulkan backface culling can remove valid terrain.
                false,
                DepthColorStorage.isRedMaskEnabled() || DepthColorStorage.isGreenMaskEnabled() || DepthColorStorage.isBlueMaskEnabled(),
                DepthColorStorage.isAlphaMaskEnabled(),
                pipeline.isWriteDepth() && DepthColorStorage.isDepthMaskEnabled(),
                pipeline.getDepthBiasScaleFactor(),
                pipeline.getDepthBiasConstant(),
                indexedBlendStates(indexedBlendOverrides)
            );
        }

        public Optional<Optional<BlendFunction>> blendOverrideForAttachment(int colorAttachmentIndex) {
            for (IndexedBlendState state : indexedBlendStates) {
                if (state.colorAttachmentIndex() == colorAttachmentIndex) {
                    return Optional.of(state.blend());
                }
            }
            return Optional.empty();
        }

        private static List<IndexedBlendState> indexedBlendStates(Collection<BufferBlendInformation> overrides) {
            return Objects.requireNonNull(overrides, "overrides must not be null").stream()
                .map(information -> new IndexedBlendState(
                    information.index(),
                    Optional.ofNullable(information.blendMode()).map(mode -> new BlendFunction(
                        toSourceFactor(mode.srcRgb()).orElseThrow(),
                        toDestFactor(mode.dstRgb()).orElseThrow(),
                        toSourceFactor(mode.srcAlpha()).orElseThrow(),
                        toDestFactor(mode.dstAlpha()).orElseThrow()
                    ))
                ))
                .toList();
        }

        private static Optional<BlendFunction> currentBlend(RenderPipeline pipeline, boolean translucentPass) {
            if (!BlendModeStorage.isBlendEnabled()) {
                return translucentPass
                    ? Optional.of(pipeline.getBlendFunction().orElse(BlendFunction.TRANSLUCENT))
                    : pipeline.getBlendFunction();
            }

            Optional<SourceFactor> sourceColor = toSourceFactor(BlendModeStorage.getBlendSrcRgb());
            Optional<DestFactor> destColor = toDestFactor(BlendModeStorage.getBlendDstRgb());
            Optional<SourceFactor> sourceAlpha = toSourceFactor(BlendModeStorage.getBlendSrcAlpha());
            Optional<DestFactor> destAlpha = toDestFactor(BlendModeStorage.getBlendDstAlpha());
            if (sourceColor.isPresent() && destColor.isPresent() && sourceAlpha.isPresent() && destAlpha.isPresent()) {
                return Optional.of(new BlendFunction(sourceColor.get(), destColor.get(), sourceAlpha.get(), destAlpha.get()));
            }

            return pipeline.getBlendFunction();
        }

        private static Optional<SourceFactor> toSourceFactor(int glFactor) {
            return VulkanicBlendFactor.fromLegacyGlConstant(glFactor)
                .map(factor -> SourceFactor.valueOf(factor.name()));
        }

        private static Optional<DestFactor> toDestFactor(int glFactor) {
            return VulkanicBlendFactor.fromLegacyGlConstant(glFactor)
                .flatMap(factor -> {
                    try {
                        return Optional.of(DestFactor.valueOf(factor.name()));
                    } catch (IllegalArgumentException e) {
                        return Optional.empty();
                    }
                });
        }
    }

    public record IndexedBlendState(int colorAttachmentIndex, Optional<BlendFunction> blend) {
        public IndexedBlendState {
            if (colorAttachmentIndex < 0) {
                throw new IllegalArgumentException("colorAttachmentIndex must be >= 0");
            }
            blend = Objects.requireNonNull(blend, "blend must not be null");
        }
    }
}
