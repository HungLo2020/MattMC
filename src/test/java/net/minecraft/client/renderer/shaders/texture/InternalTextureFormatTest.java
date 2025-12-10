package net.minecraft.client.renderer.shaders.texture;

import net.minecraft.client.renderer.shaders.gl.GlVersion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

/**
 * Tests for InternalTextureFormat enum.
 */
public class InternalTextureFormatTest {

    @Test
    public void testFromString() {
        Optional<InternalTextureFormat> result = InternalTextureFormat.fromString("RGBA8");
        assertTrue(result.isPresent());
        assertEquals(InternalTextureFormat.RGBA8, result.get());
    }

    @Test
    public void testFromStringCaseInsensitive() {
        Optional<InternalTextureFormat> result = InternalTextureFormat.fromString("rgba8");
        assertTrue(result.isPresent());
        assertEquals(InternalTextureFormat.RGBA8, result.get());
    }

    @Test
    public void testFromStringInvalid() {
        Optional<InternalTextureFormat> result = InternalTextureFormat.fromString("INVALID_FORMAT");
        assertFalse(result.isPresent());
    }

    @Test
    public void testRGBA8Properties() {
        InternalTextureFormat format = InternalTextureFormat.RGBA8;
        assertEquals(PixelFormat.RGBA, format.getPixelFormat());
        assertEquals(ShaderDataType.FLOAT, format.getShaderDataType());
        assertEquals(GlVersion.GL_11, format.getMinimumGlVersion());
    }

    @Test
    public void testR32FProperties() {
        InternalTextureFormat format = InternalTextureFormat.R32F;
        assertEquals(PixelFormat.RED, format.getPixelFormat());
        assertEquals(ShaderDataType.FLOAT, format.getShaderDataType());
        assertEquals(GlVersion.GL_30, format.getMinimumGlVersion());
    }

    @Test
    public void testRGBA32UIProperties() {
        InternalTextureFormat format = InternalTextureFormat.RGBA32UI;
        assertEquals(PixelFormat.RGBA_INTEGER, format.getPixelFormat());
        assertEquals(ShaderDataType.UINT, format.getShaderDataType());
    }

    @Test
    public void testAllFormatsHaveGlFormat() {
        for (InternalTextureFormat format : InternalTextureFormat.values()) {
            assertTrue(format.getGlFormat() > 0, "Format " + format + " should have valid GL format");
        }
    }

    @Test
    public void testAllFormatsHavePixelFormat() {
        for (InternalTextureFormat format : InternalTextureFormat.values()) {
            assertNotNull(format.getPixelFormat(), "Format " + format + " should have pixel format");
        }
    }
}
