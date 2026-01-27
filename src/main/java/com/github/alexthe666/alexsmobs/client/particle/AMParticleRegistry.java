package com.github.alexthe666.alexsmobs.client.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Stub registry class for AlexsMobs particles
 */
public class AMParticleRegistry {
    
    /**
     * Deferred holder stub that returns the actual vanilla-registered particle
     */
    public static class DeferredHolder {
        private final java.util.function.Supplier<SimpleParticleType> particleSupplier;
        
        public DeferredHolder(java.util.function.Supplier<SimpleParticleType> particleSupplier) {
            this.particleSupplier = particleSupplier;
        }
        
        public SimpleParticleType get() {
            return particleSupplier.get();
        }
    }
    
    // Use vanilla particles as fallback
    public static final DeferredHolder SHOCKED = new DeferredHolder(() -> ParticleTypes.ELECTRIC_SPARK);
    public static final DeferredHolder SMELLY = new DeferredHolder(() -> ParticleTypes.SNEEZE); // Use sneeze particle as fallback for smelly
    
    // Sunbird particles - reference vanilla ParticleTypes
    public static final DeferredHolder SUNBIRD_FEATHER = new DeferredHolder(() -> ParticleTypes.SUNBIRD_FEATHER);
}
