package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Handles vertex light sampling for mesh building.
 * Uses Minecraft's full 3x3x3 trilinear interpolation for smooth lighting.
 * 
 * <p>This implementation matches Minecraft's SmoothQuadLighter algorithm:
 * <ol>
 *   <li>Sample 27 positions (3x3x3 grid) around the block for transparency, skylight, 
 *       blocklight, and ambient occlusion values</li>
 *   <li>Check face occlusion for each of the 6 faces</li>
 *   <li>Precompute per-corner light values for 8 corners per axis using the combine function</li>
 *   <li>Calculate final light values via trilinear interpolation based on vertex position</li>
 * </ol>
 * 
 * <p>Smooth lighting is always enabled for best visual quality.</p>
 * 
 * <p>This implementation extends Minecraft's algorithm to support RGB blocklight (MattMC feature).</p>
 * 
 * Extracted from MeshBuilder as part of refactoring to single-purpose classes.
 */
public class VertexLightSampler {
    
    /** Small epsilon for numerical stability in position clamping */
    private static final float EPSILON = 1e-4f;
    
    /** Maximum squared distance for position interpolation (prevents sampling outside grid) */
    private static final float MAX_INTERPOLATION_DISTANCE_SQ = 6 - 2e-2f;
    
    /** Maximum sum of two axes for edge clamping */
    private static final float MAX_TWO_AXIS_SUM = 3f - EPSILON;
    
    /** Maximum sum of all three axes for corner clamping */
    private static final float MAX_THREE_AXIS_SUM = 4 - EPSILON;
    
    /** Slightly more than 1 for clamping comparisons */
    private static final float ONE_PLUS_EPSILON = 1 + EPSILON;
    
    /** Slightly less than 2 for axis clamping */
    private static final float TWO_MINUS_EPSILON = 2 - EPSILON;
    
    /** AO brightness value for solid blocks that cause ambient occlusion */
    private static final float SOLID_BLOCK_AO_BRIGHTNESS = 0.2f;
    
    /** Full opacity value for block opacity checks */
    private static final int FULL_OPACITY = 15;
    
    /** 
     * Direction step values for the 6 faces.
     * Index mapping: 0=UP, 1=DOWN, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST
     * Each entry is {stepX, stepY, stepZ} for the face normal direction.
     * Used in face occlusion checks and flat lighting mode.
     */
    private static final int[][] DIRECTION_STEPS = {
        {0, 1, 0},   // 0: UP (Y+)
        {0, -1, 0},  // 1: DOWN (Y-)
        {0, 0, -1},  // 2: NORTH (Z-)
        {0, 0, 1},   // 3: SOUTH (Z+)
        {-1, 0, 0},  // 4: WEST (X-)
        {1, 0, 0}    // 5: EAST (X+)
    };
    
    // Per-block 3x3x3 sample grid arrays
    private final boolean[][][] t = new boolean[3][3][3];  // transparency (getOpacity < FULL_OPACITY)
    private final int[][][] s = new int[3][3][3];          // skylight values
    private final int[][][] bR = new int[3][3][3];         // blocklight red values
    private final int[][][] bG = new int[3][3][3];         // blocklight green values
    private final int[][][] bB = new int[3][3][3];         // blocklight blue values
    private final float[][][] ao = new float[3][3][3];     // ambient occlusion/shade brightness
    
    // Per-corner precomputed light values [axis][x][y][z] where x,y,z are 0 or 1
    private final float[][][][] skyLight = new float[3][2][2][2];
    private final float[][][][] blockLightR = new float[3][2][2][2];
    private final float[][][][] blockLightG = new float[3][2][2][2];
    private final float[][][][] blockLightB = new float[3][2][2][2];
    
