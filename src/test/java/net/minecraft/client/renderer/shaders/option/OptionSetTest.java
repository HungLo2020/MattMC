package net.minecraft.client.renderer.shaders.option;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OptionSet class.
 */
public class OptionSetTest {
	
	@Test
	public void testEmptyOptionSet() {
		OptionSet optionSet = OptionSet.builder().build();
		
		assertTrue(optionSet.getBooleanOptions().isEmpty());
		assertTrue(optionSet.getStringOptions().isEmpty());
	}
	
	@Test
	public void testAddBooleanOption() {
		BooleanOption option = new BooleanOption(OptionType.DEFINE, "TEST", "Test", true);
		
		OptionSet optionSet = OptionSet.builder()
			.addBooleanOption(option)
			.build();
		
		assertEquals(1, optionSet.getBooleanOptions().size());
		assertTrue(optionSet.isBooleanOption("TEST"));
		assertEquals(option, optionSet.getBooleanOptions().get("TEST"));
	}
	
	@Test
	public void testAddStringOption() {
		StringOption option = StringOption.create(
			OptionType.CONST,
			"QUALITY",
			"Level [LOW HIGH]",
			"HIGH"
		);
		
		OptionSet optionSet = OptionSet.builder()
			.addStringOption(option)
			.build();
		
		assertEquals(1, optionSet.getStringOptions().size());
		assertEquals(option, optionSet.getStringOptions().get("QUALITY"));
	}
	
	@Test
	public void testMixedOptions() {
		BooleanOption boolOption = new BooleanOption(OptionType.DEFINE, "ENABLE", null, true);
		StringOption strOption = StringOption.create(OptionType.CONST, "MODE", "Mode [A B]", "A");
		
		OptionSet optionSet = OptionSet.builder()
			.addBooleanOption(boolOption)
			.addStringOption(strOption)
			.build();
		
		assertEquals(1, optionSet.getBooleanOptions().size());
		assertEquals(1, optionSet.getStringOptions().size());
		assertTrue(optionSet.isBooleanOption("ENABLE"));
		assertFalse(optionSet.isBooleanOption("MODE"));
	}
}
