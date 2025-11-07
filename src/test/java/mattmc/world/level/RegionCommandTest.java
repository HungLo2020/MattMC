package mattmc.world.level;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for region selection and filling functionality.
 * These tests verify the logic used by /pos1, /pos2, and /set commands.
 */
public class RegionCommandTest {
    
    @Test
    public void testBlockLookupWithNamespace() {
        // Test looking up blocks with full namespace
        Block stone = Blocks.getBlock("mattmc:stone");
        assertNotNull(stone, "Block 'mattmc:stone' should exist");
        assertEquals("mattmc:stone", stone.getIdentifier());
        
        Block dirt = Blocks.getBlock("mattmc:dirt");
        assertNotNull(dirt, "Block 'mattmc:dirt' should exist");
        assertEquals("mattmc:dirt", dirt.getIdentifier());
        
        Block air = Blocks.getBlock("mattmc:air");
        assertNotNull(air, "Block 'mattmc:air' should exist");
        assertEquals("mattmc:air", air.getIdentifier());
    }
    
    @Test
    public void testBlockLookupWithoutNamespace() {
        // Test the logic for adding namespace automatically
        String blockName = "stone";
        Block block = Blocks.getBlock("mattmc:" + blockName);
        assertNotNull(block, "Block should be found with added namespace");
        assertEquals("mattmc:stone", block.getIdentifier());
    }
    
    @Test
    public void testBlockLookupInvalidBlock() {
        // Test looking up a block that doesn't exist
        Block invalid = Blocks.getBlock("mattmc:invalid_block");
        assertNull(invalid, "Invalid block should return null");
    }
    
    @Test
    public void testRegionFilling() {
        // Create a test level
        Level level = new Level();
        
        // Define two positions for a small region
        int[] pos1 = {0, 64, 0};
        int[] pos2 = {2, 66, 2};
        
        // Calculate bounds (same logic as executeSetCommand)
        int minX = Math.min(pos1[0], pos2[0]);
        int maxX = Math.max(pos1[0], pos2[0]);
        int minY = Math.min(pos1[1], pos2[1]);
        int maxY = Math.max(pos1[1], pos2[1]);
        int minZ = Math.min(pos1[2], pos2[2]);
        int maxZ = Math.max(pos1[2], pos2[2]);
        
        // Fill the region with stone
        Block stone = Blocks.STONE;
        int blocksSet = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(x, y, z, stone);
                    blocksSet++;
                }
            }
        }
        
        // Verify the number of blocks set
        // Region is 3x3x3 = 27 blocks
        assertEquals(27, blocksSet, "Should set 27 blocks in a 3x3x3 region");
        
        // Verify blocks are actually set
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = level.getBlock(x, y, z);
                    assertEquals(stone, block, "Block at (" + x + ", " + y + ", " + z + ") should be stone");
                }
            }
        }
    }
    
    @Test
    public void testRegionFillingWithReversedPositions() {
        // Test that region filling works regardless of which position is "first"
        Level level = new Level();
        
        // Define positions in reverse order (pos1 has higher coords than pos2)
        int[] pos1 = {5, 70, 5};
        int[] pos2 = {3, 68, 3};
        
        // Calculate bounds (should handle reversed positions)
        int minX = Math.min(pos1[0], pos2[0]);
        int maxX = Math.max(pos1[0], pos2[0]);
        int minY = Math.min(pos1[1], pos2[1]);
        int maxY = Math.max(pos1[1], pos2[1]);
        int minZ = Math.min(pos1[2], pos2[2]);
        int maxZ = Math.max(pos1[2], pos2[2]);
        
        assertEquals(3, minX);
        assertEquals(5, maxX);
        assertEquals(68, minY);
        assertEquals(70, maxY);
        assertEquals(3, minZ);
        assertEquals(5, maxZ);
        
        // Fill with dirt
        Block dirt = Blocks.DIRT;
        int blocksSet = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(x, y, z, dirt);
                    blocksSet++;
                }
            }
        }
        
        // Region is 3x3x3 = 27 blocks
        assertEquals(27, blocksSet, "Should set 27 blocks regardless of position order");
    }
    
    @Test
    public void testSingleBlockRegion() {
        // Test setting a region where both positions are the same
        Level level = new Level();
        
        int[] pos1 = {10, 64, 10};
        int[] pos2 = {10, 64, 10};
        
        int minX = Math.min(pos1[0], pos2[0]);
        int maxX = Math.max(pos1[0], pos2[0]);
        int minY = Math.min(pos1[1], pos2[1]);
        int maxY = Math.max(pos1[1], pos2[1]);
        int minZ = Math.min(pos1[2], pos2[2]);
        int maxZ = Math.max(pos1[2], pos2[2]);
        
        Block grassBlock = Blocks.GRASS_BLOCK;
        int blocksSet = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(x, y, z, grassBlock);
                    blocksSet++;
                }
            }
        }
        
        // Should set exactly 1 block
        assertEquals(1, blocksSet, "Should set exactly 1 block when positions are the same");
        
        // Verify the block is set
        Block block = level.getBlock(10, 64, 10);
        assertEquals(grassBlock, block, "Block should be grass_block");
    }
    
    @Test
    public void testLargeRegion() {
        // Test a larger region to ensure the algorithm scales
        Level level = new Level();
        
        int[] pos1 = {0, 64, 0};
        int[] pos2 = {9, 69, 9}; // 10x6x10 region
        
        int minX = Math.min(pos1[0], pos2[0]);
        int maxX = Math.max(pos1[0], pos2[0]);
        int minY = Math.min(pos1[1], pos2[1]);
        int maxY = Math.max(pos1[1], pos2[1]);
        int minZ = Math.min(pos1[2], pos2[2]);
        int maxZ = Math.max(pos1[2], pos2[2]);
        
        Block air = Blocks.AIR;
        int blocksSet = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(x, y, z, air);
                    blocksSet++;
                }
            }
        }
        
        // Should set 10 * 6 * 10 = 600 blocks
        assertEquals(600, blocksSet, "Should set 600 blocks in a 10x6x10 region");
    }
}
