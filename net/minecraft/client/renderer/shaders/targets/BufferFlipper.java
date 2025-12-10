package net.minecraft.client.renderer.shaders.targets;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Tracks which render targets are "flipped" for ping-pong rendering.
 * 
 * <p>When a buffer is not flipped, users should write to the alternate texture
 * and read from the main texture.
 * 
 * <p>When a buffer is flipped, users should write to the main texture and read
 * from the alternate texture.
 * 
 * <p>Copied VERBATIM from IRIS 1.21.9 BufferFlipper.java
 */
public class BufferFlipper {
    private final IntSet flippedBuffers;

    public BufferFlipper() {
        this.flippedBuffers = new IntOpenHashSet();
    }

    /**
     * Flips the specified buffer. If it was already flipped, unflips it.
     * 
     * @param target The buffer index to flip (0-15 for colortex0-15)
     */
    public void flip(int target) {
        if (!flippedBuffers.remove(target)) {
            // If the target wasn't in the set, add it to the set.
            flippedBuffers.add(target);
        }
    }

    /**
     * Returns true if this buffer is flipped.
     * 
     * <p>If this buffer is not flipped, then users should write to the alternate
     * variant and read from the main variant.
     * 
     * <p>If this buffer is flipped, then users should write to the main variant
     * and read from the alternate variant.
     * 
     * @param target The buffer index to check
     * @return true if the buffer is currently flipped
     */
    public boolean isFlipped(int target) {
        return flippedBuffers.contains(target);
    }

    /**
     * Returns an iterator over all currently flipped buffers.
     * 
     * @return Iterator of flipped buffer indices
     */
    public IntIterator getFlippedBuffers() {
        return flippedBuffers.iterator();
    }

    /**
     * Creates an immutable snapshot of the current flip state.
     * 
     * @return Immutable set of currently flipped buffer indices
     */
    public ImmutableSet<Integer> snapshot() {
        return ImmutableSet.copyOf(flippedBuffers);
    }
}
