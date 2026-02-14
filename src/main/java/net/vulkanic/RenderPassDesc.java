package net.vulkanic;

import java.util.ArrayList;
import java.util.List;

/**
 * Descriptor for creating render passes.
 * 
 * A render pass describes:
 * - Color attachments (render targets)
 * - Depth/stencil attachment (optional)
 * - Load/store operations for each attachment
 * - Clear values
 * 
 * Example:
 * <pre>
 * RenderPassDesc desc = new RenderPassDesc()
 *     .addColorAttachment(Format.RGBA8, LoadOp.CLEAR, StoreOp.STORE)
 *     .setDepthAttachment(Format.D24, LoadOp.CLEAR, StoreOp.DONT_CARE)
 *     .setClearColor(0.0f, 0.0f, 0.0f, 1.0f)
 *     .setClearDepth(1.0f);
 * </pre>
 */
public class RenderPassDesc {
    
    /**
     * Describes a single attachment in a render pass.
     */
    public static class AttachmentDesc {
        public final Format format;
        public final LoadOp loadOp;
        public final StoreOp storeOp;
        
        public AttachmentDesc(Format format, LoadOp loadOp, StoreOp storeOp) {
            this.format = format;
            this.loadOp = loadOp;
            this.storeOp = storeOp;
        }
    }
    
    private final List<AttachmentDesc> colorAttachments = new ArrayList<>();
    private AttachmentDesc depthAttachment;
    
    // Clear values
    private float clearR = 0.0f;
    private float clearG = 0.0f;
    private float clearB = 0.0f;
    private float clearA = 1.0f;
    private float clearDepth = 1.0f;
    private int clearStencil = 0;
    
    /**
     * Adds a color attachment to the render pass.
     * 
     * @param format Image format
     * @param loadOp Load operation (LOAD, CLEAR, DONT_CARE)
     * @param storeOp Store operation (STORE, DONT_CARE)
     * @return this for method chaining
     */
    public RenderPassDesc addColorAttachment(Format format, LoadOp loadOp, StoreOp storeOp) {
        colorAttachments.add(new AttachmentDesc(format, loadOp, storeOp));
        return this;
    }
    
    /**
     * Sets the depth/stencil attachment for the render pass.
     * 
     * @param format Depth format (D24, D32F, D24_S8, etc.)
     * @param loadOp Load operation
     * @param storeOp Store operation
     * @return this for method chaining
     */
    public RenderPassDesc setDepthAttachment(Format format, LoadOp loadOp, StoreOp storeOp) {
        this.depthAttachment = new AttachmentDesc(format, loadOp, storeOp);
        return this;
    }
    
    /**
     * Sets the clear color value (used when loadOp is CLEAR).
     * 
     * @param r Red component (0.0 - 1.0)
     * @param g Green component (0.0 - 1.0)
     * @param b Blue component (0.0 - 1.0)
     * @param a Alpha component (0.0 - 1.0)
     * @return this for method chaining
     */
    public RenderPassDesc setClearColor(float r, float g, float b, float a) {
        this.clearR = r;
        this.clearG = g;
        this.clearB = b;
        this.clearA = a;
        return this;
    }
    
    /**
     * Sets the clear depth value (used when loadOp is CLEAR).
     * 
     * @param depth Depth value (typically 1.0)
     * @return this for method chaining
     */
    public RenderPassDesc setClearDepth(float depth) {
        this.clearDepth = depth;
        return this;
    }
    
    /**
     * Sets the clear stencil value (used when loadOp is CLEAR).
     * 
     * @param stencil Stencil value (typically 0)
     * @return this for method chaining
     */
    public RenderPassDesc setClearStencil(int stencil) {
        this.clearStencil = stencil;
        return this;
    }
    
    // Getters
    public List<AttachmentDesc> getColorAttachments() { return colorAttachments; }
    public AttachmentDesc getDepthAttachment() { return depthAttachment; }
    public float getClearR() { return clearR; }
    public float getClearG() { return clearG; }
    public float getClearB() { return clearB; }
    public float getClearA() { return clearA; }
    public float getClearDepth() { return clearDepth; }
    public int getClearStencil() { return clearStencil; }
    
    public boolean hasClearColor() {
        return colorAttachments.stream().anyMatch(a -> a.loadOp == LoadOp.CLEAR);
    }
    
    public boolean hasClearDepth() {
        return depthAttachment != null && depthAttachment.loadOp == LoadOp.CLEAR;
    }
}
