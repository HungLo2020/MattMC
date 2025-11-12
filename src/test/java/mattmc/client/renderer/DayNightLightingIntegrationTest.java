package mattmc.client.renderer;

import mattmc.world.level.DayCycle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify day/night cycle affects lighting brightness.
 */
public class DayNightLightingIntegrationTest {
    
    @Test
    public void testSkyBrightnessAffectsLighting() {
        DayCycle dayCycle = new DayCycle();
        
        // At noon, brightness should be full
        dayCycle.setWorldTime(DayCycle.NOON);
        float noonBrightness = dayCycle.getSkyBrightness();
        assertEquals(1.0f, noonBrightness, 0.001f, "Noon brightness should be 1.0");
        
        // At midnight, brightness should be dimmed
        dayCycle.setWorldTime(DayCycle.MIDNIGHT);
        float midnightBrightness = dayCycle.getSkyBrightness();
        assertEquals(0.3f, midnightBrightness, 0.001f, "Midnight brightness should be 0.3");
        
        // Verify the brightness difference
        assertTrue(noonBrightness > midnightBrightness, 
                  "Day brightness should be greater than night brightness");
        
        // Calculate the dimming factor
        float dimmingFactor = midnightBrightness / noonBrightness;
        assertEquals(0.3f, dimmingFactor, 0.001f, 
                    "Night should be 30% as bright as day");
    }
    
    @Test
    public void testSunDirectionChangesWithTime() {
        DayCycle dayCycle = new DayCycle();
        
        // At sunrise, sun should be on horizon
        dayCycle.setWorldTime(0L);
        float[] sunriseDir = dayCycle.getSunDirection();
        assertTrue(Math.abs(sunriseDir[1]) < 0.1f, 
                  "Sun should be near horizon at sunrise");
        
        // At noon, sun should be high
        dayCycle.setWorldTime(DayCycle.NOON);
        float[] noonDir = dayCycle.getSunDirection();
        assertTrue(noonDir[1] > 0.9f, 
                  "Sun should be nearly overhead at noon");
        
        // At sunset, sun should be back on horizon
        dayCycle.setWorldTime(DayCycle.SUNSET);
        float[] sunsetDir = dayCycle.getSunDirection();
        assertTrue(Math.abs(sunsetDir[1]) < 0.1f, 
                  "Sun should be near horizon at sunset");
        
        // At midnight, sun should be below horizon (negative Y)
        dayCycle.setWorldTime(DayCycle.MIDNIGHT);
        float[] midnightDir = dayCycle.getSunDirection();
        assertTrue(midnightDir[1] < 0, 
                  "Sun should be below horizon at midnight");
    }
    
    @Test
    public void testBrightnessTransitionDuringSunset() {
        DayCycle dayCycle = new DayCycle();
        
        // Just before sunset
        dayCycle.setWorldTime(11000L);
        float beforeSunsetBrightness = dayCycle.getSkyBrightness();
        assertEquals(1.0f, beforeSunsetBrightness, 0.001f, 
                    "Brightness should still be full before sunset");
        
        // During sunset
        dayCycle.setWorldTime(12000L); // Exact sunset time
        float sunsetBrightness = dayCycle.getSkyBrightness();
        assertTrue(sunsetBrightness < 1.0f && sunsetBrightness > 0.3f,
                  "Brightness should be transitioning during sunset");
        
        // After sunset (night)
        dayCycle.setWorldTime(13000L);
        float nightBrightness = dayCycle.getSkyBrightness();
        assertEquals(0.3f, nightBrightness, 0.001f,
                    "Brightness should be dimmed after sunset");
    }
    
    @Test
    public void testBrightnessTransitionDuringSunrise() {
        DayCycle dayCycle = new DayCycle();
        
        // Before sunrise (still night)
        dayCycle.setWorldTime(22000L);
        float nightBrightness = dayCycle.getSkyBrightness();
        assertEquals(0.3f, nightBrightness, 0.001f,
                    "Brightness should still be dimmed before sunrise");
        
        // During sunrise
        dayCycle.setWorldTime(23500L);
        float sunriseBrightness = dayCycle.getSkyBrightness();
        assertTrue(sunriseBrightness > 0.3f && sunriseBrightness < 1.0f,
                  "Brightness should be transitioning during sunrise");
        
        // After sunrise (day)
        dayCycle.setWorldTime(0L); // Wraps around to new day
        float dayBrightness = dayCycle.getSkyBrightness();
        assertTrue(dayBrightness > 0.8f,
                  "Brightness should be increasing at start of new day");
    }
}
