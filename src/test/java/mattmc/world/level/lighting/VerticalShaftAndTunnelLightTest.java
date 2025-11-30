package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.registries.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test designed to validate light propagation in vertical shafts and horizontal tunnels.
 * 
 * Scenario:
 * 1. Start at (0, 64, -1), dig straight down to (0, 52, -1) - 12 block vertical shaft
 * 2. Dig a 1×2 (1 wide, 2 tall) tunnel from the bottom to (0, 52, -12) in -Z direction
 * 3. Dig similar tunnels in the other 3 cardinal directions (+X, -X, +Z)
 * 4. Expand all tunnels to 3 blocks wide and verify light propagates into new spaces
 * 
 * This tests:
 * - Skylight propagation down a vertical shaft
 * - Skylight attenuation in horizontal tunnels
 * - Light propagation when expanding tunnels (breaking wall blocks)
 */
public class VerticalShaftAndTunnelLightTest {
    
    private Level level;
    private Path tempDir;
    
    // Test coordinates (world coordinates)
    private static final int START_X = 0;
    private static final int START_WORLD_Y = 64;
    private static final int BOTTOM_WORLD_Y = 52;
    private static final int START_Z = -1;
    private static final int TUNNEL_END_Z = -12;
    // Tunnel length: from z=-1 to z=-12 we dig at each position, that's 12 positions (including both ends)
    // But for iteration, we use 12 as the length since we start at 0 and go to length-1
    private static final int TUNNEL_LENGTH = Math.abs(TUNNEL_END_Z - START_Z) + 1; // 12 blocks
    
    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("mattmc-vertical-shaft-test-");
        level = new Level();
        level.setWorldDirectory(tempDir);
        level.setSeed(12345L);
    }
    
    /**
     * Helper to convert world Y to chunk-local Y
     */
    private int toChunkY(int worldY) {
        return LevelChunk.worldYToChunkY(worldY);
    }
    
    /**
     * Helper to get skylight at world coordinates (handles chunk boundary crossing)
     */
    private int getSkyLight(int worldX, int worldY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, 16);
        int chunkZ = Math.floorDiv(worldZ, 16);
        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        return chunk.getSkyLight(localX, toChunkY(worldY), localZ);
    }
    
    /**
     * Helper to set block at world coordinates
     */
    private void setBlock(int worldX, int worldY, int worldZ, mattmc.world.level.block.Block block) {
        level.setBlock(worldX, toChunkY(worldY), worldZ, block);
    }
    
    /**
     * Fill a region with stone to simulate underground terrain.
     */
    private void fillWithStone(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
    }
    
    /**
     * Dig a vertical shaft from startY down to endY at (x, z)
     */
    private void digVerticalShaft(int x, int startY, int endY, int z) {
        for (int y = startY; y >= endY; y--) {
            setBlock(x, y, z, Blocks.AIR);
        }
    }
    
    /**
     * Dig a 1x2 (1 wide, 2 tall) tunnel in a direction.
     * direction: 0=+X, 1=-X, 2=+Z, 3=-Z
     */
    private void dig1x2Tunnel(int startX, int startY, int startZ, int direction, int length) {
        int dx = 0, dz = 0;
        switch (direction) {
            case 0: dx = 1; break;  // +X
            case 1: dx = -1; break; // -X
            case 2: dz = 1; break;  // +Z
            case 3: dz = -1; break; // -Z
        }
        
        for (int i = 0; i < length; i++) {
            int x = startX + dx * i;
            int z = startZ + dz * i;
            setBlock(x, startY, z, Blocks.AIR);     // Floor level
            setBlock(x, startY + 1, z, Blocks.AIR); // One block higher
        }
    }
    
    /**
     * Expand a 1x2 tunnel to 3x2 (3 wide, 2 tall) by breaking walls on each side.
     * direction: 0=+X, 1=-X, 2=+Z, 3=-Z
     */
    private void expandTunnelTo3Wide(int startX, int startY, int startZ, int direction, int length) {
        int dx = 0, dz = 0;
        int wallDx = 0, wallDz = 0;
        
        switch (direction) {
            case 0: // +X - walls are in Z direction
            case 1: // -X - walls are in Z direction
                dx = (direction == 0) ? 1 : -1;
                wallDz = 1; // Walls perpendicular to tunnel direction
                break;
            case 2: // +Z - walls are in X direction
            case 3: // -Z - walls are in X direction
                dz = (direction == 2) ? 1 : -1;
                wallDx = 1; // Walls perpendicular to tunnel direction
                break;
        }
        
        for (int i = 0; i < length; i++) {
            int x = startX + dx * i;
            int z = startZ + dz * i;
            
            // Break wall blocks on each side (1 block depth)
            // Side 1 (+wallDx/+wallDz)
            setBlock(x + wallDx, startY, z + wallDz, Blocks.AIR);
            setBlock(x + wallDx, startY + 1, z + wallDz, Blocks.AIR);
            
            // Side 2 (-wallDx/-wallDz)
            setBlock(x - wallDx, startY, z - wallDz, Blocks.AIR);
            setBlock(x - wallDx, startY + 1, z - wallDz, Blocks.AIR);
        }
    }
    
    /**
     * Find how far light propagates down a tunnel before reaching 0.
     * Returns the distance where light first becomes 0, or length if light reaches the end.
     */
    private int findLightPropagationDistance(int startX, int startY, int startZ, int direction, int length) {
        int dx = 0, dz = 0;
        switch (direction) {
            case 0: dx = 1; break;  // +X
            case 1: dx = -1; break; // -X
            case 2: dz = 1; break;  // +Z
            case 3: dz = -1; break; // -Z
        }
        
        for (int i = 0; i < length; i++) {
            int x = startX + dx * i;
            int z = startZ + dz * i;
            int light = getSkyLight(x, startY, z);
            if (light == 0) {
                return i;
            }
        }
        return length; // Light reaches the end
    }
    
    /**
     * Test 1: Dig vertical shaft and verify light propagates to bottom.
     * 
     * Dig from (0, 64, -1) down to (0, 52, -1) - a 12-block shaft.
     * Skylight should propagate down the shaft.
     */
    @Test
    @DisplayName("Test 1: Vertical shaft light propagation from (0,64,-1) to (0,52,-1)")
    public void testVerticalShaftLightPropagation() {
        System.out.println("=== Test 1: Vertical Shaft Light Propagation ===\n");
        
        // Fill the underground area with stone (around the test area)
        // We need to fill a reasonably large area to simulate underground
        fillWithStone(-5, 5, BOTTOM_WORLD_Y - 5, START_WORLD_Y - 1, -20, 5);
        
        System.out.println("Created underground stone from y=" + (BOTTOM_WORLD_Y - 5) + " to y=" + (START_WORLD_Y - 1));
        
        // Dig the vertical shaft from (0, 64, -1) down to (0, 52, -1)
        digVerticalShaft(START_X, START_WORLD_Y, BOTTOM_WORLD_Y, START_Z);
        
        System.out.println("Dug vertical shaft from (" + START_X + ", " + START_WORLD_Y + ", " + START_Z + 
                          ") to (" + START_X + ", " + BOTTOM_WORLD_Y + ", " + START_Z + ")");
        
        // Print light values down the shaft
        System.out.println("\nLight levels down the shaft:");
        System.out.println("Y     Skylight");
        System.out.println("---   --------");
        
        for (int y = START_WORLD_Y; y >= BOTTOM_WORLD_Y; y--) {
            int light = getSkyLight(START_X, y, START_Z);
            System.out.printf("%3d   %2d%n", y, light);
        }
        
        // Verify light at top of shaft
        int lightAtTop = getSkyLight(START_X, START_WORLD_Y, START_Z);
        assertTrue(lightAtTop >= 14, 
            "Top of shaft should have full skylight (>= 14), got: " + lightAtTop);
        
        // Verify light at bottom of shaft (should be attenuated but > 0)
        int lightAtBottom = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
        assertTrue(lightAtBottom > 0, 
            "Bottom of shaft should have some skylight (shaft is 12 blocks), got: " + lightAtBottom);
        
        // The shaft is 12 blocks deep (from 64 to 52)
        // Skylight starts at 15 and decreases by 1 per block below heightmap
        // Expected light at bottom: 15 - 12 = 3
        int expectedMinLight = Math.max(0, 15 - (START_WORLD_Y - BOTTOM_WORLD_Y));
        System.out.println("\nExpected minimum light at bottom: " + expectedMinLight);
        System.out.println("Actual light at bottom: " + lightAtBottom);
        
        assertTrue(lightAtBottom >= expectedMinLight, 
            "Light at bottom should be at least " + expectedMinLight + ", got: " + lightAtBottom);
    }
    
    /**
     * Test 2: Dig horizontal tunnel in -Z direction and measure light propagation.
     * 
     * From the bottom of the shaft at (0, 52, -1), dig a 1x2 tunnel to (0, 52, -12).
     * Measure how far light propagates.
     */
    @Test
    @DisplayName("Test 2: Tunnel in -Z direction light propagation from bottom of shaft")
    public void testTunnelMinusZLightPropagation() {
        System.out.println("=== Test 2: Tunnel Light Propagation (-Z direction) ===\n");
        
        // Setup: Fill underground and dig vertical shaft
        fillWithStone(-5, 5, BOTTOM_WORLD_Y - 5, START_WORLD_Y - 1, -20, 5);
        digVerticalShaft(START_X, START_WORLD_Y, BOTTOM_WORLD_Y, START_Z);
        
        // Dig the 1x2 tunnel in -Z direction from (0, 52, -1) to (0, 52, -12)
        dig1x2Tunnel(START_X, BOTTOM_WORLD_Y, START_Z, 3, TUNNEL_LENGTH); // 3 = -Z direction
        
        System.out.println("Dug 1x2 tunnel from (" + START_X + ", " + BOTTOM_WORLD_Y + ", " + START_Z + 
                          ") to (" + START_X + ", " + BOTTOM_WORLD_Y + ", " + (START_Z - TUNNEL_LENGTH + 1) + ")");
        
        // Print light values along the tunnel
        System.out.println("\nLight levels along -Z tunnel:");
        System.out.println("Z       Skylight");
        System.out.println("------  --------");
        
        int lastNonZeroLight = START_Z;
        for (int z = START_Z; z >= START_Z - TUNNEL_LENGTH + 1; z--) {
            int light = getSkyLight(START_X, BOTTOM_WORLD_Y, z);
            System.out.printf("%3d     %2d%n", z, light);
            if (light > 0) {
                lastNonZeroLight = z;
            }
        }
        
        int propagationDistance = START_Z - lastNonZeroLight;
        System.out.println("\nLight propagation distance in -Z tunnel: " + propagationDistance + " blocks");
        
        // Light at the shaft entrance to the tunnel should be > 0
        int lightAtTunnelStart = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
        assertTrue(lightAtTunnelStart > 0, 
            "Light at tunnel entrance (shaft bottom) should be > 0, got: " + lightAtTunnelStart);
        
        // Report how far light propagated
        System.out.println("Light propagated " + propagationDistance + " blocks into the tunnel");
    }
    
    /**
     * Test 3: Dig tunnels in all 4 cardinal directions and compare light propagation.
     * 
     * From the shaft bottom, dig 1x2 tunnels in +X, -X, +Z, -Z directions.
     * Measure and compare light propagation in each.
     */
    @Test
    @DisplayName("Test 3: Tunnels in all cardinal directions - light propagation comparison")
    public void testAllCardinalTunnelsLightPropagation() {
        System.out.println("=== Test 3: All Cardinal Direction Tunnels ===\n");
        
        // Setup: Fill underground and dig vertical shaft
        // Need a larger area to accommodate all 4 tunnels
        fillWithStone(-15, 15, BOTTOM_WORLD_Y - 5, START_WORLD_Y - 1, -15, 15);
        digVerticalShaft(START_X, START_WORLD_Y, BOTTOM_WORLD_Y, START_Z);
        
        String[] directionNames = {"+X (East)", "-X (West)", "+Z (South)", "-Z (North)"};
        int[] propagationDistances = new int[4];
        
        // Dig tunnels in all 4 directions from the shaft bottom
        for (int dir = 0; dir < 4; dir++) {
            dig1x2Tunnel(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        System.out.println("Dug 1x2 tunnels (" + TUNNEL_LENGTH + " blocks) in all 4 directions from shaft bottom\n");
        
        // Measure light propagation in each direction
        System.out.println("Direction      Distance  Light Values");
        System.out.println("-----------    --------  -------------------------------------------");
        
        for (int dir = 0; dir < 4; dir++) {
            int distance = findLightPropagationDistance(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
            propagationDistances[dir] = distance;
            
            // Collect light values for display
            StringBuilder lightValues = new StringBuilder();
            int dx = 0, dz = 0;
            switch (dir) {
                case 0: dx = 1; break;
                case 1: dx = -1; break;
                case 2: dz = 1; break;
                case 3: dz = -1; break;
            }
            
            for (int i = 0; i < TUNNEL_LENGTH; i++) {
                int x = START_X + dx * i;
                int z = START_Z + dz * i;
                int light = getSkyLight(x, BOTTOM_WORLD_Y, z);
                lightValues.append(light).append(" ");
            }
            
            System.out.printf("%-14s %2d        %s%n", directionNames[dir], distance, lightValues.toString().trim());
        }
        
        // Report findings
        System.out.println("\n=== Light Propagation Summary ===");
        for (int dir = 0; dir < 4; dir++) {
            System.out.println(directionNames[dir] + ": " + propagationDistances[dir] + " blocks");
        }
        
        // All directions should have similar light propagation (within 1-2 blocks)
        // This checks for directional asymmetry bugs
        int maxDist = 0, minDist = Integer.MAX_VALUE;
        for (int dist : propagationDistances) {
            maxDist = Math.max(maxDist, dist);
            minDist = Math.min(minDist, dist);
        }
        
        int asymmetry = maxDist - minDist;
        System.out.println("\nAsymmetry (max - min): " + asymmetry + " blocks");
        
        assertTrue(asymmetry <= 2, 
            "Light propagation should be similar in all directions. Asymmetry = " + asymmetry + 
            " blocks. This indicates a directional bug in the lighting system.");
    }
    
    /**
     * Test 4: Expand tunnels to 3 blocks wide and verify light propagates into new spaces.
     * 
     * After digging the initial 1x2 tunnels, expand them to 3x2 by breaking walls.
     * Light should propagate into the newly opened spaces.
     */
    @Test
    @DisplayName("Test 4: Expanding tunnels to 3-wide - light should fill new spaces")
    public void testExpandedTunnelLightPropagation() {
        System.out.println("=== Test 4: Expanded Tunnel Light Propagation ===\n");
        
        // Setup: Fill underground and dig vertical shaft
        fillWithStone(-20, 20, BOTTOM_WORLD_Y - 5, START_WORLD_Y - 1, -20, 20);
        digVerticalShaft(START_X, START_WORLD_Y, BOTTOM_WORLD_Y, START_Z);
        
        String[] directionNames = {"+X (East)", "-X (West)", "+Z (South)", "-Z (North)"};
        
        // Dig initial 1x2 tunnels
        for (int dir = 0; dir < 4; dir++) {
            dig1x2Tunnel(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        System.out.println("Initial 1x2 tunnels dug in all 4 directions");
        
        // Record light at specific test points before expansion (3 blocks from shaft)
        int testDistance = 3;
        Map<String, int[]> lightBeforeExpansion = new HashMap<>();
        
        int[][] testOffsets = {
            {testDistance, 0},   // +X
            {-testDistance, 0},  // -X
            {0, testDistance},   // +Z
            {0, -testDistance}   // -Z
        };
        
        // Wall offset perpendicular to tunnel direction
        int[][] wallOffsets = {
            {0, 1},  // +X tunnel: walls in Z
            {0, 1},  // -X tunnel: walls in Z
            {1, 0},  // +Z tunnel: walls in X
            {1, 0}   // -Z tunnel: walls in X
        };
        
        System.out.println("\nBefore expansion (at " + testDistance + " blocks from shaft):");
        System.out.println("Direction       Center  Side1  Side2");
        System.out.println("-----------     ------  -----  -----");
        
        for (int dir = 0; dir < 4; dir++) {
            int testX = START_X + testOffsets[dir][0];
            int testZ = START_Z + testOffsets[dir][1];
            
            int centerLight = getSkyLight(testX, BOTTOM_WORLD_Y, testZ);
            int side1X = testX + wallOffsets[dir][0];
            int side1Z = testZ + wallOffsets[dir][1];
            int side2X = testX - wallOffsets[dir][0];
            int side2Z = testZ - wallOffsets[dir][1];
            
            int side1Light = getSkyLight(side1X, BOTTOM_WORLD_Y, side1Z);
            int side2Light = getSkyLight(side2X, BOTTOM_WORLD_Y, side2Z);
            
            lightBeforeExpansion.put(directionNames[dir], new int[]{centerLight, side1Light, side2Light});
            System.out.printf("%-14s  %2d      %2d     %2d%n", directionNames[dir], 
                centerLight, side1Light, side2Light);
        }
        
        // Expand tunnels to 3 blocks wide
        System.out.println("\nExpanding tunnels to 3 blocks wide...");
        for (int dir = 0; dir < 4; dir++) {
            expandTunnelTo3Wide(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        System.out.println("\nAfter expansion (at " + testDistance + " blocks from shaft):");
        System.out.println("Direction       Center  Side1  Side2  Side1Δ  Side2Δ");
        System.out.println("-----------     ------  -----  -----  ------  ------");
        
        boolean allExpansionsLit = true;
        
        for (int dir = 0; dir < 4; dir++) {
            int testX = START_X + testOffsets[dir][0];
            int testZ = START_Z + testOffsets[dir][1];
            
            int centerLight = getSkyLight(testX, BOTTOM_WORLD_Y, testZ);
            int side1X = testX + wallOffsets[dir][0];
            int side1Z = testZ + wallOffsets[dir][1];
            int side2X = testX - wallOffsets[dir][0];
            int side2Z = testZ - wallOffsets[dir][1];
            
            int side1Light = getSkyLight(side1X, BOTTOM_WORLD_Y, side1Z);
            int side2Light = getSkyLight(side2X, BOTTOM_WORLD_Y, side2Z);
            
            int[] before = lightBeforeExpansion.get(directionNames[dir]);
            int side1Delta = side1Light - before[1];
            int side2Delta = side2Light - before[2];
            
            System.out.printf("%-14s  %2d      %2d     %2d     %+2d      %+2d%n", 
                directionNames[dir], centerLight, side1Light, side2Light, side1Delta, side2Delta);
            
            // If the center has light and sides don't after expansion, there's a bug
            if (centerLight > 0 && (side1Light == 0 || side2Light == 0)) {
                allExpansionsLit = false;
            }
        }
        
        System.out.println("\n=== Expansion Light Summary ===");
        int passCount = 0;
        int totalChecks = 0;
        
        for (int dir = 0; dir < 4; dir++) {
            int testX = START_X + testOffsets[dir][0];
            int testZ = START_Z + testOffsets[dir][1];
            int side1X = testX + wallOffsets[dir][0];
            int side1Z = testZ + wallOffsets[dir][1];
            int side2X = testX - wallOffsets[dir][0];
            int side2Z = testZ - wallOffsets[dir][1];
            
            int centerLight = getSkyLight(testX, BOTTOM_WORLD_Y, testZ);
            int side1Light = getSkyLight(side1X, BOTTOM_WORLD_Y, side1Z);
            int side2Light = getSkyLight(side2X, BOTTOM_WORLD_Y, side2Z);
            
            boolean side1Ok = (centerLight == 0) || (side1Light > 0);
            boolean side2Ok = (centerLight == 0) || (side2Light > 0);
            
            if (side1Ok) passCount++;
            if (side2Ok) passCount++;
            totalChecks += 2;
            
            String status1 = side1Ok ? "OK" : "BUG";
            String status2 = side2Ok ? "OK" : "BUG";
            
            System.out.println(directionNames[dir] + ": Side1 " + status1 + ", Side2 " + status2);
        }
        
        System.out.println("\nExpansion check: " + passCount + "/" + totalChecks + " passed");
        
        assertTrue(allExpansionsLit, 
            "Light should propagate into expanded tunnel spaces. " +
            "If center has light, adjacent expansion should also have light. " +
            "Passed " + passCount + "/" + totalChecks + " checks.");
    }
    
    /**
     * Comprehensive test that runs all scenarios in sequence.
     */
    @Test
    @DisplayName("Full test sequence: Vertical shaft -> Tunnels -> Expansion")
    public void testFullSequence() {
        System.out.println("=== FULL TEST SEQUENCE ===\n");
        System.out.println("Testing light propagation from surface through vertical shaft and horizontal tunnels.\n");
        
        // Setup: Fill a large underground area with stone
        System.out.println("Step 0: Creating underground stone terrain...");
        fillWithStone(-20, 20, BOTTOM_WORLD_Y - 5, START_WORLD_Y - 1, -20, 20);
        System.out.println("  Stone filled from y=" + (BOTTOM_WORLD_Y - 5) + " to y=" + (START_WORLD_Y - 1));
        System.out.println("  X range: -20 to 20, Z range: -20 to 20\n");
        
        // Step 1: Dig vertical shaft
        System.out.println("Step 1: Digging vertical shaft from (0, 64, -1) to (0, 52, -1)...");
        digVerticalShaft(START_X, START_WORLD_Y, BOTTOM_WORLD_Y, START_Z);
        
        int lightAtShaftTop = getSkyLight(START_X, START_WORLD_Y, START_Z);
        int lightAtShaftBottom = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
        
        System.out.println("  Light at top (y=64): " + lightAtShaftTop);
        System.out.println("  Light at bottom (y=52): " + lightAtShaftBottom);
        System.out.println("  Result: " + (lightAtShaftBottom > 0 ? "✓ Light reaches bottom" : "✗ Light does NOT reach bottom") + "\n");
        
        assertTrue(lightAtShaftBottom > 0, "Shaft Test: Light should reach bottom of vertical shaft");
        
        // Step 2: Dig -Z tunnel
        System.out.println("Step 2: Digging 1×2 tunnel in -Z direction (0, 52, -1) to (0, 52, -12)...");
        dig1x2Tunnel(START_X, BOTTOM_WORLD_Y, START_Z, 3, TUNNEL_LENGTH);
        
        int lightAtTunnelEnd = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z - TUNNEL_LENGTH + 1);
        int distance = findLightPropagationDistance(START_X, BOTTOM_WORLD_Y, START_Z, 3, TUNNEL_LENGTH);
        
        System.out.println("  Light at tunnel start: " + getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z));
        System.out.println("  Light at tunnel end (z=-12): " + lightAtTunnelEnd);
        System.out.println("  Light propagation distance: " + distance + " blocks\n");
        
        // Step 3: Dig tunnels in all other directions
        System.out.println("Step 3: Digging tunnels in +X, -X, +Z directions...");
        for (int dir = 0; dir < 3; dir++) { // Already did -Z (dir=3)
            dig1x2Tunnel(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        String[] dirNames = {"+X", "-X", "+Z", "-Z"};
        System.out.println("  Light propagation per direction:");
        for (int dir = 0; dir < 4; dir++) {
            int dist = findLightPropagationDistance(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
            System.out.println("    " + dirNames[dir] + ": " + dist + " blocks");
        }
        System.out.println();
        
        // Step 4: Expand tunnels
        System.out.println("Step 4: Expanding all tunnels to 3 blocks wide...");
        for (int dir = 0; dir < 4; dir++) {
            expandTunnelTo3Wide(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        // Check if light propagated into expanded spaces
        int testDist = 3;
        int[][] testPoints = {{testDist, 0}, {-testDist, 0}, {0, testDist}, {0, -testDist}};
        int[][] wallDir = {{0, 1}, {0, 1}, {1, 0}, {1, 0}};
        
        System.out.println("  Light in expanded spaces (at 3 blocks from shaft):");
        boolean allGood = true;
        for (int dir = 0; dir < 4; dir++) {
            int x = START_X + testPoints[dir][0];
            int z = START_Z + testPoints[dir][1];
            int center = getSkyLight(x, BOTTOM_WORLD_Y, z);
            int side1 = getSkyLight(x + wallDir[dir][0], BOTTOM_WORLD_Y, z + wallDir[dir][1]);
            int side2 = getSkyLight(x - wallDir[dir][0], BOTTOM_WORLD_Y, z - wallDir[dir][1]);
            
            boolean ok = (center == 0) || (side1 > 0 && side2 > 0);
            System.out.println("    " + dirNames[dir] + ": center=" + center + 
                              ", left=" + side1 + ", right=" + side2 + 
                              (ok ? " ✓" : " ✗ POTENTIAL BUG"));
            allGood = allGood && ok;
        }
        System.out.println();
        
        System.out.println("=== TEST COMPLETE ===");
        System.out.println("Vertical shaft: " + (lightAtShaftBottom > 0 ? "PASS" : "FAIL"));
        System.out.println("Tunnel expansion: " + (allGood ? "PASS" : "POTENTIAL ISSUE"));
        
        assertTrue(lightAtShaftBottom > 0, "Vertical shaft should propagate light to bottom");
    }
    
    /**
     * Test 5: Block the shaft, verify darkness propagates, then reopen and verify light returns.
     * 
     * This tests the full lifecycle of light updates:
     * 1. Open shaft with light propagating through tunnels
     * 2. Block the shaft entrance - darkness should propagate everywhere
     * 3. Reopen the shaft - light should return to all tunnels
     */
    @Test
    @DisplayName("Test 5: Block shaft -> verify darkness -> reopen -> verify light returns")
    public void testBlockAndReopenShaft() {
        System.out.println("=== Test 5: Block and Reopen Shaft ===\n");
        
        // Setup: Fill underground, dig shaft, and dig tunnels in all directions
        System.out.println("Step 0: Setting up underground terrain and digging shaft with tunnels...");
        fillWithStone(-20, 20, BOTTOM_WORLD_Y - 5, START_WORLD_Y - 1, -20, 20);
        digVerticalShaft(START_X, START_WORLD_Y, BOTTOM_WORLD_Y, START_Z);
        
        // Dig tunnels in all 4 directions
        for (int dir = 0; dir < 4; dir++) {
            dig1x2Tunnel(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        // Expand tunnels to 3 blocks wide
        for (int dir = 0; dir < 4; dir++) {
            expandTunnelTo3Wide(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        System.out.println("  Shaft dug from (" + START_X + ", " + START_WORLD_Y + ", " + START_Z + 
                          ") to (" + START_X + ", " + BOTTOM_WORLD_Y + ", " + START_Z + ")");
        System.out.println("  Tunnels dug and expanded in all 4 directions\n");
        
        // =====================================================
        // PHASE 1: Record initial light values (shaft open)
        // =====================================================
        System.out.println("=== PHASE 1: Initial Light State (Shaft Open) ===\n");
        
        String[] dirNames = {"+X", "-X", "+Z", "-Z"};
        int[][] testOffsets = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        int[][] wallOffsets = {{0, 1}, {0, 1}, {1, 0}, {1, 0}};
        
        // Record light at shaft
        int initialShaftTopLight = getSkyLight(START_X, START_WORLD_Y, START_Z);
        int initialShaftBottomLight = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
        
        System.out.println("Shaft light:");
        System.out.println("  Top (y=64): " + initialShaftTopLight);
        System.out.println("  Bottom (y=52): " + initialShaftBottomLight);
        
        // Record light in tunnels
        System.out.println("\nTunnel light (at 3 blocks from shaft):");
        System.out.println("Direction   Center  Side1  Side2");
        System.out.println("---------   ------  -----  -----");
        
        int[][] initialTunnelLights = new int[4][3]; // [direction][center, side1, side2]
        for (int dir = 0; dir < 4; dir++) {
            int x = START_X + testOffsets[dir][0];
            int z = START_Z + testOffsets[dir][1];
            int center = getSkyLight(x, BOTTOM_WORLD_Y, z);
            int side1 = getSkyLight(x + wallOffsets[dir][0], BOTTOM_WORLD_Y, z + wallOffsets[dir][1]);
            int side2 = getSkyLight(x - wallOffsets[dir][0], BOTTOM_WORLD_Y, z - wallOffsets[dir][1]);
            
            initialTunnelLights[dir][0] = center;
            initialTunnelLights[dir][1] = side1;
            initialTunnelLights[dir][2] = side2;
            
            System.out.printf("%-9s   %2d      %2d     %2d%n", dirNames[dir], center, side1, side2);
        }
        
        // Record light at tunnel ends
        System.out.println("\nTunnel end light (at 11 blocks from shaft):");
        int[] initialTunnelEndLights = new int[4];
        int[][] endOffsets = {{11, 0}, {-11, 0}, {0, 11}, {0, -11}};
        for (int dir = 0; dir < 4; dir++) {
            int x = START_X + endOffsets[dir][0];
            int z = START_Z + endOffsets[dir][1];
            initialTunnelEndLights[dir] = getSkyLight(x, BOTTOM_WORLD_Y, z);
            System.out.println("  " + dirNames[dir] + ": " + initialTunnelEndLights[dir]);
        }
        
        // Verify initial state has light
        assertTrue(initialShaftBottomLight > 0, "Initial: Shaft bottom should have light");
        
        // =====================================================
        // PHASE 2: Block the shaft entrance and verify darkness
        // =====================================================
        System.out.println("\n=== PHASE 2: Blocking Shaft Entrance ===\n");
        
        // Block the shaft at the top (place stone at surface level)
        System.out.println("Placing stone block at shaft entrance (0, 64, -1)...");
        setBlock(START_X, START_WORLD_Y, START_Z, Blocks.STONE);
        
        // Record light after blocking
        int blockedShaftTopLight = getSkyLight(START_X, START_WORLD_Y, START_Z);
        int blockedShaftBottomLight = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
        
        System.out.println("\nShaft light after blocking:");
        System.out.println("  Top (y=64): " + blockedShaftTopLight + " (was " + initialShaftTopLight + ")");
        System.out.println("  Bottom (y=52): " + blockedShaftBottomLight + " (was " + initialShaftBottomLight + ")");
        
        // Record tunnel light after blocking
        System.out.println("\nTunnel light after blocking (at 3 blocks from shaft):");
        System.out.println("Direction   Center  Side1  Side2  (Change from initial)");
        System.out.println("---------   ------  -----  -----  ---------------------");
        
        int[][] blockedTunnelLights = new int[4][3];
        boolean darknessPropagatesToTunnels = true;
        
        for (int dir = 0; dir < 4; dir++) {
            int x = START_X + testOffsets[dir][0];
            int z = START_Z + testOffsets[dir][1];
            int center = getSkyLight(x, BOTTOM_WORLD_Y, z);
            int side1 = getSkyLight(x + wallOffsets[dir][0], BOTTOM_WORLD_Y, z + wallOffsets[dir][1]);
            int side2 = getSkyLight(x - wallOffsets[dir][0], BOTTOM_WORLD_Y, z - wallOffsets[dir][1]);
            
            blockedTunnelLights[dir][0] = center;
            blockedTunnelLights[dir][1] = side1;
            blockedTunnelLights[dir][2] = side2;
            
            int centerDelta = center - initialTunnelLights[dir][0];
            int side1Delta = side1 - initialTunnelLights[dir][1];
            int side2Delta = side2 - initialTunnelLights[dir][2];
            
            System.out.printf("%-9s   %2d      %2d     %2d     (%+d, %+d, %+d)%n", 
                dirNames[dir], center, side1, side2, centerDelta, side1Delta, side2Delta);
            
            // If initial had light but blocked still has light, darkness didn't propagate
            if (initialTunnelLights[dir][0] > 0 && center > 0) {
                darknessPropagatesToTunnels = false;
            }
        }
        
        // Record tunnel end light after blocking
        System.out.println("\nTunnel end light after blocking:");
        int[] blockedTunnelEndLights = new int[4];
        for (int dir = 0; dir < 4; dir++) {
            int x = START_X + endOffsets[dir][0];
            int z = START_Z + endOffsets[dir][1];
            blockedTunnelEndLights[dir] = getSkyLight(x, BOTTOM_WORLD_Y, z);
            int delta = blockedTunnelEndLights[dir] - initialTunnelEndLights[dir];
            System.out.printf("  %s: %d (was %d, %+d)%n", dirNames[dir], 
                blockedTunnelEndLights[dir], initialTunnelEndLights[dir], delta);
        }
        
        // Check darkness propagation results
        System.out.println("\n=== Darkness Propagation Analysis ===");
        System.out.println("Shaft bottom: " + (blockedShaftBottomLight == 0 ? "✓ Dark" : "✗ Still has light: " + blockedShaftBottomLight));
        System.out.println("Tunnels: " + (darknessPropagatesToTunnels ? "✓ Dark" : "✗ Still have light"));
        
        // Verify darkness propagated
        assertEquals(0, blockedShaftBottomLight, 
            "After blocking: Shaft bottom should be dark (0), but was: " + blockedShaftBottomLight);
        
        // Verify darkness propagated to ALL tunnels (this catches the cross-chunk removal bug)
        assertTrue(darknessPropagatesToTunnels,
            "After blocking: All tunnels should be dark. Some tunnels still have light, " +
            "indicating darkness removal failed to propagate across chunk boundaries.");
        
        // =====================================================
        // PHASE 3: Reopen the shaft and verify light returns
        // =====================================================
        System.out.println("\n=== PHASE 3: Reopening Shaft ===\n");
        
        // Remove the blocking stone
        System.out.println("Breaking stone block at shaft entrance (0, 64, -1)...");
        setBlock(START_X, START_WORLD_Y, START_Z, Blocks.AIR);
        
        // Record light after reopening
        int reopenedShaftTopLight = getSkyLight(START_X, START_WORLD_Y, START_Z);
        int reopenedShaftBottomLight = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
        
        System.out.println("\nShaft light after reopening:");
        System.out.println("  Top (y=64): " + reopenedShaftTopLight + " (initial was " + initialShaftTopLight + ")");
        System.out.println("  Bottom (y=52): " + reopenedShaftBottomLight + " (initial was " + initialShaftBottomLight + ")");
        
        // Record tunnel light after reopening
        System.out.println("\nTunnel light after reopening (at 3 blocks from shaft):");
        System.out.println("Direction   Center  Side1  Side2  (vs Initial / vs Blocked)");
        System.out.println("---------   ------  -----  -----  -------------------------");
        
        int[][] reopenedTunnelLights = new int[4][3];
        boolean lightReturnsToTunnels = true;
        
        for (int dir = 0; dir < 4; dir++) {
            int x = START_X + testOffsets[dir][0];
            int z = START_Z + testOffsets[dir][1];
            int center = getSkyLight(x, BOTTOM_WORLD_Y, z);
            int side1 = getSkyLight(x + wallOffsets[dir][0], BOTTOM_WORLD_Y, z + wallOffsets[dir][1]);
            int side2 = getSkyLight(x - wallOffsets[dir][0], BOTTOM_WORLD_Y, z - wallOffsets[dir][1]);
            
            reopenedTunnelLights[dir][0] = center;
            reopenedTunnelLights[dir][1] = side1;
            reopenedTunnelLights[dir][2] = side2;
            
            String vsInitial = String.format("(%+d, %+d, %+d)", 
                center - initialTunnelLights[dir][0],
                side1 - initialTunnelLights[dir][1],
                side2 - initialTunnelLights[dir][2]);
            
            String vsBlocked = String.format("(%+d, %+d, %+d)", 
                center - blockedTunnelLights[dir][0],
                side1 - blockedTunnelLights[dir][1],
                side2 - blockedTunnelLights[dir][2]);
            
            System.out.printf("%-9s   %2d      %2d     %2d     %s / %s%n", 
                dirNames[dir], center, side1, side2, vsInitial, vsBlocked);
            
            // If initial had light but reopened doesn't, light didn't return
            if (initialTunnelLights[dir][0] > 0 && center == 0) {
                lightReturnsToTunnels = false;
            }
        }
        
        // Record tunnel end light after reopening
        System.out.println("\nTunnel end light after reopening:");
        int[] reopenedTunnelEndLights = new int[4];
        for (int dir = 0; dir < 4; dir++) {
            int x = START_X + endOffsets[dir][0];
            int z = START_Z + endOffsets[dir][1];
            reopenedTunnelEndLights[dir] = getSkyLight(x, BOTTOM_WORLD_Y, z);
            int vsInitial = reopenedTunnelEndLights[dir] - initialTunnelEndLights[dir];
            int vsBlocked = reopenedTunnelEndLights[dir] - blockedTunnelEndLights[dir];
            System.out.printf("  %s: %d (vs initial: %+d, vs blocked: %+d)%n", 
                dirNames[dir], reopenedTunnelEndLights[dir], vsInitial, vsBlocked);
        }
        
        // =====================================================
        // FINAL ANALYSIS
        // =====================================================
        System.out.println("\n=== FINAL ANALYSIS ===\n");
        
        // Check if light returned to the same levels as initial
        boolean shaftLightMatches = (reopenedShaftBottomLight == initialShaftBottomLight);
        System.out.println("Shaft bottom light:");
        System.out.println("  Initial: " + initialShaftBottomLight);
        System.out.println("  After blocking: " + blockedShaftBottomLight);
        System.out.println("  After reopening: " + reopenedShaftBottomLight);
        System.out.println("  Status: " + (shaftLightMatches ? "✓ Matches initial" : "✗ Does NOT match initial"));
        
        System.out.println("\nTunnel center light comparison:");
        boolean allTunnelsMatch = true;
        for (int dir = 0; dir < 4; dir++) {
            boolean matches = (reopenedTunnelLights[dir][0] == initialTunnelLights[dir][0]);
            if (!matches) allTunnelsMatch = false;
            System.out.printf("  %s: Initial=%d, Blocked=%d, Reopened=%d %s%n",
                dirNames[dir], 
                initialTunnelLights[dir][0],
                blockedTunnelLights[dir][0],
                reopenedTunnelLights[dir][0],
                matches ? "✓" : "✗ MISMATCH");
        }
        
        System.out.println("\n=== TEST RESULTS ===");
        System.out.println("1. Darkness propagation when blocked: " + 
            (blockedShaftBottomLight == 0 ? "PASS" : "FAIL"));
        System.out.println("2. Light returns when reopened: " + 
            (reopenedShaftBottomLight > 0 ? "PASS" : "FAIL"));
        System.out.println("3. Light matches initial after reopen: " + 
            (shaftLightMatches && allTunnelsMatch ? "PASS" : "FAIL"));
        
        // Assertions
        assertTrue(reopenedShaftBottomLight > 0, 
            "After reopening: Shaft bottom should have light again, but was: " + reopenedShaftBottomLight);
        
        assertEquals(initialShaftBottomLight, reopenedShaftBottomLight,
            "After reopening: Shaft bottom light should match initial. Initial=" + 
            initialShaftBottomLight + ", Reopened=" + reopenedShaftBottomLight);
        
        assertTrue(lightReturnsToTunnels,
            "After reopening: Light should return to all tunnels that had light initially");
    }
    
    /**
     * Test 6: Multiple block/reopen cycles - verify light system remains consistent.
     * 
     * This tests that repeatedly blocking and reopening the shaft doesn't cause
     * the lighting system to degrade or stop working.
     */
    @Test
    @DisplayName("Test 6: Multiple block/reopen cycles - verify consistency")
    public void testMultipleBlockReopenCycles() {
        System.out.println("=== Test 6: Multiple Block/Reopen Cycles ===\n");
        
        // Setup: Fill underground, dig shaft, and dig tunnels in all directions
        System.out.println("Step 0: Setting up underground terrain and digging shaft with tunnels...");
        fillWithStone(-20, 20, BOTTOM_WORLD_Y - 5, START_WORLD_Y - 1, -20, 20);
        digVerticalShaft(START_X, START_WORLD_Y, BOTTOM_WORLD_Y, START_Z);
        
        // Dig tunnels in all 4 directions
        for (int dir = 0; dir < 4; dir++) {
            dig1x2Tunnel(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        System.out.println("  Shaft and tunnels created.\n");
        
        String[] dirNames = {"+X", "-X", "+Z", "-Z"};
        int[][] testOffsets = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        
        // Record initial light state
        int initialShaftBottomLight = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
        int[] initialTunnelLights = new int[4];
        for (int dir = 0; dir < 4; dir++) {
            int x = START_X + testOffsets[dir][0];
            int z = START_Z + testOffsets[dir][1];
            initialTunnelLights[dir] = getSkyLight(x, BOTTOM_WORLD_Y, z);
        }
        
        System.out.println("Initial state:");
        System.out.println("  Shaft bottom: " + initialShaftBottomLight);
        for (int dir = 0; dir < 4; dir++) {
            System.out.println("  Tunnel " + dirNames[dir] + ": " + initialTunnelLights[dir]);
        }
        
        // Perform multiple cycles
        int numCycles = 5;
        boolean allCyclesPassed = true;
        
        for (int cycle = 1; cycle <= numCycles; cycle++) {
            System.out.println("\n=== CYCLE " + cycle + " ===");
            
            // Block the shaft
            System.out.println("  Blocking shaft entrance...");
            setBlock(START_X, START_WORLD_Y, START_Z, Blocks.STONE);
            
            // Check blocked state
            int blockedShaftLight = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
            int[] blockedTunnelLights = new int[4];
            boolean allTunnelsDark = true;
            
            for (int dir = 0; dir < 4; dir++) {
                int x = START_X + testOffsets[dir][0];
                int z = START_Z + testOffsets[dir][1];
                blockedTunnelLights[dir] = getSkyLight(x, BOTTOM_WORLD_Y, z);
                if (blockedTunnelLights[dir] > 0) {
                    allTunnelsDark = false;
                }
            }
            
            System.out.println("  After blocking:");
            System.out.println("    Shaft bottom: " + blockedShaftLight + (blockedShaftLight == 0 ? " ✓" : " ✗ SHOULD BE 0"));
            for (int dir = 0; dir < 4; dir++) {
                String status = blockedTunnelLights[dir] == 0 ? " ✓" : " ✗ SHOULD BE 0";
                System.out.println("    Tunnel " + dirNames[dir] + ": " + blockedTunnelLights[dir] + status);
            }
            
            if (blockedShaftLight != 0 || !allTunnelsDark) {
                System.out.println("  !!! DARKNESS PROPAGATION FAILED IN CYCLE " + cycle + " !!!");
                allCyclesPassed = false;
            }
            
            // Reopen the shaft
            System.out.println("  Reopening shaft...");
            setBlock(START_X, START_WORLD_Y, START_Z, Blocks.AIR);
            
            // Check reopened state
            int reopenedShaftLight = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
            int[] reopenedTunnelLights = new int[4];
            boolean allTunnelsLit = true;
            
            for (int dir = 0; dir < 4; dir++) {
                int x = START_X + testOffsets[dir][0];
                int z = START_Z + testOffsets[dir][1];
                reopenedTunnelLights[dir] = getSkyLight(x, BOTTOM_WORLD_Y, z);
                if (reopenedTunnelLights[dir] != initialTunnelLights[dir]) {
                    allTunnelsLit = false;
                }
            }
            
            System.out.println("  After reopening:");
            System.out.println("    Shaft bottom: " + reopenedShaftLight + 
                (reopenedShaftLight == initialShaftBottomLight ? " ✓" : " ✗ EXPECTED " + initialShaftBottomLight));
            for (int dir = 0; dir < 4; dir++) {
                String status = reopenedTunnelLights[dir] == initialTunnelLights[dir] ? " ✓" : 
                    " ✗ EXPECTED " + initialTunnelLights[dir];
                System.out.println("    Tunnel " + dirNames[dir] + ": " + reopenedTunnelLights[dir] + status);
            }
            
            if (reopenedShaftLight != initialShaftBottomLight || !allTunnelsLit) {
                System.out.println("  !!! LIGHT RESTORATION FAILED IN CYCLE " + cycle + " !!!");
                allCyclesPassed = false;
            }
            
            // Assert for this cycle
            assertEquals(0, blockedShaftLight, 
                "Cycle " + cycle + ": Shaft should be dark when blocked");
            assertTrue(allTunnelsDark, 
                "Cycle " + cycle + ": All tunnels should be dark when shaft is blocked");
            assertEquals(initialShaftBottomLight, reopenedShaftLight, 
                "Cycle " + cycle + ": Shaft light should match initial when reopened");
        }
        
        System.out.println("\n=== FINAL RESULT ===");
        System.out.println("All " + numCycles + " cycles: " + (allCyclesPassed ? "PASSED" : "FAILED"));
        
        assertTrue(allCyclesPassed, 
            "All block/reopen cycles should maintain consistent lighting behavior");
    }
    
    /**
     * Test 7: Multiple block/reopen cycles with expanded tunnels crossing chunk boundaries.
     * 
     * This is a more aggressive test that may better reproduce real-world behavior
     * where tunnels cross chunk boundaries.
     */
    @Test
    @DisplayName("Test 7: Multiple cycles with expanded cross-chunk tunnels")
    public void testMultipleCyclesWithExpandedTunnels() {
        System.out.println("=== Test 7: Multiple Cycles with Expanded Cross-Chunk Tunnels ===\n");
        
        // Setup: Fill underground, dig shaft, and dig tunnels in all directions
        System.out.println("Step 0: Setting up underground terrain and digging shaft with expanded tunnels...");
        fillWithStone(-20, 20, BOTTOM_WORLD_Y - 5, START_WORLD_Y - 1, -20, 20);
        digVerticalShaft(START_X, START_WORLD_Y, BOTTOM_WORLD_Y, START_Z);
        
        // Dig tunnels in all 4 directions
        for (int dir = 0; dir < 4; dir++) {
            dig1x2Tunnel(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        // Expand tunnels to 3 blocks wide (this crosses more chunk boundaries)
        for (int dir = 0; dir < 4; dir++) {
            expandTunnelTo3Wide(START_X, BOTTOM_WORLD_Y, START_Z, dir, TUNNEL_LENGTH);
        }
        
        System.out.println("  Shaft and expanded tunnels created.\n");
        
        String[] dirNames = {"+X", "-X", "+Z", "-Z"};
        // Test at multiple distances to cover cross-chunk scenarios
        int[][] testDistances = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        int[][] farDistances = {{10, 0}, {-10, 0}, {0, 10}, {0, -10}};
        
        // Record initial light state
        int initialShaftBottomLight = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
        int[] initialNearLights = new int[4];
        int[] initialFarLights = new int[4];
        
        for (int dir = 0; dir < 4; dir++) {
            initialNearLights[dir] = getSkyLight(START_X + testDistances[dir][0], BOTTOM_WORLD_Y, START_Z + testDistances[dir][1]);
            initialFarLights[dir] = getSkyLight(START_X + farDistances[dir][0], BOTTOM_WORLD_Y, START_Z + farDistances[dir][1]);
        }
        
        System.out.println("Initial state:");
        System.out.println("  Shaft bottom: " + initialShaftBottomLight);
        System.out.println("  Near tunnels (3 blocks): " + java.util.Arrays.toString(initialNearLights));
        System.out.println("  Far tunnels (10 blocks): " + java.util.Arrays.toString(initialFarLights));
        
        // Perform more cycles
        int numCycles = 10;
        boolean allCyclesPassed = true;
        
        for (int cycle = 1; cycle <= numCycles; cycle++) {
            System.out.println("\n=== CYCLE " + cycle + " ===");
            
            // Block the shaft
            setBlock(START_X, START_WORLD_Y, START_Z, Blocks.STONE);
            
            // Check blocked state
            int blockedShaftLight = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
            boolean nearAllDark = true;
            boolean farAllDark = true;
            
            for (int dir = 0; dir < 4; dir++) {
                int nearLight = getSkyLight(START_X + testDistances[dir][0], BOTTOM_WORLD_Y, START_Z + testDistances[dir][1]);
                int farLight = getSkyLight(START_X + farDistances[dir][0], BOTTOM_WORLD_Y, START_Z + farDistances[dir][1]);
                if (nearLight > 0) nearAllDark = false;
                if (farLight > 0) farAllDark = false;
            }
            
            if (blockedShaftLight != 0 || !nearAllDark || !farAllDark) {
                System.out.println("  BLOCKED: Shaft=" + blockedShaftLight + 
                    ", NearDark=" + nearAllDark + ", FarDark=" + farAllDark);
                if (blockedShaftLight != 0 || !nearAllDark) {
                    System.out.println("  !!! DARKNESS PROPAGATION FAILED !!!");
                    allCyclesPassed = false;
                }
            } else {
                System.out.println("  BLOCKED: All dark ✓");
            }
            
            // Reopen the shaft
            setBlock(START_X, START_WORLD_Y, START_Z, Blocks.AIR);
            
            // Check reopened state
            int reopenedShaftLight = getSkyLight(START_X, BOTTOM_WORLD_Y, START_Z);
            boolean nearAllMatch = true;
            boolean farAllMatch = true;
            
            for (int dir = 0; dir < 4; dir++) {
                int nearLight = getSkyLight(START_X + testDistances[dir][0], BOTTOM_WORLD_Y, START_Z + testDistances[dir][1]);
                int farLight = getSkyLight(START_X + farDistances[dir][0], BOTTOM_WORLD_Y, START_Z + farDistances[dir][1]);
                if (nearLight != initialNearLights[dir]) nearAllMatch = false;
                if (farLight != initialFarLights[dir]) farAllMatch = false;
            }
            
            if (reopenedShaftLight != initialShaftBottomLight || !nearAllMatch || !farAllMatch) {
                System.out.println("  REOPENED: Shaft=" + reopenedShaftLight + "/" + initialShaftBottomLight + 
                    ", NearMatch=" + nearAllMatch + ", FarMatch=" + farAllMatch);
                if (reopenedShaftLight != initialShaftBottomLight) {
                    System.out.println("  !!! LIGHT RESTORATION FAILED !!!");
                    allCyclesPassed = false;
                }
            } else {
                System.out.println("  REOPENED: All light restored ✓");
            }
            
            // Assert for this cycle
            assertEquals(0, blockedShaftLight, 
                "Cycle " + cycle + ": Shaft should be dark when blocked");
            assertEquals(initialShaftBottomLight, reopenedShaftLight, 
                "Cycle " + cycle + ": Shaft light should match initial when reopened");
        }
        
        System.out.println("\n=== FINAL RESULT ===");
        System.out.println("All " + numCycles + " cycles: " + (allCyclesPassed ? "PASSED" : "FAILED"));
        
        assertTrue(allCyclesPassed, 
            "All block/reopen cycles should maintain consistent lighting behavior");
    }
}
