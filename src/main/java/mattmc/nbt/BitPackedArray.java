package mattmc.nbt;

import mattmc.util.MathUtils;

/**
 * Bit-packed array implementation for efficiently storing block states.
 * Based on MattMC Java Edition's palette-based bit packing approach.
 * 
 * Instead of storing each block as a full long (64 bits), this packs multiple
 * block indices into each long using only the minimum required bits per entry.
 * 
 * For example, if a chunk section has 4 unique blocks, each block index only
 * needs 2 bits (2^2 = 4). This means we can pack 32 block indices into one long
 * instead of using 4096 separate longs.
 */
public class BitPackedArray {
    
    private final long[] data;
    private final int bitsPerEntry;
    private final int entriesPerLong;
    private final long maxEntryValue;
    private final int size;
    
    /**
     * Create a bit-packed array.
     * 
     * @param bitsPerEntry Number of bits per entry (0-64). 0 means all entries are the same (no data needed).
     * @param size Total number of entries to store
     * @throws IllegalArgumentException if bitsPerEntry is negative or greater than 64, or if size is negative
     */
    public BitPackedArray(int bitsPerEntry, int size) {
        if (bitsPerEntry < 0 || bitsPerEntry > 64) {
            throw new IllegalArgumentException("bitsPerEntry must be 0-64, got: " + bitsPerEntry);
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative, got: " + size);
        }
        
        this.bitsPerEntry = bitsPerEntry;
        this.size = size;
        
        if (bitsPerEntry == 0) {
            // All entries are the same, no data needed
            this.maxEntryValue = 0;
            this.entriesPerLong = 0;
            this.data = new long[0];
        } else {
            this.maxEntryValue = (1L << bitsPerEntry) - 1L;
            this.entriesPerLong = 64 / bitsPerEntry;
            
            // Calculate how many longs we need
            int arraySize = (size + entriesPerLong - 1) / entriesPerLong;
            this.data = new long[arraySize];
        }
    }
    
    /**
     * Create a bit-packed array from existing data.
     */
    public BitPackedArray(int bitsPerEntry, int size, long[] data) {
        this.bitsPerEntry = bitsPerEntry;
        this.size = size;
        this.data = data;
        
        if (bitsPerEntry == 0) {
            this.maxEntryValue = 0;
            this.entriesPerLong = 0;
        } else {
            this.maxEntryValue = (1L << bitsPerEntry) - 1L;
            this.entriesPerLong = 64 / bitsPerEntry;
        }
    }
    
    /**
     * Get the value at the specified index.
     */
    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        
        // If 0 bits per entry, all values are 0 (single block type)
        if (bitsPerEntry == 0) {
            return 0;
        }
        
        int longIndex = index / entriesPerLong;
        int bitIndex = (index % entriesPerLong) * bitsPerEntry;
        
        return (int) ((data[longIndex] >> bitIndex) & maxEntryValue);
    }
    
    /**
     * Set the value at the specified index.
     */
    public void set(int index, int value) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        
        // If 0 bits per entry, can only set to 0 (single block type)
        if (bitsPerEntry == 0) {
            if (value != 0) {
                throw new IllegalArgumentException("Cannot set non-zero value with 0 bits per entry");
            }
            return;
        }
        
        if (value < 0 || value > maxEntryValue) {
            throw new IllegalArgumentException("Value " + value + " exceeds max value " + maxEntryValue);
        }
        
        int longIndex = index / entriesPerLong;
        int bitIndex = (index % entriesPerLong) * bitsPerEntry;
        
        // Clear the bits at this position, then set the new value
        data[longIndex] = (data[longIndex] & ~(maxEntryValue << bitIndex)) | ((long) value << bitIndex);
    }
    
    /**
     * Get the backing long array (for NBT serialization).
     */
    public long[] getData() {
        return data;
    }
    
    /**
     * Get the number of bits per entry.
     */
    public int getBitsPerEntry() {
        return bitsPerEntry;
    }
    
    /**
     * Get the total number of entries.
     */
    public int getSize() {
        return size;
    }
    
    /**
     * Calculate the minimum bits needed to represent a palette of the given size.
     * MattMC uses a minimum of 4 bits per entry for performance reasons.
     */
    public static int calculateBitsPerEntry(int paletteSize) {
        if (paletteSize <= 0) {
            return 0; // Invalid or empty palette
        }
        if (paletteSize == 1) {
            return 0; // Single block type - no data needed
        }
        
        // Calculate bits needed: ceil(log2(paletteSize))
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        
        // MattMC uses a minimum of 4 bits for performance
        return MathUtils.clamp(bits, 4, 15);
    }
}
