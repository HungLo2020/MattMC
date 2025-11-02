package MattMC.util;

import MattMC.player.PlayerInput;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading and saving keybinds from the Options.txt file.
 */
public class KeybindManager {
    private static final String OPTIONS_FILE = "Options.txt";
    
    /**
     * Load keybinds from the Options.txt file.
     * If the file doesn't exist, it will be created with default values.
     */
    public static void loadKeybinds() {
        Path optionsPath = getOptionsPath();
        
        if (!Files.exists(optionsPath)) {
            System.out.println("Options.txt not found, creating with defaults...");
            saveKeybinds();
            return;
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
                    try {
                        int keyCode = Integer.parseInt(parts[1].trim());
                        keybinds.put(action, keyCode);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid key code for action " + action + ": " + parts[1]);
                    }
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
     * Save current keybinds to the Options.txt file.
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
                writer.write("# Keybinds (format: action=key_code)\n");
                writer.write("# Key codes are GLFW key constants\n");
                writer.write("# Negative values for mouse buttons: -1=Left, -2=Right, -3=Middle\n");
                writer.write("\n");
                
                // Write all keybinds
                Map<String, Integer> keybinds = PlayerInput.getInstance().getAllKeybinds();
                for (Map.Entry<String, Integer> entry : keybinds.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
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
     * Get the path to the Options.txt file.
     * Looks in the current directory first, then in the user's home directory.
     */
    private static Path getOptionsPath() {
        // Try current directory first (for development/running from IDE)
        Path currentDir = Paths.get(OPTIONS_FILE);
        if (Files.exists(currentDir)) {
            return currentDir;
        }
        
        // Try application directory (for packaged releases)
        Path appDir = Paths.get(System.getProperty("user.dir"), OPTIONS_FILE);
        if (Files.exists(appDir)) {
            return appDir;
        }
        
        // Default to current directory for creation
        return currentDir;
    }
}
