package net.minecraft.client.renderer.shaders.core;

/**
 * Exception thrown when shader-related operations fail.
 * Used throughout the shader system for consistent error handling.
 */
public class ShaderException extends RuntimeException {
	/**
	 * Constructs a new shader exception with the specified message.
	 * @param message The error message
	 */
	public ShaderException(String message) {
		super(message);
	}
	
	/**
	 * Constructs a new shader exception with the specified message and cause.
	 * @param message The error message
	 * @param cause The underlying cause
	 */
	public ShaderException(String message, Throwable cause) {
		super(message, cause);
	}
}
