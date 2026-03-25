package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral stencil operation.
 */
public enum VulkanicStencilOperation {
    KEEP,
    ZERO,
    REPLACE,
    INCREMENT_CLAMP,
    DECREMENT_CLAMP,
    INVERT,
    INCREMENT_WRAP,
    DECREMENT_WRAP;

    /**
     * Converts a legacy GL stencil-op constant into a typed operation when known.
     */
    public static Optional<VulkanicStencilOperation> fromLegacyGlConstant(int constant) {
        return switch (constant) {
            case VulkanicAPI.GL_KEEP -> Optional.of(KEEP);
            case VulkanicAPI.GL_ZERO -> Optional.of(ZERO);
            case VulkanicAPI.GL_REPLACE -> Optional.of(REPLACE);
            case VulkanicAPI.GL_INCR -> Optional.of(INCREMENT_CLAMP);
            case VulkanicAPI.GL_DECR -> Optional.of(DECREMENT_CLAMP);
            case VulkanicAPI.GL_INVERT -> Optional.of(INVERT);
            case VulkanicAPI.GL_INCR_WRAP -> Optional.of(INCREMENT_WRAP);
            case VulkanicAPI.GL_DECR_WRAP -> Optional.of(DECREMENT_WRAP);
            default -> Optional.empty();
        };
    }
}
