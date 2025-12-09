package net.minecraft.client.renderer.shaders.helpers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for OptionalBoolean.
 * Tests the IRIS-pattern enum behavior.
 */
class OptionalBooleanTest {
	
	@Test
	void testDefaultOrElse() {
		// When
		boolean result = OptionalBoolean.DEFAULT.orElse(true);
		
		// Then
		assertThat(result).isTrue();
	}
	
	@Test
	void testTrueOrElse() {
		// When
		boolean result = OptionalBoolean.TRUE.orElse(false);
		
		// Then
		assertThat(result).isTrue();
	}
	
	@Test
	void testFalseOrElse() {
		// When
		boolean result = OptionalBoolean.FALSE.orElse(true);
		
		// Then
		assertThat(result).isFalse();
	}
	
	@Test
	void testDefaultOrElseGet() {
		// When
		boolean result = OptionalBoolean.DEFAULT.orElseGet(() -> true);
		
		// Then
		assertThat(result).isTrue();
	}
	
	@Test
	void testTrueOrElseGet() {
		// When
		boolean result = OptionalBoolean.TRUE.orElseGet(() -> false);
		
		// Then
		assertThat(result).isTrue();
	}
	
	@Test
	void testFalseOrElseGet() {
		// When
		boolean result = OptionalBoolean.FALSE.orElseGet(() -> true);
		
		// Then
		assertThat(result).isFalse();
	}
}
