package net.vulkanic;

/**
 * Defines buffer usage patterns.
 * 
 * Backend-agnostic enum that specifies how a buffer will be used.
 */
public enum BufferUsage {
    /**
     * Buffer contains vertex data
     */
    VERTEX,
    
    /**
     * Buffer contains index data
     */
    INDEX,
    
    /**
     * Buffer contains uniform/constant data
     */
    UNIFORM,
    
    /**
     * Buffer contains storage/shader storage data
     */
    STORAGE,
    
    /**
     * Buffer is used as a transfer source
     */
    TRANSFER_SRC,
    
    /**
     * Buffer is used as a transfer destination
     */
    TRANSFER_DST
}
