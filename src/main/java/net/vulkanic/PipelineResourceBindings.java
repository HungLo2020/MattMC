package net.vulkanic;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
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
    private final Map<String, StorageImageBinding> storageImageBindings;
    private final Map<String, TexelBufferBinding> texelBufferBindings;

    private PipelineResourceBindings(
        Map<String, SamplerBinding> samplerBindings,
        Map<String, VulkanicBufferSlice> uniformBufferBindings,
        Map<String, StorageImageBinding> storageImageBindings,
        Map<String, TexelBufferBinding> texelBufferBindings
    ) {
        this.samplerBindings = Map.copyOf(samplerBindings);
        this.uniformBufferBindings = Map.copyOf(uniformBufferBindings);
        this.storageImageBindings = Map.copyOf(storageImageBindings);
        this.texelBufferBindings = Map.copyOf(texelBufferBindings);
    }

    private PipelineResourceBindings(
        Map<String, SamplerBinding> samplerBindings,
        Map<String, VulkanicBufferSlice> uniformBufferBindings,
        Map<String, StorageImageBinding> storageImageBindings,
        Map<String, TexelBufferBinding> texelBufferBindings,
        boolean adoptResolvedBindings
    ) {
        if (adoptResolvedBindings) {
            this.samplerBindings = Collections.unmodifiableMap(Objects.requireNonNull(samplerBindings, "samplerBindings must not be null"));
            this.uniformBufferBindings = Collections.unmodifiableMap(Objects.requireNonNull(uniformBufferBindings, "uniformBufferBindings must not be null"));
            this.storageImageBindings = Collections.unmodifiableMap(Objects.requireNonNull(storageImageBindings, "storageImageBindings must not be null"));
            this.texelBufferBindings = Collections.unmodifiableMap(Objects.requireNonNull(texelBufferBindings, "texelBufferBindings must not be null"));
            return;
        }

        this.samplerBindings = Map.copyOf(samplerBindings);
        this.uniformBufferBindings = Map.copyOf(uniformBufferBindings);
        this.storageImageBindings = Map.copyOf(storageImageBindings);
        this.texelBufferBindings = Map.copyOf(texelBufferBindings);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates immutable bindings from already-resolved maps without re-copying them.
     *
     * <p>The caller transfers ownership of the provided maps and must not mutate them after
     * invoking this method.</p>
     */
    public static PipelineResourceBindings ofResolvedBindings(
        Map<String, SamplerBinding> samplerBindings,
        Map<String, VulkanicBufferSlice> uniformBufferBindings,
        Map<String, StorageImageBinding> storageImageBindings,
        Map<String, TexelBufferBinding> texelBufferBindings
    ) {
        return new PipelineResourceBindings(samplerBindings, uniformBufferBindings, storageImageBindings, texelBufferBindings, true);
    }

    public Optional<SamplerBinding> getSamplerBinding(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(samplerBindings.get(name));
    }

    @Nullable
    public SamplerBinding getSamplerBindingOrNull(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return samplerBindings.get(name);
    }

    public Optional<VulkanicBufferSlice> getUniformBufferBinding(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(uniformBufferBindings.get(name));
    }

    @Nullable
    public VulkanicBufferSlice getUniformBufferBindingOrNull(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return uniformBufferBindings.get(name);
    }

    public Optional<TexelBufferBinding> getTexelBufferBinding(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(texelBufferBindings.get(name));
    }

    @Nullable
    public TexelBufferBinding getTexelBufferBindingOrNull(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return texelBufferBindings.get(name);
    }

    public Optional<StorageImageBinding> getStorageImageBinding(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return Optional.ofNullable(storageImageBindings.get(name));
    }

    @Nullable
    public StorageImageBinding getStorageImageBindingOrNull(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return storageImageBindings.get(name);
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
                case SAMPLER, COMPARISON_SAMPLER -> {
                    if (!samplerBindings.containsKey(name)) {
                        missing.add(name + "(SAMPLER)");
                    }
                }
                case UNIFORM_BUFFER -> {
                    if (!uniformBufferBindings.containsKey(name)) {
                        missing.add(name + "(UNIFORM_BUFFER)");
                    }
                }
                case STORAGE_IMAGE -> {
                    if (!storageImageBindings.containsKey(name)) {
                        missing.add(name + "(STORAGE_IMAGE)");
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
        addUnknown(storageImageBindings.keySet(), expectedNames, unknown, "STORAGE_IMAGE");
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof PipelineResourceBindings other)) {
            return false;
        }
        return samplerBindings.equals(other.samplerBindings)
            && uniformBufferBindings.equals(other.uniformBufferBindings)
            && storageImageBindings.equals(other.storageImageBindings)
            && texelBufferBindings.equals(other.texelBufferBindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(samplerBindings, uniformBufferBindings, storageImageBindings, texelBufferBindings);
    }

    public record SamplerBinding(int textureUnit, @Nullable Integer samplerObject, @Nullable VulkanicTextureView textureView) {
        public SamplerBinding {
            if (textureUnit < 0) {
                throw new IllegalArgumentException("textureUnit must be >= 0");
            }
            if (samplerObject != null && samplerObject < 0) {
                throw new IllegalArgumentException("samplerObject must be >= 0 when provided");
            }
        }

        public SamplerBinding(int textureUnit, @Nullable Integer samplerObject) {
            this(textureUnit, samplerObject, null);
        }

        public SamplerBinding(int textureUnit, VulkanicTextureView textureView) {
            this(textureUnit, null, textureView);
        }

		public SamplerBinding withTextureView(@Nullable VulkanicTextureView textureView) {
			return new SamplerBinding(textureUnit, samplerObject, textureView);
		}
    }

    public record TexelBufferBinding(int textureUnit) {
        public TexelBufferBinding {
            if (textureUnit < 0) {
                throw new IllegalArgumentException("textureUnit must be >= 0");
            }
        }
    }

    public record StorageImageBinding(
        int imageUnit,
        int texture,
        int level,
        boolean layered,
        int layer,
        int access,
        int format
    ) {
        public StorageImageBinding {
            if (imageUnit < 0) {
                throw new IllegalArgumentException("imageUnit must be >= 0");
            }
            if (texture < 0) {
                throw new IllegalArgumentException("texture must be >= 0");
            }
            if (level < 0) {
                throw new IllegalArgumentException("level must be >= 0");
            }
            if (layer < 0) {
                throw new IllegalArgumentException("layer must be >= 0");
            }
        }
    }

    public static final class Builder {
        private final Map<String, SamplerBinding> samplerBindings = new HashMap<>();
        private final Map<String, VulkanicBufferSlice> uniformBufferBindings = new HashMap<>();
        private final Map<String, StorageImageBinding> storageImageBindings = new HashMap<>();
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

        public Builder bindSampler(String name, VulkanicTextureView textureView, int textureUnit) {
            return bindSampler(name, textureView, textureUnit, null);
        }

        public Builder bindSampler(String name, VulkanicTextureView textureView, int textureUnit, @Nullable Integer samplerObject) {
            String normalizedName = normalizeName(name);
            ensureNameUnused(normalizedName);
            samplerBindings.put(
                normalizedName,
                new SamplerBinding(textureUnit, samplerObject, Objects.requireNonNull(textureView, "textureView must not be null"))
            );
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

        public Builder bindStorageImage(String name, StorageImageBinding binding) {
            String normalizedName = normalizeName(name);
            ensureNameUnused(normalizedName);
            storageImageBindings.put(
                normalizedName,
                Objects.requireNonNull(binding, "binding must not be null")
            );
            return this;
        }

        public PipelineResourceBindings build() {
            return new PipelineResourceBindings(samplerBindings, uniformBufferBindings, storageImageBindings, texelBufferBindings);
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
                || storageImageBindings.containsKey(name)
                || texelBufferBindings.containsKey(name)) {
                throw new IllegalArgumentException(
                    "Resource name '" + name + "' is already bound. " +
                    "Each pipeline resource name may be bound at most once.");
            }
        }
    }
}
