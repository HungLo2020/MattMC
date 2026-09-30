package net.minecraft.world.level.levelgen.synth;

import java.lang.foreign.MemorySegment;

/**
 * Read-only bridge from synthesis to density programs. The identity remains
 * stable until the noise configuration changes. The segment owns its lifetime
 * through an automatic arena; callers must retain it for every native call.
 * This view allocates no additional snapshot or native state.
 */
public sealed interface NativeNoiseState permits NativeNoise {
    MemorySegment state();
    boolean critical();
    int octaveCount();
}
