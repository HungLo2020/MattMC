package net.minecraft.client.renderer.shaders.program;

/**
 * Exception thrown when shader compilation or linking fails.
 * 
 * Based on IRIS's ShaderCompileException.java - EXACT copy.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/gl/shader/ShaderCompileException.java
 */
public class ShaderCompileException extends RuntimeException {
	private final String filename;
	private final String error;

	public ShaderCompileException(String filename, String error) {
		super(filename + ": " + error);

		this.filename = filename;
		this.error = error;
	}

	public ShaderCompileException(String filename, Exception error) {
		super(error);

		this.filename = filename;
		this.error = error.getMessage();
	}

	@Override
	public String getMessage() {
		return filename + ": " + super.getMessage();
	}

	public String getError() {
		return error;
	}

	public String getFilename() {
		return filename;
	}
}
