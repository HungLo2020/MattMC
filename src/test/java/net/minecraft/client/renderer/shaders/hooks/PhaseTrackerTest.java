package net.minecraft.client.renderer.shaders.hooks;

import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PhaseTracker.
 */
public class PhaseTrackerTest {
    
    private PhaseTracker tracker;
    
    @BeforeEach
    public void setUp() {
        tracker = new PhaseTracker();
    }
    
    @Test
    public void testInitialState() {
        assertEquals(WorldRenderingPhase.NONE, tracker.getCurrentPhase());
        assertFalse(tracker.isWorldRendering());
    }
    
    @Test
    public void testBeginWorldRendering() {
        tracker.beginWorldRendering();
        assertTrue(tracker.isWorldRendering());
        assertEquals(WorldRenderingPhase.NONE, tracker.getCurrentPhase());
    }
    
    @Test
    public void testEndWorldRendering() {
        tracker.beginWorldRendering();
        tracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        
        tracker.endWorldRendering();
        assertFalse(tracker.isWorldRendering());
        assertEquals(WorldRenderingPhase.NONE, tracker.getCurrentPhase());
    }
    
    @Test
    public void testSetPhase() {
        tracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        assertEquals(WorldRenderingPhase.TERRAIN_SOLID, tracker.getCurrentPhase());
        
        tracker.setPhase(WorldRenderingPhase.TRANSLUCENT_TERRAIN);
        assertEquals(WorldRenderingPhase.TRANSLUCENT_TERRAIN, tracker.getCurrentPhase());
    }
    
    @Test
    public void testSetPhaseNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            tracker.setPhase(null);
        });
    }
    
    @Test
    public void testReset() {
        tracker.beginWorldRendering();
        tracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        
        tracker.reset();
        
        assertEquals(WorldRenderingPhase.NONE, tracker.getCurrentPhase());
        assertFalse(tracker.isWorldRendering());
    }
    
    @Test
    public void testPhaseTransitions() {
        tracker.beginWorldRendering();
        
        tracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        assertEquals(WorldRenderingPhase.TERRAIN_SOLID, tracker.getCurrentPhase());
        
        tracker.setPhase(WorldRenderingPhase.TRANSLUCENT_TERRAIN);
        assertEquals(WorldRenderingPhase.TRANSLUCENT_TERRAIN, tracker.getCurrentPhase());
        
        tracker.setPhase(WorldRenderingPhase.NONE);
        assertEquals(WorldRenderingPhase.NONE, tracker.getCurrentPhase());
    }
}
