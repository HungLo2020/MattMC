package mattmc.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ColorUtils class.
 */
public class ColorUtilsTest {
    
    @Test
    public void testExtractRed() {
        int color = 0xFF8040; // R=255, G=128, B=64
        assertEquals(255, ColorUtils.extractRed(color));
    }
    
    @Test
    public void testExtractGreen() {
        int color = 0xFF8040; // R=255, G=128, B=64
        assertEquals(128, ColorUtils.extractGreen(color));
    }
    
    @Test
    public void testExtractBlue() {
        int color = 0xFF8040; // R=255, G=128, B=64
        assertEquals(64, ColorUtils.extractBlue(color));
    }
    
    @Test
    public void testExtractComponents() {
        int color = 0x123456; // R=18, G=52, B=86
        assertEquals(0x12, ColorUtils.extractRed(color));
        assertEquals(0x34, ColorUtils.extractGreen(color));
        assertEquals(0x56, ColorUtils.extractBlue(color));
    }
    
    @Test
    public void testPackRGB() {
        int r = 255, g = 128, b = 64;
        int packed = ColorUtils.packRGB(r, g, b);
        assertEquals(0xFF8040, packed);
    }
    
    @Test
    public void testPackRGBWithMasking() {
        // Test that values are properly masked to 8 bits
        int packed = ColorUtils.packRGB(0x1FF, 0x1AA, 0x155); // values > 255
        assertEquals(0xFFAA55, packed);
    }
    
    @Test
    public void testToNormalizedRGB() {
        int color = 0xFF8040; // R=255, G=128, B=64
        float[] normalized = ColorUtils.toNormalizedRGB(color);
        
        assertEquals(3, normalized.length);
        assertEquals(1.0f, normalized[0], 0.001f); // 255/255 = 1.0
        assertEquals(128/255f, normalized[1], 0.001f);
        assertEquals(64/255f, normalized[2], 0.001f);
    }
    
    @Test
    public void testToNormalizedRGBA() {
        int color = 0xFF8040; // R=255, G=128, B=64
        float alpha = 0.5f;
        float[] normalized = ColorUtils.toNormalizedRGBA(color, alpha);
        
        assertEquals(4, normalized.length);
        assertEquals(1.0f, normalized[0], 0.001f);
        assertEquals(128/255f, normalized[1], 0.001f);
        assertEquals(64/255f, normalized[2], 0.001f);
        assertEquals(0.5f, normalized[3], 0.001f);
    }
    
    @Test
    public void testLerpAtZero() {
        int color1 = 0xFF0000; // Red
        int color2 = 0x0000FF; // Blue
        int result = ColorUtils.lerp(color1, color2, 0.0f);
        assertEquals(color1, result);
    }
    
    @Test
    public void testLerpAtOne() {
        int color1 = 0xFF0000; // Red
        int color2 = 0x0000FF; // Blue
        int result = ColorUtils.lerp(color1, color2, 1.0f);
        assertEquals(color2, result);
    }
    
    @Test
    public void testLerpAtHalf() {
        int color1 = 0xFF0000; // Red (255, 0, 0)
        int color2 = 0x0000FF; // Blue (0, 0, 255)
        int result = ColorUtils.lerp(color1, color2, 0.5f);
        
        // Should be approximately (127, 0, 127)
        int r = ColorUtils.extractRed(result);
        int g = ColorUtils.extractGreen(result);
        int b = ColorUtils.extractBlue(result);
        
        assertTrue(r >= 127 && r <= 128); // Allow for rounding
        assertEquals(0, g);
        assertTrue(b >= 127 && b <= 128);
    }
    
    @Test
    public void testLerpClampsLowValue() {
        int color1 = 0xFF0000;
        int color2 = 0x0000FF;
        int result = ColorUtils.lerp(color1, color2, -0.5f);
        assertEquals(color1, result); // Should clamp to 0.0 and return color1
    }
    
    @Test
    public void testLerpClampsHighValue() {
        int color1 = 0xFF0000;
        int color2 = 0x0000FF;
        int result = ColorUtils.lerp(color1, color2, 1.5f);
        assertEquals(color2, result); // Should clamp to 1.0 and return color2
    }
    
    @Test
    public void testDarkenColor() {
        int color = 0xFF8040; // R=255, G=128, B=64
        int darkened = ColorUtils.darkenColor(color);
        
        // Should be reduced to 50%
        int r = ColorUtils.extractRed(darkened);
        int g = ColorUtils.extractGreen(darkened);
        int b = ColorUtils.extractBlue(darkened);
        
        assertEquals(127, r); // 255 * 0.5 = 127.5
        assertEquals(64, g);  // 128 * 0.5 = 64
        assertEquals(32, b);  // 64 * 0.5 = 32
    }
    
    @Test
    public void testAdjustColorBrightness() {
        int color = 0xFF8040; // R=255, G=128, B=64
        int adjusted = ColorUtils.adjustColorBrightness(color, 0.25f);
        
        int r = ColorUtils.extractRed(adjusted);
        int g = ColorUtils.extractGreen(adjusted);
        int b = ColorUtils.extractBlue(adjusted);
        
        assertEquals(63, r);  // 255 * 0.25 = 63.75
        assertEquals(32, g);  // 128 * 0.25 = 32
        assertEquals(16, b);  // 64 * 0.25 = 16
    }
    
    @Test
    public void testAdjustColorBrightnessDoesNotExceed255() {
        int color = 0x808080; // R=128, G=128, B=128
        int adjusted = ColorUtils.adjustColorBrightness(color, 3.0f);
        
        // All components should be capped at 255
        int r = ColorUtils.extractRed(adjusted);
        int g = ColorUtils.extractGreen(adjusted);
        int b = ColorUtils.extractBlue(adjusted);
        
        assertEquals(255, r);
        assertEquals(255, g);
        assertEquals(255, b);
    }
    
    @Test
    public void testApplyTint() {
        int baseColor = 0xFFFFFF; // White
        int tintColor = 0xFF0000; // Red tint
        int tinted = ColorUtils.applyTint(baseColor, tintColor, 1.0f);
        
        // White tinted with red at 100% brightness should give red
        assertEquals(0xFF0000, tinted);
    }
    
    @Test
    public void testApplyTintWithBrightness() {
        int baseColor = 0xFFFFFF; // White
        int tintColor = 0xFF0000; // Red tint
        int tinted = ColorUtils.applyTint(baseColor, tintColor, 0.5f);
        
        // White tinted with red at 50% brightness
        int r = ColorUtils.extractRed(tinted);
        assertEquals(127, r); // 255 * 255/255 * 0.5 = 127.5
        assertEquals(0, ColorUtils.extractGreen(tinted));
        assertEquals(0, ColorUtils.extractBlue(tinted));
    }
    
    @Test
    public void testPackAndExtractRoundTrip() {
        int originalR = 123;
        int originalG = 45;
        int originalB = 67;
        
        int packed = ColorUtils.packRGB(originalR, originalG, originalB);
        int extractedR = ColorUtils.extractRed(packed);
        int extractedG = ColorUtils.extractGreen(packed);
        int extractedB = ColorUtils.extractBlue(packed);
        
        assertEquals(originalR, extractedR);
        assertEquals(originalG, extractedG);
        assertEquals(originalB, extractedB);
    }
}
