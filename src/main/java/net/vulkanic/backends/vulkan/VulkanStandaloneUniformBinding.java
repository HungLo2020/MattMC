package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicGalV2;

import java.util.Objects;

/**
 * Vulkan-internal lowering reference for a GAL v2 standalone-uniform binding.
 *
 * <p>This is deliberately not a native resource handle. It preserves the
 * request-owned semantic uniform binding until descriptor materialization can
 * publish the current packed payload into the frame-slot arena and use only a
 * dynamic offset for the draw.</p>
 */
record VulkanStandaloneUniformBinding(
    int programId,
    String bindingName,
    VulkanicGalV2.Handle handle
) {
    VulkanStandaloneUniformBinding {
        if (programId <= 0) {
            throw new IllegalArgumentException("programId must be positive");
        }
        bindingName = Objects.requireNonNull(bindingName, "bindingName");
        handle = Objects.requireNonNull(handle, "handle");
    }
}
