package net.irisshaders.iris.gl.shader;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderStage;

/**
 * An enumeration over the supported OpenGL shader types.
 */
public enum ShaderType {
	VERTEX(VulkanicShaderStage.VERTEX),
	GEOMETRY(VulkanicShaderStage.GEOMETRY),
	FRAGMENT(VulkanicShaderStage.FRAGMENT),
	COMPUTE(VulkanicShaderStage.COMPUTE),
	TESSELATION_CONTROL(VulkanicShaderStage.TESSELLATION_CONTROL),
	TESSELATION_EVAL(VulkanicShaderStage.TESSELLATION_EVALUATION);

	public final int id;
	public final VulkanicShaderStage stage;

	ShaderType(VulkanicShaderStage stage) {
		this.stage = stage;
		this.id = stage.toLegacyGlShaderType();
	}
}
