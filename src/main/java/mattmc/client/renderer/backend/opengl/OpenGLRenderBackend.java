package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.VoxelLitShader;

import mattmc.client.renderer.ItemRenderLogic;

import mattmc.client.renderer.UIRenderLogic;

import mattmc.client.renderer.backend.RenderPass;

import mattmc.client.renderer.backend.DrawCommand;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.UIMeshIds;

import mattmc.client.renderer.backend.opengl.ChunkVAO;
import mattmc.client.renderer.backend.opengl.TextureAtlas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * OpenGL implementation of the RenderBackend interface.
 * 
 * <p>This backend translates abstract {@link DrawCommand} objects into concrete OpenGL API calls.
 * It manages the mapping from abstract IDs to actual OpenGL resources (VAOs, shaders, textures)
 * and handles all OpenGL state management.
 * 
 * <p><b>Design Note:</b> This is the <em>only</em> class that should contain OpenGL calls for
 * rendering geometry. All other rendering code should work through the {@link RenderBackend}
 * abstraction. This localization of GL calls is the key goal of Stage 2.
 * 
 * <h2>Resource Management</h2>
 * <p>The backend maintains several registries to map abstract IDs to OpenGL resources:
 * <ul>
 *   <li><b>Mesh Registry:</b> Maps meshId → {@link ChunkVAO} objects</li>
 *   <li><b>Material Registry:</b> Maps materialId → shader/texture combinations</li>
 *   <li><b>Transform Storage:</b> Maps transformIndex → transformation matrices</li>
 * </ul>
 * 
 * <p><b>Current Implementation:</b> This is a "Stage 2" implementation that wraps existing
 * GL helper methods (like {@code ChunkVAO.render()}). Future stages may optimize this further,
 * but the current goal is <em>localizing</em> GL calls, not fully optimizing yet.
 * 
 * <p><b>Usage Note:</b> As specified in Stage 2, this class is compiled but <b>NOT YET WIRED
 * INTO THE MAIN RENDER LOOP</b>. It exists to establish the backend implementation pattern
 * that will be used in Stage 3+ when we refactor chunk rendering.
 * 
 * <p><b>Thread Safety:</b> This class is NOT thread-safe and must only be called from the
 * OpenGL rendering thread.
 * 
 * @since Stage 2 of rendering refactor
 * @see RenderBackend
 * @see DrawCommand
 * @see RenderPass
 */
public class OpenGLRenderBackend implements RenderBackend {
    private static final Logger logger = LoggerFactory.getLogger(OpenGLRenderBackend.class);
    
    // Resource registries
    private final Map<Integer, ChunkVAO> meshRegistry = new HashMap<>();
    private final Map<Integer, MaterialInfo> materialRegistry = new HashMap<>();
    private final Map<Integer, TransformInfo> transformRegistry = new HashMap<>();
    
    // Current shader being used
    private VoxelLitShader currentShader = null;
    private TextureAtlas currentAtlas = null;
    
    // Frame state - uses reference counting to support nested begin/end calls
    private int frameDepth = 0;
    
    // Blur helper for AbstractBlurBox functionality
    private AbstractBlurBox blurHelper = null;
    
    // Sprite batcher for efficient 2D rendering (replaces immediate mode)
    private SpriteBatcher spriteBatcher = null;
    
    // Shader for 2D sprite/UI rendering
    private Shader spriteShader = null;
    
    // Current color state for 2D rendering
    private float currentColorR = 1.0f;
    private float currentColorG = 1.0f;
    private float currentColorB = 1.0f;
    private float currentColorA = 1.0f;
    
    /**
     * Information about a material (shader + texture combination).
     */
    private static class MaterialInfo {
        final VoxelLitShader shader;
        final TextureAtlas atlas;
        
        MaterialInfo(VoxelLitShader shader, TextureAtlas atlas) {
            this.shader = shader;
            this.atlas = atlas;
        }
    }
    
    /**
     * Information about a transformation.
     * For now, we store chunk coordinates for translation.
     * Future implementations may store full transformation matrices.
     */
    private static class TransformInfo {
        final float translateX;
        final float translateY;
        final float translateZ;
        
        TransformInfo(float x, float y, float z) {
            this.translateX = x;
            this.translateY = y;
            this.translateZ = z;
        }
    }
    
    /**
     * Register a mesh with the backend, assigning it an ID.
     * 
     * @param meshId the abstract mesh ID to use
     * @param vao the OpenGL VAO to associate with this ID
     */
    public void registerMesh(int meshId, ChunkVAO vao) {
        meshRegistry.put(meshId, vao);
    }
    
    /**
     * Unregister a mesh, typically when a chunk is unloaded.
     * 
     * @param meshId the mesh ID to remove
     * @return the VAO that was removed, or null if not found
     */
    public ChunkVAO unregisterMesh(int meshId) {
        return meshRegistry.remove(meshId);
    }
    
    /**
     * Register a material with the backend, assigning it an ID.
     * 
     * @param materialId the abstract material ID to use
     * @param shader the shader program to use
     * @param atlas the texture atlas to use
     */
    public void registerMaterial(int materialId, VoxelLitShader shader, TextureAtlas atlas) {
        materialRegistry.put(materialId, new MaterialInfo(shader, atlas));
    }
    
