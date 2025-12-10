package net.minecraft.client.renderer.shaders.texture;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL30C;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DepthBufferFormat enum.
 * Verifies IRIS 1.21.9 verbatim copy correctness.
 */
class DepthBufferFormatTest {

    @Test
    void testAllFormatsExist() {
        // Verify all 8 depth formats exist
        assertEquals(8, DepthBufferFormat.values().length);
        assertNotNull(DepthBufferFormat.DEPTH);
        assertNotNull(DepthBufferFormat.DEPTH16);
        assertNotNull(DepthBufferFormat.DEPTH24);
        assertNotNull(DepthBufferFormat.DEPTH32);
        assertNotNull(DepthBufferFormat.DEPTH32F);
        assertNotNull(DepthBufferFormat.DEPTH_STENCIL);
        assertNotNull(DepthBufferFormat.DEPTH24_STENCIL8);
        assertNotNull(DepthBufferFormat.DEPTH32F_STENCIL8);
    }

    @Test
    void testCombinedStencilFlag() {
        // Non-stencil formats
        assertFalse(DepthBufferFormat.DEPTH.isCombinedStencil());
        assertFalse(DepthBufferFormat.DEPTH16.isCombinedStencil());
        assertFalse(DepthBufferFormat.DEPTH24.isCombinedStencil());
        assertFalse(DepthBufferFormat.DEPTH32.isCombinedStencil());
        assertFalse(DepthBufferFormat.DEPTH32F.isCombinedStencil());
        
        // Combined stencil formats
        assertTrue(DepthBufferFormat.DEPTH_STENCIL.isCombinedStencil());
        assertTrue(DepthBufferFormat.DEPTH24_STENCIL8.isCombinedStencil());
        assertTrue(DepthBufferFormat.DEPTH32F_STENCIL8.isCombinedStencil());
    }

    @Test
    void testFromGlEnum() {
        assertEquals(DepthBufferFormat.DEPTH, DepthBufferFormat.fromGlEnum(GL30C.GL_DEPTH_COMPONENT));
        assertEquals(DepthBufferFormat.DEPTH16, DepthBufferFormat.fromGlEnum(GL30C.GL_DEPTH_COMPONENT16));
        assertEquals(DepthBufferFormat.DEPTH24, DepthBufferFormat.fromGlEnum(GL30C.GL_DEPTH_COMPONENT24));
        assertEquals(DepthBufferFormat.DEPTH32, DepthBufferFormat.fromGlEnum(GL30C.GL_DEPTH_COMPONENT32));
        assertEquals(DepthBufferFormat.DEPTH32F, DepthBufferFormat.fromGlEnum(GL30C.GL_DEPTH_COMPONENT32F));
        assertEquals(DepthBufferFormat.DEPTH_STENCIL, DepthBufferFormat.fromGlEnum(GL30C.GL_DEPTH_STENCIL));
        assertEquals(DepthBufferFormat.DEPTH24_STENCIL8, DepthBufferFormat.fromGlEnum(GL30C.GL_DEPTH24_STENCIL8));
        assertEquals(DepthBufferFormat.DEPTH32F_STENCIL8, DepthBufferFormat.fromGlEnum(GL30C.GL_DEPTH32F_STENCIL8));
        
        // Unknown enum returns null
        assertNull(DepthBufferFormat.fromGlEnum(0x9999));
    }

    @Test
    void testFromGlEnumOrDefault() {
        // Valid enums
        assertEquals(DepthBufferFormat.DEPTH24, DepthBufferFormat.fromGlEnumOrDefault(GL30C.GL_DEPTH_COMPONENT24));
        
        // Unknown enum returns DEPTH as default
        assertEquals(DepthBufferFormat.DEPTH, DepthBufferFormat.fromGlEnumOrDefault(0x9999));
    }

