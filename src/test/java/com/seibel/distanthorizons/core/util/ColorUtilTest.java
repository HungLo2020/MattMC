package com.seibel.distanthorizons.core.util;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ColorUtil Rust FFM implementation.
 * Verifies behavioral equivalence between the Rust native implementation
 * and the expected color manipulation behaviors.
 */
public class ColorUtilTest {
	
	@Test
	public void testRgbToInt() {
		// Test basic RGB to int conversion
		assertEquals(0xFFFF0000, ColorUtil.rgbToInt(255, 0, 0)); // Red
		assertEquals(0xFF00FF00, ColorUtil.rgbToInt(0, 255, 0)); // Green
		assertEquals(0xFF0000FF, ColorUtil.rgbToInt(0, 0, 255)); // Blue
		assertEquals(0xFFFFFFFF, ColorUtil.rgbToInt(255, 255, 255)); // White
		assertEquals(0xFF000000, ColorUtil.rgbToInt(0, 0, 0)); // Black
		
		// Test with mixed values
		assertEquals(0xFF804020, ColorUtil.rgbToInt(128, 64, 32));
	}
	
	@Test
	public void testArgbToInt() {
		// Test ARGB to int conversion
		assertEquals(0x80FF0000, ColorUtil.argbToInt(128, 255, 0, 0));
		assertEquals(0xFFFFFFFF, ColorUtil.argbToInt(255, 255, 255, 255));
		assertEquals(0x00000000, ColorUtil.argbToInt(0, 0, 0, 0));
		
		// Test with various alpha values
		assertEquals(0xAA112233, ColorUtil.argbToInt(170, 17, 34, 51));
	}
	
	@Test
	public void testArgbToIntFloat() {
		// Test ARGB float to int conversion (0.0-1.0 range)
		assertEquals(0xFFFF0000, ColorUtil.argbToInt(1.0f, 1.0f, 0.0f, 0.0f));
		assertEquals(0x7F7F7F7F, ColorUtil.argbToInt(0.5f, 0.5f, 0.5f, 0.5f));
		assertEquals(0x00000000, ColorUtil.argbToInt(0.0f, 0.0f, 0.0f, 0.0f));
	}
	
	@Test
	public void testGetAlpha() {
		int color = 0x80FF00AA;
		assertEquals(0x80, ColorUtil.getAlpha(color));
		
		color = 0xFFFFFFFF;
		assertEquals(0xFF, ColorUtil.getAlpha(color));
		
		color = 0x00112233;
		assertEquals(0x00, ColorUtil.getAlpha(color));
	}
	
	@Test
	public void testGetRed() {
		int color = 0x80FF00AA;
		assertEquals(0xFF, ColorUtil.getRed(color));
		
		color = 0xFFAA0000;
		assertEquals(0xAA, ColorUtil.getRed(color));
	}
	
	@Test
	public void testGetGreen() {
		int color = 0x80FF00AA;
		assertEquals(0x00, ColorUtil.getGreen(color));
		
		color = 0xFF00AA00;
		assertEquals(0xAA, ColorUtil.getGreen(color));
	}
	
	@Test
	public void testGetBlue() {
		int color = 0x80FF00AA;
		assertEquals(0xAA, ColorUtil.getBlue(color));
		
		color = 0xFF0000BB;
		assertEquals(0xBB, ColorUtil.getBlue(color));
	}
	
	@Test
	public void testSetAlpha() {
		int color = 0xFF000000;
		int newColor = ColorUtil.setAlpha(color, 128);
		assertEquals(128, ColorUtil.getAlpha(newColor));
		
		// Verify other components unchanged
		assertEquals(0, ColorUtil.getRed(newColor));
		assertEquals(0, ColorUtil.getGreen(newColor));
		assertEquals(0, ColorUtil.getBlue(newColor));
	}
	
	@Test
	public void testSetRed() {
		int color = 0xFF000000;
		int newColor = ColorUtil.setRed(color, 200);
		assertEquals(200, ColorUtil.getRed(newColor));
		
		// Verify other components unchanged
		assertEquals(0xFF, ColorUtil.getAlpha(newColor));
		assertEquals(0, ColorUtil.getGreen(newColor));
		assertEquals(0, ColorUtil.getBlue(newColor));
	}
	
