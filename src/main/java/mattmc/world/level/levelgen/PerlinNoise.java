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
    
    // Permutation table - a random shuffling of 0-255
    private static final int[] REFERENCE = {
        151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,
        8,99,37,240,21,10,23,190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,
        35,11,32,57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,74,165,71,
        134,139,48,27,166,77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,
        55,46,245,40,244,102,143,54,65,25,63,161,1,216,80,73,209,76,132,187,208,89,
        18,169,200,196,135,130,116,188,159,86,164,100,109,198,173,186,3,64,52,217,226,
        250,124,123,5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,
        189,28,42,223,183,170,213,119,248,152,2,44,154,163,70,221,153,101,155,167,43,
        172,9,129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,218,246,97,
        228,251,34,242,193,238,210,144,12,191,179,162,241,81,51,145,235,249,14,239,
        107,49,192,214,31,181,199,106,157,184,84,204,176,115,121,50,45,127,4,150,254,
        138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
    };
    
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
