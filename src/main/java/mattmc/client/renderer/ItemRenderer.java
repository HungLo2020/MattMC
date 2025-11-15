package mattmc.client.renderer;

import mattmc.client.renderer.block.BlockGeometryCapture;
import mattmc.client.renderer.texture.Texture;
import mattmc.client.resources.ResourceManager;
import mattmc.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of items in the UI (hotbar, inventory, etc.).
 * Similar to Minecraft's ItemRenderer class.
 * 
 * For block items, renders an isometric 3D view by capturing the actual in-game
 * 3D geometry and projecting it to 2D screen coordinates.
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
            
            // Check if this is a stairs block by looking at the original parent before merging
            String originalParent = itemModel != null ? itemModel.getOriginalParent() : null;
            boolean isStairs = originalParent != null && originalParent.contains("stairs");
            
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
     * Render an isometric cube showing three faces (west, north, and top).
     * Uses the actual in-game 3D block geometry projected to 2D isometric view.
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
        
        // Define scale for isometric projection
        float scale = size * 2.0f;
        float isoWidth = scale * 0.5f;
        float isoHeight = scale * 0.5f;
        
        // Capture the 3D geometry for a standard cube
        VertexCapture capture = new VertexCapture();
        
        // Capture the three visible faces in an isometric view
        // The view is from SW looking NE, so we see: west (left), north (right), and top
        BlockGeometryCapture.captureWestFace(capture, 0, 0, 0);
        List<VertexCapture.Face> westFaces = List.copyOf(capture.getFaces());
        
        capture.clear();
        BlockGeometryCapture.captureNorthFace(capture, 0, 0, 0);
        List<VertexCapture.Face> northFaces = List.copyOf(capture.getFaces());
        
        capture.clear();
        BlockGeometryCapture.captureTopFace(capture, 0, 0, 0);
        List<VertexCapture.Face> topFaces = List.copyOf(capture.getFaces());
        
        // Render the faces in back-to-front order for proper visibility
        
        // 1. West face (left side, medium brightness - 80%)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                renderFacesIsometric(westFaces, x, y, isoWidth, isoHeight);
            }
        }
        
        // 2. North face (right side, darker - 60%)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                renderFacesIsometric(northFaces, x, y, isoWidth, isoHeight);
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
                renderFacesIsometric(topFaces, x, y, isoWidth, isoHeight);
            }
        }
        
        // Restore GL state
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        glColor4f(1f, 1f, 1f, 1f); // Reset color
    }
    
    /**
     * Render stairs as an isometric 3D block with proper stepped geometry.
     * Stairs rise toward the south (back in isometric view).
     */
    private static void renderIsometricStairs(Map<String, String> texturePaths, mattmc.client.resources.model.BlockModel itemModel, float x, float y, float size) {
        // Get textures for each face
        String topTexture = getTextureForFace(texturePaths, "top");
        String sideTexture = getTextureForFace(texturePaths, "side");
        
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        if (!textureWasEnabled) {
            glEnable(GL_TEXTURE_2D);
        }
        
        // Isometric projection parameters
        float scale = size * 2.0f;
        float isoWidth = scale * 0.5f;
        float isoHeight = scale * 0.5f;
        
        // Capture south-facing stairs geometry (step rises toward z=1 - back in isometric)
        VertexCapture capture = new VertexCapture();
        BlockGeometryCapture.captureStairsSouthBottom(capture, 0, 0, 0);
        List<VertexCapture.Face> allFaces = capture.getFaces();
        
        // Separate faces by type and visibility
        // Pre-allocate with estimated capacity to reduce resizing
        int estimatedCapacity = allFaces.size() / 2;
        List<VertexCapture.Face> topFacesList = new ArrayList<>(estimatedCapacity);
        List<VertexCapture.Face> visibleSideFaces = new ArrayList<>(estimatedCapacity);
        
        for (VertexCapture.Face face : allFaces) {
            if (isTopFace(face)) {
                topFacesList.add(face);
            } else {
                // Only render visible side faces (West and North faces)
                // Filter out East and South faces which are hidden in isometric view
                if (isVisibleSideFace(face)) {
                    visibleSideFaces.add(face);
                }
            }
        }
        
        // Render visible side faces first with appropriate shading
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                
                for (VertexCapture.Face face : visibleSideFaces) {
                    // Determine brightness based on face orientation
                    // West-facing faces (x=0) get 0.8 brightness
                    // North-facing faces (z=0) get 0.6 brightness
                    boolean isWestFacing = isWestFacing(face);
                    float brightness = isWestFacing ? 0.8f : 0.6f;
                    glColor4f(brightness, brightness, brightness, 1.0f);
                    
                    renderFaceIsometric(face, x, y, isoWidth, isoHeight);
                }
            }
        }
        
        // Render top faces last with full brightness
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                
                for (VertexCapture.Face face : topFacesList) {
                    renderFaceIsometric(face, x, y, isoWidth, isoHeight);
                }
            }
        }
        
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        glColor4f(1f, 1f, 1f, 1f);
    }
    
    /**
     * Check if a face is a top face (horizontal, all Y coordinates equal and > 0).
     */
    private static boolean isTopFace(VertexCapture.Face face) {
        float y1 = face.v1.y;
        float y2 = face.v2.y;
        float y3 = face.v3.y;
        
        // All Y values are the same and greater than 0
        return Math.abs(y1 - y2) < 0.01f && Math.abs(y2 - y3) < 0.01f && y1 > 0.01f;
    }
    
    /**
     * Check if a side face is visible in isometric view.
     * Only West (x=0) and North (z=0) faces are visible, plus the inner step face at z=0.5.
     */
    private static boolean isVisibleSideFace(VertexCapture.Face face) {
        // Check if it's a West face (x=0)
        if (face.v1.x < 0.01f && face.v2.x < 0.01f && face.v3.x < 0.01f) {
            return true;
        }
        
        // Check if it's a North face (z=0)
        if (face.v1.z < 0.01f && face.v2.z < 0.01f && face.v3.z < 0.01f) {
            return true;
        }
        
        // Check if it's an inner step face at z=0.5 (full width, vertical)
        float avgZ = (face.v1.z + face.v2.z + face.v3.z) / 3.0f;
        if (Math.abs(avgZ - 0.5f) < 0.01f) {
            // Verify it's vertical (Y values differ significantly)
            float yMin = Math.min(face.v1.y, Math.min(face.v2.y, face.v3.y));
            float yMax = Math.max(face.v1.y, Math.max(face.v2.y, face.v3.y));
            if (yMax - yMin > 0.4f) { // Significant Y difference = vertical face
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a face is west-facing (vertical face on the X=0 side).
     */
    private static boolean isWestFacing(VertexCapture.Face face) {
        // West faces have all vertices at X=0
        return face.v1.x < 0.01f && face.v2.x < 0.01f && face.v3.x < 0.01f;
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
     * Render a list of captured faces using isometric projection.
     */
    private static void renderFacesIsometric(List<VertexCapture.Face> faces, float centerX, float centerY, float isoWidth, float isoHeight) {
        for (VertexCapture.Face face : faces) {
            renderFaceIsometric(face, centerX, centerY, isoWidth, isoHeight);
        }
    }
    
    /**
     * Render a single captured face using isometric projection.
     */
    private static void renderFaceIsometric(VertexCapture.Face face, float centerX, float centerY, float isoWidth, float isoHeight) {
        glBegin(GL_TRIANGLES);
        
        // Project and render vertex 1
        float x1 = project2Dx(face.v1.x, face.v1.y, face.v1.z, centerX, isoWidth);
        float y1 = project2Dy(face.v1.x, face.v1.y, face.v1.z, centerY, isoHeight);
        // Flip V coordinate for 2D rendering (3D geometry has pre-flipped coords for 3D rendering)
        glTexCoord2f(face.v1.u, 1.0f - face.v1.v);
        glVertex2f(x1, y1);
        
        // Project and render vertex 2
        float x2 = project2Dx(face.v2.x, face.v2.y, face.v2.z, centerX, isoWidth);
        float y2 = project2Dy(face.v2.x, face.v2.y, face.v2.z, centerY, isoHeight);
        glTexCoord2f(face.v2.u, 1.0f - face.v2.v);
        glVertex2f(x2, y2);
        
        // Project and render vertex 3
        float x3 = project2Dx(face.v3.x, face.v3.y, face.v3.z, centerX, isoWidth);
        float y3 = project2Dy(face.v3.x, face.v3.y, face.v3.z, centerY, isoHeight);
        glTexCoord2f(face.v3.u, 1.0f - face.v3.v);
        glVertex2f(x3, y3);
        
        glEnd();
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
