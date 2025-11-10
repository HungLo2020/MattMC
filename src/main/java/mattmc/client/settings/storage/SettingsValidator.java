package mattmc.client.settings.storage;

/**
 * Validates setting values to ensure they're within acceptable ranges.
 */
public class SettingsValidator {
    
    /**
     * Validate and clamp FPS cap value.
     */
    public int validateFpsCap(int fps) {
        if (fps <= 0) return 0; // Unlimited
        return Math.max(30, Math.min(fps, 300)); // 30-300 FPS range
    }
    
    /**
     * Validate and clamp render distance.
     */
    public int validateRenderDistance(int distance) {
        return Math.max(2, Math.min(distance, 32)); // 2-32 chunks
    }
    
    /**
     * Validate and clamp mipmap level.
     */
    public int validateMipmapLevel(int level) {
        return Math.max(0, Math.min(level, 4)); // 0-4 levels
    }
    
    /**
     * Validate and clamp anisotropic filtering level.
     */
    public int validateAnisotropicLevel(int level) {
        if (level <= 0) return 0; // Disabled
        // Power of 2: 2, 4, 8, 16
        if (level <= 2) return 2;
        if (level <= 4) return 4;
        if (level <= 8) return 8;
        return 16; // Max 16x
    }
    
    /**
     * Validate resolution dimensions.
     */
    public Resolution validateResolution(int width, int height) {
        int validWidth = Math.max(640, Math.min(width, 7680)); // 640-7680 (8K)
        int validHeight = Math.max(480, Math.min(height, 4320)); // 480-4320 (8K)
        return new Resolution(validWidth, validHeight);
    }
    
    /**
     * Represents a screen resolution.
     */
    public static class Resolution {
        public final int width;
        public final int height;
        
        public Resolution(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
