package com.seibel.distanthorizons.coreapi.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Rust FFM implementation of MathUtil.
 * This verifies that the Rust native library is properly loaded and functions correctly.
 */
public class MathUtilTest {
	
	@Test
	public void testClampInt() {
		assertEquals(5, MathUtil.clamp(0, 5, 10));
		assertEquals(0, MathUtil.clamp(0, -5, 10));
		assertEquals(10, MathUtil.clamp(0, 15, 10));
		assertEquals(7, MathUtil.clamp(5, 7, 10));
	}
	
	@Test
	public void testClampFloat() {
		assertEquals(5.5f, MathUtil.clamp(0.0f, 5.5f, 10.0f), 0.001f);
		assertEquals(0.0f, MathUtil.clamp(0.0f, -5.5f, 10.0f), 0.001f);
		assertEquals(10.0f, MathUtil.clamp(0.0f, 15.5f, 10.0f), 0.001f);
	}
	
	@Test
	public void testClampDouble() {
		assertEquals(5.5, MathUtil.clamp(0.0, 5.5, 10.0), 0.001);
		assertEquals(0.0, MathUtil.clamp(0.0, -5.5, 10.0), 0.001);
		assertEquals(10.0, MathUtil.clamp(0.0, 15.5, 10.0), 0.001);
	}
	
	@Test
	public void testCeilDiv() {
		assertEquals(3, MathUtil.ceilDiv(10, 4));
		assertEquals(3, MathUtil.ceilDiv(9, 3));
		assertEquals(1, MathUtil.ceilDiv(1, 2));
		assertEquals(0, MathUtil.ceilDiv(0, 5));
	}
	
	@Test
	public void testMinByte() {
		assertEquals((byte)5, MathUtil.min((byte)5, (byte)10));
		assertEquals((byte)-10, MathUtil.min((byte)-5, (byte)-10));
		assertEquals((byte)0, MathUtil.min((byte)0, (byte)5));
	}
	
	@Test
	public void testMaxByte() {
		assertEquals((byte)10, MathUtil.max((byte)5, (byte)10));
		assertEquals((byte)-5, MathUtil.max((byte)-5, (byte)-10));
		assertEquals((byte)5, MathUtil.max((byte)0, (byte)5));
	}
	
	@Test
	public void testFastInvSqrt() {
		// Test that fast inverse square root gives approximately correct results
		float result = MathUtil.fastInvSqrt(4.0f);
		float expected = 1.0f / (float)Math.sqrt(4.0f);
		// Fast inverse sqrt is approximate, so use a larger epsilon
		assertEquals(expected, result, 0.01f);
	}
	
	@Test
	public void testPow2Float() {
		assertEquals(25.0f, MathUtil.pow2(5.0f), 0.001f);
		assertEquals(0.0f, MathUtil.pow2(0.0f), 0.001f);
		assertEquals(100.0f, MathUtil.pow2(10.0f), 0.001f);
	}
	
	@Test
	public void testPow2Double() {
		assertEquals(25.0, MathUtil.pow2(5.0), 0.001);
		assertEquals(0.0, MathUtil.pow2(0.0), 0.001);
		assertEquals(100.0, MathUtil.pow2(10.0), 0.001);
	}
	
	@Test
	public void testPow2Int() {
		assertEquals(25, MathUtil.pow2(5));
		assertEquals(0, MathUtil.pow2(0));
		assertEquals(100, MathUtil.pow2(10));
	}
	
	@Test
	public void testPow2Long() {
		assertEquals(25L, MathUtil.pow2(5L));
		assertEquals(0L, MathUtil.pow2(0L));
		assertEquals(100L, MathUtil.pow2(10L));
	}
	
	@Test
	public void testLog2() {
		assertEquals(3, MathUtil.log2(8));
		assertEquals(4, MathUtil.log2(16));
		assertEquals(0, MathUtil.log2(1));
		assertEquals(10, MathUtil.log2(1024));
	}
}
