package net.minecraft.client.renderer.shaders.uniform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UniformUpdateFrequency enum.
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class UniformUpdateFrequencyTest {

	@Test
	public void testAllFrequenciesExist() {
		// Verify all expected update frequencies exist
		assertNotNull(UniformUpdateFrequency.ONCE);
		assertNotNull(UniformUpdateFrequency.PER_TICK);
		assertNotNull(UniformUpdateFrequency.PER_FRAME);
		assertNotNull(UniformUpdateFrequency.CUSTOM);
	}

	@Test
	public void testFrequencyCount() {
		// Verify we have exactly 4 update frequencies (matching IRIS)
		UniformUpdateFrequency[] frequencies = UniformUpdateFrequency.values();
		assertEquals(4, frequencies.length, "Should have 4 update frequencies");
	}

	@Test
	public void testFrequencyNames() {
		// Verify frequency names match IRIS exactly
		assertEquals("ONCE", UniformUpdateFrequency.ONCE.name());
		assertEquals("PER_TICK", UniformUpdateFrequency.PER_TICK.name());
		assertEquals("PER_FRAME", UniformUpdateFrequency.PER_FRAME.name());
		assertEquals("CUSTOM", UniformUpdateFrequency.CUSTOM.name());
	}

	@Test
	public void testFrequencyOrdering() {
		// Verify ordering matches IRIS
		UniformUpdateFrequency[] frequencies = UniformUpdateFrequency.values();
		assertEquals(UniformUpdateFrequency.ONCE, frequencies[0]);
		assertEquals(UniformUpdateFrequency.PER_TICK, frequencies[1]);
		assertEquals(UniformUpdateFrequency.PER_FRAME, frequencies[2]);
		assertEquals(UniformUpdateFrequency.CUSTOM, frequencies[3]);
	}
}
