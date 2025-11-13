package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for shadow removal when blocks are several blocks above the ground.
 */
public class ColumnShadowTest {
    
    private static final int GROUND_Y = 250; // Well above typical terrain
    private Level level;
    private LightPropagator propagator;
    
    @BeforeEach
    public void setup() {
        level = new Level();
        propagator = level.getLightPropagator();
        
        // Pre-load chunks
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.getChunk(x, z);
            }
        }
    }
    
    @Test
    public void testShadowRemovedWhenBlockHighAboveGroundIsDeleted() {
        System.out.println("=== Column Shadow Test ===");
        
        // Check initial skylight at ground level (should be 15 in open air)
        int skylightBefore = getSkyLight(0, GROUND_Y, 0);
        System.out.println("Initial skylight at ground: " + skylightBefore);
        
        // Show initial column state
        int blockHeight = GROUND_Y + 10;
        System.out.println("\nInitial column state:");
        for (int y = GROUND_Y; y <= blockHeight + 1; y++) {
            int light = getSkyLight(0, y, 0);
            System.out.printf("  Y=%3d: skylight=%2d\n", y, light);
        }
        
        // Place a block 10 blocks above the ground
        level.setBlock(0, blockHeight, 0, Blocks.STONE);
        propagator.updateBudget(100.0);
        
        // Check skylight at various heights in the column
        System.out.println("\nAfter placing block at Y=" + blockHeight + ":");
        for (int y = GROUND_Y; y <= blockHeight + 1; y++) {
            int light = getSkyLight(0, y, 0);
            System.out.printf("  Y=%3d: skylight=%2d\n", y, light);
        }
        
        int skylightAtGroundWithBlock = getSkyLight(0, GROUND_Y, 0);
        System.out.println("Skylight at ground with block above: " + skylightAtGroundWithBlock);
        
        // The shadow should extend all the way down to the ground
        assertTrue(skylightAtGroundWithBlock < skylightBefore, 
            "Shadow should appear at ground beneath block");
        
        // Now remove the block
        level.setBlock(0, blockHeight, 0, Blocks.AIR);
        System.out.println("\nBlock removed. Pending updates: " + propagator.hasPendingUpdates());
        System.out.println("Pending update count: " + propagator.getPendingUpdateCount());
        propagator.updateBudget(100.0);
        System.out.println("After processing. Pending updates: " + propagator.hasPendingUpdates());
        
        // Check skylight at various heights after removal
        System.out.println("\nAfter removing block:");
        for (int y = GROUND_Y; y <= blockHeight + 1; y++) {
            int light = getSkyLight(0, y, 0);
            System.out.printf("  Y=%3d: skylight=%2d\n", y, light);
        }
        
        int skylightAfterRemoval = getSkyLight(0, GROUND_Y, 0);
        System.out.println("Skylight at ground after removal: " + skylightAfterRemoval);
        System.out.println("Expected: " + skylightBefore);
        
        // The shadow should be gone - skylight should be restored
        assertEquals(skylightBefore, skylightAfterRemoval, 
            "Shadow should disappear when block high above is removed. Got " + skylightAfterRemoval + ", expected " + skylightBefore);
    }
    
    private int getSkyLight(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) chunk = level.getChunk(chunkX, chunkZ);
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getSkyLight(localX, chunkY, localZ);
    }
}
