package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive light propagation tests designed to detect bugs in:
 * - BFS propagation algorithm
 * - Cross-chunk boundaries
 * - Light removal and re-propagation
 * - RGBI color handling
 * - Opacity blocking
 * 
 * These tests are specifically designed to expose common light propagation issues:
 * - Light not propagating to all reachable blocks
 * - Light values not correctly attenuating
 * - Light not being removed when sources are destroyed
 * - Light "leaking" through opaque blocks
 * - Color mixing issues in RGBI system
 */
public class LightPropagationBugDetectionTest {
    
    private WorldLightManager worldLightManager;
    private LevelChunk chunk;
    
    // Torch has RGB=(14, 11, 0) with intensity 14
    private static final int TORCH_R = 14;
    private static final int TORCH_G = 11;
    private static final int TORCH_B = 0;
    private static final int TORCH_INTENSITY = 14;
    
    @BeforeEach
    public void setup() {
        worldLightManager = new WorldLightManager();
        chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        // Ensure the neighbor accessor is set for cross-chunk tests
        worldLightManager.setNeighborAccessor((chunkX, chunkZ) -> {
            if (chunkX == 0 && chunkZ == 0) return chunk;
            return null;
        });
    }
    
    // ========== BASIC PROPAGATION TESTS ==========
    
    @Test
    @DisplayName("Light propagates exactly N-1 blocks away where N is intensity")
    public void testLightPropagationDistance() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place torch at center
        chunk.setBlock(7, y, 7, Blocks.TORCH);
        
        // Verify exact light values at each distance
        assertEquals(TORCH_INTENSITY, chunk.getBlockLightI(7, y, 7), "Source should have intensity 14");
        assertEquals(13, chunk.getBlockLightI(8, y, 7), "Distance 1 should have 13");
        assertEquals(12, chunk.getBlockLightI(9, y, 7), "Distance 2 should have 12");
        assertEquals(11, chunk.getBlockLightI(10, y, 7), "Distance 3 should have 11");
        assertEquals(10, chunk.getBlockLightI(11, y, 7), "Distance 4 should have 10");
        assertEquals(9, chunk.getBlockLightI(12, y, 7), "Distance 5 should have 9");
        assertEquals(8, chunk.getBlockLightI(13, y, 7), "Distance 6 should have 8");
        assertEquals(7, chunk.getBlockLightI(14, y, 7), "Distance 7 should have 7");
        
