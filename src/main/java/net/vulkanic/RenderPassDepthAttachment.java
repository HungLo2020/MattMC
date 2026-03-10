package net.vulkanic;

import java.util.Objects;

/**
 * Descriptor for a depth attachment in a {@link VulkanicRenderPass}.
 *
 * <p>Carries a texture view together with explicit load and store operations for
 * the depth buffer, replacing the {@code (VulkanicTextureView, OptionalDouble)} pair
 * used by the legacy {@link GraphicsBackend#beginRenderPass} overloads. Use with
 * {@link RenderPassColorAttachment} in the attachment-descriptor overloads:
 *
 * <pre>
 * RenderPassColorAttachment color = RenderPassColorAttachment.clear(colorView, 0xFF000000);
 * RenderPassDepthAttachment depth = RenderPassDepthAttachment.clear(depthView);   // clears to 1.0
 *
 * CommandContext ctx = VulkanicAPI.beginCommandBuffer();
 * try (VulkanicRenderPass pass = VulkanicAPI.beginRenderPass(ctx, () -&gt; "scene", color, depth)) {
 *     pass.setPipeline(pipeline);
 *     pass.setVertexBuffer(0, vbo);
 *     pass.drawIndexed(0, indexCount, 0, 1);
 * }
 * VulkanicAPI.submitCommandBuffer(ctx);
 * </pre>
 *
 * <p>In Vulkan this maps to a {@code VkAttachmentDescription} for the depth attachment.
 * The {@link #clearDepth} field is placed in the depth component of {@code VkClearValue}
 * and is only consulted when {@link #loadOp} is {@link AttachmentLoadOp#CLEAR}.
 *
 * @see RenderPassColorAttachment
 * @see AttachmentLoadOp
 * @see AttachmentStoreOp
 */
public final class RenderPassDepthAttachment {

    /** The texture view to use as the depth attachment. */
    public final VulkanicTextureView view;

    /** What to do with the attachment contents at the start of the render pass. */
    public final AttachmentLoadOp loadOp;

    /** What to do with the attachment contents at the end of the render pass. */
    public final AttachmentStoreOp storeOp;

    /**
     * Depth clear value in the range {@code [0.0, 1.0]}.
     * Only meaningful when {@link #loadOp} is {@link AttachmentLoadOp#CLEAR}.
     * {@code 1.0} represents the far plane (far-away geometry); {@code 0.0} the near plane.
     */
    public final double clearDepth;

    /**
     * Creates a depth attachment descriptor with explicit load/store operations.
     *
     * @param view       the texture view for this attachment (must not be null)
     * @param loadOp     load operation at the start of the render pass (must not be null)
     * @param storeOp    store operation at the end of the render pass (must not be null)
     * @param clearDepth depth clear value in {@code [0.0, 1.0]} (only used when loadOp == CLEAR)
     */
    public RenderPassDepthAttachment(VulkanicTextureView view,
                                     AttachmentLoadOp loadOp,
                                     AttachmentStoreOp storeOp,
                                     double clearDepth) {
        this.view = Objects.requireNonNull(view, "view must not be null");
        this.loadOp = Objects.requireNonNull(loadOp, "loadOp must not be null");
        this.storeOp = Objects.requireNonNull(storeOp, "storeOp must not be null");
        this.clearDepth = clearDepth;
    }

    /**
     * Creates a "load existing depth contents, store result" attachment descriptor.
     *
     * <p>Use for passes that continue rendering with an existing depth buffer (e.g.
     * transparent objects rendered after an opaque pass that already populated depth).
     *
     * @param view the depth texture view (must not be null)
     * @return a descriptor with {@code loadOp=LOAD, storeOp=STORE, clearDepth=1.0}
     */
    public static RenderPassDepthAttachment load(VulkanicTextureView view) {
        return new RenderPassDepthAttachment(view,
            AttachmentLoadOp.LOAD, AttachmentStoreOp.STORE, 1.0);
    }

    /**
     * Creates a "clear depth to {@code 1.0}, store result" attachment descriptor.
     *
     * <p>This is the most common depth attachment setup for an initial render pass.
     * Clearing to {@code 1.0} initializes the depth buffer to the farthest possible value,
     * so all geometry passes the depth test on the first draw.
     *
     * @param view the depth texture view (must not be null)
     * @return a descriptor with {@code loadOp=CLEAR, storeOp=STORE, clearDepth=1.0}
     */
    public static RenderPassDepthAttachment clear(VulkanicTextureView view) {
        return new RenderPassDepthAttachment(view,
            AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE, 1.0);
    }

    /**
     * Creates a "clear depth to a specific value, store result" attachment descriptor.
     *
     * @param view       the depth texture view (must not be null)
     * @param clearDepth depth value to clear to; {@code 1.0} = far plane, {@code 0.0} = near plane
     * @return a descriptor with {@code loadOp=CLEAR, storeOp=STORE}
     */
    public static RenderPassDepthAttachment clear(VulkanicTextureView view, double clearDepth) {
        return new RenderPassDepthAttachment(view,
            AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE, clearDepth);
    }

    /**
     * Creates a "don't care about initial depth, store result" attachment descriptor.
     *
     * <p>Use when the pass will write every depth value before any depth test occurs
     * (e.g. a shadow map pass that completely repopulates the depth buffer).
     *
     * @param view the depth texture view (must not be null)
     * @return a descriptor with {@code loadOp=DONT_CARE, storeOp=STORE, clearDepth=1.0}
     */
    public static RenderPassDepthAttachment dontCareLoad(VulkanicTextureView view) {
        return new RenderPassDepthAttachment(view,
            AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.STORE, 1.0);
    }

    /**
     * Creates a "clear depth to {@code 1.0}, discard result" attachment descriptor.
     *
     * <p>Use when depth is only needed for hidden-surface removal within this pass and
     * the depth values are not read by any subsequent pass. The DONT_CARE store op
     * allows tile-based GPUs to skip writing depth back to main memory.
     *
     * @param view the depth texture view (must not be null)
     * @return a descriptor with {@code loadOp=CLEAR, storeOp=DONT_CARE, clearDepth=1.0}
     */
    public static RenderPassDepthAttachment clearTransient(VulkanicTextureView view) {
        return new RenderPassDepthAttachment(view,
            AttachmentLoadOp.CLEAR, AttachmentStoreOp.DONT_CARE, 1.0);
    }

    @Override
    public String toString() {
        return "RenderPassDepthAttachment{view=" + view
            + ", loadOp=" + loadOp
            + ", storeOp=" + storeOp
            + ", clearDepth=" + clearDepth + "}";
    }
}
