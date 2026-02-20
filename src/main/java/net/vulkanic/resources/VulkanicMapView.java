package net.vulkanic.resources;

import java.nio.ByteBuffer;

/**
 * A mapped view of a GPU buffer region that is CPU-accessible.
 *
 * <p>In OpenGL: backed by {@code glMapBufferRange} / {@code glUnmapBuffer}.
 * <br>In Vulkan: backed by {@code vkMapMemory} / {@code vkUnmapMemory}.
 *
 * <p>The mapped region is valid until {@link #close()} is called.
 * Always close in a finally block to avoid leaving GPU memory permanently mapped.
 *
 * <p>Replaces the Blaze3D {@code GpuBuffer.MappedView} abstraction — that interface
 * now extends this one, so all existing Blaze3D code continues to compile.
 */
public interface VulkanicMapView extends AutoCloseable {

    /**
     * Returns the CPU-visible byte buffer for this mapped region.
     *
     * <p>In OpenGL the buffer position/limit are set to the mapped slice.
     * In Vulkan the buffer wraps the {@code void*} pointer returned by
     * {@code vkMapMemory}.
     *
     * @return a {@link ByteBuffer} backed by mapped GPU memory
     */
    ByteBuffer data();

    /**
     * Unmaps the buffer region and releases the CPU-side pointer.
     *
     * <p>OpenGL: {@code glUnmapBuffer(target)}.
     * Vulkan: {@code vkUnmapMemory(device, memory)}.
     *
     * <p>After calling this method {@link #data()} must not be used.
     */
    @Override
    void close();
}
