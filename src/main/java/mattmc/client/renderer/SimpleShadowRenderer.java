package mattmc.client.renderer;

import mattmc.client.renderer.chunk.ChunkRenderer;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Simple shadow renderer that creates shadows based on sun position.
 * Renders opaque blocks from the sun's perspective to create a shadow map.
 */
public class SimpleShadowRenderer {
    private static final Logger logger = LoggerFactory.getLogger(SimpleShadowRenderer.class);
    private static final int SHADOW_MAP_SIZE = 1024;
    private static final float SHADOW_DISTANCE = 64.0f; // How far shadows extend
    
    private final SimpleShadowMap shadowMap;
    private final float[] shadowMatrix = new float[16];
    private boolean firstRender = true;
    
    public SimpleShadowRenderer() {
        this.shadowMap = new SimpleShadowMap(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
    }
    
    /**
     * Render the shadow map from the sun's perspective.
     */
    public void renderShadowMap(Level world, ChunkRenderer chunkRenderer,
                               float[] sunDirection, float playerX, float playerY, float playerZ) {
        // Only render shadows when sun is above horizon
        if (sunDirection[1] <= 0) {
            return; // Sun is below horizon, no shadows
        }
        
        if (firstRender) {
            logger.info("Shadow rendering started - sun direction: [{}, {}, {}]", 
                sunDirection[0], sunDirection[1], sunDirection[2]);
            firstRender = false;
        }
        
        // Save viewport
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        
        // Bind shadow map
        shadowMap.bind();
        glViewport(0, 0, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE);
        shadowMap.clear();
        
        // Save matrix mode
        int[] matrixMode = new int[1];
        glGetIntegerv(GL_MATRIX_MODE, matrixMode);
        
        // Set up orthographic projection centered on player
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        
        float halfSize = SHADOW_DISTANCE / 2.0f;
        glOrtho(-halfSize, halfSize, -halfSize, halfSize, -100.0f, 100.0f);
        
        // Get projection matrix
        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_PROJECTION_MATRIX, projBuffer);
        float[] projectionMatrix = new float[16];
        projBuffer.get(projectionMatrix);
        projBuffer.rewind();
        
        // Set up view matrix looking from sun toward player
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Position sun above and behind player in opposite direction of sun vector
        float sunDist = 50.0f;
        float sunX = playerX - sunDirection[0] * sunDist;
        float sunY = playerY - sunDirection[1] * sunDist;
        float sunZ = playerZ - sunDirection[2] * sunDist;
        
        // Look from sun toward player
        gluLookAt(sunX, sunY, sunZ, playerX, playerY, playerZ, 0.0f, 1.0f, 0.0f);
        
        // Get view matrix
        FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_MODELVIEW_MATRIX, viewBuffer);
        float[] viewMatrix = new float[16];
        viewBuffer.get(viewMatrix);
        viewBuffer.rewind();
        
        // Calculate shadow matrix
        calculateShadowMatrix(projectionMatrix, viewMatrix);
        
        // Render opaque blocks only (no color output, depth only)
        glColorMask(false, false, false, false);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT); // Front face culling reduces shadow acne
        
        // Render chunks
        for (LevelChunk chunk : world.getLoadedChunks()) {
            if (!chunkRenderer.hasChunkMesh(chunk)) {
                continue;
            }
            
            int chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
            int chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            
            glPushMatrix();
            glTranslatef(chunkWorldX, 0, chunkWorldZ);
            chunkRenderer.renderChunkGeometryOnly(chunk);
            glPopMatrix();
        }
        
        // Restore state
        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);
        glColorMask(true, true, true, true);
        
        // Restore matrices
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(matrixMode[0]);
        
        // Unbind shadow map
        shadowMap.unbind();
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
    
    private void gluLookAt(float eyeX, float eyeY, float eyeZ,
                           float centerX, float centerY, float centerZ,
                           float upX, float upY, float upZ) {
        // Forward vector
        float fx = centerX - eyeX;
        float fy = centerY - eyeY;
        float fz = centerZ - eyeZ;
        float flen = (float) Math.sqrt(fx*fx + fy*fy + fz*fz);
        fx /= flen; fy /= flen; fz /= flen;
        
        // Side vector
        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float slen = (float) Math.sqrt(sx*sx + sy*sy + sz*sz);
        sx /= slen; sy /= slen; sz /= slen;
        
        // Up vector
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
    
    private void calculateShadowMatrix(float[] projection, float[] view) {
        // Bias matrix transforms from [-1,1] to [0,1]
        float[] bias = {
            0.5f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.5f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.5f, 0.0f,
            0.5f, 0.5f, 0.5f, 1.0f
        };
        
        // shadow = bias * projection * view
        float[] temp = new float[16];
        multiplyMatrices(bias, projection, temp);
        multiplyMatrices(temp, view, shadowMatrix);
    }
    
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
    
    public float[] getShadowMatrix() {
        return shadowMatrix;
    }
    
    public void bindShadowMap(int textureUnit) {
        shadowMap.bindDepthTexture(textureUnit);
    }
    
    public void cleanup() {
        shadowMap.cleanup();
    }
}
