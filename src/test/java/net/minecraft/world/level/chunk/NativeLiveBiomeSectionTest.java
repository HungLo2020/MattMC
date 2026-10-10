package net.minecraft.world.level.chunk;

import io.netty.buffer.Unpooled;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.biome.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeLiveBiomeSectionTest {
    static MappedRegistry<Biome> registry;
    static Strategy<Holder<Biome>> strategy;
    static List<Holder<Biome>> values;
    @BeforeAll static synchronized void load() {
        if (values != null) return;
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        for (int i = 0; i < 512; i++) {
            var biome = new Biome.BiomeBuilder().hasPrecipitation(false).temperature(.5F).downfall(.5F)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor((i * 0x03050b) & 0xffffff).waterColor(0).waterFogColor(0).skyColor(i).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build();
            Registry.register(registry, ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("native_biome_test_" + i)), biome);
        }
        registry.freeze(); strategy = Strategy.createForBiomes(registry.asHolderIdMap());
        NativeLiveBiomeSection.register(strategy, registry);
        values = java.util.stream.IntStream.range(0,512).mapToObj(i -> strategy.globalMap().byId(i)).toList();
    }
    record Expected(int bits, int requested, boolean global, int[] palette, long[] words, int[] values) {}
    static Expected read(DataInputStream in) throws IOException {
        int bits = in.readInt(), requested = in.readInt(); boolean global = in.readBoolean();
        int[] palette = new int[in.readInt()]; for (int i=0;i<palette.length;i++) palette[i]=in.readInt();
        long[] words = new long[in.readInt()]; for (int i=0;i<words.length;i++) words[i]=in.readLong();
        int[] values = new int[64]; for (int i=0;i<64;i++) values[i]=in.readInt();
        return new Expected(bits,requested,global,palette,words,values);
    }
    static void equal(PalettedContainer<Holder<Biome>> container, Expected expected) {
        assertNotNull(container.nativeLiveBiomes()); var data=container.dataForNativeScan();
        assertEquals(expected.bits,data.storage().getBits());
        assertEquals(expected.requested,data.configuration().bitsInStorage());
        assertEquals(expected.global,data.palette()==strategy.globalPalette());
        if (!expected.global) {
            assertEquals(expected.palette.length,data.palette().getSize());
            for(int i=0;i<expected.palette.length;i++) assertSame(values.get(expected.palette[i]),data.palette().valueFor(i));
        }
        assertArrayEquals(expected.words,data.storage().getRaw());
        for(int i=0;i<64;i++) assertSame(values.get(expected.values[i]),container.get(i&3,i>>4,(i>>2)&3));
    }
    @Test void allActualFrozenPaletteOperationsCrossProductionJavaNativeBoundary() throws IOException {
        Path fixture=Path.of("src/main/rust/world/level/chunk/biomes/frozen-live-biomes.bin.gz");
        try(var input=new DataInputStream(new GZIPInputStream(Files.newInputStream(fixture)))) {
            assertEquals(0x42494d31,input.readInt());assertEquals(16,input.readInt());int operations=0;
            for(int scenario=0;scenario<16;scenario++) {
                assertEquals(512,input.readInt());assertEquals(9,input.readInt());var first=read(input);
                @SuppressWarnings("unchecked") PalettedContainer<Holder<Biome>>[] owners=new PalettedContainer[4];
                var config=strategy.getConfigurationForBitCount(first.requested);
                owners[0]=new PalettedContainer<>(strategy,config,first.bits==0?new ZeroBitStorage(64):new SimpleBitStorage(first.bits,64,first.words),
                    config.createPalette(strategy,Arrays.stream(first.palette).mapToObj(values::get).toList()));
                equal(owners[0],first);int steps=input.readInt();
                for(int step=0;step<steps;step++) {
                    int kind=input.readInt(),slot=input.readInt(),index=input.readInt(),value=input.readInt(),old=input.readInt();
                    switch(kind) {
                        case 0 -> assertSame(values.get(old),owners[slot].getAndSetUnchecked(index&3,index>>4,(index>>2)&3,values.get(value)));
                        case 1 -> owners[slot]=owners[index].copy();
                        case 2 -> {var buffer=new FriendlyByteBuf(Unpooled.buffer());try {buffer.writeByte(0);buffer.writeVarInt(value);owners[slot].read(buffer);}finally {buffer.release();}}
                        default -> fail("Unexpected oracle operation");
                    }
                    int mask=input.readInt();for(int i=0;i<4;i++) {assertEquals((mask&(1<<i))!=0,owners[i]!=null);if(owners[i]!=null) equal(owners[i],read(input));}operations++;
                }
            }
            assertEquals(1600,operations);assertEquals(-1,input.read());
        }
    }
    @Test void compatibilityTransferInvalidatesOwnerWhileOldCpuViewSurvives() {
        var container=new PalettedContainer<>(values.get(0),strategy);var owner=container.nativeLiveBiomes();
        assertNotNull(owner);container.dataForCompatibilityMutation();assertNull(container.nativeLiveBiomes());
        assertSame(values.get(0),owner.get(0));container.set(0,0,0,values.get(1));assertSame(values.get(1),container.get(0,0,0));
    }
    @Test void compatibilityPaletteTransferPreservesAllSingleValueCopyAliases() {
        var source = new PalettedContainer<>(values.get(0), strategy);
        var copy = source.copy(); var grown = source.copy();
        grown.set(0,0,0,values.get(1));
        var grownOwner = grown.nativeLiveBiomes();
        var palette = copy.dataForCompatibilityMutation().palette();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { buffer.writeVarInt(7); palette.read(buffer, strategy.globalMap()); }
        finally { buffer.release(); }
        assertSame(values.get(7),source.get(0,0,0));
        assertNull(source.nativeLiveBiomes());
        assertSame(values.get(7),copy.get(0,0,0));
        assertSame(grownOwner,grown.nativeLiveBiomes()); // Growth detached before escape.
        assertSame(values.get(0),grown.get(1,0,0));
        var later = source.copy();
        buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { buffer.writeByte(0); buffer.writeVarInt(9); later.read(buffer); }
        finally { buffer.release(); }
        assertSame(values.get(9),source.get(0,0,0));
        assertSame(values.get(9),copy.get(0,0,0));
        assertNull(later.nativeLiveBiomes());
        buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { buffer.writeByte(0); buffer.writeVarInt(11); source.read(buffer); }
        finally { buffer.release(); }
        assertSame(values.get(11),later.get(0,0,0));
        assertSame(values.get(11),copy.get(0,0,0));
    }
    @Test void customStrategyPreservesCompatibilityAndSingleAliasRead() {
        var custom=Strategy.createForBiomes(strategy.globalMap());var source=new PalettedContainer<>(values.get(0),custom);
        assertNull(source.nativeLiveBiomes());var copy=source.copy();var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {buffer.writeByte(0);buffer.writeVarInt(7);copy.read(buffer);}finally {buffer.release();}
        assertSame(values.get(7),source.get(0,0,0));
    }
}
