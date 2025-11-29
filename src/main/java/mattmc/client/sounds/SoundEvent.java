package mattmc.client.sounds;

import java.util.Objects;

/**
 * Represents a registered sound event, mirroring Minecraft's SoundEvent.
 * <p>
 * A SoundEvent identifies a sound by its resource location (namespace:path).
 * Sounds can have either variable range (based on volume) or fixed range.
 * <p>
 * Variable range: range = 16.0 * max(volume, 1.0)
 * Fixed range: range = specified value, regardless of volume
 */
public final class SoundEvent {

    private static final float DEFAULT_RANGE = 16.0f;

    private final String location;
    private final float range;
    private final boolean fixedRange;

    private SoundEvent(String location, float range, boolean fixedRange) {
        this.location = Objects.requireNonNull(location, "Sound location cannot be null");
        this.range = range;
        this.fixedRange = fixedRange;
    }

    /**
     * Create a sound event with variable range (default 16 blocks, scales with volume).
     * @param location Resource location in format "namespace:path" or just "path" (uses default namespace)
     */
    public static SoundEvent createVariableRangeEvent(String location) {
        return new SoundEvent(location, DEFAULT_RANGE, false);
    }

    /**
     * Create a sound event with fixed range (does not scale with volume).
     * @param location Resource location in format "namespace:path" or just "path"
     * @param range Fixed audible range in blocks
     */
    public static SoundEvent createFixedRangeEvent(String location, float range) {
        return new SoundEvent(location, range, true);
    }

    /**
     * Get the resource location of this sound event.
     * @return The sound's resource location
     */
    public String getLocation() {
        return location;
    }

    /**
     * Calculate the audible range based on volume.
     * @param volume The sound's volume (0.0 - 1.0+)
     * @return The range in blocks at which this sound can be heard
     */
    public float getRange(float volume) {
        if (fixedRange) {
            return range;
        } else {
            // Variable range scales with volume above 1.0
            return volume > 1.0f ? DEFAULT_RANGE * volume : DEFAULT_RANGE;
        }
    }

    /**
     * Check if this sound has a fixed range.
     */
    public boolean hasFixedRange() {
        return fixedRange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SoundEvent that = (SoundEvent) o;
        return location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }

    @Override
    public String toString() {
        return "SoundEvent{location='" + location + "', range=" + range + ", fixedRange=" + fixedRange + "}";
    }
}