    /**
     * Register a transform with the backend, assigning it an ID.
     * Currently only supports simple translation.
     * 
     * @param transformIndex the abstract transform index to use
     * @param x translation in X
     * @param y translation in Y
     * @param z translation in Z
     */
    public void registerTransform(int transformIndex, float x, float y, float z) {
        transformRegistry.put(transformIndex, new TransformInfo(x, y, z));
    }
    
    /**
     * Clear all registered resources.
     * Useful for cleanup or reset scenarios.
     */
    public void clearAll() {
        meshRegistry.clear();
        materialRegistry.clear();
        transformRegistry.clear();
        currentShader = null;
        currentAtlas = null;
        if (spriteBatcher != null) {
            spriteBatcher.dispose();
            spriteBatcher = null;
        }
        spriteShader = null;
    }
    
    /**
     * Initialize the sprite batcher and shader for 2D rendering.
     * Called lazily on first use.
     */
    private void ensureSpriteBatcherInitialized() {
        if (spriteBatcher == null) {
            spriteBatcher = new SpriteBatcher();
        }
        if (spriteShader == null) {
            // Simple 2D sprite shader (GLSL 130 for consistency)
            String vertexSource = """
                #version 130
                in vec2 aPosition;
                in vec2 aTexCoord;
                in vec4 aColor;
                
                out vec2 vTexCoord;
                out vec4 vColor;
                
                uniform mat4 uProjection;
                
                void main() {
                    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
                    vTexCoord = aTexCoord;
                    vColor = aColor;
                }
                """;
            String fragmentSource = """
                #version 130
                in vec2 vTexCoord;
                in vec4 vColor;
                
                uniform sampler2D uTexture;
                uniform bool uUseTexture;
                
                out vec4 fragColor;
                
                void main() {
                    if (uUseTexture) {
                        fragColor = texture(uTexture, vTexCoord) * vColor;
                    } else {
                        fragColor = vColor;
                    }
                }
                """;
            spriteShader = new Shader(vertexSource, fragmentSource);
        }
    }
    
    // Current screen dimensions for 2D batching
    private int screenWidth = 800;
    private int screenHeight = 600;
    
    /**
     * Set the screen dimensions for 2D rendering.
     * Must be called when the window is resized.
     */
    public void setScreenDimensions(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }
    
    /**
     * Begin batched 2D sprite rendering.
     * Sets up orthographic projection and binds the sprite shader.
     * Call this before using fillRectBatched or addQuadToBatch.
     */
    private void beginSpriteBatch() {
        ensureSpriteBatcherInitialized();
        
        // Bind shader and set up orthographic projection
        spriteShader.use();
        
        // Create orthographic projection matrix (top-left origin)
        float[] projection = createOrthoMatrix(0, screenWidth, screenHeight, 0, -1, 1);
        spriteShader.setUniformMatrix4f("uProjection", projection);
        spriteShader.setUniform1i("uUseTexture", 0); // Default to solid color
        
        spriteBatcher.begin();
    }
    
    /**
     * End batched 2D sprite rendering.
     * Flushes all pending quads and restores state.
     */
    private void endSpriteBatch() {
        if (spriteBatcher != null && spriteBatcher.isDrawing()) {
            spriteBatcher.end();
        }
        Shader.unbind();
    }
    
    /**
     * Add a solid color quad to the current batch.
     * Must be called between beginSpriteBatch() and endSpriteBatch().
     */
    private void addQuadToBatch(float x, float y, float width, float height) {
        if (spriteBatcher == null || !spriteBatcher.isDrawing()) {
            logger.warn("addQuadToBatch called outside of active batch - quad will not be rendered");
            return;
        }
        spriteBatcher.addQuad(x, y, width, height);
    }
    
    /**
     * Set the color for subsequent batched quads.
     */
    private void setBatchColor(float r, float g, float b, float a) {
        if (spriteBatcher == null) {
            logger.warn("setBatchColor called before SpriteBatcher initialization");
            return;
        }
        spriteBatcher.setColor(r, g, b, a);
    }
    
    /**
     * Create an orthographic projection matrix.
     * Uses column-major order as expected by OpenGL.
     */
    private float[] createOrthoMatrix(float left, float right, float bottom, float top, float near, float far) {
        float[] m = new float[16];
        // Initialize all elements to 0
        for (int i = 0; i < 16; i++) m[i] = 0.0f;
        
        // Set the non-zero elements
        m[0] = 2.0f / (right - left);
        m[5] = 2.0f / (top - bottom);
        m[10] = -2.0f / (far - near);
        m[12] = -(right + left) / (right - left);
        m[13] = -(top + bottom) / (top - bottom);
        m[14] = -(far + near) / (far - near);
        m[15] = 1.0f;
        return m;
    }

    @Override
    public void beginFrame() {
        frameDepth++;
        
        // Only initialize on first nested call
        if (frameDepth == 1) {
            // Reset current state
            currentShader = null;
            currentAtlas = null;
            
            // OpenGL state setup could go here
            // For now, we assume the caller has already set up the GL state
            // (viewport, clear color, etc.) before calling beginFrame()
        }
    }
    
