package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral shader stage identifier.
 */
public enum VulkanicShaderStage {
    VERTEX(VulkanicAPI.GL_VERTEX_SHADER),
    FRAGMENT(VulkanicAPI.GL_FRAGMENT_SHADER),
    GEOMETRY(VulkanicAPI.GL_GEOMETRY_SHADER),
    COMPUTE(VulkanicAPI.GL_COMPUTE_SHADER),
    TESSELLATION_CONTROL(VulkanicAPI.GL_TESS_CONTROL_SHADER),
    TESSELLATION_EVALUATION(VulkanicAPI.GL_TESS_EVALUATION_SHADER);

    private final int legacyGlShaderType;

    VulkanicShaderStage(int legacyGlShaderType) {
        this.legacyGlShaderType = legacyGlShaderType;
    }

    public int toLegacyGlShaderType() {
        return legacyGlShaderType;
    }

    public static Optional<VulkanicShaderStage> fromLegacyGlShaderType(int shaderType) {
        return switch (shaderType) {
            case VulkanicAPI.GL_VERTEX_SHADER -> Optional.of(VERTEX);
            case VulkanicAPI.GL_FRAGMENT_SHADER -> Optional.of(FRAGMENT);
            case VulkanicAPI.GL_GEOMETRY_SHADER -> Optional.of(GEOMETRY);
            case VulkanicAPI.GL_COMPUTE_SHADER -> Optional.of(COMPUTE);
            case VulkanicAPI.GL_TESS_CONTROL_SHADER -> Optional.of(TESSELLATION_CONTROL);
            case VulkanicAPI.GL_TESS_EVALUATION_SHADER -> Optional.of(TESSELLATION_EVALUATION);
            default -> Optional.empty();
        };
    }
}