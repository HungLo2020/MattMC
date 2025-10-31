package MattMC.player;

/**
 * Represents the player in the game world.
 * Similar to Minecraft's EntityPlayer class.
 * Handles player position, orientation, and movement.
 */
public class Player {
    // Player position in world coordinates
    private float x;
    private float y;
    private float z;
    
    // Camera orientation
    private float yaw;   // Horizontal rotation (left/right)
    private float pitch; // Vertical rotation (up/down)
    
    // Movement speed
    private float moveSpeed = 10f;
    
    public Player(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = 0f;
        this.pitch = 0f;
    }
    
    /**
     * Move the player forward/backward based on view direction.
     */
    public void moveForward(float distance) {
        float yawRad = (float) Math.toRadians(yaw);
        x += (float) Math.sin(yawRad) * distance;
        z -= (float) Math.cos(yawRad) * distance;
    }
    
    /**
     * Strafe the player left/right based on view direction.
     */
    public void moveRight(float distance) {
        float yawRad = (float) Math.toRadians(yaw);
        x += (float) Math.cos(yawRad) * distance;
        z += (float) Math.sin(yawRad) * distance;
    }
    
    /**
     * Move the player up/down (vertical movement).
     */
    public void moveUp(float distance) {
        y += distance;
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
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getMoveSpeed() { return moveSpeed; }
    
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
}
