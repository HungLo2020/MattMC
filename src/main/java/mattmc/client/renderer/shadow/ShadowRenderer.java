package mattmc.client.renderer.shadow;

import mattmc.client.renderer.chunk.ChunkRenderer;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of shadow depth maps for cascaded shadow mapping.
 * Renders opaque geometry from the sun's perspective into shadow map cascades.
 */
public class ShadowRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ShadowRenderer.class);
    
    private final CascadedShadowMap csm;
    private final ShadowDepthShader depthShader;
    
    /**
     * Create a shadow renderer with the specified resolution and cascade count.
     * 
     * @param resolution Shadow map resolution (1024, 1536, or 2048)
     * @param numCascades Number of cascades (typically 3 or 4)
     */
    public ShadowRenderer(int resolution, int numCascades) {
        this.csm = new CascadedShadowMap(resolution, numCascades);
        this.depthShader = new ShadowDepthShader();
        logger.info("Created ShadowRenderer with {}x{} resolution and {} cascades", 
                    resolution, resolution, numCascades);
    }
    
    /**
     * Update camera parameters for cascade computation.
     */
    public void setCameraParameters(float near, float far, float fov, float aspect) {
        csm.setCameraParameters(near, far, fov, aspect);
    }
    
    /**
     * Render shadow maps for all cascades.
     * 
     * @param world The world to render
     * @param chunkRenderer The chunk renderer to use for rendering geometry
     * @param cameraViewMatrix Current camera view matrix (4x4 column-major)
     * @param cameraProjMatrix Current camera projection matrix (4x4 column-major)
     * @param sunDirection Normalized sun direction vector [x, y, z]
     */
    public void renderShadowMaps(Level world, ChunkRenderer chunkRenderer, 
                                  float[] cameraViewMatrix, float[] cameraProjMatrix, 
                                  float[] sunDirection) {
        // Update cascade splits and matrices
        csm.updateCascades(cameraViewMatrix, cameraProjMatrix, sunDirection);
        
        // Save current GL state
        int[] oldViewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, oldViewport);
        
        // Disable features not needed for depth rendering
        glDisable(GL_BLEND);
        glColorMask(false, false, false, false); // Don't write to color buffer
        
        // Use depth shader
        depthShader.use();
        
        // Render each cascade
        for (int i = 0; i < csm.getNumCascades(); i++) {
            renderCascade(world, chunkRenderer, i);
        }
        
        // Unbind shader
        ShadowDepthShader.unbind();
        
        // Restore GL state
        glColorMask(true, true, true, true);
        glEnable(GL_BLEND);
        glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
    }
    
    /**
     * Render a single cascade's shadow map.
     */
    private void renderCascade(Level world, ChunkRenderer chunkRenderer, int cascadeIndex) {
        ShadowCascade cascade = csm.getCascades()[cascadeIndex];
        
        // Bind shadow framebuffer for this cascade
        csm.getShadowFBO().bindForCascade(cascadeIndex);
        
        // Set up light matrices
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadMatrixf(cascade.getLightProjectionMatrix());
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadMatrixf(cascade.getLightViewMatrix());
        
        // Render all visible chunks
        // Note: We render all chunks since they might cast shadows into view
        for (LevelChunk chunk : world.getLoadedChunks()) {
            if (!chunkRenderer.hasChunkMesh(chunk)) {
                continue;
            }
            
            // Calculate chunk world position
            int chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
            int chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            
            // Render chunk depth
            glPushMatrix();
            glTranslatef(chunkWorldX, 0, chunkWorldZ);
            renderChunkDepth(chunkRenderer, chunk);
            glPopMatrix();
        }
        
        // Restore matrices
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        
        // Unbind framebuffer
        csm.getShadowFBO().unbind();
    }
    
    /**
     * Render a chunk's depth to the shadow map.
     * This uses the same VAO as regular rendering but only writes depth.
     */
    private void renderChunkDepth(ChunkRenderer chunkRenderer, LevelChunk chunk) {
        // Render only depth using the chunk's VAO
        // The depth shader is already active and depth-only mode is set
        chunkRenderer.renderChunkDepth(chunk);
    }
    
    /**
     * Get the cascaded shadow map system.
     */
    public CascadedShadowMap getCascadedShadowMap() {
        return csm;
    }
    
    /**
     * Clean up resources.
     */
    public void cleanup() {
        csm.close();
        depthShader.close();
    }
}
