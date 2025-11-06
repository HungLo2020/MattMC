package mattmc.world.level.levelgen;

/**
 * Simple manual test to visualize noise-based terrain generation.
 * Run this to see an ASCII representation of generated terrain.
 */
public class TerrainVisualization {
    
    public static void main(String[] args) {
        long seed = 12345L;
        WorldGenerator generator = new WorldGenerator(seed);
        
        System.out.println("Terrain Visualization (Seed: " + seed + ")");
        System.out.println("Legend: . = ocean, - = low land, = = plains, ^ = hills, M = mountains");
        System.out.println();
        
        // Generate a 50x50 area
        int size = 50;
        int centerX = 0;
        int centerZ = 0;
        
        for (int z = centerZ - size/2; z < centerZ + size/2; z++) {
            for (int x = centerX - size/2; x < centerX + size/2; x++) {
                int height = generator.getTerrainHeight(x, z);
                char symbol = getSymbolForHeight(height);
                System.out.print(symbol);
            }
            System.out.println();
        }
        
        System.out.println();
        System.out.println("Statistics:");
        
        // Collect statistics
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        int oceanCount = 0;
        int landCount = 0;
        
        for (int z = centerZ - 100; z < centerZ + 100; z++) {
            for (int x = centerX - 100; x < centerX + 100; x++) {
                int height = generator.getTerrainHeight(x, z);
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
                
                if (height < 63) {
                    oceanCount++;
                } else {
                    landCount++;
                }
            }
        }
        
        System.out.println("Height range: " + minHeight + " to " + maxHeight);
        System.out.println("Height variation: " + (maxHeight - minHeight) + " blocks");
        System.out.println("Ocean blocks: " + oceanCount + " (" + (oceanCount * 100 / (oceanCount + landCount)) + "%)");
        System.out.println("Land blocks: " + landCount + " (" + (landCount * 100 / (oceanCount + landCount)) + "%)");
    }
    
    private static char getSymbolForHeight(int height) {
        if (height < 55) {
            return '.';  // Deep ocean
        } else if (height < 63) {
            return '~';  // Shallow ocean
        } else if (height < 70) {
            return '-';  // Low land
        } else if (height < 80) {
            return '=';  // Plains
        } else if (height < 90) {
            return '^';  // Hills
        } else {
            return 'M';  // Mountains
        }
    }
}
