package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderPass;
import mattmc.client.renderer.backend.opengl.OpenGLRenderBackend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the OpenGLRenderBackend implementation.
 * 
 * <p>Note: These tests focus on registry management and state tracking, which can be tested
 * without an OpenGL context. Tests that require actual GL rendering are marked and would
 * need to be run in an integration test environment with GL context.
 * 
 * <p>This is part of Stage 2 of the rendering refactor.
 */
public class OpenGLRenderBackendTest {
    
    private OpenGLRenderBackend backend;
    
    @BeforeEach
    public void setUp() {
        backend = new OpenGLRenderBackend();
    }
    
    // ===== Frame Lifecycle Tests =====
    
    @Test
    public void testFrameLifecycle() {
        // Initially, no frame should be active
        assertFalse(backend.isFrameActive(), "Frame should not be active initially");
        
        // Begin frame
        backend.beginFrame();
        assertTrue(backend.isFrameActive(), "Frame should be active after beginFrame()");
        
        // End frame
        backend.endFrame();
        assertFalse(backend.isFrameActive(), "Frame should not be active after endFrame()");
    }
    
    @Test
    public void testDoubleBeginFrameThrows() {
        backend.beginFrame();
        assertThrows(IllegalStateException.class, () -> {
            backend.beginFrame();
        }, "Calling beginFrame() twice should throw");
    }
    
    @Test
    public void testEndFrameWithoutBeginThrows() {
        assertThrows(IllegalStateException.class, () -> {
            backend.endFrame();
        }, "Calling endFrame() without beginFrame() should throw");
    }
    
    @Test
    public void testMultipleFrames() {
        // Frame 1
        backend.beginFrame();
        backend.endFrame();
        
        // Frame 2
        backend.beginFrame();
        backend.endFrame();
        
        // Frame 3
        backend.beginFrame();
        backend.endFrame();
        
        assertFalse(backend.isFrameActive(), "Frame should not be active after multiple frames");
    }
    
    // ===== Mesh Registry Tests =====
    
    @Test
    public void testMeshRegistryInitiallyEmpty() {
        assertEquals(0, backend.getMeshCount(), "Mesh registry should be initially empty");
    }
    
    @Test
    public void testRegisterMesh() {
        // We can't create a real ChunkVAO without GL context, but we can test the count
        // In a real scenario, this would use a mock or test double
        assertEquals(0, backend.getMeshCount());
        
        // Note: This test would need GL context to actually create ChunkVAO
        // For now, we verify the registry methods exist and have correct signatures
        assertFalse(backend.hasMesh(1), "Mesh 1 should not exist initially");
        assertFalse(backend.hasMesh(100), "Mesh 100 should not exist initially");
    }
    
    @Test
    public void testHasMeshReturnsFalseForUnregistered() {
        assertFalse(backend.hasMesh(0));
        assertFalse(backend.hasMesh(1));
        assertFalse(backend.hasMesh(-1));
        assertFalse(backend.hasMesh(999));
    }
    
    // ===== Material Registry Tests =====
    
    @Test
    public void testMaterialRegistryInitiallyEmpty() {
        assertEquals(0, backend.getMaterialCount(), "Material registry should be initially empty");
    }
    
    @Test
    public void testHasMaterialReturnsFalseForUnregistered() {
        assertFalse(backend.hasMaterial(0));
        assertFalse(backend.hasMaterial(1));
        assertFalse(backend.hasMaterial(-1));
        assertFalse(backend.hasMaterial(999));
    }
    
    // ===== Transform Registry Tests =====
    
    @Test
    public void testTransformRegistryInitiallyEmpty() {
        assertEquals(0, backend.getTransformCount(), "Transform registry should be initially empty");
    }
    
