package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.PlayerChunkDistancesVerification;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Opt-in benchmark of the SURFACE stage on Rust-owned storage against Java
 * storage (-Dmattmc.worldgen.javaSurfaceStorage=true), over chunks the NOISE
 * stage filled. Biomes are the dimension's multi-noise biomes, sampled before
 * timing into a quart table, since production reads them from the region's
 * chunk biome containers. Timed: buildSurface, including Rust storage
 * creation, every boundary crossing and the install. */
public final class NativeSurfaceChunkVerification {
    record Case(String name, ResourceKey<NoiseGeneratorSettings> settings, ResourceKey<MultiNoiseBiomeSourceParameterList> biomes) {}

    /** After Bootstrap: the keys' classes need registries. */
    static List<Case> cases() {
        return List.of(
        new Case("overworld", NoiseGeneratorSettings.OVERWORLD, MultiNoiseBiomeSourceParameterLists.OVERWORLD),
        new Case("amplified", NoiseGeneratorSettings.AMPLIFIED, MultiNoiseBiomeSourceParameterLists.OVERWORLD),
        new Case("nether", NoiseGeneratorSettings.NETHER, MultiNoiseBiomeSourceParameterLists.NETHER));
    }

    record Prepared(ProtoChunk chunk, NoiseChunk noise, BiomeManager biomes) {}

    /** A NOISE-filled chunk at the SURFACE stage with a precomputed biome table. */
    static Prepared prepare(Holder<NoiseGeneratorSettings> setting, RandomState random, MultiNoiseBiomeSource source, long seed, ChunkPos pos) {
        var filled = NativeNoiseFillTest.fill(setting, random, pos, true);
        filled.chunk().setPersistedStatus(ChunkStatus.NOISE);
        var noise = setting.value().noiseSettings();
        int qx = (pos.getMinBlockX() >> 2) - 2, qz = (pos.getMinBlockZ() >> 2) - 2, qy = (noise.minY() >> 2) - 2;
        int sizeY = (noise.height() >> 2) + 4;
        @SuppressWarnings("unchecked")
        Holder<Biome>[] table = new Holder[8 * 8 * sizeY];
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) for (int y = 0; y < sizeY; y++)
            table[(x * 8 + z) * sizeY + y] = source.getNoiseBiome(qx + x, qy + y, qz + z, random.sampler());
        BiomeManager.NoiseBiomeSource lookup = (x, y, z) -> {
            int dx = x - qx, dz = z - qz, dy = Math.clamp(y - qy, 0, sizeY - 1);
            return dx >= 0 && dx < 8 && dz >= 0 && dz < 8 ? table[(dx * 8 + dz) * sizeY + dy] : source.getNoiseBiome(x, y, z, random.sampler());
        };
        return new Prepared(filled.chunk(), filled.noise(), new BiomeManager(lookup, BiomeManager.obfuscateSeed(seed)));
    }

    /** {@code main(mode, case[, quick])}: mode {@code java} (Java storage) or {@code native}. */
    public static void main(String[] args) throws Exception {
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        if (!List.of("native", "java", "javaconditions").contains(mode)) throw new IllegalArgumentException(mode);
        NativeNoiseFillTest.load();
        try {
            var registries = NativeNoiseFillTest.registries;
            var biomeRegistry = registries.lookupOrThrow(Registries.BIOME);
            var test = cases().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
            var setting = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(test.settings());
            var source = MultiNoiseBiomeSource.createFromPreset(registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(test.biomes()));
            var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(biomeRegistry.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS)), setting);
            var config = setting.value();
            var random = RandomState.create(config, registries.lookupOrThrow(Registries.NOISE), 7_340_013L);
            ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(5, -3), new ChunkPos(-41, 17), new ChunkPos(160, 90),
                new ChunkPos(-1200, -800), new ChunkPos(3000, 4100), new ChunkPos(-9, -9), new ChunkPos(77, -512)};
            // javaconditions: Rust storage with Java answering conditions and surface
            // inputs and compiling each chunk's program (the previous production path).
            NativeSurfaceChunk.setEnabled(!mode.equals("java"));
            NativeSurfaceChunk.nativeConditions = mode.equals("native");
            // The previous production path also compiled each chunk's rule program.
            NativeSurface.cacheCompiled = mode.equals("native");
            long before = NativeSurfaceChunk.CHUNKS.get();
            PlayerChunkDistancesVerification.measure(mode, name, quick, () -> {
                List<Prepared> chunks = new ArrayList<>();
                for (ChunkPos pos : positions) chunks.add(prepare(setting, random, source, 7_340_013L, pos));
                return chunks;
            }, chunks -> {
                long checksum = 0;
                for (var chunk : chunks) {
                    random.surfaceSystem().buildSurface(random, chunk.biomes(), biomeRegistry, config.useLegacyRandomSource(),
                        new WorldGenerationContext(generator, chunk.chunk()), chunk.chunk(), chunk.noise(), config.surfaceRule());
                    for (var type : new Heightmap.Types[]{Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG}) {
                        for (long word : chunk.chunk().getOrCreateHeightmapUnprimed(type).getRawData()) checksum = checksum * 31 + word;
                    }
                    // Block ids along each section's diagonals, cheap against the stage.
                    for (var section : chunk.chunk().getSections()) {
                        for (int i = 0; i < 16; i++) {
                            checksum = checksum * 31 + net.minecraft.world.level.block.Block.getId(section.getBlockState(i, i, i));
                            checksum = checksum * 31 + net.minecraft.world.level.block.Block.getId(section.getBlockState(15 - i, i, i));
                        }
                    }
                    for (var marks : chunk.chunk().getPostProcessing()) checksum = checksum * 31 + (marks == null ? -1 : marks.size());
                }
                return checksum;
            });
            long owned = NativeSurfaceChunk.CHUNKS.get() - before;
            if (mode.equals("java") != (owned == 0)) throw new IllegalStateException("Route not taken: " + owned + " owned chunks");
            System.out.println("SURFACE_CHUNK_ROUTE case=" + name + " mode=" + mode + " owned=" + owned);
        } finally {
            NativeSurfaceChunk.setEnabled(true);
            NativeSurfaceChunk.nativeConditions = true;
            NativeSurface.cacheCompiled = true;
            NativeNoiseFillTest.close();
        }
    }
}
