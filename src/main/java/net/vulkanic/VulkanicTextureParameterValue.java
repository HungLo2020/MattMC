package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral texture parameter value for common filter and wrap settings.
 */
public enum VulkanicTextureParameterValue {
    NEAREST,
    LINEAR,
    NEAREST_MIPMAP_NEAREST,
    LINEAR_MIPMAP_LINEAR,
    CLAMP_TO_EDGE,
    REPEAT;

    /**
     * Converts this typed value to its legacy GL constant.
     */
    public int toLegacyGlConstant() {
        return switch (this) {
            case NEAREST -> VulkanicAPI.GL_NEAREST;
            case LINEAR -> VulkanicAPI.GL_LINEAR;
            case NEAREST_MIPMAP_NEAREST -> VulkanicAPI.GL_NEAREST_MIPMAP_NEAREST;
            case LINEAR_MIPMAP_LINEAR -> VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR;
            case CLAMP_TO_EDGE -> VulkanicAPI.GL_CLAMP_TO_EDGE;
            case REPEAT -> VulkanicAPI.GL_REPEAT;
        };
    }

    /**
     * Converts a legacy GL texture parameter value to a typed value when known.
     */
    public static Optional<VulkanicTextureParameterValue> fromLegacyGlConstant(int glConstant) {
        return switch (glConstant) {
            case VulkanicAPI.GL_NEAREST -> Optional.of(NEAREST);
            case VulkanicAPI.GL_LINEAR -> Optional.of(LINEAR);
            case VulkanicAPI.GL_NEAREST_MIPMAP_NEAREST -> Optional.of(NEAREST_MIPMAP_NEAREST);
            case VulkanicAPI.GL_LINEAR_MIPMAP_LINEAR -> Optional.of(LINEAR_MIPMAP_LINEAR);
            case VulkanicAPI.GL_CLAMP_TO_EDGE -> Optional.of(CLAMP_TO_EDGE);
            case VulkanicAPI.GL_REPEAT -> Optional.of(REPEAT);
            default -> Optional.empty();
        };
    }
}