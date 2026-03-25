package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral face-culling mode.
 */
public enum VulkanicCullFaceMode {
    FRONT,
    BACK,
    FRONT_AND_BACK;

    /**
     * Converts a legacy GL cull-face mode constant into a typed mode when known.
     */
    public static Optional<VulkanicCullFaceMode> fromLegacyGlConstant(int constant) {
        return switch (constant) {
            case VulkanicAPI.GL_FRONT -> Optional.of(FRONT);
            case VulkanicAPI.GL_BACK -> Optional.of(BACK);
            case VulkanicAPI.GL_FRONT_AND_BACK -> Optional.of(FRONT_AND_BACK);
            default -> Optional.empty();
        };
    }
}