package net.vulkanic.resources;

import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.vertex.VertexFormat;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Vulkanic equivalent of Blaze3D's {@code RenderPass}.
 *
 * <p>Represents a single rendering scope bounded by a colour (and optionally depth) attachment.
 * All draw calls, pipeline binds, and buffer/sampler binds are performed within an active
 * {@code VulkanicRenderPass}.
 *
 * <p>Obtained via {@link net.vulkanic.VulkanicAPI#createVulkanicRenderPass}.
 * Must be {@link #close()}d when rendering for that scope is complete.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> wraps {@code GlRenderPass} — FBO binding, pipeline state,
 *       vertex-array configuration, and {@code glDraw*} calls.</li>
 *   <li><b>Vulkan backend (future):</b> records {@code vkCmdBindPipeline},
 *       {@code vkCmdBindVertexBuffers}, {@code vkCmdBindIndexBuffer},
 *       {@code vkCmdDrawIndexed}, etc. between
 *       {@code vkCmdBeginRenderPass} and {@code vkCmdEndRenderPass}.</li>
 * </ul>
 *
 * <p>All methods that take buffer or texture arguments use Vulkanic types so
 * that a Vulkan backend never needs to touch Blaze3D resource objects.
 */
public interface VulkanicRenderPass extends AutoCloseable {

    // -------------------------------------------------------------------------
    // Debug groups
    // -------------------------------------------------------------------------

    /**
     * Pushes a debug group label.
     * <ul>
     *   <li>OpenGL: {@code glPushDebugGroup}</li>
     *   <li>Vulkan: {@code vkCmdBeginDebugUtilsLabelEXT}</li>
     * </ul>
     */
    void pushDebugGroup(Supplier<String> label);

    /** Pops the last debug group. */
    void popDebugGroup();

    // -------------------------------------------------------------------------
    // Pipeline bind
    // -------------------------------------------------------------------------

    /**
     * Binds a compiled render pipeline for subsequent draw calls.
     *
     * <p>{@code RenderPipeline} is a Blaze3D descriptor object (shader locations, vertex
     * format, blend/depth state) that is backend-agnostic in its <em>description</em> role.
     * Using it here keeps the migration incremental while the Vulkanic pipeline compilation
     * path ({@link net.vulkanic.VulkanicAPI#precompilePipeline}) is established.
     *
     * <ul>
     *   <li>OpenGL: compiles/looks-up the GLSL program, applies rasterisation state.</li>
     *   <li>Vulkan: will look up the pre-compiled {@code VkPipeline} and call
     *       {@code vkCmdBindPipeline}.</li>
     * </ul>
     *
     * @param renderPipeline Pipeline descriptor to compile and bind
     */
    void setPipeline(RenderPipeline renderPipeline);

    // -------------------------------------------------------------------------
    // Resource binds — all use Vulkanic types so the Vulkan backend is clean
    // -------------------------------------------------------------------------

    /**
     * Binds a texture sampler at the given name.
     *
     * <ul>
     *   <li>OpenGL: sets the named uniform sampler.</li>
     *   <li>Vulkan: will update the descriptor set for the sampler binding.</li>
     * </ul>
     *
     * @param name Sampler name as declared in the shader
     * @param view Texture view to bind; {@code null} to unbind
     */
    void bindSampler(String name, @Nullable VulkanicTextureView view);

    /**
     * Binds a uniform buffer at the given name.
     *
     * <ul>
     *   <li>OpenGL: binds the whole buffer to the named UBO binding point.</li>
     *   <li>Vulkan: will update the descriptor set for the UBO binding.</li>
     * </ul>
     *
     * @param name   Uniform name as declared in the shader
     * @param buffer Uniform buffer (must have {@code USAGE_UNIFORM})
     */
    void setUniform(String name, VulkanicBuffer buffer);

    /**
     * Binds a sub-range of a uniform buffer at the given name.
     *
     * <ul>
     *   <li>OpenGL: calls {@code glBindBufferRange} for the named UBO slot.</li>
     *   <li>Vulkan: will update the descriptor set with an offset/range
     *       {@code VkDescriptorBufferInfo}.</li>
     * </ul>
     *
     * @param name  Uniform name as declared in the shader
     * @param slice Buffer slice (must cover a valid range within a USAGE_UNIFORM buffer)
     */
    void setUniform(String name, VulkanicBufferSlice slice);

    /**
     * Binds a vertex buffer at the given slot.
     *
     * <ul>
     *   <li>OpenGL: binds the buffer in the VAO's vertex-buffer binding slot.</li>
     *   <li>Vulkan: will call {@code vkCmdBindVertexBuffers}.</li>
     * </ul>
     *
     * @param slot   Vertex-buffer binding slot (0-based)
     * @param buffer Vertex buffer (must have {@code USAGE_VERTEX})
     */
    void setVertexBuffer(int slot, VulkanicBuffer buffer);

    /**
     * Binds an index buffer.
     *
     * <ul>
     *   <li>OpenGL: binds the buffer to {@code GL_ELEMENT_ARRAY_BUFFER}.</li>
     *   <li>Vulkan: will call {@code vkCmdBindIndexBuffer}.</li>
     * </ul>
     *
     * @param buffer    Index buffer (must have {@code USAGE_INDEX})
     * @param indexType {@code VertexFormat.IndexType.SHORT} or {@code INT}
     */
    void setIndexBuffer(VulkanicBuffer buffer, VertexFormat.IndexType indexType);

    // -------------------------------------------------------------------------
    // Scissor
    // -------------------------------------------------------------------------

    /**
     * Enables scissor testing within the given rectangle.
     *
     * <ul>
     *   <li>OpenGL: {@code glEnable(GL_SCISSOR_TEST)} + {@code glScissor}.</li>
     *   <li>Vulkan: will call {@code vkCmdSetScissor}.</li>
     * </ul>
     */
    void enableScissor(int x, int y, int width, int height);

    /** Disables scissor testing. */
    void disableScissor();

    // -------------------------------------------------------------------------
    // Draw calls
    // -------------------------------------------------------------------------

    /**
     * Issues an indexed draw call.
     *
     * <ul>
     *   <li>OpenGL: {@code glDrawElementsBaseVertex}.</li>
     *   <li>Vulkan: {@code vkCmdDrawIndexed}.</li>
     * </ul>
     *
     * @param firstIndex  First index in the index buffer
     * @param indexCount  Number of indices to draw
     * @param vertexOffset Offset added to each index value to determine the vertex index
     * @param instanceCount Number of instances to draw
     */
    void drawIndexed(int firstIndex, int indexCount, int vertexOffset, int instanceCount);

    /**
     * Issues a non-indexed draw call.
     *
     * <ul>
     *   <li>OpenGL: {@code glDrawArrays}.</li>
     *   <li>Vulkan: {@code vkCmdDraw}.</li>
     * </ul>
     *
     * @param firstVertex First vertex index
     * @param vertexCount Number of vertices to draw
     */
    void draw(int firstVertex, int vertexCount);

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Ends the render pass and releases the FBO / attachment binding.
     *
     * <ul>
     *   <li>OpenGL: unbinds the FBO, restores default framebuffer state.</li>
     *   <li>Vulkan: calls {@code vkCmdEndRenderPass}.</li>
     * </ul>
     */
    @Override
    void close();
}
