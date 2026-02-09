package net.vulkanic.backends.opengl;

import net.vulkanic.GraphicsBackend;
import net.vulkanic.GraphicsBackendProvider;
import net.vulkanic.VulkanicAPI;

/**
 * OpenGL backend provider implementation.
 * Registered via ServiceLoader to provide OpenGL backend instances.
 */
public class OpenGLBackendProvider implements GraphicsBackendProvider {
    
    @Override
    public VulkanicAPI.BackendType getBackendType() {
        return VulkanicAPI.BackendType.OPENGL;
    }
    
    @Override
    public GraphicsBackend createBackend() {
        return new OpenGLBackend();
    }
}
