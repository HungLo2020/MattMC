package mattmc.client.renderer.backend.opengl;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * OpenGL-specific system information utilities.
 * This class contains methods that require an active OpenGL context or GLFW.
 * For pure system information (CPU, memory, Java), see {@link mattmc.client.util.SystemInfo}.
 */
public class OpenGLSystemInfo {
    
    /**
     * Get the display resolution.
     * @param windowHandle GLFW window handle
     * @return Display resolution string (e.g., "1920x1080")
     */
    public static String getDisplayResolution(long windowHandle) {
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowSize(windowHandle, width, height);
        return width[0] + "x" + height[0];
    }
    
    /**
     * Get the graphics card name.
     * Requires an active OpenGL context.
     * @return Graphics card name
     */
    public static String getGPUName() {
        try {
            // Check if we're in a headless environment
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                return "Unknown (Headless)";
            }
            String renderer = GL11.glGetString(GL11.GL_RENDERER);
            return renderer != null ? renderer : "Unknown";
        } catch (Exception | Error e) {
            return "Unknown";
        }
    }
    
    /**
     * Get GPU usage percentage.
     * Note: This is not reliably available through standard Java/LWJGL APIs.
     * @return GPU usage percentage or -1 if not available
     */
    public static int getGPUUsage() {
        // GPU usage is not available through standard Java/LWJGL APIs
        // This would require platform-specific native code or third-party libraries
        return -1;
    }
    
    /**
     * Get GPU VRAM usage.
     * Note: This is not reliably available through standard Java/LWJGL APIs.
     * @return GPU VRAM usage string or "N/A" if not available
     */
    public static String getGPUVRAMUsage() {
        // VRAM usage is not available through standard Java/LWJGL APIs
        // This would require platform-specific native code or extensions
        return "N/A";
    }
}
