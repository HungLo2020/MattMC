package net.minecraft.hooks;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Hook interface for client level (world) events.
 * Allows mods to capture initialization data and track level events.
 */
public interface ClientLevelHooks {
    /**
     * Called when a client level is initialized.
     * 
     * @param level The client level being initialized
     * @param biomeZoomSeed The biome zoom seed for this level
     */
    default void onClientLevelInit(ClientLevel level, long biomeZoomSeed) {
    }
    
    /**
     * Called when a chunk is being unloaded from the client level.
     * 
     * @param level The client level
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    default void onChunkUnload(ClientLevel level, int chunkX, int chunkZ) {
    }
    
    /**
     * Called after a chunk is loaded into the client chunk cache.
     * 
     * @param level The client level
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    default void onChunkLoaded(ClientLevel level, int chunkX, int chunkZ) {
    }
    
    /**
     * Called after a chunk is dropped from the client chunk cache.
     * 
     * @param level The client level
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    default void onChunkDropped(ClientLevel level, int chunkX, int chunkZ) {
    }
}
