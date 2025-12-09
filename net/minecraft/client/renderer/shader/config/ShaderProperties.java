package net.minecraft.client.renderer.shader.config;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents parsed shader properties from shaders.properties file.
 */
@Environment(EnvType.CLIENT)
public class ShaderProperties {
    private final Map<String, String> settings;
    
    public ShaderProperties(Map<String, String> settings) {
        this.settings = new HashMap<>(settings);
    }
    
    /**
     * Gets a property value.
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(settings.get(key));
    }
    
    /**
     * Gets a property value with a default.
     */
    public String get(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }
    
    /**
     * Gets an integer property value.
     */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(settings.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Gets a boolean property value.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = settings.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Gets all property keys.
     */
    public Iterable<String> getKeys() {
        return settings.keySet();
    }
}
