package net.vulkanic;

import java.nio.ByteBuffer;

/**
 * Abstract GPU buffer managed by the Vulkanic abstraction layer.
 *
 * <p>In OpenGL, this wraps a GL buffer object (glGenBuffers/glDeleteBuffers).
 * In Vulkan, this will wrap a VkBuffer + VkDeviceMemory pair.
 *
 * <p>Usage constants mirror those in Blaze3D's GpuBuffer for compatibility during migration.
 */
public abstract class VulkanicBuffer implements AutoCloseable {

    /** Buffer can be mapped for CPU reading. */
    public static final int USAGE_MAP_READ = 1;
    /** Buffer can be mapped for CPU writing. */
    public static final int USAGE_MAP_WRITE = 2;
    /** Hint: prefer client-side storage. */
    public static final int USAGE_HINT_CLIENT_STORAGE = 4;
    /** Buffer is a copy destination (transfer dst in Vulkan). */
    public static final int USAGE_COPY_DST = 8;
    /** Buffer is a copy source (transfer src in Vulkan). */
    public static final int USAGE_COPY_SRC = 16;
    /** Buffer is used as a vertex buffer. */
    public static final int USAGE_VERTEX = 32;
    /** Buffer is used as an index buffer. */
    public static final int USAGE_INDEX = 64;
    /** Buffer is used as a uniform buffer. */
    public static final int USAGE_UNIFORM = 128;
    /** Buffer is used as a uniform texel buffer. */
    public static final int USAGE_UNIFORM_TEXEL_BUFFER = 256;

    /** Returns the size of this buffer in bytes. */
    public abstract int size();

    /** Returns the usage flags this buffer was created with. */
    public abstract int usage();

    /** Returns true if this buffer has been closed and its GPU resources freed. */
    public abstract boolean isClosed();

    /** Frees the GPU resources backing this buffer. */
    @Override
    public abstract void close();

    /**
     * Creates a slice of this buffer.
     *
     * @param offset byte offset within the buffer
     * @param length byte length of the slice
     * @return a slice spanning [offset, offset+length)
     */
    public VulkanicBufferSlice slice(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > size()) {
            throw new IllegalArgumentException(
                "Offset " + offset + " and length " + length +
                " would be out of range for buffer of size " + size());
        }
        return new VulkanicBufferSlice(this, offset, length);
    }

    /** Returns a slice covering the entire buffer. */
    public VulkanicBufferSlice slice() {
        return new VulkanicBufferSlice(this, 0, size());
    }

    /**
     * Mapped view of a VulkanicBuffer — provides CPU access to GPU memory.
     *
     * <p>Callers must {@link #close()} the view when done to unmap the memory.
     */
    public interface MappedView extends AutoCloseable {
        /** Returns the CPU-accessible ByteBuffer backing this view. */
        ByteBuffer data();

        /** Unmaps the buffer memory. */
        @Override
        void close();
    }
}
