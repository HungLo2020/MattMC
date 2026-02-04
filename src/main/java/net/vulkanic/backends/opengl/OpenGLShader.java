package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicShader;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of VulkanicShader.
 * 
 * Compiles and manages GLSL shaders using direct OpenGL calls.
 * This is the ONLY place that should compile shaders using OpenGL.
 */
public class OpenGLShader implements VulkanicShader {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGLShader.class);
    
    private final int vertexShaderId;
    private final int fragmentShaderId;
    private final int programId;
    
    /**
     * Creates and compiles a shader program from vertex and fragment shader sources.
     * 
     * @param vertexShaderSource the vertex shader source code (GLSL)
     * @param fragmentShaderSource the fragment shader source code (GLSL)
     * @throws IllegalStateException if shader compilation or linking fails
     */
    public OpenGLShader(String vertexShaderSource, String fragmentShaderSource) {
        // Compile vertex shader
        this.vertexShaderId = compileShader(GL20.GL_VERTEX_SHADER, "vertex", vertexShaderSource);
        if (this.vertexShaderId == 0) {
            throw new IllegalStateException("Failed to compile vertex shader");
        }
        
        // Compile fragment shader
        this.fragmentShaderId = compileShader(GL20.GL_FRAGMENT_SHADER, "fragment", fragmentShaderSource);
        if (this.fragmentShaderId == 0) {
            GL20.glDeleteShader(this.vertexShaderId);
            throw new IllegalStateException("Failed to compile fragment shader");
        }
        
        // Link program
        this.programId = linkProgram(this.vertexShaderId, this.fragmentShaderId);
        if (this.programId == 0) {
            GL20.glDeleteShader(this.vertexShaderId);
            GL20.glDeleteShader(this.fragmentShaderId);
            throw new IllegalStateException("Failed to link shader program");
        }
    }
    
    /**
     * Compiles a shader from source.
     * 
     * @param type the shader type (GL_VERTEX_SHADER or GL_FRAGMENT_SHADER)
     * @param typeName the shader type name for logging
     * @param source the shader source code
     * @return the shader ID, or 0 if compilation failed
     */
    private int compileShader(int type, String typeName, String source) {
        int shaderId = GL20.glCreateShader(type);
        if (shaderId == 0) {
            LOGGER.error("Failed to create {} shader", typeName);
            return 0;
        }
        
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        
        int compileStatus = GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS);
        if (compileStatus == 0) {
            String log = GL20.glGetShaderInfoLog(shaderId, 32768);
            LOGGER.error("Failed to compile {} shader: {}", typeName, log);
            GL20.glDeleteShader(shaderId);
            return 0;
        }
        
        return shaderId;
    }
    
    /**
     * Links vertex and fragment shaders into a program.
     * 
     * @param vertexShaderId the vertex shader ID
     * @param fragmentShaderId the fragment shader ID
     * @return the program ID, or 0 if linking failed
     */
    private int linkProgram(int vertexShaderId, int fragmentShaderId) {
        int programId = GL20.glCreateProgram();
        if (programId == 0) {
            LOGGER.error("Failed to create shader program");
            return 0;
        }
        
        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);
        GL20.glLinkProgram(programId);
        
        int linkStatus = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS);
        if (linkStatus == 0) {
            String log = GL20.glGetProgramInfoLog(programId, 32768);
            LOGGER.error("Failed to link shader program: {}", log);
            GL20.glDeleteProgram(programId);
            return 0;
        }
        
        return programId;
    }
    
    @Override
    public void setUniform(String name, int value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }
    
    @Override
    public void setUniform(String name, float value) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }
    
    @Override
    public void setUniform(String name, float x, float y) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }
    
    @Override
    public void setUniform(String name, float x, float y, float z) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z);
        }
    }
    
    @Override
    public void setUniform(String name, float x, float y, float z, float w) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }
    
    @Override
    public void setUniformMatrix4(String name, float[] matrix) {
        if (matrix.length != 16) {
            throw new IllegalArgumentException("Matrix must have 16 elements");
        }
        int location = GL20.glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniformMatrix4fv(location, false, matrix);
        }
    }
    
    /**
     * Gets the OpenGL program ID.
     * Package-private for use by other OpenGL backend classes.
     * 
     * @return the program ID
     */
    int getProgramId() {
        return programId;
    }
    
    @Override
    public void close() {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
        }
        if (vertexShaderId != 0) {
            GL20.glDeleteShader(vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            GL20.glDeleteShader(fragmentShaderId);
        }
    }
}
