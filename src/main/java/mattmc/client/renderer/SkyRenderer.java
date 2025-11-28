package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.world.level.DayCycle;

/**
 * Renders the sky, sun, moon, and stars similar to Minecraft's LevelRenderer.
 * 
 * The sky rendering includes:
 * 1. Sky dome with color based on time of day
 * 2. Sun texture rotating across the sky
 * 3. Moon texture with phases rotating opposite the sun
 * 4. Stars visible at night
 * 5. Sunrise/sunset color gradient on the horizon
 */
public class SkyRenderer {
    // Texture paths for celestial bodies
    private static final String SUN_TEXTURE_PATH = "/assets/textures/environment/sun.png";
    private static final String MOON_TEXTURE_PATH = "/assets/textures/environment/moon_phases.png";
    
    // Sun and moon sizes (in world units at distance 100)
    private static final float SUN_SIZE = 30.0f;
    private static final float MOON_SIZE = 20.0f;
    private static final float CELESTIAL_DISTANCE = 100.0f;
    
    // Star rendering constants (matching Minecraft)
    private static final int STAR_COUNT = 1500;
    
    // Texture IDs (loaded lazily)
    private int sunTextureId = -1;
    private int moonTextureId = -1;
    
    // Star vertex data (generated once)
    private float[] starVertices = null;
    
    private final RenderBackend backend;
    
    public SkyRenderer(RenderBackend backend) {
        this.backend = backend;
    }
    
    /**
     * Initialize textures. Should be called from the render thread.
     */
    private void ensureTexturesLoaded() {
        if (sunTextureId == -1) {
            sunTextureId = backend.loadTexture(SUN_TEXTURE_PATH);
        }
        if (moonTextureId == -1) {
            moonTextureId = backend.loadTexture(MOON_TEXTURE_PATH);
        }
    }
    
    /**
     * Generate star vertex data (positions only).
     * Stars are rendered as small quads at random positions on a celestial sphere.
     * 
     * This matches Minecraft's LevelRenderer.drawStars() algorithm.
     */
    private void ensureStarsGenerated() {
        if (starVertices != null) {
            return;
        }
        
        // Each star is a quad (4 vertices), each vertex has 3 floats (x, y, z)
        // Total: STAR_COUNT * 4 * 3 floats
        starVertices = new float[STAR_COUNT * 4 * 3];
        
        // Use fixed seed for consistent star pattern (matching Minecraft)
        java.util.Random random = new java.util.Random(10842L);
        
        int vertexIndex = 0;
        
        for (int i = 0; i < STAR_COUNT; i++) {
            // Random position on unit sphere
            double d0 = random.nextFloat() * 2.0f - 1.0f;
            double d1 = random.nextFloat() * 2.0f - 1.0f;
            double d2 = random.nextFloat() * 2.0f - 1.0f;
            double size = 0.15f + random.nextFloat() * 0.1f;
            double distSq = d0 * d0 + d1 * d1 + d2 * d2;
            
            // Skip points inside or too close to origin
            if (distSq < 1.0 && distSq > 0.01) {
                // Normalize to sphere surface
                double invDist = 1.0 / Math.sqrt(distSq);
                d0 *= invDist;
                d1 *= invDist;
                d2 *= invDist;
                
                // Position at distance 100
                double x = d0 * 100.0;
                double y = d1 * 100.0;
                double z = d2 * 100.0;
                
                // Calculate orientation vectors for the quad
                double angle1 = Math.atan2(d0, d2);
                double sin1 = Math.sin(angle1);
                double cos1 = Math.cos(angle1);
                double angle2 = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
                double sin2 = Math.sin(angle2);
                double cos2 = Math.cos(angle2);
                double rotAngle = random.nextDouble() * Math.PI * 2.0;
                double sinRot = Math.sin(rotAngle);
                double cosRot = Math.cos(rotAngle);
                
                // Generate 4 vertices for the star quad
                for (int j = 0; j < 4; j++) {
                    double dx = ((j & 2) - 1) * size;
                    double dy = ((j + 1 & 2) - 1) * size;
                    
                    // Rotate the quad corner
                    double rx = dx * cosRot - dy * sinRot;
                    double ry = dy * cosRot + dx * sinRot;
                    
                    // Transform to sphere surface
                    double ry2 = rx * sin2 + 0.0 * cos2;
                    double rx2 = 0.0 * sin2 - rx * cos2;
                    double vx = rx2 * sin1 - ry * cos1;
                    double vy = ry2;
                    double vz = ry * sin1 + rx2 * cos1;
                    
                    starVertices[vertexIndex++] = (float) (x + vx);
                    starVertices[vertexIndex++] = (float) (y + vy);
                    starVertices[vertexIndex++] = (float) (z + vz);
                }
            } else {
                // If point was rejected, add a degenerate quad (will be invisible)
                for (int j = 0; j < 12; j++) {
                    starVertices[vertexIndex++] = 0;
                }
            }
        }
    }
    
