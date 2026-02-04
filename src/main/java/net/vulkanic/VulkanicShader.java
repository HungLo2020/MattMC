package net.vulkanic;

/**
 * Represents a compiled shader program.
 * 
 * A shader consists of at minimum a vertex shader and fragment shader.
 * This interface abstracts shader compilation and management across different graphics APIs.
 */
public interface VulkanicShader {
    /**
     * Sets a uniform integer value.
     * 
     * @param name the uniform name
     * @param value the value to set
     */
    void setUniform(String name, int value);
    
    /**
     * Sets a uniform float value.
     * 
     * @param name the uniform name
     * @param value the value to set
     */
    void setUniform(String name, float value);
    
    /**
     * Sets a uniform vec2 value.
     * 
     * @param name the uniform name
     * @param x the x component
     * @param y the y component
     */
    void setUniform(String name, float x, float y);
    
    /**
     * Sets a uniform vec3 value.
     * 
     * @param name the uniform name
     * @param x the x component
     * @param y the y component
     * @param z the z component
     */
    void setUniform(String name, float x, float y, float z);
    
    /**
     * Sets a uniform vec4 value.
     * 
     * @param name the uniform name
     * @param x the x component
     * @param y the y component
     * @param z the z component
     * @param w the w component
     */
    void setUniform(String name, float x, float y, float z, float w);
    
    /**
     * Sets a uniform mat4 value.
     * 
     * @param name the uniform name
     * @param matrix the 4x4 matrix in column-major order
     */
    void setUniformMatrix4(String name, float[] matrix);
    
    /**
     * Releases resources associated with this shader.
     */
    void close();
}
