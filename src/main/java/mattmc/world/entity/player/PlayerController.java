package mattmc.world.entity.player;

import mattmc.client.Minecraft;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles player input and controls.
 * Similar to Minecraft's PlayerController/MovementInput classes.
 */
public class PlayerController {
    // Mouse sensitivity multiplier (1.0 = default, 0.5 = half speed, 2.0 = double speed)
    public static final float MOUSE_SENSITIVITY = 1.0f;
    
    private final LocalPlayer player;
    private final PlayerInput input;
    
    // Mouse state for delta calculation
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean firstMouse = true;
    
    public PlayerController(LocalPlayer player) {
        this.player = player;
        this.input = PlayerInput.getInstance();
    }
    
    /**
     * Update player movement based on keyboard input.
     * @param window GLFW window handle
     * @param deltaTime Time since last frame in seconds
     */
    public void updateMovement(long window, float deltaTime) {
        PlayerPhysics physics = player.getPhysics();
        boolean isFlying = physics != null && physics.isFlying();
        
        // Use flying speed when flying, walking speed otherwise
        float moveSpeed = isFlying ? player.getFlySpeed() * deltaTime : player.getMoveSpeed() * deltaTime;
        
        // Movement using PlayerInput abstraction
        if (input.isPressed(window, PlayerInput.FORWARD)) {
            player.moveForward(moveSpeed);
        }
        if (input.isPressed(window, PlayerInput.BACKWARD)) {
            player.moveForward(-moveSpeed);
        }
        if (input.isPressed(window, PlayerInput.LEFT)) {
            player.moveRight(-moveSpeed);
        }
        if (input.isPressed(window, PlayerInput.RIGHT)) {
            player.moveRight(moveSpeed);
        }
        
        // Vertical movement (flying only)
        if (isFlying) {
            if (input.isPressed(window, PlayerInput.FLY_UP)) {
                player.moveUp(moveSpeed);
            }
            if (input.isPressed(window, PlayerInput.CROUCH)) {
                player.moveUp(-moveSpeed);
            }
        }
    }
    
    /**
     * Handle space key press for jumping/flying toggle.
     */
    public void handleSpacePress() {
        PlayerPhysics physics = player.getPhysics();
        if (physics != null) {
            physics.handleSpacePress();
        }
    }
    
    /**
     * Handle mouse movement for camera rotation.
     * @param xpos Current mouse X position
     * @param ypos Current mouse Y position
     */
    public void handleMouseMovement(double xpos, double ypos) {
        if (firstMouse) {
            lastMouseX = xpos;
            lastMouseY = ypos;
            firstMouse = false;
            return;
        }
        
        double xOffset = xpos - lastMouseX;
        double yOffset = ypos - lastMouseY; // Natural mouse movement: up = look up, down = look down
        lastMouseX = xpos;
        lastMouseY = ypos;
        
        // Apply sensitivity
        xOffset *= MOUSE_SENSITIVITY * 0.1;
        yOffset *= MOUSE_SENSITIVITY * 0.1;
        
        player.rotateYaw((float) xOffset);
        player.rotatePitch((float) yOffset);
    }
    
    /**
     * Reset mouse state (useful when screen opens).
     */
    public void resetMouseState() {
        firstMouse = true;
    }
}
