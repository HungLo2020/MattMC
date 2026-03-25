package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral blend factor.
 */
public enum VulkanicBlendFactor {
    ZERO,
    ONE,
    SRC_COLOR,
    ONE_MINUS_SRC_COLOR,
    DST_COLOR,
    ONE_MINUS_DST_COLOR,
    SRC_ALPHA,
    ONE_MINUS_SRC_ALPHA,
    DST_ALPHA,
    ONE_MINUS_DST_ALPHA,
    SRC_ALPHA_SATURATE,
    CONSTANT_COLOR,
    ONE_MINUS_CONSTANT_COLOR,
    CONSTANT_ALPHA,
    ONE_MINUS_CONSTANT_ALPHA;

    /**
     * Converts a legacy GL blend-factor constant into a typed factor when known.
     */
    public static Optional<VulkanicBlendFactor> fromLegacyGlConstant(int constant) {
        return switch (constant) {
            case VulkanicAPI.GL_ZERO -> Optional.of(ZERO);
            case VulkanicAPI.GL_ONE -> Optional.of(ONE);
            case VulkanicAPI.GL_SRC_COLOR -> Optional.of(SRC_COLOR);
            case VulkanicAPI.GL_ONE_MINUS_SRC_COLOR -> Optional.of(ONE_MINUS_SRC_COLOR);
            case VulkanicAPI.GL_DST_COLOR -> Optional.of(DST_COLOR);
            case VulkanicAPI.GL_ONE_MINUS_DST_COLOR -> Optional.of(ONE_MINUS_DST_COLOR);
            case VulkanicAPI.GL_SRC_ALPHA -> Optional.of(SRC_ALPHA);
            case VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA -> Optional.of(ONE_MINUS_SRC_ALPHA);
            case VulkanicAPI.GL_DST_ALPHA -> Optional.of(DST_ALPHA);
            case VulkanicAPI.GL_ONE_MINUS_DST_ALPHA -> Optional.of(ONE_MINUS_DST_ALPHA);
            case VulkanicAPI.GL_SRC_ALPHA_SATURATE -> Optional.of(SRC_ALPHA_SATURATE);
            case VulkanicAPI.GL_CONSTANT_COLOR -> Optional.of(CONSTANT_COLOR);
            case VulkanicAPI.GL_ONE_MINUS_CONSTANT_COLOR -> Optional.of(ONE_MINUS_CONSTANT_COLOR);
            case VulkanicAPI.GL_CONSTANT_ALPHA -> Optional.of(CONSTANT_ALPHA);
            case VulkanicAPI.GL_ONE_MINUS_CONSTANT_ALPHA -> Optional.of(ONE_MINUS_CONSTANT_ALPHA);
            default -> Optional.empty();
        };
    }
}
