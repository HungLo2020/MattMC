package mattmc.world.level.levelgen;

/**
 * Improved Perlin noise implementation.
 * Based on Ken Perlin's improved noise algorithm (2002).
 * 
 * This generates smooth, continuous noise values that are commonly
 * used in procedural terrain generation.
 */
public class PerlinNoise {
    private final int[] permutation;
    
    /**
     * Create a new Perlin noise generator with the given seed.
     */
    public PerlinNoise(long seed) {
        permutation = new int[512];
        
        // Initialize permutation array with seed-based shuffling
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        
        // Fisher-Yates shuffle with seed
        java.util.Random random = new java.util.Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }
        
        // Duplicate the permutation array to avoid overflow
        for (int i = 0; i < 512; i++) {
            permutation[i] = p[i & 255];
        }
    }
    
    /**
     * Generate 2D Perlin noise value at the given coordinates.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @return Noise value between -1.0 and 1.0
     */
    public double noise(double x, double y) {
        // Find unit grid cell containing point
        int X = (int)Math.floor(x) & 255;
        int Y = (int)Math.floor(y) & 255;
        
        // Get relative xy coordinates of point within that cell
        x -= Math.floor(x);
        y -= Math.floor(y);
        
        // Compute fade curves for each of x, y
        double u = fade(x);
        double v = fade(y);
        
        // Hash coordinates of the 4 square corners
        int A = permutation[X] + Y;
        int AA = permutation[A];
        int AB = permutation[A + 1];
        int B = permutation[X + 1] + Y;
        int BA = permutation[B];
        int BB = permutation[B + 1];
        
        // Add blended results from 4 corners of square
        double res = lerp(v,
            lerp(u, grad(permutation[AA], x, y), grad(permutation[BA], x - 1, y)),
            lerp(u, grad(permutation[AB], x, y - 1), grad(permutation[BB], x - 1, y - 1))
        );
        
        return res;
    }
    
    /**
     * Generate 3D Perlin noise value at the given coordinates.
     * 
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Noise value between -1.0 and 1.0
     */
    public double noise(double x, double y, double z) {
        // Find unit cube that contains point
        int X = (int)Math.floor(x) & 255;
        int Y = (int)Math.floor(y) & 255;
        int Z = (int)Math.floor(z) & 255;
        
        // Find relative x,y,z of point in cube
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        
        // Compute fade curves for each of x,y,z
        double u = fade(x);
        double v = fade(y);
        double w = fade(z);
        
        // Hash coordinates of the 8 cube corners
        int A = permutation[X] + Y;
        int AA = permutation[A] + Z;
        int AB = permutation[A + 1] + Z;
        int B = permutation[X + 1] + Y;
        int BA = permutation[B] + Z;
        int BB = permutation[B + 1] + Z;
        
        // Add blended results from 8 corners of cube
        return lerp(w,
            lerp(v,
                lerp(u, grad(permutation[AA], x, y, z), grad(permutation[BA], x - 1, y, z)),
                lerp(u, grad(permutation[AB], x, y - 1, z), grad(permutation[BB], x - 1, y - 1, z))
            ),
            lerp(v,
                lerp(u, grad(permutation[AA + 1], x, y, z - 1), grad(permutation[BA + 1], x - 1, y, z - 1)),
                lerp(u, grad(permutation[AB + 1], x, y - 1, z - 1), grad(permutation[BB + 1], x - 1, y - 1, z - 1))
            )
        );
    }
    
    /**
     * Fade function as defined by Ken Perlin. This eases coordinate values
     * so that they will ease towards integral values.
     */
    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    
    /**
     * Linear interpolation between a and b.
     */
    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
    
    /**
     * 2D gradient function.
     */
    private double grad(int hash, double x, double y) {
        int h = hash & 3;
        double u = h < 2 ? x : y;
        double v = h < 2 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
    
    /**
     * 3D gradient function.
     */
    private double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
