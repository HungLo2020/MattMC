package net.minecraft.client.renderer.shaders.option;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OptionType enum.
 */
public class OptionTypeTest {
	
	@Test
	public void testOptionTypeValues() {
		// Verify enum values exist (following IRIS exactly)
		assertEquals(2, OptionType.values().length);
		assertNotNull(OptionType.DEFINE);
		assertNotNull(OptionType.CONST);
	}
	
	@Test
	public void testOptionTypeOrdering() {
		// Verify ordering matches IRIS
		OptionType[] values = OptionType.values();
		assertEquals(OptionType.DEFINE, values[0]);
		assertEquals(OptionType.CONST, values[1]);
	}
}
