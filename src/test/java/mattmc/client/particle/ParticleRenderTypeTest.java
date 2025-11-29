package mattmc.client.particle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ParticleRenderType enum.
 */
class ParticleRenderTypeTest {
    
    @Test
    void testTerrainSheetUsesTerrainAtlas() {
        assertTrue(ParticleRenderType.TERRAIN_SHEET.usesTerrainAtlas());
        assertFalse(ParticleRenderType.TERRAIN_SHEET.usesParticleAtlas());
    }
    
    @Test
    void testParticleSheetOpaqueUsesParticleAtlas() {
        assertTrue(ParticleRenderType.PARTICLE_SHEET_OPAQUE.usesParticleAtlas());
        assertFalse(ParticleRenderType.PARTICLE_SHEET_OPAQUE.usesTerrainAtlas());
    }
    
    @Test
    void testParticleSheetTranslucentUsesBlending() {
        assertTrue(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.usesBlending());
        assertFalse(ParticleRenderType.PARTICLE_SHEET_OPAQUE.usesBlending());
    }
    
    @Test
    void testAllTypesUseDepthTestExceptNoRender() {
        assertTrue(ParticleRenderType.TERRAIN_SHEET.usesDepthTest());
        assertTrue(ParticleRenderType.PARTICLE_SHEET_OPAQUE.usesDepthTest());
        assertTrue(ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.usesDepthTest());
        assertFalse(ParticleRenderType.NO_RENDER.usesDepthTest());
    }
    
    @Test
    void testParticleSheetLitUsesLighting() {
        assertTrue(ParticleRenderType.PARTICLE_SHEET_LIT.usesLighting());
        assertFalse(ParticleRenderType.PARTICLE_SHEET_OPAQUE.usesLighting());
    }
    
    @Test
    void testToStringReturnsName() {
        assertEquals("TERRAIN_SHEET", ParticleRenderType.TERRAIN_SHEET.toString());
        assertEquals("PARTICLE_SHEET_OPAQUE", ParticleRenderType.PARTICLE_SHEET_OPAQUE.toString());
        assertEquals("NO_RENDER", ParticleRenderType.NO_RENDER.toString());
    }
}
