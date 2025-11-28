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
     * <p>This follows Minecraft's algorithm from ModelBlockRenderer.AmbientOcclusionFace exactly:
     * <ol>
     *   <li>blockpos = block position + face_normal (for a full block)</li>
     *   <li>Sample 4 edge neighbors at blockpos + corners[0-3]</li>
     *   <li>For corners (diagonals), check if blockpos + corner + face_normal blocks view</li>
     *   <li>If corner is visible, sample blockpos + corner0 + corner1</li>
     *   <li>Also sample face-adjacent position for the center brightness</li>
     *   <li>Average: (f[corner0] + f[corner1] + f[diagonal] + f[center]) * 0.25</li>
     * </ol>
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
        int bx = face.cx;  // Block position
        int by = face.cy;
        int bz = face.cz;
        
        // Get face normal
        int[] normal = FACE_NORMAL[faceIndex];
        
        // blockpos = block position + face_normal (position adjacent to face)
        // For an UP face, this is the air block directly above
        int sx = bx + normal[0];
        int sy = by + normal[1];
        int sz = bz + normal[2];
        
        // Get the corner directions for this face (4 directions perpendicular to face)
        int[][] corners = FACE_CORNERS[faceIndex];
        int vertIdx = Math.min(vertexIndex, 3);
        int[] vertCorners = VERTEX_CORNERS[faceIndex][vertIdx];
        int edge0Idx = vertCorners[0];
        int edge1Idx = vertCorners[1];
        
        int[] edge0Dir = corners[edge0Idx];
        int[] edge1Dir = corners[edge1Idx];
        
        // Sample 4 edge neighbors at blockpos + corners[0-3]
        // For this vertex, we need edge0 and edge1
        
        // Edge0 position: blockpos + edge0Dir
        int e0x = sx + edge0Dir[0];
        int e0y = sy + edge0Dir[1];
        int e0z = sz + edge0Dir[2];
        float edge0Brightness = getShadeBrightness(chunk, e0x, e0y, e0z);
        
        // Edge1 position: blockpos + edge1Dir  
        int e1x = sx + edge1Dir[0];
        int e1y = sy + edge1Dir[1];
        int e1z = sz + edge1Dir[2];
        float edge1Brightness = getShadeBrightness(chunk, e1x, e1y, e1z);
        
        // Check if corner is visible by looking at (edge position + face_normal)
        // This is Minecraft's flag check for whether to sample the diagonal
        boolean edge0CanSeeCorner = !isViewBlocking(chunk, e0x + normal[0], e0y + normal[1], e0z + normal[2]);
        boolean edge1CanSeeCorner = !isViewBlocking(chunk, e1x + normal[0], e1y + normal[1], e1z + normal[2]);
        
        // Corner (diagonal) position: blockpos + edge0Dir + edge1Dir
        // Only sample if at least one edge can see it
        float cornerBrightness;
        if (edge0CanSeeCorner || edge1CanSeeCorner) {
            int cx = sx + edge0Dir[0] + edge1Dir[0];
            int cy = sy + edge0Dir[1] + edge1Dir[1];
            int cz = sz + edge0Dir[2] + edge1Dir[2];
            cornerBrightness = getShadeBrightness(chunk, cx, cy, cz);
        } else {
            // Both edges block the corner - use edge0's brightness as fallback
            cornerBrightness = edge0Brightness;
        }
        
        // Face center brightness: sample the block at blockpos (face-adjacent)
        // This is f8 in Minecraft's code
        float faceBrightness = getShadeBrightness(chunk, sx, sy, sz);
        
        // Average the 4 samples (matching Minecraft's formula)
        float ao = (edge0Brightness + edge1Brightness + cornerBrightness + faceBrightness) * 0.25f;
        
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
     * Check if a block at the given position blocks view for corner visibility.
     * Matches Minecraft's isViewBlocking check.
     */
    private boolean isViewBlocking(LevelChunk chunk, int x, int y, int z) {
        Block block = getBlockSafe(chunk, x, y, z);
        // In Minecraft: !blockstate.isViewBlocking() || blockstate.getLightBlock() == 0
        // We simplify this: solid blocks block view, air/non-solid don't
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
