package net.sodium.client.gl.shader;

import net.vulkanic.VulkanicShaderStage;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(VulkanicShaderStage.VERTEX),
    GEOMETRY(VulkanicShaderStage.GEOMETRY),
    TESS_CONTROL(VulkanicShaderStage.TESSELLATION_CONTROL),
    TESS_EVALUATION(VulkanicShaderStage.TESSELLATION_EVALUATION),
    FRAGMENT(VulkanicShaderStage.FRAGMENT);

    public final int id;
    public final VulkanicShaderStage stage;

    ShaderType(VulkanicShaderStage stage) {
        this.stage = stage;
        this.id = stage.toLegacyGlShaderType();
    }

    public static ShaderType fromGlShaderType(int id) {
        for (ShaderType type : values()) {
            if (type.id == id) {
                return type;
            }
        }

        return null;
    }
}
