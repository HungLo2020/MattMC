package mattmc.nbt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BitPackedArray to verify correct bit packing/unpacking.
 */
public class BitPackedArrayTest {
    
    @Test
    public void testBasicGetSet() {
        BitPackedArray array = new BitPackedArray(4, 16);
        
        array.set(0, 5);
        array.set(1, 12);
        array.set(15, 7);
        
        assertEquals(5, array.get(0));
        assertEquals(12, array.get(1));
        assertEquals(7, array.get(15));
    }
    
    @Test
    public void test2BitsPerEntry() {
        // 2 bits can represent 0-3
        BitPackedArray array = new BitPackedArray(2, 64);
        
        for (int i = 0; i < 64; i++) {
            array.set(i, i % 4);
        }
        
        for (int i = 0; i < 64; i++) {
            assertEquals(i % 4, array.get(i), "Index " + i + " should have value " + (i % 4));
        }
    }
    
    @Test
    public void test4BitsPerEntry() {
        // 4 bits can represent 0-15
        BitPackedArray array = new BitPackedArray(4, 4096);
        
        // Fill with pattern
        for (int i = 0; i < 4096; i++) {
            array.set(i, i % 16);
        }
        
        // Verify pattern
        for (int i = 0; i < 4096; i++) {
            assertEquals(i % 16, array.get(i), "Index " + i + " should have value " + (i % 16));
        }
    }
    
    @Test
    public void testMaxValues() {
        BitPackedArray array4 = new BitPackedArray(4, 100);
        array4.set(0, 15); // Max value for 4 bits
        assertEquals(15, array4.get(0));
        
        BitPackedArray array8 = new BitPackedArray(8, 100);
        array8.set(0, 255); // Max value for 8 bits
        assertEquals(255, array8.get(0));
    }
    
    @Test
    public void testCalculateBitsPerEntry() {
        assertEquals(0, BitPackedArray.calculateBitsPerEntry(1)); // Single block
        assertEquals(4, BitPackedArray.calculateBitsPerEntry(2)); // Min 4 bits
        assertEquals(4, BitPackedArray.calculateBitsPerEntry(4)); // 4 values -> 2 bits, but min is 4
        assertEquals(4, BitPackedArray.calculateBitsPerEntry(16)); // 16 values -> 4 bits
        assertEquals(5, BitPackedArray.calculateBitsPerEntry(17)); // 17 values -> 5 bits
        assertEquals(8, BitPackedArray.calculateBitsPerEntry(256)); // 256 values -> 8 bits
    }
    
    @Test
    public void testPackingEfficiency() {
        // For 4096 entries with 4 bits each
        BitPackedArray array = new BitPackedArray(4, 4096);
        
        // Should use 4096 * 4 / 64 = 256 longs
        assertEquals(256, array.getData().length);
        
        // Compare to unpacked: would need 4096 longs (16x more space)
        long packedSize = array.getData().length * 8; // bytes
        long unpackedSize = 4096 * 8; // bytes
        
        assertTrue(packedSize < unpackedSize / 10, "Packed array should be much smaller");
    }
    
    @Test
    public void testBoundaryConditions() {
        BitPackedArray array = new BitPackedArray(4, 100);
        
        // Test first and last indices
        array.set(0, 7);
        array.set(99, 13);
        
        assertEquals(7, array.get(0));
        assertEquals(13, array.get(99));
    }
    
    @Test
    public void testInvalidIndexThrows() {
        BitPackedArray array = new BitPackedArray(4, 10);
        
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(10));
        assertThrows(IndexOutOfBoundsException.class, () -> array.set(-1, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> array.set(10, 5));
    }
    
    @Test
    public void testInvalidValueThrows() {
        BitPackedArray array = new BitPackedArray(4, 10);
        
        // Max value for 4 bits is 15
        assertThrows(IllegalArgumentException.class, () -> array.set(0, 16));
        assertThrows(IllegalArgumentException.class, () -> array.set(0, -1));
    }
}