    // Cached block position for the current block
    private LevelChunk cachedChunk;
    private int cachedCx, cachedCy, cachedCz;
    private boolean lightingComputed = false;
    
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
         * 
         * <p>This method is used for transparency checks in the 3x3x3 sampling grid.
         * The default implementation returns {@link Blocks#AIR}, which treats all
         * out-of-chunk positions as transparent. This is appropriate for:
         * <ul>
         *   <li>Single-chunk rendering scenarios</li>
         *   <li>Blocks at chunk edges where neighboring chunks are not yet loaded</li>
         * </ul>
         * 
         * <p>For accurate cross-chunk lighting, implementors should override this method
         * to return the actual block at the specified position by querying neighboring chunks.
         * 
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return The block at the specified position, or AIR if unknown
         */
        default Block getBlockAcrossChunks(LevelChunk chunk, int x, int y, int z) {
            return Blocks.AIR;  // Default: treat as air (transparent)
        }
    }
    
    private ChunkLightAccessor lightAccessor;
    
    /**
     * Set the light accessor for cross-chunk light sampling.
     */
    public void setLightAccessor(ChunkLightAccessor accessor) {
        this.lightAccessor = accessor;
    }
    
    /**
     * Sample light for a vertex using Minecraft's 3x3x3 trilinear interpolation.
     * 
     * <p>This method implements the full Minecraft smooth lighting algorithm:
     * <ol>
     *   <li>Sample 27 positions (3x3x3) around the block</li>
     *   <li>Precompute corner light values using the combine function</li>
     *   <li>Use trilinear interpolation based on vertex position</li>
     * </ol>
     * 
     * <p>Smooth lighting is always enabled for best visual quality.</p>
     * 
     * @param face The face data containing chunk reference and position
     * @param normalIndex Which face (0=top, 1=bottom, 2=north, 3=south, 4=west, 5=east)
     * @param cornerIndex Which corner of the face (0-3)
     * @return [skyLight, blockLightR, blockLightG, blockLightB, ao] as floats (0-15 for light, 0-1 for ao)
     */
    public float[] sampleVertexLight(BlockFaceCollector.FaceData face, 
                                      int normalIndex,
                                      int cornerIndex) {
        // If no chunk reference, return default lighting
        if (face.chunk == null) {
            return new float[] {15.0f, 0.0f, 0.0f, 0.0f, 1.0f}; // Full skylight, no blocklight RGB, full AO brightness
        }
        
        // Get chunk-local coordinates of the block
        int cx = face.cx;
        int cy = face.cy;
        int cz = face.cz;
        
        // Smooth lighting is always enabled
        // Check if we need to recompute lighting for this block
        if (!lightingComputed || cachedChunk != face.chunk || 
            cachedCx != cx || cachedCy != cy || cachedCz != cz) {
            computeLightingAt(face.chunk, cx, cy, cz);
            cachedChunk = face.chunk;
            cachedCx = cx;
            cachedCy = cy;
            cachedCz = cz;
            lightingComputed = true;
        }
        
        // Get vertex position relative to block center (range -0.5 to 0.5)
        float[] vertexPos = getVertexPosition(normalIndex, cornerIndex);
        
        // Apply normal offset to push sample position toward the face
        // This matches Minecraft's behavior: position + (normal * 0.5)
        // This ensures the vertex samples light from the correct side of the block
        int[] normal = DIRECTION_STEPS[normalIndex];
        vertexPos[0] += normal[0] * 0.5f;
        vertexPos[1] += normal[1] * 0.5f;
        vertexPos[2] += normal[2] * 0.5f;
        
        // Calculate interpolated light values
        float sky = calcLightmap(skyLight, vertexPos[0], vertexPos[1], vertexPos[2]);
        float blR = calcLightmap(blockLightR, vertexPos[0], vertexPos[1], vertexPos[2]);
        float blG = calcLightmap(blockLightG, vertexPos[0], vertexPos[1], vertexPos[2]);
        float blB = calcLightmap(blockLightB, vertexPos[0], vertexPos[1], vertexPos[2]);
        float aoValue = calculateBrightness(vertexPos);
        
        // Convert from 0-1 range to 0-15 range for light values
        return new float[] {sky * 15.0f, blR * 15.0f, blG * 15.0f, blB * 15.0f, aoValue};
    }
    