    @Test
    public void testRegisterTransform() {
        assertEquals(0, backend.getTransformCount());
        
        // Register a transform
        backend.registerTransform(0, 0.0f, 0.0f, 0.0f);
        assertEquals(1, backend.getTransformCount());
        assertTrue(backend.hasTransform(0));
        
        // Register another transform
        backend.registerTransform(1, 16.0f, 0.0f, 16.0f);
        assertEquals(2, backend.getTransformCount());
        assertTrue(backend.hasTransform(1));
        
        // Register with negative index
        backend.registerTransform(-1, -16.0f, 0.0f, -16.0f);
        assertEquals(3, backend.getTransformCount());
        assertTrue(backend.hasTransform(-1));
    }
    
    @Test
    public void testHasTransformReturnsFalseForUnregistered() {
        assertFalse(backend.hasTransform(0));
        assertFalse(backend.hasTransform(1));
        assertFalse(backend.hasTransform(-1));
        assertFalse(backend.hasTransform(999));
    }
    
    @Test
    public void testHasTransformReturnsTrueForRegistered() {
        backend.registerTransform(42, 100.0f, 200.0f, 300.0f);
        assertTrue(backend.hasTransform(42));
        assertFalse(backend.hasTransform(43));
    }
    
    @Test
    public void testRegisterMultipleTransforms() {
        backend.registerTransform(0, 0.0f, 0.0f, 0.0f);
        backend.registerTransform(1, 1.0f, 2.0f, 3.0f);
        backend.registerTransform(2, 4.0f, 5.0f, 6.0f);
        
        assertEquals(3, backend.getTransformCount());
        assertTrue(backend.hasTransform(0));
        assertTrue(backend.hasTransform(1));
        assertTrue(backend.hasTransform(2));
    }
    
    @Test
    public void testRegisterTransformOverwrites() {
        // Register transform 0
        backend.registerTransform(0, 1.0f, 2.0f, 3.0f);
        assertEquals(1, backend.getTransformCount());
        
        // Overwrite transform 0
        backend.registerTransform(0, 10.0f, 20.0f, 30.0f);
        assertEquals(1, backend.getTransformCount(), "Count should not increase on overwrite");
        assertTrue(backend.hasTransform(0));
    }
    
    // ===== Clear Tests =====
    
    @Test
    public void testClearAll() {
        // Register some transforms
        backend.registerTransform(0, 0.0f, 0.0f, 0.0f);
        backend.registerTransform(1, 1.0f, 1.0f, 1.0f);
        
        assertEquals(2, backend.getTransformCount());
        
        // Clear all
        backend.clearAll();
        
        assertEquals(0, backend.getMeshCount());
        assertEquals(0, backend.getMaterialCount());
        assertEquals(0, backend.getTransformCount());
        
        assertFalse(backend.hasTransform(0));
        assertFalse(backend.hasTransform(1));
    }
    
    @Test
    public void testClearAllWhenEmpty() {
        // Should not throw when clearing empty registries
        assertDoesNotThrow(() -> {
            backend.clearAll();
        });
        
        assertEquals(0, backend.getMeshCount());
        assertEquals(0, backend.getMaterialCount());
        assertEquals(0, backend.getTransformCount());
    }
    
    // ===== Submit Tests (Without GL Context) =====
    
    @Test
    public void testSubmitWithoutBeginFrameThrows() {
        DrawCommand cmd = new DrawCommand(1, 1, 1, RenderPass.OPAQUE);
        
        assertThrows(IllegalStateException.class, () -> {
            backend.submit(cmd);
        }, "Submitting without beginFrame() should throw");
    }
    
    @Test
    public void testSubmitNullCommandThrows() {
        backend.beginFrame();
        
        assertThrows(NullPointerException.class, () -> {
            backend.submit(null);
        }, "Submitting null command should throw");
    }
    
    @Test
    public void testSubmitWithMissingResourcesLogsWarning() {
        // This test verifies that submit() handles missing resources gracefully
        // In a real scenario with GL context, it would log warnings
        backend.beginFrame();
        
        DrawCommand cmd = new DrawCommand(999, 999, 999, RenderPass.OPAQUE);
        
        // Should not throw, just log warnings (which we can't easily verify in unit tests)
        assertDoesNotThrow(() -> {
            backend.submit(cmd);
        });
        
        backend.endFrame();
    }
    
