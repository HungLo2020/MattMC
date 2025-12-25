package net.minecraft.hooks;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Hooks for tracking chunk loading/unloading status.
 * Used by mods to monitor chunk data availability and light data updates.
 */
public interface ChunkStatusHooks {
    /**
     * Called when a chunk is unloaded.
     *
     * @param chunk The chunk being unloaded
     */
    default void onChunkUnload(LevelChunk chunk) {}

    /**
     * Called when light data is received for a chunk.
     *
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    default void onLightDataReceived(int chunkX, int chunkZ) {}

    /**
     * Called when a chunk is unloaded via packet.
     *
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    default void onChunkUnloadPacket(int chunkX, int chunkZ) {}

    /**
     * Called when a chunk's block data is dropped from cache.
     *
     * @param pos The chunk position
     */
    default void onChunkBlockDataDropped(ChunkPos pos) {}

    /**
     * Called when a chunk is loaded with block data.
     *
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     */
    default void onChunkBlockDataLoaded(int chunkX, int chunkZ) {}
}
