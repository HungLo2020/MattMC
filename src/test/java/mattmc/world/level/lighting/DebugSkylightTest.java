package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;

public class DebugSkylightTest {
    public static void main(String[] args) {
        System.out.println("=== Debug Skylight Column Test ===");
        
        Level level = new Level();
        LightPropagator propagator = level.getLightPropagator();
        
        // Pre-load chunks
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.getChunk(x, z);
            }
        }
        
        final int GROUND_Y = 100;
        final int BLOCK_HEIGHT = GROUND_Y + 10;
        
        // Check initial skylight
        int initialSkylight = getSkyLight(level, 0, GROUND_Y, 0);
        System.out.println("Initial skylight at ground (Y=" + GROUND_Y + "): " + initialSkylight);
        
        // Place a block high above
        System.out.println("\nPlacing STONE at Y=" + BLOCK_HEIGHT);
        level.setBlock(0, BLOCK_HEIGHT, 0, Blocks.STONE);
        propagator.updateBudget(100.0);
        
        // Show column after placing block
        System.out.println("\nColumn after placing block:");
        for (int y = GROUND_Y; y <= BLOCK_HEIGHT + 1; y++) {
            int light = getSkyLight(level, 0, y, 0);
            System.out.printf("  Y=%3d: skylight=%2d\n", y, light);
        }
        
        int skylightWithBlock = getSkyLight(level, 0, GROUND_Y, 0);
        System.out.println("\nSkylight at ground with block: " + skylightWithBlock);
        
        // Remove the block
        System.out.println("\nRemoving block at Y=" + BLOCK_HEIGHT);
        level.setBlock(0, BLOCK_HEIGHT, 0, Blocks.AIR);
        
        System.out.println("Pending updates before processing: " + propagator.hasPendingUpdates());
        propagator.updateBudget(100.0);
        System.out.println("Pending updates after processing: " + propagator.hasPendingUpdates());
        
        // Show column after removing block
        System.out.println("\nColumn after removing block:");
        for (int y = GROUND_Y; y <= BLOCK_HEIGHT + 1; y++) {
            int light = getSkyLight(level, 0, y, 0);
            System.out.printf("  Y=%3d: skylight=%2d\n", y, light);
        }
        
        int skylightAfterRemoval = getSkyLight(level, 0, GROUND_Y, 0);
        System.out.println("\nSkylight at ground after removal: " + skylightAfterRemoval);
        System.out.println("Expected: " + initialSkylight);
        
        if (skylightAfterRemoval == initialSkylight) {
            System.out.println("\n✓ SUCCESS: Shadow was properly removed!");
        } else {
            System.out.println("\n✗ FAILURE: Shadow still present!");
        }
    }
    
    private static int getSkyLight(Level level, int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) chunk = level.getChunk(chunkX, chunkZ);
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getSkyLight(localX, chunkY, localZ);
    }
}
