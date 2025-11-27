package mattmc.client.renderer.chunk;

/**
 * Handles 3D rotation transformations for model elements.
 * Provides methods to rotate points around X and Y axes,
 * centered at (0.5, 0.5, 0.5) to match Minecraft's rotation convention.
 * 
 * This is separated from ModelElementRenderer to keep rotation mathematics isolated,
 * following the pattern in Minecraft's FaceBakery class.
 * 
 * IMPORTANT: This class matches Minecraft's BlockModelRotation behavior which uses
 * rotateYXZ with NEGATIVE angles. The rotatePoint method applies rotations in Y-then-X
 * order with negated angles to match Minecraft's quaternion-based rotation.
 */
public class ElementRotationHelper {
    
    /**
     * Rotate a single point around X and Y axes (around center 0.5, 0.5, 0.5).
     * Rotations are applied in Y-then-X order with negated angles to match
     * Minecraft's BlockModelRotation constructor: rotateYXZ(-yDegrees, -xDegrees, 0).
     * 
     * @param x X coordinate in 0-1 space
     * @param y Y coordinate in 0-1 space
     * @param z Z coordinate in 0-1 space
     * @param xDegrees X-axis rotation in degrees from blockstate (0, 90, 180, or 270)
     * @param yDegrees Y-axis rotation in degrees from blockstate (0, 90, 180, or 270)
     * @return Rotated point as [x, y, z]
     */
    public static float[] rotatePoint(float x, float y, float z, int xDegrees, int yDegrees) {
        // Center around origin
        float cx = x - 0.5f, cy = y - 0.5f, cz = z - 0.5f;
        
        // Apply rotations in YXZ order to match Minecraft's rotateYXZ convention
        // Minecraft uses NEGATIVE angles in its rotateYXZ call, so we negate here
        // See BlockModelRotation constructor: new Quaternionf().rotateYXZ(-yDegrees, -xDegrees, 0)
        
        // Apply Y rotation first (with negated angle)
        if (yDegrees != 0) {
            float[] rotated = rotateY(cx, cy, cz, -yDegrees);
            cx = rotated[0]; cy = rotated[1]; cz = rotated[2];
        }
        
        // Apply X rotation second (with negated angle)
        if (xDegrees != 0) {
            float[] rotated = rotateX(cx, cy, cz, -xDegrees);
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
     * @param degrees Rotation in degrees (can be positive or negative, normalized to 0-360)
     * @return Rotated coordinates as [x, y, z]
     */
    public static float[] rotateX(float x, float y, float z, int degrees) {
        // Normalize to 0-359 range
        degrees = ((degrees % 360) + 360) % 360;
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
     * @param degrees Rotation in degrees (can be positive or negative, normalized to 0-360)
     * @return Rotated coordinates as [x, y, z]
     */
    public static float[] rotateY(float x, float y, float z, int degrees) {
        // Normalize to 0-359 range
        degrees = ((degrees % 360) + 360) % 360;
        return switch (degrees) {
            case 90 -> new float[]{-z, y, x};
            case 180 -> new float[]{-x, y, -z};
            case 270 -> new float[]{z, y, -x};
            default -> new float[]{x, y, z};
        };
    }
}
