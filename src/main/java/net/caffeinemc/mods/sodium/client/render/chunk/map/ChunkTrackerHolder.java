package net.caffeinemc.mods.sodium.client.render.chunk.map;

import net.caffeinemc.mods.sodium.client.hooks.SodiumClientLevelHook;
import net.minecraft.client.multiplayer.ClientLevel;

public interface ChunkTrackerHolder {
    static ChunkTracker get(ClientLevel level) {
        return SodiumClientLevelHook.getChunkTracker(level);
    }

    ChunkTracker sodium$getTracker();
}
