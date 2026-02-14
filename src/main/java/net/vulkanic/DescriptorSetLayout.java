package net.vulkanic;

/**
 * Defines the structure of a descriptor set.
 * 
 * In Vulkan: Maps to VkDescriptorSetLayout
 * In OpenGL: Describes what resources will be bound
 * 
 * A descriptor set layout describes what types of resources
 * (textures, buffers) are at which binding points and which
 * shader stages use them.
 */
public interface DescriptorSetLayout {
    
    /**
     * Gets the backend-specific handle for this layout.
     * 
     * @return Backend-specific layout handle
     */
    long getHandle();
    
    /**
     * Gets the number of bindings in this layout.
     * 
     * @return Number of bindings
     */
    int getBindingCount();
}
