package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Fabric API stub for ServerChunkEvents
 */
public final class ServerChunkEvents {
    public static final Event<ChunkLoad> CHUNK_LOAD = Event.create(ChunkLoad.class, callbacks -> (world, chunk) -> {
        for (ChunkLoad callback : callbacks) {
            callback.onChunkLoad(world, chunk);
        }
    });
    
    public static final Event<ChunkUnload> CHUNK_UNLOAD = Event.create(ChunkUnload.class, callbacks -> (world, chunk) -> {
        for (ChunkUnload callback : callbacks) {
            callback.onChunkUnload(world, chunk);
        }
    });
    
    @FunctionalInterface
    public interface ChunkLoad {
        void onChunkLoad(ServerLevel world, LevelChunk chunk);
    }
    
    @FunctionalInterface
    public interface ChunkUnload {
        void onChunkUnload(ServerLevel world, LevelChunk chunk);
    }
    
    private ServerChunkEvents() {}
}
