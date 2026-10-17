package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import net.minecraft.core.IdMapper;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeBlockSectionSnapshotTest {
    @BeforeAll static void bootstrap() { NativePalettePackingTest.bootstrap(); }

    private static void compare(PalettedContainer<BlockState> source) {
        try (var arena = Arena.ofConfined()) {
            var nativeStates = NativeBlockSectionSnapshot.capture(source, arena);
            assertNotNull(nativeStates);
            for (int i = 0; i < 4096; i++) {
                var state = source.get(i & 15, i >> 8, i >> 4 & 15);
                assertEquals(Block.getId(state), nativeStates.stateId(i));
                assertSame(state, nativeStates.state(i));
            }
        }
    }

    @Test void everyPaletteBoundaryAndPattern() {
        for (int n : PalettePackingFixtures.CARDINALITIES)
            for (var pattern : List.of("cycle", "runs", "random"))
                compare(PalettePackingFixtures.states(n, 67, pattern));
    }

    @Test void realSavedSectionsAcrossDimensions() {
        int count = 0;
        for (var chunk : PalettePackingFixtures.records()) {
            for (var section : PalettePackingFixtures.recorded(chunk)) { compare(section); count++; }
        }
        assertEquals(324, count);
    }

    @Test void retainedSnapshotSurvivesWritesResizesAndSourceCollection() {
        var source = PalettePackingFixtures.states(16, 7, "cycle");
        try (var arena = Arena.ofConfined()) {
            var capture = NativeBlockSectionSnapshot.capture(source, arena);
            assertNotNull(capture);
            try {
                var field = NativeBlockSectionSnapshot.class.getDeclaredField("states");
                field.setAccessible(true);
                var view = (java.lang.foreign.MemorySegment) field.get(capture);
                assertTrue(view.isReadOnly());
                assertThrows(IllegalArgumentException.class, () -> view.setAtIndex(ValueLayout.JAVA_SHORT, 0, (short) 1));
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            int[] before = new int[4096];
            for (int i = 0; i < 4096; i++) before[i] = capture.stateId(i);
            for (int i = 0; i < 4096; i++) {
                source.getAndSetUnchecked(i & 15, i >> 8, i >> 4 & 15, Block.BLOCK_STATE_REGISTRY.byId(i));
            }
            source = null;
            System.gc();
            for (int i = 0; i < 4096; i++) assertEquals(before[i], capture.stateId(i));
            assertThrows(IndexOutOfBoundsException.class, () -> capture.state(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> capture.state(4096));
        }
    }

    @Test void bulkNeighbourhoodMatchesAllFacesEdgesAndCorners() {
        try (var arena = Arena.ofConfined()) {
            var captures = new NativeBlockSectionSnapshot[27];
            var sources = new ArrayList<PalettedContainer<BlockState>>();
            for (int i = 0; i < 27; i++) {
                var source = PalettePackingFixtures.states(512, i, "random");
                sources.add(source);
                captures[i] = NativeBlockSectionSnapshot.capture(source, arena);
                assertNotNull(captures[i]);
            }
            var output = arena.allocate(18 * 18 * 18 * 4, 4);
            NativeBlockSectionSnapshot.writePadded(captures, output);
            for (int y = 0; y < 18; y++) for (int z = 0; z < 18; z++) for (int x = 0; x < 18; x++) {
                int rx = x + 15, ry = y + 15, rz = z + 15;
                int section = (ry / 16 * 3 + rz / 16) * 3 + rx / 16;
                int expected = Block.getId(sources.get(section).get(rx & 15, ry & 15, rz & 15));
                assertEquals(expected, output.getAtIndex(ValueLayout.JAVA_INT, (y * 18 + z) * 18 + x));
            }
            java.util.Arrays.fill(captures, null);
            NativeBlockSectionSnapshot.writePadded(captures, output);
            for (int i = 0; i < 18 * 18 * 18; i++) assertEquals(Block.getId(Blocks.AIR.defaultBlockState()), output.getAtIndex(ValueLayout.JAVA_INT, i));
        }
    }

    @Test void customCallbacksAndRegistriesAreNotInspected() {
        var source = PalettePackingFixtures.states(32, 1, "cycle");
        var trace = new ArrayList<String>();
        var values = new ArrayList<BlockState>();
        for (int i = 0; i < source.dataForNativeScan().palette().getSize(); i++) values.add(source.dataForNativeScan().palette().valueFor(i));
        var palette = new HashMapPalette<BlockState>(5, values) {
            @Override public int getSize() { trace.add("size"); return super.getSize(); }
            @Override public BlockState valueFor(int id) { trace.add("value"); return super.valueFor(id); }
        };
        var custom = PalettePackingFixtures.custom(PalettePackingFixtures.owner(source), Strategy.FIVE_BITS_HASHMAP,
                source.dataForNativeScan().storage(), palette);
        assertNull(NativeBlockSectionSnapshot.capture(custom));
        assertTrue(trace.isEmpty());
        var otherRegistry = new IdMapper<BlockState>(); otherRegistry.add(Blocks.STONE.defaultBlockState());
        assertNull(NativeBlockSectionSnapshot.capture(new PalettedContainer<>(Blocks.STONE.defaultBlockState(), Strategy.createForBlockStates(otherRegistry))));
    }

    @Test void aliasesAndUnusedNullEntriesPreserveOriginalObjects() {
        var state = Blocks.STONE.defaultBlockState();
        var strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var palette = LinearPalette.<BlockState>create(4, List.of(state, state));
        var values = new int[4096]; for (int i = 0; i < values.length; i++) values[i] = i % 2;
        compare(PalettePackingFixtures.custom(strategy, Strategy.FOUR_BITS_LINEAR, new SimpleBitStorage(4, 4096, values), palette));
        var invalid = LinearPalette.<BlockState>create(4, java.util.Arrays.asList(state, null));
        var container = PalettePackingFixtures.custom(strategy, Strategy.FOUR_BITS_LINEAR, new SimpleBitStorage(4, 4096), invalid);
        assertNull(NativeBlockSectionSnapshot.capture(container));
        assertSame(state, container.get(0, 0, 0));
    }

    @Test void readOnlyLifetimeAndConcurrentGcReaders() throws Exception {
        NativeBlockSectionSnapshot expired;
        try (var arena = Arena.ofConfined()) {
            expired = NativeBlockSectionSnapshot.capture(PalettePackingFixtures.states(1, 0, "cycle"), arena);
            assertNotNull(expired);
        }
        assertThrows(IllegalStateException.class, () -> expired.state(0));
        var closed = Arena.ofConfined(); closed.close();
        assertThrows(IllegalStateException.class, () -> NativeBlockSectionSnapshot.capture(PalettePackingFixtures.states(1, 0, "cycle"), closed));
        try (var outputArena = Arena.ofConfined()) {
            var captures = new NativeBlockSectionSnapshot[27]; captures[13] = expired;
            var output = outputArena.allocate(18 * 18 * 18 * 4, 4);
            assertThrows(IllegalStateException.class, () -> NativeBlockSectionSnapshot.writePadded(captures, output));
        }
        try (var arena = Arena.ofShared(); var pool = Executors.newFixedThreadPool(4)) {
            var capture = NativeBlockSectionSnapshot.capture(PalettePackingFixtures.states(256, 4, "cycle"), arena);
            assertNotNull(capture);
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int t = 0; t < 4; t++) futures.add(pool.submit(() -> {
                for (int r = 0; r < 20; r++) for (int i = 0; i < 4096; i++) assertSame(Block.BLOCK_STATE_REGISTRY.byId(capture.stateId(i)), capture.state(i));
            }));
            System.gc();
            for (var future : futures) future.get();
        }
    }
}
