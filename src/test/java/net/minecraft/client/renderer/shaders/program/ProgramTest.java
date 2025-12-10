package net.minecraft.client.renderer.shaders.program;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Program class.
 * Follows Step 12 of NEW-SHADER-PLAN.md.
 */
class ProgramTest {
	
	@Test
	void testProgramClassExists() {
		// Verify Program class exists and matches IRIS structure
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.program.Program");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testProgramHasRequiredMethods() throws Exception {
		// Verify methods match IRIS's Program.java
		Class<?> clazz = Program.class;
		
		// Static unbind method (IRIS Program.java:21)
		assertThat(clazz.getMethod("unbind")).isNotNull();
		
		// use() method (IRIS Program.java:27)
		assertThat(clazz.getMethod("use")).isNotNull();
		
		// destroyInternal() method (IRIS Program.java:36)
		assertThat(clazz.getMethod("destroyInternal")).isNotNull();
		
		// getProgramId() method (IRIS Program.java:45)
		assertThat(clazz.getMethod("getProgramId")).isNotNull();
	}
	
	@Test
	void testProgramConstructor() throws Exception {
		// Verify constructor signature
		Class<?> clazz = Program.class;
		assertThat(clazz.getDeclaredConstructor(int.class)).isNotNull();
	}
}
