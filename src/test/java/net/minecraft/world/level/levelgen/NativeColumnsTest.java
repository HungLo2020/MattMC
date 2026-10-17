package net.minecraft.world.level.levelgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** {@code getBaseHeight} through Rust's column source against Java's one-cell
 * noise chunk, for every vanilla noise setting, both world-generation
 * heightmaps and the predicates structures use, over scattered columns
 * (several per chunk, so cached chunk routers and aquifers are reused). */
class NativeColumnsTest {
    @BeforeAll static void load() {
        NativeNoiseRouterTest.load();
    }

    @AfterAll static void close() {
        NativeColumns.setEnabled(true);
        NativeNoiseRouterTest.close();
    }

    @Test void baseHeightsMatchJavaForEverySetting() {
        var plains = NativeNoiseRouterTest.registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var random = new java.util.Random(7);
        Heightmap.Types[] types = {Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.MOTION_BLOCKING};
        for (var setting : NativeNoiseRouterTest.settings) {
            var config = setting.value();
            var state = RandomState.create(config, NativeNoiseRouterTest.noises, 42L);
            var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), setting);
            var noise = config.noiseSettings();
            var height = LevelHeightAccessor.create(noise.minY(), noise.height());
            long before = NativeColumns.HEIGHTS.get();
            int columns = 0;
            for (int chunk = 0; chunk < 24; chunk++) {
                int baseX = (random.nextInt(4000) - 2000) * 16, baseZ = (random.nextInt(4000) - 2000) * 16;
                for (int n = 0; n < 6; n++) {
                    int x = baseX + random.nextInt(16), z = baseZ + random.nextInt(16);
                    for (var type : types) {
                        NativeColumns.setEnabled(false);
                        int expected = generator.getBaseHeight(x, z, type, height, state);
                        NativeColumns.setEnabled(true);
                        int actual = generator.getBaseHeight(x, z, type, height, state);
                        String context = setting.key().location() + " " + type + " at " + x + ", " + z;
                        assertEquals(expected, actual, context);
                        columns++;
                    }
                }
            }
            long engaged = NativeColumns.HEIGHTS.get() - before;
            System.out.println("NativeColumnsTest " + setting.key().location() + ": " + columns + " columns, " + engaged + " native");
        }
    }
}
