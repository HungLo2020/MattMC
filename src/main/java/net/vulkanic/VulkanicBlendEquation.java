package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral blend equation.
 */
public enum VulkanicBlendEquation {
    ADD,
    SUBTRACT,
    REVERSE_SUBTRACT,
    MIN,
    MAX;

    /**
     * Converts a legacy GL blend-equation constant into a typed equation when known.
     */
    public static Optional<VulkanicBlendEquation> fromLegacyGlConstant(int constant) {
        return switch (constant) {
            case VulkanicAPI.GL_FUNC_ADD -> Optional.of(ADD);
            case VulkanicAPI.GL_FUNC_SUBTRACT -> Optional.of(SUBTRACT);
            case VulkanicAPI.GL_FUNC_REVERSE_SUBTRACT -> Optional.of(REVERSE_SUBTRACT);
            case VulkanicAPI.GL_MIN -> Optional.of(MIN);
            case VulkanicAPI.GL_MAX -> Optional.of(MAX);
            default -> Optional.empty();
        };
    }
}
