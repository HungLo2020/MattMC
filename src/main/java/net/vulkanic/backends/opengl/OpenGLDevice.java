package net.vulkanic.backends.opengl;

import net.vulkanic.BackendType;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicCommandBuffer;
import net.vulkanic.VulkanicDevice;
import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicShader;
import net.vulkanic.VulkanicTexture;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of VulkanicDevice.
 * 
 * This is the ONLY place in the codebase (besides other backend classes) that should
 * directly interact with OpenGL. All rendering operations go through this backend.
 */
public class OpenGLDevice implements VulkanicDevice {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGLDevice.class);
    private final OpenGLCommandBuffer sharedCommandBuffer;
    
    public OpenGLDevice() {
        this.sharedCommandBuffer = new OpenGLCommandBuffer();
        LOGGER.info("OpenGL backend initialized");
    }
    
    @Override
    public VulkanicCommandBuffer createCommandBuffer() {
        // Return the shared command buffer for immediate-mode rendering
        // OpenGL backend executes commands immediately, so no buffering needed
        return sharedCommandBuffer;
    }
    
    @Override
    public VulkanicShader createShader(String vertexShaderSource, String fragmentShaderSource) {
        return new OpenGLShader(vertexShaderSource, fragmentShaderSource);
    }
    
    @Override
    public VulkanicBuffer createBuffer(int sizeInBytes) {
        return new OpenGLBuffer(sizeInBytes);
    }
    
    @Override
    public VulkanicTexture createTexture(int width, int height) {
        return new OpenGLTexture(width, height);
    }
    
    @Override
    public VulkanicFramebuffer createFramebuffer(int width, int height) {
        return new OpenGLFramebuffer(width, height);
    }
    
    @Override
    public BackendType getBackendType() {
        return BackendType.OPENGL;
    }
    
    @Override
    public String getBackendName() {
        return "OpenGL";
    }
    
    @Override
    public String getVendor() {
        return GL11.glGetString(GL11.GL_VENDOR);
    }
    
    @Override
    public String getRenderer() {
        return GL11.glGetString(GL11.GL_RENDERER);
    }
    
    @Override
    public int getMaxTextureSize() {
        return GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
    }
    
    @Override
    public void close() {
        LOGGER.info("OpenGL backend closed");
    }
}
