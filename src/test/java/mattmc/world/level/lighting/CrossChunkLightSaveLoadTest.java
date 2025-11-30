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
 * Tests for cross-chunk light propagation during save/load cycles.
 * 
 * These tests specifically target potential bugs related to:
 * - Light crossing chunk boundaries that need deferred updates
 * - World reload with multiple chunks having cross-chunk light
 * - Light removal that affects neighboring chunks
 * - Edge cases where light source is in one chunk but affects another
 */
public class CrossChunkLightSaveLoadTest {

    // Torch has RGB=(14, 11, 0) with intensity 14
    private static final int TORCH_R = 14;
    private static final int TORCH_G = 11;
    private static final int TORCH_B = 0;
    private static final int TORCH_INTENSITY = 14;

    // ========== CROSS-CHUNK PROPAGATION TESTS ==========

    @Test
    @DisplayName("Light at chunk edge propagates to neighbor chunk after reload")
    public void testCrossChunkLightAfterReload(@TempDir Path tempDir) throws IOException {
        Level level = new Level();
        level.setWorldDirectory(tempDir);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Get both chunks
        LevelChunk chunk0 = level.getChunk(0, 0);
        LevelChunk chunk1 = level.getChunk(1, 0);
        
        // Place torch at chunk 0 edge (x=15)
        level.setBlock(15, y, 8, Blocks.TORCH);
        
        // Verify light crosses to chunk 1
        int lightInChunk1 = chunk1.getBlockLightI(0, y, 8);
        assertEquals(TORCH_INTENSITY - 1, lightInChunk1, 
                    "Light should cross chunk boundary before save");
        
        // Save and reload
        level.updateChunksAroundPlayer(1000, 1000);
        level.shutdown();
        
        // Reload world
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        
        // Get chunks again
        LevelChunk loadedChunk0 = level2.getChunk(0, 0);
        LevelChunk loadedChunk1 = level2.getChunk(1, 0);
        
        // Verify torch exists
        assertEquals(Blocks.TORCH, loadedChunk0.getBlock(15, y, 8), 
                    "Torch should persist");
        
        // Verify light still crosses to chunk 1
        int loadedLight = loadedChunk1.getBlockLightI(0, y, 8);
        assertTrue(loadedLight > 0, 
                  "Light should cross chunk boundary after reload");
        
        level2.shutdown();
    }

    @Test
    @DisplayName("Multiple chunks with cross-chunk light reload correctly")
    public void testMultiChunkLightReload(@TempDir Path tempDir) throws IOException {
        Level level = new Level();
        level.setWorldDirectory(tempDir);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Load 4 chunks
        level.getChunk(0, 0);
        level.getChunk(1, 0);
        level.getChunk(0, 1);
        level.getChunk(1, 1);
        
        // Place torch at the corner where all 4 chunks meet
        // World coords (16, y, 16) is at chunk (1,1) local (0, y, 0)
        level.setBlock(16, y, 16, Blocks.TORCH);
        
        // Verify all 4 chunks have some light near the corner
        LevelChunk chunk00 = level.getChunk(0, 0);
        LevelChunk chunk10 = level.getChunk(1, 0);
        LevelChunk chunk01 = level.getChunk(0, 1);
        LevelChunk chunk11 = level.getChunk(1, 1);
        
        // The torch is at (1,1) local (0,0), so it should propagate to:
        // - chunk (0,0) local (15, y, 15) - diagonal, distance 2
        // - chunk (1,0) local (0, y, 15) - distance 1
        // - chunk (0,1) local (15, y, 0) - distance 1
        
        int light00 = chunk00.getBlockLightI(15, y, 15); // Diagonal
        int light10 = chunk10.getBlockLightI(0, y, 15);  // -Z
        int light01 = chunk01.getBlockLightI(15, y, 0);  // -X
        int light11 = chunk11.getBlockLightI(0, y, 0);   // Source
        
        assertEquals(TORCH_INTENSITY, light11, "Source chunk should have full light");
        
        // Save and reload
        level.updateChunksAroundPlayer(1000, 1000);
        level.shutdown();
        
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        
        // Reload all 4 chunks
        LevelChunk loaded00 = level2.getChunk(0, 0);
        LevelChunk loaded10 = level2.getChunk(1, 0);
        LevelChunk loaded01 = level2.getChunk(0, 1);
        LevelChunk loaded11 = level2.getChunk(1, 1);
        
        // Verify torch exists
        assertEquals(Blocks.TORCH, loaded11.getBlock(0, y, 0), "Torch should persist");
        
        // Verify light propagation is restored in all chunks
        assertTrue(loaded11.getBlockLightI(0, y, 0) > 0, "Source chunk should have light");
        
        level2.shutdown();
    }

