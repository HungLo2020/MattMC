package mattmc.client.renderer.block;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlockFaceCollector, specifically the cross-chunk face culling functionality.
 */
public class BlockFaceCollectorTest {
    
    /**
     * Test that faces at chunk edges are properly culled when neighboring chunks have blocks.
     */
    @Test
    public void testChunkEdgeFaceCullingWithNeighborBlocks() {
        // Create two adjacent chunks
        LevelChunk chunk1 = new LevelChunk(0, 0);
        LevelChunk chunk2 = new LevelChunk(1, 0); // East neighbor
        
        // Place blocks at the edge of chunk1 and beginning of chunk2
        chunk1.setBlock(15, 64, 8, Blocks.STONE); // At east edge of chunk1
        chunk2.setBlock(0, 64, 8, Blocks.STONE);  // At west edge of chunk2 (adjacent to chunk1)
        
        // Create a neighbor accessor that can query both chunks
        BlockFaceCollector.ChunkNeighborAccessor accessor = (chunk, x, y, z) -> {
            // If within chunk bounds, use direct access
            if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
                return chunk.getBlock(x, y, z);
            }
            
            // Handle east boundary (x >= 16)
            if (x >= LevelChunk.WIDTH) {
                return chunk2.getBlock(x - LevelChunk.WIDTH, y, z);
            }
            
            return Blocks.AIR;
        };
        
        // Collect faces for chunk1
        BlockFaceCollector collector = new BlockFaceCollector();
        collector.setNeighborAccessor(accessor);
        collector.collectBlockFaces(15, 0, 8, Blocks.STONE, chunk1, 15, 64, 8);
        
        // The east face should NOT be visible because chunk2 has a block there
        assertTrue(collector.getEastFaces().isEmpty(), 
            "East face should be culled when neighboring chunk has a block");
        
