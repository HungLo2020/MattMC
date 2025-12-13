/*
 * Compatibility shim for ConfigBase
 * This bridges the old wrapper code API to the new Config class
 */
package com.seibel.distanthorizons.core.config;

import com.seibel.distanthorizons.core.config.types.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility class for old wrapper code that expects ConfigBase
 * The new API uses Config class directly, but old wrapper code expects ConfigBase.INSTANCE
 */
public class ConfigBase
{
    public static ConfigBase INSTANCE;
    
    public final List<AbstractConfigType<?, ?>> entries = new ArrayList<>();
    public ConfigFile configFileINSTANCE;
    
    public ConfigBase(String modId, String modName, Class<?> configClass, int configFileVersion) {
        // Initialize with empty list - entries will be populated by the new Config system
        this.configFileINSTANCE = new ConfigFile(modId);
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
