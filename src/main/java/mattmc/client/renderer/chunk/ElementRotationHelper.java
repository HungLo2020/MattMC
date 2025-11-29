package mattmc.client.renderer.chunk;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Handles 3D rotation transformations for model elements.
 * Uses JOML's Quaternionf and Matrix4f to match Minecraft's FaceBakery exactly.
 * 
 * This is a direct port of Minecraft's rotation system:
 * - BlockModelRotation creates a Quaternionf with rotateYXZ(-yDegrees, -xDegrees, 0)
 * - FaceBakery.rotateVertexBy applies the rotation matrix around center (0.5, 0.5, 0.5)
 */
public class ElementRotationHelper {
    
    // Cache rotation matrices for common rotations to avoid allocations
    private static final Matrix4f[][] ROTATION_MATRICES = new Matrix4f[4][4];
    
    static {
        // Pre-compute rotation matrices for all valid blockstate rotations
        // Indices: [xRotation / 90][yRotation / 90]
        for (int xIdx = 0; xIdx < 4; xIdx++) {
            for (int yIdx = 0; yIdx < 4; yIdx++) {
                int xDeg = xIdx * 90;
                int yDeg = yIdx * 90;
                ROTATION_MATRICES[xIdx][yIdx] = createRotationMatrix(xDeg, yDeg);
            }
        }
    }
    
    /**
     * Create a rotation matrix matching Minecraft's BlockModelRotation.
     * Minecraft uses: new Quaternionf().rotateYXZ(-yDegrees, -xDegrees, 0)
     */
    private static Matrix4f createRotationMatrix(int xDegrees, int yDegrees) {
        float xRad = (float)(-xDegrees) * ((float)Math.PI / 180F);
        float yRad = (float)(-yDegrees) * ((float)Math.PI / 180F);
        Quaternionf quaternion = new Quaternionf().rotateYXZ(yRad, xRad, 0.0F);
        return new Matrix4f().rotation(quaternion);
    }
    
    /**
     * Rotate a single point around X and Y axes (around center 0.5, 0.5, 0.5).
     * This is a direct port of Minecraft's FaceBakery.rotateVertexBy method.
     * 
     * @param x X coordinate in 0-1 space
     * @param y Y coordinate in 0-1 space
     * @param z Z coordinate in 0-1 space
     * @param xDegrees X-axis rotation in degrees from blockstate (0, 90, 180, or 270)
     * @param yDegrees Y-axis rotation in degrees from blockstate (0, 90, 180, or 270)
     * @return Rotated point as [x, y, z]
     */
    public static float[] rotatePoint(float x, float y, float z, int xDegrees, int yDegrees) {
        // Normalize degrees to 0-359 range
        xDegrees = ((xDegrees % 360) + 360) % 360;
        yDegrees = ((yDegrees % 360) + 360) % 360;
        
        // No rotation needed
        if (xDegrees == 0 && yDegrees == 0) {
            return new float[]{x, y, z};
        }
        
        // Validate that degrees are multiples of 90 (required for blockstate rotations)
        // If not, clamp to nearest valid value to avoid array index out of bounds
        if (xDegrees % 90 != 0) {
            xDegrees = (xDegrees / 90) * 90;
        }
        if (yDegrees % 90 != 0) {
            yDegrees = (yDegrees / 90) * 90;
        }
        
        // Get the cached rotation matrix (indices are 0-3 for 0°, 90°, 180°, 270°)
        int xIdx = xDegrees / 90;
        int yIdx = yDegrees / 90;
        Matrix4f rotationMatrix = ROTATION_MATRICES[xIdx][yIdx];
        
        // Apply rotation around center (0.5, 0.5, 0.5) - matching FaceBakery.rotateVertexBy
        // 1. Translate to origin
        float cx = x - 0.5f;
        float cy = y - 0.5f;
        float cz = z - 0.5f;
        
        // 2. Apply rotation matrix
        Vector4f vec = rotationMatrix.transform(new Vector4f(cx, cy, cz, 1.0f));
        
        // 3. Translate back
        return new float[]{vec.x() + 0.5f, vec.y() + 0.5f, vec.z() + 0.5f};
    }
    
    /**
     * Rotate a point around the X axis (for backwards compatibility).
     * Uses a lookup table for 90-degree increments for precision.
     */
    public static float[] rotateX(float x, float y, float z, int degrees) {
        degrees = ((degrees % 360) + 360) % 360;
        return switch (degrees) {
            case 90 -> new float[]{x, -z, y};
            case 180 -> new float[]{x, -y, -z};
            case 270 -> new float[]{x, z, -y};
            default -> new float[]{x, y, z};
        };
    }
    
    /**
     * Rotate a point around the Y axis (for backwards compatibility).
     * Uses a lookup table for 90-degree increments for precision.
     */
    public static float[] rotateY(float x, float y, float z, int degrees) {
        degrees = ((degrees % 360) + 360) % 360;
        return switch (degrees) {
            case 90 -> new float[]{-z, y, x};
            case 180 -> new float[]{-x, y, -z};
            case 270 -> new float[]{z, y, -x};
            default -> new float[]{x, y, z};
        };
    }
}
