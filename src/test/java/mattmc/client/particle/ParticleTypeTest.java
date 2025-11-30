package mattmc.client.particle;

import mattmc.registries.ParticleTypes;
import mattmc.core.particles.SimpleParticleType;
import mattmc.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the particle type registry and basic particle type functionality.
 */
class ParticleTypeTest {
    
    @Test
    void testSimpleParticleTypeCreation() {
        SimpleParticleType type = new SimpleParticleType(false);
        assertFalse(type.getOverrideLimiter());
        assertEquals(type, type.getType());
    }
    
    @Test
    void testSimpleParticleTypeWithOverrideLimiter() {
        SimpleParticleType type = new SimpleParticleType(true);
        assertTrue(type.getOverrideLimiter());
    }
    
    @Test
    void testBuiltInParticleTypesRegistered() {
        // Check that built-in types are registered
        assertNotNull(ParticleTypes.SMOKE);
        assertNotNull(ParticleTypes.FLAME);
        assertNotNull(ParticleTypes.POOF);
        assertNotNull(ParticleTypes.EXPLOSION);
    }
    
    @Test
    void testParticleTypeKeyLookup() {
        ResourceLocation smokeKey = ParticleTypes.getKey(ParticleTypes.SMOKE);
        assertNotNull(smokeKey);
        assertEquals("mattmc", smokeKey.getNamespace());
        assertEquals("smoke", smokeKey.getPath());
    }
    
    @Test
    void testParticleTypeReverseLookup() {
        ResourceLocation key = new ResourceLocation("smoke");
        assertEquals(ParticleTypes.SMOKE, ParticleTypes.get(key));
    }
    
    @Test
    void testParticleTypesContains() {
        assertTrue(ParticleTypes.contains(new ResourceLocation("smoke")));
        assertTrue(ParticleTypes.contains(new ResourceLocation("mattmc:flame")));
        assertFalse(ParticleTypes.contains(new ResourceLocation("nonexistent")));
    }
    
    @Test
    void testRegisteredKeysNotEmpty() {
        assertFalse(ParticleTypes.getRegisteredKeys().isEmpty());
        assertTrue(ParticleTypes.getRegisteredKeys().size() >= 7); // At least our built-in types
    }
}
