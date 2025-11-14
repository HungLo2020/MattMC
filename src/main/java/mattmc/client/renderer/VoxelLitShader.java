package mattmc.client.renderer;

/**
 * Shader program for rendering voxel chunks with per-vertex lighting and ambient occlusion.
 * Supports gamma-corrected lighting with configurable gamma curve, emissive boost, and AO strength.
 */
public class VoxelLitShader extends Shader {
	
	// Default lighting parameters
	private static final float DEFAULT_LIGHT_GAMMA = 1.4f;
	private static final float DEFAULT_EMISSIVE_BOOST = 1.0f;
	private static final float DEFAULT_AO_STRENGTH = 0.8f;
	
	private float lightGamma = DEFAULT_LIGHT_GAMMA;
	private float emissiveBoost = DEFAULT_EMISSIVE_BOOST;
	private float aoStrength = DEFAULT_AO_STRENGTH;
	
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
	 * Set the texture sampler unit (typically 0).
	 */
	public void setTextureSampler(int unit) {
		setUniform1i("uTexture", unit);
	}
	
	/**
	 * Set the gamma exponent for the light curve.
	 * Higher values create more contrast, lower values create flatter lighting.
	 * 
	 * @param gamma Gamma exponent (typically 1.0-2.0, default 1.4)
	 */
	public void setLightGamma(float gamma) {
		this.lightGamma = gamma;
		setUniform1f("uLightGamma", gamma);
	}
	
	/**
	 * Set the brightness boost for emissive textures.
	 * 
	 * @param boost Brightness multiplier (default 1.0)
	 */
	public void setEmissiveBoost(float boost) {
		this.emissiveBoost = boost;
		setUniform1f("uEmissiveBoost", boost);
	}
	
	/**
	 * Set the ambient occlusion strength.
	 * 
	 * @param strength AO strength (0.0=off, 1.0=full, default 0.8)
	 */
	public void setAOStrength(float strength) {
		this.aoStrength = strength;
		setUniform1f("uAOStrength", strength);
	}
	
	/**
	 * Get the current gamma exponent.
	 */
	public float getLightGamma() {
		return lightGamma;
	}
	
	/**
	 * Get the current emissive boost.
	 */
	public float getEmissiveBoost() {
		return emissiveBoost;
	}
	
	/**
	 * Get the current AO strength.
	 */
	public float getAOStrength() {
		return aoStrength;
	}
	
	/**
	 * Apply default lighting parameters.
	 * Should be called after use() to ensure uniforms are set.
	 */
	public void applyDefaultLighting() {
		setLightGamma(DEFAULT_LIGHT_GAMMA);
		setEmissiveBoost(DEFAULT_EMISSIVE_BOOST);
		setAOStrength(DEFAULT_AO_STRENGTH);
	}
}