    /**
     * Sample vertex light using actual vertex position and normal.
     * This is used for rotated model elements (like horizontal logs, stairs, etc.)
     * where the vertex positions don't match the standard cube face positions.
     * 
     * <p>This matches Minecraft's QuadLighter.process() approach where:
     * <pre>
     * adjustedPosition = position - 0.5 + (normal * 0.5)
     * </pre>
     * 
     * @param face The face data containing chunk reference and block position
     * @param vertexX Vertex X position relative to block origin (0-1)
     * @param vertexY Vertex Y position relative to block origin (0-1)
     * @param vertexZ Vertex Z position relative to block origin (0-1)
     * @param normalX Normal X component (-1 to 1)
     * @param normalY Normal Y component (-1 to 1)
     * @param normalZ Normal Z component (-1 to 1)
     * @return [skyLight, blockLightR, blockLightG, blockLightB, ao] as floats
     */
    public float[] sampleVertexLightWithPosition(BlockFaceCollector.FaceData face,
                                                  float vertexX, float vertexY, float vertexZ,
                                                  float normalX, float normalY, float normalZ) {
        // If no chunk reference, return default lighting
        if (face.chunk == null) {
            return new float[] {15.0f, 0.0f, 0.0f, 0.0f, 1.0f};
        }
        
        // Get chunk-local coordinates of the block
        int cx = face.cx;
        int cy = face.cy;
        int cz = face.cz;
        
        // Check if we need to recompute lighting for this block
        if (!lightingComputed || cachedChunk != face.chunk || 
            cachedCx != cx || cachedCy != cy || cachedCz != cz) {
            computeLightingAt(face.chunk, cx, cy, cz);
            cachedChunk = face.chunk;
            cachedCx = cx;
            cachedCy = cy;
            cachedCz = cz;
            lightingComputed = true;
        }
        
        // Convert vertex position from 0-1 block space to -0.5 to 0.5 centered space
        // Then apply normal offset matching Minecraft's approach
        float x = vertexX - 0.5f + normalX * 0.5f;
        float y = vertexY - 0.5f + normalY * 0.5f;
        float z = vertexZ - 0.5f + normalZ * 0.5f;
        
        // Calculate interpolated light values
        float sky = calcLightmap(skyLight, x, y, z);
        float blR = calcLightmap(blockLightR, x, y, z);
        float blG = calcLightmap(blockLightG, x, y, z);
        float blB = calcLightmap(blockLightB, x, y, z);
        float aoValue = calculateBrightness(new float[] {x, y, z});
        
        // Convert from 0-1 range to 0-15 range for light values
        return new float[] {sky * 15.0f, blR * 15.0f, blG * 15.0f, blB * 15.0f, aoValue};
    }
    
