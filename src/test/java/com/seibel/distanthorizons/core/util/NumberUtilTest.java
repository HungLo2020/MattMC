package com.seibel.distanthorizons.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Rust FFM implementation of NumberUtil.
 * This verifies that the Rust native library is properly loaded and functions correctly.
 */
public class NumberUtilTest {
	
	// Test greaterThan for different types
	
	@Test
	public void testGreaterThanInt() {
		assertTrue(NumberUtil.greaterThan(10, 5), "10 > 5 should be true");
		assertFalse(NumberUtil.greaterThan(5, 10), "5 > 10 should be false");
		assertFalse(NumberUtil.greaterThan(5, 5), "5 > 5 should be false");
	}
	
	@Test
	public void testGreaterThanLong() {
		assertTrue(NumberUtil.greaterThan(100L, 50L), "100L > 50L should be true");
		assertFalse(NumberUtil.greaterThan(50L, 100L), "50L > 100L should be false");
		assertFalse(NumberUtil.greaterThan(50L, 50L), "50L > 50L should be false");
	}
	
	@Test
	public void testGreaterThanFloat() {
		assertTrue(NumberUtil.greaterThan(10.5f, 5.5f), "10.5f > 5.5f should be true");
		assertFalse(NumberUtil.greaterThan(5.5f, 10.5f), "5.5f > 10.5f should be false");
		assertFalse(NumberUtil.greaterThan(5.5f, 5.5f), "5.5f > 5.5f should be false");
	}
	
	@Test
	public void testGreaterThanDouble() {
		assertTrue(NumberUtil.greaterThan(10.5, 5.5), "10.5 > 5.5 should be true");
		assertFalse(NumberUtil.greaterThan(5.5, 10.5), "5.5 > 10.5 should be false");
		assertFalse(NumberUtil.greaterThan(5.5, 5.5), "5.5 > 5.5 should be false");
	}
	
	@Test
	public void testGreaterThanShort() {
		assertTrue(NumberUtil.greaterThan((short)100, (short)50), "100 > 50 should be true");
		assertFalse(NumberUtil.greaterThan((short)50, (short)100), "50 > 100 should be false");
		assertFalse(NumberUtil.greaterThan((short)50, (short)50), "50 > 50 should be false");
	}
	
	@Test
	public void testGreaterThanByte() {
		assertTrue(NumberUtil.greaterThan((byte)10, (byte)5), "10 > 5 should be true");
		assertFalse(NumberUtil.greaterThan((byte)5, (byte)10), "5 > 10 should be false");
		assertFalse(NumberUtil.greaterThan((byte)5, (byte)5), "5 > 5 should be false");
	}
	
	// Test lessThan for different types
	
	@Test
	public void testLessThanInt() {
		assertTrue(NumberUtil.lessThan(5, 10), "5 < 10 should be true");
		assertFalse(NumberUtil.lessThan(10, 5), "10 < 5 should be false");
		assertFalse(NumberUtil.lessThan(5, 5), "5 < 5 should be false");
	}
	
	@Test
	public void testLessThanLong() {
		assertTrue(NumberUtil.lessThan(50L, 100L), "50L < 100L should be true");
		assertFalse(NumberUtil.lessThan(100L, 50L), "100L < 50L should be false");
		assertFalse(NumberUtil.lessThan(50L, 50L), "50L < 50L should be false");
	}
	
	@Test
	public void testLessThanFloat() {
		assertTrue(NumberUtil.lessThan(5.5f, 10.5f), "5.5f < 10.5f should be true");
		assertFalse(NumberUtil.lessThan(10.5f, 5.5f), "10.5f < 5.5f should be false");
		assertFalse(NumberUtil.lessThan(5.5f, 5.5f), "5.5f < 5.5f should be false");
	}
	
	@Test
	public void testLessThanDouble() {
		assertTrue(NumberUtil.lessThan(5.5, 10.5), "5.5 < 10.5 should be true");
		assertFalse(NumberUtil.lessThan(10.5, 5.5), "10.5 < 5.5 should be false");
		assertFalse(NumberUtil.lessThan(5.5, 5.5), "5.5 < 5.5 should be false");
	}
	
	@Test
	public void testLessThanShort() {
		assertTrue(NumberUtil.lessThan((short)50, (short)100), "50 < 100 should be true");
		assertFalse(NumberUtil.lessThan((short)100, (short)50), "100 < 50 should be false");
		assertFalse(NumberUtil.lessThan((short)50, (short)50), "50 < 50 should be false");
	}
	
	@Test
	public void testLessThanByte() {
		assertTrue(NumberUtil.lessThan((byte)5, (byte)10), "5 < 10 should be true");
		assertFalse(NumberUtil.lessThan((byte)10, (byte)5), "10 < 5 should be false");
		assertFalse(NumberUtil.lessThan((byte)5, (byte)5), "5 < 5 should be false");
	}
	
	// Test with negative numbers
	
	@Test
	public void testGreaterThanWithNegatives() {
		assertTrue(NumberUtil.greaterThan(5, -5), "5 > -5 should be true");
		assertTrue(NumberUtil.greaterThan(-5, -10), "-5 > -10 should be true");
		assertFalse(NumberUtil.greaterThan(-10, -5), "-10 > -5 should be false");
	}
	
	@Test
	public void testLessThanWithNegatives() {
		assertTrue(NumberUtil.lessThan(-5, 5), "-5 < 5 should be true");
		assertTrue(NumberUtil.lessThan(-10, -5), "-10 < -5 should be true");
		assertFalse(NumberUtil.lessThan(-5, -10), "-5 < -10 should be false");
	}
	
	// Test generic Number interface (should use the new type-specific methods)
	
	@Test
	public void testGreaterThanGenericNumber() {
		Number a = Integer.valueOf(10);
		Number b = Integer.valueOf(5);
		assertTrue(NumberUtil.greaterThan(a, b), "Generic Number comparison should work");
	}
	
	@Test
	public void testLessThanGenericNumber() {
		Number a = Integer.valueOf(5);
		Number b = Integer.valueOf(10);
		assertTrue(NumberUtil.lessThan(a, b), "Generic Number comparison should work");
	}
}
