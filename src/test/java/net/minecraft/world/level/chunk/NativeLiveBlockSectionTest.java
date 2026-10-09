package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.*;
import java.util.concurrent.Executors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeLiveBlockSectionTest {
    @BeforeAll static void bootstrap() { NativePalettePackingTest.bootstrap(); }
    private static Strategy<BlockState> strategy() { return Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY); }
    private static PalettedContainer<BlockState> fresh() { return new PalettedContainer<>(Blocks.AIR.defaultBlockState(), strategy()); }
    private static void equal(PalettedContainer.Data<BlockState> expected, PalettedContainer<BlockState> actual) {
        var d = actual.dataForNativeScan();
        assertEquals(expected.configuration(), d.configuration());
        assertEquals(expected.palette().getSize(), d.palette().getSize());
        assertArrayEquals(expected.storage().getRaw(), d.storage().getRaw());
        if (!(expected.palette() instanceof GlobalPalette<?>)) for (int i = 0; i < expected.palette().getSize(); i++) assertSame(expected.palette().valueFor(i), d.palette().valueFor(i));
        for (int i = 0; i < 4096; i++) assertSame(expected.palette().valueFor(expected.storage().get(i)), actual.get(i & 15, i >> 8, i >> 4 & 15));
    }
    @Test void everyGrowthBoundaryAndRegisteredStateMatchesOriginalWrites() {
        var owner = strategy(); var source = new PalettedContainer<>(Blocks.AIR.defaultBlockState(), owner);
        var original = new JavaPaletteResize<>(owner, new PalettedContainer.Data<>(Strategy.ZERO_BITS, new ZeroBitStorage(4096), new SingleValuePalette<>(List.of(Blocks.AIR.defaultBlockState()))));
        var rng = new Random(123);
        for (int i = 0; i < Block.BLOCK_STATE_REGISTRY.size(); i++) {
            int index = rng.nextInt(4096); var state = Block.BLOCK_STATE_REGISTRY.byId(i);
            assertSame(original.getAndSetUnchecked(index & 15, index >> 8, index >> 4 & 15, state), source.getAndSetUnchecked(index & 15, index >> 8, index >> 4 & 15, state));
            if (i < 600 || (i & 511) == 0) equal(original.data, source);
        }
        assertNotNull(source.nativeLiveBlocks()); equal(original.data, source);
    }
    @Test void liveStorageHasNoJavaMirrorAndRetainedViewsRemainReadOnly() throws Exception {
        var source = fresh(); var owner = source.nativeLiveBlocks(); assertNotNull(owner);
        var dataField = PalettedContainer.class.getDeclaredField("data"); dataField.setAccessible(true); assertNull(dataField.get(source));
        var viewField = NativeLiveBlockSection.class.getDeclaredField("view"); viewField.setAccessible(true);
        var old = (NativeLiveBlockSection.View)viewField.get(owner);
        source.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        var local = (NativeLiveBlockSection.View)viewField.get(owner);
        assertTrue(local.words.isReadOnly());
        assertThrows(IllegalArgumentException.class, () -> local.words.setAtIndex(ValueLayout.JAVA_LONG, 0, 0));
        for (int i = 0; i < 600; i++) source.set(i & 15, i >> 8, i >> 4 & 15, Block.BLOCK_STATE_REGISTRY.byId(i));
        source = null; owner = null; System.gc();
        assertSame(Blocks.AIR.defaultBlockState(), old.state(0));
        assertSame(Blocks.AIR.defaultBlockState(), local.state(4095));
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) tasks.add(pool.submit(() -> { for (int n = 0; n < 20000; n++) assertNotNull(local.state(n % 4096)); }));
            for (var task : tasks) task.get();
        }
    }
    @Test void networkCopyAndSavedSectionsRetainExactWordsAndPalettes() {
        for (int size : PalettePackingFixtures.CARDINALITIES) {
            var source = PalettePackingFixtures.states(size, 17, "random");
            var before = source.dataForNativeScan(); var copy = source.copy(); assertNotNull(copy.nativeLiveBlocks()); equal(before, copy);
            var a = new FriendlyByteBuf(Unpooled.buffer()); var b = new FriendlyByteBuf(Unpooled.buffer());
            try {
                before.write(a, Block.BLOCK_STATE_REGISTRY); source.write(b);
                assertEquals(a.readableBytes(), source.getSerializedSize());
                assertEquals(a, b);
                var loaded = fresh(); loaded.read(b); assertNotNull(loaded.nativeLiveBlocks());
                // Wire stores memory width; its original decoder requests that width.
                equal(new PalettedContainer.Data<>(strategy().getConfigurationForBitCount(before.storage().getBits()), before.storage(), before.palette()), loaded);
            } finally { a.release(); b.release(); }
            copy.set(0, 0, 0, Blocks.BEDROCK.defaultBlockState()); equal(before, source);
        }
        int total = 0;
        for (var record : PalettePackingFixtures.records()) for (var source : PalettePackingFixtures.recorded(record)) {
            assertNotNull(source.nativeLiveBlocks()); equal(source.dataForNativeScan(), source.copy()); total++;
        }
        assertEquals(324, total);
    }
    @Test void invalidAliasesAndCustomPoliciesKeepCompatibilityOwnership() {
        var state = Blocks.STONE.defaultBlockState(); var owner = strategy();
        var aliases = PalettePackingFixtures.custom(owner, Strategy.FOUR_BITS_LINEAR, new SimpleBitStorage(4, 4096), LinearPalette.create(4, List.of(state, state)));
        assertNull(aliases.nativeLiveBlocks()); assertSame(state, aliases.get(0, 0, 0));
        var malformed = new SimpleBitStorage(4, 4096); malformed.set(0, 15);
        var bad = PalettePackingFixtures.custom(owner, Strategy.FOUR_BITS_LINEAR, malformed, LinearPalette.create(4, List.of(state)));
        assertNull(bad.nativeLiveBlocks()); assertThrows(MissingPaletteEntryException.class, () -> bad.get(0, 0, 0));
        var trace = new ArrayList<String>();
        var custom = new HashMapPalette<BlockState>(5, List.of(state)) { @Override public int getSize() { trace.add("size"); return super.getSize(); } };
        var other = PalettePackingFixtures.custom(owner, Strategy.FIVE_BITS_HASHMAP, new SimpleBitStorage(5, 4096), custom);
        assertNull(other.nativeLiveBlocks()); assertTrue(trace.isEmpty());
    }
    @Test void bulkExportsAndRebuildSnapshotsMatchLiveReads() {
        try (var arena = Arena.ofConfined()) {
            var words = arena.allocate(8192, 8); var palette = arena.allocate(1028, 4); var header = arena.allocate(16, 4);
            for (int size : PalettePackingFixtures.CARDINALITIES) {
                var source = PalettePackingFixtures.states(size, 23, "cycle"); var live = source.nativeLiveBlocks(); assertNotNull(live);
                int len = live.export(words, palette, header); var d = source.dataForNativeScan();
                assertEquals(d.storage().getRaw().length, len); assertEquals(d.storage().getBits(), header.getAtIndex(ValueLayout.JAVA_INT, 0));
                for (int i = 0; i < len; i++) assertEquals(d.storage().getRaw()[i], words.getAtIndex(ValueLayout.JAVA_LONG, i));
                var snapshot = NativeBlockSectionSnapshot.capture(source, arena); assertNotNull(snapshot);
                for (int i = 0; i < 4096; i++) assertSame(source.get(i & 15, i >> 8, i >> 4 & 15), snapshot.state(i));
                var light = arena.allocate(NativeLightBlocks.BUFFER, 8); assertEquals(0, live.light(light));
                assertEquals(size == 1 ? 0 : size > 256 ? 2 : 1, light.getAtIndex(ValueLayout.JAVA_INT, 0));
            }
        }
    }
    @Test void singleCopiesShareNetworkPaletteReplacementUntilResize() {
        var source = fresh(); var copy = source.copy();
        var original = new SingleValuePalette<BlockState>(List.of(Blocks.AIR.defaultBlockState()));
        var originalCopy = original.copy(); assertSame(original, originalCopy);
        for (var state : List.of(Blocks.STONE.defaultBlockState(), Blocks.BEDROCK.defaultBlockState())) {
            var wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                wire.writeByte(0); wire.writeVarInt(Block.getId(state));
                var reference = new FriendlyByteBuf(wire.copy());
                try { reference.readByte(); originalCopy.read(reference, Block.BLOCK_STATE_REGISTRY); }
                finally { reference.release(); }
                copy.read(wire);
                assertSame(original.valueFor(0), source.get(0, 0, 0));
                assertSame(state, copy.get(0, 0, 0));
            } finally { wire.release(); }
        }
        assertEquals(0, source.onResize(0, Blocks.BEDROCK.defaultBlockState()));
        assertThrows(IllegalArgumentException.class, () -> source.onResize(0, Blocks.AIR.defaultBlockState()));
        assertThrows(IllegalArgumentException.class, () -> source.getAndSetUnchecked(-1, 0, 0, Blocks.BEDROCK.defaultBlockState()));
        // A different-width decode that fails must not detach the old shared palette.
        var failed = new FriendlyByteBuf(Unpooled.buffer());
        try { failed.writeByte(4); assertThrows(IndexOutOfBoundsException.class, () -> source.read(failed)); }
        finally { failed.release(); }
        var shared = new FriendlyByteBuf(Unpooled.buffer());
        try { shared.writeByte(0); shared.writeVarInt(Block.getId(Blocks.STONE.defaultBlockState())); copy.read(shared); }
        finally { shared.release(); }
        assertSame(Blocks.STONE.defaultBlockState(), source.get(0, 0, 0));
        source.set(0, 0, 0, Blocks.BEDROCK.defaultBlockState());
        var wire = new FriendlyByteBuf(Unpooled.buffer());
        try { wire.writeByte(0); wire.writeVarInt(Block.getId(Blocks.AIR.defaultBlockState())); copy.read(wire); }
        finally { wire.release(); }
        assertSame(Blocks.BEDROCK.defaultBlockState(), source.get(0, 0, 0));
        assertSame(Blocks.STONE.defaultBlockState(), source.get(1, 0, 0));
        assertSame(Blocks.AIR.defaultBlockState(), copy.get(0, 0, 0));
        assertNotNull(source.nativeLiveBlocks()); assertNotNull(copy.nativeLiveBlocks());
    }
    @Test void invalidWritePreservesOriginalPaletteMutationBeforeException() {
        var source = fresh(); var original = new JavaPaletteResize<>(strategy(), source.dataForNativeScan().copy());
        var state = Blocks.STONE.defaultBlockState();
        assertThrows(IllegalArgumentException.class, () -> original.getAndSetUnchecked(-1, 0, 0, state));
        assertThrows(IllegalArgumentException.class, () -> source.getAndSetUnchecked(-1, 0, 0, state));
        equal(original.data, source);
    }
}
