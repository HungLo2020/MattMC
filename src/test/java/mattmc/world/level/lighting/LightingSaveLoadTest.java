package mattmc.world.level.lighting;

import mattmc.registries.Blocks;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.ChunkNBT;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for lighting persistence across save/load cycles.
 * 
 * These tests specifically target potential bugs in:
 * - Block light RGBI values persisting correctly through NBT serialization
 * - Skylight values persisting (not being overwritten on load)
 * - Light propagation state being maintained after chunk reload
 * - Cross-chunk light propagation after world reload
 * - Light removal state persisting correctly
 */
public class LightingSaveLoadTest {

    private WorldLightManager worldLightManager;
    
    // Torch has RGB=(14, 11, 0) with intensity 14
    private static final int TORCH_R = 14;
    private static final int TORCH_G = 11;
    private static final int TORCH_B = 0;
    private static final int TORCH_INTENSITY = 14;

    @BeforeEach
    public void setup() {
        worldLightManager = new WorldLightManager();
    }

    // ========== NBT SERIALIZATION TESTS ==========

    @Test
    @DisplayName("Block light RGBI values persist through NBT round-trip")
    public void testBlockLightRGBIPersistence() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place a torch
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        
        // Verify light before save
        assertEquals(TORCH_R, chunk.getBlockLightR(8, y, 8), "Red before save");
        assertEquals(TORCH_G, chunk.getBlockLightG(8, y, 8), "Green before save");
        assertEquals(TORCH_B, chunk.getBlockLightB(8, y, 8), "Blue before save");
        assertEquals(TORCH_INTENSITY, chunk.getBlockLightI(8, y, 8), "Intensity before save");
        
