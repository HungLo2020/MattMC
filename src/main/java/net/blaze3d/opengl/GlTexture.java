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
import net.logging.LogUtils;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;
import net.vulkanic.VulkanicTextureTarget;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlTexture extends GpuTexture {
	private static final Logger LOGGER = LogUtils.getLogger();
	protected final int id;
	private final Int2IntMap fboCache = new Int2IntOpenHashMap();
	protected boolean closed;
	protected boolean modesDirty = true;
	private int views;
	
	// Iris: From MixinGpuTexture - mipmap non-linear flag
	private boolean iris$mipmapNonLinear;

	protected GlTexture(int i, String string, TextureFormat textureFormat, int j, int k, int l, int m, int n) {
		super(i, string, textureFormat, j, k, l, m);
		this.id = n;
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
		IntIterator var1 = this.fboCache.values().iterator();
		net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();

		while (var1.hasNext()) {
			int i = (Integer)var1.next();
			if (VulkanicAPI.getReadFramebufferBinding() == i) {
				VulkanicAPI.bindReadFramebuffer(ctx, 0);
			}

			if (VulkanicAPI.getDrawFramebufferBinding() == i) {
				VulkanicAPI.bindDrawFramebuffer(ctx, 0);
			}

			VulkanicAPI.deleteFramebuffer(ctx, i);
		}
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	public int getFbo(DirectStateAccess directStateAccess, @Nullable GpuTexture gpuTexture) {
		int i = gpuTexture == null ? 0 : ((GlTexture)gpuTexture).id;
		boolean depthStencil = gpuTexture != null && gpuTexture.getFormat().hasStencilAspect();
		int cacheKey = depthStencil ? i | Integer.MIN_VALUE : i;
		return this.fboCache.computeIfAbsent(cacheKey, (Int2IntFunction)(j -> {
			int k = directStateAccess.createFrameBufferObject();
			directStateAccess.bindFrameBufferTextures(k, this.id, i, 0, 0, depthStencil);
			LOGGER.info(
				"TACZ_SCOPE_DEBUG phase=gl-texture-fbo-create colorTexture={} colorFormat={} depthTexture={} depthFormat={} depthStencil={} fbo={} cacheKey={}",
				this.id,
				this.getFormat(),
				i,
				gpuTexture == null ? "none" : gpuTexture.getFormat(),
				depthStencil,
				k,
				cacheKey
			);
			return k;
		}));
	}

	public void flushModeChanges(int i) {
		if (this.modesDirty) {
			// Iris: From MixinGpuTexture - use IrisRenderSystem.texParameteri instead of GlStateManager._texParameter
			iris$texParameterDSA(i, VulkanicTextureParameterName.WRAP_S, toVulkanicTextureParameterValue(this.addressModeU));
			iris$texParameterDSA(i, VulkanicTextureParameterName.WRAP_T, toVulkanicTextureParameterValue(this.addressModeV));
			switch (this.minFilter) {
				case NEAREST:
					iris$texParameterDSA(
						i,
						VulkanicTextureParameterName.MIN_FILTER,
						this.useMipmaps ? VulkanicTextureParameterValue.NEAREST_MIPMAP_LINEAR : VulkanicTextureParameterValue.NEAREST
					);
					break;
				case LINEAR:
					iris$texParameterDSA(
						i,
						VulkanicTextureParameterName.MIN_FILTER,
						this.useMipmaps ? VulkanicTextureParameterValue.LINEAR_MIPMAP_LINEAR : VulkanicTextureParameterValue.LINEAR
					);
			}

			switch (this.magFilter) {
				case NEAREST:
					iris$texParameterDSA(i, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.NEAREST);
					break;
				case LINEAR:
					iris$texParameterDSA(i, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.LINEAR);
			}

			this.modesDirty = false;
		}
	}

	public void flushModeChanges(VulkanicTextureTarget target) {
		this.flushModeChanges(target.toLegacyGlTarget());
	}

	public void flushModeChanges2D() {
		this.flushModeChanges(VulkanicTextureTarget.TEXTURE_2D);
	}
	
	// Iris: From MixinGpuTexture - helper method for DSA texture parameter setting with mipmap non-linear handling
	private void iris$texParameterDSA(int target, VulkanicTextureParameterName pname, VulkanicTextureParameterValue param) {
		VulkanicTextureParameterValue effectiveParam = param;

		if (this.iris$mipmapNonLinear) {
			if (param == VulkanicTextureParameterValue.LINEAR_MIPMAP_LINEAR) {
				effectiveParam = VulkanicTextureParameterValue.LINEAR_MIPMAP_NEAREST;
			} else if (param == VulkanicTextureParameterValue.NEAREST_MIPMAP_LINEAR) {
				effectiveParam = VulkanicTextureParameterValue.NEAREST_MIPMAP_NEAREST;
			}
		}

		net.irisshaders.iris.gl.IrisRenderSystem.texParameteri(this.id, target, pname, effectiveParam);
	}

	private static VulkanicTextureParameterValue toVulkanicTextureParameterValue(AddressMode addressMode) {
		return switch (addressMode) {
			case REPEAT -> VulkanicTextureParameterValue.REPEAT;
			case CLAMP_TO_EDGE -> VulkanicTextureParameterValue.CLAMP_TO_EDGE;
		};
	}

	@Deprecated
	public int glId() {
		return this.id;
	}

	/**
	 * Returns the raw OpenGL texture object name for backend seam extraction.
	 *
	 * <p>Prefer backend-neutral handle resolution at callsites. This accessor exists
	 * so OpenGL backend bridge code can extract a concrete GL handle when required.</p>
	 */
	public int getGlHandle() {
		return this.id;
	}

	// Iris: From MixinGpuTexture - GpuTextureInterface implementation
	@Override
	@Deprecated
	public int iris$getGlId() {
		this.flushModeChanges2D();
		return this.id;
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
