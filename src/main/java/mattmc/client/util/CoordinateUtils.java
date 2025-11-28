package mattmc.client.util;

import org.lwjgl.system.MemoryStack;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Utility class for coordinate system conversions in windowing contexts.
 * Handles conversions between window coordinates and framebuffer coordinates,
 * which is essential for HiDPI (Retina) display support.
 */
public final class CoordinateUtils {
    
    private CoordinateUtils() {} // Prevent instantiation
    
    /**
     * Represents a 2D coordinate pair.
     */
    public static class Point2D {
        public final float x;
        public final float y;
        
        public Point2D(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
    
    /**
     * Represents scaling factors for coordinate conversion.
     */
    public static class ScaleFactors {
        public final float scaleX;
        public final float scaleY;
        
        public ScaleFactors(float scaleX, float scaleY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }
    }
    
    /**
     * Get the scale factors between window coordinates and framebuffer coordinates.
     * This is necessary for HiDPI displays where framebuffer may be larger than window.
     * 
     * @param windowHandle GLFW window handle
     * @return Scale factors for X and Y axes
     */
    public static ScaleFactors getFramebufferScale(long windowHandle) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1),  fbH  = stack.mallocInt(1);
            
            glfwGetWindowSize(windowHandle, winW, winH);
            glfwGetFramebufferSize(windowHandle, fbW, fbH);
            
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            
            return new ScaleFactors(sx, sy);
        }
    }
    
    /**
     * Convert window coordinates to framebuffer coordinates.
     * Essential for accurate mouse hit-testing on HiDPI displays.
     * 
     * @param windowHandle GLFW window handle
     * @param windowX Window X coordinate
     * @param windowY Window Y coordinate
     * @return Framebuffer coordinates
     */
    public static Point2D windowToFramebuffer(long windowHandle, double windowX, double windowY) {
        ScaleFactors scale = getFramebufferScale(windowHandle);
        return new Point2D(
            (float) windowX * scale.scaleX,
            (float) windowY * scale.scaleY
        );
    }
    
    /**
     * Convert framebuffer coordinates to window coordinates.
     * 
     * @param windowHandle GLFW window handle
     * @param framebufferX Framebuffer X coordinate
     * @param framebufferY Framebuffer Y coordinate
     * @return Window coordinates
     */
    public static Point2D framebufferToWindow(long windowHandle, float framebufferX, float framebufferY) {
        ScaleFactors scale = getFramebufferScale(windowHandle);
        // Defensive check: protect against edge cases where scale might be zero or very small
        // (e.g., minimized windows, unusual display configurations, or GLFW returning invalid values)
        float scaleX = Math.max(0.001f, scale.scaleX);
        float scaleY = Math.max(0.001f, scale.scaleY);
        return new Point2D(
            framebufferX / scaleX,
            framebufferY / scaleY
        );
    }
}
