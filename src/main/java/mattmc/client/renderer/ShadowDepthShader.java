package mattmc.client.renderer;

/**
 * Shader program for rendering depth to shadow map.
 * Simple depth-only rendering from the light's perspective.
 */
public class ShadowDepthShader extends Shader {
    
    /**
     * Create and compile the shadow depth shader program.
     */
    public ShadowDepthShader() {
        super(
            ShaderLoader.loadShader("shadow_depth.vs"),
            ShaderLoader.loadShader("shadow_depth.fs")
        );
    }
}
