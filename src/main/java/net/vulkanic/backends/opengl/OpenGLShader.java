package net.vulkanic.backends.opengl;

import net.blaze3d.opengl.GlStateManager;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.VulkanicShader;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of VulkanicShader.
 * 
 * Compiles and manages GLSL shaders using Blaze3D's shader infrastructure.
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
        RenderSystem.assertOnRenderThread();
        
        // Compile vertex shader
        this.vertexShaderId = compileShader(ShaderType.VERTEX, vertexShaderSource);
        if (this.vertexShaderId == 0) {
            throw new IllegalStateException("Failed to compile vertex shader");
        }
        
        // Compile fragment shader
        this.fragmentShaderId = compileShader(ShaderType.FRAGMENT, fragmentShaderSource);
        if (this.fragmentShaderId == 0) {
            GlStateManager.glDeleteShader(this.vertexShaderId);
            throw new IllegalStateException("Failed to compile fragment shader");
        }
        
        // Link program
        this.programId = linkProgram(this.vertexShaderId, this.fragmentShaderId);
        if (this.programId == 0) {
            GlStateManager.glDeleteShader(this.vertexShaderId);
            GlStateManager.glDeleteShader(this.fragmentShaderId);
            throw new IllegalStateException("Failed to link shader program");
        }
    }
    
    /**
     * Compiles a shader from source.
     * 
     * @param type the shader type
     * @param source the shader source code
     * @return the shader ID, or 0 if compilation failed
     */
    private int compileShader(ShaderType type, String source) {
        int shaderId = GlStateManager.glCreateShader(type == ShaderType.VERTEX ? 35633 : 35632);
        if (shaderId == 0) {
            LOGGER.error("Failed to create {} shader", type.getName());
            return 0;
        }
        
        GlStateManager.glShaderSource(shaderId, source);
        GlStateManager.glCompileShader(shaderId);
        
        int compileStatus = GlStateManager.glGetShaderi(shaderId, 35713);
        if (compileStatus == 0) {
            String log = GlStateManager.glGetShaderInfoLog(shaderId, 32768);
            LOGGER.error("Failed to compile {} shader: {}", type.getName(), log);
            GlStateManager.glDeleteShader(shaderId);
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
        int programId = GlStateManager.glCreateProgram();
        if (programId == 0) {
            LOGGER.error("Failed to create shader program");
            return 0;
        }
        
        GlStateManager.glAttachShader(programId, vertexShaderId);
        GlStateManager.glAttachShader(programId, fragmentShaderId);
        GlStateManager.glLinkProgram(programId);
        
        int linkStatus = GlStateManager.glGetProgrami(programId, 35714);
        if (linkStatus == 0) {
            String log = GlStateManager.glGetProgramInfoLog(programId, 32768);
            LOGGER.error("Failed to link shader program: {}", log);
            GlStateManager.glDeleteProgram(programId);
            return 0;
        }
        
        return programId;
    }
    
    @Override
    public void setUniform(String name, int value) {
        RenderSystem.assertOnRenderThread();
        int location = GlStateManager._glGetUniformLocation(programId, name);
        if (location != -1) {
            GlStateManager._glUniform1i(location, value);
        }
    }
    
    @Override
    public void setUniform(String name, float value) {
        RenderSystem.assertOnRenderThread();
        int location = GlStateManager._glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }
    
    @Override
    public void setUniform(String name, float x, float y) {
        RenderSystem.assertOnRenderThread();
        int location = GlStateManager._glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }
    
    @Override
    public void setUniform(String name, float x, float y, float z) {
        RenderSystem.assertOnRenderThread();
        int location = GlStateManager._glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z);
        }
    }
    
    @Override
    public void setUniform(String name, float x, float y, float z, float w) {
        RenderSystem.assertOnRenderThread();
        int location = GlStateManager._glGetUniformLocation(programId, name);
        if (location != -1) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }
    
    @Override
    public void setUniformMatrix4(String name, float[] matrix) {
        if (matrix.length != 16) {
            throw new IllegalArgumentException("Matrix must have 16 elements");
        }
        RenderSystem.assertOnRenderThread();
        int location = GlStateManager._glGetUniformLocation(programId, name);
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
        RenderSystem.assertOnRenderThread();
        if (programId != 0) {
            GlStateManager.glDeleteProgram(programId);
        }
        if (vertexShaderId != 0) {
            GlStateManager.glDeleteShader(vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            GlStateManager.glDeleteShader(fragmentShaderId);
        }
    }
}
