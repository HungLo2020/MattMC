package net.sodium.fabric;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.sodium.client.SodiumClientMod;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.vulkanic.VulkanicAPI;

/**
 * Helper class to manage GPU synchronization fences for Sodium.
 * Replaces the fence queue that was in MinecraftMixin.
 */
public class SodiumGpuSyncHelper {
    private static final LongArrayFIFOQueue fences = new LongArrayFIFOQueue();

    /**
     * Called at the beginning of each frame to wait for the GPU to catch up.
     * This allows us to stall on ClientWaitSync for less time.
     */
    public static void beforeFrameTick() {
        if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            // Rust owns the Vulkan queue, submission completion, and pacing.
            // Sodium's legacy Java fence queue has no semantic resource or
            // submission contract in this route, so it must remain empty.
            fences.clear();
            return;
        }
        ProfilerFiller profiler = Profiler.get();
        profiler.push("wait_for_gpu");
        var ctx = VulkanicAPI.getCommandContext();

        while (fences.size() > SodiumClientMod.options().advanced.cpuRenderAheadLimit) {
            long fence = fences.dequeueLong();
            // We do a ClientWaitSync here instead of a WaitSync to not allow the CPU to get too far ahead of the GPU.
            // This is also needed to make sure that our persistently-mapped staging buffers function correctly, rather
            // than being overwritten by data meant for future frames before the current one has finished rendering on
            // the GPU.
            //
            // Because we use GL_SYNC_FLUSH_COMMANDS_BIT, a flush will be inserted at some point in the command stream
            // (the stream of commands the GPU and/or driver (aka. the "server") is processing).
            // In OpenGL 4.4 contexts and below, the flush will be inserted *right before* the call to ClientWaitSync.
            // In OpenGL 4.5 contexts and above, the flush will be inserted *right after* the call to FenceSync (the
            // creation of the fence).
            // The flush, when the server reaches it in the command stream and processes it, tells the server that it
            // must *finish execution* of all the commands that have already been processed in the command stream,
            // and only after everything before the flush is done is it allowed to start processing and executing
            // commands after the flush.
            // Because we are also waiting on the client for the FenceSync to finish, the flush is effectively treated
            // like a Finish command, where we know that once ClientWaitSync returns, it's likely that everything
            // before it has been completed by the GPU.
            VulkanicAPI.waitForSyncWithFlush(ctx, fence, Long.MAX_VALUE);
            VulkanicAPI.destroySync(ctx, fence);
        }

        profiler.pop();
    }

    /**
     * Called at the end of each frame to create a new fence for GPU synchronization.
     */
    public static void afterFrameTick() {
        if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            fences.clear();
            return;
        }
        long fence = VulkanicAPI.createGpuCompletionFence(VulkanicAPI.getCommandContext());

        if (fence == 0) {
            throw new RuntimeException("Failed to create fence object");
        }

        fences.enqueue(fence);
    }
}
