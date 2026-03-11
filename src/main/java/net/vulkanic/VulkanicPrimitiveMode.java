package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral primitive topology for draw commands.
 */
public enum VulkanicPrimitiveMode {
    LINES,
    TRIANGLES,
    TRIANGLE_FAN;

    /**
     * Converts a legacy GL primitive mode constant into a typed mode when known.
     */
    public static Optional<VulkanicPrimitiveMode> fromLegacyGlConstant(int constant) {
        return switch (constant) {
            case VulkanicAPI.GL_LINES -> Optional.of(LINES);
            case VulkanicAPI.GL_TRIANGLES -> Optional.of(TRIANGLES);
            case VulkanicAPI.GL_TRIANGLE_FAN -> Optional.of(TRIANGLE_FAN);
            default -> Optional.empty();
        };
    }

    /**
     * Returns the corresponding Vulkanic/OpenGL primitive mode constant.
     */
    public int toGlModeConstant() {
        return switch (this) {
            case LINES -> VulkanicAPI.GL_LINES;
            case TRIANGLES -> VulkanicAPI.GL_TRIANGLES;
            case TRIANGLE_FAN -> VulkanicAPI.GL_TRIANGLE_FAN;
        };
    }
}