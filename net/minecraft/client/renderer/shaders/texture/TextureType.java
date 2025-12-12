package net.minecraft.client.renderer.shaders.texture;

import org.lwjgl.opengl.ARBTextureRectangle;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * OpenGL texture types.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/gl/texture/TextureType.java
 */
public enum TextureType {
	TEXTURE_1D(GL11.GL_TEXTURE_1D),
	TEXTURE_2D(GL11.GL_TEXTURE_2D),
	TEXTURE_3D(GL12.GL_TEXTURE_3D),
	TEXTURE_RECTANGLE(ARBTextureRectangle.GL_TEXTURE_RECTANGLE_ARB);

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

	public int getGlType() {
		return glType;
	}

	public void apply(int texture, int sizeX, int sizeY, int sizeZ, int internalFormat, int format, int pixelType, ByteBuffer pixels) {
		switch (this) {
			case TEXTURE_1D:
				GL11.glTexImage1D(getGlType(), 0, internalFormat, sizeX, 0, format, pixelType, pixels);
				break;
			case TEXTURE_2D, TEXTURE_RECTANGLE:
				GL11.glTexImage2D(getGlType(), 0, internalFormat, sizeX, sizeY, 0, format, pixelType, pixels);
				break;
			case TEXTURE_3D:
				GL12.glTexImage3D(getGlType(), 0, internalFormat, sizeX, sizeY, sizeZ, 0, format, pixelType, pixels);
				break;
		}
	}
}
