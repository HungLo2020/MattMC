package mattmc.client.renderer.chunk;

import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Samples light values for mesh vertices using smooth lighting.
 * 
 * For each vertex, samples 8 nearby voxels and averages their light values.
 * Also calculates ambient occlusion using the 3-block corner rule.
 * 
 * Smooth lighting interpolates light values between adjacent blocks to create
 * a smoother, more realistic lighting appearance similar to Minecraft's smooth lighting.
 */
public class VertexLightSampler {
    
    /**
     * Interface for accessing blocks across chunk boundaries.
     */
    public interface BlockAccessor {
        /**
         * Get a block at chunk-local coordinates, checking neighboring chunks if necessary.
         */
        Block getBlockAcrossChunks(LevelChunk chunk, int x, int y, int z);
        
        /**
         * Get sky light level at chunk-local coordinates.
         */
        int getSkyLight(LevelChunk chunk, int x, int y, int z);
        
        /**
         * Get block light level at chunk-local coordinates.
         */
        int getBlockLight(LevelChunk chunk, int x, int y, int z);
    }
    
    /**
     * Stores the light data for a vertex.
     */
    public static class VertexLight {
        public final int skyLight;   // 0-15
        public final int blockLight;  // 0-15
        public final int ao;          // 0-3 (number of occluding blocks)
        
        public VertexLight(int skyLight, int blockLight, int ao) {
            this.skyLight = skyLight;
            this.blockLight = blockLight;
            this.ao = ao;
        }
        
        /**
         * Get AO factor for shading (1.0, 0.8, 0.6, 0.45).
         */
        public float getAOFactor() {
            switch (ao) {
                case 0: return 1.0f;
                case 1: return 0.8f;
                case 2: return 0.6f;
                case 3: return 0.45f;
                default: return 1.0f;
            }
        }
    }
    
    /**
     * Face normal directions for sampling.
     */
    public enum Normal {
        UP(0, 1, 0),
        DOWN(0, -1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);
        
        public final int dx, dy, dz;
        
