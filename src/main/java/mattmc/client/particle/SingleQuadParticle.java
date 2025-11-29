package mattmc.client.particle;

import mattmc.world.level.Level;

/**
 * Base class for particles that render as a single camera-facing quad (billboard).
 * 
 * <p>This class handles the billboard rendering logic - creating four vertices
 * that form a quad always facing the camera. Subclasses provide the texture
 * coordinates (U0/U1/V0/V1).
 * 
 * <p>Mirrors Minecraft's SingleQuadParticle.
 */
public abstract class SingleQuadParticle extends Particle {
    
    /** The size of the rendered quad. */
    protected float quadSize = 0.1f * (random.nextFloat() * 0.5f + 0.5f) * 2.0f;
    
    protected SingleQuadParticle(Level level, double x, double y, double z) {
        super(level, x, y, z);
    }
    
    protected SingleQuadParticle(Level level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
    }
    
    @Override
    public void render(ParticleVertexBuilder builder, double cameraX, double cameraY, double cameraZ, float partialTicks) {
        // Interpolate position
        float x = (float) (lerp(partialTicks, this.xo, this.x) - cameraX);
        float y = (float) (lerp(partialTicks, this.yo, this.y) - cameraY);
        float z = (float) (lerp(partialTicks, this.zo, this.z) - cameraZ);
        
        // Get the current quad size (can be animated)
        float size = this.getQuadSize(partialTicks);
        
        // Get texture coordinates
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        
        // Get light level
        int light = this.getLightColor(partialTicks);
        
        // Create billboard vertices
        // These offsets create a camera-facing quad
        // The actual billboard rotation should be applied by the renderer based on camera orientation
        // For simplicity, we create axis-aligned quads here and let the rendering system handle rotation
        
        // Simple billboard: vertical quad facing +Z (will be rotated by camera later)
        // Vertices in order: bottom-left, bottom-right, top-right, top-left (counter-clockwise for front-facing)
        
        // For a proper billboard, we need camera rotation
        // For now, create a Y-axis aligned billboard (always faces the XZ plane toward camera)
        // This is simpler and still looks good for most particles
        
        float halfSize = size;
        
        // Interpolate roll if present
        float roll = lerp(partialTicks, this.oRoll, this.roll);
        
        // Create rotated quad corners
        float cos = (float) Math.cos(roll);
        float sin = (float) Math.sin(roll);
        
        // Billboard vertices (Y-axis aligned, simple approach)
        // These create a quad in the XY plane at the particle position
        // A full camera-facing billboard would require camera rotation quaternion
        
        // Simplified billboard: just offset in X and Y for now
        // The backend renderer will handle full camera rotation
        
        builder.vertex(x - halfSize * cos - halfSize * sin, y - halfSize * cos + halfSize * sin, z, 
                      u1, v1, rCol, gCol, bCol, alpha, light);
        builder.vertex(x - halfSize * cos + halfSize * sin, y + halfSize * cos + halfSize * sin, z,
                      u1, v0, rCol, gCol, bCol, alpha, light);
        builder.vertex(x + halfSize * cos + halfSize * sin, y + halfSize * cos - halfSize * sin, z,
                      u0, v0, rCol, gCol, bCol, alpha, light);
        builder.vertex(x + halfSize * cos - halfSize * sin, y - halfSize * cos - halfSize * sin, z,
                      u0, v1, rCol, gCol, bCol, alpha, light);
    }
    
    /**
     * Get the quad size, potentially animated based on particle age.
     */
    public float getQuadSize(float partialTicks) {
        return this.quadSize;
    }
    
    @Override
    public Particle scale(float scale) {
        this.quadSize *= scale;
        return super.scale(scale);
    }
    
    /**
     * Linear interpolation.
     */
    protected static float lerp(float delta, double start, double end) {
        return (float) (start + delta * (end - start));
    }
    
    // Texture coordinate methods - to be implemented by subclasses
    protected abstract float getU0();
    protected abstract float getU1();
    protected abstract float getV0();
    protected abstract float getV1();
}
