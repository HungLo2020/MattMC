package mattmc.client.renderer.shader;

/**
 * Backend-agnostic shader program interface.
 * 
 * <p>This interface abstracts away the details of specific graphics APIs (OpenGL, Vulkan, etc.)
 * and provides a common interface for shader programs. Game-specific shaders should use this
 * interface rather than depending directly on OpenGL Shader implementations.
 * 
 * <p><b>Architecture:</b> This allows shaders to be defined outside the backend/ directory
 * while the actual OpenGL/Vulkan shader implementation remains in the backend.
 * 
 * @see mattmc.client.renderer.backend.opengl.Shader OpenGL implementation
 */
public interface ShaderProgram extends AutoCloseable {
    
    /**
     * Activate this shader program for rendering.
     */
    void use();
    
    /**
     * Set a float uniform value.
     * 
     * @param name uniform variable name
     * @param value float value
     */
    void setUniform1f(String name, float value);
    
    /**
     * Set a vec2 uniform value.
     * 
     * @param name uniform variable name
     * @param v1 first component
     * @param v2 second component
     */
    void setUniform2f(String name, float v1, float v2);
    
    /**
     * Set an integer uniform value.
     * 
     * @param name uniform variable name
     * @param value integer value
     */
    void setUniform1i(String name, int value);
    
    /**
     * Set a vec3 uniform value.
     * 
     * @param name uniform variable name
     * @param v1 first component
     * @param v2 second component
     * @param v3 third component
     */
    void setUniform3f(String name, float v1, float v2, float v3);
    
    /**
     * Set a vec4 uniform value.
     * 
     * @param name uniform variable name
     * @param v1 first component
     * @param v2 second component
     * @param v3 third component
     * @param v4 fourth component
     */
    void setUniform4f(String name, float v1, float v2, float v3, float v4);
    
    /**
     * Set a mat4 uniform value.
     * 
     * @param name uniform variable name
     * @param matrix 16-element float array representing the matrix in column-major order
     */
    void setUniformMatrix4f(String name, float[] matrix);
    
    /**
     * Cleanup shader resources.
     * Should be called when the shader is no longer needed.
     */
    @Override
    void close();
}
