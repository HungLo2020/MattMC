package net.minecraft.client.renderer.shaders.hooks;

import net.minecraft.client.renderer.shaders.pipeline.ShaderRenderingPipeline;
import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPhase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RenderingHooks.
 */
public class RenderingHooksTest {
    
    private MockShaderPipeline mockPipeline;
    
    @BeforeEach
    public void setUp() {
        mockPipeline = new MockShaderPipeline();
        RenderingHooks.reset();
    }
    
    @AfterEach
    public void tearDown() {
        RenderingHooks.reset();
    }
    
    @Test
    public void testSetActivePipeline() {
        assertNull(RenderingHooks.getActivePipeline());
        
        RenderingHooks.setActivePipeline(mockPipeline);
        assertSame(mockPipeline, RenderingHooks.getActivePipeline());
    }
    
    @Test
    public void testWorldRenderingLifecycle() {
        RenderingHooks.setActivePipeline(mockPipeline);
        
        RenderingHooks.onWorldRenderStart();
        assertTrue(mockPipeline.isBeginWorldRenderingCalled());
        assertTrue(RenderingHooks.isPipelineActive());
        
        RenderingHooks.onWorldRenderEnd();
        assertTrue(mockPipeline.isFinishWorldRenderingCalled());
        assertFalse(RenderingHooks.isPipelineActive());
    }
    
    @Test
    public void testTerrainRenderingHooks() {
        RenderingHooks.setActivePipeline(mockPipeline);
        RenderingHooks.onWorldRenderStart();
        
        RenderingHooks.onBeginTerrainRendering();
        assertTrue(mockPipeline.isBeginTerrainRenderingCalled());
        assertEquals(WorldRenderingPhase.TERRAIN_SOLID, 
            RenderingHooks.getPhaseTracker().getCurrentPhase());
        
        RenderingHooks.onEndTerrainRendering();
        assertTrue(mockPipeline.isEndTerrainRenderingCalled());
        assertEquals(WorldRenderingPhase.NONE, 
            RenderingHooks.getPhaseTracker().getCurrentPhase());
    }
    
    @Test
    public void testTranslucentRenderingHooks() {
        RenderingHooks.setActivePipeline(mockPipeline);
        RenderingHooks.onWorldRenderStart();
        
        RenderingHooks.onBeginTranslucentRendering();
        assertTrue(mockPipeline.isBeginTranslucentRenderingCalled());
        assertEquals(WorldRenderingPhase.TRANSLUCENT_TERRAIN, 
            RenderingHooks.getPhaseTracker().getCurrentPhase());
        
        RenderingHooks.onEndTranslucentRendering();
        assertTrue(mockPipeline.isEndTranslucentRenderingCalled());
        assertEquals(WorldRenderingPhase.NONE, 
            RenderingHooks.getPhaseTracker().getCurrentPhase());
    }
    
    @Test
    public void testHooksWithNoPipeline() {
        // Should not crash when no pipeline set
        assertDoesNotThrow(() -> {
            RenderingHooks.onWorldRenderStart();
            RenderingHooks.onBeginTerrainRendering();
            RenderingHooks.onEndTerrainRendering();
            RenderingHooks.onWorldRenderEnd();
        });
    }
    
    @Test
    public void testReset() {
        RenderingHooks.setActivePipeline(mockPipeline);
        RenderingHooks.onWorldRenderStart();
        
        assertTrue(RenderingHooks.isPipelineActive());
        
        RenderingHooks.reset();
        
        assertNull(RenderingHooks.getActivePipeline());
        assertFalse(RenderingHooks.isPipelineActive());
        assertEquals(WorldRenderingPhase.NONE, 
            RenderingHooks.getPhaseTracker().getCurrentPhase());
    }
    
    @Test
    public void testHookInvocationOrder() {
        RenderingHooks.setActivePipeline(mockPipeline);
        
        // Start world rendering
        RenderingHooks.onWorldRenderStart();
        assertTrue(mockPipeline.isBeginWorldRenderingCalled());
        
        // Begin terrain
        RenderingHooks.onBeginTerrainRendering();
        assertTrue(mockPipeline.isBeginTerrainRenderingCalled());
        
        // End terrain
        RenderingHooks.onEndTerrainRendering();
        assertTrue(mockPipeline.isEndTerrainRenderingCalled());
        
        // Begin translucent
        RenderingHooks.onBeginTranslucentRendering();
        assertTrue(mockPipeline.isBeginTranslucentRenderingCalled());
        
        // End translucent
        RenderingHooks.onEndTranslucentRendering();
        assertTrue(mockPipeline.isEndTranslucentRenderingCalled());
        
        // End world rendering
        RenderingHooks.onWorldRenderEnd();
        assertTrue(mockPipeline.isFinishWorldRenderingCalled());
    }
    
    @Test
    public void testPhaseTrackerAccess() {
        assertNotNull(RenderingHooks.getPhaseTracker());
        assertEquals(WorldRenderingPhase.NONE, 
            RenderingHooks.getPhaseTracker().getCurrentPhase());
    }
    
    /**
     * Mock shader pipeline for testing.
     */
    private static class MockShaderPipeline implements ShaderRenderingPipeline {
        private boolean beginWorldRenderingCalled = false;
        private boolean finishWorldRenderingCalled = false;
        private boolean beginTerrainRenderingCalled = false;
        private boolean endTerrainRenderingCalled = false;
        private boolean beginTranslucentRenderingCalled = false;
        private boolean endTranslucentRenderingCalled = false;
        
        @Override
        public void beginWorldRendering() {
            beginWorldRenderingCalled = true;
        }
        
        @Override
        public void finishWorldRendering() {
            finishWorldRenderingCalled = true;
        }
        
        @Override
        public void beginTerrainRendering() {
            beginTerrainRenderingCalled = true;
        }
        
        @Override
        public void endTerrainRendering() {
            endTerrainRenderingCalled = true;
        }
        
        @Override
        public void beginTranslucentRendering() {
            beginTranslucentRenderingCalled = true;
        }
        
        @Override
        public void endTranslucentRendering() {
            endTranslucentRenderingCalled = true;
        }
        
        public boolean isBeginWorldRenderingCalled() { return beginWorldRenderingCalled; }
        public boolean isFinishWorldRenderingCalled() { return finishWorldRenderingCalled; }
        public boolean isBeginTerrainRenderingCalled() { return beginTerrainRenderingCalled; }
        public boolean isEndTerrainRenderingCalled() { return endTerrainRenderingCalled; }
        public boolean isBeginTranslucentRenderingCalled() { return beginTranslucentRenderingCalled; }
        public boolean isEndTranslucentRenderingCalled() { return endTranslucentRenderingCalled; }
    }
}
