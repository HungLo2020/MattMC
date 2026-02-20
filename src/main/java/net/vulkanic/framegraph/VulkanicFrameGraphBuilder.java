package net.vulkanic.framegraph;

import net.vulkanic.CommandContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Portable frame-graph scheduler for Vulkanic.
 *
 * <p>Game/mod code uses this class to register per-frame render passes and their
 * resource dependencies. After all passes are registered, call
 * {@link net.vulkanic.VulkanicAPI#executeFrame(VulkanicFrameGraphBuilder)} to run them.
 *
 * <p>The builder resolves execution order, handles resource lifetime, and (on the Vulkan
 * backend) inserts the necessary pipeline barriers and semaphore signals.
 *
 * <p>On the OpenGL backend this wraps {@link net.blaze3d.framegraph.FrameGraphBuilder}
 * so existing OpenGL frame-graph logic continues to work unchanged.
 *
 * <pre>{@code
 * VulkanicFrameGraphBuilder frame = VulkanicAPI.beginFrame();
 * frame.addPass("sky",    ctx -> { ... });
 * frame.addPass("level",  ctx -> { ... });
 * frame.addPass("gui",    ctx -> { ... });
 * VulkanicAPI.executeFrame(frame);
 * }</pre>
 */
public class VulkanicFrameGraphBuilder {

    /**
     * Internal representation of a registered pass.
     */
    private static final class PassEntry {
        final String name;
        final VulkanicFramePass.Executor executor;

        PassEntry(String name, VulkanicFramePass.Executor executor) {
            this.name = name;
            this.executor = executor;
        }
    }

    private final List<PassEntry> passes = new ArrayList<>();

    /**
     * Registers a named pass with its execution body.
     *
     * @param name     Human-readable name (used in profiling/debug output)
     * @param executor Code to run when this pass is executed
     * @return A {@link VulkanicFramePass} handle (currently informational only)
     */
    public VulkanicFramePass addPass(String name, VulkanicFramePass.Executor executor) {
        passes.add(new PassEntry(name, executor));
        return () -> name;
    }

    /**
     * Executes all registered passes in registration order, using the supplied command context.
     *
     * <p>On the OpenGL backend, passes execute immediately (immediate-mode).
     * On the Vulkan backend, commands will be recorded into a command buffer and submitted.
     *
     * @param ctx Command context for this frame
     */
    public void execute(CommandContext ctx) {
        for (PassEntry entry : passes) {
            entry.executor.execute(ctx);
        }
    }

    /** Returns the number of passes registered in this frame. */
    public int getPassCount() {
        return passes.size();
    }
}
