package mattmc.client.renderer.chunk;

/**
 * Calculates vertex positions and normals for block faces.
 * Maps face directions to their corner vertices, following the exact vertex ordering
 * from Minecraft's FaceInfo enum to ensure consistent quad winding and lighting.
 * 
 * This is separated from ModelElementRenderer to isolate face geometry calculations,
 * matching the pattern in Minecraft's FaceInfo class.
 */
public class FaceVertexCalculator {
    
    /**
     * Get the corner position for a specific vertex index of a face.
     * This EXACTLY follows Minecraft's FaceInfo.VertexInfo mapping from FaceInfo.java.
     * 
     * Constants mapping: MIN_X=x0, MAX_X=x1, MIN_Y=y0, MAX_Y=y1, MIN_Z=z0, MAX_Z=z1
     * 
     * @param direction Face direction (up, down, north, south, east, west)
     * @param vertexIndex Vertex index (0-3)
     * @param x0 Minimum X bound
     * @param y0 Minimum Y bound
     * @param z0 Minimum Z bound
     * @param x1 Maximum X bound
     * @param y1 Maximum Y bound
     * @param z1 Maximum Z bound
     * @return Corner position as [x, y, z]
     */
    public static float[] getFaceVertexCorner(String direction, int vertexIndex, float x0, float y0, float z0,
                                              float x1, float y1, float z1) {
        // EXACT mapping from Minecraft's FaceInfo enum (FaceInfo.java lines 10-15)
        return switch (direction) {
            case "up" -> switch (vertexIndex) {
                // UP: (MIN_X,MAX_Y,MIN_Z), (MIN_X,MAX_Y,MAX_Z), (MAX_X,MAX_Y,MAX_Z), (MAX_X,MAX_Y,MIN_Z)
                case 0 -> new float[]{x0, y1, z0};
                case 1 -> new float[]{x0, y1, z1};
                case 2 -> new float[]{x1, y1, z1};
                case 3 -> new float[]{x1, y1, z0};
                default -> new float[]{x0, y1, z0};
            };
            case "down" -> switch (vertexIndex) {
                // DOWN: (MIN_X,MIN_Y,MAX_Z), (MIN_X,MIN_Y,MIN_Z), (MAX_X,MIN_Y,MIN_Z), (MAX_X,MIN_Y,MAX_Z)
                case 0 -> new float[]{x0, y0, z1};
                case 1 -> new float[]{x0, y0, z0};
                case 2 -> new float[]{x1, y0, z0};
                case 3 -> new float[]{x1, y0, z1};
                default -> new float[]{x0, y0, z1};
            };
            case "north" -> switch (vertexIndex) {
                // NORTH: (MAX_X,MAX_Y,MIN_Z), (MAX_X,MIN_Y,MIN_Z), (MIN_X,MIN_Y,MIN_Z), (MIN_X,MAX_Y,MIN_Z)
                case 0 -> new float[]{x1, y1, z0};
                case 1 -> new float[]{x1, y0, z0};
                case 2 -> new float[]{x0, y0, z0};
                case 3 -> new float[]{x0, y1, z0};
                default -> new float[]{x1, y1, z0};
            };
            case "south" -> switch (vertexIndex) {
                // SOUTH: (MIN_X,MAX_Y,MAX_Z), (MIN_X,MIN_Y,MAX_Z), (MAX_X,MIN_Y,MAX_Z), (MAX_X,MAX_Y,MAX_Z)
                case 0 -> new float[]{x0, y1, z1};
                case 1 -> new float[]{x0, y0, z1};
                case 2 -> new float[]{x1, y0, z1};
                case 3 -> new float[]{x1, y1, z1};
                default -> new float[]{x0, y1, z1};
            };
            case "west" -> switch (vertexIndex) {
                // WEST: (MIN_X,MAX_Y,MIN_Z), (MIN_X,MIN_Y,MIN_Z), (MIN_X,MIN_Y,MAX_Z), (MIN_X,MAX_Y,MAX_Z)
                case 0 -> new float[]{x0, y1, z0};
                case 1 -> new float[]{x0, y0, z0};
                case 2 -> new float[]{x0, y0, z1};
                case 3 -> new float[]{x0, y1, z1};
                default -> new float[]{x0, y1, z0};
            };
            case "east" -> switch (vertexIndex) {
                // EAST: (MAX_X,MAX_Y,MAX_Z), (MAX_X,MIN_Y,MAX_Z), (MAX_X,MIN_Y,MIN_Z), (MAX_X,MAX_Y,MIN_Z)
                case 0 -> new float[]{x1, y1, z1};
                case 1 -> new float[]{x1, y0, z1};
                case 2 -> new float[]{x1, y0, z0};
                case 3 -> new float[]{x1, y1, z0};
                default -> new float[]{x1, y1, z1};
            };
            default -> switch (vertexIndex) {
                // Default to NORTH if unknown
                case 0 -> new float[]{x1, y1, z0};
                case 1 -> new float[]{x1, y0, z0};
                case 2 -> new float[]{x0, y0, z0};
                case 3 -> new float[]{x0, y1, z0};
                default -> new float[]{x1, y1, z0};
            };
        };
    }
    
    /**
     * Get face normal vector for a given direction.
     * 
     * @param direction Face direction (up, down, north, south, east, west)
     * @return Normal vector as [x, y, z]
     */
    public static float[] getFaceNormal(String direction) {
        return switch (direction) {
            case "up" -> new float[]{0, 1, 0};
            case "down" -> new float[]{0, -1, 0};
            case "north" -> new float[]{0, 0, -1};
            case "south" -> new float[]{0, 0, 1};
            case "west" -> new float[]{-1, 0, 0};
            case "east" -> new float[]{1, 0, 0};
            default -> new float[]{0, 0, -1};
        };
    }
    
    /**
     * Get face index for lighting calculations.
     * Maps direction strings to integer indices used by the lighting system.
     * 
     * @param direction Face direction (up, down, north, south, east, west)
     * @return Face index (0-5)
     */
    public static int getFaceIndex(String direction) {
        return switch (direction) {
            case "up" -> 0;
            case "down" -> 1;
            case "north" -> 2;
            case "south" -> 3;
            case "west" -> 4;
            case "east" -> 5;
            default -> 0;
        };
    }
}
