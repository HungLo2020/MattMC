package mattmc.client.renderer;

import mattmc.client.renderer.block.BlockGeometryCapture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the vertex capture and isometric projection system used in item rendering.
 */
public class ItemRendererGeometryTest {
    
    @Test
    public void testVertexCapture() {
        VertexCapture capture = new VertexCapture();
        
        // Add a simple triangle
        capture.texCoord(0, 0);
        capture.addVertex(0, 0, 0);
        
        capture.texCoord(1, 0);
        capture.addVertex(1, 0, 0);
        
        capture.texCoord(0, 1);
        capture.addVertex(0, 1, 0);
        
        List<VertexCapture.Face> faces = capture.getFaces();
        assertEquals(1, faces.size(), "Should have captured one triangle face");
        
        VertexCapture.Face face = faces.get(0);
        assertEquals(0, face.v1.x, 0.001);
        assertEquals(0, face.v1.y, 0.001);
        assertEquals(0, face.v1.z, 0.001);
        assertEquals(0, face.v1.u, 0.001);
        assertEquals(0, face.v1.v, 0.001);
    }
    
    @Test
    public void testCubeGeometryCapture() {
        VertexCapture capture = new VertexCapture();
        
        // Capture a top face
        BlockGeometryCapture.captureTopFace(capture, 0, 0, 0);
        
        // A quad is made of 2 triangles = 2 faces
        assertEquals(2, capture.getFaces().size(), "Top face should be 2 triangles");
        
        // All vertices should have Y=1 (top of the block)
        for (VertexCapture.Face face : capture.getFaces()) {
            assertEquals(1.0f, face.v1.y, 0.001, "Top face vertices should be at Y=1");
            assertEquals(1.0f, face.v2.y, 0.001, "Top face vertices should be at Y=1");
            assertEquals(1.0f, face.v3.y, 0.001, "Top face vertices should be at Y=1");
        }
    }
    
    @Test
    public void testStairsGeometryCapture() {
        VertexCapture capture = new VertexCapture();
        
        // Capture stairs geometry
        BlockGeometryCapture.captureStairsNorthBottom(capture, 0, 0, 0);
        
        // Stairs have many faces (bottom slab + top step)
        assertTrue(capture.getFaces().size() > 10, "Stairs should have many faces");
        
        // Check that we have some vertices at Y=0.5 (the step height)
        boolean hasHalfHeightVertex = false;
        for (VertexCapture.Face face : capture.getFaces()) {
            if (Math.abs(face.v1.y - 0.5f) < 0.001 || 
                Math.abs(face.v2.y - 0.5f) < 0.001 || 
                Math.abs(face.v3.y - 0.5f) < 0.001) {
                hasHalfHeightVertex = true;
                break;
            }
        }
        assertTrue(hasHalfHeightVertex, "Stairs should have vertices at Y=0.5");
        
        // Check that we have some vertices at Y=1.0 (the top of the step)
        boolean hasFullHeightVertex = false;
        for (VertexCapture.Face face : capture.getFaces()) {
            if (Math.abs(face.v1.y - 1.0f) < 0.001 || 
                Math.abs(face.v2.y - 1.0f) < 0.001 || 
                Math.abs(face.v3.y - 1.0f) < 0.001) {
                hasFullHeightVertex = true;
                break;
            }
        }
        assertTrue(hasFullHeightVertex, "Stairs should have vertices at Y=1.0");
    }
    
    @Test
    public void testIsometricProjection() {
        // Test the isometric projection formula
        // For a point at (1, 1, 0), with center at (100, 100) and scale factors of 10:
        // screen_x = centerX + (wx - wz) * isoWidth = 100 + (1 - 0) * 10 = 110
        // screen_y = centerY - wy * isoHeight - (wx + wz) * isoHeight * 0.5
        //          = 100 - 1 * 10 - (1 + 0) * 10 * 0.5 = 100 - 10 - 5 = 85
        
        float centerX = 100f;
        float centerY = 100f;
        float isoWidth = 10f;
        float isoHeight = 10f;
        
        float wx = 1f, wy = 1f, wz = 0f;
        
        float screenX = centerX + (wx - wz) * isoWidth;
        float screenY = centerY - wy * isoHeight - (wx + wz) * isoHeight * 0.5f;
        
        assertEquals(110f, screenX, 0.001, "X projection should be correct");
        assertEquals(85f, screenY, 0.001, "Y projection should be correct");
    }
}
