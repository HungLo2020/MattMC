package net.sodium.client.hooks;

import net.sodium.client.render.chunk.map.ChunkStatus;
import net.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.hooks.ClientLevelHooks;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sodium's implementation of ClientLevelHooks.
 * Captures biome zoom seed and tracks chunk lifecycle for optimization purposes.
 */
public class SodiumClientLevelHook implements ClientLevelHooks {
    private static final SodiumClientLevelHook INSTANCE = new SodiumClientLevelHook();
    
    // Use WeakHashMap to avoid memory leaks when levels are unloaded
    private final Map<ClientLevel, Long> biomeZoomSeeds = new WeakHashMap<>();
    private final Map<ClientLevel, ChunkTracker> chunkTrackers = new WeakHashMap<>();

    private SodiumClientLevelHook() {
    }

    public static SodiumClientLevelHook getInstance() {
        return INSTANCE;
    }

    @Override
    public void onClientLevelInit(ClientLevel level, long biomeZoomSeed) {
        this.biomeZoomSeeds.put(level, biomeZoomSeed);
        this.chunkTrackers.put(level, new ChunkTracker());
    }

    @Override
    public void onChunkUnload(ClientLevel level, int chunkX, int chunkZ) {
        ChunkTracker tracker = this.chunkTrackers.get(level);
        if (tracker != null) {
            tracker.onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_ALL);
        }
    }

    @Override
    public void onChunkLoaded(ClientLevel level, int chunkX, int chunkZ) {
        ChunkTracker tracker = this.chunkTrackers.get(level);
        if (tracker != null) {
            tracker.onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }

    @Override
    public void onChunkDropped(ClientLevel level, int chunkX, int chunkZ) {
        ChunkTracker tracker = this.chunkTrackers.get(level);
        if (tracker != null) {
            tracker.onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }

    /**
     * Get the biome zoom seed for a level.
     * Used by BiomeSeedProvider interface.
     */
    public static long getBiomeZoomSeed(ClientLevel level) {
        Long seed = INSTANCE.biomeZoomSeeds.get(level);
        return seed != null ? seed : 0L;
    }

    /**
     * Get the chunk tracker for a level.
     * Used by ChunkTrackerHolder interface.
     */
    public static ChunkTracker getChunkTracker(ClientLevel level) {
        return INSTANCE.chunkTrackers.get(level);
    }
}
