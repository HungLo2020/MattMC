package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.hooks.ChunkStatusHooks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Sodium implementation of chunk status tracking hooks.
 * Replaces mixins: ClientLevelMixin, ClientPacketListenerMixin, ClientChunkCacheMixin
 */
public class SodiumChunkStatusHook implements ChunkStatusHooks {
    @Override
    public void onChunkUnload(LevelChunk chunk) {
        var pos = chunk.getPos();
        ChunkTrackerHolder.get(chunk.getLevel())
                .onChunkStatusRemoved(pos.x, pos.z, ChunkStatus.FLAG_ALL);
    }

    @Override
    public void onLightDataReceived(int chunkX, int chunkZ) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            ChunkTrackerHolder.get(level)
                    .onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_HAS_LIGHT_DATA);
        }
    }

    @Override
    public void onChunkUnloadPacket(int chunkX, int chunkZ) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            ChunkTrackerHolder.get(level)
                    .onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_ALL);
        }
    }

    @Override
    public void onChunkBlockDataDropped(ChunkPos pos) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            ChunkTrackerHolder.get(level)
                    .onChunkStatusRemoved(pos.x, pos.z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }

    @Override
    public void onChunkBlockDataLoaded(int chunkX, int chunkZ) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            ChunkTrackerHolder.get(level)
                    .onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }
}
