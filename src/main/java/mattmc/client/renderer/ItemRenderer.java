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
     * Renders block items using 3D geometry with isometric projection.
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
            
            // Render as 3D block with isometric projection
            render3DBlockItem(texturePaths, itemModel, itemName, x, y, size);
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
     * Render a 3D block item using isometric projection.
     * This renders the actual block geometry (cubes, stairs, etc.) in 3D space
     * and projects it isometrically, eliminating the need for special-case rendering.
     */
    private static void render3DBlockItem(Map<String, String> texturePaths, 
                                          mattmc.client.resources.model.BlockModel itemModel,
                                          String itemName, float x, float y, float size) {
        // Save current GL state
        glPushMatrix();
        
        // Move to the item's screen position
        glTranslatef(x, y, 0);
        
        // Set up isometric projection
        // Scale to the desired size
        float scale = size * 16.0f; // Scale factor to make block visible
        glScalef(scale, -scale, scale); // Negative Y to flip to screen coordinates
        
        // Apply isometric rotation (rotate to show 3 faces)
        // Standard isometric view: rotate 45° around Y, then ~35.264° around X
        glRotatef(45, 0, 1, 0);     // Rotate around Y axis
        glRotatef(-30, 1, 0, 0);    // Rotate around X axis (closer to true isometric would be atan(1/sqrt(2)) ≈ 35.264°)
        
        // Center the block (move it so center is at origin)
        glTranslatef(-0.5f, -0.5f, -0.5f);
        
        // Save specific GL states we're going to change
        boolean wasTextureEnabled = glIsEnabled(GL_TEXTURE_2D);
        boolean wasDepthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        boolean wasBlendEnabled = glIsEnabled(GL_BLEND);
        
        // Enable texturing and depth test for 3D rendering
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glClear(GL_DEPTH_BUFFER_BIT); // Clear depth buffer for this item
        
        // Disable blending to ensure solid rendering
        glDisable(GL_BLEND);
        
        // Ensure polygons are filled (not wireframe)
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        
        // Check if this is a stairs block
        boolean isStairs = itemModel != null && itemModel.getParent() != null && 
                          itemModel.getParent().contains("stairs");
        
        if (isStairs) {
            // Render stairs geometry
            renderStairsGeometry(texturePaths, itemModel);
        } else {
            // Render cube geometry
            renderCubeGeometry(texturePaths, itemModel);
        }
        
        // Restore GL state
        if (!wasDepthTestEnabled) glDisable(GL_DEPTH_TEST);
        if (!wasTextureEnabled) glDisable(GL_TEXTURE_2D);
        if (wasBlendEnabled) glEnable(GL_BLEND);
        
        glPopMatrix();
    }
    
    /**
     * Render a cube block in 3D space (0,0,0 to 1,1,1).
     */
    private static void renderCubeGeometry(Map<String, String> texturePaths, 
                                           mattmc.client.resources.model.BlockModel itemModel) {
        String topTexture = getTextureForFace(texturePaths, "top");
        String sideTexture = getTextureForFace(texturePaths, "side");
        String bottomTexture = getTextureForFace(texturePaths, "bottom");
        
        // Get tint color if any
        int topTintColor = 0xFFFFFF;
        if (itemModel != null && itemModel.getTints() != null && !itemModel.getTints().isEmpty()) {
            topTintColor = itemModel.getTints().get(0).getTintColor();
        }
        
        // Draw all 6 faces of the cube
        // Top face (y=1)
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                float r = ((topTintColor >> 16) & 0xFF) / 255.0f;
                float g = ((topTintColor >> 8) & 0xFF) / 255.0f;
                float b = (topTintColor & 0xFF) / 255.0f;
                glColor3f(r, g, b);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0); glVertex3f(0, 1, 0);
                glTexCoord2f(0, 1); glVertex3f(0, 1, 1);
                glTexCoord2f(1, 1); glVertex3f(1, 1, 1);
                glTexCoord2f(1, 0); glVertex3f(1, 1, 0);
                glEnd();
            }
        }
        
        // Bottom face (y=0)
        if (bottomTexture != null) {
            Texture tex = loadTexture(bottomTexture);
            if (tex != null) {
                tex.bind();
                glColor3f(0.5f, 0.5f, 0.5f); // Darken bottom
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0); glVertex3f(0, 0, 1);
                glTexCoord2f(0, 1); glVertex3f(0, 0, 0);
                glTexCoord2f(1, 1); glVertex3f(1, 0, 0);
                glTexCoord2f(1, 0); glVertex3f(1, 0, 1);
                glEnd();
            }
        }
        
        // Side faces (darker than top)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                
                // North face (z=0)
                glColor3f(0.8f, 0.8f, 0.8f);
                glBegin(GL_QUADS);
                glTexCoord2f(1, 0); glVertex3f(1, 0, 0);
                glTexCoord2f(0, 0); glVertex3f(0, 0, 0);
                glTexCoord2f(0, 1); glVertex3f(0, 1, 0);
                glTexCoord2f(1, 1); glVertex3f(1, 1, 0);
                glEnd();
                
                // South face (z=1)
                glColor3f(0.8f, 0.8f, 0.8f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0); glVertex3f(0, 0, 1);
                glTexCoord2f(1, 0); glVertex3f(1, 0, 1);
                glTexCoord2f(1, 1); glVertex3f(1, 1, 1);
                glTexCoord2f(0, 1); glVertex3f(0, 1, 1);
                glEnd();
                
                // West face (x=0)
                glColor3f(0.6f, 0.6f, 0.6f);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 0); glVertex3f(0, 0, 0);
                glTexCoord2f(1, 0); glVertex3f(0, 0, 1);
                glTexCoord2f(1, 1); glVertex3f(0, 1, 1);
                glTexCoord2f(0, 1); glVertex3f(0, 1, 0);
                glEnd();
                
                // East face (x=1)
                glColor3f(0.6f, 0.6f, 0.6f);
                glBegin(GL_QUADS);
                glTexCoord2f(1, 0); glVertex3f(1, 0, 1);
                glTexCoord2f(0, 0); glVertex3f(1, 0, 0);
                glTexCoord2f(0, 1); glVertex3f(1, 1, 0);
                glTexCoord2f(1, 1); glVertex3f(1, 1, 1);
                glEnd();
            }
        }
        
        glColor3f(1, 1, 1); // Reset color
    }
    
    /**
     * Render stairs geometry in 3D space.
     * Stairs consist of a bottom slab (full width, half height) 
     * and a top step (back half, half height).
     */
    private static void renderStairsGeometry(Map<String, String> texturePaths,
                                             mattmc.client.resources.model.BlockModel itemModel) {
        String topTexture = getTextureForFace(texturePaths, "top");
        String sideTexture = getTextureForFace(texturePaths, "side");
        String bottomTexture = getTextureForFace(texturePaths, "bottom");
        
        if (sideTexture == null || topTexture == null) {
            return;
        }
        
        Texture sideTex = loadTexture(sideTexture);
        Texture topTex = loadTexture(topTexture);
        Texture bottomTex = bottomTexture != null ? loadTexture(bottomTexture) : sideTex;
        
        if (sideTex == null || topTex == null) {
            return;
        }
        
        // Bottom slab: from y=0 to y=0.5, full width and depth (x: 0-1, z: 0-1)
        
        // Bottom slab - Top face (y=0.5)
        topTex.bind();
        glColor3f(1, 1, 1);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex3f(0, 0.5f, 0);
        glTexCoord2f(0, 1); glVertex3f(0, 0.5f, 1);
        glTexCoord2f(1, 1); glVertex3f(1, 0.5f, 1);
        glTexCoord2f(1, 0); glVertex3f(1, 0.5f, 0);
        glEnd();
        
        // Bottom slab - Bottom face (y=0)
        if (bottomTex != null) {
            bottomTex.bind();
            glColor3f(0.5f, 0.5f, 0.5f);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex3f(0, 0, 1);
            glTexCoord2f(0, 0); glVertex3f(0, 0, 0);
            glTexCoord2f(1, 0); glVertex3f(1, 0, 0);
            glTexCoord2f(1, 1); glVertex3f(1, 0, 1);
            glEnd();
        }
        
        // Bottom slab - Side faces (half height, use bottom half of texture)
        sideTex.bind();
        
        // North face (z=0)
        glColor3f(0.8f, 0.8f, 0.8f);
        glBegin(GL_QUADS);
        glTexCoord2f(1, 0.5f); glVertex3f(1, 0, 0);
        glTexCoord2f(0, 0.5f); glVertex3f(0, 0, 0);
        glTexCoord2f(0, 1);    glVertex3f(0, 0.5f, 0);
        glTexCoord2f(1, 1);    glVertex3f(1, 0.5f, 0);
        glEnd();
        
        // South face (z=1)
        glColor3f(0.8f, 0.8f, 0.8f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0.5f); glVertex3f(0, 0, 1);
        glTexCoord2f(1, 0.5f); glVertex3f(1, 0, 1);
        glTexCoord2f(1, 1);    glVertex3f(1, 0.5f, 1);
        glTexCoord2f(0, 1);    glVertex3f(0, 0.5f, 1);
        glEnd();
        
        // West face (x=0)
        glColor3f(0.6f, 0.6f, 0.6f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0.5f); glVertex3f(0, 0, 0);
        glTexCoord2f(1, 0.5f); glVertex3f(0, 0, 1);
        glTexCoord2f(1, 1);    glVertex3f(0, 0.5f, 1);
        glTexCoord2f(0, 1);    glVertex3f(0, 0.5f, 0);
        glEnd();
        
        // East face (x=1)
        glColor3f(0.6f, 0.6f, 0.6f);
        glBegin(GL_QUADS);
        glTexCoord2f(1, 0.5f); glVertex3f(1, 0, 1);
        glTexCoord2f(0, 0.5f); glVertex3f(1, 0, 0);
        glTexCoord2f(0, 1);    glVertex3f(1, 0.5f, 0);
        glTexCoord2f(1, 1);    glVertex3f(1, 0.5f, 1);
        glEnd();
        
        // Top step: from y=0.5 to y=1.0, back half only (x: 0-1, z: 0-0.5)
        
        // Top step - Top face (y=1.0)
        topTex.bind();
        glColor3f(1, 1, 1);
        glBegin(GL_QUADS);
        glTexCoord2f(0.5f, 0);   glVertex3f(0, 1, 0);
        glTexCoord2f(0.5f, 0.5f); glVertex3f(0, 1, 0.5f);
        glTexCoord2f(1, 0.5f);   glVertex3f(1, 1, 0.5f);
        glTexCoord2f(1, 0);      glVertex3f(1, 1, 0);
        glEnd();
        
        // Top step - Side faces
        sideTex.bind();
        
        // North face (z=0) - full height for this part
        glColor3f(0.8f, 0.8f, 0.8f);
        glBegin(GL_QUADS);
        glTexCoord2f(1, 0.5f); glVertex3f(1, 0.5f, 0);
        glTexCoord2f(0, 0.5f); glVertex3f(0, 0.5f, 0);
        glTexCoord2f(0, 1);    glVertex3f(0, 1, 0);
        glTexCoord2f(1, 1);    glVertex3f(1, 1, 0);
        glEnd();
        
        // Front face (z=0.5) - vertical face at front of step
        glColor3f(0.8f, 0.8f, 0.8f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0.5f); glVertex3f(0, 0.5f, 0.5f);
        glTexCoord2f(1, 0.5f); glVertex3f(1, 0.5f, 0.5f);
        glTexCoord2f(1, 1);    glVertex3f(1, 1, 0.5f);
        glTexCoord2f(0, 1);    glVertex3f(0, 1, 0.5f);
        glEnd();
        
        // West face (x=0) - back half only
        glColor3f(0.6f, 0.6f, 0.6f);
        glBegin(GL_QUADS);
        glTexCoord2f(0.5f, 0.5f); glVertex3f(0, 0.5f, 0);
        glTexCoord2f(1, 0.5f);    glVertex3f(0, 0.5f, 0.5f);
        glTexCoord2f(1, 1);       glVertex3f(0, 1, 0.5f);
        glTexCoord2f(0.5f, 1);    glVertex3f(0, 1, 0);
        glEnd();
        
        // East face (x=1) - back half only
        glColor3f(0.6f, 0.6f, 0.6f);
        glBegin(GL_QUADS);
        glTexCoord2f(1, 0.5f);    glVertex3f(1, 0.5f, 0.5f);
        glTexCoord2f(0.5f, 0.5f); glVertex3f(1, 0.5f, 0);
        glTexCoord2f(0.5f, 1);    glVertex3f(1, 1, 0);
        glTexCoord2f(1, 1);       glVertex3f(1, 1, 0.5f);
        glEnd();
        
        glColor3f(1, 1, 1); // Reset color
    }
    
    /**
     * Render an isometric cube showing three faces (top, left, and right).
     * This creates an orthographic 3D view of a block item matching Minecraft's style.
     */
    
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
