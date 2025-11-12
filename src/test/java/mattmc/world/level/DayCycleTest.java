package mattmc.world.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the day/night cycle system.
 */
public class DayCycleTest {
    
    @Test
    public void testDayCycleAdvancement() {
        DayCycle cycle = new DayCycle();
        assertEquals(0L, cycle.getWorldTime());
        
        // Tick forward
        cycle.tick();
        assertEquals(1L, cycle.getWorldTime());
        
        // Tick through a full day
        for (int i = 0; i < 23999; i++) {
            cycle.tick();
        }
        assertEquals(24000L, cycle.getWorldTime());
        assertEquals(0L, cycle.getTimeOfDay()); // Should wrap around
    }
    
    @Test
    public void testTimeOfDay() {
        DayCycle cycle = new DayCycle();
        
        // Start of day
        assertEquals(0L, cycle.getTimeOfDay());
        
        // Advance to noon
        cycle.setWorldTime(DayCycle.NOON);
        assertEquals(6000L, cycle.getTimeOfDay());
        
        // Advance to sunset
        cycle.setWorldTime(DayCycle.SUNSET);
        assertEquals(12000L, cycle.getTimeOfDay());
        
        // Advance to midnight
        cycle.setWorldTime(DayCycle.MIDNIGHT);
        assertEquals(18000L, cycle.getTimeOfDay());
        
        // Advance past one full day
        cycle.setWorldTime(25000L);
        assertEquals(1000L, cycle.getTimeOfDay());
    }
    
    @Test
    public void testCelestialAngle() {
        DayCycle cycle = new DayCycle();
        
        // Sunrise
        cycle.setWorldTime(0L);
        assertEquals(0.0f, cycle.getCelestialAngle(), 0.001f);
        
        // Noon (quarter through the day)
        cycle.setWorldTime(DayCycle.NOON);
        assertEquals(0.25f, cycle.getCelestialAngle(), 0.001f);
        
        // Sunset (halfway through the day)
        cycle.setWorldTime(DayCycle.SUNSET);
        assertEquals(0.5f, cycle.getCelestialAngle(), 0.001f);
        
        // Midnight (three quarters through the day)
        cycle.setWorldTime(DayCycle.MIDNIGHT);
        assertEquals(0.75f, cycle.getCelestialAngle(), 0.001f);
    }
    
    @Test
    public void testSunDirection() {
        DayCycle cycle = new DayCycle();
        
        // At sunrise, sun should be low on horizon
        cycle.setWorldTime(0L);
        float[] sunriseDir = cycle.getSunDirection();
        assertNotNull(sunriseDir);
        assertEquals(3, sunriseDir.length);
        
        // Direction should be normalized
        float length = (float) Math.sqrt(
            sunriseDir[0] * sunriseDir[0] + 
            sunriseDir[1] * sunriseDir[1] + 
            sunriseDir[2] * sunriseDir[2]
        );
        assertEquals(1.0f, length, 0.001f);
        
        // At noon, sun should be high (positive Y)
        cycle.setWorldTime(DayCycle.NOON);
        float[] noonDir = cycle.getSunDirection();
        assertTrue(noonDir[1] > 0.9f, "Sun should be nearly overhead at noon");
        
        // At sunset, sun should be low again
        cycle.setWorldTime(DayCycle.SUNSET);
        float[] sunsetDir = cycle.getSunDirection();
        assertTrue(Math.abs(sunsetDir[1]) < 0.1f, "Sun should be near horizon at sunset");
    }
    
    @Test
    public void testSkyColor() {
        DayCycle cycle = new DayCycle();
        
        // Day sky should be blue
        cycle.setWorldTime(DayCycle.NOON);
        float[] dayColor = cycle.getSkyColor();
        assertEquals(3, dayColor.length);
        assertEquals(0.53f, dayColor[0], 0.001f); // R
        assertEquals(0.81f, dayColor[1], 0.001f); // G
        assertEquals(0.92f, dayColor[2], 0.001f); // B
        
        // Night sky should be dark
        cycle.setWorldTime(DayCycle.MIDNIGHT);
        float[] nightColor = cycle.getSkyColor();
        assertTrue(nightColor[0] < 0.2f, "Night sky should be dark");
        assertTrue(nightColor[1] < 0.2f, "Night sky should be dark");
        assertTrue(nightColor[2] < 0.3f, "Night sky should be dark (slightly blue)");
    }
    
    @Test
    public void testSkyBrightness() {
        DayCycle cycle = new DayCycle();
        
        // Full brightness during day
        cycle.setWorldTime(DayCycle.NOON);
        assertEquals(1.0f, cycle.getSkyBrightness(), 0.001f);
        
        // Reduced brightness at night
        cycle.setWorldTime(DayCycle.MIDNIGHT);
        assertEquals(0.3f, cycle.getSkyBrightness(), 0.001f);
        
        // Transitioning brightness during sunset
        cycle.setWorldTime(DayCycle.SUNSET);
        float sunsetBrightness = cycle.getSkyBrightness();
        assertTrue(sunsetBrightness < 1.0f && sunsetBrightness > 0.3f, 
                   "Brightness should be transitioning during sunset");
    }
    
    @Test
    public void testInitialTime() {
        // Default should start at 0 (sunrise)
        DayCycle defaultCycle = new DayCycle();
        assertEquals(0L, defaultCycle.getWorldTime());
        
        // Can start at a specific time
        DayCycle noonCycle = new DayCycle(DayCycle.NOON);
        assertEquals(DayCycle.NOON, noonCycle.getWorldTime());
    }
}
