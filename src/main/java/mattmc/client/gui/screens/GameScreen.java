package mattmc.client.gui.screens;

import mattmc.client.settings.OptionsManager;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.gui.components.Button;
import mattmc.client.gui.components.ButtonRenderer;
import mattmc.client.gui.components.TextRenderer;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/** Game options submenu screen. */
public final class GameScreen implements Screen {
    private final Minecraft game;
    private final Window window;
    private final List<Button> buttons = new ArrayList<>();
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;

    private float titleScale = 2.5f;
    private float titleCX, titleCY;
    private int buttonWidth = 300, buttonHeight = 44, buttonGap = 12;
    private int buttonsStartY;

    public GameScreen(Minecraft game) {
        this.game = game;
        this.window = game.window();
        recomputeLayout();
    }

    private void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.18f;

        int totalButtonsH = 3 * buttonHeight + 2 * buttonGap;
        buttonsStartY = (int)(h / 2f - totalButtonsH / 2f);

        int x = (w - buttonWidth) / 2;
        buttons.clear();

        // Game-related buttons
        buttons.add(new Button(getFpsCapButtonLabel(), x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getRenderDistanceButtonLabel(), x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Back",     x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }
    
    private String getFpsCapButtonLabel() {
        int fpsCap = mattmc.client.settings.OptionsManager.getFpsCap();
        return "FPS Cap: " + fpsCap + " (-/+)";
    }
    
    private String getRenderDistanceButtonLabel() {
        int renderDistance = mattmc.client.settings.OptionsManager.getRenderDistance();
        return "Render Distance: " + renderDistance + " chunks";
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
        if (label.startsWith("FPS Cap:")) {
            // Cycle through common FPS values
            int current = mattmc.client.settings.OptionsManager.getFpsCap();
            int[] commonValues = {30, 60, 75, 120, 144, 165, 240, 360, 999};
            int nextIndex = 0;
            
            // Find the next value in the cycle
            for (int i = 0; i < commonValues.length; i++) {
                if (commonValues[i] > current) {
                    nextIndex = i;
                    break;
                }
            }
            
            // If we're at or past the last value, wrap to the first
            if (current >= commonValues[commonValues.length - 1]) {
                nextIndex = 0;
            }
            
            mattmc.client.settings.OptionsManager.setFpsCap(commonValues[nextIndex]);
            game.window().applyFpsCapSetting();
            game.updateFpsCap();
            recomputeLayout();
            return;
        }
        if (label.startsWith("Render Distance:")) {
            // Cycle through allowed render distance values
            int current = mattmc.client.settings.OptionsManager.getRenderDistance();
            int[] allowedValues = mattmc.client.settings.OptionsManager.ALLOWED_RENDER_DISTANCES;
            
            // Find the current or next higher allowed value
            int nextIndex = 0;
            boolean foundCurrent = false;
            
            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == current) {
                    // Found exact match, use next value
                    nextIndex = (i + 1) % allowedValues.length;
                    foundCurrent = true;
                    break;
                } else if (allowedValues[i] > current) {
                    // Current value is between allowed values, jump to next higher
                    nextIndex = i;
                    foundCurrent = true;
                    break;
                }
            }
            
            // If current is higher than all allowed values, wrap to first
            if (!foundCurrent) {
                nextIndex = 0;
            }
            
            mattmc.client.settings.OptionsManager.setRenderDistance(allowedValues[nextIndex]);
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
        for (var b : buttons) {
            ButtonRenderer.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        drawTitle("Game", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("Configure game settings", titleCX, titleCY + 48f, 1.0f, 0xB0C4DE);
    }

    private void setupOrtho() {
        int w = window.width(), h = window.height();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
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
