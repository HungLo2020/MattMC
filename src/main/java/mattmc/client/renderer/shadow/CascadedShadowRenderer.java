package mattmc.client.renderer.shadow;

import mattmc.world.level.DayCycle;
import mattmc.world.level.Level;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Cascaded Shadow Mapping (CSM) system for directional sunlight shadows.
 * 
 * Renders the scene from the sun's perspective into 3 shadow maps (cascades)
 * at different distance ranges to optimize shadow quality distribution.
 * 
 * Based on the approach used by Minecraft shader mods like Complementary.
 */
public class CascadedShadowRenderer {
    private static final Logger logger = LoggerFactory.getLogger(CascadedShadowRenderer.class);
    
    // Shadow map configuration
    private static final int NUM_CASCADES = 3;
    private static final int SHADOW_MAP_RESOLUTION = 2048;
    
    // Cascade distance ranges (in blocks)
    private static final float[] CASCADE_SPLITS = {
        16.0f,   // Near cascade: 0-16 blocks
        48.0f,   // Mid cascade: 16-48 blocks  
        128.0f   // Far cascade: 48-128 blocks
    };
    
    // Orthographic frustum sizes for each cascade (in blocks)
    private static final float[] CASCADE_SIZES = {
        32.0f,   // Near: 32x32 blocks
        96.0f,   // Mid: 96x96 blocks
        256.0f   // Far: 256x256 blocks
    };
    
    private final ShadowMap[] shadowMaps = new ShadowMap[NUM_CASCADES];
    private boolean enabled = false;
    
    // Sun direction vector (normalized)
    private final float[] sunDirection = new float[3];
    
    // Matrices for shadow rendering
    private final FloatBuffer viewMatrix = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionMatrix = BufferUtils.createFloatBuffer(16);
    
    public CascadedShadowRenderer() {
        initialize();
    }
    
    private void initialize() {
        logger.info("Initializing Cascaded Shadow Mapping system...");
        
        // Create shadow maps for each cascade
        for (int i = 0; i < NUM_CASCADES; i++) {
            shadowMaps[i] = new ShadowMap(SHADOW_MAP_RESOLUTION);
        }
        
        logger.info("CSM initialized with {} cascades at {}x{} resolution", 
                   NUM_CASCADES, SHADOW_MAP_RESOLUTION, SHADOW_MAP_RESOLUTION);
    }
    
    /**
     * Enable or disable shadow rendering.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Update sun direction based on day/night cycle.
     */
    public void updateSunDirection(DayCycle dayCycle) {
        // Get celestial angle (0-1 over full day)
        float celestialAngle = dayCycle.getCelestialAngle();
        
        // Convert to radians (0 = noon, 0.5 = midnight)
        float angle = celestialAngle * 2.0f * (float)Math.PI;
        
        // Sun rotates in a circle around the world
        // At noon (angle=0): sun is directly overhead (0, -1, 0)
        // At sunset/sunrise: sun is on horizon
        sunDirection[0] = 0.0f;  // No east-west movement
        sunDirection[1] = -(float)Math.cos(angle);  // Vertical component
        sunDirection[2] = (float)Math.sin(angle);   // North-south component
        
        // Normalize
        float length = (float)Math.sqrt(
            sunDirection[0] * sunDirection[0] +
            sunDirection[1] * sunDirection[1] +
            sunDirection[2] * sunDirection[2]
        );
        sunDirection[0] /= length;
        sunDirection[1] /= length;
        sunDirection[2] /= length;
    }
    
    /**
     * Render shadow maps for all cascades.
     * This should be called before the main scene rendering.
     * 
     * @param level The world to render
     * @param playerX Player X position (center of shadow frustum)
     * @param playerY Player Y position
     * @param playerZ Player Z position
     * @param renderCallback Callback to render chunks in depth-only mode
     */
    public void renderShadowMaps(Level level, float playerX, float playerY, float playerZ,
                                 ShadowRenderCallback renderCallback) {
        if (!enabled) {
            return;
        }
        
        // Only render shadows during daytime
        DayCycle dayCycle = level.getDayCycle();
        if (!shouldRenderShadows(dayCycle)) {
            return;
        }
        
        // Update sun direction
        updateSunDirection(dayCycle);
        
        // Save current GL state
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        
        // Render each cascade
        for (int cascade = 0; cascade < NUM_CASCADES; cascade++) {
            renderCascade(cascade, playerX, playerY, playerZ, renderCallback);
        }
        
        // Restore viewport
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
    
    /**
     * Render a single shadow cascade.
     */
    private void renderCascade(int cascade, float playerX, float playerY, float playerZ,
                               ShadowRenderCallback renderCallback) {
        ShadowMap shadowMap = shadowMaps[cascade];
        
        // Bind shadow framebuffer
        shadowMap.bind();
        
        // Enable depth testing
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(true);
        
        // Disable color writes (depth-only pass)
        glColorMask(false, false, false, false);
        
        // Enable back-face culling to reduce shadow acne
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);  // Cull front faces for shadow rendering
        
        // Setup shadow camera matrices
        setupShadowMatrices(cascade, playerX, playerY, playerZ);
        
        // Apply matrices
        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(projectionMatrix);
        
        glMatrixMode(GL_MODELVIEW);
        glLoadMatrixf(viewMatrix);
        
        // Render chunks in depth-only mode
        if (renderCallback != null) {
            renderCallback.renderDepthOnly();
        }
        
        // Restore GL state
        glColorMask(true, true, true, true);
        glCullFace(GL_BACK);
        
        // Unbind shadow framebuffer
        shadowMap.unbind();
        
        // Update shadow matrix for shader usage
        float[] view = new float[16];
        float[] proj = new float[16];
        viewMatrix.get(view);
        projectionMatrix.get(proj);
        shadowMap.updateShadowMatrix(view, proj);
        
        // Reset buffer positions
        viewMatrix.rewind();
        projectionMatrix.rewind();
    }
    