    @Override
    public void submit(DrawCommand cmd) {
        if (frameDepth == 0) {
            throw new IllegalStateException("No active frame - beginFrame() must be called first");
        }
        
        if (cmd == null) {
            throw new NullPointerException("DrawCommand cannot be null");
        }
        
        // Stage 4: Handle UI render pass differently from OPAQUE/TRANSPARENT
        if (cmd.pass == RenderPass.UI) {
            submitUICommand(cmd);
            return;
        }
        
        // Original 3D mesh rendering for OPAQUE, TRANSPARENT, SHADOW passes
        // Look up resources
        ChunkVAO vao = meshRegistry.get(cmd.meshId);
        MaterialInfo material = materialRegistry.get(cmd.materialId);
        TransformInfo transform = transformRegistry.get(cmd.transformIndex);
        
        // Validate resources exist
        if (vao == null) {
            logger.warn("Mesh ID {} not found in registry, skipping draw command", cmd.meshId);
            return;
        }
        
        if (material == null) {
            logger.warn("Material ID {} not found in registry, skipping draw command", cmd.materialId);
            return;
        }
        
        // Transform is optional for now - if not found, assume identity
        if (transform == null) {
            logger.warn("Transform ID {} not found in registry, using identity transform", cmd.transformIndex);
            transform = new TransformInfo(0, 0, 0);
        }
        
        // Set up material (shader + texture) if it changed
        if (material.shader != currentShader || material.atlas != currentAtlas) {
            // Unbind previous shader/texture
            if (currentShader != null) {
                Shader.unbind();
            }
            if (currentAtlas != null) {
                glBindTexture(GL_TEXTURE_2D, 0);
            }
            
            // Bind new shader
            material.shader.use();
            material.shader.setTextureSampler(0);
            material.shader.applyDefaultLighting();
            
            // Bind texture atlas
            glEnable(GL_TEXTURE_2D);
            glActiveTexture(GL_TEXTURE0);
            if (material.atlas != null) {
                material.atlas.bind();
            }
            
            currentShader = material.shader;
            currentAtlas = material.atlas;
        }
        
        // Apply transformation
        glPushMatrix();
        glTranslatef(transform.translateX, transform.translateY, transform.translateZ);
        
        // Render the mesh
        vao.render();
        
        // Restore transformation
        glPopMatrix();
    }
    
    /**
     * Handle UI render pass commands (Stage 4).
     * UI commands use a simplified rendering path for 2D elements.
     * 
     * @param cmd the UI draw command
     */
    private void submitUICommand(DrawCommand cmd) {
        // Handle different UI element types based on meshId
        // See UIMeshIds for the complete list of constants
        
        if (cmd.meshId == UIMeshIds.CROSSHAIR) {
            // Crosshair rendering
            submitCrosshairCommand(cmd);
        } else if (UIMeshIds.isItemMeshId(cmd.meshId)) {
            // Item rendering (fallback, cube, stairs, flat)
            submitItemCommand(cmd);
        } else if (cmd.meshId == UIMeshIds.HOTBAR) {
            // Hotbar rendering
            submitHotbarCommand(cmd);
        } else if (cmd.meshId == UIMeshIds.DEBUG_TEXT) {
            // Debug info text
            submitDebugTextCommand(cmd);
        } else if (cmd.meshId == UIMeshIds.COMMAND_UI) {
            // Command UI
            submitCommandUICommand(cmd);
        } else if (cmd.meshId == UIMeshIds.SYSTEM_INFO) {
            // System info text
            submitSystemInfoCommand(cmd);
        } else if (cmd.meshId == UIMeshIds.TOOLTIP) {
            // Tooltip
            submitTooltipCommand(cmd);
        }
    }
    
    /**
     * Render a crosshair command.
     */
    private void submitCrosshairCommand(DrawCommand cmd) {
        // Decode crosshair data from materialId
        boolean horizontal = (cmd.materialId & 1) == 1;
        int centerX = (cmd.materialId >> 1) & 0xFFF;
        int centerY = (cmd.materialId >> 13) & 0xFFF;
        int size = (cmd.materialId >> 25) & 0xFF;
        
        // Use batched rendering instead of immediate mode.
        // NOTE: Crosshair renders both horizontal and vertical bars in separate commands,
        // but each is batched individually. Future optimization could combine them.
        beginSpriteBatch();
        setBatchColor(1f, 1f, 1f, 1f);
        
        if (horizontal) {
            float thickness = 2f;
            addQuadToBatch(centerX - size/2f, centerY - thickness/2, size, thickness);
        } else {
            float thickness = 2f;
            addQuadToBatch(centerX - thickness/2, centerY - size/2f, thickness, size);
        }
        
        endSpriteBatch();
    }
    
    /**
     * Render an item command.
     * Looks up the item from ItemRenderLogic registry and delegates to ItemRenderer.
     */
    private void submitItemCommand(DrawCommand cmd) {
        // Get item info from registry using transformIndex as the item ID
        ItemRenderLogic.ItemStackRenderInfo itemInfo = ItemRenderLogic.getItemInfo(cmd.transformIndex);
        
        if (itemInfo == null) {
            logger.warn("Item info not found for transformIndex: {}", cmd.transformIndex);
            return;
        }
        
        // Get the item renderer instance (avoids static method usage)
        OpenGLItemRenderer itemRenderer = OpenGLItemRenderer.getInstance();
        
        // Render based on meshId type using UIMeshIds constants
        switch (cmd.meshId) {
            case UIMeshIds.ITEM_FALLBACK:
                // Fallback item (magenta square)
                itemRenderer.renderFallbackItem(itemInfo.x, itemInfo.y, itemInfo.size);
                break;
            case UIMeshIds.ITEM_CUBE:
            case UIMeshIds.ITEM_STAIRS:
            case UIMeshIds.ITEM_FLAT:
                // Delegate to ItemRenderer's existing rendering methods
                // Use the standard rendering path which handles all item types
                // applyInventoryOffset=true for proper hotbar positioning (matches legacy behavior)
                itemRenderer.renderItem(itemInfo.stack, itemInfo.x, itemInfo.y, itemInfo.size, true);
                break;
        }
    }
    
