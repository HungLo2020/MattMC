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
}
