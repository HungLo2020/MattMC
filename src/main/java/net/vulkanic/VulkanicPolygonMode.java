package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral polygon rasterization mode.
 */
public enum VulkanicPolygonMode {
    POINT,
    LINE,
    FILL;

    /**
     * Converts a legacy GL polygon mode constant into a typed mode when known.
     */
    public static Optional<VulkanicPolygonMode> fromLegacyGlConstant(int constant) {
        return switch (constant) {
            case VulkanicAPI.GL_POINT -> Optional.of(POINT);
            case VulkanicAPI.GL_LINE -> Optional.of(LINE);
            case VulkanicAPI.GL_FILL -> Optional.of(FILL);
            default -> Optional.empty();
        };
    }

    /**
     * Returns the corresponding Vulkanic/OpenGL polygon mode constant.
     */
    public int toGlModeConstant() {
        return switch (this) {
            case POINT -> VulkanicAPI.GL_POINT;
            case LINE -> VulkanicAPI.GL_LINE;
            case FILL -> VulkanicAPI.GL_FILL;
        };
    }
}