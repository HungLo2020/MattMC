package mattmc.client.gui.screens;

import mattmc.client.MattMC;
import mattmc.client.gui.components.Button;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.util.CoordinateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for menu screens that share common functionality.
 * Provides mouse handling, layout utilities, and rendering helpers.
 * 
 * <p>This class is backend-agnostic and uses the {@link RenderBackend} interface
 * for all rendering operations. No OpenGL-specific code is used.
 * 
 * <p><b>Architecture:</b> This class lives outside the backend/ directory and
 * uses only backend-agnostic interfaces for rendering and input handling.
 */
public abstract class AbstractMenuScreen implements Screen {
    protected final MattMC game;
    protected final WindowHandle window;
    protected final RenderBackend backend;
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
    
    protected AbstractMenuScreen(MattMC game) {
        this.game = game;
        this.window = game.window();
        this.backend = game.getRenderBackend();
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
        // Set up callbacks when screen opens using backend-agnostic interface
        backend.setCursorPosCallback(window.handle(), (x, y) -> { 
            mouseXWin = x; 
            mouseYWin = y; 
        });
        backend.setMouseButtonCallback(window.handle(), (button, action, mods) -> {
            if (button == RenderBackend.MOUSE_BUTTON_LEFT) {
                mouseDown = (action == RenderBackend.ACTION_PRESS);
            }
        });
        backend.setFramebufferSizeCallback(window.handle(), (newW, newH) -> {
            backend.setViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
    }
    
    @Override
    public void onClose() {
        // Panorama is now shared and managed by MattMC
    }
    
    // === Rendering Utilities ===
    
    protected void setupOrtho() {
        int w = window.width(), h = window.height();
        backend.setup2DProjection(w, h);
    }
    
    protected void restoreOrtho() {
        backend.restore2DProjection();
    }
    
    protected void drawTitle(String text, float cx, float cy, float scale, int rgb) {
        float tw = backend.getTextWidth(text, scale);
        float th = backend.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }
    
    protected void drawTextCentered(String text, float cx, float cy, float scale, int rgb) {
        float tw = backend.getTextWidth(text, scale);
        float th = backend.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }
    
    protected void drawTextRight(String text, float rx, float y, float scale, int rgb) {
        float tw = backend.getTextWidth(text, scale);
        float x = rx - tw;
        drawText(text, x, y, scale, rgb);
    }
    
    protected void drawText(String text, float x, float y, float scale, int rgb) {
        backend.setColor(rgb, 1f);
        backend.drawText(text, x, y, scale);
    }
}
