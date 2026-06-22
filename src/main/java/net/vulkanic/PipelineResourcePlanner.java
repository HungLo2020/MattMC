package net.vulkanic;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared Vulkanic resource-binding planner for render-pass submissions.
 *
 * <p>This class owns the descriptor-layout walk that decides which declared
 * resources are actually available for a draw.  Command encoders still own
 * render-pass lifetime and pipeline binding; the planner only produces the
 * descriptor variant and resource bindings to submit.</p>
 */
public final class PipelineResourcePlanner {
    private PipelineResourcePlanner() {
    }

    @Nullable
    public static Plan buildPlan(
        PipelineDescriptor descriptor,
        ResourceResolver resolver,
        Options options
    ) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(resolver, "resolver must not be null");
        Objects.requireNonNull(options, "options must not be null");

        List<PipelineDescriptor.ResourceBinding> layoutBindings = descriptor.getResourceLayout().bindings();
        int layoutBindingCount = layoutBindings.size();
        Map<String, PipelineResourceBindings.SamplerBinding> samplerBindings = new HashMap<>(layoutBindingCount);
        Map<String, VulkanicBufferSlice> uniformBufferBindings = new HashMap<>(layoutBindingCount);
        Map<String, PipelineResourceBindings.TexelBufferBinding> texelBufferBindings = new HashMap<>(layoutBindingCount);
        List<PipelineDescriptor.ResourceBinding> boundResources = new ArrayList<>(layoutBindingCount);
        List<String> missingResources = options.collectMissingResources() ? new ArrayList<>() : List.of();

        for (PipelineDescriptor.ResourceBinding binding : layoutBindings) {
            @Nullable ResolvedResource resolvedResource = resolver.resolve(binding);
            if (resolvedResource == null) {
                if (options.collectMissingResources()) {
                    missingResources.add(options.missingResourceDescriber().describe(binding));
                }
                continue;
            }

            switch (binding.type()) {
                case SAMPLER, COMPARISON_SAMPLER -> {
                    PipelineResourceBindings.SamplerBinding samplerBinding = resolvedResource.samplerBinding();
                    if (samplerBinding != null) {
                        samplerBindings.put(binding.name(), samplerBinding);
                        boundResources.add(binding);
                    } else if (options.collectMissingResources()) {
                        missingResources.add(options.missingResourceDescriber().describe(binding));
                    }
                }
                case UNIFORM_BUFFER -> {
                    VulkanicBufferSlice slice = resolvedResource.uniformBufferSlice();
                    if (slice != null) {
                        uniformBufferBindings.put(binding.name(), slice);
                        boundResources.add(binding);
                    } else if (options.collectMissingResources()) {
                        missingResources.add(options.missingResourceDescriber().describe(binding));
                    }
                }
                case TEXEL_BUFFER -> {
                    PipelineResourceBindings.TexelBufferBinding texelBufferBinding = resolvedResource.texelBufferBinding();
                    if (texelBufferBinding != null) {
                        texelBufferBindings.put(binding.name(), texelBufferBinding);
                        boundResources.add(binding);
                    } else if (options.collectMissingResources()) {
                        missingResources.add(options.missingResourceDescriber().describe(binding));
                    }
                }
            }
        }

        if (options.requireAtLeastOneBinding() && boundResources.isEmpty()) {
            return null;
        }

        boolean completeCoverage = boundResources.size() == layoutBindingCount;
        PipelineDescriptor submissionDescriptor = !completeCoverage && options.filterIncompleteLayout()
            ? descriptor.withResourceLayout(new PipelineDescriptor.ResourceLayout(boundResources))
            : descriptor;

        return new Plan(
            submissionDescriptor,
            PipelineResourceBindings.ofResolvedBindings(samplerBindings, uniformBufferBindings, texelBufferBindings),
            completeCoverage,
            boundResources.size(),
            missingResources
        );
    }

    public static Options options() {
        return new Options(true, true, MissingResourceDescriber.DEFAULT);
    }

    public record Plan(
        PipelineDescriptor descriptor,
        PipelineResourceBindings bindings,
        boolean completeCoverage,
        int boundResourceCount,
        List<String> missingResources
    ) {
        public Plan {
            Objects.requireNonNull(descriptor, "descriptor must not be null");
            Objects.requireNonNull(bindings, "bindings must not be null");
            missingResources = List.copyOf(Objects.requireNonNull(missingResources, "missingResources must not be null"));
        }
    }

    public record Options(
        boolean requireAtLeastOneBinding,
        boolean filterIncompleteLayout,
        MissingResourceDescriber missingResourceDescriber
    ) {
        public Options {
            Objects.requireNonNull(missingResourceDescriber, "missingResourceDescriber must not be null");
        }

        public Options requireAtLeastOneBinding(boolean requireAtLeastOneBinding) {
            return new Options(requireAtLeastOneBinding, this.filterIncompleteLayout, this.missingResourceDescriber);
        }

        public Options filterIncompleteLayout(boolean filterIncompleteLayout) {
            return new Options(this.requireAtLeastOneBinding, filterIncompleteLayout, this.missingResourceDescriber);
        }

        public Options missingResourceDescriber(MissingResourceDescriber missingResourceDescriber) {
            return new Options(this.requireAtLeastOneBinding, this.filterIncompleteLayout, missingResourceDescriber);
        }

        public boolean collectMissingResources() {
            return this.missingResourceDescriber != MissingResourceDescriber.NONE;
        }
    }

    public record ResolvedResource(
        @Nullable PipelineResourceBindings.SamplerBinding samplerBinding,
        @Nullable VulkanicBufferSlice uniformBufferSlice,
        @Nullable PipelineResourceBindings.TexelBufferBinding texelBufferBinding
    ) {
        public static ResolvedResource sampler(PipelineResourceBindings.SamplerBinding samplerBinding) {
            return new ResolvedResource(Objects.requireNonNull(samplerBinding, "samplerBinding must not be null"), null, null);
        }

        public static ResolvedResource uniformBuffer(VulkanicBufferSlice slice) {
            return new ResolvedResource(null, Objects.requireNonNull(slice, "slice must not be null"), null);
        }

        public static ResolvedResource texelBuffer(PipelineResourceBindings.TexelBufferBinding texelBufferBinding) {
            return new ResolvedResource(null, null, Objects.requireNonNull(texelBufferBinding, "texelBufferBinding must not be null"));
        }
    }

    @FunctionalInterface
    public interface ResourceResolver {
        @Nullable
        ResolvedResource resolve(PipelineDescriptor.ResourceBinding binding);
    }

    @FunctionalInterface
    public interface MissingResourceDescriber {
        MissingResourceDescriber NONE = binding -> "";
        MissingResourceDescriber DEFAULT = binding -> binding.name() + "(" + binding.type() + ")";

        String describe(PipelineDescriptor.ResourceBinding binding);
    }
}
