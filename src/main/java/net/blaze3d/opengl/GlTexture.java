package net.blaze3d.opengl;

import net.blaze3d.textures.AddressMode;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.resources.VulkanicTexture;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlTexture extends GpuTexture implements net.irisshaders.iris.mixinterface.GpuTextureInterface, VulkanicTexture {
	protected final int id;
	private final Int2IntMap fboCache = new Int2IntOpenHashMap();
	protected boolean closed;
	protected boolean modesDirty = true;
	private int views;
	// Vulkanic format — set at construction time by OpenGLBackend so that
	// getVulkanicFormat() does not need a switch over TextureFormat (keeping
	// the conversion logic inside the Vulkanic layer, not in Blaze3D).
	private final net.vulkanic.resources.VulkanicTextureFormat vulkanicFormat;

	// Iris: From MixinGpuTexture - mipmap non-linear flag
	private boolean iris$mipmapNonLinear;

	/** Legacy constructor — used by Blaze3D code that has a {@link TextureFormat}. */
	public GlTexture(int i, String string, TextureFormat textureFormat, int j, int k, int l, int m, int n) {
		super(i, string, textureFormat, j, k, l, m);
		this.id = n;
		this.vulkanicFormat = switch (textureFormat) {
			case RGBA8   -> net.vulkanic.resources.VulkanicTextureFormat.RGBA8;
			case RED8    -> net.vulkanic.resources.VulkanicTextureFormat.RED8;
			case RED8I   -> net.vulkanic.resources.VulkanicTextureFormat.RED8I;
			case DEPTH32 -> net.vulkanic.resources.VulkanicTextureFormat.DEPTH32;
		};
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
		GlStateManager._deleteTexture(this.id);
		IntIterator var1 = this.fboCache.values().iterator();

		while (var1.hasNext()) {
			int i = (Integer)var1.next();
			GlStateManager._glDeleteFramebuffers(i);
		}
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	public int getFbo(DirectStateAccess directStateAccess, @Nullable GpuTexture gpuTexture) {
		int i = gpuTexture == null ? 0 : ((GlTexture)gpuTexture).id;
		return this.fboCache.computeIfAbsent(i, (Int2IntFunction)(j -> {
			int k = directStateAccess.createFrameBufferObject();
			directStateAccess.bindFrameBufferTextures(k, this.id, i, 0, 0);
			return k;
		}));
	}

	public void flushModeChanges(int i) {
		if (this.modesDirty) {
			// Iris: From MixinGpuTexture - use IrisRenderSystem.texParameteri instead of GlStateManager._texParameter
			iris$texParameterDSA(i, 10242, GlConst.toGl(this.addressModeU));
			iris$texParameterDSA(i, 10243, GlConst.toGl(this.addressModeV));
			switch (this.minFilter) {
				case NEAREST:
					iris$texParameterDSA(i, 10241, this.useMipmaps ? 9986 : 9728);
					break;
				case LINEAR:
					iris$texParameterDSA(i, 10241, this.useMipmaps ? 9987 : 9729);
			}

			switch (this.magFilter) {
				case NEAREST:
					iris$texParameterDSA(i, 10240, 9728);
					break;
				case LINEAR:
					iris$texParameterDSA(i, 10240, 9729);
			}

			this.modesDirty = false;
		}
	}
	
	// Iris: From MixinGpuTexture - helper method for DSA texture parameter setting with mipmap non-linear handling
	private void iris$texParameterDSA(int target, int pname, int param) {
		int newId = param;

		// Handle mipmap non-linear flag
		if (this.iris$mipmapNonLinear && (param == 9987 || param == 9986)) { // GL_LINEAR_MIPMAP_LINEAR or GL_NEAREST_MIPMAP_LINEAR
			newId = (param == 9987 ? 9985 : 9984); // GL_LINEAR_MIPMAP_NEAREST or GL_NEAREST_MIPMAP_NEAREST
		}

		net.irisshaders.iris.gl.IrisRenderSystem.texParameteri(this.id, target, pname, newId);
	}

	public int glId() {
		return this.id;
	}

	// VulkanicTexture implementation (bridge to GpuTexture superclass)
	@Override
	public long getNativeHandle() {
		return this.id;
	}

	@Override
	public int getWidth() {
		return super.getWidth(0);
	}

	@Override
	public int getHeight() {
		return super.getHeight(0);
	}

	@Override
	public int getUsage() {
		return super.usage();
	}

	@Override
	public net.vulkanic.resources.VulkanicTextureFormat getVulkanicFormat() {
		return this.vulkanicFormat;
	}

	// Iris: From MixinGpuTexture - GpuTextureInterface implementation
	@Override
	public int iris$getGlId() {
		this.flushModeChanges(VulkanicAPI.GL_TEXTURE_2D);
		return this.glId();
	}
	
	@Override
	public void iris$markMipmapNonLinear() {
		boolean wasNonLinear = this.iris$mipmapNonLinear;
		this.iris$mipmapNonLinear = true;
		this.modesDirty = modesDirty || !wasNonLinear;
	}

	@Override
	public void iris$copyStateTo(GpuTexture texture) {
		texture.setTextureFilter(this.minFilter, this.magFilter, this.useMipmaps);
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

	public void addViews() {
		this.views++;
	}

	public void removeViews() {
		this.views--;
		if (this.closed && this.views == 0) {
			this.destroyImmediately();
		}
	}
}
