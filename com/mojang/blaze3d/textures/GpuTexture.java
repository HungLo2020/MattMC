package com.mojang.blaze3d.textures;

import com.mojang.blaze3d.DontObfuscate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public abstract class GpuTexture implements AutoCloseable {
	public static final int USAGE_COPY_DST = 1;
	public static final int USAGE_COPY_SRC = 2;
	public static final int USAGE_TEXTURE_BINDING = 4;
	public static final int USAGE_RENDER_ATTACHMENT = 8;
	public static final int USAGE_CUBEMAP_COMPATIBLE = 16;
	private final TextureFormat format;
	private final int width;
	private final int height;
	private final int depthOrLayers;
	private final int mipLevels;
	private final int usage;
	private final String label;
	protected AddressMode addressModeU = AddressMode.REPEAT;
	protected AddressMode addressModeV = AddressMode.REPEAT;
	protected FilterMode minFilter = FilterMode.NEAREST;
	protected FilterMode magFilter = FilterMode.LINEAR;
	protected boolean useMipmaps = true;

	public GpuTexture(int i, String string, TextureFormat textureFormat, int j, int k, int l, int m) {
		this.usage = i;
		this.label = string;
		this.format = textureFormat;
		this.width = j;
		this.height = k;
		this.depthOrLayers = l;
		this.mipLevels = m;
	}

	public int getWidth(int i) {
		return this.width >> i;
	}

	public int getHeight(int i) {
		return this.height >> i;
	}

	public int getDepthOrLayers() {
		return this.depthOrLayers;
	}

	public int getMipLevels() {
		return this.mipLevels;
	}

	public TextureFormat getFormat() {
		return this.format;
	}

	public int usage() {
		return this.usage;
	}

	public void setAddressMode(AddressMode addressMode) {
		this.setAddressMode(addressMode, addressMode);
	}

	public void setAddressMode(AddressMode addressMode, AddressMode addressMode2) {
		this.addressModeU = addressMode;
		this.addressModeV = addressMode2;
	}

	public void setTextureFilter(FilterMode filterMode, boolean bl) {
		this.setTextureFilter(filterMode, filterMode, bl);
	}

	public void setTextureFilter(FilterMode filterMode, FilterMode filterMode2, boolean bl) {
		this.minFilter = filterMode;
		this.magFilter = filterMode2;
		this.setUseMipmaps(bl);
	}

	public void setUseMipmaps(boolean bl) {
		this.useMipmaps = bl;
	}

	public String getLabel() {
		return this.label;
	}

	public abstract void close();

	public abstract boolean isClosed();
}
