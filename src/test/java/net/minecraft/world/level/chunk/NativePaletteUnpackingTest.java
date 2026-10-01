package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.DataResult;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.LongStream;
import net.minecraft.core.IdMapper;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativePaletteUnpackingTest {
    @BeforeAll static void bootstrap() { NativePalettePackingTest.bootstrap(); }
    record Input(List<BlockState> palette, long[] words, int bits) {
        PalettedContainerRO.PackedData<BlockState> packed() {
            return new PalettedContainerRO.PackedData<>(palette, Optional.of(LongStream.of(words)), bits);
        }
    }
    static Input input(int count, long seed, String pattern, String listType) {
        var entries = new ArrayList<BlockState>();
        for (int i = 0; i < count; i++) entries.add(Block.BLOCK_STATE_REGISTRY.byId(i % Block.BLOCK_STATE_REGISTRY.size()));
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(count - 1));
        var storage = new SimpleBitStorage(bits, 4096);
        Arrays.fill(storage.getRaw(), -1L); // Non-zero unused bits must not reach output.
        var random = new Random(seed);
        for (int i = 0; i < 4096; i++) {
            int id = switch (pattern) {
                case "cycle" -> i % count;
                case "runs" -> i / 32 % count;
                case "stale" -> count - 1;
                default -> random.nextInt(count);
            };
            storage.set(i, id);
        }
        List<BlockState> list = switch (listType) {
            case "arrays" -> Arrays.asList(entries.toArray(BlockState[]::new));
            case "list" -> List.copyOf(entries);
            case "arraylist" -> entries;
            default -> ImmutableList.copyOf(entries);
        };
        return new Input(list, storage.getRaw(), bits);
    }
    static PalettedContainer<BlockState> compare(Input input, boolean nativeRequired) {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var before = input.words.clone();
        var original = JavaPaletteUnpacking.unpack(owner, input.packed()).result().orElseThrow();
        if (nativeRequired) {
            var direct = NativePaletteUnpacking.unpack(owner, owner.getConfigurationForPaletteSize(input.palette.size()), input.palette, input.words);
            assertNotNull(direct, "Native slice bypassed");
            NativePaletteResizeTest.equal(original.dataForNativeScan(), direct.dataForNativeScan());
        }
        var migrated = PalettedContainer.unpack(owner, input.packed()).result().orElseThrow();
        NativePaletteResizeTest.equal(original.dataForNativeScan(), migrated.dataForNativeScan());
        assertArrayEquals(before, input.words);
        return migrated;
    }
    @Test void allSourceWidthsBoundariesListsPatternsAndPadding() {
        int cases = 0;
        for (int bits = 9; bits <= 16; bits++)
            for (int n : new int[]{(1 << (bits - 1)) + 1, 1 << bits})
                for (String pattern : List.of("random", "cycle", "runs", "stale"))
                    for (String list : List.of("guava", "arrays", "list", "arraylist"))
                        for (int seed = 0; seed < 3; seed++) {
                            compare(input(n, seed * 1977L, pattern, list), true);
                            cases++;
                        }
        System.out.println("UNPACK_PARITY fixtures=" + cases + " entries=" + cases * 4096);
    }
    @Test void everyRegisteredStateAndIdentityAliases() {
        int count = Block.BLOCK_STATE_REGISTRY.size();
        var input = input(count, 71, "cycle", "guava");
        for (int start = 0; start < count; start += 4096) {
            var storage = new SimpleBitStorage(input.bits, 4096);
            for (int i = 0; i < 4096; i++) storage.set(i, (start + i) % count);
            compare(new Input(input.palette, storage.getRaw(), input.bits), true);
        }
        var aliased = input(513, 71, "random", "arraylist");
        for (int i = 0; i < 513; i++) aliased.palette.set(i, Block.BLOCK_STATE_REGISTRY.byId(i % 7));
        compare(aliased, true);
        // Non-null unregistered objects still map to registry ID zero, exactly as GlobalPalette.idFor.
        @SuppressWarnings({"rawtypes", "unchecked"}) Strategy<Object> owner = (Strategy) Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var unknown = new Object();
        var entries = new ArrayList<Object>(Collections.nCopies(257, unknown));
        var words = new SimpleBitStorage(9, 4096).getRaw();
        var original = JavaPaletteUnpacking.unpack(owner, new PalettedContainerRO.PackedData<>(entries, Optional.of(LongStream.of(words)), 9)).result().orElseThrow();
        var rust = NativePaletteUnpacking.unpack(owner, owner.getConfigurationForPaletteSize(257), entries, words);
        assertNotNull(rust);
        NativePaletteResizeTest.equal(original.dataForNativeScan(), rust.dataForNativeScan());
        System.out.println("UNPACK_REGISTERED_PARITY registered_states=" + count);
    }
    @Test void savedSectionsAndOriginalPackedRoundTripsAcrossExistingMigrations() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        int saved = 0, edited = 0;
        for (var record : PalettePackingFixtures.records()) {
            var sections = PalettePackingFixtures.recorded(record);
            for (var section : sections) {
                var packed = JavaPalettePacking.pack(section, owner);
                var words = packed.storage().map(LongStream::toArray).orElse(new long[0]);
                var a = JavaPaletteUnpacking.unpack(owner, new PalettedContainerRO.PackedData<>(packed.paletteEntries(),
                        words.length == 0 ? Optional.empty() : Optional.of(LongStream.of(words)), packed.bitsPerEntry())).result().orElseThrow();
                var b = PalettedContainer.unpack(owner, new PalettedContainerRO.PackedData<>(packed.paletteEntries(),
                        words.length == 0 ? Optional.empty() : Optional.of(LongStream.of(words)), packed.bitsPerEntry())).result().orElseThrow();
                NativePaletteResizeTest.equal(a.dataForNativeScan(), b.dataForNativeScan()); saved++;
            }
            // Edited saved sections exercise the global branch; unchanged corpus is local compatibility coverage.
            var section = sections.getFirst().copy();
            for (int i = 0; i < 768; i++) section.getAndSetUnchecked(i & 15, i >> 8, i >> 4 & 15,
                    Block.BLOCK_STATE_REGISTRY.byId(i));
            var packed = JavaPalettePacking.pack(section, owner);
            var migrated = compare(new Input(ImmutableList.copyOf(packed.paletteEntries()), packed.storage().orElseThrow().toArray(), packed.bitsPerEntry()), true);
            var nativePack = migrated.pack(owner); var originalPack = JavaPalettePacking.pack(section, owner);
            assertEquals(originalPack.paletteEntries(), nativePack.paletteEntries());
            assertArrayEquals(originalPack.storage().orElseThrow().toArray(), nativePack.storage().orElseThrow().toArray());
            var a = new IdentityHashMap<BlockState,Integer>(); var b = new IdentityHashMap<BlockState,Integer>();
            JavaPaletteHistogram.count(section, a::put); migrated.count(b::put);
            assertEquals(a.size(),b.size());
            for(var entry:a.entrySet())assertEquals(entry.getValue(),b.get(entry.getKey())); edited++;
        }
        System.out.println("UNPACK_SAVED_PARITY unchanged_local_sections=" + saved + " edited_global_sections=" + edited);
    }
    static <T> Object outcome(Strategy<T> owner, List<T> entries, long[] words, int bits, boolean nativeMode) {
        try {
            var packed = new PalettedContainerRO.PackedData<>(entries, words == null ? Optional.<LongStream>empty() : Optional.of(LongStream.of(words)), bits);
            var result = nativeMode ? PalettedContainer.unpack(owner, packed) : JavaPaletteUnpacking.unpack(owner, packed);
            return result.error().map(e -> e.message()).orElse("success");
        } catch (RuntimeException e) { return e.getClass().getName() + ":" + e.getMessage(); }
    }
    @Test void malformedIdsLengthsDeclaredBitsMissingStorageAndScratchRecovery() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var good = input(257, 71, "random", "guava");
        for (long[] words : new long[][]{null, new long[0], Arrays.copyOf(good.words, good.words.length-1), Arrays.copyOf(good.words,good.words.length+1), good.words})
            for (int declared : new int[]{-1, 8, 9, 15}) {
                assertEquals(outcome(owner, good.palette, words, declared, false), outcome(owner, good.palette, words, declared, true));
            }
        for (int at : new int[]{0,1,127,4095}) {
            var storage = new SimpleBitStorage(9,4096,good.words.clone()); storage.set(at,511);
            var a = outcome(owner,good.palette,storage.getRaw(),9,false);
            assertTrue(a.toString().contains("MissingPaletteEntryException"));
            assertEquals(a,outcome(owner,good.palette,storage.getRaw(),9,true));
            compare(good,true);
        }
        var events = new ArrayList<Long>();
        var result = PalettedContainer.unpack(owner,new PalettedContainerRO.PackedData<>(good.palette,
                Optional.of(LongStream.of(good.words).peek(events::add)),9));
        assertTrue(result.result().isPresent()); assertEquals(good.words.length,events.size());
        events.clear();
        result = PalettedContainer.unpack(owner,new PalettedContainerRO.PackedData<>(good.palette,
                Optional.of(LongStream.of(good.words).peek(events::add)),8));
        assertTrue(result.error().isPresent()); assertTrue(events.isEmpty());
    }
    @Test void nullEntriesAndCustomListCallbackOrderKeepOriginalSemantics() {
        var owner = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        for (int nullAt : new int[]{0,1,127,256}) {
            var input = input(257,71,"stale","arraylist"); input.palette.set(nullAt,null);
            assertNull(NativePaletteUnpacking.unpack(owner,owner.getConfigurationForPaletteSize(257),input.palette,input.words));
            var expected = outcome(owner,input.palette,input.words,9,false);
            assertEquals(expected,outcome(owner,input.palette,input.words,9,true));
            if (expected.equals("success")) compare(input,false);
        }
        class ObservedList extends AbstractList<BlockState> {
            final List<String> events = new ArrayList<>(); final List<BlockState> values = input(257,71,"random","guava").palette;
            public int size() { events.add("size"); return values.size(); }
            public BlockState get(int i) { events.add("get:"+i); return values.get(i); }
            public void forEach(java.util.function.Consumer<? super BlockState> f) { events.add("forEach"); super.forEach(f); }
        }
        var list = new ObservedList(); var words = input(257,71,"random","guava").words;
        assertEquals("success",outcome(owner,list,words,9,false)); var expected = List.copyOf(list.events); list.events.clear();
        assertEquals("success",outcome(owner,list,words,9,true)); assertEquals(expected,list.events);
        compare(new Input(list,words,9),false);
    }
    @Test void customRegistriesBiomesAndLocalPalettesRetainOriginalResults() {
        var registry = new IdMapper<Object>(); for(int i=0;i<1024;i++)registry.add(new Object());
        for (var owner : List.of(Strategy.createForBlockStates(registry), Strategy.createForBiomes(registry))) {
            for (int n : new int[]{1,2,16,32,256,257,513}) {
                var entries = new ArrayList<Object>(); for(int i=0;i<n;i++)entries.add(registry.byId(i));
                var config=owner.getConfigurationForPaletteSize(n); int bits=config.bitsInStorage();
                long[] words = bits == 0 ? new long[0] : new SimpleBitStorage(bits,owner.entryCount()).getRaw();
                var a = JavaPaletteUnpacking.unpack(owner,new PalettedContainerRO.PackedData<>(entries,
                        bits==0?Optional.empty():Optional.of(LongStream.of(words)),bits)).result().orElseThrow();
                var b = PalettedContainer.unpack(owner,new PalettedContainerRO.PackedData<>(entries,
                        bits==0?Optional.empty():Optional.of(LongStream.of(words)),bits)).result().orElseThrow();
                NativePaletteResizeTest.equal(a.dataForNativeScan(),b.dataForNativeScan());
                assertNull(NativePaletteUnpacking.unpack(owner,config,entries,words));
            }
        }
        for(int n : PalettePackingFixtures.CARDINALITIES) {
            if(n>256)continue;
            var input=input(n==1?2:n,71,"random","guava"); compare(input,false);
        }
    }
    @Test void codecRoundTripUsesNativeEligibleListAndExactOriginalDecodedContents() {
        var owner=Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var source=PalettePackingFixtures.states(1024,71,"cycle");
        var codec=PalettedContainer.codecRW(BlockState.CODEC,owner,Block.BLOCK_STATE_REGISTRY.byId(0));
        var json=codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,source).result().orElseThrow();
        var decoded=codec.parse(com.mojang.serialization.JsonOps.INSTANCE,json).result().orElseThrow();
        var entries=BlockState.CODEC.listOf().parse(com.mojang.serialization.JsonOps.INSTANCE,json.getAsJsonObject().get("palette")).result().orElseThrow();
        var words=json.getAsJsonObject().getAsJsonArray("data"); long[] raw=new long[words.size()];
        for(int i=0;i<raw.length;i++)raw[i]=words.get(i).getAsLong();
        var config=owner.getConfigurationForPaletteSize(entries.size());
        assertNotNull(NativePaletteUnpacking.unpack(owner,config,entries,raw),"Actual codec list bypasses native path: "+entries.getClass());
        var original=JavaPaletteUnpacking.unpack(owner,new PalettedContainerRO.PackedData<>(entries,Optional.of(LongStream.of(raw)))).result().orElseThrow();
        NativePaletteResizeTest.equal(original.dataForNativeScan(),decoded.dataForNativeScan());
        for(int i=0;i<4096;i++)assertSame(source.get(i&15,i>>8,i>>4&15),decoded.get(i&15,i>>8,i>>4&15));
        System.out.println("UNPACK_CODEC_PARITY actual_list="+entries.getClass().getName()+" states="+entries.size());
    }
    @Test void concurrentThreadScratchAndGarbageCollection() throws Exception {
        var executor=Executors.newFixedThreadPool(4);
        try {
            var futures=new ArrayList<Future<?>>();
            for(int thread=0;thread<4;thread++) { final int seed=thread;
                futures.add(executor.submit(()->{for(int i=0;i<20;i++)compare(input(513,seed*1977L+i,"random","guava"),true);}));
            }
            System.gc(); for(var future:futures)future.get();
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(30,TimeUnit.SECONDS)); }
    }
}
