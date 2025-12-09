package net.minecraft.client.renderer.shader.program;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;

/**
 * Compiles GLSL shader source code into OpenGL shader programs.
 */
@Environment(EnvType.CLIENT)
public class ShaderCompiler {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Compiles a shader program from vertex and fragment shader source.
     */
    public static CompiledShaderProgram compile(ShaderProgramType type, String vertexSource, String fragmentSource) {
        RenderSystem.assertOnRenderThread();
        
        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        
        try {
            // Compile vertex shader
            if (vertexSource != null && !vertexSource.isEmpty()) {
                vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource, type.getName() + ".vsh");
                if (vertexShader == 0) {
                    throw new RuntimeException("Failed to compile vertex shader for " + type.getName());
                }
            }
            
            // Compile fragment shader
            if (fragmentSource != null && !fragmentSource.isEmpty()) {
                fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource, type.getName() + ".fsh");
                if (fragmentShader == 0) {
                    throw new RuntimeException("Failed to compile fragment shader for " + type.getName());
                }
            }
            
            // Link shader program
            program = GL20.glCreateProgram();
            if (vertexShader != 0) {
                GL20.glAttachShader(program, vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glAttachShader(program, fragmentShader);
            }
            
            GL20.glLinkProgram(program);
            
            // Check link status
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                String error = GL20.glGetProgramInfoLog(program, 32768);
                throw new RuntimeException("Failed to link shader program " + type.getName() + ": " + error);
            }
            
            // Validate program
            GL20.glValidateProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == 0) {
                LOGGER.warn("Shader program validation failed for {}: {}", 
                    type.getName(), 
                    GL20.glGetProgramInfoLog(program, 32768));
            }
            
            LOGGER.debug("Successfully compiled shader program: {}", type.getName());
            return new CompiledShaderProgram(program, type);
            
        } catch (Exception e) {
            // Clean up on error
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            throw e;
        } finally {
            // Clean up shader objects (they're no longer needed after linking)
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
    }
    
    /**
     * Compiles a single shader (vertex or fragment).
     */
    private static int compileShader(int type, String source, String name) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        
        // Check compilation status
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String error = GL20.glGetShaderInfoLog(shader, 32768);
            LOGGER.error("Failed to compile shader {}: {}", name, error);
            GL20.glDeleteShader(shader);
            return 0;
        }
        
        return shader;
    }
}
