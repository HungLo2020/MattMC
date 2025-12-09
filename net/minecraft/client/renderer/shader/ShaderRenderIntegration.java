package net.minecraft.client.renderer.shader;

import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.shader.pack.ShaderPack;
import net.minecraft.client.renderer.shader.pack.ShaderPackRepository;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Integration point between Minecraft's rendering system and the shader pipeline.
 * Provides a simple interface for LevelRenderer to use shaders.
 */
@Environment(EnvType.CLIENT)
public class ShaderRenderIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ShaderRenderPipeline activePipeline = null;
    
    /**
     * Checks if shader rendering is active.
     */
    public static boolean isShaderRenderingActive() {
        return activePipeline != null && activePipeline.isInitialized();
    }
    
    /**
     * Gets the active shader pipeline.
     */
    public static ShaderRenderPipeline getActivePipeline() {
        return activePipeline;
    }
    
    /**
     * Sets up the shader pipeline for the active shader pack.
     * Called when a shader pack is activated.
     */
    public static void setupShaderPipeline(ShaderPack pack, int width, int height) {
        // Close existing pipeline
        if (activePipeline != null) {
            activePipeline.close();
            activePipeline = null;
        }
        
        if (pack == null) {
            LOGGER.info("No shader pack active - using vanilla rendering");
            return;
        }
        
        try {
            LOGGER.info("Setting up shader pipeline for pack: {}", pack.getName());
            activePipeline = new ShaderRenderPipeline(pack);
            activePipeline.initialize(width, height);
            LOGGER.info("Shader pipeline ready for rendering");
        } catch (Exception e) {
            LOGGER.error("Failed to setup shader pipeline", e);
            activePipeline = null;
        }
    }
    
    /**
     * Resizes the shader pipeline buffers.
     */
    public static void resizeShaderPipeline(int width, int height) {
        if (activePipeline != null) {
            activePipeline.resize(width, height);
        }
    }
    
    /**
     * Closes the active shader pipeline.
     */
    public static void closeShaderPipeline() {
        if (activePipeline != null) {
            activePipeline.close();
            activePipeline = null;
        }
    }
    
    /**
     * Executes shader rendering for the current frame.
     * This is the main entry point for shader rendering.
     */
    public static void executeShaderRendering(Camera camera, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (!isShaderRenderingActive()) {
            return;
        }
        
        try {
            activePipeline.render(camera, viewMatrix, projectionMatrix);
        } catch (Exception e) {
            LOGGER.error("Error during shader rendering", e);
        }
    }
    
    /**
     * Updates shader integration when shader pack changes.
     * Should be called from Minecraft when shader pack is loaded/unloaded.
     */
    public static void updateFromRepository(Minecraft minecraft) {
        ShaderPackRepository repository = minecraft.getShaderPackRepository();
        if (repository == null) {
            return;
        }
        
        ShaderPack activePack = repository.getActivePack().orElse(null);
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        
        setupShaderPipeline(activePack, width, height);
    }
}
