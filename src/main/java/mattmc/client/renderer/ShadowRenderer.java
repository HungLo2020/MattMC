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
 * Manages shadow map rendering for directional sunlight.
 * Renders the scene from the sun's perspective to create a depth map.
 */
public class ShadowRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ShadowRenderer.class);
    
    // Shadow map resolution (higher = better quality, lower performance)
    private static final int SHADOW_MAP_SIZE = 2048;
    
    // Shadow frustum size (how much area the shadow map covers)
    private static final float SHADOW_FRUSTUM_SIZE = 128.0f;
    
    private final ShadowMapFramebuffer shadowMapFbo;
    private final ShadowDepthShader depthShader;
    private final float[] shadowProjectionMatrix = new float[16];
    private final float[] shadowViewMatrix = new float[16];
    private final float[] shadowMatrix = new float[16];
    
    public ShadowRenderer() {
        this.shadowMapFbo = new ShadowMapFramebuffer(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
        this.depthShader = new ShadowDepthShader();
        logger.info("Shadow renderer initialized with {}x{} shadow map", SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
    }
    
    /**
     * Render the shadow map from the sun's perspective.
     * 
     * @param world The world to render
     * @param chunkRenderer The chunk renderer to use for rendering geometry
     * @param sunDirection The sun direction vector
     * @param playerX Player X position (center of shadow frustum)
     * @param playerY Player Y position
     * @param playerZ Player Z position (center of shadow frustum)
     */
    public void renderShadowMap(Level world, mattmc.client.renderer.chunk.ChunkRenderer chunkRenderer,
                                float[] sunDirection, float playerX, float playerY, float playerZ) {
        // Bind shadow map framebuffer
        shadowMapFbo.bind();
        shadowMapFbo.clear();
        
        // Save current viewport and matrices
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        
        glPushMatrix();
        
        // Set up orthographic projection for shadow map
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        
        // Orthographic projection centered on player
        float halfSize = SHADOW_FRUSTUM_SIZE / 2.0f;
        glOrtho(-halfSize, halfSize, -halfSize, halfSize, -200.0f, 200.0f);
        
        // Store projection matrix
        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_PROJECTION_MATRIX, projBuffer);
        projBuffer.get(shadowProjectionMatrix);
        projBuffer.rewind();
        
        // Set up view matrix looking from sun position toward scene
        glMatrixMode(GL_MODELVIEW);
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
        
        // Store view matrix
        FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_MODELVIEW_MATRIX, viewBuffer);
        viewBuffer.get(shadowViewMatrix);
        viewBuffer.rewind();
        
        // Calculate combined shadow matrix (bias * projection * view)
        calculateShadowMatrix();
        
        // Enable depth shader
        depthShader.use();
        
        // Disable color writes, only write depth
        glColorMask(false, false, false, false);
        
        // Enable face culling to reduce shadow acne
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT); // Cull front faces for shadow map
        
        // Render chunks (simplified - just geometry)
        renderChunksDepthOnly(world, chunkRenderer);
        
        // Restore state
        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);
        glColorMask(true, true, true, true);
        
        ShadowDepthShader.unbind();
        
        // Restore matrices
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        
        // Restore viewport
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        
        // Unbind shadow map framebuffer
        shadowMapFbo.unbind();
    }
    
    /**
     * Render chunks in depth-only mode for shadow map.
     */
    private void renderChunksDepthOnly(Level world, mattmc.client.renderer.chunk.ChunkRenderer chunkRenderer) {
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
     * Calculate the shadow matrix that transforms from world space to shadow texture space.
     * This includes a bias matrix to transform from [-1,1] to [0,1] range.
     */
    private void calculateShadowMatrix() {
        // Bias matrix to go from [-1,1] to [0,1] range
        float[] biasMatrix = {
            0.5f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.5f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.5f, 0.0f,
            0.5f, 0.5f, 0.5f, 1.0f
        };
        
        // Multiply: bias * projection * view
        float[] temp = new float[16];
        multiplyMatrices(biasMatrix, shadowProjectionMatrix, temp);
        multiplyMatrices(temp, shadowViewMatrix, shadowMatrix);
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
     * Get the shadow matrix for shader uniforms.
     */
    public float[] getShadowMatrix() {
        return shadowMatrix;
    }
    
    /**
     * Bind the shadow map texture for sampling in shaders.
     */
    public void bindShadowMap(int textureUnit) {
        shadowMapFbo.bindDepthTexture(textureUnit);
    }
    
    /**
     * Get the shadow map framebuffer.
     */
    public ShadowMapFramebuffer getShadowMapFbo() {
        return shadowMapFbo;
    }
    
    /**
     * Clean up OpenGL resources.
     */
    public void cleanup() {
        shadowMapFbo.cleanup();
    }
}
