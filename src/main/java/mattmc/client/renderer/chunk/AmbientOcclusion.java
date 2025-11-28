package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Calculates ambient occlusion (AO) for block faces using Minecraft's algorithm.
 * 
 * <p>Minecraft's AO works by sampling the brightness (shade) and light values of 
 * neighboring blocks around each vertex of a face. For each vertex:
 * <ul>
 *   <li>Sample 4 neighbors: 2 edge-adjacent + 1 corner-adjacent + 1 face-adjacent</li>
 *   <li>Corner is only sampled if at least one edge neighbor doesn't block light</li>
 *   <li>Average the brightness values to create a smooth gradient</li>
 * </ul>
 * 
 * <p>This implementation follows Minecraft's ModelBlockRenderer.AmbientOcclusionFace
 * as closely as possible while adapting to MattMC's architecture.
 * 
 * <p><b>Architecture:</b> This class is in the chunk/ package and does NOT access
 * the backend/ or opengl/ packages directly, following MattMC's abstraction paradigm.
 * 
 * @see mattmc.client.renderer.chunk.VertexLightSampler
 */
public class AmbientOcclusion {
    
    /**
     * Interface for accessing blocks across chunk boundaries.
     * This allows AO calculation to work even at chunk edges.
     */
    public interface BlockAccessor {
        /**
         * Get a block at chunk-local coordinates, checking neighboring chunks if necessary.
         * @param chunk The current chunk
         * @param x Chunk-local X coordinate (can be outside 0-15 range)
         * @param y Chunk-local Y coordinate (0-383)
         * @param z Chunk-local Z coordinate (can be outside 0-15 range)
         * @return The block at the specified position
         */
        Block getBlockAcrossChunks(LevelChunk chunk, int x, int y, int z);
    }
    
    /**
     * Face direction indices matching MeshBuilder's convention.
     * TOP=0, BOTTOM=1, NORTH=2, SOUTH=3, WEST=4, EAST=5
     */
    public static final int FACE_UP = 0;
    public static final int FACE_DOWN = 1;
    public static final int FACE_NORTH = 2;
    public static final int FACE_SOUTH = 3;
    public static final int FACE_WEST = 4;
    public static final int FACE_EAST = 5;
    
    /**
     * Brightness value for solid blocks that cause occlusion.
     * Matches Minecraft's shading for solid blocks.
     */
    private static final float SOLID_BLOCK_BRIGHTNESS = 0.2f;
    
