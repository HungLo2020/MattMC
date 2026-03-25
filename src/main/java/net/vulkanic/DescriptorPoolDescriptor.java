package net.vulkanic;

/**
 * Backend-agnostic descriptor-pool allocation metadata.
 *
 * <p>This is a pre-Vulkan seam for explicit descriptor allocation lifetime.
 */
public record DescriptorPoolDescriptor(int maxSets) {

    public DescriptorPoolDescriptor {
        if (maxSets <= 0) {
            throw new IllegalArgumentException("maxSets must be > 0");
        }
    }
}