    /**
     * Compute lighting data for a block position.
     * This populates the 3x3x3 sample grids and precomputes corner light values.
     */
    private void computeLightingAt(LevelChunk chunk, int originX, int originY, int originZ) {
        // Step 1: Sample the 3x3x3 grid around the block
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    int posX = originX + x - 1;
                    int posY = originY + y - 1;
                    int posZ = originZ + z - 1;
                    
                    Block neighborBlock = getBlockSafe(chunk, posX, posY, posZ);
                    
                    // Transparency check: block doesn't fully occlude light (opacity < 15)
                    t[x][y][z] = neighborBlock.getOpacity() < FULL_OPACITY;
                    
                    // Sample sky light
                    s[x][y][z] = getSkyLightSafe(chunk, posX, posY, posZ);
                    
                    // Sample block light RGB
                    int[] blockLight = getBlockLightRGBSafe(chunk, posX, posY, posZ);
                    bR[x][y][z] = blockLight[0];
                    bG[x][y][z] = blockLight[1];
                    bB[x][y][z] = blockLight[2];
                    
                    // Ambient occlusion / shade brightness
                    ao[x][y][z] = getShadeBrightness(neighborBlock);
                }
            }
        }
        
        // Step 2: Face occlusion check - adjust light values for occluded faces
        for (int dir = 0; dir < 6; dir++) {
            int[] step = DIRECTION_STEPS[dir];
            int posX = originX + step[0];
            int posY = originY + step[1];
            int posZ = originZ + step[2];
            
            Block neighborBlock = getBlockSafe(chunk, posX, posY, posZ);
            
            // If the neighbor fully blocks light (opacity >= 15), use center block's light - 1 as minimum
            if (neighborBlock.getOpacity() >= FULL_OPACITY) {
                int x = step[0] + 1;
                int y = step[1] + 1;
                int z = step[2] + 1;
                
                // Ensure face-adjacent position has at least center light - 1
                s[x][y][z] = Math.max(s[1][1][1] - 1, s[x][y][z]);
                bR[x][y][z] = Math.max(bR[1][1][1] - 1, bR[x][y][z]);
                bG[x][y][z] = Math.max(bG[1][1][1] - 1, bG[x][y][z]);
                bB[x][y][z] = Math.max(bB[1][1][1] - 1, bB[x][y][z]);
            }
        }
        
        // Step 3: Precompute per-corner light values for each axis
        // The corner sampling logic uses transparency to determine if the corner is visible.
        // If at least one edge is transparent, sample the actual corner value; otherwise use face value.
        // This matches Minecraft's SmoothQuadLighter logic: `txz || txy ? sxyz : sx`
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    int x1 = x * 2;
                    int y1 = y * 2;
                    int z1 = z * 2;
                    
                    // Corner values
                    int sxyz = s[x1][y1][z1];
                    int bRxyz = bR[x1][y1][z1], bGxyz = bG[x1][y1][z1], bBxyz = bB[x1][y1][z1];
                    boolean txyz = t[x1][y1][z1];
                    
                    // Edge values (along two axes)
                    int sxz = s[x1][1][z1], sxy = s[x1][y1][1], syz = s[1][y1][z1];
                    int bRxz = bR[x1][1][z1], bRxy = bR[x1][y1][1], bRyz = bR[1][y1][z1];
                    int bGxz = bG[x1][1][z1], bGxy = bG[x1][y1][1], bGyz = bG[1][y1][z1];
                    int bBxz = bB[x1][1][z1], bBxy = bB[x1][y1][1], bByz = bB[1][y1][z1];
                    boolean txz = t[x1][1][z1], txy = t[x1][y1][1], tyz = t[1][y1][z1];
                    
                    // Face values (center of faces)
                    int sx = s[x1][1][1], sy = s[1][y1][1], sz = s[1][1][z1];
                    int bRx = bR[x1][1][1], bRy = bR[1][y1][1], bRz = bR[1][1][z1];
                    int bGx = bG[x1][1][1], bGy = bG[1][y1][1], bGz = bG[1][1][z1];
                    int bBx = bB[x1][1][1], bBy = bB[1][y1][1], bBz = bB[1][1][z1];
                    boolean tx = t[x1][1][1], ty = t[1][y1][1], tz = t[1][1][z1];
                    
                    // Corner visibility check: A corner is visible if at least one of its two
                    // adjacent edges (perpendicular to the face normal) is transparent.
                    // This allows light to reach corners even when one edge is blocked.
                    // This matches Minecraft's SmoothQuadLighter logic: `txz || txy ? sxyz : sx`
                    boolean canSeeCornerXZ = txz || txy;
                    boolean canSeeCornerXY = txy || tyz;
                    boolean canSeeCornerYZ = tyz || txz;
                    
                    // Combine for X-axis face (YZ plane)
                    skyLight[0][x][y][z] = combine(sx, sxz, sxy, canSeeCornerXZ ? sxyz : sx,
                            tx, txz, txy, canSeeCornerXZ ? txyz : tx);
                    blockLightR[0][x][y][z] = combine(bRx, bRxz, bRxy, canSeeCornerXZ ? bRxyz : bRx,
                            tx, txz, txy, canSeeCornerXZ ? txyz : tx);
                    blockLightG[0][x][y][z] = combine(bGx, bGxz, bGxy, canSeeCornerXZ ? bGxyz : bGx,
                            tx, txz, txy, canSeeCornerXZ ? txyz : tx);
                    blockLightB[0][x][y][z] = combine(bBx, bBxz, bBxy, canSeeCornerXZ ? bBxyz : bBx,
                            tx, txz, txy, canSeeCornerXZ ? txyz : tx);
                    
                    // Combine for Y-axis face (XZ plane)
                    skyLight[1][x][y][z] = combine(sy, sxy, syz, canSeeCornerXY ? sxyz : sy,
                            ty, txy, tyz, canSeeCornerXY ? txyz : ty);
                    blockLightR[1][x][y][z] = combine(bRy, bRxy, bRyz, canSeeCornerXY ? bRxyz : bRy,
                            ty, txy, tyz, canSeeCornerXY ? txyz : ty);
                    blockLightG[1][x][y][z] = combine(bGy, bGxy, bGyz, canSeeCornerXY ? bGxyz : bGy,
                            ty, txy, tyz, canSeeCornerXY ? txyz : ty);
                    blockLightB[1][x][y][z] = combine(bBy, bBxy, bByz, canSeeCornerXY ? bBxyz : bBy,
                            ty, txy, tyz, canSeeCornerXY ? txyz : ty);
                    
                    // Combine for Z-axis face (XY plane)
                    skyLight[2][x][y][z] = combine(sz, syz, sxz, canSeeCornerYZ ? sxyz : sz,
                            tz, tyz, txz, canSeeCornerYZ ? txyz : tz);
                    blockLightR[2][x][y][z] = combine(bRz, bRyz, bRxz, canSeeCornerYZ ? bRxyz : bRz,
                            tz, tyz, txz, canSeeCornerYZ ? txyz : tz);
                    blockLightG[2][x][y][z] = combine(bGz, bGyz, bGxz, canSeeCornerYZ ? bGxyz : bGz,
                            tz, tyz, txz, canSeeCornerYZ ? txyz : tz);
                    blockLightB[2][x][y][z] = combine(bBz, bByz, bBxz, canSeeCornerYZ ? bBxyz : bBz,
                            tz, tyz, txz, canSeeCornerYZ ? txyz : tz);
                }
            }
        }
    }
    
    /**
     * Combine 4 light samples with transparency-based fallback logic.
     * This is an exact copy of Minecraft's SmoothQuadLighter.combine() function.
     * 
     * <p>The function uses cascading fallback logic: if a position is blocked (value=0)
     * and not transparent, it uses a neighboring value minus 1 as a fallback. The order
     * of operations matters - later calculations use potentially modified earlier values.
     * This is intentional and matches Minecraft's behavior.</p>
     * 
     * @param c Center value (face-adjacent)
     * @param s1 Edge neighbor 1
     * @param s2 Edge neighbor 2
     * @param s3 Corner value
     * @param t0 Center transparency (true if light can pass through)
     * @param t1 Edge 1 transparency
     * @param t2 Edge 2 transparency
     * @param t3 Corner transparency
     * @return Combined light value (0-1 range)
     */
    private float combine(int c, int s1, int s2, int s3, boolean t0, boolean t1, boolean t2, boolean t3) {
        // If center is blocked and not transparent, use max of neighbors - 1
        if (c == 0 && !t0) c = Math.max(0, Math.max(s1, s2) - 1);
        // If edge 1 is blocked and not transparent, use (potentially modified) center - 1
        if (s1 == 0 && !t1) s1 = Math.max(0, c - 1);
        // If edge 2 is blocked and not transparent, use (potentially modified) center - 1
        if (s2 == 0 && !t2) s2 = Math.max(0, c - 1);
        // If corner is blocked and not transparent, use max of (potentially modified) edges - 1
        if (s3 == 0 && !t3) s3 = Math.max(0, Math.max(s1, s2) - 1);
        
        // Return average in 0-1 range
        return (c + s1 + s2 + s3) / (15.0f * 4.0f);
    }
    
    /**
     * Calculate AO brightness using trilinear interpolation.
     * Matches Minecraft's SmoothQuadLighter.calculateBrightness().
     */
    private float calculateBrightness(float[] position) {
        float x = position[0], y = position[1], z = position[2];
        
        // Determine which octant of the 2x2x2 AO grid to interpolate within
        int sx = x < 0 ? 1 : 2;
        int sy = y < 0 ? 1 : 2;
        int sz = z < 0 ? 1 : 2;
        
        // Adjust coordinates to be 0-1 within the selected cube
        if (x < 0) x++;
        if (y < 0) y++;
        if (z < 0) z++;
        
        // Trilinear interpolation of AO values
        float a = 0;
        a += ao[sx - 1][sy - 1][sz - 1] * (1 - x) * (1 - y) * (1 - z);
        a += ao[sx - 1][sy - 1][sz    ] * (1 - x) * (1 - y) * z;
        a += ao[sx - 1][sy    ][sz - 1] * (1 - x) * y * (1 - z);
        a += ao[sx - 1][sy    ][sz    ] * (1 - x) * y * z;
        a += ao[sx    ][sy - 1][sz - 1] * x * (1 - y) * (1 - z);
        a += ao[sx    ][sy - 1][sz    ] * x * (1 - y) * z;
        a += ao[sx    ][sy    ][sz - 1] * x * y * (1 - z);
        a += ao[sx    ][sy    ][sz    ] * x * y * z;
        
        // Clamp result to 0-1
        return Math.max(0, Math.min(1, a));
    }
    
    /**
     * Calculate lightmap value using complex trilinear interpolation.
     * Matches Minecraft's SmoothQuadLighter.calcLightmap().
     * 
     * @param light The precomputed light array [axis][x][y][z]
     * @param x Vertex X position (-0.5 to 0.5)
     * @param y Vertex Y position (-0.5 to 0.5)
     * @param z Vertex Z position (-0.5 to 0.5)
     * @return Interpolated light value (0-1 range)
     */
    private float calcLightmap(float[][][][] light, float x, float y, float z) {
        // Scale position to -2 to 2 range
        x *= 2;
        y *= 2;
        z *= 2;
        
        // Position clamping to valid range (prevents sampling outside grid)
        float l2 = x * x + y * y + z * z;
        if (l2 > MAX_INTERPOLATION_DISTANCE_SQ) {
            float scale = (float) Math.sqrt(MAX_INTERPOLATION_DISTANCE_SQ / l2);
            x *= scale;
            y *= scale;
            z *= scale;
        }
        
        // Clamp along axes
        float ax = x > 0 ? x : -x;
        float ay = y > 0 ? y : -y;
        float az = z > 0 ? z : -z;
        
        if (ax > TWO_MINUS_EPSILON && ay <= ONE_PLUS_EPSILON && az <= ONE_PLUS_EPSILON) {
            x = x < 0 ? -TWO_MINUS_EPSILON : TWO_MINUS_EPSILON;
        } else if (ay > TWO_MINUS_EPSILON && az <= ONE_PLUS_EPSILON && ax <= ONE_PLUS_EPSILON) {
            y = y < 0 ? -TWO_MINUS_EPSILON : TWO_MINUS_EPSILON;
        } else if (az > TWO_MINUS_EPSILON && ax <= ONE_PLUS_EPSILON && ay <= ONE_PLUS_EPSILON) {
            z = z < 0 ? -TWO_MINUS_EPSILON : TWO_MINUS_EPSILON;
        }
        
        // Recalculate absolute values after adjustment
        ax = x > 0 ? x : -x;
        ay = y > 0 ? y : -y;
        az = z > 0 ? z : -z;
        
        // Edge clamping - ensure sums of two or three axes stay within valid range
        if (ax <= ONE_PLUS_EPSILON && ay + az > MAX_TWO_AXIS_SUM) {
            float scale = MAX_TWO_AXIS_SUM / (ay + az);
            y *= scale;
            z *= scale;
        } else if (ay <= ONE_PLUS_EPSILON && az + ax > MAX_TWO_AXIS_SUM) {
            float scale = MAX_TWO_AXIS_SUM / (az + ax);
            z *= scale;
            x *= scale;
        } else if (az <= ONE_PLUS_EPSILON && ax + ay > MAX_TWO_AXIS_SUM) {
            float scale = MAX_TWO_AXIS_SUM / (ax + ay);
            x *= scale;
            y *= scale;
        } else if (ax + ay + az > MAX_THREE_AXIS_SUM) {
            float scale = MAX_THREE_AXIS_SUM / (ax + ay + az);
            x *= scale;
            y *= scale;
            z *= scale;
        }
        
        // Weighted interpolation across all 8 corners
        float l = 0;
        float totalWeight = 0;
        
        for (int ix = 0; ix <= 1; ix++) {
            for (int iy = 0; iy <= 1; iy++) {
                for (int iz = 0; iz <= 1; iz++) {
                    float vx = x * (1 - ix * 2);
                    float vy = y * (1 - iy * 2);
                    float vz = z * (1 - iz * 2);
                    
                    float s3 = vx + vy + vz + 4;
                    float sx = vy + vz + 3;
                    float sy = vz + vx + 3;
                    float sz = vx + vy + 3;
                    
                    // Weight for X-axis face contribution
                    float bx = (2 * vx + vy + vz + 6) / (s3 * sy * sz * (vx + 2));
                    totalWeight += bx;
                    l += bx * light[0][ix][iy][iz];
                    
                    // Weight for Y-axis face contribution
                    float by = (2 * vy + vz + vx + 6) / (s3 * sz * sx * (vy + 2));
                    totalWeight += by;
                    l += by * light[1][ix][iy][iz];
                    
                    // Weight for Z-axis face contribution
                    float bz = (2 * vz + vx + vy + 6) / (s3 * sx * sy * (vz + 2));
                    totalWeight += bz;
                    l += bz * light[2][ix][iy][iz];
                }
            }
        }
        
        // Normalize by total weight
        l /= totalWeight;
        
        // Clamp to 0-1
        return Math.max(0, Math.min(1, l));
    }
    
    /**
     * Get vertex position relative to block center based on face and corner.
     * Returns position in range -0.5 to 0.5 for each axis.
     */
    private float[] getVertexPosition(int normalIndex, int cornerIndex) {
        // Map face + corner to vertex position
        // Vertices are at corners of the unit cube (0 or 1 along each axis)
        // We convert to -0.5 to 0.5 range for interpolation
        
        switch (normalIndex) {
            case 0: // TOP (Y+)
                switch (cornerIndex) {
                    case 0: return new float[] {-0.5f, 0.5f, -0.5f}; // x0, y1, z0
                    case 1: return new float[] {-0.5f, 0.5f, 0.5f};  // x0, y1, z1
                    case 2: return new float[] {0.5f, 0.5f, 0.5f};   // x1, y1, z1
                    case 3: return new float[] {0.5f, 0.5f, -0.5f};  // x1, y1, z0
                }
                break;
            case 1: // BOTTOM (Y-)
                switch (cornerIndex) {
                    case 0: return new float[] {-0.5f, -0.5f, -0.5f}; // x0, y0, z0
                    case 1: return new float[] {0.5f, -0.5f, -0.5f};  // x1, y0, z0
                    case 2: return new float[] {0.5f, -0.5f, 0.5f};   // x1, y0, z1
                    case 3: return new float[] {-0.5f, -0.5f, 0.5f};  // x0, y0, z1
                }
                break;
            case 2: // NORTH (Z-)
                switch (cornerIndex) {
                    case 0: return new float[] {0.5f, -0.5f, -0.5f};  // x1, y0, z0
                    case 1: return new float[] {-0.5f, -0.5f, -0.5f}; // x0, y0, z0
                    case 2: return new float[] {-0.5f, 0.5f, -0.5f};  // x0, y1, z0
                    case 3: return new float[] {0.5f, 0.5f, -0.5f};   // x1, y1, z0
                }
                break;
            case 3: // SOUTH (Z+)
                switch (cornerIndex) {
                    case 0: return new float[] {-0.5f, -0.5f, 0.5f}; // x0, y0, z1
                    case 1: return new float[] {0.5f, -0.5f, 0.5f};  // x1, y0, z1
                    case 2: return new float[] {0.5f, 0.5f, 0.5f};   // x1, y1, z1
                    case 3: return new float[] {-0.5f, 0.5f, 0.5f};  // x0, y1, z1
                }
                break;
            case 4: // WEST (X-)
                switch (cornerIndex) {
                    case 0: return new float[] {-0.5f, -0.5f, -0.5f}; // x0, y0, z0
                    case 1: return new float[] {-0.5f, -0.5f, 0.5f};  // x0, y0, z1
                    case 2: return new float[] {-0.5f, 0.5f, 0.5f};   // x0, y1, z1
                    case 3: return new float[] {-0.5f, 0.5f, -0.5f};  // x0, y1, z0
                }
                break;
            case 5: // EAST (X+)
                switch (cornerIndex) {
                    case 0: return new float[] {0.5f, -0.5f, 0.5f};  // x1, y0, z1
                    case 1: return new float[] {0.5f, -0.5f, -0.5f}; // x1, y0, z0
                    case 2: return new float[] {0.5f, 0.5f, -0.5f};  // x1, y1, z0
                    case 3: return new float[] {0.5f, 0.5f, 0.5f};   // x1, y1, z1
                }
                break;
        }
        
        // Default: center of block
        return new float[] {0, 0, 0};
    }
    
    /**
     * Get shade brightness for a block.
     * Air and non-solid blocks have full brightness (1.0).
     * Solid blocks have reduced brightness for AO effect.
     */
    private float getShadeBrightness(Block block) {
        if (block.isAir() || !block.isSolid()) {
            return 1.0f;
        }
        // Solid blocks reduce brightness due to ambient occlusion
        return SOLID_BLOCK_AO_BRIGHTNESS;
    }
    
    /**
     * Get a block safely, handling cross-chunk access if needed.
     */
    private Block getBlockSafe(LevelChunk chunk, int x, int y, int z) {
        // Check Y bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return Blocks.AIR;
        }
        
        // If within chunk bounds, use direct access
        if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
            return chunk.getBlock(x, y, z);
        }
        
        // Out of chunk bounds - use accessor if available
        if (lightAccessor != null) {
            return lightAccessor.getBlockAcrossChunks(chunk, x, y, z);
        }
        
        // No accessor - return air
        return Blocks.AIR;
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
     * Get blocklight RGB values safely, returning [0,0,0] if out of bounds.
     * Scales RGB values by intensity to properly attenuate light with distance.
     * @return Array of [R, G, B] values (0-15 each), scaled by intensity
     */
    private int[] getBlockLightRGBSafe(LevelChunk chunk, int x, int y, int z) {
        // Check bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return new int[] {0, 0, 0}; // Out of bounds: no blocklight
        }
        
        // If we have a light accessor and coordinates are out of chunk bounds, use cross-chunk sampling
        if (lightAccessor != null && 
            (x < 0 || x >= LevelChunk.WIDTH ||
             z < 0 || z >= LevelChunk.DEPTH)) {
            return lightAccessor.getBlockLightRGBAcrossChunks(chunk, x, y, z);
        }
        
        // Within chunk bounds - use direct access
        if (x < 0 || x >= LevelChunk.WIDTH ||
            z < 0 || z >= LevelChunk.DEPTH) {
            return new int[] {0, 0, 0}; // Out of chunk bounds without accessor: no blocklight
        }
        
        // Get RGB and intensity values
        int r = chunk.getBlockLightR(x, y, z);
        int g = chunk.getBlockLightG(x, y, z);
        int b = chunk.getBlockLightB(x, y, z);
        int intensity = chunk.getBlockLightI(x, y, z);
        
        // If no light, return early
        if (intensity == 0) {
            return new int[] {0, 0, 0};
        }
        
        // Scale RGB by intensity ratio to properly attenuate light
        // RGB values stay constant during propagation, but intensity decrements
        // We need to scale RGB to match the current intensity
        int maxRGB = Math.max(r, Math.max(g, b));
        
        // If maxRGB is 0 but intensity is not, something is wrong - return intensity as white light
        if (maxRGB == 0) {
            return new int[] {intensity, intensity, intensity};
        }
        
        // Scale RGB by the intensity ratio
        float scale = (float) intensity / maxRGB;
        int scaledR = Math.round(r * scale);
        int scaledG = Math.round(g * scale);
        int scaledB = Math.round(b * scale);
        
        return new int[] {scaledR, scaledG, scaledB};
    }
    
    /**
     * Invalidate the cached lighting data.
     * Call this when moving to a new block or when chunk data changes.
     */
    public void invalidateCache() {
        lightingComputed = false;
        cachedChunk = null;
    }
}
