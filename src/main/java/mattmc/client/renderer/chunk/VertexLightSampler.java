package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Handles vertex light sampling for mesh building.
 * Samples light from adjacent blocks to create smooth lighting gradients.
 * 
 * <p>Now includes Minecraft-style ambient occlusion (AO) calculation.
 * The AO value is stored in the vertex data and used by the shader to
 * darken corners and edges where geometry creates shadows.
 * 
 * Extracted from MeshBuilder as part of refactoring to single-purpose classes.
 */
public class VertexLightSampler {
    
    /**
     * Pre-computed sample offsets for each face/corner combination.
     * Format: SAMPLE_OFFSETS[normalIndex][cornerIndex] = int[12] {dx0,dy0,dz0, dx1,dy1,dz1, dx2,dy2,dz2, dx3,dy3,dz3}
     * 
     * ISSUE-001 fix: Static cache eliminates ~40K int[] allocations per chunk mesh build.
     */
    private static final int[][][] SAMPLE_OFFSETS = new int[6][4][];
    
    static {
        // Top face (normal = 0,1,0)
        SAMPLE_OFFSETS[0][0] = new int[] {0,1,0, -1,1,0, 0,1,-1, -1,1,-1}; // x0, z0
        SAMPLE_OFFSETS[0][1] = new int[] {0,1,0, -1,1,0, 0,1,1, -1,1,1};   // x0, z1
        SAMPLE_OFFSETS[0][2] = new int[] {0,1,0, 1,1,0, 0,1,1, 1,1,1};     // x1, z1
        SAMPLE_OFFSETS[0][3] = new int[] {0,1,0, 1,1,0, 0,1,-1, 1,1,-1};   // x1, z0
        
        // Bottom face (normal = 0,-1,0)
        SAMPLE_OFFSETS[1][0] = new int[] {0,-1,0, -1,-1,0, 0,-1,-1, -1,-1,-1}; // x0, z0
        SAMPLE_OFFSETS[1][1] = new int[] {0,-1,0, 1,-1,0, 0,-1,-1, 1,-1,-1};   // x1, z0
        SAMPLE_OFFSETS[1][2] = new int[] {0,-1,0, 1,-1,0, 0,-1,1, 1,-1,1};     // x1, z1
        SAMPLE_OFFSETS[1][3] = new int[] {0,-1,0, -1,-1,0, 0,-1,1, -1,-1,1};   // x0, z1
        
        // North face (normal = 0,0,-1)
        SAMPLE_OFFSETS[2][0] = new int[] {0,0,-1, 1,0,-1, 0,-1,-1, 1,-1,-1};   // x1, y0
        SAMPLE_OFFSETS[2][1] = new int[] {0,0,-1, -1,0,-1, 0,-1,-1, -1,-1,-1}; // x0, y0
        SAMPLE_OFFSETS[2][2] = new int[] {0,0,-1, -1,0,-1, 0,1,-1, -1,1,-1};   // x0, y1
        SAMPLE_OFFSETS[2][3] = new int[] {0,0,-1, 1,0,-1, 0,1,-1, 1,1,-1};     // x1, y1
        
        // South face (normal = 0,0,1)
        SAMPLE_OFFSETS[3][0] = new int[] {0,0,1, -1,0,1, 0,-1,1, -1,-1,1}; // x0, y0
        SAMPLE_OFFSETS[3][1] = new int[] {0,0,1, 1,0,1, 0,-1,1, 1,-1,1};   // x1, y0
        SAMPLE_OFFSETS[3][2] = new int[] {0,0,1, 1,0,1, 0,1,1, 1,1,1};     // x1, y1
        SAMPLE_OFFSETS[3][3] = new int[] {0,0,1, -1,0,1, 0,1,1, -1,1,1};   // x0, y1
        
        // West face (normal = -1,0,0)
        SAMPLE_OFFSETS[4][0] = new int[] {-1,0,0, -1,0,-1, -1,-1,0, -1,-1,-1}; // z0, y0
        SAMPLE_OFFSETS[4][1] = new int[] {-1,0,0, -1,0,1, -1,-1,0, -1,-1,1};   // z1, y0
        SAMPLE_OFFSETS[4][2] = new int[] {-1,0,0, -1,0,1, -1,1,0, -1,1,1};     // z1, y1
        SAMPLE_OFFSETS[4][3] = new int[] {-1,0,0, -1,0,-1, -1,1,0, -1,1,-1};   // z0, y1
        
        // East face (normal = 1,0,0)
        SAMPLE_OFFSETS[5][0] = new int[] {1,0,0, 1,0,1, 1,-1,0, 1,-1,1};   // z1, y0
        SAMPLE_OFFSETS[5][1] = new int[] {1,0,0, 1,0,-1, 1,-1,0, 1,-1,-1}; // z0, y0
        SAMPLE_OFFSETS[5][2] = new int[] {1,0,0, 1,0,-1, 1,1,0, 1,1,-1};   // z0, y1
        SAMPLE_OFFSETS[5][3] = new int[] {1,0,0, 1,0,1, 1,1,0, 1,1,1};     // z1, y1
    }
    
