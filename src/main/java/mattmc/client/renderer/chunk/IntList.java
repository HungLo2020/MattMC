package mattmc.client.renderer.chunk;

import java.util.Arrays;

/**
 * Growable array of primitive ints to avoid boxing overhead.
 * Eliminates the need for ArrayList<Integer> which creates millions of Integer objects.
 * 
 * ISSUE-002 fix: Replaces ArrayList<Integer> in MeshBuilder to improve performance.
 */
class IntList {
    private int[] data;
    private int size;
    
    /**
     * Create an IntList with default initial capacity.
     */
    public IntList() {
        this(1024);
    }
    
    /**
     * Create an IntList with specified initial capacity.
     */
    public IntList(int initialCapacity) {
        this.data = new int[initialCapacity];
        this.size = 0;
    }
    
    /**
     * Add an int value to the list.
     */
    public void add(int value) {
        if (size == data.length) {
            // Grow by 1.5x when capacity is reached
            int newCapacity = data.length + (data.length >> 1);
            data = Arrays.copyOf(data, newCapacity);
        }
        data[size++] = value;
    }
    
    /**
     * Get the value at the specified index.
     */
    public int get(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return data[index];
    }
    
    /**
     * Get the current size of the list.
     */
    public int size() {
        return size;
    }
    
    /**
     * Clear the list (reset size to 0).
     */
    public void clear() {
        size = 0;
    }
    
    /**
     * Convert to a trimmed array containing only the used elements.
     */
    public int[] toArray() {
        return Arrays.copyOf(data, size);
    }
    
    /**
     * Get the raw internal array (may contain extra capacity).
     * Use size() to know how many elements are valid.
     */
    public int[] getRawArray() {
        return data;
    }
}