    /**
     * Render a hotbar command (background or selection).
     */
    private void submitHotbarCommand(DrawCommand cmd) {
        // Decode hotbar data from materialId
        int type = cmd.materialId & 0x3; // 0=background, 1=selection
        int x = (cmd.materialId >> 2) & 0xFFF;
        int y = (cmd.materialId >> 14) & 0xFFF;
        
        // Calculate width and height based on type and HOTBAR_SCALE
        float HOTBAR_SCALE = 3.0f;
        int width = (type == 0) ? (int)(182 * HOTBAR_SCALE) : (int)(24 * HOTBAR_SCALE);
        int height = (type == 0) ? (int)(22 * HOTBAR_SCALE) : (int)(24 * HOTBAR_SCALE);
        
        // Load textures using HotbarRenderer's texture paths (cached via textureCache)
        String texturePath = (type == 0) ? 
            "/assets/textures/gui/sprites/hud/hotbar.png" :
            "/assets/textures/gui/sprites/hud/hotbar_selection.png";
        
        // Use cached texture to avoid loading textures every frame
        Texture texture = textureCache.get(texturePath);
        if (texture == null) {
            texture = Texture.load(texturePath);
            if (texture != null) {
                textureCache.put(texturePath, texture);
            }
        }
        
        if (texture != null) {
            glEnable(GL_TEXTURE_2D);
            texture.bind();
            glColor4f(1f, 1f, 1f, 1f);
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(x, y);
            glTexCoord2f(1, 1); glVertex2f(x + width, y);
            glTexCoord2f(1, 0); glVertex2f(x + width, y + height);
            glTexCoord2f(0, 0); glVertex2f(x, y + height);
            glEnd();
            
            glDisable(GL_TEXTURE_2D);
        }
    }
    
    /**
     * Render a debug info text command.
     */
    private void submitDebugTextCommand(DrawCommand cmd) {
        // Get text info from registry
        UIRenderLogic.TextRenderInfo textInfo = UIRenderLogic.getTextInfo(cmd.transformIndex);
        
        if (textInfo == null) {
            logger.warn("Text info not found for transformIndex: {}", cmd.transformIndex);
            return;
        }
        
        // Delegate to UIRenderHelper for text rendering
        UIRenderHelper.drawText(textInfo.text, textInfo.x, textInfo.y, textInfo.scale, textInfo.color);
    }
    
    /**
     * Render a command UI command (overlay or feedback).
     */
    private void submitCommandUICommand(DrawCommand cmd) {
        // Decode command UI data
        int type = cmd.materialId & 0x3; // 0=overlay, 1=feedback
        int x = (cmd.materialId >> 2) & 0xFFF;
        int y = (cmd.materialId >> 14) & 0xFFF;
        int width = (cmd.materialId >> 26) & 0x3F;
        
        // Get text info from registry
        UIRenderLogic.TextRenderInfo textInfo = UIRenderLogic.getTextInfo(cmd.transformIndex);
        
        if (type == 0) {
            // Command overlay - draw box and text
            int boxHeight = 50;
            
            // Draw semi-transparent background
            UIRenderHelper.setColor(0x000000, 0.7f);
            UIRenderHelper.fillRect(x, y, width, boxHeight);
            
            // Draw border
            UIRenderHelper.setColor(0xFFFFFF, 1.0f);
            glBegin(GL_LINE_LOOP);
            glVertex2f(x, y);
            glVertex2f(x + width, y);
            glVertex2f(x + width, y + boxHeight);
            glVertex2f(x, y + boxHeight);
            glEnd();
            
            // Draw text
            if (textInfo != null) {
                UIRenderHelper.drawText(textInfo.text, textInfo.x, textInfo.y, textInfo.scale, textInfo.color);
            }
        } else {
            // Command feedback - draw background and text
            int padding = 8;
            int bgHeight = (int)(16 * 1.2f) + padding * 2;
            
            // Draw background
            UIRenderHelper.setColor(0x000000, 0.6f);
            UIRenderHelper.fillRect(x, y, width, bgHeight);
            
            // Draw text
            if (textInfo != null) {
                UIRenderHelper.drawText(textInfo.text, textInfo.x, textInfo.y, textInfo.scale, textInfo.color);
            }
        }
    }
    
    @Override
    public void endFrame() {
        if (frameDepth == 0) {
            throw new IllegalStateException("No active frame - beginFrame() must be called first");
        }
        
        frameDepth--;
        
        // Only cleanup on final nested call
        if (frameDepth == 0) {
            // Unbind any active resources
            if (currentShader != null) {
                Shader.unbind();
                currentShader = null;
            }
            
            if (currentAtlas != null) {
                glBindTexture(GL_TEXTURE_2D, 0);
                currentAtlas = null;
            }
        }
    }
    
