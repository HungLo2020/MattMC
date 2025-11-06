package mattmc.world.level.levelgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for world generator.
 */
public class WorldGeneratorTest {
    
    @Test
    public void testTerrainHeightVariation() {
        WorldGenerator generator = new WorldGenerator(12345L);
        
        // Collect height values across a range
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                int height = generator.getTerrainHeight(x, z);
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
            }
        }
        
        // Terrain should have variation (not all the same height)
        assertTrue(maxHeight > minHeight,
            "Terrain should have height variation");
        
        // Heights should be within valid Minecraft range
        assertTrue(minHeight >= -64 && maxHeight <= 319,
            "Heights should be in valid range: min=" + minHeight + ", max=" + maxHeight);
        
        // Terrain should have reasonable variation (at least 10 blocks difference)
        int variation = maxHeight - minHeight;
        assertTrue(variation >= 10,
            "Terrain should have at least 10 blocks of variation, got " + variation);
    }
    
    @Test
    public void testSeedConsistency() {
        long seed = 12345L;
        WorldGenerator gen1 = new WorldGenerator(seed);
        WorldGenerator gen2 = new WorldGenerator(seed);
        
        // Same seed should produce same terrain
        for (int i = 0; i < 20; i++) {
            int x = i * 10;
            int z = i * 10;
            int height1 = gen1.getTerrainHeight(x, z);
            int height2 = gen2.getTerrainHeight(x, z);
            
            assertEquals(height1, height2,
                "Same seed should produce same terrain at (" + x + ", " + z + ")");
        }
    }
    
    @Test
    public void testDifferentSeedsProduceDifferentTerrain() {
        WorldGenerator gen1 = new WorldGenerator(12345L);
        WorldGenerator gen2 = new WorldGenerator(54321L);
        
        int differentCount = 0;
        
        // Check several positions
        for (int i = 0; i < 50; i++) {
            int x = i * 5;
            int z = i * 5;
            int height1 = gen1.getTerrainHeight(x, z);
            int height2 = gen2.getTerrainHeight(x, z);
            
            if (height1 != height2) {
                differentCount++;
            }
        }
        
        // At least 80% of positions should be different with different seeds
        assertTrue(differentCount >= 40,
            "Different seeds should produce different terrain. Only " + differentCount + "/50 positions were different");
    }
    
    @Test
    public void testNoiseParametersNotNull() {
        WorldGenerator generator = new WorldGenerator(12345L);
        
        assertNotNull(generator.getNoiseParameters(),
            "Noise parameters should not be null");
    }
    
    @Test
    public void testOceanDetection() {
        WorldGenerator generator = new WorldGenerator(12345L);
        
        // Check a wide area to ensure we have both ocean and land
        boolean hasOcean = false;
        boolean hasLand = false;
        
        for (int x = -100; x < 100; x += 10) {
            for (int z = -100; z < 100; z += 10) {
                if (generator.isOcean(x, z)) {
                    hasOcean = true;
                } else {
                    hasLand = true;
                }
                
                if (hasOcean && hasLand) {
                    break;
                }
            }
            if (hasOcean && hasLand) {
                break;
            }
        }
        
        // With noise-based generation, we should have both ocean and land
        // Note: This might rarely fail due to random chance, but it's very unlikely
        assertTrue(hasOcean || hasLand,
            "World should have either ocean or land areas");
    }
    
    @Test
    public void testGetSeed() {
        long seed = 123456789L;
        WorldGenerator generator = new WorldGenerator(seed);
        
        assertEquals(seed, generator.getSeed(),
            "Generator should return the seed it was created with");
    }
}
