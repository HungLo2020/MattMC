package net.vulkanic;

/**
 * Defines store operations for render pass attachments.
 * 
 * Specifies what to do with attachment contents at the end of a render pass.
 */
public enum StoreOp {
    /**
     * Store contents to memory
     */
    STORE,
    
    /**
     * Don't care about contents (can be discarded)
     */
    DONT_CARE
}
