package net.vulkanic;

/**
 * Represents a set of resources (textures, buffers) bound together.
 * 
 * In Vulkan: Maps to VkDescriptorSet
 * In OpenGL: Represents a collection of texture/buffer bindings
 * 
 * Descriptor sets allow batching resource bindings instead of binding
 * textures and buffers individually. This is more efficient and matches
 * modern GPU architecture.
 * 
 * Example:
 * - Binding 0: Albedo texture
 * - Binding 1: Normal map texture
 * - Binding 2: Uniform buffer (transform data)
 */
public interface DescriptorSet {
    
    /**
     * Gets the backend-specific handle for this descriptor set.
     * 
     * @return Backend-specific descriptor set handle
     */
    long getHandle();
    
    /**
     * Gets the layout that describes this descriptor set's structure.
     * 
     * @return The descriptor set layout
     */
    DescriptorSetLayout getLayout();
}
