package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.gui.components.Button;
import mattmc.client.gui.components.EditBox;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.settings.OptionsManager;
import mattmc.client.util.CoordinateUtils;
import mattmc.world.level.storage.LevelStorageSource;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Create Level screen - allows creating a new world. */
public final class CreateWorldScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(CreateWorldScreen.class);

    private final Minecraft game;
    private final WindowHandle window;
    private final RenderBackend backend;
    private final List<Button> buttons = new ArrayList<>();
    private EditBox worldNameField;
    private EditBox seedField;
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
        this.backend = game.getRenderBackend();

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
        
        // Set up character callback for text field input
        backend.setCharCallback(window.handle(), (codepoint) -> {
            EditBox focusedField = getFocusedField();
            if (focusedField != null) {
                char c = (char) codepoint;
                // For world name, only allow alphanumeric, space, and some punctuation
                if (focusedField == worldNameField && isValidWorldNameCharacter(c)) {
                    focusedField.appendChar(c);
                }
                // For seed, allow any printable character
                else if (focusedField == seedField && isPrintableCharacter(c)) {
                    focusedField.appendChar(c);
                }
            }
        });
        
        // Set up key callback for backspace and enter
        backend.setKeyCallback(window.handle(), (key, scancode, action, mods) -> {
            EditBox focusedField = getFocusedField();
            if (focusedField != null) {
                if ((action == RenderBackend.ACTION_PRESS || action == RenderBackend.ACTION_REPEAT) && key == RenderBackend.KEY_BACKSPACE) {
                    focusedField.backspace();
                }
                if (action == RenderBackend.ACTION_PRESS && key == RenderBackend.KEY_ENTER) {
                    // Create world on Enter
                    createWorld();
                }
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
        titleCY = h * 0.18f;

        // Layout: world name field, seed field, then buttons below
        int firstTextFieldY = (int)(h * 0.30f);
        int fieldGap = 15;
        int totalButtonsH = 2 * buttonHeight + 1 * buttonGap;
        buttonsStartY = firstTextFieldY + 2 * textFieldHeight + fieldGap + 50;

        int x = (w - buttonWidth) / 2;
        int tfx = (w - textFieldWidth) / 2;
        buttons.clear();

        // Create text field with unique world name
        String defaultName = LevelStorageSource.generateUniqueWorldName("New World");
        worldNameField = new EditBox(tfx, firstTextFieldY, textFieldWidth, textFieldHeight, 50);
        worldNameField.setText(defaultName);
        worldNameField.setFocused(false); // Start unfocused
        
        // Create seed field with random seed
        long randomSeed = new java.util.Random().nextLong();
        seedField = new EditBox(tfx, firstTextFieldY + textFieldHeight + fieldGap, textFieldWidth, textFieldHeight, 100);
        seedField.setText(String.valueOf(randomSeed));
        seedField.setFocused(false);

        // Centered buttons
        buttons.add(new Button("Create World", x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Back",         x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
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
        if (worldNameField != null) worldNameField.setHover(worldNameField.contains(mxFB, myFB));
        if (seedField != null) seedField.setHover(seedField.contains(mxFB, myFB));

        if (mouseDown) {
            // Check text field clicks
            if (worldNameField != null && worldNameField.contains(mxFB, myFB)) {
                worldNameField.setFocused(true);
                if (seedField != null) seedField.setFocused(false);
            } else if (seedField != null && seedField.contains(mxFB, myFB)) {
                seedField.setFocused(true);
                if (worldNameField != null) worldNameField.setFocused(false);
            } else {
                if (worldNameField != null) worldNameField.setFocused(false);
                if (seedField != null) seedField.setFocused(false);
                
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
        
        // Parse seed from seed field
        String seedText = seedField.getText().trim();
        long seed = parseSeed(seedText);
        
        logger.info("→ Creating world: {} with seed: {}", worldName, seed);
        // Use the OpenGL-specific DevplayScreen for now (still needs refactoring)
        game.setScreen(new mattmc.client.renderer.backend.opengl.gui.screens.DevplayScreen(game, worldName, seed));
    }
    
    /**
     * Parse a seed from text input.
     * If the text is a valid long number, use it directly.
     * Otherwise, use the hash code of the string.
     * This matches Minecraft's behavior.
     */
    private long parseSeed(String seedText) {
        if (seedText.isEmpty()) {
            return new java.util.Random().nextLong();
        }
        
        try {
            // Try to parse as a long number
            return Long.parseLong(seedText);
        } catch (NumberFormatException e) {
            // Use hash code of the string (like Minecraft does)
            return seedText.hashCode();
        }
    }
    
    /**
     * Get the currently focused field, if any.
     */
    private EditBox getFocusedField() {
        if (worldNameField != null && worldNameField.isFocused()) {
            return worldNameField;
        }
        if (seedField != null && seedField.isFocused()) {
            return seedField;
        }
        return null;
    }
    
    /**
     * Check if a character is valid for world names.
     * Allows alphanumeric, space, hyphen, underscore, and parentheses.
     */
    private static boolean isValidWorldNameCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '(' || c == ')';
    }
    
    /**
     * Check if a character is printable (for seed input).
     * Allows most printable ASCII characters.
     */
    private static boolean isPrintableCharacter(char c) {
        return c >= 32 && c <= 126; // Printable ASCII range
    }

    @Override
    public void render(double alpha) {
        // Render panorama background with blur based on settings
        boolean blurred = OptionsManager.isMenuScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        backend.setup2DProjection(window.width(), window.height());
        
        // Draw text fields
        if (worldNameField != null) drawTextField(worldNameField);
        if (seedField != null) drawTextField(seedField);
        
        for (var b : buttons) {
            backend.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        drawTitle("Create New World", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("World Name:", titleCX, worldNameField.y - 20f, 1.0f, 0xB0C4DE);
        drawTitle("Seed:", titleCX, seedField.y - 20f, 1.0f, 0xB0C4DE);
    }

    private void drawTextField(EditBox tf) {
        // Background
        int bgColor = tf.isFocused() ? 0x000000 : 0x222222;
        backend.setColor(bgColor, 0.8f);
        backend.fillRect(tf.x, tf.y, tf.w, tf.h);
        
        // Border
        int borderColor = tf.isFocused() ? 0xFFFFFF : 0x888888;
        backend.setColor(borderColor, 1f);
        backend.drawRect(tf.x, tf.y, tf.w, tf.h);
        
        // Text
        String text = tf.getText();
        if (!text.isEmpty()) {
            drawText(text, tf.x + 8f, tf.y + 10f, 1.0f, 0xFFFFFF);
        }
        
        // Cursor
        if (tf.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            float textWidth = backend.getTextWidth(text, 1.0f);
            float cursorX = tf.x + 8f + textWidth;
            backend.setColor(0xFFFFFF, 1f);
            backend.drawLine(cursorX, tf.y + 6f, cursorX, tf.y + tf.h - 6f);
        }
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
        // Panorama is now shared and managed by Minecraft
    }
}
