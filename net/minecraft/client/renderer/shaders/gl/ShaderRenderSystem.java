package net.minecraft.client.renderer.shaders.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstracts OpenGL calls and provides shader-specific rendering utilities.
 * 
 * Based on IRIS IrisRenderSystem pattern.
 * Reference: frnsrc/Iris-1.21.9/.../gl/IrisRenderSystem.java:42-76
 */
public class ShaderRenderSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderRenderSystem.class);
    
    private static boolean initialized = false;
    private static DSASupport dsaSupport;
    private static boolean supportsCompute;
    private static boolean supportsTessellation;
    private static int maxTextureUnits;
    private static int maxDrawBuffers;
    private static int maxColorAttachments;
    
    /**
     * Initializes the shader render system.
     * Must be called after OpenGL context is created.
     * 
     * Based on IRIS IrisRenderSystem.initRenderer()
     * Reference: frnsrc/Iris-1.21.9/.../gl/IrisRenderSystem.java:56-75
     */
    public static void initRenderer() {
        if (initialized) {
            LOGGER.warn("ShaderRenderSystem already initialized, skipping");
            return;
        }
        
        RenderSystem.assertOnRenderThread();
        
        // Detect DSA support (Direct State Access)
        if (GL.getCapabilities().OpenGL45) {
            dsaSupport = DSASupport.CORE;
            LOGGER.info("OpenGL 4.5 detected, DSA available (Core)");
        } else if (GL.getCapabilities().GL_ARB_direct_state_access) {
            dsaSupport = DSASupport.ARB;
            LOGGER.info("ARB_direct_state_access detected, DSA available (ARB)");
        } else {
            dsaSupport = DSASupport.NONE;
            LOGGER.info("DSA support not detected, using fallback");
        }
        
        // Detect compute shader support
        supportsCompute = GL.getCapabilities().glDispatchCompute != MemoryUtil.NULL;
        LOGGER.info("Compute shader support: {}", supportsCompute);
        
        // Detect tessellation shader support
        supportsTessellation = GL.getCapabilities().GL_ARB_tessellation_shader || 
                               GL.getCapabilities().OpenGL40;
        LOGGER.info("Tessellation shader support: {}", supportsTessellation);
        
        // Query OpenGL limits
        int[] result = new int[1];
        GL32C.glGetIntegerv(GL32C.GL_MAX_TEXTURE_IMAGE_UNITS, result);
        maxTextureUnits = result[0];
        LOGGER.info("Max texture units: {}", maxTextureUnits);
        
        GL32C.glGetIntegerv(GL30C.GL_MAX_DRAW_BUFFERS, result);
        maxDrawBuffers = result[0];
        LOGGER.info("Max draw buffers: {}", maxDrawBuffers);
        
        GL32C.glGetIntegerv(GL30C.GL_MAX_COLOR_ATTACHMENTS, result);
        maxColorAttachments = result[0];
        LOGGER.info("Max color attachments: {}", maxColorAttachments);
        
        initialized = true;
        LOGGER.info("ShaderRenderSystem initialized successfully");
    }
    
    /**
     * Returns whether the render system has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Returns the DSA support level.
     */
    public static DSASupport getDSASupport() {
        return dsaSupport;
    }
    
    /**
     * Returns whether compute shaders are supported.
     */
    public static boolean supportsCompute() {
        return supportsCompute;
    }
    
    /**
     * Returns whether tessellation shaders are supported.
     */
    public static boolean supportsTessellation() {
        return supportsTessellation;
    }
    
    /**
     * Returns the maximum number of texture units available.
     */
    public static int getMaxTextureUnits() {
        return maxTextureUnits;
    }
    
    /**
     * Returns the maximum number of draw buffers supported.
     */
    public static int getMaxDrawBuffers() {
        return maxDrawBuffers;
    }
    
    /**
     * Returns the maximum number of color attachments supported.
     */
    public static int getMaxColorAttachments() {
        return maxColorAttachments;
    }
    
    /**
     * Direct State Access support levels.
     */
    public enum DSASupport {
        /** OpenGL 4.5 Core DSA */
        CORE,
        /** ARB_direct_state_access extension */
        ARB,
        /** No DSA support, use fallback */
        NONE
    }
}
