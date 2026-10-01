package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.IdMapper;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class NativePalettePackingTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static <T> void equal(
            PalettedContainerRO.PackedData<T> a, PalettedContainerRO.PackedData<T> b) {
        assertNotNull(b);
        assertEquals(a.bitsPerEntry(), b.bitsPerEntry());
        assertEquals(a.paletteEntries().size(), b.paletteEntries().size());
        for (int i = 0; i < a.paletteEntries().size(); i++)
            assertSame(a.paletteEntries().get(i), b.paletteEntries().get(i));
        assertEquals(a.storage().isPresent(), b.storage().isPresent());
        if (a.storage().isPresent())
            assertArrayEquals(a.storage().get().toArray(), b.storage().get().toArray());
    }

    static <T> void compare(PalettedContainer<T> source) {
        var target = PalettePackingFixtures.owner(source);
        long[] before = source.dataForNativeScan().storage().getRaw().clone();
        equal(
                JavaPalettePacking.pack(source, target),
                PalettePackingFixtures.nativePack(source, target));
        equal(JavaPalettePacking.pack(source, target), source.pack(target));
        assertArrayEquals(before, source.dataForNativeScan().storage().getRaw());
    }

    @Test
    void everyPaletteBoundaryAndInputPattern() {
        int cases = 0;
        for (int cardinality : PalettePackingFixtures.CARDINALITIES)
            for (var pattern : new String[] {"runs", "cycle", "random"})
                for (int seed = 0; seed < 16; seed++) {
                    compare(PalettePackingFixtures.states(cardinality, seed, pattern));
                    cases++;
                }
        System.out.println(
                "PALETTE_PARITY fixtures=" + cases + " decoded_entries=" + (cases * 4096));
    }

    @Test
    void staleLocalAndGlobalPalettesCompactUnusedEntries() {
        for (int size : PalettePackingFixtures.CARDINALITIES) {
            var source = PalettePackingFixtures.states(size, 1977, "cycle");
            var first = source.get(0, 0, 0);
            for (int i = 0; i < 4096; i++)
                source.getAndSetUnchecked(i & 15, i >> 8, (i >> 4) & 15, first);
            compare(source);
            assertEquals(
                    1, source.pack(PalettePackingFixtures.owner(source)).paletteEntries().size());
        }
    }

    @Test
    void exactIdentityAliasesAndPackingPadding() {
        var map = new IdMapper<Object>();
        Object a = new String("equal"), b = new String("equal");
        map.add(a);
        map.add(b);
        var strategy = Strategy.createForBlockStates(map);
        var palette = LinearPalette.<Object>create(4, List.of(a, b, a));
        var values = new int[4096];
        for (int i = 0; i < values.length; i++) values[i] = (i * 7) % 3;
        var storage = new SimpleBitStorage(4, 4096, values);
        var source =
                PalettePackingFixtures.custom(
                        strategy, Strategy.FOUR_BITS_LINEAR, storage, palette);
        compare(source);
        for (int bits = 4; bits <= 8; bits++) {
            int n = 1 << bits;
            var registry = new IdMapper<Object>();
            var entries = new ArrayList<Object>();
            for (int i = 0; i < n; i++) {
                var value = new Object();
                registry.add(value);
                entries.add(value);
            }
            var st = Strategy.createForBlockStates(registry);
            var p = new HashMapPalette<>(bits, entries);
            var raw = new SimpleBitStorage(bits, 4096);
            Arrays.fill(raw.getRaw(), -1L);
            compare(
                    PalettePackingFixtures.custom(
                            st, st.getConfigurationForBitCount(bits), raw, p));
        }
    }

    @Test
    void savedTerrainPackingAndRoundTrips() {
        int cases = 0;
        for (var record : PalettePackingFixtures.records())
            for (var source : PalettePackingFixtures.recorded(record)) {
                compare(source);
                cases++;
                var packed = source.pack(PalettePackingFixtures.owner(source));
                var decoded =
                        PalettedContainer.unpack(PalettePackingFixtures.owner(source), packed)
                                .result()
                                .orElseThrow();
                for (int i = 0; i < 4096; i++)
                    assertSame(
                            source.get(i & 15, i >> 8, (i >> 4) & 15),
                            decoded.get(i & 15, i >> 8, (i >> 4) & 15));
            }
        System.out.println("PALETTE_SAVED_PARITY sections=" + cases + " saved_chunks=16");
    }

    @Test
    void malformedIdsKeepExceptionsAndReleaseTheLock() {
        var source = PalettePackingFixtures.states(2, 0, "cycle");
        var raw = source.dataForNativeScan().storage().getRaw();
        raw[0] |= 15;
        var strategy = PalettePackingFixtures.owner(source);
        var a =
                assertThrows(
                        RuntimeException.class, () -> JavaPalettePacking.pack(source, strategy));
        var b = assertThrows(RuntimeException.class, () -> source.pack(strategy));
        assertEquals(a.getClass(), b.getClass());
        assertEquals(a.getMessage(), b.getMessage());
        source.acquire();
        source.release();
    }

    @Test
    void customCallbacksAndBiomeContainersKeepCompatibility() {
        var base = PalettePackingFixtures.states(2, 1, "runs");
        var owner = PalettePackingFixtures.owner(base);
        var seen = new ArrayList<Integer>();
        var custom =
                new HashMapPalette<BlockState>(4, List.of(base.get(0, 0, 0), base.get(1, 0, 0))) {
                    @Override
                    public BlockState valueFor(int id) {
                        seen.add(id);
                        return super.valueFor(id);
                    }
                };
        var values = new int[4096];
        for (int i = 0; i < 4096; i++) values[i] = (i / 17) & 1;
        var source =
                PalettePackingFixtures.custom(
                        owner,
                        Strategy.FOUR_BITS_LINEAR,
                        new SimpleBitStorage(4, 4096, values),
                        custom);
        JavaPalettePacking.pack(source, owner);
        var trace = List.copyOf(seen);
        seen.clear();
        source.pack(owner);
        assertEquals(trace, seen);
        var map = new IdMapper<Object>();
        var first = new Object();
        map.add(first);
        var biomeStrategy = Strategy.createForBiomes(map);
        var biome = new PalettedContainer<>(first, biomeStrategy);
        assertNull(PalettePackingFixtures.nativePack(biome, biomeStrategy));
        equal(JavaPalettePacking.pack(biome, biomeStrategy), biome.pack(biomeStrategy));
    }

    @Test
    void everySupportedSourceWidthAndTailPadding() {
        for (int bits = 1; bits <= 16; bits++) {
            int count = Math.min(1 << bits, 16);
            var registry = new IdMapper<Object>();
            var entries = new ArrayList<Object>();
            for (int i = 0; i < count; i++) {
                var value = new Object();
                registry.add(value);
                entries.add(value);
            }
            var strategy = Strategy.createForBlockStates(registry);
            var storage = new SimpleBitStorage(bits, 4096);
            Arrays.fill(storage.getRaw(), -1L);
            // Keep every unused bit set; overwrite only actual entries.
            for (int i = 0; i < 4096; i++) storage.set(i, (i * 7) % count);
            compare(
                    PalettePackingFixtures.custom(
                            strategy,
                            Strategy.FOUR_BITS_LINEAR,
                            storage,
                            LinearPalette.create(4, entries)));
        }
    }

    @Test
    void unusedNullEntriesAndWideStorageKeepOriginalBehavior() {
        var registry = new IdMapper<Object>();
        Object first = new Object();
        registry.add(first);
        var strategy = Strategy.createForBlockStates(registry);
        var palette = LinearPalette.create(4, Arrays.asList(first, null));
        var source =
                PalettePackingFixtures.custom(
                        strategy,
                        Strategy.FOUR_BITS_LINEAR,
                        new SimpleBitStorage(4, 4096),
                        palette);
        compare(source); // Unused null entries must never be read.
        for (int bits : new int[] {31, 32}) {
            var wide =
                    PalettePackingFixtures.custom(
                            strategy,
                            Strategy.FOUR_BITS_LINEAR,
                            new SimpleBitStorage(bits, 4096),
                            LinearPalette.create(4, List.of(first)));
            assertNull(PalettePackingFixtures.nativePack(wide, strategy));
            if (bits == 31) {
                var a =
                        assertThrows(
                                RuntimeException.class,
                                () -> JavaPalettePacking.pack(wide, strategy));
                var b = assertThrows(RuntimeException.class, () -> wide.pack(strategy));
                assertEquals(a.getClass(), b.getClass());
                assertEquals(a.getMessage(), b.getMessage());
            } else equal(JavaPalettePacking.pack(wide, strategy), wide.pack(strategy));
        }
    }

    @Test
    void customStorageAndStrategyKeepCallbackOrder() {
        var base = PalettePackingFixtures.states(2, 39, "cycle");
        var owner = PalettePackingFixtures.owner(base);
        var trace = new ArrayList<String>();
        var customStorage =
                new SimpleBitStorage(4, 4096, base.dataForNativeScan().storage().getRaw().clone()) {
                    @Override
                    public int getSize() {
                        trace.add("size");
                        return super.getSize();
                    }

                    @Override
                    public int getBits() {
                        trace.add("bits");
                        return super.getBits();
                    }

                    @Override
                    public void unpack(int[] values) {
                        trace.add("unpack");
                        super.unpack(values);
                    }
                };
        var source =
                PalettePackingFixtures.custom(
                        owner,
                        Strategy.FOUR_BITS_LINEAR,
                        customStorage,
                        base.dataForNativeScan().palette());
        equal(JavaPalettePacking.pack(source, owner), source.pack(owner));
        trace.clear();
        JavaPalettePacking.pack(source, owner);
        var expected = List.copyOf(trace);
        trace.clear();
        source.pack(owner);
        assertEquals(expected, trace);
        var customStrategy =
                new Strategy<BlockState>(Block.BLOCK_STATE_REGISTRY, 4) {
                    @Override
                    public int entryCount() {
                        trace.add("entryCount");
                        return super.entryCount();
                    }

                    @Override
                    protected Configuration getConfigurationForBitCount(int bits) {
                        trace.add("config=" + bits);
                        return owner.getConfigurationForBitCount(bits);
                    }
                };
        trace.clear();
        JavaPalettePacking.pack(base, customStrategy);
        expected = List.copyOf(trace);
        trace.clear();
        base.pack(customStrategy);
        assertEquals(expected, trace);
    }

    @Test
    void everyRegisteredGlobalStateAndChangingLocalEntries() {
        var strategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        int total = Block.BLOCK_STATE_REGISTRY.size();
        var config = strategy.getConfigurationForBitCount(15);
        int cases = 0;
        for (int start = 0; start < total; start += 4096) {
            int[] ids = new int[4096];
            for (int i = 0; i < ids.length; i++) ids[i] = Math.min(start + i, total - 1);
            compare(
                    PalettePackingFixtures.custom(
                            strategy,
                            config,
                            new SimpleBitStorage(config.bitsInMemory(), 4096, ids),
                            strategy.globalPalette()));
            cases++;
        }
        var local = PalettePackingFixtures.states(2, 13, "cycle");
        compare(local);
        for (int i = 0; i < 4096; i++)
            local.getAndSetUnchecked(
                    i & 15, i >> 8, (i >> 4) & 15, Block.BLOCK_STATE_REGISTRY.byId(300 + i % 8));
        compare(local);
        System.out.println("PALETTE_REGISTRY_PARITY states=" + total + " fixtures=" + cases);
    }

    @Test
    void independentReadersDuringGc() throws Exception {
        var source = PalettePackingFixtures.states(512, 991, "random");
        var copies = new ArrayList<PalettedContainer<BlockState>>();
        for (int i = 0; i < 4; i++) copies.add(source.copy());
        var running = new java.util.concurrent.atomic.AtomicBoolean(true);
        var gc =
                new Thread(
                        () -> {
                            while (running.get()) {
                                System.gc();
                                Thread.yield();
                            }
                        },
                        "palette-test-gc");
        gc.setDaemon(true);
        gc.start();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<?>>();
            for (var copy : copies)
                futures.add(
                        executor.submit(
                                () -> {
                                    for (int i = 0; i < 20; i++) compare(copy);
                                }));
            for (var future : futures) future.get();
        } finally {
            running.set(false);
            gc.join(10000);
        }
        assertFalse(gc.isAlive());
    }
}
