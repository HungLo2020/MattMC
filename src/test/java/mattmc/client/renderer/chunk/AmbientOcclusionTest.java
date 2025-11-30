package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.world.level.Level;
import mattmc.world.level.block.Block;
import mattmc.registries.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Minecraft-style ambient occlusion calculation.
 * 
 * <p>These tests verify that the AmbientOcclusion class correctly calculates
 * per-vertex AO values based on surrounding block geometry, following
 * Minecraft's algorithm.
 * 
 * <p>Key behaviors tested:
 * <ul>
 *   <li>Exposed vertices (surrounded by air) get full brightness (1.0)</li>
 *   <li>Vertices near solid blocks get reduced brightness (occlusion)</li>
 *   <li>Interior corners (two solid edges) get significant darkening</li>
 *   <li>Edge cases with one solid neighbor get moderate darkening</li>
 *   <li>Corner blocks (three solid neighbors) get maximum darkening</li>
 * </ul>
 */
public class AmbientOcclusionTest {
    
    /**
     * Test that a vertex on an exposed face (surrounded by air) gets full brightness.
     */
    @Test
    public void testExposedVertexFullBrightness() {
        Level level = new Level();
        LevelChunk chunk = level.getChunk(0, 0);
        
        // Clear the area around test position
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place a single stone block
        chunk.setBlock(8, y, 8, Blocks.STONE);
        
        // Ensure surrounding blocks are air
        chunk.setBlock(8, y + 1, 8, Blocks.AIR); // Above
        chunk.setBlock(7, y + 1, 8, Blocks.AIR); // Above-west
        chunk.setBlock(9, y + 1, 8, Blocks.AIR); // Above-east
        chunk.setBlock(8, y + 1, 7, Blocks.AIR); // Above-north
        chunk.setBlock(8, y + 1, 9, Blocks.AIR); // Above-south
        
        // Create FaceData for top face
        BlockFaceCollector.FaceData face = new BlockFaceCollector.FaceData(
            8f, 64f, 8f,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "top", null,
            chunk, 8, y, 8
        );
        
        // Create AO calculator
        AmbientOcclusion ao = new AmbientOcclusion();
        
        // Calculate AO for all 4 vertices of the top face
        for (int vertex = 0; vertex < 4; vertex++) {
            float aoValue = ao.calculateVertexAO(face, AmbientOcclusion.FACE_UP, vertex);
            
            // Exposed vertex should have full brightness (1.0)
            assertEquals(1.0f, aoValue, 0.01f, 
                "Exposed vertex " + vertex + " should have full brightness");
        }
    }
    
    /**
     * Test that a vertex next to a solid block gets reduced brightness.
     * 
     * For Minecraft-style AO, the sampling is done at the FACE level (block + face_normal).
     * For an UP face at (8, y, 8), samples are at (8, y+1, 8) + corner directions.
     * A solid block at (9, y+1, 8) (above and to the east) will darken the eastern vertices.
     */
    @Test
    public void testEdgeOcclusion() {
        Level level = new Level();
        LevelChunk chunk = level.getChunk(0, 0);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place the test block
        chunk.setBlock(8, y, 8, Blocks.STONE);
        
        // Place a block above and to the east - this creates edge occlusion on the UP face
        // Because AO samples at face level (y+1) + corner direction (EAST = +1,0,0)
        chunk.setBlock(9, y + 1, 8, Blocks.STONE);
        
        // Create FaceData for top face
        BlockFaceCollector.FaceData face = new BlockFaceCollector.FaceData(
            8f, 64f, 8f,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "top", null,
            chunk, 8, y, 8
        );
        
        AmbientOcclusion ao = new AmbientOcclusion();
        
        // The eastern vertices should be occluded by the neighboring block
        // Check that at least one vertex has reduced brightness
        boolean hasOcclusion = false;
        for (int vertex = 0; vertex < 4; vertex++) {
            float aoValue = ao.calculateVertexAO(face, AmbientOcclusion.FACE_UP, vertex);
            if (aoValue < 0.95f) {
                hasOcclusion = true;
            }
        }
        
        assertTrue(hasOcclusion, "Vertices near solid blocks should have reduced brightness");
    }
    
    /**
     * Test that interior corner (two solid edges) gets significant darkening.
     * 
     * For Minecraft-style AO, sampling is at face level.
     * Blocks at (9, y+1, 8) and (8, y+1, 9) will darken the SE corner of (8, y, 8)'s UP face.
     */
    @Test
    public void testCornerOcclusion() {
        Level level = new Level();
        LevelChunk chunk = level.getChunk(0, 0);
        
        int y = LevelChunk.worldYToChunkY(64);
        
        // Place the test block
        chunk.setBlock(8, y, 8, Blocks.STONE);
        
        // Create an interior corner by placing blocks above and at adjacent sides
        // These are at face level (y+1) in the corner directions
        chunk.setBlock(9, y + 1, 8, Blocks.STONE);  // East at face level
        chunk.setBlock(8, y + 1, 9, Blocks.STONE);  // South at face level
        chunk.setBlock(9, y + 1, 9, Blocks.STONE);  // Diagonal corner at face level
        
        // Create FaceData for top face
        BlockFaceCollector.FaceData face = new BlockFaceCollector.FaceData(
            8f, 64f, 8f,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "top", null,
            chunk, 8, y, 8
        );
        
        AmbientOcclusion ao = new AmbientOcclusion();
        
        // Find the most occluded vertex (should be the corner near east+south neighbors)
        float minAO = 1.0f;
        for (int vertex = 0; vertex < 4; vertex++) {
            float aoValue = ao.calculateVertexAO(face, AmbientOcclusion.FACE_UP, vertex);
            minAO = Math.min(minAO, aoValue);
        }
        
        // Corner vertex should be significantly darkened
        assertTrue(minAO < 0.7f, 
            "Interior corner should be significantly occluded, but AO was " + minAO);
    }
    