    @Test
    void testGetGlInternalFormat() {
        assertEquals(GL30C.GL_DEPTH_COMPONENT, DepthBufferFormat.DEPTH.getGlInternalFormat());
        assertEquals(GL30C.GL_DEPTH_COMPONENT16, DepthBufferFormat.DEPTH16.getGlInternalFormat());
        assertEquals(GL30C.GL_DEPTH_COMPONENT24, DepthBufferFormat.DEPTH24.getGlInternalFormat());
        assertEquals(GL30C.GL_DEPTH_COMPONENT32, DepthBufferFormat.DEPTH32.getGlInternalFormat());
        assertEquals(GL30C.GL_DEPTH_COMPONENT32F, DepthBufferFormat.DEPTH32F.getGlInternalFormat());
        assertEquals(GL30C.GL_DEPTH_STENCIL, DepthBufferFormat.DEPTH_STENCIL.getGlInternalFormat());
        assertEquals(GL30C.GL_DEPTH24_STENCIL8, DepthBufferFormat.DEPTH24_STENCIL8.getGlInternalFormat());
        assertEquals(GL30C.GL_DEPTH32F_STENCIL8, DepthBufferFormat.DEPTH32F_STENCIL8.getGlInternalFormat());
    }

    @Test
    void testGetGlType() {
        // Non-stencil formats return GL_DEPTH_COMPONENT
        assertEquals(GL30C.GL_DEPTH_COMPONENT, DepthBufferFormat.DEPTH.getGlType());
        assertEquals(GL30C.GL_DEPTH_COMPONENT, DepthBufferFormat.DEPTH16.getGlType());
        assertEquals(GL30C.GL_DEPTH_COMPONENT, DepthBufferFormat.DEPTH24.getGlType());
        assertEquals(GL30C.GL_DEPTH_COMPONENT, DepthBufferFormat.DEPTH32.getGlType());
        assertEquals(GL30C.GL_DEPTH_COMPONENT, DepthBufferFormat.DEPTH32F.getGlType());
        
        // Stencil formats return GL_DEPTH_STENCIL
        assertEquals(GL30C.GL_DEPTH_STENCIL, DepthBufferFormat.DEPTH_STENCIL.getGlType());
        assertEquals(GL30C.GL_DEPTH_STENCIL, DepthBufferFormat.DEPTH24_STENCIL8.getGlType());
        assertEquals(GL30C.GL_DEPTH_STENCIL, DepthBufferFormat.DEPTH32F_STENCIL8.getGlType());
    }

    @Test
    void testGetGlFormat() {
        // DEPTH and DEPTH16 use UNSIGNED_SHORT
        assertEquals(GL30C.GL_UNSIGNED_SHORT, DepthBufferFormat.DEPTH.getGlFormat());
        assertEquals(GL30C.GL_UNSIGNED_SHORT, DepthBufferFormat.DEPTH16.getGlFormat());
        
        // DEPTH24 and DEPTH32 use UNSIGNED_INT
        assertEquals(GL30C.GL_UNSIGNED_INT, DepthBufferFormat.DEPTH24.getGlFormat());
        assertEquals(GL30C.GL_UNSIGNED_INT, DepthBufferFormat.DEPTH32.getGlFormat());
        
        // DEPTH32F uses FLOAT
        assertEquals(GL30C.GL_FLOAT, DepthBufferFormat.DEPTH32F.getGlFormat());
        
        // Stencil formats use packed formats
        assertEquals(GL30C.GL_UNSIGNED_INT_24_8, DepthBufferFormat.DEPTH_STENCIL.getGlFormat());
        assertEquals(GL30C.GL_UNSIGNED_INT_24_8, DepthBufferFormat.DEPTH24_STENCIL8.getGlFormat());
        assertEquals(GL30C.GL_FLOAT_32_UNSIGNED_INT_24_8_REV, DepthBufferFormat.DEPTH32F_STENCIL8.getGlFormat());
    }

    @Test
    void testConsistentRoundTrip() {
        // All formats should round-trip through GL enum conversion
        for (DepthBufferFormat format : DepthBufferFormat.values()) {
            int glEnum = format.getGlInternalFormat();
            DepthBufferFormat roundTrip = DepthBufferFormat.fromGlEnum(glEnum);
            assertEquals(format, roundTrip, "Format " + format + " failed round-trip");
        }
    }
}
