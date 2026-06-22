package net.sodium.client.gl.device;

/**
 * Vulkan-selected Sodium render device adapter.
 *
 * <p>The buffer arena and VAO/tessellation implementation underneath is still
 * the legacy compatibility implementation. Keeping a distinct Vulkan-selected
 * device gives the chunk renderer a backend-selected ownership seam without
 * changing OpenGL behavior or forcing a fragile flag-day buffer migration.</p>
 */
public class VulkanicRenderDevice extends GLRenderDevice {
}
