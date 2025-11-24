package mattmc.client.renderer;

import mattmc.client.renderer.shader.ShaderProgram;

/**
 * Backend-agnostic shader configuration for rendering voxel chunks with per-vertex lighting.
 * 
 * <p>This class wraps a ShaderProgram and provides game-specific configuration for voxel
 * rendering with gamma-corrected lighting and emissive boost.
 * 
 * <p><b>Architecture:</b> This class lives outside backend/ and uses the ShaderProgram
 * interface, making it independent of OpenGL-specific implementations.
 */
public class VoxelLitShader implements AutoCloseable {

// Default lighting parameters
private static final float DEFAULT_LIGHT_GAMMA = 1.4f;
private static final float DEFAULT_EMISSIVE_BOOST = 1.0f;

private final ShaderProgram shader;
private float lightGamma = DEFAULT_LIGHT_GAMMA;
private float emissiveBoost = DEFAULT_EMISSIVE_BOOST;

/**
 * Create voxel lit shader with the provided shader program implementation.
 * 
 * @param shader the backend-specific shader program implementation
 */
public VoxelLitShader(ShaderProgram shader) {
this.shader = shader;
}

/**
 * Activate this shader for rendering.
 */
public void use() {
shader.use();
}

/**
 * Set the texture sampler unit (typically 0).
 * 
 * @param unit texture unit
 */
public void setTextureSampler(int unit) {
shader.setUniform1i("uTexture", unit);
}

/**
 * Set the gamma exponent for the light curve.
 * Higher values create more contrast, lower values create flatter lighting.
 * 
 * @param gamma Gamma exponent (typically 1.0-2.0, default 1.4)
 */
public void setLightGamma(float gamma) {
this.lightGamma = gamma;
shader.setUniform1f("uLightGamma", gamma);
}

/**
 * Set the brightness boost for emissive textures.
 * 
 * @param boost Brightness multiplier (default 1.0)
 */
public void setEmissiveBoost(float boost) {
this.emissiveBoost = boost;
shader.setUniform1f("uEmissiveBoost", boost);
}

/**
 * Get the current gamma exponent.
 * 
 * @return current gamma value
 */
public float getLightGamma() {
return lightGamma;
}

/**
 * Get the current emissive boost.
 * 
 * @return current emissive boost value
 */
public float getEmissiveBoost() {
return emissiveBoost;
}

/**
 * Apply default lighting parameters.
 * Should be called after use() to ensure uniforms are set.
 */
public void applyDefaultLighting() {
setLightGamma(DEFAULT_LIGHT_GAMMA);
setEmissiveBoost(DEFAULT_EMISSIVE_BOOST);
}

/**
 * Get the underlying shader program.
 * This allows access to additional shader operations if needed.
 * 
 * @return the shader program
 */
public ShaderProgram getShaderProgram() {
return shader;
}

@Override
public void close() {
shader.close();
}
}
