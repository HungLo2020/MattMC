package net.vulkanic.framegraph;

import net.vulkanic.CommandContext;

/**
 * A single logical render pass registered with a {@link VulkanicFrameGraphBuilder}.
 *
 * <p>Callers add passes to the frame graph via
 * {@link VulkanicFrameGraphBuilder#addPass(String, VulkanicFramePass.Executor)} and
 * declare which resources each pass reads/writes. The builder resolves execution order
 * and manages resource lifetimes before calling each executor.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> delegates to {@code FramePass} / {@code FrameGraphBuilder}</li>
 *   <li><b>Vulkan backend:</b> will record commands into a {@code VkCommandBuffer} and
 *       insert pipeline barriers automatically</li>
 * </ul>
 */
public interface VulkanicFramePass {

    /**
     * Functional interface executed when this pass is run.
     * Receives the command context for recording render commands.
     */
    @FunctionalInterface
    interface Executor {
        void execute(CommandContext ctx);
    }

    /** Human-readable name of this pass (for profiling/debugging). */
    String getName();
}
