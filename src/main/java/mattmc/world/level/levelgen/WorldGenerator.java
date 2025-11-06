package mattmc.world.level.levelgen;

/**
 * World generator using Minecraft-style noise-based terrain generation.
 * 
 * Combines multiple noise parameters (continentalness, erosion, peaks/valleys, weirdness)
 * to create varied, natural-looking terrain with mountains, plains, and oceans.
 */
public class WorldGenerator {
    private final NoiseParameters noiseParams;
    private final long seed;
    
    // Terrain height configuration (matching Minecraft's ranges)
    private static final int SEA_LEVEL = 63;
    private static final int MIN_HEIGHT = -64;
    private static final int MAX_HEIGHT = 319;
    
    /**
     * Create a world generator with the given seed.
     * 
     * @param seed World seed for reproducible generation
     */
    public WorldGenerator(long seed) {
        this.seed = seed;
        this.noiseParams = new NoiseParameters(seed);
    }
    
    /**
     * Get the world seed.
     */
    public long getSeed() {
        return seed;
    }
    
    /**
     * Calculate the terrain height at the given world coordinates.
     * 
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Terrain height (Y coordinate)
     */
    public int getTerrainHeight(int worldX, int worldZ) {
        double x = worldX;
        double z = worldZ;
        
        // Sample all noise parameters
        double continentalness = noiseParams.sampleContinentalness(x, z);
        double erosion = noiseParams.sampleErosion(x, z);
        double pv = noiseParams.samplePeaksValleys(x, z);
        double weirdness = noiseParams.sampleWeirdness(x, z);
        
        // Combine noise values to determine base height
        // This is a simplified version of Minecraft's density function approach
        
        // Start with continentalness controlling base elevation
        // Map from [-1, 1] to elevation offset
        double baseElevation = continentalness * 40.0;
        
        // Erosion affects the terrain - high erosion = flatter
        // Low erosion = more dramatic height changes
        double erosionFactor = 1.0 - (erosion * 0.3);
        
        // Peaks and valleys add local height variation
        // This is scaled by erosion (less erosion = more dramatic peaks/valleys)
        double pvHeight = pv * 30.0 * erosionFactor;
        
        // Weirdness adds variety and prevents terrain from being too uniform
        double weirdnessEffect = weirdness * 8.0;
        
        // Combine all factors
        double heightOffset = baseElevation + pvHeight + weirdnessEffect;
        
        // Calculate final height
        int height = SEA_LEVEL + (int)Math.round(heightOffset);
        
        // Clamp to valid range
        height = Math.max(MIN_HEIGHT, Math.min(height, MAX_HEIGHT));
        
        return height;
    }
    
    /**
     * Check if a position should be ocean.
     * 
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return true if this position should be ocean
     */
    public boolean isOcean(int worldX, int worldZ) {
        int height = getTerrainHeight(worldX, worldZ);
        return height < SEA_LEVEL;
    }
    
    /**
     * Get the noise parameters for advanced usage.
     */
    public NoiseParameters getNoiseParameters() {
        return noiseParams;
    }
}
