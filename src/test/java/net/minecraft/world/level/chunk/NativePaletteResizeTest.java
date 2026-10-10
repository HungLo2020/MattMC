package net.minecraft.world.level.chunk;

import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.IdMapper;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Tag("parity")
class NativePaletteResizeTest {
    @BeforeAll static void bootstrap() { NativePalettePackingTest.bootstrap(); }

    static <T> PalettedContainer.Data<T> fresh(Strategy<T> owner, int requested) {
        var config = owner.getConfigurationForBitCount(requested);
        return new PalettedContainer.Data<>(config,
                config.bitsInMemory() == 0 ? new ZeroBitStorage(owner.entryCount())
                        : new SimpleBitStorage(config.bitsInMemory(), owner.entryCount()),
                config.createPalette(owner, List.of()));
    }

    static <T> void equal(PalettedContainer.Data<T> javaData, PalettedContainer.Data<T> rustData) {
        assertEquals(javaData.configuration(), rustData.configuration());
        assertEquals(javaData.storage().getBits(), rustData.storage().getBits());
        assertArrayEquals(javaData.storage().getRaw(), rustData.storage().getRaw());
        assertEquals(javaData.palette().getSize(), rustData.palette().getSize());
        if (!(javaData.palette() instanceof GlobalPalette))
            for (int i = 0; i < javaData.palette().getSize(); i++)
                assertSame(javaData.palette().valueFor(i), rustData.palette().valueFor(i));
        for (int i = 0; i < javaData.storage().getSize(); i++) {
            assertEquals(javaData.storage().get(i), rustData.storage().get(i));
            assertSame(javaData.palette().valueFor(javaData.storage().get(i)),
                    rustData.palette().valueFor(rustData.storage().get(i)));
        }
    }

    static <T> void compare(Strategy<T> owner, PalettedContainer.Data<T> source, int requested, T added) {
        var words = source.storage().getRaw().clone();
        var a = fresh(owner, requested);
        var b = fresh(owner, requested);
        assertTrue(NativePaletteResize.copy(owner, source, b), "Native slice bypassed");
        JavaPaletteResize.copyFrom(a, source.palette(), source.storage());
        equal(a, b);
        var original = new JavaPaletteResize<>(owner, source);
        var nativeContainer = PalettePackingFixtures.custom(owner, source.configuration(), source.storage(), source.palette());
        assertEquals(original.onResize(requested, added), nativeContainer.onResize(requested, added));
        equal(original.data, nativeContainer.dataForNativeScan());
        assertArrayEquals(words, source.storage().getRaw());
    }

