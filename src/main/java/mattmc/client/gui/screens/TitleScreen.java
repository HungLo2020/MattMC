package mattmc.client.gui.screens;

import mattmc.client.settings.OptionsManager;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.renderer.texture.Texture;
import mattmc.client.gui.components.Button;
import mattmc.client.gui.components.ButtonRenderer;
import mattmc.client.gui.components.TextRenderer;
import mattmc.client.gui.SplashTextLoader;
import mattmc.client.util.CoordinateUtils;
import mattmc.util.ColorUtils;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/** Title screen with a time-based, fluid cubemap panorama and game logic at fixed 20 TPS. */
public final class TitleScreen implements Screen {
    // Core
    private final Minecraft game;
    private final Window window;

    // UI
    private final List<Button> buttons = new ArrayList<>();
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    private Texture logoTexture;
    private String splashText;

    // Fixed 20 TPS logic clock
    private static final double TPS = 20.0;
    private static final double TICK_LEN = 1.0 / TPS;
    private double tickAcc = 0.0;

    // Real-time frame clock for visuals & accumulator
    private double lastFrameTimeSec = System.nanoTime() * 1e-9;
    
    // Splash text animation
    private double splashAnimationTime = 0.0;

    // Layout
    private float titleScale = 3.0f;
    private float subtitleScale = 1.1f;
    private float titleYFrac = 0.18f; // Raised to 18% of screen height (closer to top)
    private int buttonWidth = 280, buttonHeight = 42, buttonGap = 12;
    private float titleCX, titleCY, subtitleCX, subtitleCY;
    private int buttonsStartY;