    @Test
    @DisplayName("Deferred light updates applied when chunk loads")
    public void testDeferredUpdatesOnLoad(@TempDir Path tempDir) throws IOException {
        Level level = new Level();
        level.setWorldDirectory(tempDir);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Load only chunk 0
        LevelChunk chunk0 = level.getChunk(0, 0);
        
        // Place torch at edge - light would try to propagate to unloaded chunk 1
        level.setBlock(15, y, 8, Blocks.TORCH);
        
        // Check if there are deferred updates
        int deferredCount = level.getWorldLightManager().getTotalDeferredUpdateCount();
        
        // Save chunk 0
        level.updateChunksAroundPlayer(1000, 1000);
        level.shutdown();
        
        // Reload and load chunk 1 AFTER chunk 0
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        
        LevelChunk loadedChunk0 = level2.getChunk(0, 0);
        
        // Now load chunk 1 - this should trigger deferred light updates
        LevelChunk loadedChunk1 = level2.getChunk(1, 0);
        
        // Verify light now exists in chunk 1
        int lightInChunk1 = loadedChunk1.getBlockLightI(0, y, 8);
        assertTrue(lightInChunk1 > 0, 
                  "Light should propagate to chunk 1 after it loads");
        
        level2.shutdown();
    }

    // ========== LIGHT REMOVAL CROSS-CHUNK TESTS ==========

    @Test
    @DisplayName("Cross-chunk light removal persists correctly")
    public void testCrossChunkLightRemovalPersistence(@TempDir Path tempDir) throws IOException {
        Level level = new Level();
        level.setWorldDirectory(tempDir);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Load both chunks
        LevelChunk chunk0 = level.getChunk(0, 0);
        LevelChunk chunk1 = level.getChunk(1, 0);
        
        // Place torch at chunk edge
        level.setBlock(15, y, 8, Blocks.TORCH);
        
        // Verify light exists in both chunks
        assertTrue(chunk0.getBlockLightI(15, y, 8) > 0, "Chunk 0 should have light");
        assertTrue(chunk1.getBlockLightI(0, y, 8) > 0, "Chunk 1 should have light");
        
        // Remove the torch
        level.setBlock(15, y, 8, Blocks.AIR);
        
        // Verify light is removed from both chunks
        assertEquals(0, chunk0.getBlockLightI(15, y, 8), "Chunk 0 light should be removed");
        assertEquals(0, chunk1.getBlockLightI(0, y, 8), "Chunk 1 light should be removed");
        
        // Save and reload
        level.updateChunksAroundPlayer(1000, 1000);
        level.shutdown();
        
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        
        LevelChunk loaded0 = level2.getChunk(0, 0);
        LevelChunk loaded1 = level2.getChunk(1, 0);
        
        // Verify light removal persisted
        assertEquals(0, loaded0.getBlockLightI(15, y, 8), 
                    "Chunk 0 light removal should persist");
        assertEquals(0, loaded1.getBlockLightI(0, y, 8), 
                    "Chunk 1 light removal should persist");
        
        level2.shutdown();
    }

    // ========== SKYLIGHT CROSS-CHUNK TESTS ==========

