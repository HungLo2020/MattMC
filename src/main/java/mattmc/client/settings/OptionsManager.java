package mattmc.client.settings;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages game options/settings (excluding keybinds).
 */
public class OptionsManager {
    private static final Logger logger = LoggerFactory.getLogger(OptionsManager.class);

    private static final String OPTIONS_FILE = "Options.txt";
    private static final String DEFAULT_OPTIONS_RESOURCE = "/config/DefaultOptions.txt";
    private static final int MIN_FPS_CAP = 30;
    private static final int MAX_FPS_CAP = 999;
    private static final int MIN_RENDER_DISTANCE = 2;
    private static final int MAX_RENDER_DISTANCE = 64;
    private static final int DEFAULT_RENDER_DISTANCE = 16;
    
    // Allowed render distance values (powers of 2 from 2 to 64)
    public static final int[] ALLOWED_RENDER_DISTANCES = {2, 4, 8, 16, 32, 64};
    
    // Blur settings
    private static boolean titleScreenBlurEnabled = false;  // Default: disabled for title screen
    private static boolean menuScreenBlurEnabled = true;    // Default: enabled for other screens
    
    // FPS cap setting
    private static int fpsCapValue = 60;  // Default: 60 FPS
    
    // Resolution settings
    private static int resolutionWidth = 1280;  // Default: 1280x720
    private static int resolutionHeight = 720;
    
    // Fullscreen setting
    private static boolean fullscreenEnabled = false;  // Default: windowed mode
    
    // Render distance setting
    private static int renderDistance = DEFAULT_RENDER_DISTANCE;  // Default: 16 chunks
    
    // Mipmap setting (0 = off, 1-4 = mipmap levels)
    private static int mipmapLevel = 4;  // Default: 4
    
    // Anisotropic filtering setting (0 = off, 2/4/8/16 = filtering level)
    private static int anisotropicFiltering = 16;  // Default: 16
    
    // Allowed mipmap levels
    public static final int[] ALLOWED_MIPMAP_LEVELS = {0, 1, 2, 3, 4};
    
    // Allowed anisotropic filtering levels
    public static final int[] ALLOWED_ANISOTROPIC_LEVELS = {0, 2, 4, 8, 16};
    
    /**
     * Validate and clamp FPS cap value to valid range.
     */
    private static int validateFpsCap(int fps) {
        return Math.max(MIN_FPS_CAP, Math.min(MAX_FPS_CAP, fps));
    }
    
    /**
     * Validate and clamp render distance value to valid range.
     */
    private static int validateRenderDistance(int distance) {
        return Math.max(MIN_RENDER_DISTANCE, Math.min(MAX_RENDER_DISTANCE, distance));
    }
    
    /**
     * Validate mipmap level (0=off, 1-4).
     */
    private static int validateMipmapLevel(int level) {
        if (level < 0) return 0;
        if (level > 4) return 4;
        return level;
    }
    
    /**
     * Validate anisotropic filtering level (0=off, or one of 2,4,8,16).
     */
    private static int validateAnisotropicLevel(int level) {
        if (level <= 0) return 0;
        
        // Find closest valid level
        int closestLevel = ALLOWED_ANISOTROPIC_LEVELS[1]; // Start with 2
        int minDifference = Math.abs(level - closestLevel);
        
        for (int validLevel : ALLOWED_ANISOTROPIC_LEVELS) {
            if (validLevel == 0) continue; // Skip 0, already handled above
            int difference = Math.abs(level - validLevel);
            if (difference < minDifference) {
                minDifference = difference;
                closestLevel = validLevel;
            }
        }
        
        return closestLevel;
    }
    