    // ===== State Management Tests =====
    
    @Test
    public void testFrameStateResets() {
        backend.beginFrame();
        assertTrue(backend.isFrameActive());
        
        backend.endFrame();
        assertFalse(backend.isFrameActive());
        
        // Should be able to start a new frame
        backend.beginFrame();
        assertTrue(backend.isFrameActive());
        backend.endFrame();
    }
    
    @Test
    public void testRegistriesPersistAcrossFrames() {
        // Register transforms
        backend.registerTransform(0, 0.0f, 0.0f, 0.0f);
        backend.registerTransform(1, 1.0f, 1.0f, 1.0f);
        
        assertEquals(2, backend.getTransformCount());
        
        // Run a frame
        backend.beginFrame();
        backend.endFrame();
        
        // Registries should still have the transforms
        assertEquals(2, backend.getTransformCount());
        assertTrue(backend.hasTransform(0));
        assertTrue(backend.hasTransform(1));
        
        // Run another frame
        backend.beginFrame();
        backend.endFrame();
        
        // Still there
        assertEquals(2, backend.getTransformCount());
    }
    
    // ===== Edge Cases =====
    
    @Test
    public void testRegisterTransformWithZeroValues() {
        backend.registerTransform(0, 0.0f, 0.0f, 0.0f);
        
        assertEquals(1, backend.getTransformCount());
        assertTrue(backend.hasTransform(0));
    }
    
    @Test
    public void testRegisterTransformWithNegativeValues() {
        backend.registerTransform(0, -10.0f, -20.0f, -30.0f);
        
        assertEquals(1, backend.getTransformCount());
        assertTrue(backend.hasTransform(0));
    }
    
    @Test
    public void testRegisterTransformWithLargeValues() {
        backend.registerTransform(0, 10000.0f, 10000.0f, 10000.0f);
        
        assertEquals(1, backend.getTransformCount());
        assertTrue(backend.hasTransform(0));
    }
    
    @Test
    public void testRegisterTransformWithNegativeIndex() {
        backend.registerTransform(-1, 0.0f, 0.0f, 0.0f);
        backend.registerTransform(-100, 1.0f, 1.0f, 1.0f);
        
        assertEquals(2, backend.getTransformCount());
        assertTrue(backend.hasTransform(-1));
        assertTrue(backend.hasTransform(-100));
    }
    
    @Test
    public void testManyTransforms() {
        // Register many transforms
        for (int i = 0; i < 1000; i++) {
            backend.registerTransform(i, i * 1.0f, i * 2.0f, i * 3.0f);
        }
        
        assertEquals(1000, backend.getTransformCount());
        
        // Verify some are present
        assertTrue(backend.hasTransform(0));
        assertTrue(backend.hasTransform(500));
        assertTrue(backend.hasTransform(999));
        assertFalse(backend.hasTransform(1000));
    }
    
    // ===== Implementation Verification Tests =====
    
    @Test
    public void testBackendImplementsRenderBackendInterface() {
        // Verify that OpenGLRenderBackend implements RenderBackend
        assertTrue(backend instanceof RenderBackend,
                  "OpenGLRenderBackend should implement RenderBackend");
    }
    
    @Test
    public void testBackendHasRequiredMethods() {
        // This test verifies the methods exist and are callable
        // (compilation would fail if they didn't exist)
        assertDoesNotThrow(() -> {
            backend.beginFrame();
            backend.endFrame();
        });
        
        assertDoesNotThrow(() -> {
            backend.clearAll();
        });
        
        assertDoesNotThrow(() -> {
            backend.getMeshCount();
            backend.getMaterialCount();
            backend.getTransformCount();
        });
    }
    
    @Test
    public void testBackendCanBeUsedThroughInterface() {
        // Verify we can use the backend through the interface
        RenderBackend interfaceRef = backend;
        
        assertDoesNotThrow(() -> {
            interfaceRef.beginFrame();
            interfaceRef.endFrame();
        });
    }
}
