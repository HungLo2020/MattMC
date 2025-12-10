package net.minecraft.client.renderer.shaders.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CompositePass enum.
 * Validates IRIS 1.21.9 compatibility for composite rendering passes.
 */
class CompositePassTest {

	@Test
	void testCompositePassCount() {
		// IRIS has exactly 4 composite passes
		CompositePass[] passes = CompositePass.values();
		assertEquals(4, passes.length, "Should have exactly 4 composite passes");
	}

	@Test
	void testCompositePassNames() {
		// Verify all pass names match IRIS exactly
		assertEquals("BEGIN", CompositePass.BEGIN.name());
		assertEquals("PREPARE", CompositePass.PREPARE.name());
		assertEquals("DEFERRED", CompositePass.DEFERRED.name());
		assertEquals("COMPOSITE", CompositePass.COMPOSITE.name());
	}

	@Test
	void testCompositePassOrder() {
		// Verify passes are in correct order
		CompositePass[] passes = CompositePass.values();
		assertEquals(CompositePass.BEGIN, passes[0]);
		assertEquals(CompositePass.PREPARE, passes[1]);
		assertEquals(CompositePass.DEFERRED, passes[2]);
		assertEquals(CompositePass.COMPOSITE, passes[3]);
	}

	@Test
	void testCompositePassOrdinals() {
		// Verify ordinal values
		assertEquals(0, CompositePass.BEGIN.ordinal());
		assertEquals(1, CompositePass.PREPARE.ordinal());
		assertEquals(2, CompositePass.DEFERRED.ordinal());
		assertEquals(3, CompositePass.COMPOSITE.ordinal());
	}

	@Test
	void testValueOfMethod() {
		// Test valueOf method works correctly
		assertEquals(CompositePass.BEGIN, CompositePass.valueOf("BEGIN"));
		assertEquals(CompositePass.PREPARE, CompositePass.valueOf("PREPARE"));
		assertEquals(CompositePass.DEFERRED, CompositePass.valueOf("DEFERRED"));
		assertEquals(CompositePass.COMPOSITE, CompositePass.valueOf("COMPOSITE"));
	}

	@Test
	void testInvalidValueOf() {
		// Test that invalid names throw exception
		assertThrows(IllegalArgumentException.class, () -> {
			CompositePass.valueOf("INVALID");
		});
	}
}
