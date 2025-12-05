package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class GlTextureView extends GpuTextureView {
	private boolean closed;

	protected GlTextureView(GlTexture glTexture, int i, int j) {
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

	public GlTexture texture() {
		return (GlTexture)super.texture();
	}
}
