package mattmc.client.renderer.chunk;

/**
 * Handles 3D rotation transformations for model elements.
 * Provides methods to rotate points around X and Y axes,
 * centered at (0.5, 0.5, 0.5) to match MattMC's rotation convention.
 * 
 * This is separated from ModelElementRenderer to keep rotation mathematics isolated,
 * following the pattern in MattMC's FaceBakery class.
 */
public class ElementRotationHelper {
    
    /**
     * Rotate a single point around X and Y axes (around center 0.5, 0.5, 0.5).
     * X rotation is applied first, then Y rotation (following MattMC's convention).
     * 
     * @param x X coordinate in 0-1 space
     * @param y Y coordinate in 0-1 space
     * @param z Z coordinate in 0-1 space
     * @param xDegrees X-axis rotation in degrees (0, 90, 180, or 270)
     * @param yDegrees Y-axis rotation in degrees (0, 90, 180, or 270)
     * @return Rotated point as [x, y, z]
     */
    public static float[] rotatePoint(float x, float y, float z, int xDegrees, int yDegrees) {
        // Center around origin
        float cx = x - 0.5f, cy = y - 0.5f, cz = z - 0.5f;
        
        // Apply X rotation
        if (xDegrees != 0) {
            float[] rotated = rotateX(cx, cy, cz, xDegrees);
            cx = rotated[0]; cy = rotated[1]; cz = rotated[2];
        }
        
        // Apply Y rotation
        if (yDegrees != 0) {
            float[] rotated = rotateY(cx, cy, cz, yDegrees);
            cx = rotated[0]; cy = rotated[1]; cz = rotated[2];
        }
        
        // Un-center
        return new float[]{cx + 0.5f, cy + 0.5f, cz + 0.5f};
    }
    
    /**
     * Rotate a point around the X axis.
     * Uses a lookup table for 90-degree increments for precision.
     * 
     * @param x X coordinate (centered at origin)
     * @param y Y coordinate (centered at origin)
     * @param z Z coordinate (centered at origin)
     * @param degrees Rotation in degrees (0, 90, 180, or 270)
     * @return Rotated coordinates as [x, y, z]
     */
    public static float[] rotateX(float x, float y, float z, int degrees) {
        return switch (degrees) {
            case 90 -> new float[]{x, -z, y};
            case 180 -> new float[]{x, -y, -z};
            case 270 -> new float[]{x, z, -y};
            default -> new float[]{x, y, z};
        };
    }
    
    /**
     * Rotate a point around the Y axis.
     * Uses a lookup table for 90-degree increments for precision.
     * 
     * @param x X coordinate (centered at origin)
     * @param y Y coordinate (centered at origin)
     * @param z Z coordinate (centered at origin)
     * @param degrees Rotation in degrees (0, 90, 180, or 270)
     * @return Rotated coordinates as [x, y, z]
     */
    public static float[] rotateY(float x, float y, float z, int degrees) {
        return switch (degrees) {
            case 90 -> new float[]{-z, y, x};
            case 180 -> new float[]{-x, y, -z};
            case 270 -> new float[]{z, y, -x};
            default -> new float[]{x, y, z};
        };
    }
}
