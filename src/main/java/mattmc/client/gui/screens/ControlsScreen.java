package mattmc.client.gui.screens;

import mattmc.client.settings.OptionsManager;
import mattmc.world.level.block.Block;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.world.entity.player.PlayerInput;
import mattmc.client.gui.components.Button;
import mattmc.client.gui.components.ButtonRenderer;
import mattmc.client.gui.components.TextRenderer;
import mattmc.client.settings.KeybindManager;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/** Keybinds configuration screen. */
public final class ControlsScreen implements Screen {
    private final Minecraft game;
    private final Window window;
    private final List<KeybindButton> keybindButtons = new ArrayList<>();
    private final Button backButton;
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    
    private String waitingForKey = null; // Action name we're waiting to rebind
    private boolean ignoreNextRelease = false; // Flag to ignore the mouse release from the initial click
    
    private float titleScale = 2.0f;
    private float titleCX, titleCY;
    private int buttonWidth = 400, buttonHeight = 36, buttonGap = 8;
    private int keybindsStartY;
    private int backButtonY;
    
    // Scrolling support
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private static final int SCROLL_BUFFER = 50; // Buffer room above/below keybinds
    private static final int SCROLL_SPEED = 20; // Pixels to scroll per mouse wheel notch
    
    // Keybind actions with display names
    private static final String[][] KEYBIND_ACTIONS = {
        {PlayerInput.FORWARD, "Move Forward"},
        {PlayerInput.BACKWARD, "Move Backward"},
        {PlayerInput.LEFT, "Strafe Left"},
        {PlayerInput.RIGHT, "Strafe Right"},
        {PlayerInput.JUMP, "Jump"},
        {PlayerInput.CROUCH, "Crouch / Fly Down"},
        {PlayerInput.FLY_UP, "Fly Up"},
        {PlayerInput.BREAK_BLOCK, "Break Block"},
        {PlayerInput.PLACE_BLOCK, "Place Block"},
        {PlayerInput.OPEN_COMMAND, "Open Command"},
        {PlayerInput.HOTBAR_1, "Hotbar Slot 1"},
        {PlayerInput.HOTBAR_2, "Hotbar Slot 2"},
        {PlayerInput.HOTBAR_3, "Hotbar Slot 3"},
        {PlayerInput.HOTBAR_4, "Hotbar Slot 4"},
        {PlayerInput.HOTBAR_5, "Hotbar Slot 5"},
        {PlayerInput.HOTBAR_6, "Hotbar Slot 6"},
        {PlayerInput.HOTBAR_7, "Hotbar Slot 7"},
        {PlayerInput.HOTBAR_8, "Hotbar Slot 8"},
        {PlayerInput.HOTBAR_9, "Hotbar Slot 9"}
    };

    public ControlsScreen(Minecraft game) {
        this.game = game;
        this.window = game.window();
        backButton = new Button("Back", 0, 0, 200, 40);
        recomputeLayout();
    }

    private void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.12f;

        int totalKeybindsH = KEYBIND_ACTIONS.length * buttonHeight + (KEYBIND_ACTIONS.length - 1) * buttonGap;
        keybindsStartY = (int)(h * 0.25f);

        int x = (w - buttonWidth) / 2;
        keybindButtons.clear();
        
        for (int i = 0; i < KEYBIND_ACTIONS.length; i++) {
            String action = KEYBIND_ACTIONS[i][0];
            String display = KEYBIND_ACTIONS[i][1];
            int y = keybindsStartY + i * (buttonHeight + buttonGap);
            keybindButtons.add(new KeybindButton(action, display, x, y, buttonWidth, buttonHeight));
        }
        
        // Position back button at bottom
        backButtonY = h - 80;
        backButton.x = (w - backButton.w) / 2;
        backButton.y = backButtonY;
        
