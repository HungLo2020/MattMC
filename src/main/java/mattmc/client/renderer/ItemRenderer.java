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
        boolean blendWasEnabled = glIsEnabled(GL_BLEND);
        
        glEnable(GL_TEXTURE_2D);
        // Disable depth test - render in painter's algorithm order instead
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // CRITICAL: Explicitly set polygon mode to fill
        // Some graphics drivers or states might have it set to GL_LINE
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        
        // Work within existing projection, just use modelview transforms
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
        
        // Render all quads from the baked model in back-to-front order
        List<BakedQuad> quads = bakedModel.getQuads();
        if (quads != null && !quads.isEmpty()) {
            // Render in specific order: bottom, back faces first, then front faces
            for (BakedQuad quad : quads) {
                if (quad.getFace() == BakedQuad.Direction.DOWN || 
                    quad.getFace() == BakedQuad.Direction.WEST ||
                    quad.getFace() == BakedQuad.Direction.SOUTH) {
                    renderQuad3D(quad, itemModel);
                }
            }
            for (BakedQuad quad : quads) {
                if (quad.getFace() == BakedQuad.Direction.UP || 
                    quad.getFace() == BakedQuad.Direction.EAST ||
                    quad.getFace() == BakedQuad.Direction.NORTH) {
                    renderQuad3D(quad, itemModel);
                }
            }
        } else {
            // No quads - render fallback
            glPopMatrix();
            if (!textureWasEnabled) glDisable(GL_TEXTURE_2D);
            if (!blendWasEnabled) glDisable(GL_BLEND);
            renderFallbackItem(x, y, size);
            return;
        }
        
        // Restore matrix
        glPopMatrix();
        
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
     * Render a single quad from a baked model.
     * Projects 3D vertices to 2D screen space after model transforms are applied.
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
        
        // Transform vertices through the current modelview matrix and render as 2D
        float[][] transformedVerts = new float[4][3];
        for (int i = 0; i < 4; i++) {
            int offset = i * 12;
            float x = vertices[offset + 0];
            float y = vertices[offset + 1];
            float z = vertices[offset + 2];
            
            // Apply current modelview transformation manually
            // Get the transformed position by multiplying with the current matrix
            float[] transformed = transform3DVertex(x, y, z);
            transformedVerts[i] = transformed;
        }
        
        glBegin(GL_TRIANGLES);
        
        // First triangle: vertices 0, 1, 2
        for (int i : new int[]{0, 1, 2}) {
            int offset = i * 12;
            float u = vertices[offset + 3];
            float v = vertices[offset + 4];
            float vr = vertices[offset + 8] * r;
            float vg = vertices[offset + 9] * g;
            float vb = vertices[offset + 10] * b;
            float va = vertices[offset + 11];
            
            glColor4f(vr * shade, vg * shade, vb * shade, va);
            glTexCoord2f(u, v);
            // Use transformed 2D coordinates (ignore Z)
            glVertex2f(transformedVerts[i][0], transformedVerts[i][1]);
        }
        
        // Second triangle: vertices 0, 2, 3
        for (int i : new int[]{0, 2, 3}) {
            int offset = i * 12;
            float u = vertices[offset + 3];
            float v = vertices[offset + 4];
            float vr = vertices[offset + 8] * r;
            float vg = vertices[offset + 9] * g;
            float vb = vertices[offset + 10] * b;
            float va = vertices[offset + 11];
            
            glColor4f(vr * shade, vg * shade, vb * shade, va);
            glTexCoord2f(u, v);
            // Use transformed 2D coordinates (ignore Z)
            glVertex2f(transformedVerts[i][0], transformedVerts[i][1]);
        }
        
        glEnd();
    }
    
    /**
     * Transform a 3D vertex through the current modelview matrix.
     * Returns the transformed x, y, z coordinates.
     */
    private static float[] transform3DVertex(float x, float y, float z) {
        // Get current modelview matrix
        java.nio.FloatBuffer matrixBuffer = java.nio.ByteBuffer.allocateDirect(16 * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        glGetFloatv(GL_MODELVIEW_MATRIX, matrixBuffer);
        
        // Extract matrix elements (column-major order)
        float m00 = matrixBuffer.get(0), m01 = matrixBuffer.get(1), m02 = matrixBuffer.get(2), m03 = matrixBuffer.get(3);
        float m10 = matrixBuffer.get(4), m11 = matrixBuffer.get(5), m12 = matrixBuffer.get(6), m13 = matrixBuffer.get(7);
        float m20 = matrixBuffer.get(8), m21 = matrixBuffer.get(9), m22 = matrixBuffer.get(10), m23 = matrixBuffer.get(11);
        float m30 = matrixBuffer.get(12), m31 = matrixBuffer.get(13), m32 = matrixBuffer.get(14), m33 = matrixBuffer.get(15);
        
        // Transform the vertex
        float tx = m00 * x + m10 * y + m20 * z + m30;
        float ty = m01 * x + m11 * y + m21 * z + m31;
        float tz = m02 * x + m12 * y + m22 * z + m32;
        
        return new float[]{tx, ty, tz};
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
