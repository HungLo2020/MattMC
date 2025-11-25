package mattmc.world.entity.player;


import mattmc.client.MattMC;
import mattmc.world.item.Inventory;
/**
 * Represents the player in the game world.
 * Similar to MattMC's EntityPlayer class.
 * Handles player position, orientation, and movement.
 */
public class LocalPlayer {
    // Eye height offset from feet (MattMC default is 1.62 blocks)
    public static final float EYE_HEIGHT = 1.62f;
    
    // LocalPlayer position in world coordinates (feet position)
    private float x;
    private float y;
    private float z;
    
    // Previous position for interpolation
    private float prevX;
    private float prevY;
    private float prevZ;
    
    // Camera orientation
    private float yaw;   // Horizontal rotation (left/right)
    private float pitch; // Vertical rotation (up/down)
    
    // Previous orientation for interpolation
    private float prevYaw;
    private float prevPitch;
    
    // Movement speed
    private float moveSpeed = 4.317f; // MattMC walking speed (blocks/second)
    private float sprintSpeed = 5.612f; // MattMC sprinting speed (1.3x walking)
    private float sneakSpeed = 1.295f; // MattMC sneaking speed (0.3x walking)
    private float flySpeed = 10.92f;   // MattMC flying speed
    private float flySprintSpeed = 21.84f; // MattMC flying sprint speed (2x flying)
    
    // Physics reference (set externally)
    private PlayerPhysics physics;
    
    // Player inventory
    private final Inventory inventory;
    
    public LocalPlayer(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.yaw = 0f;
        this.pitch = 0f;
        this.prevYaw = 0f;
        this.prevPitch = 0f;
        this.inventory = new Inventory();
    }
    
    /**
     * Call this at the start of each tick to save current position for interpolation.
     * This allows smooth rendering between ticks.
     */
    public void updatePreviousPosition() {
        prevX = x;
        prevY = y;
        prevZ = z;
        prevYaw = yaw;
        prevPitch = pitch;
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
     * Get interpolated X position for smooth rendering.
     * @param alpha interpolation factor between ticks (0.0 to 1.0)
     */
    public float getX(float alpha) {
        return prevX + (x - prevX) * alpha;
    }
    
    /**
     * Get interpolated Y position for smooth rendering.
     * @param alpha interpolation factor between ticks (0.0 to 1.0)
     */
    public float getY(float alpha) {
        return prevY + (y - prevY) * alpha;
    }
    
    /**
     * Get interpolated Z position for smooth rendering.
     * @param alpha interpolation factor between ticks (0.0 to 1.0)
     */
    public float getZ(float alpha) {
        return prevZ + (z - prevZ) * alpha;
    }
    
    /**
     * Get the Y coordinate of the player's eyes (camera position).
     * In MattMC, the camera is 1.62 blocks above the feet.
     */
    public float getEyeY() { return y + EYE_HEIGHT; }
    
    /**
     * Get interpolated eye Y position for smooth rendering.
     * @param alpha interpolation factor between ticks (0.0 to 1.0)
     */
    public float getEyeY(float alpha) {
        return getY(alpha) + EYE_HEIGHT;
    }
    
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    
    /**
     * Get interpolated yaw for smooth rendering.
     * @param alpha interpolation factor between ticks (0.0 to 1.0)
     */
    public float getYaw(float alpha) {
        // Handle wrapping around 360 degrees
        float delta = yaw - prevYaw;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return prevYaw + delta * alpha;
    }
    
    /**
     * Get interpolated pitch for smooth rendering.
     * @param alpha interpolation factor between ticks (0.0 to 1.0)
     */
    public float getPitch(float alpha) {
        return prevPitch + (pitch - prevPitch) * alpha;
    }
    public float getMoveSpeed() { return moveSpeed; }
    public float getSprintSpeed() { return sprintSpeed; }
    public float getSneakSpeed() { return sneakSpeed; }
    public float getFlySpeed() { return flySpeed; }
    public float getFlySprintSpeed() { return flySprintSpeed; }
    
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
    
    /**
     * Get the player's inventory.
     * 
     * @return The inventory
     */
    public Inventory getInventory() { return inventory; }
}
