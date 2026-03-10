package net.vulkanic.backends.vulkan;

import net.vulkanic.GraphicsBackendType;
import net.vulkanic.backends.opengl.OpenGLBackend;

public class VulkanBackend extends OpenGLBackend {
    @Override
    public GraphicsBackendType getBackendType() {
        return GraphicsBackendType.VULKAN;
    }

    @Override
    public boolean isNativeVulkanReady() {
        return false;
    }

    public boolean isFallbackMode() {
        return !isNativeVulkanReady();
    }
}