	@Test
	public void testSetGreen() {
		int color = 0xFF000000;
		int newColor = ColorUtil.setGreen(color, 150);
		assertEquals(150, ColorUtil.getGreen(newColor));
		
		// Verify other components unchanged
		assertEquals(0xFF, ColorUtil.getAlpha(newColor));
		assertEquals(0, ColorUtil.getRed(newColor));
		assertEquals(0, ColorUtil.getBlue(newColor));
	}
	
	@Test
	public void testSetBlue() {
		int color = 0xFF000000;
		int newColor = ColorUtil.setBlue(color, 100);
		assertEquals(100, ColorUtil.getBlue(newColor));
		
		// Verify other components unchanged
		assertEquals(0xFF, ColorUtil.getAlpha(newColor));
		assertEquals(0, ColorUtil.getRed(newColor));
		assertEquals(0, ColorUtil.getGreen(newColor));
	}
	
	@Test
	public void testApplyShadeInt() {
		// Test darkening
		int white = ColorUtil.rgbToInt(255, 255, 255);
		int darkened = ColorUtil.applyShade(white, -50);
		assertEquals(205, ColorUtil.getRed(darkened));
		assertEquals(205, ColorUtil.getGreen(darkened));
		assertEquals(205, ColorUtil.getBlue(darkened));
		
		// Test lightening
		int gray = ColorUtil.rgbToInt(100, 100, 100);
		int lightened = ColorUtil.applyShade(gray, 50);
		assertEquals(150, ColorUtil.getRed(lightened));
		assertEquals(150, ColorUtil.getGreen(lightened));
		assertEquals(150, ColorUtil.getBlue(lightened));
		
		// Test clamping (shouldn't go below 0)
		int dark = ColorUtil.rgbToInt(10, 10, 10);
		int clamped = ColorUtil.applyShade(dark, -20);
		assertEquals(0, ColorUtil.getRed(clamped));
		assertEquals(0, ColorUtil.getGreen(clamped));
		assertEquals(0, ColorUtil.getBlue(clamped));
		
		// Test clamping (shouldn't go above 255)
		int bright = ColorUtil.rgbToInt(250, 250, 250);
		int clampedBright = ColorUtil.applyShade(bright, 20);
		assertEquals(255, ColorUtil.getRed(clampedBright));
		assertEquals(255, ColorUtil.getGreen(clampedBright));
		assertEquals(255, ColorUtil.getBlue(clampedBright));
	}
	
	@Test
	public void testApplyShadeFloat() {
		// Test darkening (shade < 1.0)
		int white = ColorUtil.rgbToInt(255, 255, 255);
		int darkened = ColorUtil.applyShade(white, 0.5f);
		assertEquals(127, ColorUtil.getRed(darkened));
		assertEquals(127, ColorUtil.getGreen(darkened));
		assertEquals(127, ColorUtil.getBlue(darkened));
		
		// Test lightening (shade > 1.0)
		int gray = ColorUtil.rgbToInt(100, 100, 100);
		int lightened = ColorUtil.applyShade(gray, 2.0f);
		assertEquals(200, ColorUtil.getRed(lightened));
		assertEquals(200, ColorUtil.getGreen(lightened));
		assertEquals(200, ColorUtil.getBlue(lightened));
		
		// Test clamping
		int bright = ColorUtil.rgbToInt(200, 200, 200);
		int clamped = ColorUtil.applyShade(bright, 2.0f);
		assertEquals(255, ColorUtil.getRed(clamped));
		assertEquals(255, ColorUtil.getGreen(clamped));
		assertEquals(255, ColorUtil.getBlue(clamped));
	}
	
	@Test
	public void testMultiplyARGBwithRGB() {
		int argb = ColorUtil.argbToInt(255, 255, 128, 64);
		int rgb = ColorUtil.rgbToInt(128, 255, 255);
		int result = ColorUtil.multiplyARGBwithRGB(argb, rgb);
		
		// Alpha should remain unchanged
		assertEquals(255, ColorUtil.getAlpha(result));
		
		// RGB should be multiplied: 255 * 128 / 255 = 128
		assertEquals(128, ColorUtil.getRed(result));
		// 128 * 255 / 255 = 128
		assertEquals(128, ColorUtil.getGreen(result));
		// 64 * 255 / 255 = 64
		assertEquals(64, ColorUtil.getBlue(result));
	}
	
