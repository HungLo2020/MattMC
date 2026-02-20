package net.vulkanic.backends.opengl;

import net.vulkanic.resources.VulkanicSampler;
import net.vulkanic.resources.VulkanicSamplerDescriptor;

/**
 * OpenGL implementation of {@link VulkanicSampler}.
 *
 * <p>Stores the sampler descriptor for use by the {@link OpenGLBackend} when
 * applying sampler parameters to texture bindings.  In environments that
 * support {@code GL_ARB_sampler_objects} (core since OpenGL 3.3) the
 * native handle is a real GL sampler object name; otherwise it is 0 and
 * the backend must apply parameters at texture-bind time.
 *
 * <p>A Vulkan backend's equivalent will wrap a {@code VkSampler} handle
 * created with {@code vkCreateSampler}, populated from the same
 * {@link VulkanicSamplerDescriptor}.
 */
public final class OpenGLSampler implements VulkanicSampler {

    private final VulkanicSamplerDescriptor descriptor;
    /** GL sampler object name, or 0 if not using ARB_sampler_objects. */
    private final int glHandle;
    private boolean valid = true;

    OpenGLSampler(VulkanicSamplerDescriptor descriptor, int glHandle) {
        this.descriptor = descriptor;
        this.glHandle   = glHandle;
    }

    @Override
    public long getNativeHandle() {
        return glHandle;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public VulkanicSamplerDescriptor getDescriptor() {
        return descriptor;
    }

    /** Called by {@link OpenGLBackend#deleteSampler} to mark this sampler as destroyed. */
    void invalidate() {
        this.valid = false;
    }
}
