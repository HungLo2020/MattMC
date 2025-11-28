package mattmc.world.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the day/night cycle system.
 * 
 * Note: The DayCycle class now uses Minecraft's timing formulas.
 * The timeOfDayFloat value follows Minecraft's DimensionType.timeOfDay() calculation:
 * - 0.0 = sunrise
 * - 0.25 = noon (sun at zenith)
 * - 0.5 = sunset
 * - 0.75 = midnight
 * - 1.0 = sunrise again
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
        
        // Note: Minecraft's timeOfDay formula maps:
        // - Noon (time 6000) -> ~0.0 (sun at zenith)
        // - Evening (time 12000) -> ~0.21 (sun setting)
        // - Midnight (time 18000) -> 0.5 (moon at zenith)
        // - Morning (time 0) -> ~0.78 (sun rising)
        
        cycle.setWorldTime(DayCycle.NOON);
        float noonAngle = cycle.getCelestialAngle();
        assertEquals(0.0f, noonAngle, 0.01f, 
            "At noon, celestial angle should be ~0.0 (sun at zenith)");
        
        cycle.setWorldTime(DayCycle.MIDNIGHT);
        float midnightAngle = cycle.getCelestialAngle();
        assertEquals(0.5f, midnightAngle, 0.01f, 
            "At midnight, celestial angle should be ~0.5 (moon at zenith)");
        
        // At time 0 (dawn), the angle should be around 0.78
        cycle.setWorldTime(0L);
        float dawnAngle = cycle.getCelestialAngle();
        assertTrue(dawnAngle > 0.7f && dawnAngle < 0.85f, 
            "At dawn, celestial angle should be ~0.78, was: " + dawnAngle);
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
        
        // At noon, the sun direction should have significant positive Y component
        cycle.setWorldTime(DayCycle.NOON);
        float[] noonDir = cycle.getSunDirection();
        assertTrue(noonDir[1] > 0.5f, "Sun Y should be positive at noon, was: " + noonDir[1]);
        
        // At sunset, sun direction Y should be lower
        cycle.setWorldTime(DayCycle.SUNSET);
        float[] sunsetDir = cycle.getSunDirection();
        assertTrue(sunsetDir[1] < noonDir[1], "Sun should be lower at sunset than noon");
    }
    
    @Test
    public void testSkyColor() {
        DayCycle cycle = new DayCycle();
        
        // Day sky - the Minecraft formula multiplies base color by brightness factor
        cycle.setWorldTime(DayCycle.NOON);
        float[] dayColor = cycle.getSkyColor();
        assertEquals(3, dayColor.length);
        // At noon, brightness factor should be high (sky is brightest)
        assertTrue(dayColor[0] > 0.4f && dayColor[0] < 0.6f, "Day sky red component: " + dayColor[0]);
        assertTrue(dayColor[1] > 0.7f && dayColor[1] < 0.9f, "Day sky green component: " + dayColor[1]);
        assertTrue(dayColor[2] > 0.8f && dayColor[2] < 1.0f, "Day sky blue component: " + dayColor[2]);
        
        // Night sky should be dark
        cycle.setWorldTime(DayCycle.MIDNIGHT);
        float[] nightColor = cycle.getSkyColor();
        assertTrue(nightColor[0] < 0.2f, "Night sky should be dark (R): " + nightColor[0]);
        assertTrue(nightColor[1] < 0.2f, "Night sky should be dark (G): " + nightColor[1]);
        assertTrue(nightColor[2] < 0.3f, "Night sky should be dark blue (B): " + nightColor[2]);
    }
    
    @Test
    public void testSkyBrightness() {
        DayCycle cycle = new DayCycle();
        
        // Brightness at noon (Minecraft formula: based on cos of celestial angle)
        cycle.setWorldTime(DayCycle.NOON);
        float noonBrightness = cycle.getSkyBrightness();
        assertTrue(noonBrightness > 0.8f, "Noon should be bright, was: " + noonBrightness);
        
        // Brightness at midnight should be lower
        cycle.setWorldTime(DayCycle.MIDNIGHT);
        float midnightBrightness = cycle.getSkyBrightness();
        assertTrue(midnightBrightness < 0.4f, "Midnight should be dim, was: " + midnightBrightness);
        assertTrue(midnightBrightness >= 0.2f, "Midnight should have some ambient light, was: " + midnightBrightness);
        
        // Transitioning brightness during sunset
        cycle.setWorldTime(DayCycle.SUNSET);
        float sunsetBrightness = cycle.getSkyBrightness();
        assertTrue(sunsetBrightness < noonBrightness, 
            "Sunset should be dimmer than noon");
        assertTrue(sunsetBrightness > midnightBrightness, 
            "Sunset should be brighter than midnight");
    }
    
    @Test
    public void testMoonPhase() {
        DayCycle cycle = new DayCycle();
        
        // Day 0 = phase 0 (full moon)
        cycle.setWorldTime(0L);
        assertEquals(0, cycle.getMoonPhase());
        
        // Day 1 = phase 1
        cycle.setWorldTime(DayCycle.TICKS_PER_DAY);
        assertEquals(1, cycle.getMoonPhase());
        
        // Day 7 = phase 7
        cycle.setWorldTime(7 * DayCycle.TICKS_PER_DAY);
        assertEquals(7, cycle.getMoonPhase());
        
        // Day 8 = phase 0 again (wraps)
        cycle.setWorldTime(8 * DayCycle.TICKS_PER_DAY);
        assertEquals(0, cycle.getMoonPhase());
    }
    
    @Test
    public void testStarBrightness() {
        DayCycle cycle = new DayCycle();
        
        // Stars should be visible at night
        cycle.setWorldTime(DayCycle.MIDNIGHT);
        float midnightStars = cycle.getStarBrightness();
        assertTrue(midnightStars > 0.2f, "Stars should be visible at midnight: " + midnightStars);
        
        // Stars should not be visible during day
        cycle.setWorldTime(DayCycle.NOON);
        float noonStars = cycle.getStarBrightness();
        assertTrue(noonStars < 0.05f, "Stars should not be visible at noon: " + noonStars);
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