        // Other faces should be visible (assuming air around)
        assertFalse(collector.getTopFaces().isEmpty(), 
            "Top face should be visible");
        assertFalse(collector.getBottomFaces().isEmpty(), 
            "Bottom face should be visible");
        assertFalse(collector.getNorthFaces().isEmpty(), 
            "North face should be visible");
        assertFalse(collector.getSouthFaces().isEmpty(), 
            "South face should be visible");
        assertFalse(collector.getWestFaces().isEmpty(), 
            "West face should be visible");
    }
    
    /**
     * Test that faces at chunk edges are rendered when neighboring chunks have air.
     */
    @Test
    public void testChunkEdgeFaceVisibilityWithNeighborAir() {
        // Create a chunk
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Place a block at the edge
        chunk.setBlock(15, 64, 8, Blocks.STONE); // At east edge
        
        // Create a neighbor accessor that returns air for neighboring chunks
        BlockFaceCollector.ChunkNeighborAccessor accessor = (ch, x, y, z) -> {
            // If within chunk bounds, use direct access
            if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
                return ch.getBlock(x, y, z);
            }
            
            // Outside chunk bounds = air
            return Blocks.AIR;
        };
        
        // Collect faces
        BlockFaceCollector collector = new BlockFaceCollector();
        collector.setNeighborAccessor(accessor);
        collector.collectBlockFaces(15, 0, 8, Blocks.STONE, chunk, 15, 64, 8);
        
        // All faces should be visible because neighboring chunk has air
        assertFalse(collector.getTopFaces().isEmpty(), 
            "Top face should be visible");
        assertFalse(collector.getBottomFaces().isEmpty(), 
            "Bottom face should be visible");
        assertFalse(collector.getNorthFaces().isEmpty(), 
            "North face should be visible");
        assertFalse(collector.getSouthFaces().isEmpty(), 
            "South face should be visible");
        assertFalse(collector.getWestFaces().isEmpty(), 
            "West face should be visible");
        assertFalse(collector.getEastFaces().isEmpty(), 
            "East face should be visible with air in neighboring chunk");
    }
    
    /**
     * Test face culling at all four horizontal chunk edges.
     */
    @Test
    public void testAllChunkEdgeFaceCulling() {
        // Create a chunk with neighboring chunks
        LevelChunk centerChunk = new LevelChunk(0, 0);
        LevelChunk northChunk = new LevelChunk(0, -1);
        LevelChunk southChunk = new LevelChunk(0, 1);
        LevelChunk westChunk = new LevelChunk(-1, 0);
        LevelChunk eastChunk = new LevelChunk(1, 0);
        
        // Place blocks at edges and in neighboring chunks
        centerChunk.setBlock(8, 64, 0, Blocks.STONE);  // North edge
        northChunk.setBlock(8, 64, 15, Blocks.STONE);  // Adjacent in north chunk
        
        centerChunk.setBlock(8, 64, 15, Blocks.DIRT);  // South edge
        southChunk.setBlock(8, 64, 0, Blocks.DIRT);    // Adjacent in south chunk
        
        centerChunk.setBlock(0, 64, 8, Blocks.GRASS_BLOCK);  // West edge
        westChunk.setBlock(15, 64, 8, Blocks.GRASS_BLOCK);   // Adjacent in west chunk
        
        centerChunk.setBlock(15, 64, 8, Blocks.STONE);  // East edge
        eastChunk.setBlock(0, 64, 8, Blocks.STONE);     // Adjacent in east chunk
        
        // Create neighbor accessor
        BlockFaceCollector.ChunkNeighborAccessor accessor = (chunk, x, y, z) -> {
            if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
                return chunk.getBlock(x, y, z);
            }
            
            // Determine which chunk to query based on coordinates
            int targetChunkX = centerChunk.chunkX();
            int targetChunkZ = centerChunk.chunkZ();
            int targetX = x;
            int targetZ = z;
            
            // Adjust for X boundary crossing
            if (x < 0) {
                targetChunkX--;
                targetX = LevelChunk.WIDTH + x;
            } else if (x >= LevelChunk.WIDTH) {
                targetChunkX++;
                targetX = x - LevelChunk.WIDTH;
            }
            
            // Adjust for Z boundary crossing
            if (z < 0) {
                targetChunkZ--;
                targetZ = LevelChunk.DEPTH + z;
            } else if (z >= LevelChunk.DEPTH) {
                targetChunkZ++;
                targetZ = z - LevelChunk.DEPTH;
            }
            
            // Get the appropriate neighbor chunk
            LevelChunk targetChunk = null;
            if (targetChunkX == centerChunk.chunkX() - 1 && targetChunkZ == centerChunk.chunkZ()) {
                targetChunk = westChunk;
            } else if (targetChunkX == centerChunk.chunkX() + 1 && targetChunkZ == centerChunk.chunkZ()) {
                targetChunk = eastChunk;
            } else if (targetChunkX == centerChunk.chunkX() && targetChunkZ == centerChunk.chunkZ() - 1) {
                targetChunk = northChunk;
            } else if (targetChunkX == centerChunk.chunkX() && targetChunkZ == centerChunk.chunkZ() + 1) {
                targetChunk = southChunk;
            }
            
            if (targetChunk != null) {
                return targetChunk.getBlock(targetX, y, targetZ);
            }
            
            return Blocks.AIR;
        };
        
        // Test north edge
        BlockFaceCollector collector1 = new BlockFaceCollector();
        collector1.setNeighborAccessor(accessor);
        collector1.collectBlockFaces(8, 0, 0, Blocks.STONE, centerChunk, 8, 64, 0);
        assertTrue(collector1.getNorthFaces().isEmpty(), 
            "North face should be culled when neighboring chunk has a block");
        
        // Test south edge
        BlockFaceCollector collector2 = new BlockFaceCollector();
        collector2.setNeighborAccessor(accessor);
        collector2.collectBlockFaces(8, 0, 15, Blocks.DIRT, centerChunk, 8, 64, 15);
        assertTrue(collector2.getSouthFaces().isEmpty(), 
            "South face should be culled when neighboring chunk has a block");
        
        // Test west edge
        BlockFaceCollector collector3 = new BlockFaceCollector();
        collector3.setNeighborAccessor(accessor);
        collector3.collectBlockFaces(0, 0, 8, Blocks.GRASS_BLOCK, centerChunk, 0, 64, 8);
        assertTrue(collector3.getWestFaces().isEmpty(), 
            "West face should be culled when neighboring chunk has a block");
        
        // Test east edge
        BlockFaceCollector collector4 = new BlockFaceCollector();
        collector4.setNeighborAccessor(accessor);
        collector4.collectBlockFaces(15, 0, 8, Blocks.STONE, centerChunk, 15, 64, 8);
        assertTrue(collector4.getEastFaces().isEmpty(), 
            "East face should be culled when neighboring chunk has a block");
    }
    
    /**
     * Test that internal chunk faces are properly culled (no neighbor accessor needed).
     */
    @Test
    public void testInternalChunkFaceCulling() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Place two adjacent blocks
        chunk.setBlock(8, 64, 8, Blocks.STONE);
        chunk.setBlock(9, 64, 8, Blocks.STONE);
        
        // Collect faces for the first block (no neighbor accessor needed for internal culling)
        BlockFaceCollector collector = new BlockFaceCollector();
        collector.collectBlockFaces(8, 0, 8, Blocks.STONE, chunk, 8, 64, 8);
        
        // East face should be culled because there's a block at (9, 64, 8)
        assertTrue(collector.getEastFaces().isEmpty(), 
            "East face should be culled when adjacent block exists");
        
        // Other faces should be visible
        assertFalse(collector.getTopFaces().isEmpty(), "Top face should be visible");
        assertFalse(collector.getBottomFaces().isEmpty(), "Bottom face should be visible");
        assertFalse(collector.getNorthFaces().isEmpty(), "North face should be visible");
        assertFalse(collector.getSouthFaces().isEmpty(), "South face should be visible");
        assertFalse(collector.getWestFaces().isEmpty(), "West face should be visible");
    }
}
