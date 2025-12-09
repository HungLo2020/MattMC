package net.minecraft.client.renderer.shader;

import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.shader.pack.ShaderPack;
import net.minecraft.client.renderer.shader.program.CompiledShaderProgram;
import net.minecraft.client.renderer.shader.program.ShaderProgramType;
import org.slf4j.Logger;

/**
 * Helper utilities for debugging and validating shader packs.
 * Provides diagnostic information about loaded shaders.
 */
@Environment(EnvType.CLIENT)
public class ShaderDebugHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Logs diagnostic information about a shader pack.
     */
    public static void logShaderPackInfo(ShaderPack shaderPack) {
        LOGGER.info("=== Shader Pack Diagnostic Info ===");
        LOGGER.info("Pack Name: {}", shaderPack.getName());
        LOGGER.info("Pack Description: {}", shaderPack.getMetadata().description());
        LOGGER.info("Pack Author: {}", shaderPack.getMetadata().author());
        LOGGER.info("Pack Version: {}", shaderPack.getMetadata().version());
        
        // Log discovered shader programs
        int programCount = 0;
        int compiledCount = 0;
        
        for (String programName : shaderPack.getShaderProgramNames()) {
            programCount++;
            boolean hasVertex = shaderPack.getVertexSource(programName) != null;
            boolean hasFragment = shaderPack.getFragmentSource(programName) != null;
            
            ShaderProgramType type = ShaderProgramType.fromName(programName);
            boolean isCompiled = type != null && shaderPack.getCompiledProgram(type) != null;
            
            if (isCompiled) {
                compiledCount++;
            }
            
            LOGGER.info("  Program: {} [V:{} F:{} Compiled:{}]", 
                programName, hasVertex ? "✓" : "✗", hasFragment ? "✓" : "✗", isCompiled ? "✓" : "✗");
        }
        
        LOGGER.info("Total Programs: {} (Compiled: {})", programCount, compiledCount);
        
        // Log shader properties
        LOGGER.info("Properties:");
        for (String key : shaderPack.getProperties().getKeys()) {
            String value = shaderPack.getProperties().get(key, "");
            LOGGER.info("  {} = {}", key, value);
        }
        
        LOGGER.info("===================================");
    }
    
    /**
     * Validates that essential shader programs are present.
     */
    public static boolean validateShaderPack(ShaderPack shaderPack) {
        boolean valid = true;
        
        // Check for at least one geometry shader
        boolean hasGeometryShader = false;
        String[] geometryShaders = {
            "gbuffers_basic", "gbuffers_textured", "gbuffers_terrain",
            "gbuffers_water", "gbuffers_entities"
        };
        
        for (String shader : geometryShaders) {
            if (shaderPack.hasShaderProgram(shader)) {
                hasGeometryShader = true;
                break;
            }
        }
        
        if (!hasGeometryShader) {
            LOGGER.warn("Shader pack '{}' has no geometry shaders - rendering may not work", 
                shaderPack.getName());
            valid = false;
        }
        
        // Check for final shader
        if (!shaderPack.hasShaderProgram("final")) {
            LOGGER.debug("Shader pack '{}' has no 'final' shader - will use default output", 
                shaderPack.getName());
        }
        
        return valid;
    }
    
    /**
     * Logs OpenGL information relevant to shaders.
     */
    public static void logOpenGLInfo() {
        LOGGER.info("=== OpenGL Shader Capabilities ===");
        try {
            org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR);
            String vendor = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR);
            String renderer = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
            String version = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION);
            String glslVersion = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION);
            
            LOGGER.info("Vendor: {}", vendor);
            LOGGER.info("Renderer: {}", renderer);
            LOGGER.info("OpenGL Version: {}", version);
            LOGGER.info("GLSL Version: {}", glslVersion);
            
            // Check for required extensions/features
            int maxTextureUnits = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_MAX_TEXTURE_UNITS);
            int maxColorAttachments = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_MAX_COLOR_ATTACHMENTS);
            int maxDrawBuffers = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_MAX_DRAW_BUFFERS);
            
            LOGGER.info("Max Texture Units: {}", maxTextureUnits);
            LOGGER.info("Max Color Attachments: {}", maxColorAttachments);
            LOGGER.info("Max Draw Buffers: {}", maxDrawBuffers);
            
        } catch (Exception e) {
            LOGGER.error("Failed to query OpenGL information", e);
        }
        LOGGER.info("===================================");
    }
    
    /**
     * Gets a formatted summary of a shader pack.
     */
    public static String getShaderPackSummary(ShaderPack shaderPack) {
        int programCount = 0;
        for (String name : shaderPack.getShaderProgramNames()) {
            programCount++;
        }
        
        return String.format("%s v%s by %s (%d programs)",
            shaderPack.getMetadata().name(),
            shaderPack.getMetadata().version(),
            shaderPack.getMetadata().author(),
            programCount
        );
    }
}
