package net.irisshaders.iris.gl.texture;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureTarget;

import java.nio.ByteBuffer;
import java.util.Optional;

public enum TextureType {
	TEXTURE_1D(VulkanicAPI.GL_TEXTURE_1D),
	TEXTURE_2D(VulkanicAPI.GL_TEXTURE_2D),
	TEXTURE_3D(VulkanicAPI.GL_TEXTURE_3D),
	TEXTURE_RECTANGLE(VulkanicAPI.GL_TEXTURE_RECTANGLE);

	private final int glType;

	TextureType(int glType) {
		this.glType = glType;
	}

	public static Optional<TextureType> fromString(String name) {
		try {
			return Optional.of(TextureType.valueOf(name));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

}
