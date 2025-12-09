package net.minecraft.client.renderer.shader.pack;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.shader.config.ShaderProperties;
import net.minecraft.client.renderer.shader.program.CompiledShaderProgram;
import net.minecraft.client.renderer.shader.program.ShaderProgramType;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a loaded shader pack with all its programs, textures, and configuration.
 */
@Environment(EnvType.CLIENT)
public class ShaderPack implements AutoCloseable {
    private final String name;
    private final ShaderPackMetadata metadata;
    private final Map<String, String> vertexSources;
    private final Map<String, String> fragmentSources;
    private final Map<ShaderProgramType, CompiledShaderProgram> compiledPrograms;
    private final ShaderProperties properties;
    
    public ShaderPack(String name, ShaderPackMetadata metadata, 
                     Map<String, String> vertexSources, 
                     Map<String, String> fragmentSources,
                     ShaderProperties properties) {
        this.name = name;
        this.metadata = metadata;
        this.vertexSources = vertexSources;
        this.fragmentSources = fragmentSources;
        this.compiledPrograms = new HashMap<>();
        this.properties = properties;
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
     * Gets the shader properties.
     */
    public ShaderProperties getProperties() {
        return properties;
    }
    
    /**
     * Gets the vertex shader source for a program.
     */
    public String getVertexSource(String programName) {
        return vertexSources.get(programName);
    }
    
    /**
     * Gets the fragment shader source for a program.
     */
    public String getFragmentSource(String programName) {
        return fragmentSources.get(programName);
    }
    
    /**
     * Checks if this shader pack has a specific shader program.
     */
    public boolean hasShaderProgram(String programName) {
        return vertexSources.containsKey(programName) || fragmentSources.containsKey(programName);
    }
    
    /**
     * Gets all shader program names in this pack.
     */
    public Iterable<String> getShaderProgramNames() {
        return vertexSources.keySet();
    }
    
    /**
     * Gets a compiled shader program, or null if not compiled yet.
     */
    public CompiledShaderProgram getCompiledProgram(ShaderProgramType type) {
        return compiledPrograms.get(type);
    }
    
    /**
     * Stores a compiled shader program.
     */
    public void setCompiledProgram(ShaderProgramType type, CompiledShaderProgram program) {
        // Close old program if exists
        CompiledShaderProgram old = compiledPrograms.put(type, program);
        if (old != null) {
            old.close();
        }
    }
    
    @Override
    public void close() {
        // Clean up compiled programs
        for (CompiledShaderProgram program : compiledPrograms.values()) {
            program.close();
        }
        compiledPrograms.clear();
    }
}
