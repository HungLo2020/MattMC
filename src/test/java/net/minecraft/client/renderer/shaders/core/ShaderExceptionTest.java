package net.minecraft.client.renderer.shaders.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ShaderException.
 * Verifies exception construction and message handling.
 */
class ShaderExceptionTest {
	
	@Test
	void testExceptionWithMessage() {
		// Given
		String message = "Shader compilation failed";
		
		// When
		ShaderException exception = new ShaderException(message);
		
		// Then
		assertThat(exception).hasMessage(message);
	}
	
	@Test
	void testExceptionWithMessageAndCause() {
		// Given
		String message = "Shader loading error";
		RuntimeException cause = new RuntimeException("File not found");
		
		// When
		ShaderException exception = new ShaderException(message, cause);
		
		// Then
		assertThat(exception).hasMessage(message);
		assertThat(exception).hasCause(cause);
	}
	
	@Test
	void testExceptionIsRuntimeException() {
		// Given
		ShaderException exception = new ShaderException("test");
		
		// When & Then
		assertThat(exception).isInstanceOf(RuntimeException.class);
	}
}
