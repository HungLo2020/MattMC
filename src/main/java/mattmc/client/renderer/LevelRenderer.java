package mattmc.client.renderer;

import mattmc.client.renderer.texture.TextureAtlas;
import mattmc.client.renderer.shadow.ShadowRenderer;

import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;
import mattmc.client.renderer.chunk.ChunkRenderer;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;

import java.util.List;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders all loaded chunks in an infinite world.
 * Similar to RegionRenderer but works with the Level system.
 * 
 * Now handles async mesh uploads from background threads and texture atlas.
 * Implements frustum culling to skip rendering chunks outside the camera view.
 * Supports cascaded shadow maps for realistic sun shadows.
 */
public class LevelRenderer {
    private static final Logger logger = LoggerFactory.getLogger(LevelRenderer.class);

    private final ChunkRenderer chunkRenderer;
    private final Frustum frustum;
    private Level currentLevel;
    private boolean textureAtlasInitialized = false;
    
    // Shadow rendering
    private ShadowRenderer shadowRenderer;
    private boolean shadowsEnabled = true;
    
    // Statistics for debugging
    private int totalChunks = 0;
    private int renderedChunks = 0;
    private int culledChunks = 0;
    
    public LevelRenderer() {
        this.chunkRenderer = new ChunkRenderer();
        this.frustum = new Frustum();
        
        // Initialize shadow renderer - OpenGL 3.2+ supports texture arrays
        try {
            this.shadowRenderer = new ShadowRenderer(1536, 3);
            logger.info("Shadow renderer initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize shadow renderer: {}", e.getMessage());
            this.shadowRenderer = null;
            this.shadowsEnabled = false;
        }
    }
    
    /**
     * Initialize the renderer with a level.
     * This sets up the chunk unload listener and builds the texture atlas.
     */
    public void initWithLevel(Level level) {
        if (currentLevel != null) {
            currentLevel.setChunkUnloadListener(null);
        }
        this.currentLevel = level;
        level.setChunkUnloadListener(chunk -> chunkRenderer.removeChunkFromCache(chunk));
        
        // Build texture atlas once on first initialization
        if (!textureAtlasInitialized) {
            // logger.info("Initializing texture atlas for VBO rendering...");
            TextureAtlas atlas = new TextureAtlas();
            chunkRenderer.setTextureAtlas(atlas);
            level.getAsyncLoader().setTextureAtlas(atlas);
            textureAtlasInitialized = true;
            // logger.info("Texture atlas initialized with {} textures", atlas.getTextureCount());
        }
    }
    
