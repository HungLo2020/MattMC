package net.sodium.client.gl.shader;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
    VERTEX(35633),  // GL_VERTEX_SHADER
    GEOMETRY(36313),  // GL_GEOMETRY_SHADER
    TESS_CONTROL(36488),  // GL_TESS_CONTROL_SHADER
    TESS_EVALUATION(36487),  // GL_TESS_EVALUATION_SHADER
    FRAGMENT(35632);  // GL_FRAGMENT_SHADER

    public final int id;

    ShaderType(int id) {
        this.id = id;
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
