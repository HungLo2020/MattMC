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
     * Renders block items as orthographic 3D cubes (isometric view).
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
        
        // Check if this is a block item (has block textures)
        boolean isBlockItem = texturePaths.containsKey("all") || texturePaths.containsKey("top") || 
                              texturePaths.containsKey("side") || texturePaths.containsKey("bottom");
        
        if (isBlockItem) {
            // Render as isometric 3D cube
            renderIsometricCube(texturePaths, x, y, size);
        } else {
            // Render as flat 2D icon (for non-block items)
            String texturePath = texturePaths.get("layer0");
            if (texturePath == null) {
                texturePath = texturePaths.values().iterator().next();
            }
            if (texturePath != null) {
                renderTextureAsFlat(texturePath, x, y, size);
            } else {
                renderFallbackItem(x, y, size);
            }
        }
    }
    
    /**
     * Render an isometric cube showing three faces (top, front-right, and right).
     * This creates an orthographic 3D view of a block item.
     */
    private static void renderIsometricCube(Map<String, String> texturePaths, float x, float y, float size) {
        // Get textures for each face
        String topTexture = getTextureForFace(texturePaths, "top");
        String sideTexture = getTextureForFace(texturePaths, "side");
        
        // Save GL state
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        glEnable(GL_TEXTURE_2D);
        
        // Define the isometric cube dimensions
        // Using a 2:1 pixel ratio for isometric projection (standard in Minecraft)
        float cubeSize = size * 0.85f; // Slightly smaller to fit nicely in the slot
        float faceWidth = cubeSize * 0.5f;  // Half width for each face
        float faceHeight = cubeSize * 0.5f; // Half height for each face
        
        // Isometric angle offsets (26.565 degrees for true isometric, but we'll use simpler values)
        float xOffset = faceWidth * 0.866f;  // cos(30°) ≈ 0.866
        float yOffset = faceHeight * 0.5f;   // sin(30°) = 0.5
        
        // Draw the three visible faces in back-to-front order for proper layering
        
        // 1. Right face (darkest - 60% brightness)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                glBegin(GL_QUADS);
                // Right face as parallelogram
                glTexCoord2f(0, 0); glVertex2f(x, y - yOffset);                          // Top center
                glTexCoord2f(1, 0); glVertex2f(x + xOffset, y);                          // Top right
                glTexCoord2f(1, 1); glVertex2f(x + xOffset, y + faceHeight);            // Bottom right
                glTexCoord2f(0, 1); glVertex2f(x, y + faceHeight - yOffset);            // Bottom center
                glEnd();
            }
        }
        
        // 2. Front-left face (medium - 80% brightness)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                glBegin(GL_QUADS);
                // Left face as parallelogram
                glTexCoord2f(0, 0); glVertex2f(x - xOffset, y);                          // Top left
                glTexCoord2f(1, 0); glVertex2f(x, y - yOffset);                          // Top center
                glTexCoord2f(1, 1); glVertex2f(x, y + faceHeight - yOffset);            // Bottom center
                glTexCoord2f(0, 1); glVertex2f(x - xOffset, y + faceHeight);            // Bottom left
                glEnd();
            }
        }
        
        // 3. Top face (brightest - 100% brightness)
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                glBegin(GL_QUADS);
                // Top face as diamond/rhombus
                glTexCoord2f(0, 0); glVertex2f(x, y - yOffset - faceHeight);            // Top
                glTexCoord2f(1, 0); glVertex2f(x + xOffset, y - faceHeight);            // Right
                glTexCoord2f(1, 1); glVertex2f(x, y - yOffset);                          // Bottom
                glTexCoord2f(0, 1); glVertex2f(x - xOffset, y - faceHeight);            // Left
                glEnd();
            }
        }
        
        // Restore GL state
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        glColor4f(1f, 1f, 1f, 1f); // Reset color
    }
    
    /**
     * Get the texture for a specific face of a block.
     * Falls back to "all" texture if specific face not found.
     */
    private static String getTextureForFace(Map<String, String> texturePaths, String faceKey) {
        // Try specific face first
        String texture = texturePaths.get(faceKey);
        if (texture != null) {
            return texture;
        }
        
        // Fall back to "all" texture (for cube_all blocks)
        texture = texturePaths.get("all");
        if (texture != null) {
            return texture;
        }
        
        // Fall back to any available texture
        return texturePaths.values().isEmpty() ? null : texturePaths.values().iterator().next();
    }
    
    /**
     * Get the main texture to display for an item.
     * Priority: all > top > side > layer0 > first available
     */
    private static String getMainTexture(Map<String, String> texturePaths) {
        // Try "all" first (for cube_all blocks like stone, dirt)
        String texture = texturePaths.get("all");
        if (texture != null) {
            return texture;
        }
        
        // Try "top" (for blocks like grass)
        texture = texturePaths.get("top");
        if (texture != null) {
            return texture;
        }
        
        // Try "side"
        texture = texturePaths.get("side");
        if (texture != null) {
            return texture;
        }
        
        // Try "layer0" (for flat items)
        texture = texturePaths.get("layer0");
        if (texture != null) {
            return texture;
        }
        
        // Return first available
        return texturePaths.values().isEmpty() ? null : texturePaths.values().iterator().next();
    }
    
    /**
     * Render a texture as a flat 2D square.
     */
    private static void renderTextureAsFlat(String texturePath, float x, float y, float size) {
        Texture texture = loadTexture(texturePath);
        if (texture == null) {
            renderFallbackItem(x, y, size);
            return;
        }
        
        // Save current GL state
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        
        glEnable(GL_TEXTURE_2D);
        texture.bind();
        glColor4f(1f, 1f, 1f, 1f);
        
        float halfSize = size / 2f;
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(x - halfSize, y - halfSize);
        glTexCoord2f(1, 0); glVertex2f(x + halfSize, y - halfSize);
        glTexCoord2f(1, 1); glVertex2f(x + halfSize, y + halfSize);
        glTexCoord2f(0, 1); glVertex2f(x - halfSize, y + halfSize);
        glEnd();
        
        // Restore GL state
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
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
