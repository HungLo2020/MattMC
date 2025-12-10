package net.minecraft.client.renderer.shaders.targets;

import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClearPassInformation (IRIS-based clear pass metadata).
 */
class ClearPassInformationTest {

    @Test
    void testConstruction() {
        Vector4f color = new Vector4f(1.0f, 0.5f, 0.25f, 1.0f);
        ClearPassInformation info = new ClearPassInformation(color, 1920, 1080);
        
        assertEquals(color, info.getColor());
        assertEquals(1920, info.getWidth());
        assertEquals(1080, info.getHeight());
    }

    @Test
    void testEquality() {
        Vector4f color1 = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        Vector4f color2 = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        
        ClearPassInformation info1 = new ClearPassInformation(color1, 1920, 1080);
        ClearPassInformation info2 = new ClearPassInformation(color2, 1920, 1080);
        
        assertEquals(info1, info2);
    }

    @Test
    void testInequality_DifferentColor() {
        Vector4f color1 = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        Vector4f color2 = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        
        ClearPassInformation info1 = new ClearPassInformation(color1, 1920, 1080);
        ClearPassInformation info2 = new ClearPassInformation(color2, 1920, 1080);
        
        assertNotEquals(info1, info2);
    }

    @Test
    void testInequality_DifferentWidth() {
        Vector4f color = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        
        ClearPassInformation info1 = new ClearPassInformation(color, 1920, 1080);
        ClearPassInformation info2 = new ClearPassInformation(color, 1280, 1080);
        
        assertNotEquals(info1, info2);
    }

    @Test
    void testInequality_DifferentHeight() {
        Vector4f color = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        
        ClearPassInformation info1 = new ClearPassInformation(color, 1920, 1080);
        ClearPassInformation info2 = new ClearPassInformation(color, 1920, 720);
        
        assertNotEquals(info1, info2);
    }

    @Test
    void testHashCode() {
        Vector4f color = new Vector4f(1.0f, 0.0f, 0.0f, 1.0f);
        ClearPassInformation info1 = new ClearPassInformation(color, 1920, 1080);
        ClearPassInformation info2 = new ClearPassInformation(color, 1920, 1080);
        
        // Equal objects should have equal hash codes
        assertEquals(info1.hashCode(), info2.hashCode());
    }

    @Test
    void testNotEqualToNull() {
        Vector4f color = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        ClearPassInformation info = new ClearPassInformation(color, 1920, 1080);
        
        assertNotEquals(null, info);
    }

    @Test
    void testNotEqualToDifferentType() {
        Vector4f color = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        ClearPassInformation info = new ClearPassInformation(color, 1920, 1080);
        
        assertNotEquals("not a ClearPassInformation", info);
    }
}
