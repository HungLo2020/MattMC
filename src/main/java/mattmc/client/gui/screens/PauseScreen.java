package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.renderer.backend.opengl.BlurEffect;
import mattmc.client.renderer.backend.opengl.BlurRenderer;
import mattmc.client.gui.components.Button;
import mattmc.client.gui.components.ButtonRenderer;
import mattmc.client.gui.components.TextRenderer;
import mattmc.client.util.CoordinateUtils;
import mattmc.util.ColorUtils;
import mattmc.world.level.Level;
import mattmc.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pause menu overlay shown when player presses ESC in-game.
 * Similar to Minecraft's pause screen.
 */
public final class PauseScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(PauseScreen.class);

    private final Minecraft game;
    private final Window window;
    private final DevplayScreen gameScreen;
    private final List<Button> buttons = new ArrayList<>();
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    
    private float titleScale = 2.5f;
    private float titleCX, titleCY;
    private int buttonWidth = 300, buttonHeight = 44, buttonGap = 12;
    private int buttonsStartY;
    
    // Blur effect for background
    private BlurEffect blurEffect;

    public PauseScreen(Minecraft game, DevplayScreen gameScreen) {
        this.game = game;
        this.window = game.window();
        this.gameScreen = gameScreen;
        
        // Sync player position to prevent flickering during interpolation
        gameScreen.syncPlayerPosition();

        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { mouseXWin = x; mouseYWin = y; });
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) mouseDown = (action == GLFW_PRESS);
        });
        
        // Set up key callback for ESC to unpause
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                unpause();
            }
        });

        recomputeLayout();

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
        
        // Release mouse cursor
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
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
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
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
            glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            game.setScreen(new TitleScreen(game));
        } catch (IOException e) {
            logger.error("Failed to save world", e);
            // Still exit even if save failed
            glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            game.setScreen(new TitleScreen(game));
        }
    }

    @Override
    public void render(double alpha) {
        int w = window.width(), h = window.height();
        
        // First render the game screen behind this overlay
        gameScreen.render(alpha);
        
        // Apply blur if enabled
        if (isMenuScreenBlurEnabled()) {
            if (blurEffect == null) {
                blurEffect = new BlurEffect();
            }
            BlurRenderer.renderBlurredBackground(blurEffect, w, h);
        }
        
        // Draw dark overlay
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        ColorUtils.setGLColor(0x000000, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(w, 0);
        glVertex2f(w, h);
        glVertex2f(0, h);
        glEnd();
        
        // Draw UI
        for (var b : buttons) {
            ButtonRenderer.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        drawTitle("Game Paused", titleCX, titleCY, titleScale, 0xFFFFFF);
    }

    private void setupOrtho() {
        int w = window.width(), h = window.height();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }



    private void drawTitle(String text, float cx, float cy, float scale, int rgb) {
        float tw = TextRenderer.getTextWidth(text, scale);
        float th = TextRenderer.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawTextCentered(String text, float cx, float cy, float scale, int rgb) {
        float tw = TextRenderer.getTextWidth(text, scale);
        float th = TextRenderer.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawText(String text, float x, float y, float scale, int rgb) {
        ColorUtils.setGLColor(rgb, 1f);
        TextRenderer.drawText(text, x, y, scale);
    }

    @Override
    public void onOpen() {}
    
    @Override
    public void onClose() {
        if (blurEffect != null) {
            blurEffect.close();
            blurEffect = null;
        }
    }
}
