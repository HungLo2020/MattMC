package net.vulkanic;

/**
 * Descriptor for a virtual resource inside a {@link VulkanicFrameGraph}.
 *
 * <p>A descriptor knows how to allocate and free a physical GPU resource
 * (e.g. a texture or buffer).  The frame-graph scheduler calls
 * {@link #allocate()} when the resource first becomes needed and
 * {@link #free(Object)} when no subsequent pass will read it.
 *
 * <p>Two descriptors are considered compatible (the frame graph may reuse one
 * physical allocation for both) when
 * {@link #canUsePhysicalResource(ResourceDescriptor)} returns {@code true}.
 *
 * <p>This type lives in {@code net.vulkanic} so that the frame-graph scheduler
 * can be implemented entirely without importing Blaze3D types.
 */
public interface ResourceDescriptor<T> {

    /**
     * Allocates and returns a new physical GPU resource matching this descriptor.
     */
    T allocate();

    /**
     * Called immediately after {@link #allocate()} to perform any additional
     * initialisation (e.g. clearing a texture).  No-op by default.
     */
    default void prepare(T object) {
    }

    /**
     * Frees the physical GPU resource previously returned by {@link #allocate()}.
     */
    void free(T object);

    /**
     * Returns {@code true} if the physical resource allocated for
     * {@code other} can be re-used for {@code this} descriptor without
     * reallocation.  The default implementation requires descriptor equality.
     */
    default boolean canUsePhysicalResource(ResourceDescriptor<?> other) {
        return this.equals(other);
    }
}
