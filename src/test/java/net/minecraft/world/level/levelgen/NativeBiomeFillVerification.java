package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.PlayerChunkDistancesVerification;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Opt-in benchmark of the BIOMES stage: the noise chunk the stage creates
 * (no structures, no blending) and {@code doCreateBiomes}, with Java's fill
 * ({@code java}) or the Rust-owned fill ({@code native}). Each sample fills
 * eight fresh chunks from a cleared previous climate leaf. */
public final class NativeBiomeFillVerification {
    record Case(String name, ResourceKey<NoiseGeneratorSettings> settings, ResourceKey<MultiNoiseBiomeSourceParameterList> biomes) {}

    /** After Bootstrap: the keys' classes need registries. */
    static List<Case> cases() {
        return List.of(
            new Case("overworld", NoiseGeneratorSettings.OVERWORLD, MultiNoiseBiomeSourceParameterLists.OVERWORLD),
            new Case("amplified", NoiseGeneratorSettings.AMPLIFIED, MultiNoiseBiomeSourceParameterLists.OVERWORLD),
            new Case("nether", NoiseGeneratorSettings.NETHER, MultiNoiseBiomeSourceParameterLists.NETHER));
    }

    /** {@code main(mode, case[, quick])}. */
    public static void main(String[] args) throws Exception {
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        if (!mode.equals("native") && !mode.equals("java")) throw new IllegalArgumentException(mode);
        NativeNoiseFillTest.load();
        try {
            var registries = NativeNoiseFillTest.registries;
            var test = cases().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
            var setting = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(test.settings());
            var source = MultiNoiseBiomeSource.createFromPreset(registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(test.biomes()));
            var list = source.nativeParameters();
            var generator = new NoiseBasedChunkGenerator(source, setting);
            var config = setting.value();
            var noise = config.noiseSettings();
            var random = RandomState.create(config, registries.lookupOrThrow(Registries.NOISE), 7_340_013L);
            var containers = PalettedContainerFactory.create(registries);
            ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(5, -3), new ChunkPos(-41, 17), new ChunkPos(160, 90),
                new ChunkPos(-1200, -800), new ChunkPos(3000, 4100), new ChunkPos(-9, -9), new ChunkPos(77, -512)};
            NativeBiomeFill.setEnabled(mode.equals("native"));
            long before = NativeBiomeFill.CHUNKS.get();
            PlayerChunkDistancesVerification.measure(mode, name, quick, () -> {
                List<ProtoChunk> chunks = new ArrayList<>();
                for (ChunkPos pos : positions) {
                    chunks.add(new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(noise.minY(), noise.height()), containers, null));
                }
                return chunks;
            }, chunks -> {
                list.setPreviousNativeLeaf(-1);
                long checksum = 0;
                for (var chunk : chunks) {
                    chunk.getOrCreateNoiseChunk(access -> NoiseChunk.forChunk(access, random, Beardifier.EMPTY, config,
                        new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty()));
                    generator.doCreateBiomes(Blender.empty(), random, null, chunk);
                    for (var section : chunk.getSections()) {
                        for (int i = 0; i < 4; i++) checksum = checksum * 31 + section.generatedBiomeIds().getId(section.getNoiseBiome(i, i, 3 - i));
                    }
                }
                return checksum * 31 + list.previousNativeLeaf();
            });
            long filled = NativeBiomeFill.CHUNKS.get() - before;
            if (mode.equals("native") == (filled == 0)) throw new IllegalStateException("Route not taken: " + filled + " native chunks");
            System.out.println("BIOME_FILL_ROUTE case=" + name + " mode=" + mode + " native=" + filled);
        } finally {
            NativeBiomeFill.setEnabled(true);
            NativeNoiseFillTest.close();
        }
    }
}
