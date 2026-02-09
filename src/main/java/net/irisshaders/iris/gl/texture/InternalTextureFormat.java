package net.irisshaders.iris.gl.texture;

import net.irisshaders.iris.gl.GlVersion;

import java.util.Locale;
import java.util.Optional;

public enum InternalTextureFormat {
	// Default
	// TODO: This technically shouldn't be exposed to shaders since it's not in the specification, it's the default anyways
	RGBA(0x1908, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),         // GL11C.GL_RGBA
	// 8-bit normalized
	R8(0x8229, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),           // GL30C.GL_R8
	RG8(0x822B, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),           // GL30C.GL_RG8
	RGB8(0x8051, GlVersion.GL_11, PixelFormat.RGB, ShaderDataType.FLOAT),         // GL11C.GL_RGB8
	RGBA8(0x8058, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),       // GL11C.GL_RGBA8
	// 8-bit signed normalized
	R8_SNORM(0x8F94, GlVersion.GL_31, PixelFormat.RED, ShaderDataType.FLOAT),     // GL31C.GL_R8_SNORM
	RG8_SNORM(0x8F95, GlVersion.GL_31, PixelFormat.RG, ShaderDataType.FLOAT),     // GL31C.GL_RG8_SNORM
	RGB8_SNORM(0x8F96, GlVersion.GL_31, PixelFormat.RGB, ShaderDataType.FLOAT),   // GL31C.GL_RGB8_SNORM
	RGBA8_SNORM(0x8F97, GlVersion.GL_31, PixelFormat.RGBA, ShaderDataType.FLOAT), // GL31C.GL_RGBA8_SNORM
	// 16-bit normalized
	R16(0x822A, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),          // GL30C.GL_R16
	RG16(0x822C, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),          // GL30C.GL_RG16
	RGB16(0x8054, GlVersion.GL_11, PixelFormat.RGB, ShaderDataType.FLOAT),        // GL11C.GL_RGB16
	RGBA16(0x805B, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),      // GL11C.GL_RGBA16
	// 16-bit signed normalized
	R16_SNORM(0x8F98, GlVersion.GL_31, PixelFormat.RED, ShaderDataType.FLOAT),    // GL31C.GL_R16_SNORM
	RG16_SNORM(0x8F99, GlVersion.GL_31, PixelFormat.RG, ShaderDataType.FLOAT),    // GL31C.GL_RG16_SNORM
	RGB16_SNORM(0x8F9A, GlVersion.GL_31, PixelFormat.RGB, ShaderDataType.FLOAT),  // GL31C.GL_RGB16_SNORM
	RGBA16_SNORM(0x8F9B, GlVersion.GL_31, PixelFormat.RGBA, ShaderDataType.FLOAT),// GL31C.GL_RGBA16_SNORM
	// 16-bit float
	R16F(0x822D, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),         // GL30C.GL_R16F
	RG16F(0x822F, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),         // GL30C.GL_RG16F
	RGB16F(0x881B, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT),       // GL30C.GL_RGB16F
	RGBA16F(0x881A, GlVersion.GL_30, PixelFormat.RGBA, ShaderDataType.FLOAT),     // GL30C.GL_RGBA16F
	// 32-bit float
	R32F(0x822E, GlVersion.GL_30, PixelFormat.RED, ShaderDataType.FLOAT),         // GL30C.GL_R32F
	RG32F(0x8230, GlVersion.GL_30, PixelFormat.RG, ShaderDataType.FLOAT),         // GL30C.GL_RG32F
	RGB32F(0x8815, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT),       // GL30C.GL_RGB32F
	RGBA32F(0x8814, GlVersion.GL_30, PixelFormat.RGBA, ShaderDataType.FLOAT),     // GL30C.GL_RGBA32F
	// 8-bit integer
	R8I(0x8231, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.INT),    // GL30C.GL_R8I
	RG8I(0x8237, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.INT),    // GL30C.GL_RG8I
	RGB8I(0x8D8F, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.INT),  // GL30C.GL_RGB8I
	RGBA8I(0x8D8E, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.INT),// GL30C.GL_RGBA8I
	// 8-bit unsigned integer
	R8UI(0x8232, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.UINT),  // GL30C.GL_R8UI
	RG8UI(0x8238, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.UINT),  // GL30C.GL_RG8UI
	RGB8UI(0x8D7D, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.UINT),// GL30C.GL_RGB8UI
	RGBA8UI(0x8D7C, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT),// GL30C.GL_RGBA8UI
	// 16-bit integer
	R16I(0x8233, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.INT),   // GL30C.GL_R16I
	RG16I(0x8239, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.INT),   // GL30C.GL_RG16I
	RGB16I(0x8D89, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.INT), // GL30C.GL_RGB16I
	RGBA16I(0x8D88, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.INT),// GL30C.GL_RGBA16I
	// 16-bit unsigned integer
	R16UI(0x8234, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.UINT), // GL30C.GL_R16UI
	RG16UI(0x823A, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.UINT), // GL30C.GL_RG16UI
	RGB16UI(0x8D77, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.UINT),// GL30C.GL_RGB16UI
	RGBA16UI(0x8D76, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT),// GL30C.GL_RGBA16UI
	// 32-bit integer
	R32I(0x8235, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.INT),   // GL30C.GL_R32I
	RG32I(0x823B, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.INT),   // GL30C.GL_RG32I
	RGB32I(0x8D83, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.INT), // GL30C.GL_RGB32I
	RGBA32I(0x8D82, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.INT),// GL30C.GL_RGBA32I
	// 32-bit unsigned integer
	R32UI(0x8236, GlVersion.GL_30, PixelFormat.RED_INTEGER, ShaderDataType.UINT), // GL30C.GL_R32UI
	RG32UI(0x823C, GlVersion.GL_30, PixelFormat.RG_INTEGER, ShaderDataType.UINT), // GL30C.GL_RG32UI
	RGB32UI(0x8D71, GlVersion.GL_30, PixelFormat.RGB_INTEGER, ShaderDataType.UINT),// GL30C.GL_RGB32UI
	RGBA32UI(0x8D70, GlVersion.GL_30, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT),// GL30C.GL_RGBA32UI
	// 2-bit normalized
	RGBA2(0x8055, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),       // GL11C.GL_RGBA2
	// 4-bit normalized
	RGBA4(0x8056, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),       // GL11C.GL_RGBA4
	// Mixed
	R3_G3_B2(0x2A10, GlVersion.GL_11, PixelFormat.RGB, ShaderDataType.FLOAT),     // GL11C.GL_R3_G3_B2
	RGB5_A1(0x8057, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),     // GL11C.GL_RGB5_A1
	RGB565(0x8D62, GlVersion.GL_41, PixelFormat.RGB, ShaderDataType.FLOAT),       // GL41C.GL_RGB565
	RGB10_A2(0x8059, GlVersion.GL_11, PixelFormat.RGBA, ShaderDataType.FLOAT),    // GL11C.GL_RGB10_A2
	RGB10_A2UI(0x906F, GlVersion.GL_33, PixelFormat.RGBA_INTEGER, ShaderDataType.UINT),// GL33C.GL_RGB10_A2UI
	R11F_G11F_B10F(0x8C3A, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT),// GL30C.GL_R11F_G11F_B10F
	RGB9_E5(0x8C3D, GlVersion.GL_30, PixelFormat.RGB, ShaderDataType.FLOAT);      // GL30C.GL_RGB9_E5

	private final int glFormat;
	private final GlVersion minimumGlVersion;
	private final PixelFormat expectedPixelFormat;
	private final ShaderDataType shaderDataType;

	InternalTextureFormat(int glFormat, GlVersion minimumGlVersion, PixelFormat expectedPixelFormat, ShaderDataType shaderDataType) {
		this.glFormat = glFormat;
		this.minimumGlVersion = minimumGlVersion;
		this.expectedPixelFormat = expectedPixelFormat;
		this.shaderDataType = shaderDataType;
	}

	public static Optional<InternalTextureFormat> fromString(String name) {
		try {
			return Optional.of(InternalTextureFormat.valueOf(name.toUpperCase(Locale.US)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public int getGlFormat() {
		return glFormat;
	}

	public PixelFormat getPixelFormat() {
		return expectedPixelFormat;
	}

	public GlVersion getMinimumGlVersion() {
		return minimumGlVersion;
	}

	public ShaderDataType getShaderDataType() {
		return shaderDataType;
	}
}
