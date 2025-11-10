package mattmc.client.renderer.chunk;

import java.util.Arrays;

/**
 * Growable array of primitive floats to avoid boxing overhead.
 * Eliminates the need for ArrayList<Float> which creates millions of Float objects.
 * 
 * ISSUE-002 fix: Replaces ArrayList<Float> in MeshBuilder to improve performance.
 */
class FloatList {
    private float[] data;
    private int size;
    
    /**
     * Create a FloatList with default initial capacity.
     */
    public FloatList() {
        this(1024);
    }
    
    /**
     * Create a FloatList with specified initial capacity.
     */
    public FloatList(int initialCapacity) {
        this.data = new float[initialCapacity];
        this.size = 0;
    }
    
    /**
     * Add a float value to the list.
     */
    public void add(float value) {
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
    public float get(int index) {
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
    public float[] toArray() {
        return Arrays.copyOf(data, size);
    }
    
    /**
     * Get the raw internal array (may contain extra capacity).
     * Use size() to know how many elements are valid.
     */
    public float[] getRawArray() {
        return data;
    }
}
