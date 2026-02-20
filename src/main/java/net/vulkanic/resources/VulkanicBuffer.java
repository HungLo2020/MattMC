package net.vulkanic.resources;

/**
 * Vulkanic abstraction for a GPU buffer resource.
 *
 * <p>This interface owns the buffer lifecycle concept that previously lived only
 * in Blaze3D's {@code GpuBuffer}. Having it in Vulkanic means both OpenGL and
 * Vulkan backends can implement it, and game/mod code can hold {@code VulkanicBuffer}
 * references without knowing which backend is active.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> wraps a {@code GlBuffer} (GL buffer object handle)</li>
 *   <li><b>Vulkan backend:</b> will wrap a {@code VkBuffer} + {@code VkDeviceMemory}</li>
 * </ul>
 *
 * Create via {@link net.vulkanic.VulkanicAPI#createVulkanicBuffer(int, int)}.
 */
public interface VulkanicBuffer extends AutoCloseable {

    // Usage flag constants (mirror GpuBuffer values for compatibility)
    int USAGE_MAP_READ             = 1;
    int USAGE_MAP_WRITE            = 2;
    int USAGE_HINT_CLIENT_STORAGE  = 4;
    int USAGE_COPY_DST             = 8;
    int USAGE_COPY_SRC             = 16;
    int USAGE_VERTEX               = 32;
    int USAGE_INDEX                = 64;
    int USAGE_UNIFORM              = 128;
    int USAGE_UNIFORM_TEXEL_BUFFER = 256;

    /**
     * Returns the backend-native handle for this buffer.
     * <ul>
     *   <li>OpenGL: the GL buffer object name (int)</li>
     *   <li>Vulkan: the {@code VkBuffer} handle (long)</li>
     * </ul>
     */
    long getNativeHandle();

    /** Size of the buffer in bytes. */
    int getSize();

    /** Usage flags bitmask (see {@code USAGE_*} constants). */
    int getUsage();
    /** Returns {@code true} if this buffer has been closed/freed. */
    boolean isClosed();

    /** Frees the GPU buffer. Must not be used after this call. */
    @Override
    void close();
}
