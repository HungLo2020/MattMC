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

    private PipelineDescriptor(@Nullable Object nativeDescriptor, PortableState portableState) {
        this.nativeDescriptor = nativeDescriptor;
        this.portableState = Objects.requireNonNull(portableState, "portableState must not be null");
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
        return new PipelineDescriptor(pipeline, PortableState.fromRenderPipeline(pipeline));
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
        return new PipelineDescriptor(null, portableState);
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
        return portableState.toResourceLayout();
    }

    /**
     * Returns deterministic cache key for this descriptor's portable state.
     *
     * <p>This key is backend-agnostic and intended for future pipeline cache lookup.
     */
    public String getStableCacheKey() {
        return portableState.stableCacheKey();
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
        @Nullable TextureFormat textureFormat
    ) {
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
        }
    }

    /**
     * Resource categories used for backend-agnostic pipeline binding layout.
     */
    public enum ResourceType {
        SAMPLER,
        UNIFORM_BUFFER,
        TEXEL_BUFFER;

        static ResourceType fromUniformType(UniformType uniformType) {
            return switch (uniformType) {
                case UNIFORM_BUFFER -> UNIFORM_BUFFER;
                case TEXEL_BUFFER -> TEXEL_BUFFER;
            };
        }
    }
}
