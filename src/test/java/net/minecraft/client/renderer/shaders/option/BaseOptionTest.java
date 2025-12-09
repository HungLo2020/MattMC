package net.minecraft.client.renderer.shaders.option;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BaseOption abstract class.
 */
public class BaseOptionTest {
	
	// Helper concrete implementation for testing
	private static class TestOption extends BaseOption {
		TestOption(OptionType type, String name, String comment) {
			super(type, name, comment);
		}
	}
	
	@Test
	public void testOptionWithComment() {
		BaseOption option = new TestOption(OptionType.DEFINE, "TEST_OPTION", "A test comment");
		
		assertEquals(OptionType.DEFINE, option.getType());
		assertEquals("TEST_OPTION", option.getName());
		assertEquals(Optional.of("A test comment"), option.getComment());
	}
	
	@Test
	public void testOptionWithoutComment() {
		BaseOption option = new TestOption(OptionType.CONST, "TEST_CONST", null);
		
		assertEquals(OptionType.CONST, option.getType());
		assertEquals("TEST_CONST", option.getName());
		assertEquals(Optional.empty(), option.getComment());
	}
	
	@Test
	public void testOptionWithEmptyComment() {
		// Empty comments are treated as no comment (IRIS behavior)
		BaseOption option = new TestOption(OptionType.DEFINE, "OPTION", "");
		
		assertEquals(Optional.empty(), option.getComment());
	}
}
