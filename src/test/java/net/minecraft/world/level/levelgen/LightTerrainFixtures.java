package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;

/** NOISE-filled terrain for lighting tests outside this package. */
public final class LightTerrainFixtures {
    private LightTerrainFixtures() {}

    public static void load() {
        NativeNoiseFillTest.load();
    }

    public static void close() {
        NativeNoiseFillTest.close();
    }

    /** Chunks over [x0, x0 + size) × [z0, z0 + size), row by row. */
    public static List<ProtoChunk> grid(ResourceKey<NoiseGeneratorSettings> key, long seed, int x0, int z0, int size) {
        var setting = NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
        var random = RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), seed);
        var chunks = new ArrayList<ProtoChunk>();
        for (int z = z0; z < z0 + size; z++) {
            for (int x = x0; x < x0 + size; x++) chunks.add(NativeNoiseFillTest.fill(setting, random, new ChunkPos(x, z), true).chunk());
        }
        return chunks;
    }
}
