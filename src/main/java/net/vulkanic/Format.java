package net.vulkanic;

/**
 * Defines texture/image formats.
 * 
 * Backend-agnostic enum for specifying texture/image formats.
 */
public enum Format {
    // Color formats
    /**
     * 8-bit RGBA (32 bits per pixel)
     */
    RGBA8,
    
    /**
     * 8-bit RGBA sRGB
     */
    RGBA8_SRGB,
    
    /**
     * 16-bit float RGBA (64 bits per pixel)
     */
    RGBA16F,
    
    /**
     * 32-bit float RGBA (128 bits per pixel)
     */
    RGBA32F,
    
    /**
     * 8-bit RGB (24 bits per pixel)
     */
    RGB8,
    
    /**
     * 8-bit RG (16 bits per pixel)
     */
    RG8,
    
    /**
     * 8-bit single channel (8 bits per pixel)
     */
    R8,
    
    // Depth/stencil formats
    /**
     * 24-bit depth
     */
    D24,
    
    /**
     * 32-bit float depth
     */
    D32F,
    
    /**
     * 24-bit depth + 8-bit stencil
     */
    D24_S8,
    
    /**
     * 32-bit float depth + 8-bit stencil
     */
    D32F_S8
}
