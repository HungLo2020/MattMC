package net.minecraft.world.level.levelgen;

import com.mojang.datafixers.util.Pair;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Seeded sampler -> biome lookup -> section palette comparison. Runs with either
 * original Java classes or the production native bridge, in separate JVMs. */
public final class NativeBiomeWorldVerification {
    private static final int[][] POSITIONS = {{0,0},{-2,1},{1,-2},{64,128},{-1024,-2048},{1874998,-1874998}};
    public static String fingerprint(ChunkAccess chunk) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        for (var section : chunk.getSections()) {
            for (int x=0;x<4;x++) for (int y=0;y<4;y++) for (int z=0;z<4;z++) {
                digest.update(section.getNoiseBiome(x,y,z).unwrapKey().orElseThrow().location().toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        boolean nativeMode = args.length > 0 && args[0].equals("native");
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.SERVER_DATA,
                List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()))) {
            var layers = net.minecraft.server.RegistryLayer.createRegistryAccess();
            var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(net.minecraft.server.RegistryLayer.STATIC));
            var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(net.minecraft.server.RegistryLayer.WORLDGEN), tags);
            var registries = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
            var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).listElements().sorted(Comparator.comparing(h -> h.key().location().toString())).toList();
            var noises = registries.lookupOrThrow(Registries.NOISE);
            var biomes = registries.lookupOrThrow(Registries.BIOME);
            var factory = PalettedContainerFactory.create(registries);
            for (var holder : settings) for (long seed : new long[]{0,42,-123456789,Long.MIN_VALUE,Long.MAX_VALUE}) {
                var config = holder.value();
                var random = RandomState.create(config, noises, seed);
                var presets = new TreeMap<String, Climate.ParameterList<net.minecraft.resources.ResourceKey<Biome>>>();
                MultiNoiseBiomeSourceParameterList.knownPresets().forEach((p,v) -> presets.put(p.id().getPath(),v));
                for (var preset : presets.entrySet()) {
                    var corpus = new ArrayList<Climate.TargetPoint>();
                    boolean recordCorpus = args.length > 1 && seed == 42 && holder.key().location().getPath().equals(preset.getKey());
                    List<Pair<Climate.ParameterPoint,Holder<Biome>>> rows = new ArrayList<>();
                    preset.getValue().values().forEach(p -> rows.add(Pair.of(p.getFirst(),biomes.getOrThrow(p.getSecond()))));
                    var source = MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(rows));
                    var digest = MessageDigest.getInstance("SHA-256");
                    int count = 0;
                    for (int[] pos : POSITIONS) {
                        var noise = new NoiseChunk(16/config.noiseSettings().getCellWidth(), random, pos[0]*16, pos[1]*16,
                            config.noiseSettings(), DensityFunctions.BeardifierMarker.INSTANCE, config,
                            (x,y,z) -> new Aquifer.FluidStatus(config.seaLevel(),config.defaultFluid()), Blender.empty());
                        var sampler = noise.cachedClimateSampler(random.router(), config.spawnTarget());
                        if (nativeMode && !BiomeSamplerSafety.canBatch(sampler)) throw new AssertionError("Built-in sampler was not batched: " + holder.key());
                        for (int y = config.noiseSettings().minY()/4; y < (config.noiseSettings().minY()+config.noiseSettings().height())/4; y += 4) {
                            var section = new LevelChunkSection(factory);
                            section.fillBiomesFromNoise(source, sampler, pos[0]*4, y, pos[1]*4);
                            for (int x=0;x<4;x++) for (int dy=0;dy<4;dy++) for (int z=0;z<4;z++) {
                                var biome = section.getNoiseBiome(x,dy,z);
                                digest.update(biome.unwrapKey().orElseThrow().location().toString().getBytes(StandardCharsets.UTF_8));
                                digest.update((byte)0);
                                count++;
                                if (recordCorpus) corpus.add(sampler.sample(pos[0]*4+x,y+dy,pos[1]*4+z));
                            }
                        }
                    }
                    System.out.println("BIOME_WORLD name="+holder.key().location().getPath()+"/"+preset.getKey()+" seed="+seed+" decisions="+count+" sha256="+HexFormat.of().formatHex(digest.digest()));
                    if (recordCorpus) {
                        var directory = java.nio.file.Path.of(args[1]);
                        java.nio.file.Files.createDirectories(directory);
                        try (var output = new java.io.DataOutputStream(java.nio.file.Files.newOutputStream(directory.resolve(preset.getKey()+".bin")))) {
                            for (var point : corpus) {
                                output.writeLong(point.temperature());output.writeLong(point.humidity());
                                output.writeLong(point.continentalness());output.writeLong(point.erosion());
                                output.writeLong(point.depth());output.writeLong(point.weirdness());
                            }
                        }
                    }
                }
            }
        }
    }
}
