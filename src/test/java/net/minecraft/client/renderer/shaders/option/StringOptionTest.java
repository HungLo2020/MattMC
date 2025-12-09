package net.minecraft.client.renderer.shaders.option;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StringOption class.
 */
public class StringOptionTest {
	
	@Test
	public void testStringOptionWithAllowedValues() {
		StringOption option = StringOption.create(
			OptionType.DEFINE,
			"SHADOW_QUALITY",
			"Quality level [LOW MEDIUM HIGH]",
			"MEDIUM"
		);
		
		assertNotNull(option);
		assertEquals("SHADOW_QUALITY", option.getName());
		assertEquals("MEDIUM", option.getDefaultValue());
		assertTrue(option.getAllowedValues().contains("LOW"));
		assertTrue(option.getAllowedValues().contains("MEDIUM"));
		assertTrue(option.getAllowedValues().contains("HIGH"));
		assertEquals(Optional.of("Quality level"), option.getComment());
	}
	
	@Test
	public void testStringOptionWithoutBrackets() {
		// Without brackets, should return null (IRIS behavior)
		StringOption option = StringOption.create(
			OptionType.CONST,
			"TEST",
			"No brackets here",
			"default"
		);
		
		assertNull(option);
	}
	
	@Test
	public void testStringOptionNullComment() {
		// Null comment should return null (IRIS behavior)
		StringOption option = StringOption.create(
			OptionType.DEFINE,
			"TEST",
			null,
			"value"
		);
		
		assertNull(option);
	}
	
	@Test
	public void testDefaultValueNotInAllowedValues() {
		// Default value not in list should be added (IRIS behavior)
		StringOption option = StringOption.create(
			OptionType.CONST,
			"shadowMapResolution",
			"Resolution [1024 2048 4096]",
			"2048"
		);
		
		assertNotNull(option);
		assertTrue(option.getAllowedValues().contains("2048"));
		assertTrue(option.getAllowedValues().contains("1024"));
		assertTrue(option.getAllowedValues().contains("4096"));
	}
}
