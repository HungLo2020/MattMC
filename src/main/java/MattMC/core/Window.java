package MattMC.core;

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

        // ⚠️ Important: DO NOT request a core 3.x context.
        // Let GLFW pick a compatibility context (often GL 2.1 on Linux),
        // so fixed-function calls like glMatrixMode work.
        // If you want to be explicit, you could set:
        // glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        // glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        handle = glfwCreateWindow(w, h, title, 0, 0);
        if (handle == 0) throw new IllegalStateException("Window creation failed");

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();          // after makeCurrent
        glfwSwapInterval(1);              // vsync

        // initial GL state
        glDisable(GL_DEPTH_TEST);
        setOrtho2D();

        // handle resize
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
        glOrtho(0, width, height, 0, -1, 1); // (0,0) = top-left
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

    @Override public void close() {
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
