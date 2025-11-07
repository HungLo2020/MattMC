package mattmc.client.gui.screens;

import mattmc.client.settings.OptionsManager;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.gui.components.Button;
import mattmc.client.gui.components.TextRenderer;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/** Graphics options submenu screen. */
public final class GraphicsScreen implements Screen {
    private final Minecraft game;
    private final Window window;
    private final List<Button> buttons = new ArrayList<>();
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;

    private float titleScale = 2.5f;
    private float titleCX, titleCY;
    private int buttonWidth = 300, buttonHeight = 44, buttonGap = 12;
    private int buttonsStartY;

    public GraphicsScreen(Minecraft game) {
        this.game = game;
        this.window = game.window();
        recomputeLayout();
    }

    private void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.18f;

        int totalButtonsH = 7 * buttonHeight + 6 * buttonGap;
        buttonsStartY = (int)(h / 2f - totalButtonsH / 2f);

        int x = (w - buttonWidth) / 2;
        buttons.clear();

        // Graphics-related buttons
        buttons.add(new Button(getResolutionButtonLabel(), x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getFullscreenButtonLabel(), x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getMenuBlurButtonLabel(), x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getTitleBlurButtonLabel(), x, buttonsStartY + 3 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getMipmapButtonLabel(), x, buttonsStartY + 4 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getAnisotropicButtonLabel(), x, buttonsStartY + 5 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Back",     x, buttonsStartY + 6 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }
    
    private String getResolutionButtonLabel() {
        String currentRes = mattmc.client.settings.OptionsManager.getResolutionString();
        return "Resolution: " + currentRes;
    }
    
    private String getFullscreenButtonLabel() {
        return "Fullscreen: " + (mattmc.client.settings.OptionsManager.isFullscreenEnabled() ? "ON" : "OFF");
    }
    
    private String getMenuBlurButtonLabel() {
        return "Menu Blur: " + (mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled() ? "ON" : "OFF");
    }
    
    private String getTitleBlurButtonLabel() {
        return "Title Blur: " + (mattmc.client.settings.OptionsManager.isTitleScreenBlurEnabled() ? "ON" : "OFF");
    }
    
    private String getMipmapButtonLabel() {
        int level = mattmc.client.settings.OptionsManager.getMipmapLevel();
        if (level == 0) {
            return "Mipmaps: OFF";
        }
        return "Mipmaps: " + level;
    }
    
    private String getAnisotropicButtonLabel() {
        int level = mattmc.client.settings.OptionsManager.getAnisotropicFiltering();
        if (level == 0) {
            return "Anisotropic Filtering: OFF";
        }
        return "Anisotropic Filtering: " + level + "x";
    }

    @Override
    public void tick() {
        // Convert window coords -> framebuffer coords for accurate hit-testing on HiDPI
        float mxFB, myFB;
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1), fbH  = stack.mallocInt(1);
            glfwGetWindowSize(window.handle(), winW, winH);
            glfwGetFramebufferSize(window.handle(), fbW, fbH);
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            mxFB = (float) mouseXWin * sx;
            myFB = (float) mouseYWin * sy;
        }

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
        if ("Back".equals(label)) {
            game.setScreen(new OptionsScreen(game));
            return;
        }
        if (label.startsWith("Resolution:")) {
            // Cycle through supported resolutions
            String current = mattmc.client.settings.OptionsManager.getResolutionString();
            String[] supportedResolutions = {"1280x720", "1600x900", "1920x1080"};
            int currentIndex = -1;
            
            // Find current resolution index
            for (int i = 0; i < supportedResolutions.length; i++) {
                if (supportedResolutions[i].equals(current)) {
                    currentIndex = i;
                    break;
                }
            }
            
            // Move to next resolution (wrap around)
            int nextIndex = (currentIndex == -1) ? 0 : (currentIndex + 1) % supportedResolutions.length;
            String nextRes = supportedResolutions[nextIndex];
            String[] parts = nextRes.split("x");
            int newWidth = Integer.parseInt(parts[0]);
            int newHeight = Integer.parseInt(parts[1]);
            
            // Update settings and apply new resolution
            mattmc.client.settings.OptionsManager.setResolution(newWidth, newHeight);
            window.setSize(newWidth, newHeight);
            recomputeLayout();
            return;
        }
        if (label.startsWith("Fullscreen:")) {
            mattmc.client.settings.OptionsManager.toggleFullscreen();
            window.setFullscreen(mattmc.client.settings.OptionsManager.isFullscreenEnabled());
            recomputeLayout();
            return;
        }
        if (label.startsWith("Menu Blur:")) {
            mattmc.client.settings.OptionsManager.toggleMenuScreenBlur();
            recomputeLayout();
            return;
        }
        if (label.startsWith("Title Blur:")) {
            mattmc.client.settings.OptionsManager.toggleTitleScreenBlur();
            recomputeLayout();
            return;
        }
        if (label.startsWith("Mipmaps:")) {
            // Cycle through mipmap levels: off, 1, 2, 3, 4
            int current = mattmc.client.settings.OptionsManager.getMipmapLevel();
            int[] allowedValues = mattmc.client.settings.OptionsManager.ALLOWED_MIPMAP_LEVELS;
            
            // Find next value in the cycle
            int nextIndex = 0;
            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == current) {
                    nextIndex = (i + 1) % allowedValues.length;
                    break;
                }
            }
            
            mattmc.client.settings.OptionsManager.setMipmapLevel(allowedValues[nextIndex]);
            recomputeLayout();
            return;
        }
        if (label.startsWith("Anisotropic Filtering:")) {
            // Cycle through anisotropic filtering levels: off, 2x, 4x, 8x, 16x
            int current = mattmc.client.settings.OptionsManager.getAnisotropicFiltering();
            int[] allowedValues = mattmc.client.settings.OptionsManager.ALLOWED_ANISOTROPIC_LEVELS;
            
            // Find next value in the cycle
            int nextIndex = 0;
            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == current) {
                    nextIndex = (i + 1) % allowedValues.length;
                    break;
                }
            }
            
            mattmc.client.settings.OptionsManager.setAnisotropicFiltering(allowedValues[nextIndex]);
            recomputeLayout();
            return;
        }
    }

    @Override
    public void render(double alpha) {
        // Render panorama background with blur based on settings
        boolean blurred = mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        setupOrtho();
        for (var b : buttons) drawButton(b);
        drawTitle("Graphics", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("Configure graphics settings", titleCX, titleCY + 48f, 1.0f, 0xB0C4DE);
    }

    private void setupOrtho() {
        int w = window.width(), h = window.height();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void drawButton(Button b) {
        int base = b.hover() ? 0x3A5FCD : 0x2E4A9B;
        int edge = b.hover() ? 0x6D89E3 : 0x20356B;

        setColor(0x000000, 0.35f);
        fillRect(b.x + 2, b.y + 3, b.w, b.h);

        glBegin(GL_QUADS);
        setColor(edge, 1f);
        glVertex2f(b.x, b.y);
        glVertex2f(b.x + b.w, b.y);
        setColor(base, 1f);
        glVertex2f(b.x + b.w, b.y + b.h);
        glVertex2f(b.x, b.y + b.h);
        glEnd();

        setColor(0x0B1220, 1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(b.x, b.y);
        glVertex2f(b.x + b.w, b.y);
        glVertex2f(b.x + b.w, b.y + b.h);
        glVertex2f(b.x, b.y + b.h);
        glEnd();

        drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
    }

    private void fillRect(int x, int y, int w, int h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
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
        setColor(rgb, 1f);
        TextRenderer.drawText(text, x, y, scale);
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
}
