package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.VoxelLitShader;

import mattmc.client.renderer.ItemRenderLogic;

import mattmc.client.renderer.UIRenderLogic;

import mattmc.client.renderer.backend.RenderPass;

import mattmc.client.renderer.backend.DrawCommand;

import mattmc.client.renderer.backend.RenderBackend;

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
    
    // Frame state
    private boolean frameActive = false;
    
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
    }
    
    @Override
    public void beginFrame() {
        if (frameActive) {
            throw new IllegalStateException("Frame already active - endFrame() must be called before beginFrame()");
        }
        
        frameActive = true;
        
        // Reset current state
        currentShader = null;
        currentAtlas = null;
        
        // OpenGL state setup could go here
        // For now, we assume the caller has already set up the GL state
        // (viewport, clear color, etc.) before calling beginFrame()
    }
    
    @Override
    public void submit(DrawCommand cmd) {
        if (!frameActive) {
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
                VoxelLitShader.unbind();
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
        // Handle different UI element types based on meshId:
        // -1 = crosshair
        // -2 to -5 = items (fallback, cube, stairs, flat)
        // -6 = hotbar (background/selection)
        // -7 = debug info text
        // -8 = command UI (overlay/feedback)
        // -9 = system info text
        // -10 = tooltip
        
        if (cmd.meshId == -1) {
            // Crosshair rendering
            submitCrosshairCommand(cmd);
        } else if (cmd.meshId <= -2 && cmd.meshId >= -5) {
            // Item rendering
            submitItemCommand(cmd);
        } else if (cmd.meshId == -6) {
            // Hotbar rendering
            submitHotbarCommand(cmd);
        } else if (cmd.meshId == -7) {
            // Debug info text
            submitDebugTextCommand(cmd);
        } else if (cmd.meshId == -8) {
            // Command UI
            submitCommandUICommand(cmd);
        } else if (cmd.meshId == -9) {
            // System info text
            submitSystemInfoCommand(cmd);
        } else if (cmd.meshId == -10) {
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
        
        // Render the crosshair quad using immediate mode
        if (horizontal) {
            float thickness = 2f;
            glColor4f(1f, 1f, 1f, 1f);
            glBegin(GL_QUADS);
            glVertex2f(centerX - size/2f, centerY - thickness/2);
            glVertex2f(centerX + size/2f, centerY - thickness/2);
            glVertex2f(centerX + size/2f, centerY + thickness/2);
            glVertex2f(centerX - size/2f, centerY + thickness/2);
            glEnd();
        } else {
            float thickness = 2f;
            glColor4f(1f, 1f, 1f, 1f);
            glBegin(GL_QUADS);
            glVertex2f(centerX - thickness/2, centerY - size/2f);
            glVertex2f(centerX + thickness/2, centerY - size/2f);
            glVertex2f(centerX + thickness/2, centerY + size/2f);
            glVertex2f(centerX - thickness/2, centerY + size/2f);
            glEnd();
        }
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
        
        // Render based on meshId type
        switch (cmd.meshId) {
            case -2:
                // Fallback item (magenta square)
                ItemRenderer.renderFallbackItem(itemInfo.x, itemInfo.y, itemInfo.size);
                break;
            case -3:
            case -4:
            case -5:
                // Delegate to ItemRenderer's existing rendering methods
                // Use the standard rendering path which handles all item types
                // applyInventoryOffset=true for proper hotbar positioning (matches legacy behavior)
                ItemRenderer.renderItem(itemInfo.stack, itemInfo.x, itemInfo.y, itemInfo.size, true);
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
        
        // Load textures using HotbarRenderer's texture paths
        String texturePath = (type == 0) ? 
            "/assets/textures/gui/sprites/hud/hotbar.png" :
            "/assets/textures/gui/sprites/hud/hotbar_selection.png";
        
        Texture texture = 
            Texture.load(texturePath);
        
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
        if (!frameActive) {
            throw new IllegalStateException("No active frame - beginFrame() must be called first");
        }
        
        // Unbind any active resources
        if (currentShader != null) {
            VoxelLitShader.unbind();
            currentShader = null;
        }
        
        if (currentAtlas != null) {
            glBindTexture(GL_TEXTURE_2D, 0);
            currentAtlas = null;
        }
        
        frameActive = false;
    }
    
    /**
     * Check if a frame is currently active.
     * Useful for testing and debugging.
     * 
     * @return true if between beginFrame() and endFrame()
     */
    public boolean isFrameActive() {
        return frameActive;
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
                SystemInfoRenderer.renderSystemInfoLine(textInfo.text, (int)textInfo.x, (int)textInfo.y, textInfo.scale, textInfo.color);
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
        
        // Approximate size calculation (tooltips need size for blur/border)
        float TOOLTIP_PADDING = 13.5f;
        float textWidth = textInfo.text.length() * 8 * textInfo.scale;
        float textHeight = 16 * textInfo.scale;
        float boxWidth = textWidth + TOOLTIP_PADDING * 2;
        float boxHeight = textHeight + TOOLTIP_PADDING * 2;
        
        // Render tooltip using TooltipRenderer
        TooltipRenderer.renderTooltipDirect(textInfo.text, x, y, (int)boxWidth, (int)boxHeight, TOOLTIP_PADDING);
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
}
