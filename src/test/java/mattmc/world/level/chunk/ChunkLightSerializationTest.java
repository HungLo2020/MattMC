package mattmc.world.level.chunk;

import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for light data serialization and deserialization in chunks.
 * Verifies that light values persist across save/load cycles.
 */
public class ChunkLightSerializationTest {
	
	@Test
	public void testLightDataRoundTrip() {
		// Create a chunk with some blocks and light data
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Set some blocks
		chunk.setBlock(0, 64, 0, Blocks.STONE);
		chunk.setBlock(5, 100, 5, Blocks.DIRT);
		
		// Set some light values
		chunk.setSkyLight(0, 64, 0, 10);
		chunk.setBlockLightRGBI(0, 64, 0, 5, 5, 5, 5);
		chunk.setSkyLight(5, 100, 5, 8);
		chunk.setBlockLightRGBI(5, 100, 5, 12, 12, 12, 12);
		
		// Set light in a different section
		chunk.setSkyLight(8, 200, 8, 6);
		chunk.setBlockLightRGBI(8, 200, 8, 3, 3, 3, 3);
		
		// Serialize to NBT
		Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
		
		// Deserialize from NBT
		LevelChunk loaded = ChunkNBT.fromNBT(nbt);
		
		// Verify blocks are preserved
		assertEquals(Blocks.STONE, loaded.getBlock(0, 64, 0));
		assertEquals(Blocks.DIRT, loaded.getBlock(5, 100, 5));
		
		// Verify light values are preserved
		assertEquals(10, loaded.getSkyLight(0, 64, 0), "Sky light at (0,64,0)");
		assertEquals(5, loaded.getBlockLightI(0, 64, 0), "Block light at (0,64,0)");
		assertEquals(8, loaded.getSkyLight(5, 100, 5), "Sky light at (5,100,5)");
		assertEquals(12, loaded.getBlockLightI(5, 100, 5), "Block light at (5,100,5)");
		assertEquals(6, loaded.getSkyLight(8, 200, 8), "Sky light at (8,200,8)");
		assertEquals(3, loaded.getBlockLightI(8, 200, 8), "Block light at (8,200,8)");
	}
	
	@Test
	public void testDefaultLightValuesPreserved() {
		// Create a chunk (light should default to skyLight=15, blockLight=0)
		LevelChunk chunk = new LevelChunk(5, 10);
		
		// Add one block so the section gets saved
		chunk.setBlock(0, 64, 0, Blocks.STONE);
		
		// Serialize and deserialize
		Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
		LevelChunk loaded = ChunkNBT.fromNBT(nbt);
		
		// Verify default light values are preserved
		assertEquals(15, loaded.getSkyLight(1, 64, 1), "Default sky light should be 15");
		assertEquals(0, loaded.getBlockLightI(1, 64, 1), "Default block light should be 0");
	}
	
	@Test
	public void testLightWithModifiedValues() {
		LevelChunk chunk = new LevelChunk(0, 0);
		
		// Set a block and modify its light
		chunk.setBlock(8, 64, 8, Blocks.STONE);
		chunk.setSkyLight(8, 64, 8, 3);
		chunk.setBlockLightRGBI(8, 64, 8, 14, 14, 14, 14);
		
		// Serialize and deserialize
		Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
		LevelChunk loaded = ChunkNBT.fromNBT(nbt);
		
		// Verify
		assertEquals(Blocks.STONE, loaded.getBlock(8, 64, 8));
		assertEquals(3, loaded.getSkyLight(8, 64, 8));
		assertEquals(14, loaded.getBlockLightI(8, 64, 8));
	}
	
	@Test
	public void testChunkPosition() {
		// Create chunk at specific position
		LevelChunk chunk = new LevelChunk(42, -17);
		chunk.setBlock(0, 64, 0, Blocks.STONE);
		chunk.setSkyLight(3, 100, 7, 11);
		chunk.setBlockLightRGBI(3, 100, 7, 4, 4, 4, 4);
		
		// Serialize and deserialize
		Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
		LevelChunk loaded = ChunkNBT.fromNBT(nbt);
		
		// Verify position
		assertEquals(42, loaded.chunkX());
		assertEquals(-17, loaded.chunkZ());
		
		// Verify light data
		assertEquals(11, loaded.getSkyLight(3, 100, 7));
		assertEquals(4, loaded.getBlockLightI(3, 100, 7));
	}
}
