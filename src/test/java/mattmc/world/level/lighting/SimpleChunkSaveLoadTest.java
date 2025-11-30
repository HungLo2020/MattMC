package mattmc.world.level.lighting;

import mattmc.registries.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.ChunkNBT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for chunk heightmap save/load.
 */
public class SimpleChunkSaveLoadTest {
    
    @Test
    @DisplayName("Heightmap saves and loads correctly")
    public void testHeightmapSaveLoad() {
        // Create chunk with stone
        LevelChunk chunk = new LevelChunk(0, 0);
        
        int surfaceY = LevelChunk.worldYToChunkY(64);
        
        // Fill terrain
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= surfaceY; y++) {
                    chunk.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Manually set heightmap (simulating what SkylightEngine does)
        chunk.getHeightmap().setHeight(8, 8, 64);
        
        System.out.println("Before save:");
        System.out.println("  Block at (8," + surfaceY + ",8): " + chunk.getBlock(8, surfaceY, 8).getIdentifier());
        System.out.println("  Heightmap at (8,8): " + chunk.getHeightmap().getHeight(8, 8));
        
        // Save to NBT
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        
        // Check what's in the NBT
        Object heightmapObj = nbt.get("Heightmap");
        System.out.println("\nIn NBT:");
        System.out.println("  Heightmap type: " + (heightmapObj != null ? heightmapObj.getClass().getName() : "null"));
        if (heightmapObj instanceof int[]) {
            int[] hm = (int[]) heightmapObj;
            System.out.println("  Heightmap length: " + hm.length);
            // Find index for x=8, z=8
            int index = 8 * 16 + 8;
            System.out.println("  Heightmap[8,8] (index " + index + "): " + hm[index]);
        }
        
        // Load from NBT
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        System.out.println("\nAfter load:");
        System.out.println("  Block at (8," + surfaceY + ",8): " + loaded.getBlock(8, surfaceY, 8).getIdentifier());
        System.out.println("  Heightmap at (8,8): " + loaded.getHeightmap().getHeight(8, 8));
        
        assertEquals(64, loaded.getHeightmap().getHeight(8, 8), "Heightmap should be preserved");
    }
}
