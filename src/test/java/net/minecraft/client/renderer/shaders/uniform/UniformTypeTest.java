package net.minecraft.client.renderer.shaders.uniform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UniformType enum.
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class UniformTypeTest {

	@Test
	public void testAllTypesExist() {
		// Verify all expected uniform types exist
		assertNotNull(UniformType.INT);
		assertNotNull(UniformType.FLOAT);
		assertNotNull(UniformType.MAT3);
		assertNotNull(UniformType.MAT4);
		assertNotNull(UniformType.VEC2);
		assertNotNull(UniformType.VEC2I);
		assertNotNull(UniformType.VEC3);
		assertNotNull(UniformType.VEC3I);
		assertNotNull(UniformType.VEC4);
		assertNotNull(UniformType.VEC4I);
	}

	@Test
	public void testTypeCount() {
		// Verify we have exactly 10 uniform types (matching IRIS)
		UniformType[] types = UniformType.values();
		assertEquals(10, types.length, "Should have 10 uniform types");
	}

	@Test
	public void testTypeNames() {
		// Verify type names match IRIS exactly
		assertEquals("INT", UniformType.INT.name());
		assertEquals("FLOAT", UniformType.FLOAT.name());
		assertEquals("MAT3", UniformType.MAT3.name());
		assertEquals("MAT4", UniformType.MAT4.name());
		assertEquals("VEC2", UniformType.VEC2.name());
		assertEquals("VEC2I", UniformType.VEC2I.name());
		assertEquals("VEC3", UniformType.VEC3.name());
		assertEquals("VEC3I", UniformType.VEC3I.name());
		assertEquals("VEC4", UniformType.VEC4.name());
		assertEquals("VEC4I", UniformType.VEC4I.name());
	}
}
