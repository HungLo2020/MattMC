package net.minecraft.client.renderer.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Manages a full-screen quad for post-processing effects.
 * Creates and renders a simple quad covering the entire screen.
 */
@Environment(EnvType.CLIENT)
public class FullScreenQuad implements AutoCloseable {
    private int vao = -1;
    private int vbo = -1;
    private boolean initialized = false;
    
    /**
     * Initializes the full-screen quad.
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        
        RenderSystem.assertOnRenderThread();
        
        // Full-screen quad vertices (2 triangles)
        // Position (x, y) and UV (u, v)
        float[] vertices = {
            // Triangle 1
            -1.0f, -1.0f,  0.0f, 0.0f, // Bottom-left
             1.0f, -1.0f,  1.0f, 0.0f, // Bottom-right
             1.0f,  1.0f,  1.0f, 1.0f, // Top-right
            
            // Triangle 2
            -1.0f, -1.0f,  0.0f, 0.0f, // Bottom-left
             1.0f,  1.0f,  1.0f, 1.0f, // Top-right
            -1.0f,  1.0f,  0.0f, 1.0f  // Top-left
        };
        
        // Create VAO
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        
        // Create VBO
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        
        // Position attribute (location 0)
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        
        // UV attribute (location 1)
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        
        // Unbind
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        
        initialized = true;
    }
    
    /**
     * Renders the full-screen quad.
     */
    public void render() {
        if (!initialized) {
            initialize();
        }
        
        RenderSystem.assertOnRenderThread();
        
        // Bind VAO and draw
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
    }
    
    /**
     * Checks if the quad is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        
        if (vbo != -1) {
            GL15.glDeleteBuffers(vbo);
            vbo = -1;
        }
        
        if (vao != -1) {
            GL30.glDeleteVertexArrays(vao);
            vao = -1;
        }
        
        initialized = false;
    }
}
