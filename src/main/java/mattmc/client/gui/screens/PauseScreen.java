package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.gui.components.Button;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.settings.OptionsManager;
import mattmc.client.util.CoordinateUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pause menu overlay shown when player presses ESC in-game.
 * Similar to Minecraft's pause screen.
 */
public final class PauseScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(PauseScreen.class);

    private final Minecraft game;
    private final WindowHandle window;
    private final RenderBackend backend;
    private final mattmc.client.renderer.backend.opengl.gui.screens.DevplayScreen gameScreen;
    private final List<Button> buttons = new ArrayList<>();
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    
    private float titleScale = 2.5f;
    private float titleCX, titleCY;
    private int buttonWidth = 300, buttonHeight = 44, buttonGap = 12;
    private int buttonsStartY;

    public PauseScreen(Minecraft game, mattmc.client.renderer.backend.opengl.gui.screens.DevplayScreen gameScreen) {
        this.game = game;
        this.window = game.window();
        this.backend = game.getRenderBackend();
        this.gameScreen = gameScreen;
        
        // Sync player position to prevent flickering during interpolation
        gameScreen.syncPlayerPosition();

        recomputeLayout();
    }
    
    @Override
    public void onOpen() {
        backend.setCursorPosCallback(window.handle(), (x, y) -> { 
            mouseXWin = x; 
            mouseYWin = y; 
        });
        backend.setMouseButtonCallback(window.handle(), (button, action, mods) -> {
            if (button == RenderBackend.MOUSE_BUTTON_LEFT) {
                mouseDown = (action == RenderBackend.ACTION_PRESS);
            }
        });
        
        // Set up key callback for ESC to unpause
        backend.setKeyCallback(window.handle(), (key, scancode, action, mods) -> {
            if (key == RenderBackend.KEY_ESCAPE && action == RenderBackend.ACTION_PRESS) {
                unpause();
            }
        });

        backend.setFramebufferSizeCallback(window.handle(), (newW, newH) -> {
            backend.setViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
        
        // Release mouse cursor
        backend.setCursorMode(window.handle(), RenderBackend.CURSOR_NORMAL);
    }

    private void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.30f;

        int totalButtonsH = 2 * buttonHeight + 1 * buttonGap;
        buttonsStartY = (int)(h / 2f - totalButtonsH / 2f);

        int x = (w - buttonWidth) / 2;
        buttons.clear();

        buttons.add(new Button("Resume",  x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Save and Exit", x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }
    
    private void unpause() {
        // Recapture mouse for FPS controls
        backend.setCursorMode(window.handle(), RenderBackend.CURSOR_DISABLED);
        game.setScreen(gameScreen);
    }

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

    private void onClick(String label) {
        if ("Resume".equals(label)) {
            unpause();
            return;
        }
        if ("Save and Exit".equals(label)) {
            saveAndExit();
            return;
        }
    }
    
    private void saveAndExit() {
        try {
            // Save the world and mark it for shutdown
            gameScreen.saveAndShutdown();
            
            // Return to title screen
            backend.setCursorMode(window.handle(), RenderBackend.CURSOR_NORMAL);
            game.setScreen(new TitleScreen(game));
        } catch (IOException e) {
            logger.error("Failed to save world", e);
            // Still exit even if save failed
            backend.setCursorMode(window.handle(), RenderBackend.CURSOR_NORMAL);
            game.setScreen(new TitleScreen(game));
        }
    }

    @Override
    public void render(double alpha) {
        int w = window.width(), h = window.height();
        
        // First render the game screen behind this overlay
        gameScreen.render(alpha);
        
        // Apply blur if enabled
        if (OptionsManager.isMenuScreenBlurEnabled()) {
            backend.applyRegionalBlur(0, 0, w, h, w, h);
        }
        
        // Set up 2D projection for UI
        backend.setup2DProjection(w, h);
        
        // Enable blending for transparent overlay
        backend.enableBlend();
        
        // Draw dark overlay
        backend.setColor(0x000000, 0.5f);
        backend.fillRect(0, 0, w, h);
        
        // Draw UI
        for (var b : buttons) {
            backend.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        drawTitle("Game Paused", titleCX, titleCY, titleScale, 0xFFFFFF);
    }

    private void drawTitle(String text, float cx, float cy, float scale, int rgb) {
        float tw = backend.getTextWidth(text, scale);
        float th = backend.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawTextCentered(String text, float cx, float cy, float scale, int rgb) {
        float tw = backend.getTextWidth(text, scale);
        float th = backend.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawText(String text, float x, float y, float scale, int rgb) {
        backend.setColor(rgb, 1f);
        backend.drawText(text, x, y, scale);
    }

    @Override
    public void onClose() {
        // Blur effect is managed by the backend now
    }
}