    /**
     * Render the complete sky including sun, moon, and stars.
     * 
     * @param dayCycle The day/night cycle manager
     * @param playerPitch Player's pitch for proper sky orientation
     * @param playerYaw Player's yaw for proper sky orientation
     */
    public void render(DayCycle dayCycle, float playerPitch, float playerYaw) {
        ensureTexturesLoaded();
        ensureStarsGenerated();
        
        // Get time-based values
        float timeOfDay = dayCycle.getTimeOfDayFloat();
        float starBrightness = dayCycle.getStarBrightness();
        int moonPhase = dayCycle.getMoonPhase();
        
        // Calculate rain level dimming (we don't have rain yet, so use 1.0)
        float clearness = 1.0f;
        
        // Disable depth writing for sky (it's infinitely far away)
        backend.setDepthMask(false);
        backend.enableBlend();
        backend.setBlendFunc(RenderBackend.BLEND_SRC_ALPHA, RenderBackend.BLEND_ONE);
        
        // Save modelview matrix
        backend.pushMatrix();
        
        // Rotate by -90 degrees on Y axis (for proper sun path east to west)
        backend.rotateMatrix(-90.0f, 0.0f, 1.0f, 0.0f);
        
        // Rotate by time of day (full 360 degrees over 24000 ticks)
        backend.rotateMatrix(timeOfDay * 360.0f, 1.0f, 0.0f, 0.0f);
        
        // Render sun
        backend.setColor(0xFFFFFF, clearness);
        renderSun();
        
        // Render moon with phases
        renderMoon(moonPhase);
        
        // Render stars (only visible at night)
        if (starBrightness > 0.0f) {
            renderStars(starBrightness * clearness);
        }
        
        // Restore modelview matrix
        backend.popMatrix();
        
        // Restore render state
        backend.setBlendFunc(RenderBackend.BLEND_SRC_ALPHA, RenderBackend.BLEND_ONE_MINUS_SRC_ALPHA);
        backend.disableBlend();
        backend.setDepthMask(true);
    }
    
    /**
     * Render the sun as a textured quad in 3D space.
     */
    private void renderSun() {
        if (sunTextureId <= 0) {
            return;
        }
        
        backend.enableTexture2D();
        backend.bindTexture(sunTextureId);
        
        // Draw sun quad at distance 100, size 30x30
        float halfSize = SUN_SIZE / 2.0f;
        
        // The sun is positioned at (0, 100, 0) in the rotated coordinate system
        backend.begin3DQuads();
        backend.addTexturedQuadVertex(-halfSize, CELESTIAL_DISTANCE, -halfSize, 0.0f, 0.0f);
        backend.addTexturedQuadVertex(halfSize, CELESTIAL_DISTANCE, -halfSize, 1.0f, 0.0f);
        backend.addTexturedQuadVertex(halfSize, CELESTIAL_DISTANCE, halfSize, 1.0f, 1.0f);
        backend.addTexturedQuadVertex(-halfSize, CELESTIAL_DISTANCE, halfSize, 0.0f, 1.0f);
        backend.end3DQuads();
        
        backend.unbindTexture();
        backend.disableTexture2D();
    }
    
    /**
     * Render the moon as a textured quad with the correct phase.
     * The moon_phases.png texture is 4x2 grid of moon phases.
     */
    private void renderMoon(int moonPhase) {
        if (moonTextureId <= 0) {
            return;
        }
        
        backend.enableTexture2D();
        backend.bindTexture(moonTextureId);
        
        // Moon is opposite the sun (at -100 distance)
        float halfSize = MOON_SIZE / 2.0f;
        
        // Calculate UV coordinates for the current moon phase
        // Moon phases texture is 4 columns x 2 rows (8 phases total)
        int phaseX = moonPhase % 4;
        int phaseY = moonPhase / 4 % 2;
        float u0 = (phaseX + 1) / 4.0f;  // Note: Minecraft uses reversed U for moon
        float v0 = phaseY / 2.0f;
        float u1 = phaseX / 4.0f;
        float v1 = (phaseY + 1) / 2.0f;
        
        // Draw moon quad at distance -100 (opposite of sun)
        backend.begin3DQuads();
        backend.addTexturedQuadVertex(-halfSize, -CELESTIAL_DISTANCE, halfSize, u0, v1);
        backend.addTexturedQuadVertex(halfSize, -CELESTIAL_DISTANCE, halfSize, u1, v1);
        backend.addTexturedQuadVertex(halfSize, -CELESTIAL_DISTANCE, -halfSize, u1, v0);
        backend.addTexturedQuadVertex(-halfSize, -CELESTIAL_DISTANCE, -halfSize, u0, v0);
        backend.end3DQuads();
        
        backend.unbindTexture();
        backend.disableTexture2D();
    }
    
    /**
     * Render stars as small quads in the sky.
     */
    private void renderStars(float brightness) {
        if (starVertices == null || brightness <= 0.0f) {
            return;
        }
        
        // Disable texturing for stars (they are solid white points)
        backend.disableTexture2D();
        
        // Set star color (white with variable alpha based on time of night)
        int starColor = 0xFFFFFF;
        backend.setColor(starColor, brightness);
        
        // Draw stars as quads
        backend.begin3DQuads();
        
        for (int i = 0; i < starVertices.length; i += 12) {
            // Only draw if not a degenerate quad
            if (starVertices[i] != 0 || starVertices[i+1] != 0 || starVertices[i+2] != 0) {
                // Add 4 vertices for the star quad
                backend.addTexturedQuadVertex(starVertices[i], starVertices[i+1], starVertices[i+2], 0, 0);
                backend.addTexturedQuadVertex(starVertices[i+3], starVertices[i+4], starVertices[i+5], 0, 0);
                backend.addTexturedQuadVertex(starVertices[i+6], starVertices[i+7], starVertices[i+8], 0, 0);
                backend.addTexturedQuadVertex(starVertices[i+9], starVertices[i+10], starVertices[i+11], 0, 0);
            }
        }
        
        backend.end3DQuads();
    }
    
    /**
     * Cleanup resources when no longer needed.
     */
    public void dispose() {
        if (sunTextureId > 0) {
            backend.releaseTexture(sunTextureId);
            sunTextureId = -1;
        }
        if (moonTextureId > 0) {
            backend.releaseTexture(moonTextureId);
            moonTextureId = -1;
        }
        starVertices = null;
    }
}
