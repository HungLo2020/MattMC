package mattmc.client.settings;

import mattmc.world.entity.player.PlayerInput;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages loading and saving keybinds from the Options.txt file.
 */
public class KeybindManager {
    private static final Logger logger = LoggerFactory.getLogger(KeybindManager.class);

    private static final String OPTIONS_FILE = "Options.txt";
    private static final String DEFAULT_OPTIONS_RESOURCE = "/config/DefaultOptions.txt";
    
    /**
     * Load keybinds from the Options.txt file.
     * If the file doesn't exist, it will be copied from DefaultOptions.txt in resources.
     */
    public static void loadKeybinds() {
        Path optionsPath = getOptionsPath();
        
        // Set default keybinds first (fallback for missing entries)
        setDefaultKeybinds();
        
        if (!Files.exists(optionsPath)) {
            logger.info("Options.txt not found, copying from defaults...");
            copyDefaultOptions(optionsPath);
        }
        
        try (BufferedReader reader = Files.newBufferedReader(optionsPath)) {
            Map<String, Integer> keybinds = new HashMap<>();
            String line;
            boolean inKeybindSection = false;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }
                
                // Check if we've reached the keybind section
                if (line.startsWith("# Keybinds")) {
                    inKeybindSection = true;
                    continue;
                }
                
                // Skip all lines until we reach the keybind section
                if (!inKeybindSection) {
                    continue;
                }
                
                // Skip comments within the keybind section
                if (line.startsWith("#")) {
                    continue;
                }
                
                // Parse key=value format
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String action = parts[0].trim();
                    String keyName = parts[1].trim();
                    
                    // Try to parse as human-readable key name first
                    Integer keyCode = KeyNameParser.parseKeyName(keyName);
                    
                    // Fall back to numeric parsing for backward compatibility
                    if (keyCode == null) {
                        try {
                            keyCode = Integer.parseInt(keyName);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid key name for action {}: {}", action, keyName);
                            continue;
                        }
                    }
                    
                    keybinds.put(action, keyCode);
                }
            }
            
            // Apply loaded keybinds to PlayerInput (this will override defaults)
            if (!keybinds.isEmpty()) {
                for (Map.Entry<String, Integer> entry : keybinds.entrySet()) {
                    PlayerInput.getInstance().setKeybind(entry.getKey(), entry.getValue());
                }
                // logger.info("Loaded {} keybinds from Options.txt", keybinds.size());
            }
        } catch (IOException e) {
            logger.error("Error loading keybinds from Options.txt", e);
        }
    }
    
    /**
     * Set default keybinds as fallback for any missing entries in Options.txt.
     */
    private static void setDefaultKeybinds() {
        PlayerInput input = PlayerInput.getInstance();
        input.setKeybind(PlayerInput.FORWARD, GLFW_KEY_W);
        input.setKeybind(PlayerInput.BACKWARD, GLFW_KEY_S);
        input.setKeybind(PlayerInput.LEFT, GLFW_KEY_A);
        input.setKeybind(PlayerInput.RIGHT, GLFW_KEY_D);
        input.setKeybind(PlayerInput.JUMP, GLFW_KEY_SPACE);
        input.setKeybind(PlayerInput.SPRINT, GLFW_KEY_LEFT_SHIFT);
        input.setKeybind(PlayerInput.CROUCH, GLFW_KEY_LEFT_CONTROL);
        input.setKeybind(PlayerInput.BREAK_BLOCK, -(GLFW_MOUSE_BUTTON_LEFT + 1));
        input.setKeybind(PlayerInput.PLACE_BLOCK, -(GLFW_MOUSE_BUTTON_RIGHT + 1));
        input.setKeybind(PlayerInput.FLY_UP, GLFW_KEY_SPACE);
        input.setKeybind(PlayerInput.OPEN_COMMAND, GLFW_KEY_SLASH);
        input.setKeybind(PlayerInput.INVENTORY, GLFW_KEY_E);
        input.setKeybind(PlayerInput.HOTBAR_1, GLFW_KEY_1);
        input.setKeybind(PlayerInput.HOTBAR_2, GLFW_KEY_2);
        input.setKeybind(PlayerInput.HOTBAR_3, GLFW_KEY_3);
        input.setKeybind(PlayerInput.HOTBAR_4, GLFW_KEY_4);
        input.setKeybind(PlayerInput.HOTBAR_5, GLFW_KEY_5);
        input.setKeybind(PlayerInput.HOTBAR_6, GLFW_KEY_6);
        input.setKeybind(PlayerInput.HOTBAR_7, GLFW_KEY_7);
        input.setKeybind(PlayerInput.HOTBAR_8, GLFW_KEY_8);
        input.setKeybind(PlayerInput.HOTBAR_9, GLFW_KEY_9);
        input.setKeybind(PlayerInput.DELETE_ITEM, GLFW_KEY_DELETE);
        logger.debug("Set default keybinds");
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
            InputStream input = mattmc.util.ResourceLoader.getResourceStream(DEFAULT_OPTIONS_RESOURCE);
            if (input == null) {
                logger.error("DefaultOptions.txt not found in resources, creating with hardcoded defaults...");
                saveKeybinds();
                return;
            }
            try (input) {
                Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied DefaultOptions.txt to {}", targetPath);
            }
        } catch (IOException e) {
            logger.error("Error copying default options to {}", targetPath, e);
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
                    String keyName = KeyNameParser.getKeyName(entry.getValue());
                    writer.write(entry.getKey() + "=" + keyName + "\n");
                }
                
                logger.info("Saved {} keybinds to Options.txt", keybinds.size());
            }
        } catch (IOException e) {
            logger.error("Error saving keybinds to Options.txt", e);
        }
    }
    
    /**
     * Reload keybinds from the Options.txt file.
     * This allows keybinds to be changed mid-game without restarting.
     */
    public static void reloadKeybinds() {
        logger.info("Reloading keybinds...");
        loadKeybinds();
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
