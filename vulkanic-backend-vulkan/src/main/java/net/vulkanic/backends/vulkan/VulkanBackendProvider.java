package net.vulkanic.backends.vulkan;

import net.vulkanic.GraphicsBackend;
import net.vulkanic.GraphicsBackendProvider;
import net.vulkanic.VulkanicAPI;

/**
 * Vulkan backend provider implementation (STUB).
 * Registered via ServiceLoader but throws UnsupportedOperationException.
 */
public class VulkanBackendProvider implements GraphicsBackendProvider {
    
    @Override
    public VulkanicAPI.BackendType getBackendType() {
        return VulkanicAPI.BackendType.VULKAN;
    }
    
    @Override
    public GraphicsBackend createBackend() {
        throw new UnsupportedOperationException("Vulkan backend not yet implemented");
    }
}
