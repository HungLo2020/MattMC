package net.vulkanic;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared resource resolver for descriptor-style render-pass submissions.
 *
 * <p>The encoders own render-pass lifetime and resource storage, but the rules
 * for converting a reflected pipeline layout into submitted sampler, UBO, and
 * texel-buffer bindings should be identical across compatibility and native
 * Vulkan paths.</p>
 */
public final class VulkanicPipelineResourceResolver {
    private VulkanicPipelineResourceResolver() {
    }

    @Nullable
    public static PipelineResourcePlanner.Plan buildPlan(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        ResourceLookup lookup,
        PipelineResourcePlanner.Options options
    ) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(lookup, "lookup must not be null");
        Objects.requireNonNull(options, "options must not be null");

        return PipelineResourcePlanner.buildPlan(
            descriptor,
            binding -> resolve(ctx, binding, lookup),
            options
        );
    }

    public static List<String> collectMissingResources(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        ResourceLookup lookup
    ) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(lookup, "lookup must not be null");

        List<String> missing = new ArrayList<>();
        for (PipelineDescriptor.ResourceBinding binding : descriptor.getResourceLayout().bindings()) {
            switch (binding.type()) {
                case SAMPLER, COMPARISON_SAMPLER -> {
                    Integer samplerUnit = lookup.samplerUnit(binding);
                    VulkanicTextureView view = lookup.samplerView(binding);
                    if (samplerUnit == null || view == null) {
                        missing.add(binding.name() + "(" + binding.type()
                            + ",unit=" + (samplerUnit != null)
                            + ",view=" + (view != null) + ")");
                    }
                }
                case UNIFORM_BUFFER -> {
                    VulkanicBufferSlice slice = resolveUniformSlice(ctx, binding, lookup);
                    if (slice == null) {
                        missing.add(binding.name() + "(UNIFORM_BUFFER)");
                    }
                }
                case TEXEL_BUFFER -> {
                    Integer textureUnit = lookup.texelBufferUnit(binding);
                    if (textureUnit == null) {
                        missing.add(binding.name() + "(TEXEL_BUFFER,unit=false)");
                    }
                }
            }
        }
        return missing;
    }

    @Nullable
    private static PipelineResourcePlanner.ResolvedResource resolve(
        CommandContext ctx,
        PipelineDescriptor.ResourceBinding binding,
        ResourceLookup lookup
    ) {
        return switch (binding.type()) {
            case SAMPLER, COMPARISON_SAMPLER -> {
                VulkanicTextureView view = lookup.samplerView(binding);
                Integer samplerUnit = lookup.samplerUnit(binding);
                if (view != null && samplerUnit != null) {
                    yield PipelineResourcePlanner.ResolvedResource.sampler(
                        new PipelineResourceBindings.SamplerBinding(
                            samplerUnit,
                            lookup.samplerObject(samplerUnit),
                            view
                        )
                    );
                }
                yield null;
            }
            case UNIFORM_BUFFER -> {
                VulkanicBufferSlice slice = resolveUniformSlice(ctx, binding, lookup);
                yield slice != null ? PipelineResourcePlanner.ResolvedResource.uniformBuffer(slice) : null;
            }
            case TEXEL_BUFFER -> {
                Integer textureUnit = lookup.texelBufferUnit(binding);
                yield textureUnit != null
                    ? PipelineResourcePlanner.ResolvedResource.texelBuffer(
                        new PipelineResourceBindings.TexelBufferBinding(textureUnit)
                    )
                    : null;
            }
        };
    }

    @Nullable
    private static VulkanicBufferSlice resolveUniformSlice(
        CommandContext ctx,
        PipelineDescriptor.ResourceBinding binding,
        ResourceLookup lookup
    ) {
        VulkanicBufferSlice slice = lookup.uniformBufferSlice(binding);
        if (slice != null || !VulkanicAPI.generatedStandaloneUniformBlockName().equals(binding.name())) {
            return slice;
        }

        Integer programId = lookup.standaloneProgramId(binding);
        return programId != null && programId >= 0
            ? VulkanicAPI.getStandaloneUniformBufferSlice(ctx, programId)
            : null;
    }

    public interface ResourceLookup {
        @Nullable
        VulkanicTextureView samplerView(PipelineDescriptor.ResourceBinding binding);

        @Nullable
        Integer samplerUnit(PipelineDescriptor.ResourceBinding binding);

        @Nullable
        VulkanicBufferSlice uniformBufferSlice(PipelineDescriptor.ResourceBinding binding);

        @Nullable
        Integer texelBufferUnit(PipelineDescriptor.ResourceBinding binding);

        @Nullable
        Integer standaloneProgramId(PipelineDescriptor.ResourceBinding binding);

        @Nullable
        Integer samplerObject(int samplerUnit);
    }
}
