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
    
    // Hummingbird sounds - reference vanilla SoundEvents
    public static final DeferredHolder HUMMINGBIRD_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.HUMMINGBIRD_IDLE);
    public static final DeferredHolder HUMMINGBIRD_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.HUMMINGBIRD_HURT);
    public static final DeferredHolder HUMMINGBIRD_LOOP = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.HUMMINGBIRD_LOOP);
    
    // Jerboa sounds - reference vanilla SoundEvents
    public static final DeferredHolder JERBOA_IDLE = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.JERBOA_IDLE);
    public static final DeferredHolder JERBOA_HURT = new DeferredHolder(() -> net.minecraft.sounds.SoundEvents.JERBOA_HURT);
}