    /**
     * Test AO for all face directions.
     */
    @Test
    public void testAllFaceDirections() {
        Level level = new Level();
        LevelChunk chunk = level.getChunk(0, 0);
        
        int y = LevelChunk.worldYToChunkY(80); // Use higher Y to avoid ground
        
        // Place a single stone block surrounded by air
        chunk.setBlock(8, y, 8, Blocks.STONE);
        
        // Explicitly clear all surrounding blocks to ensure no occlusion
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        chunk.setBlock(8 + dx, y + dy, 8 + dz, Blocks.AIR);
                    }
                }
            }
        }
        
        AmbientOcclusion ao = new AmbientOcclusion();
        
        // Test all 6 faces
        String[] faceNames = {"UP", "DOWN", "NORTH", "SOUTH", "WEST", "EAST"};
        int[] faceIndices = {
            AmbientOcclusion.FACE_UP,
            AmbientOcclusion.FACE_DOWN,
            AmbientOcclusion.FACE_NORTH,
            AmbientOcclusion.FACE_SOUTH,
            AmbientOcclusion.FACE_WEST,
            AmbientOcclusion.FACE_EAST
        };
        
        for (int f = 0; f < 6; f++) {
            BlockFaceCollector.FaceData face = new BlockFaceCollector.FaceData(
                8f, 64f, 8f,
                0xFFFFFF, 1.0f, 1.0f,
                Blocks.STONE, faceNames[f].toLowerCase(), null,
                chunk, 8, y, 8
            );
            
            // All vertices on exposed faces should have full brightness
            for (int vertex = 0; vertex < 4; vertex++) {
                float aoValue = ao.calculateVertexAO(face, faceIndices[f], vertex);
                assertEquals(1.0f, aoValue, 0.01f,
                    "Face " + faceNames[f] + " vertex " + vertex + " should be fully lit");
            }
        }
    }
    
    /**
     * Test directional shade values match Minecraft's convention.
     */
    @Test
    public void testDirectionalShade() {
        // UP = brightest (1.0)
        assertEquals(1.0f, AmbientOcclusion.getDirectionalShade(AmbientOcclusion.FACE_UP), 0.001f);
        
        // DOWN = darkest (0.5)
        assertEquals(0.5f, AmbientOcclusion.getDirectionalShade(AmbientOcclusion.FACE_DOWN), 0.001f);
        
        // NORTH/SOUTH = medium (0.8)
        assertEquals(0.8f, AmbientOcclusion.getDirectionalShade(AmbientOcclusion.FACE_NORTH), 0.001f);
        assertEquals(0.8f, AmbientOcclusion.getDirectionalShade(AmbientOcclusion.FACE_SOUTH), 0.001f);
        
        // WEST/EAST = dim (0.6)
        assertEquals(0.6f, AmbientOcclusion.getDirectionalShade(AmbientOcclusion.FACE_WEST), 0.001f);
        assertEquals(0.6f, AmbientOcclusion.getDirectionalShade(AmbientOcclusion.FACE_EAST), 0.001f);
    }
    
    /**
     * Test that calculateFaceAO returns correct array size.
     */
    @Test
    public void testCalculateFaceAO() {
        Level level = new Level();
        LevelChunk chunk = level.getChunk(0, 0);
        
        int y = LevelChunk.worldYToChunkY(64);
        chunk.setBlock(8, y, 8, Blocks.STONE);
        
        BlockFaceCollector.FaceData face = new BlockFaceCollector.FaceData(
            8f, 64f, 8f,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "top", null,
            chunk, 8, y, 8
        );
        
        AmbientOcclusion ao = new AmbientOcclusion();
        float[] faceAO = ao.calculateFaceAO(face, AmbientOcclusion.FACE_UP);
        
        assertNotNull(faceAO);
        assertEquals(4, faceAO.length, "Face AO should have 4 vertex values");
        
        // All should be valid AO values between 0 and 1
        for (int i = 0; i < 4; i++) {
            assertTrue(faceAO[i] >= 0.0f && faceAO[i] <= 1.0f,
                "AO value should be between 0 and 1");
        }
    }
    
    /**
     * Test AO with null chunk returns full brightness (no occlusion).
     */
    @Test
    public void testNullChunkNoOcclusion() {
        BlockFaceCollector.FaceData face = new BlockFaceCollector.FaceData(
            8f, 64f, 8f,
            0xFFFFFF, 1.0f, 1.0f,
            Blocks.STONE, "top", null,
            null, 0, 0, 0  // No chunk
        );
        
        AmbientOcclusion ao = new AmbientOcclusion();
        
        for (int vertex = 0; vertex < 4; vertex++) {
            float aoValue = ao.calculateVertexAO(face, AmbientOcclusion.FACE_UP, vertex);
            assertEquals(1.0f, aoValue, 0.001f,
                "Null chunk should return full brightness");
        }
    }
}
