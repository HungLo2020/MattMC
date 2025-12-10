package net.minecraft.client.renderer.shaders.texture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

/**
 * Tests for PixelFormat enum.
 */
public class PixelFormatTest {

    @Test
    public void testFromString() {
        Optional<PixelFormat> result = PixelFormat.fromString("RGBA");
        assertTrue(result.isPresent());
        assertEquals(PixelFormat.RGBA, result.get());
    }

    @Test
    public void testFromStringCaseInsensitive() {
        Optional<PixelFormat> result = PixelFormat.fromString("rgba");
        assertTrue(result.isPresent());
        assertEquals(PixelFormat.RGBA, result.get());
    }

    @Test
    public void testFromStringInvalid() {
        Optional<PixelFormat> result = PixelFormat.fromString("INVALID");
        assertFalse(result.isPresent());
    }

    @Test
    public void testRGBAProperties() {
        PixelFormat format = PixelFormat.RGBA;
        assertEquals(4, format.getComponentCount());
        assertFalse(format.isInteger());
    }

    @Test
    public void testRGBA_INTEGERProperties() {
        PixelFormat format = PixelFormat.RGBA_INTEGER;
        assertEquals(4, format.getComponentCount());
        assertTrue(format.isInteger());
    }

    @Test
    public void testREDProperties() {
        PixelFormat format = PixelFormat.RED;
        assertEquals(1, format.getComponentCount());
        assertFalse(format.isInteger());
    }

    @Test
    public void testAllFormatsHaveValidComponentCount() {
        for (PixelFormat format : PixelFormat.values()) {
            assertTrue(format.getComponentCount() >= 1 && format.getComponentCount() <= 4,
                "Format " + format + " should have component count between 1 and 4");
        }
    }
}
