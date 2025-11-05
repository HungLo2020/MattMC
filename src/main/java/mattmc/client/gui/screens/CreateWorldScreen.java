package mattmc.client.gui.screens;

import mattmc.client.settings.OptionsManager;
import mattmc.world.level.Level;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.gui.components.Button;
import mattmc.client.gui.components.EditBox;
import mattmc.client.gui.components.TextRenderer;
import mattmc.world.level.storage.LevelStorageSource;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/* Create Level screen - allows creating a new world. */
public final class CreateWorldScreen implements Screen {
    private final Minecraft game;
    private final Window window;
    private final List<Button> buttons = new ArrayList<>();
    private EditBox worldNameField;
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;

    private float titleScale = 2.5f;
    private float titleCX, titleCY;
    private int buttonWidth = 300, buttonHeight = 44, buttonGap = 12;
    private int textFieldWidth = 300, textFieldHeight = 32;
    private int buttonsStartY;

    public CreateWorldScreen(Minecraft game) {
        this.game = game;
        this.window = game.window();

        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { mouseXWin = x; mouseYWin = y; });
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) mouseDown = (action == GLFW_PRESS);
        });
        
        // Set up character callback for text field input
        glfwSetCharCallback(window.handle(), (win, codepoint) -> {
            if (worldNameField != null && worldNameField.isFocused()) {
                char c = (char) codepoint;
                // Only allow alphanumeric, space, and some punctuation
                if (isValidWorldNameCharacter(c)) {
                    worldNameField.appendChar(c);
                }
            }
        });
        
        // Set up key callback for backspace and enter
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (worldNameField != null && worldNameField.isFocused()) {
                if ((action == GLFW_PRESS || action == GLFW_REPEAT) && key == GLFW_KEY_BACKSPACE) {
                    worldNameField.backspace();
                }
                if (action == GLFW_PRESS && key == GLFW_KEY_ENTER) {
                    // Create world on Enter
                    createWorld();
                }
            }
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
        titleCY = h * 0.18f;

        // Layout: text field, then buttons below
        int textFieldY = (int)(h * 0.35f);
        int totalButtonsH = 2 * buttonHeight + 1 * buttonGap;
        buttonsStartY = textFieldY + textFieldHeight + 30;

        int x = (w - buttonWidth) / 2;
        int tfx = (w - textFieldWidth) / 2;
        buttons.clear();

        // Create text field with unique world name
        String defaultName = LevelStorageSource.generateUniqueWorldName("New World");
        worldNameField = new EditBox(tfx, textFieldY, textFieldWidth, textFieldHeight, 50);
        worldNameField.setText(defaultName);
        worldNameField.setFocused(true); // Auto-focus

        // Centered buttons
        buttons.add(new Button("Create World", x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Back",         x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }

    @Override
    public void tick() {
        // Update panorama animation
        game.panorama().update();
        
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
        if (worldNameField != null) worldNameField.setHover(worldNameField.contains(mxFB, myFB));

        if (mouseDown) {
            // Check text field click
            if (worldNameField != null && worldNameField.contains(mxFB, myFB)) {
                worldNameField.setFocused(true);
            } else {
                if (worldNameField != null) worldNameField.setFocused(false);
                
                // Check button clicks
                for (var b : buttons) {
                    if (b.contains(mxFB, myFB)) {
                        onClick(b.label);
                        break;
                    }
                }
            }
            mouseDown = false;
        }
    }

    private void onClick(String label) {
        if ("Back".equals(label)) {
            game.setScreen(new SelectWorldScreen(game));
            return;
        }
        if ("Create World".equals(label)) {
            createWorld();
            return;
        }
    }
    
    private void createWorld() {
        String worldName = worldNameField.getText().trim();
        if (worldName.isEmpty()) {
            worldName = "New World";
        }
        
        // Ensure unique name
        worldName = LevelStorageSource.generateUniqueWorldName(worldName);
        
        System.out.println("→ Creating world: " + worldName);
        game.setScreen(new DevplayScreen(game, worldName));
    }
    
    /**
     * Check if a character is valid for world names.
     * Allows alphanumeric, space, hyphen, underscore, and parentheses.
     */
    private static boolean isValidWorldNameCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '(' || c == ')';
    }

    @Override
    public void render(double alpha) {
        // Render panorama background with blur based on settings
        boolean blurred = mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        setupOrtho();
        
        // Draw text field
        if (worldNameField != null) drawTextField(worldNameField);
        
        for (var b : buttons) drawButton(b);
        drawTitle("Create New World", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("World Name:", titleCX, worldNameField.y - 20f, 1.0f, 0xB0C4DE);
    }

    private void setupOrtho() {
        int w = window.width(), h = window.height();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void drawTextField(EditBox tf) {
        // Background
        int bgColor = tf.isFocused() ? 0x000000 : 0x222222;
        setColor(bgColor, 0.8f);
        fillRect(tf.x, tf.y, tf.w, tf.h);
        
        // Border
        int borderColor = tf.isFocused() ? 0xFFFFFF : 0x888888;
        setColor(borderColor, 1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(tf.x, tf.y);
        glVertex2f(tf.x + tf.w, tf.y);
        glVertex2f(tf.x + tf.w, tf.y + tf.h);
        glVertex2f(tf.x, tf.y + tf.h);
        glEnd();
        
        // Text
        String text = tf.getText();
        if (!text.isEmpty()) {
            drawText(text, tf.x + 8f, tf.y + 10f, 1.0f, 0xFFFFFF);
        }
        
        // Cursor
        if (tf.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            float textWidth = TextRenderer.getTextWidth(text, 1.0f);
            float cursorX = tf.x + 8f + textWidth;
            setColor(0xFFFFFF, 1f);
            glBegin(GL_LINES);
            glVertex2f(cursorX, tf.y + 6f);
            glVertex2f(cursorX, tf.y + tf.h - 6f);
            glEnd();
        }
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
    public void onOpen() {}
    @Override
    public void onClose() {
        // Panorama is now shared and managed by Minecraft
    }
}
