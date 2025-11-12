package mattmc.world.level.levelgen;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SkylightInitializer.
 * Verifies that skylight is correctly initialized for chunk columns.
 */
public class SkylightInitializerTest {
    
    @Test
    public void testFlatWorldSurfaceHasSkylight() {
        // Create a flat world chunk
        LevelChunk chunk = new LevelChunk(0, 0);
        int surfaceWorldY = 64;
        chunk.generateFlatTerrain(surfaceWorldY);
        
        // Initialize skylight
        SkylightInitializer.initializeChunk(chunk);
        
        // Surface should have skylight=15
        int surfaceChunkY = LevelChunk.worldYToChunkY(surfaceWorldY);
        assertEquals(15, chunk.getSkyLight(8, surfaceChunkY, 8), 
            "Surface block should have skylight=15");
        
        // Air above surface should have skylight=15
        assertEquals(15, chunk.getSkyLight(8, surfaceChunkY + 1, 8),
            "Air above surface should have skylight=15");
        assertEquals(15, chunk.getSkyLight(8, surfaceChunkY + 10, 8),
            "Air well above surface should have skylight=15");
        
        // Test all columns have surface skylight
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int z = 0; z < LevelChunk.DEPTH; z++) {
                assertEquals(15, chunk.getSkyLight(x, surfaceChunkY, z),
                    "All surface positions should have skylight=15");
            }
        }
        
        System.out.println("[Flat World Surface] Test passed - skylight=15 at surface and above");
    }
    
    @Test
    public void testFlatWorldBelowSurfaceHasNoSkylight() {
        // Create a flat world chunk
        LevelChunk chunk = new LevelChunk(0, 0);
        int surfaceWorldY = 64;
        chunk.generateFlatTerrain(surfaceWorldY);
        
        // Initialize skylight
        SkylightInitializer.initializeChunk(chunk);
        
        // Below surface (in opaque blocks) should have skylight=0
        int surfaceChunkY = LevelChunk.worldYToChunkY(surfaceWorldY);
        assertEquals(0, chunk.getSkyLight(8, surfaceChunkY - 1, 8),
            "One block below surface should have skylight=0");
        assertEquals(0, chunk.getSkyLight(8, surfaceChunkY - 10, 8),
            "Deep underground should have skylight=0");
        assertEquals(0, chunk.getSkyLight(8, 0, 8),
            "Bottom of world should have skylight=0");
        
        System.out.println("[Flat World Underground] Test passed - skylight=0 below surface");
    }
    
    @Test
    public void testCaveHasNoSkylight() {
        // Create a chunk with a cave (air pocket underground)
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Fill with stone from bottom to y=100
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int z = 0; z < LevelChunk.DEPTH; z++) {
                for (int y = 0; y <= 100; y++) {
                    chunk.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Create a cave (air pocket) at y=50
        for (int x = 5; x < 11; x++) {
            for (int z = 5; z < 11; z++) {
                for (int y = 48; y < 53; y++) {
                    chunk.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        
        // Initialize skylight
        SkylightInitializer.initializeChunk(chunk);
        
        // Cave should have skylight=0 (no path to sky)
        assertEquals(0, chunk.getSkyLight(8, 50, 8),
            "Cave air should have skylight=0");
        assertEquals(0, chunk.getSkyLight(5, 48, 5),
            "Cave floor should have skylight=0");
        assertEquals(0, chunk.getSkyLight(10, 52, 10),
            "Cave ceiling should have skylight=0");
        
        // Sky above the terrain should have skylight=15
        assertEquals(15, chunk.getSkyLight(8, 101, 8),
            "Open sky above terrain should have skylight=15");
        assertEquals(15, chunk.getSkyLight(8, 200, 8),
            "High in the sky should have skylight=15");
        
        System.out.println("[Cave] Test passed - cave has skylight=0, sky has skylight=15");
    }
    
    @Test
    public void testBlockLightIsZeroed() {
        // Create a chunk
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        
        // Set some block light values
        chunk.setBlockLight(8, 64, 8, 15);
        chunk.setBlockLight(0, 0, 0, 10);
        chunk.setBlockLight(15, 100, 15, 5);
        
        // Initialize skylight (should zero block light)
        SkylightInitializer.initializeChunk(chunk);
        
        // All block light should be 0
        assertEquals(0, chunk.getBlockLight(8, 64, 8),
            "Block light should be zeroed");
        assertEquals(0, chunk.getBlockLight(0, 0, 0),
            "Block light should be zeroed");
        assertEquals(0, chunk.getBlockLight(15, 100, 15),
            "Block light should be zeroed");
        
        // Test all positions
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int z = 0; z < LevelChunk.DEPTH; z++) {
                for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                    assertEquals(0, chunk.getBlockLight(x, y, z),
                        "All block light should be 0 at (" + x + "," + y + "," + z + ")");
                }
            }
        }
        
        System.out.println("[Block Light Zeroed] Test passed - all block light is 0");
    }
    
    @Test
    public void testEmptyChunkHasFullSkylight() {
        // Create an empty chunk (all air)
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Initialize skylight
        SkylightInitializer.initializeChunk(chunk);
        
        // All positions should have skylight=15
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int z = 0; z < LevelChunk.DEPTH; z++) {
                for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                    assertEquals(15, chunk.getSkyLight(x, y, z),
                        "Empty chunk should have skylight=15 everywhere");
                }
            }
        }
        
        System.out.println("[Empty Chunk] Test passed - all air has skylight=15");
    }
    
    @Test
    public void testSingleBlockShadow() {
        // Create a chunk with a single opaque block
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setBlock(8, 100, 8, Blocks.STONE);
        
        // Initialize skylight
        SkylightInitializer.initializeChunk(chunk);
        
        // Above the block should have skylight=15
        assertEquals(15, chunk.getSkyLight(8, 101, 8),
            "Above opaque block should have skylight=15");
        assertEquals(15, chunk.getSkyLight(8, 200, 8),
            "High above opaque block should have skylight=15");
        
        // The block itself should have skylight=15 (it's lit from above)
        assertEquals(15, chunk.getSkyLight(8, 100, 8),
            "Opaque block itself should have skylight=15");
        
        // Below the block should have skylight=0
        assertEquals(0, chunk.getSkyLight(8, 99, 8),
            "Below opaque block should have skylight=0");
        assertEquals(0, chunk.getSkyLight(8, 50, 8),
            "Well below opaque block should have skylight=0");
        
        // Adjacent columns should still have full skylight
        assertEquals(15, chunk.getSkyLight(7, 100, 8),
            "Adjacent column should have full skylight");
        assertEquals(15, chunk.getSkyLight(9, 100, 8),
            "Adjacent column should have full skylight");
        assertEquals(15, chunk.getSkyLight(8, 100, 7),
            "Adjacent column should have full skylight");
        
        System.out.println("[Single Block Shadow] Test passed - shadow only in same column");
    }
    
    @Test
    public void testMultipleBlocksInColumn() {
        // Create a chunk with multiple opaque blocks in a column
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setBlock(8, 100, 8, Blocks.STONE);
        chunk.setBlock(8, 80, 8, Blocks.DIRT);
        chunk.setBlock(8, 60, 8, Blocks.COBBLESTONE);
        
        // Initialize skylight
        SkylightInitializer.initializeChunk(chunk);
        
        // Above top block should have skylight=15
        assertEquals(15, chunk.getSkyLight(8, 101, 8),
            "Above top block should have skylight=15");
        
        // Top block should have skylight=15
        assertEquals(15, chunk.getSkyLight(8, 100, 8),
            "Top block should have skylight=15");
        
        // Air between blocks should have skylight=0 (below first opaque)
        assertEquals(0, chunk.getSkyLight(8, 99, 8),
            "Air below first opaque should have skylight=0");
        assertEquals(0, chunk.getSkyLight(8, 81, 8),
            "Air below first opaque should have skylight=0");
        
        // All opaque blocks below the first should have skylight=0
        assertEquals(0, chunk.getSkyLight(8, 80, 8),
            "Second block should have skylight=0");
        assertEquals(0, chunk.getSkyLight(8, 60, 8),
            "Third block should have skylight=0");
        
        System.out.println("[Multiple Blocks] Test passed - first opaque blocks skylight for entire column below");
    }
    
    @Test
    public void testDifferentHeightsInDifferentColumns() {
        // Create a chunk with varying heights
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Column (0,0) has blocks up to y=50
        for (int y = 0; y <= 50; y++) {
            chunk.setBlock(0, y, 0, Blocks.STONE);
        }
        
        // Column (8,8) has blocks up to y=100
        for (int y = 0; y <= 100; y++) {
            chunk.setBlock(8, y, 8, Blocks.STONE);
        }
        
        // Column (15,15) has blocks up to y=150
        for (int y = 0; y <= 150; y++) {
            chunk.setBlock(15, y, 15, Blocks.STONE);
        }
        
        // Initialize skylight
        SkylightInitializer.initializeChunk(chunk);
        
        // Each column should have independent skylight
        // Column (0,0) - surface at 50
        assertEquals(15, chunk.getSkyLight(0, 50, 0), "Surface at y=50 should be lit");
        assertEquals(0, chunk.getSkyLight(0, 49, 0), "Below y=50 should be dark");
        assertEquals(15, chunk.getSkyLight(0, 51, 0), "Above y=50 should be lit");
        
        // Column (8,8) - surface at 100
        assertEquals(15, chunk.getSkyLight(8, 100, 8), "Surface at y=100 should be lit");
        assertEquals(0, chunk.getSkyLight(8, 99, 8), "Below y=100 should be dark");
        assertEquals(15, chunk.getSkyLight(8, 101, 8), "Above y=100 should be lit");
        
        // Column (15,15) - surface at 150
        assertEquals(15, chunk.getSkyLight(15, 150, 15), "Surface at y=150 should be lit");
        assertEquals(0, chunk.getSkyLight(15, 149, 15), "Below y=150 should be dark");
        assertEquals(15, chunk.getSkyLight(15, 151, 15), "Above y=150 should be lit");
        
        System.out.println("[Different Heights] Test passed - each column has independent skylight");
    }
}
