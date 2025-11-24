package mattmc.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MathUtils utility class.
 */
public class MathUtilsTest {
    
    @Test
    public void testClampInt() {
        assertEquals(5, MathUtils.clamp(5, 0, 10));
        assertEquals(0, MathUtils.clamp(-5, 0, 10));
        assertEquals(10, MathUtils.clamp(15, 0, 10));
        assertEquals(0, MathUtils.clamp(0, 0, 10));
        assertEquals(10, MathUtils.clamp(10, 0, 10));
    }
    
    @Test
    public void testClampFloat() {
        assertEquals(5.0f, MathUtils.clamp(5.0f, 0.0f, 10.0f), 0.0001f);
        assertEquals(0.0f, MathUtils.clamp(-5.0f, 0.0f, 10.0f), 0.0001f);
        assertEquals(10.0f, MathUtils.clamp(15.0f, 0.0f, 10.0f), 0.0001f);
        assertEquals(0.5f, MathUtils.clamp(0.5f, 0.0f, 1.0f), 0.0001f);
    }
    
    @Test
    public void testClampDouble() {
        assertEquals(5.0, MathUtils.clamp(5.0, 0.0, 10.0), 0.0001);
        assertEquals(0.0, MathUtils.clamp(-5.0, 0.0, 10.0), 0.0001);
        assertEquals(10.0, MathUtils.clamp(15.0, 0.0, 10.0), 0.0001);
        assertEquals(0.5, MathUtils.clamp(0.5, 0.0, 1.0), 0.0001);
    }
    
    @Test
    public void testLerpFloat() {
        assertEquals(0.0f, MathUtils.lerp(0.0f, 10.0f, 0.0f), 0.0001f);
        assertEquals(10.0f, MathUtils.lerp(0.0f, 10.0f, 1.0f), 0.0001f);
        assertEquals(5.0f, MathUtils.lerp(0.0f, 10.0f, 0.5f), 0.0001f);
        assertEquals(2.5f, MathUtils.lerp(0.0f, 10.0f, 0.25f), 0.0001f);
        assertEquals(7.5f, MathUtils.lerp(0.0f, 10.0f, 0.75f), 0.0001f);
        
        // Test with negative values
        assertEquals(-5.0f, MathUtils.lerp(-10.0f, 0.0f, 0.5f), 0.0001f);
    }
    
    @Test
    public void testLerpDouble() {
        assertEquals(0.0, MathUtils.lerp(0.0, 10.0, 0.0), 0.0001);
        assertEquals(10.0, MathUtils.lerp(0.0, 10.0, 1.0), 0.0001);
        assertEquals(5.0, MathUtils.lerp(0.0, 10.0, 0.5), 0.0001);
        assertEquals(2.5, MathUtils.lerp(0.0, 10.0, 0.25), 0.0001);
        assertEquals(7.5, MathUtils.lerp(0.0, 10.0, 0.75), 0.0001);
        
        // Test with negative values
        assertEquals(-5.0, MathUtils.lerp(-10.0, 0.0, 0.5), 0.0001);
    }
    
    @Test
    public void testApproximatelyFloat() {
        assertTrue(MathUtils.approximately(1.0f, 1.0f));
        assertTrue(MathUtils.approximately(1.0f, 1.0f + MathUtils.EPSILON * 0.5f));
        assertFalse(MathUtils.approximately(1.0f, 1.0f + MathUtils.EPSILON * 2.0f));
        assertTrue(MathUtils.approximately(0.0f, 0.0f));
        assertFalse(MathUtils.approximately(0.0f, 1.0f));
    }
    
    @Test
    public void testApproximatelyDouble() {
        assertTrue(MathUtils.approximately(1.0, 1.0));
        assertTrue(MathUtils.approximately(1.0, 1.0 + MathUtils.EPSILON_D * 0.5));
        assertFalse(MathUtils.approximately(1.0, 1.0 + MathUtils.EPSILON_D * 2.0));
        assertTrue(MathUtils.approximately(0.0, 0.0));
        assertFalse(MathUtils.approximately(0.0, 1.0));
    }
    
    @Test
    public void testIsZeroFloat() {
        assertTrue(MathUtils.isZero(0.0f));
        assertTrue(MathUtils.isZero(MathUtils.EPSILON * 0.5f));
        assertFalse(MathUtils.isZero(MathUtils.EPSILON * 2.0f));
        assertFalse(MathUtils.isZero(1.0f));
        assertFalse(MathUtils.isZero(-1.0f));
    }
    
    @Test
    public void testIsZeroDouble() {
        assertTrue(MathUtils.isZero(0.0));
        assertTrue(MathUtils.isZero(MathUtils.EPSILON_D * 0.5));
        assertFalse(MathUtils.isZero(MathUtils.EPSILON_D * 2.0));
        assertFalse(MathUtils.isZero(1.0));
        assertFalse(MathUtils.isZero(-1.0));
    }
    
