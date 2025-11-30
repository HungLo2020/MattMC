package mattmc.client.particle;

import mattmc.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResourceLocation parsing and manipulation.
 */
class ResourceLocationTest {
    
    @Test
    void testParseWithNamespace() {
        ResourceLocation loc = new ResourceLocation("mattmc:smoke");
        assertEquals("mattmc", loc.getNamespace());
        assertEquals("smoke", loc.getPath());
    }
    
    @Test
    void testParseWithoutNamespace() {
        ResourceLocation loc = new ResourceLocation("smoke");
        assertEquals("mattmc", loc.getNamespace());
        assertEquals("smoke", loc.getPath());
    }
    
    @Test
    void testParseWithPath() {
        ResourceLocation loc = new ResourceLocation("textures/particle/flame");
        assertEquals("mattmc", loc.getNamespace());
        assertEquals("textures/particle/flame", loc.getPath());
    }
    
    @Test
    void testConstructorWithNamespaceAndPath() {
        ResourceLocation loc = new ResourceLocation("minecraft", "flame");
        assertEquals("minecraft", loc.getNamespace());
        assertEquals("flame", loc.getPath());
    }
    
    @Test
    void testToString() {
        ResourceLocation loc = new ResourceLocation("mattmc", "smoke");
        assertEquals("mattmc:smoke", loc.toString());
    }
    
    @Test
    void testEquality() {
        ResourceLocation loc1 = new ResourceLocation("mattmc:smoke");
        ResourceLocation loc2 = new ResourceLocation("mattmc", "smoke");
        ResourceLocation loc3 = new ResourceLocation("smoke");
        
        assertEquals(loc1, loc2);
        assertEquals(loc2, loc3);
        assertEquals(loc1.hashCode(), loc2.hashCode());
    }
    
    @Test
    void testInequality() {
        ResourceLocation loc1 = new ResourceLocation("mattmc:smoke");
        ResourceLocation loc2 = new ResourceLocation("mattmc:flame");
        ResourceLocation loc3 = new ResourceLocation("minecraft:smoke");
        
        assertNotEquals(loc1, loc2);
        assertNotEquals(loc1, loc3);
    }
    
    @Test
    void testToFilePath() {
        ResourceLocation loc = new ResourceLocation("mattmc:particles/smoke");
        String path = loc.toFilePath("assets/", ".json");
        // Namespace is no longer included in path since resources are directly under assets/
        assertEquals("assets/particles/smoke.json", path);
    }
    
    @Test
    void testTryParseValid() {
        ResourceLocation loc = ResourceLocation.tryParse("mattmc:smoke");
        assertNotNull(loc);
        assertEquals("smoke", loc.getPath());
    }
    
    @Test
    void testWithDefaultNamespace() {
        ResourceLocation loc = ResourceLocation.withDefaultNamespace("flame");
        assertEquals("mattmc", loc.getNamespace());
        assertEquals("flame", loc.getPath());
    }
    
    @Test
    void testComparable() {
        ResourceLocation loc1 = new ResourceLocation("mattmc:apple");
        ResourceLocation loc2 = new ResourceLocation("mattmc:banana");
        
        assertTrue(loc1.compareTo(loc2) < 0);
        assertTrue(loc2.compareTo(loc1) > 0);
        assertEquals(0, loc1.compareTo(new ResourceLocation("apple")));
    }
}
