package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral pixel upload tuple for legacy 2D texture image paths.
 *
 * <p>This keeps OpenGL-shaped integer triplets ({@code internalFormat},
 * {@code format}, {@code type}) out of high-traffic callsites while preserving
 * compatibility with legacy upload entry points.</p>
 */
public enum VulkanicTextureUploadFormat {
    RGBA8_UNORM(VulkanicAPI.GL_RGBA8, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_UNSIGNED_BYTE),
    RGB8_UNORM(VulkanicAPI.GL_RGB, VulkanicAPI.GL_RGB, VulkanicAPI.GL_UNSIGNED_BYTE),
    RED8_UNORM(VulkanicAPI.GL_R8, VulkanicAPI.GL_RED, VulkanicAPI.GL_UNSIGNED_BYTE),
    RED8_SINT(VulkanicAPI.GL_R8I, VulkanicAPI.GL_RED_INTEGER, VulkanicAPI.GL_BYTE),
    RED16_SFLOAT(VulkanicAPI.GL_R16F, VulkanicAPI.GL_RED, VulkanicAPI.GL_HALF_FLOAT),
    RED32_SFLOAT(VulkanicAPI.GL_R32F, VulkanicAPI.GL_RED, VulkanicAPI.GL_FLOAT),
    RGBA16_SFLOAT(VulkanicAPI.GL_RGBA16F, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_HALF_FLOAT),
    // GL_LUMINANCE=0x1909 and GL_LUMINANCE_ALPHA=0x190A are inlined as literals because
    // enum constant constructors execute before static field initializers in Java.
    LUMINANCE8_UNORM(0x1909, 0x1909, VulkanicAPI.GL_UNSIGNED_BYTE),
    LUMINANCE8_ALPHA8_UNORM(0x190A, 0x190A, VulkanicAPI.GL_UNSIGNED_BYTE),
    DEPTH32_SFLOAT(VulkanicAPI.GL_DEPTH_COMPONENT32F, VulkanicAPI.GL_DEPTH_COMPONENT, VulkanicAPI.GL_FLOAT);

    // Named constants for use in fromLegacyGlTuple method body (called after class init completes)
    private static final int GL_LUMINANCE = 0x1909;
    private static final int GL_LUMINANCE_ALPHA = 0x190A;

    private final int legacyInternalFormat;
    private final int legacyFormat;
    private final int legacyType;

    VulkanicTextureUploadFormat(int legacyInternalFormat, int legacyFormat, int legacyType) {
        this.legacyInternalFormat = legacyInternalFormat;
        this.legacyFormat = legacyFormat;
        this.legacyType = legacyType;
    }

    public int legacyInternalFormat() {
        return legacyInternalFormat;
    }

    public int legacyFormat() {
        return legacyFormat;
    }

    public int legacyType() {
        return legacyType;
    }

    public static Optional<VulkanicTextureUploadFormat> fromLegacyGlTuple(int internalFormat, int format, int type) {
        // Exact match only: all three fields must match the enum entry's canonical tuple.
        // Overly-broad OR conditions are intentionally avoided here because they silently
        // change the internalFormat (e.g. GL_RGBA16 → GL_RGBA8, GL_RGBA → GL_RGBA8,
        // GL_RGB16 → GL_RGB) which corrupts higher-precision render targets and causes
        // visual regressions (e.g. white/transparent water surfaces in shader packs
        // that use elevated-precision colortex buffers).
        for (VulkanicTextureUploadFormat fmt : values()) {
            if (fmt.legacyInternalFormat == internalFormat
                    && fmt.legacyFormat == format
                    && fmt.legacyType == type) {
                return Optional.of(fmt);
            }
        }
        return Optional.empty();
    }
}