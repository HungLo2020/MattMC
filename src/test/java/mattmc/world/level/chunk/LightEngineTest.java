package mattmc.world.level.chunk;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the LightEngine light propagation system.
 */
public class LightEngineTest {
    
    /**
     * Test that skylight propagates down through air blocks.
     */
    @Test
    public void testSkylightPropagation() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Create a column of air blocks
        for (int y = 0; y < LevelChunk.HEIGHT; y++) {
            chunk.setBlock(8, y, 8, Blocks.AIR);
        }
        
        // Create a simple ChunkAccess
        LightEngine.ChunkAccess chunkAccess = new LightEngine.ChunkAccess() {
            @Override
            public LevelChunk getChunk(int chunkX, int chunkZ) {
                if (chunkX == 0 && chunkZ == 0) return chunk;
                return null;
            }
            
            @Override
            public Block getBlock(int worldX, int chunkY, int worldZ) {
                if (worldX >= 0 && worldX < 16 && worldZ >= 0 && worldZ < 16) {
                    return chunk.getBlock(worldX, chunkY, worldZ);
                }
                return Blocks.AIR;
            }
        };
        
        // Initialize lighting
        LightEngine.initializeChunkLighting(chunk, chunkAccess);
        
        // Check that skylight reaches the top of the chunk
        int topLight = chunk.getSkyLight(8, LevelChunk.HEIGHT - 1, 8);
        assertEquals(15, topLight, "Top of air column should have full skylight");
        
        // Check that skylight propagates down
        int midLight = chunk.getSkyLight(8, LevelChunk.HEIGHT / 2, 8);
        assertEquals(15, midLight, "Middle of air column should have full skylight");
        
