/*
 * Compatibility shim for ConfigBase
 * This bridges the old wrapper code API to the new Config class
 */
package com.seibel.distanthorizons.core.config;

import com.seibel.distanthorizons.core.config.types.AbstractConfigType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility class for old wrapper code that expects ConfigBase
 * The new API uses Config class directly, but old wrapper code expects ConfigBase.INSTANCE
 */
public class ConfigBase
{
    public static ConfigBase INSTANCE;
    
    public final String modID;
    public final String modName;
    
    public final List<AbstractConfigType<?, ?>> entries = new ArrayList<>();
    public ConfigFile configFileINSTANCE;
    
    public ConfigBase(String modId, String modName, Class<?> configClass, int configFileVersion) {
        this.modID = modId;
        this.modName = modName;
        this.configFileINSTANCE = new ConfigFile(modId);
        
        // Scan the config class to collect all AbstractConfigType entries
        collectConfigEntries(configClass, entries);
    }
    
    /**
     * Recursively scans a class and its inner classes for AbstractConfigType fields
     */
    private static void collectConfigEntries(Class<?> clazz, List<AbstractConfigType<?, ?>> entries) {
        // Scan all static fields of this class
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof AbstractConfigType<?, ?>) {
                        entries.add((AbstractConfigType<?, ?>) value);
                    }
                } catch (IllegalAccessException | SecurityException e) {
                    // These exceptions are expected for some fields - ignore silently
                }
            }
        }
        
        // Recursively scan inner classes
        for (Class<?> innerClass : clazz.getDeclaredClasses()) {
            collectConfigEntries(innerClass, entries);
        }
    }
    
    /**
     * Stub for language generation
     */
    public String generateLang(boolean b1, boolean b2) {
        return "";
    }
    
    /**
     * Inner class for config file handling
     */
    public static class ConfigFile {
        private final String modId;
        
        public ConfigFile(String modId) {
            this.modId = modId;
        }
        
        public void saveToFile() {
            // Delegate to new config system - stub for compatibility
        }
        
        public void loadFromFile() {
            // Delegate to new config system - stub for compatibility
        }
    }
}
