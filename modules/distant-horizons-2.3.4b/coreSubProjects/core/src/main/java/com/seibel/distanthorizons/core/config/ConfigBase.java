/*
 * Compatibility shim for ConfigBase
 * This bridges the old wrapper code API to the new Config class
 */
package com.seibel.distanthorizons.core.config;

import com.seibel.distanthorizons.core.config.types.AbstractConfigType;
import com.seibel.distanthorizons.core.config.types.AbstractConfigBase;
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
    
    /**
     * Returns the config entries from ConfigHandler.
     * This is a view that delegates to ConfigHandler.INSTANCE.configBaseList
     * to ensure entries have proper category/name initialized.
     */
    public final List<AbstractConfigType<?, ?>> entries = new EntriesListView();
    public ConfigFile configFileINSTANCE;
    
    public ConfigBase(String modId, String modName, Class<?> configClass, int configFileVersion) {
        this.modID = modId;
        this.modName = modName;
        this.configFileINSTANCE = new ConfigFile(modId);
        
        // Initialize the config system properly through ConfigHandler
        // This sets category and name on all config entries
        ConfigHandler.tryRunFirstTimeSetup();
    }
    
    /**
     * A view list that delegates to ConfigHandler.INSTANCE.configBaseList
     * filtering only for AbstractConfigType entries.
     */
    private static class EntriesListView extends ArrayList<AbstractConfigType<?, ?>> {
        @Override
        public int size() {
            return (int) ConfigHandler.INSTANCE.configBaseList.stream()
                .filter(e -> e instanceof AbstractConfigType)
                .count();
        }
        
        @Override
        public AbstractConfigType<?, ?> get(int index) {
            int i = 0;
            for (AbstractConfigBase<?> entry : ConfigHandler.INSTANCE.configBaseList) {
                if (entry instanceof AbstractConfigType) {
                    if (i == index) {
                        return (AbstractConfigType<?, ?>) entry;
                    }
                    i++;
                }
            }
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public java.util.Iterator<AbstractConfigType<?, ?>> iterator() {
            List<AbstractConfigType<?, ?>> list = new ArrayList<>();
            for (AbstractConfigBase<?> entry : ConfigHandler.INSTANCE.configBaseList) {
                if (entry instanceof AbstractConfigType) {
                    list.add((AbstractConfigType<?, ?>) entry);
                }
            }
            return list.iterator();
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
            ConfigHandler.INSTANCE.configFileHandler.saveToFile();
        }
        
        public void loadFromFile() {
            ConfigHandler.INSTANCE.configFileHandler.loadFromFile();
        }
    }
}
