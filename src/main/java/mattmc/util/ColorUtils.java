package mattmc.util;

/**
 * Utility class for color manipulation operations.
 * Handles RGB color transformations, extractions, brightness adjustments, and tinting.
 * This contains pure math operations with no OpenGL dependencies.
 */
public final class ColorUtils {
    
    /** Mask for extracting a single color channel (8 bits). */
    private static final int CHANNEL_MASK = 0xFF;
    
    /** Bit shift for the red channel in a packed RGB integer. */
    private static final int RED_SHIFT = 16;
    
    /** Bit shift for the green channel in a packed RGB integer. */
    private static final int GREEN_SHIFT = 8;
    
    /** Maximum value for a color channel (255). */
    public static final int MAX_CHANNEL_VALUE = 255;
    
    /** Normalization divisor for converting byte color (0-255) to float (0.0-1.0). */
    private static final float NORMALIZE_DIVISOR = 255f;
    
    private ColorUtils() {} // Prevent instantiation
    
    /**
     * Extract red component from packed RGB integer.
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Red component (0-255)
     */
    public static int extractRed(int rgb) {
        return (rgb >> RED_SHIFT) & CHANNEL_MASK;
    }
    
    /**
     * Extract green component from packed RGB integer.
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Green component (0-255)
     */
    public static int extractGreen(int rgb) {
        return (rgb >> GREEN_SHIFT) & CHANNEL_MASK;
    }
    
    /**
     * Extract blue component from packed RGB integer.
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Blue component (0-255)
     */
    public static int extractBlue(int rgb) {
        return rgb & CHANNEL_MASK;
    }
    
    /**
     * Convert packed RGB to normalized float array [r, g, b].
     * @param rgb Packed RGB color (0xRRGGBB)
     * @return Float array with values 0.0-1.0
     */
    public static float[] toNormalizedRGB(int rgb) {
        return new float[] {
            extractRed(rgb) / NORMALIZE_DIVISOR,
            extractGreen(rgb) / NORMALIZE_DIVISOR,
            extractBlue(rgb) / NORMALIZE_DIVISOR
        };
    }
    
    /**
     * Convert packed RGB to normalized float values and store in output array.
     * PERFORMANCE FIX #7: Eliminates array allocation by using output parameter.
     * 
     * @param rgb Packed RGB color (0xRRGGBB)
     * @param out Output array of at least length 3 to store [r, g, b]
     */
    public static void toNormalizedRGB(int rgb, float[] out) {
        out[0] = extractRed(rgb) / NORMALIZE_DIVISOR;
        out[1] = extractGreen(rgb) / NORMALIZE_DIVISOR;
        out[2] = extractBlue(rgb) / NORMALIZE_DIVISOR;
    }
    
    /**
     * Convert packed RGB to normalized float array [r, g, b, a].
     * @param rgb Packed RGB color (0xRRGGBB)
     * @param alpha Alpha value (0.0-1.0)
     * @return Float array with values 0.0-1.0
     */
    public static float[] toNormalizedRGBA(int rgb, float alpha) {
        return new float[] {
            extractRed(rgb) / NORMALIZE_DIVISOR,
            extractGreen(rgb) / NORMALIZE_DIVISOR,
            extractBlue(rgb) / NORMALIZE_DIVISOR,
            alpha
        };
    }
    
    /**
     * Convert packed RGB to normalized float values with alpha and store in output array.
     * PERFORMANCE FIX #7: Eliminates array allocation by using output parameter.
     * 
     * @param rgb Packed RGB color (0xRRGGBB)
     * @param alpha Alpha value (0.0-1.0)
     * @param out Output array of at least length 4 to store [r, g, b, a]
     */
    public static void toNormalizedRGBA(int rgb, float alpha, float[] out) {
        out[0] = extractRed(rgb) / NORMALIZE_DIVISOR;
        out[1] = extractGreen(rgb) / NORMALIZE_DIVISOR;
        out[2] = extractBlue(rgb) / NORMALIZE_DIVISOR;
        out[3] = alpha;
    }
    
    /**
     * Pack separate RGB components into single integer.
     * @param r Red (0-255)
     * @param g Green (0-255)
     * @param b Blue (0-255)
     * @return Packed RGB integer
     */
    public static int packRGB(int r, int g, int b) {
        return ((r & CHANNEL_MASK) << RED_SHIFT) | ((g & CHANNEL_MASK) << GREEN_SHIFT) | (b & CHANNEL_MASK);
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
        int r = MathUtils.min(MAX_CHANNEL_VALUE, (int)(extractRed(rgb) * factor));
        int g = MathUtils.min(MAX_CHANNEL_VALUE, (int)(extractGreen(rgb) * factor));
        int b = MathUtils.min(MAX_CHANNEL_VALUE, (int)(extractBlue(rgb) * factor));
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
        int r = MathUtils.min(MAX_CHANNEL_VALUE, (int)((baseR * tintR / NORMALIZE_DIVISOR) * brightnessFactor));
        int g = MathUtils.min(MAX_CHANNEL_VALUE, (int)((baseG * tintG / NORMALIZE_DIVISOR) * brightnessFactor));
        int b = MathUtils.min(MAX_CHANNEL_VALUE, (int)((baseB * tintB / NORMALIZE_DIVISOR) * brightnessFactor));
        
        return packRGB(r, g, b);
    }
}
