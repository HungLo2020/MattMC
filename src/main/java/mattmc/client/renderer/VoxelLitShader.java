package mattmc.client.renderer;

/**
 * Shader program for rendering voxel chunks with basic texturing.
 * Simplified version with no lighting or shadow effects.
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
}
