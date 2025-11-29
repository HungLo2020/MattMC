package mattmc.client.renderer;

import mattmc.client.renderer.shader.ShaderProgram;

/**
 * Backend-agnostic shader configuration for rendering voxel chunks with per-vertex lighting.
 * 
 * <p>This class wraps a ShaderProgram and provides game-specific configuration for voxel
 * rendering with Minecraft-style lighting including:
 * <ul>
 *   <li>Minecraft's brightness curve (f / (4 - 3f)) instead of simple gamma</li>
 *   <li>Ambient occlusion applied in shader</li>
 *   <li>Directional face shading (UP=1.0, DOWN=0.5, N/S=0.8, E/W=0.6)</li>
 *   <li>Sky darkness for day/night cycle</li>
 *   <li>Darker minimum brightness (0.04 instead of 0.25) for cave atmosphere</li>
 * </ul>
 * 
 * <p><b>Architecture:</b> This class lives outside backend/ and uses the ShaderProgram
 * interface, making it independent of OpenGL-specific implementations.
 */
public class VoxelLitShader implements AutoCloseable {

// Default lighting parameters
private static final float DEFAULT_LIGHT_GAMMA = 1.4f;
private static final float DEFAULT_EMISSIVE_BOOST = 1.0f;
private static final float DEFAULT_SKY_DARKNESS = 1.0f;

private final ShaderProgram shader;
private float lightGamma = DEFAULT_LIGHT_GAMMA;
private float emissiveBoost = DEFAULT_EMISSIVE_BOOST;
private float skyDarkness = DEFAULT_SKY_DARKNESS;

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
 * Note: The shader now uses Minecraft's brightness curve (f/(4-3f)) 
 * instead of simple pow(). This parameter is kept for backwards compatibility
 * but is no longer used by the shader.
 * 
 * @param gamma Gamma exponent (typically 1.0-2.0, default 1.4)
 * @deprecated The shader now uses Minecraft's brightness curve instead of gamma.
 *             This method is retained for API compatibility but has no effect.
 */
@Deprecated
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
 * Set the sky darkness factor for day/night cycle.
 * This multiplies with sky light to create darker nights like Minecraft.
 * 
 * @param darkness Sky brightness multiplier (0.0 = pitch black night, 1.0 = full day)
 */
public void setSkyDarkness(float darkness) {
this.skyDarkness = darkness;
shader.setUniform1f("uSkyDarkness", darkness);
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
 * Get the current sky darkness.
 * 
 * @return current sky darkness value (0.0-1.0)
 */
public float getSkyDarkness() {
return skyDarkness;
}

/**
 * Apply default lighting parameters.
 * Should be called after use() to ensure uniforms are set.
 */
public void applyDefaultLighting() {
setLightGamma(DEFAULT_LIGHT_GAMMA);
setEmissiveBoost(DEFAULT_EMISSIVE_BOOST);
setSkyDarkness(DEFAULT_SKY_DARKNESS);
}

@Override
public void close() {
shader.close();
}
}
