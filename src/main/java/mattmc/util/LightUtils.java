package mattmc.util;

/**
 * Utility class for light-related calculations.
 * 
 * ISSUE-002 fix: Consolidates duplicate RGB scaling logic from
 * VertexLightSampler and AsyncChunkLoader into a single location.
 */
public final class LightUtils {
    
    private LightUtils() {} // Prevent instantiation
    
    /**
     * Scale RGB light values by intensity ratio for proper attenuation.
     * 
     * During light propagation, RGB values stay constant but intensity decrements.
     * This method scales RGB to match the current intensity level.
     * 
     * @param r Red channel (0-15)
     * @param g Green channel (0-15)
     * @param b Blue channel (0-15)
     * @param intensity Current intensity level (0-15)
     * @param result Pre-allocated int[3] array to store results [scaledR, scaledG, scaledB]
     */
    public static void scaleRGBByIntensity(int r, int g, int b, int intensity, int[] result) {
        // If no light, return zeros
        if (intensity == 0) {
            result[0] = 0;
            result[1] = 0;
            result[2] = 0;
            return;
        }
        
        int maxRGB = Math.max(r, Math.max(g, b));
        
        // If maxRGB is 0 but intensity is not, use intensity as white light
        if (maxRGB == 0) {
            result[0] = intensity;
            result[1] = intensity;
            result[2] = intensity;
            return;
        }
        
        // Scale RGB by the intensity ratio
        float scale = (float) intensity / maxRGB;
        result[0] = Math.round(r * scale);
        result[1] = Math.round(g * scale);
        result[2] = Math.round(b * scale);
    }
    
    /**
     * Scale RGB light values by intensity ratio, returning a new array.
     * 
     * <p><b>Note:</b> For performance-critical code, prefer the version that takes a 
     * pre-allocated array to avoid allocation overhead. This method allocates a new
     * array on each call, which creates GC pressure in hot paths.
     * 
     * @param r Red channel (0-15)
     * @param g Green channel (0-15)
     * @param b Blue channel (0-15)
     * @param intensity Current intensity level (0-15)
     * @return int[3] array with [scaledR, scaledG, scaledB]
     */
    public static int[] scaleRGBByIntensity(int r, int g, int b, int intensity) {
        int[] result = new int[3];
        scaleRGBByIntensity(r, g, b, intensity, result);
        return result;
    }
}
