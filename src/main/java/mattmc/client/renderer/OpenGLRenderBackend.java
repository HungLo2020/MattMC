package mattmc.client.renderer;

import mattmc.client.renderer.chunk.ChunkVAO;
import mattmc.client.renderer.texture.TextureAtlas;
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
        // For Stage 4, meshId = -1 indicates a crosshair UI element
        // The materialId encodes position/size data
        if (cmd.meshId == -1) {
            // Decode crosshair data from materialId
            boolean horizontal = (cmd.materialId & 1) == 1;
            int centerX = (cmd.materialId >> 1) & 0xFFF;
            int centerY = (cmd.materialId >> 13) & 0xFFF;
            int size = (cmd.materialId >> 25) & 0xFF;
            
            // Render the crosshair quad using immediate mode
            // (This is a Stage 4 implementation - could be optimized later)
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
        // Future: handle other UI element types (hotbar, etc.)
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
}
