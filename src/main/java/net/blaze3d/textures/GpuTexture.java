package net.blaze3d.textures;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public abstract class GpuTexture implements AutoCloseable, net.irisshaders.iris.mixinterface.GpuTextureInterface,
		net.vulkanic.resources.VulkanicTexture {
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

	// VulkanicTexture bridge methods — allow GpuTexture to be used anywhere
	// a VulkanicTexture is expected without casting.

	/** Returns the width of the base mip level (mip 0). */
	@Override
	public int getWidth() {
		return getWidth(0);
	}

	/** Returns the height of the base mip level (mip 0). */
	@Override
	public int getHeight() {
		return getHeight(0);
	}

	/** Returns the usage flags bitmask for this texture. */
	@Override
	public int getUsage() {
		return usage();
	}

	/**
	 * Returns the backend-native handle for this texture.
	 * <ul>
	 *   <li>OpenGL: GL texture object name</li>
	 *   <li>Vulkan: VkImage handle</li>
	 * </ul>
	 */
	@Override
	public abstract long getNativeHandle();

	/**
	 * Returns the Vulkanic pixel format for this texture.
	 * Subclasses map the Blaze3D {@link TextureFormat} to the equivalent
	 * {@link net.vulkanic.resources.VulkanicTextureFormat}.
	 */
	@Override
	public abstract net.vulkanic.resources.VulkanicTextureFormat getVulkanicFormat();

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
