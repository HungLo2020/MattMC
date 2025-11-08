package mattmc.world.entity.player;

import mattmc.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Abstracts player input handling, allowing for configurable keybinds.
 * This class acts as a layer between raw GLFW input and game actions.
 */
public class PlayerInput {
    // Singleton instance
    private static PlayerInput instance;
    
    // Map of action names to key codes
    private final Map<String, Integer> keybinds = new HashMap<>();
    
    // Action names
    public static final String FORWARD = "forward";
    public static final String BACKWARD = "backward";
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String JUMP = "jump";
    public static final String SPRINT = "sprint";
    public static final String CROUCH = "crouch";
    public static final String BREAK_BLOCK = "break_block";
    public static final String PLACE_BLOCK = "place_block";
    public static final String FLY_UP = "fly_up";
    public static final String OPEN_COMMAND = "open_command";
    public static final String INVENTORY = "inventory";
    public static final String HOTBAR_1 = "hotbar_1";
    public static final String HOTBAR_2 = "hotbar_2";
    public static final String HOTBAR_3 = "hotbar_3";
    public static final String HOTBAR_4 = "hotbar_4";
    public static final String HOTBAR_5 = "hotbar_5";
    public static final String HOTBAR_6 = "hotbar_6";
    public static final String HOTBAR_7 = "hotbar_7";
    public static final String HOTBAR_8 = "hotbar_8";
    public static final String HOTBAR_9 = "hotbar_9";
    
    private PlayerInput() {
        // Keybinds are initialized by KeybindManager from DefaultOptions.txt config file
    }
    
    /**
     * Get the singleton instance of PlayerInput.
     */
    public static PlayerInput getInstance() {
        if (instance == null) {
            instance = new PlayerInput();
        }
        return instance;
    }
    

    /**
     * Check if an action is currently pressed.
     * @param window GLFW window handle
     * @param action Action name (e.g., "forward", "jump")
     * @return true if the key/button for this action is pressed
     */
    public boolean isPressed(long window, String action) {
        Integer keyCode = keybinds.get(action);
        if (keyCode == null) return false;
        
        // Mouse buttons use negative values to distinguish from keyboard keys
        if (keyCode < 0) {
            return glfwGetMouseButton(window, convertToGlfwMouseButton(keyCode)) == GLFW_PRESS;
        } else {
            return glfwGetKey(window, keyCode) == GLFW_PRESS;
        }
    }
    
    /**
     * Convert internal mouse button representation to GLFW mouse button constant.
     * @param keyCode Negative value representing a mouse button
     * @return GLFW mouse button constant
     */
    private static int convertToGlfwMouseButton(int keyCode) {
        return -keyCode - 1;
    }
    
    /**
     * Get the key code for an action.
     * @param action Action name
     * @return Key code, or null if action doesn't exist
     */
    public Integer getKeybind(String action) {
        return keybinds.get(action);
    }
    
    /**
     * Set the key code for an action.
     * @param action Action name
     * @param keyCode Key code (positive for keyboard, negative for mouse buttons)
     */
    public void setKeybind(String action, int keyCode) {
        keybinds.put(action, keyCode);
    }
    
    /**
     * Get all keybinds as a map.
     * @return Map of action names to key codes
     */
    public Map<String, Integer> getAllKeybinds() {
        return new HashMap<>(keybinds);
    }
    
    /**
     * Set multiple keybinds at once.
     * @param newKeybinds Map of action names to key codes
     */
    public void setAllKeybinds(Map<String, Integer> newKeybinds) {
        keybinds.clear();
        keybinds.putAll(newKeybinds);
    }
    
