package mattmc.client.particle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ParticleSprite UV coordinate handling.
 */
class ParticleSpriteTest {
    
    @Test
    void testSpriteCreation() {
        ParticleSprite sprite = new ParticleSprite("test", 0.0f, 0.0f, 0.5f, 0.5f);
        assertEquals("test", sprite.getName());
        assertEquals(0.0f, sprite.getU0(), 0.001f);
        assertEquals(0.0f, sprite.getV0(), 0.001f);
        assertEquals(0.5f, sprite.getU1(), 0.001f);
        assertEquals(0.5f, sprite.getV1(), 0.001f);
    }
    
    @Test
    void testGetUInterpolation() {
        ParticleSprite sprite = new ParticleSprite("test", 0.0f, 0.0f, 1.0f, 1.0f);
        
        // At offset 0, should return u0
        assertEquals(0.0f, sprite.getU(0), 0.001f);
        
        // At offset 16, should return u1
        assertEquals(1.0f, sprite.getU(16), 0.001f);
        
        // At offset 8, should be halfway
        assertEquals(0.5f, sprite.getU(8), 0.001f);
    }
    
    @Test
    void testGetVInterpolation() {
        ParticleSprite sprite = new ParticleSprite("test", 0.25f, 0.25f, 0.75f, 0.75f);
        
        // At offset 0
        assertEquals(0.25f, sprite.getV(0), 0.001f);
        
        // At offset 16
        assertEquals(0.75f, sprite.getV(16), 0.001f);
        
        // At offset 8
        assertEquals(0.5f, sprite.getV(8), 0.001f);
    }
    
    @Test
    void testToString() {
        ParticleSprite sprite = new ParticleSprite("flame", 0.1f, 0.2f, 0.3f, 0.4f);
        String str = sprite.toString();
        assertTrue(str.contains("flame"));
        assertTrue(str.contains("0.1"));
    }
}
