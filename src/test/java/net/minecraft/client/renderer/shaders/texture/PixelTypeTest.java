package net.minecraft.client.renderer.shaders.texture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

/**
 * Tests for PixelType enum.
 */
public class PixelTypeTest {

    @Test
    public void testFromString() {
        Optional<PixelType> result = PixelType.fromString("FLOAT");
        assertTrue(result.isPresent());
        assertEquals(PixelType.FLOAT, result.get());
    }

    @Test
    public void testFromStringCaseInsensitive() {
        Optional<PixelType> result = PixelType.fromString("float");
        assertTrue(result.isPresent());
        assertEquals(PixelType.FLOAT, result.get());
    }

    @Test
    public void testFromStringInvalid() {
        Optional<PixelType> result = PixelType.fromString("INVALID");
        assertFalse(result.isPresent());
    }

    @Test
    public void testFLOATProperties() {
        PixelType type = PixelType.FLOAT;
        assertEquals(4, type.getByteSize());
    }

    @Test
    public void testUNSIGNED_BYTEProperties() {
        PixelType type = PixelType.UNSIGNED_BYTE;
        assertEquals(1, type.getByteSize());
    }

    @Test
    public void testAllTypesHaveValidByteSize() {
        for (PixelType type : PixelType.values()) {
            assertTrue(type.getByteSize() >= 1 && type.getByteSize() <= 4,
                "Type " + type + " should have byte size between 1 and 4");
        }
    }
}
