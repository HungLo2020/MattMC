package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.*;

class NativeSavedPaletteLoadingTest {
    @BeforeAll static void setup() throws Exception { SavedPaletteVerification.setup(); }
    @Test void actualFrozenCallerOutputsMatchAcrossAllSavedWidthsAndLaterConsumers() throws Exception {
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(
            Path.of("src/main/rust/world/level/chunk/palette/frozen-saved-palettes.bin.gz"))))) {
            var cases=SavedPaletteVerification.cases();assertEquals(0x53505631,in.readInt());assertEquals(82,in.readInt());
            for(var input:cases) {
                assertEquals(input.label(),in.readUTF());var expected=in.readNBytes(in.readInt());
                var actual=SavedPaletteVerification.outcome(input);
                assertEquals(-1,Arrays.mismatch(expected,actual),"Frozen mismatch: "+input.label());
            }
            assertEquals(-1,in.read());
        }
    }
    @Test void canonicalImportsInstallPrimaryOwnersWithNoJavaStorageMirror() throws Exception {
        var data=PalettedContainer.class.getDeclaredField("data");data.setAccessible(true);
        for(var input:SavedPaletteVerification.cases()) {
            // Local duplicate palettes deliberately preserve the existing compatibility owner.
            boolean aliases=input.label().endsWith("/true");
            boolean global=input.bits()>(input.strategy().entryCount()==4096?8:3);
            var before=input.words().clone();var owner=input.load();
            if(!aliases||global) {
                assertNull(data.get(owner),input.label());
                assertTrue(owner.nativeLiveBlocks()!=null||owner.nativeLiveBiomes()!=null,input.label());
            } else assertNotNull(data.get(owner),input.label());
            assertArrayEquals(before,input.words());
        }
    }
    @Test void zeroWidthNeverConsumesTheOptionalStreamAndDeclaredErrorsRunFirst() {
        var consumed=new java.util.concurrent.atomic.AtomicInteger();
        var stream=LongStream.of(42).peek(value->consumed.incrementAndGet());
        var result=PalettedContainer.unpack(SavedPaletteVerification.blocks,new PalettedContainerRO.PackedData<>(
            List.of(Block.BLOCK_STATE_REGISTRY.byId(0)),Optional.of(stream),0));
        assertNotNull(result.getOrThrow().nativeLiveBlocks());assertEquals(0,consumed.get());assertEquals(42,stream.findFirst().getAsLong());
        stream=LongStream.of(42).peek(value->fail("Invalid declaration consumed storage"));
        var invalid=PalettedContainer.unpack(SavedPaletteVerification.blocks,new PalettedContainerRO.PackedData<>(
            List.of(Block.BLOCK_STATE_REGISTRY.byId(0),Block.BLOCK_STATE_REGISTRY.byId(1)),Optional.of(stream),5));
        assertEquals("Invalid bit count, calculated 4, but container declared 5",invalid.error().orElseThrow().message());
        stream.close();
    }
    @Test void malformedLengthAndIndicesRetainOriginalResultsAndExceptions() {
        for(int count:new int[]{2,17,257,513}) {
            var input=SavedPaletteVerification.input(SavedPaletteVerification.blocks,count,31,false);
            var truncated=Arrays.copyOf(input.words(),input.words().length-1);
            var reference=JavaPaletteUnpacking.unpack(input.strategy(),new PalettedContainerRO.PackedData<>(input.palette(),Optional.of(Arrays.stream(truncated)),input.bits()));
            var actual=PalettedContainer.unpack(input.strategy(),new PalettedContainerRO.PackedData<>(input.palette(),Optional.of(Arrays.stream(truncated)),input.bits()));
            assertEquals(reference.error().orElseThrow().message(),actual.error().orElseThrow().message());
        }
        var input=SavedPaletteVerification.input(SavedPaletteVerification.biomes,9,31,false);
        input.words()[0]=(input.words()[0]&~15L)|15L;
        assertNull(NativeSavedPaletteLoading.unpack(input.strategy(),input.palette(),input.words()));
        var expected=assertThrows(MissingPaletteEntryException.class,()->JavaPaletteUnpacking.unpack(input.strategy(),new PalettedContainerRO.PackedData<>(input.palette(),Optional.of(Arrays.stream(input.words())),input.bits())));
        var actual=assertThrows(MissingPaletteEntryException.class,input::load);assertEquals(expected.getMessage(),actual.getMessage());
    }
    @Test void customListsAreNotReadByNativeAdmissionAndStreamsAreConsumedOnce() {
        var input=SavedPaletteVerification.input(SavedPaletteVerification.blocks,257,31,false);
        var trace=new ArrayList<String>();
        var list=new AbstractList<net.minecraft.world.level.block.state.BlockState>() {
            public int size(){trace.add("size");return input.palette().size();}
            public net.minecraft.world.level.block.state.BlockState get(int i){trace.add("get"+i);return input.palette().get(i);}
        };
        assertNull(NativeSavedPaletteLoading.unpack(input.strategy(),list,input.words()));assertTrue(trace.isEmpty());
        var consumed=new java.util.concurrent.atomic.AtomicInteger();
        var loaded=PalettedContainer.unpack(input.strategy(),new PalettedContainerRO.PackedData<>(input.palette(),
            Optional.of(Arrays.stream(input.words()).peek(word->consumed.incrementAndGet())),input.bits())).getOrThrow();
        assertNotNull(loaded.nativeLiveBlocks());assertEquals(input.words().length,consumed.get());
    }
}
