package net.minecraft.world.level.levelgen;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Compiled separately against actual Frozen and Current. No baseline rewrites.
 * Complete generated blocks, section counters, heightmaps and marks are hashed.
 * This is a world-generation observer, not a gameplay benchmark. */
public final class VerifyRetainedGeneration {
    static void image(String name) throws Exception {
        try (var stream = ClassLoader.getSystemResourceAsStream(name.replace('.', '/') + ".class")) {
            if (stream == null) throw new AssertionError(name);
            System.out.println("CLASS " + name + " " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())));
        }
    }
    static String fingerprint(ProtoChunk chunk) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var data = new DataOutputStream(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            // Read section objects without publishing a mutable array alias.
            for (int index = 0; index < chunk.getSectionsCount(); index++) {
                var section = chunk.getSection(index);
                data.writeBoolean(section.hasOnlyAir());
                data.writeBoolean(section.isRandomlyTickingBlocks());
                data.writeBoolean(section.isRandomlyTickingFluids());
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                    data.writeInt(Block.getId(section.getBlockState(x, y, z)));
            }
            for (var type : new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG})
                for (long value : chunk.getOrCreateHeightmapUnprimed(type).getRawData()) data.writeLong(value);
            for (var marks : chunk.getPostProcessing()) {
                data.writeInt(marks == null ? -1 : marks.size());
                if (marks != null) for (int i = 0; i < marks.size(); i++) data.writeShort(marks.getShort(i));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void main(String[] args) throws Exception {
        boolean current = args[0].equals("current");
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        for (String name : List.of("net.minecraft.world.level.chunk.ChunkAccess", "net.minecraft.world.level.chunk.ProtoChunk",
            "net.minecraft.world.level.chunk.LevelChunkSection", "net.minecraft.world.level.chunk.PalettedContainer",
            "net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator", "net.minecraft.world.level.levelgen.SurfaceSystem")) image(name);
        if (current) for (String name : List.of("net.minecraft.world.level.chunk.NativeGenerationSections",
            "net.minecraft.world.level.chunk.NativeChunkInputs", "net.minecraft.world.level.levelgen.NativeProtoChunk",
            "net.minecraft.world.level.levelgen.NativeSurfaceChunk")) image(name);
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
             var out = Files.newBufferedWriter(Path.of(args[1]))) {
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
            RegistryAccess.Frozen registries = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            var pickerMethod = NoiseBasedChunkGenerator.class.getDeclaredMethod("createFluidPicker", NoiseGeneratorSettings.class);
            pickerMethod.setAccessible(true);
            int cases = 0;
            long before = current ? nativeStages() : 0;
            for (var key : List.of(NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.AMPLIFIED, NoiseGeneratorSettings.NETHER)) {
                var setting = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
                var config = setting.value(); var noise = config.noiseSettings();
                var source = MultiNoiseBiomeSource.createFromPreset(registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(key.equals(NoiseGeneratorSettings.NETHER) ? MultiNoiseBiomeSourceParameterLists.NETHER : MultiNoiseBiomeSourceParameterLists.OVERWORLD));
                var generator = new NoiseBasedChunkGenerator(source, setting);
                var picker = (Aquifer.FluidPicker)pickerMethod.invoke(null, config);
                for (long seed : new long[]{7340013, -871551}) {
                    var random = RandomState.create(config, registries.lookupOrThrow(Registries.NOISE), seed);
                    var biomeManager = new BiomeManager((x, y, z) -> source.getNoiseBiome(x, y, z, random.sampler()), BiomeManager.obfuscateSeed(seed));
                    for (var pos : List.of(new ChunkPos(0, 0), new ChunkPos(5, -3), new ChunkPos(-41, 17), new ChunkPos(160, 90))) {
                        var chunk = new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(noise.minY(), noise.height()), PalettedContainerFactory.create(registries), null);
                        chunk.getOrCreateNoiseChunk(access -> NoiseChunk.forChunk(access, random, DensityFunctions.BeardifierMarker.INSTANCE, config, picker, Blender.empty()));
                        generator.fillFromNoise(Blender.empty(), random, null, chunk).join();
                        chunk.setPersistedStatus(ChunkStatus.NOISE);
                        generator.buildSurface(chunk, new WorldGenerationContext(generator, chunk), random, null, biomeManager,
                            registries.lookupOrThrow(Registries.BIOME), Blender.empty());
                        out.write(key.location() + " " + seed + " " + pos.x + " " + pos.z + " " + fingerprint(chunk) + "\n");
                        if (current && (boolean)ChunkAccess.class.getMethod("hasEscapedSectionArray").invoke(chunk))
                            throw new AssertionError("Generation escaped the section directory");
                        cases++;
                    }
                }
            }
            if (current && nativeStages() - before != cases) throw new AssertionError("Native surface path was not used for every case");
            System.out.println("GENERATION_CASES " + cases);
        }
        if (current) for (String line : Files.readAllLines(Path.of("/proc/self/maps")))
            if (line.contains("mattmc_rust") && line.contains(".so")) System.out.println("NATIVE_MAP " + line);
    }
    static long nativeStages() throws Exception {
        var field = Class.forName("net.minecraft.world.level.levelgen.NativeSurfaceChunk").getDeclaredField("CHUNKS");
        field.setAccessible(true); return ((java.util.concurrent.atomic.AtomicLong)field.get(null)).get();
    }
}