    @Test void allGrowthBoundariesSeedsPatternsAndSkippedWidths() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        int cases = 0;
        for (int n : new int[] {1, 2, 3, 16, 17, 32, 64, 128, 256})
            for (var pattern : List.of("random", "cycle", "runs"))
                for (int seed = 0; seed < 16; seed++) {
                    var source = PalettePackingFixtures.states(n, seed, pattern).dataForNativeScan();
                    int requested = source.storage().getBits() + 1;
                    compare(owner, source, requested, Block.BLOCK_STATE_REGISTRY.byId(31800));
                    cases++;
                }
        for (int n : new int[] {1, 16, 32, 64, 128, 256}) {
            var source = PalettePackingFixtures.states(n, 77, "cycle").dataForNativeScan();
            compare(owner, source, 9, Block.BLOCK_STATE_REGISTRY.byId(31800));
            cases++;
        }
        System.out.println("RESIZE_PARITY fixtures=" + cases + " entries=" + cases * 4096);
    }

    @Test void savedSectionsAndEveryRegisteredStateThroughInitialGrowth() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        int eligible = 0, total = 0;
        for (var record : PalettePackingFixtures.records())
            for (var section : PalettePackingFixtures.recorded(record)) {
                total++;
                var source = section.dataForNativeScan();
                if (source.storage().getBits() > 8) continue;
                compare(owner, source, source.storage().getBits() + 1, Block.BLOCK_STATE_REGISTRY.byId(31800));
                eligible++;
            }
        for (int id = 0; id < Block.BLOCK_STATE_REGISTRY.size(); id++) {
            var state = Block.BLOCK_STATE_REGISTRY.byId(id);
            var source = new PalettedContainer<>(state, owner).dataForNativeScan();
            var target = fresh(owner, 1);
            assertTrue(NativePaletteResize.copy(owner, source, target));
            assertSame(state, target.palette().valueFor(0));
            assertArrayEquals(new long[256], target.storage().getRaw());
        }
        System.out.println("RESIZE_RECORDED_PARITY eligible_sections=" + eligible + " total_sections=" + total
                + " registered_states=" + Block.BLOCK_STATE_REGISTRY.size());
    }

    @Test void incrementalWritesTriggerActualResizesWithIdenticalOldValuesAndPacking() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        for (int seed = 0; seed < 8; seed++) {
            var nativeContainer = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY.byId(71), owner);
            var original = new JavaPaletteResize<>(owner, nativeContainer.dataForNativeScan().copy());
            var random = new Random(seed);
            for (int step = 0; step < 9000; step++) {
                int i = random.nextInt(4096);
                var state = Block.BLOCK_STATE_REGISTRY.byId((step * 37 + 71) % Block.BLOCK_STATE_REGISTRY.size());
                var old = original.getAndSetUnchecked(i & 15, i >> 8, i >> 4 & 15, state);
                assertSame(old, nativeContainer.getAndSetUnchecked(i & 15, i >> 8, i >> 4 & 15, state));
                if ((step & 63) == 0) equal(original.data, nativeContainer.dataForNativeScan());
            }
            equal(original.data, nativeContainer.dataForNativeScan());
            var referenceContainer = PalettePackingFixtures.custom(owner, original.data.configuration(), original.data.storage(), original.data.palette());
            var javaPack = JavaPalettePacking.pack(referenceContainer, owner);
            var nativePack = nativeContainer.pack(owner);
            assertEquals(javaPack.paletteEntries().size(), nativePack.paletteEntries().size());
            for (int i = 0; i < javaPack.paletteEntries().size(); i++)
                assertSame(javaPack.paletteEntries().get(i), nativePack.paletteEntries().get(i));
            assertEquals(javaPack.bitsPerEntry(), nativePack.bitsPerEntry());
            assertArrayEquals(javaPack.storage().orElseThrow().toArray(), nativePack.storage().orElseThrow().toArray());
        }
    }

    @Test void hashMapTriggerAlreadyInsertedBeforeGrowthStillUsesNative() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var added = Block.BLOCK_STATE_REGISTRY.byId(31800);
        for (int n : new int[] {32, 64, 128, 256}) {
            var source = PalettePackingFixtures.states(n, 91, "cycle").dataForNativeScan();
            source.palette().idFor(added, (bits, value) -> 0);
            assertEquals(n + 1, source.palette().getSize());
            compare(owner, source, source.storage().getBits() + 1, added);
        }
    }

    @Test void aliasIdentityNullUnusedEntriesStalePalettesAndPadding() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var state = Block.BLOCK_STATE_REGISTRY.byId(71);
        for (int bits = 1; bits <= 8; bits++) {
            int count = 1 << bits;
            var entries = new ArrayList<BlockState>();
            for (int i = 0; i < count; i++) entries.add(Block.BLOCK_STATE_REGISTRY.byId((i % 7) * 37 + 71));
            Palette<BlockState> palette = bits <= 4 ? LinearPalette.create(bits, entries) : new HashMapPalette<>(bits, entries);
            var storage = new SimpleBitStorage(bits, 4096);
            Arrays.fill(storage.getRaw(), -1L);
            for (int i = 0; i < 4096; i++) storage.set(i, (i * 1977) & (count - 1));
            var source = new PalettedContainer.Data<>(owner.getConfigurationForBitCount(bits), storage, palette);
            compare(owner, source, Math.max(5, bits + 1), state);
        }
        var entries = new ArrayList<BlockState>(Collections.nCopies(256, null)); entries.set(0, state);
        var source = new PalettedContainer.Data<>(Strategy.EIGHT_BITS_HASHMAP, new SimpleBitStorage(8, 4096),
                new HashMapPalette<BlockState>(8, entries));
        compare(owner, source, 9, state);
        for (int changed = 0; changed < 12; changed++) {
            var base = PalettePackingFixtures.states(256, changed, "cycle");
            for (int i = 0; i < 4096; i++) base.getAndSetUnchecked(i & 15, i >> 8, i >> 4 & 15,
                    Block.BLOCK_STATE_REGISTRY.byId(71 + 37 * (changed % 256)));
            compare(owner, base.dataForNativeScan(), 9, state);
        }
    }

    @Test void equalObjectsRemainDistinctIdentities() {
        record EqualValue(int value) {}
        @SuppressWarnings("unchecked") var owner = (Strategy<Object>) (Strategy<?>)
                Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var entries = new ArrayList<Object>();
        for (int i = 0; i < 16; i++) entries.add(new EqualValue(1));
        entries.set(15, entries.get(3));
        int[] ids = new int[4096];
        for (int i = 0; i < ids.length; i++) ids[i] = (i * 7 + 11) & 15;
        var source = new PalettedContainer.Data<>(Strategy.FOUR_BITS_LINEAR,
                new SimpleBitStorage(4, 4096, ids), LinearPalette.create(4, entries));
        compare(owner, source, 5, new EqualValue(1));
    }

    @Test void malformedIdsPreserveExceptionsUnpublishedSourceAndPartialCopies() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        for (int badAt : new int[] {0, 1, 2048, 4095}) {
            var state = Block.BLOCK_STATE_REGISTRY.byId(71);
            var storage = new SimpleBitStorage(4, 4096);
            storage.set(badAt, 15);
            var source = new PalettedContainer.Data<>(Strategy.FOUR_BITS_LINEAR, storage, LinearPalette.create(4, List.of(state)));
            var a = fresh(owner, 5); var b = fresh(owner, 5);
            assertFalse(NativePaletteResize.copy(owner, source, b));
            assertEquals(0, b.palette().getSize());
            var javaError = assertThrows(MissingPaletteEntryException.class,
                    () -> JavaPaletteResize.copyFrom(a, source.palette(), source.storage()));
            var rustError = assertThrows(MissingPaletteEntryException.class,
                    () -> b.copyFrom(source.palette(), source.storage()));
            assertEquals(javaError.getMessage(), rustError.getMessage());
            assertArrayEquals(a.storage().getRaw(), b.storage().getRaw());
            assertEquals(a.palette().getSize(), b.palette().getSize());
            var container = PalettePackingFixtures.custom(owner, source.configuration(), storage, source.palette());
            assertThrows(MissingPaletteEntryException.class, () -> container.onResize(5, state));
            assertSame(source.storage(), container.dataForNativeScan().storage());
        }
        var nullPalette = LinearPalette.<BlockState>create(4, Arrays.asList((BlockState) null));
        var source = new PalettedContainer.Data<>(Strategy.FOUR_BITS_LINEAR, new SimpleBitStorage(4, 4096), nullPalette);
        var a = fresh(owner, 5); var b = fresh(owner, 5);
        assertFalse(NativePaletteResize.copy(owner, source, b));
        JavaPaletteResize.copyFrom(a, source.palette(), source.storage()); b.copyFrom(source.palette(), source.storage());
        assertArrayEquals(a.storage().getRaw(), b.storage().getRaw());
        assertEquals(a.palette().getSize(), b.palette().getSize());
    }

    @Test void customCallbacksBiomesGlobalSourcesAndReuseKeepOriginalPath() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var trace = new ArrayList<Integer>();
        var state = Block.BLOCK_STATE_REGISTRY.byId(71);
        var palette = new HashMapPalette<BlockState>(5, List.of(state)) {
            @Override public BlockState valueFor(int id) { trace.add(id); return super.valueFor(id); }
            @Override public int getSize() { throw new AssertionError("Custom eligibility callback"); }
        };
        var source = new PalettedContainer.Data<>(Strategy.FIVE_BITS_HASHMAP, new SimpleBitStorage(5, 4096), palette);
        assertFalse(NativePaletteResize.copy(owner, source, fresh(owner, 6)));
        var original = new JavaPaletteResize<>(owner, source);
        var nativeContainer = PalettePackingFixtures.custom(owner, source.configuration(), source.storage(), palette);
        original.onResize(6, state); var expected = List.copyOf(trace); trace.clear();
        nativeContainer.onResize(6, state); assertEquals(expected, trace);
        equal(original.data, nativeContainer.dataForNativeScan());
        var customStorage = new SimpleBitStorage(4, 4096) {
            @Override public int getSize() { throw new AssertionError("Custom eligibility callback"); }
        };
        assertFalse(NativePaletteResize.copy(owner, new PalettedContainer.Data<>(Strategy.FOUR_BITS_LINEAR,
                customStorage, LinearPalette.create(4, List.of(state))), fresh(owner, 5)));
        var globals = PalettePackingFixtures.states(512, 19, "cycle").dataForNativeScan();
        assertFalse(NativePaletteResize.copy(owner, globals, fresh(owner, 16)));
        var smallMap = new IdMapper<BlockState>(); smallMap.add(state);
        var smallOwner = Strategy.createForBiomes(smallMap);
        var small = new PalettedContainer<>(state, smallOwner);
        assertFalse(NativePaletteResize.copy(smallOwner, small.dataForNativeScan(), fresh(smallOwner, 1)));
        var standard = PalettePackingFixtures.states(16, 8, "cycle").dataForNativeScan();
        assertFalse(NativePaletteResize.copy(owner, standard, standard));
        var wide = new PalettedContainer.Data<>(Strategy.EIGHT_BITS_HASHMAP, new SimpleBitStorage(17, 4096), standard.palette());
        assertFalse(NativePaletteResize.copy(owner, wide, fresh(owner, 9)));
    }

    @Test void independentThreadsAndGcRetainNoPaletteValues() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<Callable<Void>>();
            for (int thread = 0; thread < 4; thread++) {
                final int seed = thread;
                tasks.add(() -> {
                    var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
                    for (int n : new int[] {1, 16, 32, 64, 128, 256}) {
                        var source = PalettePackingFixtures.states(n, seed, "random").dataForNativeScan();
                        for (int iteration = 0; iteration < 20; iteration++) {
                            compare(owner, source, source.storage().getBits() + 1, Block.BLOCK_STATE_REGISTRY.byId(31800));
                            if (seed == 0) System.gc();
                        }
                    }
                    var field = NativePaletteResize.class.getDeclaredField("SCRATCH");
                    field.setAccessible(true);
                    var scratch = ((ThreadLocal<?>) field.get(null)).get();
                    var values = scratch.getClass().getDeclaredField("values"); values.setAccessible(true);
                    for (var value : (Object[]) values.get(scratch)) assertNull(value);
                    return null;
                });
            }
            for (var future : pool.invokeAll(tasks)) future.get();
        } finally { pool.shutdownNow(); }
    }
}
