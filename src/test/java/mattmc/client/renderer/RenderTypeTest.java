package mattmc.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the RenderType enum.
 * 
 * These tests verify the basic properties and behavior of the RenderType enum,
 * which determines how blocks and items with transparency are rendered.
 */
public class RenderTypeTest {
    
    @Test
    public void testAllRenderTypesExist() {
        // Verify that all expected render types are defined
        RenderType[] types = RenderType.values();
        
        assertEquals(4, types.length, "Should have exactly 4 render types");
        
        // Verify each expected type exists
        assertNotNull(RenderType.valueOf("SOLID"));
        assertNotNull(RenderType.valueOf("CUTOUT"));
        assertNotNull(RenderType.valueOf("CUTOUT_MIPPED"));
        assertNotNull(RenderType.valueOf("TRANSLUCENT"));
    }
    
    @Test
    public void testRenderTypeFromJson() {
        // Test parsing from JSON strings
        assertEquals(RenderType.SOLID, RenderType.fromJson("solid"));
        assertEquals(RenderType.CUTOUT, RenderType.fromJson("cutout"));
        assertEquals(RenderType.CUTOUT_MIPPED, RenderType.fromJson("cutout_mipped"));
        assertEquals(RenderType.TRANSLUCENT, RenderType.fromJson("translucent"));
        
        // Test null and empty strings return SOLID (default)
        assertEquals(RenderType.SOLID, RenderType.fromJson(null));
        assertEquals(RenderType.SOLID, RenderType.fromJson(""));
        
        // Test unknown value returns SOLID (default)
        assertEquals(RenderType.SOLID, RenderType.fromJson("unknown"));
        
        // Test with Minecraft namespace prefix
        assertEquals(RenderType.CUTOUT, RenderType.fromJson("minecraft:cutout"));
        assertEquals(RenderType.TRANSLUCENT, RenderType.fromJson("minecraft:translucent"));
    }
    
    @Test
    public void testRenderTypeJsonNames() {
        assertEquals("solid", RenderType.SOLID.getJsonName());
        assertEquals("cutout", RenderType.CUTOUT.getJsonName());
        assertEquals("cutout_mipped", RenderType.CUTOUT_MIPPED.getJsonName());
        assertEquals("translucent", RenderType.TRANSLUCENT.getJsonName());
    }
    
    @Test
    public void testIsCutout() {
        assertFalse(RenderType.SOLID.isCutout());
        assertTrue(RenderType.CUTOUT.isCutout());
        assertTrue(RenderType.CUTOUT_MIPPED.isCutout());
        assertFalse(RenderType.TRANSLUCENT.isCutout());
    }
    
    @Test
    public void testIsTranslucent() {
        assertFalse(RenderType.SOLID.isTranslucent());
        assertFalse(RenderType.CUTOUT.isTranslucent());
        assertFalse(RenderType.CUTOUT_MIPPED.isTranslucent());
        assertTrue(RenderType.TRANSLUCENT.isTranslucent());
    }
    
    @Test
    public void testHasTransparency() {
        assertFalse(RenderType.SOLID.hasTransparency());
        assertTrue(RenderType.CUTOUT.hasTransparency());
        assertTrue(RenderType.CUTOUT_MIPPED.hasTransparency());
        assertTrue(RenderType.TRANSLUCENT.hasTransparency());
    }
    
    @Test
    public void testUsesMipmaps() {
        assertTrue(RenderType.SOLID.usesMipmaps());
        assertFalse(RenderType.CUTOUT.usesMipmaps()); // Cutout doesn't use mipmaps
        assertTrue(RenderType.CUTOUT_MIPPED.usesMipmaps());
        assertTrue(RenderType.TRANSLUCENT.usesMipmaps());
    }
    
    @Test
    public void testToRenderPass() {
        assertEquals(mattmc.client.renderer.backend.RenderPass.OPAQUE, RenderType.SOLID.toRenderPass());
        assertEquals(mattmc.client.renderer.backend.RenderPass.CUTOUT, RenderType.CUTOUT.toRenderPass());
        assertEquals(mattmc.client.renderer.backend.RenderPass.CUTOUT_MIPPED, RenderType.CUTOUT_MIPPED.toRenderPass());
        assertEquals(mattmc.client.renderer.backend.RenderPass.TRANSPARENT, RenderType.TRANSLUCENT.toRenderPass());
    }
    
    @Test
    public void testCaseInsensitiveParsing() {
        // Test that parsing is case-insensitive
        assertEquals(RenderType.SOLID, RenderType.fromJson("SOLID"));
        assertEquals(RenderType.CUTOUT, RenderType.fromJson("CUTOUT"));
        assertEquals(RenderType.CUTOUT_MIPPED, RenderType.fromJson("CUTOUT_MIPPED"));
        assertEquals(RenderType.TRANSLUCENT, RenderType.fromJson("TRANSLUCENT"));
        
        // Mixed case
        assertEquals(RenderType.CUTOUT, RenderType.fromJson("Cutout"));
        assertEquals(RenderType.CUTOUT_MIPPED, RenderType.fromJson("Cutout_Mipped"));
    }
}
