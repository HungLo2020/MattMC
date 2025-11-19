package mattmc.client.renderer.model;

/**
 * Represents a pre-processed quad (4 vertices) ready for rendering.
 * Similar to Minecraft's BakedQuad class.
 * 
 * Each vertex contains:
 * - Position (x, y, z)
 * - Texture coordinates (u, v)
 * - Normal vector (nx, ny, nz)
 * - Color (r, g, b, a)
 */
public class BakedQuad {
    private final float[] vertices;  // 4 vertices * 12 floats = 48 floats
    private final int tintIndex;
    private final Direction face;
    private final String texturePath;
    
    // Vertex format: x, y, z, u, v, nx, ny, nz, r, g, b, a (12 floats per vertex)
    private static final int VERTEX_SIZE = 12;
    private static final int VERTICES_PER_QUAD = 4;
    
    /**
     * Create a baked quad.
     * 
     * @param vertices Array of 48 floats (4 vertices * 12 floats each)
     * @param tintIndex Tint index for this quad (-1 if no tint)
     * @param face The face direction this quad represents
     * @param texturePath The texture path for this quad
     */
    public BakedQuad(float[] vertices, int tintIndex, Direction face, String texturePath) {
        if (vertices.length != VERTICES_PER_QUAD * VERTEX_SIZE) {
            throw new IllegalArgumentException("Vertices array must contain exactly " + (VERTICES_PER_QUAD * VERTEX_SIZE) + " floats");
        }
        this.vertices = vertices;
        this.tintIndex = tintIndex;
        this.face = face;
        this.texturePath = texturePath;
    }
    
    /**
     * Get the vertex data array.
     * Format: x, y, z, u, v, nx, ny, nz, r, g, b, a (repeated 4 times)
     */
    public float[] getVertices() {
        return vertices;
    }
    
    /**
     * Get the tint index for this quad.
     * -1 means no tint should be applied.
     */
    public int getTintIndex() {
        return tintIndex;
    }
    
    /**
     * Get the face direction this quad represents.
     */
    public Direction getFace() {
        return face;
    }
    
    /**
     * Get the texture path for this quad.
     */
    public String getTexturePath() {
        return texturePath;
    }
    
    /**
     * Direction enum matching Minecraft's Direction.
     */
    public enum Direction {
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);
        
        private final int normalX;
        private final int normalY;
        private final int normalZ;
        
        Direction(int normalX, int normalY, int normalZ) {
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
        }
        
        public int getNormalX() {
            return normalX;
        }
        
        public int getNormalY() {
            return normalY;
        }
        
        public int getNormalZ() {
            return normalZ;
        }
    }
}
