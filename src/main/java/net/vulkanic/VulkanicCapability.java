package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral state capability toggle key.
 */
public enum VulkanicCapability {
    BLEND,
    CULL_FACE,
    DEPTH_TEST,
    SCISSOR_TEST,
    POLYGON_OFFSET_FILL,
    COLOR_LOGIC_OP,
    PROGRAM_POINT_SIZE,
    DEBUG_OUTPUT,
    DEBUG_OUTPUT_SYNCHRONOUS,
    STENCIL_TEST;

    /**
     * Converts a legacy GL capability constant to a typed capability when known.
     */
    public static Optional<VulkanicCapability> fromLegacyGlConstant(int cap) {
        return switch (cap) {
            case VulkanicAPI.GL_BLEND -> Optional.of(BLEND);
            case VulkanicAPI.GL_CULL_FACE -> Optional.of(CULL_FACE);
            case VulkanicAPI.GL_DEPTH_TEST -> Optional.of(DEPTH_TEST);
            case VulkanicAPI.GL_SCISSOR_TEST -> Optional.of(SCISSOR_TEST);
            case VulkanicAPI.GL_POLYGON_OFFSET_FILL -> Optional.of(POLYGON_OFFSET_FILL);
            case VulkanicAPI.GL_COLOR_LOGIC_OP -> Optional.of(COLOR_LOGIC_OP);
            case VulkanicAPI.GL_PROGRAM_POINT_SIZE -> Optional.of(PROGRAM_POINT_SIZE);
            case VulkanicAPI.GL_DEBUG_OUTPUT -> Optional.of(DEBUG_OUTPUT);
            case VulkanicAPI.GL_DEBUG_OUTPUT_SYNCHRONOUS -> Optional.of(DEBUG_OUTPUT_SYNCHRONOUS);
            case VulkanicAPI.GL_STENCIL_TEST -> Optional.of(STENCIL_TEST);
            default -> Optional.empty();
        };
    }
}