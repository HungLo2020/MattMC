package mattmc.world.level;

/**
 * Manages the day/night cycle calculations for MattMC-like worlds.
 * Closely follows Minecraft's implementation.
 * 
 * In Minecraft:
 * - Full cycle = 24,000 ticks (20 real-world minutes at 20 TPS)
 * - Day starts at tick 0 (sunrise)
 * - Noon is at tick 6,000 (sun at zenith)
 * - Sunset begins around tick 12,000
 * - Night is from about 13,000 to 23,000
 * - New day begins at tick 24,000 (wraps to 0)
 * 
 * The timeOfDay value (0.0 to 1.0) represents:
 * - 0.0 = sunrise
 * - 0.25 = noon (sun at zenith)
 * - 0.5 = sunset
 * - 0.75 = midnight
 * - 1.0 = sunrise again
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
    
    /** Moon brightness values for each of the 8 moon phases (from Minecraft) */
    public static final float[] MOON_BRIGHTNESS_PER_PHASE = {1.0F, 0.75F, 0.5F, 0.25F, 0.0F, 0.25F, 0.5F, 0.75F};
    
    // Minecraft sky base color (light blue) - this is the biome sky color
    // The actual sky color is this multiplied by the brightness factor
    private static final float[] BASE_SKY_COLOR = {0.529f, 0.808f, 0.922f}; // Light blue (#87CEEB)
    
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
     * This follows Minecraft's exact calculation from DimensionType.timeOfDay().
     * 
     * Result: 0.0 = sunrise, 0.25 = noon, 0.5 = sunset, 0.75 = midnight, 1.0 = sunrise
     */
    public float getCelestialAngle() {
        return getTimeOfDayFloat();
    }
    
    /**
     * Get the time of day as a float (0.0 to 1.0).
     * This matches Minecraft's DimensionType.timeOfDay() calculation.
     * 
     * @return Time of day from 0.0 (sunrise) to 1.0 (next sunrise)
     */
    public float getTimeOfDayFloat() {
        // Minecraft's formula: frac(dayTime/24000 - 0.25) with cos adjustment
        // This shifts the cycle so 0.0 = sunrise, 0.25 = noon, 0.5 = sunset
        double d0 = frac((double) worldTime / 24000.0 - 0.25);
        double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
        return (float) ((d0 * 2.0 + d1) / 3.0);
    }
    
    /**
     * Helper method to get fractional part of a double (like Minecraft's Mth.frac).
     */
    private static double frac(double value) {
        return value - Math.floor(value);
    }
    
    /**
     * Get the sun angle in radians for positioning the sun/moon in the sky.
     * This is getTimeOfDayFloat() * 2 * PI, matching Minecraft's Level.getSunAngle().
     * 
     * @return Sun angle in radians (0 to 2*PI)
     */
    public float getSunAngleRadians() {
        return getTimeOfDayFloat() * ((float) Math.PI * 2F);
    }
    
    /**
     * Get the sky color based on time of day, matching Minecraft's calculation.
     * Returns RGB color as float array [r, g, b].
     * 
     * The formula multiplies the base sky color by a brightness factor
     * that varies with the celestial angle.
     */
    public float[] getSkyColor() {
        float timeOfDay = getTimeOfDayFloat();
        
        // Minecraft's brightness calculation:
        // f1 = cos(timeOfDay * 2PI) * 2 + 0.5, clamped to [0, 1]
        float brightness = (float) Math.cos(timeOfDay * Math.PI * 2.0) * 2.0F + 0.5F;
        brightness = clamp(brightness, 0.0f, 1.0f);
        
        // Apply brightness to base sky color
        return new float[] {
            BASE_SKY_COLOR[0] * brightness,
            BASE_SKY_COLOR[1] * brightness,
            BASE_SKY_COLOR[2] * brightness
        };
    }
    
    /**
     * Get the sunrise/sunset color effect (orange/red glow on the horizon).
     * Returns null when there's no sunrise/sunset effect.
     * Returns [r, g, b, alpha] when there is a sunrise/sunset effect.
     * 
     * This matches Minecraft's DimensionSpecialEffects.getSunriseColor().
     */
    public float[] getSunriseColor() {
        float timeOfDay = getTimeOfDayFloat();
        float f1 = (float) Math.cos(timeOfDay * ((float) Math.PI * 2F));
        
        // Sunrise/sunset only visible when cos value is between -0.4 and 0.4
        // (around sunrise at 0.0 and sunset at 0.5)
        if (f1 >= -0.4F && f1 <= 0.4F) {
            float f3 = (f1 + 0.0F) / 0.4F * 0.5F + 0.5F;
            float f4 = 1.0F - (1.0F - (float) Math.sin(f3 * Math.PI)) * 0.99F;
            f4 *= f4;
            
            return new float[] {
                f3 * 0.3F + 0.7F,  // Red: strong
                f3 * f3 * 0.7F + 0.2F,  // Green: moderate
                f3 * f3 * 0.0F + 0.2F,  // Blue: weak
                f4  // Alpha
            };
        }
        return null;
    }
    
    /**
     * Get the star brightness (0.0 to 0.5) for rendering stars at night.
     * Stars become visible as it gets dark and fade out at sunrise.
     * 
     * This matches Minecraft's ClientLevel.getStarBrightness().
     */
    public float getStarBrightness() {
        float timeOfDay = getTimeOfDayFloat();
        float f1 = 1.0F - ((float) Math.cos(timeOfDay * ((float) Math.PI * 2F)) * 2.0F + 0.25F);
        f1 = clamp(f1, 0.0F, 1.0F);
        return f1 * f1 * 0.5F;
    }
    
    /**
     * Get the current moon phase (0-7).
     * The moon cycles through 8 phases over 8 in-game days.
     * 
     * This matches Minecraft's DimensionType.moonPhase().
     */
    public int getMoonPhase() {
        return (int) (worldTime / 24000L % 8L + 8L) % 8;
    }
    
    /**
     * Get the moon brightness based on current moon phase.
     * Full moon = 1.0, new moon = 0.0.
     */
    public float getMoonBrightness() {
        return MOON_BRIGHTNESS_PER_PHASE[getMoonPhase()];
    }
    
    /**
     * Helper method to clamp a value between min and max.
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Get the directional light direction vector for the sun.
     * Returns [x, y, z] normalized direction vector.
     * 
     * Note: This uses the raw time-based angle calculation for lighting purposes,
     * not the adjusted celestial angle used for rendering. This ensures:
     * - At dawn (time 0): sun is on the eastern horizon
     * - At noon (time 6000): sun is directly overhead
     * - At dusk (time 12000): sun is on the western horizon
     * - At night (time 13000+): sun direction is below horizon (negative Y)
     */
    public float[] getSunDirection() {
        long timeOfDay = getTimeOfDay();
        
        // Map time to angle: 0 at sunrise, PI/2 at noon, PI at sunset, 3PI/2 at midnight
        // Full rotation over 24000 ticks
        float angle = (float) (timeOfDay / (double) TICKS_PER_DAY * Math.PI * 2.0);
        
        // Sun moves in an arc across the sky
        // Using sin for Y (height) so sun is at horizon at 0 and PI
        // Using cos for X (east-west position)
        float x = (float) Math.cos(angle);
        float y = (float) Math.sin(angle);
        float z = 0.0f;
        
        // Normalize (should already be normalized, but just to be safe)
        float length = (float) Math.sqrt(x*x + y*y + z*z);
        if (length < 0.0001f) length = 1.0f;
        return new float[] {x/length, y/length, z/length};
    }
    
    /**
     * Get the brightness multiplier based on time of day (0.0 to 1.0).
     * Used to dim lighting during night.
     * 
     * This matches Minecraft's ClientLevel.getSkyDarken() formula.
     */
    public float getSkyBrightness() {
        float timeOfDay = getTimeOfDayFloat();
        float f1 = 1.0F - ((float) Math.cos(timeOfDay * ((float) Math.PI * 2F)) * 2.0F + 0.2F);
        f1 = clamp(f1, 0.0F, 1.0F);
        f1 = 1.0F - f1;
        return f1 * 0.8F + 0.2F;
    }
}
