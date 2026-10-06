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

/** Production-path timing of chunk noise instantiation. One JVM runs one mode:
 * {@code java} (each NoiseChunk wraps its graph) or {@code native} (per-seed
 * templates). Each round, per fresh chunk: the biome stage's NoiseChunk and
 * climate sampler (sampled at every quart of the chunk), then
 * {@code fillFromNoise}. ProtoChunk construction is untimed. */
public final class ChunkNoiseVerification {
    record Job(NoiseBasedChunkGenerator generator, RandomState random, ProtoChunk chunk, DensityFunctions.BeardifierOrMarker beardifier) {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        String setting = switch (name) {
            case "overworld" -> "overworld";
            case "amplified" -> "amplified";
            case "nether" -> "nether";
            // Overworld chunks beside the recorded structure corpus, with their Beardifiers.
            case "structures" -> "overworld";
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
            DensityFunctions.BeardifierOrMarker[] beardifiers = new DensityFunctions.BeardifierOrMarker[positions.length];
            java.util.Arrays.fill(beardifiers, Beardifier.EMPTY);
            if (name.equals("structures")) {
                var fixtures = NativeBeardifierVerification.fixtures();
                for (int index = 0; index < positions.length; index++) {
                    var fixture = fixtures.get(index * fixtures.size() / positions.length);
                    var cell = fixture.full().get(0);
                    positions[index] = new ChunkPos(cell.x() >> 4, cell.z() >> 4);
                    beardifiers[index] = Beardifier.forGeometry(fixture.pieces(), fixture.junctions(), fixture.bounds());
                }
            }
            System.out.println("PLAYER_DISTANCE_FIXTURE case=" + name + " ticks=" + positions.length + " moves=" + positions.length
                + " trace=" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest((setting + java.util.Arrays.toString(positions)).getBytes())));
            if (!mode.equals("native") && !mode.equals("java")) throw new IllegalArgumentException(mode);
            NativeChunkNoise.setEnabled(mode.equals("native"));
            long before = NativeNoiseFill.INSTANCES.get();
            PlayerChunkDistancesVerification.measure(mode, name, quick, () -> {
                List<Job> jobs = new ArrayList<>();
                for (int index = 0; index < positions.length; index++) {
                    var chunk = new ProtoChunk(positions[index], UpgradeData.EMPTY, LevelHeightAccessor.create(noise.minY(), noise.height()), containers, null);
                    jobs.add(new Job(generator, random, chunk, beardifiers[index]));
                }
                return jobs;
            }, jobs -> {
                long checksum = 0;
                for (Job job : jobs) {
                    // The chunk's structure Beardifier (EMPTY without terrain-adjusting structures).
                    var noiseChunk = job.chunk().getOrCreateNoiseChunk(access -> NoiseChunk.forChunk(access, random, job.beardifier(),
                        config, new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), Blender.empty()));
                    var pos = job.chunk().getPos();
                    var sampler = noiseChunk.cachedClimateSampler(random.router(), config.spawnTarget());
                    for (int qy = noise.minY() >> 2; qy < (noise.minY() + noise.height()) >> 2; qy++) {
                        for (int qx = 0; qx < 4; qx++) {
                            for (int qz = 0; qz < 4; qz++) {
                                var point = sampler.sample((pos.getMinBlockX() >> 2) + qx, qy, (pos.getMinBlockZ() >> 2) + qz);
                                checksum = checksum * 31 + point.temperature() + point.erosion() + point.depth();
                            }
                        }
                    }
                    job.generator().fillFromNoise(Blender.empty(), job.random(), null, job.chunk()).join();
                    for (var type : new Heightmap.Types[]{Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG}) {
                        for (long word : job.chunk().getOrCreateHeightmapUnprimed(type).getRawData()) checksum = checksum * 31 + word;
                    }
                    for (var section : job.chunk().getSections()) checksum = checksum * 31 + (section.hasOnlyAir() ? 0 : 1);
                }
                return checksum;
            });
            long instances = NativeNoiseFill.INSTANCES.get() - before;
            if (mode.equals("native") == (instances == 0)) throw new IllegalStateException("Unexpected route: native instances " + instances);
        }
    }
}
