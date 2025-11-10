package mattmc.client.renderer;

import mattmc.client.renderer.block.BlockGeometryCapture;
import mattmc.client.renderer.texture.Texture;
import mattmc.client.resources.ResourceManager;
import mattmc.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Render an isometric cube showing three faces (south, east, and top).
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
        // In isometric view, we see: south (left side), east (right side), and top
        BlockGeometryCapture.captureSouthFace(capture, 0, 0, 0);
        List<VertexCapture.Face> southFaces = List.copyOf(capture.getFaces());
        
        capture.clear();
        BlockGeometryCapture.captureEastFace(capture, 0, 0, 0);
        List<VertexCapture.Face> eastFaces = List.copyOf(capture.getFaces());
        
        capture.clear();
        BlockGeometryCapture.captureTopFace(capture, 0, 0, 0);
        List<VertexCapture.Face> topFaces = List.copyOf(capture.getFaces());
        
        // Render the faces in back-to-front order for proper visibility
        
        // 1. South face (left side, medium brightness - 80%)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
                renderFacesIsometric(southFaces, x, y, isoWidth, isoHeight);
            }
        }
        
        // 2. East face (right side, darker - 60%)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                renderFacesIsometric(eastFaces, x, y, isoWidth, isoHeight);
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
     * Uses the exact same 3D geometry as the in-game stairs (from BlockFaceGeometry.drawStairsNorthBottom)
     * and projects it to 2D isometric view.
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
        
        // Capture the 3D geometry for stairs using the actual in-game geometry
        VertexCapture capture = new VertexCapture();
        BlockGeometryCapture.captureStairsNorthBottom(capture, 0, 0, 0);
        List<VertexCapture.Face> allFaces = capture.getFaces();
        
        // Render all faces with appropriate textures and shading
        // We'll render in two passes: sides first, then tops, for proper depth ordering
        
        // Pass 1: Side faces (darker shading)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                
                // Render each face with appropriate shading based on face orientation
                for (VertexCapture.Face face : allFaces) {
                    // Determine face type based on vertex positions
                    // Top faces have all vertices at same Y value that's not 0
                    boolean isTopFace = isTopFace(face);
                    
                    if (!isTopFace) {
                        // Determine if it's a south-facing or east-facing side
                        boolean isSouthFacing = isSouthFacing(face);
                        
                        // Apply appropriate shading: south faces are lighter (0.8), others darker (0.6)
                        float brightness = isSouthFacing ? 0.8f : 0.6f;
                        glColor4f(brightness, brightness, brightness, 1.0f);
                        
                        renderFaceIsometric(face, x, y, isoWidth, isoHeight);
                    }
                }
            }
        }
        
        // Pass 2: Top faces (brightest)
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                
                for (VertexCapture.Face face : allFaces) {
                    if (isTopFace(face)) {
                        renderFaceIsometric(face, x, y, isoWidth, isoHeight);
                    }
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
     * Check if a face is south-facing (vertical face on the Z=1 side).
     */
    private static boolean isSouthFacing(VertexCapture.Face face) {
        // South faces have all vertices at Z=1 or Z=0.5 (for the inner step face)
        float z1 = face.v1.z;
        float z2 = face.v2.z;
        float z3 = face.v3.z;
        
        // Check if all Z values are the same and close to 1.0 or 0.5
        if (Math.abs(z1 - z2) < 0.01f && Math.abs(z2 - z3) < 0.01f) {
            return z1 > 0.4f;  // Z >= 0.5 (south or inner step face)
        }
        return false;
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
        glTexCoord2f(face.v1.u, face.v1.v);
        glVertex2f(x1, y1);
        
        // Project and render vertex 2
        float x2 = project2Dx(face.v2.x, face.v2.y, face.v2.z, centerX, isoWidth);
        float y2 = project2Dy(face.v2.x, face.v2.y, face.v2.z, centerY, isoHeight);
        glTexCoord2f(face.v2.u, face.v2.v);
        glVertex2f(x2, y2);
        
        // Project and render vertex 3
        float x3 = project2Dx(face.v3.x, face.v3.y, face.v3.z, centerX, isoWidth);
        float y3 = project2Dy(face.v3.x, face.v3.y, face.v3.z, centerY, isoHeight);
        glTexCoord2f(face.v3.u, face.v3.v);
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
