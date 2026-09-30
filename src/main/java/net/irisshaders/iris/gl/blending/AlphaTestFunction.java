package net.irisshaders.iris.gl.blending;

import net.vulkanic.VulkanicAPI;

import java.util.Optional;

public enum AlphaTestFunction {
	NEVER(VulkanicAPI.GL_NEVER, null),
	LESS(VulkanicAPI.GL_LESS, "<"),
	EQUAL(VulkanicAPI.GL_EQUAL, "=="),
	LEQUAL(VulkanicAPI.GL_LEQUAL, "<="),
	GREATER(VulkanicAPI.GL_GREATER, ">"),
	NOTEQUAL(VulkanicAPI.GL_NOTEQUAL, "!="),
	GEQUAL(VulkanicAPI.GL_GEQUAL, ">="),
	ALWAYS(VulkanicAPI.GL_ALWAYS, null);

	private final int glId;
	private final String expression;

	AlphaTestFunction(int glFormat, String expression) {
		this.glId = glFormat;
		this.expression = expression;
	}

	public static Optional<AlphaTestFunction> fromString(String name) {
		if ("GL_ALWAYS".equals(name)) {
			// shaders.properties states that GL_ALWAYS is the name to use, but I haven't verified that this actually
			// matches the implementation... All of the other names do not have the GL_ prefix.
			//
			// We'll support it here just to be safe, even though just a plain ALWAYS seems more likely to be what it
			// parses.
			return Optional.of(AlphaTestFunction.ALWAYS);
		}

		try {
			return Optional.of(AlphaTestFunction.valueOf(name));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public int getGlId() {
		return glId;
	}

}
