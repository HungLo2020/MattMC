package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.VertexCapture;

import mattmc.client.renderer.CommandBuffer;

import mattmc.client.renderer.ItemRenderLogic;

import mattmc.client.renderer.backend.DrawCommand;

import mattmc.client.renderer.backend.RenderBackend;

import mattmc.client.renderer.block.BlockGeometryCapture;
import mattmc.client.renderer.item.ItemDisplayContext;
import mattmc.client.renderer.item.ItemRenderer;
import mattmc.client.renderer.backend.opengl.Texture;
import mattmc.client.resources.ResourceManager;
import mattmc.client.resources.metadata.animation.AnimationMetadataSection;
import mattmc.client.resources.model.BlockModel;
import mattmc.client.resources.model.ModelDisplay;
import mattmc.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * OpenGL implementation of item rendering for the UI (hotbar, inventory, etc.).
 * 
 * <p>This class handles OpenGL-specific operations for rendering items:
 * <ul>
 *   <li>Texture loading and caching</li>
 *   <li>Isometric 3D projection for block items</li>
 *   <li>Flat 2D rendering for regular items</li>
 *   <li>Display transform application from JSON models</li>
 * </ul>
 * 
 * <p>For block items, renders an isometric 3D view by capturing the actual in-game
 * 3D geometry and projecting it to 2D screen coordinates.
 * For regular items, renders a 2D icon.
 * 
 * @see ItemRenderer
 */
public class OpenGLItemRenderer implements ItemRenderer {
    private static final Logger logger = LoggerFactory.getLogger(OpenGLItemRenderer.class);
    
    // Cache for item textures
    private static final Map<String, Texture> TEXTURE_CACHE = new HashMap<>();
    
    // Cache for animated texture UV scale (maps texture path to V scale for first frame)
    // Value is the V coordinate for the bottom of the first frame (e.g., 0.2 for 16x80 texture)
    private static final Map<String, Float> ANIMATED_TEXTURE_V_SCALE = new HashMap<>();
    
    // Item texture dimension - items are rendered as 16x16 pixel textures
    private static final float ITEM_TEXTURE_SIZE = 16.0f;
    
    // Tolerance for float comparison in geometry detection
    private static final float EPSILON = 0.01f;
    
    // Singleton instance for static method compatibility
    private static final OpenGLItemRenderer INSTANCE = new OpenGLItemRenderer();
    
    /**
     * Get the singleton instance.
     * 
     * @return the OpenGLItemRenderer instance
     */
    public static OpenGLItemRenderer getInstance() {
        return INSTANCE;
    }
    
    /**
     * Render an item at the specified screen position (static convenience method).
     * Renders block items as orthographic 3D cubes (isometric view).
     * 
     * @param stack The item stack to render
     * @param x Screen X position (center of item)
     * @param y Screen Y position (center of item)
     * @param size Size of the rendered item in pixels
     */
    public static void renderItemStatic(ItemStack stack, float x, float y, float size) {
        INSTANCE.renderItem(stack, x, y, size, false);
    }
    
    @Override
    public void renderItem(ItemStack stack, float x, float y, float size) {
        renderItem(stack, x, y, size, false);
    }
    
    /**
     * Render an item using data-driven 3D perspective rendering with display transforms (static version).
     * This method reads display transforms from the item's JSON model and applies them,
     * making it compatible with MattMC's data-driven rendering system.
     * 
     * @param stack The item stack to render
     * @param context The display context (GUI, firstperson, thirdperson, etc.)
     * @param x Screen X position (center of item)
     * @param y Screen Y position (center of item)
     * @param size Base size for rendering
     */
    public static void renderItemWithTransformStatic(ItemStack stack, ItemDisplayContext context, float x, float y, float size) {
        INSTANCE.renderItemWithTransform(stack, context, x, y, size);
    }
    
    @Override
    public void renderItemWithTransform(ItemStack stack, ItemDisplayContext context, float x, float y, float size) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        
        String itemId = stack.getItem().getIdentifier();
        if (itemId == null) {
            return;
        }
        
