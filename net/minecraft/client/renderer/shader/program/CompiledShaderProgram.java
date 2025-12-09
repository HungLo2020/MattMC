package net.minecraft.client.renderer.shader.program;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.lwjgl.opengl.GL20;

/**
 * Represents a compiled OpenGL shader program.
 * Manages the lifecycle of vertex and fragment shaders.
 */
@Environment(EnvType.CLIENT)
public class CompiledShaderProgram implements AutoCloseable {
    private final int programId;
    private final ShaderProgramType type;
    private boolean closed = false;
    
    public CompiledShaderProgram(int programId, ShaderProgramType type) {
        this.programId = programId;
        this.type = type;
    }
    
    /**
     * Gets the OpenGL program ID.
     */
    public int getProgramId() {
        return programId;
    }
    
    /**
     * Gets the shader program type.
     */
    public ShaderProgramType getType() {
        return type;
    }
    
    /**
     * Binds this shader program for rendering.
     */
    public void bind() {
        RenderSystem.assertOnRenderThread();
        if (!closed) {
            GL20.glUseProgram(programId);
        }
    }
    
    /**
     * Unbinds the current shader program.
     */
    public static void unbind() {
        RenderSystem.assertOnRenderThread();
        GL20.glUseProgram(0);
    }
    
    /**
     * Gets the location of a uniform variable in this shader program.
     */
    public int getUniformLocation(String name) {
        return GL20.glGetUniformLocation(programId, name);
    }
    
    @Override
    public void close() {
        if (!closed) {
            RenderSystem.assertOnRenderThread();
            GL20.glDeleteProgram(programId);
            closed = true;
        }
    }
}
