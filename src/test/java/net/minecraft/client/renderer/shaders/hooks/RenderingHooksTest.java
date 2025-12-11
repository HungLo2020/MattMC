package net.minecraft.client.renderer.shaders.hooks;

import net.minecraft.client.renderer.shaders.pipeline.ShaderRenderingPipeline;
import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPhase;
import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPipeline;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RenderingHooks.
 */
public class RenderingHooksTest {
    
    private MockShaderPipeline mockPipeline;
    private Matrix4f testModelView;
    private Matrix4f testProjection;
    
    @BeforeEach
    public void setUp() {
        mockPipeline = new MockShaderPipeline();
        testModelView = new Matrix4f().identity();
        testProjection = new Matrix4f().identity();
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
    public void testPhaseTrackerAccess() {
        assertNotNull(RenderingHooks.getPhaseTracker());
        assertEquals(WorldRenderingPhase.NONE, 
            RenderingHooks.getPhaseTracker().getCurrentPhase());
    }
    
    @Test
    public void testReset() {
        RenderingHooks.setActivePipeline(mockPipeline);
        
        RenderingHooks.reset();
        
        assertNull(RenderingHooks.getActivePipeline());
        assertFalse(RenderingHooks.isPipelineActive());
        assertEquals(WorldRenderingPhase.NONE, 
            RenderingHooks.getPhaseTracker().getCurrentPhase());
    }
    
    /**
     * Mock shader pipeline for testing.
     * Implements WorldRenderingPipeline interface.
     */
    private static class MockShaderPipeline implements WorldRenderingPipeline {
        private boolean beginLevelRenderingCalled = false;
        private boolean finalizeLevelRenderingCalled = false;
        private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
        
        @Override
        public void beginLevelRendering() {
            beginLevelRenderingCalled = true;
        }
        
        @Override
        public void finalizeLevelRendering() {
            finalizeLevelRenderingCalled = true;
        }
        
        @Override
        public WorldRenderingPhase getPhase() {
            return phase;
        }
        
        @Override
        public void setPhase(WorldRenderingPhase phase) {
            this.phase = phase;
        }
        
        @Override
        public boolean shouldDisableFrustumCulling() {
            return false;
        }
        
        @Override
        public boolean shouldDisableOcclusionCulling() {
            return false;
        }
        
        @Override
        public boolean shouldRenderUnderwaterOverlay() {
            return true;
        }
        
        @Override
        public boolean shouldRenderVignette() {
            return true;
        }
        
        @Override
        public boolean shouldRenderSun() {
            return true;
        }
        
        @Override
        public boolean shouldRenderMoon() {
            return true;
        }
        
        @Override
        public boolean shouldRenderWeather() {
            return true;
        }
        
        @Override
        public void destroy() {
        }
        
        public boolean isBeginLevelRenderingCalled() { return beginLevelRenderingCalled; }
        public boolean isFinalizeLevelRenderingCalled() { return finalizeLevelRenderingCalled; }
    }
}
