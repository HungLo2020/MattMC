package mattmc.client.particle;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SpriteSet functionality.
 */
class SpriteSetTest {
    
    @Test
    void testSimpleSpriteSetRandomSelection() {
        ParticleSprite sprite1 = new ParticleSprite("sprite1", 0.0f, 0.0f, 0.5f, 0.5f);
        ParticleSprite sprite2 = new ParticleSprite("sprite2", 0.5f, 0.0f, 1.0f, 0.5f);
        
        SpriteSet.Simple spriteSet = new SpriteSet.Simple(Arrays.asList(sprite1, sprite2));
        
        Random random = new Random(42); // Fixed seed for deterministic test
        ParticleSprite selected = spriteSet.get(random);
        
        assertNotNull(selected);
        assertTrue(selected == sprite1 || selected == sprite2);
    }
    
    @Test
    void testSimpleSpriteSetAgeBasedSelection() {
        ParticleSprite sprite0 = new ParticleSprite("sprite0", 0.0f, 0.0f, 0.25f, 1.0f);
        ParticleSprite sprite1 = new ParticleSprite("sprite1", 0.25f, 0.0f, 0.5f, 1.0f);
        ParticleSprite sprite2 = new ParticleSprite("sprite2", 0.5f, 0.0f, 0.75f, 1.0f);
        ParticleSprite sprite3 = new ParticleSprite("sprite3", 0.75f, 0.0f, 1.0f, 1.0f);
        
        SpriteSet.Simple spriteSet = new SpriteSet.Simple(Arrays.asList(sprite0, sprite1, sprite2, sprite3));
        
        // At age 0, should return first sprite
        assertEquals(sprite0, spriteSet.get(0, 20));
        
        // At end of lifetime, should return last sprite
        assertEquals(sprite3, spriteSet.get(20, 20));
        
        // At middle, should return middle sprites
        ParticleSprite midSprite = spriteSet.get(10, 20);
        assertTrue(midSprite == sprite1 || midSprite == sprite2);
    }
    
    @Test
    void testEmptySpriteSetReturnsNull() {
        SpriteSet.Simple spriteSet = new SpriteSet.Simple(Arrays.asList());
        
        assertNull(spriteSet.get(new Random()));
        assertNull(spriteSet.get(0, 10));
    }
    
    @Test
    void testSingleSpriteSet() {
        ParticleSprite sprite = new ParticleSprite("only", 0.0f, 0.0f, 1.0f, 1.0f);
        SpriteSet.Simple spriteSet = new SpriteSet.Simple(Arrays.asList(sprite));
        
        assertEquals(sprite, spriteSet.get(new Random()));
        assertEquals(sprite, spriteSet.get(0, 10));
        assertEquals(sprite, spriteSet.get(5, 10));
        assertEquals(sprite, spriteSet.get(10, 10));
    }
    
    @Test
    void testSpriteSetSize() {
        SpriteSet.Simple spriteSet = new SpriteSet.Simple(Arrays.asList(
            new ParticleSprite("1", 0, 0, 0, 0),
            new ParticleSprite("2", 0, 0, 0, 0),
            new ParticleSprite("3", 0, 0, 0, 0)
        ));
        
        assertEquals(3, spriteSet.size());
    }
}