    /**
     * Render all loaded chunks in the world.
     * Also processes pending mesh uploads from background threads and handles dirty chunks.
     * Uses frustum culling to skip chunks outside the camera view.
     * 
     * @param world The world to render
     * @param playerX Player X position
     * @param playerY Player Y position  
     * @param playerZ Player Z position
     * @param cameraFov Camera field of view in degrees
     * @param cameraAspect Camera aspect ratio
     * @param cameraNear Camera near plane distance
     * @param cameraFar Camera far plane distance
     */
    public void render(Level world, float playerX, float playerY, float playerZ, 
                      float cameraFov, float cameraAspect, float cameraNear, float cameraFar) {
        // Update frustum from current GL matrices (must be called after camera setup)
        frustum.update();
        
        // Reset statistics
        totalChunks = 0;
        renderedChunks = 0;
        culledChunks = 0;
        
        // Get sky brightness and sun direction from day cycle
        float skyBrightness = world.getDayCycle().getSkyBrightness();
        float[] sunDirection = world.getDayCycle().getSunDirection();
        
        // Render shadow maps if shadows are enabled
        if (shadowsEnabled) {
            renderShadowMaps(world, sunDirection, cameraFov, cameraAspect, cameraNear, cameraFar);
        }
        
        // Process completed mesh buffers from async loader first
        // This makes newly loaded chunk meshes available for rendering
        List<ChunkMeshBuffer> completedMeshBuffers = world.getAsyncLoader().collectCompletedMeshBuffers();
        for (ChunkMeshBuffer meshBuffer : completedMeshBuffers) {
            chunkRenderer.uploadMeshBuffer(meshBuffer);
        }
        
        glPushMatrix();
        
        // Render each loaded chunk with frustum culling
        for (LevelChunk chunk : world.getLoadedChunks()) {
            totalChunks++;
            
            // Frustum culling: skip chunks outside the camera view early
            // This is done before registration and dirty checks to avoid unnecessary operations for culled chunks
            if (!frustum.isChunkVisible(chunk.chunkX(), chunk.chunkZ(), 
                                       LevelChunk.WIDTH, LevelChunk.DEPTH, 
                                       LevelChunk.MIN_Y, LevelChunk.MAX_Y)) {
                culledChunks++;
                continue;
            }
            
            // Register chunk for mesh uploads (idempotent operation)
            chunkRenderer.registerChunk(chunk);
            
            // Check if chunk is dirty and needs mesh rebuild
            if (chunk.isDirty()) {
                // Don't invalidate the old VAO yet - keep it visible until new one is ready
                chunk.setDirty(false);
                world.getAsyncLoader().requestChunkMeshRebuild(chunk);
            }
            
            // Skip chunks without mesh data to avoid wasted GL calls
            // These are counted as culled since they can't be rendered
            if (!chunkRenderer.hasChunkMesh(chunk)) {
                culledChunks++;
                continue;
            }
            
            // Calculate chunk world position
            int chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
            int chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            
            // Only do GL matrix operations if we're actually going to render
            glPushMatrix();
            glTranslatef(chunkWorldX, 0, chunkWorldZ);
            if (chunkRenderer.renderChunk(chunk, playerX, playerY, playerZ, skyBrightness, sunDirection, 
                                         shadowsEnabled ? shadowRenderer : null)) {
                renderedChunks++;
            } else {
                // Chunk lost its VAO between the hasChunkMesh check and now
                // This is rare but can happen with concurrent modifications
                culledChunks++;
            }
            glPopMatrix();
        }
        
        glPopMatrix();
    }
    
    /**
     * Backwards compatible render method.
     * Uses default camera parameters.
     */
    public void render(Level world, float playerX, float playerY, float playerZ) {
        render(world, playerX, playerY, playerZ, 70f, 1.0f, 0.1f, 500f);
    }
    
    /**
     * Render shadow maps for all cascades.
     */
    private void renderShadowMaps(Level world, float[] sunDirection, 
                                   float cameraFov, float cameraAspect, 
                                   float cameraNear, float cameraFar) {
        // Get current view and projection matrices
        FloatBuffer viewMatrix = BufferUtils.createFloatBuffer(16);
        FloatBuffer projMatrix = BufferUtils.createFloatBuffer(16);
        
        glGetFloatv(GL_MODELVIEW_MATRIX, viewMatrix);
        glGetFloatv(GL_PROJECTION_MATRIX, projMatrix);
        
        float[] viewArray = new float[16];
        float[] projArray = new float[16];
        viewMatrix.get(viewArray);
        projMatrix.get(projArray);
        
        // Update shadow renderer camera parameters
        shadowRenderer.setCameraParameters(cameraNear, cameraFar, cameraFov, cameraAspect);
        
        // Render shadow maps
        shadowRenderer.renderShadowMaps(world, chunkRenderer, viewArray, projArray, sunDirection);
    }
    
    /**
     * Get the number of chunks that were rendered in the last frame.
     */
    public int getRenderedChunkCount() {
        return renderedChunks;
    }
    
    /**
     * Get the number of chunks that were culled (not rendered) in the last frame.
     */
    public int getCulledChunkCount() {
        return culledChunks;
    }
    
    /**
     * Get the total number of loaded chunks in the last frame.
     */
    public int getTotalChunkCount() {
        return totalChunks;
    }
}
