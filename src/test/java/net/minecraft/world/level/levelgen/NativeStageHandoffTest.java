package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.NativeBlockSectionSnapshot;
import net.minecraft.world.level.chunk.NativeGenerationSections;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.SectionFingerprint;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeStageHandoffTest {
    @BeforeAll static void load() { NativeNoiseFillTest.load(); }
    @AfterAll static void close() { NativeNoiseFillTest.close(); }
    private static ProtoChunk fresh() {
        var chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(-16, 32),
                PalettedContainerFactory.create(NativeNoiseFillTest.registries), null);
        chunk.setPersistedStatus(ChunkStatus.NOISE);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        return chunk;
    }
    private static Object field(PalettedContainer<?> source, String name) {
        try {
            Field field = PalettedContainer.class.getDeclaredField(name);
            field.setAccessible(true); return field.get(source);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    @Test void importedInputsAreIsolatedAndInstalledOwnersOutliveTheirStage() {
        var chunk = fresh(); var section = chunk.getSection(0); var container = section.getStates();
        section.setBlockState(0, 8, 0, Blocks.STONE.defaultBlockState());
        var untouched = field(chunk.getSection(1).getStates(), "nativeBlocks");
        var position = new BlockPos.MutableBlockPos(0, -8, 0);
        try (var stage = NativeProtoChunk.create(chunk)) {
            assertNotNull(stage);
            // Deliberate mutation after capture proves that the stage owns an
            // isolated input, rather than retaining mutable live words.
            section.setBlockState(0, 8, 0, Blocks.BEDROCK.defaultBlockState());
            assertSame(Blocks.STONE.defaultBlockState(), stage.get(position, -8));
            stage.set(1, -8, 0, Blocks.WATER.defaultBlockState(), false);
            assertEquals(1, NativeGenerationSections.installProto(section, stage.handle, 0));
            assertEquals(0, NativeGenerationSections.installProto(chunk.getSection(1), stage.handle, 1));
            assertSame(untouched, field(chunk.getSection(1).getStates(), "nativeBlocks"));
        }
        System.gc();
        assertSame(container, section.getStates());
        assertNull(field(container, "data"));
        assertSame(Blocks.STONE.defaultBlockState(), section.getBlockState(0, 8, 0));
        assertSame(Blocks.WATER.defaultBlockState(), section.getBlockState(1, 8, 0));
        var capture = NativeBlockSectionSnapshot.capture(container); assertNotNull(capture);
        assertSame(Blocks.WATER.defaultBlockState(), capture.state(8 * 256 + 1));
        section.setBlockState(1, 8, 0, Blocks.OAK_LEAVES.defaultBlockState());
        assertSame(Blocks.WATER.defaultBlockState(), capture.state(8 * 256 + 1));
    }
    @Test void untouchedSectionsKeepTheirNativeOwnerAndNoJavaMirror() {
        var chunk = fresh(); var a = field(chunk.getSection(0).getStates(), "nativeBlocks");
        var b = field(chunk.getSection(1).getStates(), "nativeBlocks");
        try (var stage = NativeProtoChunk.create(chunk)) { assertNotNull(stage); stage.install(); }
        assertSame(a, field(chunk.getSection(0).getStates(), "nativeBlocks"));
        assertSame(b, field(chunk.getSection(1).getStates(), "nativeBlocks"));
        assertNull(field(chunk.getSection(0).getStates(), "data"));
    }
    @Test void aliasCompatibilityKeepsPaletteHistoryNetworkBytesAndCounters() {
        var chunk = fresh(); var section = chunk.getSection(0); var stone = Blocks.STONE.defaultBlockState();
        section.installGenerated(4, List.of(stone, stone), new long[256], 4096, 0, 0);
        var expected = section.copy(); expected.setBlockState(0, 0, 0, Blocks.BEDROCK.defaultBlockState());
        assertEquals(-1, NativeGenerationSections.createProto(chunk,
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).getRawData(),
                chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).getRawData()));
        try (var stage = NativeProtoChunk.create(chunk)) {
            assertNotNull(stage);
            stage.set(0, -16, 0, Blocks.BEDROCK.defaultBlockState(), false);
            assertEquals(2, NativeGenerationSections.installProto(section, stage.handle, 0));
            stage.install();
        }
        assertEquals(SectionFingerprint.of(expected), SectionFingerprint.of(section));
        assertArrayEquals(NativeNoiseFillTest.network(expected), NativeNoiseFillTest.network(section));
        assertEquals(NativeNoiseFillTest.field(expected, "tickingBlockCount"), NativeNoiseFillTest.field(section, "tickingBlockCount"));
        assertEquals(NativeNoiseFillTest.field(expected, "tickingFluidCount"), NativeNoiseFillTest.field(section, "tickingFluidCount"));
    }
    @Test void everyPaletteGrowthBoundaryInstallsTheExactLiveRepresentation() {
        for (int size : new int[]{1, 16, 17, 32, 33, 64, 65, 128, 129, 256, 257, 600}) {
            var chunk = fresh(); var expected = chunk.getSection(0).copy();
            try (var stage = NativeProtoChunk.create(chunk)) {
                assertNotNull(stage);
                for (int i = 0; i < 4096; i++) {
                    var state = Block.BLOCK_STATE_REGISTRY.byId(i % size);
                    int x = i & 15, y = i >> 8, z = i >> 4 & 15;
                    stage.set(x, y - 16, z, state, false);
                    expected.setBlockState(x, y, z, state);
                }
                stage.install();
            }
            assertEquals(SectionFingerprint.of(expected), SectionFingerprint.of(chunk.getSection(0)), "cardinality " + size);
            assertArrayEquals(NativeNoiseFillTest.network(expected), NativeNoiseFillTest.network(chunk.getSection(0)));
            assertNull(field(chunk.getSection(0).getStates(), "data"));
        }
    }
    @Test void noiseInstallKeepsUntouchedCustomChunkAccessorsUncalled() {
        var registries = NativeNoiseFillTest.registries;
        var setting = registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
            .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var config = setting.value(); var noise = config.noiseSettings();
        int generatedSections = noise.height() / 16;
        class CustomChunk extends ProtoChunk {
            CustomChunk() {
                super(new ChunkPos(0, 0), UpgradeData.EMPTY,
                    LevelHeightAccessor.create(noise.minY(), noise.height() + 32),
                    PalettedContainerFactory.create(registries), null);
            }
            @Override public net.minecraft.world.level.chunk.LevelChunkSection getSection(int index) {
                assertTrue(index < generatedSections, "untouched custom section accessor " + index);
                return super.getSection(index);
            }
        }
        var chunk = new CustomChunk();
        var biome = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
            .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var generator = new NoiseBasedChunkGenerator(new net.minecraft.world.level.biome.FixedBiomeSource(biome), setting);
        var random = RandomState.create(config, registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), 7_340_013L);
        var blender = net.minecraft.world.level.levelgen.blending.Blender.empty();
        var noiseChunk = chunk.getOrCreateNoiseChunk(access -> NoiseChunk.forChunk(access, random,
            DensityFunctions.BeardifierMarker.INSTANCE, config,
            new AquiferFluidPicker(config.seaLevel(), config.defaultFluid()), blender));
        // The custom class is admitted by the existing NOISE gate. The two
        // extra sections have no fill result and must never be queried here.
        assertNotNull(NativeNoiseFill.prepare(config, noiseChunk, chunk,
            chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG),
            chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG),
            noise.minY() / noise.getCellHeight(), noise.height() / noise.getCellHeight()));
        generator.fillFromNoise(blender, random, null, chunk).join();
        assertTrue(chunk.getSections()[generatedSections].hasOnlyAir());
        assertTrue(chunk.getSections()[generatedSections + 1].hasOnlyAir());
    }

}
