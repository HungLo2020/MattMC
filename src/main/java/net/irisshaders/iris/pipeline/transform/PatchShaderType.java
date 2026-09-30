package net.irisshaders.iris.pipeline.transform;

import net.irisshaders.iris.gl.shader.ShaderType;

public enum PatchShaderType {
	VERTEX(ShaderType.VERTEX, ".vsh"),
	GEOMETRY(ShaderType.GEOMETRY, ".gsh"),
	TESS_CONTROL(ShaderType.TESSELATION_CONTROL, ".tcs"),
	TESS_EVAL(ShaderType.TESSELATION_EVAL, ".tes"),
	FRAGMENT(ShaderType.FRAGMENT, ".fsh"),
	COMPUTE(ShaderType.COMPUTE, ".csh");

	public final ShaderType glShaderType;
	public final String extension;

	PatchShaderType(ShaderType glShaderType, String extension) {
		this.glShaderType = glShaderType;
		this.extension = extension;
	}

}
