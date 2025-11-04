package MattMC.screens;

import MattMC.core.Window;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Helper class for handling common screen input events.
 * Reduces boilerplate code in screen implementations.
 */
public class ScreenInputHandler {
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    private final Window window;
    
    public ScreenInputHandler(Window window) {
        this.window = window;
        setupCallbacks();
    }
    
    /**
     * Setup common GLFW callbacks for mouse input.
     */
    private void setupCallbacks() {
        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> {
            mouseXWin = x;
            mouseYWin = y;
        });
        
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                mouseDown = (action == GLFW_PRESS);
            }
        });
    }
    
    /**
     * Setup framebuffer resize callback with a custom handler.
     */
    public void setupResizeCallback(ResizeHandler handler) {
        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            handler.onResize(newW, newH);
        });
    }
    
    public double getMouseX() { return mouseXWin; }
    public double getMouseY() { return mouseYWin; }
    public boolean isMouseDown() { return mouseDown; }
    public void setMouseDown(boolean down) { this.mouseDown = down; }
    
    /**
     * Functional interface for handling window resize events.
     */
    @FunctionalInterface
    public interface ResizeHandler {
        void onResize(int width, int height);
    }
}
