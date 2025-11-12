package mattmc.world.level;

/**
 * Manages the day/night cycle calculations for Minecraft-like worlds.
 * 
 * In Minecraft:
 * - Full cycle = 24,000 ticks (20 real-world minutes at 20 TPS)
 * - Day (sunrise to sunset) = 0-12,000 ticks (~10 minutes)
 * - Sunset/Dusk = 12,000-13,000 ticks (~1.5 minutes)
 * - Night = 13,000-23,000 ticks (~7 minutes)
 * - Sunrise/Dawn = 23,000-0 ticks (~1.5 minutes)
 * 
 * Sun is at zenith (directly overhead) at tick 6,000 (noon).
 * Sun sets at tick 12,000 and rises at tick 0/24,000.
 */
public class DayCycle {
    /** Total ticks in one full day/night cycle */
    public static final long TICKS_PER_DAY = 24000L;
    
    /** Tick when sun is at zenith (noon) */
    public static final long NOON = 6000L;
    
    /** Tick when sunset begins */
    public static final long SUNSET = 12000L;
    
    /** Tick when it's fully night (midnight) */
    public static final long MIDNIGHT = 18000L;
    
    /** Tick when sunrise begins */
    public static final long SUNRISE = 23000L;
    
    private long worldTime = 0L;
    
    public DayCycle() {
        this(0L);
    }
    
    public DayCycle(long initialTime) {
        this.worldTime = initialTime;
    }
    
    /**
     * Advance time by one tick.
     */
    public void tick() {
        worldTime++;
    }
    
    /**
     * Get the current world time in ticks.
     */
    public long getWorldTime() {
        return worldTime;
    }
    
    /**
     * Set the world time.
     */
    public void setWorldTime(long time) {
        this.worldTime = time;
    }
    
    /**
     * Get the time of day (0-24000).
     */
    public long getTimeOfDay() {
        return worldTime % TICKS_PER_DAY;
    }
    
    /**
     * Get the celestial angle (0.0 to 1.0) representing the sun/moon position.
     * 0.0 = sunrise, 0.25 = noon, 0.5 = sunset, 0.75 = midnight, 1.0 = sunrise again
     */
    public float getCelestialAngle() {
        long timeOfDay = getTimeOfDay();
        return (float) timeOfDay / (float) TICKS_PER_DAY;
    }
    
    /**
     * Get the sun angle in radians for positioning the directional light.
     * Returns the angle of the sun in the sky (0 = horizon at sunrise, PI = horizon at sunset).
     */
    public float getSunAngle() {
        float celestialAngle = getCelestialAngle();
        // Offset so that 0.0 (sunrise) starts at horizon
        // Add 0.25 so that time 0 corresponds to the sun being at the eastern horizon
        float adjusted = (celestialAngle + 0.25f) % 1.0f;
        return (float) (adjusted * Math.PI * 2.0);
    }
    
    /**
     * Get the sky color based on time of day.
     * Returns RGB color as float array [r, g, b].
     */
    public float[] getSkyColor() {
        long timeOfDay = getTimeOfDay();
        
        // Day sky (light blue)
        if (timeOfDay >= 0 && timeOfDay < SUNSET) {
            return new float[] {0.53f, 0.81f, 0.92f};
        }
        // Sunset (orange/red gradient)
        else if (timeOfDay >= SUNSET && timeOfDay < 13000) {
            float t = (timeOfDay - SUNSET) / 1000f; // 0.0 to 1.0
            // Interpolate from day blue to sunset orange
            float r = 0.53f + (0.85f - 0.53f) * t;
            float g = 0.81f + (0.45f - 0.81f) * t;
            float b = 0.92f + (0.25f - 0.92f) * t;
            return new float[] {r, g, b};
        }
        // Night (dark blue)
        else if (timeOfDay >= 13000 && timeOfDay < SUNRISE) {
            return new float[] {0.05f, 0.05f, 0.15f};
        }
        // Sunrise (orange/red gradient back to day)
        else {
            float t = (timeOfDay - SUNRISE) / 1000f; // 0.0 to 1.0
            // Interpolate from night dark to day blue
            float r = 0.85f + (0.53f - 0.85f) * t;
            float g = 0.45f + (0.81f - 0.45f) * t;
            float b = 0.25f + (0.92f - 0.25f) * t;
            return new float[] {r, g, b};
        }
    }
    
    /**
     * Get the directional light direction vector for the sun.
     * Returns [x, y, z] normalized direction vector.
     * The sun moves from east to west, rising at time 0 and setting at time 12000.
     */
    public float[] getSunDirection() {
        float angle = getSunAngle();
        
        // Sun moves in the X-Z plane, with Y component for height
        // At sunrise (angle=0): sun is at eastern horizon
        // At noon (angle=PI/2): sun is directly overhead
        // At sunset (angle=PI): sun is at western horizon
        
        float x = (float) Math.sin(angle);
        float y = (float) Math.cos(angle);
        float z = 0.0f;
        
        // Normalize (should already be normalized, but just to be safe)
        float length = (float) Math.sqrt(x*x + y*y + z*z);
        return new float[] {x/length, y/length, z/length};
    }
    
    /**
     * Get the brightness multiplier based on time of day (0.0 to 1.0).
     * Used to dim lighting during night.
     */
    public float getSkyBrightness() {
        long timeOfDay = getTimeOfDay();
        
        // Full brightness during day
        if (timeOfDay >= 0 && timeOfDay < SUNSET) {
            return 1.0f;
        }
        // Fade to dim during sunset
        else if (timeOfDay >= SUNSET && timeOfDay < 13000) {
            float t = (timeOfDay - SUNSET) / 1000f;
            return 1.0f - (0.7f * t); // Dim to 30% brightness
        }
        // Dim during night (moon provides some light)
        else if (timeOfDay >= 13000 && timeOfDay < SUNRISE) {
            return 0.3f;
        }
        // Fade back to full during sunrise
        else {
            float t = (timeOfDay - SUNRISE) / 1000f;
            return 0.3f + (0.7f * t);
        }
    }
}
