package net.blaze3d.textures;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;

@Environment(EnvType.CLIENT)
public abstract class GpuTexture implements AutoCloseable, net.irisshaders.iris.mixinterface.GpuTextureInterface, VulkanicTexture {
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

	/**
	 * Returns the Vulkanic equivalent of this texture's format.
	 *
	 * <p>Implements {@link VulkanicTexture#getVulkanicFormat()} by converting from the
	 * Blaze3D {@link TextureFormat}. Both enums carry identical values; the two types
	 * coexist only during the Blaze3D → Vulkanic migration period.
	 *
	 * <p>This default implementation is concrete so that all subclasses (including
	 * {@code GlTexture}) inherit it without any changes.
	 */
	@Override
	public VulkanicTextureFormat getVulkanicFormat() {
		return switch (this.format) {
			case RGBA8   -> VulkanicTextureFormat.RGBA8;
			case RED8    -> VulkanicTextureFormat.RED8;
			case RED8I   -> VulkanicTextureFormat.RED8I;
			case DEPTH32 -> VulkanicTextureFormat.DEPTH32;
		};
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

	public int glId() {
		return this.iris$getGlId();
	}

	public void flushModeChanges(int target) {
	}

	public void flushModeChanges2D() {
	}

	// Iris compatibility methods
	public int iris$getGlId() {
		return 0; // Subclasses should override
	}

	public void iris$markMipmapNonLinear() {
		// No-op by default
	}
	
	public void iris$copyStateTo(GpuTexture other) {
		// No-op by default - Iris mixin implementation
	}
}
