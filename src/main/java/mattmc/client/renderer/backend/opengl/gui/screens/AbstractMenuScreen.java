package mattmc.client.renderer.backend.opengl.gui.screens;

import mattmc.client.gui.screens.Screen;
import mattmc.client.Minecraft;
import mattmc.client.renderer.backend.opengl.Window;
import mattmc.client.gui.components.Button;
import mattmc.client.renderer.backend.opengl.gui.components.TextRenderer;
import mattmc.client.util.CoordinateUtils;
import mattmc.util.ColorUtils;
import mattmc.client.renderer.backend.opengl.OpenGLColorHelper;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Abstract base class for menu screens that share common functionality.
 * Provides mouse handling, layout utilities, and rendering helpers.
 */
public abstract class AbstractMenuScreen implements Screen {
    protected final Minecraft game;
    protected final Window window;
    protected final List<Button> buttons = new ArrayList<>();
    
    // Mouse state
    protected double mouseXWin, mouseYWin;
    protected boolean mouseDown;
    
    // Layout properties (can be customized by subclasses)
    protected float titleScale = 2.5f;
    protected float titleCX, titleCY;
    protected int buttonWidth = 300;
    protected int buttonHeight = 44;
    protected int buttonGap = 12;
    protected int buttonsStartY;
    
    protected AbstractMenuScreen(Minecraft game) {
        this.game = game;
        this.window = (Window) game.window();
    }
    
    /**
     * Subclasses should implement this to set up their specific layout.
     */
    protected abstract void recomputeLayout();
    
    /**
     * Subclasses should implement this to handle button clicks.
     */
    protected abstract void onClick(String label);
    
    @Override
    public void tick() {
        // Convert window coords -> framebuffer coords for accurate hit-testing on HiDPI
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        float mxFB = fbCoords.x;
        float myFB = fbCoords.y;

        for (var b : buttons) b.setHover(b.contains(mxFB, myFB));

        if (mouseDown) {
            for (var b : buttons) {
                if (b.contains(mxFB, myFB)) {
                    onClick(b.label);
                    break;
                }
            }
            mouseDown = false;
        }
    }
    
    @Override
    public void onOpen() {
        // Set up callbacks when screen opens
        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { mouseXWin = x; mouseYWin = y; });
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) mouseDown = (action == GLFW_PRESS);
        });
        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
    }
    
    @Override
    public void onClose() {
        // Panorama is now shared and managed by Minecraft
    }
    
    // === Rendering Utilities ===
    
    protected void setupOrtho() {
        int w = window.width(), h = window.height();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }
    
    protected void drawTitle(String text, float cx, float cy, float scale, int rgb) {
        float tw = TextRenderer.getTextWidth(text, scale);
        float th = TextRenderer.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }
    
    protected void drawTextCentered(String text, float cx, float cy, float scale, int rgb) {
        float tw = TextRenderer.getTextWidth(text, scale);
        float th = TextRenderer.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }
    
    protected void drawTextRight(String text, float rx, float y, float scale, int rgb) {
        float tw = TextRenderer.getTextWidth(text, scale);
        float x = rx - tw;
        drawText(text, x, y, scale, rgb);
    }
    
    protected void drawText(String text, float x, float y, float scale, int rgb) {
        OpenGLColorHelper.setGLColor(rgb, 1f);
        TextRenderer.drawText(text, x, y, scale);
    }
}
