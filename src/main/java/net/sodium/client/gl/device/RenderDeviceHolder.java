package net.sodium.client.gl.device;

import net.vulkanic.VulkanicAPI;

final class RenderDeviceHolder {
    private static final RenderDevice OPENGL_DEVICE = new GLRenderDevice();
    private static final RenderDevice VULKAN_DEVICE = new VulkanicRenderDevice();

    private RenderDeviceHolder() {
    }

    static RenderDevice instance() {
        return VulkanicAPI.isVulkanBackendSelected() ? VULKAN_DEVICE : OPENGL_DEVICE;
    }
}
