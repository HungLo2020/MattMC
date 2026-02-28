package net.vulkanic;

/**
 * A single rendering pass within a {@link VulkanicFrameGraph}.
 *
 * <p>A frame pass declares which resources it reads or writes, and provides
 * a {@link Runnable} task that is executed when the frame graph is run.
 * The frame-graph scheduler uses the declared dependencies to:
 * <ul>
 *   <li>cull passes whose outputs are never consumed;</li>
 *   <li>determine the order in which passes are executed;</li>
 *   <li>manage the physical-resource lifetime of each virtual resource.</li>
 * </ul>
 *
 * <h3>Usage example</h3>
 * <pre>
 * VulkanicFrameGraph fg = VulkanicAPI.beginFrame();
 * VulkanicFramePass skyPass = fg.addPass("sky");
 * ResourceHandle&lt;RenderTarget&gt; skyHandle = skyPass.createsInternal("sky-color", skyDescriptor);
 * skyPass.executes(() -&gt; renderSky(skyHandle.get()));
 *
 * VulkanicFramePass mainPass = fg.addPass("main");
 * mainPass.reads(skyHandle);
 * mainPass.executes(() -&gt; renderMain());
 *
 * VulkanicAPI.executeFrame(fg, allocator);
 * </pre>
 *
 * <p>This interface lives in {@code net.vulkanic} so that both the OpenGL
 * backend (which delegates to {@code FrameGraphBuilder}) and a future Vulkan
 * backend can implement it natively.
 */
public interface VulkanicFramePass {

    /**
     * Declares that this pass creates an internal (transient) resource.
     *
     * <p>The returned handle can be passed to other passes via
     * {@link #reads(ResourceHandle)} or {@link #readsAndWrites(ResourceHandle)}.
     *
     * @param <T>        type of the resource
     * @param name       debug name for the resource
     * @param descriptor descriptor describing how to allocate the resource
     * @return a handle through which the physical resource is accessible
     *         during pass execution
     */
    <T> ResourceHandle<T> createsInternal(String name, ResourceDescriptor<T> descriptor);

    /**
     * Declares that this pass reads the resource identified by {@code handle}.
     *
     * @param <T>    type of the resource
     * @param handle handle obtained from another pass's
     *               {@link #createsInternal} or
     *               {@link VulkanicFrameGraph#importExternal}
     */
    <T> void reads(ResourceHandle<T> handle);

    /**
     * Declares that this pass both reads and potentially modifies the resource
     * identified by {@code handle}.  Returns a new handle representing the
     * post-write version of the resource (the original handle becomes stale).
     *
     * @param <T>    type of the resource
     * @param handle handle for the resource to read and write
     * @return a new handle representing the updated resource
     */
    <T> ResourceHandle<T> readsAndWrites(ResourceHandle<T> handle);

    /**
     * Declares an explicit ordering dependency: this pass must execute after
     * {@code other}, regardless of resource dependencies.
     *
     * @param other the pass that must precede this one
     */
    void requires(VulkanicFramePass other);

    /**
     * Prevents the scheduler from culling this pass even if none of its
     * outputs are consumed.  Use for passes with side-effects (e.g. a pass
     * that writes to a present-target swap chain image).
     */
    void disableCulling();

    /**
     * Registers the task to execute when this pass runs.
     *
     * @param task the rendering work to perform
     */
    void executes(Runnable task);
}
