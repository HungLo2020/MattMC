package net.vulkanic.backends.vulkan;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks buffers owned by the Vulkan compatibility backend without borrowing
 * Iris' OpenGL resource counters.  The counter is intentionally local to the
 * backend; it is diagnostic bookkeeping and never participates in rendering.
 */
final class VulkanBufferTracker {
	private static final AtomicInteger LIVE_BUFFERS = new AtomicInteger();

	private VulkanBufferTracker() {
	}

	static void created() {
		LIVE_BUFFERS.incrementAndGet();
	}

	static void destroyed() {
		LIVE_BUFFERS.updateAndGet(value -> value > 0 ? value - 1 : 0);
	}

	static int liveCount() {
		return LIVE_BUFFERS.get();
	}
}
