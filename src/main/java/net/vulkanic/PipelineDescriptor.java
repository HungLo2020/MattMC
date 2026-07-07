package net.vulkanic;

import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.LogicOp;
import net.blaze3d.platform.PolygonMode;
import net.blaze3d.shaders.UniformType;
import net.blaze3d.textures.TextureFormat;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opaque descriptor used to create a {@link PipelineHandle} via
 * {@link GraphicsBackend#createPipeline(PipelineDescriptor)}.
 *
 * <p>The descriptor wraps backend-specific pipeline specification data.
 * For OpenGL, this wraps a {@code net.blaze3d.pipeline.RenderPipeline}.
 * For Vulkan, this will wrap SPIR-V bytecode and pipeline state.
 *
 * <p>Use the factory methods to create descriptors from existing objects:
 * <pre>
 * PipelineDescriptor desc = PipelineDescriptor.fromRenderPipeline(myPipeline);
 * PipelineHandle handle = VulkanicAPI.createPipeline(desc);
 * </pre>
 */
public final class PipelineDescriptor {

    private final Object nativeDescriptor;
    private final PortableState portableState;
    @Nullable
    private final ResourceLayout explicitResourceLayout;
    private final List<VulkanicSpirvModule> spirvModules;
    private final List<PushConstantRange> pushConstantRanges;
    @Nullable
    private final VertexInputState vertexInputState;
    @Nullable
    private volatile ResourceLayout derivedResourceLayout;
    @Nullable
    private volatile String stableCacheKey;
    @Nullable
    private volatile String pipelineCompilationKey;
    @Nullable
    private volatile String resourceLayoutCacheKey;
    @Nullable
    private volatile ConcurrentHashMap<ResourceLayout, PipelineDescriptor> resourceLayoutVariants;

    private PipelineDescriptor(
        @Nullable Object nativeDescriptor,
        PortableState portableState,
        @Nullable ResourceLayout explicitResourceLayout,
        List<VulkanicSpirvModule> spirvModules,
        List<PushConstantRange> pushConstantRanges,
        @Nullable VertexInputState vertexInputState
    ) {
        this.nativeDescriptor = nativeDescriptor;
        this.portableState = Objects.requireNonNull(portableState, "portableState must not be null");
        this.explicitResourceLayout = explicitResourceLayout;
        this.spirvModules = normalizeSpirvModules(spirvModules);
        this.pushConstantRanges = normalizePushConstantRanges(pushConstantRanges);
        this.vertexInputState = vertexInputState;
    }

    /**
     * Creates a PipelineDescriptor from an existing Blaze3D RenderPipeline.
     *
     * @param pipeline the Blaze3D pipeline to wrap
     * @return a descriptor for creating a compiled pipeline handle
     */
    public static PipelineDescriptor fromRenderPipeline(RenderPipeline pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline must not be null");
        }
        return new PipelineDescriptor(
            pipeline,
            PortableState.fromRenderPipeline(pipeline),
            null,
            List.of(),
            List.of(),
            null
        );
    }

    /**
     * Creates a PipelineDescriptor from a backend-agnostic portable snapshot.
     *
     * <p>This is the forward-compatible path for eventual non-OpenGL pipeline
     * compilation backends.</p>
     */
    public static PipelineDescriptor fromPortableState(PortableState portableState) {
        if (portableState == null) {
            throw new IllegalArgumentException("portableState must not be null");
        }
        return new PipelineDescriptor(null, portableState, null, List.of(), List.of(), null);
    }

    /**
     * Creates a PipelineDescriptor from portable state plus precompiled SPIR-V modules.
     */
    public static PipelineDescriptor fromPortableStateAndSpirvModules(
        PortableState portableState,
        List<VulkanicSpirvModule> spirvModules
    ) {
        if (portableState == null) {
            throw new IllegalArgumentException("portableState must not be null");
        }
        return new PipelineDescriptor(null, portableState, null, spirvModules, List.of(), null);
    }

    /**
     * Creates a PipelineDescriptor from RenderPipeline plus precompiled SPIR-V modules.
     */
    public static PipelineDescriptor fromRenderPipelineAndSpirvModules(
        RenderPipeline pipeline,
        List<VulkanicSpirvModule> spirvModules
    ) {
        if (pipeline == null) {
            throw new IllegalArgumentException("pipeline must not be null");
        }
        return new PipelineDescriptor(
            pipeline,
            PortableState.fromRenderPipeline(pipeline),
            null,
            spirvModules,
            List.of(),
            null
        );
    }

    /**
     * Returns the underlying native descriptor object.
     * Backend implementations cast this to the appropriate type.
     *
     * <p>May be null when created via {@link #fromPortableState(PortableState)}.</p>
     */
    public Object getNativeDescriptor() {
        return nativeDescriptor;
    }

    /**
     * Returns backend-agnostic pipeline metadata snapshot.
     */
    public PortableState getPortableState() {
        return portableState;
    }

    /**
     * Returns true when this descriptor carries a backend-native object.
     */
    public boolean hasNativeDescriptor() {
        return nativeDescriptor != null;
    }

    /**
     * Returns descriptor-set-style resource binding layout derived from portable pipeline state.
     *
     * <p>Current mapping is set=0 with deterministic binding order:
     * samplers first, then uniforms in declaration order.
     */
    public ResourceLayout getResourceLayout() {
        if (explicitResourceLayout != null) {
            return explicitResourceLayout;
        }

        ResourceLayout cached = derivedResourceLayout;
        if (cached == null) {
            cached = derivePortableResourceLayout();
            derivedResourceLayout = cached;
        }
        return cached;
    }

    /**
     * Returns true when this descriptor carries explicit resource layout metadata.
     */
    public boolean hasExplicitResourceLayout() {
        return explicitResourceLayout != null;
    }

    /**
     * Returns precompiled SPIR-V modules associated with this descriptor.
     */
    public List<VulkanicSpirvModule> getSpirvModules() {
        return spirvModules;
    }

    /**
     * Returns true when this descriptor carries precompiled SPIR-V modules.
     */
    public boolean hasSpirvModules() {
        return !spirvModules.isEmpty();
    }

    /**
     * Returns push-constant range metadata for pipeline layout preparation.
     */
    public List<PushConstantRange> getPushConstantRanges() {
        return pushConstantRanges;
    }

    /**
     * Returns explicit vertex input metadata when a caller is migrating a
     * GL-style vertex attribute layout that cannot be represented by
     * Blaze3D's fixed {@link VertexFormatElement} set.
     */
    @Nullable
    public VertexInputState getVertexInputState() {
        return vertexInputState;
    }

    /**
     * Returns a copy of this descriptor with push-constant range metadata attached.
     */
    public PipelineDescriptor withPushConstantRanges(List<PushConstantRange> ranges) {
        return new PipelineDescriptor(
            this.nativeDescriptor,
            this.portableState,
            this.explicitResourceLayout,
            this.spirvModules,
            ranges,
            this.vertexInputState
        );
    }

    /**
     * Returns a copy of this descriptor with explicit Vulkan-style vertex input
     * metadata attached.
     */
    public PipelineDescriptor withVertexInputState(VertexInputState vertexInputState) {
        return new PipelineDescriptor(
            this.nativeDescriptor,
            this.portableState,
            this.explicitResourceLayout,
            this.spirvModules,
            this.pushConstantRanges,
            Objects.requireNonNull(vertexInputState, "vertexInputState must not be null")
        );
    }

    /**
     * Returns a copy of this descriptor with explicit resource-layout metadata attached.
     */
    public PipelineDescriptor withResourceLayout(ResourceLayout resourceLayout) {
        ResourceLayout normalizedLayout = Objects.requireNonNull(resourceLayout, "resourceLayout must not be null");
        if (explicitResourceLayout != null && explicitResourceLayout.equals(normalizedLayout)) {
            return this;
        }

        ConcurrentHashMap<ResourceLayout, PipelineDescriptor> variants = resourceLayoutVariants;
        if (variants == null) {
            variants = new ConcurrentHashMap<>();
            resourceLayoutVariants = variants;
        }

        return variants.computeIfAbsent(normalizedLayout, layout -> new PipelineDescriptor(
            this.nativeDescriptor,
            this.portableState,
            layout,
            this.spirvModules,
            this.pushConstantRanges,
            this.vertexInputState
        ));
    }

    /**
     * Returns deterministic cache key for this descriptor's portable state.
     *
     * <p>This key is backend-agnostic and intended for future pipeline cache lookup.
     */
    public String getStableCacheKey() {
        String cached = stableCacheKey;
        if (cached == null) {
            if (vertexInputState == null) {
                cached = portableState.stableCacheKey();
            } else {
                cached = sha256Hex(
                    (portableState.stableCacheKey() + "|vertexInput=" + vertexInputSignature(vertexInputState))
                        .getBytes(StandardCharsets.UTF_8)
                );
            }
            stableCacheKey = cached;
        }
        return cached;
    }

    private static String vertexInputSignature(VertexInputState state) {
        StringBuilder builder = new StringBuilder(256);
        for (VertexInputBinding binding : state.bindings()) {
            builder.append("b:")
                .append(binding.binding()).append(':')
                .append(binding.stride()).append(':')
                .append(binding.inputRate().name()).append(';');
        }
        for (VertexInputAttribute attribute : state.attributes()) {
            builder.append("a:")
                .append(attribute.location()).append(':')
                .append(attribute.binding()).append(':')
                .append(attribute.format().name()).append(':')
                .append(attribute.offset()).append(';');
        }
        return builder.toString();
    }

    /**
     * Returns deterministic cache key for this descriptor's resolved resource layout.
     */
    public String getResourceLayoutCacheKey() {
        String cached = resourceLayoutCacheKey;
        if (cached == null) {
            cached = resourceLayoutCacheKey(getResourceLayout());
            resourceLayoutCacheKey = cached;
        }
        return cached;
    }

    /**
     * Returns deterministic cache key for resource-layout metadata.
     */
    public static String resourceLayoutCacheKey(ResourceLayout layout) {
        Objects.requireNonNull(layout, "layout must not be null");
        StringBuilder builder = new StringBuilder(256);
        for (ResourceBinding binding : layout.bindings()) {
            builder.append(binding.set()).append(':')
                .append(binding.binding()).append(':')
                .append(binding.name()).append(':')
                .append(binding.type()).append(':')
                .append(binding.textureFormat() == null ? "" : binding.textureFormat().name())
                .append(':');

            List<String> stages = binding.stages().stream()
                .map(Enum::name)
                .sorted()
                .toList();
            builder.append(String.join(",", stages)).append(';');
        }
        return builder.toString();
    }

    /**
     * Returns deterministic cache key for full pipeline compilation inputs.
     *
     * <p>Includes portable state, optional SPIR-V module payload identity, and
     * push-constant range metadata.</p>
     */
    public String getPipelineCompilationKey() {
        String cached = pipelineCompilationKey;
        if (cached != null) {
            return cached;
        }

        StringBuilder canonical = new StringBuilder(1024);
        canonical.append("portable=").append(getStableCacheKey()).append(';');
        canonical.append("explicitLayout=").append(explicitResourceLayout != null).append(';');

        ResourceLayout layout = getResourceLayout();
        canonical.append("resourceBindingCount=").append(layout.bindings().size()).append(';');
        for (ResourceBinding binding : layout.bindings()) {
            canonical.append("set=").append(binding.set()).append(';');
            canonical.append("binding=").append(binding.binding()).append(';');
            canonical.append("name=").append(binding.name()).append(';');
            canonical.append("type=").append(binding.type().name()).append(';');
            canonical.append("texFormat=").append(binding.textureFormat() == null ? "" : binding.textureFormat().name()).append(';');
            List<String> stageNames = binding.stages().stream().map(Enum::name).sorted().toList();
            canonical.append("stages=").append(String.join(",", stageNames)).append(';');
        }

        canonical.append("spirvCount=").append(spirvModules.size()).append(';');
        for (VulkanicSpirvModule module : spirvModules) {
            canonical.append("stage=").append(module.stage().name()).append(';');
            canonical.append("entry=").append(module.entryPoint()).append(';');
            canonical.append("source=").append(module.sourceName()).append(';');
            canonical.append("compiler=").append(module.compilerName()).append(';');
            canonical.append("size=").append(module.byteSize()).append(';');
            canonical.append("bytesSha=").append(sha256Hex(module.spirvBytes())).append(';');
        }

        canonical.append("pushCount=").append(pushConstantRanges.size()).append(';');
        for (PushConstantRange range : pushConstantRanges) {
            canonical.append("offset=").append(range.offset()).append(';');
            canonical.append("size=").append(range.size()).append(';');
            List<String> stageNames = range.stages().stream()
                .map(Enum::name)
                .sorted()
                .toList();
            canonical.append("stages=").append(String.join(",", stageNames)).append(';');
        }

        canonical.append("vertexInputPresent=").append(vertexInputState != null).append(';');
        if (vertexInputState != null) {
            canonical.append("vertexBindingCount=").append(vertexInputState.bindings().size()).append(';');
            for (VertexInputBinding binding : vertexInputState.bindings()) {
                canonical.append("binding=").append(binding.binding()).append(';');
                canonical.append("stride=").append(binding.stride()).append(';');
                canonical.append("inputRate=").append(binding.inputRate().name()).append(';');
            }
            canonical.append("vertexAttributeCount=").append(vertexInputState.attributes().size()).append(';');
            for (VertexInputAttribute attribute : vertexInputState.attributes()) {
                canonical.append("location=").append(attribute.location()).append(';');
                canonical.append("binding=").append(attribute.binding()).append(';');
                canonical.append("format=").append(attribute.format().name()).append(';');
                canonical.append("offset=").append(attribute.offset()).append(';');
            }
        }

        cached = sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
        pipelineCompilationKey = cached;
        return cached;
    }

    /**
     * Returns a Blaze3D RenderPipeline representation for OpenGL compilation.
     *
     * <p>If this descriptor was created from a RenderPipeline, returns it directly.
     * Otherwise reconstructs a semantically equivalent RenderPipeline from the
     * portable snapshot.</p>
     */
    public RenderPipeline requireRenderPipeline() {
        if (nativeDescriptor instanceof RenderPipeline renderPipeline) {
            return renderPipeline;
        }
        return portableState.toRenderPipeline();
    }

    private ResourceLayout derivePortableResourceLayout() {
        ResourceLayout baseLayout = portableState.toResourceLayout();
        if (spirvModules.isEmpty()) {
            return baseLayout;
        }

        Set<VulkanicShaderStage> inferredStages = spirvModules.stream()
            .map(VulkanicSpirvModule::stage)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (inferredStages.isEmpty()) {
            return baseLayout;
        }

        List<ResourceBinding> bindingsWithInferredStages = baseLayout.bindings().stream()
            .map(binding -> binding.withStages(inferredStages))
            .toList();
        return new ResourceLayout(bindingsWithInferredStages);
    }

    private static List<VulkanicSpirvModule> normalizeSpirvModules(List<VulkanicSpirvModule> modules) {
        List<VulkanicSpirvModule> copied = List.copyOf(Objects.requireNonNull(modules, "spirvModules must not be null"));
        Set<VulkanicShaderStage> seenStages = new LinkedHashSet<>();
        for (VulkanicSpirvModule module : copied) {
            Objects.requireNonNull(module, "spirv module entry must not be null");
            if (!seenStages.add(module.stage())) {
                throw new IllegalArgumentException(
                    "Duplicate SPIR-V module stage '" + module.stage() + "' is not allowed for a single pipeline descriptor"
                );
            }
        }
        return copied;
    }

    private static List<PushConstantRange> normalizePushConstantRanges(List<PushConstantRange> ranges) {
        List<PushConstantRange> copied = List.copyOf(Objects.requireNonNull(ranges, "pushConstantRanges must not be null"));
        List<PushConstantRange> sorted = new ArrayList<>(copied);
        sorted.sort(java.util.Comparator.comparingInt(PushConstantRange::offset));

        int previousEnd = -1;
        for (PushConstantRange range : sorted) {
            Objects.requireNonNull(range, "push constant range entry must not be null");
            if (range.offset() < previousEnd) {
                throw new IllegalArgumentException(
                    "Push constant ranges overlap: previous end=" + previousEnd + ", next offset=" + range.offset()
                );
            }
            previousEnd = range.offset() + range.size();
        }

        return copied;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Backend-agnostic push-constant range metadata for future pipeline layout derivation.
     */
    public record PushConstantRange(int offset, int size, Set<VulkanicShaderStage> stages) {
        public PushConstantRange {
            if (offset < 0) {
                throw new IllegalArgumentException("offset must be >= 0");
            }
            if (size <= 0) {
                throw new IllegalArgumentException("size must be > 0");
            }
            stages = Set.copyOf(Objects.requireNonNull(stages, "stages must not be null"));
            if (stages.isEmpty()) {
                throw new IllegalArgumentException("stages must not be empty");
            }
        }
    }

    /**
     * Backend-neutral vertex input metadata for GL-style programs that do not
     * have a native Blaze3D {@link VertexFormat}.
     */
    public record VertexInputState(List<VertexInputBinding> bindings, List<VertexInputAttribute> attributes) {
        public VertexInputState {
            bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings must not be null"));
            attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
            if (attributes.isEmpty()) {
                throw new IllegalArgumentException("attributes must not be empty");
            }
        }
    }

    public record VertexInputBinding(int binding, int stride, VertexInputRate inputRate) {
        public VertexInputBinding {
            if (binding < 0) {
                throw new IllegalArgumentException("binding must be >= 0");
            }
            if (stride <= 0) {
                throw new IllegalArgumentException("stride must be > 0");
            }
            inputRate = Objects.requireNonNull(inputRate, "inputRate must not be null");
        }
    }

    public record VertexInputAttribute(int location, int binding, VertexAttributeFormat format, int offset) {
        public VertexInputAttribute {
            if (location < 0) {
                throw new IllegalArgumentException("location must be >= 0");
            }
            if (binding < 0) {
                throw new IllegalArgumentException("binding must be >= 0");
            }
            format = Objects.requireNonNull(format, "format must not be null");
            if (offset < 0) {
                throw new IllegalArgumentException("offset must be >= 0");
            }
        }
    }

    public enum VertexInputRate {
        VERTEX,
        INSTANCE
    }

    public enum VertexAttributeFormat {
        R8_UNORM,
        R8G8_UNORM,
        R8G8B8_UNORM,
        R8G8B8A8_UNORM,
        R8_UINT,
        R8G8_UINT,
        R8G8B8_UINT,
        R8G8B8A8_UINT,
        R8_SNORM,
        R8G8_SNORM,
        R8G8B8_SNORM,
        R8G8B8A8_SNORM,
        R8_SINT,
        R8G8_SINT,
        R8G8B8_SINT,
        R8G8B8A8_SINT,
        R16_USCALED,
        R16G16_USCALED,
        R16G16B16_USCALED,
        R16G16B16A16_USCALED,
        R16_UINT,
        R16G16_UINT,
        R16G16B16_UINT,
        R16G16B16A16_UINT,
        R16_SSCALED,
        R16G16_SSCALED,
        R16G16B16_SSCALED,
        R16G16B16A16_SSCALED,
        R16_SINT,
        R16G16_SINT,
        R16G16B16_SINT,
        R16G16B16A16_SINT,
        R32_SFLOAT,
        R32G32_SFLOAT,
        R32G32B32_SFLOAT,
        R32G32B32A32_SFLOAT,
        R32_SINT,
        R32G32_SINT,
        R32G32B32_SINT,
        R32G32B32A32_SINT,
        R32_UINT,
        R32G32_UINT,
        R32G32B32_UINT,
        R32G32B32A32_UINT
    }

    /**
     * Backend-agnostic snapshot of pipeline state required for compilation.
     */
    public record PortableState(
        ResourceLocation location,
        ResourceLocation vertexShader,
        ResourceLocation fragmentShader,
        Map<String, String> shaderDefineValues,
        Set<String> shaderDefineFlags,
        List<String> samplers,
        List<UniformBinding> uniforms,
        Optional<BlendState> blendState,
        DepthTestFunction depthTestFunction,
        PolygonMode polygonMode,
        boolean cull,
        int cullFaceMode,
        boolean writeColor,
        boolean writeAlpha,
        boolean writeDepth,
        LogicOp colorLogic,
        VertexFormat vertexFormat,
        VertexFormat.Mode vertexFormatMode,
        float depthBiasScaleFactor,
        float depthBiasConstant
    ) {
        public PortableState {
            location = Objects.requireNonNull(location, "location must not be null");
            vertexShader = Objects.requireNonNull(vertexShader, "vertexShader must not be null");
            fragmentShader = Objects.requireNonNull(fragmentShader, "fragmentShader must not be null");
            shaderDefineValues = Map.copyOf(Objects.requireNonNull(shaderDefineValues, "shaderDefineValues must not be null"));
            shaderDefineFlags = Set.copyOf(Objects.requireNonNull(shaderDefineFlags, "shaderDefineFlags must not be null"));
            samplers = List.copyOf(Objects.requireNonNull(samplers, "samplers must not be null"));
            uniforms = List.copyOf(Objects.requireNonNull(uniforms, "uniforms must not be null"));
            blendState = Objects.requireNonNull(blendState, "blendState must not be null");
            depthTestFunction = Objects.requireNonNull(depthTestFunction, "depthTestFunction must not be null");
            polygonMode = Objects.requireNonNull(polygonMode, "polygonMode must not be null");
            if (cullFaceMode != VulkanicAPI.GL_FRONT
                && cullFaceMode != VulkanicAPI.GL_BACK
                && cullFaceMode != VulkanicAPI.GL_FRONT_AND_BACK) {
                throw new IllegalArgumentException("cullFaceMode must be GL_FRONT, GL_BACK, or GL_FRONT_AND_BACK");
            }
            colorLogic = Objects.requireNonNull(colorLogic, "colorLogic must not be null");
            vertexFormat = Objects.requireNonNull(vertexFormat, "vertexFormat must not be null");
            vertexFormatMode = Objects.requireNonNull(vertexFormatMode, "vertexFormatMode must not be null");
        }

        public static PortableState fromRenderPipeline(RenderPipeline pipeline) {
            ShaderDefines shaderDefines = pipeline.getShaderDefines();
            List<UniformBinding> uniformBindings = pipeline.getUniforms().stream()
                .map(uniform -> new UniformBinding(uniform.name(), uniform.type(), uniform.textureFormat()))
                .toList();
            VertexFormat rawVertexFormat = extractRawVertexFormat(pipeline);

            Optional<BlendState> blend = pipeline.getBlendFunction().map(function -> new BlendState(
                function.sourceColor(),
                function.destColor(),
                function.sourceAlpha(),
                function.destAlpha()
            ));

            return new PortableState(
                pipeline.getLocation(),
                pipeline.getVertexShader(),
                pipeline.getFragmentShader(),
                shaderDefines.values(),
                shaderDefines.flags(),
                pipeline.getSamplers(),
                uniformBindings,
                blend,
                pipeline.getDepthTestFunction(),
                pipeline.getPolygonMode(),
                pipeline.isCull(),
                VulkanicAPI.GL_BACK,
                pipeline.isWriteColor(),
                pipeline.isWriteAlpha(),
                pipeline.isWriteDepth(),
                pipeline.getColorLogic(),
                rawVertexFormat,
                pipeline.getVertexFormatMode(),
                pipeline.getDepthBiasScaleFactor(),
                pipeline.getDepthBiasConstant()
            );
        }

        private static VertexFormat extractRawVertexFormat(RenderPipeline pipeline) {
            try {
                java.lang.reflect.Field vertexFormatField = RenderPipeline.class.getDeclaredField("vertexFormat");
                vertexFormatField.setAccessible(true);
                Object value = vertexFormatField.get(pipeline);
                if (value instanceof VertexFormat vertexFormat) {
                    return vertexFormat;
                }
            } catch (ReflectiveOperationException ignored) {
            }

            return pipeline.getVertexFormat();
        }

        /**
         * Builds descriptor-set-style resource layout metadata for this pipeline.
         */
        public ResourceLayout toResourceLayout() {
            List<ResourceBinding> bindings = new ArrayList<>(samplers.size() + uniforms.size());
            Set<String> seenNames = new LinkedHashSet<>();
            int bindingIndex = 0;

            for (String sampler : samplers) {
                ensureUniqueResourceName(seenNames, sampler);
                bindings.add(new ResourceBinding(0, bindingIndex, sampler, ResourceType.SAMPLER, null));
                bindingIndex++;
            }

            for (UniformBinding uniform : uniforms) {
                ensureUniqueResourceName(seenNames, uniform.name());
                bindings.add(new ResourceBinding(
                    0,
                    bindingIndex,
                    uniform.name(),
                    ResourceType.fromUniformType(uniform.type()),
                    uniform.textureFormat()
                ));
                bindingIndex++;
            }

            return new ResourceLayout(bindings);
        }

        /**
         * Returns deterministic SHA-256 cache key over canonicalized portable pipeline state.
         */
        public String stableCacheKey() {
            String canonical = canonicalSignature();
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is not available", e);
            }
        }

        private String canonicalSignature() {
            StringBuilder builder = new StringBuilder(1024);

            appendField(builder, "location", location.toString());
            appendField(builder, "vertexShader", vertexShader.toString());
            appendField(builder, "fragmentShader", fragmentShader.toString());

            List<String> defineKeys = new ArrayList<>(shaderDefineValues.keySet());
            Collections.sort(defineKeys);
            appendField(builder, "defineValueCount", defineKeys.size());
            for (String key : defineKeys) {
                appendField(builder, "defineKey", key);
                appendField(builder, "defineValue", shaderDefineValues.get(key));
            }

            List<String> defineFlags = new ArrayList<>(shaderDefineFlags);
            Collections.sort(defineFlags);
            appendField(builder, "defineFlagCount", defineFlags.size());
            for (String flag : defineFlags) {
                appendField(builder, "defineFlag", flag);
            }

            appendField(builder, "samplerCount", samplers.size());
            for (String sampler : samplers) {
                appendField(builder, "sampler", sampler);
            }

            appendField(builder, "uniformCount", uniforms.size());
            for (UniformBinding uniform : uniforms) {
                appendField(builder, "uniformName", uniform.name());
                appendField(builder, "uniformType", uniform.type().name());
                appendField(builder, "uniformTextureFormat",
                    uniform.textureFormat() == null ? "" : uniform.textureFormat().name());
            }

            if (blendState.isPresent()) {
                BlendState blend = blendState.get();
                appendField(builder, "blendPresent", true);
                appendField(builder, "blendSourceColor", blend.sourceColor().name());
                appendField(builder, "blendDestColor", blend.destColor().name());
                appendField(builder, "blendSourceAlpha", blend.sourceAlpha().name());
                appendField(builder, "blendDestAlpha", blend.destAlpha().name());
            } else {
                appendField(builder, "blendPresent", false);
            }

            appendField(builder, "depthTestFunction", depthTestFunction.name());
            appendField(builder, "polygonMode", polygonMode.name());
            appendField(builder, "cull", cull);
            appendField(builder, "cullFaceMode", cullFaceMode);
            appendField(builder, "writeColor", writeColor);
            appendField(builder, "writeAlpha", writeAlpha);
            appendField(builder, "writeDepth", writeDepth);
            appendField(builder, "colorLogic", colorLogic.name());
            appendField(builder, "vertexFormat", vertexFormat.toString());
            appendField(builder, "vertexFormatVertexSize", vertexFormat.getVertexSize());
            appendField(builder, "vertexFormatElementsMask", vertexFormat.getElementsMask());
            appendField(builder, "vertexFormatMode", vertexFormatMode.name());
            appendField(builder, "depthBiasScaleFactor", depthBiasScaleFactor);
            appendField(builder, "depthBiasConstant", depthBiasConstant);

            return builder.toString();
        }

        private static void appendField(StringBuilder builder, String key, Object value) {
            String text = String.valueOf(value);
            builder.append(key)
                .append('#')
                .append(text.length())
                .append(':')
                .append(text)
                .append(';');
        }

        private static void ensureUniqueResourceName(Set<String> seenNames, String name) {
            if (!seenNames.add(name)) {
                throw new IllegalStateException(
                    "PortableState has duplicate pipeline resource name '" + name +
                    "'. Sampler and uniform names must be unique for deterministic binding layout.");
            }
        }

        public RenderPipeline toRenderPipeline() {
            RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .withDepthTestFunction(depthTestFunction)
                .withPolygonMode(polygonMode)
                .withCull(cull)
                .withColorWrite(writeColor, writeAlpha)
                .withDepthWrite(writeDepth)
                .withVertexFormat(vertexFormat, vertexFormatMode)
                .withDepthBias(depthBiasScaleFactor, depthBiasConstant);

            for (Map.Entry<String, String> defineEntry : shaderDefineValues.entrySet()) {
                applyShaderDefine(builder, defineEntry.getKey(), defineEntry.getValue());
            }

            for (String defineFlag : shaderDefineFlags) {
                builder.withShaderDefine(defineFlag);
            }

            for (String sampler : samplers) {
                builder.withSampler(sampler);
            }

            for (UniformBinding uniform : uniforms) {
                if (uniform.type() == UniformType.TEXEL_BUFFER) {
                    if (uniform.textureFormat() == null) {
                        throw new IllegalStateException(
                            "Texel-buffer uniform '" + uniform.name() + "' requires a texture format");
                    }
                    builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
                } else {
                    builder.withUniform(uniform.name(), uniform.type());
                }
            }

            if (blendState.isPresent()) {
                builder.withBlend(blendState.get().toBlendFunction());
            } else {
                builder.withoutBlend();
            }

            if (colorLogic != LogicOp.NONE) {
                builder.withColorLogic(colorLogic);
            }

            return builder.build();
        }

        private static void applyShaderDefine(RenderPipeline.Builder builder, String name, String value) {
            try {
                builder.withShaderDefine(name, Integer.parseInt(value));
                return;
            } catch (NumberFormatException ignored) {
            }

            try {
                builder.withShaderDefine(name, Float.parseFloat(value));
                return;
            } catch (NumberFormatException ignored) {
            }

            throw new IllegalStateException(
                "Cannot reconstruct shader define '" + name + "' with non-numeric value '" + value +
                "'. RenderPipeline.Builder only supports int/float/value-less defines.");
        }
    }

    public record UniformBinding(String name, UniformType type, @Nullable TextureFormat textureFormat) {
        public UniformBinding {
            name = Objects.requireNonNull(name, "name must not be null");
            type = Objects.requireNonNull(type, "type must not be null");
            if (type == UniformType.TEXEL_BUFFER && textureFormat == null) {
                throw new IllegalArgumentException(
                    "Texel-buffer uniform '" + name + "' requires a texture format");
            }
            if (type != UniformType.TEXEL_BUFFER && textureFormat != null) {
                throw new IllegalArgumentException(
                    "Only texel-buffer uniforms may include a texture format");
            }
        }
    }

    public record BlendState(
        net.blaze3d.platform.SourceFactor sourceColor,
        net.blaze3d.platform.DestFactor destColor,
        net.blaze3d.platform.SourceFactor sourceAlpha,
        net.blaze3d.platform.DestFactor destAlpha
    ) {
        public BlendState {
            sourceColor = Objects.requireNonNull(sourceColor, "sourceColor must not be null");
            destColor = Objects.requireNonNull(destColor, "destColor must not be null");
            sourceAlpha = Objects.requireNonNull(sourceAlpha, "sourceAlpha must not be null");
            destAlpha = Objects.requireNonNull(destAlpha, "destAlpha must not be null");
        }

        public BlendFunction toBlendFunction() {
            return new BlendFunction(sourceColor, destColor, sourceAlpha, destAlpha);
        }
    }

    /**
     * Backend-agnostic resource binding layout for a pipeline descriptor.
     */
    public record ResourceLayout(List<ResourceBinding> bindings) {
        public ResourceLayout {
            bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings must not be null"));
            Set<String> names = new LinkedHashSet<>();
            for (ResourceBinding binding : bindings) {
                Objects.requireNonNull(binding, "binding entry must not be null");
                if (!names.add(binding.name())) {
                    throw new IllegalArgumentException(
                        "Duplicate resource binding name '" + binding.name() + "' in layout");
                }
            }
        }

        public Optional<ResourceBinding> findByName(String name) {
            Objects.requireNonNull(name, "name must not be null");
            for (ResourceBinding binding : bindings) {
                if (binding.name().equals(name)) {
                    return Optional.of(binding);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Single resource binding declaration in a backend-agnostic pipeline layout.
     */
    public record ResourceBinding(
        int set,
        int binding,
        String name,
        ResourceType type,
        @Nullable TextureFormat textureFormat,
        Set<VulkanicShaderStage> stages
    ) {
        public ResourceBinding(int set, int binding, String name, ResourceType type, @Nullable TextureFormat textureFormat) {
            this(set, binding, name, type, textureFormat, defaultGraphicsStages());
        }

        public ResourceBinding {
            if (set < 0) {
                throw new IllegalArgumentException("set must be >= 0");
            }
            if (binding < 0) {
                throw new IllegalArgumentException("binding must be >= 0");
            }
            name = Objects.requireNonNull(name, "name must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            type = Objects.requireNonNull(type, "type must not be null");

            if (type == ResourceType.TEXEL_BUFFER && textureFormat == null) {
                throw new IllegalArgumentException(
                    "Texel-buffer resource binding '" + name + "' requires a texture format");
            }

            if (type != ResourceType.TEXEL_BUFFER && textureFormat != null) {
                throw new IllegalArgumentException(
                    "Only texel-buffer resource bindings may include a texture format");
            }

            stages = Set.copyOf(Objects.requireNonNull(stages, "stages must not be null"));
            if (stages.isEmpty()) {
                throw new IllegalArgumentException("stages must not be empty");
            }
        }

        public ResourceBinding withStages(Set<VulkanicShaderStage> stages) {
            return new ResourceBinding(set, binding, name, type, textureFormat, stages);
        }

        private static Set<VulkanicShaderStage> defaultGraphicsStages() {
            return Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT);
        }
    }

    /**
     * Resource categories used for backend-agnostic pipeline binding layout.
     */
    public enum ResourceType {
        SAMPLER,
        /**
         * Depth/shadow comparison sampler ({@code sampler2DShadow}, {@code sampler1DShadow},
         * {@code samplerCubeShadow}). On Vulkan this requires a VkSampler with
         * {@code compareEnable=true}, regardless of the underlying texture's sampler state.
         * On OpenGL the comparison mode is driven by the texture object, so this behaves
         * identically to {@link #SAMPLER} on that backend.
         */
        COMPARISON_SAMPLER,
        UNIFORM_BUFFER,
        STORAGE_IMAGE,
        TEXEL_BUFFER;

        static ResourceType fromUniformType(UniformType uniformType) {
            return switch (uniformType) {
                case UNIFORM_BUFFER -> UNIFORM_BUFFER;
                case TEXEL_BUFFER -> TEXEL_BUFFER;
            };
        }
    }
}
