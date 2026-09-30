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
		// Rust owns the Vulkan queue, submission completion, and pacing.
		// Sodium's legacy Java fence queue has no semantic resource or
		// submission contract in this route, so it must remain empty.
		fences.clear();
		return;
    }

    /**
     * Called at the end of each frame to create a new fence for GPU synchronization.
     */
    public static void afterFrameTick() {
		fences.clear();
		return;
    }
}
