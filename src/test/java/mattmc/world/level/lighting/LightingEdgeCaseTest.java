package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Aggressive edge-case tests designed to detect subtle lighting bugs.
 * 
 * These tests specifically target reported issues:
 * - Light not propagating correctly
 * - Light not "going away" after source removal
 * - Inconsistent lighting behavior
 * - Face illumination issues
 */
public class LightingEdgeCaseTest {
    
    private WorldLightManager worldLightManager;
    private LevelChunk chunk;
    
    @BeforeEach
    public void setup() {
        worldLightManager = new WorldLightManager();
        chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
    }
    
    // ========== LIGHT NOT GOING AWAY TESTS ==========
    
    @Test
    @DisplayName("BUG DETECTION: Place/remove torch 10 times - check for residual light")
    public void testRepeatedTorchPlaceRemove() {
        int y = LevelChunk.worldYToChunkY(64);
        
        for (int i = 0; i < 10; i++) {
            // Place torch
            chunk.setBlock(8, y, 8, Blocks.TORCH);
            assertTrue(chunk.getBlockLightI(8, y, 8) > 0, "Iteration " + i + ": Torch should emit light");
            assertTrue(chunk.getBlockLightI(9, y, 8) > 0, "Iteration " + i + ": Neighbor should have light");
            
            // Remove torch
            chunk.setBlock(8, y, 8, Blocks.AIR);
            
            // Check ALL nearby positions for residual light
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int light = chunk.getBlockLightI(8 + dx, y, 8 + dz);
                    assertEquals(0, light, 
                        "Iteration " + i + ": Residual light at offset (" + dx + "," + dz + ") = " + light);
                }
            }
        }
    }
    
    @Test
    @DisplayName("BUG DETECTION: Torch in corner of enclosed room")
    public void testTorchInCornerRemoval() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Build 5x5x5 enclosed room
        for (int x = 5; x <= 10; x++) {
            for (int z = 5; z <= 10; z++) {
                for (int dy = -1; dy <= 5; dy++) {
                    // Walls and floor/ceiling
                    if (x == 5 || x == 10 || z == 5 || z == 10 || dy == -1 || dy == 5) {
                        chunk.setBlock(x, y + dy, z, Blocks.STONE);
                    }
                }
            }
        }
        
        // Place torch in corner
        chunk.setBlock(6, y, 6, Blocks.TORCH);
        
        System.out.println("Torch in corner at (6," + y + ",6): I=" + chunk.getBlockLightI(6, y, 6));
        
        // Verify light spreads throughout room
        assertTrue(chunk.getBlockLightI(6, y, 6) > 0, "Corner torch should have light");
        assertTrue(chunk.getBlockLightI(9, y, 9) > 0, "Opposite corner should have light");
        
        // Remove torch
        chunk.setBlock(6, y, 6, Blocks.AIR);
        
        // Check entire room interior for residual light
        int residualCount = 0;
        for (int x = 6; x <= 9; x++) {
            for (int z = 6; z <= 9; z++) {
                for (int dy = 0; dy <= 4; dy++) {
                    int light = chunk.getBlockLightI(x, y + dy, z);
                    if (light > 0) {
                        System.out.println("RESIDUAL LIGHT at (" + x + "," + (y+dy) + "," + z + "): " + light);
                        residualCount++;
                    }
                }
            }
        }
        assertEquals(0, residualCount, "Found " + residualCount + " positions with residual light");
    }
    
    @Test
    @DisplayName("BUG DETECTION: Multiple torches placed and removed sequentially")
    public void testMultipleTorchesSequentialRemoval() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place 3 torches
        chunk.setBlock(4, y, 8, Blocks.TORCH);
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        chunk.setBlock(12, y, 8, Blocks.TORCH);
        
        System.out.println("3 torches placed:");
        System.out.println("  Torch1 at (4): " + chunk.getBlockLightI(4, y, 8));
        System.out.println("  Torch2 at (8): " + chunk.getBlockLightI(8, y, 8));
        System.out.println("  Torch3 at (12): " + chunk.getBlockLightI(12, y, 8));
        System.out.println("  Middle at (6): " + chunk.getBlockLightI(6, y, 8));
        
        // Remove middle torch
        chunk.setBlock(8, y, 8, Blocks.AIR);
        
        System.out.println("After removing middle torch:");
        System.out.println("  At (8): " + chunk.getBlockLightI(8, y, 8));
        System.out.println("  At (6): " + chunk.getBlockLightI(6, y, 8) + " (should be from torch1)");
        System.out.println("  At (10): " + chunk.getBlockLightI(10, y, 8) + " (should be from torch3)");
        
        // Position (8) should now have light from adjacent torches only
        // Torch at (4) is 4 blocks away: 14-4 = 10
        // Torch at (12) is 4 blocks away: 14-4 = 10
        int lightAtMiddle = chunk.getBlockLightI(8, y, 8);
        assertTrue(lightAtMiddle <= 10, "Light at (8) should be ≤10 from remaining torches, got " + lightAtMiddle);
        
        // Remove torch1
        chunk.setBlock(4, y, 8, Blocks.AIR);
        
        System.out.println("After removing torch1:");
        System.out.println("  At (4): " + chunk.getBlockLightI(4, y, 8));
        System.out.println("  At (6): " + chunk.getBlockLightI(6, y, 8));
        
        // Area around (4) should only have light from torch3 at (12)
        // Distance from (4) to (12) is 8, so light = 14 - 8 = 6
        int lightAtPos4 = chunk.getBlockLightI(4, y, 8);
        assertEquals(6, lightAtPos4, "Light at (4) should be 6 from torch3, got " + lightAtPos4);
        
        // Remove torch3
        chunk.setBlock(12, y, 8, Blocks.AIR);
        
        System.out.println("After removing all torches:");
        // ALL light should be gone
        for (int x = 0; x <= 15; x++) {
            int light = chunk.getBlockLightI(x, y, 8);
            assertEquals(0, light, "All light should be gone at x=" + x + ", got " + light);
        }
    }
    
    // ========== LIGHT NOT PROPAGATING TESTS ==========
    
    @Test
    @DisplayName("BUG DETECTION: Light should reach through L-shaped tunnel")
    public void testLShapedTunnelPropagation() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Fill area with stone
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int dy = -1; dy <= 1; dy++) {
                    chunk.setBlock(x, y + dy, z, Blocks.STONE);
                }
            }
        }
        
        // Dig L-shaped tunnel
        // Horizontal segment: x=2 to x=8, z=5
        for (int x = 2; x <= 8; x++) {
            chunk.setBlock(x, y, 5, Blocks.AIR);
        }
        // Vertical segment: x=8, z=5 to z=10
        for (int z = 5; z <= 10; z++) {
            chunk.setBlock(8, y, z, Blocks.AIR);
        }
        
        // Place torch at start of tunnel
        chunk.setBlock(2, y, 5, Blocks.TORCH);
        
        System.out.println("Torch at (2,y,5): " + chunk.getBlockLightI(2, y, 5));
        System.out.println("At corner (8,y,5): " + chunk.getBlockLightI(8, y, 5));
        System.out.println("End of L (8,y,10): " + chunk.getBlockLightI(8, y, 10));
        
        // Light should reach corner (distance 6)
        int lightAtCorner = chunk.getBlockLightI(8, y, 5);
        assertEquals(14 - 6, lightAtCorner, "Light at corner should be 8");
        
        // Light should reach end of L (distance 11)
        int lightAtEnd = chunk.getBlockLightI(8, y, 10);
        assertEquals(14 - 11, lightAtEnd, "Light at end should be 3");
    }
    
    @Test
    @DisplayName("BUG DETECTION: Light propagates through narrow 1-block gap")
    public void testNarrowGapPropagation() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Build a wall with a 1-block hole
        for (int z = 0; z < 16; z++) {
            if (z != 8) { // Leave gap at z=8
                chunk.setBlock(8, y, z, Blocks.STONE);
            }
        }
        
        // Place torch on one side
        chunk.setBlock(6, y, 8, Blocks.TORCH);
        
        System.out.println("Torch at (6,y,8): " + chunk.getBlockLightI(6, y, 8));
        System.out.println("At gap (8,y,8): " + chunk.getBlockLightI(8, y, 8));
        System.out.println("Through gap (10,y,8): " + chunk.getBlockLightI(10, y, 8));
        
        // Light should pass through the gap
        // Distance from (6) to (8) is 2: 14-2 = 12
        assertEquals(12, chunk.getBlockLightI(8, y, 8), "Light at gap should be 12");
        
        // Distance from (6) to (10) is 4: 14-4 = 10
        assertEquals(10, chunk.getBlockLightI(10, y, 8), "Light through gap should be 10");
        
        // Remove torch and verify cleanup
        chunk.setBlock(6, y, 8, Blocks.AIR);
        assertEquals(0, chunk.getBlockLightI(10, y, 8), "Light should be removed after torch gone");
    }
    
    // ========== FACE ILLUMINATION TESTS ==========
    
    @Test
    @DisplayName("BUG DETECTION: Light reaches all 6 faces of adjacent blocks")
    public void testAllFacesIlluminated() {
        int x = 8, y = LevelChunk.worldYToChunkY(64), z = 8;
        
        // Place torch
        chunk.setBlock(x, y, z, Blocks.TORCH);
        
        // Check all 6 adjacent positions have light
        int[] lights = {
            chunk.getBlockLightI(x + 1, y, z), // +X
            chunk.getBlockLightI(x - 1, y, z), // -X
            chunk.getBlockLightI(x, y + 1, z), // +Y
            chunk.getBlockLightI(x, y - 1, z), // -Y
            chunk.getBlockLightI(x, y, z + 1), // +Z
            chunk.getBlockLightI(x, y, z - 1)  // -Z
        };
        String[] directions = {"+X", "-X", "+Y", "-Y", "+Z", "-Z"};
        
        for (int i = 0; i < 6; i++) {
            assertEquals(13, lights[i], "Face " + directions[i] + " should have light 13, got " + lights[i]);
        }
    }
    
    @Test
    @DisplayName("BUG DETECTION: Diagonal face illumination")
    public void testDiagonalFaceIllumination() {
        int x = 8, y = LevelChunk.worldYToChunkY(64), z = 8;
        
        // Place torch
        chunk.setBlock(x, y, z, Blocks.TORCH);
        
        // Check diagonal positions (Manhattan distance 2)
        int[][] diagonals = {
            {x+1, y, z+1}, {x+1, y, z-1}, {x-1, y, z+1}, {x-1, y, z-1},
            {x+1, y+1, z}, {x+1, y-1, z}, {x-1, y+1, z}, {x-1, y-1, z},
            {x, y+1, z+1}, {x, y+1, z-1}, {x, y-1, z+1}, {x, y-1, z-1}
        };
        
        for (int[] pos : diagonals) {
            int light = chunk.getBlockLightI(pos[0], pos[1], pos[2]);
            assertEquals(12, light, 
                "Diagonal at (" + pos[0] + "," + pos[1] + "," + pos[2] + ") should have light 12, got " + light);
        }
    }
    
    // ========== OPEN/CLOSE TESTS (user reported bug) ==========
    
    @Test
    @DisplayName("BUG DETECTION: Open hole, close it, open again - skylight should return")
    public void testOpenCloseOpenSkylight() {
        int surfaceY = LevelChunk.worldYToChunkY(64);
        
        // Fill underground with stone
        for (int x = 5; x <= 10; x++) {
            for (int z = 5; z <= 10; z++) {
                for (int y = 0; y < surfaceY; y++) {
                    chunk.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Create underground cave
        int caveY = surfaceY - 3;
        for (int x = 6; x <= 9; x++) {
            for (int z = 6; z <= 9; z++) {
                chunk.setBlock(x, caveY, z, Blocks.AIR);
            }
        }
        
        System.out.println("=== Initial state (cave is sealed) ===");
        System.out.println("Cave center skylight: " + chunk.getSkyLight(7, caveY, 7));
        assertEquals(0, chunk.getSkyLight(7, caveY, 7), "Sealed cave should be dark");
        
        // Open vertical shaft
        System.out.println("=== Opening shaft ===");
        for (int y = caveY + 1; y <= surfaceY; y++) {
            chunk.setBlock(7, y, 7, Blocks.AIR);
        }
        
        int lightAfterOpen1 = chunk.getSkyLight(7, caveY, 7);
        System.out.println("Cave skylight after opening: " + lightAfterOpen1);
        assertTrue(lightAfterOpen1 > 0, "Cave should have light after opening, got " + lightAfterOpen1);
        
        // Close shaft
        System.out.println("=== Closing shaft ===");
        chunk.setBlock(7, surfaceY, 7, Blocks.STONE);
        
        int lightAfterClose = chunk.getSkyLight(7, caveY, 7);
        System.out.println("Cave skylight after closing: " + lightAfterClose);
        assertEquals(0, lightAfterClose, "Cave should be dark after closing, got " + lightAfterClose);
        
        // Open shaft again
        System.out.println("=== Re-opening shaft ===");
        chunk.setBlock(7, surfaceY, 7, Blocks.AIR);
        
        int lightAfterOpen2 = chunk.getSkyLight(7, caveY, 7);
        System.out.println("Cave skylight after re-opening: " + lightAfterOpen2);
        assertTrue(lightAfterOpen2 > 0, "Cave should have light after re-opening, got " + lightAfterOpen2);
        
        // Should be same as first opening
        assertEquals(lightAfterOpen1, lightAfterOpen2, 
            "Light should be same after re-opening (" + lightAfterOpen2 + ") as first opening (" + lightAfterOpen1 + ")");
    }
    
    @Test
    @DisplayName("BUG DETECTION: Block placed over torch, then removed - light returns")
    public void testBlockOverTorchRemoved() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place torch
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        
        int initialLight = chunk.getBlockLightI(8, y + 1, 8);
        System.out.println("Light above torch initially: " + initialLight);
        assertTrue(initialLight > 0, "Should have light above torch");
        
        // Place stone above torch (blocking upward light)
        chunk.setBlock(8, y + 1, 8, Blocks.STONE);
        
        System.out.println("Light above after stone placed: " + chunk.getBlockLightI(8, y + 1, 8));
        // Stone blocks light
        
        // Check light at y+2 (through stone)
        int lightThroughStone = chunk.getBlockLightI(8, y + 2, 8);
        System.out.println("Light 2 blocks above (through stone): " + lightThroughStone);
        
        // Remove the stone
        chunk.setBlock(8, y + 1, 8, Blocks.AIR);
        
        int lightAfterRemove = chunk.getBlockLightI(8, y + 1, 8);
        System.out.println("Light above after stone removed: " + lightAfterRemove);
        assertEquals(initialLight, lightAfterRemove, 
            "Light should return after stone removed: expected " + initialLight + ", got " + lightAfterRemove);
    }
}
