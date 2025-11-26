package mattmc.client.gui.screens;

import mattmc.client.MattMC;
import mattmc.client.gui.SplashTextLoader;
import mattmc.client.gui.components.Button;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.settings.OptionsManager;
import mattmc.client.util.CoordinateUtils;
import mattmc.util.MathUtils;

import java.util.ArrayList;
import java.util.List;

/** Title screen with a time-based, fluid cubemap panorama and game logic at fixed 20 TPS. */
public final class TitleScreen implements Screen {
    // Core
    private final MattMC game;
    private final WindowHandle window;
    private final RenderBackend backend;

    // UI
    private final List<Button> buttons = new ArrayList<>();
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    private int logoTextureId = -1;
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

    public TitleScreen(MattMC game) {
        this.game = game;
        this.window = game.window();
        this.backend = game.getRenderBackend();
        
        // Load the MattMC logo texture
        logoTextureId = backend.loadTexture("/assets/textures/gui/MattMC.png");
        
        // Load random splash text
        splashText = SplashTextLoader.getRandomSplashText();

        recomputeLayout();
    }
    
    @Override
    public void onOpen() {
        // Set up callbacks using backend-agnostic interface
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

    private void recomputeLayout() {
        int w = window.width(), h = window.height();

        titleCX = w / 2f;
        titleCY = h * titleYFrac;

        subtitleCX = w / 2f;
        subtitleCY = titleCY + 56f;

        int subtitleH = (int)(backend.getTextHeight("A", subtitleScale));
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
                backend.setWindowShouldClose(window.handle(), true);
                game.quit();
            }
        }
    }

    @Override
    public void render(double alpha) {
        // 1) draw rotating cubemap panorama (perspective) - blur based on settings
        boolean blurred = OptionsManager.isTitleScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        // 2) switch to orthographic for UI and draw
        backend.setup2DProjection(window.width(), window.height());
        for (var b : buttons) {
            backend.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        drawLogo();
        drawTitle(" ", subtitleCX, subtitleCY, subtitleScale, 0xB0C4DE);
        drawSplashText();
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

    private void drawLogo() {
        if (logoTextureId < 0) return;
        
        // Calculate logo dimensions - adaptive scaling based on window size
        // Target width is roughly 60% of screen width for good visibility
        int w = window.width();
        int logoWidth = backend.getTextureWidth(logoTextureId);
        int logoHeight = backend.getTextureHeight(logoTextureId);
        
        float targetWidth = w * 0.6f;
        float logoScale = targetWidth / logoWidth;
        
        // Clamp scale to reasonable bounds
        logoScale = MathUtils.clamp(logoScale, 0.3f, 2.0f);
        
        float scaledWidth = logoWidth * logoScale;
        float scaledHeight = logoHeight * logoScale;
        float logoX = titleCX - scaledWidth / 2f;
        float logoY = titleCY - scaledHeight / 2f;
        
        backend.drawTexture(logoTextureId, logoX, logoY, scaledWidth, scaledHeight);
    }

    private void drawSplashText() {
        if (splashText == null || splashText.isEmpty()) return;
        if (logoTextureId < 0) return;
        
        // Position splash text further to the left from the logo
        // Calculate logo bottom-right position
        int w = window.width();
        int h = window.height();
        int logoWidth = backend.getTextureWidth(logoTextureId);
        int logoHeight = backend.getTextureHeight(logoTextureId);
        
        float targetWidth = w * 0.6f;
        float logoScale = targetWidth / logoWidth;
        logoScale = MathUtils.clamp(logoScale, 0.3f, 2.0f);
        
        float scaledWidth = logoWidth * logoScale;
        float scaledHeight = logoHeight * logoScale;
        float logoRight = titleCX + scaledWidth / 2f;
        float logoBottom = titleCY + scaledHeight / 2f;
        
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
        backend.pushMatrix();
        
        // Move to the splash text position
        backend.translateMatrix(splashX, splashY, 0);
        
        // Rotate -20 degrees (top of text faces top-left corner)
        backend.rotateMatrix(-20f, 0, 0, 1);
        
        // Center the text on the anchor point by offsetting by half the text width
        float textWidth = backend.getTextWidth(splashText, animatedScale);
        float offsetX = -textWidth / 2f;
        
        // Draw text centered on the anchor point
        // Draw in yellow color (0xFFFF00)
        backend.setColor(0xFFFF00, 1f);
        backend.drawText(splashText, offsetX, 0, animatedScale);
        
        // Restore matrix state
        backend.popMatrix();
    }

    @Override
    public void onClose() {
        // Panorama is now shared and managed by MattMC, don't close it
        if (logoTextureId >= 0) { 
            backend.releaseTexture(logoTextureId); 
            logoTextureId = -1; 
        }
    }
}
