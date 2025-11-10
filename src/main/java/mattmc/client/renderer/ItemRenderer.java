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
    /**
     * Render stairs as an isometric 3D block with proper stepped geometry.
     * Closely follows Minecraft's approach: uses the exact same 3D geometry as the in-game
     * stairs (from BlockFaceGeometry.drawStairsNorthBottom) and projects it to 2D isometric view.
     *
     * The stairs consist of two parts:
     * - Bottom slab: (0,0,0) to (1,0.5,1) - full block width, half height
     * - Top step: (0,0.5,0) to (1,1,0.5) - full width, north half depth, top half height
     */
    private static void renderIsometricStairs(Map<String, String> texturePaths, mattmc.client.resources.model.BlockModel itemModel, float x, float y, float size) {
        // Get textures for each face
        String topTexture = getTextureForFace(texturePaths, "top");
        String sideTexture = getTextureForFace(texturePaths, "side");
        
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        if (!textureWasEnabled) {
            glEnable(GL_TEXTURE_2D);
        }
        
        // Isometric projection parameters (same as cube)
        // For 3D point (wx, wy, wz), the 2D screen coordinates are:
        // screen_x = x + (wx - wz) * isoWidth
        // screen_y = y - wy * isoHeight - (wx + wz) * isoHeight * 0.5
        float isoWidth = size * 2.0f;
        float isoHeight = size * 1.0f;
        
        // === BOTTOM SLAB: (0,0,0) to (1,0.5,1) ===
        
        // West face of bottom slab (x=0, z=0→1, y=0→0.5)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                glBegin(GL_QUADS);
                // Vertices from BlockFaceGeometry pattern: (x0,y0,z0), (x0,y0,z1), (x0,y0.5,z1), (x0,y0.5,z0)
                glTexCoord2f(0, 1);    glVertex2f(project2Dx(0, 0, 0, x, isoWidth),    project2Dy(0, 0, 0, y, isoHeight));      // (0,0,0)
                glTexCoord2f(1, 1);    glVertex2f(project2Dx(0, 0, 1, x, isoWidth),    project2Dy(0, 0, 1, y, isoHeight));      // (0,0,1)
                glTexCoord2f(1, 0.5f); glVertex2f(project2Dx(0, 0.5f, 1, x, isoWidth), project2Dy(0, 0.5f, 1, y, isoHeight));   // (0,0.5,1)
                glTexCoord2f(0, 0.5f); glVertex2f(project2Dx(0, 0.5f, 0, x, isoWidth), project2Dy(0, 0.5f, 0, y, isoHeight));   // (0,0.5,0)
                glEnd();
            }
        }
        
        // South face of bottom slab (z=1, x=0→1, y=0→0.5)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                glBegin(GL_QUADS);
                // Vertices from BlockFaceGeometry pattern: (x0,y0,z1), (x1,y0,z1), (x1,y0.5,z1), (x0,y0.5,z1)
                glTexCoord2f(0, 1);    glVertex2f(project2Dx(0, 0, 1, x, isoWidth),    project2Dy(0, 0, 1, y, isoHeight));      // (0,0,1)
                glTexCoord2f(1, 1);    glVertex2f(project2Dx(1, 0, 1, x, isoWidth),    project2Dy(1, 0, 1, y, isoHeight));      // (1,0,1)
                glTexCoord2f(1, 0.5f); glVertex2f(project2Dx(1, 0.5f, 1, x, isoWidth), project2Dy(1, 0.5f, 1, y, isoHeight));   // (1,0.5,1)
                glTexCoord2f(0, 0.5f); glVertex2f(project2Dx(0, 0.5f, 1, x, isoWidth), project2Dy(0, 0.5f, 1, y, isoHeight));   // (0,0.5,1)
                glEnd();
            }
        }
        
        // Top face of bottom slab (y=0.5, x=0→1, z=0→1)
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                glBegin(GL_QUADS);
                // Vertices from BlockFaceGeometry pattern (for top face)
                glTexCoord2f(0, 0); glVertex2f(project2Dx(0, 0.5f, 0, x, isoWidth), project2Dy(0, 0.5f, 0, y, isoHeight));   // (0,0.5,0)
                glTexCoord2f(0, 1); glVertex2f(project2Dx(0, 0.5f, 1, x, isoWidth), project2Dy(0, 0.5f, 1, y, isoHeight));   // (0,0.5,1)
                glTexCoord2f(1, 1); glVertex2f(project2Dx(1, 0.5f, 1, x, isoWidth), project2Dy(1, 0.5f, 1, y, isoHeight));   // (1,0.5,1)
                glTexCoord2f(1, 0); glVertex2f(project2Dx(1, 0.5f, 0, x, isoWidth), project2Dy(1, 0.5f, 0, y, isoHeight));   // (1,0.5,0)
                glEnd();
            }
        }
        
        // === TOP STEP: (0,0.5,0) to (1,1,0.5) - north half only ===
        
        // West face of top step (x=0, z=0→0.5, y=0.5→1)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0.5f);   glVertex2f(project2Dx(0, 0.5f, 0, x, isoWidth),   project2Dy(0, 0.5f, 0, y, isoHeight));    // (0,0.5,0)
                glTexCoord2f(0.5f, 0.5f); glVertex2f(project2Dx(0, 0.5f, 0.5f, x, isoWidth), project2Dy(0, 0.5f, 0.5f, y, isoHeight));  // (0,0.5,0.5)
                glTexCoord2f(0.5f, 0);    glVertex2f(project2Dx(0, 1, 0.5f, x, isoWidth),    project2Dy(0, 1, 0.5f, y, isoHeight));     // (0,1,0.5)
                glTexCoord2f(0, 0);       glVertex2f(project2Dx(0, 1, 0, x, isoWidth),       project2Dy(0, 1, 0, y, isoHeight));        // (0,1,0)
                glEnd();
            }
        }
        
        // North face of top step (z=0, x=0→1, y=0.5→1)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(1, 0.5f); glVertex2f(project2Dx(1, 0.5f, 0, x, isoWidth), project2Dy(1, 0.5f, 0, y, isoHeight));   // (1,0.5,0)
                glTexCoord2f(0, 0.5f); glVertex2f(project2Dx(0, 0.5f, 0, x, isoWidth), project2Dy(0, 0.5f, 0, y, isoHeight));   // (0,0.5,0)
                glTexCoord2f(0, 0);    glVertex2f(project2Dx(0, 1, 0, x, isoWidth),    project2Dy(0, 1, 0, y, isoHeight));      // (0,1,0)
                glTexCoord2f(1, 0);    glVertex2f(project2Dx(1, 1, 0, x, isoWidth),    project2Dy(1, 1, 0, y, isoHeight));      // (1,1,0)
                glEnd();
            }
        }
        
        // South face of top step (z=0.5, x=0→1, y=0.5→1) - the inner vertical step face
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0.5f); glVertex2f(project2Dx(0, 0.5f, 0.5f, x, isoWidth), project2Dy(0, 0.5f, 0.5f, y, isoHeight));   // (0,0.5,0.5)
                glTexCoord2f(1, 0.5f); glVertex2f(project2Dx(1, 0.5f, 0.5f, x, isoWidth), project2Dy(1, 0.5f, 0.5f, y, isoHeight));   // (1,0.5,0.5)
                glTexCoord2f(1, 0);    glVertex2f(project2Dx(1, 1, 0.5f, x, isoWidth),    project2Dy(1, 1, 0.5f, y, isoHeight));      // (1,1,0.5)
                glTexCoord2f(0, 0);    glVertex2f(project2Dx(0, 1, 0.5f, x, isoWidth),    project2Dy(0, 1, 0.5f, y, isoHeight));      // (0,1,0.5)
                glEnd();
            }
        }
        
        // East face of top step (x=1, z=0→0.5, y=0.5→1)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(0.5f, 0.5f); glVertex2f(project2Dx(1, 0.5f, 0.5f, x, isoWidth), project2Dy(1, 0.5f, 0.5f, y, isoHeight));  // (1,0.5,0.5)
                glTexCoord2f(0, 0.5f);    glVertex2f(project2Dx(1, 0.5f, 0, x, isoWidth),    project2Dy(1, 0.5f, 0, y, isoHeight));     // (1,0.5,0)
                glTexCoord2f(0, 0);       glVertex2f(project2Dx(1, 1, 0, x, isoWidth),       project2Dy(1, 1, 0, y, isoHeight));        // (1,1,0)
                glTexCoord2f(0.5f, 0);    glVertex2f(project2Dx(1, 1, 0.5f, x, isoWidth),    project2Dy(1, 1, 0.5f, y, isoHeight));     // (1,1,0.5)
                glEnd();
            }
        }
        
        // Top face of top step (y=1, x=0→1, z=0→0.5)
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0);    glVertex2f(project2Dx(0, 1, 0, x, isoWidth),    project2Dy(0, 1, 0, y, isoHeight));      // (0,1,0)
                glTexCoord2f(0, 0.5f); glVertex2f(project2Dx(0, 1, 0.5f, x, isoWidth), project2Dy(0, 1, 0.5f, y, isoHeight));   // (0,1,0.5)
                glTexCoord2f(1, 0.5f); glVertex2f(project2Dx(1, 1, 0.5f, x, isoWidth), project2Dy(1, 1, 0.5f, y, isoHeight));   // (1,1,0.5)
                glTexCoord2f(1, 0);    glVertex2f(project2Dx(1, 1, 0, x, isoWidth),    project2Dy(1, 1, 0, y, isoHeight));      // (1,1,0)
                glEnd();
            }
        }
        
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        glColor4f(1f, 1f, 1f, 1f);
    }
    
    /**
     * Project a 3D world coordinate to 2D isometric X coordinate.
     * Formula: screen_x = centerX + (wx - wz) * isoWidth
     */
    private static float project2Dx(float wx, float wy, float wz, float centerX, float isoWidth) {
        return centerX + (wx - wz) * isoWidth;
    }
    
    /**
     * Project a 3D world coordinate to 2D isometric Y coordinate.
     * Formula: screen_y = centerY - wy * isoHeight - (wx + wz) * isoHeight * 0.5
     */
    private static float project2Dy(float wx, float wy, float wz, float centerY, float isoHeight) {
        return centerY - wy * isoHeight - (wx + wz) * isoHeight * 0.5f;
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
