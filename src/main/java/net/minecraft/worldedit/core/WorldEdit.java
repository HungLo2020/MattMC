package net.minecraft.worldedit.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.session.SessionManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * The main entry point and singleton for WorldEdit.
 * This class manages all WorldEdit subsystems and serves as the central coordination point.
 */
public final class WorldEdit {
    private static WorldEdit instance;
    
    private final MattMCPlatform platform;
    private final SessionManager sessionManager;
    private final Map<String, Object> configuration;
    
    private WorldEdit(MinecraftServer server) {
        this.platform = new MattMCPlatform(server);
        this.sessionManager = new SessionManager(this);
        this.configuration = new ConcurrentHashMap<>();
        
        // Set default configuration
        initializeDefaultConfiguration();
    }
    
    /**
     * Initialize WorldEdit. Should be called during server startup.
     */
    public static synchronized void initialize(MinecraftServer server) {
        if (instance != null) {
            throw new IllegalStateException("WorldEdit already initialized");
        }
        instance = new WorldEdit(server);
    }
    
    /**
     * Get the WorldEdit instance.
     */
    public static WorldEdit getInstance() {
        if (instance == null) {
            throw new IllegalStateException("WorldEdit not initialized");
        }
        return instance;
    }
    
    /**
     * Check if WorldEdit is initialized.
     */
    public static boolean isInitialized() {
        return instance != null;
    }
    
    /**
     * Shutdown WorldEdit. Should be called during server shutdown.
     */
    public static synchronized void shutdown() {
        if (instance != null) {
            instance.sessionManager.saveAllSessions();
            instance = null;
        }
    }
    
    /**
     * Get the platform implementation.
     */
    public MattMCPlatform getPlatform() {
        return platform;
    }
    
    /**
     * Get the session manager.
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }
    
    /**
     * Get a configuration value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfiguration(String key, T defaultValue) {
        return (T) configuration.getOrDefault(key, defaultValue);
    }
    
    /**
     * Set a configuration value.
     */
    public <T> void setConfiguration(String key, T value) {
        configuration.put(key, value);
    }
    
    /**
     * Initialize default configuration values.
     */
    private void initializeDefaultConfiguration() {
        // Limits
        configuration.put("max-blocks-changed", 1000000);
        configuration.put("max-entities", 1000);
        configuration.put("max-polygon-points", 20);
        configuration.put("max-brush-radius", 10);
        configuration.put("max-super-pickaxe-size", 5);
        
        // History
        configuration.put("history-size", 15);
        configuration.put("history-expiration", 10);
        
        // Sessions
        configuration.put("session-timeout", 10);
        configuration.put("session-save", true);
        
        // Tools
        configuration.put("allow-all-items", false);
        configuration.put("max-tool-range", 100);
        
        // Performance
        configuration.put("fast-mode", false);
        configuration.put("chunk-batching", true);
        
        // Logging
        configuration.put("log-commands", false);
    }
}
