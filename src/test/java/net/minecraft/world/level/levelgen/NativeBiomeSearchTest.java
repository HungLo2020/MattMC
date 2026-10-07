package net.minecraft.world.level.levelgen;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSearchAccess;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeBiomeSearchTest {
    @BeforeAll static void load() {
        NativeNoiseFillTest.load();
    }

    @AfterAll static void close() {
        NativeBiomeSearch.setEnabled(true);
        NativeNoiseFillTest.close();
    }

    record World(String name, Holder<NoiseGeneratorSettings> settings, MultiNoiseBiomeSource source, RandomState random) {}

    static World world(ResourceKey<NoiseGeneratorSettings> settings, ResourceKey<MultiNoiseBiomeSourceParameterList> preset, long seed) {
        var registries = NativeNoiseFillTest.registries;
        var setting = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settings);
        var source = MultiNoiseBiomeSource.createFromPreset(registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(preset));
        return new World(settings.location().getPath(), setting, source, RandomState.create(setting.value(), registries.lookupOrThrow(Registries.NOISE), seed));
    }

    static List<World> worlds() {
        var worlds = new ArrayList<World>();
        for (long seed : new long[]{7_340_013L, -42L, 1L}) {
            worlds.add(world(NoiseGeneratorSettings.OVERWORLD, MultiNoiseBiomeSourceParameterLists.OVERWORLD, seed));
            worlds.add(world(NoiseGeneratorSettings.NETHER, MultiNoiseBiomeSourceParameterLists.NETHER, seed));
        }
        worlds.add(world(NoiseGeneratorSettings.LARGE_BIOMES, MultiNoiseBiomeSourceParameterLists.OVERWORLD, 5L));
        // Small custom lists: a single leaf (Java's constant shortcut) and a few points.
        var biomes = NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME);
        var overworld = worlds.getFirst();
        var single = new net.minecraft.world.level.biome.Climate.ParameterList<Holder<Biome>>(List.of(
            Pair.of(net.minecraft.world.level.biome.Climate.parameters(0, 0, 0, 0, 0, 0, 0), biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS))));
        var few = new net.minecraft.world.level.biome.Climate.ParameterList<Holder<Biome>>(List.of(
            Pair.of(net.minecraft.world.level.biome.Climate.parameters(-0.5F, 0, 0, 0, 0, 0, 0), biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS)),
            Pair.of(net.minecraft.world.level.biome.Climate.parameters(0.5F, 0, 0, 0, 0, 0, 0), biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.DESERT)),
            Pair.of(net.minecraft.world.level.biome.Climate.parameters(0, 0.5F, 0, 0, 0, 0, 0), biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.JUNGLE))));
        worlds.add(new World("single", overworld.settings(), MultiNoiseBiomeSource.createFromList(single), overworld.random()));
        worlds.add(new World("few", overworld.settings(), MultiNoiseBiomeSource.createFromList(few), overworld.random()));
        return worlds;
    }

    /** A result plus everything a search leaves behind: random state and the thread's previous leaf. */
    record Outcome(Pair<BlockPos, Holder<Biome>> result, long random, int previous) {}

    static Outcome run(World world, boolean rust, int previous, long seed, java.util.function.Function<RandomSource, Pair<BlockPos, Holder<Biome>>> search) {
        var list = world.source().nativeParameters();
        list.setPreviousNativeLeaf(previous);
        NativeBiomeSearch.setEnabled(rust);
        try {
            long before = NativeBiomeSearch.SEARCHES.get();
            RandomSource random = RandomSource.create(seed);
            var result = search.apply(random);
            assertEquals(rust ? before + 1 : before, NativeBiomeSearch.SEARCHES.get(), "route");
            return new Outcome(result, random.nextLong(), list.previousNativeLeaf());
        } finally {
            NativeBiomeSearch.setEnabled(true);
        }
    }

    static int randomLeaf(World world, Random random) {
        var list = world.source().nativeParameters();
        if (random.nextInt(4) == 0) return -1;
        while (true) {
            int node = random.nextInt(list.nativeNodeCount());
            if (list.nativeLeafValue(node) != null) return node;
        }
    }

    static List<Holder<Biome>> biomeSet(World world, Random random) {
        var possible = new ArrayList<>(world.source().possibleBiomes());
        possible.sort(java.util.Comparator.comparing(h -> h.unwrapKey().orElseThrow().location().toString()));
        var set = new ArrayList<Holder<Biome>>();
        int size = new int[]{0, 1, 2, 4, 9, possible.size()}[random.nextInt(6)];
        for (int i = 0; i < size; i++) set.add(possible.get(random.nextInt(possible.size())));
        return set;
    }

    @Test void horizontalSearchesMatchJava() {
        var random = new Random(31);
        int searches = 0, found = 0;
        for (World world : worlds()) {
            for (int round = 0; round < 24; round++) {
                int x = random.nextInt(40_000) - 20_000, z = random.nextInt(40_000) - 20_000, y = random.nextInt(256) - 64;
                int radius = new int[]{0, 3, 16, 64, 112}[random.nextInt(5)];
                var biomes = HolderSet.direct(biomeSet(world, random));
                int previous = randomLeaf(world, random);
                long seed = random.nextLong();
                Supplier<java.util.function.Function<RandomSource, Pair<BlockPos, Holder<Biome>>>> search = () ->
                    r -> world.source().findBiomeHorizontal(x, y, z, radius, biomes, r, world.random().sampler());
                Outcome java = run(world, false, previous, seed, search.get());
                Outcome rust = run(world, true, previous, seed, search.get());
                assertEquals(java, rust, world.name() + " " + x + "," + y + "," + z + " r=" + radius);
                // The holder-set overload is the original predicate search.
                world.source().nativeParameters().setPreviousNativeLeaf(previous);
                NativeBiomeSearch.setEnabled(false);
                var original = world.source().findBiomeHorizontal(x, y, z, radius, biomes::contains, RandomSource.create(seed), world.random().sampler());
                NativeBiomeSearch.setEnabled(true);
                assertEquals(java.result(), original);
                searches++;
                if (rust.result() != null) found++;
            }
        }
        System.out.println("BIOME_SEARCH_PARITY horizontal=" + searches + " found=" + found);
    }

    @Test void closestSearchesMatchJava() {
        var random = new Random(77);
        int searches = 0, found = 0;
        for (World world : worlds()) {
            int minY = world.settings().value().noiseSettings().minY(), maxY = minY + world.settings().value().noiseSettings().height() - 1;
            for (int round = 0; round < 12; round++) {
                int x = random.nextInt(40_000) - 20_000, z = random.nextInt(40_000) - 20_000, y = random.nextInt(maxY - minY) + minY;
                int step = new int[]{8, 32, 64}[random.nextInt(3)];
                int radius = new int[]{0, 64, 512, 2048}[random.nextInt(4)];
                int verticalStep = new int[]{8, 64}[random.nextInt(2)];
                var accepted = new HashSet<>(biomeSet(world, random));
                if (accepted.isEmpty()) accepted.add(world.source().possibleBiomes().iterator().next());
                int[] ys = Mth.outFromOrigin(y, minY + 1, maxY + 1, verticalStep).toArray();
                int previous = randomLeaf(world, random);
                var center = new BlockPos(x, y, z);
                Outcome java = run(world, false, previous, 0, r -> BiomeSearchAccess.closest(world.source(), center, radius, step, accepted, ys,
                    world.random().sampler()));
                Outcome rust = run(world, true, previous, 0, r -> BiomeSearchAccess.closest(world.source(), center, radius, step, accepted, ys,
                    world.random().sampler()));
                assertEquals(java, rust, world.name() + " " + center + " r=" + radius + " step=" + step);
                searches++;
                if (rust.result() != null) found++;
            }
        }
        System.out.println("BIOME_SEARCH_PARITY closest=" + searches + " found=" + found);
    }

    @Test void strongholdRingsMatchJava() {
        var registries = NativeNoiseFillTest.registries;
        int rings = 0;
        for (long seed : new long[]{7_340_013L, -42L}) {
            World world = world(NoiseGeneratorSettings.OVERWORLD, MultiNoiseBiomeSourceParameterLists.OVERWORLD, seed);
            List<List<net.minecraft.world.level.ChunkPos>> results = new ArrayList<>();
            for (boolean rust : new boolean[]{false, true}) {
                NativeBiomeSearch.setEnabled(rust);
                long before = NativeBiomeSearch.SEARCHES.get();
                var state = ChunkGeneratorStructureState.createForNormal(world.random(), seed, world.source(), registries.lookupOrThrow(Registries.STRUCTURE_SET));
                var positions = new ArrayList<net.minecraft.world.level.ChunkPos>();
                for (var set : state.possibleStructureSets()) {
                    if (set.value().placement() instanceof ConcentricRingsStructurePlacement rings0) {
                        positions.addAll(Objects.requireNonNull(state.getRingPositionsFor(rings0)));
                    }
                }
                NativeBiomeSearch.setEnabled(true);
                assertEquals(rust, NativeBiomeSearch.SEARCHES.get() > before, "route");
                results.add(positions);
            }
            assertEquals(results.get(0), results.get(1));
            rings += results.get(1).size();
        }
        System.out.println("BIOME_SEARCH_PARITY stronghold_positions=" + rings);
    }
}
