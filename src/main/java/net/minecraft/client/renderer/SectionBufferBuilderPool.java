package net.minecraft.client.renderer;

import com.google.common.collect.Queues;
import net.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SectionBufferBuilderPool {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Queue<SectionBufferBuilderPack> freeBuffers;
	private volatile int freeBufferCount;

	protected SectionBufferBuilderPool(List<SectionBufferBuilderPack> list) {
		this.freeBuffers = Queues.<SectionBufferBuilderPack>newArrayDeque(list);
		this.freeBufferCount = this.freeBuffers.size();
	}

	public static SectionBufferBuilderPool allocate(int i) {
		int j = Math.max(1, (int)(Runtime.getRuntime().maxMemory() * 0.3) / SectionBufferBuilderPack.TOTAL_BUFFERS_SIZE);
		boolean rustWholeFrameVulkan = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
		// Rust owns semantic terrain extraction on Vulkan; the legacy Java
		// compiler still needs one reusable pack for bookkeeping, but allocating
		// a heap-sized staging pool would reserve memory for discarded meshes.
		int k = rustWholeFrameVulkan ? 1 : Math.max(1, Math.min(i, j));
		List<SectionBufferBuilderPack> list = new ArrayList(k);

		try {
			for (int l = 0; l < k; l++) {
				// Vulkan whole-frame terrain is extracted into Rust-owned semantic
				// meshes. Keep the single legacy pack only for visibility/bookkeeping;
				// allocating full Java staging buffers here would retain a large,
				// discarded upload pool on every Vulkan startup.
				list.add(new SectionBufferBuilderPack(rustWholeFrameVulkan));
			}
		} catch (OutOfMemoryError var7) {
			LOGGER.warn("Allocated only {}/{} buffers", list.size(), k);
			int m = Math.min(list.size() * 2 / 3, list.size() - 1);

			for (int n = 0; n < m; n++) {
				((SectionBufferBuilderPack)list.remove(list.size() - 1)).close();
			}
		}

		return new SectionBufferBuilderPool(list);
	}

	@Nullable
	public SectionBufferBuilderPack acquire() {
		SectionBufferBuilderPack sectionBufferBuilderPack = (SectionBufferBuilderPack)this.freeBuffers.poll();
		if (sectionBufferBuilderPack != null) {
			this.freeBufferCount = this.freeBuffers.size();
			return sectionBufferBuilderPack;
		} else {
			return null;
		}
	}

	public void release(SectionBufferBuilderPack sectionBufferBuilderPack) {
		this.freeBuffers.add(sectionBufferBuilderPack);
		this.freeBufferCount = this.freeBuffers.size();
	}

	public boolean isEmpty() {
		return this.freeBuffers.isEmpty();
	}

	public int getFreeBufferCount() {
		return this.freeBufferCount;
	}
}
