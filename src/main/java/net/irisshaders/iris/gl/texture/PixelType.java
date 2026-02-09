package net.irisshaders.iris.gl.texture;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.GlVersion;

import java.util.Locale;
import java.util.Optional;

public enum PixelType {
	BYTE(1, 0x1400, GlVersion.GL_11),                          // GL11C.GL_BYTE
	SHORT(2, 0x1402, GlVersion.GL_11),                         // GL11C.GL_SHORT
	INT(4, 0x1404, GlVersion.GL_11),                           // GL11C.GL_INT
	HALF_FLOAT(2, 0x140B, GlVersion.GL_30),                    // GL30C.GL_HALF_FLOAT
	FLOAT(4, 0x1406, GlVersion.GL_11),                         // GL11C.GL_FLOAT
	UNSIGNED_BYTE(1, 0x1401, GlVersion.GL_11),                 // GL11C.GL_UNSIGNED_BYTE
	UNSIGNED_BYTE_3_3_2(1, 0x8032, GlVersion.GL_12),           // GL12C.GL_UNSIGNED_BYTE_3_3_2
	UNSIGNED_BYTE_2_3_3_REV(1, 0x8362, GlVersion.GL_12),       // GL12C.GL_UNSIGNED_BYTE_2_3_3_REV
	UNSIGNED_SHORT(2, 0x1403, GlVersion.GL_11),                // GL11C.GL_UNSIGNED_SHORT
	UNSIGNED_SHORT_5_6_5(2, 0x8363, GlVersion.GL_12),          // GL12C.GL_UNSIGNED_SHORT_5_6_5
	UNSIGNED_SHORT_5_6_5_REV(2, 0x8364, GlVersion.GL_12),      // GL12C.GL_UNSIGNED_SHORT_5_6_5_REV
	UNSIGNED_SHORT_4_4_4_4(2, 0x8033, GlVersion.GL_12),        // GL12C.GL_UNSIGNED_SHORT_4_4_4_4
	UNSIGNED_SHORT_4_4_4_4_REV(2, 0x8365, GlVersion.GL_12),    // GL12C.GL_UNSIGNED_SHORT_4_4_4_4_REV
	UNSIGNED_SHORT_5_5_5_1(2, 0x8034, GlVersion.GL_12),        // GL12C.GL_UNSIGNED_SHORT_5_5_5_1
	UNSIGNED_SHORT_1_5_5_5_REV(2, 0x8366, GlVersion.GL_12),    // GL12C.GL_UNSIGNED_SHORT_1_5_5_5_REV
	UNSIGNED_INT(4, 0x1405, GlVersion.GL_11),                  // GL11C.GL_UNSIGNED_INT
	UNSIGNED_INT_8_8_8_8(4, 0x8035, GlVersion.GL_12),          // GL12C.GL_UNSIGNED_INT_8_8_8_8
	UNSIGNED_INT_8_8_8_8_REV(4, 0x8367, GlVersion.GL_12),      // GL12C.GL_UNSIGNED_INT_8_8_8_8_REV
	UNSIGNED_INT_10_10_10_2(4, 0x8036, GlVersion.GL_12),       // GL12C.GL_UNSIGNED_INT_10_10_10_2
	UNSIGNED_INT_2_10_10_10_REV(4, 0x8368, GlVersion.GL_12),   // GL12C.GL_UNSIGNED_INT_2_10_10_10_REV
	UNSIGNED_INT_10F_11F_11F_REV(4, 0x8C3B, GlVersion.GL_30),  // GL30C.GL_UNSIGNED_INT_10F_11F_11F_REV
	UNSIGNED_INT_5_9_9_9_REV(4, 0x8C3E, GlVersion.GL_30);      // GL30C.GL_UNSIGNED_INT_5_9_9_9_REV

	private final int byteSize;
	private final int glFormat;
	private final GlVersion minimumGlVersion;

	PixelType(int byteSize, int glFormat, GlVersion minimumGlVersion) {
		this.byteSize = byteSize;
		this.glFormat = glFormat;
		this.minimumGlVersion = minimumGlVersion;
	}

	public static Optional<PixelType> fromString(String name) {
		try {
			return Optional.of(PixelType.valueOf(name.toUpperCase(Locale.US)));
		} catch (IllegalArgumentException e) {
			Iris.logger.error("Failed to find pixel type " + name.toUpperCase(Locale.ROOT));
			return Optional.empty();
		}
	}

	public int getByteSize() {
		return byteSize;
	}

	public int getGlFormat() {
		return glFormat;
	}

	public GlVersion getMinimumGlVersion() {
		return minimumGlVersion;
	}
}
