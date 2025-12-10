package net.minecraft.client.renderer.shaders.targets;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BufferFlipper (IRIS-based buffer flip tracking).
 */
class BufferFlipperTest {

    @Test
    void testInitialState() {
        BufferFlipper flipper = new BufferFlipper();
        
        // Initially, no buffers should be flipped
        assertFalse(flipper.isFlipped(0));
        assertFalse(flipper.isFlipped(1));
        assertFalse(flipper.isFlipped(15));
    }

    @Test
    void testFlipBuffer() {
        BufferFlipper flipper = new BufferFlipper();
        
        // Flip buffer 0
        flipper.flip(0);
        assertTrue(flipper.isFlipped(0));
        assertFalse(flipper.isFlipped(1));
    }

    @Test
    void testFlipBufferTwice() {
        BufferFlipper flipper = new BufferFlipper();
        
        // Flip buffer twice - should toggle back to not flipped
        flipper.flip(0);
        assertTrue(flipper.isFlipped(0));
        
        flipper.flip(0);
        assertFalse(flipper.isFlipped(0));
    }

    @Test
    void testFlipMultipleBuffers() {
        BufferFlipper flipper = new BufferFlipper();
        
        // Flip multiple buffers
        flipper.flip(0);
        flipper.flip(2);
        flipper.flip(5);
        
        assertTrue(flipper.isFlipped(0));
        assertFalse(flipper.isFlipped(1));
        assertTrue(flipper.isFlipped(2));
        assertFalse(flipper.isFlipped(3));
        assertFalse(flipper.isFlipped(4));
        assertTrue(flipper.isFlipped(5));
    }

    @Test
    void testSnapshot() {
        BufferFlipper flipper = new BufferFlipper();
        
        // Flip some buffers
        flipper.flip(1);
        flipper.flip(3);
        flipper.flip(7);
        
        // Create snapshot
        ImmutableSet<Integer> snapshot = flipper.snapshot();
        
        // Verify snapshot contains the right buffers
        assertEquals(3, snapshot.size());
        assertTrue(snapshot.contains(1));
        assertTrue(snapshot.contains(3));
        assertTrue(snapshot.contains(7));
        assertFalse(snapshot.contains(0));
        assertFalse(snapshot.contains(2));
    }

    @Test
    void testSnapshotIsImmutable() {
        BufferFlipper flipper = new BufferFlipper();
        
        flipper.flip(0);
        ImmutableSet<Integer> snapshot = flipper.snapshot();
        
        // Modify original
        flipper.flip(1);
        
        // Snapshot should be unchanged
        assertEquals(1, snapshot.size());
        assertTrue(snapshot.contains(0));
        assertFalse(snapshot.contains(1));
    }

    @Test
    void testGetFlippedBuffers() {
        BufferFlipper flipper = new BufferFlipper();
        
        flipper.flip(0);
        flipper.flip(5);
        flipper.flip(10);
        
        // Count flipped buffers via iterator
        int count = 0;
        var iterator = flipper.getFlippedBuffers();
        while (iterator.hasNext()) {
            iterator.nextInt();
            count++;
        }
        
        assertEquals(3, count);
    }

    @Test
    void testFlipCycle() {
        BufferFlipper flipper = new BufferFlipper();
        
        // Cycle through multiple flips
        assertFalse(flipper.isFlipped(0));
        flipper.flip(0);
        assertTrue(flipper.isFlipped(0));
        flipper.flip(0);
        assertFalse(flipper.isFlipped(0));
        flipper.flip(0);
        assertTrue(flipper.isFlipped(0));
    }
}
