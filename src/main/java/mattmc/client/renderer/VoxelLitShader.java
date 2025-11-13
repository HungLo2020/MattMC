package mattmc.client.renderer;

/**
 * Shader program for rendering voxel chunks with basic directional lighting.
 * Implements simple Lambert diffuse lighting from the sun.
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
     * Set the texture sampler unit (typically 0).
     */
    public void setTextureSampler(int unit) {
        setUniform1i("uTexture", unit);
    }
    
    /**
     * Set the sun direction (should be normalized).
     */
    public void setSunDirection(float x, float y, float z) {
        setUniform3f("uSunDir", x, y, z);
    }
    
    /**
     * Set the sky brightness multiplier (0.0 to 1.0).
     * This dims lighting during night time.
     */
    public void setSkyBrightness(float brightness) {
        setUniform1f("uSkyBrightness", brightness);
    }
}
