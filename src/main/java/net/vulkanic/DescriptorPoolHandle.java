package net.vulkanic;

/**
 * Opaque handle to a descriptor allocation pool.
 *
 * <p>In OpenGL this models logical descriptor-set lifetime and reuse semantics.
 * In Vulkan this will wrap a {@code VkDescriptorPool}.
 */
public interface DescriptorPoolHandle extends AutoCloseable {

    /** Returns the configured maximum descriptor-set allocations for this pool. */
    int maxSets();

    /** Returns the number of currently live descriptor-set allocations. */
    int allocatedSetCount();

    /** Returns true if this pool can still be used for allocation/reset operations. */
    boolean isValid();

    /** Releases this pool and invalidates all descriptor sets allocated from it. */
    @Override
    void close();
}
