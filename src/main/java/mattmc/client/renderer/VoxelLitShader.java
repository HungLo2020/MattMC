package mattmc.client.renderer;

import mattmc.client.renderer.shadow.ShadowCascade;

/**
 * Shader program for rendering lit voxel chunks.
 * Implements:
 * - Smooth lighting with sky and block light
 * - Ambient occlusion
 * - Directional sun lighting (Lambert N·L)
 * - Cascaded shadow maps with PCF filtering
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
     * Set shadow map sampler unit.
     */
    public void setShadowMapSampler(int unit) {
        setUniform1i("uShadowMap", unit);
    }
    
    /**
     * Enable or disable shadow rendering.
     */
    public void setShadowsEnabled(boolean enabled) {
        setUniform1i("uShadowsEnabled", enabled ? 1 : 0);
    }
    
    /**
     * Set the number of active shadow cascades.
     */
    public void setNumCascades(int numCascades) {
        setUniform1i("uNumCascades", numCascades);
    }
    
    /**
     * Set cascade split distances.
     */
    public void setCascadeSplits(float[] splits) {
        if (splits.length >= 4) {
            setUniform4f("uCascadeSplits", splits[0], splits[1], splits[2], splits[3]);
        }
    }
    
    /**
     * Set light view-projection matrices for all cascades.
     */
    public void setLightViewProjMatrices(ShadowCascade[] cascades) {
        for (int i = 0; i < cascades.length && i < 4; i++) {
            String uniformName = "uLightViewProj[" + i + "]";
            setUniformMatrix4fv(uniformName, false, cascades[i].getLightViewProjectionMatrix());
        }
    }
}
