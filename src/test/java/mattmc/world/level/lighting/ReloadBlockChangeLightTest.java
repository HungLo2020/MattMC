package mattmc.world.level.lighting;

import mattmc.registries.Blocks;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for lighting behavior after world reload and block changes.
 * 
 * This test suite specifically addresses the bug where:
 * 1. Dig a cave with one block opening to surface
 * 2. Close the opening, save and exit
 * 3. Reload - cave is correctly dark
 * 4. Break block at opening - light SHOULD propagate but DOESN'T
 * 5. Place and break again - light DOES propagate (inconsistent)
 * 6. Place block again - light SHOULD un-propagate but DOESN'T
 */
public class ReloadBlockChangeLightTest {
    
    @Test
    @DisplayName("Light propagates into cave after reload and block break")
    public void testLightPropagatesAfterReloadAndBlockBreak(@TempDir Path tempDir) throws Exception {
        // First session - create cave, close hole, save
        Level level1 = new Level();
        level1.setWorldDirectory(tempDir);
        
        LevelChunk chunk = level1.getChunk(0, 0);
        
        int surfaceY = LevelChunk.worldYToChunkY(64);
        int caveY = LevelChunk.worldYToChunkY(60);
        
        // Create solid terrain
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = caveY - 3; y <= surfaceY; y++) {
                    level1.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Create cave room at y=60
        for (int x = 6; x <= 10; x++) {
            for (int z = 6; z <= 10; z++) {
                for (int y = caveY; y <= caveY + 3; y++) {
                    level1.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        
        // Create vertical shaft from surface to cave
        for (int y = caveY + 3; y <= surfaceY; y++) {
            level1.setBlock(8, y, 8, Blocks.AIR);
        }
        
        // Print skylight in cave before closing
        System.out.println("Before closing hole:");
        System.out.println("  Skylight in cave center: " + chunk.getSkyLight(8, caveY, 8));
        System.out.println("  Heightmap at (8,8): " + chunk.getHeightmap().getHeight(8, 8));
        
        // Close the hole at surface
        level1.setBlock(8, surfaceY, 8, Blocks.STONE);
        
        System.out.println("After closing hole:");
        System.out.println("  Skylight in cave center: " + chunk.getSkyLight(8, caveY, 8));
        System.out.println("  Heightmap at (8,8): " + chunk.getHeightmap().getHeight(8, 8));
        
        // Force chunk save by unloading it
        System.out.println("\nMoving player far away to trigger chunk unload and save...");
        level1.updateChunksAroundPlayer(1000, 1000);
        
        // Wait a bit for async save to complete
        Thread.sleep(500);
        
        // Shutdown level (also flushes saves)
        level1.shutdown();
        
        // Second session - reload and try to reopen hole
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        
        // Check if the saved file exists
        java.nio.file.Path regionDir = tempDir.resolve("region");
        java.nio.file.Path regionFile = regionDir.resolve("r.0.0.mca");
        System.out.println("\nChecking saved files:");
        System.out.println("  Region dir exists: " + java.nio.file.Files.exists(regionDir));
        System.out.println("  Region file exists: " + java.nio.file.Files.exists(regionFile));
        if (java.nio.file.Files.exists(regionFile)) {
            System.out.println("  Region file size: " + java.nio.file.Files.size(regionFile));
        }
        
        LevelChunk reloadedChunk = level2.getChunk(0, 0);
        
        System.out.println("\nAfter reload:");
        System.out.println("  Skylight in cave center: " + reloadedChunk.getSkyLight(8, caveY, 8));
        System.out.println("  Block at surface: " + reloadedChunk.getBlock(8, surfaceY, 8).getIdentifier());
        System.out.println("  Heightmap at (8,8): " + reloadedChunk.getHeightmap().getHeight(8, 8));
        
        // Cave should be dark
        assertEquals(0, reloadedChunk.getSkyLight(8, caveY, 8), "Cave should be dark after reload");
        
        // Now break the block at surface - THIS IS WHERE THE BUG IS
        level2.setBlock(8, surfaceY, 8, Blocks.AIR);
        
        System.out.println("\nAfter breaking block at surface:");
        System.out.println("  Skylight in cave center: " + reloadedChunk.getSkyLight(8, caveY, 8));
        System.out.println("  Heightmap at (8,8): " + reloadedChunk.getHeightmap().getHeight(8, 8));
        
        // Light should propagate into cave
        assertTrue(reloadedChunk.getSkyLight(8, caveY, 8) > 0, 
                  "Light should propagate into cave after breaking surface block");
        
        // Now place the block again
        level2.setBlock(8, surfaceY, 8, Blocks.STONE);
        
        System.out.println("\nAfter placing block at surface:");
        System.out.println("  Skylight in cave center: " + reloadedChunk.getSkyLight(8, caveY, 8));
        
        // Cave should be dark again
        assertEquals(0, reloadedChunk.getSkyLight(8, caveY, 8), 
                    "Cave should be dark again after placing surface block");
        
        level2.shutdown();
    }
    
    @Test
    @DisplayName("Cave with single block opening - light cycles correctly after reload")
    public void testCaveLightCycleAfterReload(@TempDir Path tempDir) throws Exception {
        // Setup: Create world with cave and opening
        Level level1 = new Level();
        level1.setWorldDirectory(tempDir);
        
        LevelChunk chunk = level1.getChunk(0, 0);
        
        int surfaceY = LevelChunk.worldYToChunkY(64);
        int caveY = LevelChunk.worldYToChunkY(55);
        
        // Create solid terrain around y=55-64
        for (int x = 4; x <= 12; x++) {
            for (int z = 4; z <= 12; z++) {
                for (int y = caveY - 1; y <= surfaceY; y++) {
                    level1.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Create 3x3 cave room at y=55-57
        for (int x = 7; x <= 9; x++) {
            for (int z = 7; z <= 9; z++) {
                for (int y = caveY; y <= caveY + 2; y++) {
                    level1.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        
        // Create 1-block wide vertical shaft
        for (int y = caveY + 2; y <= surfaceY; y++) {
            level1.setBlock(8, y, 8, Blocks.AIR);
        }
        
        // Verify cave has light through the shaft
        assertTrue(chunk.getSkyLight(8, caveY, 8) > 0, "Cave should have light through shaft");
        
        // Close the shaft at surface level
        level1.setBlock(8, surfaceY, 8, Blocks.STONE);
        
        // Cave should now be dark
        assertEquals(0, chunk.getSkyLight(8, caveY, 8), "Cave should be dark after closing shaft");
        
        // Save and reload
        level1.updateChunksAroundPlayer(1000, 1000);
        level1.shutdown();
        
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        LevelChunk loaded = level2.getChunk(0, 0);
        
        // Verify cave is still dark after reload
        assertEquals(0, loaded.getSkyLight(8, caveY, 8), "Cave should remain dark after reload");
        
        // Test multiple open/close cycles
        for (int cycle = 1; cycle <= 3; cycle++) {
            System.out.println("\n=== Cycle " + cycle + " ===");
            
            // Break the surface block - light should enter
            level2.setBlock(8, surfaceY, 8, Blocks.AIR);
            int lightAfterBreak = loaded.getSkyLight(8, caveY, 8);
            System.out.println("After break: skylight = " + lightAfterBreak);
            assertTrue(lightAfterBreak > 0, 
                      "Cycle " + cycle + ": Cave should have light after breaking surface block");
            
            // Place the surface block - light should leave
            level2.setBlock(8, surfaceY, 8, Blocks.STONE);
            int lightAfterPlace = loaded.getSkyLight(8, caveY, 8);
            System.out.println("After place: skylight = " + lightAfterPlace);
            assertEquals(0, lightAfterPlace, 
                        "Cycle " + cycle + ": Cave should be dark after placing surface block");
        }
        
        level2.shutdown();
    }
}
