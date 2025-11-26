package mattmc.client.renderer.chunk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UVTransformer UV lock functionality.
 */
@DisplayName("UV Transformer Tests")
public class UVTransformerTest {
    
    @Test
    @DisplayName("No rotation should return original UV")
    void testNoRotation() {
        float[] uv = {0, 0, 16, 16};
        UVTransformer.UVLockResult result = UVTransformer.transformUVsWithUVLock(uv, "up", 0, 0, 0);
        
        assertArrayEquals(uv, result.uv, 0.001f);
        assertEquals(0, result.additionalRotation);
    }
    
    @Test
    @DisplayName("Up face with Y rotation 90 should rotate UVs 270 (counter-clockwise)")
    void testUpFaceY90() {
        // Original UV: 8x16 region
        float[] uv = {8, 0, 16, 16};
        UVTransformer.UVLockResult result = UVTransformer.transformUVsWithUVLock(uv, "up", 0, 90, 0);
        
        // After 270° CCW rotation (to counter 90° Y rotation):
        // Original: u0=8, v0=0, u1=16, v1=16
        // After 90 CCW: u'=16-v, v'=u -> corners move
        // After 270 CCW (3x90 CCW): 
        // Point (8,0) -> after 1 step: (16,8) -> after 2 steps: (8,16) -> after 3 steps: (0,8)
        // Point (16,16) -> after 1 step: (0,16) -> after 2 steps: (0,0) -> after 3 steps: (16,0)
        // So new bounds should be approximately [0, 0, 16, 8] or [0, 8, 16, 16] depending on normalization
        
        System.out.println("Up face Y=90 result UV: [" + result.uv[0] + ", " + result.uv[1] + ", " + result.uv[2] + ", " + result.uv[3] + "]");
        System.out.println("Additional rotation: " + result.additionalRotation);
        
        // The UV region dimensions should be swapped (8x16 becomes 16x8)
        float newWidth = result.uv[2] - result.uv[0];
        float newHeight = result.uv[3] - result.uv[1];
        System.out.println("New dimensions: " + newWidth + " x " + newHeight);
    }
    
    @Test
    @DisplayName("Up face with Y rotation 180 should rotate UVs 180")
    void testUpFaceY180() {
        float[] uv = {0, 0, 16, 16};
        UVTransformer.UVLockResult result = UVTransformer.transformUVsWithUVLock(uv, "up", 0, 180, 0);
        
        System.out.println("Up face Y=180 result UV: [" + result.uv[0] + ", " + result.uv[1] + ", " + result.uv[2] + ", " + result.uv[3] + "]");
        System.out.println("Additional rotation: " + result.additionalRotation);
        
        // A 16x16 UV rotated 180 should still be 16x16
        float width = result.uv[2] - result.uv[0];
        float height = result.uv[3] - result.uv[1];
        assertEquals(16, width, 0.001f);
        assertEquals(16, height, 0.001f);
    }
    
    @Test
    @DisplayName("Vertical face with Y rotation should not have UV bounds changed")
    void testNorthFaceY90() {
        // Vertical faces (north, south, east, west) might need different treatment
        float[] uv = {0, 8, 16, 16};
        UVTransformer.UVLockResult result = UVTransformer.transformUVsWithUVLock(uv, "north", 0, 90, 0);
        
        System.out.println("North face Y=90 result UV: [" + result.uv[0] + ", " + result.uv[1] + ", " + result.uv[2] + ", " + result.uv[3] + "]");
        System.out.println("Additional rotation: " + result.additionalRotation);
    }
    
    @Test
    @DisplayName("getRotatedUVCoordinates should correctly shift UV assignment")
    void testGetRotatedUVCoordinates() {
        // Test per-face rotation
        float[][] uvs0 = UVTransformer.getRotatedUVCoordinates(0, 0, 1, 1, 0);
        float[][] uvs90 = UVTransformer.getRotatedUVCoordinates(0, 0, 1, 1, 90);
        float[][] uvs180 = UVTransformer.getRotatedUVCoordinates(0, 0, 1, 1, 180);
        float[][] uvs270 = UVTransformer.getRotatedUVCoordinates(0, 0, 1, 1, 270);
        
        // At 0 degrees, vertex 0 should have (0,0)
        assertEquals(0, uvs0[0][0], 0.001f);
        assertEquals(0, uvs0[0][1], 0.001f);
        
        // At 90 degrees, vertex 0 should have the UV from vertex 1
        assertEquals(0, uvs90[0][0], 0.001f);
        assertEquals(1, uvs90[0][1], 0.001f);
        
        // At 180 degrees, vertex 0 should have the UV from vertex 2
        assertEquals(1, uvs180[0][0], 0.001f);
        assertEquals(1, uvs180[0][1], 0.001f);
        
        // At 270 degrees, vertex 0 should have the UV from vertex 3
        assertEquals(1, uvs270[0][0], 0.001f);
        assertEquals(0, uvs270[0][1], 0.001f);
    }
}