        // Extract item name from identifier
        String itemName = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        
        // Load the item model
        BlockModel itemModel = ResourceManager.resolveItemModel(itemName);
        if (itemModel == null) {
            // Fallback to old rendering method
            renderItemImpl(stack, x, y, size, context == ItemDisplayContext.GUI);
            return;
        }
        
        // Get display transform for this context
        Map<String, ModelDisplay.Transform> displayMap = itemModel.getDisplay();
        ModelDisplay.Transform transform = null;
        if (displayMap != null) {
            transform = displayMap.get(context.getJsonKey());
        }
        
        // If no transform is defined, use defaults based on context
        if (transform == null) {
            transform = getDefaultTransform(context, itemModel);
        }
        
        // Setup 3D rendering with the transform
        render3DWithTransform(stack, itemModel, transform, x, y, size);
    }
    
    /**
     * Render an item at the specified screen position (static version for backwards compatibility).
     * Renders block items as orthographic 3D cubes (isometric view).
     * 
     * @param stack The item stack to render
     * @param x Screen X position (center of item)
     * @param y Screen Y position (center of item)
     * @param size Size of the rendered item in pixels
     * @param applyInventoryOffset Apply +18f Y offset for inventory screen block item rendering
     */
    public static void renderItemStatic(ItemStack stack, float x, float y, float size, boolean applyInventoryOffset) {
        INSTANCE.renderItem(stack, x, y, size, applyInventoryOffset);
    }
    
    @Override
    public void renderItem(ItemStack stack, float x, float y, float size, boolean applyInventoryOffset) {
        renderItemImpl(stack, x, y, size, applyInventoryOffset);
    }
    
    /**
     * Internal implementation of renderItem.
     */
    private void renderItemImpl(ItemStack stack, float x, float y, float size, boolean applyInventoryOffset) {
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
            INSTANCE.renderFallbackItem(x, y, size);
            return;
        }
        
        // Check if this is a block item (has block textures)
        boolean isBlockItem = texturePaths.containsKey("all") || texturePaths.containsKey("top") || 
                              texturePaths.containsKey("side") || texturePaths.containsKey("bottom");
        
        if (isBlockItem) {
            // Get the item model to check for tints and special rendering
            mattmc.client.resources.model.BlockModel itemModel = ResourceManager.resolveItemModel(itemName);
            
            // Check if this is a stairs or slab block by looking at the original parent before merging
            String originalParent = itemModel != null ? itemModel.getOriginalParent() : null;
            boolean isStairs = originalParent != null && originalParent.contains("stairs");
            boolean isSlab = originalParent != null && originalParent.contains("slab");
            
            // Apply inventory offset for block items if requested
            float adjustedY = applyInventoryOffset ? y + 18f : y;
            
            if (isStairs) {
                // Render as isometric stairs
                renderIsometricStairs(texturePaths, itemModel, x, adjustedY, size);
            } else if (isSlab) {
                // Render as isometric slab (half-height block)
                renderIsometricSlab(texturePaths, itemModel, x, adjustedY, size);
            } else {
                // Render as isometric 3D cube
                renderIsometricCube(texturePaths, itemModel, x, adjustedY, size);
            }
        } else {
            // Render as flat 2D icon (for non-block items)
            String texturePath = texturePaths.get("layer0");
            if (texturePath == null) {
                texturePath = texturePaths.values().iterator().next();
            }
            
            // Flat items need different offset than block items:
            // - In inventory screen: no additional offset needed (already centered in slots)
            // - In hotbar: need to move UP by 18f to align with slots (opposite of block items)
            float adjustedY = applyInventoryOffset ? y : y - 18f;
            
            if (texturePath != null) {
                renderTextureAsFlat(texturePath, x, adjustedY, size);
            } else {
                INSTANCE.renderFallbackItem(x, adjustedY, size);
            }
        }
    }
    
    /**
     * Render an isometric cube showing three faces (west, north, and top).
     * Uses the actual in-game 3D block geometry projected to 2D isometric view.
     * 
     * @param size Half-width for the isometric projection
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
        // size is the half-width, so double it for the full scale
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
                float sideVScale = getAnimatedTextureVScale(sideTexture);
                renderFacesIsometric(westFaces, x, y, isoWidth, isoHeight, sideVScale);
            }
        }
        
        // 2. North face (right side, darker - 60%)
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
                float sideVScale = getAnimatedTextureVScale(sideTexture);
                renderFacesIsometric(northFaces, x, y, isoWidth, isoHeight, sideVScale);
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
                float topVScale = getAnimatedTextureVScale(topTexture);
                renderFacesIsometric(topFaces, x, y, isoWidth, isoHeight, topVScale);
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
                float sideVScale = getAnimatedTextureVScale(sideTexture);
                
                for (VertexCapture.Face face : visibleSideFaces) {
                    // Determine brightness based on face orientation
                    // West-facing faces (x=0) get 0.8 brightness
                    // North-facing faces (z=0) get 0.6 brightness
                    boolean isWestFacing = isWestFacing(face);
                    float brightness = isWestFacing ? 0.8f : 0.6f;
                    glColor4f(brightness, brightness, brightness, 1.0f);
                    
                    renderFaceIsometric(face, x, y, isoWidth, isoHeight, sideVScale);
                }
            }
        }
        
        // Render top faces last with full brightness
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                float topVScale = getAnimatedTextureVScale(topTexture);
                
                for (VertexCapture.Face face : topFacesList) {
                    renderFaceIsometric(face, x, y, isoWidth, isoHeight, topVScale);
                }
            }
        }
        
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        glColor4f(1f, 1f, 1f, 1f);
    }
    
    /**
     * Render a slab as an isometric 3D half-height block.
     * Slabs are rendered at half the height of a full block.
     */
    private static void renderIsometricSlab(Map<String, String> texturePaths, mattmc.client.resources.model.BlockModel itemModel, float x, float y, float size) {
        // Get textures for each face
        String topTexture = getTextureForFace(texturePaths, "top");
        String sideTexture = getTextureForFace(texturePaths, "side");
        String bottomTexture = getTextureForFace(texturePaths, "bottom");
        
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        if (!textureWasEnabled) {
            glEnable(GL_TEXTURE_2D);
        }
        
        // Isometric projection parameters
        float scale = size * 2.0f;
        float isoWidth = scale * 0.5f;
        float isoHeight = scale * 0.5f;
        
        // Capture slab geometry (half-height block)
        VertexCapture capture = new VertexCapture();
        BlockGeometryCapture.captureSlabBottom(capture, 0, 0, 0);
        List<VertexCapture.Face> allFaces = capture.getFaces();
        
        // Separate faces by type
        List<VertexCapture.Face> topFacesList = new ArrayList<>();
        List<VertexCapture.Face> visibleSideFaces = new ArrayList<>();
        
        for (VertexCapture.Face face : allFaces) {
            if (isSlabTopFace(face)) {
                topFacesList.add(face);
            } else {
                // Only render visible side faces (West and North faces)
                if (isSlabVisibleSideFace(face)) {
                    visibleSideFaces.add(face);
                }
            }
        }
        
        // Render visible side faces first with appropriate shading
        if (sideTexture != null) {
            Texture tex = loadTexture(sideTexture);
            if (tex != null) {
                tex.bind();
                float sideVScale = getAnimatedTextureVScale(sideTexture);
                
                for (VertexCapture.Face face : visibleSideFaces) {
                    // Determine brightness based on face orientation
                    // West-facing faces (x=0) get 0.8 brightness
                    // North-facing faces (z=0) get 0.6 brightness
                    boolean isWestFacing = isWestFacing(face);
                    float brightness = isWestFacing ? 0.8f : 0.6f;
                    glColor4f(brightness, brightness, brightness, 1.0f);
                    
                    renderFaceIsometric(face, x, y, isoWidth, isoHeight, sideVScale);
                }
            }
        }
        
        // Render top faces last with full brightness
        if (topTexture != null) {
            Texture tex = loadTexture(topTexture);
            if (tex != null) {
                tex.bind();
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                float topVScale = getAnimatedTextureVScale(topTexture);
                
                for (VertexCapture.Face face : topFacesList) {
                    renderFaceIsometric(face, x, y, isoWidth, isoHeight, topVScale);
                }
            }
        }
        
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        glColor4f(1f, 1f, 1f, 1f);
    }
    
    /**
     * Check if a face is a slab top face (horizontal, at y=0.5).
     */
    private static boolean isSlabTopFace(VertexCapture.Face face) {
        float y1 = face.v1.y;
        float y2 = face.v2.y;
        float y3 = face.v3.y;
        
        // All Y values are the same and at 0.5 (slab top height)
        return Math.abs(y1 - y2) < EPSILON && Math.abs(y2 - y3) < EPSILON && Math.abs(y1 - 0.5f) < EPSILON;
    }
    
    /**
     * Check if a slab side face is visible in isometric view.
     * Only West (x=0) and North (z=0) faces are visible.
     */
    private static boolean isSlabVisibleSideFace(VertexCapture.Face face) {
        // Check if it's a West face (x=0)
        if (face.v1.x < EPSILON && face.v2.x < EPSILON && face.v3.x < EPSILON) {
            return true;
        }
        
        // Check if it's a North face (z=0)
        if (face.v1.z < EPSILON && face.v2.z < EPSILON && face.v3.z < EPSILON) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a face is a top face (horizontal, all Y coordinates equal and > 0).
     */
    private static boolean isTopFace(VertexCapture.Face face) {
        float y1 = face.v1.y;
        float y2 = face.v2.y;
        float y3 = face.v3.y;
        
        // All Y values are the same and greater than 0
        return Math.abs(y1 - y2) < EPSILON && Math.abs(y2 - y3) < EPSILON && y1 > EPSILON;
    }
    
    /**
     * Check if a side face is visible in isometric view.
     * Only West (x=0) and North (z=0) faces are visible, plus the inner step face at z=0.5.
     */
    private static boolean isVisibleSideFace(VertexCapture.Face face) {
        // Check if it's a West face (x=0)
        if (face.v1.x < EPSILON && face.v2.x < EPSILON && face.v3.x < EPSILON) {
            return true;
        }
        
        // Check if it's a North face (z=0)
        if (face.v1.z < EPSILON && face.v2.z < EPSILON && face.v3.z < EPSILON) {
            return true;
        }
        
        // Check if it's an inner step face at z=0.5 (full width, vertical)
        float avgZ = (face.v1.z + face.v2.z + face.v3.z) / 3.0f;
        if (Math.abs(avgZ - 0.5f) < EPSILON) {
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
        return face.v1.x < EPSILON && face.v2.x < EPSILON && face.v3.x < EPSILON;
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
        renderFacesIsometric(faces, centerX, centerY, isoWidth, isoHeight, 1.0f);
    }
    
    /**
     * Render a list of captured faces using isometric projection with UV scaling.
     * @param vScale V coordinate scale for animated textures (e.g., 0.2 for first frame of 16x80 texture)
     */
    private static void renderFacesIsometric(List<VertexCapture.Face> faces, float centerX, float centerY, float isoWidth, float isoHeight, float vScale) {
        for (VertexCapture.Face face : faces) {
            renderFaceIsometric(face, centerX, centerY, isoWidth, isoHeight, vScale);
        }
    }
    
    /**
     * Render a single captured face using isometric projection.
     */
    private static void renderFaceIsometric(VertexCapture.Face face, float centerX, float centerY, float isoWidth, float isoHeight) {
        renderFaceIsometric(face, centerX, centerY, isoWidth, isoHeight, 1.0f);
    }
    
    /**
     * Render a single captured face using isometric projection with UV scaling.
     * @param vScale V coordinate scale for animated textures (e.g., 0.2 for first frame of 16x80 texture)
     */
    private static void renderFaceIsometric(VertexCapture.Face face, float centerX, float centerY, float isoWidth, float isoHeight, float vScale) {
        glBegin(GL_TRIANGLES);
        
        // Project and render vertex 1
        float x1 = project2Dx(face.v1.x, face.v1.y, face.v1.z, centerX, isoWidth);
        float y1 = project2Dy(face.v1.x, face.v1.y, face.v1.z, centerY, isoHeight);
        // Flip V coordinate for 2D rendering (3D geometry has pre-flipped coords for 3D rendering)
        // For animated textures, scale V to only use first frame (vScale portion of texture)
        glTexCoord2f(face.v1.u, (1.0f - face.v1.v) * vScale);
        glVertex2f(x1, y1);
        
        // Project and render vertex 2
        float x2 = project2Dx(face.v2.x, face.v2.y, face.v2.z, centerX, isoWidth);
        float y2 = project2Dy(face.v2.x, face.v2.y, face.v2.z, centerY, isoHeight);
        glTexCoord2f(face.v2.u, (1.0f - face.v2.v) * vScale);
        glVertex2f(x2, y2);
        
        // Project and render vertex 3
        float x3 = project2Dx(face.v3.x, face.v3.y, face.v3.z, centerX, isoWidth);
        float y3 = project2Dy(face.v3.x, face.v3.y, face.v3.z, centerY, isoHeight);
        glTexCoord2f(face.v3.u, (1.0f - face.v3.v) * vScale);
        glVertex2f(x3, y3);
        
        glEnd();
    }
    
    /**
     * Get the texture for a specific face of a block.
     * Falls back to "all" texture if specific face not found.
     * For pillar blocks (logs, etc.), "end" texture is used for top/bottom.
     */
    private static String getTextureForFace(Map<String, String> texturePaths, String faceKey) {
        // Try specific face first
        String texture = texturePaths.get(faceKey);
        if (texture != null) {
            return texture;
        }
        
        // For top/bottom faces, try "end" texture (used by pillar blocks like logs)
        if ("top".equals(faceKey) || "bottom".equals(faceKey)) {
            texture = texturePaths.get("end");
            if (texture != null) {
                return texture;
            }
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
     * Calculate the vertical offset (1 texture pixel) to align flat items properly.
     * 
     * The size parameter represents half the rendered item size. Since items are 
     * ITEM_TEXTURE_SIZE (16) pixels, and size is half of the total rendered size,
     * one texture pixel equals: (2 * size) / ITEM_TEXTURE_SIZE = size / 8
     * 
     * @param size Half-width/height of the item
     * @return The vertical offset representing 1 texture pixel
     */
    private static float calculateTexturePixelOffset(float size) {
        return (2 * size) / ITEM_TEXTURE_SIZE;
    }
    
    /**
     * Render a texture as a flat 2D square.
     * 
     * @param texturePath Path to the texture
     * @param x Center X coordinate
     * @param y Center Y coordinate
     * @param size Half-width/height of the item (item will be rendered as 2*size x 2*size)
     */
    private static void renderTextureAsFlat(String texturePath, float x, float y, float size) {
        Texture texture = loadTexture(texturePath);
        if (texture == null) {
            INSTANCE.renderFallbackItem(x, y, size);
            return;
        }
        
        // Save current GL state
        boolean textureWasEnabled = glIsEnabled(GL_TEXTURE_2D);
        boolean blendWasEnabled = glIsEnabled(GL_BLEND);
        
        // Enable blending for transparent textures (prevents black background)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glEnable(GL_TEXTURE_2D);
        texture.bind();
        glColor4f(1f, 1f, 1f, 1f);
        
        // Render item centered at (x, y) with full size
        // size parameter is the half-width, so total size is 2*size
        float halfSize = size;
        
        // Move items UP by 1 texture pixel to fix vertical alignment
        float adjustedY = y - calculateTexturePixelOffset(size);
        
        // For animated textures, only render the first frame (use V scale)
        float vScale = getAnimatedTextureVScale(texturePath);
        float vTop = 0.0f;       // Top of first frame
        float vBottom = vScale;   // Bottom of first frame (e.g., 0.2 for 16x80)
        
        glBegin(GL_QUADS);
        glTexCoord2f(0, vBottom); glVertex2f(x - halfSize, adjustedY - halfSize);
        glTexCoord2f(1, vBottom); glVertex2f(x + halfSize, adjustedY - halfSize);
        glTexCoord2f(1, vTop); glVertex2f(x + halfSize, adjustedY + halfSize);
        glTexCoord2f(0, vTop); glVertex2f(x - halfSize, adjustedY + halfSize);
        glEnd();
        
        // Restore GL state
        if (!textureWasEnabled) {
            glDisable(GL_TEXTURE_2D);
        }
        if (!blendWasEnabled) {
            glDisable(GL_BLEND);
        }
    }
    
    /**
     * Render a fallback magenta square when texture is missing (static version).
     * Package-private for backend access.
     */
    static void renderFallbackItemStatic(float x, float y, float size) {
        INSTANCE.renderFallbackItem(x, y, size);
    }
    
    @Override
    public void renderFallbackItem(float x, float y, float size) {
        glColor4f(1f, 0f, 1f, 1f); // Magenta
        
        // Match the scale of flat items (which matches isometric block items)
        float halfSize = size;
        
        // Move fallback items UP by 1 texture pixel to match flat items alignment
        float adjustedY = y - calculateTexturePixelOffset(size);
        
        glBegin(GL_QUADS);
        glVertex2f(x - halfSize, adjustedY - halfSize);
        glVertex2f(x + halfSize, adjustedY - halfSize);
        glVertex2f(x + halfSize, adjustedY + halfSize);
        glVertex2f(x - halfSize, adjustedY + halfSize);
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
            
            // Check for animated texture and calculate V scale
            checkAndCacheAnimatedTextureInfo(path, resourcePath, texture);
            
            return texture;
        } catch (Exception e) {
            logger.error("Failed to load texture: {}", path, e);
            return null;
        }
    }
    
    /**
     * Check if a texture is animated and cache its UV scale for first frame rendering.
     * For animated textures (e.g., 16x80 with 5 frames), we need to render only the first frame (16x16).
     * This method calculates the V scale factor (e.g., 0.2 for 16/80).
     */
    private static void checkAndCacheAnimatedTextureInfo(String path, String resourcePath, Texture texture) {
        // Check for .mcmeta file
        String mcmetaPath = resourcePath + ".mcmeta";
        try (InputStream mcmetaStream = mattmc.util.ResourceLoader.getResourceStreamFromClassLoader(mcmetaPath)) {
            if (mcmetaStream != null) {
                AnimationMetadataSection metadata = AnimationMetadataSection.load(mcmetaStream);
                if (metadata != AnimationMetadataSection.EMPTY) {
                    // This is an animated texture - calculate V scale for first frame
                    // Use metadata to properly calculate frame size
                    mattmc.client.resources.metadata.animation.FrameSize frameSize = 
                        metadata.calculateFrameSize(texture.width, texture.height);
                    int frameHeight = frameSize.height();
                    
                    // If texture height > frame height, it's a strip of frames
                    if (texture.height > frameHeight) {
                        float vScale = (float) frameHeight / (float) texture.height;
                        ANIMATED_TEXTURE_V_SCALE.put(path, vScale);
                    }
                }
            }
        } catch (Exception e) {
            // No mcmeta file or error reading - treat as static texture
        }
    }
    
    /**
     * Get the V scale for the first frame of an animated texture.
     * Returns 1.0f for non-animated textures (use full texture height).
     */
    private static float getAnimatedTextureVScale(String path) {
        Float scale = ANIMATED_TEXTURE_V_SCALE.get(path);
        return scale != null ? scale : 1.0f;
    }
    
    /**
     * Clear the texture cache (static version for backwards compatibility).
     */
    public static void clearCacheStatic() {
        for (Texture texture : TEXTURE_CACHE.values()) {
            if (texture != null) {
                texture.close();
            }
        }
        TEXTURE_CACHE.clear();
        ANIMATED_TEXTURE_V_SCALE.clear();
    }
    
    @Override
    public void clearCache() {
        clearCacheStatic();
    }
    
    /**
     * Render an item using the backend architecture (Stage 4) - static version.
     * This method uses ItemRenderLogic to build commands and submits them to the backend.
     * 
     * @param stack the item stack to render
     * @param x screen X position
     * @param y screen Y position
     * @param size size of the item
     * @param backend the render backend to use
     */
    public static void renderStatic(ItemStack stack, float x, float y, float size, RenderBackend backend) {
        INSTANCE.render(stack, x, y, size, backend);
    }
    
    @Override
    public void render(ItemStack stack, float x, float y, float size, RenderBackend backend) {
        if (stack == null || backend == null) {
            return;
        }
        
        // Use ItemRenderLogic to build commands
        ItemRenderLogic logic = new ItemRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        logic.buildItemCommand(stack, x, y, size, buffer);
        
        // Submit commands to backend
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
    }
    
    /**
     * Get default display transform for a context when none is defined in the model.
     * These defaults match MattMC's standard transforms.
     */
    private static ModelDisplay.Transform getDefaultTransform(ItemDisplayContext context, BlockModel model) {
        ModelDisplay.Transform transform = new ModelDisplay.Transform();
        
        // Check if this is a block item or flat item
        boolean isBlockItem = model.getTextures() != null && 
                             (model.getTextures().containsKey("all") || 
                              model.getTextures().containsKey("top") ||
                              model.getTextures().containsKey("side"));
        
        // Default transforms based on context and item type
        switch (context) {
            case GUI:
                if (isBlockItem) {
                    // Block items in GUI: rotated isometric view, scaled to 16x16
                    // Move down 6 pixels (in texture coordinates) to align properly
                    transform.setRotation(java.util.Arrays.asList(30f, 225f, 0f));
                    transform.setTranslation(java.util.Arrays.asList(0f, 6f, 0f));
                    transform.setScale(java.util.Arrays.asList(0.889f, 0.889f, 0.889f)); // 16/18 = 0.889
                } else {
                    // Flat items in GUI: no rotation, scaled to 16x16
                    // Move up 2 pixels (in texture coordinates) to align properly
                    transform.setRotation(java.util.Arrays.asList(0f, 0f, 0f));
                    transform.setTranslation(java.util.Arrays.asList(0f, -2f, 0f));
                    transform.setScale(java.util.Arrays.asList(0.889f, 0.889f, 0.889f)); // 16/18 = 0.889
                }
                break;
                
            case GROUND:
                // Items on ground: small, flat on ground
                transform.setRotation(java.util.Arrays.asList(0f, 0f, 0f));
                transform.setTranslation(java.util.Arrays.asList(0f, 3f, 0f));
                transform.setScale(java.util.Arrays.asList(0.25f, 0.25f, 0.25f));
                break;
                
            case FIRSTPERSON_RIGHTHAND:
            case FIRSTPERSON_LEFTHAND:
                // First person: rotated, medium size
                transform.setRotation(java.util.Arrays.asList(0f, 45f, 0f));
                transform.setTranslation(java.util.Arrays.asList(0f, 0f, 0f));
                transform.setScale(java.util.Arrays.asList(0.4f, 0.4f, 0.4f));
                break;
                
            case THIRDPERSON_RIGHTHAND:
            case THIRDPERSON_LEFTHAND:
                // Third person: rotated, smaller
                transform.setRotation(java.util.Arrays.asList(75f, 45f, 0f));
                transform.setTranslation(java.util.Arrays.asList(0f, 2.5f, 0f));
                transform.setScale(java.util.Arrays.asList(0.375f, 0.375f, 0.375f));
                break;
                
            case FIXED:
                // Item frames: medium size, no rotation
                transform.setRotation(java.util.Arrays.asList(0f, 0f, 0f));
                transform.setTranslation(java.util.Arrays.asList(0f, 0f, 0f));
                transform.setScale(java.util.Arrays.asList(0.5f, 0.5f, 0.5f));
                break;
                
            case HEAD:
                // On head: normal size
                transform.setRotation(java.util.Arrays.asList(0f, 0f, 0f));
                transform.setTranslation(java.util.Arrays.asList(0f, 0f, 0f));
                transform.setScale(java.util.Arrays.asList(1f, 1f, 1f));
                break;
                
            default:
                // NONE or unknown: identity transform
                transform.setRotation(java.util.Arrays.asList(0f, 0f, 0f));
                transform.setTranslation(java.util.Arrays.asList(0f, 0f, 0f));
                transform.setScale(java.util.Arrays.asList(1f, 1f, 1f));
                break;
        }
        
        return transform;
    }
    
    /**
     * Render item in 3D with the given display transform applied.
     * This uses OpenGL matrix transformations to apply rotation, translation, and scale.
     */
    private static void render3DWithTransform(ItemStack stack, BlockModel model, 
                                               ModelDisplay.Transform transform, 
                                               float x, float y, float size) {
        // For now, fall back to the existing isometric rendering
        // The full 3D rendering with proper projection matrices would require
        // setting up a proper 3D viewport, which is more involved
        
        // Get item name for texture lookup
        String itemId = stack.getItem().getIdentifier();
        String itemName = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        
        // Get texture paths
        Map<String, String> texturePaths = ResourceManager.getItemTexturePaths(itemName);
        if (texturePaths == null || texturePaths.isEmpty()) {
            INSTANCE.renderFallbackItem(x, y, size);
            return;
        }
        
        // Apply transform scale and translation
        java.util.List<Float> scaleList = transform.getScale();
        java.util.List<Float> translationList = transform.getTranslation();
        
        float scale = (scaleList != null && !scaleList.isEmpty()) ? scaleList.get(0) : 1.0f;
        float transformedSize = size * scale;
        
        // Apply translation offset
        // Translation is in texture pixels, scale by GUI_SCALE (3.0) to get screen pixels
        float offsetX = 0f;
        float offsetY = 0f;
        if (translationList != null && translationList.size() >= 2) {
            offsetX = translationList.get(0) * 3.0f; // GUI_SCALE
            offsetY = translationList.get(1) * 3.0f; // GUI_SCALE
        }
        
        // Check if this is a block item
        boolean isBlockItem = texturePaths.containsKey("all") || texturePaths.containsKey("top") || 
                             texturePaths.containsKey("side") || texturePaths.containsKey("bottom");
        
        if (isBlockItem) {
            // For block items, use isometric rendering with scaled size
            boolean isStairs = model.getOriginalParent() != null && model.getOriginalParent().contains("stairs");
            if (isStairs) {
                renderIsometricStairs(texturePaths, model, x + offsetX, y + offsetY, transformedSize);
            } else {
                renderIsometricCube(texturePaths, model, x + offsetX, y + offsetY, transformedSize);
            }
        } else {
            // For flat items, render as 2D
            String texturePath = texturePaths.get("layer0");
            if (texturePath == null) {
                texturePath = texturePaths.values().iterator().next();
            }
            
            if (texturePath != null) {
                renderTextureAsFlat(texturePath, x + offsetX, y + offsetY, transformedSize);
            } else {
                INSTANCE.renderFallbackItem(x + offsetX, y + offsetY, transformedSize);
            }
        }
    }
}
