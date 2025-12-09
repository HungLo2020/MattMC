package net.minecraft.client.renderer.shaders.option;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BooleanOption class.
 */
public class BooleanOptionTest {
	
	@Test
	public void testBooleanOptionTrue() {
		BooleanOption option = new BooleanOption(OptionType.DEFINE, "ENABLE_SHADOWS", "Enable shadows", true);
		
		assertEquals(OptionType.DEFINE, option.getType());
		assertEquals("ENABLE_SHADOWS", option.getName());
		assertEquals(Optional.of("Enable shadows"), option.getComment());
		assertTrue(option.getDefaultValue());
	}
	
	@Test
	public void testBooleanOptionFalse() {
		BooleanOption option = new BooleanOption(OptionType.CONST, "use_pbr", null, false);
		
		assertEquals(OptionType.CONST, option.getType());
		assertEquals("use_pbr", option.getName());
		assertEquals(Optional.empty(), option.getComment());
		assertFalse(option.getDefaultValue());
	}
	
	@Test
	public void testToString() {
		BooleanOption option = new BooleanOption(OptionType.DEFINE, "TEST", "Test option", true);
		String str = option.toString();
		
		// Verify toString contains key information
		assertTrue(str.contains("TEST"));
		assertTrue(str.contains("true"));
	}
}
