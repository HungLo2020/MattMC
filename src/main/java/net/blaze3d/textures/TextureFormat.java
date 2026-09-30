package net.blaze3d.textures;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public enum TextureFormat {
	RGBA8(4),
	BGRA8(4),
	RED8(1),
	RED8I(1),
	DEPTH32(4),
	DEPTH24_STENCIL8(4),
	DEPTH32F_STENCIL8(8);

	private final int pixelSize;

	private TextureFormat(final int j) {
		this.pixelSize = j;
	}

}
