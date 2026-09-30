package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral primitive topology for draw commands.
 */
public enum VulkanicPrimitiveMode {
    LINES,
    TRIANGLES,
    TRIANGLE_FAN,
    PATCHES;

    /**
     * Returns the corresponding Vulkanic/OpenGL primitive mode constant.
     */
    public int toGlModeConstant() {
        return switch (this) {
            case LINES -> VulkanicAPI.GL_LINES;
            case TRIANGLES -> VulkanicAPI.GL_TRIANGLES;
            case TRIANGLE_FAN -> VulkanicAPI.GL_TRIANGLE_FAN;
            case PATCHES -> VulkanicAPI.GL_PATCHES;
        };
    }
}