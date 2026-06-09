package net.vulkanic.backends.vulkan;

import net.blaze3d.textures.AddressMode;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.TextureFormat;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;

public final class VulkanGpuTexture extends GpuTexture {
	private final int id;
	private boolean closed;
	private boolean modesDirty = true;
	private boolean mipmapNonLinear;
	private int views;

	public VulkanGpuTexture(int usage, String label, TextureFormat textureFormat, int width, int height, int depthOrLayers, int mipLevels, int id) {
		super(usage, label, textureFormat, width, height, depthOrLayers, mipLevels);
		this.id = id;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			if (this.views == 0) {
				this.destroyImmediately();
			}
		}
	}

	private void destroyImmediately() {
		net.irisshaders.iris.gl.IrisRenderSystem.deleteTextureId(this.id);
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	public int getGlHandle() {
		return this.id;
	}

	@Override
	public void flushModeChanges(int target) {
		if (this.modesDirty) {
			this.texParameter(target, VulkanicTextureParameterName.WRAP_S, this.toVulkanicTextureParameterValue(this.addressModeU));
			this.texParameter(target, VulkanicTextureParameterName.WRAP_T, this.toVulkanicTextureParameterValue(this.addressModeV));
			switch (this.minFilter) {
				case NEAREST:
					this.texParameter(
						target,
						VulkanicTextureParameterName.MIN_FILTER,
						this.useMipmaps ? VulkanicTextureParameterValue.NEAREST_MIPMAP_LINEAR : VulkanicTextureParameterValue.NEAREST
					);
					break;
				case LINEAR:
					this.texParameter(
						target,
						VulkanicTextureParameterName.MIN_FILTER,
						this.useMipmaps ? VulkanicTextureParameterValue.LINEAR_MIPMAP_LINEAR : VulkanicTextureParameterValue.LINEAR
					);
			}

			switch (this.magFilter) {
				case NEAREST:
					this.texParameter(target, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.NEAREST);
					break;
				case LINEAR:
					this.texParameter(target, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.LINEAR);
			}

			this.modesDirty = false;
		}
	}

	private void texParameter(int target, VulkanicTextureParameterName pname, VulkanicTextureParameterValue param) {
		VulkanicTextureParameterValue effectiveParam = param;
		if (this.mipmapNonLinear) {
			if (param == VulkanicTextureParameterValue.LINEAR_MIPMAP_LINEAR) {
				effectiveParam = VulkanicTextureParameterValue.LINEAR_MIPMAP_NEAREST;
			} else if (param == VulkanicTextureParameterValue.NEAREST_MIPMAP_LINEAR) {
				effectiveParam = VulkanicTextureParameterValue.NEAREST_MIPMAP_NEAREST;
			}
		}
		net.irisshaders.iris.gl.IrisRenderSystem.texParameteri(this.id, target, pname, effectiveParam);
	}

	private VulkanicTextureParameterValue toVulkanicTextureParameterValue(AddressMode addressMode) {
		return switch (addressMode) {
			case REPEAT -> VulkanicTextureParameterValue.REPEAT;
			case CLAMP_TO_EDGE -> VulkanicTextureParameterValue.CLAMP_TO_EDGE;
		};
	}

	@Override
	public void iris$markMipmapNonLinear() {
		boolean wasNonLinear = this.mipmapNonLinear;
		this.mipmapNonLinear = true;
		this.modesDirty = this.modesDirty || !wasNonLinear;
	}

	@Override
	public void iris$copyStateTo(GpuTexture texture) {
		texture.setTextureFilter(this.minFilter, this.magFilter, this.useMipmaps);
		texture.setAddressMode(this.addressModeU, this.addressModeV);
	}

	@Override
	public void setAddressMode(AddressMode addressMode, AddressMode addressMode2) {
		super.setAddressMode(addressMode, addressMode2);
		this.modesDirty = true;
	}

	@Override
	public void setTextureFilter(FilterMode filterMode, FilterMode filterMode2, boolean bl) {
		super.setTextureFilter(filterMode, filterMode2, bl);
		this.modesDirty = true;
	}

	@Override
	public void setUseMipmaps(boolean bl) {
		super.setUseMipmaps(bl);
		this.modesDirty = true;
	}

	void addViews() {
		this.views++;
	}

	void removeViews() {
		this.views--;
		if (this.closed && this.views == 0) {
			this.destroyImmediately();
		}
	}
}
