package mattmc.client.renderer.shadow;

/**
 * Represents a single cascade in a cascaded shadow map system.
 * Contains the light-space transformation matrix and split distance information.
 */
public class ShadowCascade {
    private final int index;
    private float nearSplit;
    private float farSplit;
    private final float[] lightViewMatrix = new float[16];
    private final float[] lightProjectionMatrix = new float[16];
    private final float[] lightViewProjectionMatrix = new float[16];
    
    public ShadowCascade(int index) {
        this.index = index;
    }
    
    public int getIndex() {
        return index;
    }
    
    public float getNearSplit() {
        return nearSplit;
    }
    
    public void setNearSplit(float nearSplit) {
        this.nearSplit = nearSplit;
    }
    
    public float getFarSplit() {
        return farSplit;
    }
    
    public void setFarSplit(float farSplit) {
        this.farSplit = farSplit;
    }
    
    public float[] getLightViewMatrix() {
        return lightViewMatrix;
    }
    
    public float[] getLightProjectionMatrix() {
        return lightProjectionMatrix;
    }
    
    public float[] getLightViewProjectionMatrix() {
        return lightViewProjectionMatrix;
    }
    
    /**
     * Set the light view matrix (4x4 column-major).
     */
    public void setLightViewMatrix(float[] matrix) {
        System.arraycopy(matrix, 0, lightViewMatrix, 0, 16);
    }
    
    /**
     * Set the light projection matrix (4x4 column-major).
     */
    public void setLightProjectionMatrix(float[] matrix) {
        System.arraycopy(matrix, 0, lightProjectionMatrix, 0, 16);
    }
    
    /**
     * Compute and store the combined light view-projection matrix.
     */
    public void updateLightViewProjectionMatrix() {
        // Multiply projection * view (column-major order)
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                lightViewProjectionMatrix[i * 4 + j] = 0;
                for (int k = 0; k < 4; k++) {
                    lightViewProjectionMatrix[i * 4 + j] += 
                        lightProjectionMatrix[i * 4 + k] * lightViewMatrix[k * 4 + j];
                }
            }
        }
    }
}
