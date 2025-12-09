package net.minecraft.client.renderer.shader.pack;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

import java.util.Map;

/**
 * Represents a loaded shader pack with all its programs, textures, and configuration.
 */
@Environment(EnvType.CLIENT)
public class ShaderPack implements AutoCloseable {
    private final String name;
    private final ShaderPackMetadata metadata;
    private final Map<String, String> shaderSources;
    
    public ShaderPack(String name, ShaderPackMetadata metadata, Map<String, String> shaderSources) {
        this.name = name;
        this.metadata = metadata;
        this.shaderSources = shaderSources;
    }
    
    /**
     * Gets the shader pack name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the shader pack metadata.
     */
    public ShaderPackMetadata getMetadata() {
        return metadata;
    }
    
    /**
     * Gets the source code for a specific shader program.
     * @param programName Name of the shader program (e.g., "gbuffers_terrain", "composite")
     * @return Shader source code, or null if not found
     */
    public String getShaderSource(String programName) {
        return shaderSources.get(programName);
    }
    
    /**
     * Checks if this shader pack has a specific shader program.
     */
    public boolean hasShaderProgram(String programName) {
        return shaderSources.containsKey(programName);
    }
    
    /**
     * Gets all shader program names in this pack.
     */
    public Iterable<String> getShaderProgramNames() {
        return shaderSources.keySet();
    }
    
    @Override
    public void close() {
        // Clean up any resources
        // TODO: Implement proper cleanup for compiled shader programs, textures, etc.
    }
}
