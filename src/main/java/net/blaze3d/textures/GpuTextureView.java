package net.blaze3d.textures;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public abstract class GpuTextureView implements AutoCloseable, net.vulkanic.resources.VulkanicTextureView {
	private final GpuTexture texture;
	private final int baseMipLevel;
	private final int mipLevels;

	public GpuTextureView(GpuTexture gpuTexture, int i, int j) {
		this.texture = gpuTexture;
		this.baseMipLevel = i;
		this.mipLevels = j;
	}

	@Override
	public abstract void close();

	/**
	 * Covariant return: {@link GpuTexture} implements {@link net.vulkanic.resources.VulkanicTexture},
	 * so this satisfies {@code VulkanicTextureView.texture()} without an extra override.
	 */
	@Override
	public GpuTexture texture() {
		return this.texture;
	}

	public int baseMipLevel() {
		return this.baseMipLevel;
	}

	public int mipLevels() {
		return this.mipLevels;
	}

	@Override
	public int getBaseMipLevel() {
		return this.baseMipLevel;
	}

	@Override
	public int getMipLevelCount() {
		return this.mipLevels;
	}

	@Override
	public int getWidth(int i) {
		return this.texture.getWidth(i + this.baseMipLevel);
	}

	@Override
	public int getHeight(int i) {
		return this.texture.getHeight(i + this.baseMipLevel);
	}

	@Override
	public abstract boolean isClosed();

	/**
	 * Returns the backend-native handle for this texture view.
	 * <ul>
	 *   <li>OpenGL: same as the underlying texture object name</li>
	 *   <li>Vulkan: VkImageView handle</li>
	 * </ul>
	 */
	@Override
	public abstract long getNativeHandle();
}
