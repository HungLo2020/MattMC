package net.vulkanic;

/**
 * Opaque handle to a descriptor-style resource set allocation.
 *
 * <p>In OpenGL this stores validated resource bindings for bind-time application.
 * In Vulkan this will wrap a {@code VkDescriptorSet}.
 */
public interface DescriptorSetHandle extends AutoCloseable {

    /** Returns true if this descriptor set can still be updated/bound. */
    boolean isValid();

    /** Returns the stable descriptor-layout key this set was allocated for. */
    String layoutKey();

    /** Returns the resource layout metadata this set was allocated against. */
    PipelineDescriptor.ResourceLayout layout();

    /** Invalidates this descriptor set allocation and releases pool capacity. */
    @Override
    void close();
}
