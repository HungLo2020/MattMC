package com.github.alexthe666.alexsmobs.misc;

import net.minecraft.sounds.SoundEvent;

/**
 * Stub registry class for AlexsMobs sounds
 * Points to actual vanilla-registered sounds in SoundEvents.java
 */
public class AMSoundRegistry {
    
    /**
     * Deferred holder stub that returns the actual vanilla-registered sound
     */
    public static class DeferredHolder {
        private final java.util.function.Supplier<SoundEvent> soundSupplier;
        
        public DeferredHolder(java.util.function.Supplier<SoundEvent> soundSupplier) {
            this.soundSupplier = soundSupplier;
        }
        
        public SoundEvent get() {
            return soundSupplier.get();
        }
    }
    
    // Comb Jelly sounds - reference vanilla SoundEvents
    public static final DeferredHolder COMB_JELLY_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.COMB_JELLY_HURT);
    
    // Crow sounds - reference vanilla SoundEvents
    public static final DeferredHolder CROW_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.CROW_IDLE);
    public static final DeferredHolder CROW_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.CROW_HURT);
    
    // Endergrade sounds - reference vanilla SoundEvents
    public static final DeferredHolder ENDERGRADE_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ENDERGRADE_IDLE);
    public static final DeferredHolder ENDERGRADE_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.ENDERGRADE_HURT);
}
