package net.minecraft.world.entity.animal.subterranodon;

/**
 * Interface for mounts that have a stamina/power meter for special abilities.
 * Based on AlexsCaves RidingMeterMount interface.
 */
public interface RidingMeterMount {
    
    /**
     * Returns whether this mount has a riding meter.
     */
    default boolean hasRidingMeter() {
        return false;
    }
    
    /**
     * Gets the current meter amount (0.0 to 1.0).
     */
    float getMeterAmount();
    
    /**
     * Sets the current meter amount (0.0 to 1.0).
     */
    void setMeterAmount(float amount);
}
