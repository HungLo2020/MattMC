package mattmc.client.renderer;

import mattmc.client.renderer.chunk.ChunkRenderer;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import org.lwjgl.BufferUtils;

/**
 * Cascaded Shadow Maps (CSM) renderer for directional sunlight.
 * Uses multiple shadow cascades for better quality distribution across view distance.
 * Similar to Minecraft's Complementary shaders implementation.
 */
public class CascadedShadowRenderer {
    private static final Logger logger = LoggerFactory.getLogger(CascadedShadowRenderer.class);
    
    // Shadow map resolution per cascade
    private static final int SHADOW_MAP_SIZE = 2048;
    
    // Number of cascades (near, mid, far)
    private static final int NUM_CASCADES = 3;
    
    // Cascade distances from camera (in blocks)
    private static final float[] CASCADE_SPLITS = {
        16.0f,   // Near cascade: 0-16 blocks
        48.0f,   // Mid cascade: 16-48 blocks
        128.0f   // Far cascade: 48-128 blocks
    };
    
    // Shadow frustum sizes for each cascade (how much area each covers)
    private static final float[] CASCADE_FRUSTUM_SIZES = {
        32.0f,   // Near: tight, high quality
        96.0f,   // Mid: medium quality
        256.0f   // Far: wide coverage, lower quality
    };
    
    private final ShadowMapFramebuffer[] cascadeFramebuffers;
    private final ShadowDepthShader depthShader;
    
    // Shadow matrices for each cascade
    private final float[][] cascadeProjectionMatrices;
    private final float[][] cascadeViewMatrices;
    private final float[][] cascadeShadowMatrices;
    
    public CascadedShadowRenderer() {
        this.cascadeFramebuffers = new ShadowMapFramebuffer[NUM_CASCADES];
        this.cascadeProjectionMatrices = new float[NUM_CASCADES][16];
        this.cascadeViewMatrices = new float[NUM_CASCADES][16];
        this.cascadeShadowMatrices = new float[NUM_CASCADES][16];
        
        // Initialize framebuffers for each cascade
        for (int i = 0; i < NUM_CASCADES; i++) {
            cascadeFramebuffers[i] = new ShadowMapFramebuffer(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
        }
        
        this.depthShader = new ShadowDepthShader();
        logger.info("Cascaded shadow renderer initialized with {} cascades at {}x{} per cascade", 
                    NUM_CASCADES, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
    }
    
    /**
     * Render shadow maps for all cascades from the sun's perspective.
     * 
     * @param world The world to render
     * @param chunkRenderer The chunk renderer to use for rendering geometry
     * @param sunDirection The sun direction vector (normalized)
     * @param playerX Player X position (center of shadow frustum)
     * @param playerY Player Y position
     * @param playerZ Player Z position (center of shadow frustum)
     * @param viewMatrix The camera view matrix for cascade calculation
     * @param projectionMatrix The camera projection matrix for cascade calculation
     */
    public void renderShadowCascades(Level world, ChunkRenderer chunkRenderer,
                                     float[] sunDirection, float playerX, float playerY, float playerZ,
                                     float[] viewMatrix, float[] projectionMatrix) {
        // Save current viewport
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        
        // Render each cascade
        for (int cascade = 0; cascade < NUM_CASCADES; cascade++) {
            renderCascade(world, chunkRenderer, cascade, sunDirection, playerX, playerY, playerZ);
        }
        
        // Restore viewport
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
    
    /**
     * Render a single shadow cascade.
     */
    private void renderCascade(Level world, ChunkRenderer chunkRenderer, int cascadeIndex,
                               float[] sunDirection, float playerX, float playerY, float playerZ) {
        ShadowMapFramebuffer fbo = cascadeFramebuffers[cascadeIndex];
        float frustumSize = CASCADE_FRUSTUM_SIZES[cascadeIndex];
        
        // Bind cascade framebuffer
        fbo.bind();
        
        // Set viewport to shadow map size
        glViewport(0, 0, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
        
        // Clear shadow map
        fbo.clear();
        
        // Save current matrix mode
        int[] matrixMode = new int[1];
        glGetIntegerv(GL_MATRIX_MODE, matrixMode);
        
        // Set up projection matrix
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        
        // Orthographic projection for directional light
        float halfSize = frustumSize / 2.0f;
        glOrtho(-halfSize, halfSize, -halfSize, halfSize, -200.0f, 200.0f);
        
        // Store projection matrix for this cascade
        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_PROJECTION_MATRIX, projBuffer);
        projBuffer.get(cascadeProjectionMatrices[cascadeIndex]);
        projBuffer.rewind();
        
        // Set up view matrix looking from sun position toward scene
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Calculate sun position (far from player in opposite sun direction)
        float sunDist = 100.0f;
        float sunX = playerX - sunDirection[0] * sunDist;
        float sunY = playerY - sunDirection[1] * sunDist;
        float sunZ = playerZ - sunDirection[2] * sunDist;
        
        // Look from sun position toward player position
        gluLookAt(sunX, sunY, sunZ,
                  playerX, playerY, playerZ,
                  0.0f, 1.0f, 0.0f);
        
        // Store view matrix for this cascade
        FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_MODELVIEW_MATRIX, viewBuffer);
        viewBuffer.get(cascadeViewMatrices[cascadeIndex]);
        viewBuffer.rewind();
        
        // Calculate combined shadow matrix for this cascade
        calculateShadowMatrix(cascadeIndex);
        
        // Enable depth shader
        depthShader.use();
        
        // Disable color writes, only write depth
        glColorMask(false, false, false, false);
        
        // Enable face culling to reduce shadow acne
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT); // Cull front faces for shadow map
        
        // Render chunks (depth-only pass)
        renderChunksDepthOnly(world, chunkRenderer);
        
        // Restore state
        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);
        glColorMask(true, true, true, true);
        
        ShadowDepthShader.unbind();
        
        // Restore matrices
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        
        // Restore original matrix mode
        glMatrixMode(matrixMode[0]);
        
        // Unbind framebuffer
        fbo.unbind();
    }
    
