package mattmc.world.level.chunk;

import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LightStorage, ColumnHeightmap, and their integration with chunk serialization.
 */
public class LightStorageTest {
	
	@Test
	public void testLightStorageGetSet() {
		LightStorage storage = new LightStorage();
		
		// Test default values (sky = 15, block = 0)
		assertEquals(15, storage.getSky(0, 0, 0), "Default sky light should be 15");
		assertEquals(0, storage.getBlock(0, 0, 0), "Default block light should be 0");
		
		// Test setting sky light
		storage.setSky(5, 10, 7, 12);
		assertEquals(12, storage.getSky(5, 10, 7), "Sky light should be set to 12");
		assertEquals(0, storage.getBlock(5, 10, 7), "Block light should remain 0");
		
		// Test setting block light
		storage.setBlock(5, 10, 7, 8);
		assertEquals(12, storage.getSky(5, 10, 7), "Sky light should remain 12");
		assertEquals(8, storage.getBlock(5, 10, 7), "Block light should be set to 8");
		
		// Log test results
		System.out.println("[LightStorage] Get/Set test passed:");
		System.out.println("  - Sky light at (5,10,7): " + storage.getSky(5, 10, 7));
		System.out.println("  - Block light at (5,10,7): " + storage.getBlock(5, 10, 7));
	}
	
	@Test
	public void testLightStorageBoundaries() {
		LightStorage storage = new LightStorage();
		
		// Test all valid light levels (0-15)
		for (int level = 0; level <= 15; level++) {
			storage.setSky(0, 0, 0, level);
			assertEquals(level, storage.getSky(0, 0, 0), "Sky light level " + level + " should be preserved");
			
			storage.setBlock(0, 0, 0, level);
			assertEquals(level, storage.getBlock(0, 0, 0), "Block light level " + level + " should be preserved");
		}
		
		// Test invalid light levels
		assertThrows(IllegalArgumentException.class, () -> storage.setSky(0, 0, 0, -1));
		assertThrows(IllegalArgumentException.class, () -> storage.setSky(0, 0, 0, 16));
		assertThrows(IllegalArgumentException.class, () -> storage.setBlock(0, 0, 0, -1));
		assertThrows(IllegalArgumentException.class, () -> storage.setBlock(0, 0, 0, 16));
		
		System.out.println("[LightStorage] Boundary test passed - all light levels 0-15 work correctly");
	}
	
	@Test
	public void testLightStorageIndependence() {
		LightStorage storage = new LightStorage();
		
		// Set different values at different positions
		storage.setSky(0, 0, 0, 5);
		storage.setBlock(0, 0, 0, 10);
		storage.setSky(15, 15, 15, 3);
		storage.setBlock(15, 15, 15, 7);
		
		// Verify they don't interfere with each other
		assertEquals(5, storage.getSky(0, 0, 0));
		assertEquals(10, storage.getBlock(0, 0, 0));
		assertEquals(3, storage.getSky(15, 15, 15));
		assertEquals(7, storage.getBlock(15, 15, 15));
		
		System.out.println("[LightStorage] Independence test passed - positions don't interfere");
	}
	
	@Test
	public void testColumnHeightmapGetSet() {
		ColumnHeightmap heightmap = new ColumnHeightmap();
		
		// Test default values (MIN_Y)
		assertEquals(LevelChunk.MIN_Y, heightmap.getHeight(0, 0), "Default height should be MIN_Y");
		
		// Test setting heights
		heightmap.setHeight(5, 10, 100);
		assertEquals(100, heightmap.getHeight(5, 10), "Height should be set to 100");
		
		// Test different positions
		heightmap.setHeight(0, 0, 50);
		heightmap.setHeight(15, 15, 200);
		assertEquals(50, heightmap.getHeight(0, 0));
		assertEquals(200, heightmap.getHeight(15, 15));
		assertEquals(100, heightmap.getHeight(5, 10), "Other heights should not change");
		
		System.out.println("[ColumnHeightmap] Get/Set test passed:");
		System.out.println("  - Height at (0,0): " + heightmap.getHeight(0, 0));
		System.out.println("  - Height at (5,10): " + heightmap.getHeight(5, 10));
		System.out.println("  - Height at (15,15): " + heightmap.getHeight(15, 15));
	}
	
	@Test
	public void testChunkLightIntegration() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Test chunk-level light API
		chunk.setSkyLight(8, 64, 8, 14);
		chunk.setBlockLight(8, 64, 8, 5);
		
		assertEquals(14, chunk.getSkyLight(8, 64, 8), "Chunk sky light should be 14");
		assertEquals(5, chunk.getBlockLight(8, 64, 8), "Chunk block light should be 5");
		
		// Test across different sections
		chunk.setSkyLight(8, 0, 8, 10);    // Section 0
		chunk.setSkyLight(8, 64, 8, 14);   // Section 4
		chunk.setSkyLight(8, 200, 8, 7);   // Section 12
		
