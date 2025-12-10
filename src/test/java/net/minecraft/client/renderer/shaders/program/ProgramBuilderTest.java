package net.minecraft.client.renderer.shaders.program;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ProgramBuilder class structure.
 * Follows Step 12 of NEW-SHADER-PLAN.md.
 * 
 * Note: Full integration tests with OpenGL will be added in Steps 16-20
 * when rendering infrastructure is available.
 */
class ProgramBuilderTest {
	
	@Test
	void testProgramBuilderClassExists() {
		// Verify ProgramBuilder class exists and matches IRIS structure
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.program.ProgramBuilder");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testProgramBuilderHasBeginMethod() throws Exception {
		// Verify begin() factory method matches IRIS ProgramBuilder.java:33
		Class<?> clazz = ProgramBuilder.class;
		
		var method = clazz.getMethod("begin", String.class, String.class, String.class, String.class);
		assertThat(method).isNotNull();
		assertThat(java.lang.reflect.Modifier.isStatic(method.getModifiers())).isTrue();
	}
	
	@Test
	void testProgramBuilderHasBeginComputeMethod() throws Exception {
		// Verify beginCompute() factory method matches IRIS ProgramBuilder.java:70
		Class<?> clazz = ProgramBuilder.class;
		
		var method = clazz.getMethod("beginCompute", String.class, String.class);
		assertThat(method).isNotNull();
		assertThat(java.lang.reflect.Modifier.isStatic(method.getModifiers())).isTrue();
	}
	
	@Test
	void testProgramBuilderHasBuildMethod() throws Exception {
		// Verify build() method matches IRIS ProgramBuilder.java:100
		Class<?> clazz = ProgramBuilder.class;
		
		var method = clazz.getMethod("build");
		assertThat(method).isNotNull();
		assertThat(method.getReturnType()).isEqualTo(Program.class);
	}
	
	@Test
	void testProgramBuilderHasBindAttributeLocationMethod() throws Exception {
		// Verify bindAttributeLocation() method matches IRIS ProgramBuilder.java:96
		Class<?> clazz = ProgramBuilder.class;
		
		assertThat(clazz.getMethod("bindAttributeLocation", int.class, String.class))
			.isNotNull();
	}
}
