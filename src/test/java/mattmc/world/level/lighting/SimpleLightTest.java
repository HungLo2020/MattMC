package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to debug light propagation.
 */
public class SimpleLightTest {
    
    private Level level;
    private LightPropagator propagator;
    
    @BeforeEach
    public void setup() {
        level = new Level();
        propagator = level.getLightPropagator();
    }
    
    @Test
    public void testSimpleTorch() {
        System.out.println("=== Simple Torch Test ===");
        
        // Ensure all chunks are loaded first
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.getChunk(x, z);
            }
        }
        System.out.println("Chunks pre-loaded");
        
        // Place the torch high up where there's air (above terrain generation)
        int torchY = 200; // Well above typical terrain
        
        // Place a torch at (0, 200, 0)
        System.out.println("Placing torch at (0, " + torchY + ", 0)");
        level.setBlock(0, torchY, 0, Blocks.TORCH);
        
        // Check if updates were queued
        System.out.println("Has pending updates: " + propagator.hasPendingUpdates());
        
        // Get initial light (before processing)
        int lightBefore = getBlockLight(0, torchY, 0);
        System.out.println("Light at torch before processing: " + lightBefore);
        
        // Process light updates
        System.out.println("Processing light updates...");
        propagator.updateBudget(100.0);
        
        // Check light at torch
        int lightAtTorch = getBlockLight(0, torchY, 0);
        System.out.println("Light at torch after processing: " + lightAtTorch);
        
        // Check adjacent blocks
        int lightEast = getBlockLight(1, torchY, 0);
        int lightWest = getBlockLight(-1, torchY, 0);
        int lightNorth = getBlockLight(0, torchY, -1);
        int lightSouth = getBlockLight(0, torchY, 1);
        
        System.out.println("Light east (+1,0,0): " + lightEast);
        System.out.println("Light west (-1,0,0): " + lightWest);
        System.out.println("Light north (0,0,-1): " + lightNorth);
        System.out.println("Light south (0,0,+1): " + lightSouth);
        
        System.out.println("Has pending updates after processing: " + propagator.hasPendingUpdates());
        
        assertEquals(14, lightAtTorch, "Torch should emit light level 14");
        assertEquals(13, lightEast, "Light should propagate with attenuation");
    }
    
    private int getBlockLight(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) {
            chunk = level.getChunk(chunkX, chunkZ);
        }
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getBlockLight(localX, chunkY, localZ);
    }
}
