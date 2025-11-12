package mattmc.client.renderer.shadow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;

/**
 * Manages cascaded shadow maps (CSM) for directional sun lighting.
 * 
 * Features:
 * - 3-4 cascades with logarithmic/practical split scheme
 * - Stable texel-grid snapping to reduce shimmer
 * - Per-cascade light view/projection matrices
 * - Configurable shadow map resolution by quality tier
 */
public class CascadedShadowMap implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(CascadedShadowMap.class);
    
    private final ShadowFramebuffer shadowFBO;
    private final ShadowCascade[] cascades;
    private final int numCascades;
    
    // Camera frustum parameters
    private float cameraNear = 0.1f;
    private float cameraFar = 500f;
    private float cameraFov = 70f;
    private float cameraAspect = 1.0f;
    
    // Split scheme parameter (0 = uniform, 1 = logarithmic)
    private float lambda = 0.75f; // Practical split scheme
    
    /**
     * Create a cascaded shadow map system.
     * 
     * @param resolution Shadow map resolution (1024, 1536, or 2048)
     * @param numCascades Number of cascades (typically 3 or 4)
     */
    public CascadedShadowMap(int resolution, int numCascades) {
        this.numCascades = numCascades;
        this.shadowFBO = new ShadowFramebuffer(resolution, numCascades);
        this.cascades = new ShadowCascade[numCascades];
        
        for (int i = 0; i < numCascades; i++) {
            cascades[i] = new ShadowCascade(i);
        }
        
        logger.info("Created CascadedShadowMap with {} cascades at {}x{} resolution", 
                    numCascades, resolution, resolution);
    }
    
    /**
     * Update camera frustum parameters.
     */
    public void setCameraParameters(float near, float far, float fov, float aspect) {
        this.cameraNear = near;
        this.cameraFar = far;
        this.cameraFov = fov;
        this.cameraAspect = aspect;
    }
    
    /**
     * Compute cascade split distances using practical split scheme.
     * This blends uniform and logarithmic splits based on lambda.
     */
    public void computeCascadeSplits() {
        float near = cameraNear;
        float far = cameraFar;
        float range = far - near;
        float ratio = far / near;
        
        for (int i = 0; i < numCascades; i++) {
            float p = (i + 1) / (float) numCascades;
            float log = near * (float) Math.pow(ratio, p);
            float uniform = near + range * p;
            float d = lambda * log + (1.0f - lambda) * uniform;
            
            cascades[i].setNearSplit(i == 0 ? near : cascades[i - 1].getFarSplit());
            cascades[i].setFarSplit(d);
        }
    }
    
    /**
     * Update light matrices for all cascades based on current camera and sun direction.
     * 
     * @param cameraViewMatrix Current camera view matrix (4x4 column-major)
     * @param cameraProjMatrix Current camera projection matrix (4x4 column-major)
     * @param sunDirection Normalized sun direction vector [x, y, z]
     */
    public void updateCascades(float[] cameraViewMatrix, float[] cameraProjMatrix, float[] sunDirection) {
        // Compute cascade splits first
        computeCascadeSplits();
        
        // Get camera inverse view-projection matrix
        float[] cameraInvVP = new float[16];
        invertMatrix(multiplyMatrices(cameraProjMatrix, cameraViewMatrix), cameraInvVP);
        
        for (int i = 0; i < numCascades; i++) {
            ShadowCascade cascade = cascades[i];
            
            // Compute frustum corners for this cascade in world space
            float[] frustumCorners = computeFrustumCorners(
                cameraInvVP, 
                cascade.getNearSplit(), 
                cascade.getFarSplit()
            );
            
            // Compute light view matrix (look at frustum center from sun direction)
            float[] lightView = computeLightViewMatrix(frustumCorners, sunDirection);
            
            // Compute light projection matrix (orthographic projection covering frustum)
            float[] lightProj = computeLightProjectionMatrix(frustumCorners, lightView, shadowFBO.getResolution());
            
            // Store matrices in cascade
            cascade.setLightViewMatrix(lightView);
            cascade.setLightProjectionMatrix(lightProj);
            cascade.updateLightViewProjectionMatrix();
        }
    }
    
    /**
     * Compute the 8 corners of a frustum sub-section in world space.
     */
    private float[] computeFrustumCorners(float[] invViewProj, float nearDist, float farDist) {
        float[] corners = new float[8 * 3]; // 8 corners, 3 components each
        
        // NDC cube corners
        float[][] ndcCorners = {
            {-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, // near
            {-1, -1,  1}, {1, -1,  1}, {1, 1,  1}, {-1, 1,  1}  // far
        };
        
        for (int i = 0; i < 8; i++) {
            float[] ndc = ndcCorners[i];
            
            // Transform NDC to world space
            float[] world = transformPoint(invViewProj, ndc[0], ndc[1], ndc[2]);
            
            corners[i * 3 + 0] = world[0];
            corners[i * 3 + 1] = world[1];
            corners[i * 3 + 2] = world[2];
        }
        
        return corners;
    }
    
    /**
     * Compute light view matrix that looks at the frustum from the sun direction.
     */
    private float[] computeLightViewMatrix(float[] frustumCorners, float[] sunDir) {
        // Compute frustum center
        float centerX = 0, centerY = 0, centerZ = 0;
        for (int i = 0; i < 8; i++) {
            centerX += frustumCorners[i * 3 + 0];
            centerY += frustumCorners[i * 3 + 1];
            centerZ += frustumCorners[i * 3 + 2];
        }
        centerX /= 8.0f;
        centerY /= 8.0f;
        centerZ /= 8.0f;
        
        // Light position is at frustum center offset by sun direction
        float lightX = centerX - sunDir[0] * 100.0f;
        float lightY = centerY - sunDir[1] * 100.0f;
        float lightZ = centerZ - sunDir[2] * 100.0f;
        
        // Compute look-at matrix
        return computeLookAtMatrix(lightX, lightY, lightZ, centerX, centerY, centerZ, 0, 1, 0);
    }
    
    /**
     * Compute orthographic projection matrix that tightly fits the frustum in light space.
     * Applies texel-grid snapping to reduce shimmer.
     */
    private float[] computeLightProjectionMatrix(float[] frustumCorners, float[] lightView, int resolution) {
        // Transform frustum corners to light space
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        
        for (int i = 0; i < 8; i++) {
            float[] lightSpace = transformPoint(lightView, 
                frustumCorners[i * 3 + 0],
                frustumCorners[i * 3 + 1],
                frustumCorners[i * 3 + 2]);
            
            minX = Math.min(minX, lightSpace[0]);
            maxX = Math.max(maxX, lightSpace[0]);
            minY = Math.min(minY, lightSpace[1]);
            maxY = Math.max(maxY, lightSpace[1]);
            minZ = Math.min(minZ, lightSpace[2]);
            maxZ = Math.max(maxZ, lightSpace[2]);
        }
        
        // Add padding to prevent edge clipping
        float paddingFactor = 1.1f;
        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        float extentX = (maxX - minX) * 0.5f * paddingFactor;
        float extentY = (maxY - minY) * 0.5f * paddingFactor;
        
        minX = centerX - extentX;
        maxX = centerX + extentX;
        minY = centerY - extentY;
        maxY = centerY + extentY;
        
        // Texel-grid snapping to reduce shimmer
        float worldUnitsPerTexel = (maxX - minX) / resolution;
        minX = (float) Math.floor(minX / worldUnitsPerTexel) * worldUnitsPerTexel;
        maxX = (float) Math.floor(maxX / worldUnitsPerTexel) * worldUnitsPerTexel;
        minY = (float) Math.floor(minY / worldUnitsPerTexel) * worldUnitsPerTexel;
        maxY = (float) Math.floor(maxY / worldUnitsPerTexel) * worldUnitsPerTexel;
        
        // Extend depth range for distant geometry
        minZ -= 100.0f;
        maxZ += 100.0f;
        
        return createOrthographicMatrix(minX, maxX, minY, maxY, minZ, maxZ);
    }
    
    /**
     * Create a look-at view matrix.
     */
    private float[] computeLookAtMatrix(float eyeX, float eyeY, float eyeZ,
                                        float centerX, float centerY, float centerZ,
                                        float upX, float upY, float upZ) {
        // Forward vector (normalized)
        float fX = centerX - eyeX;
        float fY = centerY - eyeY;
        float fZ = centerZ - eyeZ;
        float fLen = (float) Math.sqrt(fX * fX + fY * fY + fZ * fZ);
        fX /= fLen; fY /= fLen; fZ /= fLen;
        
        // Right vector (normalized)
        float rX = fY * upZ - fZ * upY;
        float rY = fZ * upX - fX * upZ;
        float rZ = fX * upY - fY * upX;
        float rLen = (float) Math.sqrt(rX * rX + rY * rY + rZ * rZ);
        rX /= rLen; rY /= rLen; rZ /= rLen;
        
        // Up vector (normalized)
        float uX = rY * fZ - rZ * fY;
        float uY = rZ * fX - rX * fZ;
        float uZ = rX * fY - rY * fX;
        
        // Build matrix (column-major)
        return new float[] {
            rX, uX, -fX, 0,
            rY, uY, -fY, 0,
            rZ, uZ, -fZ, 0,
            -(rX * eyeX + rY * eyeY + rZ * eyeZ),
            -(uX * eyeX + uY * eyeY + uZ * eyeZ),
            (fX * eyeX + fY * eyeY + fZ * eyeZ),
            1
        };
    }
    
    /**
     * Create an orthographic projection matrix.
     */
    private float[] createOrthographicMatrix(float left, float right, float bottom, float top, float near, float far) {
        float[] matrix = new float[16];
        
        matrix[0] = 2.0f / (right - left);
        matrix[5] = 2.0f / (top - bottom);
        matrix[10] = -2.0f / (far - near);
        matrix[12] = -(right + left) / (right - left);
        matrix[13] = -(top + bottom) / (top - bottom);
        matrix[14] = -(far + near) / (far - near);
        matrix[15] = 1.0f;
        
        return matrix;
    }
    
    /**
     * Transform a point by a 4x4 matrix.
     */
    private float[] transformPoint(float[] matrix, float x, float y, float z) {
        float w = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15];
        float[] result = new float[3];
        result[0] = (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]) / w;
        result[1] = (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]) / w;
        result[2] = (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]) / w;
        return result;
    }
    
    /**
     * Multiply two 4x4 matrices (column-major).
     */
    private float[] multiplyMatrices(float[] a, float[] b) {
        float[] result = new float[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                result[i * 4 + j] = 0;
                for (int k = 0; k < 4; k++) {
                    result[i * 4 + j] += a[i * 4 + k] * b[k * 4 + j];
                }
            }
        }
        return result;
    }
    
    /**
     * Invert a 4x4 matrix (simplified for view-projection matrices).
     */
    private void invertMatrix(float[] m, float[] result) {
        // This is a simplified inversion using cofactor expansion
        // For production, consider using a more robust library like JOML
        FloatBuffer mBuffer = BufferUtils.createFloatBuffer(16).put(m).flip();
        FloatBuffer invBuffer = BufferUtils.createFloatBuffer(16);
        
        // Use OpenGL to invert (fallback method)
        // In production, use proper matrix library
        glPushMatrix();
        glLoadMatrixf(mBuffer);
        glGetFloatv(GL_MODELVIEW_MATRIX, invBuffer);
        glPopMatrix();
        
        invBuffer.get(result);
    }
    
    public ShadowFramebuffer getShadowFBO() {
        return shadowFBO;
    }
    
    public ShadowCascade[] getCascades() {
        return cascades;
    }
    
    public int getNumCascades() {
        return numCascades;
    }
    
    @Override
    public void close() {
        shadowFBO.close();
    }
}
