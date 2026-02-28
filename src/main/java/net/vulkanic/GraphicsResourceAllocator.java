package net.vulkanic;

/**
 * Allocator for physical GPU resources used by a {@link VulkanicFrameGraph}.
 *
 * <p>Implementations may pool physical resources across frames to avoid
 * redundant GPU allocations.  The unpooled implementation simply calls
 * {@link ResourceDescriptor#allocate()} and {@link ResourceDescriptor#free(Object)}
 * directly.
 *
 * <p>This type lives in {@code net.vulkanic} so that the frame-graph scheduler
 * can be implemented entirely without importing Blaze3D types.
 */
public interface GraphicsResourceAllocator {

    /**
     * A non-pooling allocator that allocates a new resource for every acquire
     * and frees it on every release.
     */
    GraphicsResourceAllocator UNPOOLED = new GraphicsResourceAllocator() {
        @Override
        public <T> T acquire(ResourceDescriptor<T> descriptor) {
            T resource = descriptor.allocate();
            descriptor.prepare(resource);
            return resource;
        }

        @Override
        public <T> void release(ResourceDescriptor<T> descriptor, T resource) {
            descriptor.free(resource);
        }
    };

    /**
     * Acquires a physical resource that matches {@code descriptor}.
     *
     * <p>May return a previously-released resource if one is available and
     * {@link ResourceDescriptor#canUsePhysicalResource(ResourceDescriptor)}
     * returns {@code true}.
     */
    <T> T acquire(ResourceDescriptor<T> descriptor);

    /**
     * Releases the physical resource back to the pool (or frees it immediately
     * if pooling is not supported).
     */
    <T> void release(ResourceDescriptor<T> descriptor, T resource);
}
