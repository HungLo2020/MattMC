package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.client.renderer.chunk.VertexLightSampler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test specifically designed to detect the reported bug where:
 * - User digs down from surface (say 6 blocks)
 * - Then digs a horizontal shaft (say 30 blocks)
 * - One particular wall direction of the shaft is always darker than expected
 * - Not the ceiling or floor - specifically one of the 4 wall faces (N/S/E/W)
 * - The issue is inconsistent but tends to affect one particular face direction
 * 
 * This test checks that all wall faces receive consistent light attenuation
 * in a horizontal shaft scenario.
 */
public class HorizontalShaftWallLightingTest {
    
    private WorldLightManager worldLightManager;
    private LevelChunk chunk;
    private VertexLightSampler lightSampler;
    
    @BeforeEach
    public void setup() {
        worldLightManager = new WorldLightManager();
        chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        lightSampler = new VertexLightSampler();
        
        // Set up light accessor for the sampler
        lightSampler.setLightAccessor(new VertexLightSampler.ChunkLightAccessor() {
            @Override
            public int getSkyLightAcrossChunks(LevelChunk c, int x, int y, int z) {
                if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < 0 || y >= LevelChunk.HEIGHT) {
                    return 0;
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
     * Create a horizontal shaft scenario and verify all wall faces have consistent lighting.
     * This is designed to detect the bug where one wall direction is always darker.
     */
    @Test
    @DisplayName("BUG DETECTION: Horizontal shaft wall faces should have consistent lighting")
    public void testHorizontalShaftWallLighting() {
        int surfaceY = LevelChunk.worldYToChunkY(64);
        int shaftDepth = 6;  // Dig down 6 blocks
        int shaftY = surfaceY - shaftDepth;
        
        System.out.println("=== HORIZONTAL SHAFT WALL LIGHTING TEST ===");
        System.out.println("Surface Y (chunk-local): " + surfaceY);
        System.out.println("Shaft Y (chunk-local): " + shaftY);
        System.out.println("Shaft depth: " + shaftDepth + " blocks below surface");
        
        // Step 1: Fill the underground area with stone
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < surfaceY; y++) {
                    chunk.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Step 2: Dig vertical shaft from surface down to shaft level (at x=8, z=8)
        for (int y = shaftY; y <= surfaceY; y++) {
            chunk.setBlock(8, y, 8, Blocks.AIR);
        }
        
        System.out.println("Vertical shaft dug at (8, ?, 8)");
        System.out.println("Skylight at bottom of vertical shaft: " + chunk.getSkyLight(8, shaftY, 8));
        
        // Step 3: Dig horizontal shaft going in +X direction (east)
        // Shaft is 3 blocks wide (z=7,8,9) and 2 blocks tall
        int shaftLength = 6;  // Length of horizontal shaft
        for (int x = 8; x < 8 + shaftLength; x++) {
            for (int z = 7; z <= 9; z++) {
                chunk.setBlock(x, shaftY, z, Blocks.AIR);      // Floor level
                chunk.setBlock(x, shaftY + 1, z, Blocks.AIR);  // One block higher
            }
        }
        
        System.out.println("Horizontal shaft dug from x=8 to x=" + (8 + shaftLength - 1));
        System.out.println("Shaft width: z=7 to z=9 (3 blocks)");
        System.out.println("Shaft height: 2 blocks\n");
        
        // Step 4: Sample light at blocks along the shaft and compare wall lighting
        System.out.println("=== LIGHT VALUES ALONG SHAFT ===");
        System.out.println("Position     Sky  Block  North  South  East  West");
        System.out.println("------------ ---  -----  -----  -----  ----  ----");
        
        // Track differences for each wall direction
        Map<String, Integer> maxLightByWall = new HashMap<>();
        Map<String, Integer> minLightByWall = new HashMap<>();
        maxLightByWall.put("north", 0);
        maxLightByWall.put("south", 0);
        maxLightByWall.put("east", 0);
        maxLightByWall.put("west", 0);
        minLightByWall.put("north", 15);
        minLightByWall.put("south", 15);
        minLightByWall.put("east", 15);
        minLightByWall.put("west", 15);
        
        // Check light at each position along the center of the shaft (z=8)
        for (int x = 9; x < 8 + shaftLength - 1; x++) {  // Skip first/last blocks
            int y = shaftY;
            int z = 8;  // Center of shaft
            
            int skyLight = chunk.getSkyLight(x, y, z);
            int blockLight = chunk.getBlockLightI(x, y, z);
            
            // Sample vertex light for each wall face at this position
            // We need to create FaceData for each wall
            BlockFaceCollector.FaceData northFace = createFaceData(x, y, z, "north");
            BlockFaceCollector.FaceData southFace = createFaceData(x, y, z, "south");
            BlockFaceCollector.FaceData eastFace = createFaceData(x, y, z, "east");
            BlockFaceCollector.FaceData westFace = createFaceData(x, y, z, "west");
            
            // Sample light for corner 0 of each wall face (representative)
            float[] northLight = lightSampler.sampleVertexLight(northFace, 2, 0);
            float[] southLight = lightSampler.sampleVertexLight(southFace, 3, 0);
            float[] eastLight = lightSampler.sampleVertexLight(eastFace, 5, 0);
            float[] westLight = lightSampler.sampleVertexLight(westFace, 4, 0);
            
            // For simplicity, use skylight component (index 0)
            int nLight = (int) northLight[0];
            int sLight = (int) southLight[0];
            int eLight = (int) eastLight[0];
            int wLight = (int) westLight[0];
            
            System.out.printf("(%2d, %3d, %2d)  %2d    %2d     %2d     %2d    %2d    %2d%n",
                x, y, z, skyLight, blockLight, nLight, sLight, eLight, wLight);
            
            // Track min/max for each wall direction
            maxLightByWall.merge("north", nLight, Math::max);
            minLightByWall.merge("north", nLight, Math::min);
            maxLightByWall.merge("south", sLight, Math::max);
            minLightByWall.merge("south", sLight, Math::min);
            maxLightByWall.merge("east", eLight, Math::max);
            minLightByWall.merge("east", eLight, Math::min);
            maxLightByWall.merge("west", wLight, Math::max);
            minLightByWall.merge("west", wLight, Math::min);
        }
        
        System.out.println("\n=== WALL LIGHTING SUMMARY ===");
        System.out.println("Wall      Min   Max   Range");
        System.out.println("-------   ---   ---   -----");
        
        int maxRange = 0;
        String wallWithMaxRange = "";
        
        for (String wall : new String[]{"north", "south", "east", "west"}) {
            int min = minLightByWall.get(wall);
            int max = maxLightByWall.get(wall);
            int range = max - min;
            System.out.printf("%-9s  %2d    %2d     %2d%n", wall, min, max, range);
            
            if (range > maxRange) {
                maxRange = range;
                wallWithMaxRange = wall;
            }
        }
        
        // Calculate the average light for each wall direction
        System.out.println("\n=== CROSS-WALL COMPARISON ===");
        int avgNorth = (maxLightByWall.get("north") + minLightByWall.get("north")) / 2;
        int avgSouth = (maxLightByWall.get("south") + minLightByWall.get("south")) / 2;
        int avgEast = (maxLightByWall.get("east") + minLightByWall.get("east")) / 2;
        int avgWest = (maxLightByWall.get("west") + minLightByWall.get("west")) / 2;
        
        System.out.println("Average light: North=" + avgNorth + " South=" + avgSouth + 
                          " East=" + avgEast + " West=" + avgWest);
        
        // The bug: one wall is consistently darker
        // Check if any wall is significantly darker than others
        int[] avgs = {avgNorth, avgSouth, avgEast, avgWest};
        int maxAvg = 0;
        int minAvg = 15;
        String darkestWall = "";
        String[] wallNames = {"north", "south", "east", "west"};
        
        for (int i = 0; i < 4; i++) {
            if (avgs[i] > maxAvg) maxAvg = avgs[i];
            if (avgs[i] < minAvg) {
                minAvg = avgs[i];
                darkestWall = wallNames[i];
            }
        }
        
        int wallDifference = maxAvg - minAvg;
        System.out.println("Wall brightness difference: " + wallDifference + " levels");
        
        if (wallDifference > 0) {
            System.out.println("POTENTIAL BUG DETECTED: '" + darkestWall + "' wall is darker by " + 
                              wallDifference + " levels");
        }
        
        // This assertion checks for the bug: walls should have similar lighting
        // A difference of more than 1 level between walls indicates a bug
        // The user reports one wall is consistently darker - this tests for that
        assertTrue(wallDifference <= 1, 
            "BUG DETECTED: Wall lighting is inconsistent! " + darkestWall + 
            " wall is " + wallDifference + " levels darker than brightest wall. " +
            "North=" + avgNorth + " South=" + avgSouth + " East=" + avgEast + " West=" + avgWest +
            ". This confirms the user's reported bug where one wall face direction " +
            "receives incorrect light attenuation.");
    }
    
    /**
     * Test that opposite walls (north/south, east/west) have symmetric lighting at same distance.
     */
    @Test
    @DisplayName("BUG DETECTION: Opposite walls should have symmetric lighting")
    public void testOppositeWallSymmetry() {
        int surfaceY = LevelChunk.worldYToChunkY(64);
        int shaftY = surfaceY - 6;
        
        System.out.println("=== OPPOSITE WALL SYMMETRY TEST ===\n");
        
        // Fill underground with stone
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < surfaceY; y++) {
                    chunk.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Create a simple 3x3 room at shaft level with opening to sky
        int roomX = 8, roomZ = 8;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunk.setBlock(roomX + dx, shaftY, roomZ + dz, Blocks.AIR);
                chunk.setBlock(roomX + dx, shaftY + 1, roomZ + dz, Blocks.AIR);
            }
        }
        // Vertical shaft for light
        for (int y = shaftY; y <= surfaceY; y++) {
            chunk.setBlock(roomX, y, roomZ, Blocks.AIR);
        }
        
        // Sample light for the 4 walls of the central block
        BlockFaceCollector.FaceData northFace = createFaceData(roomX, shaftY, roomZ, "north");
        BlockFaceCollector.FaceData southFace = createFaceData(roomX, shaftY, roomZ, "south");
        BlockFaceCollector.FaceData eastFace = createFaceData(roomX, shaftY, roomZ, "east");
        BlockFaceCollector.FaceData westFace = createFaceData(roomX, shaftY, roomZ, "west");
        
        // Sample all 4 corners of each face
        System.out.println("Wall face light sampling (all 4 corners):");
        System.out.println("Face    Corner0  Corner1  Corner2  Corner3  Average");
        System.out.println("------  -------  -------  -------  -------  -------");
        
        float[] northAvg = sampleAllCorners(northFace, 2, "north");
        float[] southAvg = sampleAllCorners(southFace, 3, "south");
        float[] eastAvg = sampleAllCorners(eastFace, 5, "east");
        float[] westAvg = sampleAllCorners(westFace, 4, "west");
        
        // Check symmetry between opposite walls
        float northSouthDiff = Math.abs(northAvg[0] - southAvg[0]);
        float eastWestDiff = Math.abs(eastAvg[0] - westAvg[0]);
        
        System.out.println("\n=== SYMMETRY CHECK ===");
        System.out.println("North-South difference: " + northSouthDiff);
        System.out.println("East-West difference: " + eastWestDiff);
        
        // Opposite walls should have the same average lighting
        // Allow small tolerance for floating point
        assertTrue(northSouthDiff < 0.5f, 
            "North and South walls should have symmetric lighting but differ by " + northSouthDiff);
        assertTrue(eastWestDiff < 0.5f, 
            "East and West walls should have symmetric lighting but differ by " + eastWestDiff);
    }
    
    /**
     * Test light attenuation along a long horizontal shaft in each direction.
     * The bug might manifest in one particular shaft direction (N/S/E/W).
     */
    @Test
    @DisplayName("BUG DETECTION: Light attenuation should be consistent in all shaft directions")
    public void testShaftDirectionLightAttenuation() {
        int surfaceY = LevelChunk.worldYToChunkY(64);
        int shaftY = surfaceY - 6;
        
        System.out.println("=== SHAFT DIRECTION LIGHT ATTENUATION TEST ===\n");
        
        // Test each direction separately
        String[] directions = {"east (+X)", "west (-X)", "south (+Z)", "north (-Z)"};
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        
        Map<String, float[]> attenuationByDirection = new HashMap<>();
        
        for (int dir = 0; dir < 4; dir++) {
            // Reset chunk
            chunk = new LevelChunk(0, 0);
            chunk.setWorldLightManager(worldLightManager);
            
            // Fill with stone
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < surfaceY; y++) {
                        chunk.setBlock(x, y, z, Blocks.STONE);
                    }
                }
            }
            
            // Create vertical shaft at center
            int startX = 8, startZ = 8;
            for (int y = shaftY; y <= surfaceY; y++) {
                chunk.setBlock(startX, y, startZ, Blocks.AIR);
            }
            
            // Create horizontal shaft in this direction
            float[] lightValues = new float[5];
            for (int i = 0; i < 5; i++) {
                int x = startX + dx[dir] * i;
                int z = startZ + dz[dir] * i;
                
                if (x >= 0 && x < 16 && z >= 0 && z < 16) {
                    chunk.setBlock(x, shaftY, z, Blocks.AIR);
                    chunk.setBlock(x, shaftY + 1, z, Blocks.AIR);
                    lightValues[i] = chunk.getSkyLight(x, shaftY, z);
                }
            }
            
            attenuationByDirection.put(directions[dir], lightValues);
            
            System.out.print(directions[dir] + " shaft light: ");
            for (float v : lightValues) {
                System.out.print((int)v + " ");
            }
            System.out.println();
        }
        
