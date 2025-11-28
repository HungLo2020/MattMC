package mattmc.world.level;

/**
 * Manages the day/night cycle calculations for MattMC-like worlds.
 * 
 * In MattMC:
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
    
    /** Tick when sunset transition begins */
    public static final long SUNSET_START = 11000L;
    
    /** Tick when it's fully night (midnight) */
    public static final long MIDNIGHT = 18000L;
    
    /** Tick when night phase starts */
    public static final long NIGHT_START = 13000L;
    
    /** Tick when sunrise begins */
    public static final long SUNRISE = 23000L;
    
    // Sky color constants (RGB components)
    private static final float[] DAY_SKY_COLOR = {0.53f, 0.81f, 0.92f};
    private static final float[] NIGHT_SKY_COLOR = {0.05f, 0.05f, 0.15f};
    private static final float[] SUNSET_SKY_COLOR = {0.85f, 0.45f, 0.25f};
    
    // Brightness constants
    private static final float FULL_BRIGHTNESS = 1.0f;
    private static final float NIGHT_BRIGHTNESS = 0.3f;
    
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
     * Returns the angle of the sun in the sky for a half-circle arc.
     * 0 = horizon at sunrise, PI/2 = overhead at noon, PI = horizon at sunset.
     */
    public float getSunAngle() {
        long timeOfDay = getTimeOfDay();
        
        // Sun is visible from time 0 to 12000 (half day)
        // Map this to 0 to PI radians for a half-circle arc
        if (timeOfDay <= SUNSET) {
            // During daytime: map 0-12000 to 0-PI
            return (float) (timeOfDay / (double) SUNSET * Math.PI);
        } else {
            // During nighttime: sun is below horizon, moon would be visible
            // For now, just continue the arc (sun goes around the world)
            return (float) ((timeOfDay - SUNSET) / (double) SUNSET * Math.PI) + (float) Math.PI;
        }
    }
    
    /**
     * Get the sky color based on time of day.
     * Returns RGB color as float array [r, g, b].
     */
    public float[] getSkyColor() {
        long timeOfDay = getTimeOfDay();
        
        // Day sky (light blue) - from sunrise through most of day
        if (timeOfDay >= 0 && timeOfDay < SUNSET_START) {
            return DAY_SKY_COLOR.clone();
        }
        // Sunset transition (orange/red gradient) - 11000 to 13000
        else if (timeOfDay >= SUNSET_START && timeOfDay < NIGHT_START) {
            float t = (timeOfDay - SUNSET_START) / 2000f; // 0.0 to 1.0
            // Interpolate from day blue to sunset orange
            return interpolateColor(DAY_SKY_COLOR, SUNSET_SKY_COLOR, t);
        }
        // Night (dark blue) - 13000 to 23000
        else if (timeOfDay >= NIGHT_START && timeOfDay < SUNRISE) {
            return NIGHT_SKY_COLOR.clone();
        }
        // Sunrise (orange/red gradient back to day) - 23000 to 24000
        else {
            float t = (timeOfDay - SUNRISE) / 1000f; // 0.0 to 1.0
            // Interpolate from sunset orange to day blue
            return interpolateColor(SUNSET_SKY_COLOR, DAY_SKY_COLOR, t);
        }
    }
    
    /**
     * Interpolate between two RGB colors.
     * @param color1 First color [r, g, b]
     * @param color2 Second color [r, g, b]
     * @param t Interpolation factor (0.0 to 1.0)
     * @return Interpolated color [r, g, b]
     */
    private float[] interpolateColor(float[] color1, float[] color2, float t) {
        return new float[] {
            color1[0] + (color2[0] - color1[0]) * t,
            color1[1] + (color2[1] - color1[1]) * t,
            color1[2] + (color2[2] - color1[2]) * t
        };
    }
    
    /**
     * Get the directional light direction vector for the sun.
     * Returns [x, y, z] normalized direction vector.
     * The sun moves from east to west, rising at time 0 and setting at time 12000.
     */
    public float[] getSunDirection() {
        float angle = getSunAngle();
        
        // Sun moves in an arc across the sky
        // At sunrise (angle=0): sun is at eastern horizon (y=0)
        // At noon (angle=PI/2): sun is directly overhead (y=1)  
        // At sunset (angle=PI): sun is at western horizon (y=0)
        
        // sin(angle) gives horizontal position (east to west)
        // sin(angle) for angle in [0, PI] gives values [0, 1, 0] which is what we want for height
        float x = (float) Math.cos(angle); // East-west position
        float y = (float) Math.sin(angle); // Height (0 at horizon, 1 at zenith)
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
        
        // Full brightness during day (0 to 11000)
        if (timeOfDay >= 0 && timeOfDay < SUNSET_START) {
            return FULL_BRIGHTNESS;
        }
        // Fade to dim during sunset (11000 to 13000)
        else if (timeOfDay >= SUNSET_START && timeOfDay < NIGHT_START) {
            float t = (timeOfDay - SUNSET_START) / 2000f; // 0.0 to 1.0
            return FULL_BRIGHTNESS - ((FULL_BRIGHTNESS - NIGHT_BRIGHTNESS) * t);
        }
        // Dim during night (moon provides some light) (13000 to 23000)
        else if (timeOfDay >= NIGHT_START && timeOfDay < SUNRISE) {
            return NIGHT_BRIGHTNESS;
        }
        // Fade back to full during sunrise (23000 to 24000)
        else {
            float t = (timeOfDay - SUNRISE) / 1000f; // 0.0 to 1.0
            return NIGHT_BRIGHTNESS + ((FULL_BRIGHTNESS - NIGHT_BRIGHTNESS) * t);
        }
    }
}
