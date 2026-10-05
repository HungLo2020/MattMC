package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.PlayerChunkDistancesVerification;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Production-path timing of the NOISE fill. One JVM runs one mode: {@code java}
 * (doFill's Java loop), {@code javaslices} (the Rust fill with Java's
 * interpolation slices) or {@code native} (the Rust fill and Rust slices). Each round fills fresh
 * chunks through {@code NoiseBasedChunkGenerator.fillFromNoise}; chunk and
 * NoiseChunk construction are untimed and identical for both modes. */
public final class NoiseFillVerification {
    record Job(NoiseBasedChunkGenerator generator, RandomState random, ProtoChunk chunk) {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        String setting = switch (name) {
            case "overworld" -> "overworld";
            case "amplified" -> "amplified";
            case "nether" -> "nether";
            default -> throw new IllegalArgumentException(name);
        };
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
            RegistryAccess.Frozen registries = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            Holder<NoiseGeneratorSettings> holder = registries.lookupOrThrow(Registries.NOISE_SETTINGS).listElements()
                .filter(element -> element.key().location().getPath().equals(setting)).findFirst().orElseThrow();
            var config = holder.value();
            var random = RandomState.create(config, registries.lookupOrThrow(Registries.NOISE), 7_340_013L);
            var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)), holder);
            var containers = PalettedContainerFactory.create(registries);
            var noise = config.noiseSettings();
            ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(5, -3), new ChunkPos(-41, 17), new ChunkPos(160, 90),
                new ChunkPos(-1200, -800), new ChunkPos(3000, 4100), new ChunkPos(-9, -9), new ChunkPos(77, -512)};
            System.out.println("PLAYER_DISTANCE_FIXTURE case=" + name + " ticks=" + positions.length + " moves=" + positions.length
                + " trace=" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest((setting + java.util.Arrays.toString(positions)).getBytes())));
            if (!mode.equals("native") && !mode.equals("javaslices") && !mode.equals("java")) throw new IllegalArgumentException(mode);
            NativeNoiseFill.setEnabled(!mode.equals("java"));
            NativeNoiseRouter.setEnabled(mode.equals("native"));
            long before = NativeNoiseFill.RUNS.get(), slicesBefore = NativeNoiseRouter.SLICES.get();
            PlayerChunkDistancesVerification.measure(mode, name, quick, () -> {
                List<Job> jobs = new ArrayList<>();
                for (ChunkPos pos : positions) {
                    var chunk = new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(noise.minY(), noise.height()), containers, null);
                    chunk.getOrCreateNoiseChunk(access -> NoiseChunk.forChunk(access, random, DensityFunctions.BeardifierMarker.INSTANCE, config,
                        new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty()));
                    jobs.add(new Job(generator, random, chunk));
                }
                return jobs;
            }, jobs -> {
                long checksum = 0;
                for (Job job : jobs) {
                    job.generator().fillFromNoise(Blender.empty(), job.random(), null, job.chunk()).join();
                    for (var type : new Heightmap.Types[]{Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG}) {
                        for (long word : job.chunk().getOrCreateHeightmapUnprimed(type).getRawData()) checksum = checksum * 31 + word;
                    }
                    for (var section : job.chunk().getSections()) checksum = checksum * 31 + (section.hasOnlyAir() ? 0 : 1);
                }
                return checksum;
            });
            long nativeRuns = NativeNoiseFill.RUNS.get() - before, nativeSlices = NativeNoiseRouter.SLICES.get() - slicesBefore;
            if (mode.equals("java") != (nativeRuns == 0) || mode.equals("native") == (nativeSlices == 0))
                throw new IllegalStateException("Unexpected route: native runs " + nativeRuns + ", native slices " + nativeSlices);
        }
    }
}
