package net.blaze3d.opengl;

import net.blaze3d.textures.GpuTextureView;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.resources.VulkanicTexture;
import net.vulkanic.resources.VulkanicTextureView;

@Environment(EnvType.CLIENT)
public class GlTextureView extends GpuTextureView implements VulkanicTextureView {
	private boolean closed;

	public GlTextureView(GlTexture glTexture, int i, int j) {
		super(glTexture, i, j);
		glTexture.addViews();
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			this.texture().removeViews();
		}
	}

	/**
	 * Covariant return override.
	 *
	 * <p>Returns {@link GlTexture} which satisfies <em>both</em> parent signatures:
	 * <ul>
	 *   <li>{@code GpuTextureView.texture()} — returns {@code GpuTexture}</li>
	 *   <li>{@code VulkanicTextureView.texture()} — returns {@code VulkanicTexture}</li>
	 * </ul>
	 * This works because {@code GlTexture extends GpuTexture implements VulkanicTexture}.
	 */
	@Override
	public GlTexture texture() {
		return (GlTexture)super.texture();
	}

	// VulkanicTextureView — remaining bridge methods
	@Override
	public long getNativeHandle() {
		return texture().getNativeHandle();
	}

	@Override
	public int getBaseMipLevel() {
		return super.baseMipLevel();
	}

	@Override
	public int getMipLevelCount() {
		return super.mipLevels();
	}

	@Override
	public int getWidth(int mipLevel) {
		return super.getWidth(mipLevel);
	}

	@Override
	public int getHeight(int mipLevel) {
		return super.getHeight(mipLevel);
	}
}
