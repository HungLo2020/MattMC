package net.minecraft.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for MthRust to verify Rust-Java JNI integration
 */
public class MthRustTest {
    
    @Test
    public void testLibraryLoading() {
        // Just verify that the library loading doesn't crash
        // It's okay if it's not available - we have fallbacks
        System.out.println("Rust library available: " + MthRust.isRustAvailable());
    }
    
    @Test
    public void testFloor() {
        assertEquals(3, MthRust.floorFloat(3.7f));
        assertEquals(-4, MthRust.floorFloat(-3.2f));
        assertEquals(3, MthRust.floorDouble(3.7));
        assertEquals(-4, MthRust.floorDouble(-3.2));
    }
    
    @Test
    public void testLfloor() {
        assertEquals(3L, MthRust.lfloorDouble(3.7));
        assertEquals(-4L, MthRust.lfloorDouble(-3.2));
    }
    
    @Test
    public void testCeil() {
        assertEquals(4, MthRust.ceilFloat(3.2f));
        assertEquals(-3, MthRust.ceilFloat(-3.7f));
        assertEquals(4, MthRust.ceilDouble(3.2));
        assertEquals(-3, MthRust.ceilDouble(-3.7));
    }
    
    @Test
    public void testCeilLong() {
        assertEquals(4L, MthRust.ceilLongDouble(3.2));
        assertEquals(-3L, MthRust.ceilLongDouble(-3.7));
    }
    
    @Test
    public void testClampInt() {
        assertEquals(5, MthRust.clampInt(5, 0, 10));
        assertEquals(0, MthRust.clampInt(-5, 0, 10));
        assertEquals(10, MthRust.clampInt(15, 0, 10));
    }
    
    @Test
    public void testClampLong() {
        assertEquals(5L, MthRust.clampLong(5L, 0L, 10L));
        assertEquals(0L, MthRust.clampLong(-5L, 0L, 10L));
        assertEquals(10L, MthRust.clampLong(15L, 0L, 10L));
    }
    
    @Test
    public void testClampFloat() {
        assertEquals(5.5f, MthRust.clampFloat(5.5f, 0.0f, 10.0f), 0.001f);
        assertEquals(0.0f, MthRust.clampFloat(-5.5f, 0.0f, 10.0f), 0.001f);
        assertEquals(10.0f, MthRust.clampFloat(15.5f, 0.0f, 10.0f), 0.001f);
    }
    
    @Test
    public void testClampDouble() {
        assertEquals(5.5, MthRust.clampDouble(5.5, 0.0, 10.0), 0.001);
        assertEquals(0.0, MthRust.clampDouble(-5.5, 0.0, 10.0), 0.001);
        assertEquals(10.0, MthRust.clampDouble(15.5, 0.0, 10.0), 0.001);
    }
    
    @Test
    public void testAbs() {
        assertEquals(5.5f, MthRust.absFloat(-5.5f), 0.001f);
        assertEquals(5.5f, MthRust.absFloat(5.5f), 0.001f);
        assertEquals(5, MthRust.absInt(-5));
        assertEquals(5, MthRust.absInt(5));
    }
    
    @Test
    public void testSquare() {
        assertEquals(25.0f, MthRust.squareFloat(5.0f), 0.001f);
        assertEquals(25.0, MthRust.squareDouble(5.0), 0.001);
        assertEquals(25, MthRust.squareInt(5));
        assertEquals(25L, MthRust.squareLong(5L));
    }
    
    @Test
    public void testNegativeSquare() {
        assertEquals(25.0f, MthRust.squareFloat(-5.0f), 0.001f);
        assertEquals(25, MthRust.squareInt(-5));
    }
}
