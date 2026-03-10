package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral stencil-face selector.
 */
public enum VulkanicStencilFace {
    FRONT,
    BACK,
    FRONT_AND_BACK;

    /**
     * Converts a legacy GL stencil-face constant into a typed face selector when known.
     */
    public static Optional<VulkanicStencilFace> fromLegacyGlConstant(int constant) {
        return switch (constant) {
            case VulkanicAPI.GL_FRONT -> Optional.of(FRONT);
            case VulkanicAPI.GL_BACK -> Optional.of(BACK);
            case VulkanicAPI.GL_FRONT_AND_BACK -> Optional.of(FRONT_AND_BACK);
            default -> Optional.empty();
        };
    }
}
