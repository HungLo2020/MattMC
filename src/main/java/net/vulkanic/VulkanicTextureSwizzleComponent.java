package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral swizzle component value for texture channel remapping.
 */
public enum VulkanicTextureSwizzleComponent {
    RED,
    GREEN,
    BLUE,
    ALPHA,
    ZERO,
    ONE;

    /**
     * Converts this swizzle component to its legacy GL constant.
     */
    public int toLegacyGlConstant() {
        return switch (this) {
            case RED -> VulkanicAPI.GL_RED;
            case GREEN -> VulkanicAPI.GL_GREEN;
            case BLUE -> VulkanicAPI.GL_BLUE;
            case ALPHA -> VulkanicAPI.GL_ALPHA;
            case ZERO -> VulkanicAPI.GL_ZERO;
            case ONE -> VulkanicAPI.GL_ONE;
        };
    }

    /**
     * Converts a legacy GL swizzle component constant to a typed component when known.
     */
    public static Optional<VulkanicTextureSwizzleComponent> fromLegacyGlConstant(int glConstant) {
        return switch (glConstant) {
            case VulkanicAPI.GL_RED -> Optional.of(RED);
            case VulkanicAPI.GL_GREEN -> Optional.of(GREEN);
            case VulkanicAPI.GL_BLUE -> Optional.of(BLUE);
            case VulkanicAPI.GL_ALPHA -> Optional.of(ALPHA);
            case VulkanicAPI.GL_ZERO -> Optional.of(ZERO);
            case VulkanicAPI.GL_ONE -> Optional.of(ONE);
            default -> Optional.empty();
        };
    }
}