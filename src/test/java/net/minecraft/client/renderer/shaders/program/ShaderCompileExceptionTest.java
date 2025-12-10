package net.minecraft.client.renderer.shaders.program;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ShaderCompileException.
 * Follows Step 11 of NEW-SHADER-PLAN.md.
 */
class ShaderCompileExceptionTest {
	
	@Test
	void testExceptionWithErrorString() {
		// Given
		String filename = "test_shader.fsh";
		String error = "Compilation failed: syntax error";
		
		// When
		ShaderCompileException exception = new ShaderCompileException(filename, error);
		
		// Then
		assertThat(exception.getFilename()).isEqualTo(filename);
		assertThat(exception.getError()).isEqualTo(error);
		assertThat(exception.getMessage()).contains(filename);
		assertThat(exception.getMessage()).contains(error);
	}
	
	@Test
	void testExceptionWithNestedError() {
		// Given
		String filename = "test_shader.vsh";
		Exception nested = new RuntimeException("OpenGL error");
		
		// When
		ShaderCompileException exception = new ShaderCompileException(filename, nested);
		
		// Then
		assertThat(exception.getFilename()).isEqualTo(filename);
		assertThat(exception.getError()).isEqualTo("OpenGL error");
		assertThat(exception.getMessage()).contains(filename);
	}
	
	@Test
	void testExceptionMessageFormat() {
		// Given
		String filename = "composite.fsh";
		String error = "undefined variable: gl_Color";
		
		// When
		ShaderCompileException exception = new ShaderCompileException(filename, error);
		
		// Then
		// IRIS format: "filename: error"
		assertThat(exception.getMessage()).isEqualTo(filename + ": " + filename + ": " + error);
	}
	
	@Test
	void testExceptionIsRuntimeException() {
		// Given
		ShaderCompileException exception = new ShaderCompileException("test.fsh", "error");
		
		// Then
		assertThat(exception).isInstanceOf(RuntimeException.class);
	}
	
	@Test
	void testGettersNeverReturnNull() {
		// Given
		ShaderCompileException exception = new ShaderCompileException("test.fsh", "error");
		
		// Then
		assertThat(exception.getFilename()).isNotNull();
		assertThat(exception.getError()).isNotNull();
		assertThat(exception.getMessage()).isNotNull();
	}
}