    /**
     * Direction offsets for each face's adjacent directions.
     * For each face, we need the 4 edge directions that are perpendicular to the face normal.
     * 
     * Format: corners[face][corner_direction] = {dx, dy, dz}
     * Corner directions follow Minecraft's AdjacencyInfo enum pattern.
     */
    private static final int[][][] FACE_CORNERS = {
        // UP (Y+): corners are EAST, WEST, NORTH, SOUTH (in X-Z plane)
        {{1, 0, 0}, {-1, 0, 0}, {0, 0, -1}, {0, 0, 1}},
        // DOWN (Y-): corners are WEST, EAST, NORTH, SOUTH
        {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}},
        // NORTH (Z-): corners are UP, DOWN, EAST, WEST
        {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}},
        // SOUTH (Z+): corners are WEST, EAST, DOWN, UP
        {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}},
        // WEST (X-): corners are UP, DOWN, NORTH, SOUTH
        {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}},
        // EAST (X+): corners are DOWN, UP, NORTH, SOUTH
        {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}}
    };
    
    /**
     * Face normal directions.
     * FACE_NORMAL[face] = {dx, dy, dz}
     */
    private static final int[][] FACE_NORMAL = {
        {0, 1, 0},   // UP
        {0, -1, 0},  // DOWN
        {0, 0, -1},  // NORTH
        {0, 0, 1},   // SOUTH
        {-1, 0, 0},  // WEST
        {1, 0, 0}    // EAST
    };
    
    /**
     * Vertex indices for each corner of each face.
     * Each vertex is influenced by 2 edge neighbors and 1 corner neighbor.
     * 
     * For UP face: vert0 = (corner0 + corner3), vert1 = (corner0 + corner2), etc.
     * This follows Minecraft's AdjacencyInfo pattern.
     * 
     * Format: VERTEX_CORNERS[face][vertex] = {edge0, edge1}
     * where edge0 and edge1 are indices into FACE_CORNERS[face]
     */
    private static final int[][][] VERTEX_CORNERS = {
        // UP: vertex to corner mapping
        // v0 = EAST + SOUTH, v1 = EAST + NORTH, v2 = WEST + NORTH, v3 = WEST + SOUTH
        {{0, 3}, {0, 2}, {1, 2}, {1, 3}},
        // DOWN: v0 = WEST + SOUTH, v1 = WEST + NORTH, v2 = EAST + NORTH, v3 = EAST + SOUTH
        {{0, 3}, {0, 2}, {1, 2}, {1, 3}},
        // NORTH: v0 = UP + WEST, v1 = UP + EAST, v2 = DOWN + EAST, v3 = DOWN + WEST
        {{0, 3}, {0, 2}, {1, 2}, {1, 3}},
        // SOUTH: v0 = UP + WEST, v1 = DOWN + WEST, v2 = DOWN + EAST, v3 = UP + EAST
        {{3, 0}, {2, 0}, {2, 1}, {3, 1}},
        // WEST: v0 = UP + SOUTH, v1 = UP + NORTH, v2 = DOWN + NORTH, v3 = DOWN + SOUTH
        {{0, 3}, {0, 2}, {1, 2}, {1, 3}},
        // EAST: v0 = DOWN + SOUTH, v1 = DOWN + NORTH, v2 = UP + NORTH, v3 = UP + SOUTH
        {{0, 3}, {0, 2}, {1, 2}, {1, 3}}
    };
    
    private BlockAccessor blockAccessor;
    
    /**
     * Create a new ambient occlusion calculator.
     */
    public AmbientOcclusion() {
    }
    
    /**
     * Set the block accessor for cross-chunk queries.
     */
    public void setBlockAccessor(BlockAccessor accessor) {
        this.blockAccessor = accessor;
    }
    
    /**
     * Calculate ambient occlusion brightness for a vertex.
     * 
     * <p>This follows Minecraft's algorithm from ModelBlockRenderer.AmbientOcclusionFace:
     * <ol>
     *   <li>Sample 4 positions: 2 edge neighbors, 1 corner (diagonal), and 1 face-adjacent</li>
     *   <li>Check if corner should be sampled by looking one block in face direction</li>
     *   <li>Average the brightness values</li>
     * </ol>
     * 
     * <p>Key insight: For an UP face, we sample horizontal neighbors at the block's Y level,
     * NOT at the face level (Y+1). The occlusion comes from neighboring blocks at the same
     * height, not from blocks above.
     * 
     * @param face The face data containing position and chunk reference
     * @param faceIndex Face direction (0=UP, 1=DOWN, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST)
     * @param vertexIndex Vertex index (0-3)
     * @return AO brightness value (0.0 = fully occluded, 1.0 = no occlusion)
     */
    public float calculateVertexAO(BlockFaceCollector.FaceData face, int faceIndex, int vertexIndex) {
        if (face.chunk == null) {
            return 1.0f; // No occlusion if we don't have chunk data
        }
        
        LevelChunk chunk = face.chunk;
        int bx = face.cx;  // Block position (not face position)
        int by = face.cy;
        int bz = face.cz;
        
        // Get face normal for checking if corners should be sampled
        int[] normal = FACE_NORMAL[faceIndex];
        
        // Get the corner directions for this face (4 directions perpendicular to face)
        int[][] corners = FACE_CORNERS[faceIndex];
        int vertIdx = Math.min(vertexIndex, 3);
        int[] vertCorners = VERTEX_CORNERS[faceIndex][vertIdx];
        int edge0Idx = vertCorners[0];
        int edge1Idx = vertCorners[1];
        
        int[] edge0Dir = corners[edge0Idx];
        int[] edge1Dir = corners[edge1Idx];
        
        // Sample the 4 neighbor positions (at block level, in directions perpendicular to face)
        // These are the positions that create occlusion on this vertex
        
        // 1. Edge0 neighbor (e.g., EAST neighbor for UP face)
        int e0x = bx + edge0Dir[0];
        int e0y = by + edge0Dir[1];
        int e0z = bz + edge0Dir[2];
        float edge0Brightness = getShadeBrightness(chunk, e0x, e0y, e0z);
        
        // 2. Edge1 neighbor (e.g., SOUTH neighbor for UP face)  
        int e1x = bx + edge1Dir[0];
        int e1y = by + edge1Dir[1];
        int e1z = bz + edge1Dir[2];
        float edge1Brightness = getShadeBrightness(chunk, e1x, e1y, e1z);
        
        // Check if we should sample the corner by looking at positions in the face direction
        // (This is how Minecraft determines if the corner is "visible" or blocked)
        int e0fx = e0x + normal[0];
        int e0fy = e0y + normal[1];
        int e0fz = e0z + normal[2];
        boolean edge0CanSeeCorner = !isBlockingLight(chunk, e0fx, e0fy, e0fz);
        
        int e1fx = e1x + normal[0];
        int e1fy = e1y + normal[1];
        int e1fz = e1z + normal[2];
        boolean edge1CanSeeCorner = !isBlockingLight(chunk, e1fx, e1fy, e1fz);
        
        // 3. Corner neighbor (diagonal - only sampled if at least one edge can see it)
        float cornerBrightness;
        if (edge0CanSeeCorner || edge1CanSeeCorner) {
            int cx2 = bx + edge0Dir[0] + edge1Dir[0];
            int cy2 = by + edge0Dir[1] + edge1Dir[1];
            int cz2 = bz + edge0Dir[2] + edge1Dir[2];
            cornerBrightness = getShadeBrightness(chunk, cx2, cy2, cz2);
        } else {
            // Both edges block - corner contributes minimum brightness
            cornerBrightness = SOLID_BLOCK_BRIGHTNESS;
        }
        
        // 4. Face-adjacent position (position in normal direction from block)
        int fx = bx + normal[0];
        int fy = by + normal[1];
        int fz = bz + normal[2];
        float faceBrightness = getShadeBrightness(chunk, fx, fy, fz);
        
        // Average the 4 samples
        float ao = (faceBrightness + edge0Brightness + edge1Brightness + cornerBrightness) * 0.25f;
        
        return ao;
    }
    
    /**
     * Calculate AO for all 4 vertices of a face.
     * Returns array of 4 brightness values [v0, v1, v2, v3].
     */
    public float[] calculateFaceAO(BlockFaceCollector.FaceData face, int faceIndex) {
        float[] ao = new float[4];
        for (int i = 0; i < 4; i++) {
            ao[i] = calculateVertexAO(face, faceIndex, i);
        }
        return ao;
    }
    
    /**
     * Get the shade brightness for a block at the given position.
     * This is Minecraft's getShadeBrightness() equivalent.
     * 
     * @return Brightness value (0.0-1.0), where 1.0 = full brightness (air), lower = occluded
     */
    private float getShadeBrightness(LevelChunk chunk, int x, int y, int z) {
        Block block = getBlockSafe(chunk, x, y, z);
        
        // Air and non-solid blocks have full brightness (don't cause occlusion)
        if (block.isAir() || !block.isSolid()) {
            return 1.0f;
        }
        
        // Solid blocks cause occlusion - return lower brightness
        return SOLID_BLOCK_BRIGHTNESS;
    }
    
    /**
     * Check if a block at the given position blocks light for AO calculation.
     */
    private boolean isBlockingLight(LevelChunk chunk, int x, int y, int z) {
        Block block = getBlockSafe(chunk, x, y, z);
        return block.isSolid();
    }
    
    /**
     * Get a block safely, handling cross-chunk access if needed.
     */
    private Block getBlockSafe(LevelChunk chunk, int x, int y, int z) {
        // Check Y bounds
        if (y < 0 || y >= LevelChunk.HEIGHT) {
            return mattmc.world.level.block.Blocks.AIR;
        }
        
        // If within chunk bounds, use direct access
        if (x >= 0 && x < LevelChunk.WIDTH && z >= 0 && z < LevelChunk.DEPTH) {
            return chunk.getBlock(x, y, z);
        }
        
        // Out of chunk bounds - use accessor if available
        if (blockAccessor != null) {
            return blockAccessor.getBlockAcrossChunks(chunk, x, y, z);
        }
        
        // No accessor - return air (no occlusion at chunk edges)
        return mattmc.world.level.block.Blocks.AIR;
    }
    
    /**
     * Directional shade values following Minecraft's convention.
     * These are applied after AO calculation to give faces different base brightness.
     * 
     * UP = 1.0 (brightest)
     * DOWN = 0.5
     * NORTH/SOUTH = 0.8
     * WEST/EAST = 0.6
     */
    public static float getDirectionalShade(int faceIndex) {
        return switch (faceIndex) {
            case FACE_UP -> 1.0f;
            case FACE_DOWN -> 0.5f;
            case FACE_NORTH, FACE_SOUTH -> 0.8f;
            case FACE_WEST, FACE_EAST -> 0.6f;
            default -> 1.0f;
        };
    }
}
