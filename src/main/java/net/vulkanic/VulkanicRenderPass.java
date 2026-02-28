package net.vulkanic;

/**
 * Scoped handle for recording rendering commands within a single render pass.
 *
 * <p>A render pass targets a specific set of color and optional depth textures.
 * All draw calls issued through a {@code VulkanicRenderPass} render into those
 * attachments.
 *
 * <p><b>In OpenGL:</b> backed by a framebuffer object (FBO). Commands execute
 * immediately in the OpenGL thread. {@link #close()} unbinds the FBO.
 *
 * <p><b>In Vulkan (future):</b> backed by a {@code VkRenderPass} + {@code VkCommandBuffer}.
 * Commands are recorded for deferred execution. {@link #close()} records
 * {@code vkCmdEndRenderPass} into the command buffer.
 *
 * <p>Typical usage:
 * <pre>
 * CommandContext ctx = VulkanicAPI.beginCommandBuffer();
 * try (VulkanicRenderPass pass = VulkanicAPI.beginRenderPass(
 *         ctx, () -> "main-pass", colorView, OptionalInt.of(0xFF000000))) {
 *     pass.setPipeline(pipelineHandle);
 *     pass.setVertexBuffer(0, vertexBuffer);
 *     pass.setIndexBuffer(indexBuffer, VulkanicIndexType.INT);
 *     pass.drawIndexed(0, indexCount, 0, 1);
 * }
 * VulkanicAPI.submitCommandBuffer(ctx);
 * </pre>
 */
public interface VulkanicRenderPass extends AutoCloseable {

    /**
     * Binds a compiled pipeline for subsequent draw calls.
     *
     * <p>In OpenGL: activates the GL shader program.
     * In Vulkan: records {@code vkCmdBindPipeline}.
     *
     * @param pipeline a valid {@link PipelineHandle} obtained from
     *                 {@link VulkanicAPI#createPipeline(PipelineDescriptor)}
     * @throws IllegalArgumentException if the pipeline is not valid
     */
    void setPipeline(PipelineHandle pipeline);

    /**
     * Binds a vertex buffer to the given slot.
     *
     * <p>In OpenGL: binds the GL buffer as {@code GL_ARRAY_BUFFER}.
     * In Vulkan: records {@code vkCmdBindVertexBuffers}.
     *
     * @param slot   vertex buffer binding slot (0-based)
     * @param buffer the vertex data buffer
     */
    void setVertexBuffer(int slot, VulkanicBuffer buffer);

    /**
     * Binds an index buffer.
     *
     * <p>In OpenGL: binds the GL buffer as {@code GL_ELEMENT_ARRAY_BUFFER}.
     * In Vulkan: records {@code vkCmdBindIndexBuffer}.
     *
     * @param buffer    the index data buffer
     * @param indexType the index element type
     */
    void setIndexBuffer(VulkanicBuffer buffer, VulkanicIndexType indexType);

    /**
     * Issues an indexed draw call.
     *
     * <p>In OpenGL: calls {@code glDrawElementsInstancedBaseVertex} (or
     * {@code glDrawElements} when instanceCount == 1 and baseVertex == 0).
     * In Vulkan: records {@code vkCmdDrawIndexed}.
     *
     * @param firstIndex    index of the first element in the index buffer (multiplied by bytesPerIndex to get byte offset)
     * @param indexCount    number of index elements to draw
     * @param baseVertex    constant added to each index before fetching vertex data
     * @param instanceCount number of instances to draw (typically 1)
     */
    void drawIndexed(int firstIndex, int indexCount, int baseVertex, int instanceCount);

    /**
     * Issues a non-indexed draw call.
     *
     * <p>In OpenGL: calls {@code glDrawArrays(GL_TRIANGLES, ...)}.
     * In Vulkan: records {@code vkCmdDraw}.
     *
     * @param firstVertex first vertex index in the vertex buffer
     * @param vertexCount number of vertices to draw
     */
    void draw(int firstVertex, int vertexCount);

    /**
     * Ends the render pass and releases any held resources.
     *
     * <p>In OpenGL: unbinds the FBO (restores default framebuffer) and
     * deletes the temporary FBO created by {@link VulkanicAPI#beginRenderPass}.
     * In Vulkan: records {@code vkCmdEndRenderPass} into the command buffer.
     *
     * <p>This method is idempotent — calling it more than once is safe.
     */
    @Override
    void close();
}
