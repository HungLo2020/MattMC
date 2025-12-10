package net.minecraft.client.renderer.shaders.program;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Structure tests for ShaderCompiler.
 * These tests verify the class structure without requiring OpenGL context.
 * 
 * Note: Full integration tests with actual OpenGL compilation will be added
 * when the rendering infrastructure is in place (Steps 16-20).
 * 
 * Follows Step 11 of NEW-SHADER-PLAN.md.
 */
class ShaderCompilerStructureTest {
	
	@Test
	void testShaderCompilerClassExists() {
		// Verify ShaderCompiler class is available
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.program.ShaderCompiler");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testShaderCompilerHasRequiredMethods() throws Exception {
		// Verify public API matches IRIS's GlShader
		Class<?> clazz = ShaderCompiler.class;
		
		// getName() method (IRIS GlShader.java:52-54)
		assertThat(clazz.getMethod("getName")).isNotNull();
		
		// getHandle() method (IRIS GlShader.java:56-58)
		assertThat(clazz.getMethod("getHandle")).isNotNull();
		
		// delete() method (IRIS GlShader.java:60-63)
		assertThat(clazz.getMethod("delete")).isNotNull();
	}
	
	@Test
	void testShaderCompilerConstructorSignature() throws Exception {
		// Verify constructor matches IRIS's GlShader constructor
		Class<?> clazz = ShaderCompiler.class;
		
		assertThat(clazz.getConstructor(ShaderType.class, String.class, String.class)).isNotNull();
	}
	
	@Test
	void testShaderWorkaroundsExists() {
		// Verify ShaderWorkarounds class exists for AMD driver workaround
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.program.ShaderWorkarounds");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testShaderWorkaroundsHasSafeShaderSourceMethod() throws Exception {
		// Verify safeShaderSource method exists (AMD workaround)
		Class<?> clazz = ShaderWorkarounds.class;
		
		assertThat(clazz.getMethod("safeShaderSource", int.class, CharSequence.class)).isNotNull();
	}
	
	@Test
	void testProgramLinkerExists() {
		// Verify ProgramLinker class exists
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.program.ProgramLinker");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testProgramLinkerHasCreateMethod() throws Exception {
		// Verify create method matches IRIS's ProgramCreator.create()
		Class<?> clazz = ProgramLinker.class;
		
		assertThat(clazz.getMethod("create", String.class, ShaderCompiler[].class)).isNotNull();
	}
	
	@Test
	void testProgramLinkerHasDeleteMethod() throws Exception {
		// Verify deleteProgram method exists
		Class<?> clazz = ProgramLinker.class;
		
		assertThat(clazz.getMethod("deleteProgram", int.class)).isNotNull();
	}
}
