package mattmc.client.settings;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages game options/settings (excluding keybinds).
 */
public class OptionsManager {
    private static final String OPTIONS_FILE = "Options.txt";
    private static final String DEFAULT_OPTIONS_RESOURCE = "/config/DefaultOptions.txt";
    
    // Blur settings
    private static boolean titleScreenBlurEnabled = false;  // Default: disabled for title screen
    private static boolean menuScreenBlurEnabled = true;    // Default: enabled for other screens
    
    // FPS cap setting
    private static int fpsCapValue = 60;  // Default: 60 FPS
    
    /**
     * Load options from the Options.txt file.
     */
    public static void loadOptions() {
        Path optionsPath = getOptionsPath();
        
        if (!Files.exists(optionsPath)) {
            System.out.println("Options.txt not found for loading settings");
            return;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(optionsPath)) {
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
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    // Parse blur settings
                    if (key.equals("blur_title_screen")) {
                        titleScreenBlurEnabled = Boolean.parseBoolean(value);
                    } else if (key.equals("blur_menu_screens")) {
                        menuScreenBlurEnabled = Boolean.parseBoolean(value);
                    } else if (key.equals("fps_cap")) {
                        try {
                            int fps = Integer.parseInt(value);
                            // Clamp to valid range: 30-999
                            fpsCapValue = Math.max(30, Math.min(999, fps));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid FPS cap value: " + value);
                        }
                    }
                }
            }
            
            System.out.println("Loaded options: title blur=" + titleScreenBlurEnabled + ", menu blur=" + menuScreenBlurEnabled);
        } catch (IOException e) {
            System.err.println("Error loading options: " + e.getMessage());
        }
    }
    
    /**
     * Save current options to the Options.txt file.
     */
    public static void saveOptions() {
        Path optionsPath = getOptionsPath();
        
        try {
            // Read existing file content
            Map<String, String> options = new HashMap<>();
            
            if (Files.exists(optionsPath)) {
                try (BufferedReader reader = Files.newBufferedReader(optionsPath)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            String[] parts = line.split("=", 2);
                            if (parts.length == 2) {
                                options.put(parts[0].trim(), parts[1].trim());
                            }
                        }
                    }
                }
            }
            
            // Update blur settings
            options.put("blur_title_screen", String.valueOf(titleScreenBlurEnabled));
            options.put("blur_menu_screens", String.valueOf(menuScreenBlurEnabled));
            
            // Update FPS cap setting
            options.put("fps_cap", String.valueOf(fpsCapValue));
            
            // Write back to file
            Path parent = optionsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            try (BufferedWriter writer = Files.newBufferedWriter(optionsPath)) {
                writer.write("# MattMC Options File\n");
                writer.write("# This file contains global settings for the game\n");
                writer.write("\n");
                
                // Write blur settings
                writer.write("# Blur settings\n");
                writer.write("blur_title_screen=" + titleScreenBlurEnabled + "\n");
                writer.write("blur_menu_screens=" + menuScreenBlurEnabled + "\n");
                writer.write("\n");
                
                // Write FPS cap setting
                writer.write("# FPS cap (30-999)\n");
                writer.write("fps_cap=" + fpsCapValue + "\n");
                writer.write("\n");
                
                // Write keybinds section header
                writer.write("# Keybinds (format: action=key_name)\n");
                writer.write("# Keyboard keys: a-z, 0-9, space, left_shift, left_ctrl, left_alt, etc.\n");
                writer.write("# Mouse buttons: left_mouse, right_mouse, middle_mouse\n");
                
                // Write all other options (keybinds)
                for (Map.Entry<String, String> entry : options.entrySet()) {
                    String key = entry.getKey();
                    if (!key.equals("blur_title_screen") && !key.equals("blur_menu_screens")) {
                        writer.write(key + "=" + entry.getValue() + "\n");
                    }
                }
                
                System.out.println("Saved options to Options.txt");
            }
        } catch (IOException e) {
            System.err.println("Error saving options: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get the path to the Options.txt file.
     */
    private static Path getOptionsPath() {
        String dataDir = System.getProperty("mattmc.dataDir");
        if (dataDir != null) {
            return Paths.get(dataDir).resolve(OPTIONS_FILE);
        }
        return Paths.get(OPTIONS_FILE);
    }
    
    // Getters and setters for blur settings
    public static boolean isTitleScreenBlurEnabled() {
        return titleScreenBlurEnabled;
    }
    
    public static void setTitleScreenBlurEnabled(boolean enabled) {
        titleScreenBlurEnabled = enabled;
    }
    
    public static boolean isMenuScreenBlurEnabled() {
        return menuScreenBlurEnabled;
    }
    
    public static void setMenuScreenBlurEnabled(boolean enabled) {
        menuScreenBlurEnabled = enabled;
    }
    
    public static void toggleTitleScreenBlur() {
        titleScreenBlurEnabled = !titleScreenBlurEnabled;
        saveOptions();
    }
    
    public static void toggleMenuScreenBlur() {
        menuScreenBlurEnabled = !menuScreenBlurEnabled;
        saveOptions();
    }
    
    // FPS cap getters and setters
    public static int getFpsCap() {
        return fpsCapValue;
    }
    
    public static void setFpsCap(int fps) {
        // Clamp to valid range: 30-999
        fpsCapValue = Math.max(30, Math.min(999, fps));
        saveOptions();
    }
}
