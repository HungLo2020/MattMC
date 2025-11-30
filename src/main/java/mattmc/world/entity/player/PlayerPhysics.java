package mattmc.world.entity.player;

import mattmc.registries.Blocks;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.LevelAccessor;

/**
 * Handles player physics including gravity, collision detection, and flying.
 * Similar to MattMC's EntityPlayerSP physics.
 */
public class PlayerPhysics {
    // LocalPlayer hitbox dimensions (MattMC-like)
    public static final float PLAYER_WIDTH = 0.6f;
    public static final float PLAYER_HEIGHT = 1.8f;
    public static final float PLAYER_DEPTH = 0.6f;
    
    // Physics constants (MattMC Java Edition values adapted for our engine)
    // MattMC runs at 20 TPS (0.05s per tick)
    private static final float GRAVITY = 32f; // Blocks per second squared (MC: 0.08/tick² × 400)
    private static final float TERMINAL_VELOCITY = 78.4f; // Max fall speed (blocks/second)
    // Jump velocity: 8.17 blocks/s achieves exactly 1.25 block height with fixed dt=0.05s
    // Matches MattMC Java Edition jump height (MC uses 8.4 blocks/s with air resistance)
    private static final float JUMP_VELOCITY = 8.17f; // Initial jump speed (blocks/second)
    
    private final LocalPlayer player;
    private final CollisionDetector collisionDetector;
    
    // Physics state
    private float velocityY = 0f; // Vertical velocity
    private boolean onGround = false;
    private boolean flying = false;
    
    // Double-tap space detection
    private double lastSpacePress = 0;
    private static final double DOUBLE_TAP_THRESHOLD = 0.3; // seconds
    
    public PlayerPhysics(LocalPlayer player, LevelAccessor world) {
        this.player = player;
        this.collisionDetector = new CollisionDetector(world);
    }
    
    /**
     * Update physics (gravity, collision) each frame.
     */
    public void update(float deltaTime) {
        if (!flying) {
            // Apply vertical movement FIRST using current velocity (MattMC physics order)
            float newY = player.getY() + velocityY * deltaTime;
            
            // Check collision and adjust
            if (velocityY < 0) {
                // Falling - check ground collision
                newY = collisionDetector.handleVerticalCollision(player.getX(), player.getZ(), newY, true);
                if (newY != player.getY() + velocityY * deltaTime) {
                    // Hit ground
                    velocityY = 0;
                    onGround = true;
                }
            } else {
                // Rising - check ceiling collision
                newY = collisionDetector.handleVerticalCollision(player.getX(), player.getZ(), newY, false);
                if (newY != player.getY() + velocityY * deltaTime) {
                    velocityY = 0;
                }
            }
            
            player.setY(newY);
            
            // Apply gravity AFTER movement (correct MattMC order)
            velocityY -= GRAVITY * deltaTime;
            
            // Terminal velocity
            if (velocityY < -TERMINAL_VELOCITY) {
                velocityY = -TERMINAL_VELOCITY;
            }
        } else {
            // Flying mode - no gravity
            velocityY = 0;
            onGround = false;
        }
    }
    
    /**
     * Attempt to move player with collision detection.
     * @return true if movement was successful (not blocked)
     */
    public boolean tryMove(float dx, float dy, float dz) {
        float newX = player.getX() + dx;
        float newY = player.getY() + dy;
        float newZ = player.getZ() + dz;
        
        // Check horizontal collision (X and Z)
        if (dx != 0) {
            if (!collisionDetector.checkCollision(newX, player.getY(), player.getZ())) {
                player.setX(newX);
            } else {
                return false;
            }
        }
        
        if (dz != 0) {
            if (!collisionDetector.checkCollision(player.getX(), player.getY(), newZ)) {
                player.setZ(newZ);
            } else {
                return false;
            }
        }
        
        // Vertical movement in flying mode
        if (flying && dy != 0) {
            if (!collisionDetector.checkCollision(player.getX(), newY, player.getZ())) {
                player.setY(newY);
            }
        }
        
        return true;
    }
    
    /**
     * Handle double-tap space for flying toggle.
     */
    public void handleSpacePress() {
        double now = System.nanoTime() * 1e-9;
        
        if (now - lastSpacePress < DOUBLE_TAP_THRESHOLD) {
            // Double tap detected - toggle flying
            flying = !flying;
            velocityY = 0;
        }
        
        lastSpacePress = now;
        
        // Jump if on ground and not flying
        if (!flying && onGround) {
            velocityY = JUMP_VELOCITY;
            onGround = false;
        }
    }
    
    /**
     * Handle continuous jump when space is held.
     * This allows jumping immediately when hitting the ground if space is still held.
     */
    public void handleSpaceHeld() {
        // Only jump if on ground, not flying, and not in the double-tap window
        if (!flying && onGround) {
            velocityY = JUMP_VELOCITY;
            onGround = false;
        }
    }
    
    /**
     * Find the highest solid block at given X,Z position to spawn player on top.
     * Ensures spawn position has at least 2 blocks of air above for player headroom.
     */
    public static float findSpawnHeight(LevelAccessor world, float x, float z) {
        return CollisionDetector.findSpawnHeight(world, x, z);
    }
    
    public boolean isFlying() { return flying; }
    public void setFlying(boolean flying) { this.flying = flying; }
    public boolean isOnGround() { return onGround; }
}
