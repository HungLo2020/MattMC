package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NamespacedId class (IRIS verbatim).
 */
public class NamespacedIdTest {
    
    @Test
    public void testConstructorWithColon() {
        NamespacedId id = new NamespacedId("minecraft:overworld");
        assertEquals("minecraft", id.getNamespace());
        assertEquals("overworld", id.getName());
    }
    
    @Test
    public void testConstructorWithoutColon() {
        NamespacedId id = new NamespacedId("overworld");
        assertEquals("minecraft", id.getNamespace());
        assertEquals("overworld", id.getName());
    }
    
    @Test
    public void testConstructorWithNamespaceAndName() {
        NamespacedId id = new NamespacedId("custom", "dimension");
        assertEquals("custom", id.getNamespace());
        assertEquals("dimension", id.getName());
    }
    
    @Test
    public void testToString() {
        NamespacedId id = new NamespacedId("minecraft", "the_nether");
        assertEquals("minecraft:the_nether", id.toString());
    }
    
    @Test
    public void testEquality() {
        NamespacedId id1 = new NamespacedId("minecraft:overworld");
        NamespacedId id2 = new NamespacedId("minecraft", "overworld");
        NamespacedId id3 = new NamespacedId("overworld");
        
        assertEquals(id1, id2);
        assertEquals(id2, id3);
        assertEquals(id1, id3);
    }
    
    @Test
    public void testInequality() {
        NamespacedId id1 = new NamespacedId("minecraft:overworld");
        NamespacedId id2 = new NamespacedId("minecraft:the_nether");
        
        assertNotEquals(id1, id2);
    }
    
    @Test
    public void testHashCode() {
        NamespacedId id1 = new NamespacedId("minecraft:overworld");
        NamespacedId id2 = new NamespacedId("minecraft", "overworld");
        
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
