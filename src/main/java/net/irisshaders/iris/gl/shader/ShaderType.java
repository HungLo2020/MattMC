package net.irisshaders.iris.gl.shader;

import net.vulkanic.VulkanicAPI;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
	VERTEX(VulkanicAPI.GL_VERTEX_SHADER),
	GEOMETRY(VulkanicAPI.GL_GEOMETRY_SHADER),
	FRAGMENT(VulkanicAPI.GL_FRAGMENT_SHADER),
	COMPUTE(VulkanicAPI.GL_COMPUTE_SHADER),
	TESSELATION_CONTROL(VulkanicAPI.GL_TESS_CONTROL_SHADER),
	TESSELATION_EVAL(VulkanicAPI.GL_TESS_EVALUATION_SHADER);

	public final int id;

	ShaderType(int id) {
		this.id = id;
	}
}
