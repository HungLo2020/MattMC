package MattMC.player;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles player input and controls.
 * Similar to Minecraft's PlayerController/MovementInput classes.
 */
public class PlayerController {
    // Mouse sensitivity multiplier (1.0 = default, 0.5 = half speed, 2.0 = double speed)
    public static final float MOUSE_SENSITIVITY = 1.0f;
    
    private final Player player;
    
    // Mouse state for delta calculation
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean firstMouse = true;
    
    public PlayerController(Player player) {
        this.player = player;
    }
    
    /**
     * Update player movement based on keyboard input.
     * @param window GLFW window handle
     * @param deltaTime Time since last frame in seconds
     */
    public void updateMovement(long window, float deltaTime) {
        float moveSpeed = player.getMoveSpeed() * deltaTime;
        
        // WASD movement
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            player.moveForward(moveSpeed);
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            player.moveForward(-moveSpeed);
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            player.moveRight(-moveSpeed);
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            player.moveRight(moveSpeed);
        }
        
        // Vertical movement
        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
            player.moveUp(moveSpeed);
        }
        if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS || 
            glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS) {
            player.moveUp(-moveSpeed);
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
