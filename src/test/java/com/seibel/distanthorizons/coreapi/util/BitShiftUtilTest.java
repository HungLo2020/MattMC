package com.seibel.distanthorizons.coreapi.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Rust FFM implementation of BitShiftUtil.
 * This verifies that the Rust native library is properly loaded and functions correctly.
 */
public class BitShiftUtilTest {
	
	@Test
	public void testPowerOfTwoInt() {
		assertEquals(1, BitShiftUtil.powerOfTwo(0));
		assertEquals(2, BitShiftUtil.powerOfTwo(1));
		assertEquals(4, BitShiftUtil.powerOfTwo(2));
		assertEquals(8, BitShiftUtil.powerOfTwo(3));
		assertEquals(1024, BitShiftUtil.powerOfTwo(10));
	}
	
	@Test
	public void testPowerOfTwoLong() {
		assertEquals(1L, BitShiftUtil.powerOfTwo(0L));
		assertEquals(2L, BitShiftUtil.powerOfTwo(1L));
		assertEquals(4L, BitShiftUtil.powerOfTwo(2L));
		assertEquals(8L, BitShiftUtil.powerOfTwo(3L));
		assertEquals(1024L, BitShiftUtil.powerOfTwo(10L));
	}
	
	@Test
	public void testHalfInt() {
		assertEquals(5, BitShiftUtil.half(10));
		assertEquals(2, BitShiftUtil.half(4));
		assertEquals(0, BitShiftUtil.half(1));
		assertEquals(50, BitShiftUtil.half(100));
	}
	
	@Test
	public void testHalfLong() {
		assertEquals(5L, BitShiftUtil.half(10L));
		assertEquals(2L, BitShiftUtil.half(4L));
		assertEquals(0L, BitShiftUtil.half(1L));
		assertEquals(50L, BitShiftUtil.half(100L));
	}
	
	@Test
	public void testDivideByPowerOfTwoInt() {
		assertEquals(5, BitShiftUtil.divideByPowerOfTwo(10, 1));
		assertEquals(2, BitShiftUtil.divideByPowerOfTwo(8, 2));
		assertEquals(1, BitShiftUtil.divideByPowerOfTwo(16, 4));
		assertEquals(25, BitShiftUtil.divideByPowerOfTwo(100, 2));
	}
	
	@Test
	public void testDivideByPowerOfTwoLong() {
		assertEquals(5L, BitShiftUtil.divideByPowerOfTwo(10L, 1L));
		assertEquals(2L, BitShiftUtil.divideByPowerOfTwo(8L, 2L));
		assertEquals(1L, BitShiftUtil.divideByPowerOfTwo(16L, 4L));
		assertEquals(25L, BitShiftUtil.divideByPowerOfTwo(100L, 2L));
	}
	
	@Test
	public void testSquareInt() {
		assertEquals(10, BitShiftUtil.square(5));
		assertEquals(0, BitShiftUtil.square(0));
		assertEquals(20, BitShiftUtil.square(10));
	}
	
	@Test
	public void testSquareLong() {
		assertEquals(10L, BitShiftUtil.square(5L));
		assertEquals(0L, BitShiftUtil.square(0L));
		assertEquals(20L, BitShiftUtil.square(10L));
	}
	
	@Test
	public void testPowInt() {
		assertEquals(10, BitShiftUtil.pow(5, 1));
		assertEquals(20, BitShiftUtil.pow(5, 2));
		assertEquals(40, BitShiftUtil.pow(5, 3));
		assertEquals(0, BitShiftUtil.pow(0, 5));
	}
	
	@Test
	public void testPowLong() {
		assertEquals(10L, BitShiftUtil.pow(5L, 1L));
		assertEquals(20L, BitShiftUtil.pow(5L, 2L));
		assertEquals(40L, BitShiftUtil.pow(5L, 3L));
		assertEquals(0L, BitShiftUtil.pow(0L, 5L));
	}
}
