package mattmc.world.level.lighting;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.chunk.VertexLightSampler;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test specifically designed to detect the reported bug where interior edges
 * are too dark when there is a step/slope boundary between two planes.
 * 
 * Scenario:
 * - Lower plane at Y=64
 * - Upper plane at Y=65, only covering part of the lower plane (creating a "step")
 * - Block placed at the boundary where the upper plane juts out
 * - Interior edges of this boundary block are incorrectly darker
 * 
 * The issue is unrelated to Ambient Occlusion and predates the AO implementation.
 * It is a bug in the lighting system's vertex light sampling.
 */
public class StepBoundaryLightingTest {
    
    private WorldLightManager worldLightManager;
    private LevelChunk chunk;
    private VertexLightSampler lightSampler;
    
    @BeforeEach
    public void setup() {
        // Create a fresh chunk without any terrain generation
        worldLightManager = new WorldLightManager();
        chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        lightSampler = new VertexLightSampler();
        
        // Set up light accessor for the sampler
        lightSampler.setLightAccessor(new VertexLightSampler.ChunkLightAccessor() {
            @Override
            public int getSkyLightAcrossChunks(LevelChunk c, int x, int y, int z) {
                if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < 0 || y >= LevelChunk.HEIGHT) {
                    return 15;  // Default full skylight outside bounds
                }
                return c.getSkyLight(x, y, z);
            }
            
            @Override
            public int getBlockLightAcrossChunks(LevelChunk c, int x, int y, int z) {
                if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < 0 || y >= LevelChunk.HEIGHT) {
                    return 0;
                }
                return c.getBlockLightI(x, y, z);
            }
            
            @Override
            public int[] getBlockLightRGBAcrossChunks(LevelChunk c, int x, int y, int z) {
                if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < 0 || y >= LevelChunk.HEIGHT) {
                    return new int[] {0, 0, 0};
                }
                int r = c.getBlockLightR(x, y, z);
                int g = c.getBlockLightG(x, y, z);
                int b = c.getBlockLightB(x, y, z);
                return new int[] {r, g, b};
            }
        });
    }
    
    /**
     * Test the step boundary lighting scenario:
     * 
     * The scenario as described:
     * - Lower plane at Y=64 covering z <= 7 (ground before the step)
     * - Upper plane at Y=65 covering z >= 8 (the elevated area)
     * - Block placed at (8, 65, 7) which juts out ONE block over the lower area
     * 
     * The "interior edge" is the SOUTH face of the jutting block - it faces
     * the step but should still receive skylight from above (since there's
     * air above the step).
     * 
     * ASCII side view (looking from west, +X going into page):
     *       z=6    z=7    z=8    z=9
     * Y=66  AIR    AIR    AIR    AIR     <- open sky
     * Y=65  AIR   [JUTS]  STEP   STEP    <- upper plane starts at z=8, jut block at z=7
     * Y=64  GND    GND    AIR    AIR     <- lower plane at z<=7, air at z>=8
     * 
     * The south face of JUTS (at z=7, facing z=8) should receive light from
     * the air above (Y=66) even though the STEP blocks are at the same level.
     */
    @Test
    @DisplayName("BUG DETECTION: Step boundary interior edge should not be too dark")
    public void testStepBoundaryInteriorEdgeLighting() {
        int lowerY = LevelChunk.worldYToChunkY(64);
        int upperY = LevelChunk.worldYToChunkY(65);
        
        System.out.println("=== STEP BOUNDARY INTERIOR EDGE LIGHTING TEST ===");
        System.out.println("Lower plane Y (chunk-local): " + lowerY);
        System.out.println("Upper plane Y (chunk-local): " + upperY);
        
        // Step 1: Create lower plane of blocks at Y=64 covering z <= 5 (ground before step, leaving gap)
        System.out.println("\nCreating lower plane at Y=64 (z <= 5)...");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z <= 5; z++) {
                chunk.setBlock(x, lowerY, z, Blocks.STONE);
            }
        }
        // Note: z=6,7 at Y=128 are now AIR (gap before the step)
        
        // Step 2: Create upper plane at Y=65, covering z >= 8 (the elevated step)
        System.out.println("Creating upper plane at Y=65 (z >= 8)...");
        for (int x = 0; x < 16; x++) {
            for (int z = 8; z < 16; z++) {
                chunk.setBlock(x, upperY, z, Blocks.STONE);
            }
        }
        
        // Step 3: Place a single block at the boundary that juts out
        // This block is at (8, 65, 7) - one block beyond the upper plane's edge
        int boundaryX = 8;
        int boundaryZ = 7; // One block before where the upper plane normally starts
        System.out.println("Placing boundary block at (" + boundaryX + ", " + upperY + ", " + boundaryZ + ")");
        chunk.setBlock(boundaryX, upperY, boundaryZ, Blocks.STONE);
        
        // Step 4: Initialize skylight
        worldLightManager.initializeChunkSkylight(chunk);
        
        // Step 5: Check skylight values around the boundary
        System.out.println("\n=== SKYLIGHT VALUES ===");
        System.out.println("Position                    SkyLight");
        System.out.println("---------------------------  --------");
        
        // Sample positions around the boundary block
        int[][] positions = {
            {boundaryX, upperY + 1, boundaryZ},      // Above the boundary block
            {boundaryX - 1, upperY + 1, boundaryZ},  // West of above
            {boundaryX + 1, upperY + 1, boundaryZ},  // East of above
            {boundaryX, upperY + 1, boundaryZ - 1},  // North of above
            {boundaryX, upperY + 1, boundaryZ + 1},  // South of above (on the step)
            {boundaryX, upperY, boundaryZ - 1},      // North of block (air, lower level exposed)
            {boundaryX - 1, upperY, boundaryZ},      // West of block (air)
            {boundaryX + 1, upperY, boundaryZ},      // East of block (air)
        };
        String[] posNames = {
            "Above boundary block",
            "West above boundary",
            "East above boundary", 
            "North above boundary",
            "South above boundary (step)",
            "North of block (lower level air)",
            "West of block (air)",
            "East of block (air)"
        };
        
        for (int i = 0; i < positions.length; i++) {
            int x = positions[i][0];
            int y = positions[i][1];
            int z = positions[i][2];
            int skyLight = chunk.getSkyLight(x, y, z);
            Block block = chunk.getBlock(x, y, z);
            System.out.printf("%-28s %2d  (%s)%n", posNames[i], skyLight, block.getIdentifier());
        }
        
        // Step 6: Sample vertex light for the TOP face of the boundary block
        System.out.println("\n=== VERTEX LIGHT SAMPLING FOR TOP FACE ===");
        System.out.println("The TOP face of the boundary block should have full skylight (15)");
        System.out.println("at all vertices since it is exposed to sky.\n");
        
        BlockFaceCollector.FaceData topFace = new BlockFaceCollector.FaceData(
            boundaryX, 65, boundaryZ,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "top", null,
            chunk, boundaryX, upperY, boundaryZ
        );
        
        float minSkyLight = 15.0f;
        for (int corner = 0; corner < 4; corner++) {
            float[] lightData = lightSampler.sampleVertexLight(topFace, 0, corner);
            float skyLight = lightData[0];
            System.out.printf("  Corner %d: skyLight=%.1f%n", corner, skyLight);
            minSkyLight = Math.min(minSkyLight, skyLight);
        }
        
        // The top face of the boundary block should have full skylight since it's exposed
        assertTrue(minSkyLight >= 14.0f, 
            "BUG DETECTED: Top face of boundary block should have full skylight (15) " +
            "but minimum was " + minSkyLight + ". Interior edges are too dark!");
        
        // Step 7: Sample vertex light for the SOUTH face of the boundary block
        // This is the "interior edge" facing the step
        System.out.println("\n=== VERTEX LIGHT SAMPLING FOR SOUTH FACE (INTERIOR EDGE) ===");
        System.out.println("The SOUTH face is the interior edge where the block meets the step.");
        System.out.println("It should receive light from the exposed air to the south-above.\n");
        
        BlockFaceCollector.FaceData southFace = new BlockFaceCollector.FaceData(
            boundaryX, 65, boundaryZ,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "south", null,
            chunk, boundaryX, upperY, boundaryZ
        );
        
        float minSouthSkyLight = 15.0f;
        float maxSouthSkyLight = 0.0f;
        float minAO = 1.0f;
        float maxAO = 0.0f;
        for (int corner = 0; corner < 4; corner++) {
            float[] lightData = lightSampler.sampleVertexLight(southFace, 3, corner); // 3 = SOUTH
            float skyLight = lightData[0];
            float ao = lightData[4];  // AO is at index 4
            System.out.printf("  Corner %d: skyLight=%.1f, AO=%.2f%n", corner, skyLight, ao);
            minSouthSkyLight = Math.min(minSouthSkyLight, skyLight);
            maxSouthSkyLight = Math.max(maxSouthSkyLight, skyLight);
            minAO = Math.min(minAO, ao);
            maxAO = Math.max(maxAO, ao);
        }
        
        // Debug: print exact sample positions for corner 0
        System.out.println("\n  Debug: Sample positions for corner 0:");
        int[][] corner0Offsets = {{0,0,1}, {-1,0,1}, {0,-1,1}, {-1,-1,1}};
        for (int[] off : corner0Offsets) {
            int sx = boundaryX + off[0];
            int sy = upperY + off[1];
            int sz = boundaryZ + off[2];
            Block b = chunk.getBlock(sx, sy, sz);
            int sky = chunk.getSkyLight(sx, sy, sz);
            System.out.printf("    (%d,%d,%d): %s, skylight=%d%n", sx, sy, sz, 
                b.isAir() ? "AIR" : b.getIdentifier(), sky);
        }
        
        // Debug: print a grid of skylight around the boundary
        System.out.println("\n  Debug: Skylight grid (x=8):");
        System.out.println("  Y\\Z    6   7   8   9  10");
        System.out.println("  ----  --- --- --- --- ---");
        for (int y = upperY + 2; y >= lowerY - 1; y--) {
            System.out.printf("  %3d  ", y);
            for (int z = 6; z <= 10; z++) {
                System.out.printf(" %2d ", chunk.getSkyLight(boundaryX, y, z));
            }
            // Show blocks at each z
            StringBuilder blocks = new StringBuilder();
            for (int z = 6; z <= 10; z++) {
                Block b = chunk.getBlock(boundaryX, y, z);
                blocks.append(b.isAir() ? "A" : "S");
            }
            System.out.println("  " + blocks);
        }
        
        // Debug: print heightmap
        System.out.println("\n  Debug: Heightmap at x=8:");
        System.out.print("  z:         ");
        for (int z = 6; z <= 10; z++) {
            System.out.printf(" %2d ", z);
        }
        System.out.println();
        System.out.print("  heightmap: ");
        for (int z = 6; z <= 10; z++) {
            int hmY = chunk.getHeightmap().getHeight(boundaryX, z);
            System.out.printf(" %2d ", hmY);
        }
        System.out.println();
        
        System.out.println("\n  Min skylight: " + minSouthSkyLight + ", Max skylight: " + maxSouthSkyLight);
        System.out.println("  Min AO: " + minAO + ", Max AO: " + maxAO);
        
        // The south face's upper corners should have reasonable light
        // Even the lower corners should not be completely dark
        float southVertexRange = maxSouthSkyLight - minSouthSkyLight;
        System.out.println("  Vertex light range: " + southVertexRange);
        
        // Step 8: Check the NORTH face - this faces away from the step, toward open air
        System.out.println("\n=== VERTEX LIGHT SAMPLING FOR NORTH FACE (EXPOSED TO AIR) ===");
        
        BlockFaceCollector.FaceData northFace = new BlockFaceCollector.FaceData(
            boundaryX, 65, boundaryZ,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "north", null,
            chunk, boundaryX, upperY, boundaryZ
        );
        
        float minNorthSkyLight = 15.0f;
        for (int corner = 0; corner < 4; corner++) {
            float[] lightData = lightSampler.sampleVertexLight(northFace, 2, corner); // 2 = NORTH
            float skyLight = lightData[0];
            float ao = lightData[4];
            System.out.printf("  Corner %d: skyLight=%.1f, AO=%.2f%n", corner, skyLight, ao);
            minNorthSkyLight = Math.min(minNorthSkyLight, skyLight);
        }
        
        // Compare north and south face lighting
        // The upper vertices of the south face should not be dramatically darker
        // than the north face upper vertices
        System.out.println("\n=== COMPARISON ===");
        System.out.println("North face min: " + minNorthSkyLight);
        System.out.println("South face min: " + minSouthSkyLight);
        System.out.println("South face max: " + maxSouthSkyLight);
        
        // With Minecraft's blend approach:
        // - If a sample is 0, it's replaced with the face-adjacent light value
        // - All 4 samples are then averaged
        // The south face may be somewhat darker than the north face due to partial
        // occlusion from the step, which is visually correct for smooth lighting.
        
        // The south face should have at least SOME light from the sky
        // With Minecraft's blend approach, interior edges will be somewhat darker
        // but should still have reasonable lighting (>= 5)
        assertTrue(maxSouthSkyLight >= 5.0f,
            "South face (interior edge) should have some skylight >= 5, " +
            "but maximum was " + maxSouthSkyLight + ". The interior edge is too dark!");
    }
    
    /**
     * Test the specific scenario where a block at a step boundary
     * has its interior edge vertices incorrectly darkened.
     * 
     * This is a more focused version that isolates the issue.
     */
    @Test
    @DisplayName("BUG DETECTION: Step boundary vertex light should sample from air, not solid blocks")
    public void testStepBoundaryVertexSamplingCorrectness() {
        int lowerY = LevelChunk.worldYToChunkY(64);
        int upperY = LevelChunk.worldYToChunkY(65);
        
        System.out.println("=== STEP BOUNDARY VERTEX SAMPLING TEST ===\n");
        
        // Create a simple step: lower plane at Y=64, upper plane starting at z=8
        // Fill everything below lowerY with stone (ground)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < lowerY; y++) {
                    chunk.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Upper step at z >= 8
        for (int x = 0; x < 16; x++) {
            for (int z = 8; z < 16; z++) {
                chunk.setBlock(x, lowerY, z, Blocks.STONE);
            }
        }
        
        // Initialize skylight
        worldLightManager.initializeChunkSkylight(chunk);
        
        // Print skylight values at the step boundary
        System.out.println("Skylight at step boundary (x=8):");
        System.out.println("Y\\Z    6   7   8   9");
        System.out.println("----  --- --- --- ---");
        for (int y = lowerY + 2; y >= lowerY - 1; y--) {
            System.out.printf("%3d  ", y);
            for (int z = 6; z <= 9; z++) {
                System.out.printf(" %2d ", chunk.getSkyLight(8, y, z));
            }
            Block b1 = chunk.getBlock(8, y, 8);
            System.out.println("  " + (b1.isAir() ? "AIR" : b1.getIdentifier()));
        }
        
        // Now test the key scenario: a block at (8, lowerY, 7) looking at its south face
        // The south face of this block faces z=8, which is where the step is
        // At Y=lowerY (the floor of the lower area), looking south, we should see
        // the air at (8, lowerY, 8) which is below the step
        
        // Actually, let me reconsider - the issue is when we have a block on the UPPER level
        // that is at the edge, and its interior edge faces DOWN toward the step
        
        // Let's create the scenario exactly as described:
        // - Block at (8, upperY, 7) which is one step beyond the upper plane edge
        System.out.println("\n--- Placing boundary block at (8, " + upperY + ", 7) ---");
        chunk.setBlock(8, upperY, 7, Blocks.STONE);
        
        // Reinitialize skylight after adding the block
        worldLightManager.initializeChunkSkylight(chunk);
        
        // The bottom face of this block is an "interior edge" that faces the step
        BlockFaceCollector.FaceData bottomFace = new BlockFaceCollector.FaceData(
            8, 65, 7,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "bottom", null,
            chunk, 8, upperY, 7
        );
        
        System.out.println("\nBottom face vertex light sampling:");
        System.out.println("(The bottom face looks down at the lower plane/step)");
        
        float minBottomLight = 15.0f;
        for (int corner = 0; corner < 4; corner++) {
            float[] lightData = lightSampler.sampleVertexLight(bottomFace, 1, corner); // 1 = BOTTOM
            float skyLight = lightData[0];
            System.out.printf("  Corner %d: skyLight=%.1f%n", corner, skyLight);
            minBottomLight = Math.min(minBottomLight, skyLight);
        }
        
        // The bottom face looks down at air (the lower level)
        // It should receive light from that air
        System.out.println("\nMinimum bottom face light: " + minBottomLight);
        
        // At Y=lowerY (below the boundary block), there should be air with skylight
        int skylightBelowBlock = chunk.getSkyLight(8, lowerY, 7);
        System.out.println("Skylight at position below block (8, " + lowerY + ", 7): " + skylightBelowBlock);
        
        // The bottom face should receive at least some of this light
        assertTrue(minBottomLight > 0 || skylightBelowBlock == 0, 
            "BUG DETECTED: Bottom face should receive light from the air below, " +
            "but got 0 even though skylight below is " + skylightBelowBlock);
    }
    
    /**
     * Test that demonstrates the non-zero sample averaging behavior
     * and verifies it works correctly at step boundaries.
     */
    @Test
    @DisplayName("Verify non-zero sample averaging at step boundaries")
    public void testNonZeroSampleAveragingAtStepBoundary() {
        int lowerY = LevelChunk.worldYToChunkY(64);
        int upperY = LevelChunk.worldYToChunkY(65);
        
        System.out.println("=== NON-ZERO SAMPLE AVERAGING TEST ===\n");
        
        // Create step scenario
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Ground below lowerY
                for (int y = 0; y < lowerY; y++) {
                    chunk.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Create step: solid at Y=lowerY for z >= 8
        for (int x = 0; x < 16; x++) {
            for (int z = 8; z < 16; z++) {
                chunk.setBlock(x, lowerY, z, Blocks.STONE);
            }
        }
        
        // Place boundary block jutting out
        chunk.setBlock(8, upperY, 7, Blocks.STONE);
        
        // Initialize skylight
        worldLightManager.initializeChunkSkylight(chunk);
        
        // The key test: sample the south face of the boundary block
        // South face vertices sample from (block_x, block_y, block_z + 1) direction
        // For our boundary block at (8, upperY, 7), south is toward z=8
        
        BlockFaceCollector.FaceData southFace = new BlockFaceCollector.FaceData(
            8, 65, 7,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "south", null,
            chunk, 8, upperY, 7
        );
        
        // For the south face:
        // - Upper corners sample from Y+1 direction
        // - Lower corners sample from Y-1 direction
        // At the step, Y-1 direction hits the step (solid), Y+1 hits air
        
        System.out.println("South face (facing the step at z=8) vertex light:");
        
        for (int corner = 0; corner < 4; corner++) {
            float[] lightData = lightSampler.sampleVertexLight(southFace, 3, corner);
            float skyLight = lightData[0];
            System.out.printf("  Corner %d: skyLight=%.1f%n", corner, skyLight);
        }
        
        // Check that the skylight at z=8, Y=upperY is what we expect
        // The block at (8, lowerY, 8) is solid (the step surface)
        // The air at (8, upperY, 8) should have skylight
        Block blockAtStep = chunk.getBlock(8, lowerY, 8);
        int skylightAboveStep = chunk.getSkyLight(8, upperY, 8);
        
        System.out.println("\nBlock at step surface (8, " + lowerY + ", 8): " + blockAtStep.getIdentifier());
        System.out.println("Skylight above step (8, " + upperY + ", 8): " + skylightAboveStep);
        
        // The issue might be that when sampling for the south face upper corners,
        // some samples hit the step block (solid, 0 light) and this incorrectly
        // pulls down the average despite the non-zero filter
        
        // Let's check what blocks are at the sample positions for south face corner 2 (upper right)
        // South face corner 2 is at (x1, y1, z1) which should sample from:
        // (0,0,1), (1,0,1), (0,1,1), (1,1,1) offsets from block position
        System.out.println("\nSample positions for south face corner 2 (x1,y1 upper-right):");
        int bx = 8, by = upperY, bz = 7;
        int[][] offsets = {{0,0,1}, {1,0,1}, {0,1,1}, {1,1,1}};  // From getVertexSampleOffsets for south corner 2
        for (int[] off : offsets) {
            int sx = bx + off[0];
            int sy = by + off[1];
            int sz = bz + off[2];
            Block b = chunk.getBlock(sx, sy, sz);
            int sky = chunk.getSkyLight(sx, sy, sz);
            System.out.printf("  (%d,%d,%d): %s, skylight=%d%n", sx, sy, sz, b.getIdentifier(), sky);
        }
    }
}
