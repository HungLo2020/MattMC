package mattmc.client.renderer;

import mattmc.client.renderer.model.BakedModel;
import mattmc.client.renderer.model.BakedQuad;
import mattmc.client.renderer.model.ModelBakery;
import mattmc.client.renderer.texture.Texture;
import mattmc.client.resources.ResourceManager;
import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.ModelDisplay;
import mattmc.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of items in the UI (hotbar, inventory, etc.).
 * Matches Minecraft's ItemRenderer class using BakedModel system.
 * 
 * Uses JSON model-based rendering with display transforms for proper 3D perspective
 * rendering, matching Minecraft's approach EXACTLY (except for namespace).
 */
public class ItemRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ItemRenderer.class);
    
    // Cache for item textures
    private static final Map<String, Texture> TEXTURE_CACHE = new HashMap<>();
    
    /**
     * Render an item at the specified screen position.
     * Uses BakedModel system with perspective 3D rendering, matching Minecraft.
     * 
     * @param stack The item stack to render
     * @param x Screen X position (center of item)
     * @param y Screen Y position (center of item)
     * @param size Size of the rendered item in pixels
     */
    public static void renderItem(ItemStack stack, float x, float y, float size) {
        renderItem(stack, x, y, size, false);
    }
    
    /**
     * Render an item at the specified screen position.
     * Uses BakedModel system with perspective 3D rendering, matching Minecraft.
     * 
     * @param stack The item stack to render
     * @param x Screen X position (center of item)
     * @param y Screen Y position (center of item)
     * @param size Size of the rendered item in pixels
     * @param applyInventoryOffset Apply +18f Y offset for inventory screen rendering
     */
    public static void renderItem(ItemStack stack, float x, float y, float size, boolean applyInventoryOffset) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        
        String itemId = stack.getItem().getIdentifier();
        if (itemId == null) {
            return;
        }
        
        // Extract item name from identifier (e.g., "mattmc:grass_block" -> "grass_block")
        String itemName = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        
        // Bake the item model
        BakedModel bakedModel = ModelBakery.bakeItemModel(itemName);
        if (bakedModel == null) {
            // Fallback: render magenta square
            renderFallbackItem(x, y, size);
            return;
        }
        
        // Get the item model for tint information
        BlockModel itemModel = ResourceManager.resolveItemModel(itemName);
        
        // Apply inventory offset if requested
        float adjustedY = applyInventoryOffset ? y + 18f : y;
        
        // Render the baked model with perspective 3D
        renderBakedModel(bakedModel, itemModel, x, adjustedY, size);
    }
    
    /**
     * Render a baked model with isometric/orthographic 3D projection.
     * Renders all quads from the model to show a 3D representation,
     * matching Minecraft's item rendering approach.
     */
    private static void renderBakedModel(BakedModel bakedModel, BlockModel itemModel, float x, float y, float size) {
        // Save GL state
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        boolean depthTestWasEnabled = glIsEnabled(GL_DEPTH_TEST);
        
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        
        // Clear depth buffer for this item
        glClear(GL_DEPTH_BUFFER_BIT);
        
        // Work within the existing coordinate system
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        
        // Translate to item position
        glTranslatef(x, y, 0);
        
        // Apply isometric-style rotation to show 3 faces (like Minecraft GUI display)
        // These rotations show top, north, and east faces
        glRotatef(30, 1, 0, 0);   // Tilt down to see top
        glRotatef(45, 0, 1, 0);   // Rotate to see corner
        
        // Scale to fit the item size
        glScalef(size, -size, size);  // Negative Y to flip correctly
        
        // Center the model (models are in 0-1 range)
        glTranslatef(-0.5f, -0.5f, -0.5f);
        
        // Render all quads from the baked model
        List<BakedQuad> quads = bakedModel.getQuads();
        if (quads != null && !quads.isEmpty()) {
            for (BakedQuad quad : quads) {
                renderQuad3D(quad, itemModel);
            }
        } else {
            // No quads - render fallback
            glPopMatrix();
            if (!depthTestWasEnabled) glDisable(GL_DEPTH_TEST);
            if (!textureWasEnabled) glDisable(GL_TEXTURE_2D);
            renderFallbackItem(x, y, size);
            return;
        }
        
        // Restore matrix
        glPopMatrix();
        
        // Restore GL state
        if (!depthTestWasEnabled) {
            glDisable(GL_DEPTH_TEST);
        }
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        glColor4f(1f, 1f, 1f, 1f); // Reset color
    }
    
    /**
     * Apply a display transform from the model JSON.
     */
    private static void applyDisplayTransform(ModelDisplay.Transform transform, float size) {
        // Apply rotation (in degrees)
        if (transform.getRotation() != null && transform.getRotation().size() == 3) {
            float rx = transform.getRotation().get(0);
            float ry = transform.getRotation().get(1);
            float rz = transform.getRotation().get(2);
            
            if (rx != 0) glRotatef(rx, 1, 0, 0);
            if (ry != 0) glRotatef(ry, 0, 1, 0);
            if (rz != 0) glRotatef(rz, 0, 0, 1);
        }
        
        // Apply translation (Minecraft uses a different scale, so we need to adjust)
        if (transform.getTranslation() != null && transform.getTranslation().size() == 3) {
            float tx = transform.getTranslation().get(0);
            float ty = transform.getTranslation().get(1);
            float tz = transform.getTranslation().get(2);
            
            glTranslatef(tx * size / 16.0f, ty * size / 16.0f, tz * size / 16.0f);
        }
        
        // Apply scale
        if (transform.getScale() != null && transform.getScale().size() == 3) {
            float sx = transform.getScale().get(0) * size;
            float sy = transform.getScale().get(1) * size;
            float sz = transform.getScale().get(2) * size;
            
            glScalef(sx, sy, sz);
        } else {
            // Default scale
            glScalef(size, size, size);
        }
    }
    
    /**
     * Render a single quad in 3D space from a baked model.
     */
    private static void renderQuad3D(BakedQuad quad, BlockModel itemModel) {
        // Load and bind texture
        String texturePath = quad.getTexturePath();
        Texture texture = loadTexture(texturePath);
        if (texture == null) {
            return;
        }
        texture.bind();
        
        // Get vertex data
        float[] vertices = quad.getVertices();
        
        // Apply tint if needed
        int tintIndex = quad.getTintIndex();
        float r = 1.0f, g = 1.0f, b = 1.0f;
        
        if (tintIndex >= 0 && itemModel != null && itemModel.getTints() != null && 
            tintIndex < itemModel.getTints().size()) {
            int tintColor = itemModel.getTints().get(tintIndex).getTintColor();
            r = ((tintColor >> 16) & 0xFF) / 255.0f;
            g = ((tintColor >> 8) & 0xFF) / 255.0f;
            b = (tintColor & 0xFF) / 255.0f;
        }
        
        // Render the quad (4 vertices, vertex format: x, y, z, u, v, nx, ny, nz, r, g, b, a)
        glBegin(GL_QUADS);
        for (int i = 0; i < 4; i++) {
            int offset = i * 12;
            
            // Position
            float x = vertices[offset + 0];
            float y = vertices[offset + 1];
            float z = vertices[offset + 2];
            
            // Texture coordinates
            float u = vertices[offset + 3];
            float v = vertices[offset + 4];
            
            // Normal (for lighting, not used in fixed function but kept for consistency)
            float nx = vertices[offset + 5];
            float ny = vertices[offset + 6];
            float nz = vertices[offset + 7];
            
            // Color (from vertex, multiplied by tint)
            float vr = vertices[offset + 8] * r;
            float vg = vertices[offset + 9] * g;
            float vb = vertices[offset + 10] * b;
            float va = vertices[offset + 11];
            
            // Apply shading based on normal direction (matching Minecraft's directional shading)
            float shade = 1.0f;
            if (Math.abs(ny - 1.0f) < 0.01f) {
                // Top face
                shade = 1.0f;
            } else if (Math.abs(ny + 1.0f) < 0.01f) {
                // Bottom face
                shade = 0.5f;
            } else if (Math.abs(nz) > 0.5f) {
                // North/South face
                shade = 0.8f;
            } else if (Math.abs(nx) > 0.5f) {
                // East/West face
                shade = 0.6f;
            }
            
            glColor4f(vr * shade, vg * shade, vb * shade, va);
            glTexCoord2f(u, v);
            glVertex3f(x, y, z);
        }
        glEnd();
    }
    
    /**
     * Render a fallback magenta square when model is missing.
     */
    private static void renderFallbackItem(float x, float y, float size) {
        glDisable(GL_TEXTURE_2D);
        glColor4f(1f, 0f, 1f, 1f); // Magenta
        
        float halfSize = size;
        
        glBegin(GL_QUADS);
        glVertex2f(x - halfSize, y - halfSize);
        glVertex2f(x + halfSize, y - halfSize);
        glVertex2f(x + halfSize, y + halfSize);
        glVertex2f(x - halfSize, y + halfSize);
        glEnd();
        
        glColor4f(1f, 1f, 1f, 1f);
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
            logger.error("Failed to load texture: {}", path, e);
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
