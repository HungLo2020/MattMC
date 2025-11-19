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
     * 
     * Uses manual 3D-to-2D projection like other UI renderers in this codebase (HotbarRenderer, etc.)
     */
    private static void renderBakedModel(BakedModel bakedModel, BlockModel itemModel, float x, float y, float size) {
        // Save GL state
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        boolean blendWasEnabled = glIsEnabled(GL_BLEND);
        
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Extract rotation from display.gui.rotation if available (JSON model data-driven)
        float xRotation = 30f;  // Default X rotation for isometric view
        float yRotation = -45f; // Default Y rotation for isometric view
        
        if (itemModel != null && itemModel.getDisplay() != null) {
            ModelDisplay.Transform guiTransform = itemModel.getDisplay().get("gui");
            if (guiTransform != null && guiTransform.getRotation() != null && guiTransform.getRotation().size() >= 2) {
                xRotation = guiTransform.getRotation().get(0);
                yRotation = guiTransform.getRotation().get(1);
            }
        }
        
        // Render all quads from the baked model in back-to-front order
        // Painter's algorithm for isometric view: back faces first, front faces last
        // From isometric view (Y=-45°, X=30°), we see: right (EAST), front (NORTH), top (UP)
        // The UP face (top) must be rendered LAST as it's the topmost visible face
        List<BakedQuad> quads = bakedModel.getQuads();
        if (quads != null && !quads.isEmpty()) {
            // Render back/bottom faces first (most occluded)
            for (BakedQuad quad : quads) {
                if (quad.getFace() == BakedQuad.Direction.DOWN) {   // Bottom face - render first
                    renderQuad2D(quad, itemModel, x, y, size, xRotation, yRotation);
                }
            }
            // Render back side faces
            for (BakedQuad quad : quads) {
                if (quad.getFace() == BakedQuad.Direction.SOUTH ||  // Back face
                    quad.getFace() == BakedQuad.Direction.WEST) {   // Back-left face
                    renderQuad2D(quad, itemModel, x, y, size, xRotation, yRotation);
                }
            }
            // Render front side faces
            for (BakedQuad quad : quads) {
                if (quad.getFace() == BakedQuad.Direction.NORTH ||  // Front face
                    quad.getFace() == BakedQuad.Direction.EAST) {   // Front-right face
                    renderQuad2D(quad, itemModel, x, y, size, xRotation, yRotation);
                }
            }
            // Render top face LAST (most visible, should occlude everything else)
            for (BakedQuad quad : quads) {
                if (quad.getFace() == BakedQuad.Direction.UP) {     // Top face - render last!
                    renderQuad2D(quad, itemModel, x, y, size, xRotation, yRotation);
                }
            }
        } else {
            // No quads - render fallback
            if (!textureWasEnabled) glDisable(GL_TEXTURE_2D);
            if (!blendWasEnabled) glDisable(GL_BLEND);
            renderFallbackItem(x, y, size);
            return;
        }
        
        // Restore GL state
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        if (!blendWasEnabled) {
            glDisable(GL_BLEND);
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
     * Render a single quad from a baked model in 2D.
     * Manually projects 3D vertices to 2D, then uses GL_QUADS with glVertex2f.
     * This matches how other UI elements render (HotbarRenderer, UIRenderHelper, etc.)
     */
    private static void renderQuad2D(BakedQuad quad, BlockModel itemModel, float centerX, float centerY, float size, float xRotation, float yRotation) {
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
        
        // Get normal for shading
        float nx = vertices[5];
        float ny = vertices[6];
        float nz = vertices[7];
        
        // Apply shading based on normal direction (matching Minecraft's directional shading)
        float shade = 1.0f;
        if (Math.abs(ny - 1.0f) < 0.01f) {
            shade = 1.0f;  // Top face
        } else if (Math.abs(ny + 1.0f) < 0.01f) {
            shade = 0.5f;  // Bottom face
        } else if (Math.abs(nz) > 0.5f) {
            shade = 0.8f;  // North/South face
        } else if (Math.abs(nx) > 0.5f) {
            shade = 0.6f;  // East/West face
        }
        
        // Manually project 3D vertices to 2D screen space
        // This is the key - we do the 3D math ourselves, then use glVertex2f like all other UI
        float[][] projected = new float[4][2];
        for (int i = 0; i < 4; i++) {
            int offset = i * 12;
            float x = vertices[offset + 0];
            float y = vertices[offset + 1];
            float z = vertices[offset + 2];
            
            // Center the model (models are in 0-1 range)
            x -= 0.5f;
            y -= 0.5f;
            z -= 0.5f;
            
            // Apply rotation from JSON model (data-driven)
            // X rotation and Y rotation come from display.gui.rotation in the JSON
            float radX = (float) Math.toRadians(xRotation);
            float radY = (float) Math.toRadians(yRotation);
            
            // Rotate around Y-axis (uses yRotation from JSON)
            float cosY = (float) Math.cos(radY);
            float sinY = (float) Math.sin(radY);
            float x1 = x * cosY - z * sinY;
            float z1 = x * sinY + z * cosY;
            
            // Rotate around X-axis (uses xRotation from JSON)
            float cosX = (float) Math.cos(radX);
            float sinX = (float) Math.sin(radX);
            float y1 = y * cosX - z1 * sinX;
            
            // Scale and project to screen
            // x,y are top-left of the item area, not center
            // Scale by size to fit in the item slot
            // Use + for Y (not -) - correct default projection
            projected[i][0] = centerX + (x1 * size);
            projected[i][1] = centerY + (y1 * size);  // Standard projection (+ not -)
        }
        
        // Render as a quad using glVertex2f - EXACTLY like HotbarRenderer and other UI
        glBegin(GL_QUADS);
        for (int i = 0; i < 4; i++) {
            int offset = i * 12;
            float u = vertices[offset + 3];
            float v = vertices[offset + 4];
            float vr = vertices[offset + 8] * r;
            float vg = vertices[offset + 9] * g;
            float vb = vertices[offset + 10] * b;
            float va = vertices[offset + 11];
            
            glColor4f(vr * shade, vg * shade, vb * shade, va);
            glTexCoord2f(u, v);
            glVertex2f(projected[i][0], projected[i][1]);
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