    /**
     * Setup view and projection matrices for shadow rendering.
     */
    private void setupShadowMatrices(int cascade, float playerX, float playerY, float playerZ) {
        float frustumSize = CASCADE_SIZES[cascade];
        
        // Camera position: offset from player in the direction opposite to sun
        // This ensures the shadow frustum encompasses the player's view
        float distance = frustumSize * 0.5f;
        float camX = playerX - sunDirection[0] * distance;
        float camY = playerY - sunDirection[1] * distance;
        float camZ = playerZ - sunDirection[2] * distance;
        
        // Look-at target (player position)
        float targetX = playerX;
        float targetY = playerY;
        float targetZ = playerZ;
        
        // Up vector (always world up for directional light)
        float upX = 0.0f;
        float upY = 1.0f;
        float upZ = 0.0f;
        
        // Build view matrix (look-at)
        buildLookAtMatrix(camX, camY, camZ, targetX, targetY, targetZ, upX, upY, upZ, viewMatrix);
        
        // Build orthographic projection matrix
        float halfSize = frustumSize / 2.0f;
        buildOrthoMatrix(-halfSize, halfSize, -halfSize, halfSize, 0.1f, frustumSize * 2.0f, projectionMatrix);
    }
    
    /**
     * Build a look-at view matrix.
     */
    private void buildLookAtMatrix(float eyeX, float eyeY, float eyeZ,
                                   float targetX, float targetY, float targetZ,
                                   float upX, float upY, float upZ,
                                   FloatBuffer result) {
        // Forward vector (from eye to target)
        float fx = targetX - eyeX;
        float fy = targetY - eyeY;
        float fz = targetZ - eyeZ;
        float flen = (float)Math.sqrt(fx*fx + fy*fy + fz*fz);
        fx /= flen; fy /= flen; fz /= flen;
        
        // Side vector (cross product of forward and up)
        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float slen = (float)Math.sqrt(sx*sx + sy*sy + sz*sz);
        sx /= slen; sy /= slen; sz /= slen;
        
        // Recompute up vector
        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;
        
        result.clear();
        result.put(new float[] {
            sx, ux, -fx, 0,
            sy, uy, -fy, 0,
            sz, uz, -fz, 0,
            -(sx*eyeX + sy*eyeY + sz*eyeZ),
            -(ux*eyeX + uy*eyeY + uz*eyeZ),
            (fx*eyeX + fy*eyeY + fz*eyeZ),
            1
        });
        result.flip();
    }
    
    /**
     * Build an orthographic projection matrix.
     */
    private void buildOrthoMatrix(float left, float right, float bottom, float top,
                                  float near, float far, FloatBuffer result) {
        result.clear();
        result.put(new float[] {
            2.0f/(right-left), 0, 0, 0,
            0, 2.0f/(top-bottom), 0, 0,
            0, 0, -2.0f/(far-near), 0,
            -(right+left)/(right-left),
            -(top+bottom)/(top-bottom),
            -(far+near)/(far-near),
            1
        });
        result.flip();
    }
    
    /**
     * Check if shadows should be rendered based on time of day.
     */
    private boolean shouldRenderShadows(DayCycle dayCycle) {
        long timeOfDay = dayCycle.getTimeOfDay();
        // Only render shadows during daytime (roughly 0-12000 ticks)
        return timeOfDay < DayCycle.SUNSET;
    }
    
    /**
     * Bind shadow textures for use in main rendering pass.
     */
    public void bindShadowTextures() {
        for (int i = 0; i < NUM_CASCADES; i++) {
            shadowMaps[i].bindTexture(GL_TEXTURE3 + i);  // Use texture units 3, 4, 5
        }
    }
    
    /**
     * Get shadow map for a specific cascade.
     */
    public ShadowMap getShadowMap(int cascade) {
        if (cascade >= 0 && cascade < NUM_CASCADES) {
            return shadowMaps[cascade];
        }
        return null;
    }
    
    /**
     * Get sun direction vector.
     */
    public float[] getSunDirection() {
        return sunDirection;
    }
    
    /**
     * Get cascade split distances.
     */
    public float[] getCascadeSplits() {
        return CASCADE_SPLITS;
    }
    
    /**
     * Clean up OpenGL resources.
     */
    public void cleanup() {
        for (ShadowMap shadowMap : shadowMaps) {
            if (shadowMap != null) {
                shadowMap.cleanup();
            }
        }
    }
    
    /**
     * Callback interface for rendering chunks in depth-only mode.
     */
    public interface ShadowRenderCallback {
        void renderDepthOnly();
    }
}
