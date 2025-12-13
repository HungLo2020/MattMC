package net.minecraft.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Fabric mod initialization using Java ServiceLoader
 */
public class ModInitializationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModInitializationManager.class);
    private static final ModInitializationManager INSTANCE = new ModInitializationManager();
    
    private final List<ModInitializer> commonInitializers = new ArrayList<>();
    private final List<ClientModInitializer> clientInitializers = new ArrayList<>();
    private final List<DedicatedServerModInitializer> serverInitializers = new ArrayList<>();
    
    private boolean commonInitialized = false;
    private boolean clientInitialized = false;
    private boolean serverInitialized = false;
    
    private ModInitializationManager() {
        // Load mod initializers via ServiceLoader
        loadInitializers();
    }
    
    public static ModInitializationManager getInstance() {
        return INSTANCE;
    }
    
    private void loadInitializers() {
        // Load common initializers
        for (ModInitializer initializer : ServiceLoader.load(ModInitializer.class)) {
            commonInitializers.add(initializer);
            LOGGER.info("Loaded Fabric mod initializer: {}", initializer.getClass().getName());
        }
        
        // Load client initializers
        for (ClientModInitializer initializer : ServiceLoader.load(ClientModInitializer.class)) {
            clientInitializers.add(initializer);
            LOGGER.info("Loaded Fabric client mod initializer: {}", initializer.getClass().getName());
        }
        
        // Load server initializers
        for (DedicatedServerModInitializer initializer : ServiceLoader.load(DedicatedServerModInitializer.class)) {
            serverInitializers.add(initializer);
            LOGGER.info("Loaded Fabric server mod initializer: {}", initializer.getClass().getName());
        }
    }
    
    public void initializeCommonMods() {
        if (commonInitialized) {
            return;
        }
        
        LOGGER.info("Initializing {} Fabric common mod(s)", commonInitializers.size());
        for (ModInitializer initializer : commonInitializers) {
            try {
                initializer.onInitialize();
                LOGGER.info("Initialized Fabric mod: {}", initializer.getClass().getName());
            } catch (Exception e) {
                LOGGER.error("Failed to initialize Fabric mod: {}", initializer.getClass().getName(), e);
            }
        }
        commonInitialized = true;
    }
    
    public void initializeClientMods() {
        if (clientInitialized) {
            return;
        }
        
        // Initialize common mods first
        initializeCommonMods();
        
        LOGGER.info("Initializing {} Fabric client mod(s)", clientInitializers.size());
        for (ClientModInitializer initializer : clientInitializers) {
            try {
                initializer.onInitializeClient();
                LOGGER.info("Initialized Fabric client mod: {}", initializer.getClass().getName());
            } catch (Exception e) {
                LOGGER.error("Failed to initialize Fabric client mod: {}", initializer.getClass().getName(), e);
            }
        }
        clientInitialized = true;
    }
    
    public void initializeServerMods() {
        if (serverInitialized) {
            return;
        }
        
        // Initialize common mods first
        initializeCommonMods();
        
        LOGGER.info("Initializing {} Fabric server mod(s)", serverInitializers.size());
        for (DedicatedServerModInitializer initializer : serverInitializers) {
            try {
                initializer.onInitializeServer();
                LOGGER.info("Initialized Fabric server mod: {}", initializer.getClass().getName());
            } catch (Exception e) {
                LOGGER.error("Failed to initialize Fabric server mod: {}", initializer.getClass().getName(), e);
            }
        }
        serverInitialized = true;
    }
}
