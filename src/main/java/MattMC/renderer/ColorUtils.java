package MattMC.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Utility class for color manipulation operations in rendering.
 * Handles RGB color transformations, brightness adjustments, and tinting.
 */
public final class ColorUtils {
    
    private ColorUtils() {} // Prevent instantiation
    
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
     * @param factor Brightness factor (0.0 to 1.0+)
     * @return Adjusted color
     */
    public static int adjustColorBrightness(int rgb, float factor) {
        int r = Math.min(255, (int)(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int)(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int)((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Apply a color tint to a base color.
     * Multiplies the base color by the tint color component-wise.
     * 
     * @param baseColor The base color (typically white 0xFFFFFF for textures)
     * @param tintColor The tint color to apply (e.g., 0x5BB53B for grass green)
     * @param brightnessFactor Additional brightness adjustment
     * @return The tinted color
     */
    public static int applyTint(int baseColor, int tintColor, float brightnessFactor) {
        // Extract RGB components from base and tint
        int baseR = (baseColor >> 16) & 0xFF;
        int baseG = (baseColor >> 8) & 0xFF;
        int baseB = baseColor & 0xFF;
        
        int tintR = (tintColor >> 16) & 0xFF;
        int tintG = (tintColor >> 8) & 0xFF;
        int tintB = tintColor & 0xFF;
        
        // Multiply components (treating them as 0-1 range)
        int r = Math.min(255, (int)((baseR * tintR / 255.0f) * brightnessFactor));
        int g = Math.min(255, (int)((baseG * tintG / 255.0f) * brightnessFactor));
        int b = Math.min(255, (int)((baseB * tintB / 255.0f) * brightnessFactor));
        
        return (r << 16) | (g << 8) | b;
    }
    
    /**
     * Set the current OpenGL color from an RGB value and alpha.
     * @param rgb RGB color value
     * @param a Alpha value (0.0 to 1.0)
     */
    public static void setGLColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }
}