    /**
     * Default light result for when chunk is null.
     * ISSUE-001 fix: Pre-allocated to avoid allocation on null chunk path.
     */
    private static final float[] DEFAULT_LIGHT_RESULT = new float[] {15.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    
    /**
     * Interface for sampling light values across chunk boundaries.
     */
    public interface ChunkLightAccessor {
        /**
         * Get skylight level at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return Skylight level (0-15)
         */
        int getSkyLightAcrossChunks(LevelChunk chunk, int x, int y, int z);
        
        /**
         * Get blocklight level at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return Blocklight level (0-15)
         */
        int getBlockLightAcrossChunks(LevelChunk chunk, int x, int y, int z);
        
        /**
         * Get blocklight RGB values at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return Blocklight RGB as array [R, G, B] (0-15 each)
         */
        default int[] getBlockLightRGBAcrossChunks(LevelChunk chunk, int x, int y, int z) {
            // Default implementation for backward compatibility - returns white light
            int intensity = getBlockLightAcrossChunks(chunk, x, y, z);
            return new int[] {intensity, intensity, intensity};
        }
        
        /**
         * Get block at chunk-local coordinates, checking neighboring chunks if necessary.
         * Used for ambient occlusion calculation.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return Block at the position
         */
        default Block getBlockAcrossChunks(LevelChunk chunk, int x, int y, int z) {
            return mattmc.world.level.block.Blocks.AIR;
        }
    }
    
    private ChunkLightAccessor lightAccessor;
    private final AmbientOcclusion ambientOcclusion;
    
    /**
     * Thread-local reusable result array for sampleVertexLight().
     * ISSUE-001 fix: Eliminates ~40K float[] allocations per chunk mesh build.
     * Thread-local to ensure thread-safety during parallel mesh building.
     */
    private static final ThreadLocal<float[]> LIGHT_RESULT = ThreadLocal.withInitial(() -> new float[5]);
    
    /**
     * Thread-local reusable array for RGB values.
     * ISSUE-001/002 fix: Eliminates int[] allocations in getBlockLightRGBSafe().
     */
    private static final ThreadLocal<int[]> RGB_RESULT = ThreadLocal.withInitial(() -> new int[3]);
    
    /**
     * Create a new VertexLightSampler with ambient occlusion support.
     */
    public VertexLightSampler() {
        this.ambientOcclusion = new AmbientOcclusion();
    }
    
    /**
     * Set the light accessor for cross-chunk light sampling.
     * Also configures the ambient occlusion calculator with block access.
     */
    public void setLightAccessor(ChunkLightAccessor accessor) {
        this.lightAccessor = accessor;
        
        // Configure AO calculator with block accessor from light accessor
        if (accessor != null) {
            this.ambientOcclusion.setBlockAccessor((chunk, x, y, z) -> {
                return accessor.getBlockAcrossChunks(chunk, x, y, z);
            });
        }
    }
    
    /**
     * Sample light for a vertex using smart smooth lighting.
     * 
     * For each vertex, we sample light from the 3 adjacent faces + 1 diagonal corner.
     * This creates smooth lighting gradients across block edges without banding.
     * 
     * Only non-zero light samples are averaged to prevent interior corners from being too dark.
     * This fixes the issue where solid blocks (with 0 light) would darken adjacent corners.
     * 
     * <p><b>IMPORTANT:</b> This method returns a thread-local reusable array for performance.
     * The returned array is only valid until the next call to this method on the same thread.
     * Callers MUST consume the values immediately (e.g., pass to addVertex) and NOT store
     * the array reference for later use.
     * 
     * <p>ISSUE-001 fix: Uses pre-allocated arrays to eliminate ~40K allocations per chunk.
     * 
     * @param face The face data containing chunk reference and position
     * @param normalIndex Which face (0=top, 1=bottom, 2=north, 3=south, 4=west, 5=east)
     * @param cornerIndex Which corner of the face (0-3)
     * @return [skyLight, blockLightR, blockLightG, blockLightB, ao] as floats (0-15 for light, 0-3 for ao).
     *         WARNING: This array is reused - consume values immediately!
     */
    public float[] sampleVertexLight(BlockFaceCollector.FaceData face, 
                                      int normalIndex,
                                      int cornerIndex) {
        // If no chunk reference, return default lighting (pre-allocated static array)
        if (face.chunk == null) {
            return DEFAULT_LIGHT_RESULT;
        }
        
        // Get chunk-local coordinates of the block
        int cx = face.cx;
        int cy = face.cy;
        int cz = face.cz;
        
        // ISSUE-001 fix: Use pre-computed static offsets instead of allocating new array
        int[] offsets = SAMPLE_OFFSETS[normalIndex][cornerIndex];
        
        float skyLightSum = 0;
        float blockLightRSum = 0;
        float blockLightGSum = 0;
        float blockLightBSum = 0;
        int skyLightSamples = 0;
        int blockLightSamples = 0;
        
        // Sample 4 positions (3 adjacent + 1 diagonal)
        for (int i = 0; i < 4; i++) {
            int dx = offsets[i * 3];
            int dy = offsets[i * 3 + 1];
            int dz = offsets[i * 3 + 2];
            
            int sx = cx + dx;
            int sy = cy + dy;
            int sz = cz + dz;
            
            // Check if sample position is inside a solid block
            // If so, we should sample from an alternative position above/around it
            Block sampleBlock = getBlockSafe(face.chunk, sx, sy, sz);
            if (sampleBlock != null && sampleBlock.isSolid()) {
                // Sample from above this solid block instead
                // This prevents interior edges from being too dark when adjacent
                // solid blocks block the direct sampling path
                // Search upward up to 3 blocks to find air
                for (int searchY = sy + 1; searchY <= sy + 3 && searchY < LevelChunk.HEIGHT; searchY++) {
                    Block altBlock = getBlockSafe(face.chunk, sx, searchY, sz);
                    if (altBlock == null || !altBlock.isSolid()) {
                        // Found a non-solid position above
                        sy = searchY;
                        break;
                    }
                }
            }
            
            // Sample light at this position (possibly adjusted)
            int skyLight = getSkyLightSafe(face.chunk, sx, sy, sz);
            
            // ISSUE-001/002 fix: Use reusable array via getBlockLightRGBSafe
            int[] blockLightRGB = getBlockLightRGBSafe(face.chunk, sx, sy, sz);
            
            // Only include non-zero skylight samples in the average
            // This prevents solid blocks (with 0 skylight) from darkening interior corners
            if (skyLight > 0) {
                skyLightSum += skyLight;
                skyLightSamples++;
            }
            
            // Only include non-zero blocklight samples in the average
            // Check if any RGB component is non-zero
            if (blockLightRGB[0] > 0 || blockLightRGB[1] > 0 || blockLightRGB[2] > 0) {
                blockLightRSum += blockLightRGB[0];
                blockLightGSum += blockLightRGB[1];
                blockLightBSum += blockLightRGB[2];
                blockLightSamples++;
            }
        }
        
        // Average only the non-zero samples to prevent interior corners from being too dark
        // If all samples are zero, use zero (fully dark, but shader has minimum brightness)
        float avgSkyLight = skyLightSamples > 0 ? skyLightSum / skyLightSamples : 0.0f;
        float avgBlockLightR = blockLightSamples > 0 ? blockLightRSum / blockLightSamples : 0.0f;
        float avgBlockLightG = blockLightSamples > 0 ? blockLightGSum / blockLightSamples : 0.0f;
        float avgBlockLightB = blockLightSamples > 0 ? blockLightBSum / blockLightSamples : 0.0f;
        
        // Calculate ambient occlusion using Minecraft's algorithm
        float ao = ambientOcclusion.calculateVertexAO(face, normalIndex, cornerIndex);
        
        // ISSUE-001 fix: Use thread-local reusable array instead of allocating new array
        float[] result = LIGHT_RESULT.get();
        result[0] = avgSkyLight;
        result[1] = avgBlockLightR;
        result[2] = avgBlockLightG;
        result[3] = avgBlockLightB;
        result[4] = ao;
        return result;
    }
    
    /**
     * Get skylight value safely, returning 15 if out of bounds.
     */
    private int getSkyLightSafe(LevelChunk chunk, int x, int y, int z) {
        // Check bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return 15; // Out of bounds: full skylight
        }
        
        // If we have a light accessor and coordinates are out of chunk bounds, use cross-chunk sampling
        if (lightAccessor != null && 
            (x < 0 || x >= LevelChunk.WIDTH ||
             z < 0 || z >= LevelChunk.DEPTH)) {
            return lightAccessor.getSkyLightAcrossChunks(chunk, x, y, z);
        }
        
        // Within chunk bounds - use direct access
        if (x < 0 || x >= LevelChunk.WIDTH ||
            z < 0 || z >= LevelChunk.DEPTH) {
            return 15; // Out of chunk bounds without accessor: full skylight
        }
        
        return chunk.getSkyLight(x, y, z);
    }
    
