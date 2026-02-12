package net.irisshaders.iris.gl.texture;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public enum DepthBufferFormat {
	DEPTH(false),
	DEPTH16(false),
	DEPTH24(false),
	DEPTH32(false),
	DEPTH32F(false),
	DEPTH_STENCIL(true),
	DEPTH24_STENCIL8(true),
	DEPTH32F_STENCIL8(true);

	// GL constants for depth buffer formats (from GL30C, GL43C)
	private static final int GL_DEPTH_COMPONENT = 0x1902;
	private static final int GL_DEPTH_COMPONENT16 = 0x81A5;
	private static final int GL_DEPTH_COMPONENT24 = 0x81A6;
	private static final int GL_DEPTH_COMPONENT32 = 0x81A7;
	private static final int GL_DEPTH_COMPONENT32F = 0x8CAC;
	private static final int GL_DEPTH_STENCIL = 0x84F9;
	private static final int GL_DEPTH24_STENCIL8 = 0x88F0;
	private static final int GL_DEPTH32F_STENCIL8 = 0x8CAD;
	private static final int GL_UNSIGNED_SHORT = 0x1403;
	private static final int GL_UNSIGNED_INT = 0x1405;
	private static final int GL_FLOAT = 0x1406;
	private static final int GL_UNSIGNED_INT_24_8 = 0x84FA;
	private static final int GL_FLOAT_32_UNSIGNED_INT_24_8_REV = 0x8DAD;

	private final boolean combinedStencil;

	DepthBufferFormat(boolean combinedStencil) {
		this.combinedStencil = combinedStencil;
	}

	@Nullable
	public static DepthBufferFormat fromGlEnum(int glenum) {
		return switch (glenum) {
			case GL_DEPTH_COMPONENT -> DepthBufferFormat.DEPTH;
			case GL_DEPTH_COMPONENT16 -> DepthBufferFormat.DEPTH16;
			case GL_DEPTH_COMPONENT24 -> DepthBufferFormat.DEPTH24;
			case GL_DEPTH_COMPONENT32 -> DepthBufferFormat.DEPTH32;
			case GL_DEPTH_COMPONENT32F -> DepthBufferFormat.DEPTH32F;
			case GL_DEPTH_STENCIL -> DepthBufferFormat.DEPTH_STENCIL;
			case GL_DEPTH24_STENCIL8 -> DepthBufferFormat.DEPTH24_STENCIL8;
			case GL_DEPTH32F_STENCIL8 -> DepthBufferFormat.DEPTH32F_STENCIL8;
			default -> null;
		};
	}

	public static DepthBufferFormat fromGlEnumOrDefault(int glenum) {
		DepthBufferFormat format = fromGlEnum(glenum);
		// yolo, just assume it's GL_DEPTH_COMPONENT
		return Objects.requireNonNullElse(format, DepthBufferFormat.DEPTH);
	}

	public int getGlInternalFormat() {
		return switch (this) {
			case DEPTH -> GL_DEPTH_COMPONENT;
			case DEPTH16 -> GL_DEPTH_COMPONENT16;
			case DEPTH24 -> GL_DEPTH_COMPONENT24;
			case DEPTH32 -> GL_DEPTH_COMPONENT32;
			case DEPTH32F -> GL_DEPTH_COMPONENT32F;
			case DEPTH_STENCIL -> GL_DEPTH_STENCIL;
			case DEPTH24_STENCIL8 -> GL_DEPTH24_STENCIL8;
			case DEPTH32F_STENCIL8 -> GL_DEPTH32F_STENCIL8;
		};

	}

	public int getGlType() {
		return isCombinedStencil() ? GL_DEPTH_STENCIL : GL_DEPTH_COMPONENT;
	}

	public int getGlFormat() {
		return switch (this) {
			case DEPTH, DEPTH16 -> GL_UNSIGNED_SHORT;
			case DEPTH24, DEPTH32 -> GL_UNSIGNED_INT;
			case DEPTH32F -> GL_FLOAT;
			case DEPTH_STENCIL, DEPTH24_STENCIL8 -> GL_UNSIGNED_INT_24_8;
			case DEPTH32F_STENCIL8 -> GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
		};

	}

	public boolean isCombinedStencil() {
		return combinedStencil;
	}
}
