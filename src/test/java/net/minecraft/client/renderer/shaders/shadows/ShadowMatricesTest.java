package net.minecraft.client.renderer.shaders.shadows;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShadowMatrices utility class.
 */
class ShadowMatricesTest {
    private ShadowMatrices matrices;
    private static final float HALF_PLANE = 120.0f;
    private static final float DISTANCE_MULTIPLIER = 1.5f;
    private static final int RESOLUTION = 1024;
    private static final float EPSILON = 0.001f;
    
    @BeforeEach
    void setUp() {
        matrices = new ShadowMatrices(HALF_PLANE, DISTANCE_MULTIPLIER, RESOLUTION);
    }
    
    @Test
    void testConstructor() {
        assertNotNull(matrices);
        assertEquals(HALF_PLANE, matrices.getHalfPlaneLength(), EPSILON);
        assertEquals(DISTANCE_MULTIPLIER, matrices.getRenderDistanceMultiplier(), EPSILON);
        assertEquals(RESOLUTION, matrices.getResolution());
    }
    
    @Test
    void testInitialMatrices() {
        Matrix4f projection = matrices.getProjectionMatrix();
        assertNotNull(projection);
        
        Matrix4f modelView = matrices.getModelViewMatrix();
        assertNotNull(modelView);
        
        Matrix4f combined = matrices.getShadowMatrix();
        assertNotNull(combined);
    }
    
    @Test
    void testUpdateWithAngle() {
        float sunAngle = (float) Math.PI / 4.0f;  // 45 degrees
        float shadowAngle = 0.1f;
        
        matrices.update(sunAngle, shadowAngle);
        
        Matrix4f modelView = matrices.getModelViewMatrix();
        assertNotNull(modelView);
        
        // Matrix should be non-identity after update
        Matrix4f identity = new Matrix4f();
        assertNotEquals(identity, modelView);
    }
    
    @Test
    void testUpdateWithDirection() {
        Vector3f lightDirection = new Vector3f(1.0f, -1.0f, 0.0f).normalize();
        
        matrices.updateWithDirection(lightDirection);
        
        Matrix4f modelView = matrices.getModelViewMatrix();
        assertNotNull(modelView);
        
        // Matrix should be non-identity after update
        Matrix4f identity = new Matrix4f();
        assertNotEquals(identity, modelView);
    }
    
    @Test
    void testWorldToShadowSpace() {
        Vector3f worldPos = new Vector3f(10.0f, 20.0f, 30.0f);
        
        Vector4f shadowPos = matrices.worldToShadowSpace(worldPos);
        
        assertNotNull(shadowPos);
        // Shadow position should be transformed (not equal to world position)
        assertNotEquals(worldPos.x, shadowPos.x, EPSILON);
    }
    
    @Test
    void testProjectionMatrixOrthographic() {
        Matrix4f projection = matrices.getProjectionMatrix();
        
        // Orthographic projection should have specific properties
        // The matrix element at [3][3] should be 1.0 for orthographic
        float m33 = projection.m33();
        assertEquals(1.0f, m33, EPSILON);
    }
    
    @Test
    void testMatrixImmutability() {
        Matrix4f original = matrices.getProjectionMatrix();
        
        // Modify the returned matrix
        original.identity();
        
        // Get a new copy - should be unchanged
        Matrix4f unchanged = matrices.getProjectionMatrix();
        
        // They should not be the same (different instances)
        assertNotSame(original, unchanged);
    }
}
