package net.minecraft.client.renderer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuFence;
import net.blaze3d.systems.RenderSystem;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class MappableRingBuffer implements AutoCloseable {
	private static final int BUFFER_COUNT = 3;
	private final GpuBuffer[] buffers = new GpuBuffer[3];
	private final GpuFence[] fences = new GpuFence[3];
	private final int size;
	private int current = 0;

	public MappableRingBuffer(Supplier<String> supplier, int i, int j) {
		throw new IllegalStateException("Java GUI ring buffers are unavailable on selected Vulkan");
	}

	public int size() {
		return this.size;
	}

	public void close() {
		for (int i = 0; i < 3; i++) {
			this.buffers[i].close();
			if (this.fences[i] != null) {
				this.fences[i].close();
			}
		}
	}
}
