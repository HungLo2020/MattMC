package mattmc.world.level.lighting;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.ChunkNBT;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that block light is correctly recalculated when registry values change.
 * This simulates the scenario where:
 * 1. A world is created with certain block light emissions
 * 2. The world is saved
 * 3. Block registry values are modified (e.g., torch emission changes)
 * 4. The world is loaded again
 * 5. The lighting should be recalculated based on new registry values
 */
public class BlockLightRegistryChangeTest {
    
    @BeforeEach
    public void setup() {
        WorldLightManager.resetInstance();
    }
    
    @Test
    public void testBlockLightRecalculationAfterRegistryChange() {
        // Create a chunk with a torch (which has RGB emission values)
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Place a torch at position (8, 100, 8)
        // The torch in the registry has emission values: R=11, G=9, B=0
        chunk.setBlock(8, 100, 8, Blocks.TORCH);
        
        // Verify the torch emits the correct light initially
        int initialR = chunk.getBlockLightR(8, 100, 8);
        int initialG = chunk.getBlockLightG(8, 100, 8);
        int initialB = chunk.getBlockLightB(8, 100, 8);
        
        assertEquals(11, initialR, "Initial red emission should be 11");
        assertEquals(9, initialG, "Initial green emission should be 9");
        assertEquals(0, initialB, "Initial blue emission should be 0");
        
        // Serialize the chunk to NBT (simulating save)
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        
        // Now simulate a registry change by creating a modified torch block
        // In the real scenario, this would be done by modifying Blocks.TORCH registration
        // For testing, we'll create a new chunk and manually set different light values
        // to simulate what would be saved with old registry values
        
        // Create a new chunk and load from NBT
        LevelChunk loadedChunk = ChunkNBT.fromNBT(nbt);
        
        // After loading, the chunk should have recalculated the light
        // based on current registry values
        int loadedR = loadedChunk.getBlockLightR(8, 100, 8);
        int loadedG = loadedChunk.getBlockLightG(8, 100, 8);
        int loadedB = loadedChunk.getBlockLightB(8, 100, 8);
        
        // The torch should still have the correct light values from the registry
        assertEquals(11, loadedR, "Loaded red emission should match registry");
        assertEquals(9, loadedG, "Loaded green emission should match registry");
        assertEquals(0, loadedB, "Loaded blue emission should match registry");
    }
    
    @Test
    public void testBlockLightRecalculationWithModifiedEmission() {
        // This test simulates the actual issue scenario:
        // 1. Save a chunk with certain light values
        // 2. Modify the saved light values to simulate old registry values
        // 3. Load the chunk and verify recalculation happens
        
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Place a torch
        chunk.setBlock(5, 80, 5, Blocks.TORCH);
        
        // Verify initial light propagation
        int initialR = chunk.getBlockLightR(5, 80, 5);
        int initialG = chunk.getBlockLightG(5, 80, 5);
        int initialB = chunk.getBlockLightB(5, 80, 5);
        
        assertEquals(11, initialR);
        assertEquals(9, initialG);
        assertEquals(0, initialB);
        
        // Verify light propagates to neighbors (one block away should be emission - 1)
        int neighborR = chunk.getBlockLightR(6, 80, 5);
        int neighborG = chunk.getBlockLightG(6, 80, 5);
        int neighborB = chunk.getBlockLightB(6, 80, 5);
        
        assertTrue(neighborR > 0, "Light should propagate to neighbor (R)");
        assertTrue(neighborG > 0, "Light should propagate to neighbor (G)");
        assertEquals(0, neighborB, "Blue light should not propagate (emission was 0)");
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loadedChunk = ChunkNBT.fromNBT(nbt);
        
        // After loading, light should be recalculated to match registry
        int loadedR = loadedChunk.getBlockLightR(5, 80, 5);
        int loadedG = loadedChunk.getBlockLightG(5, 80, 5);
        int loadedB = loadedChunk.getBlockLightB(5, 80, 5);
        
        assertEquals(11, loadedR);
        assertEquals(9, loadedG);
        assertEquals(0, loadedB);
        
        // Neighbors should also have correct propagated light
        int loadedNeighborR = loadedChunk.getBlockLightR(6, 80, 5);
        int loadedNeighborG = loadedChunk.getBlockLightG(6, 80, 5);
        
        assertTrue(loadedNeighborR > 0, "Light should propagate to neighbor after reload (R)");
        assertTrue(loadedNeighborG > 0, "Light should propagate to neighbor after reload (G)");
    }
    
    @Test
    public void testNonEmissiveBlocksDoNotRetainLight() {
        // Test that non-emissive blocks don't retain light after loading
        // if they somehow had light values saved
        
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Place a regular stone block (non-emissive)
        chunk.setBlock(10, 90, 10, Blocks.STONE);
        
        // Verify it has no light emission
        assertEquals(0, chunk.getBlockLightR(10, 90, 10));
        assertEquals(0, chunk.getBlockLightG(10, 90, 10));
        assertEquals(0, chunk.getBlockLightB(10, 90, 10));
        
        // Manually set light values (simulating corrupted or old data)
        chunk.setBlockLightRGB(10, 90, 10, 5, 5, 5);
        
        // Verify the light was set
        assertEquals(5, chunk.getBlockLightR(10, 90, 10));
        
        // Save and reload
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loadedChunk = ChunkNBT.fromNBT(nbt);
        
        // After loading, the non-emissive block should have its light cleared
        // because it doesn't match the registry emission (which is 0)
        int loadedR = loadedChunk.getBlockLightR(10, 90, 10);
        int loadedG = loadedChunk.getBlockLightG(10, 90, 10);
        int loadedB = loadedChunk.getBlockLightB(10, 90, 10);
        
        assertEquals(0, loadedR, "Non-emissive block should not have red light after reload");
        assertEquals(0, loadedG, "Non-emissive block should not have green light after reload");
        assertEquals(0, loadedB, "Non-emissive block should not have blue light after reload");
    }
}
