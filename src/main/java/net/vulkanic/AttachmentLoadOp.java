package net.vulkanic;

/**
 * Specifies how a render pass attachment's contents are treated at the start of the pass.
 *
 * <p>In OpenGL, CLEAR maps to {@code glClear()}; LOAD and DONT_CARE are no-ops
 * (the attachment's existing contents are preserved or left undefined, respectively).
 *
 * <p>In Vulkan, this maps directly to {@code VkAttachmentLoadOp}:
 * <ul>
 *   <li>LOAD  → {@code VK_ATTACHMENT_LOAD_OP_LOAD}
 *   <li>CLEAR → {@code VK_ATTACHMENT_LOAD_OP_CLEAR}
 *   <li>DONT_CARE → {@code VK_ATTACHMENT_LOAD_OP_DONT_CARE}
 * </ul>
 *
 * @see RenderPassColorAttachment
 * @see RenderPassDepthAttachment
 */
public enum AttachmentLoadOp {

    /**
     * Preserve the existing contents of the attachment at the start of the render pass.
     *
     * <p>Use this for subsequent passes that read the results written by a previous pass
     * (e.g. a post-process pass reading a scene color buffer).
     *
     * <p>In OpenGL: no-op — the framebuffer attachment's current contents are kept.
     * In Vulkan: {@code VK_ATTACHMENT_LOAD_OP_LOAD}.
     */
    LOAD,

    /**
     * Clear the attachment to a specified value at the start of the render pass.
     *
     * <p>Use this for the first pass in a frame that needs to initialize the buffer
     * (e.g. clearing the color buffer to sky color, or clearing the depth buffer to 1.0).
     * The clear value is carried in the enclosing {@link RenderPassColorAttachment} or
     * {@link RenderPassDepthAttachment} descriptor.
     *
     * <p>In OpenGL: issues {@code glClear()} with the value from the attachment descriptor.
     * In Vulkan: {@code VK_ATTACHMENT_LOAD_OP_CLEAR}; the value is placed in
     * {@code VkRenderPassBeginInfo.pClearValues}.
     */
    CLEAR,

    /**
     * The initial contents of the attachment are undefined — the caller will write
     * every pixel before reading from the attachment.
     *
     * <p>This can improve performance on tile-based GPU architectures (common on mobile)
     * by avoiding an expensive load of attachment data from main memory into tile cache.
     * On desktop GPUs the benefit is typically smaller, but it still communicates intent
     * to the driver and validation layers.
     *
     * <p>In OpenGL: treated as {@link #LOAD} (no-op). A future optimization may call
     * {@code glInvalidateFramebuffer()} on supported hardware to hint the driver that
     * the attachment's initial contents are not needed.
     * In Vulkan: {@code VK_ATTACHMENT_LOAD_OP_DONT_CARE}.
     */
    DONT_CARE
}
