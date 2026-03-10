package net.vulkanic;

import java.util.Objects;

/**
 * Descriptor for a color attachment in a {@link VulkanicRenderPass}.
 *
 * <p>Carries a texture view together with explicit load and store operations,
 * replacing the {@code (VulkanicTextureView, OptionalInt)} pair used by the legacy
 * {@link GraphicsBackend#beginRenderPass} overloads. The new attachment-descriptor
 * overloads accept this class directly:
 *
 * <pre>
 * // Clear to black, preserve after pass (typical first-frame path)
 * RenderPassColorAttachment color = RenderPassColorAttachment.clear(colorView, 0xFF000000);
 *
 * // Load existing contents (typical subsequent-pass path)
 * RenderPassColorAttachment color = RenderPassColorAttachment.load(colorView);
 *
 * // Don't care about initial contents (full-screen effect that overwrites everything)
 * RenderPassColorAttachment color = RenderPassColorAttachment.dontCareLoad(colorView);
 *
 * CommandContext ctx = VulkanicAPI.beginCommandBuffer();
 * try (VulkanicRenderPass pass = VulkanicAPI.beginRenderPass(ctx, () -&gt; "my-pass", color)) {
 *     pass.setPipeline(pipeline);
 *     pass.draw(0, 36);
 * }
 * VulkanicAPI.submitCommandBuffer(ctx);
 * </pre>
 *
 * <p>In Vulkan this maps to a {@code VkAttachmentDescription} whose
 * {@code loadOp}/{@code storeOp} fields match {@link #loadOp}/{@link #storeOp}.
 * The {@link #clearColor} field is placed in {@code VkClearValue} and is only
 * consulted when {@link #loadOp} is {@link AttachmentLoadOp#CLEAR}.
 *
 * @see RenderPassDepthAttachment
 * @see AttachmentLoadOp
 * @see AttachmentStoreOp
 */
public final class RenderPassColorAttachment {

    /** The texture view to use as the color attachment. */
    public final VulkanicTextureView view;

    /** What to do with the attachment contents at the start of the render pass. */
    public final AttachmentLoadOp loadOp;

    /** What to do with the attachment contents at the end of the render pass. */
    public final AttachmentStoreOp storeOp;

    /**
     * Packed ARGB clear color ({@code 0xAARRGGBB}).
     * Only meaningful when {@link #loadOp} is {@link AttachmentLoadOp#CLEAR}.
     */
    public final int clearColor;

    /**
     * Creates a color attachment descriptor with explicit load/store operations.
     *
     * @param view       the texture view for this attachment (must not be null)
     * @param loadOp     load operation at the start of the render pass (must not be null)
     * @param storeOp    store operation at the end of the render pass (must not be null)
     * @param clearColor packed ARGB clear color (only used when loadOp == CLEAR)
     */
    public RenderPassColorAttachment(VulkanicTextureView view,
                                     AttachmentLoadOp loadOp,
                                     AttachmentStoreOp storeOp,
                                     int clearColor) {
        this.view = Objects.requireNonNull(view, "view must not be null");
        this.loadOp = Objects.requireNonNull(loadOp, "loadOp must not be null");
        this.storeOp = Objects.requireNonNull(storeOp, "storeOp must not be null");
        this.clearColor = clearColor;
    }

    /**
     * Creates a "load existing contents, store result" attachment descriptor.
     *
     * <p>Use for passes that read results from a previous pass or the current
     * framebuffer state and must preserve the rendered output.
     *
     * @param view the color texture view (must not be null)
     * @return a descriptor with {@code loadOp=LOAD, storeOp=STORE}
     */
    public static RenderPassColorAttachment load(VulkanicTextureView view) {
        return new RenderPassColorAttachment(view,
            AttachmentLoadOp.LOAD, AttachmentStoreOp.STORE, 0);
    }

    /**
     * Creates a "clear to ARGB color, store result" attachment descriptor.
     *
     * <p>Use for the first pass in a frame that needs to initialize the color buffer
     * (e.g. clear to sky color before rendering the scene).
     *
     * @param view      the color texture view (must not be null)
     * @param argbColor packed ARGB clear color ({@code 0xAARRGGBB})
     * @return a descriptor with {@code loadOp=CLEAR, storeOp=STORE}
     */
    public static RenderPassColorAttachment clear(VulkanicTextureView view, int argbColor) {
        return new RenderPassColorAttachment(view,
            AttachmentLoadOp.CLEAR, AttachmentStoreOp.STORE, argbColor);
    }

    /**
     * Creates a "don't care about initial contents, store result" attachment descriptor.
     *
     * <p>Use when the pass will write every pixel of the attachment (e.g. a full-screen
     * post-process or blit), making the initial contents irrelevant. This can improve
     * performance on tile-based GPUs by skipping the load from main memory.
     *
     * @param view the color texture view (must not be null)
     * @return a descriptor with {@code loadOp=DONT_CARE, storeOp=STORE}
     */
    public static RenderPassColorAttachment dontCareLoad(VulkanicTextureView view) {
        return new RenderPassColorAttachment(view,
            AttachmentLoadOp.DONT_CARE, AttachmentStoreOp.STORE, 0);
    }

    /**
     * Creates a "clear to ARGB color, discard result" attachment descriptor.
     *
     * <p>Uncommon — only use when the rendered output is truly not needed after
     * the pass (e.g. a transient scratch buffer used only for intermediate
     * calculations within a single pass). Prefer {@link #clear(VulkanicTextureView, int)}
     * if the results might ever be read.
     *
     * @param view      the color texture view (must not be null)
     * @param argbColor packed ARGB clear color ({@code 0xAARRGGBB})
     * @return a descriptor with {@code loadOp=CLEAR, storeOp=DONT_CARE}
     */
    public static RenderPassColorAttachment clearTransient(VulkanicTextureView view, int argbColor) {
        return new RenderPassColorAttachment(view,
            AttachmentLoadOp.CLEAR, AttachmentStoreOp.DONT_CARE, argbColor);
    }

    @Override
    public String toString() {
        return "RenderPassColorAttachment{view=" + view
            + ", loadOp=" + loadOp
            + ", storeOp=" + storeOp
            + ", clearColor=0x" + Integer.toHexString(clearColor) + "}";
    }
}
