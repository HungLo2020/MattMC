package mattmc.client.settings.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Handles loading and saving settings to disk.
 */
public class SettingsStorage {
    private static final Logger logger = LoggerFactory.getLogger(SettingsStorage.class);
    private static final String SETTINGS_FILE = "settings.properties";
    
    /**
     * Load settings from disk.
     * @return Map of setting keys to values, or default settings if file doesn't exist
     */
    public Map<String, String> load() {
        Path settingsPath = Paths.get(SETTINGS_FILE);
        Map<String, String> settings = new HashMap<>();
        
        if (Files.exists(settingsPath)) {
            try (InputStream input = new FileInputStream(settingsPath.toFile())) {
                Properties props = new Properties();
                props.load(input);
                
                for (String key : props.stringPropertyNames()) {
                    settings.put(key, props.getProperty(key));
                }
                
                logger.info("Loaded settings from {}", SETTINGS_FILE);
            } catch (IOException e) {
                logger.error("Failed to load settings: {}", e.getMessage());
                return getDefaultSettings();
            }
        } else {
            logger.info("Settings file not found, using defaults");
            return getDefaultSettings();
        }
        
        return settings;
    }
    
    /**
     * Save settings to disk.
     * @param settings Map of setting keys to values
     */
    public void save(Map<String, String> settings) {
        Path settingsPath = Paths.get(SETTINGS_FILE);
        
        try (OutputStream output = new FileOutputStream(settingsPath.toFile())) {
            Properties props = new Properties();
            
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                props.setProperty(entry.getKey(), entry.getValue());
            }
            
            props.store(output, "MattMC Settings");
            logger.info("Saved settings to {}", SETTINGS_FILE);
        } catch (IOException e) {
            logger.error("Failed to save settings: {}", e.getMessage());
        }
    }
    
    /**
     * Get default settings.
     */
    public Map<String, String> getDefaultSettings() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("fpsCapValue", "0"); // Unlimited
        defaults.put("renderDistance", "8");
        defaults.put("fullscreenEnabled", "false");
        defaults.put("resolutionWidth", "1280");
        defaults.put("resolutionHeight", "720");
        defaults.put("titleScreenBlurEnabled", "true");
        defaults.put("menuScreenBlurEnabled", "true");
        defaults.put("mipmapLevel", "4");
        defaults.put("anisotropicFiltering", "4");
        return defaults;
    }
}