    /**
     * Get blocklight value safely, returning 0 if out of bounds.
     */
    private int getBlockLightSafe(LevelChunk chunk, int x, int y, int z) {
        // Check bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return 0; // Out of bounds: no blocklight
        }
        
        // If we have a light accessor and coordinates are out of chunk bounds, use cross-chunk sampling
        if (lightAccessor != null && 
            (x < 0 || x >= LevelChunk.WIDTH ||
             z < 0 || z >= LevelChunk.DEPTH)) {
            return lightAccessor.getBlockLightAcrossChunks(chunk, x, y, z);
        }
        
        // Within chunk bounds - use direct access
        if (x < 0 || x >= LevelChunk.WIDTH ||
            z < 0 || z >= LevelChunk.DEPTH) {
            return 0; // Out of chunk bounds without accessor: no blocklight
        }
        
        return chunk.getBlockLightI(x, y, z);
    }
    
    /**
     * Get blocklight RGB values safely, returning [0,0,0] if out of bounds.
     * Scales RGB values by intensity to properly attenuate light with distance.
     * 
     * ISSUE-001/002 fix: Uses thread-local reusable array to eliminate allocations.
     * 
     * @return Array of [R, G, B] values (0-15 each), scaled by intensity
     */
    private int[] getBlockLightRGBSafe(LevelChunk chunk, int x, int y, int z) {
        // Get thread-local reusable result array
        int[] result = RGB_RESULT.get();
        
        // Check bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            result[0] = 0;
            result[1] = 0;
            result[2] = 0;
            return result; // Out of bounds: no blocklight
        }
        
        // If we have a light accessor and coordinates are out of chunk bounds, use cross-chunk sampling
        if (lightAccessor != null && 
            (x < 0 || x >= LevelChunk.WIDTH ||
             z < 0 || z >= LevelChunk.DEPTH)) {
            // Cross-chunk accessor may return new array; copy to our reusable array
            int[] crossChunkRGB = lightAccessor.getBlockLightRGBAcrossChunks(chunk, x, y, z);
            result[0] = crossChunkRGB[0];
            result[1] = crossChunkRGB[1];
            result[2] = crossChunkRGB[2];
            return result;
        }
        
        // Within chunk bounds - use direct access
        if (x < 0 || x >= LevelChunk.WIDTH ||
            z < 0 || z >= LevelChunk.DEPTH) {
            result[0] = 0;
            result[1] = 0;
            result[2] = 0;
            return result; // Out of chunk bounds without accessor: no blocklight
        }
        
        // Get RGB and intensity values
        int r = chunk.getBlockLightR(x, y, z);
        int g = chunk.getBlockLightG(x, y, z);
        int b = chunk.getBlockLightB(x, y, z);
        int intensity = chunk.getBlockLightI(x, y, z);
        
        // If no light, return early
        if (intensity == 0) {
            result[0] = 0;
            result[1] = 0;
            result[2] = 0;
            return result;
        }
        
        // Scale RGB by intensity ratio to properly attenuate light
        // RGB values stay constant during propagation, but intensity decrements
        // We need to scale RGB to match the current intensity
        int maxRGB = Math.max(r, Math.max(g, b));
        
        // If maxRGB is 0 but intensity is not, something is wrong - return intensity as white light
        if (maxRGB == 0) {
            result[0] = intensity;
            result[1] = intensity;
            result[2] = intensity;
            return result;
        }
        
        // Scale RGB by the intensity ratio
        float scale = (float) intensity / maxRGB;
        result[0] = Math.round(r * scale);
        result[1] = Math.round(g * scale);
        result[2] = Math.round(b * scale);
        
        return result;
    }
    
    /**
     * Get block at a position safely, returning null if out of bounds.
     * Uses the light accessor's getBlockAcrossChunks if available.
     */
    private Block getBlockSafe(LevelChunk chunk, int x, int y, int z) {
        // Check Y bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return null;
        }
        
        // Check if coordinates are out of chunk bounds
        boolean outOfChunkBounds = x < 0 || x >= LevelChunk.WIDTH ||
                                   z < 0 || z >= LevelChunk.DEPTH;
        
        if (outOfChunkBounds) {
            // Use cross-chunk access if available, otherwise return null
            if (lightAccessor != null) {
                return lightAccessor.getBlockAcrossChunks(chunk, x, y, z);
            }
            return null;
        }
        
        return chunk.getBlock(x, y, z);
    }
}
