package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral polygon-face selector for rasterization state.
 */
public enum VulkanicPolygonFace {
    FRONT,
    BACK,
    FRONT_AND_BACK;

    /**
     * Converts a legacy GL polygon-face constant into a typed value when known.
     */
    public static Optional<VulkanicPolygonFace> fromLegacyGlConstant(int constant) {
        return switch (constant) {
            case VulkanicAPI.GL_FRONT -> Optional.of(FRONT);
            case VulkanicAPI.GL_BACK -> Optional.of(BACK);
            case VulkanicAPI.GL_FRONT_AND_BACK -> Optional.of(FRONT_AND_BACK);
            default -> Optional.empty();
        };
    }

    /**
     * Returns the corresponding Vulkanic/OpenGL polygon-face constant.
     */
    public int toGlFaceConstant() {
        return switch (this) {
            case FRONT -> VulkanicAPI.GL_FRONT;
            case BACK -> VulkanicAPI.GL_BACK;
            case FRONT_AND_BACK -> VulkanicAPI.GL_FRONT_AND_BACK;
        };
    }
}