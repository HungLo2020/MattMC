package mattmc.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the RenderPass enum.
 * 
 * These tests verify the basic properties and behavior of the RenderPass enum,
 * which is a core part of the rendering abstraction introduced in Stage 1.
 */
public class RenderPassTest {
    
    @Test
    public void testAllRenderPassesExist() {
        // Verify that all expected render passes are defined
        RenderPass[] passes = RenderPass.values();
        
        assertEquals(4, passes.length, "Should have exactly 4 render passes");
        
        // Verify each expected pass exists
        assertNotNull(RenderPass.valueOf("OPAQUE"));
        assertNotNull(RenderPass.valueOf("TRANSPARENT"));
        assertNotNull(RenderPass.valueOf("SHADOW"));
        assertNotNull(RenderPass.valueOf("UI"));
    }
    
    @Test
    public void testRenderPassOrdering() {
        // The order in which passes are defined is important for rendering
        // This test documents and verifies the expected order
        RenderPass[] passes = RenderPass.values();
        
        assertEquals(RenderPass.OPAQUE, passes[0], "OPAQUE should be first");
        assertEquals(RenderPass.TRANSPARENT, passes[1], "TRANSPARENT should be second");
        assertEquals(RenderPass.SHADOW, passes[2], "SHADOW should be third");
        assertEquals(RenderPass.UI, passes[3], "UI should be fourth");
    }
    
    @Test
    public void testRenderPassOrdinals() {
        // Verify ordinal values are as expected (can be useful for indexing)
        assertEquals(0, RenderPass.OPAQUE.ordinal());
        assertEquals(1, RenderPass.TRANSPARENT.ordinal());
        assertEquals(2, RenderPass.SHADOW.ordinal());
        assertEquals(3, RenderPass.UI.ordinal());
    }
    
    @Test
    public void testRenderPassNames() {
        // Verify string representation of enum values
        assertEquals("OPAQUE", RenderPass.OPAQUE.name());
        assertEquals("TRANSPARENT", RenderPass.TRANSPARENT.name());
        assertEquals("SHADOW", RenderPass.SHADOW.name());
        assertEquals("UI", RenderPass.UI.name());
    }
    
    @Test
    public void testRenderPassValueOf() {
        // Test valueOf with valid names
        assertEquals(RenderPass.OPAQUE, RenderPass.valueOf("OPAQUE"));
        assertEquals(RenderPass.TRANSPARENT, RenderPass.valueOf("TRANSPARENT"));
        assertEquals(RenderPass.SHADOW, RenderPass.valueOf("SHADOW"));
        assertEquals(RenderPass.UI, RenderPass.valueOf("UI"));
    }
    
    @Test
    public void testRenderPassValueOfInvalid() {
        // Test valueOf with invalid name throws exception
        assertThrows(IllegalArgumentException.class, () -> {
            RenderPass.valueOf("INVALID_PASS");
        });
    }
    
    @Test
    public void testRenderPassCanBeUsedInSwitch() {
        // Verify that RenderPass can be used in switch statements
        // This is important for backend implementations
        for (RenderPass pass : RenderPass.values()) {
            String description = switch (pass) {
                case OPAQUE -> "Solid geometry";
                case TRANSPARENT -> "Blended geometry";
                case SHADOW -> "Shadow maps";
                case UI -> "User interface";
            };
            
            assertNotNull(description, "Each pass should have a description");
            assertFalse(description.isEmpty(), "Description should not be empty");
        }
    }
    
    @Test
    public void testRenderPassEquality() {
        // Test that enum values have proper equality semantics
        RenderPass opaque1 = RenderPass.OPAQUE;
        RenderPass opaque2 = RenderPass.OPAQUE;
        RenderPass transparent = RenderPass.TRANSPARENT;
        
        assertSame(opaque1, opaque2, "Same enum values should be identical");
        assertEquals(opaque1, opaque2, "Same enum values should be equal");
        assertNotEquals(opaque1, transparent, "Different enum values should not be equal");
    }
    
    @Test
    public void testRenderPassIterationOrder() {
        // Test that we can iterate over passes in a predictable order
        // This is useful for rendering pipelines that need to process passes sequentially
        RenderPass[] expectedOrder = {
            RenderPass.OPAQUE,
            RenderPass.TRANSPARENT,
            RenderPass.SHADOW,
            RenderPass.UI
        };
        
        RenderPass[] actualOrder = RenderPass.values();
        assertArrayEquals(expectedOrder, actualOrder, "Pass iteration order should be consistent");
    }
}
