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

}