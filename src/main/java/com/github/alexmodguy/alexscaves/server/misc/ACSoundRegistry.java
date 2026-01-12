package com.github.alexmodguy.alexscaves.server.misc;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

// Stub - using vanilla sounds
public class ACSoundRegistry {
    public static class SoundHolder {
        private final Holder<SoundEvent> sound;
        public SoundHolder(Holder<SoundEvent> sound) { this.sound = sound; }
        public Holder<SoundEvent> get() { return sound; }
    }
    
    public static final SoundHolder SUBTERRANODON_HURT = new SoundHolder(SoundEvents.PARROT_HURT);
    public static final SoundHolder SUBTERRANODON_DEATH = new SoundHolder(SoundEvents.PARROT_DEATH);
    public static final SoundHolder SUBTERRANODON_IDLE = new SoundHolder(SoundEvents.PARROT_AMBIENT);
    public static final SoundHolder SUBTERRANODON_FLAP = new SoundHolder(SoundEvents.PARROT_FLY);
    public static final SoundHolder TECTONIC_SHARD_TRANSFORM = new SoundHolder(SoundEvents.PORTAL_TRIGGER);
    public static final SoundHolder AMBER_MONOLITH_SUMMON = new SoundHolder(SoundEvents.ENDER_DRAGON_GROWL);
}
