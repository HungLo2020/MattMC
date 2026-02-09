package net.irisshaders.iris.gl.texture;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.GlVersion;

import java.util.Locale;
import java.util.Optional;

public enum PixelFormat {
	RED(1, 0x1903, GlVersion.GL_11, false),       // GL11C.GL_RED
	RG(2, 0x8227, GlVersion.GL_30, false),        // GL30C.GL_RG
	RGB(3, 0x1907, GlVersion.GL_11, false),       // GL11C.GL_RGB
	BGR(3, 0x80E0, GlVersion.GL_12, false),       // GL12C.GL_BGR
	RGBA(4, 0x1908, GlVersion.GL_11, false),      // GL11C.GL_RGBA
	BGRA(4, 0x80E1, GlVersion.GL_12, false),      // GL12C.GL_BGRA
	RED_INTEGER(1, 0x8D94, GlVersion.GL_30, true),    // GL30C.GL_RED_INTEGER
	RG_INTEGER(2, 0x8228, GlVersion.GL_30, true),     // GL30C.GL_RG_INTEGER
	RGB_INTEGER(3, 0x8D98, GlVersion.GL_30, true),    // GL30C.GL_RGB_INTEGER
	BGR_INTEGER(3, 0x8D9A, GlVersion.GL_30, true),    // GL30C.GL_BGR_INTEGER
	RGBA_INTEGER(4, 0x8D99, GlVersion.GL_30, true),   // GL30C.GL_RGBA_INTEGER
	BGRA_INTEGER(4, 0x8D9B, GlVersion.GL_30, true);   // GL30C.GL_BGRA_INTEGER

	private final int componentCount;
	private final int glFormat;
	private final GlVersion minimumGlVersion;
	private final boolean isInteger;

	PixelFormat(int componentCount, int glFormat, GlVersion minimumGlVersion, boolean isInteger) {
		this.componentCount = componentCount;
		this.glFormat = glFormat;
		this.minimumGlVersion = minimumGlVersion;
		this.isInteger = isInteger;
	}

	public static Optional<PixelFormat> fromString(String name) {
		try {
			return Optional.of(PixelFormat.valueOf(name.toUpperCase(Locale.US)));
		} catch (IllegalArgumentException e) {
			Iris.logger.error("Looking for an illegal pixel format: " + name.toUpperCase(Locale.US));
			return Optional.empty();
		}
	}

	public int getComponentCount() {
		return componentCount;
	}

	public int getGlFormat() {
		return glFormat;
	}

	public GlVersion getMinimumGlVersion() {
		return minimumGlVersion;
	}

	public boolean isInteger() {
		return isInteger;
	}
}