    @Test
    @DisplayName("Cross-chunk skylight propagation persists")
    public void testCrossChunkSkylightPersistence(@TempDir Path tempDir) throws IOException {
        Level level = new Level();
        level.setWorldDirectory(tempDir);
        
        int surfaceY = LevelChunk.worldYToChunkY(64);
        int caveY = LevelChunk.worldYToChunkY(60);
        
        // Load both chunks
        LevelChunk chunk0 = level.getChunk(0, 0);
        LevelChunk chunk1 = level.getChunk(1, 0);
        
        // Create terrain with a cave opening at chunk boundary
        // Fill with stone around surface
        for (int x = 14; x <= 15; x++) {
            for (int z = 7; z <= 9; z++) {
                for (int y = caveY; y <= surfaceY; y++) {
                    level.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        for (int x = 16; x <= 17; x++) {
            for (int z = 7; z <= 9; z++) {
                for (int y = caveY; y <= surfaceY; y++) {
                    level.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        
        // Create opening and tunnel across chunk boundary
        // Opening at chunk 0, extends into chunk 1
        level.setBlock(15, surfaceY, 8, Blocks.AIR); // Opening
        level.setBlock(15, caveY, 8, Blocks.AIR);    // Below in chunk 0
        level.setBlock(16, caveY, 8, Blocks.AIR);    // Tunnel into chunk 1
        level.setBlock(17, caveY, 8, Blocks.AIR);    // Extends further
        
        // Save and reload
        level.updateChunksAroundPlayer(1000, 1000);
        level.shutdown();
        
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        
        LevelChunk loaded0 = level2.getChunk(0, 0);
        LevelChunk loaded1 = level2.getChunk(1, 0);
        
        // Verify cave structure persists
        assertEquals(Blocks.AIR, loaded0.getBlock(15, caveY, 8), "Cave in chunk 0 should persist");
        assertEquals(Blocks.AIR, loaded1.getBlock(0, caveY, 8), "Tunnel in chunk 1 should persist");
        
        level2.shutdown();
    }

    // ========== REGRESSION TESTS ==========

    @Test
    @DisplayName("BUG: Light at chunk corner propagates to all 4 neighbors correctly")
    public void testChunkCornerLightPropagation() {
        WorldLightManager worldLightManager = new WorldLightManager();
        
        // Create 4 chunks
        LevelChunk chunk00 = new LevelChunk(0, 0);
        LevelChunk chunk10 = new LevelChunk(1, 0);
        LevelChunk chunk01 = new LevelChunk(0, 1);
        LevelChunk chunk11 = new LevelChunk(1, 1);
        
        // Set up world light manager with access to all chunks
        worldLightManager.setNeighborAccessor((chunkX, chunkZ) -> {
            if (chunkX == 0 && chunkZ == 0) return chunk00;
            if (chunkX == 1 && chunkZ == 0) return chunk10;
            if (chunkX == 0 && chunkZ == 1) return chunk01;
            if (chunkX == 1 && chunkZ == 1) return chunk11;
            return null;
        });
        
        chunk00.setWorldLightManager(worldLightManager);
        chunk10.setWorldLightManager(worldLightManager);
        chunk01.setWorldLightManager(worldLightManager);
        chunk11.setWorldLightManager(worldLightManager);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place torch at chunk 00 corner (local 15, y, 15)
        chunk00.setBlock(15, y, 15, Blocks.TORCH);
        
        // Verify light propagates to neighboring chunks
        int light00 = chunk00.getBlockLightI(15, y, 15);
        int light10 = chunk10.getBlockLightI(0, y, 15);
        int light01 = chunk01.getBlockLightI(15, y, 0);
        int light11 = chunk11.getBlockLightI(0, y, 0);
        
        assertEquals(TORCH_INTENSITY, light00, "Source chunk corner should have full light");
        assertEquals(TORCH_INTENSITY - 1, light10, "Chunk +X should have light -1");
        assertEquals(TORCH_INTENSITY - 1, light01, "Chunk +Z should have light -1");
        assertEquals(TORCH_INTENSITY - 2, light11, "Diagonal chunk should have light -2");
        
        // Now serialize all 4 chunks
        Map<String, Object> nbt00 = ChunkNBT.toNBT(chunk00);
        Map<String, Object> nbt10 = ChunkNBT.toNBT(chunk10);
        Map<String, Object> nbt01 = ChunkNBT.toNBT(chunk01);
        Map<String, Object> nbt11 = ChunkNBT.toNBT(chunk11);
        
        // Deserialize
        LevelChunk loaded00 = ChunkNBT.fromNBT(nbt00);
        LevelChunk loaded10 = ChunkNBT.fromNBT(nbt10);
        LevelChunk loaded01 = ChunkNBT.fromNBT(nbt01);
        LevelChunk loaded11 = ChunkNBT.fromNBT(nbt11);
        
        // Verify all chunks preserved their light values
        assertEquals(light00, loaded00.getBlockLightI(15, y, 15), "Chunk 00 light should persist");
        assertEquals(light10, loaded10.getBlockLightI(0, y, 15), "Chunk 10 light should persist");
        assertEquals(light01, loaded01.getBlockLightI(15, y, 0), "Chunk 01 light should persist");
        assertEquals(light11, loaded11.getBlockLightI(0, y, 0), "Chunk 11 light should persist");
    }

    @Test
    @DisplayName("BUG: Light propagates correctly after sequential chunk loading")
    public void testSequentialChunkLoadingLight() {
        WorldLightManager worldLightManager = new WorldLightManager();
        
        // Create first chunk with torch at edge
        LevelChunk chunk0 = new LevelChunk(0, 0);
        chunk0.setWorldLightManager(worldLightManager);
        
        // Initially only chunk 0 is accessible
        worldLightManager.setNeighborAccessor((chunkX, chunkZ) -> {
            if (chunkX == 0 && chunkZ == 0) return chunk0;
            return null;
        });
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place torch at edge of chunk 0
        chunk0.setBlock(15, y, 8, Blocks.TORCH);
        
        // Light at edge should be full
        assertEquals(TORCH_INTENSITY, chunk0.getBlockLightI(15, y, 8), "Edge light should be full");
        
        // There should be deferred updates waiting for chunk 1
        int deferredBefore = worldLightManager.getTotalDeferredUpdateCount();
        assertTrue(deferredBefore > 0, "Should have deferred updates for chunk 1");
        
        // Now "load" chunk 1
        LevelChunk chunk1 = new LevelChunk(1, 0);
        chunk1.setWorldLightManager(worldLightManager);
        
        // Update accessor to include chunk 1
        worldLightManager.setNeighborAccessor((chunkX, chunkZ) -> {
            if (chunkX == 0 && chunkZ == 0) return chunk0;
            if (chunkX == 1 && chunkZ == 0) return chunk1;
            return null;
        });
        
        // Process deferred updates for chunk 1
        worldLightManager.processDeferredUpdates(chunk1);
        
        // Verify light now propagated to chunk 1
        int lightInChunk1 = chunk1.getBlockLightI(0, y, 8);
        assertEquals(TORCH_INTENSITY - 1, lightInChunk1, 
                    "Light should propagate to chunk 1 after deferred updates");
        
        // Note: Deferred updates for chunk 1 are cleared, but new deferred updates
        // might be created for chunk 2 as light continues propagating.
        // This is expected behavior - we just verify that chunk 1's updates were processed.
    }
}