	@Test
	public void testMultiplyARGBwithARGB() {
		int color1 = ColorUtil.argbToInt(255, 255, 128, 64);
		int color2 = ColorUtil.argbToInt(128, 255, 255, 255);
		int result = ColorUtil.multiplyARGBwithARGB(color1, color2);
		
		// All components should be multiplied
		// 255 * 128 / 255 = 128
		assertEquals(128, ColorUtil.getAlpha(result));
		// 255 * 255 / 255 = 255
		assertEquals(255, ColorUtil.getRed(result));
		// 128 * 255 / 255 = 128
		assertEquals(128, ColorUtil.getGreen(result));
		// 64 * 255 / 255 = 64
		assertEquals(64, ColorUtil.getBlue(result));
	}
	
	@Test
	public void testArgbToAhsv() {
		// Test conversion of pure red
		int red = ColorUtil.rgbToInt(255, 0, 0);
		float[] ahsv = ColorUtil.argbToAhsv(red);
		
		assertEquals(1.0f, ahsv[0], 0.01f); // Alpha
		assertEquals(0.0f, ahsv[1], 0.01f); // Hue (red is at 0 degrees)
		assertEquals(1.0f, ahsv[2], 0.01f); // Saturation
		assertEquals(1.0f, ahsv[3], 0.01f); // Value
		
		// Test conversion of pure green
		int green = ColorUtil.rgbToInt(0, 255, 0);
		ahsv = ColorUtil.argbToAhsv(green);
		
		assertEquals(1.0f, ahsv[0], 0.01f); // Alpha
		assertEquals(120.0f, ahsv[1], 0.01f); // Hue (green is at 120 degrees)
		assertEquals(1.0f, ahsv[2], 0.01f); // Saturation
		assertEquals(1.0f, ahsv[3], 0.01f); // Value
		
		// Test conversion of gray (no saturation)
		int gray = ColorUtil.rgbToInt(128, 128, 128);
		ahsv = ColorUtil.argbToAhsv(gray);
		
		assertEquals(1.0f, ahsv[0], 0.01f); // Alpha
		assertEquals(0.0f, ahsv[2], 0.01f); // Saturation should be 0
		assertEquals(128.0f / 255.0f, ahsv[3], 0.01f); // Value
	}
	
	@Test
	public void testAhsvToArgb() {
		// Test conversion from AHSV back to ARGB
		// Red: H=0, S=1, V=1
		int red = ColorUtil.ahsvToArgb(1.0f, 0.0f, 1.0f, 1.0f);
		assertEquals(255, ColorUtil.getRed(red));
		assertEquals(0, ColorUtil.getGreen(red));
		assertEquals(0, ColorUtil.getBlue(red));
		
		// Green: H=120, S=1, V=1
		int green = ColorUtil.ahsvToArgb(1.0f, 120.0f, 1.0f, 1.0f);
		assertEquals(0, ColorUtil.getRed(green));
		assertEquals(255, ColorUtil.getGreen(green));
		assertEquals(0, ColorUtil.getBlue(green));
		
		// Blue: H=240, S=1, V=1
		int blue = ColorUtil.ahsvToArgb(1.0f, 240.0f, 1.0f, 1.0f);
		assertEquals(0, ColorUtil.getRed(blue));
		assertEquals(0, ColorUtil.getGreen(blue));
		assertEquals(255, ColorUtil.getBlue(blue));
		
		// Gray: S=0 (achromatic)
		int gray = ColorUtil.ahsvToArgb(1.0f, 0.0f, 0.0f, 0.5f);
		int expected = (int) (0.5f * 255);
		assertEquals(expected, ColorUtil.getRed(gray));
		assertEquals(expected, ColorUtil.getGreen(gray));
		assertEquals(expected, ColorUtil.getBlue(gray));
	}
	
