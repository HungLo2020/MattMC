package net.minecraft.world.level.levelgen;

import net.minecraft.core.RegistryAccess;

/** The vanilla worldgen registries of the noise-fill tests, for tests outside this package. */
public final class WorldgenTestRegistries {
    private WorldgenTestRegistries() {}

    public static RegistryAccess load() {
        NativeNoiseFillTest.load();
        return NativeNoiseFillTest.registries;
    }

    public static void close() {
        NativeNoiseFillTest.close();
    }
}
