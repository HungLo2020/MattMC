package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;
import mattmc.client.renderer.level.ChunkMeshManager;
import mattmc.client.renderer.VoxelLitShader;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.ChunkUtils;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of chunk mesh management.
 * 
 * <p>This class handles OpenGL-specific operations for chunk meshes:
 * <ul>
 *   <li>VAO/VBO management via {@link ChunkVAO}</li>
 *   <li>Texture atlas management</li>
 *   <li>Shader initialization</li>
 *   <li>Backend material registration</li>
 * </ul>
 * 
 * <p>This class is created by the {@link OpenGLBackendFactory} and passed to
 * the backend-agnostic {@link mattmc.client.renderer.level.LevelRenderer}.
 * 
 * @see ChunkMeshManager
 */
public class OpenGLChunkMeshManager implements ChunkMeshManager {
    private static final Logger logger = LoggerFactory.getLogger(OpenGLChunkMeshManager.class);

    // Calculate expected chunk count based on maximum render distance
    private static final int EXPECTED_CHUNK_COUNT = (32 * 2 + 1) * (32 * 2 + 1);
    
    // VAO cache: maps chunks to their VAOs
    private final Map<LevelChunk, ChunkVAO> vaoCache = new HashMap<>(EXPECTED_CHUNK_COUNT);
    
    // Cache for chunk key to chunk mapping
    private final Map<Long, LevelChunk> chunkByKey = new HashMap<>(EXPECTED_CHUNK_COUNT);
    
    // Texture atlas for VBO rendering
    private TextureAtlas textureAtlas = null;
    
    // Shader for rendering
    private VoxelLitShader shader = null;
    
    // Initialization state
    private boolean textureAtlasInitialized = false;
    private boolean backendInitialized = false;
    
    @Override
    public void registerChunk(LevelChunk chunk) {
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        chunkByKey.putIfAbsent(key, chunk);
    }
    
    @Override
    public boolean hasChunkMesh(LevelChunk chunk) {
        return vaoCache.get(chunk) != null;
    }
    
    @Override
    public int getMeshIdForChunk(LevelChunk chunk) {
        if (!hasChunkMesh(chunk)) {
            return -1;
        }
        return System.identityHashCode(chunk);
    }
    
    @Override
    public int getDefaultMaterialId() {
        return 0;
    }
    
    @Override
    public boolean uploadMeshBuffer(ChunkMeshBuffer meshBuffer, RenderBackend backend) {
        long key = chunkKey(meshBuffer.getChunkX(), meshBuffer.getChunkZ());
        LevelChunk chunk = chunkByKey.get(key);
        
        if (chunk == null) {
            return false;
        }
        
        // Create new VAO from mesh buffer first
        ChunkVAO newVAO = null;
        if (!meshBuffer.isEmpty()) {
            newVAO = new ChunkVAO(meshBuffer);
        }
        
        // Replace and delete old VAO (after new one is ready)
        ChunkVAO oldVAO = vaoCache.remove(chunk);
        if (newVAO != null) {
            vaoCache.put(chunk, newVAO);
        }
        if (oldVAO != null) {
            oldVAO.delete();
        }
        
        // Register mesh with backend if upload was successful
        if (newVAO != null && backend instanceof OpenGLRenderBackend) {
            registerMeshWithBackend(chunk, (OpenGLRenderBackend) backend);
        }
        
        return true;
    }
    
    /**
     * Register a mesh with the OpenGL backend after it's been uploaded.
     */
    private void registerMeshWithBackend(LevelChunk chunk, OpenGLRenderBackend backend) {
        int meshId = getMeshIdForChunk(chunk);
        if (meshId < 0) {
            return;
        }
        
        ChunkVAO vao = getVAOByMeshId(meshId);
        if (vao == null) {
            return;
        }
        
        // Register mesh with backend
        backend.registerMesh(meshId, vao);
        
        // Register transform for this chunk
        int transformId = (chunk.chunkX() << 16) | (chunk.chunkZ() & 0xFFFF);
        float worldX = chunk.chunkX() * LevelChunk.WIDTH;
        float worldZ = chunk.chunkZ() * LevelChunk.DEPTH;
        backend.registerTransform(transformId, worldX, 0, worldZ);
    }
    
