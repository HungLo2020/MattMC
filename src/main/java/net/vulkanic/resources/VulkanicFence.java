package net.vulkanic.resources;

/**
 * Vulkanic fence — a GPU/CPU synchronisation primitive.
 *
 * <p>A fence signals when the GPU has finished executing commands up to the
 * point where the fence was inserted.  The CPU can then block on the fence
 * via {@link #awaitCompletion(long)} to ensure the GPU work is done before
 * proceeding (e.g. reading back texture data, reusing a staging buffer).
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> wraps a GL sync object created with
 *       {@code glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)}.
 *       {@link #awaitCompletion} calls {@code glClientWaitSync}.</li>
 *   <li><b>Vulkan backend (future):</b> wraps a {@code VkFence}.
 *       {@link #awaitCompletion} calls {@code vkWaitForFences}.
 *       Fences in Vulkan must be explicitly reset with
 *       {@code vkResetFences} before reuse; the backend handles this.</li>
 * </ul>
 *
 * <p>Obtained via {@link net.vulkanic.VulkanicAPI#createFence}.
 * Must be {@link #close()}d when no longer needed to release the
 * underlying GPU synchronisation object.
 *
 * <p>Usage:
 * <pre>{@code
 * CommandContext ctx = VulkanicAPI.beginCommandBuffer();
 * // ... issue GPU work ...
 * VulkanicFence fence = VulkanicAPI.createFence(ctx);
 * VulkanicAPI.submitCommandBuffer(ctx);
 * fence.awaitCompletion(Long.MAX_VALUE); // wait for GPU
 * fence.close();
 * }</pre>
 */
public interface VulkanicFence extends AutoCloseable {

    /**
     * Blocks the calling thread until this fence signals or the timeout
     * expires.
     *
     * <ul>
     *   <li>OpenGL: calls {@code glClientWaitSync}.</li>
     *   <li>Vulkan: calls {@code vkWaitForFences}.</li>
     * </ul>
     *
     * @param timeoutNanos Maximum time to wait in nanoseconds.
     *                     Use {@link Long#MAX_VALUE} to wait indefinitely.
     * @return {@code true} if the fence signalled within the timeout;
     *         {@code false} if the timeout expired before the fence signalled.
     * @throws IllegalStateException if the underlying sync object is in an
     *                               error state (e.g. {@code GL_WAIT_FAILED}).
     */
    boolean awaitCompletion(long timeoutNanos);

    /**
     * Destroys this fence and releases the underlying GPU resource.
     *
     * <ul>
     *   <li>OpenGL: calls {@code glDeleteSync}.</li>
     *   <li>Vulkan: calls {@code vkDestroyFence}.</li>
     * </ul>
     */
    @Override
    void close();
}