    /**
     * Render chunks in depth-only mode for shadow map.
     */
    private void renderChunksDepthOnly(Level world, ChunkRenderer chunkRenderer) {
        glPushMatrix();
        
        // Render each loaded chunk
        for (LevelChunk chunk : world.getLoadedChunks()) {
            if (!chunkRenderer.hasChunkMesh(chunk)) {
                continue;
            }
            
            int chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
            int chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            
            glPushMatrix();
            glTranslatef(chunkWorldX, 0, chunkWorldZ);
            
            // Render chunk VAO directly (bypasses shader setup in ChunkRenderer)
            chunkRenderer.renderChunkDepthOnly(chunk);
            
            glPopMatrix();
        }
        
        glPopMatrix();
    }
    
    /**
     * Calculate the shadow matrix for a cascade that transforms from world space to shadow texture space.
     */
    private void calculateShadowMatrix(int cascadeIndex) {
        // Bias matrix to go from [-1,1] to [0,1] range
        float[] biasMatrix = {
            0.5f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.5f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.5f, 0.0f,
            0.5f, 0.5f, 0.5f, 1.0f
        };
        
        // Multiply: bias * projection * view
        float[] temp = new float[16];
        multiplyMatrices(biasMatrix, cascadeProjectionMatrices[cascadeIndex], temp);
        multiplyMatrices(temp, cascadeViewMatrices[cascadeIndex], cascadeShadowMatrices[cascadeIndex]);
    }
    
    /**
     * Multiply two 4x4 matrices: result = a * b
     */
    private void multiplyMatrices(float[] a, float[] b, float[] result) {
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float sum = 0;
                for (int i = 0; i < 4; i++) {
                    sum += a[row * 4 + i] * b[i * 4 + col];
                }
                result[row * 4 + col] = sum;
            }
        }
    }
    
    /**
     * Simple GLU lookAt implementation.
     */
    private void gluLookAt(float eyeX, float eyeY, float eyeZ,
                           float centerX, float centerY, float centerZ,
                           float upX, float upY, float upZ) {
        // Forward vector
        float fx = centerX - eyeX;
        float fy = centerY - eyeY;
        float fz = centerZ - eyeZ;
        float flen = (float) Math.sqrt(fx*fx + fy*fy + fz*fz);
        fx /= flen; fy /= flen; fz /= flen;
        
        // Side vector (cross product of forward and up)
        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float slen = (float) Math.sqrt(sx*sx + sy*sy + sz*sz);
        sx /= slen; sy /= slen; sz /= slen;
        
        // Recompute up vector
        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;
        
        float[] m = {
            sx, ux, -fx, 0,
            sy, uy, -fy, 0,
            sz, uz, -fz, 0,
            0,  0,  0,   1
        };
        
        glMultMatrixf(m);
        glTranslatef(-eyeX, -eyeY, -eyeZ);
    }
    
    /**
     * Get the shadow matrix for a specific cascade for shader uniforms.
     */
    public float[] getCascadeShadowMatrix(int cascadeIndex) {
        if (cascadeIndex < 0 || cascadeIndex >= NUM_CASCADES) {
            return cascadeShadowMatrices[0];
        }
        return cascadeShadowMatrices[cascadeIndex];
    }
    
    /**
     * Get all shadow matrices for shader uniforms.
     */
    public float[][] getAllCascadeShadowMatrices() {
        return cascadeShadowMatrices;
    }
    
    /**
     * Get the cascade split distances.
     */
    public float[] getCascadeSplits() {
        return CASCADE_SPLITS;
    }
    
    /**
     * Get the number of cascades.
     */
    public int getNumCascades() {
        return NUM_CASCADES;
    }
    
    /**
     * Bind the shadow map texture for a specific cascade for sampling in shaders.
     */
    public void bindCascadeShadowMap(int cascadeIndex, int textureUnit) {
        if (cascadeIndex < 0 || cascadeIndex >= NUM_CASCADES) {
            return;
        }
        cascadeFramebuffers[cascadeIndex].bindDepthTexture(textureUnit);
    }
    
    /**
     * Get the shadow map framebuffer for a specific cascade.
     */
    public ShadowMapFramebuffer getCascadeFramebuffer(int cascadeIndex) {
        if (cascadeIndex < 0 || cascadeIndex >= NUM_CASCADES) {
            return cascadeFramebuffers[0];
        }
        return cascadeFramebuffers[cascadeIndex];
    }
    
    /**
     * Clean up OpenGL resources.
     */
    public void cleanup() {
        for (ShadowMapFramebuffer fbo : cascadeFramebuffers) {
            fbo.cleanup();
        }
    }
}
