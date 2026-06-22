package net.vulkanic.backends.vulkan;

/**
 * Terrain-specialized native Vulkan command encoder.
 *
 * <p>The implementation lives in {@link VulkanNativeCommandEncoder}; this wrapper preserves the
 * existing terrain entry point while enabling non-terrain render-pass slices to share the same
 * native command encoder without terrain-specific Iris/Sodium descriptor behavior.</p>
 */
final class VulkanNativeTerrainCommandEncoder extends VulkanNativeCommandEncoder {
    VulkanNativeTerrainCommandEncoder(VulkanBackend backend) {
        super(backend, ResourceMode.TERRAIN);
    }
}
