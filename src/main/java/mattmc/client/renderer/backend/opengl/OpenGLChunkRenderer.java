package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.chunk.ChunkMeshRegistry;
import mattmc.client.renderer.level.ChunkRenderer;
import mattmc.client.renderer.VoxelLitShader;
import mattmc.world.level.chunk.LevelChunk;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of chunk rendering.
 * 
 * <p>This class handles only the OpenGL-specific rendering of chunks using
 * VAO/VBO with texture atlas and per-vertex lighting shaders. It does NOT
 * manage chunk mesh caching - that responsibility belongs to
 * {@link OpenGLChunkMeshManager}.
 * 
 * <p><b>Architecture:</b> This class is part of the rendering backend and
 * should only be used from within the backend/opengl package or via the
 * {@link ChunkRenderer} interface.
 * 
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Shader initialization and binding</li>
 *   <li>Texture atlas binding</li>
 *   <li>VAO rendering</li>
 *   <li>OpenGL state management for chunk rendering</li>
 * </ul>
 * 
 * @see ChunkRenderer
 * @see OpenGLChunkMeshManager
 */
public class OpenGLChunkRenderer implements ChunkRenderer {
    private static final Logger logger = LoggerFactory.getLogger(OpenGLChunkRenderer.class);

    // Reference to mesh manager for getting VAOs
    private OpenGLChunkMeshManager meshManager;
    
    // Texture atlas for rendering
    private TextureAtlas textureAtlas = null;
    
    // Shader for rendering
    private VoxelLitShader shader = null;
    
    /**
     * Create a new OpenGL chunk renderer.
     * 
     * @param meshManager the mesh manager for accessing VAOs
     */
    public OpenGLChunkRenderer(OpenGLChunkMeshManager meshManager) {
        this.meshManager = meshManager;
    }
    
    /**
     * Create a new OpenGL chunk renderer without a mesh manager.
     * The mesh manager must be set via {@link #setMeshRegistry(ChunkMeshRegistry)}
     * before rendering.
     */
    public OpenGLChunkRenderer() {
        this.meshManager = null;
    }
    
    @Override
    public void setMeshRegistry(ChunkMeshRegistry registry) {
        if (registry instanceof OpenGLChunkMeshManager) {
            this.meshManager = (OpenGLChunkMeshManager) registry;
            // Get texture atlas and shader from mesh manager
            this.textureAtlas = meshManager.getTextureAtlas();
            this.shader = meshManager.getShader();
        } else {
            throw new IllegalArgumentException(
                "OpenGLChunkRenderer requires OpenGLChunkMeshManager, but got " + 
                (registry != null ? registry.getClass().getName() : "null"));
        }
    }
    
    @Override
    public boolean hasChunkMesh(LevelChunk chunk) {
        if (meshManager == null) {
            return false;
        }
        return meshManager.hasChunkMesh(chunk);
    }
    
    @Override
    public boolean renderChunk(LevelChunk chunk) {
        if (meshManager == null) {
            return false;
        }
        
        // Get mesh ID
        int meshId = meshManager.getMeshIdForChunk(chunk);
        if (meshId < 0) {
            return false;
        }
        
        // Get VAO for this mesh
        ChunkVAO vao = meshManager.getVAOByMeshId(meshId);
        if (vao == null) {
            return false;
        }
        
        // Ensure shader and texture atlas are available
        ensureResourcesInitialized();
        
        // Use the shader
        if (shader != null) {
            shader.use();
            shader.setTextureSampler(0); // Texture unit 0
            shader.applyDefaultLighting(); // Apply default gamma and emissive boost
        }
        
        // Enable texturing and bind texture atlas to unit 0
        glEnable(GL_TEXTURE_2D);
        glActiveTexture(GL_TEXTURE0);
        if (textureAtlas != null) {
            textureAtlas.bind();
        }
        
        // Render using VAO (single draw call!)
        vao.render();
        
        // Unbind shader and texture
        Shader.unbind();
        glBindTexture(GL_TEXTURE_2D, 0);
        
        return true;
    }
    
    /**
     * Ensure shader and texture atlas are initialized from mesh manager.
     */
    private void ensureResourcesInitialized() {
        if (meshManager != null) {
            if (textureAtlas == null) {
                textureAtlas = meshManager.getTextureAtlas();
            }
            if (shader == null) {
                shader = meshManager.getShader();
            }
        }
    }
    
    /**
     * Get the texture atlas used for rendering.
     * 
     * @return the texture atlas or null if not set
     */
    public TextureAtlas getTextureAtlas() {
        return textureAtlas;
    }
    
    /**
     * Get the shader used for rendering.
     * 
     * @return the shader or null if not set
     */
    public VoxelLitShader getShader() {
        return shader;
    }
}
