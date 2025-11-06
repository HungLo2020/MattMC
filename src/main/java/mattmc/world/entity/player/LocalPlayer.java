package mattmc.world.entity.player;


import mattmc.client.Minecraft;
/**
 * Represents the player in the game world.
 * Similar to Minecraft's EntityPlayer class.
 * Handles player position, orientation, and movement.
 */
public class LocalPlayer {
    // Eye height offset from feet (Minecraft default is 1.62 blocks)
    public static final float EYE_HEIGHT = 1.62f;
    
    // LocalPlayer position in world coordinates (feet position)
    private float x;
    private float y;
    private float z;
    
    // Camera orientation
    private float yaw;   // Horizontal rotation (left/right)
    private float pitch; // Vertical rotation (up/down)
    
    // Movement speed
    private float moveSpeed = 4.317f; // Minecraft walking speed (blocks/second)
    private float flySpeed = 10.92f;   // Minecraft flying speed
    
    // Physics reference (set externally)
    private PlayerPhysics physics;
    
    public LocalPlayer(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = 0f;
        this.pitch = 0f;
    }
    
    /**
     * Move the player forward/backward based on view direction.
     * Uses physics for collision detection.
     */
    public void moveForward(float distance) {
        float yawRad = (float) Math.toRadians(yaw);
        float dx = (float) Math.sin(yawRad) * distance;
        float dz = -(float) Math.cos(yawRad) * distance;
        
        if (physics != null) {
            physics.tryMove(dx, 0, dz);
        } else {
            x += dx;
            z += dz;
        }
    }
    
    /**
     * Strafe the player left/right based on view direction.
     * Uses physics for collision detection.
     */
    public void moveRight(float distance) {
        float yawRad = (float) Math.toRadians(yaw);
        float dx = (float) Math.cos(yawRad) * distance;
        float dz = (float) Math.sin(yawRad) * distance;
        
        if (physics != null) {
            physics.tryMove(dx, 0, dz);
        } else {
            x += dx;
            z += dz;
        }
    }
    
    /**
     * Move the player up/down (vertical movement for flying).
     * Uses physics for collision detection.
     */
    public void moveUp(float distance) {
        if (physics != null && physics.isFlying()) {
            physics.tryMove(0, distance, 0);
        } else if (physics == null) {
            y += distance;
        }
    }
    
    /**
     * Rotate the player's view horizontally.
     */
    public void rotateYaw(float degrees) {
        yaw += degrees;
    }
    
    /**
     * Rotate the player's view vertically with clamping.
     */
    public void rotatePitch(float degrees) {
        pitch += degrees;
        // Clamp pitch to prevent camera flipping
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;
    }
    
    /**
     * Get the forward direction vector based on pitch and yaw.
     */
    public float[] getForwardVector() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        
        return new float[] {
            (float) (Math.cos(pitchRad) * Math.sin(yawRad)),
            (float) (-Math.sin(pitchRad)),
            (float) (-Math.cos(pitchRad) * Math.cos(yawRad))
        };
    }
    
    // Getters and setters
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    
    /**
     * Get the Y coordinate of the player's eyes (camera position).
     * In Minecraft, the camera is 1.62 blocks above the feet.
     */
    public float getEyeY() { return y + EYE_HEIGHT; }
    
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getMoveSpeed() { return moveSpeed; }
    public float getFlySpeed() { return flySpeed; }
    
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public void setZ(float z) { this.z = z; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { 
        this.pitch = pitch;
        // Clamp pitch
        if (this.pitch > 89.0f) this.pitch = 89.0f;
        if (this.pitch < -89.0f) this.pitch = -89.0f;
    }
    public void setMoveSpeed(float speed) { this.moveSpeed = speed; }
    public void setPhysics(PlayerPhysics physics) { this.physics = physics; }
    public PlayerPhysics getPhysics() { return physics; }
}
