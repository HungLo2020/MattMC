package mattmc.client.renderer.shadow;

import mattmc.client.renderer.Shader;
import mattmc.client.renderer.ShaderLoader;

/**
 * Shader program for rendering depth to shadow maps.
 * Simple shader that only transforms vertices to light space.
 */
public class ShadowDepthShader extends Shader {
    
    public ShadowDepthShader() {
        super(
            ShaderLoader.loadShader("shadow_depth.vs"),
            ShaderLoader.loadShader("shadow_depth.fs")
        );
    }
}