    /**
     * Get a human-readable name for a key code.
     * @param keyCode GLFW key code
     * @return Human-readable name
     */
    public static String getKeyName(int keyCode) {
        // Mouse buttons (negative values)
        if (keyCode < 0) {
            int button = convertToGlfwMouseButton(keyCode);
            return switch (button) {
                case GLFW_MOUSE_BUTTON_LEFT -> "Left Mouse";
                case GLFW_MOUSE_BUTTON_RIGHT -> "Right Mouse";
                case GLFW_MOUSE_BUTTON_MIDDLE -> "Middle Mouse";
                default -> "Mouse " + (button + 1);
            };
        }
        
        // Keyboard keys
        return switch (keyCode) {
            case GLFW_KEY_SPACE -> "Space";
            case GLFW_KEY_APOSTROPHE -> "'";
            case GLFW_KEY_COMMA -> ",";
            case GLFW_KEY_MINUS -> "-";
            case GLFW_KEY_PERIOD -> ".";
            case GLFW_KEY_SLASH -> "/";
            case GLFW_KEY_0 -> "0";
            case GLFW_KEY_1 -> "1";
            case GLFW_KEY_2 -> "2";
            case GLFW_KEY_3 -> "3";
            case GLFW_KEY_4 -> "4";
            case GLFW_KEY_5 -> "5";
            case GLFW_KEY_6 -> "6";
            case GLFW_KEY_7 -> "7";
            case GLFW_KEY_8 -> "8";
            case GLFW_KEY_9 -> "9";
            case GLFW_KEY_SEMICOLON -> ";";
            case GLFW_KEY_EQUAL -> "=";
            case GLFW_KEY_A -> "A";
            case GLFW_KEY_B -> "B";
            case GLFW_KEY_C -> "C";
            case GLFW_KEY_D -> "D";
            case GLFW_KEY_E -> "E";
            case GLFW_KEY_F -> "F";
            case GLFW_KEY_G -> "G";
            case GLFW_KEY_H -> "H";
            case GLFW_KEY_I -> "I";
            case GLFW_KEY_J -> "J";
            case GLFW_KEY_K -> "K";
            case GLFW_KEY_L -> "L";
            case GLFW_KEY_M -> "M";
            case GLFW_KEY_N -> "N";
            case GLFW_KEY_O -> "O";
            case GLFW_KEY_P -> "P";
            case GLFW_KEY_Q -> "Q";
            case GLFW_KEY_R -> "R";
            case GLFW_KEY_S -> "S";
            case GLFW_KEY_T -> "T";
            case GLFW_KEY_U -> "U";
            case GLFW_KEY_V -> "V";
            case GLFW_KEY_W -> "W";
            case GLFW_KEY_X -> "X";
            case GLFW_KEY_Y -> "Y";
            case GLFW_KEY_Z -> "Z";
            case GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW_KEY_BACKSLASH -> "\\";
            case GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW_KEY_ESCAPE -> "Escape";
            case GLFW_KEY_ENTER -> "Enter";
            case GLFW_KEY_TAB -> "Tab";
            case GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW_KEY_INSERT -> "Insert";
            case GLFW_KEY_DELETE -> "Delete";
            case GLFW_KEY_RIGHT -> "Right Arrow";
            case GLFW_KEY_LEFT -> "Left Arrow";
            case GLFW_KEY_DOWN -> "Down Arrow";
            case GLFW_KEY_UP -> "Up Arrow";
            case GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW_KEY_PAGE_DOWN -> "Page Down";
            case GLFW_KEY_HOME -> "Home";
            case GLFW_KEY_END -> "End";
            case GLFW_KEY_CAPS_LOCK -> "Caps Lock";
            case GLFW_KEY_SCROLL_LOCK -> "Scroll Lock";
            case GLFW_KEY_NUM_LOCK -> "Num Lock";
            case GLFW_KEY_PRINT_SCREEN -> "Print Screen";
            case GLFW_KEY_PAUSE -> "Pause";
            case GLFW_KEY_F1 -> "F1";
            case GLFW_KEY_F2 -> "F2";
            case GLFW_KEY_F3 -> "F3";
            case GLFW_KEY_F4 -> "F4";
            case GLFW_KEY_F5 -> "F5";
            case GLFW_KEY_F6 -> "F6";
            case GLFW_KEY_F7 -> "F7";
            case GLFW_KEY_F8 -> "F8";
            case GLFW_KEY_F9 -> "F9";
            case GLFW_KEY_F10 -> "F10";
            case GLFW_KEY_F11 -> "F11";
            case GLFW_KEY_F12 -> "F12";
            case GLFW_KEY_LEFT_SHIFT -> "Left Shift";
            case GLFW_KEY_LEFT_CONTROL -> "Left Ctrl";
            case GLFW_KEY_LEFT_ALT -> "Left Alt";
            case GLFW_KEY_LEFT_SUPER -> "Left Super";
            case GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
            case GLFW_KEY_RIGHT_CONTROL -> "Right Ctrl";
            case GLFW_KEY_RIGHT_ALT -> "Right Alt";
            case GLFW_KEY_RIGHT_SUPER -> "Right Super";
            default -> "Key " + keyCode;
        };
    }
}
