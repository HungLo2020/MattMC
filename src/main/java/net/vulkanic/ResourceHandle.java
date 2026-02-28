package net.vulkanic;

/**
 * A typed handle to a virtual resource managed by a {@link VulkanicFrameGraph}.
 *
 * <p>Handles are obtained from {@link VulkanicFramePass#createsInternal} or
 * {@link VulkanicFrameGraph#importExternal} and passed between frame passes to
 * declare read/write dependencies.
 *
 * <p>The physical GPU resource backing the handle is only guaranteed to exist
 * during the execution window of a pass that has declared a dependency on it.
 *
 * <p>This type lives in {@code net.vulkanic} so that both the OpenGL and future
 * Vulkan backends can implement the frame-graph scheduler without importing
 * any Blaze3D types.
 */
public interface ResourceHandle<T> {

    /**
     * A sentinel handle whose {@link #get()} always throws
     * {@link IllegalStateException}.  Useful as a "not-yet-assigned" marker.
     */
    ResourceHandle<?> INVALID_HANDLE = () -> {
        throw new IllegalStateException("Cannot dereference handle with no underlying resource");
    };

    /**
     * Returns a typed invalid handle.
     */
    @SuppressWarnings("unchecked")
    static <T> ResourceHandle<T> invalid() {
        return (ResourceHandle<T>) INVALID_HANDLE;
    }

    /**
     * Dereferences the handle and returns the underlying physical resource.
     *
     * @throws IllegalStateException if the resource is not currently available
     *                               (i.e. outside its allocated lifetime)
     */
    T get();
}
