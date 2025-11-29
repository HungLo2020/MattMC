package mattmc.client.particle;

import mattmc.world.level.Level;

import java.util.Random;

/**
 * Base class for all client-side particles.
 * 
 * <p>Particles are visual effects that exist in the world with position, velocity,
 * color, and a limited lifetime. They are updated each tick and rendered as billboards
 * facing the camera.
 * 
 * <p>This class mirrors Minecraft's net.minecraft.client.particle.Particle structure.
 */
public abstract class Particle {
    private static final double MAXIMUM_COLLISION_VELOCITY_SQUARED = 10000.0; // 100^2
    
    protected final Level level;
    
    // Previous position (for interpolation)
    protected double xo;
    protected double yo;
    protected double zo;
    
    // Current position
    protected double x;
    protected double y;
    protected double z;
    
    // Velocity
    protected double xd;
    protected double yd;
    protected double zd;
    
    // Collision bounds
    protected float bbWidth = 0.6f;
    protected float bbHeight = 1.8f;
    
    // Physics
    protected boolean onGround;
    protected boolean hasPhysics = true;
    private boolean stoppedByCollision;
    protected boolean removed;
    
    // Lifecycle
    protected int age;
    protected int lifetime;
    
    // Physics properties
    protected float gravity;
    protected float friction = 0.98f;
    protected boolean speedUpWhenYMotionIsBlocked = false;
    
    // Appearance
    protected float rCol = 1.0f;
    protected float gCol = 1.0f;
    protected float bCol = 1.0f;
    protected float alpha = 1.0f;
    
    // Rotation
    protected float roll;
    protected float oRoll;
    
    // Random for variation
    protected final Random random = new Random();
    
    /**
     * Create a particle at the given position.
     */
    protected Particle(Level level, double x, double y, double z) {
        this.level = level;
        this.setSize(0.2f, 0.2f);
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.lifetime = (int) (4.0f / (random.nextFloat() * 0.9f + 0.1f));
    }
    
    /**
     * Create a particle with initial velocity.
     */
    public Particle(Level level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        this(level, x, y, z);
        this.xd = xSpeed + (random.nextDouble() * 2.0 - 1.0) * 0.4;
        this.yd = ySpeed + (random.nextDouble() * 2.0 - 1.0) * 0.4;
        this.zd = zSpeed + (random.nextDouble() * 2.0 - 1.0) * 0.4;
        double d0 = (random.nextDouble() + random.nextDouble() + 1.0) * 0.15;
        double d1 = Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        this.xd = this.xd / d1 * d0 * 0.4;
        this.yd = this.yd / d1 * d0 * 0.4 + 0.1;
        this.zd = this.zd / d1 * d0 * 0.4;
    }
    
    /**
     * Set the power/multiplier for particle velocity.
     */
    public Particle setPower(float multiplier) {
        this.xd *= multiplier;
        this.yd = (this.yd - 0.1) * multiplier + 0.1;
        this.zd *= multiplier;
        return this;
    }
    
    /**
     * Set the particle's velocity directly.
     */
    public void setParticleSpeed(double xd, double yd, double zd) {
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
    }
    
    /**
     * Scale the particle size.
     */
    public Particle scale(float scale) {
        this.setSize(0.2f * scale, 0.2f * scale);
        return this;
    }
    
    /**
     * Set the particle color.
     */
    public void setColor(float r, float g, float b) {
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
    }
    
    /**
     * Set the particle alpha.
     */
    protected void setAlpha(float alpha) {
        this.alpha = alpha;
    }
    
    /**
     * Set the particle lifetime.
     */
    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }
    
    /**
     * Get the particle lifetime.
     */
    public int getLifetime() {
        return lifetime;
    }
    
    /**
     * Update the particle each tick.
     */
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.yd -= 0.04 * this.gravity;
            this.move(this.xd, this.yd, this.zd);
            
            if (this.speedUpWhenYMotionIsBlocked && this.y == this.yo) {
                this.xd *= 1.1;
                this.zd *= 1.1;
            }
            
            this.xd *= this.friction;
            this.yd *= this.friction;
            this.zd *= this.friction;
            
            if (this.onGround) {
                this.xd *= 0.7;
                this.zd *= 0.7;
            }
        }
    }
    
    /**
     * Render the particle.
     * 
     * @param builder the vertex builder to render to
     * @param cameraX camera X position
     * @param cameraY camera Y position
     * @param cameraZ camera Z position
     * @param partialTicks interpolation factor (0-1)
     */
    public abstract void render(ParticleVertexBuilder builder, double cameraX, double cameraY, double cameraZ, float partialTicks);
    
    /**
     * Get the render type for this particle.
     */
    public abstract ParticleRenderType getRenderType();
    
    @Override
    public String toString() {
        return this.getClass().getSimpleName() + ", Pos (" + x + "," + y + "," + z + 
               "), RGBA (" + rCol + "," + gCol + "," + bCol + "," + alpha + "), Age " + age;
    }
    
    /**
     * Mark this particle for removal.
     */
    public void remove() {
        this.removed = true;
    }
    
    /**
     * Check if this particle is still alive.
     */
    public boolean isAlive() {
        return !this.removed;
    }
    
    /**
     * Set the particle's collision size.
     */
    protected void setSize(float width, float height) {
        this.bbWidth = width;
        this.bbHeight = height;
    }
    
    /**
     * Set the particle's position.
     */
    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    /**
     * Move the particle, optionally with collision detection.
     */
    public void move(double dx, double dy, double dz) {
        if (!this.stoppedByCollision) {
            double originalDy = dy;
            
            // Simple collision: just move without actual collision detection for now
            // TODO: Add proper collision with the level when physics are needed
            this.x += dx;
            this.y += dy;
            this.z += dz;
            
            // Check for ground collision (simple Y=0 check for now)
            if (this.hasPhysics && this.y <= 0) {
                this.y = 0;
                this.onGround = true;
                this.yd = 0;
            }
        }
    }
    
    /**
     * Get the light level at the particle's position.
     * Returns a packed light value for rendering.
     */
    protected int getLightColor(float partialTicks) {
        // For now, return max brightness
        // TODO: Sample actual light level from level when lighting is available
        return 240 | (240 << 16); // Sky light 15, block light 15
    }
    
    /**
     * Get the particle's X position.
     */
    public double getX() {
        return x;
    }
    
    /**
     * Get the particle's Y position.
     */
    public double getY() {
        return y;
    }
    
    /**
     * Get the particle's Z position.
     */
    public double getZ() {
        return z;
    }
    
    /**
     * Whether this particle should be culled against the view frustum.
     */
    public boolean shouldCull() {
        return true;
    }
}