    /**
     * Load options from the Options.txt file.
     */
    public static void loadOptions() {
        Path optionsPath = getOptionsPath();
        
        if (!Files.exists(optionsPath)) {
            logger.info("Options.txt not found for loading settings");
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
                            fpsCapValue = validateFpsCap(fps);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid FPS cap value: {}", value);
                        }
                    } else if (key.equals("resolution")) {
                        try {
                            String[] resParts = value.split("x");
                            if (resParts.length == 2) {
                                resolutionWidth = Integer.parseInt(resParts[0].trim());
                                resolutionHeight = Integer.parseInt(resParts[1].trim());
                            }
                        } catch (NumberFormatException e) {
                            logger.error("Invalid resolution value: {}", value);
                        }
                    } else if (key.equals("fullscreen")) {
                        fullscreenEnabled = Boolean.parseBoolean(value);
                    } else if (key.equals("render_distance")) {
                        try {
                            int distance = Integer.parseInt(value);
                            renderDistance = validateRenderDistance(distance);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid render distance value: {}", value);
                        }
                    } else if (key.equals("mipmaps")) {
                        try {
                            int level = Integer.parseInt(value);
                            mipmapLevel = validateMipmapLevel(level);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid mipmap level value: {}", value);
                        }
                    } else if (key.equals("anisotropic_filtering")) {
                        try {
                            int level = Integer.parseInt(value);
                            anisotropicFiltering = validateAnisotropicLevel(level);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid anisotropic filtering value: {}", value);
                        }
                    }
                }
            }
            
            logger.info("Loaded options: title blur={}, menu blur={}", titleScreenBlurEnabled, menuScreenBlurEnabled);
        } catch (IOException e) {
            logger.error("Error loading options: {}", e.getMessage());
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
            
            // Update resolution setting
            options.put("resolution", resolutionWidth + "x" + resolutionHeight);
            
            // Update fullscreen setting
            options.put("fullscreen", String.valueOf(fullscreenEnabled));
            
            // Update render distance setting
            options.put("render_distance", String.valueOf(renderDistance));
            
            // Update mipmap setting
            options.put("mipmaps", String.valueOf(mipmapLevel));
            
            // Update anisotropic filtering setting
            options.put("anisotropic_filtering", String.valueOf(anisotropicFiltering));
            
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
                
                // Write resolution setting
                writer.write("# Resolution (format: widthxheight)\n");
                writer.write("# Supported resolutions: 1280x720, 1600x900, 1920x1080\n");
                writer.write("resolution=" + resolutionWidth + "x" + resolutionHeight + "\n");
                writer.write("\n");
                
                // Write fullscreen setting
                writer.write("# Fullscreen mode\n");
                writer.write("fullscreen=" + fullscreenEnabled + "\n");
                writer.write("\n");
                
                // Write render distance setting
                writer.write("# Render distance in chunks (2-64)\n");
                writer.write("# Available values: 2, 4, 8, 16, 32, 64\n");
                writer.write("render_distance=" + renderDistance + "\n");
                writer.write("\n");
                
                // Write mipmap setting
                writer.write("# Mipmaps (off, 1, 2, 3, 4)\n");
                writer.write("mipmaps=" + mipmapLevel + "\n");
                writer.write("\n");
                
                // Write anisotropic filtering setting
                writer.write("# Anisotropic filtering (off, 2, 4, 8, 16)\n");
                writer.write("anisotropic_filtering=" + anisotropicFiltering + "\n");
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
                
                logger.info("Saved options to Options.txt");
            }
        } catch (IOException e) {
            logger.error("Error saving options: {}", e.getMessage(), e);
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
        fpsCapValue = validateFpsCap(fps);
        saveOptions();
    }
    
    // Resolution getters and setters
    public static int getResolutionWidth() {
        return resolutionWidth;
    }
    
    public static int getResolutionHeight() {
        return resolutionHeight;
    }
    
    public static void setResolution(int width, int height) {
        if (width <= 0 || height <= 0) {
            logger.error("Invalid resolution: {}x{}. Width and height must be positive.", width, height);
            return;
        }
        resolutionWidth = width;
        resolutionHeight = height;
        saveOptions();
    }
    
    /**
     * Get the resolution as a formatted string (e.g., "1280x720")
     */
    public static String getResolutionString() {
        return resolutionWidth + "x" + resolutionHeight;
    }
    
    // Fullscreen getters and setters
    public static boolean isFullscreenEnabled() {
        return fullscreenEnabled;
    }
    
    public static void setFullscreenEnabled(boolean enabled) {
        fullscreenEnabled = enabled;
        saveOptions();
    }
    
    public static void toggleFullscreen() {
        fullscreenEnabled = !fullscreenEnabled;
        saveOptions();
    }
    
    // Render distance getters and setters
    public static int getRenderDistance() {
        return renderDistance;
    }
    
    public static void setRenderDistance(int distance) {
        renderDistance = validateRenderDistance(distance);
        saveOptions();
    }
    
    // Mipmap level getters and setters
    public static int getMipmapLevel() {
        return mipmapLevel;
    }
    
    public static void setMipmapLevel(int level) {
        mipmapLevel = validateMipmapLevel(level);
        saveOptions();
    }
    
    // Anisotropic filtering getters and setters
    public static int getAnisotropicFiltering() {
        return anisotropicFiltering;
    }
    
    public static void setAnisotropicFiltering(int level) {
        anisotropicFiltering = validateAnisotropicLevel(level);
        saveOptions();
    }
}
