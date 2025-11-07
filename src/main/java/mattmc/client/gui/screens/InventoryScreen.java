package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.Window;
import mattmc.client.renderer.BlurEffect;
import mattmc.client.renderer.BlurRenderer;
import mattmc.client.renderer.texture.Texture;
import mattmc.world.entity.player.PlayerInput;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Inventory screen overlay - displays the inventory.png centered on screen.
 * Does not pause the game. Press E (or configured inventory key) to close.
 */
public final class InventoryScreen implements Screen {
    private final Minecraft game;
    private final Window window;
    private final DevplayScreen gameScreen;
    private Texture inventoryTexture;
    
    // Blur effect for background
    private BlurEffect blurEffect;

    public InventoryScreen(Minecraft game, DevplayScreen gameScreen) {
        this.game = game;
        this.window = game.window();
        this.gameScreen = gameScreen;
        
        // Sync player position to prevent flickering during interpolation
        gameScreen.syncPlayerPosition();

        // Load inventory texture
        inventoryTexture = Texture.load("/assets/textures/gui/container/inventory.png");

        // Release mouse cursor
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);

        // Disable mouse movement callback to prevent camera rotation
        glfwSetCursorPosCallback(window.handle(), null);
        
        // Disable mouse button callback to prevent block interaction
        glfwSetMouseButtonCallback(window.handle(), null);

        // Set up key callback for inventory key (respects user configuration) or ESC to close
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_ESCAPE) {
                    closeInventory();
                } else {
                    // Check if this is the inventory key
                    Integer inventoryKey = PlayerInput.getInstance().getKeybind(PlayerInput.INVENTORY);
                    if (inventoryKey != null && key == inventoryKey) {
                        closeInventory();
                    }
                }
            }
        });

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
        });
    }

    private void closeInventory() {
        // Recapture mouse for FPS controls
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        game.setScreen(gameScreen);
    }

    @Override
    public void tick() {
        // Don't update the game while inventory is open - player should not move
        // and game is effectively paused (similar to pause menu behavior)
    }

    @Override
    public void render(double alpha) {
        int w = window.width(), h = window.height();
        
        // First render the game screen behind this overlay
        gameScreen.render(alpha);
        
        // Apply blur if enabled
        if (mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled()) {
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
        
        // Semi-transparent black background
        setColor(0x000000, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(w, 0);
        glVertex2f(w, h);
        glVertex2f(0, h);
        glEnd();
        
        // Draw inventory texture centered
        if (inventoryTexture != null) {
            glEnable(GL_TEXTURE_2D);
            inventoryTexture.bind();
            glColor4f(1f, 1f, 1f, 1f);
            
            // Scale inventory to 3x size for better visibility (like Minecraft GUI scale)
            float scale = 3.0f;
            // The inventory texture is 256x256 but the actual content is ~176x166 centered
            // We need to offset by the empty space around the content
            float contentOffsetX = 40f; // Offset from left edge of texture to content
            float contentOffsetY = 45f; // Offset from top edge of texture to content
            float texWidth = inventoryTexture.width * scale;
            float texHeight = inventoryTexture.height * scale;
            float x = (w - texWidth) / 2f + (contentOffsetX * scale);
            float y = (h - texHeight) / 2f + (contentOffsetY * scale);
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(x, y);
            glTexCoord2f(1, 1); glVertex2f(x + texWidth, y);
            glTexCoord2f(1, 0); glVertex2f(x + texWidth, y + texHeight);
            glTexCoord2f(0, 0); glVertex2f(x, y + texHeight);
            glEnd();
            
            glDisable(GL_TEXTURE_2D);
        }
        
        glDisable(GL_BLEND);
    }

    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }

    @Override
    public void onOpen() {}
    
    @Override
    public void onClose() {
        if (inventoryTexture != null) {
            inventoryTexture.close();
            inventoryTexture = null;
        }
        if (blurEffect != null) {
            blurEffect.close();
            blurEffect = null;
        }
    }
}
