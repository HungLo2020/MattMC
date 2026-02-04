package net.vulkanic;

/**
 * Enumeration of available rendering backends.
 * 
 * @see Vulkanic
 */
public enum BackendType {
    /**
     * OpenGL backend - compatible with all systems, uses existing Blaze3D infrastructure.
     */
    OPENGL("OpenGL"),
    
    /**
     * Vulkan backend - modern API for improved performance (future implementation).
     */
    VULKAN("Vulkan");
    
    private final String displayName;
    
    BackendType(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Gets the human-readable display name for this backend.
     * 
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the default backend type for the current system.
     * 
     * @return the default backend type
     */
    public static BackendType getDefault() {
        // For now, always use OpenGL as the default
        // In the future, this could detect Vulkan support and prefer it
        return OPENGL;
    }
}
