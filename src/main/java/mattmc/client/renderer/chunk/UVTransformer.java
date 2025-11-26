package mattmc.client.renderer.chunk;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles UV coordinate transformations for block models.
 * This is an EXACT port of Minecraft's BlockMath and FaceBakery UV lock system.
 * 
 * Implements the matrix-based UV lock transformation from:
 * - BlockMath.getUVLockTransform()
 * - FaceBakery.recomputeUVs()
 * 
 * The formula is: GLOBAL_TO_LOCAL[original] * inverse_rotation * LOCAL_TO_GLOBAL[rotated]
 */
public class UVTransformer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(UVTransformer.class);
    
    // Direction constants matching Minecraft's Direction enum order
    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;
    
    // Direction normals
    private static final int[][] DIRECTION_NORMALS = {
        {0, -1, 0},  // DOWN
        {0, 1, 0},   // UP
        {0, 0, -1},  // NORTH
        {0, 0, 1},   // SOUTH
        {-1, 0, 0},  // WEST
        {1, 0, 0}    // EAST
    };
    
    /**
     * LOCAL_TO_GLOBAL transforms from face-local UV space to world-aligned UV space.
     * These are the exact matrices from Minecraft's BlockMath.VANILLA_UV_TRANSFORM_LOCAL_TO_GLOBAL
     */
    private static final Matrix4f[] LOCAL_TO_GLOBAL = new Matrix4f[6];
    
    /**
     * GLOBAL_TO_LOCAL is the inverse of LOCAL_TO_GLOBAL.
     * These are the exact matrices from Minecraft's BlockMath.VANILLA_UV_TRANSFORM_GLOBAL_TO_LOCAL
     */
    private static final Matrix4f[] GLOBAL_TO_LOCAL = new Matrix4f[6];
    
    static {
        // Initialize LOCAL_TO_GLOBAL matrices exactly as Minecraft does
        // SOUTH is identity (the reference face)
        LOCAL_TO_GLOBAL[SOUTH] = new Matrix4f(); // identity
        
        // EAST: rotateY(PI/2)
        LOCAL_TO_GLOBAL[EAST] = new Matrix4f().rotation(new Quaternionf().rotateY((float)Math.PI / 2f));
        
        // WEST: rotateY(-PI/2)
        LOCAL_TO_GLOBAL[WEST] = new Matrix4f().rotation(new Quaternionf().rotateY(-(float)Math.PI / 2f));
        
        // NORTH: rotateY(PI)
        LOCAL_TO_GLOBAL[NORTH] = new Matrix4f().rotation(new Quaternionf().rotateY((float)Math.PI));
        
        // UP: rotateX(-PI/2)
        LOCAL_TO_GLOBAL[UP] = new Matrix4f().rotation(new Quaternionf().rotateX(-(float)Math.PI / 2f));
        
        // DOWN: rotateX(PI/2)
        LOCAL_TO_GLOBAL[DOWN] = new Matrix4f().rotation(new Quaternionf().rotateX((float)Math.PI / 2f));
        
        // Compute inverses for GLOBAL_TO_LOCAL
        for (int i = 0; i < 6; i++) {
            GLOBAL_TO_LOCAL[i] = new Matrix4f(LOCAL_TO_GLOBAL[i]).invert();
        }
    }
    
    /**
     * Result of UV lock transformation including new UV coordinates and additional rotation.
     */
    public static class UVLockResult {
        public final float[] uv;
        public final int additionalRotation;
        
        public UVLockResult(float[] uv, int additionalRotation) {
            this.uv = uv;
            this.additionalRotation = additionalRotation;
        }
    }
    
    /**
     * Transform UV coordinates when uvlock=true to keep textures world-aligned.
     * This is an EXACT port of Minecraft's FaceBakery.recomputeUVs() method.
     * 
     * @param uv UV coordinates [u0, v0, u1, v1] in 0-16 space
     * @param faceDirection Original face direction (before rotation): up, down, north, south, east, west
     * @param xRotation X-axis rotation applied to block geometry (0, 90, 180, 270)
     * @param yRotation Y-axis rotation applied to block geometry (0, 90, 180, 270)
     * @param faceRotation Original face rotation from model JSON (0, 90, 180, 270)
     * @return UVLockResult containing transformed UV coordinates and additional rotation to apply
     */
    public static UVLockResult transformUVsWithUVLock(float[] uv, String faceDirection, 
                                                       int xRotation, int yRotation, int faceRotation) {
        // Normalize rotations to 0-359 range
        xRotation = ((xRotation % 360) + 360) % 360;
        yRotation = ((yRotation % 360) + 360) % 360;
        
        // If no rotation, no transformation needed
        if (xRotation == 0 && yRotation == 0) {
            return new UVLockResult(uv.clone(), faceRotation);
        }
        
        int faceIndex = getDirectionIndex(faceDirection);
        
        // Build the model rotation matrix (matches Minecraft's Transformation)
        Matrix4f modelRotation = buildRotationMatrix(xRotation, yRotation);
        
        // Get the UV lock transform matrix using Minecraft's BlockMath.getUVLockTransform algorithm
        Matrix4f uvLockMatrix = getUVLockTransform(modelRotation, faceIndex);
        
        // Apply the transformation to UV coordinates (matching FaceBakery.recomputeUVs)
        return applyUVLockTransform(uv, uvLockMatrix, faceRotation);
    }
    
    /**
     * Build a rotation matrix from X and Y rotation angles.
     * This creates a PURE rotation matrix (no translation) like Minecraft's Transformation.
     * The center-to-corner transform is applied separately in getUVLockTransform.
     * 
     * In Minecraft, the blockstate rotation is applied as: first rotate around Y, then around X.
     * This means the matrix is: Rx * Ry (X rotation applied after Y rotation).
     * In JOML, rotate() multiplies on the right, so we need to call rotate(X) first, then rotate(Y).
     */
    private static Matrix4f buildRotationMatrix(int xDegrees, int yDegrees) {
        Matrix4f matrix = new Matrix4f();
        
        // Build Rx * Ry matrix: call rotate(X) first, then rotate(Y)
        // This is because JOML's rotate() multiplies on the right
        if (xDegrees != 0) {
            matrix.rotate((float)Math.toRadians(xDegrees), 1, 0, 0);
        }
        if (yDegrees != 0) {
            matrix.rotate((float)Math.toRadians(yDegrees), 0, 1, 0);
        }
        
        return matrix;
    }
    
    /**
     * Get the UV lock transform matrix.
     * This is an EXACT port of Minecraft's BlockMath.getUVLockTransform().
     * 
     * Formula: GLOBAL_TO_LOCAL[original] * inverse(modelRotation) * LOCAL_TO_GLOBAL[rotated]
     */
    private static Matrix4f getUVLockTransform(Matrix4f modelRotation, int originalFaceIndex) {
        // Find where this face ends up after rotation
        int rotatedFaceIndex = rotateFaceDirection(modelRotation, originalFaceIndex);
        
        // Compute inverse of model rotation
        Matrix4f inverseRotation = new Matrix4f(modelRotation).invert();
        if (!inverseRotation.isFinite()) {
            // Log warning and fallback to identity if inverse fails (indicates numerical issues)
            LOGGER.warn("UV lock transform: matrix inverse is not finite, falling back to identity");
            return new Matrix4f();
        }
        
        // Compose: GLOBAL_TO_LOCAL[original] * inverse * LOCAL_TO_GLOBAL[rotated]
        Matrix4f result = new Matrix4f(GLOBAL_TO_LOCAL[originalFaceIndex]);
        result.mul(inverseRotation);
        result.mul(LOCAL_TO_GLOBAL[rotatedFaceIndex]);
        
        // Apply blockCenterToCorner transform (from Minecraft's BlockMath)
        Matrix4f centered = new Matrix4f().translation(0.5f, 0.5f, 0.5f);
        centered.mul(result);
        centered.translate(-0.5f, -0.5f, -0.5f);
        
        return centered;
    }
    
    /**
     * Rotate a face direction through a matrix.
     * This is an EXACT port of Minecraft's Direction.rotate(Matrix4f, Direction).
     */
    private static int rotateFaceDirection(Matrix4f matrix, int faceIndex) {
        int[] normal = DIRECTION_NORMALS[faceIndex];
        Vector4f transformed = matrix.transform(new Vector4f(normal[0], normal[1], normal[2], 0.0f));
        return getNearest(transformed.x(), transformed.y(), transformed.z());
    }
    
    /**
     * Get the nearest direction for a vector.
     * This is an EXACT port of Minecraft's Direction.getNearest().
     */
    private static int getNearest(float x, float y, float z) {
        int nearest = NORTH;
        float maxDot = Float.MIN_VALUE;
        
        for (int i = 0; i < 6; i++) {
            int[] normal = DIRECTION_NORMALS[i];
            float dot = x * normal[0] + y * normal[1] + z * normal[2];
            if (dot > maxDot) {
                maxDot = dot;
                nearest = i;
            }
        }
        
        return nearest;
    }
    
    /**
     * Apply UV lock transformation to UV coordinates.
     * This is an EXACT port of Minecraft's FaceBakery.recomputeUVs().
     */
    private static UVLockResult applyUVLockTransform(float[] uv, Matrix4f matrix, int originalRotation) {
        // Get UV coordinates for corners 0 and 2 (using reverse index like Minecraft)
        // getReverseIndex(0) and getReverseIndex(2) account for existing rotation
        int idx0 = getReverseIndex(0, originalRotation);
        int idx2 = getReverseIndex(2, originalRotation);
        
        float u0 = getUForIndex(uv, idx0);
        float v0 = getVForIndex(uv, idx0);
        float u2 = getUForIndex(uv, idx2);
        float v2 = getVForIndex(uv, idx2);
        
        // Transform both corners through the matrix
        Vector4f corner0 = matrix.transform(new Vector4f(u0 / 16.0f, v0 / 16.0f, 0.0f, 1.0f));
        Vector4f corner2 = matrix.transform(new Vector4f(u2 / 16.0f, v2 / 16.0f, 0.0f, 1.0f));
        
        float newU0 = 16.0f * corner0.x();
        float newV0 = 16.0f * corner0.y();
        float newU2 = 16.0f * corner2.x();
        float newV2 = 16.0f * corner2.y();
        
        // Determine final UV bounds, handling potential flips
        float finalU0, finalU1;
        if (Math.signum(u2 - u0) == Math.signum(newU2 - newU0)) {
            finalU0 = newU0;
            finalU1 = newU2;
        } else {
            finalU0 = newU2;
            finalU1 = newU0;
        }
        
        float finalV0, finalV1;
        if (Math.signum(v2 - v0) == Math.signum(newV2 - newV0)) {
            finalV0 = newV0;
            finalV1 = newV2;
        } else {
            finalV0 = newV2;
            finalV1 = newV0;
        }
        
        // Calculate new rotation by transforming a direction vector through the matrix
        // This matches Minecraft's FaceBakery.recomputeUVs() rotation calculation
        float rotRadians = (float)Math.toRadians(originalRotation);
        Matrix3f rotMatrix = new Matrix3f(matrix);
        Vector3f rotVector = rotMatrix.transform(new Vector3f((float)Math.cos(rotRadians), (float)Math.sin(rotRadians), 0.0f));
        
        // Calculate the angle of the transformed vector
        double transformedAngleRadians = Math.atan2(rotVector.y(), rotVector.x());
        double transformedAngleDegrees = Math.toDegrees(transformedAngleRadians);
        
        // Quantize to 90-degree increments and negate (Minecraft convention)
        int quantizedSteps = (int)Math.round(transformedAngleDegrees / 90.0);
        int newRotation = Math.floorMod(-quantizedSteps * 90, 360);
        
        return new UVLockResult(new float[]{finalU0, finalV0, finalU1, finalV1}, newRotation);
    }
    
    /**
     * Get reverse index for UV rotation (from Minecraft's BlockFaceUV.getReverseIndex).
     */
    private static int getReverseIndex(int index, int rotation) {
        return (index + 4 - rotation / 90) % 4;
    }
    
    /**
     * Get U coordinate for a vertex index (from Minecraft's BlockFaceUV.getU).
     * uvs format: [u0, v0, u1, v1]
     */
    private static float getUForIndex(float[] uvs, int index) {
        // index 0 or 1 -> uvs[0], index 2 or 3 -> uvs[2]
        return uvs[(index != 0 && index != 1) ? 2 : 0];
    }
    
    /**
     * Get V coordinate for a vertex index (from Minecraft's BlockFaceUV.getV).
     * uvs format: [u0, v0, u1, v1]
     */
    private static float getVForIndex(float[] uvs, int index) {
        // index 0 or 3 -> uvs[1], index 1 or 2 -> uvs[3]
        return uvs[(index != 0 && index != 3) ? 3 : 1];
    }
    
    /**
     * Convert face direction string to index.
     */
    private static int getDirectionIndex(String direction) {
        return switch (direction.toLowerCase()) {
            case "down" -> DOWN;
            case "up" -> UP;
            case "north" -> NORTH;
            case "south" -> SOUTH;
            case "west" -> WEST;
            case "east" -> EAST;
            default -> NORTH;
        };
    }
    
    /**
     * Legacy method for backward compatibility.
     * Transform UV coordinates when uvlock=true (Y-rotation only).
     * 
     * @deprecated since 1.0. Use {@link #transformUVsWithUVLock} instead for proper X+Y rotation handling.
     *             This method will be removed in a future version.
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public static float[] transformUVsForRotation(float[] uv, String faceDirection, int yRotation) {
        UVLockResult result = transformUVsWithUVLock(uv, faceDirection, 0, yRotation, 0);
        return result.uv;
    }
    
    /**
     * Apply per-face UV rotation by shifting which vertex gets which UV coordinate.
     * This follows Minecraft's BlockFaceUV logic where rotation shifts the vertex index.
     * Rotation is applied counter-clockwise: 0°, 90°, 180°, 270°.
     * 
     * @param u0 Minimum U coordinate in atlas space
     * @param v0 Minimum V coordinate in atlas space
     * @param u1 Maximum U coordinate in atlas space
     * @param v1 Maximum V coordinate in atlas space
     * @param faceRotation Rotation in degrees (0, 90, 180, or 270)
     * @return Array of 4 UV coordinate pairs [[u,v], [u,v], [u,v], [u,v]] for vertices 0-3
     */
    public static float[][] getRotatedUVCoordinates(float u0, float v0, float u1, float v1, int faceRotation) {
        // Normalize rotation to 0-359
        faceRotation = ((faceRotation % 360) + 360) % 360;
        
        // Create UV coordinates for the 4 vertices
        // Vertex order follows Minecraft's FaceInfo pattern:
        // 0: top-left, 1: bottom-left, 2: bottom-right, 3: top-right
        float[][] uvCoords = new float[4][2];
        uvCoords[0] = new float[]{u0, v0};  // top-left
        uvCoords[1] = new float[]{u0, v1};  // bottom-left
        uvCoords[2] = new float[]{u1, v1};  // bottom-right
        uvCoords[3] = new float[]{u1, v0};  // top-right
        
        // Shift UV assignment based on rotation (in 90-degree increments)
        int rotationSteps = (faceRotation / 90) % 4;
        
        // Create output array with rotated UV assignment
        float[][] rotatedUVs = new float[4][2];
        for (int i = 0; i < 4; i++) {
            rotatedUVs[i] = uvCoords[(i + rotationSteps) % 4];
        }
        
        return rotatedUVs;
    }
}
