package net.minecraft.client.renderer.shaders.pipeline;

import net.minecraft.client.renderer.shaders.hooks.PhaseTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for phase transitions in the rendering pipeline.
 * Verifies correct phase tracking and transitions matching IRIS behavior.
 */
public class PhaseTransitionTest {
    
    private PhaseTracker phaseTracker;
    
    @BeforeEach
    public void setup() {
        phaseTracker = new PhaseTracker();
    }
    
    @Test
    public void testInitialPhaseIsNone() {
        assertEquals(WorldRenderingPhase.NONE, phaseTracker.getCurrentPhase(),
            "Initial phase should be NONE");
    }
    
    @Test
    public void testBeginWorldRenderingResetsPhase() {
        // Set a phase, then begin world rendering
        phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        phaseTracker.beginWorldRendering();
        
        assertEquals(WorldRenderingPhase.NONE, phaseTracker.getCurrentPhase(),
            "beginWorldRendering should reset phase to NONE");
        assertTrue(phaseTracker.isWorldRendering(),
            "World rendering should be active");
    }
    
    @Test
    public void testEndWorldRenderingResetsPhase() {
        phaseTracker.beginWorldRendering();
        phaseTracker.setPhase(WorldRenderingPhase.ENTITIES);
        phaseTracker.endWorldRendering();
        
        assertEquals(WorldRenderingPhase.NONE, phaseTracker.getCurrentPhase(),
            "endWorldRendering should reset phase to NONE");
        assertFalse(phaseTracker.isWorldRendering(),
            "World rendering should not be active");
    }
    
    @Test
    public void testSkyPhaseTransition() {
        phaseTracker.beginWorldRendering();
        
        // Transition through sky phases
        phaseTracker.setPhase(WorldRenderingPhase.SKY);
        assertEquals(WorldRenderingPhase.SKY, phaseTracker.getCurrentPhase());
        
        phaseTracker.setPhase(WorldRenderingPhase.SUNSET);
        assertEquals(WorldRenderingPhase.SUNSET, phaseTracker.getCurrentPhase());
        
        phaseTracker.setPhase(WorldRenderingPhase.CUSTOM_SKY);
        assertEquals(WorldRenderingPhase.CUSTOM_SKY, phaseTracker.getCurrentPhase());
        
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        assertEquals(WorldRenderingPhase.NONE, phaseTracker.getCurrentPhase());
    }
    
    @Test
    public void testTerrainPhaseTransitions() {
        phaseTracker.beginWorldRendering();
        
        // Test terrain phase transitions (IRIS MixinLevelRenderer pattern)
        phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        assertEquals(WorldRenderingPhase.TERRAIN_SOLID, phaseTracker.getCurrentPhase());
        
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
        assertEquals(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED, phaseTracker.getCurrentPhase());
        
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
        assertEquals(WorldRenderingPhase.TERRAIN_CUTOUT, phaseTracker.getCurrentPhase());
        
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
        assertEquals(WorldRenderingPhase.TERRAIN_TRANSLUCENT, phaseTracker.getCurrentPhase());
    }
    
    @Test
    public void testEntityPhaseTransition() {
        phaseTracker.beginWorldRendering();
        
        phaseTracker.setPhase(WorldRenderingPhase.ENTITIES);
        assertEquals(WorldRenderingPhase.ENTITIES, phaseTracker.getCurrentPhase());
        
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        phaseTracker.setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
        assertEquals(WorldRenderingPhase.BLOCK_ENTITIES, phaseTracker.getCurrentPhase());
    }
    
    @Test
    public void testParticleAndWeatherTransitions() {
        phaseTracker.beginWorldRendering();
        
        phaseTracker.setPhase(WorldRenderingPhase.PARTICLES);
        assertEquals(WorldRenderingPhase.PARTICLES, phaseTracker.getCurrentPhase());
        
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        phaseTracker.setPhase(WorldRenderingPhase.RAIN_SNOW);
        assertEquals(WorldRenderingPhase.RAIN_SNOW, phaseTracker.getCurrentPhase());
        
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        phaseTracker.setPhase(WorldRenderingPhase.CLOUDS);
        assertEquals(WorldRenderingPhase.CLOUDS, phaseTracker.getCurrentPhase());
    }
    
    @Test
    public void testNullPhaseThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            phaseTracker.setPhase(null);
        }, "Setting null phase should throw IllegalArgumentException");
    }
    
    @Test
    public void testResetClearsAllState() {
        phaseTracker.beginWorldRendering();
        phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        
        phaseTracker.reset();
        
        assertEquals(WorldRenderingPhase.NONE, phaseTracker.getCurrentPhase());
        assertFalse(phaseTracker.isWorldRendering());
    }
    
    @Test
    public void testMultiplePhaseTransitionsInOneFrame() {
        // Simulate IRIS MixinLevelRenderer phase transitions pattern
        phaseTracker.beginWorldRendering();
        
        // Sky rendering
        phaseTracker.setPhase(WorldRenderingPhase.SKY);
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        
        // Terrain rendering
        phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        
        // Entities
        phaseTracker.setPhase(WorldRenderingPhase.ENTITIES);
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        
        // Particles
        phaseTracker.setPhase(WorldRenderingPhase.PARTICLES);
        phaseTracker.setPhase(WorldRenderingPhase.NONE);
        
        // Final phase should be NONE
        assertEquals(WorldRenderingPhase.NONE, phaseTracker.getCurrentPhase());
    }
}
