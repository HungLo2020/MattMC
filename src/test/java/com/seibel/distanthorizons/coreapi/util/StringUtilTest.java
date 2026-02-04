package com.seibel.distanthorizons.coreapi.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Rust FFM implementation of StringUtil.
 * This verifies that the Rust native library is properly loaded and functions correctly.
 */
public class StringUtilTest {
	
	@Test
	public void testByteArrayToHexStringBasic() {
		byte[] bytes = {0x00, 0x01, 0x0F, (byte)0xFF};
		String result = StringUtil.byteArrayToHexString(bytes);
		assertEquals("00010FFF", result, "Should convert bytes to hex correctly");
	}
	
	@Test
	public void testByteArrayToHexStringEmpty() {
		byte[] bytes = {};
		String result = StringUtil.byteArrayToHexString(bytes);
		assertEquals("", result, "Empty array should produce empty string");
	}
	
	@Test
	public void testByteArrayToHexStringSingleByte() {
		byte[] bytes = {(byte)0xAB};
		String result = StringUtil.byteArrayToHexString(bytes);
		assertEquals("AB", result, "Single byte should convert correctly");
	}
	
	@Test
	public void testByteArrayToHexStringAllZeros() {
		byte[] bytes = {0x00, 0x00, 0x00};
		String result = StringUtil.byteArrayToHexString(bytes);
		assertEquals("000000", result, "All zeros should convert correctly");
	}
	
	@Test
	public void testByteArrayToHexStringAllOnes() {
		byte[] bytes = {(byte)0xFF, (byte)0xFF, (byte)0xFF};
		String result = StringUtil.byteArrayToHexString(bytes);
		assertEquals("FFFFFF", result, "All 0xFF bytes should convert correctly");
	}
	
	@Test
	public void testByteArrayToHexStringMixedValues() {
		byte[] bytes = {0x12, 0x34, 0x56, 0x78, (byte)0x9A, (byte)0xBC, (byte)0xDE, (byte)0xF0};
		String result = StringUtil.byteArrayToHexString(bytes);
		assertEquals("123456789ABCDEF0", result, "Mixed values should convert correctly");
	}
	
	@Test
	public void testByteArrayToHexStringLowerHalf() {
		byte[] bytes = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09};
		String result = StringUtil.byteArrayToHexString(bytes);
		assertEquals("00010203040506070809", result, "Lower half of byte values should convert correctly");
	}
	
	@Test
	public void testByteArrayToHexStringUpperHalf() {
		byte[] bytes = {(byte)0xA0, (byte)0xB0, (byte)0xC0, (byte)0xD0, (byte)0xE0, (byte)0xF0};
		String result = StringUtil.byteArrayToHexString(bytes);
		assertEquals("A0B0C0D0E0F0", result, "Upper half of byte values should convert correctly");
	}
}
