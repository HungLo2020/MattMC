package net.vulkanic;

/**
 * Defines blend modes for color blending.
 * 
 * Backend-agnostic enum that replaces GL_BLEND enable/disable
 * and provides common blend configurations.
 */
public enum BlendMode {
    /**
     * No blending - source overwrites destination
     */
    NONE,
    
    /**
     * Alpha blending - standard transparency
     * src_alpha * src_color + (1 - src_alpha) * dst_color
     */
    ALPHA_BLEND,
    
    /**
     * Additive blending - adds colors together
     * src_color + dst_color
     */
    ADDITIVE,
    
    /**
     * Multiplicative blending
     * src_color * dst_color
     */
    MULTIPLY,
    
    /**
     * Premultiplied alpha blending
     * src_color + (1 - src_alpha) * dst_color
     */
    PREMULTIPLIED_ALPHA
}
