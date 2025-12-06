package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlTexture extends GpuTexture {
	protected final int id;
	private final Int2IntMap fboCache = new Int2IntOpenHashMap();
	protected boolean closed;
	protected boolean modesDirty = true;
	private int views;

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
			GlStateManager._texParameter(i, 10242, GlConst.toGl(this.addressModeU));
			GlStateManager._texParameter(i, 10243, GlConst.toGl(this.addressModeV));
			switch (this.minFilter) {
				case NEAREST:
					GlStateManager._texParameter(i, 10241, this.useMipmaps ? 9986 : 9728);
					break;
				case LINEAR:
					GlStateManager._texParameter(i, 10241, this.useMipmaps ? 9987 : 9729);
			}

			switch (this.magFilter) {
				case NEAREST:
					GlStateManager._texParameter(i, 10240, 9728);
					break;
				case LINEAR:
					GlStateManager._texParameter(i, 10240, 9729);
			}

			this.modesDirty = false;
		}
	}

	public int glId() {
		return this.id;
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
