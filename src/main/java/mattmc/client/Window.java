package mattmc.client;

import mattmc.client.settings.OptionsManager;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public final class Window implements AutoCloseable {
    private final long handle;
    private int width, height;

    public Window(int w, int h, String title) {
        this.width = w; this.height = h;

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
     * Apply the FPS cap setting from OptionsManager.
     * VSync is disabled to allow manual FPS control.
     */
    public void applyFpsCapSetting() {
        // Disable VSync for manual FPS control
        glfwSwapInterval(0);
    }

    @Override public void close() {
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
