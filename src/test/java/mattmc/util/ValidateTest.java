package mattmc.util;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Validate utility class.
 */
public class ValidateTest {
    
    @Test
    public void testNotNull() {
        String value = "test";
        assertEquals(value, Validate.notNull(value, "Should not be null"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notNull(null, "Value cannot be null");
        });
    }
    
    @Test
    public void testRequireNonNull() {
        String value = "test";
        assertEquals(value, Validate.requireNonNull(value, "Should not be null"));
        
        assertThrows(NullPointerException.class, () -> {
            Validate.requireNonNull(null, "Value cannot be null");
        });
    }
    
    @Test
    public void testNotEmptyString() {
        String value = "test";
        assertEquals(value, Validate.notEmpty(value, "Should not be empty"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty((String) null, "String cannot be null");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty("", "String cannot be empty");
        });
        
        // Should pass with whitespace
        assertEquals(" ", Validate.notEmpty(" ", "Should not be empty"));
    }
    
    @Test
    public void testNotBlank() {
        String value = "test";
        assertEquals(value, Validate.notBlank(value, "Should not be blank"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notBlank(null, "String cannot be null");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notBlank("", "String cannot be empty");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notBlank("   ", "String cannot be blank");
        });
    }
    
    @Test
    public void testInRangeInt() {
        assertEquals(5, Validate.inRange(5, 0, 10, "Out of range"));
        assertEquals(0, Validate.inRange(0, 0, 10, "Out of range"));
        assertEquals(10, Validate.inRange(10, 0, 10, "Out of range"));
        
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            Validate.inRange(-1, 0, 10, "Value out of range");
        });
        assertTrue(e.getMessage().contains("value=-1"));
        assertTrue(e.getMessage().contains("min=0"));
        assertTrue(e.getMessage().contains("max=10"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.inRange(11, 0, 10, "Value out of range");
        });
    }
    
    @Test
    public void testInRangeLong() {
        assertEquals(5L, Validate.inRange(5L, 0L, 10L, "Out of range"));
        assertEquals(0L, Validate.inRange(0L, 0L, 10L, "Out of range"));
        assertEquals(10L, Validate.inRange(10L, 0L, 10L, "Out of range"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.inRange(-1L, 0L, 10L, "Value out of range");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.inRange(11L, 0L, 10L, "Value out of range");
        });
    }
    
    @Test
    public void testInRangeFloat() {
        assertEquals(5.0f, Validate.inRange(5.0f, 0.0f, 10.0f, "Out of range"));
        assertEquals(0.0f, Validate.inRange(0.0f, 0.0f, 10.0f, "Out of range"));
        assertEquals(10.0f, Validate.inRange(10.0f, 0.0f, 10.0f, "Out of range"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.inRange(-1.0f, 0.0f, 10.0f, "Value out of range");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.inRange(11.0f, 0.0f, 10.0f, "Value out of range");
        });
    }
    
    @Test
    public void testInRangeDouble() {
        assertEquals(5.0, Validate.inRange(5.0, 0.0, 10.0, "Out of range"));
        assertEquals(0.0, Validate.inRange(0.0, 0.0, 10.0, "Out of range"));
        assertEquals(10.0, Validate.inRange(10.0, 0.0, 10.0, "Out of range"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.inRange(-1.0, 0.0, 10.0, "Value out of range");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.inRange(11.0, 0.0, 10.0, "Value out of range");
        });
    }
    
    @Test
    public void testIsTrue() {
        Validate.isTrue(true, "Condition should be true");
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.isTrue(false, "Condition must be true");
        });
    }
    
    @Test
    public void testIsFalse() {
        Validate.isFalse(false, "Condition should be false");
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.isFalse(true, "Condition must be false");
        });
    }
    
    @Test
    public void testNotEmptyArray() {
        String[] array = {"a", "b", "c"};
        assertArrayEquals(array, Validate.notEmpty(array, "Should not be empty"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty((String[]) null, "Array cannot be null");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty(new String[0], "Array cannot be empty");
        });
    }
    
    @Test
    public void testNotEmptyCollection() {
        List<String> list = new ArrayList<>();
        list.add("test");
        assertEquals(list, Validate.notEmpty(list, "Should not be empty"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty((List<String>) null, "Collection cannot be null");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty(new ArrayList<String>(), "Collection cannot be empty");
        });
    }
    
    @Test
    public void testNotEmptyIntArray() {
        int[] array = {1, 2, 3};
        assertArrayEquals(array, Validate.notEmpty(array, "Should not be empty"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty((int[]) null, "Array cannot be null");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty(new int[0], "Array cannot be empty");
        });
    }
    
    @Test
    public void testNotEmptyByteArray() {
        byte[] array = {1, 2, 3};
        assertArrayEquals(array, Validate.notEmpty(array, "Should not be empty"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty((byte[]) null, "Array cannot be null");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty(new byte[0], "Array cannot be empty");
        });
    }
    
    @Test
    public void testNotEmptyFloatArray() {
        float[] array = {1.0f, 2.0f, 3.0f};
        assertArrayEquals(array, Validate.notEmpty(array, "Should not be empty"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty((float[]) null, "Array cannot be null");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty(new float[0], "Array cannot be empty");
        });
    }
    
    @Test
    public void testNotEmptyDoubleArray() {
        double[] array = {1.0, 2.0, 3.0};
        assertArrayEquals(array, Validate.notEmpty(array, "Should not be empty"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty((double[]) null, "Array cannot be null");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.notEmpty(new double[0], "Array cannot be empty");
        });
    }
    
    @Test
    public void testPositiveInt() {
        assertEquals(5, Validate.positive(5, "Must be positive"));
        
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            Validate.positive(0, "Value must be positive");
        });
        assertTrue(e.getMessage().contains("value=0"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.positive(-5, "Value must be positive");
        });
    }
    
    @Test
    public void testPositiveLong() {
        assertEquals(5L, Validate.positive(5L, "Must be positive"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.positive(0L, "Value must be positive");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.positive(-5L, "Value must be positive");
        });
    }
    
    @Test
    public void testPositiveFloat() {
        assertEquals(5.0f, Validate.positive(5.0f, "Must be positive"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.positive(0.0f, "Value must be positive");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.positive(-5.0f, "Value must be positive");
        });
    }
    
    @Test
    public void testPositiveDouble() {
        assertEquals(5.0, Validate.positive(5.0, "Must be positive"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.positive(0.0, "Value must be positive");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.positive(-5.0, "Value must be positive");
        });
    }
    
    @Test
    public void testNonNegativeInt() {
        assertEquals(5, Validate.nonNegative(5, "Must be non-negative"));
        assertEquals(0, Validate.nonNegative(0, "Must be non-negative"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.nonNegative(-1, "Value must be non-negative");
        });
    }
    
    @Test
    public void testNonNegativeLong() {
        assertEquals(5L, Validate.nonNegative(5L, "Must be non-negative"));
        assertEquals(0L, Validate.nonNegative(0L, "Must be non-negative"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.nonNegative(-1L, "Value must be non-negative");
        });
    }
    
    @Test
    public void testNonNegativeFloat() {
        assertEquals(5.0f, Validate.nonNegative(5.0f, "Must be non-negative"));
        assertEquals(0.0f, Validate.nonNegative(0.0f, "Must be non-negative"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.nonNegative(-1.0f, "Value must be non-negative");
        });
    }
    
    @Test
    public void testNonNegativeDouble() {
        assertEquals(5.0, Validate.nonNegative(5.0, "Must be non-negative"));
        assertEquals(0.0, Validate.nonNegative(0.0, "Must be non-negative"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Validate.nonNegative(-1.0, "Value must be non-negative");
        });
    }
    
    @Test
    public void testValidIndex() {
        assertEquals(0, Validate.validIndex(0, 10, "Invalid index"));
        assertEquals(5, Validate.validIndex(5, 10, "Invalid index"));
        assertEquals(9, Validate.validIndex(9, 10, "Invalid index"));
        
        IndexOutOfBoundsException e = assertThrows(IndexOutOfBoundsException.class, () -> {
            Validate.validIndex(-1, 10, "Index out of bounds");
        });
        assertTrue(e.getMessage().contains("index=-1"));
        assertTrue(e.getMessage().contains("size=10"));
        
        assertThrows(IndexOutOfBoundsException.class, () -> {
            Validate.validIndex(10, 10, "Index out of bounds");
        });
    }
}