        // Calculate maximum scroll offset (total content height - available height + buffer)
        int availableHeight = backButtonY - keybindsStartY - SCROLL_BUFFER;
        maxScrollOffset = Math.max(0, totalKeybindsH - availableHeight + SCROLL_BUFFER);
    }

    @Override
    public void tick() {
        // Panorama rotation is now updated during rendering to prevent jitter
        
        // Convert window coords -> framebuffer coords
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

        for (var kb : keybindButtons) {
            // Apply scroll offset to button position for hover detection
            int adjustedY = kb.button.y - scrollOffset;
            kb.button.setHover(mxFB >= kb.button.x && mxFB < kb.button.x + kb.button.w &&
                              myFB >= adjustedY && myFB < adjustedY + kb.button.h);
        }
        backButton.setHover(backButton.contains(mxFB, myFB));

        if (mouseDown) {
            // Check keybind buttons with scroll offset
            for (var kb : keybindButtons) {
                int adjustedY = kb.button.y - scrollOffset;
                if (mxFB >= kb.button.x && mxFB < kb.button.x + kb.button.w &&
                    myFB >= adjustedY && myFB < adjustedY + kb.button.h) {
                    waitingForKey = kb.action;
                    ignoreNextRelease = true; // Ignore the release from this click
                    break;
                }
            }
            
            // Check back button (only if not waiting for keybind)
            if (waitingForKey == null && backButton.contains(mxFB, myFB)) {
                game.setScreen(new OptionsScreen(game));
            }
            
            mouseDown = false;
        }
    }

    @Override
    public void render(double alpha) {
        // Render panorama background with blur based on settings
        boolean blurred = mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        setupOrtho();
        
        // Draw title
        drawTitle("Keybinds", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("Click a button to change its keybind", titleCX, titleCY + 40f, 0.9f, 0xB0C4DE);
        
        // Draw keybind buttons with scroll offset
        for (var kb : keybindButtons) {
            boolean waiting = kb.action.equals(waitingForKey);
            Button b = kb.button;
            
            // Apply scroll offset to button rendering
            int adjustedY = b.y - scrollOffset;
            
            // Only render buttons that are visible on screen
            if (adjustedY + b.h >= keybindsStartY - SCROLL_BUFFER && adjustedY <= backButtonY) {
                // Temporarily adjust button Y for rendering
                int originalY = b.y;
                b.y = adjustedY;
                ButtonRenderer.drawButton(b, waiting);
                drawKeybindButtonText(kb, waiting, b);
                b.y = originalY;
            }
        }
        
        // Draw back button
        ButtonRenderer.drawButton(backButton);
        drawTextCentered(backButton.label, backButton.x + backButton.w / 2f, backButton.y + backButton.h / 2f, 1.2f, 0xFFFFFF);
    }

    private void drawKeybindButtonText(KeybindButton kb, boolean waiting, Button b) {
        // Draw action name on left, key name on right
        Integer keyCode = PlayerInput.getInstance().getKeybind(kb.action);
        String keyName = waiting ? "Press a key..." : 
                        (keyCode != null ? PlayerInput.getKeyName(keyCode) : "Unbound");
        
        drawText(kb.display, b.x + 10, b.y + b.h / 2f - 6f, 1.0f, 0xFFFFFF);
        drawTextRight(keyName, b.x + b.w - 10, b.y + b.h / 2f - 6f, 1.0f, 0xFFFFFF);
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
    
    private void drawTextRight(String text, float rx, float y, float scale, int rgb) {
        float tw = TextRenderer.getTextWidth(text, scale);
        float x = rx - tw;
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
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    mouseDown = true;
                } else if (action == GLFW_RELEASE && waitingForKey != null) {
                    // Ignore the release from the initial click
                    if (ignoreNextRelease) {
                        ignoreNextRelease = false;
                        return;
                    }
                    // Bind mouse button
                    int mouseButton = -(button + 1); // Convert to negative value
                    PlayerInput.getInstance().setKeybind(waitingForKey, mouseButton);
                    KeybindManager.saveKeybinds();
                    waitingForKey = null;
                }
            } else if (waitingForKey != null && action == GLFW_RELEASE) {
                // Bind other mouse buttons (non-left clicks)
                int mouseButton = -(button + 1);
                PlayerInput.getInstance().setKeybind(waitingForKey, mouseButton);
                KeybindManager.saveKeybinds();
                waitingForKey = null;
            }
        });
        
        // Key callback for binding keys
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS && waitingForKey != null) {
                if (key == GLFW_KEY_ESCAPE) {
                    // Cancel binding
                    waitingForKey = null;
                } else {
                    // Bind key
                    PlayerInput.getInstance().setKeybind(waitingForKey, key);
                    KeybindManager.saveKeybinds();
                    waitingForKey = null;
                }
            }
        });

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
        
        // Scroll callback for mouse wheel scrolling
        glfwSetScrollCallback(window.handle(), (win, xOffset, yOffset) -> {
            // yOffset: positive when scrolling up, negative when scrolling down
            // Negate yOffset so scrolling down increases scrollOffset (moves content up)
            int scrollAmount = (int)(-yOffset * SCROLL_SPEED);
            scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset + scrollAmount));
        });
    }
    
    @Override
    public void onClose() {
        // Panorama is now shared and managed by Minecraft
    }
    
    /** Helper class to store keybind button data. */
    private static class KeybindButton {
        final String action;
        final String display;
        final Button button;
        
        KeybindButton(String action, String display, int x, int y, int w, int h) {
            this.action = action;
            this.display = display;
            this.button = new Button(display, x, y, w, h);
        }
    }
}
