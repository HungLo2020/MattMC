package net.vulkanic;

/**
 * A per-frame render graph managed by the Vulkanic abstraction layer.
 *
 * <p>A {@code VulkanicFrameGraph} collects rendering passes and their
 * resource dependencies for a single frame.  When {@link #execute} is called
 * the scheduler:
 * <ol>
 *   <li>Prunes passes whose outputs are never consumed (unless culling is
 *       disabled via {@link VulkanicFramePass#disableCulling()}).</li>
 *   <li>Topologically sorts the surviving passes according to their declared
 *       read/write and {@link VulkanicFramePass#requires(VulkanicFramePass)}
 *       dependencies.</li>
 *   <li>Assigns physical-resource lifetimes and invokes
 *       {@link GraphicsResourceAllocator#acquire} / {@link GraphicsResourceAllocator#release}
 *       at the earliest and latest points, respectively.</li>
 *   <li>Executes each pass's {@link VulkanicFramePass#executes(Runnable) task}.</li>
 * </ol>
 *
 * <h3>Usage example</h3>
 * <pre>
 * VulkanicFrameGraph fg = VulkanicAPI.beginFrame();
 *
 * VulkanicFramePass clearPass = fg.addPass("clear");
 * ResourceHandle&lt;RenderTarget&gt; mainColor = clearPass.createsInternal("main-color", colorDescriptor);
 * clearPass.executes(() -&gt; clearColor(mainColor.get()));
 *
 * // Export mainColor so the frame graph knows this resource is "consumed"
 * // externally (prevents the pass from being culled).
 * fg.importExternal("main-color-out", mainColor.get());
 *
 * VulkanicAPI.executeFrame(fg, GraphicsResourceAllocator.UNPOOLED);
 * </pre>
 *
 * <p>In the OpenGL backend this delegates to {@code FrameGraphBuilder}.
 * In the Vulkan backend a native implementation will manage command-buffer
 * submission, pipeline barriers, and semaphore synchronisation.
 *
 * <p>This interface lives in {@code net.vulkanic} so that both backends can
 * implement it without importing any Blaze3D types.
 */
public interface VulkanicFrameGraph {

    /**
     * Adds a new pass to the frame graph and returns a handle through which
     * the pass declares its resource dependencies and task.
     *
     * @param name debug name for the pass (shown in GPU profilers)
     * @return the new pass
     */
    VulkanicFramePass addPass(String name);

    /**
     * Registers an externally-owned resource so other passes can declare
     * read/write dependencies on it.  The resource is never freed by the
     * frame graph.
     *
     * <p>Importing a resource also marks any pass that wrote it as having
     * an external consumer, preventing the scheduler from culling it.
     *
     * @param <T>      type of the resource
     * @param name     debug name for the resource
     * @param resource the already-allocated physical resource
     * @return a handle usable by any pass in this frame graph
     */
    <T> ResourceHandle<T> importExternal(String name, T resource);

    /**
     * Allocates a virtual resource that the frame graph manages internally.
     * The scheduler will call {@link ResourceDescriptor#allocate()} before the
     * first pass that needs it and {@link ResourceDescriptor#free(Object)} after
     * the last pass that uses it.
     *
     * <p>Unlike resources created through {@link VulkanicFramePass#createsInternal},
     * this variant is not associated with any specific pass.
     *
     * @param <T>        type of the resource
     * @param name       debug name
     * @param descriptor descriptor describing how to allocate the resource
     * @return a handle to the (not-yet-allocated) resource
     */
    <T> ResourceHandle<T> createInternal(String name, ResourceDescriptor<T> descriptor);

    /**
     * Executes the frame graph, pruning unused passes and running each
     * surviving pass in dependency order.
     *
     * @param allocator the resource pool used to acquire and release physical
     *                  GPU resources during execution
     */
    void execute(GraphicsResourceAllocator allocator);
}