		assertEquals(10, chunk.getSkyLight(8, 0, 8));
		assertEquals(14, chunk.getSkyLight(8, 64, 8));
		assertEquals(7, chunk.getSkyLight(8, 200, 8));
		
		System.out.println("[Chunk Light Integration] Test passed:");
		System.out.println("  - Light at y=0: sky=" + chunk.getSkyLight(8, 0, 8));
		System.out.println("  - Light at y=64: sky=" + chunk.getSkyLight(8, 64, 8) + ", block=" + chunk.getBlockLight(8, 64, 8));
		System.out.println("  - Light at y=200: sky=" + chunk.getSkyLight(8, 200, 8));
	}
	
	@Test
	public void testChunkHeightmapIntegration() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Get heightmap and set values
		ColumnHeightmap heightmap = chunk.getHeightmap();
		heightmap.setHeight(8, 8, 75);
		
		// Verify through chunk API
		assertEquals(75, chunk.getHeightmap().getHeight(8, 8), "Heightmap should be accessible through chunk");
		
		System.out.println("[Chunk Heightmap Integration] Test passed - height at (8,8): " + heightmap.getHeight(8, 8));
	}
	
	@Test
	public void testLightSerializationRoundTrip(@TempDir Path tempDir) throws IOException {
		// Create a chunk with custom light values
		LevelChunk originalChunk = new LevelChunk(5, 10);
		
		// Set some blocks
		originalChunk.setBlock(8, 64, 8, Blocks.STONE);
		
		// Set custom light values
		originalChunk.setSkyLight(8, 64, 8, 12);
		originalChunk.setBlockLight(8, 64, 8, 7);
		originalChunk.setSkyLight(0, 0, 0, 8);
		originalChunk.setBlockLight(15, 383, 15, 9);
		
		// Set heightmap values
		originalChunk.getHeightmap().setHeight(8, 8, 65);
		originalChunk.getHeightmap().setHeight(0, 0, 30);
		originalChunk.getHeightmap().setHeight(15, 15, 100);
		
		// Serialize to NBT
		Map<String, Object> nbt = ChunkNBT.toNBT(originalChunk);
		
		// Verify NBT contains heightmap data
		assertTrue(nbt.containsKey("Heightmap"), "NBT should contain heightmap");
		
		// Deserialize from NBT
		LevelChunk loadedChunk = ChunkNBT.fromNBT(nbt);
		
		// Verify block data
		assertEquals(Blocks.STONE, loadedChunk.getBlock(8, 64, 8), "Block should be preserved");
		
		// Verify light data
		assertEquals(12, loadedChunk.getSkyLight(8, 64, 8), "Sky light should be preserved");
		assertEquals(7, loadedChunk.getBlockLight(8, 64, 8), "Block light should be preserved");
		assertEquals(8, loadedChunk.getSkyLight(0, 0, 0), "Sky light at corner should be preserved");
		assertEquals(9, loadedChunk.getBlockLight(15, 383, 15), "Block light at far corner should be preserved");
		
		// Verify heightmap data
		assertEquals(65, loadedChunk.getHeightmap().getHeight(8, 8), "Heightmap at (8,8) should be preserved");
		assertEquals(30, loadedChunk.getHeightmap().getHeight(0, 0), "Heightmap at (0,0) should be preserved");
		assertEquals(100, loadedChunk.getHeightmap().getHeight(15, 15), "Heightmap at (15,15) should be preserved");
		
		System.out.println("[Serialization Round-Trip] Test passed:");
		System.out.println("  - Chunk position: (" + loadedChunk.chunkX() + ", " + loadedChunk.chunkZ() + ")");
		System.out.println("  - Light at (8,64,8): sky=" + loadedChunk.getSkyLight(8, 64, 8) + ", block=" + loadedChunk.getBlockLight(8, 64, 8));
		System.out.println("  - Heightmap at (8,8): " + loadedChunk.getHeightmap().getHeight(8, 8));
		System.out.println("  - All light and heightmap data preserved correctly");
	}
	
	@Test
	public void testBackwardCompatibility(@TempDir Path tempDir) throws IOException {
		// Create an old-style NBT without light data (to test backward compatibility)
		LevelChunk chunk = new LevelChunk(3, 7);
		chunk.setBlock(8, 64, 8, Blocks.DIRT);
		
		Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
		
		// Remove light data to simulate old format
		nbt.remove("LightSections");
		nbt.remove("Heightmap");
		
		// Should load without errors
		LevelChunk loadedChunk = ChunkNBT.fromNBT(nbt);
		
		assertNotNull(loadedChunk, "Chunk should load even without light data");
		assertEquals(Blocks.DIRT, loadedChunk.getBlock(8, 64, 8), "Block data should still load");
		
		// Light should have default values
		assertEquals(15, loadedChunk.getSkyLight(0, 0, 0), "Sky light should default to 15");
		assertEquals(0, loadedChunk.getBlockLight(0, 0, 0), "Block light should default to 0");
		
		System.out.println("[Backward Compatibility] Test passed - old format loads with default light values");
	}
}