    /**
     * Check if a frame is currently active.
     * Useful for testing and debugging.
     * 
     * @return true if between beginFrame() and endFrame()
     */
    public boolean isFrameActive() {
        return frameDepth > 0;
    }
    
    /**
     * Get the number of meshes currently registered.
     * Useful for monitoring and debugging.
     * 
     * @return number of registered meshes
     */
    public int getMeshCount() {
        return meshRegistry.size();
    }
    
    /**
     * Get the number of materials currently registered.
     * Useful for monitoring and debugging.
     * 
     * @return number of registered materials
     */
    public int getMaterialCount() {
        return materialRegistry.size();
    }
    
    /**
     * Get the number of transforms currently registered.
     * Useful for monitoring and debugging.
     * 
     * @return number of registered transforms
     */
    public int getTransformCount() {
        return transformRegistry.size();
    }
    
    /**
     * Check if a mesh ID is registered.
     * 
     * @param meshId the mesh ID to check
     * @return true if the mesh is registered
     */
    public boolean hasMesh(int meshId) {
        return meshRegistry.containsKey(meshId);
    }
    
    /**
     * Check if a material ID is registered.
     * 
     * @param materialId the material ID to check
     * @return true if the material is registered
     */
    public boolean hasMaterial(int materialId) {
        return materialRegistry.containsKey(materialId);
    }
    
    /**
     * Check if a transform index is registered.
     * 
     * @param transformIndex the transform index to check
     * @return true if the transform is registered
     */
    public boolean hasTransform(int transformIndex) {
        return transformRegistry.containsKey(transformIndex);
    }
    
    // ===== Stage 4: UI/2D Rendering Support =====
    
    /**
     * Information about a 2D quad for UI rendering.
     * Used for rendering UI elements like hotbar, crosshair, etc.
     * 
     * @since Stage 4
     */
    private static class UIQuadInfo {
        final float x, y, width, height;
        final int textureId;
        
