package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral depth comparison operation.
 */
public enum VulkanicDepthCompareOp {
    NEVER,
    LESS,
    EQUAL,
    LEQUAL,
    GREATER,
    NOTEQUAL,
    GEQUAL,
    ALWAYS;

    /**
     * Converts a legacy GL depth compare constant into a typed op when known.
     */
    public static Optional<VulkanicDepthCompareOp> fromLegacyGlConstant(int constant) {
        return switch (constant) {
            case VulkanicAPI.GL_NEVER -> Optional.of(NEVER);
            case VulkanicAPI.GL_LESS -> Optional.of(LESS);
            case VulkanicAPI.GL_EQUAL -> Optional.of(EQUAL);
            case VulkanicAPI.GL_LEQUAL -> Optional.of(LEQUAL);
            case VulkanicAPI.GL_GREATER -> Optional.of(GREATER);
            case VulkanicAPI.GL_NOTEQUAL -> Optional.of(NOTEQUAL);
            case VulkanicAPI.GL_GEQUAL -> Optional.of(GEQUAL);
            case VulkanicAPI.GL_ALWAYS -> Optional.of(ALWAYS);
            default -> Optional.empty();
        };
    }
}