package mattmc.client.settings;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Utility class for converting between GLFW key codes and human-readable key names.
 * Supports keyboard keys, mouse buttons, and special keys.
 */
public final class KeyNameParser {
    
    private KeyNameParser() {} // Prevent instantiation
    
    /**
     * Parse a human-readable key name to GLFW key code.
     * @param keyName Human-readable key name (e.g., "w", "space", "left_mouse")
     * @return GLFW key code, or null if not recognized
     */
    public static Integer parseKeyName(String keyName) {
        keyName = keyName.toLowerCase().trim();
        
        // Mouse buttons (use negative values)
        if (keyName.equals("left_mouse")) return -(GLFW_MOUSE_BUTTON_LEFT + 1);
        if (keyName.equals("right_mouse")) return -(GLFW_MOUSE_BUTTON_RIGHT + 1);
        if (keyName.equals("middle_mouse")) return -(GLFW_MOUSE_BUTTON_MIDDLE + 1);
        
        // Special keys
        if (keyName.equals("space")) return GLFW_KEY_SPACE;
        if (keyName.equals("enter")) return GLFW_KEY_ENTER;
        if (keyName.equals("tab")) return GLFW_KEY_TAB;
        if (keyName.equals("backspace")) return GLFW_KEY_BACKSPACE;
        if (keyName.equals("escape")) return GLFW_KEY_ESCAPE;
        if (keyName.equals("left_shift")) return GLFW_KEY_LEFT_SHIFT;
        if (keyName.equals("right_shift")) return GLFW_KEY_RIGHT_SHIFT;
        if (keyName.equals("left_ctrl")) return GLFW_KEY_LEFT_CONTROL;
        if (keyName.equals("right_ctrl")) return GLFW_KEY_RIGHT_CONTROL;
        if (keyName.equals("left_alt")) return GLFW_KEY_LEFT_ALT;
        if (keyName.equals("right_alt")) return GLFW_KEY_RIGHT_ALT;
        if (keyName.equals("left_super")) return GLFW_KEY_LEFT_SUPER;
        if (keyName.equals("right_super")) return GLFW_KEY_RIGHT_SUPER;
        
        // Arrow keys
        if (keyName.equals("up")) return GLFW_KEY_UP;
        if (keyName.equals("down")) return GLFW_KEY_DOWN;
        if (keyName.equals("left")) return GLFW_KEY_LEFT;
        if (keyName.equals("right")) return GLFW_KEY_RIGHT;
        
        // Function keys
        if (keyName.matches("f\\d+")) {
            int num = Integer.parseInt(keyName.substring(1));
            if (num >= 1 && num <= 12) {
                return GLFW_KEY_F1 + (num - 1);
            }
        }
        
        // Number keys
        if (keyName.length() == 1 && keyName.charAt(0) >= '0' && keyName.charAt(0) <= '9') {
            return GLFW_KEY_0 + (keyName.charAt(0) - '0');
        }
        
        // Letter keys
        if (keyName.length() == 1 && keyName.charAt(0) >= 'a' && keyName.charAt(0) <= 'z') {
            return GLFW_KEY_A + (keyName.charAt(0) - 'a');
        }
        
        return null;
    }
    
    /**
     * Convert GLFW key code to human-readable key name.
     * @param keyCode GLFW key code
     * @return Human-readable key name
     */
    public static String getKeyName(int keyCode) {
        // Mouse buttons (negative values)
        if (keyCode < 0) {
            int button = -keyCode - 1;
            if (button == GLFW_MOUSE_BUTTON_LEFT) return "left_mouse";
            if (button == GLFW_MOUSE_BUTTON_RIGHT) return "right_mouse";
            if (button == GLFW_MOUSE_BUTTON_MIDDLE) return "middle_mouse";
            return "mouse_" + (button + 1);
        }
        
        // Special keys
        if (keyCode == GLFW_KEY_SPACE) return "space";
        if (keyCode == GLFW_KEY_ENTER) return "enter";
        if (keyCode == GLFW_KEY_TAB) return "tab";
        if (keyCode == GLFW_KEY_BACKSPACE) return "backspace";
        if (keyCode == GLFW_KEY_ESCAPE) return "escape";
        if (keyCode == GLFW_KEY_LEFT_SHIFT) return "left_shift";
        if (keyCode == GLFW_KEY_RIGHT_SHIFT) return "right_shift";
        if (keyCode == GLFW_KEY_LEFT_CONTROL) return "left_ctrl";
        if (keyCode == GLFW_KEY_RIGHT_CONTROL) return "right_ctrl";
        if (keyCode == GLFW_KEY_LEFT_ALT) return "left_alt";
        if (keyCode == GLFW_KEY_RIGHT_ALT) return "right_alt";
        if (keyCode == GLFW_KEY_LEFT_SUPER) return "left_super";
        if (keyCode == GLFW_KEY_RIGHT_SUPER) return "right_super";
        
        // Arrow keys
        if (keyCode == GLFW_KEY_UP) return "up";
        if (keyCode == GLFW_KEY_DOWN) return "down";
        if (keyCode == GLFW_KEY_LEFT) return "left";
        if (keyCode == GLFW_KEY_RIGHT) return "right";
        
        // Function keys
        if (keyCode >= GLFW_KEY_F1 && keyCode <= GLFW_KEY_F12) {
            return "f" + (keyCode - GLFW_KEY_F1 + 1);
        }
        
        // Number keys
        if (keyCode >= GLFW_KEY_0 && keyCode <= GLFW_KEY_9) {
            return String.valueOf((char)('0' + (keyCode - GLFW_KEY_0)));
        }
        
        // Letter keys
        if (keyCode >= GLFW_KEY_A && keyCode <= GLFW_KEY_Z) {
            return String.valueOf((char)('a' + (keyCode - GLFW_KEY_A)));
        }
        
        // Fall back to numeric representation
        return String.valueOf(keyCode);
    }
}
