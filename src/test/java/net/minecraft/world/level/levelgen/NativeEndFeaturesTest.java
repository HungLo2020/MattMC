package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.NativeDecorationRegion;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature.EndSpike;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual Java feature methods versus the existing bound-worldgen Rust route.
 * Mocked server services do not certify live ServerLevel behavior. */
class NativeEndFeaturesTest {
    static PalettedContainerFactory factory;
    static RandomState randomState;

    @BeforeAll static void load() {
        NativeNoiseFillTest.load();
        factory = PalettedContainerFactory.create(NativeNoiseFillTest.registries);
        randomState = RandomState.create(NativeNoiseFillTest.registries, NoiseGeneratorSettings.END, 7);
    }

    @AfterAll static void close() { NativeNoiseFillTest.close(); }

    static final class Fixture implements AutoCloseable {
        final ProtoChunk[] chunks = new ProtoChunk[9];
        final ChunkGenerator generator = mock(ChunkGenerator.class);
        final WorldGenRegion level;
        NativeDecorationRegion binding;

        Fixture() {
            when(generator.getMinY()).thenReturn(0);
            when(generator.getGenDepth()).thenReturn(128);
            when(generator.getBiomeGenerationSettings(any())).thenReturn(net.minecraft.world.level.biome.BiomeGenerationSettings.EMPTY);
            when(generator.getBiomeSource()).thenReturn(new net.minecraft.world.level.biome.FixedBiomeSource(
                NativeNoiseFillTest.registries.lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.THE_END)));
            ServerLevel server = mock(ServerLevel.class);
            ServerChunkCache source = mock(ServerChunkCache.class);
            when(server.getChunkSource()).thenReturn(source);
            when(source.randomState()).thenReturn(randomState);
            when(server.getSeed()).thenReturn(7L);
            when(server.getMinY()).thenReturn(0);
            when(server.getHeight()).thenReturn(128);
            when(server.registryAccess()).thenReturn(NativeNoiseFillTest.registries);
            // The null-crystal branch must not consume a yaw draw. Real crystals
            // and live effects are covered by the separate server fixture.
            when(server.enabledFeatures()).thenReturn(FeatureFlagSet.of());
            var cache = StaticCache2D.create(0, 0, 1, (int x, int z) -> {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY,
                    LevelHeightAccessor.create(0, 128), factory, null);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                chunks[(z + 1) * 3 + x + 1] = chunk;
                GenerationChunkHolder holder = mock(GenerationChunkHolder.class);
                when(holder.getChunkIfPresentUnchecked(any())).thenReturn(chunk);
                return holder;
            });
            level = spy(new WorldGenRegion(server, cache, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), chunks[4]));
        }

        void nativePlace(NativeDecoration.Programs programs, PlacedFeature feature, BlockPos origin, WorldgenRandom random) {
            binding = NativeDecoration.bind(level, chunks[4], generator);
            assertNotNull(binding, "the fixture must bind real native chunk storage");
            assertEquals(Boolean.TRUE, NativeDecoration.placeWithRandom(programs, binding, generator, level, random, feature, origin),
                "the feature must execute natively rather than silently fall back");
        }

        List<String> snapshot() {
            List<String> rows = new ArrayList<>();
            for (var chunk : chunks) {
                for (var section : chunk.getSections()) rows.add(NativeNoiseFillTest.saved(section));
                for (var type : Heightmap.Types.values()) rows.add(type + ":" + (chunk.hasPrimedHeightmap(type)
                    ? Arrays.toString(chunk.getOrCreateHeightmapUnprimed(type).getRawData()) : "-"));
                rows.add("marks:" + Arrays.deepToString(chunk.getPostProcessing()));
                for (var pos : chunk.getBlockEntitiesPos().stream().sorted().toList())
                    rows.add("be:" + pos + ":" + chunk.getBlockEntityNbtForSaving(pos, level.registryAccess()));
            }
            return rows;
        }

        public void close() { if (binding != null) NativeDecoration.release(binding); }
    }

    static WorldgenRandom random() { return new WorldgenRandom(new XoroshiroRandomSource(42)); }
    static PlacedFeature placed(ConfiguredFeature<?, ?> feature) { return new PlacedFeature(Holder.direct(feature), List.of()); }

    static void compare(PlacedFeature feature, BlockPos origin, String context) {
        try (var java = new Fixture(); var nativeFixture = new Fixture()) {
            var expectedRandom = random(); var actualRandom = random();
            NativeEndFeatures.setEnabled(false);
            try { assertTrue(feature.place(java.level, java.generator, expectedRandom, origin)); }
            finally { NativeEndFeatures.setEnabled(true); }
            nativeFixture.nativePlace(new NativeDecoration.Programs(), feature, origin, actualRandom);
            assertEquals(java.snapshot(), nativeFixture.snapshot(), context);
            assertEquals(expectedRandom.nextLong(), actualRandom.nextLong(), context + " final caller random");
        }
    }

    @Test void existingKernelsMatchJavaForActualWritesAndGatewayExit() {
        for (boolean guarded : new boolean[]{false, true}) {
            for (int radius : new int[]{0, 2, 5}) {
                var config = new SpikeConfiguration(false, List.of(new EndSpike(8, 8, radius, 80, guarded)), (BlockPos)null);
                compare(placed(new ConfiguredFeature<>(Feature.END_SPIKE, config)), new BlockPos(0, 70, 0), "spike " + radius + "/" + guarded);
            }
        }
        compare(placed(new ConfiguredFeature<>(Feature.END_GATEWAY, EndGatewayConfiguration.knownExit(new BlockPos(100, 50, -100), true))),
            new BlockPos(8, 70, 8), "gateway with exact exit");
        compare(placed(new ConfiguredFeature<>(Feature.END_GATEWAY, EndGatewayConfiguration.delayedExitSearch())),
            new BlockPos(15, 70, 15), "gateway across chunk edge");
        compare(placed(new ConfiguredFeature<>(Feature.END_PLATFORM, NoneFeatureConfiguration.INSTANCE)), new BlockPos(15, 70, 15), "platform across edge");
    }

    @Test void cachedSpikeProgramSeesMutableConfigurationOnNextPlacement() {
        List<EndSpike> spikes = new ArrayList<>(List.of(new EndSpike(8, 8, 1, 75, false)));
        var feature = placed(new ConfiguredFeature<>(Feature.END_SPIKE, new SpikeConfiguration(false, spikes, (BlockPos)null)));
        var programs = new NativeDecoration.Programs();
        try (var first = new Fixture()) { first.nativePlace(programs, feature, BlockPos.ZERO, random()); }
        spikes.set(0, new EndSpike(8, 8, 2, 90, true));
        try (var java = new Fixture(); var actual = new Fixture()) {
            NativeEndFeatures.setEnabled(false);
            try { assertTrue(feature.place(java.level, java.generator, random(), BlockPos.ZERO)); }
            finally { NativeEndFeatures.setEnabled(true); }
            actual.nativePlace(programs, feature, BlockPos.ZERO, random());
            assertEquals(java.snapshot(), actual.snapshot(), "cached native program must observe the retained mutable spike list");
        }
    }

    @Test void customNegativeRadiusKeepsJavasNormalizedBounds() {
        compare(placed(new ConfiguredFeature<>(Feature.END_SPIKE, new SpikeConfiguration(false,
            List.of(new EndSpike(8, 8, -2, 80, false)), (BlockPos)null))), BlockPos.ZERO, "negative radius");
    }

    static BlockPos retainedBodyCursor(boolean nativeRoute, int height, int radius, boolean guarded) {
        try (var fixture = new Fixture()) {
            BlockPos[] retained = {null};
            doAnswer(call -> {
                var state = call.getArgument(1, net.minecraft.world.level.block.state.BlockState.class);
                if (retained[0] == null && state.is(Blocks.OBSIDIAN)) retained[0] = call.getArgument(0);
                return call.callRealMethod();
            }).when(fixture.level).setBlock(any(), any(), anyInt(), anyInt());
            NativeEndFeatures.setEnabled(nativeRoute);
            try {
                assertTrue(Feature.END_SPIKE.place(new SpikeConfiguration(false,
                    List.of(new EndSpike(8, 8, radius, height, guarded)), (BlockPos)null),
                    fixture.level, fixture.generator, random(), BlockPos.ZERO));
            } finally { NativeEndFeatures.setEnabled(true); }
            assertInstanceOf(BlockPos.MutableBlockPos.class, retained[0], "retain an actual body-write cursor");
            return retained[0].immutable();
        }
    }

    @Test void retainedBodyCursorAdvancesThroughSkippedTailPositions() {
        for (int height : new int[]{10, 55, 56, 80}) {
            for (int radius : new int[]{-2, 0, 2}) {
                for (boolean guarded : new boolean[]{false, true}) {
                    var expected = retainedBodyCursor(false, height, radius, guarded);
                    assertEquals(new BlockPos(8 + Math.abs(radius), height + 10, 8 + Math.abs(radius)), expected,
                        "Java iterator's final coordinate");
                    assertEquals(expected, retainedBodyCursor(true, height, radius, guarded),
                        "retained cursor height=" + height + " radius=" + radius + " guarded=" + guarded);
                }
            }
        }
    }

    static List<String> descriptorPrefix(boolean nativeRoute, EndSpike spike, boolean narrowHeight) {
        try (var fixture = new Fixture()) {
            // Keep extreme-height probes bounded without changing their Java
            // arithmetic. Throw before storage access at the first real effect.
            if (narrowHeight) doReturn(spike.getHeight() + 10).when(fixture.level).getMinY();
            var trace = new ArrayList<String>();
            var sentinel = new IllegalStateException("descriptor prefix sentinel");
            doAnswer(call -> {
                trace.add(call.getArgument(0) + ":" + call.getArgument(1) + ":" + call.getArgument(2));
                throw sentinel;
            }).when(fixture.level).setBlock(any(), any(), anyInt(), anyInt());
            NativeEndFeatures.setEnabled(nativeRoute);
            var random = random();
            try {
                var failure = assertThrows(RuntimeException.class, () -> Feature.END_SPIKE.place(
                    new SpikeConfiguration(false, List.of(spike), (BlockPos)null),
                    fixture.level, fixture.generator, random, BlockPos.ZERO));
                assertSame(sentinel, failure);
            } finally { NativeEndFeatures.setEnabled(true); }
            assertEquals(1, trace.size(), "reach the first actual write on both routes");
            trace.add("random:" + random.nextLong());
            return trace;
        }
    }

    @Test void shortAndExtremeDescriptorsKeepOriginalFailurePrefixes() {
        for (int height : new int[]{-100, -1, 0, 10, 55, 56, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (int radius : new int[]{-2, 0, 2}) {
                var spike = new EndSpike(8, 8, radius, height, true);
                boolean narrow = height == Integer.MIN_VALUE || height == Integer.MAX_VALUE;
                assertEquals(descriptorPrefix(false, spike, narrow), descriptorPrefix(true, spike, narrow),
                    "descriptor height=" + height + " radius=" + radius);
            }
        }
        for (int radius : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            var spike = new EndSpike(8, 8, radius, 80, true);
            assertEquals(descriptorPrefix(false, spike, false), descriptorPrefix(true, spike, false), "extreme radius=" + radius);
        }
    }

    static BlockPos gatewayRetainedCursor(boolean nativeRoute, boolean mutableOrigin) {
        try (var fixture = new Fixture()) {
            BlockPos[] retained = {null};
            var atCenter = new ArrayList<BlockPos>();
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                var state = call.getArgument(1, net.minecraft.world.level.block.state.BlockState.class);
                if (retained[0] == null && pos instanceof BlockPos.MutableBlockPos) retained[0] = pos;
                if (state.is(Blocks.END_GATEWAY)) {
                    assertNotSame(retained[0], pos, "the gateway write receives an immutable copy");
                    atCenter.add(retained[0].immutable());
                }
                return call.callRealMethod();
            }).when(fixture.level).setBlock(any(), any(), anyInt(), anyInt());
            NativeEndFeatures.setEnabled(nativeRoute);
            try {
                BlockPos origin = mutableOrigin ? new BlockPos.MutableBlockPos(8, 70, 8) : new BlockPos(8, 70, 8);
                assertTrue(Feature.END_GATEWAY.place(EndGatewayConfiguration.knownExit(BlockPos.ZERO, true),
                    fixture.level, fixture.generator, random(), origin));
            } finally { NativeEndFeatures.setEnabled(true); }
            assertEquals(1, atCenter.size());
            return atCenter.getFirst();
        }
    }

    @Test void retainedGatewayCursorAdvancesBeforeItsImmutableCenterWrite() {
        for (boolean mutable : new boolean[]{false, true}) {
            var expected = gatewayRetainedCursor(false, mutable);
            assertEquals(new BlockPos(8, 70, 8), expected);
            assertEquals(expected, gatewayRetainedCursor(true, mutable), "gateway cursor mutableOrigin=" + mutable);
        }
    }

    record Outcome(List<String> trace, List<String> snapshot, String exception, long random) {}

    /** These tapes call the actual Java world methods on both routes, including
     * callback-dependent reads. They supplement the independent Frozen process. */
    static Outcome effects(boolean nativeRoute, int kind, int failAt, boolean reenter, boolean moveCursor) {
        try (var fixture = new Fixture()) {
            var trace = new ArrayList<String>();
            var sentinel = new IllegalStateException("End effect sentinel");
            int[] writes = {0}; boolean[] nested = {false};
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                trace.add("write:" + pos.immutable() + ":" + call.getArgument(1) + ":" + call.getArgument(2) + ":" + pos.getClass().getSimpleName());
                if (++writes[0] == failAt) throw sentinel;
                return call.callRealMethod();
            }).when(fixture.level).setBlock(any(), any(), anyInt(), anyInt());
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                trace.add("read:" + pos.immutable() + ":" + pos.getClass().getSimpleName());
                Object result = call.callRealMethod();
                if (moveCursor && pos instanceof BlockPos.MutableBlockPos cursor) cursor.move(0, 1, 0);
                return result;
            }).when(fixture.level).getBlockState(any());
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                trace.add("be:" + pos.immutable());
                if (reenter && !nested[0]) {
                    nested[0] = true;
                    net.minecraft.world.level.levelgen.feature.EndPlatformFeature.createEndPlatform(fixture.level, new BlockPos(-8, 100, -8), false);
                }
                return call.callRealMethod();
            }).when(fixture.level).getBlockEntity(any());
            NativeEndFeatures.setEnabled(nativeRoute);
            var random = net.minecraft.util.RandomSource.create(123);
            String error = "";
            try {
                switch (kind) {
                    case 0 -> Feature.END_SPIKE.place(new SpikeConfiguration(false,
                        List.of(new EndSpike(8, 8, 2, 80, true)), (BlockPos)null), fixture.level, fixture.generator, random, BlockPos.ZERO);
                    case 1 -> Feature.END_GATEWAY.place(EndGatewayConfiguration.knownExit(BlockPos.ZERO, true),
                        fixture.level, fixture.generator, random, new BlockPos(8, 70, 8));
                    case 2 -> net.minecraft.world.level.levelgen.feature.EndPlatformFeature.createEndPlatform(fixture.level, new BlockPos(8, 70, 8), false);
                    default -> throw new AssertionError(kind);
                }
            } catch (RuntimeException failure) {
                assertSame(sentinel, failure, "the exact thrown effect must escape without wrapping");
                error = failure.getClass().getName() + ":" + failure.getMessage();
            } finally { NativeEndFeatures.setEnabled(true); }
            return new Outcome(trace, fixture.snapshot(), error, random.nextLong());
        }
    }

    @Test void directEffectsKeepOrderedPrefixesIdentityAndReentry() {
        for (int kind = 0; kind < 3; kind++) {
            for (int failure : new int[]{-1, 1, 22, 23, 24, 45, 80}) {
                assertEquals(effects(false, kind, failure, false, false), effects(true, kind, failure, false, false),
                    "ordered effects kind=" + kind + " fail=" + failure);
            }
        }
        assertEquals(effects(false, 1, -1, true, false), effects(true, 1, -1, true, false), "nested native callback");
        assertEquals(effects(false, 2, -1, false, true), effects(true, 2, -1, false, true), "mutated read cursor");
    }

    static Outcome writeCallbacks(boolean nativeRoute, int kind, int action) {
        try (var fixture = new Fixture()) {
            var trace = new ArrayList<String>();
            var spikes = new ArrayList<>(List.of(new EndSpike(8, 8, 2, 80, true)));
            BlockPos origin = action == 3 ? new BlockPos.MutableBlockPos(8, 70, 8)
                : kind == 0 ? BlockPos.ZERO : new BlockPos(8, 70, 8);
            int[] writes = {0}; boolean[] acted = {false};
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                trace.add("write:" + pos.immutable() + ":" + call.getArgument(1) + ":" + call.getArgument(2)
                    + ":" + pos.getClass().getSimpleName() + ":before=" + fixture.level.getBlockState(pos));
                int index = ++writes[0];
                if (index == 7 && !acted[0]) {
                    acted[0] = true;
                    switch (action) {
                        case 0 -> { if (pos instanceof BlockPos.MutableBlockPos cursor) cursor.move(0, 1, 0); }
                        case 1 -> {
                            // Change a later write's actual world input and
                            // reenter while the outer write has not returned.
                            fixture.level.setBlock(pos.offset(1, 0, 0), Blocks.CHEST.defaultBlockState(), 3);
                            net.minecraft.world.level.levelgen.feature.EndPlatformFeature.createEndPlatform(fixture.level,
                                new BlockPos(-8, 100, -8), false);
                        }
                        case 2 -> spikes.add(new EndSpike(9, 9, 1, 90, false));
                        case 3 -> ((BlockPos.MutableBlockPos)origin).move(1, 0, 0);
                        default -> throw new AssertionError(action);
                    }
                }
                // Original features ignore a refused write and still perform
                // every later call. The observer must see those calls too.
                return index % 11 == 0 ? false : call.callRealMethod();
            }).when(fixture.level).setBlock(any(), any(), anyInt(), anyInt());
            NativeEndFeatures.setEnabled(nativeRoute);
            var random = random(); String error = "";
            try {
                if (kind == 0) Feature.END_SPIKE.place(new SpikeConfiguration(false, spikes, (BlockPos)null),
                    fixture.level, fixture.generator, random, origin);
                else Feature.END_GATEWAY.place(EndGatewayConfiguration.knownExit(BlockPos.ZERO, true),
                    fixture.level, fixture.generator, random, origin);
                assertNotEquals(2, action, "mutating the retained descriptor list must fail its iterator");
            } catch (java.util.ConcurrentModificationException expected) {
                assertEquals(2, action);
                error = expected.getClass().getName();
            } finally { NativeEndFeatures.setEnabled(true); }
            assertTrue(acted[0], "the callback scenario must run inside the geometry writes");
            return new Outcome(trace, fixture.snapshot(), error, random.nextLong());
        }
    }

    @Test void writeCallbacksKeepFutureWorldInputsCursorsIterationAndReentry() {
        for (int action : new int[]{0, 1, 2})
            assertEquals(writeCallbacks(false, 0, action), writeCallbacks(true, 0, action), "spike callback " + action);
        for (int action : new int[]{0, 1, 3})
            assertEquals(writeCallbacks(false, 1, action), writeCallbacks(true, 1, action), "gateway callback " + action);
    }

    static Outcome destructionCallbacks(boolean nativeRoute, boolean moveCursor, int failAt) {
        try (var fixture = new Fixture()) {
            var trace = new ArrayList<String>();
            var sentinel = new IllegalStateException("destroy effect sentinel");
            int[] destroyed = {0};
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                trace.add("read:" + pos.immutable());
                Object state = call.callRealMethod();
                if (moveCursor) ((BlockPos.MutableBlockPos)pos).move(0, 1, 0);
                return state;
            }).when(fixture.level).getBlockState(any());
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                trace.add("destroy:" + pos.immutable() + ":" + call.getArgument(1) + ":" + call.getArgument(3));
                if (++destroyed[0] == failAt) throw sentinel;
                if (moveCursor) ((BlockPos.MutableBlockPos)pos).move(0, 0, 1);
                return false;
            }).when(fixture.level).destroyBlock(any(), anyBoolean(), any(), anyInt());
            doAnswer(call -> {
                BlockPos pos = call.getArgument(0);
                trace.add("write:" + pos.immutable() + ":" + call.getArgument(1));
                return call.callRealMethod();
            }).when(fixture.level).setBlock(any(), any(), anyInt(), anyInt());
            NativeEndFeatures.setEnabled(nativeRoute);
            String error = "";
            try {
                net.minecraft.world.level.levelgen.feature.EndPlatformFeature.createEndPlatform(fixture.level, new BlockPos(8, 70, 8), true);
            } catch (RuntimeException failure) {
                assertSame(sentinel, failure);
                error = failure.getMessage();
            } finally { NativeEndFeatures.setEnabled(true); }
            assertTrue(destroyed[0] > 0);
            return new Outcome(trace, fixture.snapshot(), error, 0);
        }
    }

    @Test void platformDestructionKeepsTheReadDestroyWriteCursorAndFailurePrefix() {
        for (boolean move : new boolean[]{false, true}) for (int failure : new int[]{-1, 3})
            assertEquals(destructionCallbacks(false, move, failure), destructionCallbacks(true, move, failure),
                "platform destroy cursor=" + move + " fail=" + failure);
    }

    @Test void retainedNullDescriptorKeepsTheOriginalExceptionMessage() {
        String[] messages = new String[2];
        for (int route = 0; route < 2; route++) {
            try (var fixture = new Fixture()) {
                NativeEndFeatures.setEnabled(route == 1);
                var config = new SpikeConfiguration(false, Arrays.asList((EndSpike)null), (BlockPos)null);
                var failure = assertThrows(NullPointerException.class,
                    () -> Feature.END_SPIKE.place(config, fixture.level, fixture.generator, random(), BlockPos.ZERO));
                messages[route] = failure.getMessage();
            } finally { NativeEndFeatures.setEnabled(true); }
        }
        assertEquals(messages[0], messages[1]);
    }

    @Test void effectThrowablesKeepTheirIdentityEvenWhenChecked() {
        for (boolean nativeRoute : new boolean[]{false, true}) {
            try (var fixture = new Fixture()) {
                var failure = new java.io.IOException("checked effect");
                doAnswer(call -> { throw failure; }).when(fixture.level).setBlock(any(), any(), anyInt(), anyInt());
                NativeEndFeatures.setEnabled(nativeRoute);
                assertSame(failure, assertThrows(Throwable.class, () -> Feature.END_GATEWAY.place(EndGatewayConfiguration.delayedExitSearch(),
                    fixture.level, fixture.generator, random(), new BlockPos(8, 70, 8))));
            } finally { NativeEndFeatures.setEnabled(true); }
        }
    }
}
