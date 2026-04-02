package net.vulkanic.backends.vulkan;

import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;

public final class VulkanGpuTextureView extends GpuTextureView {
	private final Runnable closeAction;
	private boolean closed;

	public VulkanGpuTextureView(GpuTexture texture, int baseMipLevel, int mipLevels, Runnable closeAction) {
		super(texture, baseMipLevel, mipLevels);
		this.closeAction = closeAction;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			this.closeAction.run();
		}
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}
}