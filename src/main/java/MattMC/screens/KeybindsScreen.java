package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.player.PlayerInput;
import MattMC.ui.UIButton;
import MattMC.util.KeybindManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/** Keybinds configuration screen. */
public final class KeybindsScreen implements Screen {
    private final Game game;
    private final Window window;
    private final List<KeybindButton> keybindButtons = new ArrayList<>();
    private final UIButton backButton;
    private final ByteBuffer fontBuffer = BufferUtils.createByteBuffer(16 * 4096);
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    
    private String waitingForKey = null; // Action name we're waiting to rebind
    
    private float titleScale = 2.0f;
    private float titleCX, titleCY;
    private int buttonWidth = 400, buttonHeight = 36, buttonGap = 8;
    private int keybindsStartY;
    private int backButtonY;
    
    // Keybind actions with display names
    private static final String[][] KEYBIND_ACTIONS = {
        {PlayerInput.FORWARD, "Move Forward"},
        {PlayerInput.BACKWARD, "Move Backward"},
        {PlayerInput.LEFT, "Strafe Left"},
        {PlayerInput.RIGHT, "Strafe Right"},
        {PlayerInput.JUMP, "Jump"},
        {PlayerInput.CROUCH, "Crouch"},
        {PlayerInput.FLY_UP, "Fly Up"},
        {PlayerInput.FLY_DOWN, "Fly Down"},
        {PlayerInput.BREAK_BLOCK, "Break Block"},
        {PlayerInput.PLACE_BLOCK, "Place Block"}
    };

    public KeybindsScreen(Game game) {
        this.game = game;
        this.window = game.window();

        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { mouseXWin = x; mouseYWin = y; });
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    mouseDown = true;
                } else if (action == GLFW_RELEASE && waitingForKey != null) {
                    // Bind mouse button
                    int mouseButton = -(button + 1); // Convert to negative value
                    PlayerInput.getInstance().setKeybind(waitingForKey, mouseButton);
                    KeybindManager.saveKeybinds();
                    waitingForKey = null;
                }
            } else if (waitingForKey != null && action == GLFW_RELEASE) {
                // Bind other mouse buttons
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

        backButton = new UIButton("Back", 0, 0, 200, 40);
        recomputeLayout();

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
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
    }

    @Override
    public void tick() {
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
            kb.button.setHover(kb.button.contains(mxFB, myFB));
        }
        backButton.setHover(backButton.contains(mxFB, myFB));

        if (mouseDown) {
            // Check keybind buttons
            for (var kb : keybindButtons) {
                if (kb.button.contains(mxFB, myFB)) {
                    waitingForKey = kb.action;
                    break;
                }
            }
            
            // Check back button
            if (backButton.contains(mxFB, myFB)) {
                game.setScreen(new OptionsScreen(game));
            }
            
            mouseDown = false;
        }
    }

    @Override
    public void render(double alpha) {
        glClearColor(0.06f, 0.08f, 0.10f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        setupOrtho();
        
        // Draw title
        drawTitle("Keybinds", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("Click a button to change its keybind", titleCX, titleCY + 40f, 0.9f, 0xB0C4DE);
        
        // Draw keybind buttons
        for (var kb : keybindButtons) {
            boolean waiting = kb.action.equals(waitingForKey);
            drawKeybindButton(kb, waiting);
        }
        
        // Draw back button
        drawButton(backButton);
    }

    private void setupOrtho() {
        int w = window.width(), h = window.height();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void drawKeybindButton(KeybindButton kb, boolean waiting) {
        UIButton b = kb.button;
        
        int base = waiting ? 0xFF6B35 : (b.hover() ? 0x3A5FCD : 0x2E4A9B);
        int edge = waiting ? 0xFF8C5A : (b.hover() ? 0x6D89E3 : 0x20356B);

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

        // Draw action name on left, key name on right
        Integer keyCode = PlayerInput.getInstance().getKeybind(kb.action);
        String keyName = waiting ? "Press a key..." : 
                        (keyCode != null ? PlayerInput.getKeyName(keyCode) : "Unbound");
        
        drawText(kb.display, b.x + 10, b.y + b.h / 2f - 6f, 1.0f, 0xFFFFFF);
        drawTextRight(keyName, b.x + b.w - 10, b.y + b.h / 2f - 6f, 1.0f, 0xFFFFFF);
    }

    private void drawButton(UIButton b) {
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
        int tw = STBEasyFont.stb_easy_font_width(text);
        int th = STBEasyFont.stb_easy_font_height(text);
        float x = cx - (tw * scale) / 2f;
        float y = cy - (th * scale) / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawTextCentered(String text, float cx, float cy, float scale, int rgb) {
        int tw = STBEasyFont.stb_easy_font_width(text);
        int th = STBEasyFont.stb_easy_font_height(text);
        float x = cx - (tw * scale) / 2f;
        float y = cy - (th * scale) / 2f;
        drawText(text, x, y, scale, rgb);
    }
    
    private void drawTextRight(String text, float rx, float y, float scale, int rgb) {
        int tw = STBEasyFont.stb_easy_font_width(text);
        float x = rx - (tw * scale);
        drawText(text, x, y, scale, rgb);
    }

    private void drawText(String text, float x, float y, float scale, int rgb) {
        setColor(rgb, 1f);
        fontBuffer.clear();
        int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, fontBuffer);

        glPushMatrix();
        glTranslatef(x, y, 0f);
        glScalef(scale, scale, 1f);

        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(2, GL_FLOAT, 16, fontBuffer);
        glDrawArrays(GL_QUADS, 0, quads * 4);
        glDisableClientState(GL_VERTEX_ARRAY);

        glPopMatrix();
    }

    @Override
    public void onOpen() {}
    
    @Override
    public void onClose() {
        // Clear callbacks to prevent memory leaks
        glfwSetKeyCallback(window.handle(), null);
        glfwSetMouseButtonCallback(window.handle(), null);
        glfwSetCursorPosCallback(window.handle(), null);
    }
    
    /** Helper class to store keybind button data. */
    private static class KeybindButton {
        final String action;
        final String display;
        final UIButton button;
        
        KeybindButton(String action, String display, int x, int y, int w, int h) {
            this.action = action;
            this.display = display;
            this.button = new UIButton(display, x, y, w, h);
        }
    }
}
