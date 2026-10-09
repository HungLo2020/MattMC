package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import net.minecraft.core.IdMapper;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class NativePaletteHistogramTest {
    record Occurrence(Object value, int count) {}

    @BeforeAll
    static void bootstrap() {
        NativePalettePackingTest.bootstrap();
    }

    static <T> List<Occurrence> capture(PalettedContainer<T> source, boolean nativeMode) {
        var list = new ArrayList<Occurrence>();
        PalettedContainer.CountConsumer<T> sink =
                (state, count) -> list.add(new Occurrence(state, count));
        if (nativeMode) source.count(sink);
        else JavaPaletteHistogram.count(source, sink);
        return list;
    }

    static void equal(List<Occurrence> a, List<Occurrence> b) {
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertSame(a.get(i).value(), b.get(i).value());
            assertEquals(a.get(i).count(), b.get(i).count());
        }
    }

    static <T> void compare(PalettedContainer<T> source) {
        var storage = source.dataForNativeScan().storage();
        var before = storage.getRaw().clone();
        var reference = new Int2IntOpenHashMap();
        storage.getAll(i -> reference.addTo(i, 1));
        var records = new ArrayList<Long>();
        reference
                .int2IntEntrySet()
                .forEach(
                        e ->
                                records.add(
                                        (long) e.getIntValue() << 32
                                                | Integer.toUnsignedLong(e.getIntKey())));
        try (var counts = NativePaletteHistogram.scan(storage)) {
            assertNotNull(counts);
            assertEquals(records.size(), counts.size);
            for (int i = 0; i < counts.size; i++)
                assertEquals(records.get(i).longValue(), counts.entry(i));
        }
        equal(capture(source, false), capture(source, true));
        assertArrayEquals(before, storage.getRaw());
    }

    @Test
    void seededPalettesAndEveryResizeBoundary() {
        int cases = 0;
        for (int n : PalettePackingFixtures.CARDINALITIES)
            for (var pattern : List.of("random", "cycle", "runs"))
                for (int seed = 0; seed < 16; seed++) {
                    compare(PalettePackingFixtures.states(n, seed, pattern));
                    cases++;
                }
        for (int n :
                new int[] {
                    23, 24, 25, 26, 47, 48, 49, 95, 96, 97, 191, 192, 193, 383, 384, 385, 767, 768,
                    769, 1535, 1536, 1537, 3071, 3072, 3073
                })
            for (int seed = 0; seed < 4; seed++) {
                compare(PalettePackingFixtures.states(n, seed, "cycle"));
                cases++;
            }
        System.out.println(
                "HISTOGRAM_PARITY fixtures=" + cases + " decoded_entries=" + (cases * 4096));
    }

    @Test
    void zeroInsertionAtEachResizeBoundary() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var config = owner.getConfigurationForBitCount(15);
        int cases = 0;
        for (int n : new int[] {25, 49, 97, 193, 385, 769, 1537, 3073, 4096})
            for (int zeroAt : new int[] {0, 23, 24, 25, 48, 96, 192, 384, 768, 1536, 3072}) {
                if (zeroAt >= n) continue;
                int[] ids = new int[4096];
                for (int i = 0; i < ids.length; i++) {
                    int position = i % n;
                    ids[i] = position == zeroAt ? 0 : position < zeroAt ? position + 1 : position;
                }
                compare(
                        PalettePackingFixtures.custom(
                                owner,
                                config,
                                new SimpleBitStorage(config.bitsInMemory(), 4096, ids),
                                owner.globalPalette()));
                cases++;
            }
        System.out.println("HISTOGRAM_ZERO_PARITY fixtures=" + cases);
    }

    @Test
    void customPaletteAndRegistryCallbacksKeepTheirExactTrace() {
        var base = PalettePackingFixtures.states(64, 9, "cycle");
        var owner = PalettePackingFixtures.owner(base);
        var trace = new ArrayList<String>();
        var states = new ArrayList<BlockState>();
        for (int i = 0; i < 64; i++) states.add(base.dataForNativeScan().palette().valueFor(i));
        var custom =
                new HashMapPalette<BlockState>(6, states) {
                    @Override
                    public int getSize() {
                        trace.add("size");
                        return super.getSize();
                    }

                    @Override
                    public BlockState valueFor(int id) {
                        trace.add("id=" + id);
                        return super.valueFor(id);
                    }
                };
        var source =
                PalettePackingFixtures.custom(
                        owner,
                        Strategy.SIX_BITS_HASHMAP,
                        base.dataForNativeScan().storage().copy(),
                        custom);
        capture(source, false);
        var expected = List.copyOf(trace);
        trace.clear();
        capture(source, true);
        assertEquals(expected, trace);
        var backing = new IdMapper<BlockState>();
        for (var state : states) backing.add(state);
        var registry =
                new net.minecraft.core.IdMap<BlockState>() {
                    @Override
                    public int size() {
                        trace.add("registry-size");
                        return backing.size();
                    }

                    @Override
                    public BlockState byId(int id) {
                        trace.add("registry-id=" + id);
                        return backing.byId(id);
                    }

                    @Override
                    public int getId(BlockState state) {
                        return backing.getId(state);
                    }

                    @Override
                    public Iterator<BlockState> iterator() {
                        return backing.iterator();
                    }
                };
        var globalOwner = Strategy.createForBlockStates(registry);
        var global =
                PalettePackingFixtures.custom(
                        globalOwner,
                        globalOwner.getConfigurationForBitCount(9),
                        new SimpleBitStorage(
                                6, 4096, base.dataForNativeScan().storage().getRaw().clone()),
                        globalOwner.globalPalette());
        trace.clear();
        capture(global, false);
        expected = List.copyOf(trace);
        trace.clear();
        capture(global, true);
        assertEquals(expected, trace);
    }

    @Test
    void everySourceWidthMaximumIdAndNonzeroPadding() {
        for (int bits = 1; bits <= 16; bits++) {
            var strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
            int[] ids = new int[4096];
            for (int i = 0; i < 4096; i++) ids[i] = (i * 1977) & ((1 << bits) - 1);
            var storage = new SimpleBitStorage(bits, 4096);
            Arrays.fill(storage.getRaw(), -1L);
            for (int i = 0; i < 4096; i++) storage.set(i, ids[i]);
            // Test records directly: high IDs may deliberately be absent from this palette.
            var reference = new Int2IntOpenHashMap();
            storage.getAll(i -> reference.addTo(i, 1));
            var records = new ArrayList<Long>();
            reference
                    .int2IntEntrySet()
                    .forEach(
                            e ->
                                    records.add(
                                            (long) e.getIntValue() << 32
                                                    | Integer.toUnsignedLong(e.getIntKey())));
            try (var counts = NativePaletteHistogram.scan(storage)) {
                assertNotNull(counts);
                assertEquals(records.size(), counts.size);
                for (int i = 0; i < counts.size; i++)
                    assertEquals(records.get(i).longValue(), counts.entry(i));
            }
            var uniform = new SimpleBitStorage(bits, 4096);
            Arrays.fill(uniform.getRaw(), -1L);
            try (var counts = NativePaletteHistogram.scan(uniform)) {
                assertEquals(1, counts.size);
                assertEquals((4096L << 32) | ((1 << bits) - 1), counts.entry(0));
            }
        }
    }

    @Test
    void aliasNullEntriesAndZeroStorageKeepSeparateIdCallbacks() {
        Object a = new String("same"), b = new String("same");
        var registry = new IdMapper<Object>();
        registry.add(a);
        registry.add(b);
        var strategy = Strategy.createForBlockStates(registry);
        var palette = LinearPalette.create(4, Arrays.asList(a, b, a, null));
        var ids = new int[4096];
        for (int i = 0; i < 4096; i++) ids[i] = i % 4;
        compare(
                PalettePackingFixtures.custom(
                        strategy,
                        Strategy.FOUR_BITS_LINEAR,
                        new SimpleBitStorage(4, 4096, ids),
                        palette));
        var zero =
                PalettePackingFixtures.custom(
                        strategy, Strategy.FOUR_BITS_LINEAR, new ZeroBitStorage(4096), palette);
        compare(zero);
    }

    @Test
    void allRegisteredGlobalStatesAndStalePalettes() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var config = owner.getConfigurationForBitCount(15);
        int total = Block.BLOCK_STATE_REGISTRY.size();
        for (int start = 0; start < total; start += 4096) {
            var ids = new int[4096];
            for (int i = 0; i < 4096; i++) ids[i] = Math.min(start + i, total - 1);
            compare(
                    PalettePackingFixtures.custom(
                            owner,
                            config,
                            new SimpleBitStorage(config.bitsInMemory(), 4096, ids),
                            owner.globalPalette()));
        }
        for (int n : PalettePackingFixtures.CARDINALITIES) {
            var source = PalettePackingFixtures.states(n, 913, "cycle");
            var value = source.get(0, 0, 0);
            for (int i = 0; i < 4096; i++)
                source.getAndSetUnchecked(i & 15, i >> 8, (i >> 4) & 15, value);
            compare(source);
        }
        System.out.println("HISTOGRAM_REGISTRY_PARITY states=" + total);
    }

    static long rawCounts(LevelChunkSection section) {
        return section.packedSectionCounts();
    }

    @Test
    void savedSectionsAndIncrementalChangesPreserveAllCounters() {
        int cases = 0;
        for (var record : PalettePackingFixtures.records())
            for (var source : PalettePackingFixtures.recorded(record)) {
                compare(source);
                var nativeSection = new LevelChunkSection(source.copy(), null);
                var javaSection = new JavaSectionCounts(source.copy());
                assertEquals(javaSection.rawCounts(), rawCounts(nativeSection));
                for (var state :
                        List.of(
                                Blocks.WATER.defaultBlockState(),
                                Blocks.LAVA.defaultBlockState(),
                                Blocks.OAK_LEAVES.defaultBlockState(),
                                Blocks.AIR.defaultBlockState())) {
                    assertSame(
                            javaSection.setBlockState(0, 0, 0, state),
                            nativeSection.setBlockState(0, 0, 0, state));
                    assertEquals(javaSection.rawCounts(), rawCounts(nativeSection));
                    javaSection.recalcBlockCounts();
                    nativeSection.recalcBlockCounts();
                    assertEquals(javaSection.rawCounts(), rawCounts(nativeSection));
                }
                cases++;
            }
        System.out.println("HISTOGRAM_SAVED_PARITY sections=" + cases + " chunks=16");
    }

    @Test
    void everyStateUniformAndMixedCounterSemantics() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        for (int id = 0; id < Block.BLOCK_STATE_REGISTRY.size(); id++) {
            var state = Block.BLOCK_STATE_REGISTRY.byId(id);
            var source = new PalettedContainer<>(state, owner);
            // Two palette entries exercise the histogram even for one occupied ID.
            source.getAndSetUnchecked(
                    0,
                    0,
                    0,
                    state == Blocks.AIR.defaultBlockState()
                            ? Blocks.STONE.defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
            source.getAndSetUnchecked(0, 0, 0, state);
            var actual = new LevelChunkSection(source, null);
            var expected = new JavaSectionCounts(source);
            assertEquals(expected.rawCounts(), rawCounts(actual));
        }
        var water = new PalettedContainer<>(Blocks.WATER.defaultBlockState(), owner);
        var section = new LevelChunkSection(water, null);
        assertEquals(
                8192,
                rawCounts(section)
                        & 65535); // Original recount counts both nonair blocks and nonempty fluids.
    }

    @Test
    void exceptionPrefixesAndReentrantCallbacksReleaseScratch() {
        var source = PalettePackingFixtures.states(64, 91, "cycle");
        var expected = capture(source, false);
        for (int stop : new int[] {0, 1, expected.size() - 1})
            for (boolean mode : new boolean[] {false, true}) {
                var seen = new ArrayList<Occurrence>();
                PalettedContainer.CountConsumer<BlockState> sink =
                        (state, n) -> {
                            seen.add(new Occurrence(state, n));
                            if (seen.size() == stop + 1)
                                throw new IllegalArgumentException("stop=" + stop);
                        };
                var failure =
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> {
                                    if (mode) source.count(sink);
                                    else JavaPaletteHistogram.count(source, sink);
                                });
                assertEquals("stop=" + stop, failure.getMessage());
                equal(expected.subList(0, stop + 1), seen);
            }
        var outer = new ArrayList<Occurrence>();
        source.count(
                (state, n) -> {
                    equal(expected, capture(source, true));
                    outer.add(new Occurrence(state, n));
                });
        equal(expected, outer);
        compare(source);
        // Mutations during the first callback change subsequent palette reads, just as in Java.
        var traces = new ArrayList<List<Occurrence>>();
        for (boolean mode : new boolean[] {false, true}) {
            var copy = source.copy();
            var trace = new ArrayList<Occurrence>();
            PalettedContainer.CountConsumer<BlockState> sink =
                    (state, n) -> {
                        trace.add(new Occurrence(state, n));
                        if (trace.size() == 1)
                            for (int i = 0; i < 4096; i++)
                                copy.getAndSetUnchecked(
                                        i & 15,
                                        i >> 8,
                                        (i >> 4) & 15,
                                        Block.BLOCK_STATE_REGISTRY.byId(1000 + i % 257));
                    };
            if (mode) copy.count(sink);
            else JavaPaletteHistogram.count(copy, sink);
            traces.add(trace);
        }
        equal(traces.get(0), traces.get(1));
    }

    @Test
    void malformedIdsPreserveExceptionsAndCounterPublication() {
        var source = PalettePackingFixtures.states(2, 0, "cycle");
        var section = new LevelChunkSection(source, null);
        long before = rawCounts(section);
        // Deliberately leave the native owner before injecting an invalid Java word.
        source.dataForCompatibilityMutation().storage().getRaw()[0] |= 15;
        var a = new ArrayList<Occurrence>();
        var b = new ArrayList<Occurrence>();
        var first =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                JavaPaletteHistogram.count(
                                        source, (s, n) -> a.add(new Occurrence(s, n))));
        var second =
                assertThrows(
                        RuntimeException.class,
                        () -> source.count((s, n) -> b.add(new Occurrence(s, n))));
        assertEquals(first.getClass(), second.getClass());
        assertEquals(first.getMessage(), second.getMessage());
        equal(a, b);
        assertThrows(RuntimeException.class, section::recalcBlockCounts);
        assertEquals(before, rawCounts(section));
        source.dataForNativeScan().storage().set(0, 0);
        compare(source);
        section.recalcBlockCounts();
    }

    @Test
    void customStorageWideFormatsAndSmallContainersUseOriginalPath() {
        var source = PalettePackingFixtures.states(2, 0, "cycle");
        var owner = PalettePackingFixtures.owner(source);
        var seen = new ArrayList<String>();
        var storage =
                new SimpleBitStorage(
                        4, 4096, source.dataForNativeScan().storage().getRaw().clone()) {
                    @Override
                    public int getSize() {
                        seen.add("size");
                        return super.getSize();
                    }

                    @Override
                    public int getBits() {
                        seen.add("bits");
                        return super.getBits();
                    }

                    @Override
                    public void getAll(java.util.function.IntConsumer sink) {
                        seen.add("all");
                        super.getAll(sink);
                    }
                };
        var custom =
                PalettePackingFixtures.custom(
                        owner,
                        Strategy.FOUR_BITS_LINEAR,
                        storage,
                        source.dataForNativeScan().palette());
        capture(custom, false);
        var trace = List.copyOf(seen);
        seen.clear();
        capture(custom, true);
        assertEquals(trace, seen);
        for (int bits : new int[] {17, 31, 32}) {
            var wide =
                    PalettePackingFixtures.custom(
                            owner,
                            Strategy.FOUR_BITS_LINEAR,
                            new SimpleBitStorage(bits, 4096),
                            source.dataForNativeScan().palette());
            assertNull(NativePaletteHistogram.scan(wide.dataForNativeScan().storage()));
            equal(capture(wide, false), capture(wide, true));
        }
        var map = new IdMapper<Object>();
        Object first = new Object(), second = new Object();
        map.add(first);
        map.add(second);
        var biomes = new PalettedContainer<>(first, Strategy.createForBiomes(map));
        biomes.getAndSetUnchecked(0, 0, 0, second);
        assertNull(NativePaletteHistogram.scan(biomes.dataForNativeScan().storage()));
        equal(capture(biomes, false), capture(biomes, true));
    }

    @Test
    void independentReadersDuringGc() throws Exception {
        var running = new java.util.concurrent.atomic.AtomicBoolean(true);
        var gc =
                new Thread(
                        () -> {
                            while (running.get()) {
                                System.gc();
                                Thread.yield();
                            }
                        });
        gc.setDaemon(true);
        gc.start();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<Future<?>>();
            for (int t = 0; t < 4; t++) {
                final int seed = t;
                jobs.add(
                        executor.submit(
                                () -> {
                                    for (int i = 0; i < 24; i++)
                                        compare(
                                                PalettePackingFixtures.states(
                                                        257 + seed, i, "random"));
                                }));
            }
            for (var job : jobs) job.get();
        } finally {
            running.set(false);
            gc.join(10000);
        }
        assertFalse(gc.isAlive());
    }
}
