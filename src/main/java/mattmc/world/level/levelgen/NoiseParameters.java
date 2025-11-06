package mattmc.world.level.levelgen;

/**
 * Minecraft-style noise parameters for terrain generation.
 * 
 * These parameters work together to create varied terrain:
 * - Continentalness: Controls land vs ocean
 * - Erosion: Affects terrain smoothness and steepness
 * - Peaks and Valleys: Controls mountain peaks and valley depth
 * - Weirdness: Adds variety and special terrain features
 * 
 * Based on Minecraft 1.18+ terrain generation system.
 */
public class NoiseParameters {
    private final OctaveNoise continentalness;
    private final OctaveNoise erosion;
    private final OctaveNoise peaksValleys;
    private final OctaveNoise weirdness;
    
    // Temperature and humidity for future biome support
    private final OctaveNoise temperature;
    private final OctaveNoise humidity;
    
    /**
     * Create noise parameters with a seed.
     * 
     * @param seed World seed for reproducible generation
     */
    public NoiseParameters(long seed) {
        // Initialize each noise type with different octave counts and seeds
        // These values are inspired by Minecraft's actual parameters
        
        // Continentalness: Large-scale land/ocean distribution
        this.continentalness = new OctaveNoise(seed, 4);
        
        // Erosion: Medium-scale terrain smoothness
        this.erosion = new OctaveNoise(seed + 1000, 4);
        
        // Peaks and Valleys: Mountain and valley formation
        this.peaksValleys = new OctaveNoise(seed + 2000, 4);
        
        // Weirdness: Adds variety and special features
        this.weirdness = new OctaveNoise(seed + 3000, 3);
        
        // Temperature and humidity for future biome support
        this.temperature = new OctaveNoise(seed + 4000, 3);
        this.humidity = new OctaveNoise(seed + 5000, 3);
    }
    
    /**
     * Sample continentalness at the given position.
     * 
     * Higher values = more continental (land), lower = oceanic
     * Range: approximately -1 to 1
     */
    public double sampleContinentalness(double x, double z) {
        // Large scale (low frequency) for continent formation
        return continentalness.sample(x, z, 0.0008, 1.0);
    }
    
    /**
     * Sample erosion at the given position.
     * 
     * Higher values = more erosion (smoother terrain)
     * Lower values = less erosion (sharper features)
     * Range: approximately -1 to 1
     */
    public double sampleErosion(double x, double z) {
        // Medium scale for terrain detail
        return erosion.sample(x, z, 0.002, 1.0);
    }
    
    /**
     * Sample peaks/valleys at the given position.
     * 
     * Positive values = peaks (mountains)
     * Negative values = valleys
     * Range: approximately -1 to 1
     */
    public double samplePeaksValleys(double x, double z) {
        // Medium-high scale for mountain/valley formation
        return peaksValleys.sample(x, z, 0.0015, 1.0);
    }
    
    /**
     * Sample weirdness at the given position.
     * 
     * Controls unusual terrain features and variation
     * Range: approximately -1 to 1
     */
    public double sampleWeirdness(double x, double z) {
        // Higher frequency for local variation
        return weirdness.sample(x, z, 0.003, 1.0);
    }
    
    /**
     * Sample temperature at the given position (for future biome support).
     * 
     * Higher = warmer, lower = colder
     * Range: approximately -1 to 1
     */
    public double sampleTemperature(double x, double z) {
        return temperature.sample(x, z, 0.001, 1.0);
    }
    
    /**
     * Sample humidity at the given position (for future biome support).
     * 
     * Higher = more humid, lower = drier
     * Range: approximately -1 to 1
     */
    public double sampleHumidity(double x, double z) {
        return humidity.sample(x, z, 0.001, 1.0);
    }
}