    @Test
    public void testFloorDiv() {
        assertEquals(2, MathUtils.floorDiv(10, 5));
        assertEquals(3, MathUtils.floorDiv(10, 3));
        assertEquals(-4, MathUtils.floorDiv(-10, 3)); // Floor division rounds towards negative infinity
        assertEquals(-2, MathUtils.floorDiv(10, -5));
    }
    
    @Test
    public void testFloorMod() {
        assertEquals(0, MathUtils.floorMod(10, 5));
        assertEquals(1, MathUtils.floorMod(10, 3));
        assertEquals(2, MathUtils.floorMod(-10, 3)); // Floor mod is always positive
        assertEquals(0, MathUtils.floorMod(10, -5));
    }
    
    @Test
    public void testMinMax() {
        assertEquals(5, MathUtils.min(5, 10));
        assertEquals(5, MathUtils.min(10, 5));
        assertEquals(10, MathUtils.max(5, 10));
        assertEquals(10, MathUtils.max(10, 5));
        
        assertEquals(5.0f, MathUtils.min(5.0f, 10.0f), 0.0001f);
        assertEquals(10.0f, MathUtils.max(5.0f, 10.0f), 0.0001f);
        
        assertEquals(5.0, MathUtils.min(5.0, 10.0), 0.0001);
        assertEquals(10.0, MathUtils.max(5.0, 10.0), 0.0001);
    }
    
    @Test
    public void testAbs() {
        assertEquals(5, MathUtils.abs(5));
        assertEquals(5, MathUtils.abs(-5));
        assertEquals(0, MathUtils.abs(0));
        
        assertEquals(5.0f, MathUtils.abs(5.0f), 0.0001f);
        assertEquals(5.0f, MathUtils.abs(-5.0f), 0.0001f);
        
        assertEquals(5.0, MathUtils.abs(5.0), 0.0001);
        assertEquals(5.0, MathUtils.abs(-5.0), 0.0001);
    }
    
    @Test
    public void testRound() {
        assertEquals(5, MathUtils.round(5.0f));
        assertEquals(5, MathUtils.round(5.4f));
        assertEquals(6, MathUtils.round(5.5f));
        assertEquals(6, MathUtils.round(5.9f));
        
        assertEquals(5L, MathUtils.round(5.0));
        assertEquals(5L, MathUtils.round(5.4));
        assertEquals(6L, MathUtils.round(5.5));
        assertEquals(6L, MathUtils.round(5.9));
    }
    
    @Test
    public void testFloorCeil() {
        assertEquals(5.0, MathUtils.floor(5.9), 0.0001);
        assertEquals(5.0, MathUtils.floor(5.0), 0.0001);
        assertEquals(5.0, MathUtils.floor(5.1), 0.0001);
        
        assertEquals(6.0, MathUtils.ceil(5.1), 0.0001);
        assertEquals(5.0, MathUtils.ceil(5.0), 0.0001);
        assertEquals(6.0, MathUtils.ceil(5.9), 0.0001);
        
        assertEquals(5, MathUtils.floor(5.9f));
        assertEquals(5, MathUtils.floor(5.0f));
        assertEquals(5, MathUtils.floor(5.1f));
        
        assertEquals(6, MathUtils.ceil(5.1f));
        assertEquals(5, MathUtils.ceil(5.0f));
        assertEquals(6, MathUtils.ceil(5.9f));
    }
    
    @Test
    public void testSquare() {
        assertEquals(25, MathUtils.square(5));
        assertEquals(25, MathUtils.square(-5));
        assertEquals(0, MathUtils.square(0));
        
        assertEquals(25.0f, MathUtils.square(5.0f), 0.0001f);
        assertEquals(25.0f, MathUtils.square(-5.0f), 0.0001f);
        
        assertEquals(25.0, MathUtils.square(5.0), 0.0001);
        assertEquals(25.0, MathUtils.square(-5.0), 0.0001);
    }
    
    @Test
    public void testSqrt() {
        assertEquals(5.0, MathUtils.sqrt(25.0), 0.0001);
        assertEquals(0.0, MathUtils.sqrt(0.0), 0.0001);
        assertEquals(1.0, MathUtils.sqrt(1.0), 0.0001);
    }
    
    @Test
    public void testTrigConversions() {
        assertEquals(Math.PI, MathUtils.toRadians(180.0), 0.0001);
        assertEquals(Math.PI / 2, MathUtils.toRadians(90.0), 0.0001);
        assertEquals(0.0, MathUtils.toRadians(0.0), 0.0001);
        
        assertEquals(180.0, MathUtils.toDegrees(Math.PI), 0.0001);
        assertEquals(90.0, MathUtils.toDegrees(Math.PI / 2), 0.0001);
        assertEquals(0.0, MathUtils.toDegrees(0.0), 0.0001);
    }
    
    @Test
    public void testEpsilonConstants() {
        assertEquals(1e-6f, MathUtils.EPSILON);
        assertEquals(1e-10, MathUtils.EPSILON_D);
    }
}
