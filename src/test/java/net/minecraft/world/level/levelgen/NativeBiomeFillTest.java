package net.minecraft.world.level.levelgen;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact parity of the Rust-owned BIOMES stage with Java's fill through the
 * production {@code doCreateBiomes}: every section's biome container (network
 * and saved forms, palette holders) and the thread's previous climate leaf,
 * over consecutive chunks that carry that leaf from one to the next. */
class NativeBiomeFillTest {
    @BeforeAll static void load() {
        NativeNoiseFillTest.load();
    }

    @AfterAll static void close() {
        NativeBiomeFill.setEnabled(true);
        NativeNoiseFillTest.close();
    }

    static List<Holder<Biome>> biomes() {
        return NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME).listElements()
            .sorted(java.util.Comparator.comparing(holder -> holder.key().location().toString())).map(holder -> (Holder<Biome>)holder).toList();
    }

    static MultiNoiseBiomeSource preset(net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList> key) {
        return MultiNoiseBiomeSource.createFromPreset(NativeNoiseFillTest.registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(key));
    }

    /** A chunk's BIOMES stage through {@code doCreateBiomes}, after the noise
     * chunk the stage would create (no structures, no blending). */
    static ProtoChunk biomes(Holder<NoiseGeneratorSettings> setting, RandomState random, BiomeSource source, ChunkPos pos, boolean nativeFill) {
        var config = setting.value();
        var noise = config.noiseSettings();
        var generator = new NoiseBasedChunkGenerator(source, setting);
        var chunk = new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(noise.minY(), noise.height()),
            PalettedContainerFactory.create(NativeNoiseFillTest.registries), null);
        chunk.getOrCreateNoiseChunk(access -> NoiseChunk.forChunk(access, random, Beardifier.EMPTY, config,
            new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty()));
        NativeBiomeFill.setEnabled(nativeFill);
        try {
            generator.doCreateBiomes(Blender.empty(), random, null, chunk);
        } finally {
            NativeBiomeFill.setEnabled(true);
        }
        return chunk;
    }

    static byte[] network(LevelChunkSection section) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        section.getBiomes().write(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    static String saved(LevelChunkSection section) {
        var ids = section.generatedBiomeIds();
        var packed = section.getBiomes().pack(net.minecraft.world.level.chunk.Strategy.createForBiomes(ids));
        return packed.paletteEntries() + " " + packed.storage().map(stream -> java.util.Arrays.toString(stream.toArray())).orElse("-") + " "
            + packed.bitsPerEntry();
    }

    static long entries;

    static void compare(ProtoChunk java, ProtoChunk candidate, String context) {
        for (int index = 0; index < java.getSectionsCount(); index++) {
            LevelChunkSection left = java.getSection(index), right = candidate.getSection(index);
            int section = index;
            assertArrayEquals(network(left), network(right), () -> context + " section " + section + " biome bytes");
            assertEquals(saved(left), saved(right), () -> context + " section " + section + " saved biomes");
            for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
                assertSame(left.getNoiseBiome(x, y, z), right.getNoiseBiome(x, y, z), () -> context + " section " + section + " holder");
                entries++;
            }
        }
    }

    /** Runs {@code positions} in order with each route from the same previous
     * leaf, comparing chunks and the previous leaf after each. */
    static int run(Holder<NoiseGeneratorSettings> setting, long seed, MultiNoiseBiomeSource source, ChunkPos[] positions, String context) {
        var list = source.nativeParameters();
        var noises = NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE);
        var javaRandom = RandomState.create(setting.value(), noises, seed);
        var nativeRandom = RandomState.create(setting.value(), noises, seed);
        int start = list.previousNativeLeaf();
        List<ProtoChunk> javaChunks = new ArrayList<>();
        List<Integer> javaLeaves = new ArrayList<>();
        for (ChunkPos pos : positions) {
            javaChunks.add(biomes(setting, javaRandom, source, pos, false));
            javaLeaves.add(list.previousNativeLeaf());
        }
        list.setPreviousNativeLeaf(start);
        long before = NativeBiomeFill.CHUNKS.get();
        for (int index = 0; index < positions.length; index++) {
            var candidate = biomes(setting, nativeRandom, source, positions[index], true);
            String at = context + " " + positions[index];
            compare(javaChunks.get(index), candidate, at);
            assertEquals(javaLeaves.get(index), list.previousNativeLeaf(), () -> at + " previous leaf");
        }
        assertEquals(positions.length, NativeBiomeFill.CHUNKS.get() - before, () -> context + " must fill natively");
        return positions.length;
    }

    @Test void nativeFillMatchesJavaForEverySettingAndPreset() {
        ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321), new ChunkPos(131_000, -131_000)};
        int chunks = 0;
        for (var preset : List.of(MultiNoiseBiomeSourceParameterLists.OVERWORLD, MultiNoiseBiomeSourceParameterLists.NETHER)) {
            var source = preset(preset);
            for (var setting : NativeNoiseFillTest.settings) {
                for (long seed : new long[]{0, -7_340_013_412_337L}) {
                    chunks += run(setting, seed, source, positions, setting.key().location() + " " + preset.location() + " seed " + seed);
                }
            }
        }
        System.out.println("BIOME_FILL_PARITY chunks=" + chunks + " entries=" + entries);
    }

    /** A list whose leaves are one biome per continentalness value the chunk's
     * columns sample, so sections hold more biomes than a linear palette. */
    static MultiNoiseBiomeSource fine(Holder<NoiseGeneratorSettings> setting, long seed, ChunkPos pos) {
        var random = RandomState.create(setting.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), seed);
        var config = setting.value();
        var noise = NoiseChunk.forChunk(new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(config.noiseSettings().minY(),
                config.noiseSettings().height()), PalettedContainerFactory.create(NativeNoiseFillTest.registries), null), random, Beardifier.EMPTY, config,
            new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty());
        var sampler = noise.cachedClimateSampler(random.router(), config.spawnTarget());
        var values = new TreeSet<Long>();
        int qx = pos.getMinBlockX() >> 2, qz = pos.getMinBlockZ() >> 2;
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) values.add(sampler.sample(qx + x, 0, qz + z).continentalness());
        var biomes = biomes();
        var wide = Climate.Parameter.span(-10, 10);
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> points = new ArrayList<>();
        int index = 0;
        for (long value : values) {
            var c = Climate.Parameter.point(Climate.unquantizeCoord(value));
            points.add(Pair.of(new Climate.ParameterPoint(wide, wide, c, wide, wide, wide, 0), biomes.get(index++ % biomes.size())));
        }
        return MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(points));
    }

    @Test void globalPalettesAndSingleLeafTreesMatchJava() {
        var overworld = NativeNoiseFillTest.settings.stream().filter(h -> h.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        ChunkPos[] positions = {new ChunkPos(3, 9), new ChunkPos(-200, 77)};
        boolean global = false;
        for (ChunkPos pos : positions) {
            var source = fine(overworld, 42, pos);
            run(overworld, 42, source, new ChunkPos[]{pos}, "fine " + pos);
            var chunk = biomes(overworld, RandomState.create(overworld.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), 42),
                source, pos, true);
            // Network form: storage bits first; the global palette uses at least 4.
            global |= network(chunk.getSection(0))[0] >= 4;
        }
        assertTrue(global, "a section must use the global biome palette");
        var single = MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(List.of(Pair.of(
            Climate.parameters(0, 0, 0, 0, 0, 0, 0), biomes().get(5)))));
        run(overworld, 7, single, positions, "single leaf");
    }

    /** FlatCache tables hold the input at Y 0: a temperature whose FlatCache
     * input depends on Y must not vary with the sampled quart's Y. */
    @Test void yDependentFlatCacheKeepsChunkSemantics() {
        var overworld = NativeNoiseFillTest.settings.stream().filter(h -> h.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var c = overworld.value();
        var r = c.noiseRouter();
        var temperature = DensityFunctions.add(r.temperature(), DensityFunctions.flatCache(DensityFunctions.yClampedGradient(-64, 320, -1, 1)));
        var router = new NoiseRouter(r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(), temperature,
            r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(), r.preliminarySurfaceLevel(), r.finalDensity(), r.veinToggle(),
            r.veinRidged(), r.veinGap());
        var setting = Holder.direct(new NoiseGeneratorSettings(c.noiseSettings(), c.defaultBlock(), c.defaultFluid(), router, c.surfaceRule(),
            c.spawnTarget(), c.seaLevel(), c.disableMobGeneration(), c.aquifersEnabled(), c.oreVeinsEnabled(), c.useLegacyRandomSource()));
        run(setting, 11, preset(MultiNoiseBiomeSourceParameterLists.OVERWORLD), new ChunkPos[]{new ChunkPos(2, 2), new ChunkPos(-30, 8)}, "y-dependent flat");
    }

    @Test void gatesKeepJavaFill() {
        var overworld = NativeNoiseFillTest.settings.stream().filter(h -> h.is(NoiseGeneratorSettings.OVERWORLD)).findFirst().orElseThrow();
        var random = RandomState.create(overworld.value(), NativeNoiseFillTest.registries.lookupOrThrow(Registries.NOISE), 1);
        long before = NativeBiomeFill.CHUNKS.get();
        biomes(overworld, random, new FixedBiomeSource(biomes().get(0)), new ChunkPos(0, 0), true);
        var subclass = new Climate.ParameterList<>(List.of(Pair.of(Climate.parameters(0, 0, 0, 0, 0, 0, 0), biomes().get(1)),
            Pair.of(Climate.parameters(1, 0, 0, 0, 0, 0, 0), biomes().get(2)))) {};
        biomes(overworld, random, MultiNoiseBiomeSource.createFromList(subclass), new ChunkPos(0, 0), true);
        assertEquals(before, NativeBiomeFill.CHUNKS.get(), "fixed sources and parameter list subclasses keep Java's fill");
        biomes(overworld, random, preset(MultiNoiseBiomeSourceParameterLists.OVERWORLD), new ChunkPos(0, 0), true);
        assertEquals(before + 1, NativeBiomeFill.CHUNKS.get());
        NativeBiomeFill.setEnabled(false);
        try {
            assertFalse(NativeBiomeFill.fill(new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384),
                PalettedContainerFactory.create(NativeNoiseFillTest.registries), null), preset(MultiNoiseBiomeSourceParameterLists.OVERWORLD),
                NoiseChunk.forChunk(new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384),
                    PalettedContainerFactory.create(NativeNoiseFillTest.registries), null), random, Beardifier.EMPTY, overworld.value(),
                    new AquiferFluidPicker(63, overworld.value().defaultFluid()), Blender.empty()), random), "disabled");
        } finally {
            NativeBiomeFill.setEnabled(true);
        }
    }
}