        // Check that skylight reaches the bottom
        int bottomLight = chunk.getSkyLight(8, 0, 8);
        assertEquals(15, bottomLight, "Bottom of air column should have full skylight");
    }
    
    /**
     * Test that opaque blocks stop skylight propagation.
     */
    @Test
    public void testOpaqueBocksStopSkylight() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Create a column with stone at y=100
        for (int y = 0; y < LevelChunk.HEIGHT; y++) {
            if (y == 100) {
                chunk.setBlock(8, y, 8, Blocks.STONE);
            } else {
                chunk.setBlock(8, y, 8, Blocks.AIR);
            }
        }
        
        LightEngine.ChunkAccess chunkAccess = new LightEngine.ChunkAccess() {
            @Override
            public LevelChunk getChunk(int chunkX, int chunkZ) {
                if (chunkX == 0 && chunkZ == 0) return chunk;
                return null;
            }
            
            @Override
            public Block getBlock(int worldX, int chunkY, int worldZ) {
                if (worldX >= 0 && worldX < 16 && worldZ >= 0 && worldZ < 16) {
                    return chunk.getBlock(worldX, chunkY, worldZ);
                }
                return Blocks.AIR;
            }
        };
        
        LightEngine.initializeChunkLighting(chunk, chunkAccess);
        
        // Above the stone should have skylight
        int aboveStone = chunk.getSkyLight(8, 101, 8);
        assertEquals(15, aboveStone, "Above stone should have full skylight");
        
        // Stone itself should have no skylight
        int atStone = chunk.getSkyLight(8, 100, 8);
        assertEquals(0, atStone, "Opaque block should have no skylight");
        
        // Below the stone should have no skylight
        int belowStone = chunk.getSkyLight(8, 99, 8);
        assertEquals(0, belowStone, "Below opaque block should have no skylight");
    }
    
    /**
     * Test that torches emit block light.
     */
    @Test
    public void testTorchEmitsLight() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Fill with air
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                for (int z = 0; z < LevelChunk.DEPTH; z++) {
                    chunk.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        
        // Place a torch at (8, 64, 8)
        chunk.setBlock(8, 64, 8, Blocks.TORCH);
        
        LightEngine.ChunkAccess chunkAccess = new LightEngine.ChunkAccess() {
            @Override
            public LevelChunk getChunk(int chunkX, int chunkZ) {
                if (chunkX == 0 && chunkZ == 0) return chunk;
                return null;
            }
            
            @Override
            public Block getBlock(int worldX, int chunkY, int worldZ) {
                if (worldX >= 0 && worldX < 16 && worldZ >= 0 && worldZ < 16) {
                    return chunk.getBlock(worldX, chunkY, worldZ);
                }
                return Blocks.AIR;
            }
        };
        
        LightEngine.initializeChunkLighting(chunk, chunkAccess);
        
        // Torch should emit light level 14
        int torchLight = chunk.getBlockLight(8, 64, 8);
        assertEquals(14, torchLight, "Torch should emit light level 14");
        
        // Adjacent blocks should have light level 13 (14 - 1 attenuation)
        int adjacentLight = chunk.getBlockLight(9, 64, 8);
        assertEquals(13, adjacentLight, "Adjacent block should have light level 13");
        
        // 2 blocks away should have light level 12
        int twoAway = chunk.getBlockLight(10, 64, 8);
        assertEquals(12, twoAway, "2 blocks away should have light level 12");
    }
    
    /**
     * Test that block light propagates in all directions.
     */
    @Test
    public void testBlockLightPropagatesInAllDirections() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Fill with air
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                for (int z = 0; z < LevelChunk.DEPTH; z++) {
                    chunk.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        
        // Place a torch at center
        chunk.setBlock(8, 64, 8, Blocks.TORCH);
        
        LightEngine.ChunkAccess chunkAccess = new LightEngine.ChunkAccess() {
            @Override
            public LevelChunk getChunk(int chunkX, int chunkZ) {
                if (chunkX == 0 && chunkZ == 0) return chunk;
                return null;
            }
            
            @Override
            public Block getBlock(int worldX, int chunkY, int worldZ) {
                if (worldX >= 0 && worldX < 16 && worldZ >= 0 && worldZ < 16) {
                    return chunk.getBlock(worldX, chunkY, worldZ);
                }
                return Blocks.AIR;
            }
        };
        
        LightEngine.initializeChunkLighting(chunk, chunkAccess);
        
        // Check all 6 directions
        assertEquals(13, chunk.getBlockLight(9, 64, 8), "East should have light");
        assertEquals(13, chunk.getBlockLight(7, 64, 8), "West should have light");
        assertEquals(13, chunk.getBlockLight(8, 65, 8), "Up should have light");
        assertEquals(13, chunk.getBlockLight(8, 63, 8), "Down should have light");
        assertEquals(13, chunk.getBlockLight(8, 64, 9), "South should have light");
        assertEquals(13, chunk.getBlockLight(8, 64, 7), "North should have light");
    }
    
    /**
     * Test that light doesn't propagate through opaque blocks.
     */
    @Test
    public void testLightDoesNotPropagateThoughOpaqueBlocks() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Fill with air
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                for (int z = 0; z < LevelChunk.DEPTH; z++) {
                    chunk.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        
        // Place a torch at (8, 64, 8)
        chunk.setBlock(8, 64, 8, Blocks.TORCH);
        
        // Place a stone wall at x=9
        for (int y = 60; y < 70; y++) {
            chunk.setBlock(9, y, 8, Blocks.STONE);
        }
        
        LightEngine.ChunkAccess chunkAccess = new LightEngine.ChunkAccess() {
            @Override
            public LevelChunk getChunk(int chunkX, int chunkZ) {
                if (chunkX == 0 && chunkZ == 0) return chunk;
                return null;
            }
            
            @Override
            public Block getBlock(int worldX, int chunkY, int worldZ) {
                if (worldX >= 0 && worldX < 16 && worldZ >= 0 && worldZ < 16) {
                    return chunk.getBlock(worldX, chunkY, worldZ);
                }
                return Blocks.AIR;
            }
        };
        
        LightEngine.initializeChunkLighting(chunk, chunkAccess);
        
        // Torch should have light
        assertEquals(14, chunk.getBlockLight(8, 64, 8), "Torch should emit light");
        
        // Stone wall should have no light
        assertEquals(0, chunk.getBlockLight(9, 64, 8), "Opaque block should have no light");
        
        // Behind the wall should have no light
        assertEquals(0, chunk.getBlockLight(10, 64, 8), "Behind wall should have no light");
        
        // West of torch should still have light
        assertEquals(13, chunk.getBlockLight(7, 64, 8), "West of torch should have light");
    }
}
