package net.minecraft.client.renderer.sodium.render.chunk.map;

import net.minecraft.client.multiplayer.ClientLevel;

public interface ChunkTrackerHolder {
    static ChunkTracker get(ClientLevel level) {
        return ((ChunkTrackerHolder) level).sodium$getTracker();
    }

    ChunkTracker sodium$getTracker();
}