        // Light propagates from source with intensity 14
        // At distance d, light = max(0, 14 - d)
        // Light stops propagating when intensity reaches 1
        // So maximum reach is 13 blocks (intensity goes: 14,13,12,...,2,1,stop)
    }
    
    @Test
    @DisplayName("Light propagates in all 6 cardinal directions equally")
    public void testSixDirectionalPropagation() {
        int x = 8, y = LevelChunk.worldYToChunkY(64), z = 8;
        
        // Place torch
        chunk.setBlock(x, y, z, Blocks.TORCH);
        
        int expectedNeighbor = TORCH_INTENSITY - 1;
        
        // All 6 neighbors should have equal light
        assertEquals(expectedNeighbor, chunk.getBlockLightI(x + 1, y, z), "+X neighbor");
        assertEquals(expectedNeighbor, chunk.getBlockLightI(x - 1, y, z), "-X neighbor");
        assertEquals(expectedNeighbor, chunk.getBlockLightI(x, y + 1, z), "+Y neighbor");
        assertEquals(expectedNeighbor, chunk.getBlockLightI(x, y - 1, z), "-Y neighbor");
        assertEquals(expectedNeighbor, chunk.getBlockLightI(x, y, z + 1), "+Z neighbor");
        assertEquals(expectedNeighbor, chunk.getBlockLightI(x, y, z - 1), "-Z neighbor");
    }
    
    @Test
    @DisplayName("Diagonal propagation follows Manhattan distance, not Euclidean")
    public void testDiagonalPropagation() {
        int x = 8, y = LevelChunk.worldYToChunkY(64), z = 8;
        
        chunk.setBlock(x, y, z, Blocks.TORCH);
        
        // Diagonal at (x+1, y, z+1) is Manhattan distance 2, should have light - 2
        int diagonalLight = chunk.getBlockLightI(x + 1, y, z + 1);
        assertEquals(TORCH_INTENSITY - 2, diagonalLight, "Diagonal should have 12 (14-2)");
        
        // 3D diagonal (x+1, y+1, z+1) is Manhattan distance 3
        int diagonal3D = chunk.getBlockLightI(x + 1, y + 1, z + 1);
        assertEquals(TORCH_INTENSITY - 3, diagonal3D, "3D diagonal should have 11 (14-3)");
    }
    
    // ========== RGBI COLOR TESTS ==========
    
    @Test
    @DisplayName("RGB color values propagate correctly (not just intensity)")
    public void testRGBColorPropagation() {
        int x = 8, y = LevelChunk.worldYToChunkY(64), z = 8;
        
        chunk.setBlock(x, y, z, Blocks.TORCH);
        
        // At source
        assertEquals(TORCH_R, chunk.getBlockLightR(x, y, z), "Source red");
        assertEquals(TORCH_G, chunk.getBlockLightG(x, y, z), "Source green");
        assertEquals(TORCH_B, chunk.getBlockLightB(x, y, z), "Source blue");
        
        // At neighbor - RGB stays the same, only intensity decreases
        assertEquals(TORCH_R, chunk.getBlockLightR(x + 1, y, z), "Neighbor red should remain");
        assertEquals(TORCH_G, chunk.getBlockLightG(x + 1, y, z), "Neighbor green should remain");
        assertEquals(TORCH_B, chunk.getBlockLightB(x + 1, y, z), "Neighbor blue should remain");
        assertEquals(TORCH_INTENSITY - 1, chunk.getBlockLightI(x + 1, y, z), "Neighbor intensity should decrease");
    }
    
    // ========== LIGHT REMOVAL TESTS ==========
    
    @Test
    @DisplayName("Removing a torch removes all its propagated light")
    public void testCompleteLightRemoval() {
        int x = 8, y = LevelChunk.worldYToChunkY(64), z = 8;
        
        // Place torch and verify light exists
        chunk.setBlock(x, y, z, Blocks.TORCH);
        assertTrue(chunk.getBlockLightI(x, y, z) > 0, "Light should exist before removal");
        assertTrue(chunk.getBlockLightI(x + 3, y, z) > 0, "Propagated light should exist");
        
        // Remove torch
        chunk.setBlock(x, y, z, Blocks.AIR);
        
        // All light should be removed
        assertEquals(0, chunk.getBlockLightI(x, y, z), "Source light should be 0");
        assertEquals(0, chunk.getBlockLightI(x + 1, y, z), "Neighbor light should be 0");
        assertEquals(0, chunk.getBlockLightI(x + 2, y, z), "Distance 2 light should be 0");
        assertEquals(0, chunk.getBlockLightI(x + 3, y, z), "Distance 3 light should be 0");
    }
    
    @Test
    @DisplayName("Removing one torch preserves light from nearby torch")
    public void testPartialLightRemoval() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place two torches with overlapping light
        chunk.setBlock(4, y, 8, Blocks.TORCH);  // Torch A
        chunk.setBlock(12, y, 8, Blocks.TORCH); // Torch B
        
        // Position 8 gets light from both
        int lightBefore = chunk.getBlockLightI(8, y, 8);
        assertTrue(lightBefore > 0, "Middle should have light from both torches");
        
        // Remove torch A
        chunk.setBlock(4, y, 8, Blocks.AIR);
        
        // Middle should still have light from torch B (4 blocks away)
        int lightAfter = chunk.getBlockLightI(8, y, 8);
        assertTrue(lightAfter > 0, "Middle should still have light from remaining torch");
        assertEquals(TORCH_INTENSITY - 4, lightAfter, "Light should come from torch B at distance 4");
        
        // Position 3 should still have light from torch B 
        // Torch B is at x=12, position 3 is at x=3
        // Distance = |12 - 3| = 9, so light = 14 - 9 = 5
        int lightAtPos3 = chunk.getBlockLightI(3, y, 8);
        assertEquals(5, lightAtPos3, "Position 3 should have light=5 from remaining torch B (distance 9)");
    }
    
    // ========== OPACITY BLOCKING TESTS ==========
    
    @Test
    @DisplayName("Opaque blocks completely stop light propagation")
    public void testOpaqueBlocksStopLight() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place stone wall
        chunk.setBlock(10, y, 8, Blocks.STONE);
        
        // Place torch on one side
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        
        // Stone should block light
        assertEquals(0, chunk.getBlockLightI(10, y, 8), "Opaque block should have 0 light");
        
        // Light should not reach beyond the stone
        int lightBeyondWall = chunk.getBlockLightI(11, y, 8);
        
        // The light can go around the stone via alternate paths
        // But directly through is blocked
        // Check that at least directly behind the stone is darker than expected
        // without the wall, position 11 would have 14 - 3 = 11
        assertTrue(lightBeyondWall < 11, "Light beyond wall should be reduced");
    }
    
    @Test
    @DisplayName("Light routes around single opaque block")
    public void testLightAroundSingleBlock() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place single stone block
        chunk.setBlock(9, y, 8, Blocks.STONE);
        
        // Place torch
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        
        // Light should route around the stone
        // Position (10, y, 8) can be reached via (8)->[(9,y,7) or (9,y,9)]->(10,y,8)
        // Path length is 3, so light should be 14 - 3 = 11
        int lightAround = chunk.getBlockLightI(10, y, 8);
        assertTrue(lightAround > 0, "Light should route around obstacle");
    }
    
    @Test
    @DisplayName("Enclosed room receives no light")
    public void testEnclosedRoomNoLight() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Create a fully enclosed room around position (8, y, 8)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    chunk.setBlock(8 + dx, y + dy, 8 + dz, Blocks.STONE);
                }
            }
        }
        
        // Place torch outside the room
        chunk.setBlock(5, y, 8, Blocks.TORCH);
        
        // Inside the room should have no light
        assertEquals(0, chunk.getBlockLightI(8, y, 8), "Enclosed room should have no light");
    }
    
    // ========== EDGE CASE TESTS ==========
    
    @Test
    @DisplayName("Light at chunk edge (x=15)")
    public void testLightAtChunkEdgeX() {
        int y = LevelChunk.worldYToChunkY(64);
        
        chunk.setBlock(15, y, 8, Blocks.TORCH);
        
        assertEquals(TORCH_INTENSITY, chunk.getBlockLightI(15, y, 8), "Torch at edge should have full light");
        assertEquals(TORCH_INTENSITY - 1, chunk.getBlockLightI(14, y, 8), "Inward neighbor should have -1 light");
    }
    
    @Test
    @DisplayName("Light at chunk edge (z=0)")
    public void testLightAtChunkEdgeZ() {
        int y = LevelChunk.worldYToChunkY(64);
        
        chunk.setBlock(8, y, 0, Blocks.TORCH);
        
        assertEquals(TORCH_INTENSITY, chunk.getBlockLightI(8, y, 0), "Torch at edge should have full light");
        assertEquals(TORCH_INTENSITY - 1, chunk.getBlockLightI(8, y, 1), "Inward neighbor should have -1 light");
    }
    
    @Test
    @DisplayName("Light at Y extremes")
    public void testLightAtYExtremes() {
        // Near bottom
        int bottomY = 1; // y=0 is the very bottom
        chunk.setBlock(8, bottomY, 8, Blocks.TORCH);
        assertEquals(TORCH_INTENSITY, chunk.getBlockLightI(8, bottomY, 8), "Torch near bottom");
        assertEquals(TORCH_INTENSITY - 1, chunk.getBlockLightI(8, bottomY + 1, 8), "Light propagates up");
        
        // Remove torch
        chunk.setBlock(8, bottomY, 8, Blocks.AIR);
        
        // Near top
        int topY = LevelChunk.HEIGHT - 2;
        chunk.setBlock(8, topY, 8, Blocks.TORCH);
        assertEquals(TORCH_INTENSITY, chunk.getBlockLightI(8, topY, 8), "Torch near top");
        assertEquals(TORCH_INTENSITY - 1, chunk.getBlockLightI(8, topY - 1, 8), "Light propagates down");
    }
    
    // ========== MULTIPLE SOURCE INTERACTION TESTS ==========
    
    @Test
    @DisplayName("Two torches - light takes brightest value")
    public void testTwoTorchesMaxLight() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place two torches at different distances from a point
        chunk.setBlock(6, y, 8, Blocks.TORCH);  // Distance 2 from (8,y,8)
        chunk.setBlock(11, y, 8, Blocks.TORCH); // Distance 3 from (8,y,8)
        
        // Position (8, y, 8) should have light from closer torch
        // Distance 2 gives 14 - 2 = 12
        int lightAtMiddle = chunk.getBlockLightI(8, y, 8);
        assertEquals(12, lightAtMiddle, "Should have light from closer torch");
    }
    
    @Test
    @DisplayName("Torch placement after another should update light correctly")
    public void testSequentialTorchPlacement() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place first torch
        chunk.setBlock(5, y, 8, Blocks.TORCH);
        int lightBefore = chunk.getBlockLightI(8, y, 8); // Distance 3, should be 11
        assertEquals(TORCH_INTENSITY - 3, lightBefore, "Initial light");
        
        // Place second torch closer
        chunk.setBlock(7, y, 8, Blocks.TORCH);
        int lightAfter = chunk.getBlockLightI(8, y, 8); // Distance 1, should be 13
        assertEquals(TORCH_INTENSITY - 1, lightAfter, "Should have brighter light from closer torch");
    }
    
    // ========== REGRESSION TESTS FOR COMMON BUGS ==========
    
    @Test
    @DisplayName("BUG: Light not propagating through air gap")
    public void testLightThroughAirGap() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Create a wall with a 1-block gap at y level
        // Wall blocks at z=7 and z=9, gap at z=8
        chunk.setBlock(10, y, 7, Blocks.STONE);
        chunk.setBlock(10, y, 9, Blocks.STONE);
        // Position (10, y, 8) is air - the gap
        
        // Place torch
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        
        // Direct path from (8,y,8) to (10,y,8) is just 2 blocks
        // So light should be 14 - 2 = 12
        int lightAtGap = chunk.getBlockLightI(10, y, 8);
        assertEquals(TORCH_INTENSITY - 2, lightAtGap, "Light should reach the air gap (distance 2)");
        
        // Light should continue through
        int lightBeyond = chunk.getBlockLightI(12, y, 8);
        assertEquals(TORCH_INTENSITY - 4, lightBeyond, "Light should continue through gap (distance 4)");
    }
    
    @Test
    @DisplayName("BUG: Light leaking after removal")
    public void testNoLightLeakAfterRemoval() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place and remove multiple torches
        for (int i = 0; i < 5; i++) {
            chunk.setBlock(8, y, 8, Blocks.TORCH);
            chunk.setBlock(8, y, 8, Blocks.AIR);
        }
        
        // Should have no residual light
        assertEquals(0, chunk.getBlockLightI(8, y, 8), "No residual light at source");
        assertEquals(0, chunk.getBlockLightI(9, y, 8), "No residual light at neighbors");
        assertEquals(0, chunk.getBlockLightI(7, y, 8), "No residual light at neighbors");
    }
    
    @Test
    @DisplayName("BUG: Incorrect attenuation in corners")
    public void testCorrectAttenuationInCorners() {
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place torch at corner of chunk
        chunk.setBlock(0, y, 0, Blocks.TORCH);
        
        // Check attenuation is consistent
        assertEquals(TORCH_INTENSITY, chunk.getBlockLightI(0, y, 0), "Source");
        assertEquals(TORCH_INTENSITY - 1, chunk.getBlockLightI(1, y, 0), "X+1");
        assertEquals(TORCH_INTENSITY - 1, chunk.getBlockLightI(0, y, 1), "Z+1");
        assertEquals(TORCH_INTENSITY - 2, chunk.getBlockLightI(1, y, 1), "Diagonal");
    }
}
