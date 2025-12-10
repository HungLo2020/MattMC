package net.minecraft.client.renderer.shaders.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ShaderSystemLifecycle initialization order and lifecycle management.
 */
public class ShaderSystemLifecycleTest {
    
    @AfterEach
    public void cleanup() {
        // Clear the active pipeline after each test to avoid state leakage
        ShaderSystemLifecycle.getInstance().setActivePipeline(null);
    }
    
    @Test
    public void testGetInstance() {
        ShaderSystemLifecycle lifecycle1 = ShaderSystemLifecycle.getInstance();
        ShaderSystemLifecycle lifecycle2 = ShaderSystemLifecycle.getInstance();
        
        assertNotNull(lifecycle1);
        assertSame(lifecycle1, lifecycle2, "getInstance should return same instance");
    }
    
    @Test
    public void testInitialState() {
        assertFalse(ShaderSystemLifecycle.isEarlyInitialized(), 
            "Should not be early initialized initially");
        assertFalse(ShaderSystemLifecycle.isRenderSystemInitialized(), 
            "Should not be render system initialized initially");
        assertFalse(ShaderSystemLifecycle.isResourcesLoaded(), 
            "Should not have resources loaded initially");
    }
    
    @Test
    public void testActivePipelineInitiallyNull() {
        ShaderSystemLifecycle lifecycle = ShaderSystemLifecycle.getInstance();
        assertNull(lifecycle.getActivePipeline(), 
            "Active pipeline should be null initially");
    }
    
    @Test
    public void testSetActivePipeline() {
        ShaderSystemLifecycle lifecycle = ShaderSystemLifecycle.getInstance();
        
        // Create a mock pipeline
        MockPipeline pipeline = new MockPipeline();
        
        lifecycle.setActivePipeline(pipeline);
        assertSame(pipeline, lifecycle.getActivePipeline(), 
            "Should set active pipeline");
        
        // Setting null should clear
        lifecycle.setActivePipeline(null);
        assertNull(lifecycle.getActivePipeline(), 
            "Should clear active pipeline");
    }
    
    @Test
    public void testWorldRenderHooksWithNoPipeline() {
        ShaderSystemLifecycle lifecycle = ShaderSystemLifecycle.getInstance();
        lifecycle.setActivePipeline(null);
        
        // Should not throw when no pipeline is active
        assertDoesNotThrow(() -> lifecycle.onWorldRenderStart());
        assertDoesNotThrow(() -> lifecycle.onWorldRenderEnd());
    }
    
    @Test
    public void testWorldRenderHooksWithInactivePipeline() {
        ShaderSystemLifecycle lifecycle = ShaderSystemLifecycle.getInstance();
        
        MockPipeline pipeline = new MockPipeline();
        pipeline.setActive(false);
        lifecycle.setActivePipeline(pipeline);
        
        lifecycle.onWorldRenderStart();
        lifecycle.onWorldRenderEnd();
        
        assertFalse(pipeline.beginCalled, "Should not call begin on inactive pipeline");
        assertFalse(pipeline.finishCalled, "Should not call finish on inactive pipeline");
    }
    
    // Mock pipeline for testing
    private static class MockPipeline implements net.minecraft.client.renderer.shaders.pipeline.ShaderRenderingPipeline {
        private boolean active = true;
        boolean beginCalled = false;
        boolean finishCalled = false;
        
        void setActive(boolean active) {
            this.active = active;
        }
        
        @Override
        public void beginWorldRendering() {
            beginCalled = true;
        }
        
        @Override
        public void setPhase(net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPhase phase) {
        }
        
        @Override
        public void finishWorldRendering() {
            finishCalled = true;
        }
        
        @Override
        public void destroy() {
        }
        
        @Override
        public boolean isActive() {
            return active;
        }
    }
}
