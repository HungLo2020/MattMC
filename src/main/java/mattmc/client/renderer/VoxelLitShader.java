package mattmc.client.renderer;

/**
 * Shader program for rendering lit voxel chunks.
 * Implements:
 * - Directional sun lighting (Lambert N·L)
 * - Basic shadow mapping based on sun position
 * - Distance fog (exponential squared)
 * - Gamma correction
 */
public class VoxelLitShader extends Shader {
    
    /**
     * Create and compile the voxel lit shader program.
     */
    public VoxelLitShader() {
        super(
            ShaderLoader.loadShader("voxel_lit.vs"),
            ShaderLoader.loadShader("voxel_lit.fs")
        );
    }
    
    /**
     * Set the camera position for fog calculations.
     */
    public void setCameraPosition(float x, float y, float z) {
        setUniform3f("uCameraPos", x, y, z);
    }
    
    /**
     * Set the sun direction (should be normalized).
     */
    public void setSunDirection(float x, float y, float z) {
        setUniform3f("uSunDir", x, y, z);
    }
    
    /**
     * Set the sun color (in linear space).
     */
    public void setSunColor(float r, float g, float b) {
        setUniform3f("uSunColor", r, g, b);
    }
    
    /**
     * Set the ambient sky light color (in linear space).
     */
    public void setAmbientSky(float r, float g, float b) {
        setUniform3f("uAmbientSky", r, g, b);
    }
    
    /**
     * Set the ambient block light color (in linear space, typically orange/yellow).
     */
    public void setAmbientBlock(float r, float g, float b) {
        setUniform3f("uAmbientBlock", r, g, b);
    }
    
    /**
     * Set the gamma correction value (typically 2.2 for sRGB).
     */
    public void setGamma(float gamma) {
        setUniform1f("uGamma", gamma);
    }
    
    /**
     * Set the fog color (in linear space).
     */
    public void setFogColor(float r, float g, float b) {
        setUniform3f("uFogColor", r, g, b);
    }
    
    /**
     * Set the texture sampler unit (typically 0).
     */
    public void setTextureSampler(int unit) {
        setUniform1i("uTexture", unit);
    }
    
    /**
     * Set the sky brightness multiplier (0.0 to 1.0).
     * This dims ambient sky lighting during night time.
     */
    public void setSkyBrightness(float brightness) {
        setUniform1f("uSkyBrightness", brightness);
    }
    
    /**
     * Enable or disable shadows.
     */
    public void setShadowsEnabled(boolean enabled) {
        setUniform1i("uShadowsEnabled", enabled ? 1 : 0);
    }
    
    /**
     * Set the shadow matrix for transforming world coords to shadow map space.
     */
    public void setShadowMatrix(float[] matrix) {
        setUniformMatrix4f("uShadowMatrix", matrix);
    }
    
    /**
     * Set the shadow map sampler unit.
     */
    public void setShadowMapSampler(int unit) {
        setUniform1i("uShadowMap", unit);
    }
}
