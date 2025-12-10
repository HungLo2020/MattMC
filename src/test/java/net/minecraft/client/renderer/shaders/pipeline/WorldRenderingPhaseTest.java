package net.minecraft.client.renderer.shaders.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for WorldRenderingPhase enum.
 * Verifies IRIS verbatim implementation matches expected behavior.
 */
public class WorldRenderingPhaseTest {
    
    @Test
    public void testAllPhasesExist() {
        // Verify all 24 IRIS phases exist (IRIS 1.21.9 has exactly 24 phases)
        assertEquals(24, WorldRenderingPhase.values().length, 
            "Should have exactly 24 rendering phases matching IRIS");
    }
    
    @Test
    public void testNonePhaseIsFirst() {
        // NONE should be the first phase (ordinal 0)
        assertEquals(WorldRenderingPhase.NONE, WorldRenderingPhase.values()[0],
            "NONE phase should be first");
        assertEquals(0, WorldRenderingPhase.NONE.ordinal(),
            "NONE phase should have ordinal 0");
    }
    
    @Test
    public void testSkyPhases() {
        // Verify sky-related phases exist and are in correct order
        WorldRenderingPhase[] phases = WorldRenderingPhase.values();
        
        assertNotNull(WorldRenderingPhase.SKY);
        assertNotNull(WorldRenderingPhase.SUNSET);
        assertNotNull(WorldRenderingPhase.CUSTOM_SKY);
        assertNotNull(WorldRenderingPhase.SUN);
        assertNotNull(WorldRenderingPhase.MOON);
        assertNotNull(WorldRenderingPhase.STARS);
        assertNotNull(WorldRenderingPhase.VOID);
        
        // Verify they come before terrain phases
        assertTrue(WorldRenderingPhase.SKY.ordinal() < WorldRenderingPhase.TERRAIN_SOLID.ordinal(),
            "Sky phases should come before terrain");
    }
    
    @Test
    public void testTerrainPhases() {
        // Verify all terrain phases exist
        assertNotNull(WorldRenderingPhase.TERRAIN_SOLID);
        assertNotNull(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
        assertNotNull(WorldRenderingPhase.TERRAIN_CUTOUT);
        assertNotNull(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
        assertNotNull(WorldRenderingPhase.TRIPWIRE);
        
        // Verify terrain solid comes before translucent
        assertTrue(WorldRenderingPhase.TERRAIN_SOLID.ordinal() < WorldRenderingPhase.TERRAIN_TRANSLUCENT.ordinal(),
            "Solid terrain should render before translucent");
    }
    
    @Test
    public void testEntityPhases() {
        // Verify entity-related phases exist
        assertNotNull(WorldRenderingPhase.ENTITIES);
        assertNotNull(WorldRenderingPhase.BLOCK_ENTITIES);
        
        // Entities should come after terrain solid
        assertTrue(WorldRenderingPhase.TERRAIN_SOLID.ordinal() < WorldRenderingPhase.ENTITIES.ordinal(),
            "Entities render after terrain");
    }
    
    @Test
    public void testDebugAndOutlinePhases() {
        // Verify debug rendering phases
        assertNotNull(WorldRenderingPhase.DESTROY);
        assertNotNull(WorldRenderingPhase.OUTLINE);
        assertNotNull(WorldRenderingPhase.DEBUG);
    }
    
    @Test
    public void testHandPhases() {
        // Verify hand rendering phases
        assertNotNull(WorldRenderingPhase.HAND_SOLID);
        assertNotNull(WorldRenderingPhase.HAND_TRANSLUCENT);
        
        // Hand translucent should be last
        assertEquals(WorldRenderingPhase.HAND_TRANSLUCENT, 
            WorldRenderingPhase.values()[WorldRenderingPhase.values().length - 1],
            "HAND_TRANSLUCENT should be the last phase");
    }
    
    @Test
    public void testWeatherAndParticlePhases() {
        // Verify weather and particle phases exist
        assertNotNull(WorldRenderingPhase.PARTICLES);
        assertNotNull(WorldRenderingPhase.CLOUDS);
        assertNotNull(WorldRenderingPhase.RAIN_SNOW);
        assertNotNull(WorldRenderingPhase.WORLD_BORDER);
        
        // Particles should come after terrain
        assertTrue(WorldRenderingPhase.TERRAIN_SOLID.ordinal() < WorldRenderingPhase.PARTICLES.ordinal(),
            "Particles render after terrain");
    }
    
    @Test
    public void testPhaseEnumNames() {
        // Verify specific phase names match IRIS exactly
        assertEquals("NONE", WorldRenderingPhase.NONE.name());
        assertEquals("SKY", WorldRenderingPhase.SKY.name());
        assertEquals("TERRAIN_SOLID", WorldRenderingPhase.TERRAIN_SOLID.name());
        assertEquals("TERRAIN_TRANSLUCENT", WorldRenderingPhase.TERRAIN_TRANSLUCENT.name());
        assertEquals("ENTITIES", WorldRenderingPhase.ENTITIES.name());
        assertEquals("PARTICLES", WorldRenderingPhase.PARTICLES.name());
        assertEquals("HAND_TRANSLUCENT", WorldRenderingPhase.HAND_TRANSLUCENT.name());
    }
    
    @Test
    public void testPhaseValueOf() {
        // Verify valueOf works for all phases
        assertEquals(WorldRenderingPhase.NONE, WorldRenderingPhase.valueOf("NONE"));
        assertEquals(WorldRenderingPhase.SKY, WorldRenderingPhase.valueOf("SKY"));
        assertEquals(WorldRenderingPhase.TERRAIN_SOLID, WorldRenderingPhase.valueOf("TERRAIN_SOLID"));
        assertEquals(WorldRenderingPhase.ENTITIES, WorldRenderingPhase.valueOf("ENTITIES"));
        assertEquals(WorldRenderingPhase.HAND_TRANSLUCENT, WorldRenderingPhase.valueOf("HAND_TRANSLUCENT"));
    }
    
    @Test
    public void testFromTerrainRenderTypeNotNull() {
        // Verify the helper method exists (actual implementation would need RenderType mocks)
        assertNotNull(WorldRenderingPhase.class.getDeclaredMethods());
        
        // Check that fromTerrainRenderType method exists
        boolean hasMethod = false;
        for (java.lang.reflect.Method method : WorldRenderingPhase.class.getDeclaredMethods()) {
            if (method.getName().equals("fromTerrainRenderType")) {
                hasMethod = true;
                break;
            }
        }
        assertTrue(hasMethod, "Should have fromTerrainRenderType method matching IRIS");
    }
}
