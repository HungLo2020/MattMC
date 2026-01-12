package com.github.alexmodguy.alexscaves.client.particle;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

// Stub - using vanilla particles
public class ACParticleRegistry {
    public static class ParticleHolder {
        private final SimpleParticleType particle;
        public ParticleHolder(SimpleParticleType particle) { this.particle = particle; }
        public SimpleParticleType get() { return particle; }
    }
    
    public static final ParticleHolder TREMORZILLA_PROTON_BEAM = new ParticleHolder(ParticleTypes.END_ROD);
}
