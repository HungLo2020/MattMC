package net.minecraft.world.level.biome;

import java.util.Optional;

/** A real virtual color provider; exact-class admission must leave it in Java. */
public final class MutableBiomeEffectsFixture extends BiomeSpecialEffects {
    private int reads;
    public MutableBiomeEffectsFixture() {
        super(0, 0, 0, 0, Optional.empty(), Optional.empty(), Optional.empty(),
            GrassColorModifier.NONE, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), 1.0F);
    }
    @Override public int getFogColor() { return 0x123400 + reads++; }
    public int reads() { return reads; }
}
