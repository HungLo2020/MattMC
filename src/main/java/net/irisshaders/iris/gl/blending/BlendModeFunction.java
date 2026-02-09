package net.irisshaders.iris.gl.blending;

import net.irisshaders.iris.Iris;
import net.vulkanic.VulkanicAPI;

import java.util.Optional;

public enum BlendModeFunction {
	ZERO(VulkanicAPI.GL_ZERO),
	ONE(VulkanicAPI.GL_ONE),
	SRC_COLOR(VulkanicAPI.GL_SRC_COLOR),
	ONE_MINUS_SRC_COLOR(VulkanicAPI.GL_ONE_MINUS_SRC_COLOR),
	DST_COLOR(VulkanicAPI.GL_DST_COLOR),
	ONE_MINUS_DST_COLOR(VulkanicAPI.GL_ONE_MINUS_DST_COLOR),
	SRC_ALPHA(VulkanicAPI.GL_SRC_ALPHA),
	ONE_MINUS_SRC_ALPHA(VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA),
	DST_ALPHA(VulkanicAPI.GL_DST_ALPHA),
	ONE_MINUS_DST_ALPHA(VulkanicAPI.GL_ONE_MINUS_DST_ALPHA),
	SRC_ALPHA_SATURATE(VulkanicAPI.GL_SRC_ALPHA_SATURATE);

	private final int glId;

	BlendModeFunction(int glFormat) {
		this.glId = glFormat;
	}

	public static Optional<BlendModeFunction> fromString(String name) {
		try {
			return Optional.of(BlendModeFunction.valueOf(name));
		} catch (IllegalArgumentException e) {
			Iris.logger.warn("Invalid blend mode! " + name);
			return Optional.empty();
		}
	}

	public int getGlId() {
		return glId;
	}
}
