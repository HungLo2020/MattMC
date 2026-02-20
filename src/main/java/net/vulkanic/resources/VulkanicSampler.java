package net.vulkanic.resources;

/**
 * Opaque handle to a compiled sampler object.
 *
 * <p>A sampler encapsulates all state controlling how a texture is read during
 * shader execution: filter modes, address modes, LOD clamping, anisotropy, etc.
 *
 * <p>In Vulkan, samplers are <em>explicit objects</em> ({@code VkSampler}) that
 * exist independently of any image.  This design is deliberately adopted here
 * so that a future Vulkan backend can implement this interface directly, binding
 * the same {@code VkSampler} to descriptor sets without any changes to call sites.
 *
 * <p>In OpenGL, there is no separate sampler object for the common case of
 * setting sampler parameters on texture objects directly.  The OpenGL backend
 * can use {@code GL_ARB_sampler_objects} (core since 3.3) to create real
 * {@code glGenSamplers} objects, or it can store the descriptor and apply
 * parameters lazily at bind time.
 *
 * <p>Obtained via {@link net.vulkanic.VulkanicAPI#createSampler}.
 * Must be released via {@link net.vulkanic.VulkanicAPI#deleteSampler} when
 * no longer needed.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> native handle is a GL sampler object name (int),
 *       or 0 if the backend stores descriptor state only.</li>
 *   <li><b>Vulkan backend (future):</b> native handle is the {@code VkSampler}
 *       opaque 64-bit handle.</li>
 * </ul>
 */
public interface VulkanicSampler {

    /**
     * Returns the backend-native handle for this sampler.
     *
     * <ul>
     *   <li>OpenGL: GL sampler object name (int, widened to long);
     *       0 if not using {@code ARB_sampler_objects}.</li>
     *   <li>Vulkan: {@code VkSampler} handle (opaque 64-bit value).</li>
     * </ul>
     */
    long getNativeHandle();

    /**
     * Returns {@code true} if this sampler handle is valid and has not been
     * deleted.
     */
    boolean isValid();

    /**
     * Returns the descriptor that was used to create this sampler.
     *
     * <p>Useful for debugging or recreating an equivalent sampler after a
     * context loss / device reset.
     */
    VulkanicSamplerDescriptor getDescriptor();
}
