package net.minecraft.client.renderer.shaders.shadows;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Utility class for shadow matrix calculations.
 * Provides IRIS-compatible shadow transformation matrices.
 */
public class ShadowMatrices {
    private final Matrix4f projectionMatrix;
    private final Matrix4f modelViewMatrix;
    private final Matrix4f shadowModelView;
    private final Matrix4f shadowProjection;
    
    private final float halfPlaneLength;
    private final float renderDistanceMultiplier;
    private final int resolution;
    
    /**
     * Creates shadow matrices with the given configuration.
     *
     * @param halfPlaneLength Half of the shadow map plane size
     * @param renderDistanceMultiplier Multiplier for shadow distance
     * @param resolution Shadow map resolution
     */
    public ShadowMatrices(float halfPlaneLength, float renderDistanceMultiplier, int resolution) {
        this.halfPlaneLength = halfPlaneLength;
        this.renderDistanceMultiplier = renderDistanceMultiplier;
        this.resolution = resolution;
        
        this.projectionMatrix = new Matrix4f();
        this.modelViewMatrix = new Matrix4f();
        this.shadowModelView = new Matrix4f();
        this.shadowProjection = new Matrix4f();
        
        initializeMatrices();
    }
    
    /**
     * Initializes the shadow matrices with default orthographic projection.
     */
    private void initializeMatrices() {
        // Shadow projection: orthographic projection for shadow mapping
        float shadowDistance = halfPlaneLength * renderDistanceMultiplier;
        shadowProjection.identity();
        shadowProjection.ortho(
            -halfPlaneLength, halfPlaneLength,  // left, right
            -halfPlaneLength, halfPlaneLength,  // bottom, top
            -shadowDistance, shadowDistance     // near, far
        );
        
        // Initialize model-view to identity
        shadowModelView.identity();
        modelViewMatrix.identity();
        projectionMatrix.set(shadowProjection);
    }
    
    /**
     * Updates shadow matrices based on sun/moon direction.
     *
     * @param sunAngle Current sun angle in radians
     * @param shadowAngle Shadow-specific angle offset
     */
    public void update(float sunAngle, float shadowAngle) {
        shadowModelView.identity();
        
        // Rotate to face light source (sun/moon direction)
        float totalAngle = sunAngle + shadowAngle;
        shadowModelView.rotateX(totalAngle);
        shadowModelView.rotateY((float) Math.PI);
        
        modelViewMatrix.set(shadowModelView);
    }
    
    /**
     * Updates shadow matrices with explicit light direction.
     *
     * @param lightDirection Direction vector of the light source
     */
    public void updateWithDirection(Vector3f lightDirection) {
        shadowModelView.identity();
        
        // Create view matrix looking from light direction
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f target = new Vector3f(0, 0, 0);
        Vector3f eye = new Vector3f(lightDirection).mul(-halfPlaneLength);
        
        shadowModelView.setLookAt(
            eye.x, eye.y, eye.z,
            target.x, target.y, target.z,
            up.x, up.y, up.z
        );
        
        modelViewMatrix.set(shadowModelView);
    }
    
    /**
     * Gets the shadow projection matrix.
     *
     * @return Projection matrix for shadow rendering
     */
    public Matrix4f getProjectionMatrix() {
        return new Matrix4f(projectionMatrix);
    }
    
    /**
     * Gets the shadow model-view matrix.
     *
     * @return Model-view matrix for shadow rendering
     */
    public Matrix4f getModelViewMatrix() {
        return new Matrix4f(modelViewMatrix);
    }
    
    /**
     * Gets the combined shadow transformation matrix.
     *
     * @return Combined projection * modelView matrix
     */
    public Matrix4f getShadowMatrix() {
        Matrix4f combined = new Matrix4f(projectionMatrix);
        combined.mul(modelViewMatrix);
        return combined;
    }
    
    /**
     * Transforms a world-space position to shadow-space.
     *
     * @param worldPos World-space position
     * @return Shadow-space position
     */
    public Vector4f worldToShadowSpace(Vector3f worldPos) {
        Vector4f shadowPos = new Vector4f(worldPos.x, worldPos.y, worldPos.z, 1.0f);
        getShadowMatrix().transform(shadowPos);
        return shadowPos;
    }
    
    /**
     * Gets the half plane length (shadow map extent).
     *
     * @return Half plane length in blocks
     */
    public float getHalfPlaneLength() {
        return halfPlaneLength;
    }
    
    /**
     * Gets the shadow render distance multiplier.
     *
     * @return Distance multiplier
     */
    public float getRenderDistanceMultiplier() {
        return renderDistanceMultiplier;
    }
    
    /**
     * Gets the shadow map resolution.
     *
     * @return Resolution in pixels
     */
    public int getResolution() {
        return resolution;
    }
}