        // Compare attenuation patterns between opposite directions
        float[] eastVals = attenuationByDirection.get("east (+X)");
        float[] westVals = attenuationByDirection.get("west (-X)");
        float[] southVals = attenuationByDirection.get("south (+Z)");
        float[] northVals = attenuationByDirection.get("north (-Z)");
        
        // Check that light attenuates similarly in all directions
        System.out.println("\n=== DIRECTION COMPARISON ===");
        
        float maxDiff = 0;
        String asymmetryDesc = "";
        
        for (int i = 0; i < 5; i++) {
            float ewDiff = Math.abs(eastVals[i] - westVals[i]);
            float nsDiff = Math.abs(northVals[i] - southVals[i]);
            
            if (ewDiff > maxDiff) {
                maxDiff = ewDiff;
                asymmetryDesc = "East-West at distance " + i;
            }
            if (nsDiff > maxDiff) {
                maxDiff = nsDiff;
                asymmetryDesc = "North-South at distance " + i;
            }
        }
        
        System.out.println("Maximum directional asymmetry: " + maxDiff + " at " + asymmetryDesc);
        
        assertTrue(maxDiff <= 1, 
            "BUG DETECTED: Light attenuation is asymmetric! " + asymmetryDesc + 
            " differs by " + maxDiff + " levels");
    }
    
    /**
     * Helper to create FaceData for a wall face.
     */
    private BlockFaceCollector.FaceData createFaceData(int x, int y, int z, String faceType) {
        return new BlockFaceCollector.FaceData(
            x, y, z, 
            0xFFFFFF, 1f, 1f, 
            Blocks.STONE, faceType, null,
            chunk, x, y, z
        );
    }
    
    /**
     * Sample all 4 corners of a face and print results.
     */
    private float[] sampleAllCorners(BlockFaceCollector.FaceData face, int normalIndex, String faceName) {
        float sum = 0;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-6s", faceName));
        
        for (int corner = 0; corner < 4; corner++) {
            float[] light = lightSampler.sampleVertexLight(face, normalIndex, corner);
            sb.append(String.format("   %5.1f", light[0]));
            sum += light[0];
        }
        
        float avg = sum / 4;
        sb.append(String.format("    %5.1f", avg));
        System.out.println(sb);
        
        return new float[] {avg, sum};
    }
}