    public TitleScreen(Minecraft game) {
        this.game = game;
        this.window = game.window();
        
        // Load the MattMC logo texture
        logoTexture = Texture.load("/assets/textures/gui/MattMC.png");
        
        // Load random splash text
        splashText = SplashTextLoader.getRandomSplashText();

        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { mouseXWin = x; mouseYWin = y; });
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) mouseDown = (action == GLFW_PRESS);
        });

        recomputeLayout();

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
    }

    private void recomputeLayout() {
        int w = window.width(), h = window.height();

        titleCX = w / 2f;
        titleCY = h * titleYFrac;

        subtitleCX = w / 2f;
        subtitleCY = titleCY + 56f;

        int subtitleH = (int)(TextRenderer.getTextHeight("A", subtitleScale));
        int minButtonsTop = (int)(subtitleCY + subtitleH + 24);

        int totalButtonsH = 3 * buttonHeight + 2 * buttonGap;
        int centeredTop = h / 2 - totalButtonsH / 2;

        buttonsStartY = Math.max(minButtonsTop, centeredTop);

        int x = (w - buttonWidth) / 2;
        buttons.clear();
        buttons.add(new Button("Singleplayer", x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Options",      x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Quit",         x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }

    @Override
    public void tick() {
        // Frame delta (seconds)
        double now = System.nanoTime() * 1e-9;
        double frameDt = now - lastFrameTimeSec;
        lastFrameTimeSec = now;
        if (frameDt < 0) frameDt = 0;
        if (frameDt > 0.25) frameDt = 0.25; // clamp huge pauses to keep things sane

        // Update splash text animation time
        splashAnimationTime += frameDt;

        // Panorama rotation is now updated during rendering to prevent jitter

        // Hover every frame for responsiveness (not tied to TPS)
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        float mxFB = fbCoords.x;
        float myFB = fbCoords.y;
        for (var b : buttons) b.setHover(b.contains(mxFB, myFB));

        // Fixed 20 TPS for game logic
        tickAcc += frameDt;
        while (tickAcc >= TICK_LEN) {
            doTick20(mxFB, myFB);
            tickAcc -= TICK_LEN;
        }
    }

    /** Logic that runs exactly at 20 TPS (clicks, timers, etc.). */
    private void doTick20(float mxFB, float myFB) {
        if (mouseDown) {
            for (var b : buttons) {
                if (b.contains(mxFB, myFB)) {
                    onClick(b.label);
                    break;
                }
            }
            mouseDown = false; // debounce
        }
    }

    private void onClick(String label) {
        switch (label) {
            case "Singleplayer" -> game.setScreen(new SelectWorldScreen(game));
            case "Options"      -> game.setScreen(new OptionsScreen(game));
            case "Quit"         -> {
                glfwSetWindowShouldClose(window.handle(), true);
                game.quit();
            }
        }
    }

    @Override
    public void render(double alpha) {
        // 1) draw rotating cubemap panorama (perspective) - blur based on settings
        boolean blurred = mattmc.client.settings.OptionsManager.isTitleScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        // 2) switch to orthographic for UI and draw
        setupOrtho();
        for (var b : buttons) {
            ButtonRenderer.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        drawLogo();
        drawTitle(" ", subtitleCX, subtitleCY, subtitleScale, 0xB0C4DE);
        drawSplashText();
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

    private void drawLogo() {
        if (logoTexture == null) return;
        
        // Calculate logo dimensions - adaptive scaling based on window size
        // Target width is roughly 60% of screen width for good visibility
        int w = window.width();
        float targetWidth = w * 0.6f;
        float logoScale = targetWidth / logoTexture.width;
        
        // Clamp scale to reasonable bounds
        logoScale = Math.max(0.3f, Math.min(logoScale, 2.0f));
        
        float logoWidth = logoTexture.width * logoScale;
        float logoHeight = logoTexture.height * logoScale;
        float logoX = titleCX - logoWidth / 2f;
        float logoY = titleCY - logoHeight / 2f;
        
        // Enable texturing and blending for transparency
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        logoTexture.bind();
        glColor4f(1f, 1f, 1f, 1f);
        
        // Flip texture coordinates vertically to fix upside-down issue
        // (Texture loader flips vertically, so we need to flip back for proper display)
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(logoX, logoY);
        glTexCoord2f(1, 1); glVertex2f(logoX + logoWidth, logoY);
        glTexCoord2f(1, 0); glVertex2f(logoX + logoWidth, logoY + logoHeight);
        glTexCoord2f(0, 0); glVertex2f(logoX, logoY + logoHeight);
        glEnd();
        
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
    }

    private void drawSplashText() {
        if (splashText == null || splashText.isEmpty()) return;
        
        // Position splash text further to the left from the logo
        // Calculate logo bottom-right position
        int w = window.width();
        int h = window.height();
        float targetWidth = w * 0.6f;
        float logoScale = targetWidth / logoTexture.width;
        logoScale = Math.max(0.3f, Math.min(logoScale, 2.0f));
        
        float logoWidth = logoTexture.width * logoScale;
        float logoHeight = logoTexture.height * logoScale;
        float logoRight = titleCX + logoWidth / 2f;
        float logoBottom = titleCY + logoHeight / 2f;
        
        // Position splash text with offset from logo - moved left by 1/10 screen width and up by 1/10 screen height
        float splashX = logoRight - 80f - (w * 0.05f); // Left by 1/10 screen width
        float splashY = logoBottom - 10f - (h * 0.1f); // Up by 1/10 screen height
        
        // Calculate animated scale using sine wave with 2 second intervals
        // Period = 2 seconds, so frequency = 1/2 = 0.5 Hz
        // Scale oscillates between 1.2 and 1.3 (slightly larger to slightly smaller)
        float baseScale = 1.65f;
        float scaleAmplitude = 0.075f; // Oscillates ±0.05 around base (1.25 ± 0.05 = 1.2 to 1.3)
        float animatedScale = baseScale + scaleAmplitude * (float)Math.sin(splashAnimationTime * 1.5 * Math.PI * 0.5);
        
        // Save current matrix state
        glPushMatrix();
        
        // Move to the splash text position
        glTranslatef(splashX, splashY, 0);
        
        // Rotate -30 degrees (top of text faces top-left corner)
        glRotatef(-20f, 0, 0, 1);
        
        // Center the text on the anchor point by offsetting by half the text width
        float textWidth = TextRenderer.getTextWidth(splashText, animatedScale);
        float offsetX = -textWidth / 2f;
        
        // Draw text centered on the anchor point
        // Draw in yellow color (0xFFFF00)
        ColorUtils.setGLColor(0xFFFF00, 1f);
        TextRenderer.drawText(splashText, offsetX, 0, animatedScale);
        
        // Restore matrix state
        glPopMatrix();
    }

    @Override
    public void onClose() {
        // Panorama is now shared and managed by Minecraft, don't close it
        if (logoTexture != null) { logoTexture.close(); logoTexture = null; }
    }
}
