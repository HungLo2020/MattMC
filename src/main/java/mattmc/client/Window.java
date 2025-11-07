package mattmc.client;

import mattmc.client.settings.OptionsManager;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Window implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Window.class);

    private final long handle;
    private int width, height;
    private int windowedWidth, windowedHeight;  // Store windowed mode dimensions
    private int windowedPosX, windowedPosY;     // Store windowed mode position

    public Window(int w, int h, String title) {
        this.width = w; this.height = h;
        this.windowedWidth = w; this.windowedHeight = h;

        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);

        // OpenGL 3.2 compatibility profile - supports both modern features (VAOs, texture arrays)
        // and legacy fixed-function pipeline for gradual migration
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);

        handle = glfwCreateWindow(w, h, title, 0, 0);
        if (handle == 0) throw new IllegalStateException("Window creation failed");

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        
        // Configure swap interval based on FPS cap setting
        applyFpsCapSetting();

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        setOrtho2D();

        glfwSetFramebufferSizeCallback(handle, (win, newW, newH) -> {
            width  = Math.max(newW, 1);
            height = Math.max(newH, 1);
            glViewport(0, 0, width, height);
            setOrtho2D();
        });
        
        // Apply fullscreen setting from OptionsManager
        if (OptionsManager.isFullscreenEnabled()) {
            setFullscreen(true);
        }
    }

    private void setOrtho2D() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1); // (0,0) top-left
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    public long handle() { return handle; }
    public int width() { return width; }
    public int height() { return height; }

    public boolean shouldClose() {
        glfwPollEvents();
        return glfwWindowShouldClose(handle);
    }

    public void swap() { glfwSwapBuffers(handle); }
    
    /**
     * Change the window size to a new resolution.
     * This will trigger the framebuffer size callback and recompute layouts.
     */
    public void setSize(int newWidth, int newHeight) {
        glfwSetWindowSize(handle, newWidth, newHeight);
        this.width = newWidth;
        this.height = newHeight;
    }
    
    /**
     * Apply the FPS cap setting from OptionsManager.
     * VSync is disabled to allow manual FPS control.
     */
    public void applyFpsCapSetting() {
        // Disable VSync for manual FPS control
        glfwSwapInterval(0);
    }
    
    /**
     * Toggle fullscreen mode on/off.
     * @param fullscreen true for fullscreen, false for windowed
     */
    public void setFullscreen(boolean fullscreen) {
        long monitor = glfwGetPrimaryMonitor();
        if (monitor == 0) {
            logger.error("Failed to get primary monitor");
            return;
        }
        
        if (fullscreen) {
            // Store current windowed position and size before going fullscreen
            int[] xPos = new int[1], yPos = new int[1];
            int[] w = new int[1], h = new int[1];
            glfwGetWindowPos(handle, xPos, yPos);
            glfwGetWindowSize(handle, w, h);
            windowedPosX = xPos[0];
            windowedPosY = yPos[0];
            windowedWidth = w[0];
            windowedHeight = h[0];
            
            // Get the video mode of the primary monitor
            org.lwjgl.glfw.GLFWVidMode mode = glfwGetVideoMode(monitor);
            if (mode == null) {
                logger.error("Failed to get video mode");
                return;
            }
            
            // Set fullscreen with monitor's native resolution
            glfwSetWindowMonitor(handle, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            // Restore windowed mode with previous position and size
            glfwSetWindowMonitor(handle, 0, windowedPosX, windowedPosY, windowedWidth, windowedHeight, GLFW_DONT_CARE);
        }
    }
    
    /**
     * Check if the window is currently in fullscreen mode.
     */
    public boolean isFullscreen() {
        return glfwGetWindowMonitor(handle) != 0;
    }

    @Override public void close() {
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
