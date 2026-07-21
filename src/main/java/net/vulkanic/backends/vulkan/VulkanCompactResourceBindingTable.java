package net.vulkanic.backends.vulkan;

import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.VulkanicBufferSlice;
import net.vulkanic.VulkanicPassResourceModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vulkan-internal, layout-ordered resource binding table derived from an
 * immutable GAL request.  The table is not part of the public GAL contract; it
 * is a compact lowering artifact that lets descriptor planning and native
 * resolution walk one indexed structure without rebuilding name-keyed maps for
 * every draw.
 */
final class VulkanCompactResourceBindingTable {
    private static final PipelineDescriptor.ResourceBinding[] EMPTY_LAYOUT_BINDINGS = new PipelineDescriptor.ResourceBinding[0];
    private static final Object[] EMPTY_RESOURCES = new Object[0];

    private final PipelineDescriptor descriptor;
    private final PipelineDescriptor.ResourceBinding[] layoutBindings;
    private final Object[] resourceBindings;
    private final int boundResourceCount;
    private final int missingResourceCount;

    VulkanCompactResourceBindingTable(
        PipelineDescriptor descriptor,
        PipelineDescriptor.ResourceBinding[] layoutBindings,
        Object[] resourceBindings,
        int boundResourceCount,
        int missingResourceCount
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(layoutBindings, "layoutBindings");
        Objects.requireNonNull(resourceBindings, "resourceBindings");
        int slotCount = layoutBindings.length;
        if (resourceBindings.length != slotCount) {
            throw new IllegalArgumentException("Compact binding resource array must match layout length");
        }
        this.layoutBindings = slotCount == 0 ? EMPTY_LAYOUT_BINDINGS : layoutBindings;
        this.resourceBindings = slotCount == 0 ? EMPTY_RESOURCES : resourceBindings;
        this.boundResourceCount = boundResourceCount;
        this.missingResourceCount = missingResourceCount;
        if (boundResourceCount < 0 || missingResourceCount < 0) {
            throw new IllegalArgumentException("Binding counts must be non-negative");
        }
        if (boundResourceCount + missingResourceCount != slotCount) {
            throw new IllegalArgumentException(
                "Bound/missing binding counts do not match slot count: bound="
                    + boundResourceCount
                    + " missing="
                    + missingResourceCount
                    + " slots="
                    + slotCount
            );
        }
        for (int index = 0; index < slotCount; index++) {
            Objects.requireNonNull(this.layoutBindings[index], "layoutBindings[" + index + "]");
            validateResourceSlot(index, this.layoutBindings[index], this.resourceBindings[index]);
        }
    }

    PipelineDescriptor descriptor() {
        return descriptor;
    }

    int slotCount() {
        return layoutBindings.length;
    }

    int boundResourceCount() {
        return boundResourceCount;
    }

    boolean completeCoverage() {
        return missingResourceCount == 0;
    }

    PipelineDescriptor.ResourceBinding layoutBinding(int index) {
        return layoutBindings[index];
    }

    @Nullable
    PipelineResourceBindings.SamplerBinding samplerBinding(int index) {
        Object resource = resourceBindings[index];
        return resource instanceof PipelineResourceBindings.SamplerBinding binding ? binding : null;
    }

    @Nullable
    VulkanicBufferSlice uniformBufferBinding(int index) {
        Object resource = resourceBindings[index];
        return resource instanceof VulkanicBufferSlice binding ? binding : null;
    }

    @Nullable
    PipelineResourceBindings.StorageImageBinding storageImageBinding(int index) {
        Object resource = resourceBindings[index];
        return resource instanceof PipelineResourceBindings.StorageImageBinding binding ? binding : null;
    }

    @Nullable
    PipelineResourceBindings.TexelBufferBinding texelBufferBinding(int index) {
        Object resource = resourceBindings[index];
        return resource instanceof PipelineResourceBindings.TexelBufferBinding binding ? binding : null;
    }

    List<String> missingResources() {
        if (missingResourceCount == 0) {
            return List.of();
        }
        ArrayList<String> missing = new ArrayList<>(missingResourceCount);
        for (int index = 0; index < layoutBindings.length; index++) {
            if (missing(index)) {
                PipelineDescriptor.ResourceBinding binding = layoutBindings[index];
                missing.add(binding.name() + "(" + binding.type() + ")");
            }
        }
        return List.copyOf(missing);
    }

    @Nullable
    PipelineResourceBindings.SamplerBinding samplerBinding(String name) {
        int index = indexOf(name);
        return index < 0 ? null : samplerBinding(index);
    }

    @Nullable
    VulkanicBufferSlice uniformBufferBinding(String name) {
        int index = indexOf(name);
        return index < 0 ? null : uniformBufferBinding(index);
    }

    @Nullable
    PipelineResourceBindings.StorageImageBinding storageImageBinding(String name) {
        int index = indexOf(name);
        return index < 0 ? null : storageImageBinding(index);
    }

    @Nullable
    PipelineResourceBindings.TexelBufferBinding texelBufferBinding(String name) {
        int index = indexOf(name);
        return index < 0 ? null : texelBufferBinding(index);
    }

    @Nullable
    VulkanicPassResourceModel.CanonicalResourceReference resourceReference(int index) {
        Object resource = resourceBindings[index];
        if (resource instanceof PipelineResourceBindings.SamplerBinding samplerBinding) {
            return samplerBinding.resourceReference();
        }
        if (resource instanceof PipelineResourceBindings.StorageImageBinding storageImageBinding) {
            return storageImageBinding.resourceReference();
        }
        if (resource instanceof PipelineResourceBindings.TexelBufferBinding texelBufferBinding) {
            return texelBufferBinding.resourceReference();
        }
        return null;
    }

    private boolean missing(int index) {
        return resourceBindings[index] == null;
    }

    private int indexOf(String name) {
        for (int index = 0; index < layoutBindings.length; index++) {
            if (layoutBindings[index].name().contentEquals(name)) {
                return index;
            }
        }
        return -1;
    }

    private static void validateResourceSlot(
        int index,
        PipelineDescriptor.ResourceBinding layoutBinding,
        @Nullable Object resource
    ) {
        if (resource == null) {
            return;
        }
        boolean valid = switch (layoutBinding.type()) {
            case SAMPLER, COMPARISON_SAMPLER -> resource instanceof PipelineResourceBindings.SamplerBinding;
            case UNIFORM_BUFFER -> resource instanceof VulkanicBufferSlice;
            case STORAGE_IMAGE -> resource instanceof PipelineResourceBindings.StorageImageBinding;
            case TEXEL_BUFFER -> resource instanceof PipelineResourceBindings.TexelBufferBinding;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                "Resource binding slot " + index + " has incompatible resource for " + layoutBinding.type());
        }
    }
}
