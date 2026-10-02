package net.minecraft.world.level.levelgen.feature;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Bundled registries and native ore placement in real fresh chunk sections, not a server terrain-generation test. */
@Isolated("Temporarily loads the bundled block tags")
class CustomOreTargetTagsTest {
    private static final List<String> FEATURES = List.of("coal", "coal_buried", "iron", "iron_small", "gold", "gold_buried",
        "lapis", "lapis_buried", "emerald", "redstone", "infested", "copper_small", "copper_large",
        "diamond_small", "diamond_medium", "diamond_large", "diamond_buried");
    private static final int[] SIZES = {12, 17, 9, 4, 9, 9, 7, 7, 3, 8, 16, 10, 20, 4, 8, 12, 8};
    private static final float[] AIR_CHANCES = {0, .85F, .3F, 0, 0, .5F, 0, 1, 0, 0, 1, 0, 0, .5F, .5F, .7F, 1};
    private static RegistryAccess.Frozen registries;
    private static Map<TagKey<Block>, List<Holder<Block>>> originalTags;

    @BeforeAll
    static void loadBundledRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalTags = BuiltInRegistries.BLOCK.listTags().collect(Collectors.toMap(HolderSet.Named::key, tag -> tag.stream().toList()));
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource()))) {
            var layers = RegistryLayer.createRegistryAccess();
            var pending = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
            var lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), pending);
            registries = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
            pending.stream().filter(tags -> tags.key().equals(Registries.BLOCK)).forEach(Registry.PendingTags::apply);
        }
    }

    @AfterAll
    static void restoreTags() {
        if (originalTags != null) BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, originalTags)).apply();
    }

    static Stream<String> features() { return FEATURES.stream(); }

    static Stream<Arguments> featureHosts() {
        return FEATURES.stream().flatMap(name -> Stream.of(Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE,
            Blocks.ANDESITE, Blocks.DEEPSLATE, Blocks.TUFF).map(host -> Arguments.of(name, host)));
    }

    private static ConfiguredFeature<?, ?> configured(String name) {
        return registries.lookupOrThrow(Registries.CONFIGURED_FEATURE).getValue(ResourceLocation.withDefaultNamespace("ore/" + name));
    }

    private static TagKey<Block> tag(TagMatchTest rule) {
        String id = TagMatchTest.CODEC.codec().encodeStart(JsonOps.INSTANCE, rule).getOrThrow().getAsJsonObject().get("tag").getAsString();
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
    }

    private static OreConfiguration config(String name) {
        ConfiguredFeature<?, ?> feature = configured(name);
        assertNotNull(feature, name);
        assertSame(Feature.ORE, feature.feature());
        return assertInstanceOf(OreConfiguration.class, feature.config());
    }

    @ParameterizedTest
    @MethodSource("features")
    void allConfiguredTargetsResolveToTheIntendedHostsAndRetainSizesAndAirRules(String name) {
        OreConfiguration config = config(name);
        int index = FEATURES.indexOf(name);
        assertEquals(SIZES[index], config.size, name);
        assertEquals(AIR_CHANCES[index], config.discardChanceOnAirExposure, name);
        assertEquals(2, config.targetStates.size(), name);
        for (int targetIndex = 0; targetIndex < 2; targetIndex++) {
            var target = config.targetStates.get(targetIndex);
            var rule = assertInstanceOf(TagMatchTest.class, target.target);
            Set<Block> expected = targetIndex == 0 ? Set.of(Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE)
                : Set.of(Blocks.DEEPSLATE, Blocks.TUFF);
            String tag = targetIndex == 0 ? "stone_ore_replaceables" : "deepslate_ore_replaceables";
            assertEquals(ResourceLocation.withDefaultNamespace(tag), tag(rule).location());
            Set<Block> actual = BuiltInRegistries.BLOCK.get(tag(rule)).orElseThrow().stream().map(Holder::value).collect(Collectors.toSet());
            assertEquals(expected, actual, name);
            for (Block block : List.of(Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.DEEPSLATE,
                    Blocks.TUFF, Blocks.BEDROCK, Blocks.DIRT, Blocks.SANDSTONE, Blocks.NETHERRACK, Blocks.AIR, Blocks.WATER)) {
                assertEquals(expected.contains(block), rule.test(block.defaultBlockState(), new LegacyRandomSource(42)), name + ": " + block);
            }
        }
    }

    @Test
    void noConfiguredOreRetainsEitherMissingTargetTag() {
        for (ConfiguredFeature<?, ?> feature : registries.lookupOrThrow(Registries.CONFIGURED_FEATURE)) {
            if (feature.config() instanceof OreConfiguration ore) {
                for (var target : ore.targetStates) {
                    if (target.target instanceof TagMatchTest rule) {
                        assertNotEquals(ResourceLocation.withDefaultNamespace("replaceable/stone_ores"), tag(rule).location());
                        assertNotEquals(ResourceLocation.withDefaultNamespace("replaceable/deepslate_ores"), tag(rule).location());
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("featureHosts")
    void seededNativePlacementWritesTheCorrectVariantIntoFreshChunkSections(String name, Block host) {
        var config = config(name);
        BlockState expected = config.targetStates.stream().filter(t -> t.target.test(host.defaultBlockState(), new LegacyRandomSource(0)))
            .findFirst().orElseThrow().state;
        // Small veins can have no occupied candidates for a seed; retain that valid behavior.
        for (long seed = 0; seed < 32; seed++) {
            OreTestWorld world = solidWorld(host);
            RandomSource random = new WorldgenRandom(new XoroshiroRandomSource(seed));
            boolean placed = NativeOreGeometryTest.place(true, world, random, config, new BlockPos(8, 0, 8));
            Map<Long, Integer> result = world.writtenBlocks();
            assertEquals(!result.isEmpty(), placed);
            assertTrue(result.values().stream().allMatch(id -> id == Block.getId(expected)), name);
            if (placed) {
                OreTestWorld repeated = solidWorld(host);
                RandomSource repeatedRandom = new WorldgenRandom(new XoroshiroRandomSource(seed));
                assertTrue(NativeOreGeometryTest.place(true, repeated, repeatedRandom, config, new BlockPos(8, 0, 8)));
                assertEquals(result, repeated.writtenBlocks(), "same fresh chunk and seed");
                assertEquals(random.nextLong(), repeatedRandom.nextLong(), "same subsequent RNG state");
                return;
            }
        }
        fail("No native ore placement across 32 deterministic seeds for " + name + " in " + host);
    }

    @ParameterizedTest
    @MethodSource("features")
    void airExposureAndUnrelatedHostsKeepTheirExistingPlacementRules(String name) {
        var config = config(name);
        var target = config.targetStates.getFirst();
        var pos = new BlockPos.MutableBlockPos(8, 0, 8);
        assertTrue(OreFeature.canPlaceOre(Blocks.STONE.defaultBlockState(), p -> Blocks.STONE.defaultBlockState(),
            constantFloat(0), config, target, pos));
        assertFalse(OreFeature.canPlaceOre(Blocks.BEDROCK.defaultBlockState(), p -> Blocks.STONE.defaultBlockState(),
            constantFloat(1), config, target, pos));
        assertEquals(config.discardChanceOnAirExposure <= 0,
            OreFeature.canPlaceOre(Blocks.STONE.defaultBlockState(), p -> Blocks.AIR.defaultBlockState(), constantFloat(0), config, target, pos));
        assertEquals(config.discardChanceOnAirExposure < 1,
            OreFeature.canPlaceOre(Blocks.STONE.defaultBlockState(), p -> Blocks.AIR.defaultBlockState(), constantFloat(.9999F), config, target, pos));
        OreTestWorld rejected = solidWorld(Blocks.BEDROCK);
        assertFalse(NativeOreGeometryTest.place(true, rejected, new LegacyRandomSource(42), config, new BlockPos(8, 0, 8)));
        assertTrue(rejected.writtenBlocks().isEmpty());
    }

    @Test
    void registryLoadedBiomeChainsIdentifyAllCallersWithoutChangingOtherFeatureRoutes() {
        Set<ConfiguredFeature<?, ?>> affected = FEATURES.stream().map(CustomOreTargetTagsTest::configured).collect(Collectors.toSet());
        var placedRegistry = registries.lookupOrThrow(Registries.PLACED_FEATURE);
        Set<PlacedFeature> placed = placedRegistry.stream().filter(p -> affected.contains(p.feature().value())).collect(Collectors.toSet());
        assertEquals(25, placed.size());
        Set<String> biomes = new TreeSet<>();
        Set<PlacedFeature> referenced = new HashSet<>();
        for (var holder : registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
            Set<PlacedFeature> selected = holder.value().getGenerationSettings().features().stream()
                .flatMap(HolderSet::stream).map(Holder::value).filter(placed::contains).collect(Collectors.toSet());
            if (!selected.isEmpty()) {
                biomes.add(holder.key().location().getPath());
                referenced.addAll(selected);
                assertEquals(holder.key().location().getPath().equals("dry_midlands") ? 23 : 25, selected.size());
            }
        }
        assertEquals(Set.of("dry_midlands", "primordial_ocean"), biomes);
        assertEquals(placed, referenced);
        assertSame(Feature.GEODE, registries.lookupOrThrow(Registries.CONFIGURED_FEATURE)
            .getValue(ResourceLocation.withDefaultNamespace("geode/diamond")).feature());
        var magma = assertInstanceOf(OreConfiguration.class, configured("magma").config());
        assertEquals(ResourceLocation.withDefaultNamespace("base_stone_dwarfhollow"),
            tag(assertInstanceOf(TagMatchTest.class, magma.targetStates.getFirst().target)).location());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dry_midlands", "primordial_ocean"})
    void biomePlacedFeaturesUseTheirActualHeightsCountsAndNativeGeometry(String biomeName) {
        Holder<Biome> biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(ResourceKey.create(Registries.BIOME,
            ResourceLocation.withDefaultNamespace(biomeName)));
        var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(ResourceKey.create(Registries.NOISE_SETTINGS,
            ResourceLocation.withDefaultNamespace("primordial_caves")));
        var generator = new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), settings);
        Set<ConfiguredFeature<?, ?>> affected = FEATURES.stream().map(CustomOreTargetTagsTest::configured).collect(Collectors.toSet());
        List<PlacedFeature> placed = biome.value().getGenerationSettings().features().stream().flatMap(HolderSet::stream)
            .map(Holder::value).filter(p -> affected.contains(p.feature().value())).toList();
        Map<Long, Integer> first = runBiomeFeatures(biome, generator, placed);
        assertFalse(first.isEmpty(), "Native biome-linked ore routes must write ore into fresh eligible host sections");
        assertEquals(first, runBiomeFeatures(biome, generator, placed));
        for (long position : first.keySet()) {
            int y = BlockPos.of(position).getY();
            assertTrue(y >= -64 && y < 320, "placement must remain inside the dimension height");
        }
    }

    private static Map<Long, Integer> runBiomeFeatures(Holder<Biome> biome, NoiseBasedChunkGenerator generator, List<PlacedFeature> placed) {
        var world = new BiomeOreWorld(biome);
        RandomSource random = new WorldgenRandom(new XoroshiroRandomSource(781));
        for (PlacedFeature feature : placed) feature.placeWithBiomeCheck(world, generator, random, BlockPos.ZERO);
        return world.terrain.writtenBlocks();
    }

    private static RandomSource constantFloat(float value) {
        return new LegacyRandomSource(0) { @Override public float nextFloat() { return value; } };
    }

    private static OreTestWorld solidWorld(Block host) {
        var world = new OreTestWorld(-64, 128, false, false);
        var chunk = world.chunk(0, 0);
        if (host != Blocks.STONE) {
            for (var section : chunk.getSections()) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                section.setBlockState(x, y, z, host.defaultBlockState(), false);
        }
        world.recording = true;
        return world;
    }

    private static final class BiomeOreWorld extends UnsupportedOreWorld {
        final OreTestWorld terrain = new OreTestWorld(-64, 384, false, true);
        final Holder<Biome> biome;
        BiomeOreWorld(Holder<Biome> biome) { this.biome = biome; }
        @Override public int getMinY() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public int getHeight(Heightmap.Types type, int x, int z) { return terrain.level.getHeight(type, x, z); }
        @Override public boolean ensureCanWrite(BlockPos pos) { return terrain.level.ensureCanWrite(pos); }
        @Override public boolean isOutsideBuildHeight(int y) { return terrain.level.isOutsideBuildHeight(y); }
        @Override public ChunkAccess getChunk(int x, int z) { return terrain.level.getChunk(x, z); }
        @Override public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) { return getChunk(x, z); }
        @Override public Holder<Biome> getBiome(BlockPos pos) { return biome; }
    }
}
