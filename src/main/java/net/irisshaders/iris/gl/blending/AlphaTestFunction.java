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

	public static Optional<AlphaTestFunction> fromGlId(int glId) {
		return switch (glId) {
			case VulkanicAPI.GL_NEVER -> Optional.of(NEVER);
			case VulkanicAPI.GL_LESS -> Optional.of(LESS);
			case VulkanicAPI.GL_EQUAL -> Optional.of(EQUAL);
			case VulkanicAPI.GL_LEQUAL -> Optional.of(LEQUAL);
			case VulkanicAPI.GL_GREATER -> Optional.of(GREATER);
			case VulkanicAPI.GL_NOTEQUAL -> Optional.of(NOTEQUAL);
			case VulkanicAPI.GL_GEQUAL -> Optional.of(GEQUAL);
			case VulkanicAPI.GL_ALWAYS -> Optional.of(ALWAYS);
			default -> Optional.empty();
		};
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

	public String getExpression() {
		return expression;
	}
}
