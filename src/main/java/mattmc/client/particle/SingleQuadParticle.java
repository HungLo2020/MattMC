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
        // Interpolate position relative to camera
        float px = (float) (lerp(partialTicks, this.xo, this.x) - cameraX);
        float py = (float) (lerp(partialTicks, this.yo, this.y) - cameraY);
        float pz = (float) (lerp(partialTicks, this.zo, this.z) - cameraZ);
        
        // Get the current quad size (can be animated)
        float size = this.getQuadSize(partialTicks);
        
        // Get texture coordinates
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        
        // Get light level
        int light = this.getLightColor(partialTicks);
        
        // Calculate billboard orientation
        // The camera is at origin (0, 0, 0) in camera space
        // The particle is at (px, py, pz) relative to the camera
        
        // Calculate the direction from particle to camera
        float dx = -px;
        float dz = -pz;
        float distXZ = (float) Math.sqrt(dx * dx + dz * dz);
        
        // Right vector (perpendicular to look direction in XZ plane)
        float rightX, rightZ;
        if (distXZ > 0.001f) {
            rightX = -dz / distXZ;
            rightZ = dx / distXZ;
        } else {
            // Camera is directly above/below, use default orientation
            rightX = 1.0f;
            rightZ = 0.0f;
        }
        
        // Up vector (always world up for simplicity - cylindrical billboard)
        float upX = 0.0f;
        float upY = 1.0f;
        float upZ = 0.0f;
        
        // Interpolate roll if present
        float roll = lerp(partialTicks, this.oRoll, this.roll);
        if (roll != 0.0f) {
            // Apply roll rotation around the look direction
            float cos = (float) Math.cos(roll);
            float sin = (float) Math.sin(roll);
            
            // Rotate right and up vectors
            float newRightX = rightX * cos + upX * sin;
            float newRightZ = rightZ * cos + upZ * sin;
            float newUpY = upY * cos;
            
            rightX = newRightX;
            rightZ = newRightZ;
            upY = newUpY;
        }
        
        // Scale vectors by quad size
        rightX *= size;
        rightZ *= size;
        upY *= size;
        
        // Create the four corners of the billboard quad
        // Vertex order: bottom-left, top-left, top-right, bottom-right
        float x0 = px - rightX - upX;
        float y0 = py - upY;
        float z0 = pz - rightZ - upZ;
        
        float x1 = px - rightX + upX;
        float y1 = py + upY;
        float z1 = pz - rightZ + upZ;
        
        float x2 = px + rightX + upX;
        float y2 = py + upY;
        float z2 = pz + rightZ + upZ;
        
        float x3 = px + rightX - upX;
        float y3 = py - upY;
        float z3 = pz + rightZ - upZ;
        
        // Emit vertices (counter-clockwise winding)
        builder.vertex(x0, y0, z0, u1, v1, rCol, gCol, bCol, alpha, light);
        builder.vertex(x1, y1, z1, u1, v0, rCol, gCol, bCol, alpha, light);
        builder.vertex(x2, y2, z2, u0, v0, rCol, gCol, bCol, alpha, light);
        builder.vertex(x3, y3, z3, u0, v1, rCol, gCol, bCol, alpha, light);
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
