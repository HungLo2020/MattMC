package net.vulkanic.backends.vulkan;

import net.blaze3d.buffers.GpuBuffer;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class VulkanGpuBuffer extends GpuBuffer {
	private final int handle;
	@Nullable
	private final Supplier<String> label;
	private boolean closed;

	public VulkanGpuBuffer(@Nullable Supplier<String> label, int usage, int size, int handle) {
		super(usage, size);
		this.label = label;
		this.handle = handle;
	}

	public int getHandle() {
		return this.handle;
	}

	@Nullable
	public Supplier<String> getLabelSupplier() {
		return this.label;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			VulkanBufferTracker.destroyed();
			net.vulkanic.VulkanicAPI.deleteBuffer(net.vulkanic.VulkanicAPI.getCommandContext(), this.handle);
		}
	}
}
