package net.vulkanic;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating descriptor set layouts.
 * 
 * A descriptor set layout defines what resources (textures, buffers) are
 * at which binding points and which shader stages use them.
 * 
 * Example:
 * <pre>
 * DescriptorSetLayout layout = new DescriptorSetLayoutBuilder()
 *     .addTexture(0, ShaderStage.FRAGMENT)        // Albedo texture
 *     .addTexture(1, ShaderStage.FRAGMENT)        // Normal map
 *     .addUniformBuffer(2, ShaderStage.VERTEX)    // Transform UBO
 *     .build();
 * </pre>
 */
public class DescriptorSetLayoutBuilder {
    
    /**
     * Represents a single binding in a descriptor set layout.
     */
    public static class Binding {
        public final int bindingIndex;
        public final DescriptorType type;
        public final ShaderStage stage;
        
        public Binding(int bindingIndex, DescriptorType type, ShaderStage stage) {
            this.bindingIndex = bindingIndex;
            this.type = type;
            this.stage = stage;
        }
    }
    
    /**
     * Type of resource in a descriptor binding.
     */
    public enum DescriptorType {
        TEXTURE,
        UNIFORM_BUFFER,
        STORAGE_BUFFER
    }
    
    private final List<Binding> bindings = new ArrayList<>();
    
    /**
     * Adds a texture binding.
     * 
     * @param binding Binding index (e.g., 0, 1, 2...)
     * @param stage Shader stage that uses this binding
     * @return this for method chaining
     */
    public DescriptorSetLayoutBuilder addTexture(int binding, ShaderStage stage) {
        bindings.add(new Binding(binding, DescriptorType.TEXTURE, stage));
        return this;
    }
    
    /**
     * Adds a uniform buffer binding.
     * 
     * @param binding Binding index
     * @param stage Shader stage that uses this binding
     * @return this for method chaining
     */
    public DescriptorSetLayoutBuilder addUniformBuffer(int binding, ShaderStage stage) {
        bindings.add(new Binding(binding, DescriptorType.UNIFORM_BUFFER, stage));
        return this;
    }
    
    /**
     * Adds a storage buffer binding.
     * 
     * @param binding Binding index
     * @param stage Shader stage that uses this binding
     * @return this for method chaining
     */
    public DescriptorSetLayoutBuilder addStorageBuffer(int binding, ShaderStage stage) {
        bindings.add(new Binding(binding, DescriptorType.STORAGE_BUFFER, stage));
        return this;
    }
    
    /**
     * Gets the list of bindings.
     * 
     * @return List of bindings
     */
    public List<Binding> getBindings() {
        return bindings;
    }
}
