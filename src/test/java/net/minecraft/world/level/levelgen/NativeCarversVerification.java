package net.minecraft.world.level.levelgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.PlayerChunkDistancesVerification;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Opt-in benchmark of the CARVERS stage ({@code carveChunk}) with Java's
 * carvers ({@code java}) or the Rust-owned stage ({@code native}), over chunks
 * the NOISE and SURFACE stages prepared outside the timer. Neighbouring
 * chunks keep their carver biomes across samples, as a server's do. */
public final class NativeCarversVerification {
    record Prepared(ProtoChunk chunk, NoiseChunk noise) {}

    static List<ResourceKey<NoiseGeneratorSettings>> cases() {
        return List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.AMPLIFIED, NoiseGeneratorSettings.NETHER);
    }

    /** {@code main(mode, case[, quick])}: case overworld, amplified or nether. */
    public static void main(String[] args) throws Exception {
        String mode = args[0], name = args[1];
        boolean quick = args.length > 2 && args[2].equals("quick");
        if (!mode.equals("native") && !mode.equals("java")) throw new IllegalArgumentException(mode);
        NativeNoiseFillTest.load();
        NativeCarversTest.bindStaticTags();
        try {
            var registries = NativeNoiseFillTest.registries;
            var key = cases().stream().filter(k -> k.location().getPath().equals(name)).findFirst().orElseThrow();
            Holder<NoiseGeneratorSettings> setting = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
            var config = setting.value();
            long seed = 7_340_013L;
            var random = RandomState.create(config, registries.lookupOrThrow(Registries.NOISE), seed);
            var biomeSource = NativeCarversTest.source(setting);
            var generator = new NoiseBasedChunkGenerator(biomeSource, setting);
            var manager = new BiomeManager((x, y, z) -> biomeSource.getNoiseBiome(x, y, z, random.sampler()), BiomeManager.obfuscateSeed(seed));
            var height = LevelHeightAccessor.create(config.noiseSettings().minY(), config.noiseSettings().height());
            var containers = PalettedContainerFactory.create(registries);
            Map<Long, ChunkAccess> neighbours = new HashMap<>();
            ChunkPos[] positions = {new ChunkPos(0, 0), new ChunkPos(5, -3), new ChunkPos(-41, 17), new ChunkPos(160, 90),
                new ChunkPos(-1200, -800), new ChunkPos(3000, 4100), new ChunkPos(-9, -9), new ChunkPos(77, -512)};
            NativeCarvers.setEnabled(mode.equals("native"));
            long before = NativeCarvers.CHUNKS.get();
            PlayerChunkDistancesVerification.measure(mode, name, quick, () -> {
                List<Prepared> chunks = new ArrayList<>();
                for (ChunkPos pos : positions) {
                    var filled = NativeNoiseFillTest.fill(setting, random, pos, true);
                    var chunk = filled.chunk();
                    chunk.setPersistedStatus(ChunkStatus.NOISE);
                    random.surfaceSystem().buildSurface(random, manager, registries.lookupOrThrow(Registries.BIOME), config.useLegacyRandomSource(),
                        new WorldGenerationContext(generator, chunk), chunk, filled.noise(), config.surfaceRule());
                    chunk.setPersistedStatus(ChunkStatus.SURFACE);
                    chunks.add(new Prepared(chunk, filled.noise()));
                }
                return chunks;
            }, chunks -> {
                long checksum = 0;
                for (var prepared : chunks) {
                    var chunk = prepared.chunk();
                    ChunkPos pos = chunk.getPos();
                    NoiseBasedChunkGenerator.CarverChunks lookup = (x, z) -> x == pos.x && z == pos.z ? chunk
                        : neighbours.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, height, containers, null));
                    generator.carveChunk(lookup, registries, seed, random, manager, chunk, prepared.noise());
                    for (var type : new Heightmap.Types[]{Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG}) {
                        for (long word : chunk.getOrCreateHeightmapUnprimed(type).getRawData()) checksum = checksum * 31 + word;
                    }
                    for (long word : chunk.getOrCreateCarvingMask().toArray()) checksum = checksum * 31 + word;
                    for (var section : chunk.getSections()) {
                        for (int i = 0; i < 16; i++) checksum = checksum * 31 + Block.getId(section.getBlockState(i, i, 15 - i));
                    }
                    for (var marks : chunk.getPostProcessing()) checksum = checksum * 31 + (marks == null ? -1 : marks.size());
                }
                return checksum;
            });
            long carved = NativeCarvers.CHUNKS.get() - before;
            if (mode.equals("native") == (carved == 0)) throw new IllegalStateException("Route not taken: " + carved + " native chunks");
            System.out.println("CARVERS_ROUTE case=" + name + " mode=" + mode + " native=" + carved);
        } finally {
            NativeCarvers.setEnabled(true);
            NativeNoiseFillTest.close();
        }
    }
}
