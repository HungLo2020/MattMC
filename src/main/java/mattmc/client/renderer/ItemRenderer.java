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
            // Get the item model to check for tints and special rendering
            mattmc.client.resources.model.BlockModel itemModel = ResourceManager.resolveItemModel(itemName);
            
            // Check if this is a stairs block
            boolean isStairs = itemModel != null && itemModel.getParent() != null && 
                              itemModel.getParent().contains("stairs");
            
            if (isStairs) {
                // Render as isometric stairs
                renderIsometricStairs(texturePaths, itemModel, x, y, size);
            } else {
                // Render as isometric 3D cube
                renderIsometricCube(texturePaths, itemModel, x, y, size);
            }
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
     * Render an isometric cube showing three faces (top, left, and right).
     * This creates an orthographic 3D view of a block item matching Minecraft's style.
     */
    private static void renderIsometricCube(Map<String, String> texturePaths, mattmc.client.resources.model.BlockModel itemModel, float x, float y, float size) {
        // Get textures for each face
        String topTexture = getTextureForFace(texturePaths, "top");
        String sideTexture = getTextureForFace(texturePaths, "side");
        
        // Check if there are tints and get the tint color for the top face
        int topTintColor = 0xFFFFFF; // Default: no tint (white)
        if (itemModel != null && itemModel.getTints() != null && !itemModel.getTints().isEmpty()) {
            // Get the first tint (grass blocks typically have one tint)
            topTintColor = itemModel.getTints().get(0).getTintColor();
        }
        
        // Save GL state
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        glEnable(GL_TEXTURE_2D);
        
        // Define the isometric cube dimensions
        // Increased scale significantly for better visibility
        float scale = size * 2.0f;  // Doubled from original 1.0, 4x from 0.5
        
        // Isometric projection parameters
        float isoWidth = scale * 0.5f;   // Width of one face edge in isometric projection
        float isoHeight = scale * 0.5f;  // Height of one face edge in isometric projection
        
        // Draw the three visible faces in back-to-front order
        // These are the FRONT-facing sides (toward the camera)
        
        // 1. Left face (medium brightness - 80%)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                glBegin(GL_QUADS);
                // Left face vertices - slopes downward away from viewer
                glTexCoord2f(0, 0); glVertex2f(x, y);                                     // Near bottom
                glTexCoord2f(1, 0); glVertex2f(x - isoWidth, y - isoHeight * 0.5f);      // Far bottom
                glTexCoord2f(1, 1); glVertex2f(x - isoWidth, y - isoHeight * 1.5f);      // Far top
                glTexCoord2f(0, 1); glVertex2f(x, y - isoHeight);                         // Near top
                glEnd();
            }
        }
        
        // 2. Right face (darker - 60%)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                glBegin(GL_QUADS);
                // Right face vertices - slopes downward away from viewer
                glTexCoord2f(0, 0); glVertex2f(x + isoWidth, y - isoHeight * 0.5f);      // Far bottom
                glTexCoord2f(1, 0); glVertex2f(x, y);                                     // Near bottom
                glTexCoord2f(1, 1); glVertex2f(x, y - isoHeight);                         // Near top
                glTexCoord2f(0, 1); glVertex2f(x + isoWidth, y - isoHeight * 1.5f);      // Far top
                glEnd();
            }
        }
        
        // 3. Top face (brightest - 100% with tint applied)
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                // Apply tint color to the top face
                float r = ((topTintColor >> 16) & 0xFF) / 255.0f;
                float g = ((topTintColor >> 8) & 0xFF) / 255.0f;
                float b = (topTintColor & 0xFF) / 255.0f;
                glColor4f(r, g, b, 1.0f);
                glBegin(GL_QUADS);
                // Top face as diamond - connects the tops of the two side faces
                glTexCoord2f(0, 0); glVertex2f(x, y - isoHeight);                         // Near (bottom of diamond)
                glTexCoord2f(1, 0); glVertex2f(x - isoWidth, y - isoHeight * 1.5f);      // Left (left of diamond)
                glTexCoord2f(1, 1); glVertex2f(x, y - isoHeight * 2.0f);                  // Far (top of diamond)
                glTexCoord2f(0, 1); glVertex2f(x + isoWidth, y - isoHeight * 1.5f);      // Right (right of diamond)
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
     * Render isometric stairs by converting the in-game 3D geometry to 2D isometric projection.
     * Uses south-facing stairs geometry for proper isometric view: bottom slab (full block, half height) + 
     * top step (full width, south/front half depth, half height).
     */
    private static void renderIsometricStairs(Map<String, String> texturePaths, mattmc.client.resources.model.BlockModel itemModel, float x, float y, float size) {
        String topTexture = getTextureForFace(texturePaths, "top");
        String sideTexture = getTextureForFace(texturePaths, "side");
        
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        glEnable(GL_TEXTURE_2D);
        
        // Use same scaling as cubes
        float scale = size * 2.0f;
        float isoWidth = scale * 0.5f;
        float isoHeight = scale * 0.5f;
        
        // Render in back-to-front order for proper 2D depth (painter's algorithm - no depth buffer)
        
        // === BOTTOM SLAB: x[0,1], y[0,0.5], z[0,1] ===
        
        // Left face (west, x=0) - full depth of slab
        // This face should be visible on the left side in isometric view
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                glBegin(GL_QUADS);
                // For a face at x=0 going from z=0 to z=1, y=0 to y=0.5
                // Use the cube's left face pattern: near-bottom, far-bottom, far-top, near-top
                glTexCoord2f(0, 0.5f); glVertex2f(x, y);                                    // (0,0,0) near-bottom
                glTexCoord2f(1, 0.5f); glVertex2f(x - isoWidth, y - isoHeight * 0.5f);     // (0,0,1) far-bottom
                glTexCoord2f(1, 1);    glVertex2f(x - isoWidth, y - isoHeight);            // (0,0.5,1) far-top
                glTexCoord2f(0, 1);    glVertex2f(x, y - isoHeight * 0.5f);                // (0,0.5,0) near-top
                glEnd();
            }
        }
        
        // Right face (south, z=1) - full width of slab
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                glBegin(GL_QUADS);
                // For a face at z=1 going from x=0 to x=1, y=0 to y=0.5
                // Use the cube's right face pattern: far-bottom, near-bottom, near-top, far-top
                glTexCoord2f(0, 0.5f); glVertex2f(x + isoWidth, y - isoHeight * 0.5f);     // (1,0,1) far-bottom
                glTexCoord2f(1, 0.5f); glVertex2f(x, y);                                    // (0,0,1) near-bottom
                glTexCoord2f(1, 1);    glVertex2f(x, y - isoHeight * 0.5f);                // (0,0.5,1) near-top
                glTexCoord2f(0, 1);    glVertex2f(x + isoWidth, y - isoHeight);            // (1,0.5,1) far-top
                glEnd();
            }
        }
        
        // Top face of slab (full diamond at y=0.5)
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                glBegin(GL_QUADS);
                // Diamond: near, left, far, right (same pattern as cube top)
                glTexCoord2f(0, 0);       glVertex2f(x, y - isoHeight * 0.5f);              // (0,0.5,0) near
                glTexCoord2f(0.5f, 0);    glVertex2f(x - isoWidth, y - isoHeight);          // (0,0.5,1) left
                glTexCoord2f(0.5f, 0.5f); glVertex2f(x, y - isoHeight * 1.5f);              // (1,0.5,1) far
                glTexCoord2f(0, 0.5f);    glVertex2f(x + isoWidth, y - isoHeight);          // (1,0.5,0) right
                glEnd();
            }
        }
        
        // === TOP STEP: x[0,1], y[0.5,1.0], z[0.5,1] (south/front half) ===
        
        // West face of step (x=0, from z=0.5 to z=1, y=0.5 to y=1.0)
        // This should show the left side of the top step
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                glBegin(GL_QUADS);
                // Use cube's left face pattern adapted for the step
                // Near is z=0.5, far is z=1.0
                glTexCoord2f(0.5f, 0.5f); glVertex2f(x - isoWidth * 0.5f, y - isoHeight * 0.75f);  // (0,0.5,0.5) near-bottom
                glTexCoord2f(1, 0.5f);    glVertex2f(x - isoWidth, y - isoHeight);                  // (0,0.5,1) far-bottom
                glTexCoord2f(1, 1);       glVertex2f(x - isoWidth, y - isoHeight * 1.5f);           // (0,1.0,1) far-top
                glTexCoord2f(0.5f, 1);    glVertex2f(x - isoWidth * 0.5f, y - isoHeight * 1.25f);   // (0,1.0,0.5) near-top
                glEnd();
            }
        }
        
        // East face of step (x=1, from z=0.5 to z=1, y=0.5 to y=1.0)
        // This should show the right side of the top step
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                glBegin(GL_QUADS);
                // For east face, reverse the winding compared to west
                // Near is z=1.0, far is z=0.5 (because we're looking from the other side)
                glTexCoord2f(0, 0.5f);    glVertex2f(x + isoWidth, y - isoHeight);                  // (1,0.5,1) near-bottom
                glTexCoord2f(0.5f, 0.5f); glVertex2f(x + isoWidth * 0.5f, y - isoHeight * 0.75f);   // (1,0.5,0.5) far-bottom
                glTexCoord2f(0.5f, 1);    glVertex2f(x + isoWidth * 0.5f, y - isoHeight * 1.25f);   // (1,1.0,0.5) far-top
                glTexCoord2f(0, 1);       glVertex2f(x + isoWidth, y - isoHeight * 1.5f);           // (1,1.0,1) near-top
                glEnd();
            }
        }
        
        // South face of step (z=1, full width, y=0.5 to y=1.0)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                glBegin(GL_QUADS);
                // Right face pattern for south-facing surface
                glTexCoord2f(0, 0.5f); glVertex2f(x + isoWidth, y - isoHeight);            // (1,0.5,1) far-bottom
                glTexCoord2f(1, 0.5f); glVertex2f(x, y - isoHeight * 0.5f);                // (0,0.5,1) near-bottom
                glTexCoord2f(1, 1);    glVertex2f(x, y - isoHeight);                       // (0,1.0,1) near-top
                glTexCoord2f(0, 1);    glVertex2f(x + isoWidth, y - isoHeight * 1.5f);     // (1,1.0,1) far-top
                glEnd();
            }
        }
        
        // Inner vertical face (z=0.5, full width, y=0.5 to y=1.0) - north face of step
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                glBegin(GL_QUADS);
                // Right face pattern for the inner vertical step
                glTexCoord2f(0, 0.5f); glVertex2f(x + isoWidth * 0.5f, y - isoHeight * 0.75f);   // (1,0.5,0.5) far-bottom
                glTexCoord2f(1, 0.5f); glVertex2f(x - isoWidth * 0.5f, y - isoHeight * 0.75f);   // (0,0.5,0.5) near-bottom
                glTexCoord2f(1, 1);    glVertex2f(x - isoWidth * 0.5f, y - isoHeight * 1.25f);   // (0,1.0,0.5) near-top
                glTexCoord2f(0, 1);    glVertex2f(x + isoWidth * 0.5f, y - isoHeight * 1.25f);   // (1,1.0,0.5) far-top
                glEnd();
            }
        }
        
        // Top face of step (south half diamond at y=1.0)
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                glBegin(GL_QUADS);
                // Half diamond: near(z=0.5), left(z=1), far(z=1), right(z=0.5)
                glTexCoord2f(0.5f, 0);    glVertex2f(x - isoWidth * 0.5f, y - isoHeight * 1.25f);   // (0,1.0,0.5) near-left
                glTexCoord2f(0.5f, 0.5f); glVertex2f(x - isoWidth, y - isoHeight * 1.5f);            // (0,1.0,1) left
                glTexCoord2f(1, 0.5f);    glVertex2f(x, y - isoHeight * 1.5f);                       // (1,1.0,1) far
                glTexCoord2f(1, 0);       glVertex2f(x + isoWidth * 0.5f, y - isoHeight * 1.25f);    // (1,1.0,0.5) near-right
                glEnd();
            }
        }
        
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        glColor4f(1f, 1f, 1f, 1f);
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
