package mattmc.client.renderer.chunk;

/**
 * Handles UV coordinate transformations for block models.
 * Provides uvlock transformation to keep textures world-aligned when geometry rotates,
 * and per-face UV rotation as specified in model JSON files.
 * 
 * This is separated from ModelElementRenderer to isolate UV transformation logic,
 * following the pattern in MattMC's BlockFaceUV and FaceBakery classes.
 */
public class UVTransformer {
    
    /**
     * Transform UV coordinates when uvlock=true to keep textures world-aligned.
     * When the block geometry rotates, UVs must be transformed so textures stay horizontal.
     * 
     * Based on MattMC's FaceBakery.recomputeUVs which transforms UVs through BlockMath matrices.
     * We use a simplified approach: rotate the UV rectangle by the negative of the Y-rotation.
     * 
     * @param uv UV coordinates [u0, v0, u1, v1] in 0-16 space
     * @param faceDirection Face direction (before rotation)
     * @param yRotation Y-axis rotation applied to block geometry
     * @return Transformed UV coordinates
     */
    public static float[] transformUVsForRotation(float[] uv, String faceDirection, int yRotation) {
        // UV format: [u0, v0, u1, v1] where (u0,v0) is top-left and (u1,v1) is bottom-right in texture space
        float u0 = uv[0];
        float v0 = uv[1];
        float u1 = uv[2];
        float v1 = uv[3];
        
        // For vertical faces, rotate UV coordinates by -yRotation to counter the geometry rotation
        // This keeps the texture aligned with world axes
        int rotationSteps = (360 - yRotation) / 90; // Counter-rotation
        rotationSteps = rotationSteps % 4;
        
        // Rotate UV rectangle around center point (8, 8) in 0-16 space
        for (int i = 0; i < rotationSteps; i++) {
            // One 90° CCW rotation around (8, 8):
            // Point (u, v) -> (16-v, u)
            // After rotation, corners may swap so we need to find new min/max
            float rotU0 = 16 - v0;
            float rotU1 = 16 - v1;
            float rotV0 = u0;
            float rotV1 = u1;
            
            // Ensure u0 < u1 and v0 < v1
            u0 = Math.min(rotU0, rotU1);
            u1 = Math.max(rotU0, rotU1);
            v0 = Math.min(rotV0, rotV1);
            v1 = Math.max(rotV0, rotV1);
        }
        
        return new float[]{u0, v0, u1, v1};
    }
    
    /**
     * Apply per-face UV rotation by shifting which vertex gets which UV coordinate.
     * This follows MattMC's BlockFaceUV logic where rotation shifts the vertex index.
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
        // Create UV coordinates for the 4 vertices
        float[][] uvCoords = new float[4][2];
        uvCoords[0] = new float[]{u0, v0};
        uvCoords[1] = new float[]{u0, v1};
        uvCoords[2] = new float[]{u1, v1};
        uvCoords[3] = new float[]{u1, v0};
        
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