	@Test
	public void testRoundTripAhsvConversion() {
		// Test that converting to AHSV and back preserves the color
		int[] testColors = {
			ColorUtil.RED,
			ColorUtil.GREEN,
			ColorUtil.BLUE,
			ColorUtil.YELLOW,
			ColorUtil.CYAN,
			ColorUtil.MAGENTA,
			ColorUtil.WHITE,
			ColorUtil.BLACK,
			ColorUtil.GRAY
		};
		
		for (int originalColor : testColors) {
			float[] ahsv = ColorUtil.argbToAhsv(originalColor);
			int roundTrip = ColorUtil.ahsvToArgb(ahsv[0], ahsv[1], ahsv[2], ahsv[3]);
			
			// Allow small differences due to floating point precision
			assertEquals(ColorUtil.getRed(originalColor), ColorUtil.getRed(roundTrip), 2);
			assertEquals(ColorUtil.getGreen(originalColor), ColorUtil.getGreen(roundTrip), 2);
			assertEquals(ColorUtil.getBlue(originalColor), ColorUtil.getBlue(roundTrip), 2);
		}
	}
	
	@Test
	public void testColorConstants() {
		// Verify that color constants are properly initialized
		assertEquals(0, ColorUtil.getAlpha(ColorUtil.INVISIBLE));
		
		assertEquals(255, ColorUtil.getRed(ColorUtil.RED));
		assertEquals(0, ColorUtil.getGreen(ColorUtil.RED));
		assertEquals(0, ColorUtil.getBlue(ColorUtil.RED));
		
		assertEquals(0, ColorUtil.getRed(ColorUtil.GREEN));
		assertEquals(255, ColorUtil.getGreen(ColorUtil.GREEN));
		assertEquals(0, ColorUtil.getBlue(ColorUtil.GREEN));
		
		assertEquals(0, ColorUtil.getRed(ColorUtil.BLUE));
		assertEquals(0, ColorUtil.getGreen(ColorUtil.BLUE));
		assertEquals(255, ColorUtil.getBlue(ColorUtil.BLUE));
		
		assertEquals(255, ColorUtil.getRed(ColorUtil.WHITE));
		assertEquals(255, ColorUtil.getGreen(ColorUtil.WHITE));
		assertEquals(255, ColorUtil.getBlue(ColorUtil.WHITE));
		
		assertEquals(0, ColorUtil.getRed(ColorUtil.BLACK));
		assertEquals(0, ColorUtil.getGreen(ColorUtil.BLACK));
		assertEquals(0, ColorUtil.getBlue(ColorUtil.BLACK));
	}
	
	@Test
	public void testToHexString() {
		int color = ColorUtil.argbToInt(170, 255, 128, 64);
		String hex = ColorUtil.toHexString(color);
		
		assertTrue(hex.contains("A:aa"));
		assertTrue(hex.contains("R:ff"));
		assertTrue(hex.contains("G:80"));
		assertTrue(hex.contains("B:40"));
	}
	
	@Test
	public void testToString() {
		int color = ColorUtil.argbToInt(170, 255, 128, 64);
		String str = ColorUtil.toString(color);
		
		assertTrue(str.contains("A:170"));
		assertTrue(str.contains("R:255"));
		assertTrue(str.contains("G:128"));
		assertTrue(str.contains("B:64"));
	}
	
	@Test
	public void testColorObjConversions() {
		// Test RGB Color object conversion
		int colorInt = ColorUtil.rgbToInt(100, 150, 200);
		Color colorObj = ColorUtil.toColorObjRGB(colorInt);
		
		assertEquals(100, colorObj.getRed());
		assertEquals(150, colorObj.getGreen());
		assertEquals(200, colorObj.getBlue());
		assertEquals(255, colorObj.getAlpha()); // Should be fully opaque
		
		// Test ARGB Color object conversion
		int colorIntArgb = ColorUtil.argbToInt(128, 100, 150, 200);
		Color colorObjArgb = ColorUtil.toColorObjARGB(colorIntArgb);
		
		assertEquals(100, colorObjArgb.getRed());
		assertEquals(150, colorObjArgb.getGreen());
		assertEquals(200, colorObjArgb.getBlue());
		assertEquals(128, colorObjArgb.getAlpha());
		
		// Test Color to int conversion
		Color javaColor = new Color(75, 125, 175, 200);
		int convertedInt = ColorUtil.toColorInt(javaColor);
		
		assertEquals(200, ColorUtil.getAlpha(convertedInt));
		assertEquals(75, ColorUtil.getRed(convertedInt));
		assertEquals(125, ColorUtil.getGreen(convertedInt));
		assertEquals(175, ColorUtil.getBlue(convertedInt));
	}
}
