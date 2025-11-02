package MattMC.util;

import MattMC.player.PlayerInput;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Manages loading and saving keybinds from the Options.txt file.
 */
public class KeybindManager {
    private static final String OPTIONS_FILE = "Options.txt";
    private static final String DEFAULT_OPTIONS_RESOURCE = "/config/DefaultOptions.txt";
    
    /**
     * Load keybinds from the Options.txt file.
     * If the file doesn't exist, it will be copied from DefaultOptions.txt in resources.
     */
    public static void loadKeybinds() {
        Path optionsPath = getOptionsPath();
        
        if (!Files.exists(optionsPath)) {
            System.out.println("Options.txt not found, copying from defaults...");
            copyDefaultOptions(optionsPath);
        }
        
        try (BufferedReader reader = Files.newBufferedReader(optionsPath)) {
            Map<String, Integer> keybinds = new HashMap<>();
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse key=value format
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String action = parts[0].trim();
                    String keyName = parts[1].trim();
                    
                    // Try to parse as human-readable key name first
                    Integer keyCode = parseKeyName(keyName);
                    
                    // Fall back to numeric parsing for backward compatibility
                    if (keyCode == null) {
                        try {
                            keyCode = Integer.parseInt(keyName);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid key name for action " + action + ": " + keyName);
                            continue;
                        }
                    }
                    
                    keybinds.put(action, keyCode);
                }
            }
            
            // Apply loaded keybinds to PlayerInput
            if (!keybinds.isEmpty()) {
                PlayerInput.getInstance().setAllKeybinds(keybinds);
                System.out.println("Loaded " + keybinds.size() + " keybinds from Options.txt");
            }
        } catch (IOException e) {
            System.err.println("Error loading keybinds: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Copy DefaultOptions.txt from resources to the target path.
     */
    private static void copyDefaultOptions(Path targetPath) {
        try {
            // Ensure parent directory exists
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            // Copy from resources
            try (InputStream input = KeybindManager.class.getResourceAsStream(DEFAULT_OPTIONS_RESOURCE)) {
                if (input == null) {
                    System.err.println("DefaultOptions.txt not found in resources, creating with hardcoded defaults...");
                    saveKeybinds();
                    return;
                }
                Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Copied DefaultOptions.txt to " + targetPath);
            }
        } catch (IOException e) {
            System.err.println("Error copying default options: " + e.getMessage());
            e.printStackTrace();
            // Fall back to saving current keybinds
            saveKeybinds();
        }
    }
    
    /**
     * Save current keybinds to the Options.txt file in human-readable format.
     */
    public static void saveKeybinds() {
        Path optionsPath = getOptionsPath();
        
        try {
            // Ensure parent directory exists (if there is one)
            Path parent = optionsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            try (BufferedWriter writer = Files.newBufferedWriter(optionsPath)) {
                writer.write("# MattMC Options File\n");
                writer.write("# This file contains global settings for the game\n");
                writer.write("\n");
                writer.write("# Keybinds (format: action=key_name)\n");
                writer.write("# Keyboard keys: a-z, 0-9, space, left_shift, left_ctrl, left_alt, etc.\n");
                writer.write("# Mouse buttons: left_mouse, right_mouse, middle_mouse\n");
                writer.write("\n");
                
                // Write all keybinds in human-readable format
                Map<String, Integer> keybinds = PlayerInput.getInstance().getAllKeybinds();
                for (Map.Entry<String, Integer> entry : keybinds.entrySet()) {
                    String keyName = getKeyName(entry.getValue());
                    writer.write(entry.getKey() + "=" + keyName + "\n");
                }
                
                System.out.println("Saved " + keybinds.size() + " keybinds to Options.txt");
            }
        } catch (IOException e) {
            System.err.println("Error saving keybinds: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Reload keybinds from the Options.txt file.
     * This allows keybinds to be changed mid-game without restarting.
     */
    public static void reloadKeybinds() {
        System.out.println("Reloading keybinds...");
        loadKeybinds();
    }
    
    /**
     * Parse a human-readable key name to GLFW key code.
     * @param keyName Human-readable key name (e.g., "w", "space", "left_mouse")
     * @return GLFW key code, or null if not recognized
     */
    private static Integer parseKeyName(String keyName) {
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
    private static String getKeyName(int keyCode) {
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
    
    /**
     * Get the path to the Options.txt file.
     * Uses the MattMC data directory.
     */
    private static Path getOptionsPath() {
        // Get the data directory from system property (set in Main)
        String dataDir = System.getProperty("mattmc.dataDir");
        if (dataDir != null) {
            Path dataDirPath = Paths.get(dataDir);
            return dataDirPath.resolve(OPTIONS_FILE);
        }
        
        // Fall back to current directory
        return Paths.get(OPTIONS_FILE);
    }
}
