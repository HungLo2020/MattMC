package mattmc.util;

/**
 * Utility class for color manipulation operations.
 * Handles RGB color transformations, extractions, brightness adjustments, and tinting.
 * This contains pure math operations with no OpenGL dependencies.
 */
public final class ColorUtils {
    
    private ColorUtils() {} // Prevent instantiation
    
    /**
     * Extract red component from packed RGB integer.
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Red component (0-255)
     */
    public static int extractRed(int rgb) {
        return (rgb >> 16) & 0xFF;
    }
    
    /**
     * Extract green component from packed RGB integer.
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Green component (0-255)
     */
    public static int extractGreen(int rgb) {
        return (rgb >> 8) & 0xFF;
    }
    
    /**
     * Extract blue component from packed RGB integer.
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Blue component (0-255)
     */
    public static int extractBlue(int rgb) {
        return rgb & 0xFF;
    }
    
    /**
     * Convert packed RGB to normalized float array [r, g, b].
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Float array with values 0.0-1.0
     */
    public static float[] toNormalizedRGB(int rgb) {
        return new float[] {
            extractRed(rgb) / 255f,
            extractGreen(rgb) / 255f,
            extractBlue(rgb) / 255f
        };
    }
    
    /**
     * Convert packed RGB to normalized float array [r, g, b, a].
     * @param rgb Packed RGB color (0xRRGGBB)
     * @param alpha Alpha value (0.0-1.0)
     * @return Float array with values 0.0-1.0
     */
    public static float[] toNormalizedRGBA(int rgb, float alpha) {
        return new float[] {
            extractRed(rgb) / 255f,
            extractGreen(rgb) / 255f,
            extractBlue(rgb) / 255f,
            alpha
        };
    }
    
    /**
     * Pack separate RGB components into single integer.
     * @param r Red (0-255)
     * @param g Green (0-255)
     * @param b Blue (0-255)
     * @return Packed RGB integer
     */
    public static int packRGB(int r, int g, int b) {
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
    
    /**
     * Interpolate between two colors.
     * @param color1 First color
     * @param color2 Second color
     * @param t Interpolation factor (0.0-1.0, will be clamped)
     * @return Interpolated color
     */
    public static int lerp(int color1, int color2, float t) {
        // Clamp t to valid range
        t = MathUtils.clamp(t, 0.0f, 1.0f);
        
        // Extract components
        int r1 = extractRed(color1);
        int g1 = extractGreen(color1);
        int b1 = extractBlue(color1);
        
        int r2 = extractRed(color2);
        int g2 = extractGreen(color2);
        int b2 = extractBlue(color2);
        
        // Interpolate in integer space to avoid float conversions
        int r = r1 + (int)((r2 - r1) * t);
        int g = g1 + (int)((g2 - g1) * t);
        int b = b1 + (int)((b2 - b1) * t);
        
        return packRGB(r, g, b);
    }
    
    /**
     * Darken a color by reducing its brightness to 50%.
     * @param rgb RGB color value
     * @return Darkened color
     */
    public static int darkenColor(int rgb) {
        return adjustColorBrightness(rgb, 0.5f);
    }
    
    /**
     * Adjust the brightness of an RGB color.
     * @param rgb RGB color value
     * @param factor Brightness factor (0.0 to 1.0+). Negative values are treated as 0.
     * @return Adjusted color
     */
    public static int adjustColorBrightness(int rgb, float factor) {
        // Clamp factor to non-negative to avoid negative color values
        if (factor < 0.0f) factor = 0.0f;
        int r = MathUtils.min(255, (int)(extractRed(rgb) * factor));
        int g = MathUtils.min(255, (int)(extractGreen(rgb) * factor));
        int b = MathUtils.min(255, (int)(extractBlue(rgb) * factor));
        return packRGB(r, g, b);
    }
    
    /**
     * Apply a color tint to a base color.
     * Multiplies the base color by the tint color component-wise.
     * 
     * @param baseColor The base color (typically white 0xFFFFFF for textures)
     * @param tintColor The tint color to apply (e.g., 0x5BB53B for grass green)
     * @param brightnessFactor Additional brightness adjustment. Negative values are treated as 0.
     * @return The tinted color
     */
    public static int applyTint(int baseColor, int tintColor, float brightnessFactor) {
        // Clamp factor to non-negative to avoid negative color values
        if (brightnessFactor < 0.0f) brightnessFactor = 0.0f;
        
        // Extract RGB components from base and tint
        int baseR = extractRed(baseColor);
        int baseG = extractGreen(baseColor);
        int baseB = extractBlue(baseColor);
        
        int tintR = extractRed(tintColor);
        int tintG = extractGreen(tintColor);
        int tintB = extractBlue(tintColor);
        
        // Multiply components (treating them as 0-1 range)
        int r = MathUtils.min(255, (int)((baseR * tintR / 255.0f) * brightnessFactor));
        int g = MathUtils.min(255, (int)((baseG * tintG / 255.0f) * brightnessFactor));
        int b = MathUtils.min(255, (int)((baseB * tintB / 255.0f) * brightnessFactor));
        
        return packRGB(r, g, b);
    }
}
