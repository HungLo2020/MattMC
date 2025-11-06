package mattmc.world.level.levelgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Perlin noise generation.
 */
public class PerlinNoiseTest {
    
    @Test
    public void testNoiseReturnsValuesInRange() {
        PerlinNoise noise = new PerlinNoise(12345L);
        
        // Test 2D noise at various positions
        for (int i = 0; i < 100; i++) {
            double x = i * 0.1;
            double z = i * 0.1;
            double value = noise.noise(x, z);
            
            // Perlin noise should return values roughly in the range [-1, 1]
            // We allow a small margin for floating point precision
            assertTrue(value >= -1.5 && value <= 1.5,
                "Noise value " + value + " is out of expected range at (" + x + ", " + z + ")");
        }
    }
    
    @Test
    public void testNoiseContinuity() {
        PerlinNoise noise = new PerlinNoise(12345L);
        
        // Test that nearby points have similar values (continuity)
        double x = 10.0;
        double z = 10.0;
        double value1 = noise.noise(x, z);
        double value2 = noise.noise(x + 0.01, z);
        
        // Values should be close for nearby points
        double diff = Math.abs(value1 - value2);
        assertTrue(diff < 0.1,
            "Noise is not continuous: values " + value1 + " and " + value2 + " differ by " + diff);
    }
    
    @Test
    public void testSameInputProducesSameOutput() {
        PerlinNoise noise = new PerlinNoise(12345L);
        
        double x = 5.5;
        double z = 7.3;
        double value1 = noise.noise(x, z);
        double value2 = noise.noise(x, z);
        
        assertEquals(value1, value2, 0.0001,
            "Same input should produce same output");
    }
    
    @Test
    public void testDifferentSeedsProduceDifferentResults() {
        PerlinNoise noise1 = new PerlinNoise(12345L);
        PerlinNoise noise2 = new PerlinNoise(54321L);
        
        double x = 5.5;
        double z = 7.3;
        double value1 = noise1.noise(x, z);
        double value2 = noise2.noise(x, z);
        
        // Different seeds should produce different values (with very high probability)
        assertNotEquals(value1, value2, 0.0001,
            "Different seeds should produce different results");
    }
    
    @Test
    public void test3DNoise() {
        PerlinNoise noise = new PerlinNoise(12345L);
        
        // Test 3D noise at various positions
        for (int i = 0; i < 50; i++) {
            double x = i * 0.1;
            double y = i * 0.1;
            double z = i * 0.1;
            double value = noise.noise(x, y, z);
            
            // 3D noise should also return values roughly in the range [-1, 1]
            assertTrue(value >= -1.5 && value <= 1.5,
                "3D noise value " + value + " is out of expected range");
        }
    }
}
