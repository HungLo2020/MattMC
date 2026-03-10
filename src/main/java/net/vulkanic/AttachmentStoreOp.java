package net.vulkanic;

/**
 * Specifies how a render pass attachment's contents are treated at the end of the pass.
 *
 * <p>In OpenGL, STORE is a no-op (OpenGL always preserves framebuffer contents after
 * rendering); DONT_CARE is also a no-op today but may map to
 * {@code glInvalidateFramebuffer()} in a future optimization pass.
 *
 * <p>In Vulkan, this maps directly to {@code VkAttachmentStoreOp}:
 * <ul>
 *   <li>STORE     → {@code VK_ATTACHMENT_STORE_OP_STORE}
 *   <li>DONT_CARE → {@code VK_ATTACHMENT_STORE_OP_DONT_CARE}
 * </ul>
 *
 * @see RenderPassColorAttachment
 * @see RenderPassDepthAttachment
 */
public enum AttachmentStoreOp {

    /**
     * The rendered contents of the attachment are written back (preserved) after
     * the render pass ends.
     *
     * <p>Use this whenever subsequent render passes, sampling, or presentation need
     * the results produced by this pass (the common case for color and depth buffers
     * that are read later in the frame).
     *
     * <p>In OpenGL: no-op — the driver always preserves framebuffer contents.
     * In Vulkan: {@code VK_ATTACHMENT_STORE_OP_STORE}.
     */
    STORE,

    /**
     * The contents of the attachment are not needed after the render pass ends and
     * may be discarded by the implementation.
     *
     * <p>This can improve performance on tile-based GPU architectures because the
     * tile memory does not need to be flushed back to main memory. On desktop GPUs
     * it signals intent to the driver and validation layers.
     *
     * <p>Typical uses: a depth buffer that is not sampled after the pass (depth was
     * only used for hidden-surface removal within the pass), or intermediate scratch
     * render targets whose results will be overwritten in a subsequent clear pass.
     *
     * <p>In OpenGL: a no-op today. A future optimization pass may call
     * {@code glInvalidateFramebuffer()} to allow the driver to discard the attachment.
     * In Vulkan: {@code VK_ATTACHMENT_STORE_OP_DONT_CARE}.
     */
    DONT_CARE
}
