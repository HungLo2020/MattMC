package mattmc.client.renderer.backend.opengl;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/** Simple shader program wrapper for GLSL shaders. */
public class Shader implements AutoCloseable {
    private final int programId;
    
    public Shader(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
        
        programId = glCreateProgram();
        glAttachShader(programId, vertexShader);
        glAttachShader(programId, fragmentShader);
        glLinkProgram(programId);
        
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            throw new RuntimeException("Shader program linking failed: " + log);
        }
        
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }
    
    private int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            throw new RuntimeException("Shader compilation failed: " + log);
        }
        
        return shader;
    }
    
    public void use() {
        glUseProgram(programId);
    }
    
    public void setUniform1f(String name, float value) {
        int location = glGetUniformLocation(programId, name);
        if (location != -1) {
            glUniform1f(location, value);
        }
    }
    
    public void setUniform2f(String name, float v1, float v2) {
        int location = glGetUniformLocation(programId, name);
        if (location != -1) {
            glUniform2f(location, v1, v2);
        }
    }
    
    public void setUniform1i(String name, int value) {
        int location = glGetUniformLocation(programId, name);
        if (location != -1) {
            glUniform1i(location, value);
        }
    }
    
    public void setUniform3f(String name, float v1, float v2, float v3) {
        int location = glGetUniformLocation(programId, name);
        if (location != -1) {
            glUniform3f(location, v1, v2, v3);
        }
    }
    
    public void setUniform4f(String name, float v1, float v2, float v3, float v4) {
        int location = glGetUniformLocation(programId, name);
        if (location != -1) {
            glUniform4f(location, v1, v2, v3, v4);
        }
    }
    
    public void setUniformMatrix4f(String name, float[] matrix) {
        int location = glGetUniformLocation(programId, name);
        if (location != -1 && matrix.length == 16) {
            glUniformMatrix4fv(location, false, matrix);
        }
    }
    
    public static void unbind() {
        glUseProgram(0);
    }
    
    @Override
    public void close() {
        glDeleteProgram(programId);
    }
}
