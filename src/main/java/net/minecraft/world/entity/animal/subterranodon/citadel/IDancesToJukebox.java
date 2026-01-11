package net.minecraft.world.entity.animal.subterranodon.citadel;

import net.minecraft.core.BlockPos;

/**
 * Stub interface to replace Citadel's IDancesToJukebox.
 * Allows entities to dance when a jukebox plays nearby.
 */
public interface IDancesToJukebox {
    
    /**
     * Sets whether a record is playing nearby.
     * @param pos The position of the jukebox
     * @param playing Whether the jukebox is playing
     */
    void setRecordPlayingNearby(BlockPos pos, boolean playing);
    
    /**
     * Called on the client when music disc playback changes.
     * @param entityId The entity ID
     * @param pos The jukebox position
     * @param playing Whether it's playing
     */
    default void onClientPlayMusicDisc(int entityId, BlockPos pos, boolean playing) {
        // Client-side handling would go here
    }
}