        UIQuadInfo(float x, float y, float width, float height, int textureId) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.textureId = textureId;
        }
    }
    
    // UI quad registry for 2D elements
    private final Map<Integer, UIQuadInfo> uiQuadRegistry = new HashMap<>();
    
    /**
     * Register a UI quad for 2D rendering.
     * 
     * @param quadId unique ID for this quad
     * @param x screen X position
     * @param y screen Y position
     * @param width quad width
     * @param height quad height
     * @param textureId OpenGL texture ID
     */
    public void registerUIQuad(int quadId, float x, float y, float width, float height, int textureId) {
        uiQuadRegistry.put(quadId, new UIQuadInfo(x, y, width, height, textureId));
    }
    
    /**
     * Unregister a UI quad.
     * 
     * @param quadId the quad ID to remove
     * @return the quad info that was removed, or null if not found
     */
    public UIQuadInfo unregisterUIQuad(int quadId) {
        return uiQuadRegistry.remove(quadId);
    }
    
    /**
     * Render a UI quad directly (for Stage 4 UI support).
     * This method handles 2D quads differently from 3D chunk meshes.
     * 
     * @param quadInfo the quad to render
     */
    private void renderUIQuad(UIQuadInfo quadInfo) {
        // Bind texture
        glBindTexture(GL_TEXTURE_2D, quadInfo.textureId);
        
        // Render quad using immediate mode (simple for Stage 4)
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(quadInfo.x, quadInfo.y);
        glTexCoord2f(1, 1); glVertex2f(quadInfo.x + quadInfo.width, quadInfo.y);
        glTexCoord2f(1, 0); glVertex2f(quadInfo.x + quadInfo.width, quadInfo.y + quadInfo.height);
        glTexCoord2f(0, 0); glVertex2f(quadInfo.x, quadInfo.y + quadInfo.height);
        glEnd();
    }
    
    /**
     * Handle system info text rendering.
     * meshId: -9
     * materialId: number of lines
     * transformIndex: first text ID
     */
    private void submitSystemInfoCommand(DrawCommand cmd) {
        int numLines = cmd.materialId;
        int firstTextId = cmd.transformIndex;
        
        // Look up and render all text lines
        for (int i = 0; i < numLines; i++) {
            UIRenderLogic.TextRenderInfo textInfo = UIRenderLogic.getTextInfo(firstTextId + i);
            if (textInfo != null) {
                // Right-aligned text rendering
                UIRenderHelper.drawTextRightAligned(textInfo.text, (int)textInfo.x, (int)textInfo.y, textInfo.scale, textInfo.color);
            }
        }
    }
    
    /**
     * Handle tooltip rendering.
     * meshId: -10
     * materialId: position (x, y packed)
     * transformIndex: text ID
     * 
     * Tooltips come in pairs: first command has position, second has size
     */
    private void submitTooltipCommand(DrawCommand cmd) {
        // Check if this is size info (high bit set in transformIndex)
        if ((cmd.transformIndex & 0x80000000) != 0) {
            // This is the size command, skip it (we'll get size when we need it)
            return;
        }
        
        // Decode position from materialId
        int x = cmd.materialId & 0xFFFF;
        int y = (cmd.materialId >> 16) & 0xFFFF;
        
        // Get text info
        int textId = cmd.transformIndex;
        UIRenderLogic.TextRenderInfo textInfo = UIRenderLogic.getTextInfo(textId);
        if (textInfo == null) return;
        
        // Render tooltip text directly
        float TOOLTIP_PADDING = 13.5f;
        glColor4f(1f, 1f, 1f, 1f);
        mattmc.client.renderer.backend.opengl.gui.components.OpenGLTextRenderer.drawText(
            textInfo.text, x + TOOLTIP_PADDING, y + TOOLTIP_PADDING, textInfo.scale);
    }
    
    /**
     * Setup 2D orthographic projection for UI rendering.
     * 
     * <p>Configures OpenGL for 2D screen-space rendering with an orthographic projection
     * where (0,0) is the top-left corner and coordinates map directly to screen pixels.
     * Also enables blending for transparent UI elements.
     * 
     * @param screenWidth the width of the screen/viewport in pixels
     * @param screenHeight the height of the screen/viewport in pixels
     */
    @Override
    public void setup2DProjection(int screenWidth, int screenHeight) {
        // Setup projection matrix for 2D rendering
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        // Setup modelview matrix
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Enable blending for transparent UI elements
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
    
    /**
     * Restore the previous projection state after 2D rendering.
     * 
     * <p>Pops the projection and modelview matrices that were pushed by
     * {@link #setup2DProjection(int, int)} and disables blending.
     */
    @Override
    public void restore2DProjection() {
        // Disable blending
        glDisable(GL_BLEND);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Get the display resolution using GLFW.
     * 
     * @param windowHandle GLFW window handle
     * @return Display resolution string (e.g., "1920x1080")
     */
    @Override
    public String getDisplayResolution(long windowHandle) {
        return OpenGLSystemInfo.getDisplayResolution(windowHandle);
    }
    
    /**
     * Get the GPU name using OpenGL.
     * 
     * @return Graphics card name from GL_RENDERER
     */
    @Override
    public String getGPUName() {
        return OpenGLSystemInfo.getGPUName();
    }
    
    /**
     * Get GPU usage percentage.
     * 
     * @return GPU usage percentage or -1 if not available
     */
    @Override
    public int getGPUUsage() {
        return OpenGLSystemInfo.getGPUUsage();
    }
    
    /**
     * Get GPU VRAM usage.
     * 
     * @return VRAM usage string or "N/A" if not available
     */
    @Override
    public String getGPUVRAMUsage() {
        return OpenGLSystemInfo.getGPUVRAMUsage();
    }
    
    /**
     * Apply regional blur effect using OpenGL framebuffers and shaders.
     * 
     * <p>Lazily initializes the blur helper on first use. The blur effect
     * captures the screen region, applies Gaussian blur (horizontal + vertical pass),
     * darkens the result, and renders it back to the same region.
     * 
     * @param x X position of the blur region
     * @param y Y position of the blur region
     * @param width Width of the blur region
     * @param height Height of the blur region
     * @param screenWidth Full screen width
     * @param screenHeight Full screen height
     */
    @Override
    public void applyRegionalBlur(float x, float y, float width, float height,
                                   int screenWidth, int screenHeight) {
        // Lazily initialize blur helper
        if (blurHelper == null) {
            blurHelper = new AbstractBlurBox();
        }
        
        // Delegate to blur helper
        blurHelper.applyRegionalBlur(x, y, width, height, screenWidth, screenHeight);
    }
    
    /**
     * Draw a rounded rectangle border using OpenGL line rendering.
     * 
     * <p>Lazily initializes the blur helper on first use (which contains
     * the rounded border drawing logic). Draws a smooth rounded border
     * around the specified rectangle.
     * 
     * @param x X position of the rectangle
     * @param y Y position of the rectangle
     * @param width Width of the rectangle
     * @param height Height of the rectangle
     * @param radius Corner radius
     * @param borderWidth Width of the border line
     * @param r Red component (0.0-1.0)
     * @param g Green component (0.0-1.0)
     * @param b Blue component (0.0-1.0)
     * @param a Alpha component (0.0-1.0)
     */
    @Override
    public void drawRoundedRectBorder(float x, float y, float width, float height, float radius,
                                      float borderWidth, float r, float g, float b, float a) {
        // Lazily initialize blur helper (contains shared drawing utilities)
        if (blurHelper == null) {
            blurHelper = new AbstractBlurBox();
        }
        
        // Delegate to blur helper's drawing method
        blurHelper.drawRoundedRectBorder(x, y, width, height, radius, borderWidth, r, g, b, a);
    }
    
    /**
     * Reset the OpenGL color to white.
     * This ensures that subsequent drawing operations (like text) appear in white
     * instead of inheriting colors from previous operations (like blue borders).
     */
    @Override
    public void resetColor() {
        currentColorR = 1.0f;
        currentColorG = 1.0f;
        currentColorB = 1.0f;
        currentColorA = 1.0f;
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    @Override
    public void setColor(int rgb, float alpha) {
        currentColorR = ((rgb >> 16) & 0xFF) / 255f;
        currentColorG = ((rgb >> 8) & 0xFF) / 255f;
        currentColorB = (rgb & 0xFF) / 255f;
        currentColorA = alpha;
        glColor4f(currentColorR, currentColorG, currentColorB, currentColorA);
    }
    
    @Override
    public void fillRect(float x, float y, float width, float height) {
        // Use batched rendering instead of immediate mode.
        // NOTE: Currently each fillRect call creates its own batch for simplicity.
        // For optimal performance, callers should use begin/endSpriteBatch externally
        // to batch multiple quads together. This infrastructure allows that optimization
        // in the future without changing the API.
        beginSpriteBatch();
        setBatchColor(currentColorR, currentColorG, currentColorB, currentColorA);
        addQuadToBatch(x, y, width, height);
        endSpriteBatch();
    }
    
    @Override
    public void drawRect(float x, float y, float width, float height) {
        // Line rendering still uses immediate mode (cannot batch with quads)
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }
    
    @Override
    public void drawLine(float x1, float y1, float x2, float y2) {
        // Line rendering still uses immediate mode (cannot batch with quads)
        glBegin(GL_LINES);
        glVertex2f(x1, y1);
        glVertex2f(x2, y2);
        glEnd();
    }
    
    @Override
    public void enableBlend() {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
    
    @Override
    public void disableBlend() {
        glDisable(GL_BLEND);
    }
    
    @Override
    public void drawText(String text, float x, float y, float scale) {
        mattmc.client.renderer.backend.opengl.gui.components.OpenGLTextRenderer.drawText(text, x, y, scale);
    }
    
    @Override
    public void drawCenteredText(String text, float centerX, float y, float scale) {
        mattmc.client.renderer.backend.opengl.gui.components.OpenGLTextRenderer.drawCenteredText(text, centerX, y, scale);
    }
    
    @Override
    public float getTextWidth(String text, float scale) {
        return mattmc.client.renderer.backend.opengl.gui.components.OpenGLTextRenderer.getTextWidth(text, scale);
    }
    
    @Override
    public float getTextHeight(String text, float scale) {
        return mattmc.client.renderer.backend.opengl.gui.components.OpenGLTextRenderer.getTextHeight(text, scale);
    }
    
    // === Input Callback Implementations ===
    
    @Override
    public void setCursorPosCallback(long windowHandle, CursorPosCallback callback) {
        org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback(windowHandle, (h, x, y) -> {
            if (callback != null) callback.invoke(x, y);
        });
    }
    
    @Override
    public void setMouseButtonCallback(long windowHandle, MouseButtonCallback callback) {
        org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback(windowHandle, (h, button, action, mods) -> {
            if (callback != null) callback.invoke(button, action, mods);
        });
    }
    
    @Override
    public void setFramebufferSizeCallback(long windowHandle, FramebufferSizeCallback callback) {
        org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback(windowHandle, (win, newW, newH) -> {
            if (callback != null) callback.invoke(newW, newH);
        });
    }
    
    @Override
    public void setKeyCallback(long windowHandle, KeyCallback callback) {
        org.lwjgl.glfw.GLFW.glfwSetKeyCallback(windowHandle, (h, key, scancode, action, mods) -> {
            if (callback != null) callback.invoke(key, scancode, action, mods);
        });
    }
    
    @Override
    public void setCharCallback(long windowHandle, CharCallback callback) {
        org.lwjgl.glfw.GLFW.glfwSetCharCallback(windowHandle, (h, codepoint) -> {
            if (callback != null) callback.invoke(codepoint);
        });
    }
    
    @Override
    public void setScrollCallback(long windowHandle, ScrollCallback callback) {
        org.lwjgl.glfw.GLFW.glfwSetScrollCallback(windowHandle, (h, xoffset, yoffset) -> {
            if (callback != null) callback.invoke(xoffset, yoffset);
        });
    }
    
    @Override
    public void setViewport(int x, int y, int width, int height) {
        glViewport(x, y, width, height);
    }
    
    // === Button Rendering ===
    
    @Override
    public void drawButton(mattmc.client.gui.components.Button button) {
        mattmc.client.renderer.backend.opengl.gui.components.OpenGLButtonRenderer.drawButton(button);
    }
    
    @Override
    public void drawButton(mattmc.client.gui.components.Button button, boolean selected) {
        mattmc.client.renderer.backend.opengl.gui.components.OpenGLButtonRenderer.drawButton(button, selected);
    }
    
    // === Texture Management ===
    
    // Cache of loaded textures: path -> Texture
    private final Map<String, Texture> textureCache = new HashMap<>();
    // Reverse map: texture ID -> path (for cleanup)
    private final Map<Integer, String> textureIdToPath = new HashMap<>();
    
    @Override
    public int loadTexture(String path) {
        Texture tex = textureCache.get(path);
        if (tex == null) {
            tex = Texture.load(path);
            textureCache.put(path, tex);
            textureIdToPath.put(tex.id, path);
        }
        return tex.id;
    }
    
    @Override
    public void drawTexture(int textureId, float x, float y, float width, float height) {
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glBindTexture(GL_TEXTURE_2D, textureId);
        glColor4f(1f, 1f, 1f, 1f);
        
        // Note: Textures are loaded with vertical flip, so we flip the V coordinates
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(x, y);
        glTexCoord2f(1, 1); glVertex2f(x + width, y);
        glTexCoord2f(1, 0); glVertex2f(x + width, y + height);
        glTexCoord2f(0, 0); glVertex2f(x, y + height);
        glEnd();
        
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
    }
    
    @Override
    public int getTextureWidth(int textureId) {
        String path = textureIdToPath.get(textureId);
        if (path != null) {
            Texture tex = textureCache.get(path);
            if (tex != null) {
                return tex.width;
            }
        }
        return 0;
    }
    
    @Override
    public int getTextureHeight(int textureId) {
        String path = textureIdToPath.get(textureId);
        if (path != null) {
            Texture tex = textureCache.get(path);
            if (tex != null) {
                return tex.height;
            }
        }
        return 0;
    }
    
    @Override
    public void releaseTexture(int textureId) {
        String path = textureIdToPath.remove(textureId);
        if (path != null) {
            Texture tex = textureCache.remove(path);
            if (tex != null) {
                tex.close();
            }
        }
    }
    
    // === Matrix Operations ===
    
    @Override
    public void pushMatrix() {
        glPushMatrix();
    }
    
    @Override
    public void popMatrix() {
        glPopMatrix();
    }
    
    @Override
    public void translateMatrix(float x, float y, float z) {
        glTranslatef(x, y, z);
    }
    
    @Override
    public void rotateMatrix(float angle, float x, float y, float z) {
        glRotatef(angle, x, y, z);
    }
    
    @Override
    public void updateFrustum(mattmc.client.renderer.Frustum frustum) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.FloatBuffer projBuffer = stack.mallocFloat(16);
            java.nio.FloatBuffer modlBuffer = stack.mallocFloat(16);
            
            // Get current matrices from OpenGL
            glGetFloatv(GL_PROJECTION_MATRIX, projBuffer);
            glGetFloatv(GL_MODELVIEW_MATRIX, modlBuffer);
            
            // Convert to arrays
            float[] projectionMatrix = new float[16];
            float[] modelviewMatrix = new float[16];
            projBuffer.get(projectionMatrix);
            modlBuffer.get(modelviewMatrix);
            
            // Update frustum with current matrices
            frustum.update(projectionMatrix, modelviewMatrix);
        }
    }

    // === Window Control ===
    
    @Override
    public void setCursorMode(long windowHandle, int mode) {
        org.lwjgl.glfw.GLFW.glfwSetInputMode(windowHandle, org.lwjgl.glfw.GLFW.GLFW_CURSOR, mode);
    }
    
    @Override
    public void setWindowShouldClose(long windowHandle, boolean shouldClose) {
        org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, shouldClose);
    }
    
    @Override
    public mattmc.client.renderer.panorama.PanoramaRenderer createPanoramaRenderer(String basePath, String extension) {
        CubeMap sky = CubeMap.load(basePath, extension);
        return new OpenGLPanoramaRenderer(sky);
    }
    
    @Override
    public mattmc.client.renderer.item.ItemRenderer getItemRenderer() {
        return OpenGLItemRenderer.getInstance();
    }
    
    // === 3D Rendering Methods ===
    
    @Override
    public void setupPerspectiveProjection(float fov, float aspect, float nearPlane, float farPlane) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float top = (float) (Math.tan(Math.toRadians(fov * 0.5)) * nearPlane);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        glFrustum(left, right, bottom, top, nearPlane, farPlane);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }
    
    @Override
    public void setClearColor(float r, float g, float b, float a) {
        glClearColor(r, g, b, a);
    }
    
    @Override
    public void clearBuffers() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }
    
    @Override
    public void enableDepthTest() {
        glEnable(GL_DEPTH_TEST);
    }
    
    @Override
    public void disableDepthTest() {
        glDisable(GL_DEPTH_TEST);
    }
    
    @Override
    public void enableCullFace() {
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }
    
    @Override
    public void disableCullFace() {
        glDisable(GL_CULL_FACE);
    }
    
    @Override
    public void enableLighting() {
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0);
        glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
    }
    
    @Override
    public void disableLighting() {
        glDisable(GL_LIGHTING);
        glDisable(GL_LIGHT0);
    }
    
    @Override
    public void setupDirectionalLight(float dirX, float dirY, float dirZ, float brightness) {
        // Position with w=0 makes it directional (parallel rays)
        float[] lightPos = {dirX, dirY, dirZ, 0.0f};
        glLightfv(GL_LIGHT0, GL_POSITION, lightPos);
        
        // Set light colors based on brightness
        float[] ambient = {0.4f * brightness, 0.4f * brightness, 0.4f * brightness, 1.0f};
        float[] diffuse = {brightness, brightness, brightness, 1.0f};
        float[] specular = {0.0f, 0.0f, 0.0f, 1.0f};  // No specular for MattMC-like look
        
        glLightfv(GL_LIGHT0, GL_AMBIENT, ambient);
        glLightfv(GL_LIGHT0, GL_DIFFUSE, diffuse);
        glLightfv(GL_LIGHT0, GL_SPECULAR, specular);
        
        // Set global ambient light (very dim)
        float[] globalAmbient = {0.2f, 0.2f, 0.2f, 1.0f};
        glLightModelfv(GL_LIGHT_MODEL_AMBIENT, globalAmbient);
    }
    
    @Override
    public void loadIdentityMatrix() {
        glLoadIdentity();
    }
    
    @Override
    public void begin3DLines() {
        glBegin(GL_LINES);
    }
    
    @Override
    public void end3DLines() {
        glEnd();
    }
    
    @Override
    public void addLineVertex(float x, float y, float z) {
        glVertex3f(x, y, z);
    }
    
    @Override
    public void enableTexture2D() {
        glEnable(GL_TEXTURE_2D);
    }
    
    @Override
    public void disableTexture2D() {
        glDisable(GL_TEXTURE_2D);
    }
    
    @Override
    public boolean isTexture2DEnabled() {
        return glIsEnabled(GL_TEXTURE_2D);
    }
}
