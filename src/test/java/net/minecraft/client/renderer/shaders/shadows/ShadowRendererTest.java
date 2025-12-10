package net.minecraft.client.renderer.shaders.shadows;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShadowRenderer class.
 * 
 * Note: These tests focus on the public API and matrix calculations.
 * Full integration tests would require OpenGL context.
 */
class ShadowRendererTest {
    
    @Test
    void testDirectivesConfiguration() {
        // Test that directives can be created and queried
        PackShadowDirectives directives = new PackShadowDirectives();
        
        assertNotNull(directives);
        assertTrue(directives.getDistance() > 0);
        assertTrue(directives.getResolution() > 0);
    }
    
    @Test
    void testMatricesConfiguration() {
        // Test shadow matrices can be created independently
        ShadowMatrices matrices = new ShadowMatrices(80.0f, 1.0f, 1024);
        
        assertNotNull(matrices);
        assertEquals(80.0f, matrices.getHalfPlaneLength(), 0.001f);
        assertEquals(1.0f, matrices.getRenderDistanceMultiplier(), 0.001f);
        assertEquals(1024, matrices.getResolution());
    }
    
    @Test
    void testMatrixCalculations() {
        ShadowMatrices matrices = new ShadowMatrices(120.0f, 1.5f, 2048);
        
        // Test projection matrix
        Matrix4f projection = matrices.getProjectionMatrix();
        assertNotNull(projection);
        
        // Test model-view matrix
        Matrix4f modelView = matrices.getModelViewMatrix();
        assertNotNull(modelView);
        
        // Test combined matrix
        Matrix4f combined = matrices.getShadowMatrix();
        assertNotNull(combined);
    }
    
    @Test
    void testShadowDirectionUpdate() {
        ShadowMatrices matrices = new ShadowMatrices(80.0f, 1.0f, 1024);
        
        // Update with angle
        float sunAngle = (float) Math.PI / 4.0f;
        matrices.update(sunAngle, 0.0f);
        
        Matrix4f modelView = matrices.getModelViewMatrix();
        assertNotNull(modelView);
    }
    
    @Test
    void testShadowDirectionUpdateWithVector() {
        ShadowMatrices matrices = new ShadowMatrices(80.0f, 1.0f, 1024);
        
        // Update with direction vector
        Vector3f lightDirection = new Vector3f(0.0f, -1.0f, 0.0f).normalize();
        matrices.updateWithDirection(lightDirection);
        
        Matrix4f modelView = matrices.getModelViewMatrix();
        assertNotNull(modelView);
    }
}