    @Override
    public void unregisterMesh(LevelChunk chunk, RenderBackend backend) {
        int meshId = getMeshIdForChunk(chunk);
        if (meshId >= 0 && backend instanceof OpenGLRenderBackend) {
            ((OpenGLRenderBackend) backend).unregisterMesh(meshId);
        }
    }
    
    @Override
    public void removeChunkFromCache(LevelChunk chunk) {
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        chunkByKey.remove(key);
        
        // Delete VAO if it exists
        ChunkVAO vao = vaoCache.remove(chunk);
        if (vao != null) {
            vao.delete();
        }
    }
    
    @Override
    public LevelChunk getRegisteredChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        return chunkByKey.get(key);
    }
    
    @Override
    public void initializeBackend(RenderBackend backend) {
        if (!(backend instanceof OpenGLRenderBackend)) {
            logger.warn("OpenGLChunkMeshManager requires OpenGLRenderBackend");
            return;
        }
        
        OpenGLRenderBackend glBackend = (OpenGLRenderBackend) backend;
        
        // Ensure shader is initialized
        ensureShaderInitialized();
        
        // Register default material (shader + texture atlas)
        int materialId = getDefaultMaterialId();
        glBackend.registerMaterial(materialId, shader, textureAtlas);
        
        backendInitialized = true;
    }
    
    @Override
    public void initializeTextureAtlas(Level level) {
        textureAtlas = new TextureAtlas();
        level.getAsyncLoader().setTextureAtlas(textureAtlas);
        textureAtlasInitialized = true;
    }
    
    @Override
    public boolean isTextureAtlasInitialized() {
        return textureAtlasInitialized;
    }
    
    @Override
    public boolean isBackendInitialized() {
        return backendInitialized;
    }
    
    @Override
    public void registerExistingChunks(Level level, RenderBackend backend) {
        if (!(backend instanceof OpenGLRenderBackend)) {
            return;
        }
        
        OpenGLRenderBackend glBackend = (OpenGLRenderBackend) backend;
        int registeredCount = 0;
        
        for (LevelChunk chunk : level.getLoadedChunks()) {
            if (!hasChunkMesh(chunk)) {
                continue;
            }
            
            int meshId = getMeshIdForChunk(chunk);
            if (meshId < 0) {
                continue;
            }
            
            ChunkVAO vao = getVAOByMeshId(meshId);
            if (vao == null) {
                continue;
            }
            
            // Register mesh with backend
            glBackend.registerMesh(meshId, vao);
            
            // Register transform
            int transformId = (chunk.chunkX() << 16) | (chunk.chunkZ() & 0xFFFF);
            float worldX = chunk.chunkX() * LevelChunk.WIDTH;
            float worldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            glBackend.registerTransform(transformId, worldX, 0, worldZ);
            
            registeredCount++;
        }
        
        if (registeredCount > 0) {
            logger.info("Registered {} existing chunks with backend during initialization", registeredCount);
        }
    }
    
    /**
     * Get the VAO for a chunk by mesh ID.
     */
    public ChunkVAO getVAOByMeshId(int meshId) {
        for (Map.Entry<LevelChunk, ChunkVAO> entry : vaoCache.entrySet()) {
            if (System.identityHashCode(entry.getKey()) == meshId) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * Ensure the shader is initialized.
     */
    private void ensureShaderInitialized() {
        if (shader == null) {
            Shader openglShader = new Shader(
                mattmc.client.renderer.ShaderLoader.loadShader("voxel_lit.vs"),
                mattmc.client.renderer.ShaderLoader.loadShader("voxel_lit.fs")
            );
            shader = new VoxelLitShader(openglShader);
            logger.info("Initialized VoxelLitShader for chunk rendering");
        }
    }
    
    /**
     * Helper to create chunk key from coordinates.
     */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ChunkUtils.chunkKey(chunkX, chunkZ);
    }
    
    /**
     * Get the texture atlas.
     */
    public TextureAtlas getTextureAtlas() {
        return textureAtlas;
    }
    
    /**
     * Get the shader.
     */
    public VoxelLitShader getShader() {
        return shader;
    }
    
    /**
     * Cleanup all OpenGL resources.
     */
    public void cleanup() {
        logger.info("Cleaning up OpenGLChunkMeshManager - deleting {} VAOs", vaoCache.size());
        for (ChunkVAO vao : vaoCache.values()) {
            if (vao != null) {
                vao.delete();
            }
        }
        vaoCache.clear();
        chunkByKey.clear();
    }
}
