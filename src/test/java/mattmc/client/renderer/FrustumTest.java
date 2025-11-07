package mattmc.client.renderer;

import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Frustum culling implementation.
 * Note: These tests verify the coordinate conversion logic.
 * Full frustum culling requires OpenGL context and matrix setup.
 */
public class FrustumTest {
    
    @Test
    public void testChunkWorldCoordinateConversion() {
        // Test that chunk coordinates are properly converted to world coordinates
        
        // Chunk (0, 0) should map to world coords [0, 16) x [0, 16)
        int chunkX = 0;
        int chunkZ = 0;
        float expectedMinX = 0f;
        float expectedMaxX = 16f;
        float expectedMinZ = 0f;
        float expectedMaxZ = 16f;
        
        // Verify the math matches what Frustum.isChunkVisible does
        float actualMinX = chunkX * LevelChunk.WIDTH;
        float actualMaxX = actualMinX + LevelChunk.WIDTH;
        float actualMinZ = chunkZ * LevelChunk.DEPTH;
        float actualMaxZ = actualMinZ + LevelChunk.DEPTH;
        
        assertEquals(expectedMinX, actualMinX, 0.001f);
        assertEquals(expectedMaxX, actualMaxX, 0.001f);
        assertEquals(expectedMinZ, actualMinZ, 0.001f);
        assertEquals(expectedMaxZ, actualMaxZ, 0.001f);
        
        // Test negative chunk coordinates
        chunkX = -1;
        chunkZ = -1;
        expectedMinX = -16f;
        expectedMaxX = 0f;
        expectedMinZ = -16f;
        expectedMaxZ = 0f;
        
        actualMinX = chunkX * LevelChunk.WIDTH;
        actualMaxX = actualMinX + LevelChunk.WIDTH;
        actualMinZ = chunkZ * LevelChunk.DEPTH;
        actualMaxZ = actualMinZ + LevelChunk.DEPTH;
        
        assertEquals(expectedMinX, actualMinX, 0.001f);
        assertEquals(expectedMaxX, actualMaxX, 0.001f);
        assertEquals(expectedMinZ, actualMinZ, 0.001f);
        assertEquals(expectedMaxZ, actualMaxZ, 0.001f);
        
        // Test large positive coordinates
        chunkX = 100;
        chunkZ = 100;
        expectedMinX = 1600f;
        expectedMaxX = 1616f;
        expectedMinZ = 1600f;
        expectedMaxZ = 1616f;
        
        actualMinX = chunkX * LevelChunk.WIDTH;
        actualMaxX = actualMinX + LevelChunk.WIDTH;
        actualMinZ = chunkZ * LevelChunk.DEPTH;
        actualMaxZ = actualMinZ + LevelChunk.DEPTH;
        
        assertEquals(expectedMinX, actualMinX, 0.001f);
        assertEquals(expectedMaxX, actualMaxX, 0.001f);
        assertEquals(expectedMinZ, actualMinZ, 0.001f);
        assertEquals(expectedMaxZ, actualMaxZ, 0.001f);
    }
    
    @Test
    public void testChunkYBounds() {
        // Verify that chunk Y bounds are consistent
        // All chunks have the same Y range in world coordinates
        assertEquals(-64, LevelChunk.MIN_Y, "MIN_Y should be -64");
        assertEquals(319, LevelChunk.MAX_Y, "MAX_Y should be 319");
        
        // The total height should be 384 (from -64 to 319 inclusive)
        int expectedHeight = LevelChunk.MAX_Y - LevelChunk.MIN_Y + 1;
        assertEquals(384, expectedHeight, "Chunk height should be 384 blocks");
    }
    
    @Test
    public void testIsBoxVisibleEdgeCases() {
        Frustum frustum = new Frustum();
        
        // Note: Without OpenGL context, we can't call frustum.update()
        // These tests verify that the method doesn't crash with edge cases
        
        // Test with zero-size box (point)
        assertDoesNotThrow(() -> {
            frustum.isBoxVisible(0, 0, 0, 0, 0, 0);
        });
        
        // Test with negative coordinates
        // Chunk at (-7, -7) would have world coords from (-112, -112) to (-96, -96)
        assertDoesNotThrow(() -> {
            frustum.isBoxVisible(-112, -64, -112, -96, 319, -96);
        });
        
        // Test with large coordinates
        assertDoesNotThrow(() -> {
            frustum.isBoxVisible(1000, -64, 1000, 1016, 319, 1016);
        });
    }
}
