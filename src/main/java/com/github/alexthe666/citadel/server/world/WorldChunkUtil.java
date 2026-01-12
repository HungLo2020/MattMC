package com.github.alexthe666.citadel.server.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

public class WorldChunkUtil {
    public static ChunkAccess getChunkIfExists(Level level, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (level.hasChunk(chunkX, chunkZ)) {
            return level.getChunk(chunkX, chunkZ);
        }
        return null;
    }
    
    public static boolean isEntityBlockLoaded(LevelAccessor world, BlockPos pos) {
        if (world instanceof Level level) {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            return level.hasChunk(chunkX, chunkZ);
        }
        return false;
    }
}
