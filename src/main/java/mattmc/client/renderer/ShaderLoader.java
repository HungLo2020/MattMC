package mattmc.client.renderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

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
        
        try (InputStream is = ShaderLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Shader file not found: " + resourcePath);
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader: " + resourcePath, e);
        }
    }
}
