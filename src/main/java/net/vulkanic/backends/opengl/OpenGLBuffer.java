package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicBuffer;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

/**
 * OpenGL implementation of {@link VulkanicBuffer}.
 *
 * <p>Wraps a GL buffer object identified by a native integer handle.
 * Resources are freed when {@link #close()} is called (calls glDeleteBuffers).
 */
public class OpenGLBuffer extends VulkanicBuffer {

    private final int glHandle;
    private final int usage;
    private final int size;
    private boolean closed;

    /**
     * Creates an OpenGLBuffer wrapping the given GL buffer handle.
     *
     * @param glHandle GL buffer object name (from glGenBuffers / glCreateBuffers)
     * @param usage    usage flags (USAGE_* constants)
     * @param size     size in bytes
     */
    public OpenGLBuffer(int glHandle, int usage, int size) {
        this.glHandle = glHandle;
        this.usage = usage;
        this.size = size;
        this.closed = false;
    }

    /**
     * Returns the native GL buffer object name.
     * Backend code may use this to bind or upload data to the buffer.
     */
    public int getGlHandle() {
        return glHandle;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int usage() {
        return usage;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            GL15.glDeleteBuffers(glHandle);
        }
    }

    /**
     * OpenGL implementation of MappedView.
     * Stores a ByteBuffer slice and an unmapping callback.
     */
    public static class OpenGLMappedView implements VulkanicBuffer.MappedView {
        private final Runnable unmap;
        private final ByteBuffer data;
        private boolean closed;

        public OpenGLMappedView(Runnable unmap, ByteBuffer data) {
            this.unmap = unmap;
            this.data = data;
        }

        @Override
        public ByteBuffer data() {
            return data;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                unmap.run();
            }
        }
    }

    @Override
    public String toString() {
        return "OpenGLBuffer{handle=" + glHandle + ", size=" + size + ", closed=" + closed + "}";
    }
}
