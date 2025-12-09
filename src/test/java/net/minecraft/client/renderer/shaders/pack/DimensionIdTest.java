package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DimensionId constants (IRIS verbatim).
 */
public class DimensionIdTest {
    
    @Test
    public void testOverworldConstant() {
        assertEquals("minecraft", DimensionId.OVERWORLD.getNamespace());
        assertEquals("overworld", DimensionId.OVERWORLD.getName());
        assertEquals("minecraft:overworld", DimensionId.OVERWORLD.toString());
    }
    
    @Test
    public void testNetherConstant() {
        assertEquals("minecraft", DimensionId.NETHER.getNamespace());
        assertEquals("the_nether", DimensionId.NETHER.getName());
        assertEquals("minecraft:the_nether", DimensionId.NETHER.toString());
    }
    
    @Test
    public void testEndConstant() {
        assertEquals("minecraft", DimensionId.END.getNamespace());
        assertEquals("the_end", DimensionId.END.getName());
        assertEquals("minecraft:the_end", DimensionId.END.toString());
    }
}
