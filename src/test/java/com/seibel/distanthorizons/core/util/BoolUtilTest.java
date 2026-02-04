package com.seibel.distanthorizons.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Rust FFM implementation of BoolUtil.
 * This verifies that the Rust native library is properly loaded and functions correctly.
 */
public class BoolUtilTest {
	
	@Test
	public void testFalseIfNullWithNull() {
		Boolean nullValue = null;
		assertFalse(BoolUtil.falseIfNull(nullValue), "Should return false for null");
	}
	
	@Test
	public void testFalseIfNullWithTrue() {
		Boolean trueValue = Boolean.TRUE;
		assertTrue(BoolUtil.falseIfNull(trueValue), "Should return true for Boolean.TRUE");
	}
	
	@Test
	public void testFalseIfNullWithFalse() {
		Boolean falseValue = Boolean.FALSE;
		assertFalse(BoolUtil.falseIfNull(falseValue), "Should return false for Boolean.FALSE");
	}
	
	@Test
	public void testFalseIfNullWithBoxedTrue() {
		Boolean boxedTrue = true;
		assertTrue(BoolUtil.falseIfNull(boxedTrue), "Should return true for boxed true");
	}
	
	@Test
	public void testFalseIfNullWithBoxedFalse() {
		Boolean boxedFalse = false;
		assertFalse(BoolUtil.falseIfNull(boxedFalse), "Should return false for boxed false");
	}
}