        Normal(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
    
    private final BlockAccessor accessor;
    
    public VertexLightSampler(BlockAccessor accessor) {
        this.accessor = accessor;
    }
    
    /**
     * Sample light values for a vertex at the given block corner.
     * 
     * @param chunk The chunk containing the block
     * @param blockX Block X position (chunk-local)
     * @param blockY Block Y position (chunk-local)
     * @param blockZ Block Z position (chunk-local)
     * @param normal The face normal direction
     * @param cornerIndex Which corner of the face (0-3)
     * @return Sampled light data
     */
    public VertexLight sampleVertex(LevelChunk chunk, int blockX, int blockY, int blockZ,
                                    Normal normal, int cornerIndex) {
        // Get the corner offset based on face and corner index
        int[] offset = getCornerOffset(normal, cornerIndex);
        
        // Sample 8 voxels around the vertex
        int skySum = 0;
        int blockSum = 0;
        int aoCount = 0;
        int samples = 0;
        
        // Define the 8 voxels to sample (2x2x2 cube around the vertex)
        // The exact voxels depend on which corner and face we're sampling
        int[][] sampleOffsets = getSampleOffsets(normal, cornerIndex);
        
        for (int[] sampleOffset : sampleOffsets) {
            int sx = blockX + sampleOffset[0];
            int sy = blockY + sampleOffset[1];
            int sz = blockZ + sampleOffset[2];
            
            skySum += accessor.getSkyLight(chunk, sx, sy, sz);
            blockSum += accessor.getBlockLight(chunk, sx, sy, sz);
            samples++;
        }
        
        // Calculate AO using 3-block corner rule
        aoCount = calculateAO(chunk, blockX, blockY, blockZ, normal, cornerIndex);
        
        // Average the light values
        int avgSky = samples > 0 ? skySum / samples : 15;
        int avgBlock = samples > 0 ? blockSum / samples : 0;
        
        // Clamp to valid range
        avgSky = Math.max(0, Math.min(15, avgSky));
        avgBlock = Math.max(0, Math.min(15, avgBlock));
        
        return new VertexLight(avgSky, avgBlock, aoCount);
    }
    
    /**
     * Calculate ambient occlusion for a vertex using the 3-block corner rule.
     * 
     * Checks 3 blocks: side1, side2, and corner.
     * Returns: 0 if all are air, 1 if one side is solid, 2 if both sides or corner only,
     *          3 if both sides and corner are solid.
     */
    private int calculateAO(LevelChunk chunk, int blockX, int blockY, int blockZ,
                           Normal normal, int cornerIndex) {
        // Get the three blocks to check for this corner
        int[][] aoBlocks = getAOBlocks(normal, cornerIndex);
        
        boolean side1 = false, side2 = false, corner = false;
        
        if (aoBlocks.length >= 1) {
            int[] b1 = aoBlocks[0];
            Block block1 = accessor.getBlockAcrossChunks(chunk, blockX + b1[0], blockY + b1[1], blockZ + b1[2]);
            side1 = block1 != null && block1.isSolid();
        }
        
        if (aoBlocks.length >= 2) {
            int[] b2 = aoBlocks[1];
            Block block2 = accessor.getBlockAcrossChunks(chunk, blockX + b2[0], blockY + b2[1], blockZ + b2[2]);
            side2 = block2 != null && block2.isSolid();
        }
        
        if (aoBlocks.length >= 3) {
            int[] b3 = aoBlocks[2];
            Block block3 = accessor.getBlockAcrossChunks(chunk, blockX + b3[0], blockY + b3[1], blockZ + b3[2]);
            corner = block3 != null && block3.isSolid();
        }
        
        // Apply 3-block corner rule
        if (side1 && side2) {
            return 3; // Both sides solid = maximum occlusion
        }
        
        int count = 0;
        if (side1) count++;
        if (side2) count++;
        if (corner) count++;
        
        return count;
    }
    
    /**
     * Get the corner offset for a given face and corner index.
     */
    private int[] getCornerOffset(Normal normal, int cornerIndex) {
        // Returns [dx, dy, dz] offset for the vertex position
        switch (normal) {
            case UP:
                switch (cornerIndex) {
                    case 0: return new int[]{0, 1, 0};
                    case 1: return new int[]{0, 1, 1};
                    case 2: return new int[]{1, 1, 1};
                    case 3: return new int[]{1, 1, 0};
                }
                break;
            case DOWN:
                switch (cornerIndex) {
                    case 0: return new int[]{0, 0, 0};
                    case 1: return new int[]{1, 0, 0};
                    case 2: return new int[]{1, 0, 1};
                    case 3: return new int[]{0, 0, 1};
                }
                break;
            case NORTH:
                switch (cornerIndex) {
                    case 0: return new int[]{1, 0, 0};
                    case 1: return new int[]{0, 0, 0};
                    case 2: return new int[]{0, 1, 0};
                    case 3: return new int[]{1, 1, 0};
                }
                break;
            case SOUTH:
                switch (cornerIndex) {
                    case 0: return new int[]{0, 0, 1};
                    case 1: return new int[]{1, 0, 1};
                    case 2: return new int[]{1, 1, 1};
                    case 3: return new int[]{0, 1, 1};
                }
                break;
            case WEST:
                switch (cornerIndex) {
                    case 0: return new int[]{0, 0, 0};
                    case 1: return new int[]{0, 0, 1};
                    case 2: return new int[]{0, 1, 1};
                    case 3: return new int[]{0, 1, 0};
                }
                break;
            case EAST:
                switch (cornerIndex) {
                    case 0: return new int[]{1, 0, 1};
                    case 1: return new int[]{1, 0, 0};
                    case 2: return new int[]{1, 1, 0};
                    case 3: return new int[]{1, 1, 1};
                }
                break;
        }
        return new int[]{0, 0, 0};
    }
    
    /**
     * Get the 8 sample offsets for light interpolation around a vertex.
     * 
     * For proper smooth lighting, we need to sample all 8 blocks that touch
     * the vertex. The vertex is at the corner of the current block, so we need
     * to sample in all 8 octants around that corner point.
     */
    private int[][] getSampleOffsets(Normal normal, int cornerIndex) {
        // Get the corner position relative to the block
        int[] corner = getCornerOffset(normal, cornerIndex);
        
        // Determine which blocks touch this vertex
        // A vertex at position (cx, cy, cz) is touched by blocks in a 2x2x2 cube
        // The cube ranges from (cx-1 to cx) in X, (cy-1 to cy) in Y, (cz-1 to cz) in Z
        // But we need to shift based on which corner we're at
        
        // For each axis, if corner offset is 0, we sample current and -1
        // If corner offset is 1, we sample current and +1
        int x0 = corner[0] == 0 ? -1 : 0;
        int x1 = corner[0] == 0 ? 0 : 1;
        int y0 = corner[1] == 0 ? -1 : 0;
        int y1 = corner[1] == 0 ? 0 : 1;
        int z0 = corner[2] == 0 ? -1 : 0;
        int z1 = corner[2] == 0 ? 0 : 1;
        
        // Return all 8 combinations
        return new int[][]{
            {x0, y0, z0},
            {x0, y0, z1},
            {x0, y1, z0},
            {x0, y1, z1},
            {x1, y0, z0},
            {x1, y0, z1},
            {x1, y1, z0},
            {x1, y1, z1}
        };
    }
    
    /**
     * Get the 3 blocks to check for AO (side1, side2, corner).
     */
    private int[][] getAOBlocks(Normal normal, int cornerIndex) {
        // Returns offsets for [side1, side2, corner] blocks to check
        // Based on Minecraft's AO calculation
        
        switch (normal) {
            case UP:
                switch (cornerIndex) {
                    case 0: return new int[][]{{0, 1, -1}, {-1, 1, 0}, {-1, 1, -1}};  // NW corner
                    case 1: return new int[][]{{0, 1, 1}, {-1, 1, 0}, {-1, 1, 1}};   // SW corner
                    case 2: return new int[][]{{0, 1, 1}, {1, 1, 0}, {1, 1, 1}};     // SE corner
                    case 3: return new int[][]{{0, 1, -1}, {1, 1, 0}, {1, 1, -1}};   // NE corner
                }
                break;
            case DOWN:
                switch (cornerIndex) {
                    case 0: return new int[][]{{0, -1, -1}, {-1, -1, 0}, {-1, -1, -1}};
                    case 1: return new int[][]{{0, -1, -1}, {1, -1, 0}, {1, -1, -1}};
                    case 2: return new int[][]{{0, -1, 1}, {1, -1, 0}, {1, -1, 1}};
                    case 3: return new int[][]{{0, -1, 1}, {-1, -1, 0}, {-1, -1, 1}};
                }
                break;
            case NORTH:
                switch (cornerIndex) {
                    case 0: return new int[][]{{1, 0, -1}, {0, -1, -1}, {1, -1, -1}};
                    case 1: return new int[][]{{-1, 0, -1}, {0, -1, -1}, {-1, -1, -1}};
                    case 2: return new int[][]{{-1, 0, -1}, {0, 1, -1}, {-1, 1, -1}};
                    case 3: return new int[][]{{1, 0, -1}, {0, 1, -1}, {1, 1, -1}};
                }
                break;
            case SOUTH:
                switch (cornerIndex) {
                    case 0: return new int[][]{{-1, 0, 1}, {0, -1, 1}, {-1, -1, 1}};
                    case 1: return new int[][]{{1, 0, 1}, {0, -1, 1}, {1, -1, 1}};
                    case 2: return new int[][]{{1, 0, 1}, {0, 1, 1}, {1, 1, 1}};
                    case 3: return new int[][]{{-1, 0, 1}, {0, 1, 1}, {-1, 1, 1}};
                }
                break;
            case WEST:
                switch (cornerIndex) {
                    case 0: return new int[][]{{-1, 0, -1}, {-1, -1, 0}, {-1, -1, -1}};
                    case 1: return new int[][]{{-1, 0, 1}, {-1, -1, 0}, {-1, -1, 1}};
                    case 2: return new int[][]{{-1, 0, 1}, {-1, 1, 0}, {-1, 1, 1}};
                    case 3: return new int[][]{{-1, 0, -1}, {-1, 1, 0}, {-1, 1, -1}};
                }
                break;
            case EAST:
                switch (cornerIndex) {
                    case 0: return new int[][]{{1, 0, 1}, {1, -1, 0}, {1, -1, 1}};
                    case 1: return new int[][]{{1, 0, -1}, {1, -1, 0}, {1, -1, -1}};
                    case 2: return new int[][]{{1, 0, -1}, {1, 1, 0}, {1, 1, -1}};
                    case 3: return new int[][]{{1, 0, 1}, {1, 1, 0}, {1, 1, 1}};
                }
                break;
        }
        
        return new int[][]{};
    }
}
