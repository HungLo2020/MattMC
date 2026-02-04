package com.seibel.distanthorizons.core.sql.dto.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Rust FFM implementation of VarintUtil.
 * This verifies that the Rust native library is properly loaded and functions correctly.
 */
public class VarintUtilTest {

@Test
public void testZigzagEncodePositive() {
assertEquals(0, VarintUtil.zigzagEncode(0), "0 should encode to 0");
assertEquals(2, VarintUtil.zigzagEncode(1), "1 should encode to 2");
assertEquals(4, VarintUtil.zigzagEncode(2), "2 should encode to 4");
assertEquals(6, VarintUtil.zigzagEncode(3), "3 should encode to 6");
assertEquals(8, VarintUtil.zigzagEncode(4), "4 should encode to 8");
}

@Test
public void testZigzagEncodeNegative() {
assertEquals(1, VarintUtil.zigzagEncode(-1), "-1 should encode to 1");
assertEquals(3, VarintUtil.zigzagEncode(-2), "-2 should encode to 3");
assertEquals(5, VarintUtil.zigzagEncode(-3), "-3 should encode to 5");
assertEquals(7, VarintUtil.zigzagEncode(-4), "-4 should encode to 7");
}

@Test
public void testZigzagEncodeLargeValues() {
int largePositive = 1000000;
int largeNegative = -1000000;

int encodedPositive = VarintUtil.zigzagEncode(largePositive);
int encodedNegative = VarintUtil.zigzagEncode(largeNegative);

assertTrue(encodedPositive > 0, "Large positive value should encode to positive");
assertTrue(encodedNegative > 0, "Large negative value should encode to positive");
}

@Test
public void testZigzagDecodePositive() {
assertEquals(0, VarintUtil.zigzagDecode(0), "0 should decode to 0");
assertEquals(1, VarintUtil.zigzagDecode(2), "2 should decode to 1");
assertEquals(2, VarintUtil.zigzagDecode(4), "4 should decode to 2");
assertEquals(3, VarintUtil.zigzagDecode(6), "6 should decode to 3");
assertEquals(4, VarintUtil.zigzagDecode(8), "8 should decode to 4");
}

@Test
public void testZigzagDecodeNegative() {
assertEquals(-1, VarintUtil.zigzagDecode(1), "1 should decode to -1");
assertEquals(-2, VarintUtil.zigzagDecode(3), "3 should decode to -2");
assertEquals(-3, VarintUtil.zigzagDecode(5), "5 should decode to -3");
assertEquals(-4, VarintUtil.zigzagDecode(7), "7 should decode to -4");
}

@Test
public void testZigzagRoundtrip() {
// Test roundtrip encoding/decoding for various values
int[] testValues = {0, 1, -1, 2, -2, 100, -100, 1000, -1000, 10000, -10000};

for (int value : testValues) {
int encoded = VarintUtil.zigzagEncode(value);
int decoded = VarintUtil.zigzagDecode(encoded);
assertEquals(value, decoded, "Roundtrip should preserve value: " + value);
}
}

@Test
public void testZigzagEdgeCases() {
// Test edge cases
assertEquals(0, VarintUtil.zigzagEncode(0), "Zero should encode to zero");

// Test that small negative numbers encode to small positive numbers
int encodedMinusOne = VarintUtil.zigzagEncode(-1);
assertTrue(encodedMinusOne > 0 && encodedMinusOne < 10, "-1 should encode to a small positive number");
}

@Test
public void testZigzagExtremeValues() {
// Test with extreme values
int maxInt = Integer.MAX_VALUE;
int minInt = Integer.MIN_VALUE;

int encodedMax = VarintUtil.zigzagEncode(maxInt);
int encodedMin = VarintUtil.zigzagEncode(minInt);

// Verify roundtrip
assertEquals(maxInt, VarintUtil.zigzagDecode(encodedMax), "MAX_VALUE roundtrip should work");
assertEquals(minInt, VarintUtil.zigzagDecode(encodedMin), "MIN_VALUE roundtrip should work");
}
}
