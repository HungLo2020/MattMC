package mattmc.client.renderer;

import mattmc.client.renderer.texture.Texture;
import mattmc.client.resources.ResourceManager;
import mattmc.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of items in the UI (hotbar, inventory, etc.).
 * Similar to Minecraft's ItemRenderer class.
 * 
 * For block items, renders an orthographic 3D view of the block.
 * For regular items, renders a 2D icon.
 */
public class ItemRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ItemRenderer.class);
    
    // Cache for item textures
    private static final Map<String, Texture> TEXTURE_CACHE = new HashMap<>();
    
    /**
     * Render an item at the specified screen position.
     * This uses orthographic projection for 3D block items.
     * 
     * @param stack The item stack to render
     * @param x Screen X position (center of item)
     * @param y Screen Y position (center of item)
     * @param size Size of the rendered item in pixels
     */
    public static void renderItem(ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        
        String itemId = stack.getItem().getIdentifier();
        if (itemId == null) {
            return;
        }
        
        // Extract item name from identifier (e.g., "mattmc:grass_block" -> "grass_block")
        String itemName = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        
        // Get texture paths for this item
        Map<String, String> texturePaths = ResourceManager.getItemTexturePaths(itemName);
        if (texturePaths == null || texturePaths.isEmpty()) {
            // Fallback: render magenta square
            renderFallbackItem(x, y, size);
            return;
        }
        
        // Check if this is a block item (has multiple texture faces)
        boolean isBlockItem = texturePaths.containsKey("top") || texturePaths.containsKey("side") || 
                              texturePaths.containsKey("bottom") || texturePaths.containsKey("all");
        
        if (isBlockItem) {
            renderBlockItem(texturePaths, x, y, size);
        } else {
            renderFlatItem(texturePaths, x, y, size);
        }
    }
    
    /**
     * Render a block item as an orthographic 3D view.
     * Shows the top, front-right, and right side faces in an isometric-like view.
     */
    private static void renderBlockItem(Map<String, String> texturePaths, float x, float y, float size) {
        glPushMatrix();
        
        // Save current state
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Set up orthographic projection for the item
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        
        // Create a small orthographic view centered on the item position
        float halfSize = size / 2f;
        glOrtho(x - halfSize, x + halfSize, y + halfSize, y - halfSize, -10, 10);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Position the block slightly in front
        glTranslatef(0, 0, -5);
        
        // Rotate to show an isometric-like view (30 degrees X, 45 degrees Y)
        glRotatef(30, 1, 0, 0);  // Tilt down slightly
        glRotatef(-45, 0, 1, 0); // Rotate to show corner
        
        // Scale to fit in the view
        float blockSize = size * 0.5f;
        glScalef(blockSize, blockSize, blockSize);
        
        // Draw a cube with the appropriate textures
        drawCubeFaces(texturePaths);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_TEXTURE_2D);
        
        glPopMatrix();
    }
    
    /**
     * Draw the faces of a cube with the given textures.
     */
    private static void drawCubeFaces(Map<String, String> texturePaths) {
        // Determine which textures to use for each face
        String topTexture = getTextureForFace(texturePaths, "top");
        String bottomTexture = getTextureForFace(texturePaths, "bottom");
        String sideTexture = getTextureForFace(texturePaths, "side");
        
        // Draw each face with its texture
        // Top face (brightest)
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0); glVertex3f(-0.5f, 0.5f, -0.5f);
                glTexCoord2f(1, 0); glVertex3f(0.5f, 0.5f, -0.5f);
                glTexCoord2f(1, 1); glVertex3f(0.5f, 0.5f, 0.5f);
                glTexCoord2f(0, 1); glVertex3f(-0.5f, 0.5f, 0.5f);
                glEnd();
            }
        }
        
        // Front face (80% brightness)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 1); glVertex3f(-0.5f, -0.5f, 0.5f);
                glTexCoord2f(1, 1); glVertex3f(0.5f, -0.5f, 0.5f);
                glTexCoord2f(1, 0); glVertex3f(0.5f, 0.5f, 0.5f);
                glTexCoord2f(0, 0); glVertex3f(-0.5f, 0.5f, 0.5f);
                glEnd();
            }
        }
        
        // Right face (60% brightness)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 1); glVertex3f(0.5f, -0.5f, 0.5f);
                glTexCoord2f(1, 1); glVertex3f(0.5f, -0.5f, -0.5f);
                glTexCoord2f(1, 0); glVertex3f(0.5f, 0.5f, -0.5f);
                glTexCoord2f(0, 0); glVertex3f(0.5f, 0.5f, 0.5f);
                glEnd();
            }
        }
    }
    
    /**
     * Get the appropriate texture path for a given face.
     */
    private static String getTextureForFace(Map<String, String> texturePaths, String faceKey) {
        // Try specific face first
        String texture = texturePaths.get(faceKey);
        if (texture != null) {
            return texture;
        }
        
        // Fall back to "all" texture
        texture = texturePaths.get("all");
        if (texture != null) {
            return texture;
        }
        
        // Fall back to any available texture
        return texturePaths.values().isEmpty() ? null : texturePaths.values().iterator().next();
    }
    
    /**
     * Render a flat 2D item icon.
     */
    private static void renderFlatItem(Map<String, String> texturePaths, float x, float y, float size) {
        // Get the main texture (layer0 for items)
        String texturePath = texturePaths.get("layer0");
        if (texturePath == null) {
            // Fallback to any available texture
            texturePath = texturePaths.values().iterator().next();
        }
        
        Texture texture = loadTexture(texturePath);
        if (texture == null) {
            renderFallbackItem(x, y, size);
            return;
        }
        
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        texture.bind();
        glColor4f(1f, 1f, 1f, 1f);
        
        float halfSize = size / 2f;
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(x - halfSize, y - halfSize);
        glTexCoord2f(1, 1); glVertex2f(x + halfSize, y - halfSize);
        glTexCoord2f(1, 0); glVertex2f(x + halfSize, y + halfSize);
        glTexCoord2f(0, 0); glVertex2f(x - halfSize, y + halfSize);
        glEnd();
        
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }
    
    /**
     * Render a fallback magenta square when texture is missing.
     */
    private static void renderFallbackItem(float x, float y, float size) {
        glColor4f(1f, 0f, 1f, 1f); // Magenta
        
        float halfSize = size / 2f;
        glBegin(GL_QUADS);
        glVertex2f(x - halfSize, y - halfSize);
        glVertex2f(x + halfSize, y - halfSize);
        glVertex2f(x + halfSize, y + halfSize);
        glVertex2f(x - halfSize, y + halfSize);
        glEnd();
    }
    
    /**
     * Load a texture from the cache or from disk.
     */
    private static Texture loadTexture(String path) {
        if (path == null) {
            return null;
        }
        
        // Check cache first
        if (TEXTURE_CACHE.containsKey(path)) {
            return TEXTURE_CACHE.get(path);
        }
        
        // Load texture
        try {
            // Convert path like "assets/textures/block/dirt.png" to "/assets/textures/block/dirt.png"
            String resourcePath = path.startsWith("/") ? path : "/" + path;
            Texture texture = Texture.load(resourcePath);
            TEXTURE_CACHE.put(path, texture);
            return texture;
        } catch (Exception e) {
            logger.warn("Failed to load texture: {}", path, e);
            return null;
        }
    }
    
    /**
     * Clear the texture cache.
     */
    public static void clearCache() {
        for (Texture texture : TEXTURE_CACHE.values()) {
            if (texture != null) {
                texture.close();
            }
        }
        TEXTURE_CACHE.clear();
    }
}
