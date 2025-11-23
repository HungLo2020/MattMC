package mattmc.client.renderer;

import mattmc.util.ResourceLoader;

/**
 * Utility class for loading shader source code from resources.
 */
public class ShaderLoader {
    
    /**
     * Load a shader source file from the resources/assets/shaders directory.
     * 
     * @param filename The shader filename (e.g., "voxel_lit.vs")
     * @return The shader source code as a string
     * @throws RuntimeException if the shader file cannot be loaded
     */
    public static String loadShader(String filename) {
        String resourcePath = "/assets/shaders/" + filename;
        String source = ResourceLoader.loadTextResource(resourcePath);
        
        if (source == null) {
            throw new RuntimeException("Shader file not found: " + resourcePath);
        }
        
        return source;
    }
}
