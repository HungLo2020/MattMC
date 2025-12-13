package com.terraformersmc.modmenu.api;

import java.util.Map;

/**
 * ModMenu API stub for ModMenuApi
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }
    
    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return Map.of();
    }
}
