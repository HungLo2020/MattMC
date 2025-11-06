package mattmc.world.level.levelgen;

/**
 * Multi-octave noise sampler.
 * 
 * Combines multiple octaves (frequencies) of Perlin noise to create
 * more detailed and natural-looking terrain. Each octave has twice
 * the frequency and half the amplitude of the previous one.
 * 
 * This is commonly used in Minecraft-style terrain generation.
 */
public class OctaveNoise {
    private final PerlinNoise[] octaves;
    private final double[] amplitudes;
    private final double maxAmplitude;
    
    /**
     * Create a multi-octave noise generator.
     * 
     * @param seed Random seed for reproducible generation
     * @param octaveCount Number of octaves to combine
     */
    public OctaveNoise(long seed, int octaveCount) {
        this.octaves = new PerlinNoise[octaveCount];
        this.amplitudes = new double[octaveCount];
        
        double totalAmplitude = 0.0;
        double amplitude = 1.0;
        
        // Initialize each octave with a different seed
        for (int i = 0; i < octaveCount; i++) {
            octaves[i] = new PerlinNoise(seed + i);
            amplitudes[i] = amplitude;
            totalAmplitude += amplitude;
            amplitude *= 0.5;
        }
        
        this.maxAmplitude = totalAmplitude;
    }
    
    /**
     * Sample 2D octave noise at the given position.
     * 
     * @param x X coordinate
     * @param z Z coordinate
     * @param frequency Base frequency multiplier
     * @param amplitude Overall amplitude multiplier
     * @return Noise value normalized to approximately [-1, 1]
     */
    public double sample(double x, double z, double frequency, double amplitude) {
        double result = 0.0;
        double freq = frequency;
        
        for (int i = 0; i < octaves.length; i++) {
            result += octaves[i].noise(x * freq, z * freq) * amplitudes[i];
            freq *= 2.0;
        }
        
        // Normalize and scale
        return (result / maxAmplitude) * amplitude;
    }
    
    /**
     * Sample 3D octave noise at the given position.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param frequency Base frequency multiplier
     * @param amplitude Overall amplitude multiplier
     * @return Noise value normalized to approximately [-1, 1]
     */
    public double sample(double x, double y, double z, double frequency, double amplitude) {
        double result = 0.0;
        double freq = frequency;
        
        for (int i = 0; i < octaves.length; i++) {
            result += octaves[i].noise(x * freq, y * freq, z * freq) * amplitudes[i];
            freq *= 2.0;
        }
        
        // Normalize and scale
        return (result / maxAmplitude) * amplitude;
    }
}