        // Serialize to NBT
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        
        // Deserialize from NBT
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Verify RGBI values are preserved
        assertEquals(TORCH_R, loaded.getBlockLightR(8, y, 8), "Red after load");
        assertEquals(TORCH_G, loaded.getBlockLightG(8, y, 8), "Green after load");
        assertEquals(TORCH_B, loaded.getBlockLightB(8, y, 8), "Blue after load");
        assertEquals(TORCH_INTENSITY, loaded.getBlockLightI(8, y, 8), "Intensity after load");
    }

    @Test
    @DisplayName("Propagated light values persist through NBT round-trip")
    public void testPropagatedLightPersistence() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place a torch
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        
        // Verify propagated light before save
        int propagatedI = chunk.getBlockLightI(10, y, 8); // Distance 2
        assertEquals(TORCH_INTENSITY - 2, propagatedI, "Propagated intensity before save");
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Verify propagated light is preserved
        assertEquals(propagatedI, loaded.getBlockLightI(10, y, 8), "Propagated intensity after load");
        assertEquals(TORCH_R, loaded.getBlockLightR(10, y, 8), "Propagated red after load");
        assertEquals(TORCH_G, loaded.getBlockLightG(10, y, 8), "Propagated green after load");
    }

    @Test
    @DisplayName("Skylight values persist through NBT round-trip")
    public void testSkylightPersistence() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int surfaceY = LevelChunk.worldYToChunkY(64);
        
        // Create a simple terrain - stone floor at y=64
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlock(x, surfaceY, z, Blocks.STONE);
            }
        }
        
        // Initialize skylight
        worldLightManager.initializeChunkSkylight(chunk);
        
        // Create a hole in the floor to let skylight propagate down
        chunk.setBlock(8, surfaceY, 8, Blocks.AIR);
        
        // Verify skylight exists below the hole
        int skylightAbove = chunk.getSkyLight(8, surfaceY + 1, 8);
        int skylightBelow = chunk.getSkyLight(8, surfaceY - 1, 8);
        
        assertTrue(skylightAbove > 0, "Skylight above floor should exist");
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Verify skylight is preserved
        assertEquals(skylightAbove, loaded.getSkyLight(8, surfaceY + 1, 8), 
                    "Skylight above floor should be preserved");
        assertEquals(skylightBelow, loaded.getSkyLight(8, surfaceY - 1, 8), 
                    "Skylight below floor should be preserved");
    }

    @Test
    @DisplayName("Zero light values persist (no phantom light after load)")
    public void testZeroLightPersistence() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place and remove a torch (should leave zero light)
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        chunk.setBlock(8, y, 8, Blocks.AIR);
        
        // Verify light is zero
        assertEquals(0, chunk.getBlockLightI(8, y, 8), "Light should be 0 after removal");
        assertEquals(0, chunk.getBlockLightI(9, y, 8), "Neighbor light should be 0");
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Verify zero values persist (no phantom light)
        assertEquals(0, loaded.getBlockLightI(8, y, 8), "Light should still be 0 after load");
        assertEquals(0, loaded.getBlockLightI(9, y, 8), "Neighbor light should still be 0");
    }

    // ========== LEVEL SAVE/LOAD TESTS ==========

    @Test
    @DisplayName("Block light persists through Level save/load cycle")
    public void testBlockLightLevelPersistence(@TempDir Path tempDir) throws IOException {
        // Create level and place torch
        Level level = new Level();
        level.setWorldDirectory(tempDir);
        
        LevelChunk chunk = level.getChunk(0, 0);
        int y = LevelChunk.worldYToChunkY(64);
        
        // Set a block (to ensure chunk is generated)
        level.setBlock(8, y, 8, Blocks.TORCH);
        
        // Get light values before save
        int lightR = chunk.getBlockLightR(8, y, 8);
        int lightG = chunk.getBlockLightG(8, y, 8);
        int lightI = chunk.getBlockLightI(8, y, 8);
        int propagatedI = chunk.getBlockLightI(10, y, 8);
        
        // Save and close
        level.updateChunksAroundPlayer(1000, 1000); // Force unload
        level.shutdown();
        
        // Reload
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        LevelChunk loaded = level2.getChunk(0, 0);
        
        // Verify torch is there
        assertEquals(Blocks.TORCH, loaded.getBlock(8, y, 8), "Torch should be saved");
        
        // Verify light values
        // Note: After loading, recalculateBlockLight should restore proper light
        assertTrue(loaded.getBlockLightI(8, y, 8) > 0, 
                  "Light at torch position should exist after load");
        
        level2.shutdown();
    }

    @Test
    @DisplayName("Enclosed room light persists correctly")
    public void testEnclosedRoomLightPersistence() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Create an enclosed room (3x3x3 stone box with hollow center)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // Create walls (skip center)
                    if (Math.abs(dx) == 1 || Math.abs(dy) == 1 || Math.abs(dz) == 1) {
                        chunk.setBlock(8 + dx, y + dy, 8 + dz, Blocks.STONE);
                    }
                }
            }
        }
        
        // The center at (8, y, 8) is air inside a fully enclosed room
        // Skylight should NOT reach here
        assertEquals(0, chunk.getSkyLight(8, y, 8), "Enclosed room should have no skylight");
        
        // Place a torch inside
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        int torchLight = chunk.getBlockLightI(8, y, 8);
        assertTrue(torchLight > 0, "Torch should emit light in enclosed room");
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Verify enclosed room state persists
        assertEquals(0, loaded.getSkyLight(8, y, 8), 
                    "Enclosed room should still have no skylight after load");
        assertEquals(Blocks.TORCH, loaded.getBlock(8, y, 8), "Torch should persist");
        
        // Block light should be restored (either from save or recalculation)
        assertTrue(loaded.getBlockLightI(8, y, 8) > 0, 
                  "Torch light should exist after load");
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    @DisplayName("Light at chunk boundaries persists correctly")
    public void testChunkBoundaryLightPersistence() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place torch at chunk edge
        chunk.setBlock(15, y, 8, Blocks.TORCH);
        
        int edgeLight = chunk.getBlockLightI(15, y, 8);
        int inwardLight = chunk.getBlockLightI(14, y, 8);
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Verify edge light persists
        assertEquals(edgeLight, loaded.getBlockLightI(15, y, 8), 
                    "Edge light should persist");
        assertEquals(inwardLight, loaded.getBlockLightI(14, y, 8), 
                    "Inward propagated light should persist");
    }

    @Test
    @DisplayName("Light in cave system persists correctly")
    public void testCaveLightPersistence() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int surfaceY = LevelChunk.worldYToChunkY(64);
        int caveY = LevelChunk.worldYToChunkY(32);
        
        // Create solid terrain from bottom to surface
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= surfaceY; y++) {
                    chunk.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Carve out a cave at y=32
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunk.setBlock(8 + dx, caveY, 8 + dz, Blocks.AIR);
            }
        }
        
        // Place torch in cave
        chunk.setBlock(8, caveY, 8, Blocks.TORCH);
        
        // Verify cave has light but no skylight
        assertTrue(chunk.getBlockLightI(8, caveY, 8) > 0, "Cave should have torch light");
        assertEquals(0, chunk.getSkyLight(8, caveY, 8), "Cave should have no skylight");
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Verify cave light state persists
        assertTrue(loaded.getBlockLightI(8, caveY, 8) > 0, 
                  "Cave torch light should persist");
        assertEquals(0, loaded.getSkyLight(8, caveY, 8), 
                    "Cave should still have no skylight after load");
    }

    @Test
    @DisplayName("Multiple light sources persist with correct interaction")
    public void testMultipleLightSourcesPersistence() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place two torches
        chunk.setBlock(4, y, 8, Blocks.TORCH);
        chunk.setBlock(12, y, 8, Blocks.TORCH);
        
        // Middle position gets light from both
        int middleLight = chunk.getBlockLightI(8, y, 8);
        assertTrue(middleLight > 0, "Middle should have combined light");
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Verify both torches and their light persist
        assertEquals(Blocks.TORCH, loaded.getBlock(4, y, 8), "First torch should persist");
        assertEquals(Blocks.TORCH, loaded.getBlock(12, y, 8), "Second torch should persist");
        assertTrue(loaded.getBlockLightI(8, y, 8) > 0, 
                  "Middle light should persist");
    }

    // ========== REGRESSION TESTS ==========

    @Test
    @DisplayName("BUG: Skylight not overwritten by initializeChunkSkylight on load")
    public void testSkylightNotOverwrittenOnLoad() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int surfaceY = LevelChunk.worldYToChunkY(64);
        
        // Create terrain with a cave opening
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlock(x, surfaceY, z, Blocks.STONE);
            }
        }
        
        // Initialize skylight
        worldLightManager.initializeChunkSkylight(chunk);
        
        // Create a complex cave system that has propagated skylight
        chunk.setBlock(8, surfaceY, 8, Blocks.AIR);      // Opening
        chunk.setBlock(8, surfaceY - 1, 8, Blocks.AIR);  // Below opening
        chunk.setBlock(9, surfaceY - 1, 8, Blocks.AIR);  // Side tunnel
        chunk.setBlock(10, surfaceY - 1, 8, Blocks.AIR); // Extends further
        
        // The side tunnel should have propagated skylight
        int tunnelSkylight = chunk.getSkyLight(10, surfaceY - 1, 8);
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // CRITICAL: The loaded chunk should preserve the propagated skylight
        // If initializeChunkSkylight is called after load, it might overwrite
        // the saved values with recalculated ones (which won't include propagation)
        int loadedTunnelSkylight = loaded.getSkyLight(10, surfaceY - 1, 8);
        assertEquals(tunnelSkylight, loadedTunnelSkylight, 
                    "Propagated skylight in tunnel should persist exactly as saved");
    }

    @Test
    @DisplayName("BUG: Block light correctly restored after load via recalculateBlockLight")
    public void testBlockLightRecalculationOnLoad() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place torch
        chunk.setBlock(8, y, 8, Blocks.TORCH);
        
        int originalLight = chunk.getBlockLightI(8, y, 8);
        int propagated5Away = chunk.getBlockLightI(13, y, 8);
        
        // Serialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        
        // Deserialize - this should call recalculateBlockLight internally
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Set world light manager to enable recalculation
        loaded.setWorldLightManager(worldLightManager);
        loaded.recalculateBlockLight();
        
        // Verify light is correctly recalculated
        assertEquals(originalLight, loaded.getBlockLightI(8, y, 8), 
                    "Torch light should match original");
        assertEquals(propagated5Away, loaded.getBlockLightI(13, y, 8), 
                    "Propagated light should match original");
    }

    @Test
    @DisplayName("Section-level light storage correctly serialized")
    public void testSectionLevelLightSerialization() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        // Place torches in different sections
        int section0Y = LevelChunk.worldYToChunkY(10);   // Section 0 (y < 16)
        int section4Y = LevelChunk.worldYToChunkY(80);   // Section ~5
        int section10Y = LevelChunk.worldYToChunkY(170); // Section ~10
        
        chunk.setBlock(8, section0Y, 8, Blocks.TORCH);
        chunk.setBlock(8, section4Y, 8, Blocks.TORCH);
        chunk.setBlock(8, section10Y, 8, Blocks.TORCH);
        
        // Verify all sections have light
        assertTrue(chunk.getBlockLightI(8, section0Y, 8) > 0, "Section 0 light");
        assertTrue(chunk.getBlockLightI(8, section4Y, 8) > 0, "Section 4 light");
        assertTrue(chunk.getBlockLightI(8, section10Y, 8) > 0, "Section 10 light");
        
        // Serialize and deserialize
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        LevelChunk loaded = ChunkNBT.fromNBT(nbt);
        
        // Verify all sections' light data persists
        assertEquals(Blocks.TORCH, loaded.getBlock(8, section0Y, 8), "Section 0 torch");
        assertEquals(Blocks.TORCH, loaded.getBlock(8, section4Y, 8), "Section 4 torch");
        assertEquals(Blocks.TORCH, loaded.getBlock(8, section10Y, 8), "Section 10 torch");
        
        assertTrue(loaded.getBlockLightI(8, section0Y, 8) > 0, "Section 0 light after load");
        assertTrue(loaded.getBlockLightI(8, section4Y, 8) > 0, "Section 4 light after load");
        assertTrue(loaded.getBlockLightI(8, section10Y, 8) > 0, "Section 10 light after load");
    }
}
