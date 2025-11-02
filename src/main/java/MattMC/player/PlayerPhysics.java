package MattMC.player;

import MattMC.world.Block;
import MattMC.world.Chunk;
import MattMC.world.WorldAccess;

/**
 * Handles player physics including gravity, collision detection, and flying.
 * Similar to Minecraft's EntityPlayerSP physics.
 */
public class PlayerPhysics {
    // Player hitbox dimensions (Minecraft-like)
    public static final float PLAYER_WIDTH = 0.6f;
    public static final float PLAYER_HEIGHT = 1.8f;
    public static final float PLAYER_DEPTH = 0.6f;
    
    // Physics constants
    private static final float GRAVITY = 20f; // Blocks per second squared
    private static final float TERMINAL_VELOCITY = 50f; // Max fall speed
    private static final float JUMP_VELOCITY = 8f; // Initial jump speed
    
    private final Player player;
    private final WorldAccess world;
    
    // Physics state
    private float velocityY = 0f; // Vertical velocity
    private boolean onGround = false;
    private boolean flying = false;
    
    // Double-tap space detection
    private double lastSpacePress = 0;
    private static final double DOUBLE_TAP_THRESHOLD = 0.3; // seconds
    
    public PlayerPhysics(Player player, WorldAccess world) {
        this.player = player;
        this.world = world;
    }
    
    /**
     * Update physics (gravity, collision) each frame.
     */
    public void update(float deltaTime) {
        if (!flying) {
            // Apply gravity
            velocityY -= GRAVITY * deltaTime;
            
            // Terminal velocity
            if (velocityY < -TERMINAL_VELOCITY) {
                velocityY = -TERMINAL_VELOCITY;
            }
            
            // Apply vertical movement
            float newY = player.getY() + velocityY * deltaTime;
            
            // Check collision and adjust
            if (velocityY < 0) {
                // Falling - check ground collision
                newY = handleVerticalCollision(newY, velocityY < 0);
                if (newY != player.getY() + velocityY * deltaTime) {
                    // Hit ground
                    velocityY = 0;
                    onGround = true;
                }
            } else {
                // Rising - check ceiling collision
                newY = handleVerticalCollision(newY, velocityY < 0);
                if (newY != player.getY() + velocityY * deltaTime) {
                    velocityY = 0;
                }
            }
            
            player.setY(newY);
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
            if (!checkCollision(newX, player.getY(), player.getZ())) {
                player.setX(newX);
            } else {
                return false;
            }
        }
        
        if (dz != 0) {
            if (!checkCollision(player.getX(), player.getY(), newZ)) {
                player.setZ(newZ);
            } else {
                return false;
            }
        }
        
        // Vertical movement in flying mode
        if (flying && dy != 0) {
            if (!checkCollision(player.getX(), newY, player.getZ())) {
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
     * Check if player hitbox collides with any blocks.
     */
    private boolean checkCollision(float x, float y, float z) {
        // Player feet are at y, head at y + HEIGHT
        // Check all blocks that could intersect with player hitbox
        
        float minX = x - PLAYER_WIDTH / 2;
        float maxX = x + PLAYER_WIDTH / 2;
        float minY = y;
        float maxY = y + PLAYER_HEIGHT;
        float minZ = z - PLAYER_DEPTH / 2;
        float maxZ = z + PLAYER_DEPTH / 2;
        
        // Check all potential collision blocks
        int startX = (int) Math.floor(minX);
        int endX = (int) Math.floor(maxX);
        int startY = (int) Math.floor(minY);
        int endY = (int) Math.floor(maxY);
        int startZ = (int) Math.floor(minZ);
        int endZ = (int) Math.floor(maxZ);
        
        for (int bx = startX; bx <= endX; bx++) {
            for (int by = startY; by <= endY; by++) {
                for (int bz = startZ; bz <= endZ; bz++) {
                    // Convert world Y to chunk Y
                    int chunkY = Chunk.worldYToChunkY(by);
                    
                    // Check if block coordinates are valid
                    if (chunkY >= 0 && chunkY < Chunk.HEIGHT) {
                        Block block = world.getBlock(bx, chunkY, bz);
                        if (!block.isAir()) {
                            // Solid block found - collision!
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Handle vertical collision (ground/ceiling).
     * @return adjusted Y position
     */
    private float handleVerticalCollision(float targetY, boolean falling) {
        float currentY = player.getY();
        
        // Check if target position collides
        if (checkCollision(player.getX(), targetY, player.getZ())) {
            // Binary search to find exact collision point
            if (falling) {
                // Find ground level
                int blockY = (int) Math.floor(targetY);
                return blockY + 1.0f; // Stand on top of block
            } else {
                // Hit ceiling
                int blockY = (int) Math.floor(targetY + PLAYER_HEIGHT);
                return blockY - PLAYER_HEIGHT; // Stop at ceiling
            }
        }
        
        return targetY;
    }
    
    /**
     * Find the highest solid block at given X,Z position to spawn player on top.
     * Ensures spawn position has at least 2 blocks of air above for player headroom.
     */
    public static float findSpawnHeight(WorldAccess world, float x, float z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        
        // Search from top down
        for (int worldY = Chunk.MAX_Y; worldY >= Chunk.MIN_Y; worldY--) {
            int chunkY = Chunk.worldYToChunkY(worldY);
            
            // Check if this is a solid block
            Block block = world.getBlock(blockX, chunkY, blockZ);
            if (!block.isAir()) {
                // Check if there's enough headroom (at least 2 blocks of air above)
                int spawnWorldY = worldY + 1;
                int spawnChunkY = Chunk.worldYToChunkY(spawnWorldY);
                int headChunkY = Chunk.worldYToChunkY(spawnWorldY + 1);
                
                if (spawnChunkY >= 0 && spawnChunkY < Chunk.HEIGHT && 
                    headChunkY >= 0 && headChunkY < Chunk.HEIGHT) {
                    Block aboveBlock = world.getBlock(blockX, spawnChunkY, blockZ);
                    Block headBlock = world.getBlock(blockX, headChunkY, blockZ);
                    
                    if (aboveBlock.isAir() && headBlock.isAir()) {
                        // Found valid spawn location - on top of solid block with air above
                        return spawnWorldY;
                    }
                }
            }
        }
        
        // No solid block found or no valid spawn location - default to surface level
        // This should rarely happen with proper terrain generation
        return 65f;
    }
    
    public boolean isFlying() { return flying; }
    public void setFlying(boolean flying) { this.flying = flying; }
    public boolean isOnGround() { return onGround; }
}
