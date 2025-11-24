package mattmc.client.renderer.backend.opengl;

import mattmc.util.ColorUtils;
import static org.lwjgl.opengl.GL11.*;

/**
 * OpenGL-specific color helper utilities.
 * This class contains only OpenGL-dependent color operations.
 * For pure color math operations, see {@link mattmc.util.ColorUtils}.
 */
public final class OpenGLColorHelper {
    
    private OpenGLColorHelper() {} // Prevent instantiation
    
    /**
     * Set the current OpenGL color from an RGB value and alpha.
     * @param rgb RGB color value
     * @param a Alpha value (0.0 to 1.0)
     */
    public static void setGLColor(int rgb, float a) {
        float r = ColorUtils.extractRed(rgb) / 255f;
        float g = ColorUtils.extractGreen(rgb) / 255f;
        float b = ColorUtils.extractBlue(rgb) / 255f;
        glColor4f(r, g, b, a);
    }
}
