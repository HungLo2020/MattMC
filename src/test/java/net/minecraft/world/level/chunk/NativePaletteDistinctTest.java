package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.core.IdMapper;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativePaletteDistinctTest {
    @BeforeAll static void bootstrap() { NativePalettePackingTest.bootstrap(); }

    static void raw(SimpleBitStorage storage) {
        var before = storage.getRaw().clone();
        var set = new IntArraySet();
        storage.getAll(set::add);
        int[] expected = set.toIntArray();
        try (var ids = NativePaletteDistinct.scan(storage)) {
            assertNotNull(ids); assertEquals(expected.length, ids.size);
            for (int i = 0; i < ids.size; i++) assertEquals(expected[i], ids.entry(i));
        }
        assertArrayEquals(before, storage.getRaw());
    }

    @Test void everyWidthPaddingAndFirstOccurrenceOrder() {
        for (int bits = 1; bits <= 16; bits++) for (int size : new int[]{64,4096}) {
            var storage = new SimpleBitStorage(bits,size);
            Arrays.fill(storage.getRaw(),-1L);
            for (int i = 0; i < size; i++) storage.set(i, (i*1977 + 71) & ((1<<bits)-1));
            raw(storage);
            Arrays.fill(storage.getRaw(),-1L); raw(storage);
            Arrays.fill(storage.getRaw(),0L); raw(storage);
        }
        // Exhaustive first-occurrence permutations of eight two-bit fields.
        var storage = new SimpleBitStorage(2,64);
        for (int word = 0; word < 65536; word++) { storage.getRaw()[0]=word; raw(storage); }
        System.out.println("DISTINCT_RAW_PARITY exhaustive=65536 widths=16 sizes=64,4096");
    }

    @Test void seededBiomeAndBlockContainersAllPaletteBoundaries() {
        int cases=0;
        for (int n : new int[]{2,3,4,5,8,9,16,32,64})
            for (var pattern : List.of("random","runs","cycle")) for (int seed=0; seed<16; seed++) {
                var source=PaletteDistinctFixtures.biomes(n,seed,pattern);
                raw((SimpleBitStorage)source.dataForNativeScan().storage());
                PaletteDistinctFixtures.compare(source);cases++;
            }
        for (int n : PalettePackingFixtures.CARDINALITIES) for (var pattern : List.of("random","runs","cycle"))
            for (int seed=0; seed<8; seed++) { var source=PalettePackingFixtures.states(n,seed,pattern);
                PaletteDistinctFixtures.compare(source);cases++; }
        System.out.println("DISTINCT_CALLER_PARITY seeded_containers="+cases);
    }

    @Test void allSavedBiomesBlockSectionsAndRegisteredStates() {
        var biomes=PaletteDistinctFixtures.recorded("all");
        for (var source:biomes) PaletteDistinctFixtures.compare(source);
        int sections=0;
        for (var record:PalettePackingFixtures.records()) for (var source:PalettePackingFixtures.recorded(record)) {
            PaletteDistinctFixtures.compare(source);sections++;
        }
        var owner=Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        var config=owner.getConfigurationForBitCount(15);
        for(int start=0;start<Block.BLOCK_STATE_REGISTRY.size();start+=4096) {
            var values=new int[4096];for(int i=0;i<4096;i++)values[i]=Math.min(start+i,Block.BLOCK_STATE_REGISTRY.size()-1);
            PaletteDistinctFixtures.compare(PalettePackingFixtures.custom(owner,config,
                new SimpleBitStorage(config.bitsInMemory(),4096,values),owner.globalPalette()));
        }
        System.out.println("DISTINCT_SAVED_PARITY biomes="+biomes.size()+" blocks="+sections+" registered_states="+Block.BLOCK_STATE_REGISTRY.size());
    }

    @Test void aliasesNullsMissingIdsAndCustomPaletteKeepLookupOrder() {
        Object a=new String("same"),b=new String("same");var map=new IdMapper<Object>();map.add(a);map.add(b);
        var owner=Strategy.createForBiomes(map);var trace=new ArrayList<Integer>();
        var values=Arrays.asList(a,b,a,null);
        var palette=new HashMapPalette<Object>(2, values) {
            @Override public Object valueFor(int id) { trace.add(id); return values.get(id); }
            @Override public int getSize() { throw new AssertionError("getAll must not inspect palette size"); }
        };
        var input=new int[64];for(int i=0;i<64;i++)input[i]=(i*3+2)%4;
        var source=PalettePackingFixtures.custom(owner,Strategy.TWO_BITS_LINEAR,new SimpleBitStorage(2,64,input),palette);
        var expected=new ArrayList<Object>(); JavaPaletteDistinct.getAll(source,expected::add);
        var order=new ArrayList<>(trace);trace.clear();var actual=new ArrayList<Object>();source.getAll(actual::add);
        assertEquals(order,trace);assertEquals(expected.size(),actual.size());
        for(int i=0;i<expected.size();i++)assertSame(expected.get(i),actual.get(i));
        var error=new IllegalArgumentException("lookup failed");
        var throwing=new HashMapPalette<Object>(2,values) {
            @Override public Object valueFor(int id) { trace.add(id); if(id==1)throw error;return values.get(id); }
        };
        source=PalettePackingFixtures.custom(owner,Strategy.TWO_BITS_LINEAR,new SimpleBitStorage(2,64,input),throwing);
        trace.clear();var before=new ArrayList<Object>();var reference=source;
        assertSame(error,assertThrows(IllegalArgumentException.class,()->JavaPaletteDistinct.getAll(reference,before::add)));
        order=new ArrayList<>(trace);trace.clear();var after=new ArrayList<Object>();
        assertSame(error,assertThrows(IllegalArgumentException.class,()->reference.getAll(after::add)));
        assertEquals(order,trace);assertEquals(before,after);
        trace.clear();assertThrows(NullPointerException.class,()->reference.getAll(null));assertEquals(List.of(2),trace);
        PaletteDistinctFixtures.compare(PaletteDistinctFixtures.biomes(4,71,"cycle"));
    }

    static List<String> mutation(boolean nativeMode, boolean reenter, boolean fail) {
        var source=PaletteDistinctFixtures.biomes(2,71,"cycle");
        var palette=source.dataForNativeScan().palette();
        var trace=new ArrayList<String>();var error=new IllegalStateException("consumer failure");
        Consumer<String> consumer=value->{
            trace.add("outer:"+value);
            if(trace.size()==1) {
                for(int i=0;i<64;i++)source.getAndSetUnchecked(i&3,i>>4,(i>>2)&3,"replacement_"+(i%16));
                assertNotSame(palette,source.dataForNativeScan().palette());
                if(reenter) {
                    Consumer<String> nested=v->trace.add("nested:"+v);
                    if(nativeMode)source.getAll(nested);else JavaPaletteDistinct.getAll(source,nested);
                }
                if(fail)throw error;
            }
        };
        if(fail)assertSame(error,assertThrows(IllegalStateException.class,()->{
            if(nativeMode)source.getAll(consumer);else JavaPaletteDistinct.getAll(source,consumer);
        })); else if(nativeMode)source.getAll(consumer);else JavaPaletteDistinct.getAll(source,consumer);
        // Callback delivery must use the palette captured before the scan.
        if(!fail)assertEquals("outer:biome_1",trace.get(trace.size()-1));
        PaletteDistinctFixtures.compare(source);
        return trace;
    }
    @Test void callbackMutationResizeReentryExceptionsAndScratchRelease() {
        for(boolean nested:new boolean[]{false,true})for(boolean fail:new boolean[]{false,true})
            assertEquals(mutation(false,nested,fail),mutation(true,nested,fail));
        var source=PaletteDistinctFixtures.biomes(4,71,"cycle");
        var raw=source.dataForNativeScan().storage().getRaw();
        for(int i=0;i<100;i++){raw[0]^=0x3333;PaletteDistinctFixtures.compare(source);}
    }

    @Test void unsupportedStorageAndSubclassCallbacksRemainOriginal() {
        for(int bits:new int[]{1,17,32})for(int size:new int[]{0,1,63,64,65,4096}) {
            var map=new IdMapper<String>();map.add("a");map.add("b");var owner=Strategy.createForBiomes(map);
            var storage=new SimpleBitStorage(bits,size);
            var source=PalettePackingFixtures.custom(owner,Strategy.ONE_BIT_LINEAR,storage,LinearPalette.create(1,List.of("a","b")));
            PaletteDistinctFixtures.compare(source);
            boolean supported=(size==64||size==4096)&&bits<=16;
            try(var ids=NativePaletteDistinct.scan(storage)){assertEquals(supported,ids!=null);}
        }
        var map=new IdMapper<String>();map.add("a");map.add("b");var owner=Strategy.createForBiomes(map);
        for(int size:new int[]{-1,0,1,64,4096}) {
            var storage=new ZeroBitStorage(size);assertNull(NativePaletteDistinct.scan(storage));
            PaletteDistinctFixtures.compare(PalettePackingFixtures.custom(owner,Strategy.ZERO_BITS,storage,SingleValuePalette.create(0,List.of("a"))));
        }
        var trace=new ArrayList<Integer>();
        var storage=new SimpleBitStorage(1,64) {
            @Override public void getAll(java.util.function.IntConsumer consumer) {
                for(int i:new int[]{1,0,1}){trace.add(i);consumer.accept(i);}
            }
        };
        var source=PalettePackingFixtures.custom(owner,Strategy.ONE_BIT_LINEAR,storage,LinearPalette.create(1,List.of("a","b")));
        assertNull(NativePaletteDistinct.scan(storage));
        var expected=new ArrayList<String>();JavaPaletteDistinct.getAll(source,expected::add);
        var order=new ArrayList<>(trace);trace.clear();var actual=new ArrayList<String>();source.getAll(actual::add);
        assertEquals(order,trace);assertEquals(expected,actual);
        var subclass=new PalettedContainer<String>("a",owner){};
        subclass.getAndSetUnchecked(0,0,0,"b");PaletteDistinctFixtures.compare(subclass);
    }

    @Test void independentReadersDuringGcHaveSeparateWorkspace() throws Exception {
        var source=PalettePackingFixtures.states(1024,71,"random");var before=source.dataForNativeScan().storage().getRaw().clone();
        var pool=Executors.newFixedThreadPool(4);
        try {var tasks=new ArrayList<Future<?>>();for(int t=0;t<4;t++)tasks.add(pool.submit(()->{
            for(int i=0;i<200;i++){PaletteDistinctFixtures.compare(source);if(i%100==0)System.gc();}
        }));for(var task:tasks)task.get();}finally{pool.shutdownNow();}
        assertArrayEquals(before,source.dataForNativeScan().storage().getRaw());
    }

    @Test void standardMissingEntriesAndStorageExceptionsKeepPartialTraces() {
        var map=new IdMapper<String>();map.add("a");map.add("b");var owner=Strategy.createForBiomes(map);
        int[] input=new int[64];input[0]=1;input[1]=0;input[2]=3;
        var source=PalettePackingFixtures.custom(owner,Strategy.TWO_BITS_LINEAR,new SimpleBitStorage(2,64,input),
            LinearPalette.create(2,List.of("a","b")));
        var expected=new ArrayList<String>();var actual=new ArrayList<String>();
        var a=assertThrows(MissingPaletteEntryException.class,()->JavaPaletteDistinct.getAll(source,expected::add));
        var b=assertThrows(MissingPaletteEntryException.class,()->source.getAll(actual::add));
        assertEquals(a.getMessage(),b.getMessage());assertEquals(List.of("b","a"),actual);assertEquals(expected,actual);
        var error=new IllegalStateException("storage failure");var trace=new ArrayList<String>();
        var custom=new SimpleBitStorage(2,64) {
            @Override public void getAll(java.util.function.IntConsumer consumer) {
                trace.add("storage");consumer.accept(1);throw error;
            }
        };
        var fallback=PalettePackingFixtures.custom(owner,Strategy.TWO_BITS_LINEAR,custom,LinearPalette.create(2,List.of("a","b")));
        assertSame(error,assertThrows(IllegalStateException.class,()->JavaPaletteDistinct.getAll(fallback,v->trace.add("consumer"))));
        var original=new ArrayList<>(trace);trace.clear();
        assertSame(error,assertThrows(IllegalStateException.class,()->fallback.getAll(v->trace.add("consumer"))));
        assertEquals(original,trace);assertEquals(List.of("storage"),trace);
    }
}
