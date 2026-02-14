package net.vulkanic;

/**
 * Defines load operations for render pass attachments.
 * 
 * Specifies what to do with attachment contents at the start of a render pass.
 */
public enum LoadOp {
    /**
     * Load existing contents from memory
     */
    LOAD,
    
    /**
     * Clear contents to a specified value
     */
    CLEAR,
    
    /**
     * Don't care about existing contents (undefined)
     */
    DONT_CARE
}
