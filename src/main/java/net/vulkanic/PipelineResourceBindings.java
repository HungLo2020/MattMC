package net.vulkanic;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Backend-agnostic resource bindings for a compiled/renderable pipeline descriptor.
 *
 * <p>This model is the Vulkan-prep seam for descriptor-set style updates.
 * Bindings are keyed by resource name from {@link PipelineDescriptor.ResourceLayout}.
 */
public final class PipelineResourceBindings {

    private final Map<String, SamplerBinding> samplerBindings;
    private final Map<String, VulkanicBufferSlice> uniformBufferBindings;
    private final Map<String, TexelBufferBinding> texelBufferBindings;

    private PipelineResourceBindings(
        Map<String, SamplerBinding> samplerBindings,
        Map<String, VulkanicBufferSlice> uniformBufferBindings,
        Map<String, TexelBufferBinding> texelBufferBindings
    ) {
        this.samplerBindings = Map.copyOf(samplerBindings);
        this.uniformBufferBindings = Map.copyOf(uniformBufferBindings);
        this.texelBufferBindings = Map.copyOf(texelBufferBindings);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<SamplerBinding> getSamplerBinding(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(samplerBindings.get(name));
    }

    public Optional<VulkanicBufferSlice> getUniformBufferBinding(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(uniformBufferBindings.get(name));
    }

    public Optional<TexelBufferBinding> getTexelBufferBinding(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(texelBufferBindings.get(name));
    }

    /**
     * Validates these bindings against a descriptor resource layout.
     *
     * <p>Throws when required bindings are missing or when unknown names are provided.
     */
    public void validateAgainst(PipelineDescriptor.ResourceLayout layout) {
        Objects.requireNonNull(layout, "layout must not be null");

        List<String> missing = new ArrayList<>();
        Set<String> expectedNames = new HashSet<>();

        for (PipelineDescriptor.ResourceBinding resourceBinding : layout.bindings()) {
            String name = resourceBinding.name();
            expectedNames.add(name);
            switch (resourceBinding.type()) {
                case SAMPLER -> {
                    if (!samplerBindings.containsKey(name)) {
                        missing.add(name + "(SAMPLER)");
                    }
                }
                case UNIFORM_BUFFER -> {
                    if (!uniformBufferBindings.containsKey(name)) {
                        missing.add(name + "(UNIFORM_BUFFER)");
                    }
                }
                case TEXEL_BUFFER -> {
                    if (!texelBufferBindings.containsKey(name)) {
                        missing.add(name + "(TEXEL_BUFFER)");
                    }
                }
            }
        }

        List<String> unknown = new ArrayList<>();
        addUnknown(samplerBindings.keySet(), expectedNames, unknown, "SAMPLER");
        addUnknown(uniformBufferBindings.keySet(), expectedNames, unknown, "UNIFORM_BUFFER");
        addUnknown(texelBufferBindings.keySet(), expectedNames, unknown, "TEXEL_BUFFER");

        if (!missing.isEmpty() || !unknown.isEmpty()) {
            StringBuilder message = new StringBuilder("Pipeline resource bindings do not match descriptor layout.");
            if (!missing.isEmpty()) {
                message.append(" Missing: ").append(String.join(", ", missing)).append('.');
            }
            if (!unknown.isEmpty()) {
                message.append(" Unknown: ").append(String.join(", ", unknown)).append('.');
            }
            throw new IllegalArgumentException(message.toString());
        }
    }

    private static void addUnknown(
        Set<String> provided,
        Set<String> expected,
        List<String> unknown,
        String typeLabel
    ) {
        for (String name : provided) {
            if (!expected.contains(name)) {
                unknown.add(name + "(" + typeLabel + ")");
            }
        }
    }

    public record SamplerBinding(int textureUnit, @Nullable Integer samplerObject) {
        public SamplerBinding {
            if (textureUnit < 0) {
                throw new IllegalArgumentException("textureUnit must be >= 0");
            }
            if (samplerObject != null && samplerObject < 0) {
                throw new IllegalArgumentException("samplerObject must be >= 0 when provided");
            }
        }
    }

    public record TexelBufferBinding(int textureUnit) {
        public TexelBufferBinding {
            if (textureUnit < 0) {
                throw new IllegalArgumentException("textureUnit must be >= 0");
            }
        }
    }

    public static final class Builder {
        private final Map<String, SamplerBinding> samplerBindings = new HashMap<>();
        private final Map<String, VulkanicBufferSlice> uniformBufferBindings = new HashMap<>();
        private final Map<String, TexelBufferBinding> texelBufferBindings = new HashMap<>();

        public Builder bindSampler(String name, int textureUnit) {
            return bindSampler(name, textureUnit, null);
        }

        public Builder bindSampler(String name, int textureUnit, @Nullable Integer samplerObject) {
            String normalizedName = normalizeName(name);
            ensureNameUnused(normalizedName);
            samplerBindings.put(normalizedName, new SamplerBinding(textureUnit, samplerObject));
            return this;
        }

        public Builder bindUniformBuffer(String name, VulkanicBufferSlice slice) {
            String normalizedName = normalizeName(name);
            ensureNameUnused(normalizedName);
            VulkanicBufferSlice normalizedSlice = Objects.requireNonNull(slice, "slice must not be null");
            if (normalizedSlice.offset() < 0) {
                throw new IllegalArgumentException("slice.offset must be >= 0");
            }
            if (normalizedSlice.length() <= 0) {
                throw new IllegalArgumentException("slice.length must be > 0");
            }
            if (normalizedSlice.buffer() == null) {
                throw new IllegalArgumentException("slice.buffer must not be null");
            }
            uniformBufferBindings.put(normalizedName, normalizedSlice);
            return this;
        }

        public Builder bindUniformBuffer(String name, VulkanicBuffer buffer) {
            Objects.requireNonNull(buffer, "buffer must not be null");
            return bindUniformBuffer(name, buffer.slice());
        }

        public Builder bindTexelBuffer(String name, int textureUnit) {
            String normalizedName = normalizeName(name);
            ensureNameUnused(normalizedName);
            texelBufferBindings.put(normalizedName, new TexelBufferBinding(textureUnit));
            return this;
        }

        public PipelineResourceBindings build() {
            return new PipelineResourceBindings(samplerBindings, uniformBufferBindings, texelBufferBindings);
        }

        private String normalizeName(String name) {
            String normalizedName = Objects.requireNonNull(name, "name must not be null");
            if (normalizedName.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            return normalizedName;
        }

        private void ensureNameUnused(String name) {
            if (samplerBindings.containsKey(name)
                || uniformBufferBindings.containsKey(name)
                || texelBufferBindings.containsKey(name)) {
                throw new IllegalArgumentException(
                    "Resource name '" + name + "' is already bound. " +
                    "Each pipeline resource name may be bound at most once.");
            }
        }
    }
}